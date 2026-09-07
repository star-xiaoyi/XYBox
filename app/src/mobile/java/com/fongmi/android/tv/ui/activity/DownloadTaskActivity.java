package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.airbnb.lottie.LottieAnimationView;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.ActivityDownloadTaskBinding;
import com.fongmi.android.tv.download.DownloadManager;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.adapter.DownloadAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.GlassConfirmDialog;
import com.fongmi.android.tv.utils.Notify;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 下载列表：只放没缓存完的任务，实时进度、可暂停/继续/重试/删除。
 * 单独一个页面而不是缓存页里的切换视图，返回键才能自然回到进来的那一级。
 */
public class DownloadTaskActivity extends BaseActivity implements DownloadAdapter.OnClickListener {

    private ActivityDownloadTaskBinding mBinding;
    private DownloadAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DownloadTaskActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDownloadTaskBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.toolbar.setTitle(R.string.download_tasks);
        mBinding.toolbar.setPrimaryAction(R.drawable.ic_control_setting, R.string.download_setting);
        mBinding.toolbar.setSecondaryActionIcon(R.drawable.ic_action_delete);
        mBinding.toolbar.attachContent(mBinding.content, mBinding.recycler);
        mBinding.recycler.setHasFixedSize(false);
        mBinding.recycler.getItemAnimator().setChangeDuration(0);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        mBinding.recycler.setAdapter(mAdapter = new DownloadAdapter(this));
        refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setBackClickListener(v -> onBackPress());
        mBinding.toolbar.setSecondaryActionClickListener(this::onDelete);
        mBinding.toolbar.setPrimaryActionClickListener(v -> mBinding.settingPanel.show(mBinding.content));
    }

    @Override
    protected boolean handleBack() {
        return true;
    }

    @Override
    protected void onBackPress() {
        if (mBinding.settingPanel.isPanelVisible()) mBinding.settingPanel.dismiss();
        else finish();
    }

    private void refresh() {
        List<Download> items = Download.getActive();
        mAdapter.setItems(items);
        boolean empty = items.isEmpty();
        mBinding.emptyLayout.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        mBinding.toolbar.setSecondaryActionVisible(!empty);
        if (!empty) return;
        mBinding.emptyLayout.text.setText(R.string.download_task_empty);
        LottieAnimationView lottie = mBinding.emptyLayout.getRoot().findViewById(R.id.lottieAnimation);
        if (lottie != null) lottie.playAnimation();
    }

    private void onDelete(View view) {
        GlassConfirmDialog.show(this, R.string.dialog_delete_download_task, () -> {
            for (Download item : new ArrayList<>(Download.getActive())) DownloadManager.get().remove(item);
            refresh();
            Notify.show(R.string.download_delete_task_done);
        });
    }

    @Override
    public void onItemToggle(Download item) {
        if (item.isRunning() || item.isPending()) DownloadManager.get().pause(item.getId());
        else DownloadManager.get().resume(item.getId());
    }

    @Override
    public void onItemDelete(Download item) {
        GlassConfirmDialog.show(this,
                getString(R.string.dialog_delete_download_episode, item.getVodName(), item.getEpisodeName()),
                () -> {
                    DownloadManager.get().remove(item);
                    refresh();
                    Notify.show(R.string.download_delete_episode_done);
                });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.DOWNLOAD) refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
