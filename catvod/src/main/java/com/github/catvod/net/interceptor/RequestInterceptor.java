package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import com.github.catvod.net.OkCookieJar;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class RequestInterceptor implements Interceptor {

    private final ConcurrentHashMap<String, String> authMap;

    public RequestInterceptor() {
        authMap = new ConcurrentHashMap<>();
    }

    public void clear() {
        authMap.clear();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        
        // AI广告检测
        if (AIAdInterceptor.isEnabled()) {
            String url = request.url().toString();
            if (AIAdInterceptor.isAd(url)) {
                // 返回空响应模拟广告请求被拦截
                return new Response.Builder()
                        .request(request)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(204)
                        .message("Blocked by AI Ad Detector")
                        .body(ResponseBody.create("", null))
                        .build();
            }
        }
        
        Request.Builder builder = request.newBuilder();
        HttpUrl url = request.url();
        checkAuth(url, builder);
        OkCookieJar.sync(url, request);
        return chain.proceed(builder.build());
    }

    private void checkAuth(HttpUrl url, Request.Builder builder) {
        String auth = url.queryParameter("auth");
        if (auth != null) authMap.put(url.host(), auth);
        if (authMap.containsKey(url.host()) && auth == null) builder.url(url.newBuilder().addQueryParameter("auth", authMap.get(url.host())).build());
    }
}
