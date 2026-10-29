package com.watchpath.app.ui.search;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.dto.MediaDto;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for MainActivity.
 * Survives configuration changes (like rotation) and holds all the UI state.
 */
public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    private final MediaRemoteDataSource remoteDataSource;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // LiveData holds the state. The Activity observes it.
    private final MutableLiveData<List<MediaDto>> _mediaList = new MutableLiveData<>();
    public LiveData<List<MediaDto>> getMediaList() { return _mediaList; }

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsLoading() { return _isLoading; }

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public LiveData<String> getErrorMessage() { return _errorMessage; }

    private final MutableLiveData<Boolean> _isTrendingMode = new MutableLiveData<>(true);
    public LiveData<Boolean> getIsTrendingMode() { return _isTrendingMode; }

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.remoteDataSource = new MediaRemoteDataSource();
        loadTrending(); // Load initial data
    }

    public void loadTrending() {
        _isTrendingMode.setValue(true);
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<MediaDto> results = remoteDataSource.getTrending();
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

        _isTrendingMode.setValue(false);
        _isLoading.setValue(true);
        _errorMessage.setValue(null);
        _mediaList.setValue(null); // Clear list while loading

        executor.execute(() -> {
            try {
                List<MediaDto> results = remoteDataSource.searchMulti(query);
                _mediaList.postValue(results);
            } catch (Exception e) {
                Log.e(TAG, "Search failed", e);
                _errorMessage.postValue("Couldn't load results. Check connection.");
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow(); // Clean up threads when ViewModel is destroyed
    }
}