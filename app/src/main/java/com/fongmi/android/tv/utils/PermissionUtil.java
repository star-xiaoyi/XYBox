package com.fongmi.android.tv.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.core.app.ActivityCompat;

import com.fongmi.android.tv.impl.PermissionCallback;
import com.permissionx.guolindev.PermissionX;
import com.github.catvod.utils.Logger;

import java.util.function.Consumer;
import java.util.Arrays;

public class PermissionUtil {

    /** Record the initiating stack when a source uses AndroidX; result callbacks alone cannot identify it. */
    public static void installAudit() {
        ActivityCompat.PermissionCompatDelegate previous = ActivityCompat.getPermissionCompatDelegate();
        ActivityCompat.setPermissionCompatDelegate(new ActivityCompat.PermissionCompatDelegate() {
            @Override public boolean requestPermissions(Activity activity, String[] permissions, int requestCode) {
                for (String permission : permissions) {
                    if (!Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)
                            && !Manifest.permission.READ_EXTERNAL_STORAGE.equals(permission)) continue;
                    String caller = SourceUiOrigin.findCaller();
                    Logger.i("FilePermission: action=request api=androidx page=" + activity.getClass().getSimpleName()
                            + " requestCode=" + requestCode + " caller=" + (caller == null ? "app" : caller)
                            + " jar=" + SourceUiOrigin.jarForCaller(caller) + " permissions=" + Arrays.toString(permissions)
                            + "\n" + android.util.Log.getStackTraceString(new Throwable("File permission request origin")));
                    break;
                }
                return previous != null && previous.requestPermissions(activity, permissions, requestCode);
            }
            @Override public boolean onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
                return previous != null && previous.onActivityResult(activity, requestCode, resultCode, data);
            }
        });
    }

    public static String fileState(Context context) {
        return "read=" + state(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                + " write=" + state(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private static String state(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ? "granted" : "denied";
    }

    public static void requestAudio(FragmentActivity activity, Consumer<Boolean> callback) {
        PermissionX.init(activity).permissions(Manifest.permission.RECORD_AUDIO).request(new PermissionCallback(callback));
    }

    public static void requestFile(FragmentActivity activity, Consumer<Boolean> callback) {
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(callback));
    }

    public static void requestFile(Fragment fragment, Consumer<Boolean> callback) {
        PermissionX.init(fragment).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(callback));
    }
}
