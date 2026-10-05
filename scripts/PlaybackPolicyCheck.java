import com.fongmi.android.tv.search.EpisodeKey;
import com.fongmi.android.tv.search.FilmIdentity;
import com.fongmi.android.tv.search.PlaybackPolicy;
import com.fongmi.android.tv.search.QualityOption;
import com.fongmi.android.tv.search.QualitySelection;
import com.fongmi.android.tv.search.PlaybackSelection;
import com.fongmi.android.tv.search.PlaybackRoutePolicy;
import com.fongmi.android.tv.search.SourceSelection;
import com.fongmi.android.tv.utils.SourceUiOrigin;
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
        equal("18", EpisodeKey.key("第十八集"), "Chinese numbered episode");
        equal("18", EpisodeKey.key("１８"), "Full width episode");
        equal("18", EpisodeKey.key("EP18"), "English numbered episode");
        equal("s2e18", EpisodeKey.key("S02E18"), "Keep season identity");
        equal("s2e18", EpisodeKey.key("第二季第十八集"), "Chinese season identity");
        equal(-1, EpisodeKey.number("2026-10-04"), "Do not treat dates as episodes");
        equal(-1, EpisodeKey.number("1080p"), "Do not treat resolution as episode");
        equal(-1, EpisodeKey.number("预告第18集"), "Do not confuse trailers with episodes");
        equal(-1, EpisodeKey.number("2026"), "Do not treat years as episodes");

        equal(true, FilmIdentity.same("film-a", "", "film-a", "", true, false), "Same film survives provider metadata drift");
        equal(true, FilmIdentity.same("film-a", "source:b", "", "source:b", true, false), "Learned provider alias");
        equal(true, FilmIdentity.same("film-a", "film:old", "old", "", true, false), "Keep merged film ids");
        equal(false, FilmIdentity.same("film-a", "source:b", "film-a", "source:b", false, true), "Never merge a different title or edition");
        equal(false, FilmIdentity.same("film-a", "", "film-b", "", true, false), "Do not merge conflicting unlinked remakes");
        equal(true, FilmIdentity.compatibleMetadata(2025, 2026, 2, 2, true, 1500000, 2700000), "Series runtimes do not split histories");
        equal(false, FilmIdentity.compatibleMetadata(1998, 2026, 1, 1, false, 6000000, 6000000), "Separate remakes");
        equal(false, FilmIdentity.compatibleMetadata(2026, 2026, 1, 2, false, 6000000, 6000000), "Separate movie and series");
        equal(false, FilmIdentity.compatibleMetadata(2026, 2026, 1, 1, false, 6000000, 7800000), "Unlinked movie cuts remain separate");

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
        equal(true, PlaybackRoutePolicy.isRestricted("ZJ-4K-限"), "Reject preview-only 4K route");
        equal(true, PlaybackRoutePolicy.isRestricted("NB-蓝光-限"), "Reject preview-only blue-ray route");
        equal(true, PlaybackRoutePolicy.isRestricted("扫码观看完整版"), "Reject QR-code route");
        equal(false, PlaybackRoutePolicy.isRestricted("免费分享！切勿上当！"), "Keep an ordinary warning label");
        checkSourceOrigin();
        System.out.println("Playback and source UI checks passed: " + checks);
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
                SourceUiOrigin.register(loader);
                equal("external.obfuscated.a", helper.getMethod("call").invoke(null), "Recognize an obfuscated source helper after registration");
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
