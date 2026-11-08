package com.watchpath.app.ui.search;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.watchpath.app.data.remote.DiscoverQuery;
import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.dto.Genre;
import com.watchpath.app.data.remote.dto.MediaDto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    /** Which endpoint feeds the list. Drives the header and the paging branch. */
    public enum Mode { TRENDING, SEARCH, DISCOVER }

    private final MediaRemoteDataSource remoteDataSource;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // LiveData
    private final MutableLiveData<List<MediaDto>> _mediaList = new MutableLiveData<>();
    public LiveData<List<MediaDto>> getMediaList() { return _mediaList; }
    private volatile int loadGeneration = 0; // bumped on every fresh load (trending, refresh, search)
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsLoading() { return _isLoading; }

    private final MutableLiveData<Boolean> _isLoadingMore = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsLoadingMore() { return _isLoadingMore; }

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public LiveData<String> getErrorMessage() { return _errorMessage; }

    private final MutableLiveData<Mode> _mode = new MutableLiveData<>(Mode.TRENDING);
    public LiveData<Mode> getMode() { return _mode; }

    // Genres for the filter sheet, keyed by the media type they were fetched for.
    private final MutableLiveData<List<Genre>> _genreList = new MutableLiveData<>();
    public LiveData<List<Genre>> getGenreList() { return _genreList; }

    // Pagination State
    private int currentPage = 1;
    private int totalPages = 1;
    private String currentQuery = "";
    private DiscoverQuery currentDiscover;

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.remoteDataSource = new MediaRemoteDataSource();
        loadTrending();
    }

    public void loadTrending() {
        loadGeneration++;
        currentPage = 1;
        currentQuery = "";
        currentDiscover = null;
        _mode.setValue(Mode.TRENDING);
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<MediaDto> results = remoteDataSource.getTrending(currentPage);
                totalPages = 500; // TMDB serves at most 500 pages; loadNextPage stops at the first empty one

                Log.d(TAG, "Trending success: loaded " + results.size() + " items.");

                _mediaList.postValue(results);
            } catch (Exception e) {
                Log.e(TAG, "Trending load failed", e);
                _errorMessage.postValue("Couldn't load trending. Check connection.");
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    public void search(String query) {
        if (query == null || query.trim().isEmpty()) return;
        loadGeneration++;
        currentPage = 1;
        currentQuery = query;
        currentDiscover = null;
        _mode.setValue(Mode.SEARCH);
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<MediaDto> results = remoteDataSource.searchMulti(currentQuery, currentPage);

                totalPages = results.isEmpty() ? 1 : 500; // same TMDB limit; loadNextPage stops at the first empty page

                Log.d(TAG, "Search success: found " + results.size() + " results for: " + currentQuery);

                _mediaList.postValue(results);
            } catch (Exception e) {
                Log.e(TAG, "Search failed", e);
                _errorMessage.postValue("Couldn't load results. Check connection.");
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * Filter mode: runs TMDB /discover with the given genre/year/rating filters.
     * A blank query (no filters at all) falls back to trending so the list isn't
     * needlessly narrowed to a default popularity feed.
     */
    public void loadDiscover(DiscoverQuery query) {
        if (query == null || query.isEmpty()) {
            loadTrending();
            return;
        }
        loadGeneration++;
        currentPage = 1;
        currentQuery = "";
        currentDiscover = query.copy(); // snapshot: later edits must not affect paging
        _mode.setValue(Mode.DISCOVER);
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        final DiscoverQuery queryAtStart = currentDiscover;
        executor.execute(() -> {
            try {
                List<MediaDto> results = remoteDataSource.getDiscover(queryAtStart, currentPage);
                totalPages = results.isEmpty() ? 1 : 500;

                Log.d(TAG, "Discover success: loaded " + results.size() + " items.");

                _mediaList.postValue(results);
            } catch (Exception e) {
                Log.e(TAG, "Discover load failed", e);
                _errorMessage.postValue("Couldn't load results. Check connection.");
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /** Fetches the genre list for a media type, for the filter sheet. */
    public void loadGenres(String mediaType) {
        executor.execute(() -> {
            try {
                _genreList.postValue(remoteDataSource.getGenres(mediaType));
            } catch (Exception e) {
                Log.e(TAG, "Genre list load failed", e);
            }
        });
    }

    public void loadNextPage() {
        // Don't load if we are already loading, or if we've hit the end
        if (Boolean.TRUE.equals(_isLoading.getValue()) || Boolean.TRUE.equals(_isLoadingMore.getValue())) return;
        if (currentPage >= totalPages) return;

        _isLoadingMore.setValue(true);
        final int nextPage = currentPage + 1;
        final String queryAtStart = currentQuery; // Capture query to prevent race conditions
        final DiscoverQuery discoverAtStart = currentDiscover;
        final Mode modeAtStart = _mode.getValue() == null ? Mode.TRENDING : _mode.getValue();
        final int generationAtStart = loadGeneration;
        executor.execute(() -> {
            try {
                List<MediaDto> newResults;
                if (modeAtStart == Mode.DISCOVER) {
                    if (discoverAtStart == null) return;
                    newResults = remoteDataSource.getDiscover(discoverAtStart, nextPage);
                } else if (queryAtStart.isEmpty()) {
                    newResults = remoteDataSource.getTrending(nextPage);
                } else {
                    newResults = remoteDataSource.searchMulti(queryAtStart, nextPage);
                }

                // A refresh or new search started while this page was loading: discard the stale result
                if (generationAtStart != loadGeneration) return;

                if (newResults != null && !newResults.isEmpty()) {
                    currentPage = nextPage;

                    List<MediaDto> currentList = _mediaList.getValue();
                    if (currentList == null) currentList = new ArrayList<>();

                    List<MediaDto> combinedList = new ArrayList<>(currentList);
                    combinedList.addAll(newResults);
                    _mediaList.postValue(combinedList);
                } else {
                    // Reached the end of the list
                    totalPages = currentPage;
                }
            } catch (Exception e) {
                Log.e(TAG, "Load next page failed", e);
            } finally {
                _isLoadingMore.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
