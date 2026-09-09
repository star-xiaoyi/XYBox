package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.utils.ResUtil;

/**
 * 居中弹窗的基类，和 BaseDialog（底部抽屉）用法一致，只是从屏幕中间弹出来。
 * 适合设备选择这种内容不多、需要聚焦的场景。
 */
public abstract class BaseCenterDialog extends DialogFragment {

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initView();
        initEvent();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected int getDialogWidthDp() {
        return 360;
    }

    protected int getDialogHeightDp() {
        return 0;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // 圆角由布局自己画，window 背景必须透明，否则四角会露出方形底
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null || getDialog().getWindow() == null) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        // 固定尺寸只在空间充足时使用；小屏至少保留约 14% 左右和 25% 上下的整体留白。
        int availableWidth = Math.min(screenWidth - ResUtil.dp2px(24), Math.round(screenWidth * 0.86f));
        int availableHeight = Math.min(screenHeight - ResUtil.dp2px(24), Math.round(screenHeight * 0.75f));
        int width = Math.min(ResUtil.dp2px(getDialogWidthDp()), availableWidth);
        int height = getDialogHeightDp() <= 0
                ? WindowManager.LayoutParams.WRAP_CONTENT
                : Math.min(ResUtil.dp2px(getDialogHeightDp()), availableHeight);
        getDialog().getWindow().setLayout(width, height);
    }
}
