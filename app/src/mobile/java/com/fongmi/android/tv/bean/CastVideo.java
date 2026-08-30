package com.fongmi.android.tv.bean;

import androidx.media3.common.C;

import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.util.Collections;
import java.util.Map;

public class CastVideo {

    private final long position;
    private final String name;
    private final String url;
    /** 播放器解析出来的 Referer/UA 等。投屏地址交给别人去拉，这些头必须跟着走。 */
    private final Map<String, String> headers;

    public static CastVideo get(String name, String url) {
        return new CastVideo(name, url, C.TIME_UNSET, null);
    }

    public static CastVideo get(String name, String url, long position) {
        return new CastVideo(name, url, position, null);
    }

    public static CastVideo get(String name, String url, long position, Map<String, String> headers) {
        return new CastVideo(name, url, position, headers);
    }

    private CastVideo(String name, String url, long position, Map<String, String> headers) {
        if (url.startsWith("file")) url = Server.get().getAddress() + "/" + url.replace(Path.rootPath(), "").replace("://", "");
        if (url.contains("127.0.0.1")) url = url.replace("127.0.0.1", Util.getIp());
        this.position = position;
        this.name = name;
        this.url = url;
        this.headers = headers == null ? Collections.emptyMap() : headers;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /** 只有 http(s) 且带头的地址才需要中转，本地文件服务的地址本来就没有鉴权问题。 */
    public boolean needRelay() {
        return !headers.isEmpty() && url.startsWith("http");
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public long getPosition() {
        return position;
    }
}