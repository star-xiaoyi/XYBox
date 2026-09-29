package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import com.fongmi.android.tv.utils.LocalProfile;
import androidx.room.Query;

import com.fongmi.android.tv.bean.Keep;

import java.util.List;

@Dao
public abstract class KeepDao extends BaseDao<Keep> {

    @Query("SELECT * FROM Keep WHERE accountId = :accountId")
    public abstract List<Keep> findAllForAccount(String accountId);

    public List<Keep> findAll() { return findAllForAccount(LocalProfile.id()); }

    @Query("SELECT * FROM Keep WHERE accountId = :accountId AND type = 0 ORDER BY createTime DESC")
    public abstract List<Keep> getVodForAccount(String accountId);

    public List<Keep> getVod() { return getVodForAccount(LocalProfile.id()); }

    @Query("SELECT * FROM Keep WHERE accountId = :accountId AND type = 1 ORDER BY createTime DESC")
    public abstract List<Keep> getLiveForAccount(String accountId);

    public List<Keep> getLive() { return getLiveForAccount(LocalProfile.id()); }

    @Query("SELECT * FROM Keep WHERE accountId = :accountId AND type = 0 AND cid = :cid AND `key` = :key")
    public abstract Keep findForAccount(String accountId, int cid, String key);

    public Keep find(int cid, String key) { return findForAccount(LocalProfile.id(), cid, key); }

    @Query("SELECT * FROM Keep WHERE accountId = :accountId AND type = 1 AND `key` = :key")
    public abstract Keep findForAccount(String accountId, String key);

    public Keep find(String key) { return findForAccount(LocalProfile.id(), key); }

    @Query("DELETE FROM Keep WHERE accountId = :accountId AND type = 1 AND `key` = :key")
    public abstract void deleteForAccount(String accountId, String key);

    public void delete(String key) { deleteForAccount(LocalProfile.id(), key); }

    @Query("DELETE FROM Keep WHERE accountId = :accountId AND type = 0 AND cid = :cid AND `key` = :key")
    public abstract void deleteForAccount(String accountId, int cid, String key);

    public void delete(int cid, String key) { deleteForAccount(LocalProfile.id(), cid, key); }

    @Query("DELETE FROM Keep WHERE accountId = :accountId AND `key` = :key")
    public abstract void deleteByKeyForAccount(String accountId, String key);

    public void deleteByKey(String key) { deleteByKeyForAccount(LocalProfile.id(), key); }

    @Query("DELETE FROM Keep WHERE accountId = :accountId AND type = 0 AND cid = :cid")
    public abstract void deleteForAccount(String accountId, int cid);

    public void delete(int cid) { deleteForAccount(LocalProfile.id(), cid); }

    @Query("DELETE FROM Keep WHERE accountId = :accountId AND type = 0")
    public abstract void deleteForAccount(String accountId);

    public void delete() { deleteForAccount(LocalProfile.id()); }
}
