package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.utils.Logger;

import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class Proxy implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String url) {
        return url.startsWith("/proxy");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            params.putAll(session.getHeaders());
            params.putAll(files);
            Object[] rs = BaseLoader.get().proxyLocal(params);
            if (rs[0] instanceof NanoHTTPD.Response) {
                Logger.i("ProxyLocal: do=" + params.get("do") + ", response=direct");
                return (NanoHTTPD.Response) rs[0];
            }
            String action = params.get("do");
            InputStream stream = (InputStream) rs[2];
            if ("m3u8".equalsIgnoreCase(action)) stream = inspectManifest(stream, params.get("range"));
            Logger.i("ProxyLocal: do=" + action + ", status=" + rs[0] + ", mime=" + rs[1] + ", range=" + params.get("range"));
            NanoHTTPD.Response response = NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.lookup((Integer) rs[0]), (String) rs[1], stream);
            if (rs.length > 3 && rs[3] != null) for (Map.Entry<String, String> entry : ((Map<String, String>) rs[3]).entrySet()) response.addHeader(entry.getKey(), entry.getValue());
            return response;
        } catch (Throwable e) {
            Logger.e("ProxyLocal", "Local proxy response failed", e);
            return Nano.error(e.getMessage());
        }
    }

    private InputStream inspectManifest(InputStream source, String range) throws Exception {
        PushbackInputStream input = new PushbackInputStream(source, 256);
        byte[] preview = new byte[256];
        int count = 0;
        while (count < preview.length) {
            int read = input.read(preview, count, preview.length - count);
            if (read <= 0) break;
            count += read;
            boolean haveLine = false;
            for (int i = 0; i < count; i++) {
                if (preview[i] == '\n') {
                    haveLine = true;
                    break;
                }
            }
            if (haveLine) break;
        }
        if (count > 0) input.unread(preview, 0, count);
        String text = new String(preview, 0, count, StandardCharsets.UTF_8)
                .replace("\uFEFF", "").trim();
        String firstLine = text.isEmpty() ? "" : text.split("\\R", 2)[0];
        if (firstLine.length() > 80) firstLine = firstLine.substring(0, 80);
        String kind = text.startsWith("#EXTM3U") ? "hls"
                : text.isEmpty() ? "empty"
                : text.startsWith("<") ? "html_or_xml"
                : text.startsWith("{") || text.startsWith("[") ? "json"
                : "other";
        Logger.i("ProxyLocal: m3u8Preview kind=" + kind + ", bytes=" + count
                + ", range=" + range + ", firstLine=" + firstLine);
        return input;
    }
}
