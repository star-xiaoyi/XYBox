package com.fongmi.android.tv.ui.dialog;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

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
import java.util.Collections;
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

    private static final int PAGE_SIZE = 30;
    private static final int ITEM_HEIGHT_DP = 48;

    private DialogDownloadEpisodeBinding binding;
    private DownloadEpisodeAdapter adapter;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<int[]> ranges = new ArrayList<>();
    private Map<String, Download> states = new HashMap<>();
    private List<Episode> episodes = new ArrayList<>();
    private Callback callback;
    private String groupKey = "";
    private int spanCount = 3;
    private int currentIndex;

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

    public DownloadEpisodeDialog currentIndex(int currentIndex) {
        this.currentIndex = Math.max(0, currentIndex);
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

    /**
     * 典型手机以三列为基准；宽屏会增加列数，集名较长时则减少列数。
     * 每页始终是 30 集，列数只决定剧集区域内部需要多少行、是否需要纵向滚动。
     */
    private void setSpanCount() {
        float density = getResources().getDisplayMetrics().density;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(ResUtil.sp2px(14));
        List<Float> widths = new ArrayList<>();
        for (Episode item : episodes) widths.add(paint.measureText(item.getName()));
        Collections.sort(widths);
        float representative = widths.get((widths.size() - 1) * 3 / 4) / density;
        int desiredCell = Math.max(104, Math.min(240, Math.round(representative + 36)));
        int available = Math.max(104, ResUtil.getWindowWidthDp(requireContext()) - 20);
        spanCount = Math.max(1, Math.min(6, available / desiredCell));
    }

    private void setRanges() {
        ranges.clear();
        for (int i = 0; i < episodes.size(); i += PAGE_SIZE) ranges.add(new int[]{i, Math.min(i + PAGE_SIZE, episodes.size())});
    }

    private void setRecycler() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
        binding.recycler.setAdapter(adapter = new DownloadEpisodeAdapter(this));
        binding.recycler.setNestedScrollingEnabled(true);
        int screenHeight = Math.round(ResUtil.getScreenHeight(requireContext()) / getResources().getDisplayMetrics().density);
        int desiredRows = (int) Math.ceil((double) PAGE_SIZE / Math.max(spanCount, 3));
        int visibleRows = Math.max(3, Math.min(desiredRows, Math.max(3, (screenHeight - 190) / ITEM_HEIGHT_DP)));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) binding.recycler.getLayoutParams();
        // recycler 自己还有上下各 10dp padding；一起计入，三列时正好完整露出十行。
        params.height = ResUtil.dp2px(visibleRows * ITEM_HEIGHT_DP + 20);
        binding.recycler.setLayoutParams(params);
        showPage(initialPage());
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
        binding.tabs.selectTab(binding.tabs.getTabAt(initialPage()));
    }

    private void showPage(int page) {
        if (page < 0 || page >= ranges.size()) return;
        int[] range = ranges.get(page);
        Episode current = episodes.get(Math.min(currentIndex, episodes.size() - 1));
        adapter.setItems(episodes.subList(range[0], range[1]), selected, states, current);
    }

    private int initialPage() {
        return Math.min(currentIndex, episodes.size() - 1) / PAGE_SIZE;
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
