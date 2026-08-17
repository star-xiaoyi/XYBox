package com.github.catvod.utils;
import com.github.catvod.utils.Logger;

import android.os.SystemClock;

import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Github {

    public static final String URL = "https://raw.githubusercontent.com/Tosencen/XMBOX-Release/main";
    
    // 国内镜像地址 - 使用Gitee作为镜像
    public static final String CN_URL = "https://gitee.com/ochenoktochen/XMBOX-Release/raw/main";
    
    // 存储测速结果
    private static Boolean useCnMirror = null;
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 24 * 60 * 60 * 1000; // 24小时

    private static String getUrl(String path, String name) {
        return URL + "/" + path + "/" + name;
    }
    
    private static String getCnUrl(String path, String name) {
        return CN_URL + "/" + path + "/" + name;
    }


    public static String getJson(boolean dev, String name) {
        if (useCnMirror()) {
            return getCnUrl("apk/" + (dev ? "dev" : "release"), name + ".json");
        } else {
            return getUrl("apk/" + (dev ? "dev" : "release"), name + ".json");
        }
    }

    public static String getApk(boolean dev, String name) {
        if (useCnMirror()) {
            return getCnUrl("apk/" + (dev ? "dev" : "release"), name + ".apk");
        } else {
            return getUrl("apk/" + (dev ? "dev" : "release"), name + ".apk");
        }
    }
    
    /**
     * 将GitHub Release下载URL转换为jsDelivr CDN URL
     * 例如: https://github.com/Tosencen/XMBOX/releases/download/v3.1.0/mobile-arm64_v8a-v3.1.0.apk
     * 转换为: https://cdn.jsdelivr.net/gh/Tosencen/XMBOX@v3.1.0/mobile-arm64_v8a-v3.1.0.apk
     * 
     * @param githubUrl GitHub Release下载URL
     * @param tagName Release标签名（如 "v3.1.0"）
     * @param fileName 文件名
     * @return jsDelivr CDN URL
     */
    public static String convertToJsDelivrUrl(String githubUrl, String tagName, String fileName) {
        try {
            // 尝试从GitHub URL中提取信息
            // 格式: https://github.com/{owner}/{repo}/releases/download/{tag}/{file}
            if (githubUrl.contains("/releases/download/")) {
                String[] parts = githubUrl.split("/releases/download/");
                if (parts.length == 2) {
                    String basePath = parts[0]; // https://github.com/Tosencen/XMBOX
                    String[] baseParts = basePath.split("/");
                    if (baseParts.length >= 2) {
                        String owner = baseParts[baseParts.length - 2];
                        String repo = baseParts[baseParts.length - 1];
                        // 使用jsDelivr CDN格式
                        String jsDelivrUrl = "https://cdn.jsdelivr.net/gh/" + owner + "/" + repo + "@" + tagName + "/" + fileName;
                        Logger.d("Github: URL转换: " + githubUrl + " -> " + jsDelivrUrl);
                        return jsDelivrUrl;
                    }
                }
            }
            // 如果无法匹配，使用默认仓库信息构建
            String jsDelivrUrl = "https://cdn.jsdelivr.net/gh/Tosencen/XMBOX@" + tagName + "/" + fileName;
            Logger.d("Github: 使用默认格式构建URL: " + jsDelivrUrl);
            return jsDelivrUrl;
        } catch (Exception e) {
            Logger.e("Github: URL转换失败: " + e.getMessage());
            // 转换失败时返回原URL
            return githubUrl;
        }
    }
    
    /**
     * 将GitHub raw URL转换为jsDelivr CDN URL
     * 例如: https://raw.githubusercontent.com/Tosencen/XMBOX-Release/main/apk/release/mobile-arm64_v8a-v3.1.0.apk
     * 转换为: https://cdn.jsdelivr.net/gh/Tosencen/XMBOX-Release@main/apk/release/mobile-arm64_v8a-v3.1.0.apk
     * 
     * @param rawUrl GitHub raw URL
     * @return jsDelivr CDN URL，如果转换失败则返回原URL
     */
    public static String convertRawToJsDelivrUrl(String rawUrl) {
        try {
            // 格式: https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}
            if (rawUrl.contains("raw.githubusercontent.com/")) {
                String path = rawUrl.substring(rawUrl.indexOf("raw.githubusercontent.com/") + "raw.githubusercontent.com/".length());
                String[] parts = path.split("/", 3);
                if (parts.length >= 3) {
                    String owner = parts[0];
                    String repo = parts[1];
                    String filePath = parts[2];
                    String jsDelivrUrl = "https://cdn.jsdelivr.net/gh/" + owner + "/" + repo + "@main/" + filePath;
                    Logger.d("Github: Raw URL转换: " + rawUrl + " -> " + jsDelivrUrl);
                    return jsDelivrUrl;
                }
            }
            // 转换失败时返回原URL
            return rawUrl;
        } catch (Exception e) {
            Logger.e("Github: Raw URL转换失败: " + e.getMessage());
            return rawUrl;
        }
    }
    
    // 智能检测是否使用国内镜像
    public static boolean useCnMirror() {
        // 如果已经测试过并且在24小时内，直接返回上次的结果
        long currentTime = SystemClock.elapsedRealtime();
        if (useCnMirror != null && (currentTime - lastCheckTime < CHECK_INTERVAL)) {
            return useCnMirror;
        }
        
        // 进行网络测速
        useCnMirror = testMirrorSpeed();
        lastCheckTime = currentTime;
        return useCnMirror;
    }
    
    // 测试镜像速度
    private static boolean testMirrorSpeed() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)  // 增加超时时间
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build();
            
            // 测试国际源
            long startTime = System.currentTimeMillis();
            boolean intlSuccess = testUrl(client, URL + "/README.md");
            long intlTime = System.currentTimeMillis() - startTime;
            Logger.d("Github: International mirror test - success: " + intlSuccess + ", time: " + intlTime + "ms");
            
            // 测试国内源
            startTime = System.currentTimeMillis();
            boolean cnSuccess = testUrl(client, CN_URL + "/README.md");
            long cnTime = System.currentTimeMillis() - startTime;
            Logger.d("Github: Chinese mirror test - success: " + cnSuccess + ", time: " + cnTime + "ms");
            
            // 如果两个都成功，选择更快的
            if (intlSuccess && cnSuccess) {
                boolean useCn = cnTime < intlTime;
                Logger.d("Github: Both mirrors work, choosing " + (useCn ? "Chinese" : "International") + " mirror");
                return useCn;
            }
            
            // 如果只有一个成功，选择成功的那个
            if (intlSuccess) {
                Logger.d("Github: Only international mirror works, using it");
                return false;
            }
            if (cnSuccess) {
                Logger.d("Github: Only Chinese mirror works, using it");
                return true;
            }
            
            // 如果都失败，默认国际源
            Logger.e("Github: Both mirrors failed, defaulting to international");
            return false;
        } catch (Exception e) {
            Logger.e("Github: Mirror test exception: " + e.getMessage());
            return false; // 出错时默认使用国际源
        }
    }
    
    private static boolean testUrl(OkHttpClient client, String url) {
        Request request = new Request.Builder().url(url).build();
        Call call = client.newCall(request);
        try {
            Response response = call.execute();
            boolean success = response.isSuccessful();
            response.close();
            return success;
        } catch (IOException e) {
            return false;
        }
    }
}
