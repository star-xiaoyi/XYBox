package com.fongmi.android.tv.api;

import java.util.Arrays;
import java.util.List;

/**
 * 广告拦截器 - 内置常用广告域名库
 */
public class AdBlocker {

    /**
     * 赌博类广告域名（澳门新葡京等）
     */
    private static final List<String> GAMBLING_ADS = Arrays.asList(
            // 澳门博彩广告
            ".*\\..*葡京.*",
            ".*\\..*皇冠.*",
            ".*\\..*金沙.*",
            ".*\\..*威尼斯人.*",
            ".*\\..*永利.*",
            ".*aomen.*",
            ".*macau.*casino.*",
            ".*xpj.*\\..*",
            ".*xinpujing.*",
            ".*amdc.*\\.com",
            ".*\\.amdc\\.alipay\\.com",
            
            // 常见博彩推广域名
            ".*\\.bz.*bet.*",
            ".*\\.casino.*",
            ".*\\.poker.*", 
            ".*\\.betting.*",
            ".*\\.gamble.*",
            ".*wnsr.*\\..*",
            ".*js[0-9]+\\..*",
            ".*vn[0-9]+\\..*",
            ".*ag[0-9]+\\..*",
            
            // 具体的博彩广告域名
            "wan.51img1.com",
            "iqiyi.hbuioo.com",
            "vip.ffzyad.com",
            "https.wshdsm.com",
            "v.%E7%88%B1%E4%B8%8A%E5%A5%B9%E5%BD%B1%E9%99%A2.com"
    );

    /**
     * 通用广告联盟域名
     */
    private static final List<String> GENERAL_ADS = Arrays.asList(
            // Google广告
            "googleads.g.doubleclick.net",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "www.googletagmanager.com",
            "static.doubleclick.net",
            ".*\\.doubleclick\\.net",
            ".*\\.googlesyndication\\.com",
            
            // 百度广告
            "cpro.baidu.com",
            "pos.baidu.com", 
            "cbjs.baidu.com",
            "hm.baidu.com",
            ".*\\.union\\.baidu\\.com",
            
            // 淘宝/阿里广告
            "mclick.simba.taobao.com",
            "simba.m.taobao.com",
            ".*\\.tanx\\.com",
            ".*\\.mmstat\\.com",
            ".*\\.atm\\.youku\\.com",
            
            // 腾讯广告
            "mi.gdt.qq.com",
            "adsmind.gdtimg.com",
            ".*\\.l\\.qq\\.com",
            "pgdt.gtimg.cn",
            
            // 其他主流广告联盟
            "union.meituan.com",
            "analytics.163.com",
            "g.163.com",
            "analytics.126.net",
            ".*\\.irs01\\.com",
            ".*\\.irs01\\.net"
    );

    /**
     * 视频平台广告域名
     */
    private static final List<String> VIDEO_ADS = Arrays.asList(
            // 优酷广告
            "atm.youku.com",
            "stat.youku.com",
            "ad.api.3g.youku.com",
            "pl.youku.com",
            "lstat.youku.com",
            ".*\\.atm\\.youku\\.com",
            
            // 爱奇艺广告
            "cupid.iqiyi.com",
            "data.video.iqiyi.com",
            "msg.71.am",
            ".*\\.cupid\\.iqiyi\\.com",
            ".*\\.data\\.video\\.iqiyi\\.com",
            
            // 腾讯视频广告
            "btrace.video.qq.com",
            "mtrace.video.qq.com",
            "vv.video.qq.com",
            "ad.video.qq.com",
            
            // 芒果TV广告
            "da.mgtv.com",
            "ad.hunantv.com",
            "v2.hunantv.com",
            
            // 其他视频平台
            "ark.letv.com",
            "stat.letv.com",
            ".*\\.beacon\\.qq\\.com"
    );

    /**
     * 弹窗广告域名
     */
    private static final List<String> POPUP_ADS = Arrays.asList(
            // 常见弹窗广告
            "mimg.0c1q0l.cn",
            "www.92424.cn",
            "k.jinxiuzhilv.com",
            "cdn.bootcss.com",
            "ppl.xunzhuo.com",
            "xc.hubeijieshikj.cn",
            "ssl.kdd.cc",
            "push.zhanzhang.baidu.com",
            "cpc.cmbchina.com",
            "adshow.58.com",
            
            // 移动端弹窗
            "afp.csbew.com",
            "aoodoo.feng.com",
            "*.popin.cc",
            "*.supersonicads.com"
    );

    /**
     * 恶意网站和钓鱼网站
     */
    private static final List<String> MALICIOUS_ADS = Arrays.asList(
            ".*\\.17un\\.com",
            ".*\\.baidustatic\\.com",
            ".*\\.cnzz\\.com",
            ".*\\.duomeng\\.cn",
            ".*\\.shuzilm\\.cn",
            ".*\\.haoyuemh\\.com",
            ".*\\.571xz\\.com",
            ".*\\.madthumbs\\.com"
    );

    /**
     * 跟踪统计域名
     */
    private static final List<String> TRACKING_ADS = Arrays.asList(
            // 统计跟踪
            "hm.baidu.com",
            "tongji.baidu.com",
            "s95.cnzz.com",
            "cnzz.com",
            ".*\\.umeng\\.com",
            ".*\\.umtrack\\.com",
            
            // Google Analytics
            "www.google-analytics.com",
            "ssl.google-analytics.com",
            ".*\\.googletagmanager\\.com"
    );

    /**
     * 获取所有内置广告域名
     * @return 完整的广告域名列表
     */
    public static List<String> getAllAdHosts() {
        return Arrays.asList(
                // 合并所有列表
                String.join(",", GAMBLING_ADS),
                String.join(",", GENERAL_ADS),
                String.join(",", VIDEO_ADS),
                String.join(",", POPUP_ADS),
                String.join(",", MALICIOUS_ADS),
                String.join(",", TRACKING_ADS)
        );
    }

    /**
     * 获取赌博类广告域名（澳门新葡京等）
     */
    public static List<String> getGamblingAdHosts() {
        return GAMBLING_ADS;
    }

    /**
     * 获取通用广告联盟域名
     */
    public static List<String> getGeneralAdHosts() {
        return GENERAL_ADS;
    }

    /**
     * 获取视频平台广告域名
     */
    public static List<String> getVideoAdHosts() {
        return VIDEO_ADS;
    }

    /**
     * 获取弹窗广告域名
     */
    public static List<String> getPopupAdHosts() {
        return POPUP_ADS;
    }

    /**
     * 获取恶意网站域名
     */
    public static List<String> getMaliciousAdHosts() {
        return MALICIOUS_ADS;
    }

    /**
     * 获取跟踪统计域名
     */
    public static List<String> getTrackingAdHosts() {
        return TRACKING_ADS;
    }

    /**
     * 检查是否应该拦截该域名
     * @param host 要检查的域名
     * @return true=应该拦截, false=不拦截
     */
    public static boolean shouldBlock(String host) {
        if (host == null || host.isEmpty()) return false;
        
        // 检查所有分类
        return checkInList(host, GAMBLING_ADS) ||
               checkInList(host, GENERAL_ADS) ||
               checkInList(host, VIDEO_ADS) ||
               checkInList(host, POPUP_ADS) ||
               checkInList(host, MALICIOUS_ADS) ||
               checkInList(host, TRACKING_ADS);
    }

    /**
     * 检查域名是否在列表中（支持正则）
     */
    private static boolean checkInList(String host, List<String> list) {
        for (String pattern : list) {
            if (host.matches(pattern.replace("*", ".*"))) {
                return true;
            }
            if (host.contains(pattern.replace(".*", ""))) {
                return true;
            }
        }
        return false;
    }
}

