package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Logger;

import java.io.File;

/**
 * Android 更新安装器
 * 处理安装权限检查和请求，以及APK安装
 */
public class UpdateInstaller {
    
    private static UpdateInstaller instance;
    private File pendingInstallFile; // 待安装的文件
    
    public static UpdateInstaller get() {
        if (instance == null) {
            instance = new UpdateInstaller();
        }
        return instance;
    }
    
    /**
     * 检查是否有安装权限
     * @return 是否有安装权限
     */
    public boolean hasInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return App.get().getPackageManager().canRequestPackageInstalls();
        }
        return true; // Android 8.0以下不需要此权限
    }
    
    /**
     * 请求安装权限（打开设置页面）
     */
    public void requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + App.get().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                App.get().startActivity(intent);
                Logger.d("UpdateInstaller: 已打开安装权限设置页面");
            } catch (Exception e) {
                Logger.e("UpdateInstaller: 无法打开安装权限设置页面: " + e.getMessage());
                Logger.e("Error", e);
            }
        }
    }
    
    /**
     * 安装 APK 文件
     * @param apkFile APK 文件
     * @return 是否成功启动安装流程
     */
    public boolean install(File apkFile) {
        return install(apkFile, false);
    }
    
    /**
     * 安装 APK 文件
     * @param apkFile APK 文件
     * @param checkPermission 是否检查权限（如果为false，即使没有权限也会尝试安装）
     * @return 是否成功启动安装流程
     */
    public boolean install(File apkFile, boolean checkPermission) {
        try {
            // Android 8.0+ 需要请求安装权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (checkPermission && !hasInstallPermission()) {
                    // 没有权限，保存待安装的文件，返回 false，由调用方处理
                    this.pendingInstallFile = apkFile;
                    Logger.d("UpdateInstaller: 没有安装权限，已保存待安装文件: " + apkFile.getAbsolutePath());
                    return false; // 返回false表示需要权限，但不表示失败
                }
            }
            
            // 检查文件是否存在
            if (!apkFile.exists() || !apkFile.isFile()) {
                Logger.e("UpdateInstaller: APK文件不存在或不是文件: " + apkFile.getAbsolutePath());
                return false;
            }
            
            // 使用 FileProvider 获取 URI
            String authority = App.get().getPackageName() + ".provider";
            Uri apkUri = FileProvider.getUriForFile(App.get(), authority, apkFile);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            App.get().startActivity(intent);
            Logger.d("UpdateInstaller: 已启动安装程序");
            this.pendingInstallFile = null; // 清除待安装文件
            return true;
        } catch (Exception e) {
            Logger.e("UpdateInstaller: 安装失败: " + e.getMessage());
            Logger.e("Error", e);
            return false;
        }
    }
    
    /**
     * 获取待安装的文件
     * @return 待安装的文件，如果没有则返回null
     */
    public File getPendingInstallFile() {
        return pendingInstallFile;
    }
    
    /**
     * 检查是否有待安装的文件且权限已授予
     * 用于应用恢复时自动检测
     */
    public boolean hasPendingInstall() {
        return pendingInstallFile != null && pendingInstallFile.exists() && hasInstallPermission();
    }
    
    /**
     * 自动重试安装（用于应用恢复时）
     */
    public boolean autoRetryInstall() {
        if (hasPendingInstall()) {
            File file = pendingInstallFile;
            pendingInstallFile = null; // 清除待安装文件
            return install(file, false); // 不检查权限，因为已经检查过了
        }
        return false;
    }
    
    /**
     * 清除待安装的文件
     */
    public void clearPendingInstall() {
        this.pendingInstallFile = null;
    }
}

