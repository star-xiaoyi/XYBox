package com.fongmi.android.tv.search;

import android.text.Html;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.Sniffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

/**
 * 合并后的一部片：各站点的同名资源都挂在这里。
 * <p>
 * 展示用的信息从各个源里拼：片名取最干净的写法，海报、简介、演员取第一个有的，
 * 更新状态取集数最多的那条。网盘文件夹这类没法合并的条目单独成组（folder）。
 */
public class VodGroup {

    private final List<VodSource> sources;
    private final boolean folder;
    private final String key;
    private final int tier;
    private int year;
    private int kind;
    private int episodes;
    private boolean filled;
    private String name;
    private String pic;
    private String area;
    private String type;
    private String actor;
    private String director;
    private String content;
    private String remarks;

    VodGroup(String key, int tier, boolean folder) {
        this.sources = new ArrayList<>();
        this.folder = folder;
        this.episodes = -1;
        this.tier = tier;
        this.key = key;
    }

    boolean accept(int year, int kind) {
        return !folder && TitleKey.sameYear(this.year, year) && TitleKey.sameKind(this.kind, kind);
    }

    void add(VodSource source, int year, int kind) {
        sources.add(source);
        if (this.year == 0) this.year = year;
        if (this.kind == TitleKey.KIND_UNKNOWN) this.kind = kind;
        merge(source.getVod());
    }

    /** 列表滑到这一行时补拉的详情：只补缺的字段，不覆盖已有的。 */
    public void fill(Vod vod) {
        filled = true;
        if (vod == null) return;
        if (year == 0) year = TitleKey.year(vod.getVodYear());
        merge(vod);
    }

    private void merge(Vod vod) {
        String vodName = vod.getVodName();
        if (TextUtils.isEmpty(name) || (!vodName.isEmpty() && vodName.length() < name.length())) name = vodName;
        if (TextUtils.isEmpty(pic)) pic = vod.getVodPic();
        if (TextUtils.isEmpty(area)) area = vod.getVodArea();
        if (TextUtils.isEmpty(type)) type = vod.getTypeName();
        if (TextUtils.isEmpty(actor)) actor = clean(vod.getVodActor());
        if (TextUtils.isEmpty(director)) director = clean(vod.getVodDirector());
        if (TextUtils.isEmpty(content)) content = clean(vod.getVodContent());
        int count = TitleKey.episodes(vod.getVodRemarks());
        if (TextUtils.isEmpty(remarks) || count > episodes) {
            if (!vod.getVodRemarks().isEmpty()) remarks = vod.getVodRemarks();
            episodes = Math.max(episodes, count);
        }
    }

    /** 站点的简介常带 HTML 和可点击标记，列表里只要纯文本，多余的空白压成一个空格。 */
    private static String clean(String text) {
        if (TextUtils.isEmpty(text)) return "";
        Matcher matcher = Sniffer.CLICKER.matcher(text);
        while (matcher.find()) text = text.replace(matcher.group(), matcher.group(2));
        return Html.fromHtml(text).toString().replaceAll("\\s+", " ").trim();
    }

    public List<VodSource> getSources() {
        return sources;
    }

    /** 进详情页先播哪个：按当前排序取第一个没播挂过的。 */
    public VodSource best() {
        List<VodSource> items = new ArrayList<>(sources);
        Collections.sort(items, VodSource.RANK);
        for (VodSource item : items) if (!item.isBroken()) return item;
        return items.get(0);
    }

    public VodSource first() {
        return sources.get(0);
    }

    public int size() {
        return sources.size();
    }

    public boolean isFolder() {
        return folder;
    }

    public boolean isFilled() {
        return filled;
    }

    public boolean hasContent() {
        return !TextUtils.isEmpty(content);
    }

    public String getKey() {
        return key;
    }

    public int getTier() {
        return tier;
    }

    public int getYear() {
        return year;
    }

    public int getKind() {
        return kind;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public String getPic() {
        return pic == null ? "" : pic;
    }

    public String getActor() {
        return TextUtils.isEmpty(actor) ? director == null ? "" : director : actor;
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public String getRemarks() {
        return remarks == null ? "" : remarks;
    }

    /** 年份 · 地区 · 类型，缺的项跳过。 */
    public String getMeta() {
        List<String> parts = new ArrayList<>();
        if (year > 0) parts.add(String.valueOf(year));
        if (!TextUtils.isEmpty(area)) parts.add(area);
        if (!TextUtils.isEmpty(type)) parts.add(type);
        return TextUtils.join(" · ", parts);
    }
}
