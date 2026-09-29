package com.fongmi.android.tv.api;

import android.text.TextUtils;

import com.fongmi.android.tv.search.TitleKey;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Response;

/**
 * 豆瓣移动版的榜单接口（Top250、一周口碑榜、热门剧集……）。
 * <p>
 * 接口和海报图都校验 Referer：接口不带会返回 400，图片不带会返回 418。
 * 海报地址末尾拼上 "@Referer=" 交给 ImgUtil，它会拆出来加到请求头里。
 * 字段是手动取的，不走 Gson 反射，混淆后也不用额外 keep。
 */
public class Douban {

    public static final String REFERER = "https://m.douban.com/";
    private static final String API = "https://m.douban.com/rexxar/api/v2/subject_collection/%s/items?start=%d&count=%d";
    private static final String SEARCH = "https://movie.douban.com/j/new_search_subjects";
    private static final String SUGGEST = "https://movie.douban.com/j/subject_suggest";
    private static final String SUBJECT = "https://m.douban.com/rexxar/api/v2/subject/%s";
    private static final String RECOMMENDATIONS = "https://m.douban.com/rexxar/api/v2/subject/%s/recommendations?start=0&count=%d";
    private static final Map<String, Double> RATINGS = new ConcurrentHashMap<>();
    private static final Map<String, String> INTROS = new ConcurrentHashMap<>();
    private static final Map<String, Subject> SUBJECTS = new ConcurrentHashMap<>();
    private static final Map<String, List<Item>> RELATED = new ConcurrentHashMap<>();

    public static Page fetch(String collection, int start, int count) throws IOException {
        String url = String.format(Locale.ROOT, API, collection, start, count);
        try (Response response = OkHttp.newCall(url, Headers.of("Referer", REFERER)).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            return Page.parse(response.body().string());
        }
    }

    /** 豆瓣分类检索，供首页电影/剧集/动漫/综艺频道的组合筛选使用。 */
    public static List<Item> search(String tag, String genre, String country, String yearRange,
                                    String sort, double minRating, int start, int count) throws IOException {
        HttpUrl base = HttpUrl.parse(SEARCH);
        if (base == null) return new ArrayList<>();
        HttpUrl.Builder builder = base.newBuilder()
                .addQueryParameter("sort", TextUtils.isEmpty(sort) ? "U" : sort)
                .addQueryParameter("range", String.format(Locale.ROOT, "%.0f,10", Math.max(0, minRating)))
                .addQueryParameter("tags", tag)
                .addQueryParameter("start", String.valueOf(Math.max(0, start)))
                .addQueryParameter("limit", String.valueOf(Math.max(1, count)));
        if (!TextUtils.isEmpty(genre)) builder.addQueryParameter("genres", genre);
        if (!TextUtils.isEmpty(country)) builder.addQueryParameter("countries", country);
        if (!TextUtils.isEmpty(yearRange)) builder.addQueryParameter("year_range", yearRange);
        List<Item> items = new ArrayList<>();
        okhttp3.Call call = OkHttp.newCall(builder.build().toString(), Headers.of("Referer", "https://movie.douban.com/"));
        call.timeout().timeout(20, java.util.concurrent.TimeUnit.SECONDS);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) throw new IOException("Search HTTP " + response.code());
            if (response.body() == null) throw new IOException("Search response has no body");
            JsonObject root = JsonParser.parseString(response.body().string()).getAsJsonObject();
            JsonElement data = root.get("data");
            if (data == null || !data.isJsonArray()) throw new IOException("Search response has no data array");
            if (data != null && data.isJsonArray()) {
                int rank = start + 1;
                for (JsonElement element : data.getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    Item item = Item.parseSearch(element.getAsJsonObject());
                    item.rank = rank++;
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** 按片名和年份找到豆瓣条目，再读取评分。匹配不到或尚未开分时返回 0。 */
    public static double rating(String title, String year) throws IOException {
        String titleKey = TitleKey.normalize(title);
        if (titleKey.isEmpty()) return 0;
        int targetYear = TitleKey.year(year);
        String cacheKey = titleKey + "#" + targetYear;
        Double cached = RATINGS.get(cacheKey);
        if (cached != null) return cached;

        JsonObject match = suggest(title, titleKey, targetYear);
        if (match == null) return 0;

        String id = getString(match, "id");
        if (id.isEmpty()) return 0;
        JsonObject object = requestSubject(id);
        JsonElement element = object.get("rating");
        double rating = element != null && element.isJsonObject() ? getDouble(element.getAsJsonObject(), "value") : 0;
        if (rating > 0) RATINGS.put(cacheKey, rating);
        return rating;
    }

    /** 主推卡片按条目补拉剧情简介；结果缓存，切换频道不会重复请求。 */
    public static String intro(String id) throws IOException {
        if (TextUtils.isEmpty(id)) return "";
        String cached = INTROS.get(id);
        if (cached != null) return cached;
        JsonObject object = requestSubject(id);
        String intro = getString(object, "intro").replace('\n', ' ').trim();
        INTROS.put(id, intro);
        return intro;
    }

    /** 详情页的豆瓣元信息：评分、简介和类型标签。 */
    public static Subject subject(String title, String year) throws IOException {
        String titleKey = TitleKey.normalize(title);
        if (titleKey.isEmpty()) return new Subject();
        int targetYear = TitleKey.year(year);
        String cacheKey = titleKey + "#" + targetYear;
        Subject cached = SUBJECTS.get(cacheKey);
        if (cached != null) return cached;

        JsonObject match = suggest(title, titleKey, targetYear);
        if (match == null) return new Subject();
        String id = getString(match, "id");
        if (id.isEmpty()) return new Subject();
        Subject subject = Subject.parse(id, requestSubject(id));
        SUBJECTS.put(cacheKey, subject);
        if (subject.rating > 0) RATINGS.put(cacheKey, subject.rating);
        if (!subject.intro.isEmpty()) INTROS.put(id, subject.intro);
        return subject;
    }

    /** 豆瓣条目页的“相关推荐”，是真正按当前影片关联的内容。 */
    public static List<Item> related(String id, int count) throws IOException {
        if (TextUtils.isEmpty(id) || count <= 0) return new ArrayList<>();
        List<Item> cached = RELATED.get(id);
        if (cached != null) return new ArrayList<>(cached.subList(0, Math.min(count, cached.size())));
        String url = String.format(Locale.ROOT, RECOMMENDATIONS, id, count);
        String referer = "https://m.douban.com/movie/subject/" + id + "/";
        List<Item> items = new ArrayList<>();
        try (Response response = OkHttp.newCall(url, Headers.of("Referer", referer)).execute()) {
            if (!response.isSuccessful()) throw new IOException("Recommendations HTTP " + response.code());
            JsonElement root = JsonParser.parseString(response.body().string());
            if (root.isJsonArray()) for (JsonElement element : root.getAsJsonArray()) if (element.isJsonObject()) items.add(Item.parse(element.getAsJsonObject()));
        }
        RELATED.put(id, items);
        return new ArrayList<>(items);
    }

    private static JsonObject suggest(String title, String titleKey, int targetYear) throws IOException {
        HttpUrl base = HttpUrl.parse(SUGGEST);
        if (base == null) return null;
        HttpUrl suggest = base.newBuilder().addQueryParameter("q", title).build();
        try (Response response = OkHttp.newCall(suggest.toString(), Headers.of("Referer", "https://movie.douban.com/")).execute()) {
            if (!response.isSuccessful()) throw new IOException("Suggest HTTP " + response.code());
            return findMatch(JsonParser.parseString(response.body().string()).getAsJsonArray(), titleKey, targetYear);
        }
    }

    private static JsonObject requestSubject(String id) throws IOException {
        String url = String.format(Locale.ROOT, SUBJECT, id);
        String referer = "https://m.douban.com/movie/subject/" + id + "/";
        try (Response response = OkHttp.newCall(url, Headers.of("Referer", referer)).execute()) {
            if (!response.isSuccessful()) throw new IOException("Subject HTTP " + response.code());
            return JsonParser.parseString(response.body().string()).getAsJsonObject();
        }
    }

    private static JsonObject findMatch(JsonArray array, String titleKey, int targetYear) {
        JsonObject best = null;
        int bestScore = Integer.MAX_VALUE;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            String itemKey = TitleKey.normalize(getString(item, "title"));
            if (itemKey.isEmpty()) continue;
            int relation = itemKey.equals(titleKey) ? 0
                    : itemKey.startsWith(titleKey) || titleKey.startsWith(itemKey) ? 1 : 2;
            if (relation > 1) continue;
            int itemYear = TitleKey.year(getString(item, "year"));
            if (!TitleKey.sameYear(targetYear, itemYear)) continue;
            int yearScore = targetYear == 0 ? 0 : itemYear == targetYear ? 0 : itemYear == 0 ? 2 : 1;
            int score = relation * 10 + yearScore;
            if (score >= bestScore) continue;
            best = item;
            bestScore = score;
        }
        return best;
    }

    public static class Page {

        private final List<Item> items = new ArrayList<>();
        private int total;

        static Page parse(String json) {
            Page page = new Page();
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            page.total = getInt(object, "total");
            JsonElement list = object.get("subject_collection_items");
            if (list != null && list.isJsonArray()) {
                JsonArray array = list.getAsJsonArray();
                for (JsonElement element : array) if (element.isJsonObject()) page.items.add(Item.parse(element.getAsJsonObject()));
            }
            return page;
        }

        public List<Item> getItems() {
            return items;
        }

        public int getTotal() {
            return total;
        }
    }

    public static class Subject {

        private String id = "";
        private String intro = "";
        private String cover = "";
        private double rating;
        private final List<String> genres = new ArrayList<>();
        private final List<String> directors = new ArrayList<>();
        private final List<String> actors = new ArrayList<>();
        private final List<String> countries = new ArrayList<>();
        private String year = "";

        static Subject parse(String id, JsonObject object) {
            Subject subject = new Subject();
            subject.id = id;
            JsonElement pic = object.get("pic");
            subject.cover = pic != null && pic.isJsonObject() ? getString(pic.getAsJsonObject(), "large") : "";
            if (subject.cover.isEmpty()) subject.cover = Item.getCover(object);
            subject.intro = getString(object, "intro").replace('\n', ' ').trim();
            JsonElement rating = object.get("rating");
            if (rating != null && rating.isJsonObject()) subject.rating = getDouble(rating.getAsJsonObject(), "value");
            addStrings(subject.genres, object.get("genres"));
            addPeople(subject.directors, object.get("directors"));
            addPeople(subject.actors, object.get("actors"));
            if (subject.actors.isEmpty()) addPeople(subject.actors, object.get("casts"));
            addStrings(subject.countries, object.get("countries"));
            subject.year = getString(object, "year");
            return subject;
        }

        public String getId() {
            return id;
        }

        public String getIntro() {
            return intro;
        }

        public String getPic() {
            return cover.isEmpty() ? "" : cover + "@Referer=" + REFERER;
        }

        public double getRating() {
            return rating;
        }

        public List<String> getDirectors() { return new ArrayList<>(directors); }
        public List<String> getActors() { return new ArrayList<>(actors); }
        public List<String> getCountries() { return new ArrayList<>(countries); }
        public String getYear() { return year; }

        public List<String> getGenres() {
            return new ArrayList<>(genres);
        }
    }

    public static class Item {

        private String id;
        private String title;
        private String cover;
        private String year;
        private String subtitle;
        private String intro;
        private double rating;
        private int rank;

        static Item parse(JsonObject object) {
            Item item = new Item();
            item.id = getString(object, "id");
            item.title = getString(object, "title");
            item.subtitle = getString(object, "card_subtitle");
            item.intro = getString(object, "description");
            item.rank = getInt(object, "rank");
            item.cover = getCover(object);
            item.year = getString(object, "year");
            if (item.year.isEmpty() && item.subtitle.length() >= 4 && TextUtils.isDigitsOnly(item.subtitle.substring(0, 4))) item.year = item.subtitle.substring(0, 4);
            JsonElement rating = object.get("rating");
            if (rating != null && rating.isJsonObject()) item.rating = getDouble(rating.getAsJsonObject(), "value");
            return item;
        }

        static Item parseSearch(JsonObject object) {
            Item item = new Item();
            item.id = getString(object, "id");
            item.title = getString(object, "title");
            item.cover = getString(object, "cover");
            item.year = getString(object, "year");
            item.rating = getDouble(object, "rate");
            List<String> people = new ArrayList<>();
            addStrings(people, object.get("directors"));
            addStrings(people, object.get("casts"));
            item.subtitle = TextUtils.join(" / ", people.subList(0, Math.min(3, people.size())));
            return item;
        }

        /** 不同榜单的海报字段不一样：有的在 pic，有的在 cover，有的只有 cover_url。 */
        private static String getCover(JsonObject object) {
            JsonElement pic = object.get("pic");
            if (pic != null && pic.isJsonObject()) {
                String normal = getString(pic.getAsJsonObject(), "normal");
                if (!normal.isEmpty()) return normal;
            }
            JsonElement cover = object.get("cover");
            if (cover != null && cover.isJsonObject()) {
                String url = getString(cover.getAsJsonObject(), "url");
                if (!url.isEmpty()) return url;
            }
            String url = getString(object, "cover_url");
            if (!url.isEmpty()) return url;
            return pic != null && pic.isJsonObject() ? getString(pic.getAsJsonObject(), "large") : "";
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        /** 交给 ImgUtil 的海报地址，已带上 Referer。 */
        public String getPic() {
            return cover.isEmpty() ? "" : cover + "@Referer=" + REFERER;
        }

        public String getYear() {
            return year;
        }

        /** "1994 / 美国 / 剧情 犯罪 / 导演 / 主演"，列表里只留前两段：年份和地区。 */
        public String getBrief() {
            String[] parts = subtitle.split(" / ");
            if (parts.length >= 2) return parts[0] + " · " + parts[1];
            return subtitle;
        }

        public double getRating() {
            return rating;
        }

        public String getIntro() {
            return intro == null ? "" : intro;
        }

        public void setIntro(String intro) {
            this.intro = intro == null ? "" : intro;
        }

        public int getRank() {
            return rank;
        }
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    private static int getInt(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? 0 : element.getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private static double getDouble(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? 0 : element.getAsDouble();
        } catch (Exception e) {
            return 0;
        }
    }

    private static void addPeople(List<String> target, JsonElement element) {
        if (element == null || !element.isJsonArray()) return;
        for (JsonElement item : element.getAsJsonArray()) {
            String name = item.isJsonObject() ? getString(item.getAsJsonObject(), "name")
                    : item.isJsonPrimitive() ? item.getAsString().trim() : "";
            if (!name.isEmpty() && !target.contains(name)) target.add(name);
        }
    }

    private static void addStrings(List<String> target, JsonElement element) {
        if (element == null || !element.isJsonArray()) return;
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonPrimitive()) continue;
            String value = item.getAsString().trim();
            if (!value.isEmpty() && !target.contains(value)) target.add(value);
        }
    }
}
