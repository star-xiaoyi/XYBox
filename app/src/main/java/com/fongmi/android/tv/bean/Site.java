package com.fongmi.android.tv.bean;
import com.github.catvod.utils.Logger;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.gson.ExtAdapter;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;

@Entity
public class Site implements Parcelable {

    @NonNull
    @PrimaryKey
    @SerializedName("key")
    private String key;

    @Ignore
    @SerializedName("name")
    private String name;

    @Ignore
    @SerializedName("api")
    private String api;

    @Ignore
    @JsonAdapter(ExtAdapter.class)
    @SerializedName("ext")
    private String ext;

    @Ignore
    @SerializedName("jar")
    private String jar;

    @Ignore
    @SerializedName("click")
    private String click;

    @Ignore
    @SerializedName("playUrl")
    private String playUrl;

    @Ignore
    @SerializedName("type")
    private Integer type;

    @Ignore
    @SerializedName("hide")
    private Integer hide;

    @Ignore
    @SerializedName("indexs")
    private Integer indexs;

    @Ignore
    @SerializedName("timeout")
    private Integer timeout;

    @SerializedName("searchable")
    private Integer searchable;

    @SerializedName("changeable")
    private Integer changeable;

    @Ignore
    @SerializedName("quickSearch")
    private Integer quickSearch;

    @Ignore
    @SerializedName("categories")
    private List<String> categories;

    @Ignore
    @SerializedName("header")
    private JsonElement header;

    @Ignore
    @SerializedName("style")
    private Style style;

    @Ignore
    private boolean activated;

    public static Site objectFrom(JsonElement element) {
        try {
            Site site = App.gson().fromJson(element, Site.class);
            // 尝试修复可能的编码问题
            if (site != null && site.getKey() != null) {
                site.setKey(fixEncoding(site.getKey()));
                if (site.getName() != null) {
                    site.setName(fixEncoding(site.getName()));
                }
            }
            return site;
        } catch (Exception e) {
            return new Site();
        }
    }
    
    private static String fixEncoding(String str) {
        if (str == null || str.isEmpty()) return str;
        try {
            // 检查是否包含乱码字符（替换字符 U+FFFD）
            if (str.indexOf('\uFFFD') >= 0) {
                // 尝试用ISO-8859-1重新解码为UTF-8
                byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                String fixed = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                com.github.catvod.utils.Logger.d("Site.fixEncoding: 修复编码 '" + str + "' -> '" + fixed + "'");
                return fixed;
            }
        } catch (Exception e) {
            com.github.catvod.utils.Logger.e("Site.fixEncoding: 修复失败 - " + e.getMessage());
        }
        return str;
    }

    public static Site get(String key) {
        Site site = new Site();
        site.setKey(key);
        return site;
    }

    public static Site get(String key, String name) {
        Site site = new Site();
        site.setKey(key);
        site.setName(name);
        return site;
    }

    public Site() {
    }

    public String getKey() {
        return TextUtils.isEmpty(key) ? "" : key;
    }

    public void setKey(@NonNull String key) {
        this.key = key;
        // 检查key中是否有异常字符
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == 0xFFFD || c < 0x20 || (c >= 0x7F && c < 0xA0)) {
                com.github.catvod.utils.Logger.w("Site.setKey: 检测到异常字符 at position " + i + ": U+" + String.format("%04X", (int)c));
            }
        }
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
        // 检查name中是否有异常字符
        if (name != null) {
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == 0xFFFD || c < 0x20 || (c >= 0x7F && c < 0xA0)) {
                    com.github.catvod.utils.Logger.w("Site.setName: 检测到异常字符 at position " + i + ": U+" + String.format("%04X", (int)c) + " in name: " + name);
                }
            }
        }
    }

    public String getApi() {
        return TextUtils.isEmpty(api) ? "" : api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public String getExt() {
        return TextUtils.isEmpty(ext) ? "" : ext;
    }

    public void setExt(String ext) {
        this.ext = ext.trim();
    }

    public String getJar() {
        return TextUtils.isEmpty(jar) ? "" : jar;
    }

    public void setJar(String jar) {
        this.jar = jar;
    }

    public String getClick() {
        return TextUtils.isEmpty(click) ? "" : click;
    }

    public String getPlayUrl() {
        return TextUtils.isEmpty(playUrl) ? "" : playUrl;
    }

    public Integer getType() {
        return type == null ? 0 : type;
    }

    public Integer getHide() {
        return hide == null ? 0 : hide;
    }

    public Integer getIndexs() {
        return indexs == null ? 0 : indexs;
    }

    public long getTimeout() {
        return timeout == null ? Constant.TIMEOUT_PLAY : TimeUnit.SECONDS.toMillis(Math.max(timeout, 1));
    }

    public Integer getSearchable() {
        return searchable == null ? 1 : searchable;
    }

    public void setSearchable(Integer searchable) {
        this.searchable = searchable;
    }

    public Integer getChangeable() {
        return changeable == null ? 1 : changeable;
    }

    public void setChangeable(Integer changeable) {
        this.changeable = changeable;
    }

    public Integer getQuickSearch() {
        return quickSearch == null ? 1 : quickSearch;
    }

    public List<String> getCategories() {
        return categories == null ? Collections.emptyList() : categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public JsonElement getHeader() {
        return header;
    }

    public Style getStyle() {
        return style;
    }

    public Style getStyle(Style style) {
        return getStyle() != null ? getStyle() : style != null ? style : Style.rect();
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public void setActivated(Site item) {
        this.activated = item.equals(this);
    }

    public boolean isHide() {
        return getHide() == 1;
    }

    public boolean isIndex() {
        return getIndexs() == 1;
    }

    public boolean isSearchable() {
        return getSearchable() == 1;
    }

    public Site setSearchable(boolean searchable) {
        if (getSearchable() != 0) setSearchable(searchable ? 1 : 2);
        return this;
    }

    public boolean isChangeable() {
        return getChangeable() == 1;
    }

    public Site setChangeable(boolean changeable) {
        if (getChangeable() != 0) setChangeable(changeable ? 1 : 2);
        return this;
    }

    public boolean isQuickSearch() {
        return getQuickSearch() == 1;
    }

    public boolean isEmpty() {
        return getKey().isEmpty() && getName().isEmpty();
    }

    public Headers getHeaders() {
        return Headers.of(Json.toMap(getHeader()));
    }

    public Site fetchExt() {
        if (!getExt().startsWith("http")) return this;
        String extend = OkHttp.string(getExt());
        if (!extend.isEmpty()) setExt(extend);
        return this;
    }

    public Site trans() {
        if (Trans.pass()) return this;
        List<String> categories = new ArrayList<>();
        for (String cate : getCategories()) categories.add(Trans.s2t(cate));
        setCategories(categories);
        return this;
    }

    public Site sync() {
        Site item = find(getKey());
        if (item == null) return this;
        if (getChangeable() != 0) setChangeable(Math.max(1, item.getChangeable()));
        if (getSearchable() != 0) setSearchable(Math.max(1, item.getSearchable()));
        return this;
    }

    public Site recent() {
        BaseLoader.get().setRecent(getKey(), getApi(), getJar());
        return this;
    }

    public Spider spider() {
        return BaseLoader.get().getSpider(getKey(), getApi(), getExt(), getJar());
    }

    public static Site find(String key) {
        return AppDatabase.get().getSiteDao().find(key);
    }

    public void save() {
        AppDatabase.get().getSiteDao().insertOrUpdate(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Site)) return false;
        Site it = (Site) obj;
        return getKey().equals(it.getKey());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.key);
        dest.writeString(this.name);
        dest.writeString(this.api);
        dest.writeString(this.ext);
        dest.writeString(this.jar);
        dest.writeString(this.click);
        dest.writeString(this.playUrl);
        dest.writeValue(this.type);
        dest.writeValue(this.indexs);
        dest.writeValue(this.timeout);
        dest.writeValue(this.searchable);
        dest.writeValue(this.changeable);
        dest.writeStringList(this.categories);
        dest.writeParcelable(this.style, flags);
        dest.writeByte(this.activated ? (byte) 1 : (byte) 0);
    }

    protected Site(Parcel in) {
        this.key = in.readString();
        this.name = in.readString();
        this.api = in.readString();
        this.ext = in.readString();
        this.jar = in.readString();
        this.click = in.readString();
        this.playUrl = in.readString();
        this.type = (Integer) in.readValue(Integer.class.getClassLoader());
        this.indexs = (Integer) in.readValue(Integer.class.getClassLoader());
        this.timeout = (Integer) in.readValue(Integer.class.getClassLoader());
        this.searchable = (Integer) in.readValue(Integer.class.getClassLoader());
        this.changeable = (Integer) in.readValue(Integer.class.getClassLoader());
        this.categories = in.createStringArrayList();
        this.style = in.readParcelable(Style.class.getClassLoader());
        this.activated = in.readByte() != 0;
    }

    public static final Creator<Site> CREATOR = new Creator<>() {
        @Override
        public Site createFromParcel(Parcel source) {
            return new Site(source);
        }

        @Override
        public Site[] newArray(int size) {
            return new Site[size];
        }
    };
}
