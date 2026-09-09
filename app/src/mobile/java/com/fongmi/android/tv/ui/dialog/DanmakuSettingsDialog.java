package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
        return 420;
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
        refreshSelection();
    }

    @Override
    protected void initEvent() {
        if (binding == null || player == null) return;
        binding.choose.setOnClickListener(this::showChooser);
        binding.sizeSmall.setOnClickListener(v -> setSize(0.8f));
        binding.sizeNormal.setOnClickListener(v -> setSize(1.0f));
        binding.sizeLarge.setOnClickListener(v -> setSize(1.2f));
        binding.opacityLow.setOnClickListener(v -> setOpacity(0.5f));
        binding.opacityNormal.setOnClickListener(v -> setOpacity(0.8f));
        binding.opacityHigh.setOnClickListener(v -> setOpacity(1.0f));
        binding.speedSlow.setOnClickListener(v -> setSpeed(1.5f));
        binding.speedNormal.setOnClickListener(v -> setSpeed(1.2f));
        binding.speedFast.setOnClickListener(v -> setSpeed(0.8f));
        binding.areaQuarter.setOnClickListener(v -> setArea(25));
        binding.areaHalf.setOnClickListener(v -> setArea(50));
        binding.areaFull.setOnClickListener(v -> setArea(100));
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
        Setting.putDanmakuSpeed(value);
        applySettings();
    }

    private void setArea(int value) {
        Setting.putDanmakuArea(value);
        applySettings();
    }

    private void applySettings() {
        player.applyDanmakuSettings(displayScale);
        refreshSelection();
    }

    private void refreshSelection() {
        activate(new TextView[]{binding.sizeSmall, binding.sizeNormal, binding.sizeLarge}, closest(Setting.getDanmakuSize(), 0.8f, 1.0f, 1.2f));
        activate(new TextView[]{binding.opacityLow, binding.opacityNormal, binding.opacityHigh}, closest(Setting.getDanmakuOpacity(), 0.5f, 0.8f, 1.0f));
        activate(new TextView[]{binding.speedSlow, binding.speedNormal, binding.speedFast}, closest(Setting.getDanmakuSpeed(), 1.5f, 1.2f, 0.8f));
        int area = Setting.getDanmakuArea();
        activate(new TextView[]{binding.areaQuarter, binding.areaHalf, binding.areaFull}, area <= 25 ? 0 : area <= 50 ? 1 : 2);
    }

    private int closest(float value, float... options) {
        int selected = 0;
        for (int i = 1; i < options.length; i++) {
            if (Math.abs(options[i] - value) < Math.abs(options[selected] - value)) selected = i;
        }
        return selected;
    }

    private void activate(TextView[] views, int selected) {
        for (int i = 0; i < views.length; i++) views[i].setActivated(i == selected);
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
