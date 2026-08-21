package com.fongmi.android.tv.ui.activity;

import com.github.catvod.utils.Logger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.media.AudioManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.CastMember;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.LinkMovement;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.CastUtil;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;
import com.github.catvod.utils.Trans;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

public class VideoActivity extends BaseActivity implements Clock.Callback, CustomKeyDownVod.Listener, TrackDialog.Listener, ControlDialog.Listener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ControlDialog mControlDialog;
    private QuickAdapter mQuickAdapter;
    private ParseAdapter mParseAdapter;
    private CustomKeyDownVod mKeyDown;
    private ExecutorService mExecutor;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private List<Dialog> mDialogs;
    private List<String> mBroken;
    private History mHistory;
    private Players mPlayers;
    private Vod mCurrentVod;  // 保存当前视频对象，用于演职人员跳转
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean useParse;
    private boolean redirect;
    private boolean rotate;
    private boolean castExpanded;
    private boolean contentExpanded;
    private float mHandleDown;
    private boolean mDragging;
    private boolean mPortraitLock;
    private int mVideoBase;
    private ValueAnimator mWidthAnimator;
    private boolean stop;
    private boolean lock;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mR5;
    private Runnable mHideGestureFeedback;
    private Clock mClock;
    private String tag;
    private PiP mPiP;
    private Handler mHandler;
    private Runnable mTimeUpdateRunnable;
    private BroadcastReceiver mBatteryReceiver;
    private BroadcastReceiver mScreenReceiver;
    private int mBatteryLevel = -1;
    private boolean mIsCharging = false;
    private boolean mPausedByScreen = false;
    private float mOriginalBrightness = -1f; // 保存原始亮度
    private AudioManager mAudioManager;

    public static void push(FragmentActivity activity, String text) {
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(activity, Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> start(activity, "push_agent", "file://" + path, name));
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void start(Activity activity, String url) {
        start(activity, "push_agent", url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        Intent intent = new Intent(activity, VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        return mFlagAdapter.getActivated();
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : Setting.getScale();
    }

    private boolean isReplay() {
        return Setting.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return ResUtil.isLand(this);
    }

    private boolean isPort() {
        return !isLand();
    }

    @Override
    protected boolean transparent() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(getId())) return;
        mBinding.swipeLayout.setRefreshing(true);
        getIntent().putExtras(intent);
        stopSearch();
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mKeyDown = CustomKeyDownVod.create(this, mBinding.exo);
        mFrameParams = mBinding.video.getLayoutParams();
        mBinding.progressLayout.showProgress();
        mBinding.swipeLayout.setEnabled(false);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mPlayers = Players.create(this);
        mDialogs = new ArrayList<>();
        mBroken = new ArrayList<>();
        mClock = Clock.create();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mR5 = () -> initSearch(mBinding.name.getText().toString(), false);
        mPiP = new PiP();
        checkDanmakuImg();
        setRecyclerView();
        setVideoView();
        setViewModel();
        showProgress();
        showDanmaku();
        checkId();
        mHandler = new Handler(Looper.getMainLooper());
        mHideGestureFeedback = () -> mBinding.widget.gestureFeedback.animate().alpha(0f).setDuration(150).withEndAction(() -> mBinding.widget.gestureFeedback.setVisibility(View.GONE)).start();
        initTimeBatteryUpdate();
    }

    private void initTimeBatteryUpdate() {
        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    
                    if (level != -1 && scale != -1) {
                        mBatteryLevel = (int) ((level / (float) scale) * 100);
                        mIsCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                      status == BatteryManager.BATTERY_STATUS_FULL);
                        updateTimeBattery();
                    }
                }
            }
        };

        // 屏幕开关监听 - 仅用于画中画模式下控制播放
        mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                
                // 只在画中画模式下处理屏幕开关
                if (isInPictureInPictureMode()) {
                    if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                        // 画中画模式下关屏，暂停播放
                        if (mPlayers.isPlaying()) {
                            onPaused();
                            mPausedByScreen = true;
                        }
                    } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                        // 画中画模式下开屏，恢复播放
                        if (mPausedByScreen) {
                            onPlay();
                            mPausedByScreen = false;
                        }
                    }
                }
            }
        };

        mTimeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimeBattery();
                mHandler.postDelayed(this, 30000);
            }
        };
    }

    private void updateTimeBattery() {
        TextView timeBattery = findViewById(R.id.time_battery);
        TextView batteryText = findViewById(R.id.battery_icon);
        android.widget.ImageView chargingIndicator = findViewById(R.id.charging_indicator);
        
        // 只在全屏模式下显示
        if (isFullscreen()) {
            // 更新时间
            if (timeBattery != null) {
                String time = DateFormat.getTimeFormat(this).format(System.currentTimeMillis());
                timeBattery.setText(time);
                timeBattery.setVisibility(View.VISIBLE);
            }
            
            // 更新充电图标
            if (chargingIndicator != null) {
                chargingIndicator.setVisibility(mIsCharging && mBatteryLevel >= 0 ? View.VISIBLE : View.GONE);
            }
            
            // 更新电池百分比文字
            if (batteryText != null && mBatteryLevel >= 0) {
                batteryText.setText(mBatteryLevel + "%");
                batteryText.setVisibility(View.VISIBLE);
            } else if (batteryText != null) {
                batteryText.setVisibility(View.GONE);
            }
        } else {
            if (timeBattery != null) {
                timeBattery.setVisibility(View.GONE);
            }
            if (batteryText != null) {
                batteryText.setVisibility(View.GONE);
            }
            if (chargingIndicator != null) {
                chargingIndicator.setVisibility(View.GONE);
            }
        }
    }

    private void startTimeBatteryUpdates() {
        registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        
        // 注册屏幕开关监听
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, screenFilter);
        
        updateTimeBattery();
        mHandler.post(mTimeUpdateRunnable);
    }

    private void stopTimeBatteryUpdates() {
        try {
            if (mBatteryReceiver != null) {
                unregisterReceiver(mBatteryReceiver);
            }
        } catch (Exception e) {
        }
        
        try {
            if (mScreenReceiver != null) {
                unregisterReceiver(mScreenReceiver);
            }
        } catch (Exception e) {
        }
        
        mHandler.removeCallbacks(mTimeUpdateRunnable);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.detailBack.setOnClickListener(view -> onBackPressed());
        mBinding.castExpand.setOnClickListener(view -> onCastExpand());
        mBinding.contentExpand.setOnClickListener(view -> onContent());
        mBinding.handle.setOnTouchListener(this::onHandleTouch);
        mBinding.handleLand.setOnTouchListener(this::onHandleTouch);
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.full.setOnClickListener(view -> onFull());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.pip.setOnClickListener(view -> enterPiP());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.back.setOnClickListener(view -> onFull());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.danmaku.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.loop.setOnClickListener(view -> onLoop());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.player.setOnClickListener(view -> onChoose());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.exit.setOnClickListener(view -> exitFullscreen());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
        mBinding.control.seek.setListener(mPlayers);
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setHasFixedSize(true);
        mBinding.quick.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.episode.setHasFixedSize(true);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.HORI));
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.control.parse.setHasFixedSize(true);
        mBinding.control.parse.setItemAnimator(null);
        mBinding.control.parse.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this, ViewType.DARK));
    }

    private void setVideoView() {
        mPlayers.init(mBinding.exo);
        PlaybackService.start(mPlayers);
        ExoUtil.setSubtitleView(mBinding.exo);
        mPlayers.setDanmakuView(mBinding.danmaku);
        mPlayers.setTag(tag = UUID.randomUUID().toString());
        applyOrientation();
        mBinding.control.action.decode.setText(mPlayers.getDecodeText());
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(getActivity(), view));
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setLayoutParams(mFrameParams);
        }
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(mPlayers.getDecodeText());
    }

    private void setScale(int scale) {
        mHistory.setScale(scale);
        mBinding.exo.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observeForever(mObserveDetail);
        mViewModel.player.observeForever(mObservePlayer);
        mViewModel.search.observeForever(mObserveSearch);
        mViewModel.episode.observe(this, episode -> {
            onItemClick(episode);
            hideSheet();
        });
    }

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", "push_agent").putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    private void getDetail() {
        mViewModel.detailContent(getKey(), getId());
    }

    private void getDetail(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getVodPic());
        getIntent().putExtra("id", item.getVodId());
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        mPlayers.reset();
        mPlayers.stop();
        getDetail();
    }

    private void setDetail(Result result) {
        mBinding.swipeLayout.setRefreshing(false);
        if (result.getList().isEmpty()) setEmpty(result.hasMsg());
        else setDetail(result.getList().get(0));
        // 只在有错误或重要消息时显示提示
        if (result.hasMsg() && result.getList().isEmpty()) {
            Notify.show(result.getMsg());
        }
    }

    private void setEmpty(boolean finish) {
        if (isFromCollect() || finish) {
            finish();
        } else if (getName().isEmpty()) {
            showEmpty();
        } else {
            mBinding.name.setText(getName());
            App.post(mR4, 10000);
            checkSearch(false);
        }
    }

    private void showEmpty() {
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
        stopSearch();
    }

    private void setDetail(Vod item) {
        mCurrentVod = item;  // 保存当前视频对象
        mBinding.progressLayout.showContent();
        mBinding.video.setTag(item.getVodPic(getPic()));
        mBinding.name.setText(item.getVodName(getName()));
        setText(mBinding.content, 0, Html.fromHtml(item.getVodContent()).toString());
        setCast(item);
        updateContentExpand();
        mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
        mFlagAdapter.addAll(item.getVodFlags());
        setMeta(item);
        setTags(item);
        setArtwork(item.getVodPic());
        App.removeCallbacks(mR4);
        checkHistory(item);
        checkFlag(item);
        checkKeepImg();
        checkQuick();
    }
    
    /**
     * 演职人员合并成一段：导演名后跟"（导演）"，再接演员，中间用斜杠分隔。
     * 默认最多两行，超出时右下角出现展开按钮。
     */
    private void setCast(Vod item) {
        List<CastMember> members = new ArrayList<>();
        String director = item.getVodDirector();
        String actor = item.getVodActor();
        if (director != null && !director.isEmpty()) members.addAll(CastUtil.parseCastMembers(Html.fromHtml(director).toString(), CastMember.CastType.DIRECTOR));
        int directors = members.size();
        if (actor != null && !actor.isEmpty()) members.addAll(CastUtil.parseCastMembers(Html.fromHtml(actor).toString(), CastMember.CastType.ACTOR));
        if (members.isEmpty()) {
            mBinding.castText.setVisibility(View.GONE);
            mBinding.castExpand.setVisibility(View.GONE);
            return;
        }
        SpannableStringBuilder span = new SpannableStringBuilder(getString(R.string.detail_cast));
        for (int i = 0; i < members.size(); i++) {
            CastMember member = members.get(i);
            int start = span.length();
            span.append(member.getName());
            span.setSpan(getCastClickSpan(member), start, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (i < directors) span.append(getString(R.string.detail_director_tag));
            if (i < members.size() - 1) span.append("  /  ");
        }
        castExpanded = false;
        mBinding.castText.setText(span, TextView.BufferType.SPANNABLE);
        mBinding.castText.setVisibility(View.VISIBLE);
        mBinding.castText.setLinkTextColor(getColor(R.color.text_primary));
        mBinding.castText.setMovementMethod(LinkMovement.getInstance());
        setExpandState(mBinding.castExpand, false);
        checkOverflow(mBinding.castText, mBinding.castExpand, 2);
    }

    private void onCastExpand() {
        castExpanded = !castExpanded;
        mBinding.castText.setMaxLines(castExpanded ? Integer.MAX_VALUE : 2);
        setExpandState(mBinding.castExpand, castExpanded);
    }

    private void updateContentExpand() {
        contentExpanded = false;
        setExpandState(mBinding.contentExpand, false);
        checkOverflow(mBinding.content, mBinding.contentExpand, 3);
    }

    private void setExpandState(TextView toggle, boolean expanded) {
        toggle.setText(expanded ? R.string.detail_collapse : R.string.detail_expand);
        toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, expanded ? R.drawable.ic_detail_collapse : R.drawable.ic_detail_expand, 0);
    }

    /**
     * 折叠状态下测量一次，只有真的被截断才显示展开按钮。
     * 必须等排版完成，所以放到 post 里跑。
     */
    private void checkOverflow(TextView text, TextView toggle, int collapsed) {
        text.setMaxLines(collapsed);
        toggle.setVisibility(View.GONE);
        text.post(() -> {
            Layout layout = text.getLayout();
            if (layout == null) return;
            boolean overflow = layout.getLineCount() > collapsed || layout.getEllipsisCount(Math.max(0, layout.getLineCount() - 1)) > 0;
            toggle.setVisibility(overflow ? View.VISIBLE : View.GONE);
        });
    }

    /**
     * 创建演员/导演点击事件
     */
    private ClickableSpan getCastClickSpan(CastMember member) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                CastWorksActivity.start(VideoActivity.this, member.getName(), member.getType());
            }
            
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);  // 移除下划线
            }
        };
    }

    private void setText(TextView view, int resId, String text) {
        view.setText(getSpan(resId, text), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
        view.setTag(text);
    }

    private SpannableStringBuilder getSpan(int resId, String text) {
        if (resId > 0) text = getString(resId, text);
        Map<String, String> map = new HashMap<>();
        Matcher m = Sniffer.CLICKER.matcher(text);
        while (m.find()) {
            String key = Trans.s2t(m.group(2)).trim();
            text = text.replace(m.group(), key);
            map.put(key, m.group(1));
        }
        SpannableStringBuilder span = new SpannableStringBuilder(text);
        for (String s : map.keySet()) {
            int index = text.indexOf(s);
            Result result = Result.type(map.get(s));
            span.setSpan(getClickSpan(result), index, index + s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private ClickableSpan getClickSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                FolderActivity.start(getActivity(), getKey(), result);
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
                setRedirect(true);
            }
        };
    }

    /**
     * 标题下方的一行摘要：年份 · 地区 · 更新状态 · 站点。
     * 年份只取前 4 位数字，站源常见的 "2019-01-18" 这类完整日期在这一行显得太长。
     */
    private void setMeta(Vod item) {
        List<String> parts = new ArrayList<>();
        String year = item.getVodYear().trim();
        if (year.length() >= 4 && TextUtils.isDigitsOnly(year.substring(0, 4))) year = year.substring(0, 4);
        if (!year.isEmpty()) parts.add(year);
        if (!item.getVodArea().trim().isEmpty()) parts.add(item.getVodArea().trim());
        if (!item.getVodRemarks().trim().isEmpty()) parts.add(item.getVodRemarks().trim());
        if (!getSite().getName().trim().isEmpty()) parts.add(getSite().getName().trim());
        mBinding.meta.setText(TextUtils.join("  ·  ", parts));
        mBinding.meta.setVisibility(parts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * 类型标签：把站源的分类字符串按常见分隔符拆开，逐个塞成灰色胶囊。
     */
    private void setTags(Vod item) {
        mBinding.tags.removeAllViews();
        String type = item.getTypeName().trim();
        List<String> tags = new ArrayList<>();
        if (!type.isEmpty()) for (String tag : type.split("[,，/、|]")) if (!tag.trim().isEmpty() && !tags.contains(tag.trim())) tags.add(tag.trim());
        for (String tag : tags) {
            TextView view = new TextView(this);
            view.setText(tag);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            view.setTextColor(getColor(R.color.text_secondary));
            view.setBackgroundResource(R.drawable.shape_detail_tag);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(ResUtil.dp2px(8));
            mBinding.tags.addView(view, params);
        }
        mBinding.tagScroll.setVisibility(tags.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void getPlayer(Flag flag, Episode episode, boolean replay) {
        mBinding.control.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mBinding.control.title.setSelected(true);
        updateHistory(episode, replay);
        showProgress();
        setMetadata();
    }

    private void setPlayer(Result result) {
        result.getUrl().set(mQualityAdapter.getPosition());
        if (!result.getDesc().isEmpty()) {
            setText(mBinding.content, R.string.detail_content, Html.fromHtml(result.getDesc()).toString());
            updateContentExpand();
        }
        setUseParse(VodConfig.hasParse() && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(result.getFlag())) || result.getJx() == 1));
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.setParseVisible(isUseParse());
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        mPlayers.start(result, isUseParse(), getSite().isChangeable() ? getSite().getTimeout() : -1);
        setQualityVisible(result.getUrl().isMulti());
        mBinding.swipeLayout.setRefreshing(false);
        mPlayers.setKey(getHistoryKey());
        mQualityAdapter.addAll(result);
    }

    @Override
    public void onItemClick(Flag item) {
        if (item.isActivated()) return;
        mFlagAdapter.setActivated(item);
        mBinding.flag.scrollToPosition(mFlagAdapter.getPosition());
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
        seamless(item);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mFlagAdapter.toggle(item);
        notifyItemChanged(mEpisodeAdapter);
        mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        onRefresh();
    }

    @Override
    public void onItemClick(Result result) {
        try {
            mPlayers.start(result, isUseParse(), getSite().isChangeable() ? getSite().getTimeout() : -1);
        } catch (Exception e) {
            ErrorEvent.extract(tag, e.getMessage());
            Logger.e("Error", e);
        }
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
        getDetail(item);
    }

    @Override
    public void onItemClick(Parse item) {
        setParse(item);
        onRefresh();
    }

    private void setParse(Parse item) {
        VodConfig.get().setParse(item);
        notifyItemChanged(mParseAdapter);
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.updateParse();
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.nextRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prevRoot.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.size() == 0 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(items.size() < 10 ? View.GONE : View.VISIBLE);
        mBinding.more.setText(getString(R.string.detail_episode_all, String.valueOf(items.size())));
        mEpisodeAdapter.addAll(items);
    }

    private void seamless(Flag flag) {
        Episode episode = flag.find(mHistory.getVodRemarks(), getMark().isEmpty());
        setQualityVisible(episode != null && episode.isActivated() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isActivated()) return;
        mHistory.setVodRemarks(episode.getName());
        onItemClick(episode);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        initSearch(name, false);
    }

    private void onMore() {
        Episode episode = getEpisode();
        EpisodeGridDialog dialog = EpisodeGridDialog.create()
                .reverse(mHistory.isRevSort())
                .episodes(mEpisodeAdapter.getItems());
        dialog.show(this);
    }

    private void onContent() {
        contentExpanded = !contentExpanded;
        mBinding.content.setMaxLines(contentExpanded ? Integer.MAX_VALUE : 3);
        setExpandState(mBinding.contentExpand, contentExpanded);
    }

    private void onReverse() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    private boolean onChange() {
        checkSearch(true);
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.content.getText().toString());
        return true;
    }

    private void onCast() {
        CastDialog.create().history(mHistory).video(CastVideo.get(mBinding.name.getText().toString(), mPlayers.getUrl(), mPlayers.getPosition())).fm(true).show(this);
    }

    private void onInfo() {
        InfoDialog.create(this).title(mBinding.control.title.getText()).headers(mPlayers.getHeaders()).url(mPlayers.getUrl()).show();
    }

    private void onFull() {
        setR1Callback();
        toggleFullscreen();
    }

    private void enterPiP() {
        // 手动触发画中画模式（force=true 不依赖后台播放设置）
        if (mPlayers == null || mPlayers.isEmpty()) return;
        if (mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) && !mPiP.isInMode(this)) {
            mPiP.enter(this, mPlayers.getVideoWidth(), mPlayers.getVideoHeight(), getScale(), true);
        }
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        RefreshEvent.keep();
        checkKeepImg();
    }

    private void checkPlay() {
        setR1Callback();
        if (mPlayers.isPlaying()) onPaused();
        else if (mPlayers.isEmpty()) onRefresh();
        else onPlay();
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        setR1Callback();
        Episode item = mEpisodeAdapter.getNext();
        if (!item.isActivated()) onItemClick(item);
        else if (notify) Notify.show(R.string.error_play_next);
    }

    private void checkPrev() {
        setR1Callback();
        Episode item = mEpisodeAdapter.getPrev();
        if (!item.isActivated()) onItemClick(item);
        else Notify.show(R.string.error_play_prev);
    }

    private void onSetting() {
        mControlDialog = ControlDialog.create().parent(mBinding).history(mHistory).player(mPlayers).parse(isUseParse()).show(this);
    }

    private void onLock() {
        setLock(!isLock());
        setRequestedOrientation(getLockOrient());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    /**
     * 详情卡片的把手：竖屏在卡片顶部往下拖，横屏在卡片左边缘往右拖。
     *
     * 竖屏拖动时视频跟着往下走，位移取卡片的一半 —— 卡片正好落到屏幕底部时，
     * 视频也正好停在屏幕竖直中央，接上竖屏全屏就没有跳变。
     */
    @SuppressLint("ClickableViewAccessibility")
    private boolean onHandleTouch(View view, MotionEvent event) {
        boolean land = isLand();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = true;
                mVideoBase = mBinding.video.getWidth();
                mHandleDown = land ? event.getRawX() : event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                // 被 DragSheetLayout 拦截进来时不会有 DOWN，第一帧就地取基准点
                if (!mDragging) {
                    mDragging = true;
                    mVideoBase = mBinding.video.getWidth();
                    mHandleDown = land ? event.getRawX() : event.getRawY();
                    return true;
                }
                dragSheet(land, Math.max(0, (land ? event.getRawX() : event.getRawY()) - mHandleDown));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mDragging) return false;
                mDragging = false;
                float distance = Math.max(0, (land ? event.getRawX() : event.getRawY()) - mHandleDown);
                if (distance > getSheetTravel(land) * 0.2f) slideOutSheet(land);
                else resetSheet(land);
                return true;
        }
        return false;
    }

    private int getSheetTravel(boolean land) {
        return land ? mBinding.swipeLayout.getWidth() : mBinding.swipeLayout.getHeight();
    }

    /**
     * 竖屏：视频在上、详情在下；横屏：视频在左、详情在右。
     * VideoActivity 声明了 configChanges="orientation"，转屏不会重建，
     * 所以只能自己改 LayoutParams，不能靠 layout-land 资源目录。
     */
    private void applyOrientation() {
        boolean land = isLand();
        RelativeLayout.LayoutParams video = (RelativeLayout.LayoutParams) mBinding.video.getLayoutParams();
        RelativeLayout.LayoutParams sheet = (RelativeLayout.LayoutParams) mBinding.swipeLayout.getLayoutParams();
        if (!(video.width == RelativeLayout.LayoutParams.MATCH_PARENT && video.height == RelativeLayout.LayoutParams.MATCH_PARENT)) {
            // 全屏时 video 被换成了一个新的 MATCH_PARENT 参数，这里不要把它当成正常态缓存
            mFrameParams = video;
        }
        if (land) {
            sheet.width = getSheetWidth();
            sheet.height = RelativeLayout.LayoutParams.MATCH_PARENT;
            sheet.removeRule(RelativeLayout.BELOW);
            sheet.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            sheet.addRule(RelativeLayout.ALIGN_PARENT_END);
            video.width = RelativeLayout.LayoutParams.MATCH_PARENT;
            video.height = RelativeLayout.LayoutParams.MATCH_PARENT;
            video.addRule(RelativeLayout.START_OF, R.id.swipeLayout);
            mBinding.progressLayout.setBackgroundResource(R.drawable.shape_detail_sheet_land);
        } else {
            sheet.width = RelativeLayout.LayoutParams.MATCH_PARENT;
            sheet.height = RelativeLayout.LayoutParams.MATCH_PARENT;
            sheet.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
            sheet.removeRule(RelativeLayout.ALIGN_PARENT_END);
            sheet.addRule(RelativeLayout.BELOW, R.id.video);
            video.width = RelativeLayout.LayoutParams.MATCH_PARENT;
            video.height = getVideoHeight();
            video.removeRule(RelativeLayout.START_OF);
            mBinding.progressLayout.setBackgroundResource(R.drawable.shape_detail_sheet);
        }
        setSheetPadding(land);
        mBinding.handleBar.setVisibility(land ? View.GONE : View.VISIBLE);
        mBinding.handleLand.setVisibility(land ? View.VISIBLE : View.GONE);
        mBinding.swipeLayout.setVisibility(View.VISIBLE);
        mBinding.video.setLayoutParams(video);
        mBinding.swipeLayout.setLayoutParams(sheet);
        mFrameParams = video;
        clearDrag();
    }

    /**
     * 竖屏视频区高度按屏宽算 16:9，而不是写死 220dp——那个值是照手机屏宽定的，
     * 放到平板上就成了顶部一条窄带，下面的详情卡片长得离谱。
     * 再夹一个屏高上限，避免超长屏上视频把整页占满。
     */
    private int getVideoHeight() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        return Math.min(width * 9 / 16, (int) (height * 0.55f));
    }

    /**
     * 横屏时卡片是贴着屏幕上下边缘的，内容不留白就会顶到最上面；
     * 左边还压着一根竖把手，也得给它让出位置。竖屏靠布局自身的间距即可。
     */
    private void setSheetPadding(boolean land) {
        int start = land ? ResUtil.dp2px(10) : 0;
        int vertical = land ? ResUtil.dp2px(22) : 0;
        mBinding.scroll.setClipToPadding(false);
        mBinding.scroll.setPadding(start, vertical, 0, vertical);
    }

    /**
     * 详情栏宽度按屏幕宽度取比例，再夹在一个合理区间里，
     * 免得写死 dp 后在窄屏平板上挤掉视频、在超宽屏上又显得空。
     */
    private int getSheetWidth() {
        int screen = getResources().getDisplayMetrics().widthPixels;
        return Math.max(ResUtil.dp2px(280), Math.min(ResUtil.dp2px(460), (int) (screen * 0.36f)));
    }

    private void clearDrag() {
        mDragging = false;
        if (mWidthAnimator != null) mWidthAnimator.cancel();
        mBinding.swipeLayout.animate().cancel();
        mBinding.video.animate().cancel();
        mBinding.handleLand.animate().cancel();
        mBinding.handleLand.setTranslationX(0);
        mBinding.swipeLayout.setTranslationX(0);
        mBinding.swipeLayout.setTranslationY(0);
        mBinding.video.setTranslationX(0);
        mBinding.video.setTranslationY(0);
    }

    private void dragSheet(boolean land, float moved) {
        float distance = Math.min(moved, getSheetTravel(land));
        if (land) {
            mBinding.swipeLayout.setTranslationX(distance);
            mBinding.handleLand.setTranslationX(distance);
            setVideoWidth(mVideoBase + (int) distance);
        } else {
            mBinding.swipeLayout.setTranslationY(distance);
            mBinding.video.setTranslationY(distance / 2);
        }
    }

    /**
     * 横屏下 video 同时锚了 alignParentStart 和 toStartOf(swipeLayout)，
     * 两端都被约束时 RelativeLayout 会忽略显式宽度 —— 必须先摘掉右锚点，
     * 显式宽度才生效。退出拖动由 applyOrientation() 把锚点加回去。
     */
    private void setVideoWidth(int width) {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBinding.video.getLayoutParams();
        if (params.width == width) return;
        params.removeRule(RelativeLayout.START_OF);
        params.width = width;
        mBinding.video.setLayoutParams(params);
    }

    private void resetSheet(boolean land) {
        mBinding.swipeLayout.animate().translationX(0).translationY(0).setDuration(180).start();
        mBinding.handleLand.animate().translationX(0).setDuration(180).start();
        if (land) animateVideoWidth(mVideoBase, 180, this::applyOrientation);
        else mBinding.video.animate().translationY(0).setDuration(180).start();
    }

    private void slideOutSheet(boolean land) {
        int target = getSheetTravel(land);
        if (land) {
            mBinding.swipeLayout.animate().translationX(target).setDuration(220).start();
            mBinding.handleLand.animate().translationX(target).setDuration(220).start();
            animateVideoWidth(mVideoBase + target, 220, () -> enterFullscreen(false));
        } else {
            mPortraitLock = true;
            mBinding.swipeLayout.animate().translationY(target).setDuration(220).start();
            mBinding.video.animate().translationY(target / 2f).setDuration(220).withEndAction(() -> enterFullscreen(true)).start();
        }
    }

    /** 横屏拖动结算：把视频宽度补到目标值，画面是连续放大的，接上全屏不会跳一下 */
    private void animateVideoWidth(int target, int duration, Runnable end) {
        if (mWidthAnimator != null) mWidthAnimator.cancel();
        mWidthAnimator = ValueAnimator.ofInt(mBinding.video.getWidth(), target);
        mWidthAnimator.setDuration(duration);
        mWidthAnimator.addUpdateListener(animation -> setVideoWidth((int) animation.getAnimatedValue()));
        if (end != null) mWidthAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                end.run();
            }
        });
        mWidthAnimator.start();
    }

    private void onRotate() {
        // 用户手动选过方向，之后就一直算数，不要再按片源比例自动转回去。
        // 原来这里清成 false，导致用旋转按钮切到竖屏全屏后，
        // 下一集的 SIZE 事件一到 checkOrientation 就把屏幕转回横屏。
        mPortraitLock = true;
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void onTrack(View view) {
        TrackDialog.create().player(mPlayers).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(mPlayers).show(this);
        hideControl();
    }

    private void onDanmakuShow() {
        Setting.putDanmakuShow(!Setting.isDanmakuShow());
        checkDanmakuImg();
        showDanmaku();
    }

    private void onLoop() {
        mBinding.control.action.loop.setActivated(!mBinding.control.action.loop.isActivated());
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else setScale(index == array.length - 1 ? 0 : ++index);
        setR1Callback();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(mPlayers.addSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        setR1Callback();
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(mPlayers.toggleSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        setR1Callback();
        return true;
    }

    private void onRefresh() {
        onReset(false);
    }

    private void onReset() {
        onReset(isReplay());
    }

    private void onReset(boolean replay) {
        mPlayers.stop();
        mPlayers.clear();
        mClock.setCallback(null);
        if (mFlagAdapter.isEmpty()) return;
        if (mEpisodeAdapter.isEmpty()) return;
        getPlayer(getFlag(), getEpisode(), replay);
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onDecode() {
        mPlayers.toggleDecode();
        setR1Callback();
        setDecode();
    }

    private void onEnding() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || duration < 0) return;
        if (duration - current > Constant.OPED_LIMIT) return;
        setEnding(duration - current);
        setR1Callback();
    }

    private boolean onEndingReset() {
        setR1Callback();
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mHistory.getEnding()));
    }

    private void onOpening() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || duration < 0) return;
        if (current > Constant.OPED_LIMIT) return;
        setOpening(current);
        setR1Callback();
    }

    private boolean onOpeningReset() {
        setR1Callback();
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
    }

    private void onEpisodes() {
        mDialogs.add(EpisodeListDialog.create(this).episodes(mEpisodeAdapter.getItems()).show());
    }

    private void onChoose() {
        mPlayers.choose(this, mBinding.control.title.getText());
        setRedirect(true);
    }

    private boolean onTextLong() {
        onSubtitleClick();
        return true;
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) getDetail();
        else onRefresh();
    }

    private void toggleFullscreen() {
        if (isFullscreen()) exitFullscreen();
        else enterFullscreen();
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isActivated();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        enterFullscreen(mPlayers.isPortrait());
    }

    private void enterFullscreen(boolean portrait) {
        if (isFullscreen()) return;
        clearDrag();
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        setRequestedOrientation(portrait ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        mBinding.control.full.setVisibility(View.GONE);
        mBinding.swipeLayout.setVisibility(View.GONE);
        mBinding.handleLand.setVisibility(View.GONE);
        mBinding.detailBack.setVisibility(View.GONE);
        setRotate(portrait, true);
        mPlayers.setDanmakuSize(1.0f);
        Util.hideSystemUI(this);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        hideControl();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        App.post(() -> mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition()), 50);
        mBinding.control.full.setVisibility(View.VISIBLE);
        mBinding.swipeLayout.setVisibility(View.VISIBLE);
        mBinding.swipeLayout.setTranslationX(0);
        mBinding.swipeLayout.setTranslationY(0);
        mBinding.video.setTranslationX(0);
        mBinding.video.setTranslationY(0);
        mPortraitLock = false;
        mBinding.detailBack.setVisibility(View.VISIBLE);
        mBinding.detailBack.setBackgroundResource(R.drawable.shape_detail_back);
        applyOrientation();
        mPlayers.setDanmakuSize(0.8f);
        setRotate(false, false);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        hideControl();
    }

    private int getLockOrient() {
        if (isLock()) {
            return ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        } else if (isRotate()) {
            return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        } else if (isPort() && isAutoRotate()) {
            return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        } else {
            return ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        }
    }

    private void showProgress() {
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.widget.progress.setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showDanmaku() {
        mBinding.danmaku.setVisibility(Setting.isDanmakuShow() ? View.VISIBLE : View.INVISIBLE);
    }

    private void hideDanmaku() {
        mBinding.danmaku.setVisibility(View.INVISIBLE);
    }

    private void showControl() {
        if (mPiP.isInMode(this)) return;
        mBinding.control.danmaku.setVisibility(isLock() || !mPlayers.haveDanmaku() ? View.GONE : View.VISIBLE);
        mBinding.control.setting.setVisibility(!isFullscreen() || mPlayers.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.right.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.keep.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        // 竖屏用悬浮返回键，这里留 INVISIBLE 只为把标题挤到返回键右边
        mBinding.control.back.setVisibility(isFullscreen() ? (isLock() ? View.GONE : View.VISIBLE) : View.INVISIBLE);
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(mPlayers.isEmpty() || !isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(mPlayers.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.pip.setVisibility(mPlayers.isEmpty() || PiP.noPiP() || !isFullscreen() ? View.GONE : View.VISIBLE);
        setActionVisible();
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        // 控制层出现时返回键去掉圆形底，避免和控制层的深色蒙版叠成两层
        if (!isFullscreen()) mBinding.detailBack.setBackground(null);
        updateTimeBattery();
        setR1Callback();
        checkPlayImg();
    }

    /**
     * 全屏动作条：内核选择和弹幕开关一律不出；竖屏全屏再收掉解码、轨道和选集，
     * 只留自动、倍速、原始、刷新、片头、片尾。
     */
    private void setActionVisible() {
        boolean land = isLand();
        mBinding.control.action.player.setVisibility(View.GONE);
        mBinding.control.action.danmaku.setVisibility(View.GONE);
        mBinding.control.action.decode.setVisibility(land ? View.VISIBLE : View.GONE);
        mBinding.control.action.exit.setVisibility(land && isFullscreen() ? View.VISIBLE : View.GONE);
        // 选集横竖屏都保留，只要不止一集
        mBinding.control.action.episodes.setVisibility(mEpisodeAdapter.getItemCount() < 2 ? View.GONE : View.VISIBLE);
        // 两个分支都要显式赋值：只写隐藏那一半的话，竖屏收起来的按钮转到横屏就再也回不来
        if (land) {
            setTrackVisible();
        } else {
            mBinding.control.action.text.setVisibility(View.GONE);
            mBinding.control.action.audio.setVisibility(View.GONE);
            mBinding.control.action.video.setVisibility(View.GONE);
        }
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        if (!isFullscreen()) mBinding.detailBack.setBackgroundResource(R.drawable.shape_detail_back);
        App.removeCallbacks(mR1);
    }

    private void hideSheet() {
        for (Dialog dialog : mDialogs) dialog.dismiss();
        for (Fragment fragment : getSupportFragmentManager().getFragments()) if (fragment instanceof DialogFragment) ((DialogFragment) fragment).dismiss();
        mDialogs.clear();
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        App.post(mR2, Constant.INTERVAL_TRAFFIC);
    }

    private void setOrient() {
        if (isPort() && isAutoRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        if (isLand() && isAutoRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setArtwork(String url) {
        ImgUtil.load(url, R.drawable.radio, new CustomTarget<>(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()) {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.exo.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable error) {
                mBinding.exo.setDefaultArtwork(error);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        });
    }

    private void checkFlag(Vod item) {
        boolean empty = item.getVodFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            ErrorEvent.flag(tag);
        } else {
            onItemClick(mHistory.getFlag());
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
    }

    private void checkHistory(Vod item) {
        mHistory = History.find(getHistoryKey());
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        if (Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) mHistory.delete();
        mBinding.control.action.opening.setText(mHistory.getOpening() <= 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
        mBinding.control.action.ending.setText(mHistory.getEnding() <= 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mHistory.getEnding()));
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
        mHistory.setVodPic(item.getVodPic());
        setScale(getScale());
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getVodName());
        history.findEpisode(item.getVodFlags());
        return history;
    }

    private void updateHistory(Episode item, boolean replay) {
        boolean firstSave = mHistory.getCreateTime() <= 0;
        replay = replay || !item.equals(mHistory.getEpisode());
        mHistory.setEpisodeUrl(item.getUrl());
        mHistory.setVodRemarks(item.getName());
        mHistory.setVodFlag(getFlag().getFlag());
        mHistory.setCreateTime(System.currentTimeMillis());
        mHistory.setPosition(replay ? C.TIME_UNSET : mHistory.getPosition());
        // Persist a new title before player preparation. This gives the first cloud upload
        // enough time to finish even when the user watches briefly and removes the task.
        if (firstSave && !Setting.isIncognito()) mHistory.update();
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkPlayImg() {
        mBinding.control.play.setImageResource(mPlayers.isPlaying() ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mPiP.update(this, mPlayers.isPlaying());
        ActionEvent.update();
    }

    private void checkKeepImg() {
        mBinding.control.keep.setImageResource(Keep.find(getHistoryKey()) == null ? R.drawable.ic_control_keep_off : R.drawable.ic_control_keep_on);
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void checkDanmakuImg() {
        mBinding.control.danmaku.setImageResource(Setting.isDanmakuShow() ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setSiteName(getSite().getName());
        keep.setVodPic(mBinding.video.getTag().toString());
        keep.setVodName(mBinding.name.getText().toString());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    @Override
    public void onSubtitleClick() {
        App.post(this::hideControl, 200);
        App.post(() -> SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).full(isFullscreen()).show(this), 200);
    }

    @Override
    public void onTimeChanged() {
        long position, duration;
        mHistory.setPosition(position = mPlayers.getPosition());
        mHistory.setDuration(duration = mPlayers.getDuration());
        if (position >= 0 && duration > 0 && !Setting.isIncognito()) App.execute(() -> mHistory.updateProgress());
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            checkEnded(false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (isRedirect()) return;
        ReceiveDialog.create().event(event).show(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (isRedirect()) return;
        if (ActionEvent.PLAY.equals(event.getAction()) || ActionEvent.PAUSE.equals(event.getAction())) {
            mBinding.control.play.performClick();
        } else if (ActionEvent.NEXT.equals(event.getAction())) {
            mBinding.control.next.performClick();
        } else if (ActionEvent.PREV.equals(event.getAction())) {
            mBinding.control.prev.performClick();
        } else if (ActionEvent.STOP.equals(event.getAction())) {
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) mPlayers.setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) mPlayers.setDanmaku(Danmaku.from(event.getPath()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerEvent(PlayerEvent event) {
        if (!event.getTag().equals(tag)) return;
        switch (event.getState()) {
            case PlayerEvent.PREPARE:
                setDecode();
                setPosition();
                break;
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                hideProgress();
                checkControl();
                checkPlayImg();
                mPlayers.reset();
                break;
            case Player.STATE_ENDED:
                checkEnded(true);
                break;
            case PlayerEvent.TRACK:
                setMetadata();
                setTrackVisible();
                mClock.setCallback(this);
                break;
            case PlayerEvent.SIZE:
                checkOrientation();
                mBinding.control.size.setText(mPlayers.getSizeText());
                break;
        }
    }

    private void setPosition() {
        if (mHistory != null) mPlayers.seekTo(Math.max(mHistory.getOpening(), mHistory.getPosition()));
    }

    private void checkOrientation() {
        // 用户是自己下拉进的竖屏全屏，切下一集时不能因为片源是横的又把屏幕转过去
        if (mPortraitLock) return;
        if (isFullscreen() && !isRotate() && mPlayers.isPortrait()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            setRotate(true);
        } else if (isFullscreen() && isRotate() && mPlayers.isLandscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
            setRotate(false);
        }
    }

    private void checkEnded(boolean notify) {
        if (mBinding.control.action.loop.isActivated()) {
            onReset(true);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            checkNext(notify);
            checkPlayImg();
            flushProgress();
        }
    }

    private void setTrackVisible() {
        mBinding.control.action.text.setVisibility(mPlayers.haveTrack(C.TRACK_TYPE_TEXT) || mPlayers.isVod() ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.setTrackVisible();
    }

    private void setMetadata() {
        String title = mHistory.getVodName();
        String episode = getEpisode().getName();
        String artist = title.equals(episode) ? "" : getString(R.string.play_now, episode);
        mPlayers.setMetadata(title, artist, mHistory.getVodPic(), mBinding.exo.getDefaultArtwork());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onErrorEvent(ErrorEvent event) {
        if (!event.getTag().equals(tag)) return;
        if (mPlayers.retried()) onError(event);
        else onRefresh();
    }

    private void onError(ErrorEvent event) {
        mBinding.swipeLayout.setEnabled(true);
        Track.delete(mPlayers.getUrl());
        showError(event.getMsg());
        mClock.setCallback(null);
        mPlayers.resetTrack();
        mPlayers.reset();
        mPlayers.stop();
        startFlow();
    }

    private void startFlow() {
        if (!getSite().isChangeable()) return;
        if (isUseParse()) checkParse();
        else checkFlag();
    }

    private void checkParse() {
        int position = mParseAdapter.getPosition();
        boolean last = position == mParseAdapter.getItemCount() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    private void initParse() {
        if (mParseAdapter.isEmpty()) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    /**
     * 播放失败时的兜底：列表里已经有别的源就直接换过去。
     * 现在详情页一进来就会主动搜，所以这里基本都走 nextSite 分支。
     */
    private void checkSearch(boolean force) {
        if (mQuickAdapter.isEmpty()) initSearch(mBinding.name.getText().toString(), true);
        else nextSite();
    }

    /**
     * 详情加载完主动搜一遍别的站点，把结果留在页面上供用户自己换源。
     * 用 auto=false，否则 setSearch 会立刻 nextSite 把结果消费掉，列表永远是空的。
     */
    private void checkQuick() {
        if (!mQuickAdapter.isEmpty() || mExecutor != null) return;
        App.post(mR5, 1000);
    }

    private void initSearch(String keyword, boolean auto) {
        stopSearch();
        setAutoMode(auto);
        setInitAuto(auto);
        startSearch(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickAdapter.clear();
        List<Site> sites = new ArrayList<>();
        mExecutor = Executors.newFixedThreadPool(20);
        for (Site item : VodConfig.get().getSites()) if (isPass(item)) sites.add(item);
        for (Site site : sites) mExecutor.execute(() -> search(site, keyword));
    }

    private void stopSearch() {
        App.removeCallbacks(mR5);
        if (mExecutor == null) return;
        mExecutor.shutdownNow();
        mExecutor = null;
    }

    private void search(Site site, String keyword) {
        try {
            mViewModel.searchContent(site, keyword, true);
        } catch (Throwable ignored) {
        }
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        Iterator<Vod> iterator = items.iterator();
        while (iterator.hasNext()) if (mismatch(iterator.next())) iterator.remove();
        mBinding.quick.setVisibility(View.VISIBLE);
        mBinding.quickText.setVisibility(View.VISIBLE);
        mQuickAdapter.addAll(items);
        if (isInitAuto()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    private boolean mismatch(Vod item) {
        if (getId().equals(item.getVodId())) return true;
        if (mBroken.contains(item.getVodId())) return true;
        String keyword = mBinding.name.getText().toString();
        if (isAutoMode()) return !item.getVodName().equals(keyword);
        else return !item.getVodName().contains(keyword);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        onItemClick(parse);
    }

    private void nextFlag(int position) {
        Flag flag = mFlagAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
        onItemClick(flag);
    }

    private void nextSite() {
        if (mQuickAdapter.isEmpty()) return;
        Vod item = mQuickAdapter.get(0);
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mQuickAdapter.remove(0);
        mBroken.add(getId());
        setInitAuto(false);
        getDetail(item);
    }

    private void onPaused() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPlayers.pause();
        checkPlayImg();
        flushProgress();
    }

    private void onPlay() {
        if (mHistory != null && mPlayers.isEnded()) mPlayers.seekTo(mHistory.getOpening());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!mPlayers.isEmpty() && mPlayers.isIdle()) mPlayers.prepare();
        mPlayers.play();
        checkPlayImg();
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, this.fullscreen = fullscreen);
    }

    private boolean isInitAuto() {
        return initAuto;
    }

    private void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    private boolean isAutoMode() {
        return autoMode;
    }

    private void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isRedirect() {
        return redirect;
    }

    public void setRedirect(boolean redirect) {
        this.redirect = redirect;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate, boolean fullscreen) {
        this.rotate = rotate;
        setFullscreen(fullscreen);
        if (!fullscreen || rotate) noPadding(mBinding.control.getRoot());
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (fullscreen && rotate) noPadding(mBinding.control.getRoot());
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
        // 检测屏幕方向变化并处理
        onOrientationChanged();
    }
    
    // 添加屏幕方向变化处理方法
    private void onOrientationChanged() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 切换到横屏模式
            onLandscapeMode();
        } else {
            // 切换到竖屏模式
            onPortraitMode();
        }
    }
    
    private void onLandscapeMode() {
        // 横屏模式下的特殊处理
        // 调整进度条的敏感度
        if (mPlayers != null) {
            long duration = mPlayers.getDuration();
            if (duration > TimeUnit.MINUTES.toMillis(30)) {
                mBinding.control.seek.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
            } else if (duration > TimeUnit.MINUTES.toMillis(10)) {
                mBinding.control.seek.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(30));
            } else if (duration > 0) {
                mBinding.control.seek.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(15));
            }
        }
        
        // 确保进度条状态正确
        if (mPlayers != null) {
            long position = mPlayers.getPosition();
            long duration = mPlayers.getDuration();
            if (position > 0 && duration > 0) {
                mBinding.control.seek.setPosition(position);
                mBinding.control.seek.setDuration(duration);
            }
        }
    }
    
    private void onPortraitMode() {
        // 竖屏模式下的处理
        // 恢复进度条的默认敏感度
        if (mPlayers != null) {
            long duration = mPlayers.getDuration();
            if (duration > 0) {
                mBinding.control.seek.setKeyTimeIncrement(duration);
            }
        }
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public boolean isLock() {
        return lock;
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    private void notifyItemChanged(RecyclerView.Adapter<?> adapter) {
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    @Override
    public void onCasted() {
        onPaused();
    }

    @Override
    public void onScale(int tag) {
        mKeyDown.resetScale();
        setScale(tag);
    }

    @Override
    public void onParse(Parse item) {
        onItemClick(item);
    }

    @Override
    public void onSpeedUp() {
        if (!mPlayers.isPlaying()) return;
        mBinding.control.action.speed.setText(mPlayers.setSpeed(Setting.getSpeed()));
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.widget.speed.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onBrightEnd() {
        mBinding.widget.bright.setVisibility(View.GONE);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onVolumeEnd() {
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    @Override
    public void onFlingUp() {
        checkNext();
    }

    @Override
    public void onFlingDown() {
        checkPrev();
    }

    @Override
    public void onSeek(long time) {
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(mPlayers.getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        handleLandscapeSeek(time);
    }
    
    // 添加新的方法，处理横屏模式下的特殊逻辑
    private void handleLandscapeSeek(long time) {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏模式下的特殊处理
            mBinding.widget.seek.setVisibility(View.GONE);
            mPlayers.pause();
            mPlayers.seek(time);
            showProgress();
            App.post(() -> {
                long actualPosition = mPlayers.getPosition();
                if (Math.abs(actualPosition - time) > 500) {
                    mPlayers.seek(time);
                }
                onPlay();
                hideProgress();
            }, 150); // 横屏模式下延迟更长，确保跳转完成
        } else {
            // 竖屏模式使用原有逻辑
            mBinding.widget.seek.setVisibility(View.GONE);
            mPlayers.pause();
            mPlayers.seek(time);
            showProgress();
            App.post(() -> {
                long actualPosition = mPlayers.getPosition();
                if (Math.abs(actualPosition - time) > 500) {
                    mPlayers.seek(time);
                }
                onPlay();
                hideProgress();
            }, 100); // 竖屏模式下延迟较短
        }
    }

    @Override
    public void onSingleTap() {
        // 单击只切换控制栏显示/隐藏，播放暂停只响应中间按钮点击
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else {
            showControl();
        }
    }

    @Override
    public void onDoubleTap() {
        if (mPlayers.isEmpty()) {
            checkPlay();
            return;
        }
        boolean wasPlaying = mPlayers.isPlaying();
        checkPlay();
        showGestureFeedback(wasPlaying ? R.drawable.exo_icon_play : R.drawable.exo_icon_pause);
    }

    @Override
    public void onDoubleTapLeft() {
        long seekTime = -TimeUnit.SECONDS.toMillis(Setting.getGestureSeekSeconds());
        long newPosition = Math.max(0, mPlayers.getPosition() + seekTime);
        mPlayers.seekTo(newPosition);
        onSeek(seekTime);
        App.post(() -> mBinding.widget.seek.setVisibility(View.GONE), 800);
    }

    @Override
    public void onDoubleTapRight() {
        long seekTime = TimeUnit.SECONDS.toMillis(Setting.getGestureSeekSeconds());
        long duration = mPlayers.getDuration();
        long newPosition = Math.min(duration > 0 ? duration : Long.MAX_VALUE, mPlayers.getPosition() + seekTime);
        mPlayers.seekTo(newPosition);
        onSeek(seekTime);
        App.post(() -> mBinding.widget.seek.setVisibility(View.GONE), 800);
    }

    private void showGestureFeedback(int icon) {
        mHandler.removeCallbacks(mHideGestureFeedback);
        mBinding.widget.gestureFeedback.animate().cancel();
        mBinding.widget.gestureFeedback.setImageResource(icon);
        mBinding.widget.gestureFeedback.setVisibility(View.VISIBLE);
        mBinding.widget.gestureFeedback.setAlpha(0f);
        mBinding.widget.gestureFeedback.setScaleX(0.8f);
        mBinding.widget.gestureFeedback.setScaleY(0.8f);
        mBinding.widget.gestureFeedback.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();
        mHandler.postDelayed(mHideGestureFeedback, 500);
    }

    @Override
    public void onShare(CharSequence title) {
        mPlayers.share(this, title);
        setRedirect(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) mPlayers.checkData(data);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isRedirect()) return;
        if (isLock()) App.post(this::onLock, 500);
        if (mPlayers.haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, mPlayers.getVideoWidth(), mPlayers.getVideoHeight(), getScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (!isFullscreen()) setVideoView(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            hideControl();
            hideDanmaku();
            hideSheet();
        } else {
            // 退出画中画模式时，重置屏幕暂停标志
            mPausedByScreen = false;
            showDanmaku();
            if (isStop()) finish();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!isFullscreen()) applyOrientation();
        if (isFullscreen()) Util.hideSystemUI(this);
        // 转屏后动作条要按新方向重算，否则一直停在进入时那一套
        if (isVisible(mBinding.control.getRoot())) showControl();
        else setActionVisible();
        updateTimeBattery();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        setStop(false);
        onPlay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startTimeBatteryUpdates();
        if (isRedirect()) onPlay();
        setRedirect(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimeBatteryUpdates();
        if (isRedirect()) onPaused();
    }

    @Override
    protected void onStop() {
        super.onStop();
        flushProgress();
        if (Setting.isBackgroundOff()) onPaused();
        if (Setting.isBackgroundOff()) mClock.stop();
        setStop(true);
    }

    /** 暂停 / 播完 / 退出播放器时把最新进度落库并立刻上传，静默无提示。 */
    private void flushProgress() {
        savePlaybackProgress();
        com.fongmi.android.tv.utils.WebDAVSyncManager.get().flushPendingSync();
    }

    private void savePlaybackProgress() {
        if (mHistory == null || mHistory.getCreateTime() <= 0 || Setting.isIncognito()) return;
        if (mPlayers != null && !mPlayers.isEmpty()) {
            long position = mPlayers.getPosition();
            long duration = mPlayers.getDuration();
            if (position >= 0) mHistory.setPosition(position);
            if (duration > 0) mHistory.setDuration(duration);
        }
        mHistory.update();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 只在视频播放时处理键盘事件
        if (mPlayers != null && !mPlayers.isEmpty()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (mPlayers.isPlaying() || mPlayers.getPosition() > 0) {
                        long currentPosition = mPlayers.getPosition();
                        long seekTime = -TimeUnit.SECONDS.toMillis(Setting.getGestureSeekSeconds());
                        long newPosition = Math.max(0, currentPosition + seekTime);
                        mPlayers.seekTo(newPosition);
                        // 显示快退提示
                        onSeek(seekTime);
                        App.post(() -> {
                            mBinding.widget.seek.setVisibility(View.GONE);
                        }, 1000);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (mPlayers.isPlaying() || mPlayers.getPosition() > 0) {
                        long currentPosition = mPlayers.getPosition();
                        long duration = mPlayers.getDuration();
                        long seekTime = TimeUnit.SECONDS.toMillis(Setting.getGestureSeekSeconds());
                        long newPosition = Math.min(duration > 0 ? duration : Long.MAX_VALUE, currentPosition + seekTime);
                        mPlayers.seekTo(newPosition);
                        // 显示快进提示
                        onSeek(seekTime);
                        App.post(() -> {
                            mBinding.widget.seek.setVisibility(View.GONE);
                        }, 1000);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    // 上方向键：增加音量
                    if (mAudioManager != null) {
                        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int newVolume = Math.min(maxVolume, currentVolume + 1);
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                        onVolume((int) (newVolume * 100.0f / maxVolume));
                        App.post(() -> onVolumeEnd(), 1000);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // 下方向键：减少音量
                    if (mAudioManager != null) {
                        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int newVolume = Math.max(0, currentVolume - 1);
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                        onVolume((int) (newVolume * 100.0f / maxVolume));
                        App.post(() -> onVolumeEnd(), 1000);
                        return true;
                    }
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (!isLock()) {
            stopSearch();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSearch();
        mPlayers.release();
        mClock.release();
        Timer.get().reset();
        RefreshEvent.history();
        PlaybackService.stop();
        mHandler.removeCallbacksAndMessages(null);
        App.removeCallbacks(mR1, mR2, mR3, mR4, mR5);
        EventBus.getDefault().unregister(this);
        mViewModel.result.removeObserver(mObserveDetail);
        mViewModel.player.removeObserver(mObservePlayer);
        mViewModel.search.removeObserver(mObserveSearch);
        stopTimeBatteryUpdates();
    }
}
