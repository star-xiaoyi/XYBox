package com.fongmi.android.tv.bean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Part {

    public static List<String> get(String source) {
        List<String> items = new ArrayList<>();
        items.add(source.trim());
        if (source.contains("：")) {
            for (String split : source.split("：")) items.add(split.trim().contains(" ") ? split.split(" ")[0].trim() : split.trim());
        } else if (source.contains("第") && source.contains("季")) {
            for (String split : source.split("第")) if (!split.contains("季")) items.add(split.trim().contains(" ") ? split.split(" ")[0].trim() : split.trim());
        } else if (source.contains("(")) {
            items.add(source.split("\\(")[0].trim());
        } else if (source.contains(" ")) {
            items.addAll(Arrays.asList(source.split(" ")));
        }
        return items;
    }
}
