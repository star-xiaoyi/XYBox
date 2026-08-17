package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogDescBinding;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.github.bassaer.library.MDColor;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DescDialog {

    public static void show(Activity activity, CharSequence desc) {
        new DescDialog().create(activity, desc);
    }

    public void create(Activity activity, CharSequence desc) {
        DialogDescBinding binding = DialogDescBinding.inflate(LayoutInflater.from(activity));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        dialog.getWindow().setDimAmount(0);
        // 设置对话框背景为透明，让布局的深色背景显示
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        initView(binding.text, desc, activity);
        dialog.show();
    }

    private void initView(TextView view, CharSequence desc, Activity activity) {
        view.setText(desc, TextView.BufferType.SPANNABLE);
        view.setLinkTextColor(ContextCompat.getColor(activity, R.color.primary));
        CustomMovement.bind(view);
    }
}
