package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.FragmentEpisodeBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.base.ViewType;

import java.util.ArrayList;
import java.util.List;

public class DownloadEpisodeFragment extends BaseFragment implements EpisodeAdapter.OnClickListener {

    private FragmentEpisodeBinding mBinding;
    private DownloadEpisodeCallback callback;

    private int getSpanCount() {
        return getArguments().getInt("spanCount");
    }

    private ArrayList<Episode> getItems() {
        return getArguments().getParcelableArrayList("items");
    }

    public static DownloadEpisodeFragment newInstance(int spanCount, List<Episode> items) {
        Bundle args = new Bundle();
        args.putInt("spanCount", spanCount);
        args.putParcelableArrayList("items", new ArrayList<>(items));
        DownloadEpisodeFragment fragment = new DownloadEpisodeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public void setCallback(DownloadEpisodeCallback callback) {
        this.callback = callback;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentEpisodeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setRecyclerView();
    }

    private void setRecyclerView() {
        EpisodeAdapter adapter;
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(getContext(), getSpanCount()));
        mBinding.recycler.setAdapter(adapter = new EpisodeAdapter(this, ViewType.GRID, getItems()));
        mBinding.recycler.scrollToPosition(adapter.getPosition());
    }

    @Override
    public void onItemClick(Episode item) {
        // 用户点击集数时，触发下载回调
        if (callback != null) {
            callback.onEpisodeSelected(item);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 防止内存泄漏，清除callback引用
        callback = null;
    }

    public interface DownloadEpisodeCallback {
        void onEpisodeSelected(Episode episode);
    }
}
