package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.Util;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.Players;

import java.util.concurrent.TimeUnit;

public class CustomSeekView extends FrameLayout implements TimeBar.OnScrubListener {

    private static final int MAX_UPDATE_INTERVAL_MS = 1000;
    private static final int MIN_UPDATE_INTERVAL_MS = 200;

    private TextView positionView;
    private TextView durationView;
    private DefaultTimeBar timeBar;

    private Runnable refresh;
    private Players player;
    private ScrubListener scrubListener;
    /** 投屏时进度来自电视而不是本地播放器，见 {@link Source}。 */
    private Source source;

    private long currentDuration;
    private long currentPosition;
    private long currentBuffered;
    private boolean scrubbing;
    private boolean isPressed;

    public CustomSeekView(Context context) {
        this(context, null);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_control_seek, this);
        init();
        start();
    }

    private void init() {
        positionView = findViewById(R.id.position);
        durationView = findViewById(R.id.duration);
        timeBar = findViewById(R.id.timeBar);
        timeBar.addListener(this);
        refresh = this::refresh;
        
        // 添加触摸事件处理，实现按住时圆球变大的效果
        timeBar.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!isPressed) {
                        isPressed = true;
                        // 按住时：轨道变高到4dp
                        setTimeBarHeight(4);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isPressed) {
                        isPressed = false;
                        // 松开时：轨道恢复到2dp
                        setTimeBarHeight(2);
                    }
                    break;
            }
            return false; // 不拦截事件，让DefaultTimeBar正常处理
        });
    }

    public void setListener(Players player) {
        this.player = player;
    }

    /** 拖动进度条时外部要跟着出缩略图预览、并挡住控制层的自动隐藏。 */
    public void setScrubListener(ScrubListener listener) {
        this.scrubListener = listener;
    }

    /**
     * 预览小窗要停在滑块正上方，所以得知道 timeBar 本身的位置——
     * 不能拿整个 CustomSeekView 算，它左右还各有一个时间文本，宽度不固定。
     */
    public int getTimeBarLeftInWindow() {
        int[] location = new int[2];
        timeBar.getLocationInWindow(location);
        return location[0];
    }

    public int getTimeBarWidth() {
        return timeBar.getWidth();
    }

    /**
     * 把进度来源换成外部实现（投屏时是电视）。传 null 恢复成读本地播放器。
     * <p>
     * 之所以做成接口而不是直接依赖投屏管理器：这个 View 在 main 源码集，投屏那套在 mobile，
     * 反向依赖编不过。
     */
    public void setSource(Source source) {
        this.source = source;
        start();
    }

    private long sourcePosition() {
        return source != null ? source.getPosition() : player.getPosition();
    }

    private long sourceDuration() {
        return source != null ? source.getDuration() : player.getDuration();
    }

    private boolean sourcePlaying() {
        return source != null ? source.isPlaying() : player.isPlaying();
    }

    private boolean sourceEmpty() {
        return source != null ? source.getDuration() <= 0 : player.isEmpty();
    }
    
    public void setPosition(long position) {
        timeBar.setPosition(position);
    }
    
    public void setDuration(long duration) {
        timeBar.setDuration(duration);
    }
    
    /**
     * 动态调整进度条高度
     * @param barHeightDp 轨道高度（dp）
     */
    private void setTimeBarHeight(int barHeightDp) {
        int barHeightPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                barHeightDp, 
                getContext().getResources().getDisplayMetrics()
        );
        
        // 尝试通过反射设置DefaultTimeBar的内部barHeight字段
        try {
            java.lang.reflect.Field barHeightField = timeBar.getClass().getDeclaredField("barHeight");
            barHeightField.setAccessible(true);
            barHeightField.setInt(timeBar, barHeightPx);
            
            // 强制刷新
            timeBar.invalidate();
            timeBar.requestLayout();
        } catch (Exception e) {
            // 如果反射失败，尝试调整布局参数
            android.util.Log.w("CustomSeekView", "Failed to set bar height via reflection: " + e.getMessage());
            if (timeBar.getLayoutParams() != null) {
                timeBar.getLayoutParams().height = barHeightPx;
                timeBar.requestLayout();
            }
        }
    }

    private void start() {
        removeCallbacks(refresh);
        post(refresh);
    }

    private void refresh() {
        long duration = sourceDuration();
        long position = sourcePosition();
        long buffered = source != null ? position : player.getBuffered();
        boolean positionChanged = position != currentPosition;
        boolean durationChanged = duration != currentDuration;
        boolean bufferedChanged = buffered != currentBuffered;
        currentDuration = duration;
        currentPosition = position;
        currentBuffered = buffered;
        if (durationChanged) {
            setKeyTimeIncrement(duration);
            timeBar.setDuration(duration);
            durationView.setText(player.stringToTime(duration < 0 ? 0 : duration));
        }
        if (positionChanged && !scrubbing) {
            timeBar.setPosition(position);
            positionView.setText(player.stringToTime(position < 0 ? 0 : position));
        }
        if (bufferedChanged) {
            timeBar.setBufferedPosition(buffered);
        }
        removeCallbacks(refresh);
        if (sourceEmpty()) {
            positionView.setText("00:00");
            durationView.setText("00:00");
            timeBar.setPosition(currentPosition = 0);
            timeBar.setDuration(currentDuration = 0);
            postDelayed(refresh, MIN_UPDATE_INTERVAL_MS);
        } else if (sourcePlaying()) {
            postDelayed(refresh, delayMs(position));
        } else {
            postDelayed(refresh, MAX_UPDATE_INTERVAL_MS);
        }
    }

    public void setKeyTimeIncrement(long duration) {
        if (duration > TimeUnit.HOURS.toMillis(3)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(5));
        } else if (duration > TimeUnit.MINUTES.toMillis(30)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
        } else if (duration > TimeUnit.MINUTES.toMillis(15)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(30));
        } else if (duration > TimeUnit.MINUTES.toMillis(10)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(15));
        } else if (duration > 0) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private long delayMs(long position) {
        long mediaTimeUntilNextFullSecondMs = 1000 - position % 1000;
        long mediaTimeDelayMs = Math.min(timeBar.getPreferredUpdateDelay(), mediaTimeUntilNextFullSecondMs);
        long delayMs = (long) (mediaTimeDelayMs / player.getSpeed());
        return Util.constrainValue(delayMs, MIN_UPDATE_INTERVAL_MS, MAX_UPDATE_INTERVAL_MS);
    }

    private void seekToTimeBarPosition(long positionMs) {
        // 先设置播放位置
        if (source != null) source.seekTo(positionMs);
        else player.seekTo(positionMs);
        // 延迟刷新进度条，确保播放器已经处理了跳转操作
        removeCallbacks(refresh);
        postDelayed(() -> {
            // 只有在非拖动状态下才刷新进度条位置
            if (!scrubbing) {
                refresh();
                // 确保进度条位置与实际播放位置一致
                long actualPosition = sourcePosition();
                if (Math.abs(actualPosition - positionMs) > 100) { // 如果差异超过100ms，再次调整
                    timeBar.setPosition(actualPosition);
                    positionView.setText(player.stringToTime(actualPosition));
                }
            }
        }, 100); // 增加延迟时间，确保拖拽状态完全结束
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(refresh);
    }

    @Override
    public void onScrubStart(@NonNull TimeBar timeBar, long position) {
        scrubbing = true;
        positionView.setText(player.stringToTime(position));
        if (scrubListener != null) scrubListener.onScrubStart(position);
    }

    @Override
    public void onScrubMove(@NonNull TimeBar timeBar, long position) {
        positionView.setText(player.stringToTime(position));
        if (scrubListener != null) scrubListener.onScrubMove(position);
    }

    @Override
    public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
        scrubbing = false;
        if (scrubListener != null) scrubListener.onScrubStop(position, canceled);

        if (!canceled) {
            // 立即设置进度条位置到目标位置，避免圆球跳回原始位置
            timeBar.setPosition(position);
            positionView.setText(player.stringToTime(position));
            
            // 调整播放位置
            seekToTimeBarPosition(position);
            // 确保播放状态正确
            if (source == null && !player.isPlaying()) {
                player.play();
            }
        }

        // 不干预DefaultTimeBar的圆球绘制，让它自己处理
    }

    /** 进度/播放态的外部来源，投屏时由 VideoActivity 接到投屏会话上。 */
    public interface Source {

        long getPosition();

        long getDuration();

        boolean isPlaying();

        void seekTo(long position);
    }

    /** 拖动进度条的三个时机，用来出/更新/收掉缩略图预览。 */
    public interface ScrubListener {

        void onScrubStart(long position);

        void onScrubMove(long position);

        void onScrubStop(long position, boolean canceled);
    }
}
