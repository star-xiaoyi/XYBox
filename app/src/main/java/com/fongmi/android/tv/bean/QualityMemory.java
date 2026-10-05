package com.fongmi.android.tv.bean;

import java.util.*;

/** Small durable metadata only: no video bytes and no expiring resolved playback URLs. */
public final class QualityMemory {
    public int version = 1;
    public String key = "", title = "", year = "", type = "";
    public List<Candidate> candidates = new ArrayList<>();
    public List<Verified> verified = new ArrayList<>();
    public Map<String, Long> checked = new LinkedHashMap<>();
    public static final class Candidate {
        public String site = "", revision = "", id = "", name = "", year = "", type = "", pic = "", remarks = "";
        public long cost;
    }
    public static final class Verified {
        public String site = "", revision = "", id = "", episodeKey = "", flag = "", episodeName = "", episodeUrl = "", valueName = "";
        public int valueIndex, width, height, bitrate;
        public long speed, at, latencyMs = -1, measuredAt;
    }
}
