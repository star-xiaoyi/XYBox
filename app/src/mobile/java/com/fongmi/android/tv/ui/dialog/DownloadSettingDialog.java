package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.LayoutInflater;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.DialogDownloadSettingBinding;
import com.fongmi.android.tv.download.DownloadManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 缓存策略设置：用户只选择智能/极速和同时任务数，连接数由模式内部管理。
 */
public class DownloadSettingDialog {

    private final DialogDownloadSettingBinding binding;
    private final Activity activity;
    private int initialMode;
    private int initialTask;
    private int mode;

    public static DownloadSettingDialog create(Activity activity) {
        return new DownloadSettingDialog(activity);
    }

    public DownloadSettingDialog(Activity activity) {
        this.activity = activity;
        this.binding = DialogDownloadSettingBinding.inflate(LayoutInflater.from(activity));
    }

    public void show() {
        initView();
        initEvent();
        initDialog();
    }

    private void initView() {
        mode = initialMode = Setting.getDownloadMode();
        initialTask = Setting.getDownloadTask();
        binding.modeSelector.setOptions(new String[]{
                activity.getString(R.string.download_mode_smart),
                activity.getString(R.string.download_mode_fast)
        });
        binding.modeSelector.setSelectedIndex(mode);
        binding.taskSlider.setRange(1, Setting.DOWNLOAD_TASK_MAX, 1);
        binding.taskSlider.setValue(Setting.getDownloadTask());
        setModeHint();
        setTaskValue(Setting.getDownloadTask());
    }

    private void initEvent() {
        binding.modeSelector.setOnOptionSelectedListener(index -> {
            mode = index;
            setModeHint();
        });
        binding.taskSlider.setOnValueChangeListener(value -> setTaskValue(Math.round(value)));
    }

    private void initDialog() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.download_setting)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, this::onPositive)
                .show();
    }

    private void setTaskValue(int value) {
        binding.taskValue.setText(activity.getString(R.string.download_concurrent_task_value, value));
    }

    private void setModeHint() {
        binding.modeHint.setText(mode == Setting.DOWNLOAD_MODE_FAST
                ? R.string.download_mode_fast_hint
                : R.string.download_mode_smart_hint);
    }

    private void onPositive(DialogInterface dialog, int which) {
        int task = Math.round(binding.taskSlider.getValue());
        Setting.putDownloadMode(mode);
        Setting.putDownloadTask(task);
        if (mode != initialMode || task != initialTask) DownloadManager.get().applySettings();
        dialog.dismiss();
    }
}
