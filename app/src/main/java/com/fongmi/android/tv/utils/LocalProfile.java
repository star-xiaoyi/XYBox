package com.fongmi.android.tv.utils;

import android.content.SharedPreferences;
import com.fongmi.android.tv.App;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    public static void rename(String name) { prefs().edit().putString("name_" + id(), name).apply(); }
    public static void setAvatar(String profile, String path) { prefs().edit().putString("avatar_" + profile, path).apply(); }
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
        return id;
    }
    static void activate(String id) {
        if (!ids().contains(id)) throw new IllegalArgumentException("账号不存在");
        prefs().edit().putString("active", id).commit();
    }
    private LocalProfile() { }
}
