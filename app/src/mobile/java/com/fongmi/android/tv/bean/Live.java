package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

/** Legacy database/backup record only. The mobile app has no live playback module. */
@Entity
public class Live {

    @NonNull
    @PrimaryKey
    @SerializedName("name")
    private String name;

    @SerializedName("keep")
    private String keep;

    @SerializedName("boot")
    private boolean boot;

    @SerializedName("pass")
    private boolean pass;

    public Live() {
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public String getKeep() {
        return TextUtils.isEmpty(keep) ? "" : keep;
    }

    public void setKeep(String keep) {
        this.keep = keep;
    }

    public boolean isBoot() {
        return boot;
    }

    public void setBoot(boolean boot) {
        this.boot = boot;
    }

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }
}
