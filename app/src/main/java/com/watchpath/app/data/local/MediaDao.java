package com.watchpath.app.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Data Access Object for the "media" table.
 *
 * Room generates the implementation at compile time. All methods here are
 * blocking I/O - always call from a background thread.
 */
@Dao
public interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MediaEntity item);

    @Query("DELETE FROM media WHERE id = :id AND type = :type")
    void delete(int id, String type);

    @Query("SELECT * FROM media WHERE id = :id AND type = :type LIMIT 1")
    MediaEntity find(int id, String type);

    @Query("SELECT * FROM media ORDER BY addedAt DESC")
    List<MediaEntity> getAll();

    @Query("SELECT * FROM media WHERE status = :status ORDER BY addedAt DESC")
    List<MediaEntity> getByStatus(String status);

    @Query("SELECT COUNT(*) FROM media WHERE status = :status")
    int countByStatus(String status);
}