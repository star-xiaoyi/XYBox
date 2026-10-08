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
    private ThreadPoolExecutor worker = newWorker();
    private final List<ThreadPoolExecutor> retiredWorkers = new ArrayList<>();
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
    private volatile int minimumEpisodes;
    private boolean ready, movie;
    public boolean isMovie() { return movie; }
    private String episode = "", film = "", title = "", year = "", type = "", aliases = "";
    private SourceDiscovery identity = new SourceDiscovery("", Collections.emptyList(), 0, TitleKey.KIND_UNKNOWN);
    private String prioritySite = "", priorityVod = "", priorityFlag = "";
    private QualityMemory memory = new QualityMemory();
    private final Map<String, QualityProbe.Measurement> media = new ConcurrentHashMap<>();

    public QualityCatalog(Runnable changed) { this(changed, values -> {}); }
    public QualityCatalog(Runnable changed, Consumer<List<VodSource>> restored) { this.changed = changed; this.restored = restored; }
    private static ThreadPoolExecutor newWorker() { return (ThreadPoolExecutor) Executors.newFixedThreadPool(6); }
    public void film(String name, String date, String kind) {
        film("", name, date, kind);
    }
    public void film(String identity, String name, String date, String kind) {
        film(identity, name, date, kind, "");
    }
    public void film(String identity, String name, String date, String kind, String knownAliases) {
        film(identity, name, date, kind, knownAliases, Collections.emptyList());
    }
    public void film(String identity, String name, String date, String kind, String knownAliases, Collection<String> titleAliases) {
        String key = identity.isEmpty() ? QualityCache.filmKey(name, date, kind) : "film\n" + identity;
        SourceDiscovery nextIdentity = new SourceDiscovery(name, titleAliases, TitleKey.year(date), TitleKey.kind(kind));
        if (film.equals(key)) {
            aliases = knownAliases;
            if (!this.identity.scope().equals(nextIdentity.scope())) {
                this.identity = nextIdentity;
                if (ready) { restored.accept(cachedSources()); changed.run(); }
            }
            return;
        }
        App.removeCallbacks(saveLater); save();
        stop(); sources.clear(); items.clear(); served.clear(); media.clear(); clearPriority(); ready = false;
        film = key; title = name; year = date; type = kind; aliases = knownAliases;
        this.identity = nextIdentity;
        minimumEpisodes = 0;
        movie = TitleKey.kind(kind) == TitleKey.KIND_MOVIE; memory = new QualityMemory();
        int token = ++filmGeneration;
        QualityCache.load(key, name, date, kind, value -> {
            if (released || token != filmGeneration) return;
            memory = value; ready = true;
            minimumEpisodes = Math.max(minimumEpisodes, memory.minimumEpisodes);
            memory.minimumEpisodes = minimumEpisodes;
            for (SourceState state : sources.values()) cacheCandidate(state.source);
            List<VodSource> cached = cachedSources();
            restoreItems(); restored.accept(cached); dispatch(); changed.run();
            Logger.i("QualityCache: restored candidates=" + cached.size() + " verified=" + items.size());
        });
    }
    public boolean isReady() { return ready; }
    public int minimumEpisodes() { return minimumEpisodes; }
    public void expectEpisodes(int count) {
        if (count <= minimumEpisodes) return;
        minimumEpisodes = count;
        memory.minimumEpisodes = count;
        dirty();
    }
    /** Configuration loading may make previously unavailable cached stations available later. */
    public void reloadAvailable() {
        if (!ready || released) return;
        restoreItems(false);
        restored.accept(cachedSources());
        changed.run();
    }
    public boolean needsVerification(QualityOption item) {
        String prefix = item.source.getSiteKey() + "\n" + QualityCache.revision(item.source.getSite())
                + "\n" + item.source.getVodId() + "\n" + episode + "\n";
        String key = prefix + "line\n" + item.flag.getFlag() + "\n" + item.episode.getUrl();
        // The deadline allows probing again; only new successful evidence removes the failed recommendation.
        return memory.cooldowns.containsKey(key) || memory.cooldowns.containsKey(prefix + "source");
    }
    public boolean coolingDown(VodSource source) {
        String key = source.getSiteKey() + "\n" + QualityCache.revision(source.getSite())
                + "\n" + source.getVodId() + "\n" + episode + "\nsource";
        return CandidateRefreshPolicy.checked(memory.cooldowns.getOrDefault(key, 0L), System.currentTimeMillis());
    }
    public boolean completeEpisodes(QualityOption item) {
        return isMovie() || EpisodeCoverage.acceptsCurrent(minimumEpisodes(), item.episodeCount, item.episode != null);
    }
    /** A decoder failure also invalidates the old recommendation, without deleting its identity or sample. */
    public void playbackFailed(QualityOption item) {
        if (item != null) playbackFailed(item.source, item.flag, item.episode);
    }
    public void playbackFailed(VodSource source, Flag flag, Episode selected) {
        if (!ready || source == null) return;
        if (flag == null || selected == null) { sourceFailed(source); return; }
        String prefix = source.getSiteKey() + "\n" + QualityCache.revision(source.getSite()) + "\n" + source.getVodId() + "\n" + episode + "\n";
        String key = prefix + "line\n" + flag.getFlag() + "\n" + selected.getUrl();
        long until = CandidateRefreshPolicy.until(System.currentTimeMillis(), false, true);
        memory.cooldowns.put(key, until); remember(key, until);
        remember(prefix + "source", until);
    }
    public void sourceFailed(VodSource source) {
        if (!ready || source == null) return;
        String key = source.getSiteKey() + "\n" + QualityCache.revision(source.getSite())
                + "\n" + source.getVodId() + "\n" + episode + "\nsource";
        long until = CandidateRefreshPolicy.until(System.currentTimeMillis(), false, true);
        memory.cooldowns.put(key, until); remember(key, until);
    }
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
        boolean occupied = !running.isEmpty();
        for (Work work : running.values()) {
            App.removeCallbacks(work.timeout); if (work.future != null) work.future.cancel(true);
            if (work.flag != null) work.state.index = Math.max(0, work.state.index - 1);
        }
        queue.clear(); running.clear(); worker.purge();
        if (occupied) retireWorker();
    }

    /** A third-party spider can ignore interruption. Retire its pool so future sites still start. */
    private void retireWorker() {
        ThreadPoolExecutor old = worker;
        old.shutdownNow();
        retiredWorkers.add(old);
        worker = newWorker();
    }

    private void replacePoisonedWorker() {
        for (Work other : new ArrayList<>(running.values())) {
            running.remove(other.state);
            App.removeCallbacks(other.timeout);
            if (other.future != null) other.future.cancel(true);
            if (other.flag != null) other.state.index = Math.max(0, other.state.index - 1);
            if (!other.state.done && !queue.contains(other.state)) queue.add(other.state);
        }
        retireWorker();
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
        // Keep the last sample visible while a new request is running. Its timestamp stays unchanged.
        int queued = 0;
        for (SourceState state : sources.values()) {
            if (running.containsKey(state) || !force && state.source.isBroken() || !VodConfig.isSiteEnabled(state.source.getSite())) continue;
            if (!force && !state.done) continue;
            if (!force && checked(state.key("source"))) continue;
            boolean fresh = false;
            for (QualityOption item : items) if (item.source.same(state.source.getSiteKey(), state.source.getVodId())
                    && (!priority(state) || priorityFlag.isEmpty() || priorityFlag.equals(item.flag.getFlag()))
                    && SourceSelection.fresh(item.measuredAt, now) && !needsVerification(item)) { fresh = true; break; }
            boolean failed = memory.cooldowns.keySet().stream().anyMatch(key -> key.equals(state.key("source")) || key.startsWith(state.key("line\n")));
            if (!force && fresh && !failed) continue;
            memory.checked.entrySet().removeIf(entry -> (force || !CandidateRefreshPolicy.checked(entry.getValue(), now))
                    && (entry.getKey().equals(state.key("source")) || entry.getKey().startsWith(state.key("line\n"))));
            if (force) state.source.setBroken(false);
            state.index = 0; state.done = false; state.failed = false;
            state.vod = null; state.initialized = false; state.flags.clear();
            if (!queue.contains(state)) queue.add(state);
            queued++;
        }
        if (force || queued > 0) media.clear();
        if (force || queued > 0) Logger.i("SourceProbe: action=refresh force=" + force + " episode=" + episode + " queued=" + queued);
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
        return pendingSites(sites, "");
    }
    public List<Site> pendingSites(List<Site> sites, String scope) {
        List<Site> result = new ArrayList<>();
        for (Site site : sites) if (!checked(searchKey(site, scope))) result.add(site);
        return result;
    }
    public void searchComplete(Site site, boolean failed) {
        searchComplete(site, "", failed);
    }
    public void searchComplete(Site site, String scope, boolean failed) {
        remember(searchKey(site, scope), CandidateRefreshPolicy.until(System.currentTimeMillis(), true, failed));
    }
    private String searchKey(Site site, String scope) {
        return "search\n" + site.getKey() + "\n" + QualityCache.revision(site) + "\n" + scope;
    }
    private boolean checked(String key) { return CandidateRefreshPolicy.checked(memory.checked.getOrDefault(key, 0L), System.currentTimeMillis()); }
    private void remember(String key, long until) { memory.checked.remove(key); memory.checked.put(key, until); dirty(); }
    private void dirty() { if (ready) App.post(saveLater, 600); }
    private void save() { if (ready && !film.isEmpty()) QualityCache.save(film, memory); }
    public void inspect(VodSource source, Vod known, String episodeName) {
        String key = source.getSiteKey() + "\n" + source.getVodId();
        if (released || !active || source.isBroken() || VodConfig.get().getSite(source.getSiteKey()).isEmpty()
                || !VodConfig.isSiteEnabled(source.getSite()) || source.getSite().isCloudDrive()) return;
        SourceState previous = sources.get(key);
        if (previous != null) {
            if (previous.revision.equals(QualityCache.revision(source.getSite()))) {
                if (!previous.done && !running.containsKey(previous) && !queue.contains(previous)) {
                    queue.add(previous); App.post(dispatchLater);
                }
                return;
            }
            Work work = running.remove(previous);
            if (work != null) { App.removeCallbacks(work.timeout); if (work.future != null) work.future.cancel(true); }
            queue.remove(previous);
            items.removeIf(item -> item.source.same(source.getSiteKey(), source.getVodId()));
            known = null;
        }
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
                    && match(flag, name, isMovie()) != null
                    && (isMovie() || EpisodeCoverage.acceptsCurrent(minimumEpisodes(), EpisodeCoverage.count(flag), true))) flags.add(flag);
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
        int detailCount;
        final Runnable timeout = () -> complete(this, null, Collections.emptyList(), stage + "-timeout");
        Work(SourceState state, Flag flag) { this.state = state; this.flag = flag; }
        void run() {
            if (!valid()) return;
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
                        detailCount = EpisodeCoverage.count(candidate);
                        if (!accepts(candidate)) reason = "different-film";
                        else fetched = candidate;
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
                            QualityProbe.Measurement sample = media.get(addressKey);
                            if (sample == null || !sample.fresh(System.currentTimeMillis())) {
                                sample = new QualityProbe.Measurement(QualityProbe.inspect(address.url, address.headers), System.currentTimeMillis());
                                if (!sample.sizes.isEmpty() && valid()) media.put(addressKey, sample);
                            }
                            List<QualityOption> additions = new ArrayList<>();
                            for (QualityProbe.Size size : sample.sizes) {
                                QualityOption item = new QualityOption(state.source, state.vod, flag, selected, player.getUrl().n(i), i,
                                        size.width, size.height, true, size.speed, size.bitrate);
                                item.verifiedUrl = size.playbackUrl; item.verifiedHeaders = new HashMap<>(address.headers);
                                item.latencyMs = size.latencyMs; item.measuredAt = sample.measuredAt;
                                Logger.i("SourceProbe: source=" + state.source.getSiteKey() + " line=" + flag.getFlag()
                                        + " episode=" + episodeKey + " latencyMs=" + item.latencyMs
                                        + " bytesPerSecond=" + item.speed + " bitrate=" + item.bitrate + " size=" + item.width + "x" + item.height
                                        + " episodes=" + item.episodeCount + " requiredEpisodes=" + minimumEpisodes());
                                options.add(item); additions.add(item);
                            }
                            if (sample.sizes.isEmpty()) reason = "no-media-dimensions";
                            App.post(() -> {
                                if (!valid() || running.get(state) != this) return;
                                for (QualityOption item : additions) record(item, episodeKey);
                                if (!additions.isEmpty()) changed.run();
                            });
                        } catch (Exception error) { reason = error.getClass().getSimpleName(); }
                    }
                    if (!options.isEmpty()) reason = "";
                    else if (reason.isEmpty()) reason = "no-media-dimensions";
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
            if (state.done) continue;
            if (state.source.isBroken() || VodConfig.get().getSite(state.source.getSiteKey()).isEmpty()
                    || !VodConfig.isSiteEnabled(state.source.getSite())) { state.done = true; continue; }
            cacheCandidate(state.source);
            if (checked(state.key("source"))) { state.done = true; continue; }
            if (state.vod != null && !state.initialized) state.initialize(state.vod);
            while (state.initialized && state.index < state.flags.size()) {
                Flag pending = state.flags.get(state.index);
                boolean incomplete = !state.isMovie() && !EpisodeCoverage.acceptsCurrent(minimumEpisodes(),
                        EpisodeCoverage.count(pending), match(pending, state.name, state.isMovie()) != null);
                if (!incomplete && !checked(state.flagKey(pending))) break;
                if (incomplete || CandidateRefreshPolicy.checked(memory.cooldowns.getOrDefault(state.flagKey(pending), 0L), System.currentTimeMillis())) state.failed = true;
                state.index++;
            }
            if (state.initialized && state.index >= state.flags.size()) {
                state.done = true;
                remember(state.key("source"), CandidateRefreshPolicy.until(System.currentTimeMillis(), false, state.failed || state.flags.isEmpty()));
                continue;
            }
            int config = state.source.getSite().getSourceId(); served.put(config, served.getOrDefault(config, 0) + 1);
            Flag flag = state.initialized ? state.flags.get(state.index++) : null;
            Work work = new Work(state, flag); running.put(state, work);
            work.started = System.nanoTime();
            App.post(work.timeout, flag == null ? 12000 : 60000);
            work.future = worker.submit(work::run);
        }
    }
    private void complete(Work work, Vod fetched, List<QualityOption> values, String reason) {
        if (released || !active || work.token != generation || running.get(work.state) != work) return;
        running.remove(work.state); App.removeCallbacks(work.timeout);
        if (reason.endsWith("-timeout") && work.future != null) {
            work.future.cancel(true);
            Logger.w("QualityCatalog: isolated poisoned worker stage=" + work.stage + " site=" + work.state.source.getSiteKey());
            replacePoisonedWorker();
        }
        values = new ArrayList<>(values);
        boolean hadValues = !values.isEmpty();
        values.removeIf(item -> !completeEpisodes(item));
        if (hadValues && values.isEmpty()) reason = "incomplete-episodes";
        for (QualityOption item : values) record(item, work.episodeKey);
        long elapsed = work.started == 0 ? 0 : (System.nanoTime() - work.started) / 1_000_000;
        Logger.i("QualityCatalog: site=" + work.state.source.getSiteKey() + " stage=" + work.stage + " elapsedMs=" + elapsed
                + " verified=" + values.size() + " requiredEpisodes=" + minimumEpisodes()
                + " actualEpisodes=" + (work.flag == null ? work.detailCount : EpisodeCoverage.count(work.flag))
                + (reason.isEmpty() ? "" : " reason=" + reason));
        if (!reason.isEmpty()) work.state.failed = true;
        if (work.flag == null) {
            if (fetched == null) {
                work.state.done = true;
                long until = CandidateRefreshPolicy.until(System.currentTimeMillis(), false, true);
                remember(work.state.key("source"), until); memory.cooldowns.put(work.state.key("source"), until);
            } else {
                memory.cooldowns.remove(work.state.key("source"));
                if (!work.state.isMovie()) expectEpisodes(EpisodeCoverage.countMatching(fetched, work.state.name));
                work.state.initialize(fetched);
            }
        } else {
            boolean failed = values.isEmpty() || !reason.isEmpty();
            String key = work.state.flagKey(work.flag);
            remember(key, CandidateRefreshPolicy.until(System.currentTimeMillis(), false, failed));
            if (failed) memory.cooldowns.put(key, CandidateRefreshPolicy.until(System.currentTimeMillis(), false, true));
            else memory.cooldowns.remove(key);
        }
        if (priority(work.state) && (work.flag != null || fetched == null)) clearPriority();
        if (!work.state.done) queue.add(work.state);
        dispatch(); changed.run();
    }
    /** Current decoder dimensions are authoritative and require no second network request. */
    public void record(QualityOption item) {
        if (!ready || item == null || !completeEpisodes(item)) return;
        String prefix = item.source.getSiteKey() + "\n" + QualityCache.revision(item.source.getSite())
                + "\n" + item.source.getVodId() + "\n" + episode + "\n";
        boolean cleared = memory.cooldowns.remove(prefix + "source") != null;
        cleared |= memory.cooldowns.remove(prefix + "line\n" + item.flag.getFlag() + "\n" + item.episode.getUrl()) != null;
        if (cleared) dirty();
        for (QualityOption old : items) if (old.identity().equals(item.identity())) {
            old.verifiedUrl = item.verifiedUrl; old.verifiedHeaders = item.verifiedHeaders; old.verifiedAt = item.verifiedAt;
            if (item.episodeCount > 0) old.episodeCount = item.episodeCount;
            return;
        }
        record(item, episode); changed.run();
    }
    private void record(QualityOption item, String episodeKey) {
        if (!item.verified || item.rank() <= 0 || !completeEpisodes(item)) return;
        item.source.setResult(VodSource.OK, Math.max(item.speed, item.source.getSpeed()));
        cacheCandidate(item.source);
        items.removeIf(old -> old.identity().equals(item.identity())); items.add(item);
        QualityMemory.Verified value = new QualityMemory.Verified();
        value.site = item.source.getSiteKey(); value.revision = QualityCache.revision(item.source.getSite()); value.id = item.source.getVodId();
        value.episodeKey = episodeKey; value.flag = item.flag.getFlag(); value.episodeName = item.episode.getName(); value.episodeUrl = item.episode.getUrl();
        value.valueName = item.valueName; value.valueIndex = item.valueIndex; value.width = item.width; value.height = item.height;
        value.speed = item.speed; value.bitrate = item.bitrate; value.at = item.verifiedAt;
        value.latencyMs = item.latencyMs; value.measuredAt = item.measuredAt;
        value.episodeCount = item.episodeCount;
        memory.verified.removeIf(old -> old.site.equals(value.site) && old.id.equals(value.id) && old.episodeKey.equals(value.episodeKey)
                && old.flag.equals(value.flag) && old.valueIndex == value.valueIndex && old.width == value.width && old.height == value.height);
        memory.verified.add(value);
        dirty();
    }
    private void restoreItems() {
        restoreItems(true);
    }
    private void restoreItems(boolean clear) {
        if (clear) items.clear();
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
            item.verifiedAt = value.at;
            boolean existing = false;
            for (QualityOption old : items) if (old.identity().equals(item.identity())) { existing = true; break; }
            if (existing) continue;
            items.add(item);
            item.latencyMs = value.latencyMs; item.measuredAt = value.measuredAt;
            item.episodeCount = value.episodeCount;
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
                    if ("requested-size-unavailable".equals(outcome)) {
                        // A vanished quality tier is different from a dead node: retain the provider and its other tiers.
                        items.removeIf(item -> item.identity().equals(option.identity()));
                        memory.verified.removeIf(item -> item.site.equals(option.source.getSiteKey()) && item.id.equals(option.source.getVodId())
                                && item.episodeKey.equals(episode) && item.flag.equals(option.flag.getFlag())
                                && item.width == option.width && item.height == option.height);
                        dirty();
                    } else playbackFailed(option);
                    changed.run();
                    Logger.i("QualityCatalog: selected-address reason=" + outcome);
                }
                callback.accept(result);
            });
        });
    }

    public static Episode match(Flag flag, String name, boolean movie) {
        return EpisodeSelection.match(flag, name, movie);
    }
    private boolean accepts(Vod vod) {
        if (!identity.matchesTitle(vod.getVodName())) return false;
        String provider = vod.getSiteKey() + com.fongmi.android.tv.db.AppDatabase.SYMBOL + vod.getVodId()
                + com.fongmi.android.tv.db.AppDatabase.SYMBOL + vod.getSite().getSourceId();
        if (FilmIdentity.aliases(aliases).contains(HistoryIdentity.sourceToken(provider))) return true;
        if (!TitleKey.sameYear(TitleKey.year(year), TitleKey.year(vod.getVodYear()))) return false;
        int count = 0;
        for (Flag flag : vod.getVodFlags()) if (!flag.isCloudDrive()) count = Math.max(count, flag.getEpisodes().size());
        int kind = TitleKey.kind(vod.getTypeName());
        if (count > 1 && kind == TitleKey.KIND_MOVIE) kind = TitleKey.KIND_UNKNOWN;
        return TitleKey.sameSourceKind(TitleKey.kind(type), kind);
    }
    public void release() {
        if (released) return;
        App.removeCallbacks(saveLater); save(); stop(); released = true;
        worker.shutdownNow(); selection.shutdownNow(); items.clear(); sources.clear();
        for (ThreadPoolExecutor value : retiredWorkers) value.shutdownNow();
        retiredWorkers.clear();
    }
}
