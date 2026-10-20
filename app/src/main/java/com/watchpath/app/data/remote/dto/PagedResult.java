package com.watchpath.app.data.remote.dto;

import java.util.List;

/** One page of TMDB results plus how many pages exist in total. */
public class PagedResult {
    public final List<MediaDto> items;
    public final int totalPages;

    public PagedResult(List<MediaDto> items, int totalPages) {
        this.items = items;
        this.totalPages = totalPages;
    }
}