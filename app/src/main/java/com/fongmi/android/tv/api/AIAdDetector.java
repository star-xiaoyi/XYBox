package com.fongmi.android.tv.api;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.Setting;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI广告检测器 - 基于智能启发式算法的广告检测
 * 
 * 功能：
 * 1. URL模式识别 - 检测常见广告URL特征
 * 2. 域名信誉分析 - 基于已知广告域名特征
 * 3. 请求特征分析 - 分析请求路径和参数
 * 4. 动态学习 - 记录被拦截的域名模式
 */
public class AIAdDetector {

    private static final String TAG = "AIAdDetector";
    
    // 置信度阈值 (0-100)
    private static final int CONFIDENCE_THRESHOLD = 60;
    
    // 广告关键词模式
    private static final List<String> AD_KEYWORDS = Arrays.asList(
            "ad", "ads", "advert", "advertising", "banner", "sponsor",
            "promo", "promotion", "click", "track", "tracking",
            "analytics", "statistics", "counter", "monitor",
            "pop", "popup", "interstitial", "overlay",
            "videoad", "preroll", "midroll", "postroll",
            "commercial", "marketing", "campaign",
            "doubleclick", "googlesyndication", "adserver",
            "adservice", "adnetwork", "addelivery",
            "cpc", "cpm", "cpa", "ppv", "popunder",
            "spam", "splash", "stitial", "vast", "vmap"
    );
    
    // 广告域名后缀
    private static final List<String> AD_DOMAIN_SUFFIXES = Arrays.asList(
            "ads.", "ad.", "adserv.", "adserver.", "advert.",
            "tracking.", "track.", "analytics.", "stats.",
            "click.", "popup.", "banner.", "media.",
            "promo.", "adsystem.", "adnetwork."
    );
    
    // 可疑路径模式
    private static final List<String> SUSPICIOUS_PATHS = Arrays.asList(
            "/ads/", "/ad/", "/advert/", "/advertising/",
            "/banner/", "/sponsor/", "/promo/",
            "/click/", "/track/", "/tracking/",
            "/analytics/", "/stat/", "/counter/",
            "/pop/", "/popup/", "/interstitial/",
            "/preroll/", "/midroll/", "/postroll/",
            "/commercial/", "/vast/", "/vmap/",
            "/adjs/", "/ad.js", "/ads.js",
            "/showad", "/showAd", "/getad", "/getAd",
            "/api/ad", "/service/ad", "/json/ad"
    );
    
    // 常见广告域名特征（用于模糊匹配）
    private static final List<String> AD_DOMAIN_PATTERNS = Arrays.asList(
            ".*ad[s]?\\d+.*",
            ".*ad[0-9]+\\.?.*",
            ".*[a-z]ad[s]?\\d+.*",
            ".*adserv(er|ice)?.*",
            ".*advert(ising|iser|ise)?.*",
            ".*banner.*",
            ".*sponsor.*",
            ".*promo(tion)?.*",
            ".*click.*track.*",
            ".*media.*ad.*",
            ".*cpc.*",
            ".*cpm.*",
            ".*pop(ad|under).*",
            ".*video.*ad.*",
            ".*preroll.*",
            ".*midroll.*"
    );
    
    // 已知的广告域名白名单（不检测）
    private static final List<String> WHITELIST = Arrays.asList(
            "google.com", "googleapis.com", "googlevideo.com",
            "amazonaws.com", "cloudfront.net",
            "akamai", "cloudflare", "fastly",
            "baidu.com", "alicdn.com", "taobao.com",
            "qq.com", "qzone.com", "weixin.qq.com",
            "youku.com", "iqiyi.com", "qq.com", "bilibili.com",
            "douyin.com", "tiktok.com"
    );
    
    // 动态学习的域名记录
    private static final ConcurrentHashMap<String, Integer> learnedDomains = new ConcurrentHashMap<>();
    
    // 检测统计
    private static int totalChecked = 0;
    private static int totalBlocked = 0;
    
    /**
     * 检测请求是否可能是广告
     * @param urlString 请求URL
     * @return true=可能是广告, false=不是广告
     */
    public static boolean isAd(String urlString) {
        // 从设置中读取启用状态
        if (!isEnabled() || TextUtils.isEmpty(urlString)) {
            return false;
        }
        
        totalChecked++;
        
        try {
            @SuppressWarnings("deprecation")
            URL url = new URL(urlString);
            String host = url.getHost() != null ? url.getHost().toLowerCase() : "";
            String path = url.getPath().toLowerCase();
            String query = url.getQuery() != null ? url.getQuery().toLowerCase() : "";
            
            // 1. 检查白名单
            if (isWhitelisted(host)) {
                return false;
            }
            
            // 2. 计算综合置信度
            int confidence = calculateConfidence(host, path, query);
            
            // 3. 检查是否达到阈值
            if (confidence >= CONFIDENCE_THRESHOLD) {
                totalBlocked++;
                // 记录被拦截的域名用于学习
                learnDomain(host, confidence);
                Log.d(TAG, "AI检测到广告: " + urlString + " (置信度: " + confidence + "%)");
                return true;
            }
            
            // 4. 检查已有的学习记录
            if (learnedDomains.containsKey(host)) {
                Integer learnedConfidence = learnedDomains.get(host);
                if (learnedConfidence != null && learnedConfidence >= CONFIDENCE_THRESHOLD) {
                    totalBlocked++;
                    return true;
                }
            }
            
            // 5. 额外检查：结合现有的AdBlocker
            if (AdBlocker.shouldBlock(host)) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "检测URL时出错: " + urlString, e);
            return false;
        }
    }
    
    /**
     * 计算广告置信度 (0-100)
     */
    private static int calculateConfidence(String host, String path, String query) {
        int confidence = 0;
        
        // 1. 域名特征分析 (最高40分)
        confidence += analyzeHost(host);
        
        // 2. 路径特征分析 (最高30分)
        confidence += analyzePath(path);
        
        // 3. 查询参数分析 (最高20分)
        confidence += analyzeQuery(query);
        
        // 4. 综合特征分析 (最高10分)
        confidence += analyzeCombined(host, path, query);
        
        // 限制在0-100范围
        return Math.min(confidence, 100);
    }
    
    /**
     * 分析域名特征
     */
    private static int analyzeHost(String host) {
        int score = 0;
        
        // 检查是否以广告域名开头
        for (String suffix : AD_DOMAIN_SUFFIXES) {
            if (host.startsWith(suffix) || host.startsWith("www." + suffix)) {
                score += 35;
                break;
            }
        }
        
        // 检查域名模式
        for (String pattern : AD_DOMAIN_PATTERNS) {
            if (host.matches(pattern)) {
                score += 30;
                break;
            }
        }
        
        // 检查是否包含广告关键词
        for (String keyword : AD_KEYWORDS) {
            if (host.contains(keyword)) {
                score += 20;
                break;
            }
        }
        
        // 检查数字广告域名 (如 ad1, ads2)
        Pattern numPattern = Pattern.compile(".*(ad|ads)\\d+.*");
        if (numPattern.matcher(host).matches()) {
            score += 25;
        }
        
        return Math.min(score, 40);
    }
    
    /**
     * 分析路径特征
     */
    private static int analyzePath(String path) {
        int score = 0;
        
        for (String suspiciousPath : SUSPICIOUS_PATHS) {
            if (path.contains(suspiciousPath)) {
                score += 25;
                break;
            }
        }
        
        // 检查路径中的广告关键词
        String[] pathParts = path.split("/");
        for (String part : pathParts) {
            for (String keyword : AD_KEYWORDS) {
                if (part.equalsIgnoreCase(keyword) || part.contains(keyword)) {
                    score += 15;
                    break;
                }
            }
        }
        
        return Math.min(score, 30);
    }
    
    /**
     * 分析查询参数特征
     */
    private static int analyzeQuery(String query) {
        if (TextUtils.isEmpty(query)) {
            return 0;
        }
        
        int score = 0;
        
        // 常见广告查询参数
        String[] adParams = {"ad", "ads", "banner", "sponsor", "promo", "click", "track", "ref"};
        
        for (String param : adParams) {
            if (query.contains(param + "=")) {
                score += 10;
            }
        }
        
        return Math.min(score, 20);
    }
    
    /**
     * 综合特征分析
     */
    private static int analyzeCombined(String host, String path, String query) {
        int score = 0;
        
        // 组合特征：短域名 + 广告路径
        if (host.length() < 20 && !path.isEmpty()) {
            for (String suspiciousPath : SUSPICIOUS_PATHS) {
                if (path.contains(suspiciousPath)) {
                    score += 5;
                    break;
                }
            }
        }
        
        // 组合特征：包含多个广告关键词
        int keywordCount = 0;
        String combined = host + path + query;
        for (String keyword : AD_KEYWORDS) {
            if (combined.contains(keyword)) {
                keywordCount++;
            }
        }
        if (keywordCount >= 3) {
            score += 5;
        }
        
        return Math.min(score, 10);
    }
    
    /**
     * 检查是否在白名单中
     */
    private static boolean isWhitelisted(String host) {
        for (String whitelist : WHITELIST) {
            if (host.equals(whitelist) || host.endsWith("." + whitelist)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 学习已拦截的域名
     */
    private static void learnDomain(String host, int confidence) {
        // 只记录高置信度的域名
        if (confidence >= 70) {
            learnedDomains.put(host, confidence);
        }
    }
    
    /**
     * 获取检测统计
     */
    public static Map<String, Integer> getStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("totalChecked", totalChecked);
        stats.put("totalBlocked", totalBlocked);
        stats.put("learnedDomains", learnedDomains.size());
        return stats;
    }
    
    /**
     * 重置统计
     */
    public static void resetStats() {
        totalChecked = 0;
        totalBlocked = 0;
    }
    
    /**
     * 清空学习的域名
     */
    public static void clearLearned() {
        learnedDomains.clear();
    }
    
    /**
     * 启用/禁用AI检测
     */
    public static void setEnabled(boolean enabled) {
        Setting.putAIAdBlockEnabled(enabled);
    }
    
    /**
     * 是否启用 - 从设置中读取
     */
    public static boolean isEnabled() {
        return Setting.isAIAdBlockEnabled();
    }
}
