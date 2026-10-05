package com.fongmi.android.tv.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.databinding.ActivityCrashBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.AppLog;
import com.github.catvod.utils.Prefers;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;

public class CrashActivity extends BaseActivity {

    private ActivityCrashBinding mBinding;
    private String details;
    private String full;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCrash();
        setTrace();
    }

    @Override
    protected void initEvent() {
        mBinding.details.setOnClickListener(v -> copyErrorToClipboard());
        mBinding.share.setOnClickListener(v -> shareError());
        mBinding.restart.setOnClickListener(v -> CustomActivityOnCrash.restartApplication(this, Objects.requireNonNull(CustomActivityOnCrash.getConfigFromIntent(getIntent()))));
    }

    private void setCrash() {
        String log = CustomActivityOnCrash.getActivityLogFromIntent(getIntent());
        if (TextUtils.isEmpty(log)) return;
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isEmpty()) continue;
            if (lines[i].contains(HomeActivity.class.getSimpleName())) {
                Prefers.put("crash", true);
                break;
            }
        }
    }

    private void setTrace() {
        String trace = Objects.toString(CustomActivityOnCrash.getStackTraceFromIntent(getIntent()), "");
        details = buildDetails(trace);
        full = details + "\n\n" + Objects.toString(CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent()), "");
        AppLog.recordCrash(full);
        mBinding.summary.setText(getSummary(trace));
        mBinding.env.setText(getEnv());
        mBinding.trace.setText(full);
        saveToFile();
    }

    private String getEnv() {
        return BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") · " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")";
    }

    /** Keep every frame, including obfuscated/framework frames and nested causes. */
    private String buildDetails(String trace) {
        return "【XY影视崩溃】\n" + getEnv() + "\n"
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())
                + "\n\n" + trace;
    }

    private void shareError() {
        try {
            File folder = new File(getCacheDir(), "log-share");
            if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("无法创建日志目录");
            File report = new File(folder, "XY影视-crash-" + System.currentTimeMillis() + ".txt");
            try (FileOutputStream output = new FileOutputStream(report)) {
                output.write(full.getBytes(StandardCharsets.UTF_8));
            }
            android.net.Uri uri = com.fongmi.android.tv.utils.FileUtil.getShareUri(report);
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newUri(getContentResolver(), "完整崩溃日志", uri));
            startActivity(android.content.Intent.createChooser(intent, "分享完整崩溃日志"));
        } catch (Exception error) { Toast.makeText(this, "分享失败，请复制完整日志", Toast.LENGTH_SHORT).show(); }
    }

    /**
     * 摘要只留最关键的两样：真正的根因（有 Caused by 就取最后一个），
     * 以及第一条落在本项目代码里的栈帧。定位问题基本看这一段就够。
     */
    private String getSummary(String trace) {
        String cause = "";
        String frame = "";
        for (String line : trace.split("\n")) {
            String text = line.trim();
            if (text.startsWith("Caused by:")) cause = text.substring("Caused by:".length()).trim();
            else if (cause.isEmpty() && !text.startsWith("at ") && !text.startsWith("...") && !text.isEmpty()) cause = text;
            if (frame.isEmpty() && text.startsWith("at com.fongmi.")) frame = text;
        }
        StringBuilder sb = new StringBuilder(cause.isEmpty() ? "未知错误" : cause);
        if (!frame.isEmpty()) sb.append("\n\n").append(frame);
        return sb.toString();
    }

    /**
     * 落一份完整日志到应用外部私有目录，方便事后用 adb pull 取走，
     * 也不用担心弹窗被误关掉就丢了现场。
     */
    private void saveToFile() {
        try {
            File dir = new File(getExternalFilesDir(null), "crash");
            if (!dir.exists() && !dir.mkdirs()) return;
            String name = "crash-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
            File file = new File(dir, name);
            try (FileOutputStream os = new FileOutputStream(file)) {
                os.write(Objects.toString(full, details).getBytes(StandardCharsets.UTF_8));
            }
            mBinding.saved.setText(file.getAbsolutePath());
            mBinding.saved.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
        }
    }

    private void copyErrorToClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("XY影视完整崩溃报告", full));
            Toast.makeText(this, "完整崩溃日志已复制", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
