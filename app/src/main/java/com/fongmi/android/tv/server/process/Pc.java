package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.utils.Asset;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 手机与 PC 浏览器之间的通道。
 * <p>
 * 浏览器在这套协议里扮演的就是 DLNA 渲染器的角色：手机把「该放什么、放到哪、是不是在放」写进
 * 这里，浏览器每秒来取一次并照做；反过来浏览器把自己的实际进度报回来，手机的遥控界面和观看
 * 记录都用它。
 * <p>
 * 指令用序号而不是事件：浏览器只比对 videoSeq / seekSeq 有没有变，因此重复拉取无副作用，
 * 页面刷新或断网重连也能自己回到正确状态，不用另做补偿。
 * <p>
 * 这个类在 main 源集里，被 leanback 一起编译，所以不能引用 mobile 的 CastManager——
 * 上报走 {@link Listener}，由 mobile 侧注册。
 */
public class Pc implements Process {

    private static String url = "";
    private static String name = "";
    private static long startPosition;
    private static boolean playing;
    private static boolean casting;
    private static float speed = 1f;
    private static int videoSeq;
    private static int seekSeq;
    private static int playSeq;
    private static int speedSeq;
    private static long seekTo;
    /** 剧集名列表，供电脑上直接选集。 */
    private static JsonArray episodes = new JsonArray();
    private static int episodeIndex = -1;
    private static int episodeSeq;

    private static Listener listener;

    public interface Listener {

        /** 浏览器上报的实际播放状态，单位毫秒。ended 为真表示这一集放完了。 */
        void onPcReport(long position, long duration, boolean playing, boolean ended);

        /** 用户在电脑上选了第 index 集。 */
        void onPcSelect(int index);
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    /** 剧集列表变了或换集了就更新一次，页面靠 episodeSeq 判断要不要重画。 */
    public static synchronized void setEpisodes(List<String> names, int index) {
        JsonArray array = new JsonArray();
        for (String name : names) array.add(name);
        boolean changed = !array.equals(episodes) || episodeIndex != index;
        episodes = array;
        episodeIndex = index;
        if (changed) episodeSeq++;
    }

    /** 换集/开播：videoSeq 一变，浏览器就重新加载并 seek 到 startPosition。 */
    public static synchronized void load(String u, String n, long position) {
        url = u == null ? "" : u;
        name = n == null ? "" : n;
        startPosition = Math.max(position, 0);
        playing = true;
        casting = true;
        videoSeq++;
    }

    /**
     * 手机按下的播放/暂停。必须是边沿触发：如果浏览器每秒拿到 playing 就无条件照做，
     * 用户在电脑上自己点播放时，同一轮里上报还没落地、状态里还是旧的 false，页面就会
     * 立刻把自己按回暂停，表现就是一播一停来回抖。改成只在 playSeq 变化时才动。
     */
    public static synchronized void setPlaying(boolean value) {
        if (playing == value) return;
        playing = value;
        playSeq++;
    }

    /** 浏览器自己改了播放态，只同步值不推指令，否则又会绕回去命令它一次。 */
    public static synchronized void syncPlaying(boolean value) {
        playing = value;
    }

    public static synchronized void setSpeed(float value) {
        if (speed == value) return;
        speed = value;
        speedSeq++;
    }

    public static synchronized void seek(long ms) {
        seekTo = Math.max(ms, 0);
        seekSeq++;
    }

    public static synchronized void stop() {
        casting = false;
        playing = false;
        url = "";
        name = "";
        episodes = new JsonArray();
        episodeIndex = -1;
        episodeSeq++;
        videoSeq++;
    }

    private static synchronized String state() {
        JsonObject o = new JsonObject();
        o.addProperty("casting", casting);
        o.addProperty("url", url);
        o.addProperty("name", name);
        o.addProperty("startPosition", startPosition);
        o.addProperty("playing", playing);
        o.addProperty("speed", speed);
        o.addProperty("videoSeq", videoSeq);
        o.addProperty("seekSeq", seekSeq);
        o.addProperty("playSeq", playSeq);
        o.addProperty("speedSeq", speedSeq);
        o.addProperty("seekTo", seekTo);
        o.add("episodes", episodes);
        o.addProperty("episodeIndex", episodeIndex);
        o.addProperty("episodeSeq", episodeSeq);
        return o.toString();
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String url) {
        return url.equals("/pc") || url.startsWith("/pc/");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String url, Map<String, String> files) {
        if (url.startsWith("/pc/state")) return json(state());
        if (url.startsWith("/pc/report")) return report(session);
        if (url.startsWith("/pc/select")) return select(session);
        return page();
    }

    private NanoHTTPD.Response select(NanoHTTPD.IHTTPSession session) {
        Listener l = listener;
        if (l != null) l.onPcSelect((int) parse(session.getParms().get("index")));
        return json("{}");
    }

    private NanoHTTPD.Response report(NanoHTTPD.IHTTPSession session) {
        Map<String, String> p = session.getParms();
        Listener l = listener;
        if (l != null) l.onPcReport(parse(p.get("position")), parse(p.get("duration")), "1".equals(p.get("playing")), "1".equals(p.get("ended")));
        return json("{}");
    }

    private long parse(String s) {
        try {
            return s == null ? 0 : (long) Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private NanoHTTPD.Response json(String text) {
        NanoHTTPD.Response res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", text);
        res.addHeader("Cache-Control", "no-store");
        return res;
    }

    private NanoHTTPD.Response page() {
        try {
            InputStream is = Asset.open("pc.html");
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html", is, is.available());
        } catch (Exception e) {
            return Nano.error(NanoHTTPD.Response.Status.NOT_FOUND, "pc.html missing");
        }
    }
}
