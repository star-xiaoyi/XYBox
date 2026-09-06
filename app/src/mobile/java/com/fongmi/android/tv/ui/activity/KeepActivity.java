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
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn(this)));
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        mAdapter.setSize(Product.getSpec(getActivity()));
    }

    private void getKeep() {
        mAdapter.addAll(Keep.getVod());
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
            Notify.tip("请先在设置中配置 WebDAV");
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
                updateEmptyState();
            }).show();
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        } else {
            mBinding.toolbar.setSecondaryActionVisible(false);
        }
    }

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
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
        Config config = Config.find(item.getCid());
        if (config == null) CollectActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        mAdapter.remove(item.delete());
        if (mAdapter.getItemCount() > 0) return;
        mBinding.toolbar.setSecondaryActionVisible(false);
        mAdapter.setDelete(false);
        updateEmptyState();
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(!mAdapter.isDelete());
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else super.onBackPressed();
    }
}
