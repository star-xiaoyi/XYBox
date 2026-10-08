import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Actual bridge/service/loader code with JVM Android substitutes; not a device/native crash test. */
class SourceIsolationCheck {
    private static SimpleJavaFileObject source(String name, String text) {
        return new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), javax.tools.JavaFileObject.Kind.SOURCE) {
            public CharSequence getCharContent(boolean ignored) { return text; }
        };
    }
    private static Path dependency(String relative) throws Exception {
        try (var files = Files.walk(Path.of("D:/dev/gradle-cache/caches/modules-2/files-2.1/" + relative))) {
            return files.filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow();
        }
    }
    public static void main(String[] args) throws Exception {
        Path output = Files.createTempDirectory("xybox-source-isolation-").toAbsolutePath().normalize();
        Path json = dependency("org.json/json/20160810"), nano = dependency("org.nanohttpd/nanohttpd/2.3.1");
        String classpath = json + java.io.File.pathSeparator + nano;
        List<javax.tools.JavaFileObject> sources = new ArrayList<>();
        for (String type : List.of("api.loader.SourceWire", "api.loader.SourceUnavailableException", "api.loader.SourceBridge", "api.loader.RemoteSpider", "api.loader.BaseLoader",
                "service.SourceService", "search.SourceDetailPolicy", "search.EpisodeSelection", "search.EpisodeKey", "search.PlaybackRoutePolicy"))
            sources.add(source("com.fongmi.android.tv." + type, Files.readString(Path.of("app/src/main/java/com/fongmi/android/tv/" + type.replace('.', '/') + ".java"))));
        sources.add(source("com.github.catvod.crawler.Spider", Files.readString(Path.of("catvod/src/main/java/com/github/catvod/crawler/Spider.java"))));
        Map<String, String> fixtures = Map.ofEntries(
            Map.entry("android.content.ComponentName", "public class ComponentName { }"),
            Map.entry("android.content.Intent", "public class Intent { public Intent() { } public Intent(Context ctx, Class<?> service) { } }"),
            Map.entry("android.content.ServiceConnection", "public interface ServiceConnection { void onServiceConnected(ComponentName name, android.os.IBinder service); void onServiceDisconnected(ComponentName name); default void onBindingDied(ComponentName name) { } default void onNullBinding(ComponentName name) { } }"),
            Map.entry("android.content.Context", "public class Context { public static final int BIND_AUTO_CREATE=1; public boolean bindService(Intent intent, ServiceConnection connection, int flags) { return test.Scenario.bind(connection); } public void unbindService(ServiceConnection connection) { } }"),
            Map.entry("android.app.Service", "public class Service extends android.content.Context { public void onCreate() { } public android.os.IBinder onBind(android.content.Intent intent) { return null; } public void onDestroy() { } }"),
            Map.entry("android.text.TextUtils", "public class TextUtils { public static boolean isEmpty(String value) { return value == null || value.isEmpty(); } }"),
            Map.entry("android.os.RemoteException", "public class RemoteException extends Exception { }"),
            Map.entry("android.os.DeadObjectException", "public class DeadObjectException extends RemoteException { }"),
            Map.entry("android.os.Parcelable", "public interface Parcelable { int PARCELABLE_WRITE_RETURN_VALUE=1; }"),
            Map.entry("android.os.Process", "public class Process { public static int myPid() { return com.fongmi.android.tv.App.isSourceProcess() ? 2 : 1; } public static void killProcess(int pid) { test.Scenario.killed=pid; } }"),
            Map.entry("android.os.SystemClock", "public class SystemClock { public static long elapsedRealtime() { return System.nanoTime()/1000000; } }"),
            Map.entry("android.os.Looper", "public class Looper { private static final Looper MAIN = new Looper(); private static final Thread OWNER = Thread.currentThread(); public static Looper getMainLooper() { return MAIN; } public static Looper myLooper() { return Thread.currentThread()==OWNER ? MAIN : null; } }"),
            Map.entry("android.os.IBinder", "public interface IBinder { int FIRST_CALL_TRANSACTION=1, INTERFACE_TRANSACTION=1598968902, FLAG_ONEWAY=1; boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException; void linkToDeath(DeathRecipient recipient, int flags) throws RemoteException; interface DeathRecipient { void binderDied(); } }"),
            Map.entry("android.os.Binder", """
                public class Binder implements IBinder {
                    private boolean dead;
                    private final java.util.List<DeathRecipient> recipients=new java.util.ArrayList<>();
                    public synchronized void linkToDeath(DeathRecipient recipient, int flags) throws RemoteException { if(dead) throw new DeadObjectException(); recipients.add(recipient); }
                    public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException { if(dead) throw new DeadObjectException(); return onTransact(code, data, reply, flags); }
                    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) { return false; }
                    public void die() { dead=true; for(var recipient:recipients) recipient.binderDied(); }
                }
                """),
            Map.entry("android.os.Parcel", """
                public class Parcel {
                    private final java.util.List<Object> values=new java.util.ArrayList<>(); private int cursor;
                    public static Parcel obtain() { return new Parcel(); } public void recycle() { }
                    public void writeInterfaceToken(String token) { writeString(token); }
                    public void enforceInterface(String token) { if(!token.equals(readString())) throw new SecurityException(); }
                    public void writeInt(int value) { values.add(value); } public int readInt() { return (Integer)values.get(cursor++); }
                    public void writeString(String value) { values.add(value); } public String readString() { return (String)values.get(cursor++); }
                    public void writeParcelable(Object value,int flags) { values.add(value instanceof ParcelFileDescriptor ? ((ParcelFileDescriptor)value).dup() : value); }
                    public <T> T readParcelable(ClassLoader loader) { return (T)values.get(cursor++); }
                    public void writeNoException() { values.add(null); } public void writeException(RuntimeException error) { values.add(error); }
                    public void readException() { RuntimeException error=(RuntimeException)values.get(cursor++); if(error!=null) throw error; }
                }
                """),
            Map.entry("android.os.ParcelFileDescriptor", """
                public class ParcelFileDescriptor implements java.io.Closeable {
                    private static final java.util.Map<java.io.FileDescriptor,ParcelFileDescriptor> FDS=new java.util.concurrent.ConcurrentHashMap<>();
                    private static class Pipe { java.io.PipedInputStream input; java.io.PipedOutputStream output; volatile boolean ended; volatile String error; Pipe() throws java.io.IOException { input=new java.io.PipedInputStream(65536); output=new java.io.PipedOutputStream(input); } }
                    private static class End { final Pipe pipe; final boolean read; final java.util.concurrent.atomic.AtomicInteger refs=new java.util.concurrent.atomic.AtomicInteger(1); End(Pipe pipe,boolean read) { this.pipe=pipe; this.read=read; } }
                    private final End end; private boolean closed; private final java.io.FileDescriptor fd=new java.io.FileDescriptor();
                    private ParcelFileDescriptor(End end) { this.end=end; FDS.put(fd,this); }
                    public static ParcelFileDescriptor[] createReliablePipe() throws java.io.IOException { Pipe pipe=new Pipe(); return new ParcelFileDescriptor[]{new ParcelFileDescriptor(new End(pipe,true)),new ParcelFileDescriptor(new End(pipe,false))}; }
                    public ParcelFileDescriptor dup() { end.refs.incrementAndGet(); return new ParcelFileDescriptor(end); }
                    public java.io.FileDescriptor getFileDescriptor() { return fd; }
                    public static boolean ready(java.io.FileDescriptor fd) throws java.io.IOException { var item=FDS.get(fd); return item==null || item.closed || item.end.pipe.ended || item.end.pipe.input.available()>0; }
                    public void checkError() throws java.io.IOException { if(end.pipe.error!=null) throw new java.io.IOException(end.pipe.error); }
                    public synchronized void close() throws java.io.IOException { if(closed) return; closed=true; FDS.remove(fd); if(end.refs.decrementAndGet()==0) { if(end.read) end.pipe.input.close(); else { end.pipe.ended=true; end.pipe.output.close(); } } }
                    public void closeWithError(String error) throws java.io.IOException { end.pipe.error=error; close(); }
                    public static class AutoCloseInputStream extends java.io.InputStream { private final ParcelFileDescriptor item; public AutoCloseInputStream(ParcelFileDescriptor item) { this.item=item; } private void wake() { synchronized(item.end.pipe.input) { item.end.pipe.input.notifyAll(); } } public int read() throws java.io.IOException { int count=item.end.pipe.input.read(); wake(); return count; } public int read(byte[] bytes,int off,int len) throws java.io.IOException { int count=item.end.pipe.input.read(bytes,off,len); wake(); return count; } public void close() throws java.io.IOException { item.close(); } }
                    public static class AutoCloseOutputStream extends java.io.OutputStream { private final ParcelFileDescriptor item; public AutoCloseOutputStream(ParcelFileDescriptor item) { this.item=item; } public void write(int b) throws java.io.IOException { item.end.pipe.output.write(b); } public void write(byte[] bytes,int off,int len) throws java.io.IOException { item.end.pipe.output.write(bytes,off,len); } public void flush() throws java.io.IOException { item.end.pipe.output.flush(); } public void close() throws java.io.IOException { item.close(); } }
                }
                """),
            Map.entry("android.system.ErrnoException", "public class ErrnoException extends Exception { }"),
            Map.entry("android.system.OsConstants", "public class OsConstants { public static final int POLLIN=1,POLLHUP=16,POLLERR=8; }"),
            Map.entry("android.system.StructPollfd", "public class StructPollfd { public java.io.FileDescriptor fd; public short events; }"),
            Map.entry("android.system.Os", "public class Os { public static int poll(StructPollfd[] items,int timeout) throws ErrnoException { try { long end=System.currentTimeMillis()+timeout; do { if(android.os.ParcelFileDescriptor.ready(items[0].fd)) return 1; Thread.sleep(2); } while(System.currentTimeMillis()<end); return 0; } catch(Exception error) { throw new ErrnoException(); } } }"),
            Map.entry("com.fongmi.android.tv.App", "public class App extends android.content.Context { public static final App INSTANCE=new App(); public static App get() { return INSTANCE; } public static boolean isSourceProcess() { return Thread.currentThread().getName().equals(\"source-rpc\"); } public static void execute(Runnable work) { work.run(); } public static void post(Runnable work,long delay) { work.run(); } }"),
            Map.entry("com.fongmi.android.tv.Setting", "public class Setting { public static String getProxy() { return \"\"; } public static String getDoh() { return \"\"; } }"),
            Map.entry("com.fongmi.android.tv.utils.AppLog", "public class AppLog { public static void event(String tag,String text) { } public static void refreshSystemExits() { } public static void recordSourceFailure(String trace) { test.Scenario.trace=trace; } }"),
            Map.entry("com.fongmi.android.tv.server.Server", "public class Server { private static final Server INSTANCE=new Server(); public static Server get() { return INSTANCE; } public void start() { com.github.catvod.Proxy.set(9978); } }"),
            Map.entry("com.github.catvod.Proxy", "public class Proxy { private static int port=-1; public static int getPort() { return port; } public static void set(int value) { port=value; } }"),
            Map.entry("com.github.catvod.utils.Util", "public class Util { public static String md5(String text) { return Integer.toHexString(text.hashCode()); } }"),
            Map.entry("com.github.catvod.utils.Trans", "public class Trans { public static String t2s(String text) { return text; } }"),
            Map.entry("com.google.gson.JsonElement", "public class JsonElement { }"),
            Map.entry("com.github.catvod.utils.Json", "public class Json { public static com.google.gson.JsonElement parse(String text) { return new com.google.gson.JsonElement(); } }"),
            Map.entry("okhttp3.Dns", "public interface Dns { }"), Map.entry("okhttp3.OkHttpClient", "public class OkHttpClient { }"),
            Map.entry("com.github.catvod.bean.Doh", "public class Doh { public static Doh objectFrom(String text) { return new Doh(); } }"),
            Map.entry("com.github.catvod.net.OkHttp", """
                public class OkHttp {
                    public static int configured;
                    public static class Net implements okhttp3.Dns { public void clear() { } public void addAll(java.util.List<String> values) { configured+=values.size(); } public void setHeaders(java.util.List<com.google.gson.JsonElement> headers) { configured+=headers.size(); } }
                    private static final OkHttp INSTANCE=new OkHttp(); private static final Net NET=new Net();
                    public static OkHttp get() { return INSTANCE; } public void setProxy(String text) { } public void setDoh(com.github.catvod.bean.Doh value) { }
                    public static Net dns() { return NET; } public static Net selector() { return NET; } public static Net responseInterceptor() { return NET; } public static okhttp3.OkHttpClient client() { return new okhttp3.OkHttpClient(); }
                }
                """),
            Map.entry("com.fongmi.android.tv.bean.Site", "public class Site { public boolean isEmpty() { return false; } public com.github.catvod.crawler.Spider spider() { return new com.github.catvod.crawler.SpiderNull(); } }"),
            Map.entry("com.fongmi.android.tv.api.config.VodConfig", "public class VodConfig { public static VodConfig get() { return new VodConfig(); } public com.fongmi.android.tv.bean.Site getSite(String key) { return new com.fongmi.android.tv.bean.Site(); } public String sourceNetwork() { return \"{\\\"hosts\\\":[\\\"a=b\\\"],\\\"proxyHosts\\\":[\\\"a\\\"],\\\"headers\\\":[{}]}\"; } }"),
            Map.entry("com.fongmi.android.tv.search.SiteHealth", "public class SiteHealth { public static int failures; public static void failed(com.fongmi.android.tv.bean.Site site) { failures++; } }"),
            Map.entry("com.github.catvod.crawler.SpiderNull", "public class SpiderNull extends Spider { }"),
            Map.entry("dalvik.system.DexClassLoader", "public class DexClassLoader extends ClassLoader { }"),
            Map.entry("com.fongmi.android.tv.api.loader.JarLoader", """
                public class JarLoader {
                    private String recent="";
                    public void clear() { } public void setRecent(String value) { recent=value; }
                    public void parseJar(String key,String jar) { test.Scenario.assertChild(); }
                    public dalvik.system.DexClassLoader dex(String jar) { test.Scenario.assertChild(); return new dalvik.system.DexClassLoader(); }
                    public Throwable lastFailure() { return new IllegalStateException("native-init-inner"); }
                    public com.github.catvod.crawler.Spider getSpider(String key,String api,String ext,String jar) { test.Scenario.assertChild(); if(key.equals("null")) return new com.github.catvod.crawler.SpiderNull(); return new test.Scenario.Provider(key,ext); }
                    public Object[] proxyInvoke(java.util.Map<String,String> args) { test.Scenario.assertChild(); byte[] body=test.Scenario.body(); if("direct".equals(args.get("do"))) { var response=fi.iki.elonen.NanoHTTPD.newFixedLengthResponse(fi.iki.elonen.NanoHTTPD.Response.Status.PARTIAL_CONTENT,"video/mp2t",new java.io.ByteArrayInputStream(body),body.length); response.addHeader("Content-Range","bytes 10-20/30"); return new Object[]{response}; } return new Object[]{206,"video/mp2t",new java.io.ByteArrayInputStream(body),java.util.Map.of("Content-Range","bytes 10-20/30","X-Jar",recent)}; }
                    public org.json.JSONObject jsonExt(String jar,String key,java.util.LinkedHashMap<String,String> parsers,String url) { return new org.json.JSONObject(java.util.Map.of("order",String.join(",",parsers.keySet()))); }
                    public org.json.JSONObject jsonExtMix(String jar,String flag,String key,String name,java.util.LinkedHashMap<String,java.util.HashMap<String,String>> parsers,String url) { return new org.json.JSONObject(java.util.Map.of("order",String.join(",",parsers.keySet()),"flag",flag)); }
                }
                """),
            Map.entry("com.fongmi.android.tv.api.loader.JsLoader", "public class JsLoader { public void clear() { } public void setRecent(String key) { } public Throwable lastFailure() { return null; } public com.github.catvod.crawler.Spider getSpider(String key,String api,String ext,String jar) { test.Scenario.assertChild(); return new test.Scenario.Provider(key,ext); } public Object[] proxyInvoke(java.util.Map<String,String> args) { try { return com.fongmi.android.tv.api.loader.BaseLoader.get().getSpider(args).proxyLocal(args); } catch(Exception error) { throw new RuntimeException(error); } } }"),
            Map.entry("com.fongmi.android.tv.api.loader.PyLoader", "public class PyLoader { public void clear() { } public void setRecent(String key) { } public com.github.catvod.crawler.Spider getSpider(String key,String api,String ext) { return new com.github.catvod.crawler.SpiderNull(); } public Object[] proxyInvoke(java.util.Map<String,String> params) { return null; } }"),
            Map.entry("com.fongmi.android.tv.bean.Episode", "public class Episode { private final String name,url; public Episode(String name,String url) { this.name=name; this.url=url; } public String getName() { return name; } public String getUrl() { return url; } }"),
            Map.entry("com.fongmi.android.tv.bean.Flag", "public class Flag { private final String name; private final java.util.List<Episode> episodes=new java.util.ArrayList<>(); public Flag(String name) { this.name=name; } public String getFlag() { return name; } public boolean isCloudDrive() { return name.equals(\"网盘\"); } public java.util.List<Episode> getEpisodes() { return episodes; } }"),
            Map.entry("com.fongmi.android.tv.bean.Vod", "public class Vod { private final java.util.List<Flag> flags=new java.util.ArrayList<>(); public java.util.List<Flag> getVodFlags() { return flags; } }"),
            Map.entry("com.fongmi.android.tv.search.QualityOption", "public class QualityOption { public com.fongmi.android.tv.bean.Flag flag; public String valueName; }"),
            Map.entry("test.CrashChild", "public class CrashChild { public static void main(String[] args) throws Exception { if(args.length>0) { com.fongmi.android.tv.api.loader.SourceWire.write(System.out,\"reconnected\"); return; } var out=new java.io.DataOutputStream(System.out); out.writeInt(64); out.write(new byte[4]); out.flush(); Runtime.getRuntime().halt(13); } }"),
            Map.entry("test.Scenario", scenario())
        );
        fixtures.forEach((name, text) -> sources.add(source(name, "package " + name.substring(0, name.lastIndexOf('.')) + ";\n" + text)));
        try (var manager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8)) {
            if (!ToolProvider.getSystemJavaCompiler().getTask(null, manager, null, List.of("-d", output.toString(), "-encoding", "UTF-8", "-classpath", classpath), null, sources).call())
                throw new AssertionError("Source isolation fixtures did not compile");
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL(), json.toUri().toURL(), nano.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            try { loader.loadClass("test.Scenario").getMethod("run", String.class).invoke(null, output + java.io.File.pathSeparator + classpath); }
            catch (java.lang.reflect.InvocationTargetException error) { throw new AssertionError("Source isolation regression failed", error.getCause()); }
        } finally {
            try (var files = Files.walk(output)) { for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!path.toAbsolutePath().normalize().startsWith(output)) throw new IllegalStateException("Fixture escaped temporary directory");
                Files.deleteIfExists(path);
            } }
        }
    }
    private static String scenario() { return """
        public class Scenario {
            public static volatile String trace="";
            public static volatile boolean rejectBind;
            public static volatile int binds;
            public static volatile int killed;
            private static int checks;
            private static com.fongmi.android.tv.service.SourceService service;
            private static android.content.ServiceConnection connection;
            private static final java.util.concurrent.CountDownLatch hanging=new java.util.concurrent.CountDownLatch(1);
            private static final java.util.concurrent.CountDownLatch interrupted=new java.util.concurrent.CountDownLatch(1);
            public static void assertChild() { if(!com.fongmi.android.tv.App.isSourceProcess()) throw new AssertionError("Plugin executed in main process"); }
            public static boolean bind(android.content.ServiceConnection callback) { binds++; if(rejectBind) return false; connection=callback; service=new com.fongmi.android.tv.service.SourceService(); callback.onServiceConnected(new android.content.ComponentName(),service.onBind(new android.content.Intent())); return true; }
            public static byte[] body() { byte[] body=new byte[2*1024*1024]; for(int i=0;i<body.length;i++) body[i]=(byte)(i%251); return body; }
            public static class Provider extends com.github.catvod.crawler.Spider {
                private final String key,ext; public Provider(String key,String ext) { this.key=key; this.ext=ext; }
                public String homeContent(boolean filter) { return "home:"+filter; }
                public String homeVideoContent() { return "homeVideo"; }
                public String categoryContent(String tid,String pg,boolean filter,java.util.HashMap<String,String> extend) { return tid+":"+pg+":"+extend.get("region"); }
                public String searchContent(String query,boolean quick,String pg) throws Exception { if(key.equals("error")) throw new IllegalStateException("outer",new IllegalArgumentException("inner-root-cause")); if(key.equals("hang")) { hanging.countDown(); try { Thread.sleep(30000); } catch(InterruptedException error) { interrupted.countDown(); throw error; } } return query+":"+pg+":"+ext.length(); }
                public String searchContent(String query,boolean quick) { return query+":"+quick; }
                public String detailContent(java.util.List<String> ids) { return String.join(",",ids); }
                public String playerContent(String flag,String id,java.util.List<String> flags) { return flag+":"+id+":"+String.join(",",flags); }
                public String action(String action) { return action; }
                public boolean manualVideoCheck() { return true; } public boolean isVideoFormat(String url) { return url.endsWith(".m3u8"); }
                public Object[] proxyLocal(java.util.Map<String,String> params) { return new Object[]{200,"text/plain",new java.io.ByteArrayInputStream(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))}; }
            }
            private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
            public static void run(String classpath) throws Exception {
                android.os.Looper.getMainLooper();
                var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
                try {
                    var base=com.fongmi.android.tv.api.loader.BaseLoader.get();
                    check(base.getSpider("http","https://a","","") instanceof com.github.catvod.crawler.SpiderNull,"Ordinary HTTP sources never create source RPCs");
                    var spider=base.getSpider("a","csp_Test","","jar-a");
                    check(spider instanceof com.fongmi.android.tv.api.loader.RemoteSpider,"Main process owns only a facade");
                    base.parseJar("jar-a",true);
                    try { base.dex("jar-a"); throw new AssertionError("Main dex was allowed"); } catch(IllegalStateException expected) { checks++; }
                    try { spider.manualVideoCheck(); throw new AssertionError("UI RPC was allowed"); } catch(java.io.IOException expected) { checks++; }
                    check(com.fongmi.android.tv.api.loader.SourceUnavailableException.isInfrastructure(new com.fongmi.android.tv.api.loader.SourceUnavailableException("Source process exited during search")),"Typed transport failure is infrastructure");
                    check(com.fongmi.android.tv.api.loader.SourceUnavailableException.isInfrastructure(new java.io.IOException("Source process exited during search")),"Flattened transport message is infrastructure");
                    check(com.fongmi.android.tv.api.loader.SourceUnavailableException.isInfrastructure(new IllegalStateException("Obsolete source request")),"A stale-generation rejection is infrastructure");
                    check(com.fongmi.android.tv.api.loader.SourceUnavailableException.isInfrastructure(new IllegalStateException("Isolated source unavailable")),"A saturated pool rejection is infrastructure");
                    check(!com.fongmi.android.tv.api.loader.SourceUnavailableException.isInfrastructure(new IllegalStateException("outer")),"A plugin exception stays a site failure");
                    rejectBind=true; int bindsBefore=binds; int failuresBefore=com.fongmi.android.tv.search.SiteHealth.failures;
                    check(pool.submit(() -> { try { spider.homeContent(true); return "no-failure"; } catch(Exception error) { return com.fongmi.android.tv.api.loader.SourceUnavailableException.isInfrastructure(error) ? "infrastructure" : "other"; } }).get().equals("infrastructure"),"Binding failure is classified as infrastructure");
                    check(binds==bindsBefore+2,"Infrastructure failure retries exactly once before giving up");
                    check(com.fongmi.android.tv.search.SiteHealth.failures==failuresBefore,"Infrastructure failure never benches the site");
                    rejectBind=false;
                    check(pool.submit(() -> spider.homeContent(true)).get().equals("home:true"),"Home RPC keeps the filter parameter");
                    check(pool.submit(() -> spider.homeVideoContent()).get().equals("homeVideo"),"Home video RPC");
                    check(pool.submit(() -> spider.categoryContent("film","2",true,new java.util.HashMap<>(java.util.Map.of("region","CN")))).get().equals("film:2:CN"),"Category RPC retains filters");
                    check(pool.submit(() -> spider.detailContent(java.util.List.of("one","two"))).get().equals("one,two"),"Detail RPC retains all IDs");
                    check(pool.submit(() -> spider.playerContent("line","id",java.util.List.of("vip1","vip2"))).get().equals("line:id:vip1,vip2"),"Player RPC carries flag/id/VIP flags");
                    check(pool.submit(() -> spider.searchContent("片名",false,"2")).get().equals("片名:2:0"),"Search RPC carries query and page");
                    check(pool.submit(() -> spider.searchContent("片名",true)).get().equals("片名:true"),"Two-argument search remains separate");
                    check(pool.submit(() -> spider.isVideoFormat("https://a/v.m3u8")).get(),"Plugin sniffer is isolated");
                    check(com.github.catvod.Proxy.getPort()==9978,"Worker uses the main HTTP proxy port");
                    check(com.github.catvod.net.OkHttp.configured==3,"Host remaps/proxy hosts/headers are mirrored once, not reset for every RPC");
                    var huge=base.getSpider("large","csp_Test","x".repeat(1100000),"jar-a");
                    check(pool.submit(() -> huge.searchContent("x",false,"1")).get().equals("x:1:1100000"),"Requests larger than Binder limit go through a descriptor too");
                    var broken=base.getSpider("error","csp_Test","","jar-a");
                    int siteFailuresBefore=com.fongmi.android.tv.search.SiteHealth.failures;
                    check(pool.submit(() -> { try { broken.searchContent("x",false,"1"); return false; } catch(Exception error) { return error.getMessage().contains("inner-root-cause"); } }).get(),"Java plugin failures keep the complete nested cause");
                    check(trace.contains("inner-root-cause"),"Child failure survives diagnostic logging");
                    check(com.fongmi.android.tv.search.SiteHealth.failures==siteFailuresBefore+1,"A plugin failure still benches its site");
                    var missing=base.getSpider("null","csp_Test","","jar-a");
                    check(pool.submit(() -> { try { missing.detailContent(java.util.List.of("x")); return false; } catch(Exception error) { return error.getMessage().contains("native-init-inner"); } }).get(),"SpiderNull initialization is a failure, not an empty successful search");
                    java.util.LinkedHashMap<String,String> parsers=new java.util.LinkedHashMap<>(); parsers.put("z","first"); parsers.put("a","second");
                    check(pool.submit(() -> { try { return base.jsonExt("jar-a","Test",parsers,"url").optString("order"); } catch(Throwable error) { throw new RuntimeException(error); } }).get().equals("z,a"),"JSON parser priority is not reordered by map serialization");
                    java.util.LinkedHashMap<String,java.util.HashMap<String,String>> mix=new java.util.LinkedHashMap<>(); mix.put("z",new java.util.HashMap<>(java.util.Map.of("url","first"))); mix.put("a",new java.util.HashMap<>(java.util.Map.of("url","second")));
                    check(pool.submit(() -> { try { return base.jsonExtMix("jar-a","flag","Test","name",mix,"url").optString("order"); } catch(Throwable error) { throw new RuntimeException(error); } }).get().equals("z,a"),"Mixed parser order survives RPC");
                    base.getSpider("b","csp_Test","","jar-b"); base.setRecent("b","csp_Test","jar-b");
                    Object[] proxy=pool.submit(() -> base.proxyLocal(java.util.Map.of("jarKey",com.github.catvod.utils.Util.md5("jar-a"),"do","m3u8"))).get();
                    check((Integer)proxy[0]==206,"Proxy retains partial-content status");
                    check(((java.util.Map<?,?>)proxy[3]).get("Content-Range").equals("bytes 10-20/30"),"Proxy retains range headers");
                    check(((java.util.Map<?,?>)proxy[3]).get("X-Jar").equals(com.github.catvod.utils.Util.md5("jar-a")),"Fixed jarKey cannot be redirected by another site's recent pointer");
                    try(var input=(java.io.InputStream)proxy[2]) { check(java.util.Arrays.equals(body(),input.readAllBytes()),"Two-MiB proxy body streams without Binder or frame truncation"); }
                    Object[] direct=pool.submit(() -> base.proxyLocal(java.util.Map.of("jarKey",com.github.catvod.utils.Util.md5("jar-a"),"do","direct"))).get();
                    check((Long)direct[4]==body().length,"Direct Nano response retains fixed content length");
                    check(((java.util.Map<?,?>)direct[3]).get("Content-Range").equals("bytes 10-20/30"),"Direct Nano response retains custom headers");
                    try(var input=(java.io.InputStream)direct[2]) { check(java.util.Arrays.equals(body(),input.readAllBytes()),"Direct Nano response streams too"); }
                    base.setRecent("js","https://a/rule.js","jar-a"); base.getSpider("js","https://a/rule.js","","jar-a");
                    Object[] js=pool.submit(() -> base.proxyLocal(java.util.Map.of("do","js","siteKey","js"))).get();
                    try(var input=(java.io.InputStream)js[2]) { check(new String(input.readAllBytes()).equals("js"),"JS siteKey uses registered worker spider without child VodConfig initialization"); }
                    var blocker=base.getSpider("hang","csp_Test","","jar-a");
                    var pending=pool.submit(() -> { try { blocker.searchContent("x",false,"1"); } catch(Exception ignored) { } });
                    check(hanging.await(2,java.util.concurrent.TimeUnit.SECONDS),"Hanging provider entered child"); pending.cancel(true);
                    check(interrupted.await(2,java.util.concurrent.TimeUnit.SECONDS),"Cancellation reaches worker rather than leaving the source request blocked");
                    check(pool.submit(() -> spider.searchContent("next",false,"1")).get().startsWith("next:1"),"Cancellation does not kill healthy source operations");
                    var old=service; var endpoint=(android.os.Binder)old.onBind(new android.content.Intent()); old.onDestroy(); endpoint.die();
                    service=new com.fongmi.android.tv.service.SourceService(); connection.onServiceConnected(new android.content.ComponentName(),service.onBind(new android.content.Intent()));
                    check(pool.submit(() -> spider.homeVideoContent()).get().equals("homeVideo"),"Existing main facades reconnect after worker binder death");
                    base.clear();
                    Object[] retained=pool.submit(() -> base.proxyLocal(java.util.Map.of("jarKey",com.github.catvod.utils.Util.md5("jar-a"),"do","m3u8"))).get();
                    try(var input=(java.io.InputStream)retained[2]) { check(java.util.Arrays.equals(body(),input.readAllBytes()),"Active jarKey proxy remains resolvable across catalog clear"); }
                    checkPolicies(); checkWire(classpath);
                    System.out.println("Actual source isolation and preflight checks passed: "+checks);
                } finally { if(service!=null) service.onDestroy(); pool.shutdownNow(); }
            }
            private static void checkPolicies() {
                var vod=new com.fongmi.android.tv.bean.Vod();
                check(!com.fongmi.android.tv.search.SourceDetailPolicy.playable(vod,false,"第26集",false),"Metadata without flags cannot replace playback");
                var flag=new com.fongmi.android.tv.bean.Flag("普通"); vod.getVodFlags().add(flag);
                check(!com.fongmi.android.tv.search.SourceDetailPolicy.playable(vod,false,"",false),"Empty episode lists are unusable");
                flag.getEpisodes().add(new com.fongmi.android.tv.bean.Episode("第1集","https://a/1"));
                check(!com.fongmi.android.tv.search.SourceDetailPolicy.playable(vod,false,"第26集",false),"A replacement cannot silently start episode one");
                flag.getEpisodes().add(new com.fongmi.android.tv.bean.Episode("第二十六集","https://a/26"));
                check(com.fongmi.android.tv.search.SourceDetailPolicy.playable(vod,false,"EP26",false),"Same semantic episode accepts Chinese/English labels");
                flag.getEpisodes().clear(); flag.getEpisodes().add(new com.fongmi.android.tv.bean.Episode("HD"," "));
                check(!com.fongmi.android.tv.search.SourceDetailPolicy.playable(vod,false,"HD",true),"Empty movie addresses are unusable");
                flag.getEpisodes().clear(); flag.getEpisodes().add(new com.fongmi.android.tv.bean.Episode("1080p","https://a/movie"));
                check(com.fongmi.android.tv.search.SourceDetailPolicy.playable(vod,false,"HD",true),"Movie quality labels are not episode mismatches");
                vod.getVodFlags().clear(); var cloud=new com.fongmi.android.tv.bean.Flag("网盘"); cloud.getEpisodes().add(new com.fongmi.android.tv.bean.Episode("HD","https://a/movie")); vod.getVodFlags().add(cloud);
                check(!com.fongmi.android.tv.search.SourceDetailPolicy.playable(vod,false,"HD",true),"Automatic fallback cannot stall on a cloud-only provider");
                check(com.fongmi.android.tv.search.SourceDetailPolicy.playable(vod,true,"HD",true),"Explicit cloud selection remains allowed");
            }
            private static void checkWire(String classpath) throws Exception {
                var bytes=new java.io.ByteArrayOutputStream(); com.fongmi.android.tv.api.loader.SourceWire.write(bytes,"电影\\n".repeat(400000));
                check(com.fongmi.android.tv.api.loader.SourceWire.read(new java.io.ByteArrayInputStream(bytes.toByteArray())).equals("电影\\n".repeat(400000)),"Unicode control frames exceed one MiB without truncation");
                for(byte[] bad:new byte[][]{new byte[0],{0,0,0},{-1,-1,-1,-1},{127,-1,-1,-1},{0,0,0,4,1}}) { try { com.fongmi.android.tv.api.loader.SourceWire.read(new java.io.ByteArrayInputStream(bad)); throw new AssertionError("Bad frame accepted"); } catch(java.io.IOException expected) { checks++; } }
                try { com.fongmi.android.tv.api.loader.SourceWire.write(new java.io.ByteArrayOutputStream(),"x".repeat(com.fongmi.android.tv.api.loader.SourceWire.MAX_FRAME+1)); throw new AssertionError("Unbounded frame accepted"); } catch(java.io.IOException expected) { checks++; }
                String javaExe=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
                var crashed=new ProcessBuilder(javaExe,"-cp",classpath,"test.CrashChild").start();
                try { com.fongmi.android.tv.api.loader.SourceWire.read(crashed.getInputStream()); throw new AssertionError("Crashed process returned success"); } catch(java.io.EOFException expected) { checks++; }
                check(crashed.waitFor()==13,"An abrupt real child-process exit leaves the host test alive");
                var restarted=new ProcessBuilder(javaExe,"-cp",classpath,"test.CrashChild","restart").start();
                check(com.fongmi.android.tv.api.loader.SourceWire.read(restarted.getInputStream()).equals("reconnected"),"A replacement child can continue protocol work"); check(restarted.waitFor()==0,"Replacement child exits successfully");
            }
        }
        """; }
}
