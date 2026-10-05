package com.fongmi.android.tv.ui.base;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThemeUtil;
import com.fongmi.android.tv.utils.SourceUiGuard;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;


public abstract class BaseActivity extends AppCompatActivity {

    private OnBackPressedCallback mBackCallback;
    private View mContentRoot;
    private View mPredictiveBackTarget;
    private float mBackAlpha;
    private float mBackScaleX;
    private float mBackScaleY;
    private float mBackTranslationX;
    private float mBackTranslationY;

    protected abstract ViewBinding getBinding();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyNightMode();
        super.onCreate(savedInstanceState);
        if (transparent()) setTransparent(this);
        ViewBinding binding = getBinding();
        mContentRoot = binding.getRoot();
        setContentView(mContentRoot);
        SourceUiGuard.protectWindow(this, mContentRoot);
        EventBus.getDefault().register(this);
        initView(savedInstanceState);
        setBackCallback();
        initEvent();
    }

    protected Activity getActivity() {
        return this;
    }

    @Override
    public Object getSystemService(String name) {
        return SourceUiGuard.filterService(name, super.getSystemService(name));
    }

    @Override
    public WindowManager getWindowManager() {
        return SourceUiGuard.filterWindowManager(super.getWindowManager());
    }

    protected boolean transparent() {
        return true;
    }

    /** 当前是否存在需要由页面先消费的返回层级。返回 Activity 时应保持 false，交给系统做跨页预览。 */
    protected boolean shouldInterceptBack() {
        return false;
    }

    protected void initView(Bundle savedInstanceState) {
    }

    protected void initEvent() {
    }

    protected void onBackPress() {
        finishAfterTransition();
    }

    /** 返回手势期间要跟手的视图；子类可缩小到面板或控制层。 */
    protected View getPredictiveBackTarget() {
        return mContentRoot;
    }

    /** 锁屏等“返回无动作”的状态不应播放会误导用户的离场动画。 */
    protected boolean shouldAnimatePredictiveBack() {
        return true;
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    protected void setPadding(ViewGroup layout) {
        setPadding(layout, false);
    }

    protected void setPadding(ViewGroup layout, boolean leftOnly) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        DisplayCutout cutout = ResUtil.getDisplay(this).getCutout();
        if (cutout == null) return;
        int top = cutout.getSafeInsetTop();
        int left = cutout.getSafeInsetLeft();
        int right = cutout.getSafeInsetRight();
        int bottom = cutout.getSafeInsetBottom();
        int padding = left | right | top | bottom;
        layout.setPadding(padding, 0, leftOnly ? 0 : padding, 0);
    }

    protected void noPadding(ViewGroup layout) {
        layout.setPadding(0, 0, 0, 0);
    }

    private void setBackCallback() {
        mBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                restorePredictiveBackTarget(false);
                onBackPress();
                refreshBackHandling();
            }

            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                if (!Setting.isPredictiveBackEnabled() || !shouldAnimatePredictiveBack()) return;
                capturePredictiveBackTarget();
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                if (!Setting.isPredictiveBackEnabled() || !shouldAnimatePredictiveBack()) return;
                if (mPredictiveBackTarget == null) capturePredictiveBackTarget();
                if (mPredictiveBackTarget == null) return;
                float progress = 1f - (float) Math.pow(1f - backEvent.getProgress(), 3);
                float direction = backEvent.getSwipeEdge() == BackEventCompat.EDGE_RIGHT ? -1f : 1f;
                mPredictiveBackTarget.setTranslationX(mBackTranslationX + direction * mPredictiveBackTarget.getWidth() * 0.08f * progress);
                mPredictiveBackTarget.setTranslationY(mBackTranslationY + mPredictiveBackTarget.getHeight() * 0.015f * progress);
                mPredictiveBackTarget.setScaleX(mBackScaleX * (1f - 0.04f * progress));
                mPredictiveBackTarget.setScaleY(mBackScaleY * (1f - 0.04f * progress));
                mPredictiveBackTarget.setAlpha(mBackAlpha * (1f - 0.14f * progress));
            }

            @Override
            public void handleOnBackCancelled() {
                restorePredictiveBackTarget(true);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, mBackCallback);
        refreshBackHandling();
    }

    private void capturePredictiveBackTarget() {
        restorePredictiveBackTarget(false);
        mPredictiveBackTarget = getPredictiveBackTarget();
        if (mPredictiveBackTarget == null) return;
        mPredictiveBackTarget.animate().cancel();
        mBackAlpha = mPredictiveBackTarget.getAlpha();
        mBackScaleX = mPredictiveBackTarget.getScaleX();
        mBackScaleY = mPredictiveBackTarget.getScaleY();
        mBackTranslationX = mPredictiveBackTarget.getTranslationX();
        mBackTranslationY = mPredictiveBackTarget.getTranslationY();
    }

    private void restorePredictiveBackTarget(boolean animate) {
        View target = mPredictiveBackTarget;
        mPredictiveBackTarget = null;
        if (target == null) return;
        target.animate().cancel();
        if (animate && target.isAttachedToWindow()) {
            target.animate()
                    .alpha(mBackAlpha)
                    .scaleX(mBackScaleX)
                    .scaleY(mBackScaleY)
                    .translationX(mBackTranslationX)
                    .translationY(mBackTranslationY)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            target.setAlpha(mBackAlpha);
            target.setScaleX(mBackScaleX);
            target.setScaleY(mBackScaleY);
            target.setTranslationX(mBackTranslationX);
            target.setTranslationY(mBackTranslationY);
        }
    }

    /** UI 状态变化后调用；公开给 Fragment，使系统能在手势开始前知道是否需要由应用接管。 */
    public final void refreshBackHandling() {
        if (mBackCallback == null) return;
        mBackCallback.setEnabled(!Setting.isPredictiveBackEnabled() || shouldInterceptBack());
    }

    protected final void dispatchBack() {
        getOnBackPressedDispatcher().onBackPressed();
    }

    private void setTransparent(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (!night) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        // 透明系统栏会触发系统自动垫的对比度 scrim（小白条下的长方形），API 29 起可以关掉。
        // 主题里也配了同样两项，这里再补一次是因为部分页面走的是运行时设色，不吃主题那份。
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
            activity.getWindow().setStatusBarContrastEnforced(false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBackHandling();
    }

    @Override
    protected void onDestroy() {
        restorePredictiveBackTarget(false);
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
