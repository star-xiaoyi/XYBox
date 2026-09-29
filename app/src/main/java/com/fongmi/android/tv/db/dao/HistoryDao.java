package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import com.fongmi.android.tv.utils.LocalProfile;
import androidx.room.Query;

import com.fongmi.android.tv.bean.History;

import java.util.List;

@Dao
public abstract class HistoryDao extends BaseDao<History> {

    @Query("SELECT * FROM History WHERE accountId = :accountId")
    public abstract List<History> findAllForAccount(String accountId);

    public List<History> findAll() { return findAllForAccount(LocalProfile.id()); }

    @Query("SELECT * FROM History WHERE accountId = :accountId AND cid = :cid AND createTime >= :createTime ORDER BY createTime DESC")
    public abstract List<History> findForAccount(String accountId, int cid, long createTime);

    public List<History> find(int cid, long createTime) { return findForAccount(LocalProfile.id(), cid, createTime); }

    @Query("SELECT * FROM History WHERE accountId = :accountId AND createTime >= :createTime ORDER BY createTime DESC")
    public abstract List<History> findAllRecentForAccount(String accountId, long createTime);

    public List<History> findAllRecent(long createTime) { return findAllRecentForAccount(LocalProfile.id(), createTime); }

    @Query("SELECT * FROM History WHERE accountId = :accountId AND cid = :cid AND `key` = :key")
    public abstract History findForAccount(String accountId, int cid, String key);

    public History find(int cid, String key) { return findForAccount(LocalProfile.id(), cid, key); }

    @Query("SELECT * FROM History WHERE accountId = :accountId AND `key` = :key")
    public abstract History findByKeyForAccount(String accountId, String key);

    public History findByKey(String key) { return findByKeyForAccount(LocalProfile.id(), key); }

    @Query("SELECT * FROM History WHERE accountId = :accountId AND cid = :cid AND vodName = :vodName ORDER BY createTime DESC")
    public abstract List<History> findByNameForAccount(String accountId, int cid, String vodName);

    public List<History> findByName(int cid, String vodName) { return findByNameForAccount(LocalProfile.id(), cid, vodName); }

    @Query("DELETE FROM History WHERE accountId = :accountId AND cid = :cid AND `key` = :key")
    public abstract void deleteForAccount(String accountId, int cid, String key);

    public void delete(int cid, String key) { deleteForAccount(LocalProfile.id(), cid, key); }

    @Query("DELETE FROM History WHERE accountId = :accountId AND `key` = :key")
    public abstract void deleteByKeyForAccount(String accountId, String key);

    public void deleteByKey(String key) { deleteByKeyForAccount(LocalProfile.id(), key); }

    /** 清掉早期离线播放留下的伪站源记录，它们会把同名的在线记录挤掉。 */
    @Query("DELETE FROM History WHERE accountId = :accountId AND `key` LIKE 'download_agent@@@%'")
    public abstract void deleteOfflineForAccount(String accountId);

    public void deleteOffline() { deleteOfflineForAccount(LocalProfile.id()); }

    @Query("DELETE FROM History WHERE accountId = :accountId AND cid = :cid")
    public abstract void deleteForAccount(String accountId, int cid);

    public void delete(int cid) { deleteForAccount(LocalProfile.id(), cid); }

    @Query("DELETE FROM History WHERE accountId = :accountId")
    public abstract void deleteForAccount(String accountId);

    public void delete() { deleteForAccount(LocalProfile.id()); }
}
