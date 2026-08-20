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
        mBinding.summary.setText(getSummary(trace));
        mBinding.env.setText(getEnv());
        mBinding.trace.setText(trace);
        saveToFile();
    }

    private String getEnv() {
        return BuildConfig.VERSION_NAME + " · " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")";
    }

    /**
     * 复制出去的内容要能独立还原现场：摘要放最前面方便一眼看到，
     * 后面跟完整环境信息、崩溃栈和页面跳转记录。
     */
    private String buildDetails(String trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("【XYBox 崩溃报告】").append("\n\n");
        sb.append("摘要：").append(getSummary(trace).replace("\n\n", " | ")).append("\n\n");
        sb.append("版本：").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")").append("\n");
        sb.append("设备：").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
        sb.append("系统：Android ").append(android.os.Build.VERSION.RELEASE).append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")").append("\n");
        sb.append("时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");
        sb.append("崩溃栈：").append("\n").append(trace).append("\n");
        String log = CustomActivityOnCrash.getActivityLogFromIntent(getIntent());
        if (!TextUtils.isEmpty(log)) sb.append("\n").append("页面记录：").append("\n").append(log);
        return sb.toString();
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
                os.write(details.getBytes(StandardCharsets.UTF_8));
            }
            mBinding.saved.setText(file.getAbsolutePath());
            mBinding.saved.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
        }
    }

    private void copyErrorToClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("XYBox 崩溃报告", details));
            Toast.makeText(this, "报错信息已复制，可直接粘贴", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
