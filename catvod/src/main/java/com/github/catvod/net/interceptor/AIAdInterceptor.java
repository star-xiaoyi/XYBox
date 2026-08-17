package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AI广告检测拦截器 - 基于智能启发式算法的广告检测
 * 集成到OkHttp拦截器链中
 */
public class AIAdInterceptor {

    // 启用AI检测
    private static boolean enabled = true;
    
    // 置信度阈值 (0-100)
    private static final int CONFIDENCE_THRESHOLD = 60;
    
    // 广告关键词
    private static final List<String> AD_KEYWORDS = Arrays.asList(
            "ad", "ads", "advert", "advertising", "banner", "sponsor",
            "promo", "promotion", "click", "track", "tracking",
            "analytics", "counter", "pop", "popup", "interstitial",
            "videoad", "preroll", "midroll", "postroll",
            "commercial", "vast", "vmap", "doubleclick",
            "googlesyndication", "adserver", "adservice"
    );
    
    // 广告域名后缀
    private static final List<String> AD_DOMAIN_SUFFIXES = Arrays.asList(
            "ads.", "ad.", "adserv.", "adserver.", "advert.",
            "tracking.", "track.", "analytics.", "stats.",
            "click.", "popup.", "banner.", "media."
    );
    
    // 可疑路径
    private static final List<String> SUSPICIOUS_PATHS = Arrays.asList(
            "/ads/", "/ad/", "/advert/", "/advertising/",
            "/banner/", "/sponsor/", "/promo/",
            "/click/", "/track/", "/tracking/",
            "/analytics/", "/stat/", "/counter/",
            "/pop/", "/popup/", "/interstitial/",
            "/preroll/", "/midroll/", "/postroll/",
            "/vast/", "/vmap/", "/showad", "/getad"
    );
    
    // 白名单
    private static final List<String> WHITELIST = Arrays.asList(
            "google.com", "googleapis.com", "googlevideo.com",
            "amazonaws.com", "cloudfront.net", "akamai",
            "baidu.com", "alicdn.com", "taobao.com",
            "qq.com", "youku.com", "iqiyi.com", "bilibili.com"
    );
    
    /**
     * 检测URL是否是广告
     */
    public static boolean isAd(String urlString) {
        if (!enabled || urlString == null || urlString.isEmpty()) {
            return false;
        }
        
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            if (host == null || host.isEmpty()) return false;
            host = host.toLowerCase();
            String path = url.getPath().toLowerCase();
            
            // 白名单检查
            if (isWhitelisted(host)) return false;
            
            // 计算置信度
            int confidence = calculateConfidence(host, path);
            
            return confidence >= CONFIDENCE_THRESHOLD;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 计算广告置信度
     */
    private static int calculateConfidence(String host, String path) {
        int confidence = 0;
        
        // 域名分析 (最高40分)
        for (String suffix : AD_DOMAIN_SUFFIXES) {
            if (host.startsWith(suffix)) {
                confidence += 35;
                break;
            }
        }
        
        for (String keyword : AD_KEYWORDS) {
            if (host.contains(keyword)) {
                confidence += 20;
                break;
            }
        }
        
        // 路径分析 (最高30分)
        for (String suspiciousPath : SUSPICIOUS_PATHS) {
            if (path.contains(suspiciousPath)) {
                confidence += 25;
                break;
            }
        }
        
        // 数字广告域名
        if (Pattern.matches(".*(ad|ads)\\d+.*", host)) {
            confidence += 20;
        }
        
        return Math.min(confidence, 100);
    }
    
    /**
     * 白名单检查
     */
    private static boolean isWhitelisted(String host) {
        for (String whitelist : WHITELIST) {
            if (host.equals(whitelist) || host.endsWith("." + whitelist)) {
                return true;
            }
        }
        return false;
    }
    
    public static void setEnabled(boolean enabled) {
        AIAdInterceptor.enabled = enabled;
    }
    
    public static boolean isEnabled() {
        return enabled;
    }
}
