package com.watchpath.app.ui.search;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.dto.MediaDto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    private final MediaRemoteDataSource remoteDataSource;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // LiveData
    private final MutableLiveData<List<MediaDto>> _mediaList = new MutableLiveData<>();
    public LiveData<List<MediaDto>> getMediaList() { return _mediaList; }

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsLoading() { return _isLoading; }

    private final MutableLiveData<Boolean> _isLoadingMore = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsLoadingMore() { return _isLoadingMore; }

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public LiveData<String> getErrorMessage() { return _errorMessage; }

    private final MutableLiveData<Boolean> _isTrendingMode = new MutableLiveData<>(true);
    public LiveData<Boolean> getIsTrendingMode() { return _isTrendingMode; }

    // Pagination State
    private int currentPage = 1;
    private int totalPages = 1;
    private String currentQuery = "";

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.remoteDataSource = new MediaRemoteDataSource();
        loadTrending();
    }

    public void loadTrending() {
        currentPage = 1;
        currentQuery = "";
        _isTrendingMode.setValue(true);
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                // Assuming getTrending now returns a response that includes totalPages
                // For simplicity, let's assume the max pages for trending is 500 (TMDB limit)
                List<MediaDto> results = remoteDataSource.getTrending(currentPage);
                totalPages = 500; // Hardcode for trending since TMDB doesn't give a clean total for trending
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

        currentPage = 1;
        currentQuery = query;
        _isTrendingMode.setValue(false);
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<MediaDto> results = remoteDataSource.searchMulti(currentQuery, currentPage);

                // If you modified MediaRemoteDataSource to return a wrapper object with totalPages,
                // you would set it here. For now, we assume 500 if results aren't empty.
                totalPages = results.isEmpty() ? 1 : 500;

                _mediaList.postValue(results);
            } catch (Exception e) {
                Log.e(TAG, "Search failed", e);
                _errorMessage.postValue("Couldn't load results. Check connection.");
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    public void loadNextPage() {
        // Don't load if we are already loading, or if we've hit the end
        if (Boolean.TRUE.equals(_isLoading.getValue()) || Boolean.TRUE.equals(_isLoadingMore.getValue())) return;
        if (currentPage >= totalPages) return;

        _isLoadingMore.setValue(true);
        int nextPage = currentPage + 1;

        executor.execute(() -> {
            try {
                List<MediaDto> newResults;
                if ("".equals(currentQuery)) {
                    newResults = remoteDataSource.getTrending(nextPage);
                } else {
                    newResults = remoteDataSource.searchMulti(currentQuery, nextPage);
                }

                if (newResults != null && !newResults.isEmpty()) {
                    currentPage = nextPage; // Only update page if successful

                    // Append to the existing list
                    List<MediaDto> currentList = _mediaList.getValue();
                    if (currentList == null) currentList = new ArrayList<>();

                    List<MediaDto> combinedList = new ArrayList<>(currentList);
                    combinedList.addAll(newResults);

                    _mediaList.postValue(combinedList);
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