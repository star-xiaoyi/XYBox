package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
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
    private View touchView;
    private boolean changeBright;
    private boolean changeVolume;
    private boolean changeSpeed;
    private boolean changeScale;
    private boolean changeTime;
    private boolean changeEpisode;
    private boolean speedLock;
    private float speedDownY;
    private float speedBase;
    private boolean animating;
    private boolean touch;
    private boolean lock;
    private float bright;
    private float volume;
    private float sideStartY;
    private float scale;
    private float scaleFocusX;
    private float scaleFocusY;
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

    public boolean onTouchEvent(View v, MotionEvent e) {
        touchView = v;
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) speedDownY = e.getY();
        if (action == MotionEvent.ACTION_POINTER_DOWN) cancelSingleGesture(e);
        // 长按倍速一旦触发，GestureDetector 就不再派发 onScroll 了
        // （它内部 ACTION_MOVE 里 if (mInLongPress) 直接 break），
        // 所以锁定/取消的上下滑只能在这里自己判。
        if (changeSpeed && action == MotionEvent.ACTION_MOVE) checkSpeedLock(speedDownY - e.getY());
        if (changeEpisode && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) onEpisodeEnd();
        if (changeTime && e.getAction() == MotionEvent.ACTION_UP) onSeekEnd();
        if (changeSpeed && e.getAction() == MotionEvent.ACTION_UP) onSpeedRelease();
        if (changeBright && e.getAction() == MotionEvent.ACTION_UP) listener.onBrightEnd();
        if (changeVolume && e.getAction() == MotionEvent.ACTION_UP) listener.onVolumeEnd();
        // 双指手势开始后，这一整组事件都只交给缩放识别器。POINTER_UP 之后虽然只剩
        // 一根手指，也不能再把残余事件送回单指识别器，否则会误触长按、切集或点击。
        return changeScale || e.getPointerCount() > 1 ? scaleDetector.onTouchEvent(e) : detector.onTouchEvent(e);
    }

    private void cancelSingleGesture(MotionEvent event) {
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        detector.onTouchEvent(cancel);
        cancel.recycle();

        if (changeSpeed) {
            boolean lockedBeforeGesture = speedBase >= 1f;
            if (lockedBeforeGesture) listener.onSpeedProgress(1f);
            else listener.onSpeedEnd();
            speedLock = lockedBeforeGesture;
        }
        if (changeBright) listener.onBrightEnd();
        if (changeVolume) listener.onVolumeEnd();
        if (changeTime) listener.onSeekCancel();
        if (changeEpisode) videoView.setTranslationY(0f);
        changeBright = false;
        changeVolume = false;
        changeSpeed = false;
        changeTime = false;
        changeEpisode = false;
        touch = false;
        time = 0;
    }

    public void resetScale() {
        if (scale == 1.0f && videoView.getTranslationX() == 0f && videoView.getTranslationY() == 0f) return;
        videoView.animate().cancel();
        scale = 1.0f;
        listener.onScaleChanged(false);
        videoView.animate().scaleX(1.0f).scaleY(1.0f).translationX(0f).translationY(0f).setDuration(250).withEndAction(() -> {
            videoView.setPivotY(videoView.getHeight() / 2f);
            videoView.setPivotX(videoView.getWidth() / 2f);
        }).start();
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    /** 拖动进度中，给外面判断要不要出缩略图预览用。 */
    public boolean isSeeking() {
        return changeTime;
    }

    /**
     * 松手：progress 已经到位就把倍速留着（changeSpeed 归位，别挡住后面的滑动手势），
     * 没到位才恢复原速。
     * <p>
     * 这里不再回调 onSpeedLock 之类的东西——界面在滑动过程中已经跟着手指走到最终样子了，
     * 松手再播一次入场动画就是用户看到的"胶囊又弹一下"。
     */
    private void onSpeedRelease() {
        changeSpeed = false;
        // 已锁定但上滑距离不足时，松手要把被拖淡的胶囊弹回完整锁定态。
        // 只有回拉到 50%、speedLock 真正变为 false 才执行取消。
        if (speedLock) {
            listener.onSpeedProgress(1f);
            return;
        }
        listener.onSpeedEnd();
    }

    /** 解除倍速锁定，点提示胶囊或换集时调用。 */
    public boolean unlockSpeed() {
        if (!speedLock) return false;
        speedLock = false;
        changeSpeed = false;
        listener.onSpeedEnd();
        return true;
    }

    /**
     * 长按倍速中上下滑：跟手推进"锁定"这件事，不是到了阈值就一刀切。
     * <p>
     * progress 0 表示还是普通长按倍速（松手就恢复原速），1 表示锁定（松手继续）。
     * 界面按这个值做连续过渡，用户才看得出自己划到哪儿了、还差多少。
     * <p>
     * 起点取按下那一刻的锁定状态：本来就锁着的话从 1 开始，往上滑才往回退——
     * 否则每次长按都会把已经锁好的胶囊打回原形，凭空冒出一对箭头。
     * <p>
     * deltaY 是按下点减当前点，向下滑为负，所以取反。
     */
    private void checkSpeedLock(float deltaY) {
        boolean wasLocked = speedLock;
        float progress = speedBase + -deltaY / ResUtil.dp2px(72);
        progress = Math.max(0f, Math.min(1f, progress));
        // 使用带回差的两个阈值：到 100% 才锁定，回拉到 50% 才取消。
        // 这样同一次长按里下滑锁定后稍微回拉不会立刻取消；已经锁定后重新长按
        // 上滑也只需要走一半距离，不必把整个胶囊拖到 0。
        if (!speedLock && progress >= 1f) speedLock = true;
        else if (speedLock && progress <= 0.5f) speedLock = false;
        if (speedLock != wasLocked) hapticTick();
        listener.onSpeedProgress(progress);
    }

    private void hapticTick() {
        videoView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    public float getScale() {
        return scale;
    }

    /**
     * 边缘判定必须用真正接收触摸的那个 View（video 容器），不能用 videoView（exo）：
     * exo 会被捏合缩放和拖动切集改 scale/translation，拿它算窗口内坐标会跟着漂。
     */
    private boolean isEdge(MotionEvent e) {
        return touchView != null && ResUtil.isEdge(touchView, e, ResUtil.dp2px(24));
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
        speedBase = speedLock ? 1f : 0f;
        hapticTick();
        // 已经锁着了就别再走一遍"开始倍速"：速度早就是倍速的，箭头不该重新蹦出来
        if (speedLock) return;
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
        // 亮度/音量要从确认手势类型的那一刻开始计算。方向识别前的 20dp 只用于
        // 防误触，不能算进实际调节量，否则胶囊首次出现时会直接跳一截。
        if (changeBright) setBright(sideStartY - e2.getY());
        if (changeVolume) setVolume(sideStartY - e2.getY());
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
        sideStartY = e2.getY();
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
        videoView.animate().cancel();
        videoView.setPivotX(videoView.getWidth() / 2f);
        videoView.setPivotY(videoView.getHeight() / 2f);
        scaleFocusX = detector.getFocusX();
        scaleFocusY = detector.getFocusY();
        return changeScale = true;
    }

    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
        App.post(() -> changeScale = false, 500);
    }

    @Override
    public boolean onScale(@NonNull ScaleGestureDetector detector) {
        float oldScale = scale;
        scale = Math.max(1.0f, Math.min(oldScale * detector.getScaleFactor(), 5.0f));
        float ratio = scale / oldScale;
        float centerX = videoView.getWidth() / 2f;
        float centerY = videoView.getHeight() / 2f;
        float focusX = detector.getFocusX();
        float focusY = detector.getFocusY();

        // 固定画面中心作为缩放支点，再把双指中心的移动量折算成平移。
        // 如果逐帧修改 pivot，双指往左移动时坐标系会反向补偿，画面看起来反而往右跑。
        float translationX = ratio * videoView.getTranslationX() + focusX - centerX - ratio * (scaleFocusX - centerX);
        float translationY = ratio * videoView.getTranslationY() + focusY - centerY - ratio * (scaleFocusY - centerY);
        float limitX = (scale - 1.0f) * videoView.getWidth() / 2f;
        float limitY = (scale - 1.0f) * videoView.getHeight() / 2f;
        videoView.setTranslationX(Math.max(-limitX, Math.min(limitX, translationX)));
        videoView.setTranslationY(Math.max(-limitY, Math.min(limitY, translationY)));
        videoView.setScaleX(scale);
        videoView.setScaleY(scale);
        scaleFocusX = focusX;
        scaleFocusY = focusY;
        listener.onScaleChanged(scale > 1.001f || Math.abs(videoView.getTranslationX()) > 1f || Math.abs(videoView.getTranslationY()) > 1f);
        return true;
    }

    public interface Listener {

        void onSpeedUp();

        void onSpeedProgress(float progress);

        void onSpeedEnd();

        void onBright(int progress);

        void onBrightEnd();

        void onVolume(int progress);

        void onVolumeEnd();

        void onFlingUp();

        void onFlingDown();

        void onSeek(long time);

        void onSeekEnd(long time);

        void onSeekCancel();

        void onScaleChanged(boolean transformed);

        void onSingleTap();

        void onDoubleTap();

        void onDoubleTapLeft();

        void onDoubleTapRight();
    }
}
