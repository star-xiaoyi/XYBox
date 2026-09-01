package com.fongmi.android.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.databinding.DialogUpdateBinding;
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

    private DialogUpdateBinding binding;
    private Download download;
    private AlertDialog dialog;
    private boolean dev;
    private boolean silent;
    private String apkUrl;

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("XYBox-update.apk");
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
        if (!silent) Notify.tip(App.get().getString(R.string.update_check));
        App.execute(() -> checkUpdate(activity));
    }

    private void checkUpdate(Activity activity) {
        try {
            String response = OkHttp.string(dev ? DEV_API : RELEASE_API);
            if (TextUtils.isEmpty(response)) {
                tipError("检查更新失败：网络连接异常");
                return;
            }

            JSONObject release = parseRelease(response);
            if (release == null) {
                tipError("检查更新失败：未找到发布版本");
                return;
            }
            // GitHub 的错误响应（404、限流）带 message 字段而非 release 数据。
            // 不能用 response.contains("404") 判断：APK 体积等数字里也可能出现 404。
            if (release.has("message")) {
                String message = release.optString("message");
                tipError(message.contains("rate limit")
                        ? "检查更新失败：API请求次数已达上限"
                        : "检查更新失败：未找到发布版本");
                return;
            }

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
        binding = DialogUpdateBinding.inflate(LayoutInflater.from(activity));
        binding.title.setText(App.get().getString(R.string.update_version, version));
        binding.desc.setText(TextUtils.isEmpty(desc) ? "有新版本可用，建议更新。" : desc.trim());
        dialog = new AlertDialog.Builder(activity).setView(binding.getRoot()).setCancelable(true).create();
        dialog.setOnCancelListener(d -> { if (download != null) download.cancel(); });
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
        if (download != null) download.cancel();
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
        download = Download.create(apkUrl, getFile(), apkUrl, this);
        download.start();
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
        if (binding == null || progress < 0) return;
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
        App.post(() -> {
            if (binding == null) return;
            binding.progress.setIndeterminate(false);
            binding.progressText.setText("下载失败：" + msg);
            binding.buttonGroup.setVisibility(View.VISIBLE);
        });
    }
}
