package com.fongmi.android.tv.search;

import android.text.TextUtils;

import com.github.catvod.utils.Trans;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 片名规整：判断不同站点返回的是不是同一部片。
 * <p>
 * 各站点的片名写法五花八门——繁简、空格、标点、[HD]、(2019)、国语版……这里把这些噪声去掉，
 * 只留下能区分作品的部分。「流浪地球2」「第二季」「（上）」这类是作品本身的一部分，不能去掉，
 * 否则续集、分季、上下部会被并进前作。
 */
public final class TitleKey {

    public static final int KIND_UNKNOWN = 0;
    public static final int KIND_MOVIE = 1;
    public static final int KIND_TV = 2;
    public static final int KIND_ANIME = 3;
    public static final int KIND_SHOW = 4;
    public static final int KIND_DOC = 5;

    private static final Pattern BRACKET = Pattern.compile("[\\[【(（〔「『《<]([^\\[\\]【】()（）〔〕「」『』《》<>]*)[\\]】)）〕」』》>]");
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");
    private static final Pattern SEASON = Pattern.compile("第([0-9一二三四五六七八九十]{1,3})季");
    private static final Pattern EPISODE = Pattern.compile("(\\d{1,4})\\s*[集期话話]");
    private static final Pattern NOISE = Pattern.compile("[\\s\\p{P}\\p{S}]+");

    /** 画质、语言、版本这类修饰词。只在括号里或片名末尾出现时才去掉，片名中间的不动。 */
    private static final String[] TAGS = {
            "导演剪辑版", "中英双字", "中英字幕", "中文字幕", "国粤双语", "未删减版", "普通话版",
            "抢先版", "抢鲜版", "正式版", "完整版", "加长版", "剪辑版", "未删减", "国语版", "粤语版",
            "英语版", "日语版", "原声版", "双语版", "1080p", "2160p", "60fps", "蓝光", "超清", "高清",
            "标清", "国语", "粤语", "中字", "双语", "全集", "完结", "枪版", "杜比", "60帧", "720p",
            "hdr", "4k", "8k", "hd", "bd", "tc", "ts"
    };

    private TitleKey() {
    }

    /** 规整后的片名，空串表示认不出。 */
    public static String normalize(String name) {
        if (TextUtils.isEmpty(name)) return "";
        String text = Trans.t2s(name).trim().toLowerCase(Locale.ROOT);
        text = stripBracket(text);
        text = stripTail(text);
        text = normalizeSeason(text);
        String key = NOISE.matcher(text).replaceAll("");
        return key.isEmpty() ? NOISE.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("") : key;
    }

    /** 取第一个像年份的四位数，"2019-01-18" 也能认出 2019。认不出返回 0。 */
    public static int year(String text) {
        if (TextUtils.isEmpty(text)) return 0;
        Matcher matcher = YEAR.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    /**
     * 按站点给的分类粗分片种。只认有把握的写法，拿不准的一律 UNKNOWN——
     * 分错了会把同一部片拆成两行，比不拆更糟。
     */
    public static int kind(String type) {
        if (TextUtils.isEmpty(type)) return KIND_UNKNOWN;
        String text = Trans.t2s(type);
        if (text.contains("动漫") || text.contains("动画") || text.contains("番剧") || text.contains("新番")) return KIND_ANIME;
        if (text.contains("综艺") || text.contains("真人秀") || text.contains("晚会")) return KIND_SHOW;
        if (text.contains("纪录")) return KIND_DOC;
        if (text.contains("电影") || text.endsWith("片")) return KIND_MOVIE;
        if (text.matches(".*(电视剧|连续剧|短剧|网剧|国产剧|大陆剧|内地剧|陆剧|港剧|台剧|港台剧|日剧|韩剧|日韩剧|美剧|英剧|欧美剧|泰剧|海外剧).*")) return KIND_TV;
        return KIND_UNKNOWN;
    }

    /** 两边都有年份时允许差一年：同一部剧跨年播出，各站点记的年份常常差一年。 */
    public static boolean sameYear(int a, int b) {
        return a == 0 || b == 0 || Math.abs(a - b) <= 1;
    }

    public static boolean sameKind(int a, int b) {
        return a == KIND_UNKNOWN || b == KIND_UNKNOWN || a == b;
    }

    /** 相关度档位：0 同名，1 以关键词开头，2 包含关键词，3 其他（比如按演员搜出来的）。 */
    public static int tier(String key, String keyword) {
        if (TextUtils.isEmpty(keyword)) return 3;
        if (key.equals(keyword)) return 0;
        if (key.startsWith(keyword)) return 1;
        if (key.contains(keyword)) return 2;
        return 3;
    }

    /** 从"更新至20集""全40集""第12期"里取集数，用来挑最新的更新状态。取不到返回 -1。 */
    public static int episodes(String remarks) {
        if (TextUtils.isEmpty(remarks)) return -1;
        Matcher matcher = EPISODE.matcher(remarks);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static String stripBracket(String text) {
        Matcher matcher = BRACKET.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String inner = matcher.group(1).trim();
            String rest = NOISE.matcher(stripTags(inner)).replaceAll("");
            boolean drop = rest.isEmpty() || YEAR.matcher(rest).matches();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(drop ? " " : " " + inner + " "));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String stripTags(String text) {
        for (String tag : TAGS) text = text.replace(tag, " ");
        return text;
    }

    private static String stripTail(String text) {
        boolean changed = true;
        while (changed) {
            changed = false;
            text = trimNoise(text);
            for (String tag : TAGS) {
                if (!endsWithTag(text, tag)) continue;
                text = text.substring(0, text.length() - tag.length());
                changed = true;
                break;
            }
        }
        return text;
    }

    /** 英文修饰词前面必须是分隔符或中文，否则 "cats" 会被当成 "ca" + "ts"。 */
    private static boolean endsWithTag(String text, String tag) {
        if (text.length() <= tag.length() || !text.endsWith(tag)) return false;
        char before = text.charAt(text.length() - tag.length() - 1);
        boolean ascii = tag.charAt(0) < 0x80;
        return !ascii || !(Character.isLetterOrDigit(before) && before < 0x80);
    }

    private static String trimNoise(String text) {
        int end = text.length();
        while (end > 0 && isNoise(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    private static boolean isNoise(char c) {
        return NOISE.matcher(String.valueOf(c)).matches();
    }

    /** "第二季"和"第2季"统一成"2"；第一季通常不写，统一去掉。 */
    private static String normalizeSeason(String text) {
        Matcher matcher = SEASON.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int number = number(matcher.group(1));
            String value = number <= 1 ? "" : String.valueOf(number);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static int number(String text) {
        if (TextUtils.isDigitsOnly(text)) return Integer.parseInt(text);
        String digits = "零一二三四五六七八九";
        int index = text.indexOf('十');
        if (index < 0) return digits.indexOf(text.charAt(0));
        int tens = index == 0 ? 1 : digits.indexOf(text.charAt(0));
        int ones = index == text.length() - 1 ? 0 : digits.indexOf(text.charAt(index + 1));
        return Math.max(0, tens) * 10 + Math.max(0, ones);
    }
}
