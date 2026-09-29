package com.fongmi.android.tv.search;

import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聚合搜索的合并器：各站点的结果陆续进来，按规整后的片名 + 年份 + 片种归到同一组。
 * <p>
 * 同名但年份差得远（1986 版和 2010 版西游记）或片种明确不同（动画和真人剧）的分成两组。
 */
public class VodGrouper {

    private final Map<String, List<VodGroup>> index;
    private final List<VodGroup> groups;
    private final Set<String> seen;
    private final String keyword;

    public VodGrouper(String keyword) {
        this.keyword = TitleKey.normalize(keyword);
        this.index = new HashMap<>();
        this.groups = new ArrayList<>();
        this.seen = new HashSet<>();
    }

    /** 并入一个站点的结果。新出现的组放进 added，只是多了个源的旧组放进 updated。 */
    public void add(List<Vod> items, long cost, List<VodGroup> added, Collection<VodGroup> updated) {
        for (Vod vod : items) {
            if (vod.getVodName().isEmpty()) continue;
            // 同一站点重复返回的同一条（翻页、接口本身重复）只算一次
            if (!seen.add(vod.getSiteKey() + "\n" + vod.getVodId())) continue;
            VodSource source = new VodSource(vod, cost);
            String key = TitleKey.normalize(vod.getVodName());
            int tier = TitleKey.tier(key, keyword);
            if (vod.isFolder() || vod.isAction()) {
                VodGroup group = new VodGroup(key, tier, true);
                group.add(source, 0, TitleKey.KIND_UNKNOWN);
                groups.add(group);
                added.add(group);
                continue;
            }
            int year = TitleKey.year(vod.getVodYear());
            int kind = TitleKey.kind(vod.getTypeName());
            VodGroup group = find(key, year, kind);
            if (group == null) {
                group = new VodGroup(key, tier, false);
                List<VodGroup> list = index.get(key);
                if (list == null) index.put(key, list = new ArrayList<>());
                list.add(group);
                groups.add(group);
                added.add(group);
            } else if (!added.contains(group) && !updated.contains(group)) {
                updated.add(group);
            }
            group.add(source, year, kind);
        }
    }

    private VodGroup find(String key, int year, int kind) {
        List<VodGroup> list = index.get(key);
        if (list == null) return null;
        for (VodGroup group : list) if (group.accept(year, kind)) return group;
        return null;
    }

    /** 首次展示时的顺序：同名 → 开头匹配 → 包含 → 其他；同档里源多的在前，其余保持到达顺序。 */
    public List<VodGroup> sorted() {
        List<VodGroup> items = new ArrayList<>(groups);
        Collections.sort(items, (a, b) -> {
            int tier = Integer.compare(a.getTier(), b.getTier());
            return tier != 0 ? tier : Integer.compare(b.size(), a.size());
        });
        return items;
    }

    public boolean hasExact() {
        for (VodGroup group : groups) if (group.getTier() == 0 && !group.isFolder()) return true;
        return false;
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    public int size() {
        return groups.size();
    }
}
