package com.fongmi.android.tv.utils;

/**
 * 设备类型工具类，用于覆盖默认的设备类型检测
 */
public class DeviceUtils {

    /**
     * 是否强制使用手机模式
     */
    public static final boolean FORCE_MOBILE_MODE = true;

    /**
     * 获取设备类型
     * @return 0:TV，1:手机
     */
    public static int getDeviceType() {
        if (FORCE_MOBILE_MODE) {
            return 1; // 强制返回手机模式
        }
        return Util.isLeanback() ? 0 : 1;
    }
} 