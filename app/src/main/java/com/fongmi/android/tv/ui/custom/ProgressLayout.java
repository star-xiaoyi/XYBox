package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.fongmi.android.tv.databinding.ViewEmptyBinding;
import com.fongmi.android.tv.databinding.ViewProgressBinding;
import com.airbnb.lottie.LottieAnimationView;

import java.util.ArrayList;
import java.util.List;

public class ProgressLayout extends RelativeLayout {

    private static final String TAG_PROGRESS = "ProgressLayout.TAG_PROGRESS";

    public enum State {
        CONTENT, PROGRESS, EMPTY
    }

    private List<View> mContentViews;
    private View mProgressView;
    private View mEmptyView;
    private State mState;

    public ProgressLayout(Context context) {
        super(context);
    }

    public ProgressLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProgressLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mState = State.CONTENT;
        mContentViews = new ArrayList<>();
        initView();
    }

    private void initView() {
        // 首页分类拉不到内容属于报错，用纯文字；摇箱子动画只留给收藏和历史记录那种"还没存东西"
        mEmptyView = LayoutInflater.from(getContext()).inflate(com.fongmi.android.tv.R.layout.view_empty_text, null);
        mEmptyView.setTag(TAG_PROGRESS);
        mEmptyView.setVisibility(GONE);
        mProgressView = ViewProgressBinding.inflate(LayoutInflater.from(getContext())).getRoot();
        mProgressView.setTag(TAG_PROGRESS);
        mProgressView.setVisibility(GONE);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(CENTER_IN_PARENT);
        addView(mProgressView, params);
        addView(mEmptyView, params);
    }

    /**
     * 把居中的转圈和空态提示往上抬 inset/2，落回可视区中央。
     * <p>
     * 这个布局挂在 ViewPager 里，而内容区用的是 appbar 滚动行为：顶栏能整块滚走，
     * 于是内容区拿到的是整屏高度再往下偏移一个顶栏，底边其实垂到了屏幕外。
     * 直接 CENTER_IN_PARENT 居中的是那个垂出去的框，看起来就偏低——而且顶栏一变高
     * （分类行加载出来那下）圈还会当场往下跳一截。
     * <p>
     * padding 加在这两个视图自己身上而不是根布局上：根布局上的 padding 会连内容列表一起缩，
     * 把最后一行挤出可视区。撑高视图自己的外框，居中时内容就正好抬起 inset/2。
     */
    public void setBottomInset(int inset) {
        if (inset < 0 || mProgressView.getPaddingBottom() == inset) return;
        mProgressView.setPadding(0, 0, 0, inset);
        mEmptyView.setPadding(mEmptyView.getPaddingLeft(), 0, mEmptyView.getPaddingRight(), inset);
    }

    /**
     * 设置空态的文案和重试动作。拉不到内容多半是断网或源挂了，
     * 只丢一句"空谷待音"用户没法自救，得说清原因并给一个能点的按钮。
     *
     * @param retry 传 null 表示这次不是错误（比如真的没有数据），不显示按钮
     */
    public void setEmpty(CharSequence text, OnClickListener retry) {
        TextView view = mEmptyView.findViewById(com.fongmi.android.tv.R.id.text);
        if (view != null && !TextUtils.isEmpty(text)) view.setText(text);
        View button = mEmptyView.findViewById(com.fongmi.android.tv.R.id.retry);
        if (button == null) return;
        button.setVisibility(retry == null ? GONE : VISIBLE);
        button.setOnClickListener(retry);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (child.getTag() == null || !child.getTag().equals(TAG_PROGRESS)) {
            mContentViews.add(child);
        }
    }

    public void showProgress() {
        switchState(State.PROGRESS);
    }

    public void showEmpty() {
        switchState(State.EMPTY);
    }

    public void showContent() {
        switchState(State.CONTENT);
    }

    public void showContent(boolean flag, int size) {
        if (flag && size == 0) showEmpty();
        else showContent();
    }

    public boolean isProgress() {
        return mState == State.PROGRESS;
    }

    public boolean isContent() {
        return mState == State.CONTENT;
    }

    public boolean isEmpty() {
        return mState == State.EMPTY;
    }

    public void switchState(State state) {
        if (mState == state) return;
        mState = state;
        switch (state) {
            case CONTENT:
                mEmptyView.setVisibility(GONE);
                mProgressView.setVisibility(GONE);
                pauseLottieAnimation();
                setContentVisibility(true);
                break;
            case PROGRESS:
                mEmptyView.setVisibility(GONE);
                mProgressView.setVisibility(VISIBLE);
                pauseLottieAnimation();
                setContentVisibility(false);
                break;
            case EMPTY:
                mEmptyView.setVisibility(VISIBLE);
                mProgressView.setVisibility(GONE);
                playLottieAnimation();
                setContentVisibility(false);
                break;
        }
    }

    private void playLottieAnimation() {
        try {
            LottieAnimationView lottieView = mEmptyView.findViewById(com.fongmi.android.tv.R.id.lottieAnimation);
            if (lottieView != null) {
                lottieView.playAnimation();
            }
        } catch (Exception e) {
            // 忽略错误，保持兼容性
        }
    }

    private void pauseLottieAnimation() {
        try {
            LottieAnimationView lottieView = mEmptyView.findViewById(com.fongmi.android.tv.R.id.lottieAnimation);
            if (lottieView != null) {
                lottieView.pauseAnimation();
            }
        } catch (Exception e) {
            // 忽略错误，保持兼容性
        }
    }

    private void setContentVisibility(boolean visible) {
        for (View view : mContentViews) {
            if (visible) showView(view);
            else hideView(view);
        }
    }

    private void showView(View view) {
        view.setAlpha(0f);
        view.setVisibility(VISIBLE);
        view.animate().alpha(1f).setDuration(100);
    }

    private void hideView(View view) {
        view.setVisibility(GONE);
    }
}
