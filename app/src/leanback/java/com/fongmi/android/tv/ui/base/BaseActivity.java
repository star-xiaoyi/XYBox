package com.fongmi.android.tv.ui.base;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import me.jessyan.autosize.AutoSizeCompat;

public abstract class BaseActivity extends AppCompatActivity {

    protected abstract ViewBinding getBinding();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getBinding().getRoot());
        EventBus.getDefault().register(this);
        Util.hideSystemUI(this);
        setBackCallback();
        initView();
        initEvent();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        refreshWall();
    }

    protected Activity getActivity() {
        return this;
    }

    protected boolean customWall() {
        return true;
    }

    protected boolean handleBack() {
        return false;
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void onBackPress() {
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    protected void notifyItemChanged(RecyclerView view, ArrayObjectAdapter adapter) {
        if (!view.isComputingLayout()) adapter.notifyArrayItemRangeChanged(0, adapter.size());
    }

    private void setBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(handleBack()) {
            @Override
            public void handleOnBackPressed() {
                onBackPress();
            }
        });
    }

    private void refreshWall() {
        try {
            if (!customWall()) return;
            int wallIndex = Setting.getWall();
            int screenWidth = ResUtil.getScreenWidth();
            int screenHeight = ResUtil.getScreenHeight();
            // 使用 Glide 动态裁剪到屏幕尺寸
            Glide.with(App.get())
                    .asBitmap()
                    .load(wallIndex == 0 ? FileUtil.getWall(0) : ResUtil.getDrawable("wallpaper_" + wallIndex))
                    .override(screenWidth, screenHeight)
                    .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop())
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull android.graphics.Bitmap resource, @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                            // 使用 BitmapDrawable 并设置重力为填充
                            android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(getResources(), resource);
                            drawable.setGravity(android.view.Gravity.FILL);
                            getWindow().setBackgroundDrawable(drawable);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            super.onLoadFailed(errorDrawable);
                            getWindow().setBackgroundDrawableResource(R.drawable.wallpaper_6);
                        }
                    });
        } catch (Exception e) {
            getWindow().setBackgroundDrawableResource(R.drawable.wallpaper_6);
        }
    }

    private Resources hackResources(Resources resources) {
        try {
            AutoSizeCompat.autoConvertDensityOfGlobal(resources);
            return resources;
        } catch (Exception ignored) {
            return resources;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.WALL) refreshWall();
    }

    @Override
    public Resources getResources() {
        return hackResources(super.getResources());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
