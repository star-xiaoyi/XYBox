package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.utils.Logger;

import java.io.InputStream;
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
            Logger.i("ProxyLocal: do=" + params.get("do") + ", status=" + rs[0] + ", mime=" + rs[1]);
            NanoHTTPD.Response response = NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.lookup((Integer) rs[0]), (String) rs[1], (InputStream) rs[2]);
            if (rs.length > 3 && rs[3] != null) for (Map.Entry<String, String> entry : ((Map<String, String>) rs[3]).entrySet()) response.addHeader(entry.getKey(), entry.getValue());
            return response;
        } catch (Throwable e) {
            Logger.e("ProxyLocal", "Local proxy response failed", e);
            return Nano.error(e.getMessage());
        }
    }
}
