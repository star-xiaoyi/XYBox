package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.ActivityKeepBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.WebDAVSyncManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.airbnb.lottie.LottieAnimationView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.toolbar.setTitle(R.string.app_keep);
        mBinding.toolbar.setPrimaryActionIcon(R.drawable.ic_action_sync);
        mBinding.toolbar.setSecondaryActionIcon(R.drawable.ic_action_delete);
        mBinding.toolbar.attachContent(mBinding.content, mBinding.recycler);
        setRecyclerView();
        getKeep();
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
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, 3));
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        mBinding.recycler.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> resizeCards(r-l));
        mBinding.recycler.post(() -> resizeCards(mBinding.recycler.getWidth()));
    }

    private void resizeCards(int width) {
        if (width <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        int available = width - mBinding.recycler.getPaddingLeft() - mBinding.recycler.getPaddingRight();
        int columns = Math.max(2, Math.min(8, (int) (available / density / 100)));
        GridLayoutManager grid = (GridLayoutManager) mBinding.recycler.getLayoutManager();
        if (grid.getSpanCount() != columns) grid.setSpanCount(columns);
        int card = Math.max(1, available / columns - Math.round(16 * density));
        mAdapter.setSize(new int[]{card, Math.round(card * 1.48f)});
    }

    private void getKeep() {
        mAdapter.addAll(Keep.getVod());
        if (mAdapter.getItemCount() == 0) setDeleteMode(false);
        else refreshBackHandling();
        mBinding.toolbar.setSecondaryActionVisible(mAdapter.getItemCount() > 0);
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = mAdapter.getItemCount() == 0;
        mBinding.emptyLayout.getRoot().setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        
        // 控制Lottie动画播放
        if (isEmpty) {
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
            Notify.tip("请先在“我的”中配置 WebDAV");
            return;
        }
        mBinding.toolbar.setPrimaryActionEnabled(false);
        App.execute(() -> {
            WebDAVSyncManager.SyncResult result = manager.syncNow();
            App.post(() -> {
                mBinding.toolbar.setPrimaryActionEnabled(true);
                getKeep();
                Notify.tip(result.message);
            });
        });
    }

    private void onDelete(View view) {
        if (mAdapter.isDelete()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_keep).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
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

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                play(item);
                RefreshEvent.config();
                RefreshEvent.video();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType().equals(RefreshEvent.Type.KEEP)) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        play(item);
    }

    private void play(Keep item) {
        com.fongmi.android.tv.bean.Site site = VodConfig.get().getSite(item.getCid(), item.getSiteKey());
        if (site.isEmpty()) VideoActivity.find(this, item.getVodName(), item.getVodPic(), null);
        else VideoActivity.start(this, site.getKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
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
