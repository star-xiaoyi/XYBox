import com.fongmi.android.tv.search.EpisodeKey;
import com.fongmi.android.tv.search.EpisodeCoverage;
import com.fongmi.android.tv.search.CandidateRefreshPolicy;
import com.fongmi.android.tv.search.FilmIdentity;
import com.fongmi.android.tv.search.PlaybackPolicy;
import com.fongmi.android.tv.search.PlaybackHealth;
import com.fongmi.android.tv.search.QualityOption;
import com.fongmi.android.tv.search.QualityProbe;
import com.fongmi.android.tv.search.QualitySelection;
import com.fongmi.android.tv.search.PlaybackSelection;
import com.fongmi.android.tv.search.DetailLoadPolicy;
import com.fongmi.android.tv.search.ForegroundSyncPolicy;
import com.fongmi.android.tv.search.PlaybackRoutePolicy;
import com.fongmi.android.tv.search.SourceSelection;
import com.fongmi.android.tv.search.SourceDiscovery;
import com.fongmi.android.tv.search.TitleKey;
import com.fongmi.android.tv.utils.SourceUiOrigin;
import com.fongmi.android.tv.utils.NativeCrashTrace;
import com.fongmi.android.tv.api.loader.SourcePluginPolicy;
import com.fongmi.android.tv.bean.SyncProfile;
import com.fongmi.android.tv.bean.SyncSourcePreference;
import com.fongmi.android.tv.bean.QualityMemory;
import java.util.Map;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.tools.ToolProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/** Deterministic checks against the actual release classes; no emulator or debug build. */
class PlaybackPolicyCheck {
    private static int checks;
    private static void equal(Object expected, Object actual, String scenario) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(scenario + ": expected=" + expected + " actual=" + actual);
    }
    public static void main(String[] args) throws Exception {
        SyncProfile cloudProfile = new SyncProfile(), localProfile = new SyncProfile();
        cloudProfile.nickname = "手机昵称"; cloudProfile.nicknameUpdatedAt = 100;
        cloudProfile.avatarBase64 = "aW1hZ2U="; cloudProfile.avatarUpdatedAt = 200;
        localProfile.nickname = "新昵称"; localProfile.nicknameUpdatedAt = 300;
        localProfile.avatarBase64 = "b2xk"; localProfile.avatarUpdatedAt = 50;
        SyncProfile mergedProfile = SyncProfile.merge(cloudProfile, localProfile);
        equal("新昵称", mergedProfile.nickname, "Nickname edit keeps its own revision");
        equal("aW1hZ2U=", mergedProfile.avatarBase64, "Changing nickname cannot roll back a newer cloud avatar");
        localProfile.nickname = "默认账号"; localProfile.nicknameUpdatedAt = 1;
        equal("手机昵称", SyncProfile.merge(cloudProfile, localProfile).nickname, "First device setup restores an established cloud nickname");
        localProfile.nickname = "旧账号"; cloudProfile.nicknameUpdatedAt = 1;
        equal("手机昵称", SyncProfile.merge(cloudProfile, localProfile).nickname, "Legacy initial values converge to existing cloud data");
        equal("旧账号", SyncProfile.merge(null, localProfile).nickname, "Schema without account data seeds from the current local account");
        localProfile.avatarBase64 = ""; localProfile.avatarUpdatedAt = 400;
        equal("", SyncProfile.merge(cloudProfile, localProfile).avatarBase64, "An explicit avatar reset propagates");
        localProfile.avatarBase64 = "file:///data/user/0/another/avatar.jpg";
        equal("aW1hZ2U=", SyncProfile.merge(cloudProfile, localProfile).avatarBase64, "A device-local path cannot become a cloud avatar");
        localProfile.avatarBase64 = "A".repeat(((SyncProfile.MAX_AVATAR_BYTES + 2) / 3) * 4 + 4);
        equal(false, localProfile.hasAvatar(), "Oversized avatar payload is bounded");
        equal(false, new SyncProfile().hasAvatar(), "A default avatar does not overwrite a custom cloud avatar");
        localProfile.avatarBase64 = "b2xk"; localProfile.avatarUpdatedAt = 1;
        cloudProfile.avatarBase64 = ""; cloudProfile.avatarUpdatedAt = 1;
        equal("", SyncProfile.merge(cloudProfile, localProfile).avatarBase64, "The first sender's default avatar remains authoritative over a legacy second device");
        Map<String, SyncSourcePreference> cloudSwitches = new HashMap<>(), localSwitches = new HashMap<>();
        String siteA = "site:xy:11111111111111111111111111111111:瓜子";
        String siteB = "site:xy:22222222222222222222222222222222:瓜子";
        String priorityA = "priority:xy:11111111111111111111111111111111:瓜子";
        cloudSwitches.put(siteA, new SyncSourcePreference(false, 100));
        cloudSwitches.put(siteB, new SyncSourcePreference(true, 50));
        localSwitches.put(priorityA, new SyncSourcePreference(true, 200));
        Map<String, SyncSourcePreference> mergedSwitches = SyncSourcePreference.merge(cloudSwitches, localSwitches);
        equal(false, mergedSwitches.get(siteA).enabled, "Disabled site restores on the second device");
        equal(true, mergedSwitches.get(siteB).enabled, "Same station name in another configuration remains independent");
        equal(true, mergedSwitches.get(priorityA).enabled, "Manual priority syncs independently of enable state");
        localSwitches.put(siteA, new SyncSourcePreference(true, 300));
        equal(true, SyncSourcePreference.merge(cloudSwitches, localSwitches).get(siteA).enabled, "Re-enabling propagates instead of reviving an older disable");
        localSwitches.put(priorityA, new SyncSourcePreference(false, 400));
        equal(false, SyncSourcePreference.merge(cloudSwitches, localSwitches).get(priorityA).enabled, "Removing priority propagates explicitly");
        localSwitches.put(siteA, new SyncSourcePreference(true, 100));
        equal(false, SyncSourcePreference.merge(cloudSwitches, localSwitches).get(siteA).enabled, "Equal migration revisions converge to cloud switch state");
        localSwitches.put("webdav_password", new SyncSourcePreference(true, 500));
        localSwitches.put(siteB, new SyncSourcePreference(false, 0));
        mergedSwitches = SyncSourcePreference.merge(cloudSwitches, localSwitches);
        equal(false, mergedSwitches.containsKey("webdav_password"), "Cloud switches cannot overwrite unrelated preferences");
        equal(true, mergedSwitches.get(siteB).enabled, "Unknown switch revision cannot overwrite a valid state");
        equal(0, SyncSourcePreference.merge(null, null).size(), "Older snapshots without switches remain readable");
        equal("18", EpisodeKey.key("第十八集"), "Chinese numbered episode");
        equal("18", EpisodeKey.key("１８"), "Full width episode");
        equal("18", EpisodeKey.key("EP18"), "English numbered episode");
        equal("s2e18", EpisodeKey.key("S02E18"), "Keep season identity");
        equal("s2e18", EpisodeKey.key("第二季第十八集"), "Chinese season identity");
        equal(-1, EpisodeKey.number("2026-10-04"), "Do not treat dates as episodes");
        equal(-1, EpisodeKey.number("1080p"), "Do not treat resolution as episode");
        equal(-1, EpisodeKey.number("预告第18集"), "Do not confuse trailers with episodes");
        equal(-1, EpisodeKey.number("2026"), "Do not treat years as episodes");

        equal(3, EpisodeCoverage.count(Arrays.asList("01", "第1集", "EP02", "第三集", "预告", "预告第4集", "1080p")),
                "Episode coverage counts semantic episodes and excludes duplicate entries and trailers");
        equal(13, EpisodeCoverage.count(java.util.stream.IntStream.rangeClosed(1, 13).mapToObj(i -> "第" + i + "集").toList()),
                "A complete thirteen-episode season is recognized");
        equal(13, EpisodeCoverage.countMatching(java.util.stream.IntStream.rangeClosed(1, 13).mapToObj(String::valueOf).toList(), "第2集"),
                "An alternative catalog must match the semantic current episode before teaching a full count");
        equal(0, EpisodeCoverage.countMatching(java.util.stream.IntStream.rangeClosed(101, 200).mapToObj(String::valueOf).toList(), "第2集"),
                "An unrelated large catalog cannot inflate the known complete episode count");
        equal(3, EpisodeCoverage.countMatching(Arrays.asList("1", "2", "3"), ""), "A new title can establish its initial catalog");
        equal(0, EpisodeCoverage.countMatching(Arrays.asList("1", "预告第2集"), "第2集"), "A trailer is not evidence for the current episode");
        equal(false, EpisodeCoverage.accepts(13, 3), "A smaller numeric catalog is incomplete without separate current-episode evidence");
        equal(false, EpisodeCoverage.accepts(13, 12), "Missing any previously known episode rejects an incomplete route");
        equal(true, EpisodeCoverage.accepts(13, 0), "Non-numeric episode labels are unknown rather than incomplete");
        equal(true, EpisodeCoverage.acceptsCurrent(100, 13, true), "A stale oversized floor cannot reject a route containing the requested episode");
        equal(false, EpisodeCoverage.acceptsCurrent(100, 13, false), "A smaller numbered catalog without the requested episode remains unsafe");
        equal(true, EpisodeCoverage.accepts(13, 13), "Keep a full alternative catalog");
        equal(true, EpisodeCoverage.accepts(13, 14), "Allow a provider with more episodes");
        equal(true, EpisodeCoverage.accepts(0, 3), "A new series without a baseline can establish its first catalog");
        equal(true, EpisodeCoverage.accepts(1, 0), "Movies are not constrained by series completeness");

        long cycleNow = 1_000_000L;
        long probeUntil = CandidateRefreshPolicy.until(cycleNow, false, false);
        long searchUntil = CandidateRefreshPolicy.until(cycleNow, true, false);
        long failureUntil = CandidateRefreshPolicy.until(cycleNow, false, true);
        equal(false, CandidateRefreshPolicy.checked(Long.MAX_VALUE, cycleNow), "Legacy successful checks never permanently exclude a candidate");
        equal(true, CandidateRefreshPolicy.checked(probeUntil, cycleNow + 119999), "Reuse a recent successful media measurement");
        equal(false, CandidateRefreshPolicy.checked(probeUntil, cycleNow + 120000), "Successful nodes re-enter probing after two minutes");
        equal(true, CandidateRefreshPolicy.checked(searchUntil, cycleNow + 299999), "Avoid repeating a recent successful film search");
        equal(false, CandidateRefreshPolicy.checked(searchUntil, cycleNow + 300000), "Searched stations re-enter discovery after five minutes");
        equal(true, CandidateRefreshPolicy.checked(failureUntil, cycleNow + 59999), "Failures have a bounded cool-down");
        equal(false, CandidateRefreshPolicy.checked(failureUntil, cycleNow + 60000), "Failed nodes can be retried after a minute");
        equal(failureUntil, CandidateRefreshPolicy.until(cycleNow, true, true), "A failed search retries sooner than a successful one");
        equal(0L, CandidateRefreshPolicy.migrate(Long.MAX_VALUE, cycleNow), "Upgrade removes old permanent search/probe exclusions");
        equal(failureUntil, CandidateRefreshPolicy.migrate(cycleNow + 1800000, cycleNow), "Upgrade shortens legacy half-hour failure waits");
        equal(cycleNow + 30000, CandidateRefreshPolicy.migrate(cycleNow + 30000, cycleNow), "Upgrade retains a short active failure delay");
        equal(0L, CandidateRefreshPolicy.migrate(cycleNow - 1, cycleNow), "Expired legacy checks re-enter the queue immediately");

        QualityMemory legacyCandidates = new QualityMemory();
        legacyCandidates.version = 1;
        QualityMemory.Candidate oldSmooth = new QualityMemory.Candidate();
        oldSmooth.site = "old-smooth-provider"; oldSmooth.id = "spy-family-season-3";
        legacyCandidates.candidates.add(oldSmooth);
        QualityMemory.Verified oldSample = new QualityMemory.Verified();
        oldSample.site = oldSmooth.site; oldSample.speed = 3000000; oldSample.latencyMs = 120; oldSample.measuredAt = cycleNow - 30000;
        legacyCandidates.verified.add(oldSample);
        legacyCandidates.checked.put("search-old", Long.MAX_VALUE);
        legacyCandidates.checked.put("probe-old", Long.MAX_VALUE);
        legacyCandidates.checked.put("failed-old", cycleNow + 1800000);
        legacyCandidates.upgrade(cycleNow);
        equal(3, legacyCandidates.version, "Read an existing cache through every schema migration");
        equal(oldSmooth, legacyCandidates.candidates.get(0), "Migration preserves an old smooth provider identity");
        equal(oldSample, legacyCandidates.verified.get(0), "Migration preserves its actual old speed and latency sample");
        equal(cycleNow - 30000, oldSample.measuredAt, "Migration does not pretend an old sample is newly measured");
        equal(false, legacyCandidates.checked.containsKey("search-old"), "Legacy successful search can participate again");
        equal(false, legacyCandidates.checked.containsKey("probe-old"), "Legacy successful probe can participate again");
        equal(failureUntil, legacyCandidates.checked.get("failed-old"), "Legacy failed provider is delayed only a minute");
        QualityMemory poisonedCoverage = new QualityMemory();
        poisonedCoverage.version = 2; poisonedCoverage.minimumEpisodes = 100;
        poisonedCoverage.upgrade(cycleNow);
        equal(3, poisonedCoverage.version, "Episode coverage cache upgrades to the safe schema");
        equal(0, poisonedCoverage.minimumEpisodes, "A legacy provider cannot leave a permanent oversized episode floor");
        legacyCandidates.checked.put("new-probe", probeUntil);
        legacyCandidates.minimumEpisodes = 13;
        legacyCandidates.upgrade(cycleNow);
        equal(probeUntil, legacyCandidates.checked.get("new-probe"), "Reloading new-schema data does not shorten a fresh probe");
        equal(13, legacyCandidates.minimumEpisodes, "Known complete episode coverage survives cache reload");
        legacyCandidates.cooldowns.put("failed-line", cycleNow - 1);
        legacyCandidates.upgrade(cycleNow);
        equal(true, legacyCandidates.cooldowns.containsKey("failed-line"), "An expired retry deadline still requires a new successful verification");

        equal(false, PlaybackPolicy.retryHttpStatus(403), "Permission-denied media addresses require failover immediately");
        equal(false, PlaybackPolicy.retryHttpStatus(404), "Missing media does not reconnect to the same URL six times");
        equal(false, PlaybackPolicy.retryHttpStatus(410), "Expired media addresses need a new route");
        equal(false, PlaybackPolicy.retryHttpStatus(-1), "Unknown bad HTTP response is handed to source recovery");
        equal(true, PlaybackPolicy.retryHttpStatus(408), "A temporary HTTP timeout may reconnect");
        equal(true, PlaybackPolicy.retryHttpStatus(429), "Temporary rate limiting retains bounded reconnect behavior");
        equal(true, PlaybackPolicy.retryHttpStatus(503), "Temporary upstream failure retains bounded reconnect behavior");
        equal(false, PlaybackPolicy.retryHttpStatus(200), "A successful response is not an HTTP failure retry");
        equal(true, PlaybackPolicy.isLocalProxyUrl("http://127.0.0.1:9978/proxy?do=jar&jarKey=x"), "Recognize the local video proxy");
        equal(true, PlaybackPolicy.isLocalProxyUrl("http://localhost:9978/proxy"), "Recognize the localhost alias");
        equal(false, PlaybackPolicy.isLocalProxyUrl("http://127.0.0.1.example.com/v.m3u8"), "A remote hostname is not the local proxy");
        equal(false, PlaybackPolicy.isLocalProxyUrl(null), "A missing URL is not the local proxy");

        equal(true, FilmIdentity.same("film-a", "", "film-a", "", true, false), "Same film survives provider metadata drift");
        equal(true, FilmIdentity.same("film-a", "source:b", "", "source:b", true, false), "Learned provider alias");
        equal(true, FilmIdentity.same("film-a", "film:old", "old", "", true, false), "Keep merged film ids");
        equal(false, FilmIdentity.same("film-a", "source:b", "film-a", "source:b", false, true), "Never merge a different title or edition");
        equal(false, FilmIdentity.same("film-a", "", "film-b", "", true, false), "Do not merge conflicting unlinked remakes");
        equal(true, FilmIdentity.compatibleMetadata(2025, 2026, 2, 2, true, 1500000, 2700000), "Series runtimes do not split histories");
        equal(false, FilmIdentity.compatibleMetadata(1998, 2026, 1, 1, false, 6000000, 6000000), "Separate remakes");
        equal(false, FilmIdentity.compatibleMetadata(2026, 2026, 1, 2, false, 6000000, 6000000), "Separate movie and series");
        equal(false, FilmIdentity.compatibleMetadata(2026, 2026, 1, 1, false, 6000000, 7800000), "Unlinked movie cuts remain separate");

        PlaybackPolicy patient = new PlaybackPolicy();
        patient.begin(0);
        equal("", patient.reason(20000, true, false, false, true, 0, true), "A progressing startup keeps waiting past the grace");
        equal("startup-timeout", patient.reason(45000, true, false, false, true, 0, true), "A progressing startup still has an upper bound");
        PlaybackPolicy stalled = new PlaybackPolicy();
        stalled.begin(0);
        equal("startup-timeout", stalled.reason(12000, true, false, false, true, 0, false), "A stalled startup fails over at the base deadline");

        PlaybackPolicy start = new PlaybackPolicy();
        start.begin(0);
        equal("", start.reason(11999, true, false, false, true, 0), "Bounded startup grace");
        equal("startup-timeout", start.reason(12000, true, false, false, true, 0), "Startup recovery");
        start.ready(12500);
        equal(0, start.stallCount(), "Initial buffering is not a rebuffer");
        equal(12500L, start.firstReadyMs(), "Record startup time");
        start.buffering(20000, true, false);
        equal("", start.reason(27999, true, false, false, true, 0), "Short stall does not switch");
        equal("long-buffering", start.reason(28000, true, false, false, true, 0), "Long stall recovery");
        start.switching(28000);
        start.begin(34000);
        start.ready(35000);
        equal(8000L, start.stalledMs(), "Switch detail loading is excluded from stall time");

        PlaybackPolicy paused = new PlaybackPolicy();
        paused.begin(0);
        equal("", paused.reason(20000, false, false, false, true, 0), "Paused startup never fails over");
        equal("", paused.reason(20500, true, false, false, true, 0), "Resume grace");
        paused.ready(22000);
        paused.buffering(30000, true, false);
        paused.suspend(33000);
        equal("", paused.reason(45000, false, false, false, true, 0), "Paused playback never fails over");
        equal(0, paused.stallCount(), "Paused buffering is not counted");
        paused.seek(50000);
        equal("", paused.reason(51000, true, true, false, true, 0), "Seek preview never fails over");
        equal(0L, paused.stalledMs(), "Seek wait is not counted");

        PlaybackPolicy repeated = new PlaybackPolicy();
        repeated.begin(0); repeated.ready(1000);
        repeated.buffering(5000, true, false); repeated.ready(9500);
        repeated.buffering(15000, true, false); repeated.ready(19500);
        equal("repeated-buffering", repeated.reason(20000, true, false, true, false, 1000), "Frequent short stalls recover");
        equal("", repeated.reason(20000, true, false, true, false, 15000), "Healthy buffer does not switch");
        repeated.seek(21000);
        equal("", repeated.reason(25000, true, false, true, false, 1000), "User seek clears recent stall trigger");
        equal(2, repeated.stallCount(), "Seek preserves diagnostic totals");
        repeated.buffering(30000, true, false); repeated.adjusted(38000);
        equal(17000L, repeated.stalledMs(), "Quality adaptation records the triggering stall");
        equal("", repeated.reason(39000, true, false, false, true, 0), "Track adaptation has a grace period");

        PlaybackPolicy switches = new PlaybackPolicy();
        switches.switched("a", 10000);
        equal(false, switches.canSwitch(24000, false, false), "Hysteresis prevents ping-pong");
        equal(true, switches.canSwitch(25000, false, false), "Switch gap expires");
        equal(false, switches.available("a", 129999), "Failed route cooldown");
        equal(true, switches.available("a", 130000), "Failed route can recover later");
        switches.switched("b", 25000); switches.switched("c", 40000); switches.switched("d", 55000);
        equal(false, switches.canSwitch(60000, false, true), "Hard failures still respect four switches per minute");
        equal(true, switches.canSwitch(60000, true, true), "Explicit source choice can override automatic cap");
        equal(true, switches.canSwitch(71000, false, true), "Automatic cap window expires");
        switches.episode(80000);
        equal(true, switches.available("d", 80000), "A new episode has its own routes");

        equal(0, QualitySelection.smoothness(2000000, 8000000, false), "Measured sustainable throughput");
        equal(3, QualitySelection.smoothness(300000, 8000000, false), "Avoid unsustainable high bitrate");
        equal(0, QualitySelection.smoothness(0, 8000000, true), "Healthy foreground playback is preferred");
        equal(0, QualityOption.tierHeight(0), "Automatic quality persists as zero");
        equal(480, QualityOption.tierHeight(360), "Smooth tier persistence");
        equal(720, QualityOption.tierHeight(576), "HD tier persistence");
        equal(1080, QualityOption.tierHeight(1080), "Full HD tier persistence");
        equal(2160, QualityOption.tierHeight(2160), "4K tier persistence");
        equal(null, PlaybackSelection.item(Collections.emptyList(), 0), "Initial source comparison before episodes are populated");
        equal(null, PlaybackSelection.item(Arrays.asList("episode"), -1), "Previous item while loading");
        equal(null, PlaybackSelection.item(Arrays.asList("episode"), 1), "List changed during detail replacement");
        equal("episode", PlaybackSelection.item(Arrays.asList("episode"), 0), "First episode after catalog is ready");
        DetailLoadPolicy detail = new DetailLoadPolicy();
        equal(true, DetailLoadPolicy.shouldInitializeCatalog(false, false, false), "Process death restoring the player before HomeActivity must start the catalog");
        equal(false, DetailLoadPolicy.shouldInitializeCatalog(false, true, false), "Restoring a second page must not restart an in-flight catalog");
        equal(false, DetailLoadPolicy.shouldInitializeCatalog(true, false, false), "An initialized empty or disabled catalog must not reload forever");
        equal(false, DetailLoadPolicy.shouldInitializeCatalog(false, false, true), "An available catalog is preserved during foreground resume");
        equal(true, detail.awaitConfiguration(0, false, false, true, false, true), "History opened before cached sources finish loading");
        equal(true, detail.awaitConfiguration(111, false, false, true, false, true), "An early empty local lookup must not become an empty film");
        equal(false, detail.awaitConfiguration(239, false, false, true, true, false), "Catalog ready resumes history detail automatically");
        equal(false, detail.awaitConfiguration(300, false, true, true, false, true), "Playable cached episode starts during configuration loading");
        equal(false, detail.awaitConfiguration(400, true, false, true, false, true), "Explicit offline entry does not await a remote catalog");
        equal(false, detail.awaitConfiguration(500, false, false, false, false, true), "Offline history keeps its local recovery path");
        equal(false, detail.awaitConfiguration(600, false, false, true, false, false), "A deleted source enters fallback without waiting for nonexistent configuration work");
        equal(true, detail.awaitConfiguration(1000, false, false, true, false, true), "New configuration wait begins");
        equal(true, detail.awaitConfiguration(12999, false, false, true, false, true), "Configuration wait remains bounded before its deadline");
        equal(false, detail.awaitConfiguration(13000, false, false, true, false, true), "A stalled catalog cannot leave an infinite loading screen");
        detail.reset();
        equal(true, detail.awaitConfiguration(14000, false, false, true, false, true), "A different film has its own loading budget");
        detail.reset();
        equal(true, DetailLoadPolicy.isDiscoveryEntry(""), "Discovery without a provider id is a title-only entry");
        equal(true, DetailLoadPolicy.isDiscoveryEntry("msearch:123"), "Aggregate search ids enter source discovery");
        equal(false, DetailLoadPolicy.isDiscoveryEntry("123"), "A provider detail id keeps its original detail path");
        equal(true, detail.awaitConfiguration(0, false, false, true, false, true, true), "Cold discovery must wait rather than finish a zero-site search");
        equal(true, detail.awaitConfiguration(15000, false, false, true, false, true, true), "Slow initial configuration is not a source buffering failure");
        equal(false, detail.awaitConfiguration(16000, false, false, true, true, false, true), "A ready catalog starts discovery without needing a saved site");
        equal(false, detail.awaitConfiguration(17000, false, false, true, false, false, true), "A completed empty catalog still reports failure rather than wait forever");
        equal(false, detail.awaitConfiguration(18000, false, true, true, false, true, true), "Local playback never waits for source discovery initialization");
        equal(false, detail.awaitConfiguration(19000, false, false, false, false, true, true), "Offline title-only entries retain local fallback");
        ForegroundSyncPolicy syncWait = new ForegroundSyncPolicy();
        syncWait.begin(1000, true);
        equal(8000L, syncWait.remaining(1000), "Foreground cloud progress has an eight second budget");
        equal(false, syncWait.expire(8999), "Fast cloud sync can finish before playback falls back");
        equal(true, syncWait.expire(9000), "An unresponsive cloud service must not keep playback paused");
        equal(false, syncWait.expire(9100), "Timeout releases the wait only once");
        syncWait.begin(10000, true);
        syncWait.finish();
        equal(false, syncWait.expire(60000), "Sync completion or leaving the page cancels delayed resume");
        syncWait.begin(70000, false);
        equal(false, syncWait.expire(80000), "No cloud wait is introduced for local or already playing media");
        syncWait.begin(90000, true);
        equal(true, syncWait.expire(1000000), "A late foreground callback after screen sleep releases an expired wait");
        syncWait.begin(1000100, true);
        equal(false, syncWait.expire(1000101), "A new foreground visit gets its own bounded cloud wait");
        equal(true, SourceSelection.compare(2000000, 4000000, 800, 1000,
                100000, 4000000, 10, 1000, 2000) < 0, "Sustainable source beats low ping with inadequate throughput");
        equal(true, SourceSelection.compare(3000000, 4000000, 50, 1000,
                4000000, 4000000, 900, 1000, 2000) < 0, "Lower latency wins when both sources have ample throughput");
        equal(true, SourceSelection.compare(500000, 2000000, 200, 150000,
                3000000, 4000000, 10, 1000, 150001) < 0, "Fresh measurement beats stale fast cache");
        equal(2, SourceSelection.grade(3000000, 4000000, 0, 2000), "Decoder verification is not a network measurement");
        equal(false, SourceSelection.fresh(5000, 1000), "Clock rollback cannot create fresh measurements");
        equal(true, SourceSelection.fresh(1000, 121000), "Two minute measurement limit inclusive");
        equal(false, SourceSelection.fresh(1000, 121001), "Expired latency is not shown as current");
        var rawSample = new java.util.ArrayList<QualityProbe.Size>();
        rawSample.add(new QualityProbe.Size(1920, 1080, 3000000, 4000000, "", 120));
        QualityProbe.Measurement sharedSample = new QualityProbe.Measurement(rawSample, 1000);
        rawSample.clear();
        equal(1, sharedSample.sizes.size(), "Shared media data is a stable snapshot across worker callbacks");
        equal(3000000L, sharedSample.sizes.get(0).speed, "The cache retains the actual measured throughput");
        equal(120L, sharedSample.sizes.get(0).latencyMs, "The cache retains the actual measured first-byte time");
        equal(true, sharedSample.fresh(121000), "A shared media URL may reuse a recent network sample");
        equal(1000L, sharedSample.measuredAt, "Reuse after pause cannot re-date an old measurement");
        equal(false, sharedSample.fresh(121001), "An expired shared URL sample requires a new media request");
        equal(false, sharedSample.fresh(999), "Clock rollback cannot make shared media samples fresh");
        PlaybackHealth noVideo = new PlaybackHealth();
        noVideo.begin(0); noVideo.ready(0, 0);
        equal("", noVideo.reason(4999, true, false, true, false, false, 0), "Give track discovery a bounded grace period");
        equal("missing-video-track", noVideo.reason(5000, true, false, true, false, false, 0), "Audio-only READY is not watchable video");
        PlaybackHealth noFrame = new PlaybackHealth();
        noFrame.begin(0); noFrame.ready(0, 0);
        equal("first-frame-timeout", noFrame.reason(10000, true, false, true, false, true, 0), "A video track without a rendered frame fails over");
        PlaybackHealth moving = new PlaybackHealth();
        moving.begin(0); moving.ready(0, 0); moving.firstFrame(1000, 0);
        equal("", moving.reason(2000, true, false, true, false, true, 800), "Rendered video with advancing time becomes healthy");
        equal(true, moving.isHealthy(), "Healthy playback is latched and protected");
        PlaybackHealth frozen = new PlaybackHealth();
        frozen.begin(0); frozen.ready(0, 0); frozen.firstFrame(1000, 0);
        equal("frozen-playback", frozen.reason(8000, true, false, true, false, true, 0), "A first frame with no progress is not a successful stream");
        PlaybackHealth pausedStartup = new PlaybackHealth();
        pausedStartup.begin(0); pausedStartup.ready(0, 0);
        equal("", pausedStartup.reason(4000, false, false, true, false, true, 0), "A user pause suspends the first-frame deadline");
        equal("", pausedStartup.reason(20000, true, false, true, false, true, 0), "Resuming after a long pause keeps the remaining grace period");
        equal("first-frame-timeout", pausedStartup.reason(26000, true, false, true, false, true, 0), "Only active playback time consumes the remaining frame grace");
        PlaybackHealth backgroundStartup = new PlaybackHealth();
        backgroundStartup.begin(0); backgroundStartup.ready(0, 0); backgroundStartup.suspend(1000); backgroundStartup.resume(31000);
        equal("", backgroundStartup.reason(39000, true, false, true, false, true, 0), "A lifecycle gap cannot immediately reject a source on return");
        equal("first-frame-timeout", backgroundStartup.reason(40000, true, false, true, false, true, 0), "Lifecycle suspension preserves rather than resets the deadline");
        equal(true, PlaybackHealth.suspiciousDuration(30000, 0), "A thirty-second preview cannot end as a full film");
        equal(true, PlaybackHealth.suspiciousDuration(120000, 7200000), "A route far shorter than known history is rejected");
        equal(false, PlaybackHealth.suspiciousDuration(7000000, 7200000), "A comparable full duration remains valid");

        SourceDiscovery aliases = new SourceDiscovery("流浪地球2 / The Wandering Earth II", Collections.emptyList(), 2023, TitleKey.KIND_MOVIE);
        equal("流浪地球2", aliases.queries().get(0), "A concrete title alias is searched before a combined display label");
        equal(true, aliases.matchesTitle("The Wandering Earth II"), "A provider may use the English alias only");
        equal(true, aliases.matchesTitle("流浪地球2 4K"), "Quality suffixes do not split a title alias");
        equal(false, aliases.matchesTitle("流浪地球"), "A sequel is never folded into the original film");
        equal(true, TitleKey.sameSourceKind(TitleKey.KIND_ANIME, TitleKey.KIND_TV), "Anime filed as TV remains discoverable");
        equal(true, TitleKey.sameSourceKind(TitleKey.KIND_DOC, TitleKey.KIND_MOVIE), "A documentary film category mismatch remains discoverable");
        equal(false, TitleKey.sameSourceKind(TitleKey.KIND_MOVIE, TitleKey.KIND_TV), "Movie and ordinary series stay separated");
        equal(TitleKey.KIND_UNKNOWN, TitleKey.kind("国产//影片"), "Generic history category cannot reject the same anime as a movie mismatch");
        equal(TitleKey.KIND_MOVIE, TitleKey.kind("动作片"), "Specific film genres remain movie evidence");
        SourceDiscovery learnedAlias = new SourceDiscovery("流浪地球2", Arrays.asList("The Wandering Earth II"), 2023, TitleKey.KIND_MOVIE);
        equal(false, aliases.scope().equals(new SourceDiscovery("流浪地球2", Collections.emptyList(), 2023, TitleKey.KIND_MOVIE).scope()),
                "A newly learned title alias re-opens completed site searches");
        equal(true, learnedAlias.matchesTitle("The Wandering Earth II"), "Metadata aliases participate in source matching");
        equal(true, PlaybackRoutePolicy.isRestricted("ZJ-4K-限"), "Reject preview-only 4K route");
        equal(true, PlaybackRoutePolicy.isRestricted("NB-蓝光-限"), "Reject preview-only blue-ray route");
        equal(true, PlaybackRoutePolicy.isRestricted("扫码观看完整版"), "Reject QR-code route");
        equal(false, PlaybackRoutePolicy.isRestricted("免费分享！切勿上当！"), "Keep an ordinary warning label");
        checkPreviewRestored();
        checkDetailExhaustion();
        checkNativeCrashTrace();
        checkSourceOrigin();
        System.out.println("Playback and source UI checks passed: " + checks);
    }

    private static void checkPreviewRestored() throws Exception {
        String activity = Files.readString(Path.of("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"));
        String player = Files.readString(Path.of("app/src/main/java/com/fongmi/android/tv/player/PreviewPlayer.java"));
        equal(true, activity.contains("mPreview.prepare(position)"), "A buffered stream can prewarm the first drag preview again");
        equal(false, activity.contains("mPreview.surfaceChanging()"), "Fullscreen transitions do not reject a user's preview request");
        equal(false, activity.contains("mPreview.setActive("), "The mistaken beta5 lifecycle restrictions are removed");
        equal(true, player.contains("ExoUtil.buildRenderersFactory("), "Preview uses its original system renderer selection");
        equal(true, player.contains("seek(position, true)"), "Releasing the seek bar keeps its exact final preview target");
        equal(true, player.contains("IDLE_RELEASE = 10000"), "Consecutive drags reuse the decoder for ten seconds");
        equal(false, player.contains("PreviewPolicy"), "The mistaken software-only decoder policy is removed");
        equal(false, SourcePluginPolicy.canInstantiate("csp_LiveAiDouYuGuard"), "The constructor identified in both SIGABRT traces is quarantined before native code");
        equal(true, SourcePluginPolicy.canInstantiate("csp_WexAiGuaZi"), "Ordinary VOD sources remain available");
        equal(true, SourcePluginPolicy.canInstantiate("csp_LiveOther"), "Unproven live sources are not disabled by a broad name heuristic");
        equal(true, SourcePluginPolicy.canInstantiate(null), "Non-CSP providers retain their own handling");
    }

    private static void checkDetailExhaustion() throws Exception {
        equal(false, DetailLoadPolicy.canShowEmpty(false, false, true, false, true), "Discovery beyond ten seconds is still pending, not failed");
        equal(false, DetailLoadPolicy.canShowEmpty(false, false, false, true, true), "A finished search batch is not full exhaustion");
        equal(false, DetailLoadPolicy.canShowEmpty(true, false, false, false, true), "Candidate validation may finish after discovery and must not show empty");
        equal(false, DetailLoadPolicy.canShowEmpty(false, true, false, false, true), "Catalog initialization cannot produce a premature empty state");
        equal(true, DetailLoadPolicy.canShowEmpty(false, false, false, false, true), "Actual full exhaustion ends the spinner");
        equal(true, DetailLoadPolicy.canShowEmpty(false, true, true, true, false), "A global offline state does not spin forever waiting for sources");
        String activity = Files.readString(Path.of("app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java"));
        equal(false, activity.contains("App.post(mR4, 10000)"), "Remove the premature ten-second failure timer");
        String preflight = activity.substring(activity.indexOf("private void getDetail(Vod item)"), activity.indexOf("private void setCandidateDetail"));
        equal(false, preflight.contains("mPlayers.stop()"), "Fetching a replacement catalog never stops healthy playback");
        equal(false, preflight.contains("putExtra(\"key\""), "The active source identity stays intact during preflight");
        equal(true, activity.contains("setDetail(Result.vod(detail))"), "Accepted candidate uses common completion path to clear refresh state");
        String manifest = Files.readString(Path.of("app/src/main/AndroidManifest.xml")).replace("\r", "");
        equal(true, manifest.contains("android:name=\".service.SourceService\"\n            android:process=\":sources\"\n            android:exported=\"false\""), "Source service is private and in its own process");
    }

    private static void checkNativeCrashTrace() throws Exception {
        java.io.ByteArrayOutputStream frames = new java.io.ByteArrayOutputStream();
        for (int index = 0; index < 80; index++) frames.writeBytes(proto(4, proto(1, 0x1234L + index,
                4, "decodeFrame" + index, 5, 12L, 6, "/vendor/lib64/libcodec.so", 8, "build-123")));
        byte[] crashed = join(proto(1, 232L, 2, "MediaCodecVideo", 7, "unwind note"), frames.toByteArray());
        byte[] other = proto(1, 231L, 2, "main", 4, proto(1, 33L, 6, "libmain.so"));
        byte[] tombstone = proto(5, 230L, 6, 232L, 2, "HUAWEI/test", 4, "2026-10-08", 9, "com.xybox.app",
                10, proto(1, 6L, 2, "SIGABRT", 3, 0L, 4, "SI_USER"), 14, "codec abort", 15, proto(1, "bad Surface"),
                16, proto(1, 231L, 2, other), 16, proto(1, 232L, 2, crashed), 1000, "future OEM field");
        String text = NativeCrashTrace.read(new java.io.ByteArrayInputStream(tombstone));
        equal(true, text.contains("pid=230 crashedTid=232"), "Native tombstone preserves the process and crashing thread identities");
        equal(true, text.contains("SIGABRT"), "Native abort signal is readable rather than binary protobuf");
        equal(true, text.contains("Abort message: codec abort"), "Native abort description survives full log export");
        equal(true, text.contains("Cause: bad Surface"), "Native root-cause text is included");
        equal(true, text.contains("decodeFrame79+12"), "All eighty backtrace frames survive without ordinary log truncation");
        equal(true, text.contains("BuildId: build-123"), "Library BuildId is available for symbolization");
        equal(true, text.indexOf("name=MediaCodecVideo") < text.indexOf("name=main"), "Crashing thread appears before other complete backtraces");
        equal(true, text.contains("libmain.so"), "Non-crashing thread backtraces are retained too");
        equal(true, text.contains("unwind note"), "System unwinder notes are retained");
        byte[] fixedUnknown = new byte[]{(byte) 0xf9, 0x3e, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0x85, 0x3f, 0, 0, 0, 0};
        equal(text, NativeCrashTrace.read(new java.io.ByteArrayInputStream(join(tombstone, fixedUnknown))), "Unknown fixed-width fields are safely skipped");
        for (byte[] invalid : new byte[][]{new byte[0], {0}, {(byte) 0x80}, {42, 10, 1}, {41, 0}, {43},
                {40, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                        (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 2}}) {
            boolean rejected = false;
            try { NativeCrashTrace.read(new java.io.ByteArrayInputStream(invalid)); }
            catch (java.io.IOException expected) { rejected = true; }
            equal(true, rejected, "Malformed, truncated or overflowing tombstones fail safely");
        }
        boolean bounded = false;
        try { NativeCrashTrace.read(new java.io.ByteArrayInputStream(new byte[NativeCrashTrace.MAX_BYTES + 1])); }
        catch (java.io.IOException expected) { bounded = true; }
        equal(true, bounded, "System trace input cannot allocate unbounded startup memory");
        String appLog = Files.readString(Path.of("app/src/main/java/com/fongmi/android/tv/utils/AppLog.java"));
        equal(true, appLog.contains("format(Log.ASSERT, \"NativeCrash\""), "Native stack export bypasses the ordinary message length limit");
        equal(true, appLog.contains("last_native_trace"), "Upgrade may retrieve an older crash even if its short exit record was already logged");
    }

    private static byte[] proto(Object... fields) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        for (int index = 0; index < fields.length; index += 2) {
            int number = ((Number) fields[index]).intValue();
            Object value = fields[index + 1];
            varint(output, ((long) number << 3) | (value instanceof Number ? 0 : 2));
            if (value instanceof Number) varint(output, ((Number) value).longValue());
            else {
                byte[] data = value instanceof byte[] ? (byte[]) value : value.toString().getBytes(StandardCharsets.UTF_8);
                varint(output, data.length); output.writeBytes(data);
            }
        }
        return output.toByteArray();
    }

    private static void varint(java.io.ByteArrayOutputStream output, long value) {
        while ((value & ~0x7fL) != 0) { output.write((int) value & 0x7f | 0x80); value >>>= 7; }
        output.write((int) value);
    }

    private static byte[] join(byte[]... pieces) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        for (byte[] piece : pieces) output.writeBytes(piece);
        return output.toByteArray();
    }

    /** Exercise the actual class-loader boundary, including a helper running on a background thread. */
    private static void checkSourceOrigin() throws Exception {
        equal(null, SourceUiOrigin.findCaller(), "Host code is trusted before loading sources");
        Path fixture = Files.createTempDirectory("xybox-source-ui-check-").toAbsolutePath().normalize();
        try {
            Path source = fixture.resolve("a.java");
            Files.writeString(source, """
                    package external.obfuscated;
                    public class a {
                        public static String call() { return com.fongmi.android.tv.utils.SourceUiOrigin.findCaller(); }
                        public static java.util.concurrent.Callable<String> task() { return () -> call(); }
                    }
                    """, StandardCharsets.UTF_8);
            equal(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath",
                    System.getProperty("java.class.path"), "-d", fixture.toString(), source.toString()),
                    "Compile a source fixture outside the spider package");
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{fixture.toUri().toURL()}, SourceUiOrigin.class.getClassLoader())) {
                Class<?> helper = loader.loadClass("external.obfuscated.a");
                equal(null, helper.getMethod("call").invoke(null), "An unregistered loader is not guessed from its name");
                SourceUiOrigin.register(loader, "source-fixture.jar");
                equal("external.obfuscated.a", helper.getMethod("call").invoke(null), "Recognize an obfuscated source helper after registration");
                equal("source-fixture.jar", SourceUiOrigin.jarForCaller("external.obfuscated.a"), "Permission diagnostics identify the initiating helper's JAR");
                equal("", SourceUiOrigin.jarForCaller(null), "Host-origin permissions do not claim a source JAR");
                equal(null, SourceUiOrigin.findCaller(), "Parent-loaded host code stays trusted");
                Callable<?> task = (Callable<?>) helper.getMethod("task").invoke(null);
                var worker = Executors.newSingleThreadExecutor();
                try { equal("external.obfuscated.a", worker.submit(task).get(), "Recognize asynchronous source UI callbacks"); }
                finally { worker.shutdownNow(); }
                SourceUiOrigin.register(loader);
                equal(null, SourceUiOrigin.findCaller(), "Registering the same source twice leaves host code trusted");
            }
        } finally {
            try (var paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(fixture)) throw new IllegalStateException("Fixture escaped its temporary directory");
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
