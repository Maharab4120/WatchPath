package com.watchpath.app.ui.search;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.watchpath.app.R;
import com.watchpath.app.data.remote.DiscoverQuery;
import com.watchpath.app.data.remote.dto.Genre;
import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.databinding.FragmentFilterSheetBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet that collects genre / year / minimum-rating filters and hands
 * them back to MainActivity through the Fragment Result API.
 */
public class FilterSheetFragment extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "filter_request";
    public static final String RESULT_QUERY = "filter_result";

    private static final String ARG_QUERY = "arg_query";

    private FragmentFilterSheetBinding binding;
    private MainViewModel viewModel;

    /** Editable copy of the currently-applied filters. */
    private final DiscoverQuery working = new DiscoverQuery();
    private final List<Chip> genreChips = new ArrayList<>();

    public static FilterSheetFragment newInstance(DiscoverQuery current) {
        FilterSheetFragment fragment = new FilterSheetFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_QUERY,
                current != null ? current.copy() : new DiscoverQuery());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFilterSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // Restore whatever was applied last time.
        Bundle args = getArguments();
        if (args != null) {
            Object o = args.getSerializable(ARG_QUERY);
            if (o instanceof DiscoverQuery) {
                DiscoverQuery q = (DiscoverQuery) o;
                working.mediaType = q.mediaType;
                working.genreIds.addAll(q.genreIds);
                working.year = q.year;
                working.minRating = q.minRating;
            }
        }

        boolean isTv = MediaDto.TYPE_TV.equals(working.mediaType);
        binding.toggleType.check(isTv ? R.id.buttonTv : R.id.buttonMovie);

        if (working.year > 0) binding.editYear.setText(String.valueOf(working.year));

        // Slider only accepts multiples of stepSize, so snap the stored value.
        float rating = (float) Math.max(0, Math.min(10, working.minRating));
        rating = Math.round(rating * 2f) / 2f;
        binding.sliderRating.setValue(rating);
        updateRatingLabel(rating);

        binding.sliderRating.addOnChangeListener(
                (slider, value, fromUser) -> updateRatingLabel(value));

        // Movie and TV genre ids are different, so switching type drops genres.
        binding.toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            String type = checkedId == R.id.buttonTv ? MediaDto.TYPE_TV : MediaDto.TYPE_MOVIE;
            if (!type.equals(working.mediaType)) {
                working.mediaType = type;
                working.genreIds.clear();
                renderGenres(null); // clear until the new list arrives
                viewModel.loadGenres(type);
            }
        });

        viewModel.getGenreList().observe(getViewLifecycleOwner(), this::renderGenres);

        // A single-thread executor in the ViewModel keeps these in order.
        viewModel.loadGenres(working.mediaType);

        binding.buttonReset.setOnClickListener(v -> resetFilters());
        binding.buttonApply.setOnClickListener(v -> applyAndDismiss());
    }

    private void renderGenres(@Nullable List<Genre> genres) {
        binding.chipGroupGenres.removeAllViews();
        genreChips.clear();

        if (genres == null || genres.isEmpty()) {
            binding.textGenresStatus.setVisibility(View.VISIBLE);
            binding.textGenresStatus.setText(R.string.filter_genres_loading);
            return;
        }
        binding.textGenresStatus.setVisibility(View.GONE);

        for (Genre genre : genres) {
            Chip chip = new Chip(requireContext());
            chip.setText(genre.name);
            chip.setCheckable(true);
            chip.setChecked(working.genreIds.contains(genre.id));
            chip.setTag(genre.id);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    working.genreIds.add(genre.id);
                } else {
                    working.genreIds.remove(genre.id);
                }
            });
            genreChips.add(chip);
            binding.chipGroupGenres.addView(chip);
        }
    }

    private void updateRatingLabel(float value) {
        if (value <= 0f) {
            binding.textRatingValue.setText(R.string.filter_rating_any);
        } else {
            binding.textRatingValue.setText(getString(R.string.filter_rating_value, value));
        }
    }

    private void resetFilters() {
        working.genreIds.clear();
        working.year = 0;
        working.minRating = 0;
        binding.editYear.setText("");
        binding.sliderRating.setValue(0f);
        updateRatingLabel(0f);
        for (Chip chip : genreChips) chip.setChecked(false);
    }

    private void applyAndDismiss() {
        String yearText = binding.editYear.getText() == null
                ? "" : binding.editYear.getText().toString().trim();
        int year = 0;
        if (!yearText.isEmpty()) {
            try {
                year = Integer.parseInt(yearText);
            } catch (NumberFormatException ignored) {
                // leave as "any year"
            }
            if (year < 1900 || year > 2100) year = 0;
        }
        working.year = year;
        working.minRating = binding.sliderRating.getValue();

        Bundle result = new Bundle();
        result.putSerializable(RESULT_QUERY, working.copy());
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
