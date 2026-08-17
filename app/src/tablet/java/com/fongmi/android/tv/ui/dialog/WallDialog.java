package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.databinding.DialogWallBinding;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.graphics.Bitmap;

public class WallDialog {

    public static final int REQUEST_PICK_WALLPAPER = 9001;

    private final DialogWallBinding binding;
    private final Fragment fragment;
    private AlertDialog dialog;

    public static WallDialog create(Fragment fragment) {
        return new WallDialog(fragment);
    }

    public WallDialog(Fragment fragment) {
        this.fragment = fragment;
        this.binding = DialogWallBinding.inflate(LayoutInflater.from(fragment.getActivity()));
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(fragment.getActivity())
                .setTitle("壁纸")
                .setView(binding.getRoot())
                .setNegativeButton(R.string.dialog_negative, null)
                .create();
        dialog.show();
    }

    private void initView() {
        // 根据当前选中的壁纸索引，显示对应的勾选标记
        updateCheckmarks(Setting.getWall());
    }

    private void initEvent() {
        binding.wall1.setOnClickListener(v -> selectBuiltin(1));
        binding.wall2.setOnClickListener(v -> selectBuiltin(2));
        binding.wall3.setOnClickListener(v -> selectBuiltin(3));
        binding.wall4.setOnClickListener(v -> selectBuiltin(4));
        binding.wall5.setOnClickListener(v -> selectBuiltin(5));
        binding.wall6.setOnClickListener(v -> selectBuiltin(6));
        binding.localImage.setOnClickListener(v -> pickLocalImage());
    }

    /** 选择内置壁纸 */
    private void selectBuiltin(int index) {
        // 清除本地自定义壁纸文件，回到内置
        File customFile = FileUtil.getWall(0);
        if (customFile.exists()) customFile.delete();

        WallConfig.refresh(index);
        updateCheckmarks(index);

        // 延迟一点关闭弹窗，让用户看到勾选效果
        binding.getRoot().postDelayed(() -> {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        }, 300);
    }

    /** 打开系统图库选择本地图片 */
    private void pickLocalImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        fragment.startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
        dialog.dismiss();
    }

    /** 根据当前壁纸索引更新勾选显示 */
    private void updateCheckmarks(int currentIndex) {
        binding.wall1Check.setVisibility(currentIndex == 1 ? View.VISIBLE : View.GONE);
        binding.wall2Check.setVisibility(currentIndex == 2 ? View.VISIBLE : View.GONE);
        binding.wall3Check.setVisibility(currentIndex == 3 ? View.VISIBLE : View.GONE);
        binding.wall4Check.setVisibility(currentIndex == 4 ? View.VISIBLE : View.GONE);
        binding.wall5Check.setVisibility(currentIndex == 5 ? View.VISIBLE : View.GONE);
        binding.wall6Check.setVisibility(currentIndex == 6 ? View.VISIBLE : View.GONE);
        // 本地壁纸(index=0)时，内置壁纸均不显示勾选
    }

    /**
     * 在 Fragment.onActivityResult 中调用此方法处理图库返回结果
     * 用法：WallDialog.handleActivityResult(requestCode, resultCode, data, fragment.getActivity())
     */
    public static void handleActivityResult(int requestCode, int resultCode, Intent data, Activity activity) {
        if (requestCode != REQUEST_PICK_WALLPAPER) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        Notify.progress(activity);
        App.execute(() -> applyLocalWallpaper(uri, activity));
    }

    private static void applyLocalWallpaper(Uri uri, Activity activity) {
        try {
            File destFile = FileUtil.getWall(0);
            // 读取 Uri 内容写入临时文件
            InputStream inputStream = activity.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                App.post(() -> {
                    Notify.dismiss();
                    Notify.show("读取图片失败");
                });
                return;
            }
            // 先写入临时文件
            File tempFile = new File(destFile.getParent(), "wallpaper_temp");
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.close();
            inputStream.close();

            // 用 Glide 裁剪到屏幕尺寸并以 JPEG 压缩写入最终文件
            Bitmap bitmap = Glide.with(App.get())
                    .asBitmap()
                    .load(tempFile)
                    .centerCrop()
                    .override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight())
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .submit()
                    .get();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, new FileOutputStream(destFile));
            bitmap.recycle();
            tempFile.delete();

            App.post(() -> {
                Notify.dismiss();
                WallConfig.refresh(0);  // index=0 = 本地自定义壁纸
            });
        } catch (Exception e) {
            App.post(() -> {
                Notify.dismiss();
                Notify.show("设置壁纸失败");
            });
        }
    }
}
