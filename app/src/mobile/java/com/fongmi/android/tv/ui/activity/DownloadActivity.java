package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.airbnb.lottie.LottieAnimationView;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.ActivityDownloadBinding;
import com.fongmi.android.tv.download.DownloadManager;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.adapter.DownloadVodAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.DownloadDeleteDialog;
import com.fongmi.android.tv.ui.dialog.GlassConfirmDialog;
import com.fongmi.android.tv.utils.Notify;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

/** 离线缓存：按剧聚合的网格，右上角进「下载列表」看进行中的任务。 */
public class DownloadActivity extends BaseActivity implements DownloadVodAdapter.OnClickListener {

    private ActivityDownloadBinding mBinding;
    private DownloadVodAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, DownloadActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityDownloadBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.toolbar.setTitle(R.string.app_download);
        mBinding.toolbar.setPrimaryAction(R.drawable.ic_action_download_list, R.string.download_tasks);
        mBinding.toolbar.setSecondaryActionIcon(R.drawable.ic_action_delete);
        mBinding.toolbar.attachContent(mBinding.content, mBinding.recycler);
        mBinding.recycler.setHasFixedSize(false);
        mBinding.recycler.getItemAnimator().setChangeDuration(0);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn(this)));
        mBinding.recycler.setAdapter(mAdapter = new DownloadVodAdapter(this));
        mAdapter.setSize(Product.getSpec(this));
        refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setBackClickListener(v -> finish());
        mBinding.toolbar.setPrimaryActionClickListener(v -> DownloadTaskActivity.start(this));
        mBinding.toolbar.setSecondaryActionClickListener(this::onDelete);
    }

    private void refresh() {
        List<Download.Group> groups = Download.group(Download.getAll());
        mAdapter.setItems(groups);
        boolean empty = groups.isEmpty();
        mBinding.emptyLayout.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        mBinding.toolbar.setSecondaryActionVisible(!empty);
        if (empty) mAdapter.setDelete(false);
        if (!empty) return;
        mBinding.emptyLayout.text.setText(R.string.download_empty);
        LottieAnimationView lottie = mBinding.emptyLayout.getRoot().findViewById(R.id.lottieAnimation);
        if (lottie != null) lottie.playAnimation();
    }

    private void onDelete(View view) {
        if (mAdapter.isDelete()) {
            GlassConfirmDialog.show(this, R.string.dialog_delete_download, () -> {
                DownloadManager.get().removeAll();
                mAdapter.setDelete(false);
                refresh();
                Notify.show(R.string.download_delete_all_done);
            });
        } else if (!mAdapter.isEmpty()) {
            mAdapter.setDelete(true);
        }
    }

    @Override
    public void onItemClick(Download.Group item) {
        // 还没缓存完的剧点进去也没得播，直接送去看进度
        if (item.getPlayable().isEmpty()) DownloadTaskActivity.start(this);
        else VideoActivity.download(this, item);
    }

    @Override
    public void onItemDelete(Download.Group item) {
        DownloadDeleteDialog.create().group(item).callback(this::refresh).show(this);
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(!mAdapter.isDelete());
        return true;
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

    @Override
    public void onBackPressed() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackPressed();
    }
}
