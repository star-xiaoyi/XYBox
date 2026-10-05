package com.fongmi.android.tv.utils;

import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import android.util.Base64;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.SyncProfile;
import com.github.catvod.utils.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Local identities only. Existing unscoped preferences belong to the default identity. */
public final class LocalProfile {
    public static final String DEFAULT = "default";
    private static SharedPreferences prefs() { return App.get().getSharedPreferences("local_profiles", 0); }
    public static String id() { return prefs().getString("active", DEFAULT); }
    public static String key(String key) { return keyFor(id(), key); }
    public static String keyFor(String id, String key) { return DEFAULT.equals(id) ? key : "profile_" + id + "_" + key; }
    public static String name() { return prefs().getString("name_" + id(), "默认账号"); }
    public static String signature() { return prefs().getString("signature_" + id(), "热爱电影，也热爱生活。"); }
    public static void setSignature(String value) { prefs().edit().putString("signature_" + id(), value).apply(); }
    public static String avatar() { return prefs().getString("avatar_" + id(), ""); }
    public static void rename(String name) {
        String profile = id();
        synchronized (LocalProfile.class) {
            if (name(profile).equals(name)) return;
            long time = Math.max(System.currentTimeMillis(), prefs().getLong("name_time_" + profile, 1) + 1);
            prefs().edit().putString("name_" + profile, name).putLong("name_time_" + profile, time).commit();
        }
        WebDAVSyncManager.get().requestSync(profile);
    }
    public static void setAvatar(String profile, String path) {
        synchronized (LocalProfile.class) {
            if (prefs().getString("avatar_" + profile, "").equals(path)) return;
            long time = Math.max(System.currentTimeMillis(), prefs().getLong("avatar_time_" + profile, 0) + 1);
            prefs().edit().putString("avatar_" + profile, path).putLong("avatar_time_" + profile, time).commit();
        }
        WebDAVSyncManager.get().requestSync(profile);
    }

    public static synchronized SyncProfile capture(String profile) throws IOException {
        SyncProfile result = new SyncProfile();
        result.nickname = name(profile);
        // Values predating cloud profiles seed an empty cloud, but cannot replace established cloud data.
        result.nicknameUpdatedAt = prefs().getLong("name_time_" + profile, prefs().contains("name_" + profile) ? 1 : 0);
        String path = prefs().getString("avatar_" + profile, "");
        if (path.isEmpty()) result.avatarUpdatedAt = prefs().getLong("avatar_time_" + profile, 0);
        else if (new File(path).isFile()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (FileInputStream input = new FileInputStream(path)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > SyncProfile.MAX_AVATAR_BYTES) throw new IOException("Avatar exceeds size limit");
                    output.write(buffer, 0, count);
                }
            }
            result.avatarBase64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            result.avatarUpdatedAt = prefs().getLong("avatar_time_" + profile, 1);
        }
        return result;
    }

    /** Recheck current fields after the network transaction so an in-flight edit cannot be rolled back. */
    public static synchronized boolean applySynced(String profile, SyncProfile incoming) throws IOException {
        SyncProfile current = capture(profile), selected = SyncProfile.merge(incoming, current);
        SharedPreferences.Editor editor = prefs().edit(); boolean changed = false;
        if (selected.hasNickname()) {
            changed |= !selected.nickname.equals(current.nickname);
            editor.putString("name_" + profile, selected.nickname).putLong("name_time_" + profile, selected.nicknameUpdatedAt);
        }
        if (selected.hasAvatar()) {
            if (!selected.avatarBase64.equals(current.avatarBase64)) {
                String path = "";
                if (!selected.avatarBase64.isEmpty()) {
                    byte[] bytes;
                    try { bytes = Base64.decode(selected.avatarBase64, Base64.NO_WRAP); }
                    catch (IllegalArgumentException error) { throw new IOException("Invalid cloud avatar", error); }
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    if (bytes.length > SyncProfile.MAX_AVATAR_BYTES || bounds.outWidth <= 0 || bounds.outHeight <= 0
                            || bounds.outWidth > 2048 || bounds.outHeight > 2048) throw new IOException("Invalid cloud avatar dimensions");
                    File directory = new File(App.get().getFilesDir(), "avatars");
                    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create avatar directory");
                    File image = new File(directory, profile + "-sync-" + UUID.randomUUID() + ".jpg");
                    AtomicFile file = new AtomicFile(image); FileOutputStream output = null;
                    try { output = file.startWrite(); output.write(bytes); file.finishWrite(output); }
                    catch (IOException error) { if (output != null) file.failWrite(output); throw error; }
                    path = image.getAbsolutePath();
                }
                editor.putString("avatar_" + profile, path); changed = true;
            }
            editor.putLong("avatar_time_" + profile, selected.avatarUpdatedAt);
        }
        if (!editor.commit()) throw new IOException("Cannot save synced profile");
        if (changed) Logger.i("WebDAVProfile: action=applied nickname=" + selected.hasNickname() + " avatar=" + selected.hasAvatar());
        return changed;
    }
    public static List<String> ids() {
        List<String> list = new ArrayList<>(); list.add(DEFAULT);
        try { JSONArray array = new JSONArray(prefs().getString("ids", "[]"));
            for (int i=0;i<array.length();i++) list.add(array.getString(i));
        } catch (Exception ignored) { }
        return list;
    }
    public static String name(String id) { return prefs().getString("name_" + id, "默认账号"); }
    public static String create(String name) {
        String id = UUID.randomUUID().toString();
        List<String> list = ids(); list.remove(DEFAULT); list.add(id);
        prefs().edit().putString("ids", new JSONArray(list).toString()).putString("name_" + id, name).commit();
        SourcePreferences.initialize(id);
        return id;
    }
    static void activate(String id) {
        if (!ids().contains(id)) throw new IllegalArgumentException("账号不存在");
        prefs().edit().putString("active", id).commit();
    }
    private LocalProfile() { }
}
