package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityLogBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.LogGlassContentView;
import com.fongmi.android.tv.utils.AppLog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;

import java.io.File;

public class LogActivity extends BaseActivity {

    private ActivityLogBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, LogActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLogBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.header.setTitle(getString(R.string.setting_log));
        updateLineWrapAction(mBinding.logContent.isLineWrapEnabled());
        mBinding.header.setBackdropView(mBinding.logContent);
        mBinding.header.setRenderingEnabled(true);
        refreshLog();
    }

    @Override
    protected void initEvent() {
        mBinding.header.setOnClickListener(v -> finish());
        mBinding.header.setActionClickListener(v -> updateLineWrapAction(mBinding.logContent.toggleLineWrap()));
        mBinding.logContent.setOnActionListener(action -> {
            switch (action) {
                case LogGlassContentView.ACTION_REFRESH: refreshLog(); break;
                case LogGlassContentView.ACTION_COPY: copyLog(); break;
                case LogGlassContentView.ACTION_SHARE: shareLog(); break;
                case LogGlassContentView.ACTION_CLEAR: clearLog(); break;
            }
        });
    }

    private void updateLineWrapAction(boolean lineWrap) {
        mBinding.header.setAction(
                lineWrap ? R.drawable.ic_log_single_line : R.drawable.ic_log_wrap,
                lineWrap ? R.string.log_single_line : R.string.log_auto_wrap);
    }

    private void refreshLog() {
        mBinding.logContent.showLoading();
        App.execute(() -> {
            String text = AppLog.readForDisplay();
            long size = AppLog.size();
            String summary = getString(R.string.log_size_summary, FileUtil.byteCountToDisplaySize(size));
            App.post(() -> {
                if (!isFinishing() && !isDestroyed()) mBinding.logContent.setLog(text, summary);
            });
        });
    }

    private void copyLog() {
        App.execute(() -> {
            String text = AppLog.readAll();
            App.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("XYBox 运行日志", text));
                Notify.show(getString(R.string.log_copied));
            });
        });
    }

    private void shareLog() {
        App.execute(() -> {
            File file = AppLog.createShareFile(this);
            App.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (file == null || !file.isFile()) {
                    Notify.show(getString(R.string.log_export_failed));
                    return;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_STREAM, FileUtil.getShareUri(file));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, getString(R.string.log_share)));
                } catch (Exception e) {
                    Notify.show(getString(R.string.log_export_failed));
                }
            });
        });
    }

    private void clearLog() {
        App.execute(() -> {
            boolean success = AppLog.clear();
            App.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                Notify.show(getString(success ? R.string.log_cleared : R.string.log_clear_failed));
                refreshLog();
            });
        });
    }
}
