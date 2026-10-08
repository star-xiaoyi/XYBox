import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Execute the actual JarLoader with JVM fixtures, without Android or untrusted native plugins. */
class JarLoaderCheck {
    private static SimpleJavaFileObject source(String name, String text) {
        return new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), javax.tools.JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignored) { return text; }
        };
    }

    public static void main(String[] args) throws Exception {
        Path output = Files.createTempDirectory("xybox-jar-check-");
        List<javax.tools.JavaFileObject> sources = new ArrayList<>();
        for (String name : List.of("JarLoader", "SourcePluginPolicy")) sources.add(source("com.fongmi.android.tv.api.loader." + name,
                Files.readString(Path.of("app/src/main/java/com/fongmi/android/tv/api/loader/" + name + ".java"))));
        Map<String, String> fixtures = Map.ofEntries(
                Map.entry("android.content.Context", "public class Context { public ClassLoader getClassLoader() { return getClass().getClassLoader(); } }"),
                Map.entry("com.fongmi.android.tv.App", "public class App extends android.content.Context { public static App get() { return new App(); } public static void execute(Runnable work) { work.run(); } }"),
                Map.entry("com.fongmi.android.tv.utils.SourceUiOrigin", "public class SourceUiOrigin { public static void register(ClassLoader loader, String name) { } }"),
                Map.entry("com.fongmi.android.tv.utils.UrlUtil", "public class UrlUtil { public static String convert(String url) { return url; } }"),
                Map.entry("com.github.catvod.crawler.Spider", "public class Spider { public void init(android.content.Context context, String ext) throws Exception { } public void destroy() { } }"),
                Map.entry("com.github.catvod.crawler.SpiderNull", "public class SpiderNull extends Spider { }"),
                Map.entry("com.github.catvod.net.OkHttp", "public class OkHttp { public static byte[] bytes(String url) { return new byte[0]; } public static String string(String url) { return url; } }"),
                Map.entry("com.github.catvod.utils.Logger", "public class Logger { public static void e(String tag, Throwable error) { } public static void e(String tag, String message, Throwable error) { } public static void w(String tag, String message) { } }"),
                Map.entry("com.github.catvod.utils.Util", "public class Util { public static String md5(String value) { return value; } public static boolean equals(String jar, String hash) { return false; } }"),
                Map.entry("com.github.catvod.utils.Path", """
                    public class Path {
                        public static java.io.File jar() { return test.Scenario.directory.toFile(); }
                        public static java.io.File jar(String key) { return local(key); }
                        public static java.io.File local(String key) { return test.Scenario.directory.resolve(key.substring(key.lastIndexOf('/') + 1)).toFile(); }
                        public static java.io.File write(java.io.File file, byte[] bytes) { return file; }
                    }
                    """),
                Map.entry("org.json.JSONObject", "public class JSONObject { }"),
                Map.entry("dalvik.system.DexClassLoader", """
                    public class DexClassLoader extends ClassLoader {
                        private final String path;
                        public DexClassLoader(String path, String output, String lib, ClassLoader parent) { super(parent); this.path = path; test.Scenario.loads.incrementAndGet(); }
                        public Class<?> loadClass(String name) throws ClassNotFoundException {
                            if (name.equals("com.github.catvod.spider.Init")) {
                                if (path.endsWith("slow")) return test.Scenario.SlowInit.class;
                                if (path.endsWith("failed")) return test.Scenario.FailedInit.class;
                                if (path.endsWith("plain")) throw new ClassNotFoundException(name);
                                return test.Scenario.Init.class;
                            }
                            if (name.equals("com.github.catvod.spider.Probe")) return test.Scenario.Probe.class;
                            if (name.equals("com.github.catvod.spider.Hanging")) return test.Scenario.Hanging.class;
                            if (name.equals("com.github.catvod.spider.LiveAiDouYuGuard")) throw new AssertionError("Quarantine reached native class loading");
                            throw new ClassNotFoundException(name);
                        }
                    }
                    """),
                Map.entry("test.Scenario", """
                    public class Scenario {
                        public static java.nio.file.Path directory;
                        public static final java.util.concurrent.atomic.AtomicInteger loads = new java.util.concurrent.atomic.AtomicInteger();
                        public static final java.util.concurrent.atomic.AtomicInteger constructs = new java.util.concurrent.atomic.AtomicInteger();
                        public static final java.util.concurrent.CountDownLatch initEntered = new java.util.concurrent.CountDownLatch(1);
                        public static final java.util.concurrent.CountDownLatch allowInit = new java.util.concurrent.CountDownLatch(1);
                        public static final java.util.concurrent.CountDownLatch spiderEntered = new java.util.concurrent.CountDownLatch(1);
                        public static final java.util.concurrent.CountDownLatch allowSpider = new java.util.concurrent.CountDownLatch(1);
                        private static int checks;
                        public static class Init { public static void init(android.content.Context context) { } }
                        public static class SlowInit { public static void init(android.content.Context context) throws Exception { initEntered.countDown(); allowInit.await(); } }
                        public static class FailedInit { public static void init(android.content.Context context) { throw new IllegalStateException("Native context failed"); } }
                        public static class Probe extends com.github.catvod.crawler.Spider { public Probe() { constructs.incrementAndGet(); } }
                        public static class Hanging extends Probe { public void init(android.content.Context context, String ext) throws Exception { spiderEntered.countDown(); allowSpider.await(); } }
                        private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
                        public static void run(String path) throws Exception {
                            directory = java.nio.file.Path.of(path);
                            for (String name : java.util.List.of("slow", "fast", "failed", "plain")) java.nio.file.Files.createFile(directory.resolve(name));
                            var loader = new com.fongmi.android.tv.api.loader.JarLoader();
                            var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
                            try {
                                var first = pool.submit(() -> loader.getSpider("one", "csp_Probe", "", "file:///slow"));
                                check(initEntered.await(2, java.util.concurrent.TimeUnit.SECONDS), "Slow plugin entered Init");
                                var second = pool.submit(() -> loader.getSpider("two", "csp_Probe", "", "file:///slow"));
                                var duplicate = pool.submit(() -> loader.getSpider("one", "csp_Probe", "", "file:///slow"));
                                var fast = pool.submit(() -> loader.getSpider("fast", "csp_Probe", "", "file:///fast"));
                                check(fast.get(2, java.util.concurrent.TimeUnit.SECONDS) instanceof Probe, "A different JAR must not wait for slow Init");
                                try { second.get(150, java.util.concurrent.TimeUnit.MILLISECONDS); throw new AssertionError("Half-initialized JAR became visible"); }
                                catch (java.util.concurrent.TimeoutException expected) { checks++; }
                                check(constructs.get() == 1, "Only the ready JAR may construct a spider");
                                allowInit.countDown();
                                var value = first.get(2, java.util.concurrent.TimeUnit.SECONDS);
                                check(second.get(2, java.util.concurrent.TimeUnit.SECONDS) instanceof Probe, "Waiting source proceeds after Init completes");
                                check(duplicate.get(2, java.util.concurrent.TimeUnit.SECONDS) == value, "One site is constructed exactly once");
                                check(constructs.get() == 3, "Distinct ready sites retain independent spider instances");
                                int before = constructs.get();
                                check(loader.getSpider("failed", "csp_Probe", "", "file:///failed") instanceof com.github.catvod.crawler.SpiderNull, "Failed Init must stop construction");
                                check(loader.dex("file:///failed") == null, "Failed Init must not publish a loader");
                                check(constructs.get() == before, "Failed native context never reaches a constructor");
                                check(loader.getSpider("plain", "csp_Probe", "", "file:///plain") instanceof Probe, "Java-only JARs without Init remain supported");
                                before = loads.get();
                                check(loader.getSpider("crashing", "csp_LiveAiDouYuGuard", "", "file:///never-loaded") instanceof com.github.catvod.crawler.SpiderNull, "Known fatal constructor is rejected");
                                check(loads.get() == before, "Quarantine is before JAR loading or native construction");
                                var hanging = pool.submit(() -> loader.getSpider("hung", "csp_Hanging", "", "file:///fast"));
                                check(spiderEntered.await(2, java.util.concurrent.TimeUnit.SECONDS), "One provider is stuck in spider.init");
                                check(pool.submit(() -> loader.getSpider("healthy", "csp_Probe", "", "file:///fast")).get(2, java.util.concurrent.TimeUnit.SECONDS) instanceof Probe,
                                        "A hung site must not block healthy sites sharing the same JAR");
                                allowSpider.countDown(); hanging.get(2, java.util.concurrent.TimeUnit.SECONDS);
                            } finally {
                                allowInit.countDown(); allowSpider.countDown(); pool.shutdownNow();
                            }
                            System.out.println("Actual JarLoader concurrency checks passed: " + checks);
                        }
                    }
                    """)
        );
        fixtures.forEach((name, text) -> sources.add(source(name, "package " + name.substring(0, name.lastIndexOf('.')) + ";\n" + text)));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8)) {
            if (!compiler.getTask(null, manager, null, List.of("-d", output.toString(), "-encoding", "UTF-8"), null, sources).call())
                throw new AssertionError("JarLoader fixtures did not compile");
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            try { loader.loadClass("test.Scenario").getMethod("run", String.class).invoke(null, output.toString()); }
            catch (java.lang.reflect.InvocationTargetException error) { throw new AssertionError("JarLoader regression failed", error.getCause()); }
        }
    }
}
