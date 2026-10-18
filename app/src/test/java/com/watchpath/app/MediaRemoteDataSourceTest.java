package com.watchpath.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.dto.MediaDto;

import org.junit.Test;

import java.util.List;

/**
 * Unit tests for the remote data source's parsing logic.
 * These run on the local JVM (no Android device needed).
 */
public class MediaRemoteDataSourceTest {

    // --- Year Extraction Tests ---

    @Test
    public void testExtractYear_ValidDate() {
        assertEquals("1999", MediaRemoteDataSource.extractYear("1999-10-15"));
        assertEquals("2023", MediaRemoteDataSource.extractYear("2023-01-01"));
    }

    @Test
    public void testExtractYear_InvalidOrEmptyDate() {
        // Handles null, empty strings, and broken strings gracefully
        assertEquals("", MediaRemoteDataSource.extractYear(null));
        assertEquals("", MediaRemoteDataSource.extractYear(""));
        assertEquals("", MediaRemoteDataSource.extractYear("99")); // Too short
    }

    // --- JSON Parsing Tests ---

    @Test
    public void testParseSearchResponse_MixedMedia() throws Exception {
        // A minimal mock JSON string mimicking TMDB's /search/multi response
        String mockJson = "{ \"results\": [ " +
                "  { \"media_type\": \"movie\", \"id\": 550, \"title\": \"Fight Club\", \"release_date\": \"1999-10-15\", \"vote_average\": 8.4 }, " +
                "  { \"media_type\": \"tv\", \"id\": 1399, \"name\": \"Game of Thrones\", \"first_air_date\": \"2011-04-17\", \"vote_average\": 8.4 }, " +
                "  { \"media_type\": \"person\", \"id\": 287, \"name\": \"Brad Pitt\" } " + // Should be ignored
                "] }";

        MediaRemoteDataSource ds = new MediaRemoteDataSource();
        List<MediaDto> results = ds.parseSearchResponse(mockJson);

        // Should be 2 items, not 3 (person is filtered out)
        assertEquals(2, results.size());

        // Test movie normalization
        MediaDto movie = results.get(0);
        assertEquals("movie", movie.mediaType);
        assertEquals(550, movie.id);
        assertEquals("Fight Club", movie.title);
        assertEquals("1999", movie.year);

        // Test TV normalization
        MediaDto tv = results.get(1);
        assertEquals("tv", tv.mediaType);
        assertEquals(1399, tv.id);
        assertEquals("Game of Thrones", tv.title); // "name" should map to "title"
        assertEquals("2011", tv.year); // "first_air_date" should map to "year"
    }

    @Test
    public void testParseSearchResponse_EmptyResults() throws Exception {
        String mockJson = "{ \"results\": [] }";

        MediaRemoteDataSource ds = new MediaRemoteDataSource();
        List<MediaDto> results = ds.parseSearchResponse(mockJson);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}