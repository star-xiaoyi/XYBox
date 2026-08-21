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
 * 缓存并发设置：同时下载几集 + 单集开几条连接。
 * <p>
 * 两个值都不是越大越好，也没有一个放之四海皆准的最优解——不同源站的限流策略差很远，
 * 所以做成用户能当场调的旋钮，而不是写死在代码里的常量。
 */
public class DownloadSettingDialog {

    private final DialogDownloadSettingBinding binding;
    private final Activity activity;

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
        binding.taskSlider.setValue(Setting.getDownloadTask());
        binding.threadSlider.setValue(Setting.getDownloadThread());
        setTaskValue(Setting.getDownloadTask());
        setThreadValue(Setting.getDownloadThread());
    }

    private void initEvent() {
        binding.taskSlider.addOnChangeListener((slider, value, fromUser) -> setTaskValue((int) value));
        binding.threadSlider.addOnChangeListener((slider, value, fromUser) -> setThreadValue((int) value));
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

    private void setThreadValue(int value) {
        binding.threadValue.setText(activity.getString(R.string.download_connection_value, value));
    }

    private void onPositive(DialogInterface dialog, int which) {
        Setting.putDownloadTask((int) binding.taskSlider.getValue());
        Setting.putDownloadThread((int) binding.threadSlider.getValue());
        // 调大了就把排队的补上去；单集连接数对已经在跑的那几集不生效，下一集才按新值开
        DownloadManager.get().applyLimit();
        dialog.dismiss();
    }
}
