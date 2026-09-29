package com.fongmi.android.tv;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.service.DownloadService;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UpdateInstaller;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/**
 * 应用内更新。
 *
 * 交互约定：已经是最新版本时只在底部弹一条轻提示；确实有新版本时才弹居中对话框，
 * 展示更新说明，并在同一个对话框里完成下载 → 进度 → 安装。
 */
public class Updater implements Download.Callback {

    private static final String RELEASE_API = "https://api.github.com/repos/star-xiaoyi/XYBox/releases/latest";
    /**
     * dev 通道要能看到 beta。GitHub 的 /releases/latest 按设计会跳过所有 prerelease，
     * 所以预发布包在那个接口上完全不可见（本项目实际踩过：v0.3.2-beta 发出来手机检查不到更新）。
     * 不能相信列表顺序：同一份未提交代码连续发 beta 时，GitHub 会给它们相同的 created_at，
     * beta10 甚至会按字符串夹在 beta1 和 beta2 之间。必须拉回列表后自己按版本号选最大值。
     */
    private static final String DEV_API = "https://api.github.com/repos/star-xiaoyi/XYBox/releases?per_page=100";

    private static final String REPOSITORY = "https://github.com/star-xiaoyi/XYBox";
    private static final java.util.concurrent.atomic.AtomicBoolean CHECKING = new java.util.concurrent.atomic.AtomicBoolean();
    private static final String[] CACHE = new String[2];
    private static final long[] CACHE_TIME = new long[2];
    private static long apiRetryAt;
    private DialogUpdateBinding binding;
    private Download download;
    private AlertDialog dialog;
    private boolean dev;
    private boolean silent;
    private String apkUrl;
    private String targetVersion;

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        String version = TextUtils.isEmpty(targetVersion) ? "unknown" : targetVersion.replaceAll("[^0-9A-Za-z._-]", "_");
        return Path.files("updates/XYBox-update-" + version + ".apk");
    }

    /** 用户主动点版本号触发：过程中的失败/无更新都要给反馈。 */
    public Updater force() {
        this.silent = false;
        return this;
    }

    /** 启动时自动检查：没有新版本就完全安静。 */
    public Updater auto() {
        this.silent = true;
        return this;
    }

    public Updater release() {
        this.dev = false;
        return this;
    }

    public Updater dev() {
        this.dev = true;
        return this;
    }

    public void start(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!CHECKING.compareAndSet(false, true)) {
            if (!silent) Notify.tip("正在检查更新，请稍候");
            return;
        }
        if (!silent) Notify.tip(App.get().getString(R.string.update_check));
        App.execute(() -> { try { checkUpdate(activity); } finally { CHECKING.set(false); } });
    }

    private void checkUpdate(Activity activity) {
        try {
            JSONObject release = fetchRelease();
            String tagName = release.optString("tag_name");
            String version = tagName.startsWith("v") || tagName.startsWith("V") ? tagName.substring(1) : tagName;
            String body = release.optString("body");

            if (!needUpdate(version)) {
                if (!silent) Notify.tip("已是最新版本 " + BuildConfig.VERSION_NAME);
                return;
            }

            apkUrl = findApk(release.optJSONArray("assets"));
            if (TextUtils.isEmpty(apkUrl)) {
                tipError("发现新版本 " + version + "，但未找到可下载的安装包");
                return;
            }

            App.post(() -> show(activity, version, body));
        } catch (Exception e) {
            Logger.e("Updater: " + e.getMessage());
            tipError("检查更新失败：" + e.getMessage());
        }
    }

    /** API is quota-limited per public IP. Cache successful lookups and fall back to official web feeds. */
    private JSONObject fetchRelease() throws Exception {
        int channel = dev ? 1 : 0;
        long now = android.os.SystemClock.elapsedRealtime();
        if (CACHE[channel] != null && now - CACHE_TIME[channel] < 60_000)
            return new JSONObject(CACHE[channel]);
        JSONObject release = null;
        if (System.currentTimeMillis() >= apiRetryAt) {
            try (okhttp3.Response response = OkHttp.newCall(dev ? DEV_API : RELEASE_API).execute()) {
                if (response.code() == 403 || response.code() == 429) {
                    long retry = System.currentTimeMillis() + 60_000;
                    try { retry = Math.max(retry, Long.parseLong(response.header("X-RateLimit-Reset", "0")) * 1000); }
                    catch (NumberFormatException ignored) { }
                    apiRetryAt = retry;
                }
                if (response.isSuccessful() && response.body() != null) release = parseRelease(response.body().string());
                if (release != null && (release.has("message") || TextUtils.isEmpty(release.optString("tag_name")))) release = null;
            } catch (Exception error) { Logger.w("Updater API: " + error.getClass().getSimpleName()); }
        }
        if (release == null || TextUtils.isEmpty(findApk(release.optJSONArray("assets")))) release = fetchWebRelease();
        if (release == null || TextUtils.isEmpty(findApk(release.optJSONArray("assets"))))
            throw new java.io.IOException("暂时无法获取安装包，请稍后重试或从项目发布页下载");
        CACHE[channel] = release.toString(); CACHE_TIME[channel] = now;
        return release;
    }

    private JSONObject fetchWebRelease() throws Exception {
        JSONObject release = null;
        if (dev) {
            try (okhttp3.Response response = OkHttp.newCall(REPOSITORY + "/releases.atom").execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("发布订阅暂时不可用");
                release = parseFeed(response.body().string());
            }
        } else {
            // GitHub redirects this public page to the latest non-prerelease tag.
            try (okhttp3.Response response = OkHttp.newCall(REPOSITORY + "/releases/latest").execute()) {
                String link = response.request().url().toString();
                String prefix = REPOSITORY + "/releases/tag/";
                if (response.isSuccessful() && link.startsWith(prefix)) {
                    String tag = link.substring(prefix.length());
                    if (validTag(tag) && !normalizeVersion(tag).contains("-")) release = new JSONObject().put("tag_name", tag);
                }
            }
        }
        if (release == null) throw new java.io.IOException("暂时无法读取发布版本");
        String tag = release.getString("tag_name");
        try (okhttp3.Response response = OkHttp.newCall(REPOSITORY + "/releases/expanded_assets/" + tag).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("安装包列表暂时不可用");
            release.put("assets", parseWebAssets(response.body().string(), tag));
        }
        return release;
    }

    private boolean validTag(String tag) {
        return tag.matches("[vV]?[0-9]+\\.[0-9]+\\.[0-9]+(?:-beta[0-9]+)?");
    }

    private JSONObject parseFeed(String xml) throws Exception {
        org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new java.io.StringReader(xml));
        JSONObject latest = null, entry = null;
        String prefix = REPOSITORY + "/releases/tag/";
        for (int event = parser.next(); event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                if ("entry".equals(parser.getName())) entry = new JSONObject();
                else if (entry != null && "link".equals(parser.getName())) {
                    String href = parser.getAttributeValue(null, "href");
                    if (href != null && href.startsWith(prefix) && validTag(href.substring(prefix.length())))
                        entry.put("tag_name", href.substring(prefix.length()));
                } else if (entry != null && "content".equals(parser.getName())) {
                    entry.put("body", android.text.Html.fromHtml(parser.nextText()).toString().trim());
                }
            } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG && "entry".equals(parser.getName())) {
                if (entry != null && entry.has("tag_name") && (latest == null
                        || compare(normalizeVersion(entry.getString("tag_name")), normalizeVersion(latest.getString("tag_name"))) > 0)) latest = entry;
                entry = null;
            }
        }
        return latest;
    }

    private JSONArray parseWebAssets(String html, String tag) throws Exception {
        String prefix = "/star-xiaoyi/XYBox/releases/download/" + tag + "/";
        java.util.regex.Matcher links = java.util.regex.Pattern.compile("href=\"(" + java.util.regex.Pattern.quote(prefix) + "[^\"<>]+)\"").matcher(html);
        JSONArray assets = new JSONArray();
        while (links.find()) {
            String path = links.group(1).replace("&amp;", "&");
            String name = android.net.Uri.decode(path.substring(prefix.length()));
            if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
            assets.put(new JSONObject().put("name", name).put("browser_download_url", "https://github.com" + path));
        }
        return assets;
    }

    /**
     * release 通道拿到的是单个对象，dev 通道拿到的是数组。
     * 数组里跳过草稿（draft 的资产还没公开，下不下来），保留 prerelease，
     * 再用和安装判断完全相同的数值规则选最大版本，绝不依赖 GitHub 的返回顺序。
     */
    private JSONObject parseRelease(String response) throws Exception {
        String trimmed = response.trim();
        if (!trimmed.startsWith("[")) return new JSONObject(trimmed);
        JSONArray releases = new JSONArray(trimmed);
        JSONObject latest = null;
        String latestVersion = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft")) continue;
            String version = normalizeVersion(release.optString("tag_name"));
            if (TextUtils.isEmpty(version)) continue;
            if (latest == null || compare(version, latestVersion) > 0) {
                latest = release;
                latestVersion = version;
            }
        }
        return latest;
    }

    private String normalizeVersion(String tagName) {
        return tagName.startsWith("v") || tagName.startsWith("V") ? tagName.substring(1) : tagName;
    }

    private void tipError(String message) {
        if (!silent) Notify.tip(message);
        else Logger.w("Updater: " + message);
    }

    /**
     * 远端是否比本地新。
     *
     * 版本号形如 0.3.4 或 0.3.4-beta2：先逐段比较主体数字，主体相同时按预发布规则收尾——
     * 正式版永远高于同主体的任何 beta（所以 0.3.4-beta2 能升到 0.3.4），
     * 两个 beta 之间比后缀里的序号（beta1 < beta2）。
     * 这样 beta→beta、beta→正式、正式→正式三条路都成立，且不会把用户往回降。
     */
    private boolean needUpdate(String remoteVersion) {
        if (TextUtils.isEmpty(remoteVersion)) return false;
        return compare(remoteVersion, BuildConfig.VERSION_NAME) > 0;
    }

    private int compare(String a, String b) {
        String[] baseA = base(a).split("\\.");
        String[] baseB = base(b).split("\\.");
        int length = Math.max(baseA.length, baseB.length);
        for (int i = 0; i < length; i++) {
            int diff = segment(baseA, i) - segment(baseB, i);
            if (diff != 0) return diff;
        }
        // 主体相同：正式版（无后缀）视为最高
        boolean preA = isPre(a);
        boolean preB = isPre(b);
        if (preA != preB) return preA ? -1 : 1;
        if (!preA) return 0;
        return preIndex(a) - preIndex(b);
    }

    /** 取 '-' 之前的主体，"0.3.4-beta2" → "0.3.4"。 */
    private String base(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    private boolean isPre(String version) {
        return version.indexOf('-') >= 0;
    }

    /** 后缀里的序号，"-beta2" → 2；"-beta" 这种不带序号的按 0 处理。 */
    private int preIndex(String version) {
        StringBuilder digits = new StringBuilder();
        for (char c : version.substring(version.indexOf('-') + 1).toCharArray()) {
            if (c >= '0' && c <= '9') digits.append(c);
        }
        try {
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private int segment(String[] parts, int index) {
        if (index >= parts.length) return 0;
        StringBuilder digits = new StringBuilder();
        for (char c : parts[index].toCharArray()) {
            if (c < '0' || c > '9') break;
            digits.append(c);
        }
        try {
            return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 挑选安装包。优先 mode + abi 双匹配，其次只匹配 abi，最后退回唯一的 apk 资产——
     * 早期发布用的是 XYBox-release.apk 这种不含 abi 的文件名，不能因此判定「没有更新」。
     */
    private String findApk(JSONArray assets) {
        if (assets == null) return null;
        String mode = BuildConfig.MODE.toLowerCase();
        String abi = BuildConfig.ABI.toLowerCase();
        String abiDash = abi.replace('_', '-');
        String abiShort = abi.replace("arm64_v8a", "arm64").replace("armeabi_v7a", "armv7");

        String byModeAndAbi = null;
        String byAbi = null;
        String anyApk = null;

        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name").toLowerCase();
            if (!name.endsWith(".apk")) continue;
            String url = asset.optString("browser_download_url");
            if (TextUtils.isEmpty(url)) continue;
            if (anyApk == null) anyApk = url;
            boolean matchAbi = name.contains(abiShort) || name.contains(abiDash) || name.contains(abi);
            if (matchAbi && byAbi == null) byAbi = url;
            if (matchAbi && name.contains(mode) && byModeAndAbi == null) byModeAndAbi = url;
        }

        if (byModeAndAbi != null) return byModeAndAbi;
        if (byAbi != null) return byAbi;
        return anyApk;
    }

    /**
     * 自绘的居中弹窗：标题、更新说明、进度条、按钮全在同一张卡片里，
     * 点"立即更新"就地把按钮换成进度条，不再另开一层。
     */
    private void show(Activity activity, String version, String desc) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        targetVersion = version;
        binding = DialogUpdateBinding.inflate(LayoutInflater.from(activity));
        binding.title.setText(App.get().getString(R.string.update_version, version));
        binding.desc.setText(TextUtils.isEmpty(desc) ? "有新版本可用，建议更新。" : desc.trim());
        // 系统返回手势、点击弹窗外部和切换前后台都不应取消下载。
        // 下载只能由界面上明确的“取消”按钮终止。
        dialog = new AlertDialog.Builder(activity).setView(binding.getRoot()).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
        setDialogWidth(activity);
        capDescHeight(activity);
        binding.positive.setOnClickListener(this::confirm);
        binding.negative.setOnClickListener(this::cancel);
        binding.cancel.setOnClickListener(this::cancel);
    }

    private void setDialogWidth(Activity activity) {
        if (dialog.getWindow() == null) return;
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min((int) (screen * 0.88f), ResUtil.dp2px(400));
        dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 更新说明是 wrap_content 的，条目一多整块自定义视图就把对话框的按钮挤出可视区。
     * 弹出后量一次，超过屏幕四成高度就钉死，多出来的内容让它自己滚。
     */
    private void capDescHeight(Activity activity) {
        binding.scroll.post(() -> {
            int max = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.4f);
            if (binding.scroll.getHeight() <= max) return;
            ViewGroup.LayoutParams params = binding.scroll.getLayoutParams();
            params.height = max;
            binding.scroll.setLayoutParams(params);
        });
    }

    private void cancel(View view) {
        // 只是这一次不更新，不能顺手关掉整个更新功能，否则之后云端有新版也检查不出来
        if (download != null) {
            download.cancel();
            DownloadService.finishUpdate();
        }
        dismiss();
    }

    private void confirm(View view) {
        if (TextUtils.isEmpty(apkUrl)) {
            Notify.tip("无法获取下载链接");
            return;
        }
        binding.buttonGroup.setVisibility(View.GONE);
        binding.progressGroup.setVisibility(View.VISIBLE);
        binding.progress.setIndeterminate(true);
        binding.progressText.setText(R.string.update_connecting);
        File cached = getFile();
        if (isDownloaded(cached)) {
            binding.progress.setIndeterminate(false);
            binding.progress.setProgressCompat(100, false);
            binding.progressText.setText("安装包已下载，正在安装…");
            success(cached);
            return;
        }
        if (DownloadService.isUpdateBusy()) {
            Notify.tip("更新正在后台下载，请稍后再试");
            dismiss();
            return;
        }
        DownloadService.beginUpdate();
        download = Download.create(apkUrl, getFile(), apkUrl, this);
        download.start();
    }

    /** 下载到一半的 APK 同样以 ZIP 文件头开头，必须让系统解析并核对目标版本。 */
    private boolean isDownloaded(File file) {
        if (file == null || !file.isFile() || file.length() == 0 || TextUtils.isEmpty(targetVersion)) return false;
        try {
            PackageInfo info = App.get().getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
            return info != null && targetVersion.equals(info.versionName);
        } catch (Exception e) {
            return false;
        }
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    // Download 已经切回主线程了，这里不用再 post 一层
    @Override
    public void progress(int progress) {
        if (progress < 0) return;
        DownloadService.updateProgress(progress);
        if (binding == null) return;
        binding.progress.setIndeterminate(false);
        binding.progress.setProgressCompat(progress, true);
        binding.progressText.setText(String.format(Locale.getDefault(), "正在下载 %d%%", progress));
    }

    @Override
    public void retry(String reason) {
        if (binding == null) return;
        binding.progressText.setText(App.get().getString(R.string.update_retrying) + (TextUtils.isEmpty(reason) ? "" : "（" + reason + "）"));
    }

    @Override
    public void success(File file) {
        if (!isDownloaded(file)) {
            if (file != null && file.isFile()) file.delete();
            error("安装包校验失败，请重新下载");
            return;
        }
        DownloadService.finishUpdate();
        App.post(() -> {
            if (binding != null) {
                binding.cancel.setVisibility(View.GONE);
                binding.progress.setIndeterminate(false);
                binding.progress.setProgress(100);
                binding.progressText.setText("下载完成，正在安装…");
            }
            UpdateInstaller.get().install(file);
            App.post(this::dismiss, 800);
        });
    }

    @Override
    public void error(String msg) {
        DownloadService.finishUpdate();
        App.post(() -> {
            if (binding == null) return;
            binding.progress.setIndeterminate(false);
            binding.progressText.setText("下载失败：" + msg);
            binding.buttonGroup.setVisibility(View.VISIBLE);
        });
    }
}
