package com.fongmi.android.tv.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class Migrations {

    /** 0 表示自动；正数保存该影片最后一次手动选择的清晰度档位。 */
    public static final Migration MIGRATION_40_41 = new Migration(40, 41) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN qualityHeight INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_39_40 = new Migration(39, 40) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN filmId TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN sourceKeys TEXT DEFAULT NULL");
        }
    };

    /** Keep every old record; grouping and resume recovery happen without rewriting legacy progress. */
    public static final Migration MIGRATION_38_39 = new Migration(38, 39) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN vodYear TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN vodType TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN sharedProgress INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE Track");
            database.execSQL("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, `track` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `selected` INTEGER NOT NULL, `adaptive` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)");
        }
    };

    public static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE History_Backup (`key` TEXT NOT NULL, `vodPic` TEXT, `vodName` TEXT, `vodFlag` TEXT, `vodRemarks` TEXT, `episodeUrl` TEXT, `revSort` INTEGER NOT NULL, `revPlay` INTEGER NOT NULL, `createTime` INTEGER NOT NULL, `opening` INTEGER NOT NULL, `ending` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `speed` REAL NOT NULL, `scale` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            database.execSQL("INSERT INTO History_Backup SELECT `key`, `vodPic`, `vodName`, `vodFlag`, `vodRemarks`, `episodeUrl`, `revSort`, `revPlay`, `createTime`, `opening`, `ending`, `position`, `duration`, `speed`, `scale`, `cid` FROM History");
            database.execSQL("DROP TABLE History");
            database.execSQL("ALTER TABLE History_Backup RENAME to History");
        }
    };

    public static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Live ADD COLUMN keep TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_33_34 = new Migration(33, 34) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS Download (`id` TEXT NOT NULL PRIMARY KEY, `vodPic` TEXT, `vodName` TEXT, `vodId` TEXT, `url` TEXT, `header` TEXT, `createTime` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `status` TEXT, `duration` INTEGER NOT NULL, `speed` INTEGER NOT NULL)");
        }
    };

    /**
     * 离线缓存重写。旧表结构随旧实现一起作废（旧下载从未跑通，不存在需要保留的数据），
     * 直接重建成新结构；34 版本的表可能来自 Room 建表也可能来自上面的迁移，两种都直接丢弃。
     */
    public static final Migration MIGRATION_34_35 = new Migration(34, 35) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS Download");
            database.execSQL("CREATE TABLE IF NOT EXISTS `Download` (`id` TEXT NOT NULL, `vodKey` TEXT, `siteKey` TEXT, `vodId` TEXT, `vodName` TEXT, `vodPic` TEXT, `flag` TEXT, `episodeName` TEXT, `episodeUrl` TEXT, `localPath` TEXT, `errorMsg` TEXT, `status` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `speed` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `doneBytes` INTEGER NOT NULL, `totalSeg` INTEGER NOT NULL, `doneSeg` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `createTime` INTEGER NOT NULL, `updateTime` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        }
    };

    /** 缓存里补上简介等元信息，离线详情页才不是一片空白。 */
    public static final Migration MIGRATION_35_36 = new Migration(35, 36) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Download ADD COLUMN vodContent TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE Download ADD COLUMN vodYear TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE Download ADD COLUMN vodArea TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE Download ADD COLUMN vodType TEXT DEFAULT NULL");
        }
    };
    /** Existing history keeps its progress; episode metadata is filled on the next playback. */
    public static final Migration MIGRATION_37_38 = new Migration(37, 38) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN episodeCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE History ADD COLUMN episodeNumber INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** Preserve all beta14 records under the default profile. */
    public static final Migration MIGRATION_36_37 = new Migration(36, 37) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Keep_Profiles` (`accountId` TEXT NOT NULL, `key` TEXT NOT NULL, `siteName` TEXT, `vodName` TEXT, `vodPic` TEXT, `createTime` INTEGER NOT NULL, `type` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`accountId`, `key`))");
            database.execSQL("INSERT INTO `Keep_Profiles` (`accountId`, `key`, `siteName`, `vodName`, `vodPic`, `createTime`, `type`, `cid`) SELECT 'default', `key`, `siteName`, `vodName`, `vodPic`, `createTime`, `type`, `cid` FROM `Keep`");
            database.execSQL("DROP TABLE `Keep`");
            database.execSQL("ALTER TABLE `Keep_Profiles` RENAME TO `Keep`");
            database.execSQL("CREATE TABLE IF NOT EXISTS `History_Profiles` (`accountId` TEXT NOT NULL, `key` TEXT NOT NULL, `vodPic` TEXT, `vodName` TEXT, `vodFlag` TEXT, `vodRemarks` TEXT, `episodeUrl` TEXT, `revSort` INTEGER NOT NULL, `revPlay` INTEGER NOT NULL, `createTime` INTEGER NOT NULL, `opening` INTEGER NOT NULL, `ending` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `speed` REAL NOT NULL, `scale` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`accountId`, `key`))");
            database.execSQL("INSERT INTO `History_Profiles` (`accountId`, `key`, `vodPic`, `vodName`, `vodFlag`, `vodRemarks`, `episodeUrl`, `revSort`, `revPlay`, `createTime`, `opening`, `ending`, `position`, `duration`, `speed`, `scale`, `cid`) SELECT 'default', `key`, `vodPic`, `vodName`, `vodFlag`, `vodRemarks`, `episodeUrl`, `revSort`, `revPlay`, `createTime`, `opening`, `ending`, `position`, `duration`, `speed`, `scale`, `cid` FROM `History`");
            database.execSQL("DROP TABLE `History`");
            database.execSQL("ALTER TABLE `History_Profiles` RENAME TO `History`");
        }
    };
}
