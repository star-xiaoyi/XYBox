package com.github.catvod;

import com.github.catvod.utils.Util;

public class Proxy {

    public static final String OWNER = "xyProxyOwner";
    public static final String MAIN = "main", SOURCES = "sources";
    private static volatile int port = -1;
    private static volatile boolean sourceProcess;

    public static void setSourceProcess(boolean value) { sourceProcess = value; }

    public static String getPath(boolean source) { return "/proxy/" + (source ? SOURCES : MAIN); }

    public static String owner(String path) {
        if (getPath(false).equals(path)) return MAIN;
        if (getPath(true).equals(path)) return SOURCES;
        return "";
    }

    public static void set(int port) {
        Proxy.port = port;
    }

    public static int getPort() {
        return port;
    }

    public static String getUrl(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + getPort() + getPath(sourceProcess);
    }
}
