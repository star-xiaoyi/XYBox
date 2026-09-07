package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.databinding.DialogDownloadDeleteBinding;
import com.fongmi.android.tv.download.DownloadManager;
import com.fongmi.android.tv.utils.Notify;

import java.util.LinkedHashSet;
import java.util.Set;

/** 选择并删除某部剧中的指定缓存；不再把整部剧作为唯一删除粒度。 */
public class DownloadDeleteDialog extends BaseCenterDialog {

    private DialogDownloadDeleteBinding binding;
    private Download.Group group;
    private Runnable callback;

    public static DownloadDeleteDialog create() {
        return new DownloadDeleteDialog();
    }

    public DownloadDeleteDialog group(Download.Group group) {
        this.group = group;
        return this;
    }

    public DownloadDeleteDialog callback(Runnable callback) {
        this.callback = callback;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof DownloadDeleteDialog && fragment.isAdded()) return;
        }
        show(activity.getSupportFragmentManager(), "DownloadDeleteDialog");
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDownloadDeleteBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (group == null || group.getItems().isEmpty()) {
            dismissAllowingStateLoss();
            return;
        }
        binding.getRoot().setGroup(group);
    }

    @Override
    protected void initEvent() {
        if (binding == null) return;
        binding.getRoot().setCancelClickListener(v -> dismiss());
        binding.getRoot().setDeleteListener(this::onConfirm);
    }

    private void onConfirm(Set<String> selected) {
        int count = selected.size();
        DownloadManager.get().removeEpisodes(group.getKey(), new LinkedHashSet<>(selected));
        if (callback != null) callback.run();
        dismiss();
        Notify.show(getString(R.string.download_delete_done, count));
    }
}
