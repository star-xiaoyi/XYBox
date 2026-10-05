package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ForegroundSyncEvent;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.WebDAVSyncManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.airbnb.lottie.LottieAnimationView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import com.github.catvod.utils.Logger;

public class HistoryActivity extends BaseActivity implements HistoryAdapter.OnClickListener {

    private ActivityHistoryBinding mBinding;
    private HistoryAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HistoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.toolbar.setTitle(R.string.app_history);
        mBinding.toolbar.setPrimaryActionIcon(R.drawable.ic_action_sync);
        mBinding.toolbar.setSecondaryActionIcon(R.drawable.ic_action_delete);
        mBinding.toolbar.attachContent(mBinding.content, mBinding.recycler);
        setRecyclerView();
        getHistory();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setBackClickListener(v -> finish());
        mBinding.toolbar.setPrimaryActionClickListener(this::onSync);
        mBinding.toolbar.setSecondaryActionClickListener(this::onDelete);
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.getItemAnimator().setChangeDuration(0);
        int columns = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 2 : 1;
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, columns));
        mBinding.recycler.setAdapter(mAdapter = new HistoryAdapter(this));
    }

    private void getHistory() {
        if (mBinding == null || mAdapter == null) return;
        List<History> items = History.getAll();
        mAdapter.addAll(items); // 显示所有视频源的观看记录
        Logger.i("HistoryScreen: action=load count=" + items.size()
                + " awaiting=" + App.isAwaitingForegroundSync() + " syncing=" + WebDAVSyncManager.get().isSyncing()
                + " profile=" + com.fongmi.android.tv.utils.LocalProfile.id());
        if (mAdapter.getItemCount() == 0) setDeleteMode(false);
        else refreshBackHandling();
        mBinding.toolbar.setSecondaryActionVisible(mAdapter.getItemCount() > 0);
        updateEmptyState();
    }

    private boolean isHistorySyncing() {
        return App.isAwaitingForegroundSync() || WebDAVSyncManager.get().isSyncing();
    }

    private void updateEmptyState() {
        boolean isEmpty = mAdapter.getItemCount() == 0;
        boolean waiting = isEmpty && isHistorySyncing();
        Logger.i("HistoryScreen: state=" + (waiting ? "loading" : isEmpty ? "empty" : "ready")
                + " count=" + mAdapter.getItemCount());
        mBinding.historyLoading.setVisibility(waiting ? View.VISIBLE : View.GONE);
        mBinding.emptyLayout.getRoot().setVisibility(isEmpty && !waiting ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        
        // 控制Lottie动画播放
        if (isEmpty && !waiting) {
            try {
                LottieAnimationView lottieView = mBinding.emptyLayout.getRoot().findViewById(R.id.lottieAnimation);
                if (lottieView != null) {
                    lottieView.playAnimation();
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }
    }

    private void onSync(View view) {
        WebDAVSyncManager manager = WebDAVSyncManager.get();
        if (!manager.isConfigured()) {
            Notify.tip("请先在设置中配置 WebDAV");
            return;
        }
        mBinding.toolbar.setPrimaryActionEnabled(false);
        App.execute(() -> {
            WebDAVSyncManager.SyncResult result = manager.syncNow();
            App.post(() -> {
                if (mBinding == null || isFinishing() || isDestroyed()) return;
                mBinding.toolbar.setPrimaryActionEnabled(true);
                getHistory();
                Notify.tip(result.message);
            });
        });
    }

    private void onDelete(View view) {
        if (mAdapter.isDelete()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_history).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                mAdapter.clear();
                setDeleteMode(false);
                updateEmptyState();
            }).show();
        } else if (mAdapter.getItemCount() > 0) {
            setDeleteMode(true);
        } else {
            mBinding.toolbar.setSecondaryActionVisible(false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType().equals(RefreshEvent.Type.HISTORY)) getHistory();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onForegroundSyncEvent(ForegroundSyncEvent event) {
        getHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getHistory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBinding = null;
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.resume(this, item);
    }

    @Override
    public void onItemDelete(History item) {
        mAdapter.remove(item.delete());
        if (mAdapter.getItemCount() > 0) return;
        mBinding.toolbar.setSecondaryActionVisible(false);
        setDeleteMode(false);
        updateEmptyState();
    }

    @Override
    public boolean onLongClick() {
        setDeleteMode(!mAdapter.isDelete());
        return true;
    }

    private void setDeleteMode(boolean delete) {
        mAdapter.setDelete(delete);
        refreshBackHandling();
    }

    @Override
    protected boolean shouldInterceptBack() {
        return mAdapter != null && mAdapter.isDelete();
    }

    @Override
    protected void onBackPress() {
        if (mAdapter.isDelete()) setDeleteMode(false);
        else super.onBackPress();
    }
}
