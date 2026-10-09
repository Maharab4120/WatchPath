package com.watchpath.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny image loader. Downloads Bitmaps off the main thread and caches them
 * in memory with an LruCache.
 *
 * Week 11 syllabus points demonstrated:
 *   - background data operations (worker thread pool)
 *   - memory management (LruCache with a size limit in KB)
 *
 * Deliberately hand-written instead of using Glide/Picasso, so the download
 * and cache logic are visible in our own code. If the instructor later
 * permits Glide, only this file would change - nothing that calls it.
 */
public final class ImageLoader {

    private static volatile ImageLoader instance;

    public static ImageLoader get() {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) instance = new ImageLoader();
            }
        }
        return instance;
    }

    // --- Internals ---

    // Four download threads. Enough parallelism without thrashing the network.
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    // Main thread handler so callbacks run where UI updates are legal.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Cache size = 1/8 of app's max heap, measured in KB. Typical Android guidance.
    private final LruCache<String, Bitmap> cache;

    private ImageLoader() {
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheKb = maxKb / 8;
        cache = new LruCache<String, Bitmap>(cacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    /** Callback delivered on the main thread. */
    public interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    /**
     * Load an image from a URL into an ImageView.
     * First shows a placeholder color, then swaps in the decoded Bitmap.
     * Safe to call from the main thread - all I/O happens on a worker.
     */
    public void load(final String url, final ImageView into) {
        if (url == null || url.isEmpty()) {
            into.setImageDrawable(null);
            into.setBackgroundColor(0xFF333333);
            return;
        }

        // 1. Cache hit? Set immediately, no thread needed.
        Bitmap cached = cache.get(url);
        if (cached != null) {
            into.setBackgroundColor(0x00000000);
            into.setImageBitmap(cached);
            return;
        }

        // 2. Show placeholder. Also tag the view so stale loads don't overwrite
        //    the wrong row when the user scrolls fast.
        into.setBackgroundColor(0xFF333333);
        into.setImageDrawable(null);
        into.setTag(url);

        final Callback cb = bitmap -> {
            // Check that this ImageView still wants THIS url (view recycling safety).
            Object tag = into.getTag();
            if (tag == null || !tag.equals(url)) return;
            if (bitmap != null) {
                into.setBackgroundColor(0x00000000);
                into.setImageBitmap(bitmap);
            }
        };

        executor.execute(() -> {
            Bitmap bmp = download(url);
            if (bmp != null) cache.put(url, bmp);
            mainHandler.post(() -> cb.onLoaded(bmp));
        });
    }

    private Bitmap download(String url) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(Constants.NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(Constants.NETWORK_TIMEOUT_MS);
            conn.setDoInput(true);
            conn.connect();

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) return null;

            in = conn.getInputStream();
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            if (conn != null) conn.disconnect();
        }
    }
}