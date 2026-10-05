package com.fongmi.android.tv.search;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.*;
import com.fongmi.android.tv.model.SiteViewModel;
import com.github.catvod.utils.Logger;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Film-owned silent work. Visibility of the quality menu never starts or cancels a probe. */
public final class QualityCatalog {
    private final ExecutorService selection = Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor worker = (ThreadPoolExecutor) Executors.newFixedThreadPool(6);
    private final Map<String, SourceState> sources = new LinkedHashMap<>();
    private final List<SourceState> queue = new ArrayList<>();
    private final Map<SourceState, Work> running = new LinkedHashMap<>();
    private final Map<Integer, Integer> served = new HashMap<>();
    private final List<QualityOption> items = new ArrayList<>();
    private final Runnable changed, dispatchLater = this::dispatch, saveLater = this::save;
    private final Consumer<List<VodSource>> restored;
    private volatile int generation;
    private volatile boolean released, active;
    private int budget = 6, filmGeneration;
    private boolean ready, movie;
    public boolean isMovie() { return movie; }
    private String episode = "", film = "", title = "", year = "", type = "", aliases = "";
    private String prioritySite = "", priorityVod = "", priorityFlag = "";
    private QualityMemory memory = new QualityMemory();
    private final Map<String, List<QualityProbe.Size>> media = new ConcurrentHashMap<>();

    public QualityCatalog(Runnable changed) { this(changed, values -> {}); }
    public QualityCatalog(Runnable changed, Consumer<List<VodSource>> restored) { this.changed = changed; this.restored = restored; }
    public void film(String name, String date, String kind) {
        film("", name, date, kind);
    }
    public void film(String identity, String name, String date, String kind) {
        film(identity, name, date, kind, "");
    }
    public void film(String identity, String name, String date, String kind, String knownAliases) {
        String key = identity.isEmpty() ? QualityCache.filmKey(name, date, kind) : "film\n" + identity;
        if (film.equals(key)) return;
        App.removeCallbacks(saveLater); save();
        stop(); sources.clear(); items.clear(); served.clear(); media.clear(); clearPriority(); ready = false;
        film = key; title = name; year = date; type = kind; aliases = knownAliases;
        movie = TitleKey.kind(kind) == TitleKey.KIND_MOVIE; memory = new QualityMemory();
        int token = ++filmGeneration;
        QualityCache.load(key, name, date, kind, value -> {
            if (released || token != filmGeneration) return;
            memory = value; ready = true;
            for (SourceState state : sources.values()) cacheCandidate(state.source);
            List<VodSource> cached = cachedSources();
            restoreItems(); restored.accept(cached); dispatch(); changed.run();
            Logger.i("QualityCache: restored candidates=" + cached.size() + " verified=" + items.size());
        });
    }
    public boolean isReady() { return ready; }
    public List<VodSource> cachedSources() {
        List<VodSource> result = new ArrayList<>();
        for (QualityMemory.Candidate item : memory.candidates) {
            VodSource source = QualityCache.source(item);
            if (source != null && accepts(source.getVod())) result.add(source);
        }
        return result;
    }
    public void episode(String name) {
        movie = "movie".equals(name);
        if (Objects.equals(name, episode)) return;
        boolean resume = active; stop(); episode = name; sources.clear(); items.clear(); served.clear(); media.clear(); clearPriority();
        if (ready) restoreItems();
        if (resume) setActive(true);
    }
    public void setActive(boolean value) {
        if (released || active == value) return;
        if (!value) { stop(); return; }
        active = true;
        for (SourceState state : sources.values()) if (!state.done && !queue.contains(state)) queue.add(state);
        App.post(dispatchLater);
    }
    public void budget(int value) {
        budget = Math.max(0, Math.min(6, value));
        // A starving foreground player must stop active probes as well as future dispatches.
        if (running.size() > budget) for (Work work : new ArrayList<>(running.values())) {
            if (running.size() <= budget) break;
            running.remove(work.state); App.removeCallbacks(work.timeout);
            if (work.future != null) work.future.cancel(true);
            if (work.flag != null) work.state.index = Math.max(0, work.state.index - 1);
            if (!queue.contains(work.state)) queue.add(work.state);
        }
        worker.purge(); App.post(dispatchLater);
    }
    private void stop() {
        active = false; generation++; App.removeCallbacks(dispatchLater);
        for (Work work : running.values()) {
            App.removeCallbacks(work.timeout); if (work.future != null) work.future.cancel(true);
            if (work.flag != null) work.state.index = Math.max(0, work.state.index - 1);
        }
        queue.clear(); running.clear(); worker.purge();
    }
    public boolean isInspecting() { return active && (!queue.isEmpty() || !running.isEmpty()); }
    public List<QualityOption> items() { return new ArrayList<>(items); }
    /** Retry old successful measurements on this episode without discarding valid film/episode metadata. */
    public void refreshMeasurements(boolean force) {
        refreshMeasurements(force, null, "");
    }
    /** Put the currently playing route first so opening the chooser produces useful numbers quickly. */
    public void refreshMeasurements(boolean force, VodSource priority, String flagName) {
        long now = System.currentTimeMillis();
        prioritySite = priority == null ? "" : priority.getSiteKey();
        priorityVod = priority == null ? "" : priority.getVodId();
        priorityFlag = flagName == null ? "" : flagName;
        // A user refresh must issue new media requests, never re-date an in-memory speed sample.
        media.clear();
        if (force) {
            List<QualityOption> reset = new ArrayList<>();
            for (QualityOption item : items) {
                QualityOption copy = new QualityOption(item.source, item.detail, item.flag, item.episode, item.valueName,
                        item.valueIndex, item.width, item.height, item.verified, 0, item.bitrate);
                copy.verifiedUrl = item.verifiedUrl; copy.verifiedHeaders = item.verifiedHeaders; copy.verifiedAt = item.verifiedAt;
                reset.add(copy);
            }
            items.clear(); items.addAll(reset);
            for (QualityMemory.Verified item : memory.verified) if (item.episodeKey.equals(episode)) {
                item.speed = 0; item.measuredAt = 0; item.latencyMs = -1;
            }
        }
        int queued = 0;
        for (SourceState state : sources.values()) {
            if (running.containsKey(state) || state.source.isBroken()) continue;
            boolean fresh = false;
            for (QualityOption item : items) if (item.source.same(state.source.getSiteKey(), state.source.getVodId())
                    && (!priority(state) || priorityFlag.isEmpty() || priorityFlag.equals(item.flag.getFlag()))
                    && SourceSelection.fresh(item.measuredAt, now)) { fresh = true; break; }
            if (!force && fresh) continue;
            memory.checked.entrySet().removeIf(entry -> (force || entry.getValue() == Long.MAX_VALUE)
                    && (entry.getKey().equals(state.key("source")) || entry.getKey().startsWith(state.key("line\n"))));
            state.index = 0; state.done = false; state.failed = false;
            state.prioritize(priority(state) ? priorityFlag : "");
            if (!queue.contains(state)) queue.add(state);
            queued++;
        }
        Logger.i("SourceProbe: action=refresh force=" + force + " episode=" + episode + " queued=" + queued);
        // Known nodes for this episode are more useful than another speculative detail lookup.
        queue.sort((a, b) -> {
            int priorityOrder = Boolean.compare(!priority(a), !priority(b));
            if (priorityOrder != 0) return priorityOrder;
            boolean knownA = false, knownB = false;
            for (QualityOption item : items) {
                knownA |= item.source.same(a.source.getSiteKey(), a.source.getVodId());
                knownB |= item.source.same(b.source.getSiteKey(), b.source.getVodId());
            }
            int known = Boolean.compare(knownB, knownA);
            return known != 0 ? known : Long.compare(b.source.getSpeed(), a.source.getSpeed());
        });
        dirty(); App.post(dispatchLater);
    }
    private boolean priority(SourceState state) {
        return state != null && state.source.same(prioritySite, priorityVod);
    }
    private void clearPriority() { prioritySite = priorityVod = priorityFlag = ""; }
    // Cached search results (including misses) prevent re-searching the same film on every visit.
    public List<Site> pendingSites(List<Site> sites) {
        List<Site> result = new ArrayList<>();
        for (Site site : sites) if (!checked(searchKey(site))) result.add(site);
        return result;
    }
    public void searchComplete(Site site, boolean failed) {
        remember(searchKey(site), failed ? System.currentTimeMillis() + 30 * 60_000 : Long.MAX_VALUE);
    }
    private String searchKey(Site site) { return "search\n" + site.getKey() + "\n" + QualityCache.revision(site); }
    private boolean checked(String key) { return memory.checked.getOrDefault(key, 0L) > System.currentTimeMillis(); }
    private void remember(String key, long until) { memory.checked.remove(key); memory.checked.put(key, until); dirty(); }
    private void dirty() { if (ready) App.post(saveLater, 600); }
    private void save() { if (ready && !film.isEmpty()) QualityCache.save(film, memory); }
    public void inspect(VodSource source, Vod known, String episodeName) {
        String key = source.getSiteKey() + "\n" + source.getVodId();
        if (released || !active || source.isBroken() || VodConfig.get().getSite(source.getSiteKey()).isEmpty()
                || source.getSite().isCloudDrive() || sources.containsKey(key)) return;
        SourceState state = new SourceState(source, known, episodeName);
        sources.put(key, state);
        if (ready) cacheCandidate(source);
        if (ready && checked(state.key("source"))) { state.done = true; return; }
        queue.add(state); App.post(dispatchLater);
    }
    private void cacheCandidate(VodSource source) {
        QualityMemory.Candidate item = QualityCache.candidate(source);
        memory.candidates.removeIf(old -> old.site.equals(item.site) && old.id.equals(item.id));
        memory.candidates.add(item); dirty();
    }
    private final class SourceState {
        final VodSource source; final String name, revision; Vod vod;
        final List<Flag> flags = new ArrayList<>();
        int index; boolean done, initialized, failed;
        SourceState(VodSource source, Vod vod, String name) { this.source = source; this.vod = vod; this.name = name; revision = QualityCache.revision(source.getSite()); }
        String key(String stage) { return source.getSiteKey() + "\n" + revision + "\n" + source.getVodId() + "\n" + episode + "\n" + stage; }
        String flagKey(Flag flag) {
            Episode selected = match(flag, name, isMovie());
            return key("line\n" + flag.getFlag() + "\n" + (selected == null ? "" : selected.getUrl()));
        }
        void initialize(Vod value) {
            vod = value; vod.setSite(source.getSite()); initialized = true;
            for (Flag flag : vod.getVodFlags()) if (!flag.isCloudDrive() && !PlaybackRoutePolicy.isRestricted(flag)
                    && match(flag, name, isMovie()) != null) flags.add(flag);
            prioritize(priority(this) ? priorityFlag : "");
        }
        void prioritize(String name) {
            if (name == null || name.isEmpty() || flags.size() < 2) return;
            for (int i = 0; i < flags.size(); i++) if (name.equals(flags.get(i).getFlag())) {
                if (i > 0) flags.add(0, flags.remove(i));
                return;
            }
        }
        boolean isMovie() { return movie || "movie".equals(episode); }
    }
    private final class Work {
        final SourceState state; final Flag flag; final int token = generation;
        final String episodeKey = episode;
        Future<?> future; volatile String stage = "queued"; volatile long started;
        final Runnable timeout = () -> complete(this, null, Collections.emptyList(), stage + "-timeout");
        Work(SourceState state, Flag flag) { this.state = state; this.flag = flag; }
        void run() {
            if (!valid()) return;
            started = System.nanoTime();
            App.post(() -> { if (valid() && running.get(state) == this) App.post(timeout, flag == null ? 12000 : 60000); });
            Vod fetched = null; List<QualityOption> options = new ArrayList<>(); String reason = "";
            try {
                if (flag == null) {
                    stage = "detail";
                    Result result = SiteViewModel.detail(state.source.getSite(), state.source.getVodId(), false);
                    if (!result.getList().isEmpty()) {
                        Vod candidate = result.getList().get(0);
                        candidate.setSite(state.source.getSite());
                        if (candidate.getVodId().isEmpty()) candidate.setVodId(state.source.getVodId());
                        if (candidate.getVodName().isEmpty()) candidate.setVodName(state.source.getVod().getVodName());
                        if (accepts(candidate)) fetched = candidate; else reason = "different-film";
                    } else reason = "empty-detail";
                } else {
                    Episode selected = match(flag, state.name, state.isMovie());
                    stage = "player";
                    Result player = SiteViewModel.probePlayer(state.source.getSite(), flag.getFlag(), selected.getUrl());
                    for (int i = 0; i < player.getUrl().getValues().size() && valid(); i++) {
                        try {
                            stage = "resolve";
                            QualityResolver.Address address = QualityResolver.resolve(player, player.getUrl().v(i));
                            if (!address.url.startsWith("http")) { reason = "non-http"; continue; }
                            stage = "media";
                            String addressKey = address.url + "\n" + new TreeMap<>(address.headers);
                            List<QualityProbe.Size> sizes = media.get(addressKey);
                            if (sizes == null) {
                                sizes = QualityProbe.inspect(address.url, address.headers);
                                if (!sizes.isEmpty() && valid()) media.put(addressKey, sizes);
                            }
                            List<QualityOption> additions = new ArrayList<>();
                            for (QualityProbe.Size size : sizes) {
                                QualityOption item = new QualityOption(state.source, state.vod, flag, selected, player.getUrl().n(i), i,
                                        size.width, size.height, true, size.speed, size.bitrate);
                                item.verifiedUrl = size.playbackUrl; item.verifiedHeaders = new HashMap<>(address.headers);
                                item.latencyMs = size.latencyMs; item.measuredAt = System.currentTimeMillis();
                                Logger.i("SourceProbe: source=" + state.source.getSiteKey() + " line=" + flag.getFlag()
                                        + " episode=" + episodeKey + " latencyMs=" + item.latencyMs
                                        + " bytesPerSecond=" + item.speed + " bitrate=" + item.bitrate + " size=" + item.width + "x" + item.height);
                                options.add(item); additions.add(item);
                            }
                            if (sizes.isEmpty()) reason = "no-media-dimensions";
                            App.post(() -> {
                                if (!valid() || running.get(state) != this) return;
                                for (QualityOption item : additions) record(item, episodeKey);
                                if (!additions.isEmpty()) changed.run();
                            });
                        } catch (Exception error) { reason = error.getClass().getSimpleName(); }
                    }
                    if (options.isEmpty() && reason.isEmpty()) reason = "no-media-dimensions";
                }
            } catch (Throwable error) { reason = error.getClass().getSimpleName(); }
            Vod value = fetched; String outcome = reason;
            App.post(() -> complete(this, value, options, outcome));
        }
        boolean valid() { return !released && active && token == generation && !Thread.currentThread().isInterrupted(); }
    }
    private void dispatch() {
        if (!ready) return;
        while (active && running.size() < budget && !queue.isEmpty()) {
            // Configurations get equal turns, then stations/lines rotate within their configuration.
            SourceState state = queue.stream().min(Comparator
                    .comparingInt((SourceState s) -> priority(s) ? 0 : 1)
                    .thenComparingInt(s -> served.getOrDefault(s.source.getSite().getSourceId(), 0))).get();
            queue.remove(state);
            if (state.done || state.source.isBroken() || VodConfig.get().getSite(state.source.getSiteKey()).isEmpty()) continue;
            cacheCandidate(state.source);
            if (checked(state.key("source"))) { state.done = true; continue; }
            if (state.vod != null && !state.initialized) state.initialize(state.vod);
            while (state.initialized && state.index < state.flags.size() && checked(state.flagKey(state.flags.get(state.index)))) {
                if (memory.checked.get(state.flagKey(state.flags.get(state.index))) != Long.MAX_VALUE) state.failed = true;
                state.index++;
            }
            if (state.initialized && state.index >= state.flags.size()) {
                state.done = true;
                remember(state.key("source"), state.failed || state.flags.isEmpty() ? System.currentTimeMillis() + 30 * 60_000 : Long.MAX_VALUE);
                continue;
            }
            int config = state.source.getSite().getSourceId(); served.put(config, served.getOrDefault(config, 0) + 1);
            Flag flag = state.initialized ? state.flags.get(state.index++) : null;
            Work work = new Work(state, flag); running.put(state, work); work.future = worker.submit(work::run);
        }
    }
    private void complete(Work work, Vod fetched, List<QualityOption> values, String reason) {
        if (released || !active || work.token != generation || running.get(work.state) != work) return;
        running.remove(work.state); App.removeCallbacks(work.timeout);
        if (reason.endsWith("-timeout") && work.future != null) { work.future.cancel(true); worker.purge(); }
        for (QualityOption item : values) record(item, work.episodeKey);
        long elapsed = work.started == 0 ? 0 : (System.nanoTime() - work.started) / 1_000_000;
        Logger.i("QualityCatalog: site=" + work.state.source.getSiteKey() + " stage=" + work.stage + " elapsedMs=" + elapsed
                + " verified=" + values.size() + (reason.isEmpty() ? "" : " reason=" + reason));
        if (!reason.isEmpty()) work.state.failed = true;
        if (work.flag == null) {
            if (fetched == null) { work.state.done = true; remember(work.state.key("source"), System.currentTimeMillis() + 30 * 60_000); }
            else work.state.initialize(fetched);
        } else remember(work.state.flagKey(work.flag), !values.isEmpty() && reason.isEmpty() ? Long.MAX_VALUE : System.currentTimeMillis() + 30 * 60_000);
        if (priority(work.state) && (work.flag != null || fetched == null)) clearPriority();
        if (!work.state.done) queue.add(work.state);
        dispatch(); changed.run();
    }
    /** Current decoder dimensions are authoritative and require no second network request. */
    public void record(QualityOption item) {
        if (!ready || item == null) return;
        for (QualityOption old : items) if (old.identity().equals(item.identity())) {
            old.verifiedUrl = item.verifiedUrl; old.verifiedHeaders = item.verifiedHeaders; old.verifiedAt = item.verifiedAt;
            return;
        }
        record(item, episode); changed.run();
    }
    private void record(QualityOption item, String episodeKey) {
        if (!item.verified || item.rank() <= 0) return;
        item.source.setResult(VodSource.OK, Math.max(item.speed, item.source.getSpeed()));
        cacheCandidate(item.source);
        items.removeIf(old -> old.identity().equals(item.identity())); items.add(item);
        QualityMemory.Verified value = new QualityMemory.Verified();
        value.site = item.source.getSiteKey(); value.revision = QualityCache.revision(item.source.getSite()); value.id = item.source.getVodId();
        value.episodeKey = episodeKey; value.flag = item.flag.getFlag(); value.episodeName = item.episode.getName(); value.episodeUrl = item.episode.getUrl();
        value.valueName = item.valueName; value.valueIndex = item.valueIndex; value.width = item.width; value.height = item.height;
        value.speed = item.speed; value.bitrate = item.bitrate; value.at = item.verifiedAt;
        value.latencyMs = item.latencyMs; value.measuredAt = item.measuredAt;
        memory.verified.removeIf(old -> old.site.equals(value.site) && old.id.equals(value.id) && old.episodeKey.equals(value.episodeKey)
                && old.flag.equals(value.flag) && old.valueIndex == value.valueIndex && old.width == value.width && old.height == value.height);
        memory.verified.add(value);
        dirty();
    }
    private void restoreItems() {
        items.clear();
        Map<String, VodSource> cached = new HashMap<>();
        for (VodSource source : cachedSources()) cached.put(source.getSiteKey() + "\n" + source.getVodId(), source);
        for (QualityMemory.Verified value : memory.verified) {
            if (!value.episodeKey.equals(episode) || value.width <= 0 || value.height <= 0
                    || PlaybackRoutePolicy.isRestricted(value.flag) || PlaybackRoutePolicy.isRestricted(value.valueName)) continue;
            VodSource source = cached.get(value.site + "\n" + value.id);
            if (source == null || !QualityCache.revision(source.getSite()).equals(value.revision)) continue;
            Episode selected = Episode.create(value.episodeName, value.episodeUrl);
            Flag flag = Flag.create(value.flag); flag.getEpisodes().add(selected);
            QualityOption item = new QualityOption(source, source.getVod(), flag, selected, value.valueName, value.valueIndex,
                    value.width, value.height, true, System.currentTimeMillis() - value.at > 24 * 3600_000L ? 0 : value.speed, value.bitrate);
            item.verifiedAt = value.at; items.add(item);
            item.latencyMs = value.latencyMs; item.measuredAt = value.measuredAt;
        }
    }
    /** Only a user-selected stale address is refreshed; durable dimensions for other lines remain intact. */
    public void refreshSelection(QualityOption option, Result player, Consumer<Boolean> callback) {
        int filmToken = filmGeneration; String episodeToken = episode;
        selection.execute(() -> {
            boolean success = false; String reason = "", resolvedUrl = ""; Map<String, String> resolvedHeaders = Collections.emptyMap();
            try {
                if (released) return;
                QualityResolver.Address address = QualityResolver.resolve(player, player.getUrl().v());
                for (QualityProbe.Size size : QualityProbe.inspect(address.url, address.headers)) {
                    if (size.width != option.width || size.height != option.height) continue;
                    resolvedUrl = size.playbackUrl; resolvedHeaders = new HashMap<>(address.headers); success = true; break;
                }
                if (!success) reason = "requested-size-unavailable";
            } catch (Throwable error) { reason = error.getClass().getSimpleName(); }
            boolean result = success; String outcome = reason, url = resolvedUrl; Map<String, String> headers = resolvedHeaders;
            App.post(() -> {
                if (released || filmToken != filmGeneration || !episodeToken.equals(episode)) return;
                if (result) { option.verifiedUrl = url; option.verifiedHeaders = headers; option.verifiedAt = System.currentTimeMillis(); }
                if (!result) {
                    items.removeIf(item -> item.identity().equals(option.identity()));
                    memory.verified.removeIf(item -> item.site.equals(option.source.getSiteKey()) && item.id.equals(option.source.getVodId())
                            && item.episodeKey.equals(episode) && item.flag.equals(option.flag.getFlag())
                            && item.width == option.width && item.height == option.height);
                    memory.checked.keySet().removeIf(key -> key.startsWith(option.source.getSiteKey() + "\n") && key.contains("\n" + episode + "\n"));
                    dirty(); changed.run();
                    Logger.i("QualityCatalog: selected-address reason=" + outcome);
                }
                callback.accept(result);
            });
        });
    }

    public static Episode match(Flag flag, String name, boolean movie) {
        if (movie && flag.getEpisodes().size() == 1) return flag.getEpisodes().get(0);
        String key = EpisodeKey.key(name);
        for (Episode item : flag.getEpisodes()) if (!key.isEmpty() && key.equals(EpisodeKey.key(item.getName()))) return item;
        if (flag.getEpisodes().size() == 1 && EpisodeKey.movieLabel(name) && EpisodeKey.movieLabel(flag.getEpisodes().get(0).getName())) return flag.getEpisodes().get(0);
        return name.isEmpty() && !flag.getEpisodes().isEmpty() ? flag.getEpisodes().get(0) : null;
    }
    private boolean accepts(Vod vod) {
        if (!HistoryIdentity.sameTitle(title, vod.getVodName())) return false;
        String provider = vod.getSiteKey() + com.fongmi.android.tv.db.AppDatabase.SYMBOL + vod.getVodId()
                + com.fongmi.android.tv.db.AppDatabase.SYMBOL + vod.getSite().getSourceId();
        if (FilmIdentity.aliases(aliases).contains(HistoryIdentity.sourceToken(provider))) return true;
        if (!TitleKey.sameYear(TitleKey.year(year), TitleKey.year(vod.getVodYear()))) return false;
        int count = 0;
        for (Flag flag : vod.getVodFlags()) if (!flag.isCloudDrive()) count = Math.max(count, flag.getEpisodes().size());
        int kind = TitleKey.kind(vod.getTypeName());
        if (count > 1 && kind == TitleKey.KIND_MOVIE) kind = TitleKey.KIND_UNKNOWN;
        return TitleKey.sameKind(TitleKey.kind(type), kind);
    }
    public void release() {
        if (released) return;
        App.removeCallbacks(saveLater); save(); stop(); released = true;
        worker.shutdownNow(); selection.shutdownNow(); items.clear(); sources.clear();
    }
}
