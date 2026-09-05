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
import com.fongmi.android.tv.ui.dialog.DownloadSettingDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
        mBinding.back.setBackdropView(mBinding.toolbar);
        mBinding.setting.setBackdropView(mBinding.toolbar);
        mBinding.delete.setBackdropView(mBinding.toolbar);
        mBinding.back.setRenderingEnabled(true);
        mBinding.setting.setRenderingEnabled(true);
        mBinding.delete.setRenderingEnabled(true);
        mBinding.recycler.setHasFixedSize(false);
        mBinding.recycler.getItemAnimator().setChangeDuration(0);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        mBinding.recycler.setAdapter(mAdapter = new DownloadAdapter(this));
        refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.back.setOnClickListener(v -> finish());
        mBinding.delete.setOnClickListener(this::onDelete);
        mBinding.setting.setOnClickListener(v -> DownloadSettingDialog.create(this).show());
    }

    private void refresh() {
        List<Download> items = Download.getActive();
        mAdapter.setItems(items);
        boolean empty = items.isEmpty();
        mBinding.emptyLayout.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        mBinding.delete.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) return;
        mBinding.emptyLayout.text.setText(R.string.download_task_empty);
        LottieAnimationView lottie = mBinding.emptyLayout.getRoot().findViewById(R.id.lottieAnimation);
        if (lottie != null) lottie.playAnimation();
    }

    private void onDelete(View view) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_record)
                .setMessage(R.string.dialog_delete_download_task)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    for (Download item : new ArrayList<>(Download.getActive())) DownloadManager.get().remove(item);
                    refresh();
                })
                .show();
    }

    @Override
    public void onItemToggle(Download item) {
        if (item.isRunning() || item.isPending()) DownloadManager.get().pause(item.getId());
        else DownloadManager.get().resume(item.getId());
    }

    @Override
    public void onItemDelete(Download item) {
        DownloadManager.get().remove(item);
        refresh();
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
