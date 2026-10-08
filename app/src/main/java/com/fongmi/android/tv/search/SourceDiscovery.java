package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable title/query plan used by player-side source discovery. */
public final class SourceDiscovery {

    private static final Pattern BRACKET = Pattern.compile("[\\[【(（〔「『《<]([^\\[\\]【】()（）〔〕「」『』《》<>]{2,80})[\\]】)）〕」』》>]");
    private static final Pattern ALIAS_SEPARATOR = Pattern.compile("(?i)\\s*(?:[/／|｜;；]|又名\\s*[:：]?|aka\\s*[:：]?)\\s*");
    private static final int MAX_QUERIES = 6;

    private final List<String> queries;
    private final Set<String> titleKeys;
    private final int year;
    private final int kind;

    public SourceDiscovery(String title, Collection<String> aliases, int year, int kind) {
        LinkedHashSet<String> raw = new LinkedHashSet<>();
        addVariants(raw, title);
        if (aliases != null) for (String alias : aliases) addVariants(raw, alias);
        List<String> terms = new ArrayList<>();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String value : raw) {
            String key = TitleKey.normalize(value);
            if (key.isEmpty() || !keys.add(key)) continue;
            if (terms.size() < MAX_QUERIES) terms.add(value);
        }
        this.queries = Collections.unmodifiableList(terms);
        this.titleKeys = Collections.unmodifiableSet(keys);
        this.year = year;
        this.kind = kind;
    }

    public List<String> queries() {
        return queries;
    }

    public Set<String> titleKeys() {
        return titleKeys;
    }

    public boolean matchesTitle(String title) {
        for (String value : variants(title)) if (titleKeys.contains(TitleKey.normalize(value))) return true;
        return false;
    }

    public boolean possible(Vod vod) {
        if (vod == null || !matchesTitle(vod.getVodName())) return false;
        return TitleKey.sameYear(year, TitleKey.year(vod.getVodYear()))
                && (vod.getVodFlags().isEmpty() || TitleKey.sameSourceKind(kind, TitleKey.kind(vod.getTypeName())));
    }

    /** Search completion is cached per query plan, so newly learned aliases re-open every site. */
    public String scope() {
        return Integer.toHexString((String.join("\n", titleKeys) + "\n" + year + "\n" + kind).hashCode());
    }

    public static List<String> variants(String title) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addVariants(result, title);
        return new ArrayList<>(result);
    }

    private static void addVariants(Set<String> values, String title) {
        if (title == null) return;
        String value = title.trim();
        if (value.isEmpty()) return;
        Matcher matcher = BRACKET.matcher(value);
        String outside = matcher.replaceAll(" ").trim();
        if (!outside.equals(value)) add(values, outside);
        String[] parts = ALIAS_SEPARATOR.split(value);
        if (parts.length > 1) for (String part : parts) add(values, part);
        add(values, value);
        matcher.reset();
        while (matcher.find()) add(values, matcher.group(1));
    }

    private static void add(Set<String> values, String value) {
        if (value == null) return;
        value = value.trim();
        if (value.length() < 2 || value.length() > 80) return;
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.equals("aka") && !lower.equals("又名")) values.add(value);
    }
}
