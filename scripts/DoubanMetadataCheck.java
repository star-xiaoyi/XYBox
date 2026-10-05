import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;

/** Checks the actual release parser; the small Html stand-in only decodes fixture text. */
class DoubanMetadataCheck {
    private static int checks;
    private static void equal(Object expected, Object actual, String scenario) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError(scenario + ": " + actual);
    }
    private static Path jar(Path root, String filename) throws Exception {
        try (var files = Files.walk(root)) {
            return files.filter(path -> path.getFileName().toString().equals(filename)).findFirst().orElseThrow();
        }
    }
    private static Object get(Object subject, String method) throws Exception {
        return subject.getClass().getMethod(method).invoke(subject);
    }
    public static void main(String[] args) throws Exception {
        Path fixtures = Path.of("app/build/metadata-check"), source = fixtures.resolve("android/text/Html.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source.resolveSibling("Spanned.java"), "package android.text; public interface Spanned extends CharSequence {}", StandardCharsets.UTF_8);
        Files.writeString(source, """
                package android.text;
                public class Html {
                    public static Spanned fromHtml(String html, int flags) { return new Text(html.replaceAll("<[^>]*>", " ").replace("&amp;", "&")); }
                    private record Text(String value) implements Spanned {
                        public int length() { return value.length(); }
                        public char charAt(int i) { return value.charAt(i); }
                        public CharSequence subSequence(int start, int end) { return value.subSequence(start, end); }
                        public String toString() { return value; }
                    }
                }
                """, StandardCharsets.UTF_8);
        int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", fixtures.toString(), source.toString(), source.resolveSibling("Spanned.java").toString());
        equal(0, compiled, "Html fixture support compiles");
        Path cache = Path.of(System.getenv("GRADLE_USER_HOME"), "caches/modules-2/files-2.1");
        var version = Pattern.compile("gsonVersion\\s*=\\s*'([^']+)'").matcher(Files.readString(Path.of("build.gradle")));
        if (!version.find()) throw new AssertionError("Gson version unavailable");
        Path gson = jar(cache.resolve("com.google.code.gson/gson"), "gson-" + version.group(1) + ".jar");
        Path okhttp = jar(cache.resolve("com.squareup.okhttp3/okhttp"), "okhttp-4.11.0.jar");
        Path kotlin = jar(cache.resolve("org.jetbrains.kotlin/kotlin-stdlib"), "kotlin-stdlib-2.4.10.jar");
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{fixtures.toUri().toURL(), Path.of("app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes").toUri().toURL(), gson.toUri().toURL(), okhttp.toUri().toURL(), kotlin.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> json = loader.loadClass("com.google.gson.JsonObject"), subjectType = loader.loadClass("com.fongmi.android.tv.api.Douban$Subject");
            Method jsonParse = loader.loadClass("com.google.gson.JsonParser").getMethod("parseString", String.class);
            Method asObject = loader.loadClass("com.google.gson.JsonElement").getMethod("getAsJsonObject");
            Method parse = subjectType.getDeclaredMethod("parse", String.class, json); parse.setAccessible(true);
            String details = "{\"year\":\"2020\",\"countries\":[\"中国大陆\"],\"genres\":[\"喜剧\",\"科幻\",\"动画\"],\"rating\":{\"value\":0}}";
            Object subject = parse.invoke(null, "34822765", asObject.invoke(jsonParse.invoke(null, details)));
            equal(0.0, get(subject, "getRating"), "Unrated subject stays unrated");
            equal(List.of("喜剧", "科幻", "动画"), get(subject, "getGenres"), "Unrated subject retains genres");
            equal("2020", get(subject, "getYear"), "Unrated subject retains year");
            equal(true, get(subject, "hasHeaderMetadata"), "Rating is not a requirement for complete metadata");
            ((List<?>) get(subject, "getGenres")).clear();
            equal(3, ((List<?>) get(subject, "getGenres")).size(), "Returned genres do not mutate cached data");
            subject = parse.invoke(null, "34822765", asObject.invoke(jsonParse.invoke(null, "{}")));
            equal(false, get(subject, "hasHeaderMetadata"), "A matched ID without facts remains incomplete");
            Object known = parse.invoke(null, "34822765", asObject.invoke(jsonParse.invoke(null, details)));
            subjectType.getMethod("retainMissing", subjectType).invoke(subject, known);
            equal(List.of("喜剧", "科幻", "动画"), get(subject, "getGenres"), "A partial retry preserves previously displayed genres");
            equal(true, get(subject, "hasHeaderMetadata"), "Retry retains known year and countries");
            Object different = parse.invoke(null, "999", asObject.invoke(jsonParse.invoke(null, "{}")));
            subjectType.getMethod("retainMissing", subjectType).invoke(different, known);
            equal(false, get(different, "hasHeaderMetadata"), "Facts from another subject cannot fill missing fields");
            subject = parse.invoke(null, "34822765", asObject.invoke(jsonParse.invoke(null, "{\"card_subtitle\":\"2020 / 中国大陆 / 中国香港 / 喜剧 科幻 动画 / 导演 / 主演\"}")));
            equal(List.of("中国大陆", "中国香港"), get(subject, "getCountries"), "Summary preserves multiple regions");
            equal(List.of("喜剧", "科幻", "动画"), get(subject, "getGenres"), "Regions never become genre tags");
            equal(true, get(subject, "hasHeaderMetadata"), "Summary supplements missing structured fields");
            Method page = loader.loadClass("com.fongmi.android.tv.api.Douban").getDeclaredMethod("subjectPage", String.class, String.class); page.setAccessible(true);
            String html = "<section data-id='34822765'></section><div class='sub-title'>测试动画</div><div class='sub-original-title'>测试动画（2020）</div><div class='sub-meta'>中国大陆 / 中国香港 / 喜剧 / 科幻 / 动画 / 2020-01-10(中国大陆)上映 / 片长15分钟</div><section class='subject-intro'><p>测试简介</p></section>";
            subject = parse.invoke(null, "34822765", page.invoke(null, "34822765", html));
            equal(List.of("中国大陆", "中国香港"), get(subject, "getCountries"), "Web page preserves multiple regions");
            equal(List.of("喜剧", "科幻", "动画"), get(subject, "getGenres"), "Web page excludes date and runtime from genres");
            equal("2020", get(subject, "getYear"), "Web page extracts subject year");
            equal("测试简介", get(subject, "getIntro"), "Web page extracts subject introduction");
            for (String invalid : new String[]{html.replace("34822765", "999"), "<section data-id='34822765'>请登录后访问</section>"}) {
                try { page.invoke(null, "34822765", invalid); throw new AssertionError("Invalid page accepted"); }
                catch (InvocationTargetException error) { equal(true, error.getCause() instanceof java.io.IOException, "Wrong subject and challenge pages cannot become metadata"); }
            }
        }
        System.out.println("Douban metadata checks passed: " + checks);
    }
}
