package com.fongmi.android.tv.search;

import java.util.LinkedHashSet;
import java.util.Set;

/** Stable identity and learned provider aliases. Provider metadata is only a fallback. */
public final class FilmIdentity {
    private FilmIdentity() { }

    public static boolean compatibleMetadata(int leftYear, int rightYear, int leftKind, int rightKind,
                                              boolean episodic, long leftDuration, long rightDuration) {
        return (leftYear == 0 || rightYear == 0 || Math.abs(leftYear - rightYear) <= 1)
                && (leftKind == 0 || rightKind == 0 || leftKind == rightKind)
                && (episodic || leftDuration <= 0 || rightDuration <= 0 || Math.abs(leftDuration - rightDuration) <= 600000);
    }

    public static boolean same(String leftId, String leftAliases, String rightId, String rightAliases,
                               boolean sameTitleAndEdition, boolean compatibleMetadata) {
        if (!sameTitleAndEdition) return false;
        if (!leftId.isEmpty() && leftId.equals(rightId)) return true;
        Set<String> left = aliases(leftAliases), right = aliases(rightAliases);
        if (!leftId.isEmpty()) left.add("film:" + leftId);
        if (!rightId.isEmpty()) right.add("film:" + rightId);
        for (String alias : left) if (right.contains(alias)) return true;
        return compatibleMetadata;
    }

    public static Set<String> aliases(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text != null) for (String value : text.split("\n")) if (!value.isEmpty()) result.add(value);
        return result;
    }

    public static String join(Set<String> values) {
        // Keep film aliases first when a very large collection needs trimming.
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) if (value.startsWith("film:")) result.add(value);
        for (String value : values) if (result.size() < 512) result.add(value);
        return String.join("\n", result);
    }
}
