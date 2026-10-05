package com.fongmi.android.tv.search;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.*;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Util;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** File I/O is serialized off the UI thread; film metadata survives page and process recreation. */
public final class QualityCache {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final int MAX_FILE = 512 * 1024;
    private static File directory() { return new File(App.get().getFilesDir(), "quality-records"); }
    private static File file(String key) { return new File(directory(), Util.md5(key) + ".json"); }
    public static String filmKey(String title, String year, String type) {
        return TitleKey.normalize(title) + "\n" + TitleKey.year(year) + "\n" + TitleKey.kind(type) + "\n" + edition(title);
    }
    private static String edition(String title) {
        String text = com.github.catvod.utils.Trans.t2s(title);
        for (String tag : new String[]{"导演剪辑版", "加长版", "未删减版", "剪辑版"}) if (text.contains(tag)) return tag;
        return "";
    }
    public static String revision(Site site) {
        StringBuilder parsers = new StringBuilder();
        for (Parse parse : VodConfig.get().getSourceParses(site.getKey()))
            parsers.append(parse.getName()).append(parse.getType()).append(parse.getUrl()).append(App.gson().toJson(parse.getExt())).append(parse.getSourceJar());
        return Util.md5(site.getKey() + "\n" + site.getApi() + "\n" + site.getExt() + "\n" + site.getJar()
                + "\n" + site.getHeader() + "\n" + parsers + "\n" + VodConfig.get().getFlags(site.getKey()));
    }
    public static void load(String key, String title, String year, String type, java.util.function.Consumer<QualityMemory> callback) {
        IO.execute(() -> {
            QualityMemory value = null;
            File target = file(key);
            try {
                if (!target.exists()) {
                    // Missing provider metadata must not split an already known film on history resume.
                    File[] files = directory().listFiles((dir, name) -> name.endsWith(".json"));
                    if (files != null) for (File candidate : files) {
                        QualityMemory old = read(candidate);
                        if (old != null && edition(old.title).equals(edition(title)) && TitleKey.normalize(old.title).equals(TitleKey.normalize(title))
                                && TitleKey.sameYear(TitleKey.year(old.year), TitleKey.year(year))
                                && TitleKey.sameKind(TitleKey.kind(old.type), TitleKey.kind(type))) {
                            if (value != null) { value = null; break; } // Ambiguous editions stay separate.
                            value = old;
                        }
                    }
                } else value = read(target);
            } catch (Exception error) { Logger.w("QualityCache: load failed " + error.getClass().getSimpleName()); }
            if (value == null) value = new QualityMemory();
            // The pinned film id owns future writes, including imported legacy cache records.
            if (key.startsWith("film\n") || value.key.isEmpty()) value.key = key;
            if (value.title.isEmpty()) value.title = title;
            if (value.year.isEmpty()) value.year = year;
            if (value.type.isEmpty()) value.type = type;
            QualityMemory result = value; App.post(() -> callback.accept(result));
        });
    }
    private static QualityMemory read(File file) throws Exception {
        if (file.length() <= 0 || file.length() > MAX_FILE) return null;
        QualityMemory value = App.gson().fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), QualityMemory.class);
        return value != null && value.version == 1 && value.candidates != null && value.verified != null && value.checked != null ? value : null;
    }
    public static void save(String key, QualityMemory value) {
        while (value.candidates.size() > 400) dropCandidate(value);

        while (value.verified.size() > 1024) dropEpisode(value);
        while (value.checked.size() > 2048) value.checked.remove(value.checked.keySet().iterator().next());
        String targetKey = value.key.isEmpty() ? key : value.key;
        String snapshot = App.gson().toJson(value);
        while (snapshot.getBytes(StandardCharsets.UTF_8).length > MAX_FILE && !value.verified.isEmpty()) {
            dropEpisode(value); snapshot = App.gson().toJson(value);
        }
        while (snapshot.getBytes(StandardCharsets.UTF_8).length > MAX_FILE && !value.candidates.isEmpty()) {
            dropCandidate(value); snapshot = App.gson().toJson(value);
        }
        String json = snapshot; // Snapshot on the owning main thread.
        IO.execute(() -> {
            try {
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_FILE) { Logger.w("QualityCache: metadata limit"); return; }
                File directory = directory(); directory.mkdirs();
                File target = file(targetKey), pending = new File(directory, target.getName() + ".tmp");
                try (FileOutputStream output = new FileOutputStream(pending)) { output.write(bytes); output.getFD().sync(); }
                java.nio.file.Files.move(pending.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
                if (files == null) return;
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                long total = 0; for (File item : files) total += item.length();
                int count = files.length;
                for (File item : files) {
                    if (total <= 32L * 1024 * 1024 && count <= 160) break;
                    if (!item.equals(target) && item.delete()) { total -= item.length(); count--; }
                }
            } catch (Exception error) { Logger.w("QualityCache: save failed " + error.getClass().getSimpleName()); }
        });
    }
    private static void dropEpisode(QualityMemory value) {
        String episode = value.verified.get(0).episodeKey;
        value.verified.removeIf(item -> item.episodeKey.equals(episode));
        value.checked.keySet().removeIf(key -> !key.startsWith("search\n") && key.contains("\n" + episode + "\n"));
    }
    private static void dropCandidate(QualityMemory value) {
        QualityMemory.Candidate old = value.candidates.remove(0);
        value.verified.removeIf(item -> item.site.equals(old.site) && item.id.equals(old.id));
        value.checked.keySet().removeIf(key -> key.startsWith(old.site + "\n") || key.startsWith("search\n" + old.site + "\n"));
    }
    static VodSource source(QualityMemory.Candidate item) {
        Site site = VodConfig.get().getSite(item.site);
        if (site.isEmpty() || !VodConfig.isSiteEnabled(site) || !revision(site).equals(item.revision)) return null;
        Vod vod = new Vod(); vod.setSite(site); vod.setVodId(item.id); vod.setVodName(item.name); vod.setVodYear(item.year);
        vod.setTypeName(item.type); vod.setVodPic(item.pic); vod.setVodRemarks(item.remarks);
        return new VodSource(vod, item.cost);
    }
    static QualityMemory.Candidate candidate(VodSource source) {
        Vod vod = source.getVod(); QualityMemory.Candidate item = new QualityMemory.Candidate();
        item.site = source.getSiteKey(); item.revision = revision(source.getSite()); item.id = source.getVodId();
        item.name = vod.getVodName(); item.year = vod.getVodYear(); item.type = vod.getTypeName();
        item.pic = vod.getVodPic(); item.remarks = vod.getVodRemarks(); item.cost = source.getCost(); return item;
    }
}
