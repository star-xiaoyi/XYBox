package com.fongmi.android.tv.search;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 搜索结果页点进详情页时，把整组片源交过去。
 * <p>
 * 不塞进 Intent：部分站点的搜索结果自带全部剧集地址，几十个源一起序列化会撞上
 * Binder 的 1MB 上限。进程被系统回收后这里是空的，详情页会退回按片名重新搜。
 */
public final class GroupCache {

    private static final Map<String, VodGroup> GROUPS = new ConcurrentHashMap<>();

    private GroupCache() {
    }

    public static String put(VodGroup group) {
        String token = UUID.randomUUID().toString();
        GROUPS.put(token, group);
        return token;
    }

    public static VodGroup get(String token) {
        return token == null ? null : GROUPS.get(token);
    }

    public static void remove(String token) {
        if (token != null) GROUPS.remove(token);
    }
}
