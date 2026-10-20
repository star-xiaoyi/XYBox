package com.fongmi.android.tv.search;

import java.util.Locale;

/**
 * 网盘类型识别与“已扫码登录”记忆。登录本身在插件内部完成，token 存在插件自己的目录，
 * App 碰不到；这里只按网盘类型（夸克/UC/阿里…）记住用户扫过码，用来标注面板和决定要不要提示。
 */
public final class CloudDrive {
    private CloudDrive() { }

    /** UI-thread request state: a timed-out/cancelled login cannot commit a late source switch. */
    public static final class Login {
        private int generation;
        private boolean pending;
        private long deadline;
        public int begin(long now, long timeout) {
            pending = true; deadline = now + timeout; return ++generation;
        }
        public boolean pending() { return pending; }
        public boolean accepts(int request, long now) { return pending && request == generation && now < deadline; }
        public boolean complete(int request, long now) {
            if (!accepts(request, now)) return false;
            pending = false; return true;
        }
        public boolean expire(int request, long now) {
            if (!pending || request != generation || now < deadline) return false;
            cancel(); return true;
        }
        public void cancel() { pending = false; generation++; }
    }

    public static String type(String... texts) {
        StringBuilder all = new StringBuilder();
        for (String text : texts) if (text != null) all.append(text.toLowerCase(Locale.ROOT)).append('\n');
        String value = all.toString();
        if (value.contains("quark") || value.contains("夸克")) return "quark";
        if (value.contains("drive.uc") || value.contains("uc浏览器") || value.contains("uc盘") || value.contains("uc云")
                || value.contains("ucdrive") || value.contains("优汐")) return "uc";
        if (value.contains("alipan") || value.contains("aliyun") || value.contains("阿里")) return "ali";
        if (value.contains("baidu") || value.contains("百度")) return "baidu";
        if (value.contains("115.com") || value.contains("115盘") || value.contains("115网盘")) return "115";
        if (value.contains("189.cn") || value.contains("天翼")) return "189";
        if (value.contains("xunlei") || value.contains("迅雷")) return "xunlei";
        return "";
    }

    public static String label(String type) {
        switch (type) {
            case "quark": return "夸克网盘";
            case "uc": return "UC 网盘";
            case "ali": return "阿里云盘";
            case "baidu": return "百度网盘";
            case "115": return "115 网盘";
            case "189": return "天翼云盘";
            case "xunlei": return "迅雷云盘";
            default: return "网盘";
        }
    }

    public static boolean loggedIn(String type) {
        return !type.isEmpty() && com.fongmi.android.tv.utils.SourcePreferences.isDriveLoggedIn(type);
    }

    public static void remember(String type, boolean loggedIn) {
        if (!type.isEmpty()) com.fongmi.android.tv.utils.SourcePreferences.setDriveLoggedIn(type, loggedIn);
    }
}
