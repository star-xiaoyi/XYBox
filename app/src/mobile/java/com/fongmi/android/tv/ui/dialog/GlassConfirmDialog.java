package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogGlassConfirmBinding;

/**
 * 只有正文和操作按钮的玻璃确认框。必须由 DialogFragment 承载，让 Compose 在 Android 16
 * 也能从 ViewTree 取得 LifecycleOwner，不能再直接 new 原生 Dialog。
 */
public class GlassConfirmDialog extends BaseCenterDialog {

    private static final String TAG = "GlassConfirmDialog";

    private DialogGlassConfirmBinding binding;
    private CharSequence message;
    private Runnable confirm;

    public static void show(FragmentActivity activity, @StringRes int message, Runnable confirm) {
        show(activity, activity.getString(message), confirm);
    }

    public static void show(FragmentActivity activity, CharSequence message, Runnable confirm) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        GlassConfirmDialog dialog = new GlassConfirmDialog();
        dialog.message = message;
        dialog.confirm = confirm;
        // 同步加入 FragmentManager，连续点击删除按钮也只会存在一个确认框。
        dialog.showNow(manager, TAG);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogGlassConfirmBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.getRoot().setMessage(message);
    }

    @Override
    protected void initEvent() {
        binding.getRoot().setCancelClickListener(v -> dismiss());
        binding.getRoot().setConfirmClickListener(v -> {
            Runnable action = confirm;
            dismiss();
            if (action != null) action.run();
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setDimAmount(0.22f);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return dialog;
    }
}
