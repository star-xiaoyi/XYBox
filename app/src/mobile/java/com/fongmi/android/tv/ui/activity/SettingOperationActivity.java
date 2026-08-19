package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.ActivitySettingOperationBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingOperationActivity extends BaseActivity {

    private static final int[] GESTURE_SEEK_SECONDS = {5, 10, 15, 30};

    private ActivitySettingOperationBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingOperationActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingOperationBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.gestureDoubleTapPlaySwitch.setChecked(Setting.isGestureDoubleTapPlay());
        mBinding.gestureDoubleTapSeekSwitch.setChecked(Setting.isGestureDoubleTapSeek());
        mBinding.gestureBrightnessSwitch.setChecked(Setting.isGestureBrightness());
        mBinding.gestureVolumeSwitch.setChecked(Setting.isGestureVolume());
        updateGestureSeekSeconds();
        updateGestureSeekVisibility();
    }

    @Override
    protected void initEvent() {
        mBinding.back.setOnClickListener(v -> finish());
        // 直接给开关按钮设置点击监听器，避免双重点击冲突
        mBinding.gestureDoubleTapPlaySwitch.setOnClickListener(this::setGestureDoubleTapPlay);
        mBinding.gestureDoubleTapSeekSwitch.setOnClickListener(this::setGestureDoubleTapSeek);
        mBinding.gestureBrightnessSwitch.setOnClickListener(this::setGestureBrightness);
        mBinding.gestureVolumeSwitch.setOnClickListener(this::setGestureVolume);
        // 点击整行也可以切换开关
        mBinding.gestureDoubleTapPlay.setOnClickListener(view -> mBinding.gestureDoubleTapPlaySwitch.performClick());
        mBinding.gestureDoubleTapSeek.setOnClickListener(view -> mBinding.gestureDoubleTapSeekSwitch.performClick());
        mBinding.gestureBrightness.setOnClickListener(view -> mBinding.gestureBrightnessSwitch.performClick());
        mBinding.gestureVolume.setOnClickListener(view -> mBinding.gestureVolumeSwitch.performClick());
        mBinding.gestureSeekSeconds.setOnClickListener(this::setGestureSeekSeconds);
    }

    private void setGestureDoubleTapPlay(View view) {
        Setting.putGestureDoubleTapPlay(!Setting.isGestureDoubleTapPlay());
    }

    private void setGestureDoubleTapSeek(View view) {
        Setting.putGestureDoubleTapSeek(!Setting.isGestureDoubleTapSeek());
        updateGestureSeekVisibility();
    }

    private void setGestureBrightness(View view) {
        Setting.putGestureBrightness(!Setting.isGestureBrightness());
    }

    private void setGestureVolume(View view) {
        Setting.putGestureVolume(!Setting.isGestureVolume());
    }

    private void setGestureSeekSeconds(View view) {
        String[] labels = new String[GESTURE_SEEK_SECONDS.length];
        int checked = 0;
        for (int i = 0; i < GESTURE_SEEK_SECONDS.length; i++) {
            labels[i] = getString(R.string.player_gesture_seek_seconds_value, GESTURE_SEEK_SECONDS[i]);
            if (GESTURE_SEEK_SECONDS[i] == Setting.getGestureSeekSeconds()) checked = i;
        }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.player_gesture_seek_seconds).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(labels, checked, (dialog, which) -> {
            Setting.putGestureSeekSeconds(GESTURE_SEEK_SECONDS[which]);
            updateGestureSeekSeconds();
            dialog.dismiss();
        }).show();
    }

    private void updateGestureSeekSeconds() {
        mBinding.gestureSeekSecondsText.setText(getString(R.string.player_gesture_seek_seconds_value, Setting.getGestureSeekSeconds()));
    }

    private void updateGestureSeekVisibility() {
        boolean visible = Setting.isGestureDoubleTapSeek();
        mBinding.gestureSeekSeconds.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.gestureSeekSecondsDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
