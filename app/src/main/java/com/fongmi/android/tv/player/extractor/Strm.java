package com.fongmi.android.tv.player.extractor;

import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.File;

import okhttp3.Response;

public class Strm implements Source.Extractor {

    @Override
    public boolean match(String scheme, String host) {
        // Strm文件通常是file://或http://协议
        return false; // 由于我们的接口不支持基于文件扩展名的匹配，暂时禁用
    }

    @Override
    public String fetch(String url) throws Exception {
        // 检查是否是.strm文件
        if (!url.toLowerCase().endsWith(".strm")) {
            return url;
        }
        
        if (url.startsWith("http")) return http(url);
        if (url.startsWith("file")) url = url.substring(7);
        return Path.read(new File(url)).split("\\R", 2)[0];
    }

    private String http(String url) throws Exception {
        try (Response res = OkHttp.newCall(OkHttp.noRedirect(30000), url).execute()) {
            String content = res.header(HttpHeaders.CONTENT_DISPOSITION, "");
            boolean text = content.contains(".strm") || content.contains(".txt");
            return text ? res.body().string().split("\\R", 2)[0] : url;
        }
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
