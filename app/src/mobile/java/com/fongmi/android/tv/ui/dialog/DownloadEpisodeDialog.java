package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogDownloadEpisodeBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.activity.DownloadTaskActivity;
import com.fongmi.android.tv.ui.adapter.DownloadEpisodeAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 选集缓存面板：可多选，格子上直接显示每一集的缓存状态。
 * 分段用 TabLayout 而不是 ViewPager，勾选状态就能留在面板自己手里，不必跨 Fragment 同步。
 */
public class DownloadEpisodeDialog extends BaseDialog implements DownloadEpisodeAdapter.OnClickListener {

    private DialogDownloadEpisodeBinding binding;
    private DownloadEpisodeAdapter adapter;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<int[]> ranges = new ArrayList<>();
    private Map<String, Download> states = new HashMap<>();
    private List<Episode> episodes = new ArrayList<>();
    private Callback callback;
    private String groupKey = "";
    private int spanCount = 5;
    private int pageSize;

    public interface Callback {
        void onDownloadEpisodes(List<Episode> items);
    }

    public static DownloadEpisodeDialog create() {
        return new DownloadEpisodeDialog();
    }

    public DownloadEpisodeDialog episodes(List<Episode> episodes) {
        this.episodes = episodes == null ? new ArrayList<>() : episodes;
        return this;
    }

    public DownloadEpisodeDialog groupKey(String groupKey) {
        this.groupKey = groupKey;
        return this;
    }

    public DownloadEpisodeDialog callback(Callback callback) {
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
        if (episodes.isEmpty()) {
            dismiss();
            return;
        }
        EventBus.getDefault().register(this);
        loadStates();
        setSpanCount();
        setRanges();
        setRecycler();
        setTabs();
        updateConfirm();
    }

    @Override
    protected void initEvent() {
        if (binding == null) return;
        binding.selectAll.setOnClickListener(v -> onSelectAll());
        // 不 dismiss：面板留在下面，从下载列表返回时还能接着勾选
        binding.taskList.setOnClickListener(v -> DownloadTaskActivity.start(getActivity()));
        binding.confirm.setOnClickListener(v -> onConfirm());
    }

    /**
     * 缓存状态按集号索引：这部剧在别的源里缓存过第 1 集，这个源的第 1 集也算已缓存，
     * 集名写法不同（第01集 / 1）也能对上。
     */
    private void loadStates() {
        states = new HashMap<>();
        for (Download item : Download.getByGroup(groupKey)) {
            String key = Download.episodeKey(item.getEpisodeName());
            Download exist = states.get(key);
            // 同一集有多条记录时，已缓存的那条优先
            if (exist == null || (!exist.isDone() && item.isDone())) states.put(key, item);
        }
    }

    private Download stateOf(Episode item) {
        return states.get(Download.episodeKey(item.getName()));
    }

    /** 沿用剧集面板的算法：名字越长每行放得越少。 */
    private void setSpanCount() {
        int total = 0;
        for (Episode item : episodes) total += item.getName().length();
        int offset = (int) Math.ceil((double) total / episodes.size());
        if (offset >= 12) spanCount = 1;
        else if (offset >= 8) spanCount = 2;
        else if (offset >= 4) spanCount = 3;
        else if (offset >= 2) spanCount = 4;
        else spanCount = 5;
        pageSize = spanCount * (ResUtil.isLand(getActivity()) ? 4 : 8);
    }

    private void setRanges() {
        ranges.clear();
        for (int i = 0; i < episodes.size(); i += pageSize) ranges.add(new int[]{i, Math.min(i + pageSize, episodes.size())});
    }

    private void setRecycler() {
        binding.recycler.setHasFixedSize(false);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
        binding.recycler.setAdapter(adapter = new DownloadEpisodeAdapter(this));
        showPage(0);
    }

    private void setTabs() {
        binding.tabs.setVisibility(ranges.size() < 2 ? View.GONE : View.VISIBLE);
        if (ranges.size() < 2) return;
        binding.tabs.removeAllTabs();
        for (int[] range : ranges) binding.tabs.addTab(binding.tabs.newTab().setText(String.format(Locale.getDefault(), "%d - %d", range[0] + 1, range[1])));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showPage(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void showPage(int page) {
        if (page < 0 || page >= ranges.size()) return;
        int[] range = ranges.get(page);
        adapter.setItems(episodes.subList(range[0], range[1]), selected, states);
    }

    private int currentPage() {
        return binding.tabs.getVisibility() == View.VISIBLE ? Math.max(0, binding.tabs.getSelectedTabPosition()) : 0;
    }

    @Override
    public void onEpisodeClick(Episode item) {
        Download state = stateOf(item);
        // 已经缓存好的就别再排一遍队了，想重来去缓存页删掉
        if (state != null && state.isDone()) {
            Notify.show(R.string.download_state_done);
            return;
        }
        if (!selected.remove(item.getName())) selected.add(item.getName());
        showPage(currentPage());
        updateConfirm();
    }

    private void onSelectAll() {
        boolean fill = selected.size() < selectableCount();
        selected.clear();
        if (fill) for (Episode item : episodes) {
            Download state = stateOf(item);
            if (state == null || !state.isDone()) selected.add(item.getName());
        }
        showPage(currentPage());
        updateConfirm();
    }

    private int selectableCount() {
        int count = 0;
        for (Episode item : episodes) {
            Download state = stateOf(item);
            if (state == null || !state.isDone()) ++count;
        }
        return count;
    }

    private void updateConfirm() {
        binding.confirm.setText(getString(R.string.download_start, String.valueOf(selected.size())));
        binding.confirm.setAlpha(selected.isEmpty() ? 0.45f : 1f);
    }

    private void onConfirm() {
        if (selected.isEmpty()) {
            Notify.show(R.string.download_none);
            return;
        }
        List<Episode> items = new ArrayList<>();
        for (Episode item : episodes) if (selected.contains(item.getName())) items.add(item);
        if (callback != null) callback.onDownloadEpisodes(items);
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (!RefreshEvent.Type.DOWNLOAD.equals(event.getType()) || binding == null) return;
        loadStates();
        showPage(currentPage());
    }

    @Override
    public void onDestroyView() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroyView();
    }
}
