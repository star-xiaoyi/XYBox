package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogDownloadEpisodeBinding;
import com.fongmi.android.tv.ui.activity.DownloadActivity;
import com.fongmi.android.tv.ui.fragment.DownloadEpisodeFragment;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class DownloadEpisodeDialog extends BaseDialog implements DownloadEpisodeFragment.DownloadEpisodeCallback {

    private DialogDownloadEpisodeBinding binding;
    private List<Episode> episodes;
    private final List<String> titles;
    private DownloadCallback callback;
    private boolean reverse;
    private int spanCount;
    private int itemCount;

    public static DownloadEpisodeDialog create() {
        return new DownloadEpisodeDialog();
    }

    public DownloadEpisodeDialog() {
        this.titles = new ArrayList<>();
        this.spanCount = 5;
    }

    public DownloadEpisodeDialog reverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    public DownloadEpisodeDialog episodes(List<Episode> episodes) {
        this.episodes = episodes;
        return this;
    }

    public DownloadEpisodeDialog callback(DownloadCallback callback) {
        this.callback = callback;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDownloadEpisodeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        // 检查episodes是否为空
        if (episodes == null || episodes.isEmpty()) {
            dismiss();
            return;
        }
        setSpanCount();
        setTitles();
        setPager();
        initEvent();
    }

    protected void initEvent() {
        binding.downloadList.setOnClickListener(v -> onDownloadList());
    }

    private void onDownloadList() {
        dismiss();
        DownloadActivity.start(getActivity());
    }

    private void setSpanCount() {
        int total = 0;
        int row = ResUtil.isLand(getActivity()) ? 5 : 10;
        for (Episode item : episodes) total += item.getName().length();
        // 防止除以零
        if (episodes.size() == 0) {
            spanCount = 5;
            itemCount = spanCount * row;
            return;
        }
        int offset = (int) Math.ceil((double) total / episodes.size());
        if (offset >= 12) spanCount = 1;
        else if (offset >= 8) spanCount = 2;
        else if (offset >= 4) spanCount = 3;
        else if (offset >= 2) spanCount = 4;
        itemCount = spanCount * row;
    }

    private void setTitles() {
        if (reverse) for (int i = episodes.size(); i > 0; i -= itemCount) titles.add(i + " - " + Math.max(i - itemCount - 1, 1));
        else for (int i = 0; i < episodes.size(); i += itemCount) titles.add((i + 1) + " - " + Math.min(i + itemCount, episodes.size()));
    }

    private void setPager() {
        binding.pager.setAdapter(new PageAdapter(getActivity()));
        new TabLayoutMediator(binding.tabs, binding.pager, (tab, position) -> tab.setText(titles.get(position))).attach();
        setCurrentPage();
    }

    private void setCurrentPage() {
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).isActivated()) {
                binding.pager.setCurrentItem(i / itemCount);
                break;
            }
        }
    }

    @Override
    public void onEpisodeSelected(Episode episode) {
        // 用户点击集数时，通知回调开始下载
        if (callback != null) {
            callback.onDownloadEpisode(episode);
        }
        dismiss();
    }

    class PageAdapter extends FragmentStateAdapter {

        public PageAdapter(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            DownloadEpisodeFragment fragment = DownloadEpisodeFragment.newInstance(spanCount, episodes.subList(position * itemCount, Math.min(position * itemCount + itemCount, episodes.size())));
            fragment.setCallback(DownloadEpisodeDialog.this);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return titles.size();
        }
    }

    public interface DownloadCallback {
        void onDownloadEpisode(Episode episode);
    }
}
