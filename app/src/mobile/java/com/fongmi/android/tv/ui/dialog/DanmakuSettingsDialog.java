package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmakuSettingsBinding;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.ui.adapter.DanmakuAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.FileChooser;

import java.util.Locale;

/** 播放器内的固定尺寸玻璃弹幕设置窗，弹幕源选择也统一收在这里。 */
public final class DanmakuSettingsDialog extends BaseCenterDialog implements DanmakuAdapter.OnClickListener {

    private static final String TAG = "DanmakuSettingsDialog";

    private final DanmakuAdapter adapter = new DanmakuAdapter(this);
    private DialogDanmakuSettingsBinding binding;
    private Players player;
    private float displayScale = 1.0f;

    public static DanmakuSettingsDialog create() {
        return new DanmakuSettingsDialog();
    }

    public DanmakuSettingsDialog player(Players player) {
        this.player = player;
        return this;
    }

    public DanmakuSettingsDialog displayScale(float displayScale) {
        this.displayScale = displayScale;
        return this;
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        show(manager, TAG);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuSettingsBinding.inflate(inflater, container, false);
    }

    @Override
    protected int getDialogHeightDp() {
        return 360;
    }

    @Override
    protected void initView() {
        if (player == null) {
            dismiss();
            return;
        }
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter.addAll(player.getDanmakus()));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 10));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        binding.sizeSlider.setRange(0.6f, 1.6f, 0.05f);
        binding.sizeSlider.setValue(Setting.getDanmakuSize());
        binding.opacitySlider.setRange(0.1f, 1.0f, 0.05f);
        binding.opacitySlider.setValue(Setting.getDanmakuOpacity());
        binding.speedSlider.setRange(0.5f, 2.0f, 0.05f);
        binding.speedSlider.setValue(toDisplaySpeed(Setting.getDanmakuSpeed()));
        binding.areaSlider.setRange(10, 100, 5);
        binding.areaSlider.setValue(Setting.getDanmakuArea());
        updateValues();
    }

    @Override
    protected void initEvent() {
        if (binding == null || player == null) return;
        binding.choose.setOnClickListener(this::showChooser);
        binding.sizeSlider.setOnValueChangeListener(this::setSize);
        binding.opacitySlider.setOnValueChangeListener(this::setOpacity);
        binding.speedSlider.setOnValueChangeListener(this::setSpeed);
        binding.areaSlider.setOnValueChangeListener(value -> setArea(Math.round(value)));
    }

    private void setSize(float value) {
        Setting.putDanmakuSize(value);
        applySettings();
    }

    private void setOpacity(float value) {
        Setting.putDanmakuOpacity(value);
        applySettings();
    }

    private void setSpeed(float value) {
        Setting.putDanmakuSpeed(1.2f / value);
        applySettings();
    }

    private void setArea(int value) {
        Setting.putDanmakuArea(value);
        applySettings();
    }

    private void applySettings() {
        player.applyDanmakuSettings(displayScale);
        updateValues();
    }

    private void updateValues() {
        binding.sizeValue.setText(String.format(Locale.getDefault(), "%d%%", Math.round(binding.sizeSlider.getValue() * 100)));
        binding.opacityValue.setText(String.format(Locale.getDefault(), "%d%%", Math.round(binding.opacitySlider.getValue() * 100)));
        binding.speedValue.setText(String.format(Locale.getDefault(), "%.2fx", binding.speedSlider.getValue()));
        binding.areaValue.setText(String.format(Locale.getDefault(), "%d%%", Math.round(binding.areaSlider.getValue())));
    }

    private float toDisplaySpeed(float factor) {
        return 1.2f / Math.max(0.1f, factor);
    }

    private void showChooser(View view) {
        FileChooser.from(this).show(new String[]{"text/*"});
        player.pause();
    }

    @Override
    public void onItemClick(Danmaku item) {
        player.setDanmaku(item.isSelected() ? Danmaku.empty() : item);
        dismiss();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || requestCode != FileChooser.REQUEST_PICK_FILE || data == null || data.getData() == null) return;
        player.setDanmaku(Danmaku.from(FileChooser.getPathFromUri(data.getData())));
        dismiss();
    }
}
