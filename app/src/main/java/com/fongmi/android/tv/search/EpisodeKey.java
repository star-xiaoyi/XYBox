package com.fongmi.android.tv.search;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semantic episode numbers; dates, resolutions and arbitrary playlist indexes are not episode ids. */
public final class EpisodeKey {
    private static final Pattern LABELED = Pattern.compile("(?:第\\s*)?([0-9零〇一二三四五六七八九十百千两]+)\\s*[集话話期回]");
    private static final Pattern ENGLISH = Pattern.compile("(?:s(\\d+))?e(?:p)?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEASON = Pattern.compile("第\\s*([0-9零〇一二三四五六七八九十百两]+)\\s*季");
    private EpisodeKey() { }
    public static int number(String name) {
        if (name == null) return -1;
        String text = narrow(name).trim();
        if (text.matches("(?i).*(?:预告|預告|花絮|特辑|特輯|特别|特別|番外|彩蛋|special).*")) return -1;
        Matcher labeled = LABELED.matcher(text), english = ENGLISH.matcher(text);
        if (labeled.find()) return parse(labeled.group(1));
        if (english.matches()) return parse(english.group(2));
        return text.matches("[0-9]{1,3}") ? parse(text) : -1;
    }
    private static int parse(String text) {
        if (text.matches("[0-9]+")) try { int value = Integer.parseInt(text); return value > 0 ? value : -1; } catch (NumberFormatException ignored) { return -1; }
        int result = 0, digit = 0;
        for (char c : text.toCharArray()) {
            int value = "零一二三四五六七八九".indexOf(c);
            if (c == '〇') value = 0;
            if (c == '两') value = 2;
            if (value >= 0) digit = value;
            else { int unit = c == '十' ? 10 : c == '百' ? 100 : c == '千' ? 1000 : 0; if (unit == 0) return -1; result += (digit == 0 ? 1 : digit) * unit; digit = 0; }
        }
        return result + digit > 0 ? result + digit : -1;
    }
    public static String key(String name) {
        int number = number(name);
        if (number > 0) {
            Matcher english = ENGLISH.matcher(narrow(name).trim()), season = SEASON.matcher(narrow(name));
            int series = english.matches() && english.group(1) != null ? parse(english.group(1))
                    : season.find() ? parse(season.group(1)) : 1;
            if (series > 1) return "s" + series + "e" + number;
        }
        return number > 0 ? String.valueOf(number) : normalized(name);
    }
    private static String narrow(String name) {
        if (name == null) return "";
        StringBuilder value = new StringBuilder();
        for (char c : name.toCharArray()) value.append(c >= '０' && c <= '９' ? (char) (c - '０' + '0') : c);
        return value.toString();
    }
    public static String normalized(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}\\p{S}]+", "");
    }
    public static boolean movieLabel(String name) {
        return normalized(name).matches("(?:正片|完整版|高清|超清|蓝光|藍光|中字|国语|國語|粤语|粵語|hd|bd|[0-9]{3,4}p)(?:中字|国语|國語|粤语|粵語)?");
    }
}
