package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityLogBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.LogGlassContentView;
import com.fongmi.android.tv.utils.AppLog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.utils.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LogActivity extends BaseActivity {

    private ActivityLogBinding mBinding;
    private boolean mSavingLog;
    private final ActivityResultLauncher<String> saveDocument = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), this::saveLogDocument);

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
                case LogGlassContentView.ACTION_SAVE: saveLog(); break;
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

    private void saveLog() {
        if (mSavingLog) return;
        setSavingLog(true);
        try {
            saveDocument.launch(AppLog.exportFileName());
        } catch (Exception error) {
            setSavingLog(false);
            Logger.e("LogExport: cannot open document picker", error);
            Notify.show(getString(R.string.log_export_failed));
        }
    }

    private void saveLogDocument(Uri uri) {
        if (uri == null) {
            setSavingLog(false);
            return;
        }
        setSavingLog(true);
        App.execute(() -> {
            File snapshot = null;
            boolean success = false;
            try {
                snapshot = AppLog.createShareFile(getApplicationContext());
                if (snapshot == null || !snapshot.isFile()) throw new IOException("Log snapshot unavailable");
                long written = 0;
                try (FileInputStream input = new FileInputStream(snapshot);
                     OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    if (output == null) throw new IOException("Document provider returned no output stream");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        written += count;
                    }
                    output.flush();
                }
                if (written != snapshot.length()) throw new IOException("Incomplete log export");
                Logger.i("LogExport: action=saved bytes=" + written + " provider=" + uri.getAuthority());
                success = true;
            } catch (Exception error) {
                Logger.e("LogExport: save failed", error);
            } finally {
                if (snapshot != null && !snapshot.delete()) Logger.w("LogExport: temporary snapshot retained");
            }
            boolean saved = success;
            App.post(() -> {
                setSavingLog(false);
                if (isFinishing() || isDestroyed()) return;
                Notify.show(getString(saved ? R.string.log_saved : R.string.log_export_failed));
            });
        });
    }

    private void setSavingLog(boolean saving) {
        mSavingLog = saving;
        if (mBinding != null && !isDestroyed()) mBinding.logContent.setSavingLog(saving);
    }

    private void shareLog() {
        if (mSavingLog) return;
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
