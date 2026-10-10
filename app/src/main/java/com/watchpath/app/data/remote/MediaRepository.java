package com.watchpath.app.data.remote;

import com.watchpath.app.data.local.MediaDao;
import com.watchpath.app.data.local.MediaEntity;

import java.util.List;

/**
 * Single access point for local data. UI -> Repository -> DAO -> Room.
 *
 * Rule for this project: no Activity touches MediaDao directly.
 */
public class MediaRepository {

    private final MediaDao dao;

    public MediaRepository(MediaDao dao) {
        this.dao = dao;
    }

    public void addOrUpdate(MediaEntity item) {
        dao.upsert(item);
    }

    public void remove(int id, String type) {
        dao.delete(id, type);
    }

    public MediaEntity find(int id, String type) {
        return dao.find(id, type);
    }

    public List<MediaEntity> getAll() {
        return dao.getAll();
    }

    public List<MediaEntity> getByStatus(String status) {
        return dao.getByStatus(status);
    }
}