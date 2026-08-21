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
    private boolean touch;
    private boolean lock;
    private float bright;
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
        touch = true;
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
        if (isEdge(e1) || changeScale || lock || changeSpeed || e1.getPointerCount() > 1) return true;
        float deltaX = e2.getX() - e1.getX();
        float deltaY = e1.getY() - e2.getY();
        // 用累计位移判定方向，不能用 onScroll 传进来的每帧增量：
        // 慢速滑动时增量只有零点几像素，方向判定几乎是随机的。
        if (touch) checkFunc(Math.abs(deltaX), Math.abs(deltaY), e2);
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
        // 拖动上限是 height/3，阈值取 0.25 倍高度（上限的 75%），
        // 既要求明显的大幅拖动，又保证任何尺寸下都够得到。
        float threshold = videoView.getHeight() * 0.25f;
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
        // 切集已经改成跟手拖动（dragEpisode / onEpisodeEnd），这里不再做甩动判定
        return true;
    }

    private void onSeekEnd() {
        listener.onSeekEnd(time);
        changeTime = false;
        time = 0;
    }

    /**
     * 决定这一轮手势干什么。distanceX / distanceY 传的是从按下点算起的累计位移绝对值。
     *
     * 关键是先用 20dp 的门槛把方向判稳：位移太小就直接返回、保持 touch 为 true，
     * 下一帧再判。原来在第一帧就用每帧增量定死，慢速滑动时增量只有零点几像素，
     * 结果方向基本靠运气，表现就是"慢慢滑没反应"。
     */
    private void checkFunc(float distanceX, float distanceY, MotionEvent e2) {
        if (Math.hypot(distanceX, distanceY) < ResUtil.dp2px(20)) return;
        // 中间只留 1/4，左右各让出 3/8 给亮度和音量
        int narrow = (int) (videoWidth() * 0.375f);
        boolean center = e2.getX() > narrow && e2.getX() < videoWidth() - narrow;
        boolean episode = ResUtil.isLand(activity) ? Setting.isGestureEpisodeLand() : Setting.isGestureEpisodePort();
        if (distanceX >= distanceY) {
            if (Setting.isGestureProgress()) changeTime = true;
        } else if (center) {
            if (episode) changeEpisode = true;
        } else {
            checkSide(e2);
        }
        touch = false;
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

    /**
     * 亮度和音量每一帧都直接写下去，不做任何节流。
     * 之前加过"值没跨过阈值就 return"的节流，结果把 UI 更新也一起挡了，
     * 表现就是慢慢滑没反馈、攒够了突然跳一格。
     */
    private void setBright(float deltaY) {
        if (bright == -1.0f) bright = 0.5f;
        int height = videoView.getMeasuredHeight();
        if (height <= 0) return;
        float brightness = deltaY * 2.0f / height + bright;
        if (brightness < 0) brightness = 0f;
        if (brightness > 1.0f) brightness = 1.0f;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        activity.getWindow().setAttributes(attributes);
        listener.onBright((int) (brightness * 100));
    }

    private void setVolume(float deltaY) {
        int height = videoView.getMeasuredHeight();
        if (height <= 0) return;
        int maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float index = volume + deltaY * 2.0f / height * maxVolume;
        if (index > maxVolume) index = maxVolume;
        if (index < 0) index = 0;
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) index, 0);
        // 进度条用连续值算百分比，不要拿取整后的档位算：
        // 有的机型系统音量只有 15 档，按档位算出来的进度条是一格一格跳的。
        listener.onVolume((int) (index / maxVolume * 100.0f));
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
