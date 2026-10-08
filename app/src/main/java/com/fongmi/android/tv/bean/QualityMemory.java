package com.fongmi.android.tv.bean;

import java.util.*;
import com.fongmi.android.tv.search.CandidateRefreshPolicy;

/** Small durable metadata only: no video bytes and no expiring resolved playback URLs. */
public final class QualityMemory {
    public int version = 3;
    public String key = "", title = "", year = "", type = "";
    public List<Candidate> candidates = new ArrayList<>();
    public List<Verified> verified = new ArrayList<>();
    public Map<String, Long> checked = new LinkedHashMap<>();
    public int minimumEpisodes;
    public Map<String, Long> cooldowns = new LinkedHashMap<>();
    public void upgrade(long now) {
        if (cooldowns == null) cooldowns = new LinkedHashMap<>();
        if (version == 1) {
            checked.replaceAll((key, until) -> CandidateRefreshPolicy.migrate(until, now));
            checked.entrySet().removeIf(entry -> !CandidateRefreshPolicy.checked(entry.getValue(), now));
            version = 2;
        }
        if (version == 2) {
            // Older builds could persist a raw non-numeric list length or a provider's
            // placeholder catalog as a permanent hard minimum.
            minimumEpisodes = 0;
            version = 3;
        }
    }
    public static final class Candidate {
        public String site = "", revision = "", id = "", name = "", year = "", type = "", pic = "", remarks = "";
        public long cost;
    }
    public static final class Verified {
        public String site = "", revision = "", id = "", episodeKey = "", flag = "", episodeName = "", episodeUrl = "", valueName = "";
        public int valueIndex, width, height, bitrate, episodeCount;
        public long speed, at, latencyMs = -1, measuredAt;
    }
}
