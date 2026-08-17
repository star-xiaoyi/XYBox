package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.DialogLastWatchBinding;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LastWatchDialog {

    private final DialogLastWatchBinding binding;
    private final AlertDialog dialog;
    private final Activity activity;
    private final History history;

    public static LastWatchDialog create(Activity activity, History history) {
        return new LastWatchDialog(activity, history);
    }

    private LastWatchDialog(Activity activity, History history) {
        this.activity = activity;
        this.history = history;
        this.binding = DialogLastWatchBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
    }

    public void show() {
        initView();
        initEvent();
        dialog.getWindow().setDimAmount(0.5f);
        dialog.show();
    }

    private void initView() {
        binding.content.setText(history.getVodName());
    }

    private void initEvent() {
        binding.play.setOnClickListener(v -> {
            dismiss();
            VideoActivity.start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic());
        });
    }

    private void dismiss() {
        dialog.dismiss();
    }
} 