package com.watchpath.app;

import android.os.Bundle;

import com.watchpath.app.databinding.ActivityMainBinding;
import com.watchpath.app.ui.BaseActivity;

/**
 * Temporary launch activity. In Phase 2 this becomes SearchActivity, but for
 * now it just proves ViewBinding is wired up and the lifecycle logger works.
 */
public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String key=BuildConfig.TMDB_API_KEY;
        // ViewBinding: the generated class is named after the layout file.
        // activity_main.xml -> ActivityMainBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());

        // setContentView takes a View. binding.getRoot() is the root view
        // of activity_main.xml. From now on, we never call findViewById again.
        setContentView(binding.getRoot());
    }
}