package com.fongmi.android.tv.bean;

/** Portable account presentation. Local ids, file paths and credentials never enter this record. */
public final class SyncProfile {
    public static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;
    public String nickname = "";
    public long nicknameUpdatedAt;
    public String avatarBase64 = "";
    public long avatarUpdatedAt;

    public boolean hasNickname() {
        return nickname != null && !nickname.trim().isEmpty() && nickname.length() <= 24 && nicknameUpdatedAt > 0;
    }

    public boolean hasAvatar() {
        return avatarUpdatedAt > 0 && avatarBase64 != null
                && avatarBase64.length() <= ((MAX_AVATAR_BYTES + 2) / 3) * 4
                && avatarBase64.length() % 4 == 0 && avatarBase64.matches("[A-Za-z0-9+/]*={0,2}");
    }

    /** Merge each field independently; an established cloud value wins equal migration timestamps. */
    public static SyncProfile merge(SyncProfile remote, SyncProfile local) {
        if (remote == null) remote = new SyncProfile();
        if (local == null) local = new SyncProfile();
        SyncProfile merged = new SyncProfile();
        SyncProfile name = local.hasNickname() && (!remote.hasNickname() || local.nicknameUpdatedAt > remote.nicknameUpdatedAt) ? local : remote;
        if (name.hasNickname()) { merged.nickname = name.nickname; merged.nicknameUpdatedAt = name.nicknameUpdatedAt; }
        SyncProfile image = local.hasAvatar() && (!remote.hasAvatar() || local.avatarUpdatedAt > remote.avatarUpdatedAt) ? local : remote;
        if (image.hasAvatar()) { merged.avatarBase64 = image.avatarBase64; merged.avatarUpdatedAt = image.avatarUpdatedAt; }
        return merged;
    }
}
