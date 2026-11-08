package com.watchpath.app.ui.mylist;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.database.Cursor;
import android.net.Uri;

import com.watchpath.app.data.local.MediaContentProvider;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.IntentFilter;
import android.view.View;

import com.watchpath.app.data.remote.CloudListSync;
import com.watchpath.app.receiver.ConnectivityReceiver;
import com.watchpath.app.receiver.PowerStateReceiver;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;

import com.google.android.material.tabs.TabLayout;
import com.watchpath.app.R;
import com.watchpath.app.WatchPathApp;
import com.watchpath.app.data.MediaRepository;
import com.watchpath.app.data.local.MediaEntity;
import com.watchpath.app.databinding.ActivityMyListBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.ui.details.DetailsActivity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.watchpath.app.R;
import com.watchpath.app.service.PendingRequestService;

/**
 * Shows saved titles from the local Room database, split by status tab.
 *
 * Week 11 syllabus: Room / SQLite queries, background data operations.
 */
public class MyListActivity extends BaseActivity {

    private static final String TAG = "WatchPathMyList";

    /** Tab labels. "All" shows every saved title; the rest filter by status. */
    private static final String[] TAB_TITLES = {
            "All", "Watching", "Planned", "On-Hold", "Dropped", "Watched"
    };

    private ActivityMyListBinding binding;
    private MediaEntityAdapter adapter;
    private MediaRepository repository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentTab = "All";
    private ConnectivityReceiver connectivityReceiver;
    private PowerStateReceiver powerStateReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_my_list);
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        repository = new MediaRepository(WatchPathApp.getDatabase().mediaDao());

        adapter = new MediaEntityAdapter();
        adapter.setOnItemClickListener(this::openDetails);
        binding.recyclerMyList.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMyList.setAdapter(adapter);
        setupSwipeToDelete();

        // Populate tabs.
        for (String title : TAB_TITLES) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(title));
        }
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = TAB_TITLES[tab.getPosition()];
                loadList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload on every resume, in case the user changed a status on Details.
        loadList(); // show what we have right away
        CloudListSync.syncNow(getApplicationContext(),
                WatchPathApp.getDatabase().mediaDao(),
                () -> { if (!isFinishing() && !isDestroyed()) loadList(); }); // refresh after the cloud merge
    }

    private void loadList() {
        final String tab = currentTab;
        executor.execute(() -> {
            // Build the URI: base for "All", /status/<tab> for everything else.
            Uri uri = "All".equals(tab)
                    ? MediaContentProvider.CONTENT_URI
                    : MediaContentProvider.CONTENT_URI.buildUpon()
                    .appendPath("status")
                    .appendPath(tab)
                    .build();

            Cursor c = getContentResolver().query(uri, null, null, null, null);
            List<MediaEntity> rows = readRows(c);
            if (c != null) c.close();

            mainHandler.post(() -> {
                Log.d(TAG, "Tab \"" + tab + "\" -> " + rows.size() + " rows via ContentProvider");
                adapter.submitList(rows);
                binding.textEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                binding.recyclerMyList.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    /** Map a MediaEntity cursor to a List. All columns are nullable-safe. */
    private List<MediaEntity> readRows(Cursor c) {
        List<MediaEntity> out = new java.util.ArrayList<>();
        if (c == null) return out;
        while (c.moveToNext()) {
            MediaEntity e = new MediaEntity();
            e.id = c.getInt(c.getColumnIndexOrThrow("id"));
            e.type = c.getString(c.getColumnIndexOrThrow("type"));
            e.title = c.getString(c.getColumnIndexOrThrow("title"));
            e.posterPath = c.isNull(c.getColumnIndexOrThrow("posterPath"))
                    ? null : c.getString(c.getColumnIndexOrThrow("posterPath"));
            e.overview = c.getString(c.getColumnIndexOrThrow("overview"));
            e.rating = c.getDouble(c.getColumnIndexOrThrow("rating"));
            e.year = c.getString(c.getColumnIndexOrThrow("year"));
            e.status = c.getString(c.getColumnIndexOrThrow("status"));
            e.addedAt = c.getLong(c.getColumnIndexOrThrow("addedAt"));
            out.add(e);
        }
        return out;
    }

    /**
     * Swipe a row (either direction) to remove it. Destructive, so we always
     * offer an Undo that re-saves the exact same entity.
     */
    private void setupSwipeToDelete() {
        final Drawable deleteIcon = ContextCompat.getDrawable(this, R.drawable.ic_delete);
        final Paint deletePaint = new Paint();
        deletePaint.setColor(ContextCompat.getColor(this, R.color.swipe_delete_bg));

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false; // no drag-and-drop reordering
            }

            /**
             * Draws the classic "delete" affordance behind the sliding row: a
             * red panel filling the vacated space, with a white trash icon
             * aligned to the edge the row is being swiped toward.
             */
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY, int actionState,
                                    boolean isCurrentlyActive) {
                View item = vh.itemView;
                if (deleteIcon != null && dX != 0) {
                    int iconMargin = (item.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                    int iconTop = item.getTop() + iconMargin;
                    int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();

                    if (dX > 0) {
                        // Swiping right: red panel grows from the left edge.
                        c.drawRect(item.getLeft(), item.getTop(),
                                item.getLeft() + dX, item.getBottom(), deletePaint);
                        int iconLeft = item.getLeft() + iconMargin;
                        deleteIcon.setBounds(iconLeft, iconTop,
                                iconLeft + deleteIcon.getIntrinsicWidth(), iconBottom);
                    } else {
                        // Swiping left: red panel grows from the right edge.
                        c.drawRect(item.getRight() + dX, item.getTop(),
                                item.getRight(), item.getBottom(), deletePaint);
                        int iconRight = item.getRight() - iconMargin;
                        deleteIcon.setBounds(iconRight - deleteIcon.getIntrinsicWidth(), iconTop,
                                iconRight, iconBottom);
                    }
                    deleteIcon.draw(c);
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int position = vh.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                MediaEntity removed = adapter.getItemAt(position);
                if (removed == null) return;

                adapter.removeAt(position);
                updateEmptyState();
                deleteWithUndo(removed);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerMyList);
    }

    /** Deletes on a background thread, then shows a Snackbar offering Undo. */
    private void deleteWithUndo(MediaEntity removed) {
        executor.execute(() -> repository.remove(removed.id, removed.type));

        Snackbar.make(binding.getRoot(),
                        getString(R.string.swipe_deleted, removed.title),
                        Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, v -> executor.execute(() -> {
                    repository.addOrUpdate(removed);
                    mainHandler.post(this::loadList);
                }))
                .show();
    }

    private void updateEmptyState() {
        boolean empty = adapter.getItemCount() == 0;
        binding.textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerMyList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openDetails(MediaEntity item) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_ID, item.id);
        intent.putExtra(DetailsActivity.EXTRA_TYPE, item.type);
        intent.putExtra(DetailsActivity.EXTRA_TITLE, item.title);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_my_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_check_updates) {
            // Foreground service: show tray progress while it checks.
            ContextCompat.startForegroundService(
                    this, new Intent(this, PendingRequestService.class));
            Toast.makeText(this, "Checking for updates…", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onStart() {
        super.onStart();
        connectivityReceiver = new ConnectivityReceiver(this::updateOfflineBanner);
        IntentFilter filter = new IntentFilter();
        filter.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityReceiver, filter);
        // Also check once immediately, in case we launched while offline.
        updateOfflineBanner(ConnectivityReceiver.isOnline(this));

        // Power-state receiver: low battery and airplane-mode transitions.
        boolean lowBattery = PowerStateReceiver.isLowBattery(this);
        boolean airplaneMode = PowerStateReceiver.isAirplaneModeOn(this);
        powerStateReceiver = new PowerStateReceiver(this::updatePowerBanner, lowBattery, airplaneMode);
        IntentFilter powerFilter = new IntentFilter();
        powerFilter.addAction(Intent.ACTION_BATTERY_LOW);
        powerFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        powerFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(powerStateReceiver, powerFilter);
        updatePowerBanner(lowBattery, airplaneMode);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connectivityReceiver != null) {
            unregisterReceiver(connectivityReceiver);
            connectivityReceiver = null;
        }
        if (powerStateReceiver != null) {
            unregisterReceiver(powerStateReceiver);
            powerStateReceiver = null;
        }
    }

    private void updateOfflineBanner(boolean online) {
        binding.textOffline.setVisibility(online ? View.GONE : View.VISIBLE);
    }

    /**
     * Battery / airplane banner. Airplane mode takes precedence when both
     * apply, since it also blocks cloud sync.
     */
    private void updatePowerBanner(boolean lowBattery, boolean airplaneMode) {
        Log.d(TAG, "Power state: lowBattery=" + lowBattery + ", airplaneMode=" + airplaneMode);

        if (airplaneMode) {
            binding.textPowerBanner.setText(R.string.airplane_banner);
            binding.textPowerBanner.setVisibility(View.VISIBLE);
        } else if (lowBattery) {
            binding.textPowerBanner.setText(R.string.battery_low_banner);
            binding.textPowerBanner.setVisibility(View.VISIBLE);
        } else {
            binding.textPowerBanner.setVisibility(View.GONE);
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}