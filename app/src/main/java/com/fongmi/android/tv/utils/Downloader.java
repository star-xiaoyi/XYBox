package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.util.Log;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.service.DownloadService;

import java.util.Map;
import java.util.UUID;

public class Downloader {

    private Result result;
    private Activity activity;
    private String title;
    private String image;

    private static class Loader {
        static volatile Downloader INSTANCE = new Downloader();
    }

    public static Downloader get() {
        return Loader.INSTANCE;
    }

    public Downloader title(String title) {
        this.title = title;
        return this;
    }
    public Downloader image(String image) {
        this.image = image;
        return this;
    }

    public Downloader result(Result result) {
        this.result = result;
        return this;
    }

    public void start(Activity activity) {
        this.activity = activity;
        if (result.hasMsg()) {
            Notify.show(result.getMsg());
        }  else {
            download();
        }
    }

    private void download() {
        try {
            
            // 创建下载记录
            String downloadId = UUID.randomUUID().toString();
            String url = result.getRealUrl();
            String headers = convertHeadersToString(result.getHeaders());
            
            
            if (url == null || url.isEmpty()) {
                Notify.show("下载失败: 视频地址为空");
                return;
            }
            
            // 检测M3U8文件类型
            boolean isM3U8 = url.toLowerCase().contains(".m3u8") || 
                           url.toLowerCase().contains("m3u8") ||
                           url.toLowerCase().contains("playlist");
            
            
            if (isM3U8) {
                Log.i("Downloader", "检测到M3U8文件，使用专业下载引擎: " + url);
                Notify.show("检测到M3U8流媒体，启动专业下载引擎");
            } else {
                Log.i("Downloader", "普通文件下载: " + url);
            }
            
            // 创建下载对象
            Download download = new Download(downloadId, image, title, url, headers);
            
            // 保存到数据库
            try {
                download.save();
            } catch (Exception e) {
                Notify.show("下载失败: 数据库错误");
                return;
            }
            
            // 启动内置下载服务
            try {
                DownloadService.startDownload(download);
                Notify.show("开始下载: " + title);
            } catch (Exception e) {
                Notify.show("下载失败: 服务启动错误");
            }
            
        } catch (Exception e) {
            Notify.show("下载失败: " + e.getMessage());
        }
    }

    private String convertHeadersToString(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return sb.toString();
    }
}
