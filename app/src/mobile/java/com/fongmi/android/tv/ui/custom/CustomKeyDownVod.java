package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

public class CustomKeyDownVod extends GestureDetector.SimpleOnGestureListener implements ScaleGestureDetector.OnScaleGestureListener {

    private static final int DISTANCE = 250;
    private static final int VELOCITY = 10;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector detector;
    private final AudioManager manager;
    private final Listener listener;
    private final Activity activity;
    private final View videoView;
    private boolean changeBright;
    private boolean changeVolume;
    private boolean changeSpeed;
    private boolean changeScale;
    private boolean changeTime;
    private boolean changeEpisode;
    private boolean animating;
    private boolean center;
    private boolean touch;
    private boolean lock;
    private float bright;
    private int lastVolume;
    private float lastBright;
    private float volume;
    private float scale;
    private long time;

    public static CustomKeyDownVod create(Activity activity, View videoView) {
        return new CustomKeyDownVod(activity, videoView);
    }

    private CustomKeyDownVod(Activity activity, View videoView) {
        this.manager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.scaleDetector = new ScaleGestureDetector(activity, this);
        this.detector = new GestureDetector(activity, this);
        this.listener = (Listener) activity;
        this.videoView = videoView;
        this.activity = activity;
        this.scale = 1.0f;
    }

    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (changeEpisode && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) onEpisodeEnd();
        if (changeTime && e.getAction() == MotionEvent.ACTION_UP) onSeekEnd();
        if (changeSpeed && e.getAction() == MotionEvent.ACTION_UP) listener.onSpeedEnd();
        if (changeBright && e.getAction() == MotionEvent.ACTION_UP) listener.onBrightEnd();
        if (changeVolume && e.getAction() == MotionEvent.ACTION_UP) listener.onVolumeEnd();
        return e.getPointerCount() == 2 ? scaleDetector.onTouchEvent(e) : detector.onTouchEvent(e);
    }

    public void resetScale() {
        if (scale == 1.0f) return;
        videoView.animate().scaleX(1.0f).scaleY(1.0f).translationX(0f).translationY(0f).setDuration(250).withEndAction(() -> {
            videoView.setPivotY(videoView.getHeight() / 2f);
            videoView.setPivotX(videoView.getWidth() / 2f);
            scale = 1.0f;
        }).start();
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    public float getScale() {
        return scale;
    }

    private boolean isEdge(MotionEvent e) {
        return ResUtil.isEdge(activity, e, ResUtil.dp2px(24));
    }

    @Override
    public boolean onDown(@NonNull MotionEvent e) {
        if (isEdge(e) || changeScale || lock || e.getPointerCount() > 1) return true;
        volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
        bright = Util.getBrightness(activity);
        changeBright = false;
        changeVolume = false;
        changeSpeed = false;
        changeTime = false;
        changeEpisode = false;
        center = false;
        touch = true;
        lastVolume = (int) volume;
        lastBright = bright;
        return true;
    }

    @Override
    public void onLongPress(@NonNull MotionEvent e) {
        if (isEdge(e) || changeScale || lock || e.getPointerCount() > 1) return;
        changeSpeed = true;
        listener.onSpeedUp();
    }

    @Override
    public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        if (isEdge(e1) || changeScale || lock || e1.getPointerCount() > 1) return true;
        float deltaX = e2.getX() - e1.getX();
        float deltaY = e1.getY() - e2.getY();
        
        // 在横屏模式下，调整触摸事件的处理逻辑
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏模式下，增加对水平滑动的敏感度
            if (Math.abs(deltaX) > Math.abs(deltaY) * 0.5f) {
                if (touch) checkFunc(distanceX, distanceY, e2);
                if (changeTime) listener.onSeek(time = (long) (deltaX * 50));
                return true;
            }
        }
        
        if (touch) checkFunc(distanceX, distanceY, e2);
        if (changeTime) listener.onSeek(time = (long) (deltaX * 50));
        if (changeBright) setBright(deltaY);
        if (changeVolume) setVolume(deltaY);
        if (changeEpisode) dragEpisode(deltaY);
        return true;
    }

    /**
     * 中间区域上下拖：视频跟着手指走，松手拖够了才换集。
     * 原来是靠 onFling 判定的，表现是画面抖一下就直接跳集，完全不跟手。
     */
    private void dragEpisode(float deltaY) {
        float limit = videoView.getHeight() / 3f;
        videoView.setTranslationY(Math.max(-limit, Math.min(limit, -deltaY)));
    }

    private void onEpisodeEnd() {
        float offset = videoView.getTranslationY();
        float threshold = Math.max(ResUtil.dp2px(64), videoView.getHeight() * 0.12f);
        if (Math.abs(offset) < threshold) {
            videoView.animate().translationY(0).setDuration(150).withEndAction(() -> changeEpisode = false).start();
            return;
        }
        boolean up = offset < 0;
        videoView.animate().translationY(up ? -videoView.getHeight() / 3f : videoView.getHeight() / 3f).setDuration(150).withEndAction(() -> {
            videoView.setTranslationY(0);
            changeEpisode = false;
            if (up) listener.onFlingUp();
            else listener.onFlingDown();
        }).start();
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        if (isEdge(e) || changeScale || e.getPointerCount() > 1) return true;
        if (lock) return true;
        int screenWidth = videoWidth();
        float leftBoundary = screenWidth * 0.2f;
        float rightBoundary = screenWidth * 0.8f;
        boolean seekEnabled = Setting.isGestureDoubleTapSeek();
        if (seekEnabled && e.getX() < leftBoundary) listener.onDoubleTapLeft();
        else if (seekEnabled && e.getX() > rightBoundary) listener.onDoubleTapRight();
        else if (Setting.isGestureDoubleTapPlay()) listener.onDoubleTap();
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        if (isEdge(e) || changeScale || e.getPointerCount() > 1) return true;
        listener.onSingleTap();
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
        if (changeEpisode) return true;
        if (isEdge(e1) || changeScale || !center || animating || e1.getPointerCount() > 1) return true;
        checkFunc(e1, e2, velocityY);
        return true;
    }

    private void onSeekEnd() {
        listener.onSeekEnd(time);
        changeTime = false;
        time = 0;
    }

    private void checkFunc(float distanceX, float distanceY, MotionEvent e2) {
        int four = videoWidth() / 4;
        
        // 在横屏模式下，调整中心区域的判断
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏模式下，扩大中心区域，更容易触发进度条调整
            int centerStart = videoWidth() / 3;
            int centerEnd = videoWidth() * 2 / 3;
            if (e2.getX() > centerStart && e2.getX() < centerEnd) {
                center = true;
            } else if (Math.abs(distanceX) < Math.abs(distanceY)) {
                checkSide(e2);
            }
            // 横屏模式下，降低触发进度条调整的阈值
            if (Setting.isGestureProgress() && Math.abs(distanceX) >= Math.abs(distanceY) * 0.7f) {
                changeTime = true;
            }
            if (center && !changeTime && Setting.isGestureEpisodeLand()) changeEpisode = true;
        } else {
            // 竖屏模式保持原有逻辑
            if (e2.getX() > four && e2.getX() < four * 3) {
                center = true;
            } else if (Math.abs(distanceX) < Math.abs(distanceY)) {
                checkSide(e2);
            }
            if (Setting.isGestureProgress() && Math.abs(distanceX) >= Math.abs(distanceY)) {
                changeTime = true;
            }
            if (center && !changeTime && Setting.isGestureEpisodePort()) changeEpisode = true;
        }
        touch = false;
    }

    private void checkFunc(MotionEvent e1, MotionEvent e2, float velocityY) {
        if (e1.getY() - e2.getY() > DISTANCE && Math.abs(velocityY) > VELOCITY) {
            videoView.animate().translationYBy(-ResUtil.dp2px(24)).setDuration(150).withStartAction(() -> animating = true).withEndAction(() -> videoView.animate().translationY(0).setDuration(100).withStartAction(listener::onFlingUp).withEndAction(() -> animating = false).start()).start();
        } else if (e2.getY() - e1.getY() > DISTANCE && Math.abs(velocityY) > VELOCITY) {
            videoView.animate().translationYBy(ResUtil.dp2px(24)).setDuration(150).withStartAction(() -> animating = true).withEndAction(() -> videoView.animate().translationY(0).setDuration(100).withStartAction(listener::onFlingDown).withEndAction(() -> animating = false).start()).start();
        }
    }

    /**
     * 触摸监听挂在视频容器上，事件坐标本来就是相对视频的。
     * 分区必须按视频宽度算——横屏分栏时视频只占左半边，
     * 用屏幕宽度会把"右半边调音量"划到详情卡片上去。
     */
    private int videoWidth() {
        int width = videoView.getWidth();
        return width > 0 ? width : ResUtil.getScreenWidth(activity);
    }

    private void checkSide(MotionEvent e2) {
        int half = videoWidth() / 2;
        if (e2.getX() > half) changeVolume = Setting.isGestureVolume();
        else changeBright = Setting.isGestureBrightness();
    }

    private void setBright(float deltaY) {
        if (bright == -1.0f) bright = 0.5f;
        int height = videoView.getMeasuredHeight();
        float brightness = deltaY * 2 / height + bright;
        if (brightness < 0) brightness = 0f;
        if (brightness > 1.0f) brightness = 1.0f;
        if (Math.abs(brightness - lastBright) < 0.005f) return;
        lastBright = brightness;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        activity.getWindow().setAttributes(attributes);
        listener.onBright((int) (brightness * 100));
    }

    private void setVolume(float deltaY) {
        int height = videoView.getMeasuredHeight();
        int maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float deltaV = deltaY * 2 / height * maxVolume;
        float index = volume + deltaV;
        if (index > maxVolume) index = maxVolume;
        if (index < 0) index = 0;
        if ((int) index == lastVolume) return;
        lastVolume = (int) index;
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) index, 0);
        listener.onVolume((int) (index / maxVolume * 100));
    }

    @Override
    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
        if (changeBright || changeVolume || changeSpeed || changeTime || lock) return changeScale = false;
        return changeScale = true;
    }

    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
        App.post(() -> changeScale = false, 500);
    }

    @Override
    public boolean onScale(@NonNull ScaleGestureDetector detector) {
        scale *= detector.getScaleFactor();
        scale = Math.max(1.0f, Math.min(scale, 5.0f));
        videoView.setPivotX(detector.getFocusX());
        videoView.setPivotY(detector.getFocusY());
        videoView.setScaleX(scale);
        videoView.setScaleY(scale);
        return true;
    }

    public interface Listener {

        void onSpeedUp();

        void onSpeedEnd();

        void onBright(int progress);

        void onBrightEnd();

        void onVolume(int progress);

        void onVolumeEnd();

        void onFlingUp();

        void onFlingDown();

        void onSeek(long time);

        void onSeekEnd(long time);

        void onSingleTap();

        void onDoubleTap();

        void onDoubleTapLeft();

        void onDoubleTapRight();
    }
}
