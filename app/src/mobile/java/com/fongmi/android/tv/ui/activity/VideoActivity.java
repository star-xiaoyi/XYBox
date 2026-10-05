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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.media.AudioManager;
import android.widget.FrameLayout;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.Douban;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.CastMember;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Download;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.server.process.Pc;
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
import com.fongmi.android.tv.download.DownloadManager;
import com.fongmi.android.tv.download.OfflinePlayback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.player.PreviewPlayer;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.exo.PlaybackCache;
import com.fongmi.android.tv.player.exo.TrackNameProvider;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.search.GroupCache;
import com.fongmi.android.tv.search.PlaybackRoutePolicy;
import com.fongmi.android.tv.search.SearchTask;
import com.fongmi.android.tv.search.TitleKey;
import com.fongmi.android.tv.search.VodGroup;
import com.fongmi.android.tv.search.VodSource;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.RecommendAdapter;
import com.fongmi.android.tv.ui.adapter.SourceAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.LinkMovement;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuSettingsDialog;
import com.fongmi.android.tv.ui.dialog.DownloadEpisodeDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.CastManager;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoActivity extends BaseActivity implements Clock.Callback, CustomKeyDownVod.Listener, CustomSeekView.ScrubListener, PreviewPlayer.Callback, TrackDialog.Listener, ControlDialog.Listener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, SourceAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener, CastManager.Listener {

    /** 长按倍速那对箭头走完一个来回的毫秒数，也就是没锁定时的最快速度。 */
    private static final int SPEED_CYCLE = 700;
    private static final long BUFFERING_PROGRESS_DELAY_MS = 800;
    /** 竖屏全屏时，将画面中心固定在人眼更自然的、比屏幕几何中心高 28dp 的位置。 */
    private static final int PORTRAIT_VIEWING_CENTER_OFFSET_DP = 28;
    private static final int PLAYER_PANEL_MAX_HEIGHT_DP = 320;
    private static final Pattern RESOLUTION_PATTERN = Pattern.compile("(\\d{3,5})\\s*[xX×]\\s*(\\d{3,5})");
    private static final long MIN_AUTO_SWITCH_TIMEOUT = TimeUnit.SECONDS.toMillis(8);
    private static final int MORE_INFO = 1001;
    private static final int MORE_DANMAKU = 1002;
    private static final int MORE_TIMER = 1003;
    private static final int MORE_SCALE = 1005;
    private static final int MORE_SPEED = 1006;
    private static final int MORE_TEXT = 1007;
    private static final int MORE_AUDIO = 1008;
    private static final int MORE_VIDEO = 1009;
    private static final int MORE_PIP = 1011;
    private static final int MORE_KEEP = 1012;
    private static final int MORE_EXIT = 1014;
    private static final int MORE_EPISODES = 1015;
    private static final int MORE_ENDING = 1016;

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ControlDialog mControlDialog;
    private SourceAdapter mSourceAdapter;
    private RecommendAdapter mRelatedAdapter;
    private ParseAdapter mParseAdapter;
    private CustomKeyDownVod mKeyDown;
    private PreviewPlayer mPreview;
    private PlaybackCache mPlaybackCache;
    private Runnable mCacheWarmup;
    private Runnable mSpeedTick;
    private Runnable mShowBufferingProgress;
    private boolean mScrubPlaying;
    private boolean mScrubbing;
    private boolean mGestureSeekPlaying;
    private boolean mGestureSeeking;
    private long mGestureSeekBasePosition;
    private long mGestureSeekPosition;
    private float mPreviewRatio = 16f / 9f;
    private boolean mBufferingProgressPending;
    private long mBufferingProgressStartedAt;
    private String mBufferingProgressReason;
    private boolean mBrightnessAdjusting;
    private boolean mVolumeAdjusting;
    private int mLeftControlsVisibility;
    private int mRightControlsVisibility;
    private float mSpeedProgress;
    private float mSpeedPhase;
    private long mSpeedTime;
    private SearchTask mSourceTask;
    private boolean mSourceBackground;
    private final Runnable mQualityMaintenance = this::maintainQualityWork;
    private final Runnable mPlaybackWatchdog = this::watchPlayback;
    private final Runnable mFinishInitialSelection = this::finishInitialSelection;
    private com.fongmi.android.tv.search.PlaybackPolicy mPlaybackPolicy = new com.fongmi.android.tv.search.PlaybackPolicy();
    private History mFilmIdentity;
    private String mPlaybackEpisode = "", mSessionId = Long.toHexString(System.nanoTime()), mEntry = "detail";
    private boolean mPlaybackWanted = true, mRecoveryPending, mRecoveryManual, mInitialSelection, mInitialSelectionDone;
    private String mRecoveryReason = "fallback";
    private Flag mInitialFlag;
    private int mAutoWidth, mAutoHeight, mAutomaticSwitches, mManualSwitches;
    private long mLastHealthLog;
    private long mSourceProbeUntil, mCurrentStartupMs = -1;
    private View mSourceAnchor;
    /** 用户是否明确点了某个片源；自动流程绝不进入只有网盘线路的源。 */
    private boolean mManualSourceSelection;
    private int mRatingGeneration;
    private int mRatingAttempts;
    private String mRatingKey = "";
    private long mRatingRetryAt;
    private List<String> mDoubanGenres = new ArrayList<>();
    private Douban.Subject mDoubanSubject;
    private Vod mMetadataVod;
    private String mMetadataRenderKey = "";
    /** 片源比对的目标：规整后的片名、年份、片种，详情加载后以详情为准。 */
    private String mTargetKey = "";
    private int mTargetYear;
    private int mTargetKind;
    /** 有目标年份时，开搜后这个时刻之前自动选源只认年份对得上的。 */
    private long mStrictUntil;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private List<Dialog> mDialogs;
    private History mHistory;
    private History mPendingCloudHistory;
    private History mPendingResumeHistory;
    private com.fongmi.android.tv.search.QualityCatalog mQualityCatalog;
    private com.fongmi.android.tv.search.QualityOption mSelectedQuality;
    private boolean mAutomaticQuality = true;
    private int mQualityHeight;
    private boolean mQualityPreferenceLoaded, mQualityRestorePending;
    private View mQualityAnchor;
    private boolean mDoubanLoading;
    private long mCloudResumePosition = -1;
    /** 当前选中的剧集已经真正进入可播放状态，避免失败片源覆盖或合并掉旧记录。 */
    private boolean mHistoryPlaybackConfirmed;
    private final Object mHistoryWriteLock = new Object();
    private volatile long mKnownHistoryVersion;
    private volatile int mHistoryWriteGeneration;
    private long mLastHistoryCapture;
    private volatile boolean mAwaitingCloudSync;
    private volatile boolean mCloudChoicePending;
    private volatile boolean mSuppressHistorySaves;
    private boolean mResumeAfterCloudSync;
    private androidx.appcompat.app.AlertDialog mCloudProgressDialog;
    private Players mPlayers;
    private Vod mCurrentVod;  // 保存当前视频对象，用于演职人员跳转
    private final Runnable mDoubanRetry = () -> {
        if (mCurrentVod != null && !isFinishing() && !isDestroyed())
            loadDoubanDetails(metadataName(mCurrentVod), metadataYear(mCurrentVod));
    };
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean useParse;
    private boolean redirect;
    private boolean rotate;
    private boolean castExpanded;
    /** 投屏中电视是否已经放到本集末尾，避免自动切下一集被轮询触发多次。 */
    private boolean castEnded;
    /**
     * 当前推给接收端的是哪一集。
     * <p>
     * 换集是否在途由它和 {@link #getEpisode()} 比出来，而不是靠某个地方记得去置标记——
     * 切集的入口有好几个（点剧集、上下集、自动下一集、电脑选集），走的还不是同一条路，
     * 靠标记必然漏。
     */
    private Episode castEpisode;
    /** 最后一次推给接收端的播放地址，配合 castEpisode 用来识别重复推送。 */
    private String castUrl;
    private boolean contentExpanded;
    private float mHandleDown;
    private boolean mDragging;
    private boolean mPortraitLock;
    private final java.util.Set<String> mCachedKeys = new java.util.HashSet<>();
    private final java.util.Set<String> mFailedLocalPaths = new java.util.HashSet<>();
    private Download mPlayingDownload;
    private boolean mLocalDetail;
    private boolean mRefreshingLocalDetail;
    private int mLocalDetailGeneration;
    private long mDetailRequestedAt, mPlaybackRequestedAt;
    private boolean mLoggedPlaybackReady;
    private int mTransitionTrace;
    private int mVideoBase;
    private int mStatusBarInset;
    private ValueAnimator mWidthAnimator;
    private boolean stop;
    private boolean lock;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mR5;
    private Runnable mR6;
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
        resume(activity, history);
    }

    /**
     * 从离线缓存进来：仍然用原站源的 key/vodId 打开详情页，观看记录才不会被离线记录挤掉；
     * 缓存直接参与普通剧集播放，入口标记只用于优先选择已缓存的一集。
     */
    public static void download(Activity activity, Download.Group group) {
        Intent intent = new Intent(activity, VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("offline", true);
        intent.putExtra("offline_group", group.getKey());
        intent.putExtra("name", group.getVodName());
        intent.putExtra("pic", group.getVodPic());
        intent.putExtra("key", group.getSiteKey());
        intent.putExtra("id", group.getVodId());
        activity.startActivity(intent);
    }

    private boolean isOffline() {
        return getIntent().getBooleanExtra("offline", false);
    }

    /**
     * 搜索组是候选集合；作品和续看进度先固定，再由播放决策选择可用来源。
     */
    public static void group(Activity activity, VodGroup group) {
        Vod vod = group.best().getVod();
        History hint = new History(); hint.setVodName(group.getName()); hint.setVodYear(group.getYear() == 0 ? "" : String.valueOf(group.getYear())); hint.setVodType(vod.getTypeName());
        History resume = null;
        for (History record : hint.find()) resume = com.fongmi.android.tv.search.HistoryIdentity.prefer(resume, record);
        Intent intent = new Intent(activity, VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("group", GroupCache.put(group));
        intent.putExtra("name", group.getName());
        intent.putExtra("pic", vod.getVodPic().isEmpty() ? group.getPic() : vod.getVodPic());
        intent.putExtra("key", vod.getSiteKey());
        intent.putExtra("id", vod.getVodId());
        intent.putExtra("entry", "search").putExtra("year", hint.getVodYear()).putExtra("type", hint.getVodType());
        if (resume != null) {
            intent.putExtra("resumeHistory", resume.toString());
            Site saved = VodConfig.get().getSite(resume.getCid(), resume.getSiteKey());
            if (!saved.isEmpty()) intent.putExtra("key", saved.getKey()).putExtra("id", resume.getVodId());
        }
        activity.startActivity(intent);
    }

    /**
     * 只知道片名（首页推荐、豆瓣榜单、配置已删的收藏）：进来后按片名搜全部站点，
     * 挑同名同年、接口最快的源直接播。year 可以为空，有的话用来区分同名翻拍。
     */
    public static void find(Activity activity, String name, String pic, String year) {
        find(activity, name, pic, year, 0);
    }

    public static void find(Activity activity, String name, String pic, String year, double rating) {
        Intent intent = new Intent(activity, VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("year", year);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("rating", rating);
        intent.putExtra("entry", "discovery");
        intent.putExtra("key", "");
        intent.putExtra("id", "msearch:" + name);
        activity.startActivity(intent);
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

    public static void resume(Activity activity, History history) {
        Site site = VodConfig.get().getSite(history.getCid(), history.getSiteKey());
        String key = site.isEmpty() ? history.getSiteKey() : site.getKey();
        Intent intent = new Intent(activity, VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("key", key).putExtra("id", history.getVodId()).putExtra("name", history.getVodName())
                .putExtra("pic", history.getVodPic()).putExtra("year", history.getVodYear()).putExtra("type", history.getVodType())
                .putExtra("entry", "history").putExtra("resumeHistory", history.toString());
        activity.startActivity(intent);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        Intent intent = new Intent(activity, VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
        int cid = getSite().getSourceId() > 0 ? getSite().getSourceId() : VodConfig.getCid();
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + cid;
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

    private String getYear() {
        return Objects.toString(getIntent().getStringExtra("year"), "");
    }

    private String getGroupToken() {
        return getIntent().getStringExtra("group");
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

    /** Dynamic source packages may try to attach their own progress bars or buttons to the player window. */
    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        if (mBinding != null) {
            Logger.i("PlayerOverlay: blocked-added-view class=" + (view == null ? "null" : view.getClass().getName()));
            return;
        }
        super.addContentView(view, params);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        String key = Objects.toString(intent.getStringExtra("key"), "");
        if (TextUtils.isEmpty(id) || id.equals(getId()) && key.equals(getKey())) return;
        flushProgress();
        mBinding.swipeLayout.setRefreshing(true);
        // putExtras 只覆盖不删除：新入口没带的片源组和年份不能沿用上一部片的
        GroupCache.remove(getGroupToken());
        getIntent().removeExtra("group");
        getIntent().removeExtra("year");
        getIntent().removeExtra("type"); getIntent().removeExtra("entry"); getIntent().removeExtra("resumeHistory");
        getIntent().removeExtra("offline");
        getIntent().removeExtra("offline_group");
        getIntent().removeExtra("mark");
        getIntent().putExtras(intent);
        resetSources();
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        com.fongmi.android.tv.utils.ToastFilter.enterPlayback(this);
        com.fongmi.android.tv.utils.SourceUiGuard.protect(mBinding.video, "player");
        getWindow().setBackgroundDrawableResource(R.color.black);
        mBinding.exo.setBackgroundColor(Color.BLACK);
        mBinding.exo.setShutterBackgroundColor(Color.BLACK);
        showDetailSystemUI();
        applyDetailWindowInsets();
        mKeyDown = CustomKeyDownVod.create(this, mBinding.exo);
        mPreview = new PreviewPlayer();
        mFrameParams = mBinding.video.getLayoutParams();
        mBinding.swipeLayout.setEnabled(false);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mPlayers = Players.create(this);
        mPlaybackCache = new PlaybackCache(percent -> mBinding.control.seek.setSessionCachedPercent(percent));
        mCacheWarmup = this::startPlaybackCache;
        mDialogs = new ArrayList<>();
        mClock = Clock.create();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mR5 = () -> startSourceSearch(false);
        mR6 = this::checkAutoSwitch;
        mPiP = new PiP();
        checkDanmakuImg();
        setRecyclerView();
        setVideoView();
        setViewModel();
        showDanmaku();
        checkId();
        mHandler = new Handler(Looper.getMainLooper());
        mSpeedTick = this::tickSpeedIcon;
        mShowBufferingProgress = () -> {
            if (!mBufferingProgressPending) return;
            mBufferingProgressPending = false;
            if (!mScrubbing && !mPlayers.isReady()) {
                Logger.i("PlayerUI: spinner=show reason=" + mBufferingProgressReason
                        + " elapsedMs=" + (SystemClock.uptimeMillis() - mBufferingProgressStartedAt));
                showProgress();
            }
        };
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
        mBinding.detailBack.setOnClickListener(view -> dispatchBack());
        mBinding.castExpand.setOnClickListener(view -> onCastExpand());
        mBinding.castExit.setOnClickListener(view -> onCastExit());
        // 投屏占位层盖住了播放手势区，把触摸原样转给同一套手势识别，
        // 这样投屏时照样能滑动调进度、双击快退快进、上下拖换集
        mBinding.castOverlay.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(view, event));
        mBinding.contentExpand.setOnClickListener(view -> onContent());
        mBinding.handle.setOnTouchListener(this::onHandleTouch);
        mBinding.handleLand.setOnTouchListener(this::onHandleTouch);
        mBinding.name.setOnClickListener(view -> mBinding.name.replayOnce());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.download.setOnClickListener(view -> onDownload());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.full.setOnClickListener(view -> onFull());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.detailPlay.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.playerMore.setOnClickListener(this::onPlayerMore);
        mBinding.control.pip.setOnClickListener(view -> enterPiP());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.back.setOnClickListener(view -> onPlayerBack());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.danmaku.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.scale.setOnClickListener(this::onScale);
        mBinding.control.action.speed.setOnClickListener(this::onSpeed);
        mBinding.control.action.reset.setOnClickListener(this::showSourcePanel);
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.exit.setOnClickListener(view -> exitFullscreen());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(view, event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
        mBinding.control.seek.setListener(mPlayers);
        mBinding.control.seek.setScrubListener(this);
        mBinding.playbackPanel.setOnPanelDismissListener(() -> {
            mQualityAnchor = null;
            mSourceAnchor = null;
            setR1Callback();
            refreshBackHandling();
        });
        mBinding.playbackPanel.setOnVisibilityChangedListener(visible -> refreshBackHandling());
        mPreview.attach(mBinding.control.previewVideo, this);
        // 倍速锁定只能点这个胶囊解除，点画面其它地方仍然是开关控制栏
        mBinding.widget.speedLock.setOnClickListener(v -> mKeyDown.unlockSpeed());
        mBinding.widget.screenRestore.setOnClickListener(v -> mKeyDown.resetScale());
    }

    private void setRecyclerView() {
        mFlagAdapter = new FlagAdapter(this);
        mSourceAdapter = new SourceAdapter(this);
        mBinding.related.setHasFixedSize(true);
        mBinding.related.setItemAnimator(null);
        mBinding.related.setAdapter(mRelatedAdapter = new RecommendAdapter(item -> VideoActivity.find(this, item.getTitle(), item.getPic(), item.getYear(), item.getRating())));
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
        ExoUtil.setSubtitleView(mBinding.exo);
        mPlayers.setDanmakuView(mBinding.danmaku);
        mPlayers.setTag(tag = UUID.randomUUID().toString());
        applyOrientation();
        mBinding.control.action.reset.setText(R.string.play_change_source);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(getActivity(), view));
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setLayoutParams(mFrameParams);
        }
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
        mViewModel.episode.observe(this, episode -> {
            onItemClick(episode);
            hideSheet();
        });
    }

    private void checkId() {
        mDetailLoadPolicy.reset();
        mWaitingForDetailConfig = false;
        App.removeCallbacks(mRetryDetailConfig);
        mEntry = Objects.toString(getIntent().getStringExtra("entry"), "detail");
        String resume = getIntent().getStringExtra("resumeHistory");
        if (!TextUtils.isEmpty(resume)) {
            mPendingResumeHistory = History.objectFrom(resume);
            mPendingResumeHistory.setAccountId(com.fongmi.android.tv.utils.LocalProfile.id());
        getIntent().removeExtra("resumeHistory");
        }
        History saved = mPendingCloudHistory == null ? mPendingResumeHistory : mPendingCloudHistory;
        mFilmIdentity = saved == null ? new History() : History.objectFrom(saved.toString());
        mFilmIdentity.setAccountId(com.fongmi.android.tv.utils.LocalProfile.id());
        if (saved == null) {
            mFilmIdentity.setKey(getHistoryKey()); mFilmIdentity.setVodName(getName()); mFilmIdentity.setVodYear(getYear());
            mFilmIdentity.setVodType(Objects.toString(getIntent().getStringExtra("type"), ""));
        }
        Logger.i("PlaySession: event=enter session=" + mSessionId + " entry=" + mEntry + " title=" + getName()
                + " savedFilm=" + mFilmIdentity.getFilmId() + " savedEpisode=" + mFilmIdentity.getVodRemarks());
        VodConfig.get().activate(getKey());
        mParseAdapter.reload();
        if (getId().startsWith("push://")) getIntent().putExtra("key", "push_agent").putExtra("id", getId().substring(7));
        setTarget(mFilmIdentity.getVodName(), mFilmIdentity.getVodYear(), mFilmIdentity.getVodType());
        initSources();
        boolean hasLocal = OfflinePlayback.matching(getKey(), getId(), getName(), getYear(), "").stream().anyMatch(Download::isPlayable);
        if (hasLocal) {
            mBinding.progressLayout.showContent();
            mBinding.swipeLayout.setRefreshing(false);
            hideProgress();
        } else {
            mBinding.progressLayout.showProgress();
            showProgress();
        }
        if (!Util.isNetworkAvailable() || hasLocal) getDetail();
        else if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty();
        else getDetail();
        App.removeCallbacks(mPlaybackWatchdog); App.post(mPlaybackWatchdog, 1000);
    }

    private boolean mDetailLoading;
    private boolean mDetailStartedWithoutNetwork;
    private boolean mRetryDetailWhenOnline;
    private boolean mWaitingForDetailConfig;
    private String mFilePermissionState = "";
    private final com.fongmi.android.tv.search.DetailLoadPolicy mDetailLoadPolicy = new com.fongmi.android.tv.search.DetailLoadPolicy();
    private final Runnable mRetryDetailConfig = this::retryDetailConfiguration;

    private void getDetail() {
        App.removeCallbacks(mRetryDetailConfig, mR4);
        mDetailRequestedAt = SystemClock.elapsedRealtime();
        mLocalDetailGeneration++;
        mRefreshingLocalDetail = false;
        mRetryDetailWhenOnline = false;
        mDetailStartedWithoutNetwork = !isOffline() && (!com.fongmi.android.tv.utils.Util.isNetworkAvailable()
                || VodConfig.get().getSites().isEmpty());
        // History and cache entries share the same offline-first path, without waiting for a source timeout.
        boolean hasLocal = OfflinePlayback.matching(getKey(), getId(), getName(), getYear(), "").stream().anyMatch(Download::isPlayable);
        boolean online = Util.isNetworkAvailable();
        boolean siteReady = !getSite().getApi().isEmpty();
        logFilePermissions("detail");
        if (mDetailLoadPolicy.awaitConfiguration(mDetailRequestedAt, isOffline(), hasLocal,
                online, siteReady, !"push_agent".equals(getKey()) && VodConfig.get().isLoading())) {
            if (!mWaitingForDetailConfig) {
                mViewModel.cancelPending();
                Logger.i("VideoDetail: action=wait-configuration session=" + mSessionId + " entry=" + mEntry
                        + " site=" + getKey() + " sites=" + VodConfig.get().getSites().size());
                if (mCurrentVod == null) {
                    mBinding.progressLayout.showProgress();
                    showProgress();
                }
            }
            mWaitingForDetailConfig = true;
            mDetailLoading = false;
            mRetryDetailWhenOnline = true;
            App.post(mRetryDetailConfig, 400);
            return;
        }
        if (mWaitingForDetailConfig) {
            Logger.i("VideoDetail: action=configuration-wait-ended session=" + mSessionId
                    + " siteReady=" + siteReady + " loading=" + VodConfig.get().isLoading());
            if (siteReady) {
                VodConfig.get().activate(getKey());
                mParseAdapter.reload();
            }
        }
        mWaitingForDetailConfig = false;
        mDetailLoading = true;
        if (hasLocal) {
            mBinding.progressLayout.showContent();
            mBinding.swipeLayout.setRefreshing(false);
            cancelBufferingProgress();
            hideProgress();
        }
        Logger.i("OfflineStart: detail name=" + getName() + " site=" + getKey() + " id=" + getId()
                + " local=" + hasLocal + " network=" + online + " offlineEntry=" + isOffline()
                + " siteReady=" + siteReady + " configurationLoading=" + VodConfig.get().isLoading());
        if (!hasLocal) {
            int logged = 0;
            for (Download item : Download.getAll()) {
                if (!TitleKey.normalize(item.getVodName()).equals(TitleKey.normalize(getName()))) continue;
                Logger.i("OfflineStart: unmatched episode=" + item.getEpisodeName() + " year=" + item.getVodYear()
                        + " type=" + item.getVodType() + " status=" + item.getStatus() + " playable=" + item.isPlayable());
                if (++logged >= 8) break;
            }
        }
        mLocalDetail = !"push_agent".equals(getKey()) && (hasLocal || !online || !siteReady);
        if (mLocalDetail) mViewModel.offlineContent(getKey(), getId(), getName(), getYear());
        else mViewModel.detailContent(getKey(), getId());
    }

    private void retryDetailConfiguration() {
        if (!mWaitingForDetailConfig || isFinishing() || isDestroyed()
                || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return;
        getDetail();
    }

    private void logFilePermissions(String reason) {
        String state = com.fongmi.android.tv.utils.PermissionUtil.fileState(this);
        if (state.equals(mFilePermissionState)) return;
        mFilePermissionState = state;
        Logger.i("FilePermission: action=state session=" + mSessionId + " reason=" + reason + " " + state);
    }

    private void getDetail(Vod item) {
        mDetailLoadPolicy.reset();
        mWaitingForDetailConfig = false;
        App.removeCallbacks(mRetryDetailConfig);
        App.removeCallbacks(mFinishInitialSelection); mInitialSelection = false;
        captureSourceProgress();
        mPlaybackPolicy.switching(SystemClock.elapsedRealtime());
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getVodPic());
        getIntent().putExtra("id", item.getVodId());
        VodConfig.get().activate(item.getSiteKey());
        mParseAdapter.reload();
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.scroll.scrollTo(0, 0);
        mBinding.metaScroll.scrollTo(0, 0);
        mBinding.tagScroll.scrollTo(0, 0);
        mClock.setCallback(null);
        mPlayers.reset();
        mPlayers.stop();
        getDetail();
    }

    private void setDetail(Result result) {
        Logger.i("OfflineStart: detail-result local=" + mLocalDetail + " elapsedMs=" + (SystemClock.elapsedRealtime() - mDetailRequestedAt)
                + " items=" + result.getList().size() + " session=" + mSessionId + " site=" + getKey()
                + " siteReady=" + !getSite().getApi().isEmpty() + " configurationLoading=" + VodConfig.get().isLoading());
        mDetailLoading = false;
        mBinding.swipeLayout.setRefreshing(false);
        Vod offline = result.getList().isEmpty() ? getOfflineVod() : null;
        mRetryDetailWhenOnline = !isOffline() && offline == null && result.getList().isEmpty()
                && (mDetailStartedWithoutNetwork || !com.fongmi.android.tv.utils.Util.isNetworkAvailable()
                || VodConfig.get().getSites().isEmpty());
        // 站源拉不到详情（多半是断网）而本地有缓存时，直接用缓存把页面撑起来
        if (offline != null) { mLocalDetail = true; setDetail(offline); }
        else if (result.getList().isEmpty()) setEmpty();
        else setDetail(result.getList().get(0));
        // Provider messages are internal diagnostics. Recovery and the final player state own the UI.
        if (result.hasMsg() && result.getList().isEmpty())
            Logger.i("VideoDetail: provider-message suppressed msg=" + result.getMsg().replace('\n', ' '));
        retryDetailAfterNetwork();
    }

    private void retryDetailAfterNetwork() {
        if (mWaitingForDetailConfig) { retryDetailConfiguration(); return; }
        if (mLocalDetail && mCurrentVod != null) { refreshLocalCatalog(); return; }
        if (!mRetryDetailWhenOnline || mDetailLoading || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                || isOffline() || isCasting() || isFinishing() || isDestroyed()
                || !com.fongmi.android.tv.utils.Util.isNetworkAvailable()) return;
        VodConfig.get().recoverIfNeeded();
        if (getSite().getApi().isEmpty()) return;
        Logger.i("VideoDetail: retry after network/configuration recovery");
        getDetail();
    }

    /** Refill the online catalog after reconnecting without restarting the locally playing episode. */
    private void refreshLocalCatalog() {
        if (!mLocalDetail) return;
        if (mPlayingDownload != null && !mPlayers.isReady()) return;
        if (mRefreshingLocalDetail || mDetailLoading || !Util.isNetworkAvailable() || getSite().getApi().isEmpty()
                || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return;
        mRefreshingLocalDetail = true;
        final int generation = ++mLocalDetailGeneration;
        final String key = getKey(), id = getId();
        final Site site = getSite();
        App.execute(() -> {
            Result result;
            try { result = SiteViewModel.detail(site, id, false); }
            catch (Throwable error) { result = Result.empty(); }
            final Result loaded = result;
            App.post(() -> {
                if (generation != mLocalDetailGeneration || isFinishing() || isDestroyed()
                        || !key.equals(getKey()) || !id.equals(getId())) return;
                mRefreshingLocalDetail = false;
                if (!mLocalDetail || loaded.getList().isEmpty()) return;
                Vod vod = loaded.getList().get(0);
                Episode playing = getEpisode();
                String line = getFlag() == null ? "" : getFlag().getFlag();
                List<Download> local = OfflinePlayback.matching(key, id, vod.getVodName(), vod.getVodYear(), vod.getTypeName());
                OfflinePlayback.remember(key, id, vod);
                OfflinePlayback.merge(vod, local);
                removeRestrictedFlags(vod);
                prioritizePlayableFlags(vod);
                Flag selected = null;
                for (Flag flag : vod.getVodFlags()) {
                    if (flag.isCloudDrive() || PlaybackRoutePolicy.isRestricted(flag) || flag.getEpisodes().isEmpty()) continue;
                    if (selected == null || flag.getFlag().equals(line)) selected = flag;
                }
                if (selected == null || playing == null) return;
                Episode replacement = selected.findByRemarks(playing.getName());
                if (replacement == null) return;
                mCurrentVod = vod;
                mergeDetailMetadata(vod);
                mLocalDetail = false;
                mFlagAdapter.addAll(vod.getVodFlags());
                mFlagAdapter.setActivated(selected);
                if (mHistory.isRevSort()) mFlagAdapter.reverse();
                mFlagAdapter.toggle(replacement);
                setEpisodeAdapter(selected.getEpisodes());
                mHistory.setVodFlag(selected.getFlag());
                mHistory.setEpisodeUrl(replacement.getUrl());
                updateHistoryEpisodeNumbers(replacement);
                loadDoubanDetails(metadataName(vod), metadataYear(vod));
                renderDetailMetadata();
                checkQuick();
            });
        });
    }

    /**
     * 详情拉不到（站点挂了、只给了片名）时换到别的源。
     * 以前从搜索结果进来会直接关掉页面，现在手上有整组片源，挨个换下去就是了。
     */
    private void setEmpty() {
        if (!Util.isNetworkAvailable()) { showEmpty(); return; }
        if (getName().isEmpty()) {
            showEmpty();
        } else {
            mBinding.name.setText(getName());
            loadDoubanDetails(getName(), getYear());
            App.post(mR4, 10000);
            checkSearch(false);
        }
    }

    private void showEmpty() {
        Logger.i("VideoDetail: action=empty session=" + mSessionId + " site=" + getKey()
                + " configurationLoading=" + VodConfig.get().isLoading() + " network=" + Util.isNetworkAvailable());
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
    }

    private void setDetail(Vod item) {
        if (!mLocalDetail && !acceptsDetail(item)) {
            VodSource rejected = mSourceAdapter.getCurrent(); if (rejected != null) rejected.setBroken(true);
            Logger.i("PlayIdentity: action=reject-detail session=" + mSessionId + " title=" + item.getVodName()
                    + " expectedYear=" + mTargetYear + " actualYear=" + item.getVodYear()
                    + " expectedKind=" + mTargetKind + " actualType=" + item.getTypeName());
            checkSearch(false); return;
        }
        List<Download> local = OfflinePlayback.matching(getKey(), getId(), item.getVodName(getName()), item.getVodYear(), item.getTypeName());
        if (!mLocalDetail && !local.isEmpty()) OfflinePlayback.remember(getKey(), getId(), item);
        OfflinePlayback.merge(item, local);
        int restricted = removeRestrictedFlags(item);
        if (!isOffline() && restricted > 0 && item.getVodFlags().isEmpty()) {
            skipUnplayableSource("restricted-only");
            return;
        }
        prioritizePlayableFlags(item);
        if (!isOffline() && !mManualSourceSelection && hasOnlyCloudFlags(item)) {
            skipUnplayableSource("cloud-only");
            return;
        }
        mCurrentVod = item;  // 保存当前视频对象
        mergeDetailMetadata(item);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.progressLayout.showContent();
        mBinding.video.setTag(item.getVodPic(getPic()));
        mBinding.name.setText(getName().isEmpty() ? item.getVodName() : getName());
        mBinding.name.playOnce();
        loadDoubanDetails(metadataName(item), metadataYear(item));
        mBinding.poster.setContentDescription(item.getVodName(getName()));
        renderDetailMetadata();
        mFlagAdapter.addAll(item.getVodFlags());
        App.removeCallbacks(mR4);
        setTarget(item.getVodName(getName()), item.getVodYear().isEmpty() ? getYear() : item.getVodYear(), item.getTypeName());
        setCurrentSource(item);
        checkHistory(item);
        applyDoubanMetadata();
        checkFlag(item);
        getIntent().removeExtra("offline");
        checkKeepImg();
        checkQuick();
        mManualSourceSelection = false;
    }

    /** 普通线路排前、网盘线路沉底；手动点网盘线路仍然保留可用。 */
    private void prioritizePlayableFlags(Vod item) {
        item.getVodFlags().sort((left, right) -> Boolean.compare(left.isCloudDrive(), right.isCloudDrive()));
    }

    private int removeRestrictedFlags(Vod item) {
        if (isOffline() || item == null) return 0;
        int before = item.getVodFlags().size();
        List<String> blocked = new ArrayList<>();
        for (Flag flag : item.getVodFlags()) if (PlaybackRoutePolicy.isRestricted(flag)) blocked.add(flag.getFlag());
        item.getVodFlags().removeIf(PlaybackRoutePolicy::isRestricted);
        int removed = before - item.getVodFlags().size();
        if (removed > 0) Logger.i("PlayRoute: action=block-restricted session=" + mSessionId
                + " source=" + getKey() + " lines=" + String.join(",", blocked));
        return removed;
    }

    private boolean hasOnlyCloudFlags(Vod item) {
        if (item.getVodFlags().isEmpty()) return false;
        for (Flag flag : item.getVodFlags()) if (!flag.isCloudDrive()) return false;
        return true;
    }

    /** 当前站点只有网盘线路时不请求播放地址，直接继续找下一个普通源。 */
    private void skipUnplayableSource(String reason) {
        VodSource current = mSourceAdapter.getCurrent();
        if (current != null) current.setBroken(true);
        Logger.i("PlayDecision: action=skip-source session=" + mSessionId + " reason=" + reason + " source=" + getKey());
        mManualSourceSelection = false;
        if (nextSite()) return;
        if (mSourceTask != null && !mSourceTask.isFinished()) setInitAuto(true);
        else if (mSourceTask != null && mSourceTask.hasMore()) { setInitAuto(true); mSourceTask.searchMore(); }
        else startSourceSearch(true);
    }
    
    /**
     * 演职人员合并成一段：导演名后跟"（导演）"，再接演员，中间用斜杠分隔。
     * 默认最多两行，超出时右下角出现展开按钮；这一整行位于下方主滚动区的顶部。
     */
    private void setCast(Vod item) {
        if (mDoubanLoading && mDoubanSubject == null) { showMetadataPlaceholder(); return; }
        List<CastMember> members = new ArrayList<>();
        List<String> directorsFromDouban = mDoubanSubject == null ? new ArrayList<>() : mDoubanSubject.getDirectors();
        List<String> actorsFromDouban = mDoubanSubject == null ? new ArrayList<>() : mDoubanSubject.getActors();
        if (directorsFromDouban.isEmpty()) members.addAll(sourcePeople(item.getVodDirector(), CastMember.CastType.DIRECTOR));
        else for (String name : directorsFromDouban) members.add(new CastMember(name, CastMember.CastType.DIRECTOR));
        int directors = members.size();
        if (actorsFromDouban.isEmpty()) members.addAll(sourcePeople(item.getVodActor(), CastMember.CastType.ACTOR));
        else for (String name : actorsFromDouban) members.add(new CastMember(name, CastMember.CastType.ACTOR));
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

    private List<CastMember> sourcePeople(String raw, CastMember.CastType type) {
        List<CastMember> result = new ArrayList<>();
        for (CastMember member : CastUtil.parseCastMembers(raw, type)) {
            String name = member.getName().trim();
            if (name.matches("[0-9\\s年月日./-]+") || name.equals("未知") || name.equals("不详") || name.equals("暂无")) continue;
            result.add(member);
        }
        return result;
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
        if (mDoubanLoading && mDoubanSubject == null) { showMetadataPlaceholder(); return; }
        List<String> parts = new ArrayList<>();
        String year = mDoubanSubject != null && !mDoubanSubject.getYear().isEmpty() ? mDoubanSubject.getYear() : item.getVodYear().trim();
        if (year.isEmpty()) year = metadataYear(item);
        if (year.length() >= 4 && TextUtils.isDigitsOnly(year.substring(0, 4))) year = year.substring(0, 4);
        if (!year.isEmpty()) parts.add(year);
        String area = mDoubanSubject != null && !mDoubanSubject.getCountries().isEmpty() ? TextUtils.join(" / ", mDoubanSubject.getCountries()) : item.getVodArea().trim();
        if (!area.isEmpty()) parts.add(area);
        if (!item.getVodRemarks().trim().isEmpty()) parts.add(item.getVodRemarks().trim());
        mBinding.meta.setText(TextUtils.join("  ·  ", parts));
        mBinding.metaScroll.setVisibility(parts.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    /** 优先使用豆瓣类型；确实没有时才回退片源分类。 */
    private void setTags(List<String> doubanGenres) {
        if (mDoubanLoading && mDoubanSubject == null) { showMetadataPlaceholder(); return; }
        mBinding.tags.removeAllViews();
        List<String> tags = new ArrayList<>();
        if (doubanGenres != null) for (String tag : doubanGenres) if (!tag.trim().isEmpty() && !tags.contains(tag.trim())) tags.add(tag.trim());
        Vod metadata = mMetadataVod == null ? mCurrentVod : mMetadataVod;
        if (tags.isEmpty() && metadata != null) {
            String category = metadata.getTypeName();
            if (category.isEmpty() && mFilmIdentity != null) category = mFilmIdentity.getVodType();
            if (category.isEmpty()) category = Objects.toString(getIntent().getStringExtra("type"), "");
            for (String type : category.split("[,，/、\\s]+")) {
                if (!type.isEmpty() && !type.matches("[0-9]+") && type.length() <= 12 && !tags.contains(type)) tags.add(type);
            }
        }
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
        mBinding.tagScroll.setVisibility(tags.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    private void getPlayer(Flag flag, Episode episode, boolean replay) {
        if (flag == null || episode == null) return;
        if (mSelectedQuality != null && !isQualityMovie()
                && !com.fongmi.android.tv.search.EpisodeKey.key(mSelectedQuality.episode.getName()).equals(com.fongmi.android.tv.search.EpisodeKey.key(episode.getName())))
            mSelectedQuality = null;
        prepareQualityCatalog(episode.getName());
        if (mQualityCatalog != null) mQualityCatalog.budget(0);
        mPlaybackRequestedAt = SystemClock.elapsedRealtime();
        mCurrentStartupMs = -1;
        String semanticEpisode = isQualityMovie() ? "movie" : com.fongmi.android.tv.search.EpisodeKey.key(episode.getName());
        if (!semanticEpisode.equals(mPlaybackEpisode)) {
            mPlaybackPolicy.episode(mPlaybackRequestedAt); mPlaybackEpisode = semanticEpisode;
            mAutoWidth = 0; mAutoHeight = 0; mRecoveryPending = false; mPlaybackWanted = true;
            for (VodSource source : mSourceAdapter.getRanked()) source.setBroken(false);
        }
        mPlaybackPolicy.begin(mPlaybackRequestedAt);
        Logger.i("PlayDecision: action=start session=" + mSessionId + " film=" + mHistory.getFilmId()
                + " episode=" + semanticEpisode + " source=" + getKey() + " line=" + flag.getFlag()
                + " positionMs=" + mHistory.getPosition() + " automatic=" + mAutomaticQuality);
        mLoggedPlaybackReady = false;
        mBinding.control.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
        List<Download> local = getLocalDownloads();
        mPlayingDownload = OfflinePlayback.find(local, episode.getName(), getKey(), getId(), flag.getFlag(), mFailedLocalPaths);
        Logger.i("OfflineStart: select episode=" + episode.getName() + " line=" + flag.getFlag()
                + " candidates=" + local.size() + " hit=" + (mPlayingDownload != null) + " cloudWait=" + mAwaitingCloudSync);
        if (mPlayingDownload == null) for (Download item : local.subList(0, Math.min(8, local.size()))) Logger.i("OfflineStart: candidate episode=" + item.getEpisodeName()
                + " status=" + item.getStatus() + " playable=" + item.isPlayable() + " rejected=" + mFailedLocalPaths.contains(item.getLocalPath()));
        boolean localUrl = OfflinePlayback.isLocal(episode.getUrl());
        String localPath = localUrl ? Uri.parse(episode.getUrl()).getPath() : null;
        boolean unavailableFile = localUrl && (localPath == null || !new File(localPath).isFile() || mFailedLocalPaths.contains(localPath));
        if (mPlayingDownload == null && ((!Util.isNetworkAvailable() && !localUrl && !"push_agent".equals(getKey())) || unavailableFile)) {
            hideProgress();
            boolean cached = local.stream().anyMatch(item -> item.isPlayable()
                    && Download.episodeKey(item.getEpisodeName()).equals(Download.episodeKey(episode.getName())));
            showError(cached ? "本集缓存播放失败，文件仍在，请导出运行日志" : localUrl ? "本集缓存文件不可用" : "本集尚未缓存，联网后可播放");
            return;
        }
        // Keep the original episode URL and line in history; substitute only the playback request.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mBinding.control.title.setSelected(true);
        updateHistory(episode, replay);
        cancelBufferingProgress();
        if (mPlayingDownload != null || localUrl) {
            hideProgress();
            hideError();
        } else showProgress();
        setMetadata();
        if (mPlayingDownload != null || localUrl) {
            mAwaitingCloudSync = false;
            mResumeAfterCloudSync = false;
            mClock.start();
            mViewModel.localPlayer(mPlayingDownload == null ? episode.getUrl() : OfflinePlayback.localUrl(mPlayingDownload));
        } else mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
    }

    private void setPlayer(Result result) { setPlayer(result, false); }

    private void setPlayer(Result result, boolean addressChecked) {
        Logger.i("OfflineStart: player-result local=" + OfflinePlayback.isLocal(result.getUrl().v())
                + " elapsedMs=" + (SystemClock.elapsedRealtime() - mPlaybackRequestedAt));
        result.getUrl().set(mSelectedQuality != null && mSelectedQuality.source.same(getKey(), getId()) ? mSelectedQuality.valueIndex : 0);
        if (!addressChecked && !mAutomaticQuality && mSelectedQuality != null && mQualityCatalog != null
                && mSelectedQuality.source.same(getKey(), getId()) && getFlag() != null
                && mSelectedQuality.flag.getFlag().equals(getFlag().getFlag())
                && (isQualityMovie() || com.fongmi.android.tv.search.EpisodeKey.key(mSelectedQuality.episode.getName()).equals(com.fongmi.android.tv.search.EpisodeKey.key(mHistory.getVodRemarks())))
                && (mSelectedQuality.verifiedUrl.isEmpty() || System.currentTimeMillis() - mSelectedQuality.verifiedAt > 120000)
                && !result.getUrl().isEmpty()) {
            com.fongmi.android.tv.search.QualityOption selected = mSelectedQuality;
            long request = mPlaybackRequestedAt; Result original = result;
            mQualityCatalog.refreshSelection(selected, original, success -> {
                if (isFinishing() || isDestroyed() || request != mPlaybackRequestedAt || selected != mSelectedQuality) return;
                if (!success) { mSelectedQuality = null; mQualityHeight = 0; mAutomaticQuality = true; }
                setPlayer(original, true);
            });
            return;
        }
        if (mSelectedQuality != null && !mSelectedQuality.verifiedUrl.isEmpty()
                && mSelectedQuality.source.same(getKey(), getId()) && getFlag() != null
                && mSelectedQuality.flag.getFlag().equals(getFlag().getFlag())
                && (isQualityMovie()
                    || com.fongmi.android.tv.search.EpisodeKey.key(mSelectedQuality.episode.getName()).equals(com.fongmi.android.tv.search.EpisodeKey.key(mHistory.getVodRemarks())))
                && System.currentTimeMillis() - mSelectedQuality.verifiedAt <= 120000 && !result.getUrl().isEmpty()) {
            // This URL is already resolved and was verified with its own required headers.
            com.google.gson.JsonObject direct = App.gson().toJsonTree(result).getAsJsonObject();
            direct.add("header", App.gson().toJsonTree(mSelectedQuality.verifiedHeaders));
            result = Result.objectFrom(direct.toString());
            result.getUrl().replace(mSelectedQuality.verifiedUrl);
            result.setParse(0); result.setPlayUrl("");
            direct = App.gson().toJsonTree(result).getAsJsonObject(); direct.addProperty("jx", 0);
            result = Result.objectFrom(direct.toString());
        }
        if ((!mDoubanLoading || mDoubanSubject != null) && mMetadataVod != null && mMetadataVod.getVodContent().isEmpty() && !result.getDesc().isEmpty()
                && (mDoubanSubject == null || mDoubanSubject.getIntro().isEmpty())) {
            mMetadataVod.setVodContent(result.getDesc());
            renderDetailMetadata();
        }
        setUseParse(!OfflinePlayback.isLocal(result.getUrl().v()) && !VodConfig.get().getSourceParses(getKey()).isEmpty() && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags(getKey()).contains(result.getFlag())) || result.getJx() == 1));
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.setParseVisible(isUseParse());
        mBinding.control.parse.setVisibility(View.GONE);
        stopPlaybackCache();
        mPlayers.setKey(getHistoryKey());
        if (mAutomaticQuality || mSelectedQuality != null) { Track.delete(mPlayers.getKey(), C.TRACK_TYPE_VIDEO); mPlayers.resetTrack(C.TRACK_TYPE_VIDEO); }
        mPlayers.start(result, isUseParse(), getPlayerTimeout());
        if (mAwaitingCloudSync || mCloudChoicePending || !mPlaybackWanted) mPlayers.pause();
        setQualityVisible(result.getUrl().isMulti());
        mBinding.swipeLayout.setRefreshing(false);
        mBinding.swipeLayout.setEnabled(false);
        mPlayers.setKey(getHistoryKey());
        mQualityAdapter.addAll(result);
    }

    @Override
    public void onItemClick(Flag item) {
        mAutomaticQuality = false; mSelectedQuality = null; mQualityHeight = 0;
        selectFlag(item, false);
    }

    private boolean selectFlag(Flag item, boolean autoSwitch) {
        if (item.isActivated()) return false;
        int previousPosition = mEpisodeAdapter.isEmpty() ? -1 : mEpisodeAdapter.getPosition();
        mFlagAdapter.setActivated(item);
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
        updateDetailPanels();
        return seamless(item, autoSwitch, previousPosition);
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
            stopPlaybackCache();
            mPlayers.start(result, isUseParse(), getPlayerTimeout());
        if (mAwaitingCloudSync || mCloudChoicePending) mPlayers.pause();
        } catch (Exception e) {
            ErrorEvent.extract(tag, e.getMessage());
            Logger.e("Error", e);
        }
    }

    @Override
    public void onItemClick(VodSource item) {
        Logger.i("PlayDecision: action=source-mode session=" + mSessionId + " mode=manual source=" + item.getSiteKey());
        if (mSourceAdapter.isCurrent(item)) return;
        switchSource(item, false);
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
        mBinding.control.next.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.size() == 0 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(items.size() < 10 ? View.GONE : View.VISIBLE);
        mBinding.more.setText(getString(R.string.detail_episode_all, String.valueOf(items.size())));
        markCached(items);
        mEpisodeAdapter.addAll(items);
        setDownloadVisible(!items.isEmpty());
        updateDetailPanels();
    }

    /** 当前这部剧的缓存聚合键就是片名，和观看记录一样跨源合并。 */
    private String getGroupKey() {
        return Download.buildGroupKey(getVodName());
    }

    /** 本地缓存拼出来的详情，只在站源那边什么都没拿到时用来兜底。 */
    private Vod getOfflineVod() {
        return OfflinePlayback.detail(getKey(), getId(), getName(), getYear(), "");
    }

    private List<Download> getLocalDownloads() {
        return OfflinePlayback.matching(getKey(), getId(), getVodName(),
                mCurrentVod == null ? getYear() : mCurrentVod.getVodYear(), mCurrentVod == null ? "" : mCurrentVod.getTypeName());
    }

    private String getVodName() {
        return mCurrentVod == null || mCurrentVod.getVodName().isEmpty() ? getName() : mCurrentVod.getVodName();
    }

    /** 给已经缓存好的集打标，剧集胶囊右侧会多一个小角标。 */
    private void markCached(List<Episode> items) {
        List<String> keys = getCachedKeys();
        mCachedKeys.clear();
        mCachedKeys.addAll(keys);
        for (Episode item : items) item.setCached(keys.contains(Download.episodeKey(item.getName())));
    }

    /** 用集号比对，别的源里集名写法不同（第01集 / 1）也能认出来是同一集。 */
    private List<String> getCachedKeys() {
        List<String> keys = new ArrayList<>();
        for (Download item : getLocalDownloads()) {
            if (!item.isPlayable()) continue;
            String key = Download.episodeKey(item.getEpisodeName());
            if (!keys.contains(key)) keys.add(key);
        }
        return keys;
    }

    /** 离线播放进来的详情页本身就是缓存内容，没有再缓存一遍的道理。 */
    private void setDownloadVisible(boolean visible) {
        boolean enabled = visible && !SiteViewModel.DOWNLOAD_KEY.equals(getKey());
        mBinding.download.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (enabled) setDownloadText();
    }

    /** 按钮平时是「缓存」，这部剧有任务在跑时直接变成「缓存中 45%」。 */
    private void setDownloadText() {
        if (mBinding.download.getVisibility() != View.VISIBLE) return;
        List<Download> items = Download.getByGroup(getGroupKey());
        int total = 0;
        int count = 0;
        for (Download item : items) {
            if (item.isDone()) continue;
            total += item.getProgress();
            ++count;
        }
        if (count == 0) mBinding.download.setText(R.string.download_entry);
        else mBinding.download.setText(getString(R.string.download_entry_progress, String.valueOf(total / count)));
    }

    /**
     * 缓存状态变了：按钮上的进度实时刷；剧集角标只在「已缓存集数集合」变了才重绘，
     * 进度事件每秒来两次，无脑重绘整排胶囊会闪。
     */
    private void onDownloadRefresh() {
        setDownloadText();
        if (mEpisodeAdapter.isEmpty()) return;
        if (mCachedKeys.equals(new java.util.HashSet<>(getCachedKeys()))) return;
        Episode playing = getEpisode();
        if (mCurrentVod != null) OfflinePlayback.merge(mCurrentVod, getLocalDownloads());
        if (playing != null) mFlagAdapter.toggle(playing);
        setEpisodeAdapter(getFlag().getEpisodes());
    }

    private void onDownload() {
        if (mEpisodeAdapter.isEmpty()) return;
        DownloadEpisodeDialog.create()
                .episodes(mEpisodeAdapter.getItems())
                .currentIndex(mEpisodeAdapter.getPosition())
                .groupKey(getGroupKey())
                .identity(getKey(), getId(), mCurrentVod == null ? getYear() : mCurrentVod.getVodYear(), mCurrentVod == null ? "" : mCurrentVod.getTypeName())
                .callback(this::startDownload)
                .show(this);
    }

    private void startDownload(List<Episode> items) {
        if (mCurrentVod != null && !mLocalDetail) OfflinePlayback.remember(getKey(), getId(), mCurrentVod);
        String name = getVodName();
        String pic = mCurrentVod == null || mCurrentVod.getVodPic().isEmpty() ? getPic() : mCurrentVod.getVodPic();
        String flag = getFlag().getFlag();
        List<Download> downloads = new ArrayList<>();
        for (Episode item : items) {
            if (OfflinePlayback.isLocal(item.getUrl())) continue;
            Download download = Download.create(getKey(), getId(), name, pic, flag, item);
            // 简介等元信息一起存下来，离线详情页才不是一片空白
            if (mCurrentVod != null) {
                download.setVodContent(mCurrentVod.getVodContent());
                download.setVodYear(mCurrentVod.getVodYear());
                download.setVodArea(mCurrentVod.getVodArea());
                download.setVodType(mCurrentVod.getTypeName());
            }
            downloads.add(download);
        }
        DownloadManager.get().add(downloads);
        Notify.show(getString(R.string.download_added, String.valueOf(downloads.size())));
        setDownloadText();
    }

    private boolean seamless(Flag flag, boolean autoSwitch, int previousPosition) {
        Episode episode = com.fongmi.android.tv.search.QualityCatalog.match(flag, mHistory.getVodRemarks(), isQualityMovie());
        if ((isOffline() || !Util.isNetworkAvailable()) && (episode == null || !episode.isCached())) {
            for (Episode cached : flag.getEpisodes()) if (cached.isCached()) { episode = cached; break; }
        }
        setQualityVisible(episode != null && episode.isActivated() && mQualityAdapter.getItemCount() > 1);
        if (episode == null) return false;
        if (autoSwitch && mHistory.getPosition() >= 0) mCloudResumePosition = mHistory.getPosition();
        if (episode.isActivated()) {
            if (autoSwitch) onRefresh();
            return true;
        }
        mHistory.setVodRemarks(episode.getName());
        onItemClick(episode);
        return true;
    }

    /** 自动换源不能被站点配置压缩到 1 秒；正常进入 READY 后 Players 会立即取消计时。 */
    private long getPlayerTimeout() {
        return getSite().isChangeable() ? Math.max(getSite().getTimeout(), MIN_AUTO_SWITCH_TIMEOUT) : -1;
    }

    private void setQualityVisible(boolean visible) {
        // Provider value labels may claim fake HD/4K; use the verified quality menu instead.
        mBinding.qualityText.setVisibility(View.GONE);
        mBinding.quality.setVisibility(View.GONE);
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
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
        CastDialog.create().history(mHistory).video(CastVideo.get(mBinding.name.getText().toString(), mPlayers.getUrl(), mPlayers.getPosition(), mPlayers.getHeaders())).fm(true).show(this);
    }

    // ==================== 投屏模式 ====================
    //
    // 投屏中手机不再本地解码（否则同一条流手机和电视各拉一份，费流量还发热），画面区盖上
    // castOverlay，下面那套控制条改去操控电视：播放/暂停、拖进度、上下集。电视的进度按秒轮询
    // 回来，既刷新进度条也写回观看记录，所以投屏看完退出，下次进 App 能接着看。

    private boolean isCasting() {
        return CastManager.get().isCasting();
    }

    /**
     * 换集在途：用户已经选了新的一集，但本地还在解析地址，还没推给接收端。
     * 这段时间接收端报的仍是上一集的进度，一个字都不能写进观看记录。
     */
    private boolean isCastSwitching() {
        return isCasting() && castEpisode != null && !castEpisode.equals(getEpisode());
    }

    /** 电视端的进度/播放态代理给进度条用。 */
    private final CustomSeekView.Source mCastSource = new CustomSeekView.Source() {
        @Override
        public long getPosition() {
            return CastManager.get().getPosition();
        }

        @Override
        public long getDuration() {
            return CastManager.get().getDuration();
        }

        @Override
        public boolean isPlaying() {
            return CastManager.get().isPlaying();
        }

        @Override
        public void seekTo(long position) {
            CastManager.get().seekTo(position);
        }
    };

    private void enterCastMode() {
        stopPlaybackCache();
        onPaused();
        castEpisode = getEpisode();
        castUrl = mPlayers.getUrl();
        mBinding.castOverlay.setVisibility(View.VISIBLE);
        mBinding.castTitle.setText(getString(R.string.cast_overlay_title, CastManager.get().getDeviceName()));
        mBinding.control.seek.setSource(mCastSource);
        // 投屏中屏幕可以熄，供流靠前台服务的 WifiLock 撑着，不用一直亮着
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        CastManager.get().setListener(this);
        syncCastSpeed();
        syncCastEpisodes();
        checkPlayImg();
        showControl();
    }

    private void exitCastMode() {
        mBinding.castOverlay.setVisibility(View.GONE);
        mBinding.control.seek.setSource(null);
        castEpisode = null;
        castUrl = null;
        // 投屏这段时间里本地播放器一直停在开投那一刻，进度是在对端走的。
        // 不把它补回来，退出后会从开投的位置重播一遍。
        long position = CastManager.get().getLastPosition();
        if (position > 0) {
            if (mHistory != null) mHistory.setPosition(position);
            if (!mPlayers.isEmpty()) mPlayers.seekTo(position);
        }
        // 投屏结束就接着在手机上播下去，不该再让用户手动点一次。
        // 界面不在前台时不开播，否则会在后台闷声拉流。
        if (!isStop() && !mPlayers.isEmpty()) {
            onPlay();
            schedulePlaybackCache();
        } else {
            checkPlayImg();
        }
    }

    private void onCastExit() {
        CastManager.get().stop();
        Notify.show(R.string.cast_exited);
    }

    /**
     * 本地播放器刚解析出真实地址，把它推给电视。
     * <p>
     * 之所以还要让本地播放器走一遍 prepare：换集后的真实播放地址（含解析、重定向、本地代理改写）
     * 就是从 {@code mPlayers.getUrl()} 拿的，和最初那次投屏用的是同一条路径。拿到就立刻把本地
     * 暂停，只付一次解析的代价。
     */
    private void castCurrent() {
        String url = mPlayers.getUrl();
        if (TextUtils.isEmpty(url)) return;
        Episode episode = getEpisode();
        // 换一集会收到两次 PREPARE，同一集的同一个地址被推两遍。第二遍不只让接收端
        // 白重新缓冲一次，还会因为"这一集已经在放了"而去读观看记录里的进度——那时候
        // 记录还冻结在切集之前，于是新的一集从上一集的时间开始。直接挡掉重复推送。
        if (episode.equals(castEpisode) && url.equals(castUrl)) return;
        String name = getString(R.string.detail_title, mBinding.name.getText(), episode.getName());
        // 起播位置：换集从头（或跳过片头处）开始；同一集换了地址（切线路/画质）
        // 就接着接收端此刻的位置。两种情况都不读观看记录——投屏期间它要么冻结着旧值，
        // 要么正被接收端的回报刷新，不可信。
        long start = episode.equals(castEpisode)
                ? Math.max(CastManager.get().getPosition(), 0)
                : Math.max(mHistory.getOpening(), 0);
        castEpisode = episode;
        castUrl = url;
        CastManager.get().cast(CastVideo.get(name, url, start, mPlayers.getHeaders()), null);
        CastManager.get().setSpeed(mPlayers.getSpeed());
        syncCastEpisodes();
        Notify.show(getString(R.string.cast_switching, episode.getName()));
        castEnded = false;
        onPaused();
    }

    @Override
    public void onCastChanged() {
        if (!isCasting()) {
            exitCastMode();
            return;
        }
        // 换集在途：对端报的还是上一集的进度，写进记录会污染新一集的起播位置
        if (isCastSwitching()) {
            checkPlayImg();
            return;
        }
        long position = CastManager.get().getPosition();
        long duration = CastManager.get().getDuration();
        if (mHistory != null && position > 0 && position != mHistory.getPosition()) {
            mHistoryPlaybackConfirmed = true;
            mHistory.setPosition(position);
            if (duration > 0) mHistory.setDuration(duration);
            queueHistorySnapshot(true, false);
        }
        checkPlayImg();
        checkCastEnded(position, duration);
    }

    /**
     * 电视放完一集自动切下一集。
     * <p>
     * DLNA 没有可靠的"播放结束"事件：刚 SetAVTransportURI 完也是 STOPPED。所以只认
     * "有时长、已经放到接近末尾、且现在停了"这一种组合，并且靠 duration>0 排除掉刚开始那一段。
     */
    private void checkCastEnded(long position, long duration) {
        if (castEnded) return;
        if (duration <= 0 || position <= 0) return;
        if (CastManager.get().isPlaying()) return;
        if (position + 3000 < duration) return;
        castEnded = true;
        checkNext(false);
    }

    private void onInfo() {
        InfoDialog.create(this).title(mBinding.control.title.getText()).headers(mPlayers.getHeaders()).url(mPlayers.getUrl()).show();
    }

    private void onFull() {
        setR1Callback();
        toggleFullscreen();
    }

    private void onPlayerBack() {
        if (isFullscreen()) {
            exitFullscreen();
        } else {
            stopSourceSearch();
            super.onBackPress();
        }
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
        if (isCasting()) {
            CastManager.get().toggle();
            return;
        }
        if (mPlayers.isPlayRequested()) onPaused();
        else if (mPlayers.isEmpty()) { mPlaybackWanted = true; onRefresh(); }
        else onPlay();
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        setR1Callback();
        Episode item = mEpisodeAdapter.getNext();
        if (item != null && !item.isActivated()) onItemClick(item);
        else if (notify) Notify.show(R.string.error_play_next);
    }

    private void checkPrev() {
        setR1Callback();
        Episode item = mEpisodeAdapter.getPrev();
        if (item != null && !item.isActivated()) onItemClick(item);
        else Notify.show(R.string.error_play_prev);
    }

    private void onPlayerMore(View anchor) {
        List<Integer> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        addMore(ids, labels, icons, MORE_INFO, R.string.play_info, R.drawable.ic_control_info);
        addMore(ids, labels, icons, MORE_DANMAKU, R.string.danmaku_settings, R.drawable.ic_control_danmaku_settings);
        addMore(ids, labels, icons, MORE_TIMER, R.string.play_timer, R.drawable.ic_control_timer);
        if (!isActionShown(mBinding.control.action.scale)) addMore(ids, labels, icons, MORE_SCALE, R.string.player_scale, R.drawable.ic_control_quality);
        if (!isActionShown(mBinding.control.action.speed)) addMore(ids, labels, icons, MORE_SPEED, R.string.control_speed, 0);
        if (mPlayers.haveTrack(C.TRACK_TYPE_TEXT)) addMore(ids, labels, icons, MORE_TEXT, R.string.play_track_text, R.drawable.ic_control_subtitle);
        if (mPlayers.haveTrack(C.TRACK_TYPE_AUDIO)) addMore(ids, labels, icons, MORE_AUDIO, R.string.play_track_audio, R.drawable.ic_control_audio_track);
        if (mCurrentVod != null && !isActionShown(mBinding.control.action.video)) addMore(ids, labels, icons, MORE_VIDEO, getString(R.string.play_quality_value, getCurrentQualityLabel()), 0);
        if (!isFullscreen() && mEpisodeAdapter.getItemCount() > 1) addMore(ids, labels, icons, MORE_EPISODES, R.string.detail_episode, R.drawable.ic_action_list);
        if (isFullscreen() && !PiP.noPiP() && !isVisible(mBinding.control.pip)) addMore(ids, labels, icons, MORE_PIP, R.string.play_pip, R.drawable.ic_control_pip);
        if (!isFullscreen() && mHistory != null && !isVisible(mBinding.control.keep)) addMore(ids, labels, icons, MORE_KEEP, R.string.keep, R.drawable.ic_control_keep_off);
        if (isFullscreen() && !isActionShown(mBinding.control.action.ending)) addMore(ids, labels, icons, MORE_ENDING, R.string.play_ed, 0);
        if (isFullscreen() && !isActionShown(mBinding.control.action.exit)) addMore(ids, labels, icons, MORE_EXIT, R.string.play_exit_full, R.drawable.ic_control_exit_full);
        mQualityAnchor = null;
        mBinding.playbackPanel.show(anchor, getString(R.string.play_more), toIntArray(ids), labels.toArray(new String[0]), toIntArray(icons), null, 208, PLAYER_PANEL_MAX_HEIGHT_DP, this::onMoreItem);
        refreshBackHandling();
        App.removeCallbacks(mR1);
    }

    private void addMore(List<Integer> ids, List<String> labels, List<Integer> icons, int id, int label, int icon) {
        addMore(ids, labels, icons, id, getString(label), icon);
    }

    private void addMore(List<Integer> ids, List<String> labels, List<Integer> icons, int id, String label, int icon) {
        ids.add(id);
        labels.add(label);
        icons.add(icon);
    }

    private int[] toIntArray(List<Integer> items) {
        int[] result = new int[items.size()];
        for (int i = 0; i < items.size(); i++) result[i] = items.get(i);
        return result;
    }

    private void onMoreItem(int id) {
        View anchor = mBinding.control.playerMore;
        mBinding.playbackPanel.dismiss();
        switch (id) {
            case MORE_INFO:
                onInfo();
                break;
            case MORE_DANMAKU:
                onDanmaku();
                break;
            case MORE_TIMER:
                anchor.postDelayed(() -> showTimerPanel(anchor), 160);
                break;
            case MORE_SCALE:
                anchor.postDelayed(() -> onScale(anchor), 160);
                break;
            case MORE_SPEED:
                anchor.postDelayed(() -> onSpeed(anchor), 160);
                break;
            case MORE_TEXT:
                anchor.postDelayed(() -> showTrackPanel(anchor, C.TRACK_TYPE_TEXT), 160);
                break;
            case MORE_AUDIO:
                anchor.postDelayed(() -> showTrackPanel(anchor, C.TRACK_TYPE_AUDIO), 160);
                break;
            case MORE_VIDEO:
                anchor.postDelayed(() -> showTrackPanel(anchor, C.TRACK_TYPE_VIDEO), 160);
                break;
            case MORE_PIP:
                enterPiP();
                break;
            case MORE_KEEP:
                onKeep();
                break;
            case MORE_ENDING:
                onEnding();
                break;
            case MORE_EXIT:
                exitFullscreen();
                break;
            case MORE_EPISODES:
                onEpisodes();
                break;
        }
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
                logVideoLayout("drag-down", 0f);
                return true;
            case MotionEvent.ACTION_MOVE:
                // 被 DragSheetLayout 拦截进来时不会有 DOWN，第一帧就地取基准点
                if (!mDragging) {
                    mDragging = true;
                    mVideoBase = mBinding.video.getWidth();
                    mHandleDown = land ? event.getRawX() : event.getRawY();
                    logVideoLayout("drag-intercepted", 0f);
                    return true;
                }
                dragSheet(land, Math.max(0, (land ? event.getRawX() : event.getRawY()) - mHandleDown));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mDragging) return false;
                mDragging = false;
                float distance = Math.max(0, (land ? event.getRawX() : event.getRawY()) - mHandleDown);
                int travel = getSheetTravel(land);
                Logger.i("VideoLayout: drag-release distance=" + Math.round(distance)
                        + " travel=" + travel + " threshold=" + Math.round(travel * 0.2f)
                        + " result=" + (distance > travel * 0.2f ? "fullscreen" : "reset"));
                logVideoLayout("drag-release", distance);
                if (distance > travel * 0.2f) slideOutSheet(land);
                else resetSheet(land);
                return true;
        }
        return false;
    }

    private int getSheetTravel(boolean land) {
        if (land) return mBinding.swipeLayout.getWidth();
        // 竖屏详情层既设置了 MATCH_PARENT，又通过 BELOW 放在视频下方；它的 measuredHeight
        // 可能一直延伸到屏幕外，不能代表“滑到底”需要走的距离。用同一根布局坐标系计算，
        // 让详情层顶部最终恰好落在屏幕底边，视频中心才会和全屏中心无缝衔接。
        return Math.max(0, mBinding.getRoot().getHeight() - mBinding.swipeLayout.getTop());
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

    /** 竖屏详情页的视频区域固定占整块屏幕高度的四分之一，不跟随片源比例变化。 */
    private int getVideoHeight() {
        return Math.max(1, ResUtil.getScreenHeight(this) / 4);
    }

    /**
     * 横屏时卡片是贴着屏幕上下边缘的，内容不留白就会顶到最上面；
     * 左边还压着一根竖把手，也得给它让出位置。竖屏靠布局自身的间距即可。
     */
    private void setSheetPadding(boolean land) {
        int start = land ? ResUtil.dp2px(10) : 0;
        int vertical = land ? ResUtil.dp2px(22) : 0;
        mBinding.handle.setClipToPadding(false);
        mBinding.handle.setPadding(start, vertical, 0, vertical);
        mBinding.scroll.setPadding(0, 0, 0, 0);
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
        mBinding.playerSurface.animate().cancel();
        mBinding.handleLand.animate().cancel();
        mBinding.handleLand.setTranslationX(0);
        mBinding.swipeLayout.setTranslationX(0);
        mBinding.swipeLayout.setTranslationY(0);
        mBinding.video.setTranslationX(0);
        mBinding.video.setTranslationY(0);
        mBinding.playerSurface.setTranslationY(0);
    }

    private void dragSheet(boolean land, float moved) {
        float distance = Math.min(moved, getSheetTravel(land));
        if (land) {
            mBinding.swipeLayout.setTranslationX(distance);
            mBinding.handleLand.setTranslationX(distance);
            setVideoWidth(mVideoBase + (int) distance);
        } else {
            mBinding.swipeLayout.setTranslationY(distance);
            mBinding.video.setTranslationY(getPortraitVideoTranslation(distance));
            mBinding.playerSurface.setTranslationY(0f);
        }
    }

    /**
     * 拖动阶段直接把视频容器中心送到最终观看中心线。不能移动内部 SurfaceView：
     * SurfaceView 使用独立渲染层，越过父容器时仍会被裁掉，普通 View 的 clipChildren
     * 对它并不可靠。
     */
    private float getPortraitVideoTranslation(float sheetDistance) {
        int travel = getSheetTravel(false);
        if (travel <= 0) return 0f;
        float progress = Math.max(0f, Math.min(1f, sheetDistance / travel));
        // Before immersive mode the root starts below the status bar. Convert the physical
        // target center back into the root's current coordinates so hiding system bars does
        // not introduce a final one-frame jump.
        float targetCenter = getRealScreenHeight() / 2f
                - ResUtil.dp2px(PORTRAIT_VIEWING_CENTER_OFFSET_DP)
                - getRootScreenTop();
        float targetTranslation = targetCenter
                - mBinding.video.getTop()
                - mBinding.video.getHeight() / 2f;
        return targetTranslation * progress;
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
        else {
            mBinding.video.animate().translationY(0).setDuration(180).start();
            mBinding.playerSurface.setTranslationY(0f);
        }
    }

    private void slideOutSheet(boolean land) {
        int target = getSheetTravel(land);
        Logger.i("VideoLayout: settle-start land=" + land + " target=" + target);
        logVideoLayout("settle-start", target);
        if (land) {
            mBinding.swipeLayout.animate().translationX(target).setDuration(220).start();
            mBinding.handleLand.animate().translationX(target).setDuration(220).start();
            animateVideoWidth(mVideoBase + target, 220, () -> enterFullscreen(false));
        } else {
            mPortraitLock = true;
            mBinding.swipeLayout.animate().translationY(target).setDuration(220).start();
            mBinding.playerSurface.setTranslationY(0f);
            mBinding.video.animate().translationY(getPortraitVideoTranslation(target)).setDuration(220).withEndAction(() -> enterFullscreen(true)).start();
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
        traceTransition("rotate-click");
        // 用户手动选过方向，之后就一直算数，不要再按片源比例自动转回去。
        // 原来这里清成 false，导致用旋转按钮切到竖屏全屏后，
        // 下一集的 SIZE 事件一到 checkOrientation 就把屏幕转回横屏。
        mPortraitLock = true;
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void onTrack(View view) {
        showTrackPanel(view, Integer.parseInt(view.getTag().toString()));
    }

    private void showTrackPanel(View anchor, int type) {
        if (type == C.TRACK_TYPE_VIDEO) { showQualityPanel(anchor, false); return; }
        mQualityAnchor = null;
        List<Track> tracks = getTracks(type);
        if (tracks.isEmpty()) return;
        int[] ids = new int[tracks.size()];
        String[] labels = new String[tracks.size()];
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            ids[i] = i;
            labels[i] = type == C.TRACK_TYPE_VIDEO ? getQualityOptionLabel(tracks.get(i)) : tracks.get(i).getName();
            if (tracks.get(i).isSelected()) selected.add(i);
        }
        String title = ResUtil.getStringArray(R.array.select_track)[type - 1];
        mBinding.playbackPanel.show(anchor, title, ids, labels, null, toIntArray(selected), 232, PLAYER_PANEL_MAX_HEIGHT_DP, id -> {
            Track item = tracks.get(id);
            if (item.isAuto()) {
                Track.delete(mPlayers.getKey(), type);
                mPlayers.resetTrack(type);
                mBinding.playbackPanel.dismiss();
            } else {
                if (type == C.TRACK_TYPE_VIDEO) item.setSelected(true);
                else item.toggle();
                mPlayers.setTrack(Arrays.asList(item.key(mPlayers.getKey()).save()));
                if (item.isAdaptive() && type != C.TRACK_TYPE_VIDEO) showTrackPanel(anchor, type);
                else mBinding.playbackPanel.dismiss();
            }
            if (type == C.TRACK_TYPE_VIDEO) updateQualityLabel();
            setR1Callback();
        });
        refreshBackHandling();
        App.removeCallbacks(mR1);
    }

    private void captureSourceProgress() {
        if (mHistory == null) return;
        if (mHistoryPlaybackConfirmed && mPlayers.getPosition() >= 0 && mPlayers.getDuration() > 0) {
            mHistory.setPosition(mPlayers.getPosition()); mHistory.setDuration(mPlayers.getDuration());
            savePlaybackProgress();
        }
        mPendingResumeHistory = History.objectFrom(mHistory.toString());
        mPendingResumeHistory.setAccountId(mHistory.getAccountId());
    }

    private boolean canChooseSource() {
        return mCurrentVod != null && mHistory != null && !isOffline() && !isCasting()
                && mPlayingDownload == null && !OfflinePlayback.isLocal(mPlayers.getUrl());
    }

    private void showSourcePanel(View anchor) {
        if (!canChooseSource()) return;
        mQualityAnchor = null;
        mSourceAnchor = anchor;
        mSourceProbeUntil = SystemClock.elapsedRealtime() + 45000;
        prepareQualityCatalog(mHistory.getVodRemarks());
        if (mQualityCatalog != null) {
            mQualityCatalog.budget(0);
            mQualityCatalog.refreshMeasurements(false, mSourceAdapter.getCurrent(), getFlag() == null ? "" : getFlag().getFlag());
            mQualityCatalog.budget(1);
        }
        renderSourcePanel(anchor, false);
        ensureSourceDiscovery();
        Logger.i("SourceChoice: action=open session=" + mSessionId + " source=" + getKey()
                + " line=" + (getFlag() == null ? "" : getFlag().getFlag()) + " episode=" + mPlaybackEpisode);
        refreshBackHandling(); App.removeCallbacks(mR1);
        checkQuick();
    }

    private String currentSourceRoute() {
        return getKey() + "\n" + getId() + "\n" + (getFlag() == null ? "" : getFlag().getFlag())
                + "\n" + mQualityAdapter.getPosition();
    }

    private List<com.fongmi.android.tv.search.QualityOption> sourceOptions() {
        List<com.fongmi.android.tv.search.QualityOption> values = mQualityCatalog == null ? new ArrayList<>() : mQualityCatalog.items();
        values.removeIf(item -> {
            VodSource live = mSourceAdapter.find(item.source.getSiteKey(), item.source.getVodId());
            return live != null && live.isBroken();
        });
        return com.fongmi.android.tv.search.SourceSelection.routes(values, System.currentTimeMillis());
    }

    private String sourceMetrics(com.fongmi.android.tv.search.QualityOption item, boolean measuring) {
        if (item == null || item.measuredAt <= 0) return measuring ? "测速中…" : "暂无测速结果";
        boolean fresh = com.fongmi.android.tv.search.SourceSelection.fresh(item.measuredAt, System.currentTimeMillis());
        String latency = item.latencyMs >= 0 ? "响应 " + item.latencyMs + " ms" : "响应未测得";
        String speed = item.speed > 0 ? String.format(Locale.ROOT, "%.2f MB/s", item.speed / (1024d * 1024d)) : "速度未测得";
        return (fresh ? "" : "上次：") + latency + " · " + speed;
    }

    private void renderSourcePanel(View anchor, boolean update) {
        if (!canChooseSource()) return;
        List<com.fongmi.android.tv.search.QualityOption> routes = sourceOptions();
        boolean measuring = mQualityCatalog != null && mQualityCatalog.isInspecting()
                && SystemClock.elapsedRealtime() < mSourceProbeUntil;
        com.fongmi.android.tv.search.QualityOption current = null;
        for (com.fongmi.android.tv.search.QualityOption item : routes) if (item.route().equals(currentSourceRoute())) { current = item; break; }
        routes.removeIf(item -> item.route().equals(currentSourceRoute()));
        List<VodSource> unmeasured = new ArrayList<>();
        for (VodSource source : mSourceAdapter.getRanked()) {
            if (source.isBroken() || source.getSite().isCloudDrive() || !source.getSite().isChangeable()
                    || !VodConfig.isSiteEnabled(source.getSite()) || source.same(getKey(), getId())) continue;
            boolean measured = false;
            for (com.fongmi.android.tv.search.QualityOption item : routes) if (item.source.same(source.getSiteKey(), source.getVodId())) { measured = true; break; }
            if (!measured) unmeasured.add(source);
        }
        int[] ids = new int[3 + routes.size() + unmeasured.size()];
        String[] labels = new String[ids.length], subtitles = new String[ids.length];
        ids[0] = 0; ids[1] = -1; ids[2] = -2;
        labels[0] = getSite().getName() + (getFlag() == null ? "" : " / " + getFlag().getFlag());
        String health = !mLoggedPlaybackReady ? "正在连接" : "缓冲 " + Math.max(0, mPlayers.getBuffered() - mPlayers.getPosition()) / 1000 + " 秒"
                + (mCurrentStartupMs >= 0 ? String.format(Locale.ROOT, " · 起播 %.1f 秒", mCurrentStartupMs / 1000d) : "");
        subtitles[0] = health + "\n" + sourceMetrics(current, measuring);
        labels[1] = "自动找源";
        subtitles[1] = "";
        labels[2] = "重新测速";
        subtitles[2] = "";
        boolean recommended = false;
        long now = System.currentTimeMillis();
        for (int i = 0; i < routes.size(); i++) {
            com.fongmi.android.tv.search.QualityOption item = routes.get(i);
            int row = i + 3; ids[row] = row;
            int grade = com.fongmi.android.tv.search.SourceSelection.grade(item.speed, item.bitrate, item.measuredAt, now);
            boolean recommend = !recommended && grade < 2;
            recommended |= recommend;
            labels[row] = (recommend ? "推荐 · " : "") + item.source.getSiteName() + " / " + item.flag.getFlag();
            subtitles[row] = sourceMetrics(item, measuring && item.measuredAt <= 0) + "\n" + (grade == 0 ? "速度有余量" : grade == 3 ? "速度可能不足，容易缓冲"
                    : grade == 1 ? "已测速度，码率未知" : "等待本次测速");
        }
        for (int i = 0; i < unmeasured.size(); i++) {
            int row = 3 + routes.size() + i; ids[row] = row;
            labels[row] = unmeasured.get(i).getSiteName();
            subtitles[row] = "尚未测速 · 可手动尝试";
        }
        com.fongmi.android.tv.ui.custom.PlaybackGlassPanelView.OnItemClickListener click = id -> {
            if (mDetailLoading || !canChooseSource()) return;
            if (id == -2) {
                mSourceProbeUntil = SystemClock.elapsedRealtime() + 45000;
                prepareQualityCatalog(mHistory.getVodRemarks());
                if (mQualityCatalog != null) {
                    mQualityCatalog.budget(0);
                    mQualityCatalog.refreshMeasurements(true, mSourceAdapter.getCurrent(), getFlag() == null ? "" : getFlag().getFlag());
                    mQualityCatalog.budget(sourceProbeBudget());
                }
                ensureSourceDiscovery();
                Logger.i("SourceChoice: action=retest session=" + mSessionId + " episode=" + mPlaybackEpisode);
                renderSourcePanel(anchor, true);
                return;
            }
            mBinding.playbackPanel.dismiss();
            if (id == -1) recoverPlayback("source-request", true, false);
            else if (id >= 3 && id < 3 + routes.size()) {
                com.fongmi.android.tv.search.QualityOption item = routes.get(id - 3);
                Logger.i("SourceChoice: action=select session=" + mSessionId + " source=" + item.source.getSiteKey()
                        + " line=" + item.flag.getFlag() + " latencyMs=" + item.latencyMs + " bytesPerSecond=" + item.speed
                        + " measuredAt=" + item.measuredAt + " episode=" + mPlaybackEpisode);
                mRecoveryPending = true; mRecoveryManual = true; mRecoveryReason = "source-selection";
                selectQuality(item, mAutomaticQuality);
            } else if (id >= 3 + routes.size()) onItemClick(unmeasured.get(id - 3 - routes.size()));
            setR1Callback();
        };
        if (update) mBinding.playbackPanel.updateSources("换源 · 流畅优先", ids, labels, subtitles, new int[]{0}, click);
        else mBinding.playbackPanel.showSources(anchor, "换源 · 流畅优先", ids, labels, subtitles, new int[]{0}, click);
    }

    private int sourceProbeBudget() {
        long buffered = mPlayers == null ? 0 : mPlayers.getBuffered() - mPlayers.getPosition();
        return buffered >= 25000 ? 4 : buffered >= 10000 ? 2 : 1;
    }

    private void ensureSourceDiscovery() {
        if (mSourceTask == null) startSourceSearch(false);
        else {
            mSourceTask.resume();
            if (mSourceTask.isFinished() && mSourceTask.hasMore()) mSourceTask.searchMore();
        }
    }

    private boolean isQualityMovie() {
        if (mHistory != null && mHistory.getEpisodeCount() > 1) return false;
        if (mCurrentVod != null) for (Flag flag : mCurrentVod.getVodFlags()) if (flag.getEpisodes().size() > 1) return false;
        if (mTargetKind == TitleKey.KIND_UNKNOWN && mCurrentVod != null)
            for (Flag flag : mCurrentVod.getVodFlags()) if (flag.getEpisodes().size() == 1
                    && com.fongmi.android.tv.search.EpisodeKey.movieLabel(flag.getEpisodes().get(0).getName())) return true;
        return mQualityCatalog != null && mQualityCatalog.isMovie()
                || mTargetKind == TitleKey.KIND_MOVIE;
    }

    private void prepareQualityCatalog(String episode) {
        if (mCurrentVod == null || isOffline()) return;
        if (mQualityCatalog == null) {
            mQualityCatalog = new com.fongmi.android.tv.search.QualityCatalog(this::onQualityCatalogChanged, values -> {
                for (VodSource source : values) if (!mSourceAdapter.contains(source.getSiteKey(), source.getVodId()) && isTarget(source.getVod()))
                    mSourceAdapter.add(source);
                if (mHistory != null) prepareQualityCatalog(mHistory.getVodRemarks());
                updateSourceView();
            });
            History film = mFilmIdentity == null ? mHistory : mFilmIdentity;
            mQualityCatalog.film(film == null ? "" : film.getFilmId(), film == null ? getName() : film.getVodName(),
                    film == null ? getYear() : film.getVodYear(), film == null ? mCurrentVod.getTypeName() : film.getVodType(),
                    film == null ? "" : film.getSourceKeys());
        }
        String identity = isQualityMovie() ? "movie" : com.fongmi.android.tv.search.EpisodeKey.key(episode);
        mQualityCatalog.episode(identity);
        mQualityCatalog.setActive(!isStop());
        mQualityCatalog.budget(qualityWorkBudget());
        List<VodSource> sources = mSourceAdapter.getRanked();
        VodSource current = mSourceAdapter.getCurrent(); if (current != null) sources.add(0, current);
        for (VodSource source : sources)
            mQualityCatalog.inspect(source, source.same(getKey(), getId()) ? mCurrentVod : null, episode);
        App.removeCallbacks(mQualityMaintenance); App.post(mQualityMaintenance, 3000);
    }

    private int qualityWorkBudget() {
        if (isStop() || !Util.isNetworkAvailable() || isCasting()) return 0;
        boolean requested = SystemClock.elapsedRealtime() < mSourceProbeUntil
                && (mSourceAnchor != null && mBinding.playbackPanel.isPanelVisible() || mRecoveryPending && mRecoveryManual);
        if (requested) return 1;
        if (!mPlaybackWanted) return 0;
        if (mInitialSelection) return 2;
        if (!mPlayers.isReady()) return mRecoveryPending || !mLoggedPlaybackReady && SystemClock.elapsedRealtime() - mPlaybackRequestedAt >= 4000 ? 2 : 0;
        long buffered = mPlayers.getBuffered() - mPlayers.getPosition();
        return buffered >= 25000 ? 4 : buffered >= 10000 ? 2 : mRecoveryPending ? 1 : 0;
    }

    private void maintainQualityWork() {
        if (isFinishing() || isDestroyed() || isStop() || mQualityCatalog == null) return;
        int budget = qualityWorkBudget();
        mQualityCatalog.budget(budget);
        if (mSourceBackground && mSourceTask != null) {
            if (budget == 0) mSourceTask.pause(); else mSourceTask.resume();
        }
        com.fongmi.android.tv.search.QualityOption current = currentVerifiedQuality();
        if (current != null) mQualityCatalog.record(current);
        if (mSourceAnchor != null && mBinding.playbackPanel.isPanelVisible()) renderSourcePanel(mSourceAnchor, true);
        if (mSourceBackground && mSourceTask != null && mSourceTask.isFinished() && mSourceTask.hasMore()
                && qualityWorkBudget() > 0) mSourceTask.searchMore();
        App.removeCallbacks(mQualityMaintenance); App.post(mQualityMaintenance, 3000);
    }

    private void onQualityCatalogChanged() {
        if (isFinishing() || isDestroyed() || mQualityCatalog == null) return;
        boolean learned = false;
        for (com.fongmi.android.tv.search.QualityOption option : mQualityCatalog.items()) {
            VodSource live = mSourceAdapter.find(option.source.getSiteKey(), option.source.getVodId());
            if (live != null && option.verified) live.setResult(VodSource.OK, Math.max(live.getSpeed(), option.speed));
            if (mHistory != null && option.verified && option.isAvailable()) {
                String old = mHistory.getSourceKeys();
                com.fongmi.android.tv.search.HistoryIdentity.remember(mHistory, sourceHistoryKey(option.source));
                learned |= !old.equals(mHistory.getSourceKeys());
            }
        }
        mSourceAdapter.sort();
        if (learned && mHistoryPlaybackConfirmed) queueHistorySnapshot(false, false);
        if (restoreSavedQuality()) return;
        if (mInitialSelection && !playbackQualities().isEmpty()) finishInitialSelection();
        else if (mRecoveryPending) recoverPlayback(mRecoveryReason, mRecoveryManual, false);
        if (mQualityAnchor != null && mBinding.playbackPanel.isPanelVisible()) showQualityPanel(mQualityAnchor, true);
        else if (mSourceAnchor != null && mBinding.playbackPanel.isPanelVisible()) renderSourcePanel(mSourceAnchor, true);
    }

    /** 历史只保存清晰度档位；恢复时重新从当前仍可用的线路里选该档位的最优项。 */
    private boolean restoreSavedQuality() {
        if (!mQualityRestorePending || mAutomaticQuality || mQualityHeight <= 0 || !mLoggedPlaybackReady
                || mDetailLoading || mInitialSelection) return false;
        com.fongmi.android.tv.search.QualityOption current = currentVerifiedQuality();
        if (current != null && current.tierHeight() == mQualityHeight) {
            mQualityRestorePending = false;
            Logger.i("QualityPreference: action=keep session=" + mSessionId + " tier=" + mQualityHeight
                    + " source=" + getKey() + " actual=" + current.rank());
            applyQualityTrack();
            return false;
        }
        for (com.fongmi.android.tv.search.QualityOption item : verifiedQualities()) {
            if (item.tierHeight() != mQualityHeight) continue;
            mQualityRestorePending = false;
            Logger.i("QualityPreference: action=restore session=" + mSessionId + " tier=" + mQualityHeight
                    + " source=" + item.source.getSiteKey() + " actual=" + item.rank());
            selectQuality(item, false);
            return true;
        }
        return false;
    }

    private com.fongmi.android.tv.search.QualityOption currentVerifiedQuality() {
        if (!mLoggedPlaybackReady || mDetailLoading || mCurrentVod == null || mPlayers.get() == null
                || mFlagAdapter.isEmpty() || mEpisodeAdapter.isEmpty() || getFlag() == null || getEpisode() == null) return null;
        androidx.media3.common.VideoSize size = mPlayers.get().getVideoSize();
        VodSource current = mSourceAdapter.getCurrent();
        if (current == null || size.width <= 0 || size.height <= 0) return null;
        androidx.media3.common.Format format = mPlayers.get().getVideoFormat();
        com.fongmi.android.tv.search.QualityOption measured = null;
        if (mQualityCatalog != null) for (com.fongmi.android.tv.search.QualityOption option : mQualityCatalog.items())
            if (option.route().equals(currentSourceRoute()) && option.width == size.width && option.height == size.height
                    && (measured == null || option.measuredAt > measured.measuredAt)) measured = option;
        com.fongmi.android.tv.search.QualityOption item = new com.fongmi.android.tv.search.QualityOption(current, mCurrentVod, getFlag(), getEpisode(), "",
                mQualityAdapter.getPosition(), size.width, size.height, true, measured == null ? 0 : measured.speed,
                format != null && format.bitrate > 0 ? format.bitrate : measured == null ? 0 : measured.bitrate);
        if (measured != null) { item.latencyMs = measured.latencyMs; item.measuredAt = measured.measuredAt; }
        item.verifiedUrl = mPlayers.getUrl(); item.verifiedHeaders = new HashMap<>(mPlayers.getHeaders());
        return item;
    }

    private List<com.fongmi.android.tv.search.QualityOption> verifiedQualities() {
        List<com.fongmi.android.tv.search.QualityOption> values = mQualityCatalog == null ? new ArrayList<>() : mQualityCatalog.items();
        com.fongmi.android.tv.search.QualityOption current = currentVerifiedQuality();
        if (current != null) {
            values.add(current);
            addCurrentTrackQualities(values, current);
        }
        values.removeIf(item -> {
            VodSource live = mSourceAdapter.find(item.source.getSiteKey(), item.source.getVodId());
            return live != null && live.isBroken();
        });
        boolean healthy = mPlayers.isReady() && mPlayers.getBuffered() - mPlayers.getPosition() >= 10000;
        return com.fongmi.android.tv.search.QualitySelection.tiers(values, current == null ? "" : current.identity(), healthy);
    }

    /** Adaptive manifests expose selectable decoder tracks before background source probes finish. */
    private void addCurrentTrackQualities(List<com.fongmi.android.tv.search.QualityOption> values,
                                          com.fongmi.android.tv.search.QualityOption current) {
        if (mPlayers.get() == null || current == null) return;
        for (Tracks.Group group : mPlayers.get().getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                androidx.media3.common.Format format = group.getTrackFormat(i);
                if (!group.isTrackSupported(i) || format.width <= 0 || format.height <= 0) continue;
                com.fongmi.android.tv.search.QualityOption option = new com.fongmi.android.tv.search.QualityOption(
                        current.source, mCurrentVod, getFlag(), getEpisode(), current.valueName, current.valueIndex,
                        format.width, format.height, true, current.speed, format.bitrate > 0 ? format.bitrate : current.bitrate);
                option.latencyMs = current.latencyMs; option.measuredAt = current.measuredAt;
                option.verifiedUrl = mPlayers.getUrl(); option.verifiedHeaders = new HashMap<>(mPlayers.getHeaders());
                values.add(option);
            }
        }
    }

    private void selectQuality(com.fongmi.android.tv.search.QualityOption item, boolean automatic) {
        mAutomaticQuality = automatic; mSelectedQuality = item;
        if (automatic) mQualityHeight = 0;
        else if (mQualityHeight <= 0 && item.verified) mQualityHeight = item.tierHeight();
        mQualityAnchor = null; mBinding.playbackPanel.dismiss();
        if (item.source.same(getKey(), getId()) && getFlag() != null
                && item.flag.getFlag().equals(getFlag().getFlag()) && item.valueIndex == mQualityAdapter.getPosition()
                && mPlayers.get() != null && mLoggedPlaybackReady
                && (hasQualityTrack(item) || mPlayers.get().getVideoSize().width == item.width
                    && mPlayers.get().getVideoSize().height == item.height)) {
            if (automatic && mRecoveryPending) { mAutoWidth = item.width; mAutoHeight = item.height; }
            if (mRecoveryPending) recordPlaybackSwitch(item.source, mRecoveryReason, mRecoveryManual);
            mPlaybackPolicy.adjusted(SystemClock.elapsedRealtime());
            mRecoveryPending = false;
            applyQualityTrack(); updateQualityLabel(); return;
        }
        // Reload the selected provider's own catalog, preserving semantic episode and playback time.
        captureSourceProgress();
        recordPlaybackSwitch(item.source, mRecoveryPending ? mRecoveryReason : automatic ? "initial-selection" : "quality-selection", mRecoveryManual && mRecoveryPending);
        mRecoveryPending = false;
        if (mSourceTask != null && !mSourceTask.isBackground()) stopSourceSearch();
        mSourceBackground = true;
        mSourceAdapter.setCurrent(item.source);
        mManualSourceSelection = true; setInitAuto(false);
        getDetail(item.source.getVod());
    }

    private boolean hasQualityTrack(com.fongmi.android.tv.search.QualityOption item) {
        if (mPlayers.get() == null) return false;
        for (Tracks.Group group : mPlayers.get().getCurrentTracks().getGroups()) if (group.getType() == C.TRACK_TYPE_VIDEO)
            for (int i = 0; i < group.length; i++) if (group.isTrackSupported(i)
                    && group.getTrackFormat(i).width == item.width && group.getTrackFormat(i).height == item.height) return true;
        return false;
    }

    private String sourceHistoryKey(VodSource source) {
        return source.getSiteKey() + AppDatabase.SYMBOL + source.getVodId() + AppDatabase.SYMBOL + source.getSite().getSourceId();
    }

    private String sourceRoute(String key, String id) { return "provider\n" + key + "\n" + id; }

    private String playbackRoute() {
        com.fongmi.android.tv.search.QualityOption current = currentVerifiedQuality();
        return current == null ? sourceRoute(getKey(), getId()) + "\n" + (getFlag() == null ? "" : getFlag().getFlag()) : current.identity();
    }

    private List<com.fongmi.android.tv.search.QualityOption> playbackQualities() {
        List<com.fongmi.android.tv.search.QualityOption> values = mQualityCatalog == null ? new ArrayList<>() : mQualityCatalog.items();
        long now = SystemClock.elapsedRealtime();
        values.removeIf(item -> {
            VodSource live = mSourceAdapter.find(item.source.getSiteKey(), item.source.getVodId());
            return !item.verified || !item.canAuto() || live != null && live.isBroken()
                    || !mPlaybackPolicy.available(item.identity(), now)
                    || !mPlaybackPolicy.available(sourceRoute(item.source.getSiteKey(), item.source.getVodId()), now);
        });
        return values;
    }

    private boolean acceptsDetail(Vod vod) {
        if (mFilmIdentity == null || mFilmIdentity.getVodName() == null || mFilmIdentity.getVodName().isEmpty()) return true;
        if (!com.fongmi.android.tv.search.HistoryIdentity.sameTitle(mFilmIdentity.getVodName(), vod.getVodName(getName()))) return false;
        String key = vod.getSiteKey().isEmpty() ? getKey() : vod.getSiteKey();
        String id = vod.getVodId().isEmpty() ? getId() : vod.getVodId();
        String provider = key + AppDatabase.SYMBOL + id + AppDatabase.SYMBOL
                + (vod.getSite() == null ? getSite().getSourceId() : vod.getSite().getSourceId());
        String alias = com.fongmi.android.tv.search.HistoryIdentity.sourceToken(provider);
        boolean learned = com.fongmi.android.tv.search.FilmIdentity.aliases(mFilmIdentity.getSourceKeys()).contains(alias);
        int kind = TitleKey.kind(vod.getTypeName()), count = 0;
        for (Flag flag : vod.getVodFlags()) if (!flag.isCloudDrive() && !PlaybackRoutePolicy.isRestricted(flag))
            count = Math.max(count, flag.getEpisodes().size());
        if (count > 1 && kind == TitleKey.KIND_MOVIE) kind = TitleKey.KIND_UNKNOWN;
        return learned || TitleKey.sameYear(mTargetYear, TitleKey.year(vod.getVodYear())) && TitleKey.sameKind(mTargetKind, kind);
    }

    private void finishInitialSelection() {
        if (!mInitialSelection || isFinishing() || isDestroyed() || isStop()) return;
        App.removeCallbacks(mFinishInitialSelection);
        mInitialSelection = false; mInitialSelectionDone = true;
        if (mQualityCatalog != null) mQualityCatalog.budget(0);
        Flag flag = mInitialFlag; mInitialFlag = null;
        com.fongmi.android.tv.search.QualityOption best = bestMeasuredSource(playbackQualities());
        Logger.i("PlayDecision: action=initial-result session=" + mSessionId + " verified=" + (best != null)
                + " source=" + (best == null ? getKey() : best.source.getSiteKey()));
        if (best != null && !best.source.same(getKey(), getId())) { selectQuality(best, true); return; }
        if (best != null) {
            mSelectedQuality = best;
            Flag preferred = mFlagAdapter.find(best.flag.getFlag());
            if (preferred != null) flag = preferred;
        }
        if (flag != null) {
            selectFlag(flag, false);
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
    }

    private void watchPlayback() {
        if (isFinishing() || isDestroyed() || isStop()) return;
        if (mRecoveryPending && mRecoveryManual && "source-request".equals(mRecoveryReason)
                && SystemClock.elapsedRealtime() >= mSourceProbeUntil) {
            mRecoveryPending = false;
            Notify.show("暂未测到更流畅的源，可在换源中手动选择或重新测速");
            Logger.i("SourceChoice: action=no-measured-alternative session=" + mSessionId + " episode=" + mPlaybackEpisode);
        }
        if (mPlayers != null && mHistory != null && !mInitialSelection && !mDetailLoading) {
            long now = SystemClock.elapsedRealtime();
            boolean excluded = isCasting() || mPlayingDownload != null || OfflinePlayback.isLocal(mPlayers.getUrl())
                    || mAwaitingCloudSync || mCloudChoicePending || mScrubbing || mGestureSeeking || !Util.isNetworkAvailable();
            boolean buffering = mPlayers.get() != null && mPlayers.get().getPlaybackState() == Player.STATE_BUFFERING;
            String reason = mPlaybackPolicy.reason(now, mPlaybackWanted, excluded, mPlayers.isReady(), buffering,
                    Math.max(0, mPlayers.getBuffered() - mPlayers.getPosition()));
            if (mRecoveryPending && mRecoveryManual && "source-request".equals(mRecoveryReason) && !excluded)
                recoverPlayback(mRecoveryReason, true, false);
            else if (!reason.isEmpty()) recoverPlayback(reason, false, false);
            if (now - mLastHealthLog >= 30000 && mHistoryPlaybackConfirmed) {
                mLastHealthLog = now;
                Logger.i("PlayHealth: session=" + mSessionId + " film=" + mHistory.getFilmId() + " episode=" + mPlaybackEpisode
                        + " source=" + getKey() + " requested=" + mPlaybackWanted + " ready=" + mPlayers.isReady()
                        + " positionMs=" + mPlayers.getPosition() + " bufferMs=" + Math.max(0, mPlayers.getBuffered() - mPlayers.getPosition())
                        + " stalls=" + mPlaybackPolicy.stallCount() + " stalledMs=" + mPlaybackPolicy.stalledMs());
            }
        }
        App.post(mPlaybackWatchdog, 1000);
    }

    private void recordPlaybackSwitch(VodSource next, String reason, boolean manual) {
        long now = SystemClock.elapsedRealtime();
        mPlaybackPolicy.switched(playbackRoute(), now);
        if (!next.same(getKey(), getId())) mPlaybackPolicy.failed(sourceRoute(getKey(), getId()), now);
        if (manual) mManualSwitches++; else if (!"initial-selection".equals(reason)) mAutomaticSwitches++;
        Logger.i("PlayDecision: action=switch session=" + mSessionId + " film=" + (mHistory == null ? "" : mHistory.getFilmId())
                + " reason=" + reason + " from=" + getKey() + " to=" + next.getSiteKey()
                + " episode=" + (mHistory == null ? "" : mHistory.getVodRemarks())
                + " positionMs=" + (mHistory == null ? -1 : mHistory.getPosition()) + " speed=" + next.getSpeed());
    }

    private boolean lowerCurrentTrack(String reason) {
        if (!mAutomaticQuality || mPlayers.get() == null || !mLoggedPlaybackReady) return false;
        androidx.media3.common.VideoSize current = mPlayers.get().getVideoSize();
        int currentHeight = Math.min(current.width, current.height);
        androidx.media3.common.Format lower = null;
        for (Tracks.Group group : mPlayers.get().getCurrentTracks().getGroups()) if (group.getType() == C.TRACK_TYPE_VIDEO)
            for (int i = 0; i < group.length; i++) {
                androidx.media3.common.Format format = group.getTrackFormat(i);
                int height = Math.min(format.width, format.height);
                if (group.isTrackSupported(i) && height > 0 && height < currentHeight
                        && (lower == null || height > Math.min(lower.width, lower.height))) lower = format;
            }
        if (lower == null || mAutoWidth == lower.width && mAutoHeight == lower.height) return false;
        mPlaybackPolicy.switched(playbackRoute(), SystemClock.elapsedRealtime());
        mAutoWidth = lower.width; mAutoHeight = lower.height; mAutomaticSwitches++;
        Logger.i("PlayDecision: action=lower-track session=" + mSessionId + " reason=" + reason
                + " fromHeight=" + currentHeight + " width=" + lower.width + " height=" + lower.height);
        mPlaybackPolicy.adjusted(SystemClock.elapsedRealtime()); applyQualityTrack(); mRecoveryPending = false;
        return true;
    }

    private boolean recoverPlayback(String reason, boolean manual, boolean hardFailure) {
        if (!manual && !mPlaybackWanted) return false;
        if (mHistory == null || mCurrentVod == null || isFinishing() || isDestroyed() || isStop() || isCasting()
                || mAwaitingCloudSync || mCloudChoicePending || !Util.isNetworkAvailable() || mPlayingDownload != null
                || OfflinePlayback.isLocal(mPlayers.getUrl())) return false;
        if (mDetailLoading || mInitialSelection) return false;
        if (mScrubbing || mGestureSeeking) return false;
        long now = SystemClock.elapsedRealtime();
        if (!mPlaybackPolicy.canSwitch(now, manual, hardFailure)) return false;
        if (!manual && !hardFailure && lowerCurrentTrack(reason)) return true;
        boolean newRequest = !mRecoveryPending || !mRecoveryReason.equals(reason);
        mRecoveryPending = true; mRecoveryReason = reason; mRecoveryManual = manual;
        if ("source-request".equals(reason)) {
            List<com.fongmi.android.tv.search.QualityOption> alternatives = playbackQualities();
            alternatives.removeIf(item -> item.route().equals(currentSourceRoute()));
            com.fongmi.android.tv.search.QualityOption best = bestMeasuredSource(alternatives);
            if (best != null) {
                Logger.i("SourceChoice: action=recommend session=" + mSessionId + " source=" + best.source.getSiteKey()
                        + " line=" + best.flag.getFlag() + " latencyMs=" + best.latencyMs + " bytesPerSecond=" + best.speed);
                selectQuality(best, mAutomaticQuality);
                return true;
            }
            if (newRequest) {
                mSourceProbeUntil = now + 45000;
                prepareQualityCatalog(mHistory.getVodRemarks());
                if (mQualityCatalog != null) {
                    mQualityCatalog.budget(0);
                    mQualityCatalog.refreshMeasurements(true, mSourceAdapter.getCurrent(), getFlag() == null ? "" : getFlag().getFlag());
                }
                Notify.show(R.string.play_finding_source);
            }
            if (mSourceTask != null) {
                mSourceTask.resume();
                if (mSourceTask.isFinished() && mSourceTask.hasMore()) mSourceTask.searchMore();
            } else startSourceSearch(false);
            if (mQualityCatalog != null) mQualityCatalog.budget(1);
            return false;
        }
        String currentRoute = playbackRoute();
        List<com.fongmi.android.tv.search.QualityOption> values = playbackQualities();
        values.removeIf(item -> item.identity().equals(currentRoute)
                || item.source.same(getKey(), getId()) && getFlag() != null && item.flag.equals(getFlag())
                    && item.valueIndex == mQualityAdapter.getPosition() && (!mLoggedPlaybackReady || !hasQualityTrack(item)));
        // Fixed quality selections are preferences for a tier, not for a particular provider.
        if (!mAutomaticQuality && mQualityHeight > 0) {
            int requested = mQualityHeight;
            final int tier = requested <= 480 ? 480 : requested <= 720 ? 720 : requested <= 1080 ? 1080 : requested <= 1440 ? 1440 : requested <= 2160 ? 2160 : 4320;
            values.removeIf(item -> item.identity().equals(currentRoute) || item.tierHeight() != tier);
        }
        if (manual) {
            List<com.fongmi.android.tv.search.QualityOption> others = new ArrayList<>(values);
            others.removeIf(item -> item.source.same(getKey(), getId()));
            if (!others.isEmpty()) values = others;
        }
        com.fongmi.android.tv.search.QualityOption best = bestMeasuredSource(values);
        if (best == null) best = com.fongmi.android.tv.search.QualitySelection.best(values, "", false);
        captureSourceProgress();
        if (best != null) {
            if (manual) Notify.show(R.string.play_changing_source);
            selectQuality(best, mAutomaticQuality); return true;
        }
        for (VodSource source : mSourceAdapter.getRanked()) {
            if (source.isBroken() || source.getSite().isCloudDrive() || !source.getSite().isChangeable()
                    || !mPlaybackPolicy.available(sourceRoute(source.getSiteKey(), source.getVodId()), now)) continue;
            if (manual) Notify.show(R.string.play_changing_source);
            switchSource(source, true); return true;
        }
        // Keep a still working foreground player while discovering a usable same-episode alternative.
        if (mSourceTask != null) {
            mSourceTask.resume();
            if (mSourceTask.isFinished() && mSourceTask.hasMore()) mSourceTask.searchMore();
        } else startSourceSearch(false);
        if (mQualityCatalog != null) mQualityCatalog.budget(2);
        if (manual && newRequest) Notify.show(R.string.play_finding_source);
        if (newRequest) Logger.i("PlayDecision: action=wait-alternative session=" + mSessionId + " reason=" + reason
                + " episode=" + mPlaybackEpisode + " candidates=" + mSourceAdapter.getItemCount());
        return false;
    }

    private com.fongmi.android.tv.search.QualityOption bestMeasuredSource(List<com.fongmi.android.tv.search.QualityOption> values) {
        long now = System.currentTimeMillis();
        for (com.fongmi.android.tv.search.QualityOption item : com.fongmi.android.tv.search.SourceSelection.routes(values, now))
            if (com.fongmi.android.tv.search.SourceSelection.grade(item.speed, item.bitrate, item.measuredAt, now) < 2) return item;
        return null;
    }

    private void applyQualityTrack() {
        if (mPlayers.get() == null) return;
        androidx.media3.common.TrackSelectionParameters.Builder builder = mPlayers.get().getTrackSelectionParameters().buildUpon();
        if (mAutomaticQuality) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO).clearVideoSizeConstraints().setMaxVideoBitrate(Integer.MAX_VALUE)
                    .setForceHighestSupportedBitrate(false).setForceLowestBitrate(false);
            if (mAutoWidth > 0 && mAutoHeight > 0) builder.setMaxVideoSize(mAutoWidth, mAutoHeight);
            mPlayers.get().setTrackSelectionParameters(builder.build());
            return;
        }
        if (mQualityHeight <= 0) return;
        List<Tracks.Group> groups = mPlayers.get().getCurrentTracks().getGroups();
        Tracks.Group selectedGroup = null; int selectedTrack = -1; int selectedHeight = -1;
        for (Tracks.Group group : groups) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                androidx.media3.common.Format format = group.getTrackFormat(i);
                int height = Math.min(format.width, format.height);
                if (group.isTrackSupported(i) && com.fongmi.android.tv.search.QualityOption.tierHeight(height) == mQualityHeight
                        && height > selectedHeight) {
                    selectedGroup = group; selectedTrack = i; selectedHeight = height;
                }
            }
        }
        if (selectedGroup != null) mPlayers.get().setTrackSelectionParameters(builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .clearVideoSizeConstraints().setMaxVideoBitrate(Integer.MAX_VALUE)
                .setForceHighestSupportedBitrate(false).setForceLowestBitrate(false)
                .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(selectedGroup.getMediaTrackGroup(), selectedTrack)).build());
    }

    private void showQualityPanel(View anchor, boolean update) {
        mSourceAnchor = null;
        mQualityAnchor = anchor;
        if (mHistory != null) prepareQualityCatalog(mHistory.getVodRemarks());
        List<com.fongmi.android.tv.search.QualityOption> choices = verifiedQualities();
        List<String> labels = new ArrayList<>(); List<Integer> selected = new ArrayList<>();
        labels.add("自动 · 流畅优先"); if (mAutomaticQuality) selected.add(0);
        com.fongmi.android.tv.search.QualityOption current = currentVerifiedQuality();
        for (com.fongmi.android.tv.search.QualityOption item : choices) {
            labels.add(item.label());
            if (!mAutomaticQuality && (mSelectedQuality != null && mSelectedQuality.tierHeight() == item.tierHeight()
                    || mSelectedQuality == null && current != null && current.tierHeight() == item.tierHeight())) selected.add(labels.size()-1);
        }
        int[] ids = new int[labels.size()]; for (int i=0;i<ids.length;i++) ids[i]=i;
        com.fongmi.android.tv.ui.custom.PlaybackGlassPanelView.OnItemClickListener listener = id -> {
            mQualityAnchor = null; mBinding.playbackPanel.dismiss();
            if (id == 0) {
                rememberQualityPreference(0);
                mSelectedQuality = null; mAutoWidth = 0; mAutoHeight = 0;
                Track.delete(mPlayers.getKey(), C.TRACK_TYPE_VIDEO); mPlayers.resetTrack(C.TRACK_TYPE_VIDEO);
                // Explicitly selecting Auto may choose one sustainable candidate; passive discovery never switches.
                boolean healthy = mPlayers.isReady() && mPlayers.getBuffered() - mPlayers.getPosition() >= 10000;
                com.fongmi.android.tv.search.QualityOption best = com.fongmi.android.tv.search.QualitySelection.best(
                        playbackQualities(), current == null ? "" : current.identity(), healthy);
                if (best != null && (current == null || !best.identity().equals(current.identity()))) selectQuality(best, true);
                else applyQualityTrack();
            } else {
                com.fongmi.android.tv.search.QualityOption choice = choices.get(id - 1);
                rememberQualityPreference(choice.tierHeight());
                selectQuality(choice, false);
            }
            updateQualityLabel(); setR1Callback();
        };
        String title = "清晰度";
        if (update) mBinding.playbackPanel.updateItems(title, ids, labels.toArray(new String[0]), toIntArray(selected), listener);
        else mBinding.playbackPanel.show(anchor, title, ids, labels.toArray(new String[0]), null, toIntArray(selected), 248, PLAYER_PANEL_MAX_HEIGHT_DP, listener);
        refreshBackHandling(); App.removeCallbacks(mR1);
    }

    private void rememberQualityPreference(int height) {
        int tier = com.fongmi.android.tv.search.QualityOption.tierHeight(height);
        mQualityPreferenceLoaded = true; mQualityRestorePending = false;
        mAutomaticQuality = tier == 0; mQualityHeight = tier;
        if (mHistory != null) mHistory.setQualityHeight(tier);
        if (mFilmIdentity != null) mFilmIdentity.setQualityHeight(tier);
        Logger.i("QualityPreference: action=save session=" + mSessionId + " film="
                + (mHistory == null ? "" : mHistory.getFilmId()) + " mode=" + (tier == 0 ? "auto" : "fixed") + " tier=" + tier);
        if (mHistoryPlaybackConfirmed) queueHistorySnapshot(false, false);
    }

    private List<Track> getTracks(int type) {
        List<Track> items = new ArrayList<>();
        List<Tracks.Group> groups = mPlayers.get().getCurrentTracks().getGroups();
        Track savedVideo = type == C.TRACK_TYPE_VIDEO ? getSavedTrack(type) : null;
        boolean savedVideoValid = isTrackAvailable(groups, savedVideo);
        if (type == C.TRACK_TYPE_VIDEO) {
            Track auto = new Track(type, getString(R.string.track_auto));
            auto.setGroup(-1);
            auto.setTrack(-1);
            auto.setSelected(!savedVideoValid);
            items.add(auto);
        }
        TrackNameProvider provider = new TrackNameProvider();
        for (int i = 0; i < groups.size(); i++) {
            Tracks.Group group = groups.get(i);
            if (group.getType() != type) continue;
            for (int j = 0; j < group.length; j++) {
                if (!group.isTrackSupported(j)) continue;
                Track item = new Track(type, provider.getTrackName(group.getTrackFormat(j)));
                item.setAdaptive(group.isAdaptiveSupported());
                item.setSelected(type == C.TRACK_TYPE_VIDEO ? savedVideoValid && savedVideo.getGroup() == i && savedVideo.getTrack() == j : group.isTrackSelected(j));
                item.setGroup(i);
                item.setTrack(j);
                items.add(item);
            }
        }
        return items;
    }

    private void updateQualityLabel() {
        applyQualityTrack();
        mBinding.control.action.video.setText(getCurrentQualityLabel());
    }

    private String getCurrentQualityLabel() {
        if (mAutomaticQuality) return getString(R.string.quality_auto);
        com.fongmi.android.tv.search.QualityOption current = currentVerifiedQuality();
        if (current != null) return com.fongmi.android.tv.search.QualityOption.tier(current.rank());
        if (mSelectedQuality != null) return com.fongmi.android.tv.search.QualityOption.tier(mSelectedQuality.rank());
        if (mQualityHeight > 0) return com.fongmi.android.tv.search.QualityOption.tier(mQualityHeight);
        return getString(R.string.play_quality);
    }

    private String getQualityOptionLabel(Track item) {
        if (item.isAuto()) return getString(R.string.quality_auto);
        Matcher matcher = RESOLUTION_PATTERN.matcher(item.getName());
        if (!matcher.find()) return item.getName();
        String resolution = matcher.group(1) + "×" + matcher.group(2);
        String details = item.getName().substring(matcher.end()).replaceFirst("^[,，·\\s]+", "");
        String label = getQualityTier(item.getName()) + " · " + resolution;
        return details.isEmpty() ? label : label + " · " + details;
    }

    private String getQualityTier(String value) {
        Matcher matcher = RESOLUTION_PATTERN.matcher(value);
        if (!matcher.find()) return getString(R.string.play_quality);
        int width = Integer.parseInt(matcher.group(1));
        int height = Integer.parseInt(matcher.group(2));
        int shortSide = Math.min(width, height);
        if (shortSide <= 480) return getString(R.string.quality_smooth);
        if (shortSide <= 720) return getString(R.string.quality_hd);
        if (shortSide <= 1080) return getString(R.string.quality_full_hd);
        if (shortSide <= 1440) return "2K";
        if (shortSide <= 2160) return "4K";
        return "8K";
    }

    private Track getSavedTrack(int type) {
        for (Track item : Track.find(mPlayers.getKey())) if (item.getType() == type && item.isSelected()) return item;
        return null;
    }

    private boolean isTrackAvailable(List<Tracks.Group> groups, Track item) {
        if (item == null || item.getGroup() < 0 || item.getGroup() >= groups.size()) return false;
        Tracks.Group group = groups.get(item.getGroup());
        return group.getType() == item.getType() && item.getTrack() >= 0 && item.getTrack() < group.length && group.isTrackSupported(item.getTrack());
    }

    private void showParsePanel(View anchor) {
        mQualityAnchor = null;
        int count = mParseAdapter.getItemCount();
        if (count == 0) return;
        int[] ids = new int[count];
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            ids[i] = i;
            labels[i] = mParseAdapter.get(i).getName();
        }
        mBinding.playbackPanel.show(anchor, getString(R.string.parse), ids, labels, null, new int[]{mParseAdapter.getPosition()}, 208, PLAYER_PANEL_MAX_HEIGHT_DP, id -> {
            onItemClick(mParseAdapter.get(id));
            mBinding.playbackPanel.dismiss();
            setR1Callback();
        });
        refreshBackHandling();
        App.removeCallbacks(mR1);
    }

    private void showTimerPanel(View anchor) {
        mQualityAnchor = null;
        final int delay = -1;
        final int cancel = -2;
        int[] ids;
        String[] labels;
        if (Timer.get().isRunning()) {
            ids = new int[]{delay, cancel};
            labels = new String[]{getString(R.string.timer_delay), getString(R.string.timer_cancel)};
        } else {
            ids = new int[]{5, 15, 30, 60, 120, 180};
            labels = new String[]{getString(R.string.timer_5), getString(R.string.timer_15), getString(R.string.timer_30), getString(R.string.timer_60), getString(R.string.timer_120), getString(R.string.timer_180)};
        }
        mBinding.playbackPanel.show(anchor, getString(R.string.play_timer), ids, labels, null, null, 196, PLAYER_PANEL_MAX_HEIGHT_DP, id -> {
            if (id == delay) Timer.get().delay();
            else if (id == cancel) Timer.get().reset();
            else Timer.get().set(TimeUnit.MINUTES.toMillis(id));
            mBinding.playbackPanel.dismiss();
            setR1Callback();
        });
        refreshBackHandling();
        App.removeCallbacks(mR1);
    }

    private void onDanmaku() {
        DanmakuSettingsDialog.create()
                .player(mPlayers)
                .displayScale(isFullscreen() ? 1.0f : 0.8f)
                .show(this);
        hideControl();
    }

    private void onDanmakuShow() {
        Setting.putDanmakuShow(!Setting.isDanmakuShow());
        checkDanmakuImg();
        showDanmaku();
        Logger.i("DanmakuToggle: session=" + mSessionId + " show=" + Setting.isDanmakuShow()
                + " fullscreen=" + isFullscreen() + " landscape=" + isLand());
    }

    private void onScale(View anchor) {
        mQualityAnchor = null;
        String[] labels = ResUtil.getStringArray(R.array.select_scale);
        int[] ids = new int[labels.length];
        for (int i = 0; i < ids.length; i++) ids[i] = i;
        mBinding.playbackPanel.show(anchor, getString(R.string.player_scale), ids, labels, null, new int[]{getScale()}, 184, PLAYER_PANEL_MAX_HEIGHT_DP, id -> {
            if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
            setScale(id);
            mBinding.playbackPanel.dismiss();
            setR1Callback();
        });
        refreshBackHandling();
        App.removeCallbacks(mR1);
    }

    private void onSpeed(View anchor) {
        mQualityAnchor = null;
        float[] values = new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f, 5.0f};
        String[] labels = new String[]{"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x", "3.0x", "4.0x", "5.0x"};
        int[] ids = new int[values.length];
        int selected = 0;
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i;
            if (Math.abs(values[i] - mPlayers.getSpeed()) < Math.abs(values[selected] - mPlayers.getSpeed())) selected = i;
        }
        mBinding.playbackPanel.show(anchor, getString(R.string.control_speed), ids, labels, null, new int[]{selected}, 188, PLAYER_PANEL_MAX_HEIGHT_DP, id -> {
            mBinding.control.action.speed.setText(mPlayers.setSpeed(values[id]));
            mHistory.setSpeed(mPlayers.getSpeed());
            syncCastSpeed();
            mBinding.playbackPanel.dismiss();
            setR1Callback();
        });
        refreshBackHandling();
        App.removeCallbacks(mR1);
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(mPlayers.toggleSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        syncCastSpeed();
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

    private boolean onTextLong() {
        onSubtitleClick();
        return true;
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        Logger.i("VideoDetail: action=manual-refresh session=" + mSessionId + " empty=" + mBinding.progressLayout.isEmpty());
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
        traceTransition("fullscreen-enter");
        logVideoLayout("fullscreen-before-layout", -1f);
        clearDrag();
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        setRequestedOrientation(portrait ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        mBinding.control.full.setVisibility(View.GONE);
        mBinding.swipeLayout.setVisibility(View.GONE);
        mBinding.handleLand.setVisibility(View.GONE);
        mBinding.detailBack.setVisibility(View.GONE);
        setRotate(portrait, true);
        applyPortraitViewingOffset(portrait);
        mPlayers.applyDanmakuSettings(1.0f);
        Util.hideSystemUI(this);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        hideControl();
        mBinding.video.post(() -> logVideoLayout("fullscreen-after-layout", -1f));
        refreshBackHandling();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        traceTransition("fullscreen-exit");
        logVideoLayout("fullscreen-exit", -1f);
        applyPortraitViewingOffset(false);
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
        applyOrientation();
        mPlayers.applyDanmakuSettings(0.8f);
        setRotate(false, false);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        hideControl();
        refreshBackHandling();
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
        boolean changed = mBinding.widget.progress.getVisibility() != View.VISIBLE;
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
        if (changed) Logger.i("PlayerUI: spinner=visible positionMs=" + mPlayers.getPosition()
                + ", scrubbing=" + mScrubbing + ", delayPending=" + mBufferingProgressPending);
    }

    private void hideProgress() {
        boolean changed = mBinding.widget.progress.getVisibility() == View.VISIBLE;
        mBinding.widget.progress.setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
        if (changed) Logger.i("PlayerUI: spinner=gone positionMs=" + mPlayers.getPosition()
                + ", scrubbing=" + mScrubbing + ", delayPending=" + mBufferingProgressPending);
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

    private int getPlayerWidthDp() {
        int width = mBinding.video.getWidth();
        if (width <= 0) return getResources().getConfiguration().screenWidthDp;
        return Math.round(width / getResources().getDisplayMetrics().density);
    }

    private boolean isActionShown(View view) {
        return isFullscreen() && view.getVisibility() == View.VISIBLE;
    }

    private void showControl() {
        if (mPiP.isInMode(this)) return;
        int widthDp = getPlayerWidthDp();
        // 左侧快捷开关与底栏弹幕设置按钮是两个独立控件。
        mBinding.control.danmaku.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.playerMore.setVisibility(mPlayers.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.right.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.keep.setVisibility(mHistory == null || isFullscreen() || widthDp < 380 ? View.GONE : View.VISIBLE);
        // 控制栏出现后统一使用同一行里的纯图标返回键；详情页的静态玻璃键暂时隐藏。
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.parse.setVisibility(View.GONE);
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        // 播放信息属于低频功能，固定收进“更多”，避免横竖屏布局出现两套规则。
        mBinding.control.info.setVisibility(View.GONE);
        mBinding.control.cast.setVisibility(mPlayers.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.pip.setVisibility(mPlayers.isEmpty() || PiP.noPiP() || !isFullscreen() || !isLand() || widthDp < 720 ? View.GONE : View.VISIBLE);
        // 片名始终保留一段弹性宽度，过长时由 marquee 在这段范围内循环。
        mBinding.control.title.setVisibility(View.VISIBLE);
        mBinding.control.title.setSelected(true);
        setActionVisible();
        mBinding.control.center.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        if (!isFullscreen()) mBinding.detailBack.setVisibility(View.GONE);
        updateTimeBattery();
        setR1Callback();
        checkPlayImg();
        // 起播和弱网阶段优先给主画面。已有 15 秒余量时再预热；否则第一次拖动仍会
        // 现场创建预览播放器，功能不会消失。
        warmPreviewIfReady();
        refreshBackHandling();
    }

    private void warmPreviewIfReady() {
        if (isCasting() || mScrubbing || mGestureSeeking || !mPlayers.isReady()
                || !isVisible(mBinding.control.getRoot())) return;
        long position = mPlayers.getPosition();
        long buffered = mPlayers.getBuffered();
        long duration = mPlayers.getDuration();
        if (buffered - position >= 15000 || duration > 0 && buffered >= duration) mPreview.prepare(position);
    }

    private void setActionVisible() {
        int widthDp = getPlayerWidthDp();
        boolean portraitFull = isFullscreen() && !isLand();
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.detailPlay.setVisibility(isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.action.player.setVisibility(View.GONE);
        mBinding.control.action.scale.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.action.speed.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.action.opening.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.action.ending.setVisibility(isFullscreen() && !portraitFull ? View.VISIBLE : View.GONE);
        mBinding.control.action.episodes.setVisibility(isFullscreen() && mEpisodeAdapter.getItemCount() > 1 ? View.VISIBLE : View.GONE);
        mBinding.control.action.danmaku.setVisibility(View.GONE);
        mBinding.control.action.text.setVisibility(View.GONE);
        mBinding.control.action.audio.setVisibility(View.GONE);
        mBinding.control.action.reset.setVisibility(isFullscreen() && canChooseSource() ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(!portraitFull && isFullscreen() && widthDp >= 560 && mCurrentVod != null ? View.VISIBLE : View.GONE);
        // 竖屏全屏固定把退出键放在选集右侧；横屏仍按可用宽度决定是否直显。
        mBinding.control.action.exit.setVisibility(isFullscreen() && (portraitFull || widthDp >= 640) ? View.VISIBLE : View.GONE);
    }

    private void hideControl() {
        mBinding.playbackPanel.dismiss();
        mBinding.control.getRoot().setVisibility(View.GONE);
        // 控制栏收了就不会再拖进度，预览这一路的解码器该还回去了
        mPreview.idle();
        if (!isFullscreen()) mBinding.detailBack.setVisibility(View.VISIBLE);
        App.removeCallbacks(mR1);
        refreshBackHandling();
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
        if (item.getVodFlags().isEmpty()) { ErrorEvent.flag(tag); return; }
        boolean movie = isQualityMovie();
        String episode = mHistory.getVodRemarks();
        Flag preferred = mSelectedQuality != null && mSelectedQuality.source.same(getKey(), getId()) ? mFlagAdapter.find(mSelectedQuality.flag.getFlag()) : mFlagAdapter.find(mHistory.getVodFlag());
        if (preferred != null && (PlaybackRoutePolicy.isRestricted(preferred)
                || com.fongmi.android.tv.search.QualityCatalog.match(preferred, episode, movie) == null)) preferred = null;
        for (Flag flag : item.getVodFlags()) {
            if (PlaybackRoutePolicy.isRestricted(flag) || flag.isCloudDrive() && !mManualSourceSelection) continue;
            if (com.fongmi.android.tv.search.QualityCatalog.match(flag, episode, movie) == null) continue;
            if (preferred == null) preferred = flag;
        }
        if (preferred == null) {
            captureSourceProgress();
            VodSource current = mSourceAdapter.getCurrent(); if (current != null) current.setBroken(true);
            showProgress();
            Logger.i("PlayDecision: action=missing-episode session=" + mSessionId + " episode=" + episode + " source=" + getKey());
            checkSearch(false); return;
        }
        Episode mapped = com.fongmi.android.tv.search.QualityCatalog.match(preferred, episode, movie);
        if (mapped != null) {
            mHistory.setVodRemarks(mapped.getName()); mHistory.setEpisodeUrl(mapped.getUrl());
        }
        if (!mInitialSelectionDone && !mLocalDetail && mPendingResumeHistory == null && "search".equals(mEntry) && mHistory.getPosition() < 0) {
            mInitialFlag = preferred; mInitialSelection = true;
            prepareQualityCatalog(mHistory.getVodRemarks());
            Logger.i("PlayDecision: action=compare-initial session=" + mSessionId + " film=" + mHistory.getFilmId() + " budgetMs=1800 candidates=" + mSourceAdapter.getItemCount());
            App.post(mFinishInitialSelection, 1800);
        } else { mInitialSelectionDone = true; selectFlag(preferred, false); }
        if (mHistory.isRevSort() && !mInitialSelection) reverseEpisode(true);
        updateDetailPanels();
    }

    private void checkHistory(Vod item) {
        mHistoryPlaybackConfirmed = false;
        History target = new History(); target.setKey(getHistoryKey());
        target.setVodName(mFilmIdentity == null || mFilmIdentity.getVodName() == null || mFilmIdentity.getVodName().isEmpty() ? item.getVodName(getName()) : mFilmIdentity.getVodName());
        target.setVodYear(mFilmIdentity == null || mFilmIdentity.getVodYear().isEmpty() ? item.getVodYear() : mFilmIdentity.getVodYear());
        target.setVodType(mFilmIdentity == null || mFilmIdentity.getVodType().isEmpty() ? item.getTypeName() : mFilmIdentity.getVodType());
        if (mFilmIdentity != null) { target.setFilmId(mFilmIdentity.getFilmId()); target.setSourceKeys(mFilmIdentity.getSourceKeys()); }
        History latest = null;
        for (History record : target.find()) latest = com.fongmi.android.tv.search.HistoryIdentity.prefer(latest, record);
        History carried = mPendingResumeHistory;
        mPendingResumeHistory = null;
        if (carried != null && com.fongmi.android.tv.search.HistoryIdentity.sameTitle(target.getVodName(), carried.getVodName())
                && (latest == null || latest.getCreateTime() <= carried.getCreateTime())) latest = carried;
        mHistory = mPendingCloudHistory != null ? mPendingCloudHistory : latest;
        mPendingCloudHistory = null;
        mHistory = mHistory == null ? createHistory(item) : History.objectFrom(mHistory.toString());
        mHistory.setAccountId(com.fongmi.android.tv.utils.LocalProfile.id());
        mHistory.setKey(getHistoryKey());
        mHistory.setCid(getSite().getSourceId() > 0 ? getSite().getSourceId() : VodConfig.getCid());
        mHistory.setVodName(target.getVodName()); mHistory.setVodYear(target.getVodYear()); mHistory.setVodType(target.getVodType());
        com.fongmi.android.tv.search.HistoryIdentity.bind(mHistory, target.find());
        com.fongmi.android.tv.search.HistoryIdentity.remember(mHistory, getHistoryKey());
        if (!mQualityPreferenceLoaded) {
            mQualityHeight = com.fongmi.android.tv.search.QualityOption.tierHeight(mHistory.getQualityHeight());
            mAutomaticQuality = mQualityHeight == 0;
            mSelectedQuality = null;
            mQualityRestorePending = !mAutomaticQuality;
            mQualityPreferenceLoaded = true;
            Logger.i("QualityPreference: action=load session=" + mSessionId + " film=" + mHistory.getFilmId()
                    + " entry=" + mEntry + " mode=" + (mAutomaticQuality ? "auto" : "fixed") + " tier=" + mQualityHeight);
        }
        mFilmIdentity = History.objectFrom(mHistory.toString()); mFilmIdentity.setAccountId(mHistory.getAccountId());
        Logger.i("PlayIdentity: action=bind session=" + mSessionId + " film=" + mHistory.getFilmId()
                + " title=" + mHistory.getVodName() + " year=" + mHistory.getVodYear() + " type=" + mHistory.getVodType()
                + " providerYear=" + item.getVodYear() + " providerType=" + item.getTypeName()
                + " carried=" + (carried != null) + " episode=" + mHistory.getVodRemarks() + " positionMs=" + mHistory.getPosition());
        if (mHistory.getPosition() >= 0) mCloudResumePosition = mHistory.getPosition();
        synchronized (mHistoryWriteLock) {
            mHistoryWriteGeneration++;
            mKnownHistoryVersion = mHistory.getCreateTime();
            for (History record : mHistory.find()) mKnownHistoryVersion = Math.max(mKnownHistoryVersion, record.getCreateTime());
            mLastHistoryCapture = mKnownHistoryVersion;
        }
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        mBinding.control.action.opening.setText(mHistory.getOpening() <= 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
        mBinding.control.action.ending.setText(mHistory.getEnding() <= 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mHistory.getEnding()));
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
        mHistory.setVodPic(item.getVodPic());
        setScale(getScale());
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(getSite().getSourceId() > 0 ? getSite().getSourceId() : VodConfig.getCid());
        history.setVodName(item.getVodName());
        history.setVodYear(item.getVodYear()); history.setVodType(item.getTypeName());
        history.findEpisode(item.getVodFlags());
        return history;
    }

    private void updateHistory(Episode item, boolean replay) {
        mHistoryPlaybackConfirmed = false;
        replay = replay || !item.equals(mHistory.getEpisode());
        // Signed episode URLs may change between devices; an explicit cloud choice keeps its time.
        if (mCloudResumePosition >= 0) {
            mHistory.setPosition(mCloudResumePosition);
            mCloudResumePosition = -1;
            replay = false;
        }
        mHistory.setEpisodeUrl(item.getUrl());
        mHistory.setVodRemarks(item.getName());
        mHistory.setVodFlag(getFlag().getFlag());
        updateHistoryEpisodeNumbers(item);
        mHistory.setCreateTime(System.currentTimeMillis());
        mHistory.setPosition(replay ? C.TIME_UNSET : mHistory.getPosition());
    }

    private void updateHistoryEpisodeNumbers(Episode item) {
        int count = mEpisodeAdapter.getItemCount();
        int kind = mTargetKind;
        boolean series = count > 1 || kind == TitleKey.KIND_TV;
        int index = mEpisodeAdapter.getPosition(item);
        int number = mHistory.isRevSort() ? count - index : index + 1;
        int semanticNumber = com.fongmi.android.tv.search.EpisodeKey.number(item.getName());
        if (semanticNumber > 0) number = semanticNumber;
        if (mLocalDetail && item.getNumber() > 0) {
            number = item.getNumber();
            count = Math.max(count, mHistory.getEpisodeCount());
            if (number > count) count = 0; // Legacy downloads may not contain the full catalog yet.
        }
        mHistory.setEpisodeCount(series ? count : 0);
        mHistory.setEpisodeNumber(series && index >= 0 ? number : 0);
    }

    /** 只有播放器确认片源可用后才允许创建/更新记录，失败片源不会碰数据库里的旧记录。 */
    private void confirmHistoryPlayback() {
        if (mHistoryPlaybackConfirmed || mHistory == null || mAwaitingCloudSync || mCloudChoicePending || mSuppressHistorySaves) return;
        mHistoryPlaybackConfirmed = true;
        queueHistorySnapshot(true, false);
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkPlayImg() {
        // isPlaying() 在缓冲期间会变成 false，但此时用户并没有暂停。按钮表示点击后
        // 将执行的动作，所以只要播放器仍准备继续播放，就始终显示双竖线。
        boolean playRequested = isCasting() ? CastManager.get().isPlaying() : mPlayers.isPlayRequested();
        int icon = playRequested ? R.drawable.ic_control_pause : R.drawable.ic_control_play;
        mBinding.control.play.setImageResource(icon);
        mBinding.control.detailPlay.setImageResource(icon);
        mPiP.update(this, mPlayers.isPlayRequested());
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
        keep.setCid(getSite().getSourceId() > 0 ? getSite().getSourceId() : VodConfig.getCid());
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
        // The controls may have opened before there was enough buffer to prewarm.
        // Retry once playback has caught up, instead of leaving the first drag cold.
        warmPreviewIfReady();
        if (isCasting() || mHistory == null) return;
        long position = mPlayers.getPosition(), duration = mPlayers.getDuration();
        boolean changed = com.fongmi.android.tv.utils.PlaybackProgressPolicy.shouldRecord(
                mPlayers.isPlaying(), mAwaitingCloudSync || mCloudChoicePending || mSuppressHistorySaves,
                mHistory.getPosition(), position);
        if (!changed || duration <= 0) return;
        mHistory.setPosition(position);
        mHistory.setDuration(duration);
        confirmHistoryPlayback();
        queueHistorySnapshot(true, false);
        if (mHistory.getEnding() > 0 && mHistory.getEnding() + position >= duration) checkEnded(false);
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
            // 通知没有显式停止按钮，系统媒体会话偶发 STOP 只能暂停，不能退出页面。
            onPaused();
        } else if (event.isUpdate()) {
            startPlaybackNotification();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.NETWORK || event.getType() == RefreshEvent.Type.CONFIG) retryDetailAfterNetwork();
        else if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.DOWNLOAD) onDownloadRefresh();
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) mPlayers.setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) mPlayers.setDanmaku(Danmaku.from(event.getPath()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerEvent(PlayerEvent event) {
        if (!event.getTag().equals(tag)) return;
        Logger.i("PlayerState: state=" + playerStateName(event.getState())
                + " positionMs=" + mPlayers.getPosition()
                + " bufferedMs=" + mPlayers.getBuffered()
                + " scrubbing=" + mScrubbing
                + " delayPending=" + mBufferingProgressPending);
        switch (event.getState()) {
            case PlayerEvent.PREPARE:
                mScrubbing = false;
                cancelBufferingProgress();
                // 第一次播放时服务尚未创建，不会提前出现空通知；切集或换源时则保留现有通知，
                // 避免 PREPARE 到 READY 之间通知被撤掉又重新出现。转为投屏后本地通知才需要停止。
                if (isCasting()) PlaybackService.stop();
                // 换片源了，上一集锁定的倍速不该带过来
                mKeyDown.unlockSpeed();
                // 投屏中：本地只负责把真实地址解析出来，解析完立刻推给电视并停掉本地解码
                if (isCasting()) castCurrent();
                else setPosition();
                break;
            case Player.STATE_BUFFERING:
                mPlaybackPolicy.buffering(SystemClock.elapsedRealtime(), mPlaybackWanted,
                        mScrubbing || mGestureSeeking || mAwaitingCloudSync || mCloudChoicePending || isCasting());
                if (mQualityCatalog != null) mQualityCatalog.budget(0);
                if (mSourceBackground && mSourceTask != null) mSourceTask.pause();
                App.removeCallbacks(mCacheWarmup);
                mPlaybackCache.pause();
                mPreview.suspend();
                if (mScrubbing) {
                    // CustomSeekView seeks before delivering onScrubStop. Keep the spinner
                    // suppressed for the whole gesture so that synchronous BUFFERING cannot
                    // slip through in the single frame before the stop callback arrives.
                    hideProgress();
                    cancelBufferingProgress();
                } else if (mBinding.widget.progress.getVisibility() != View.VISIBLE) {
                    scheduleBufferingProgress("buffering");
                }
                break;
            case Player.STATE_READY:
                long wait = mPlaybackPolicy.ready(SystemClock.elapsedRealtime());
                Logger.i("PlayHealth: event=ready session=" + mSessionId + " waitMs=" + wait
                        + " positionMs=" + mPlayers.getPosition() + " bufferMs=" + Math.max(0, mPlayers.getBuffered() - mPlayers.getPosition())
                        + " stalls=" + mPlaybackPolicy.stallCount() + " stalledMs=" + mPlaybackPolicy.stalledMs());
                if (!mRecoveryManual || !"source-request".equals(mRecoveryReason)) mRecoveryPending = false;
                if (!mLoggedPlaybackReady) {
                    mLoggedPlaybackReady = true;
                    mCurrentStartupMs = SystemClock.elapsedRealtime() - mPlaybackRequestedAt;
                    com.fongmi.android.tv.search.SiteHealth.playback(getSite(), true, SystemClock.elapsedRealtime() - mPlaybackRequestedAt);
                    Logger.i("OfflineStart: ready local=" + OfflinePlayback.isLocal(mPlayers.getUrl())
                            + " elapsedMs=" + (SystemClock.elapsedRealtime() - mPlaybackRequestedAt) + " cloudWait=" + mAwaitingCloudSync);
                    if (mLocalDetail) App.post(this::refreshLocalCatalog, 400);
                }
                if (mAwaitingCloudSync || mCloudChoicePending) mPlayers.pause();
                cancelBufferingProgress();
                mPlayers.reset();
                confirmHistoryPlayback();
                // 换集时 onReset 会暂停时钟；同轨媒体不一定再次派发 TRACK，
                // READY 时恢复采样，确保当前集和进度持续写入观看记录。
                mClock.setCallback(this);
                // 先登记片源；预热由 showControl 根据主播放器的缓冲余量决定。
                if (!isCasting()) mPreview.setSource(mPlayers.getUrl(), mPlayers.getPreviewItem(), mPlayers.getPlaybackCacheTrackParameters());
                schedulePlaybackCache();
                hideProgress();
                checkControl();
                checkPlayImg();
                startPlaybackNotification();
                updateQualityLabel(); App.post(mQualityMaintenance); App.post(this::onQualityCatalogChanged);
                break;
            case Player.STATE_ENDED:
                checkEnded(true);
                break;
            case PlayerEvent.TRACK:
                if (isCasting()) confirmHistoryPlayback();
                setMetadata();
                setTrackVisible();
                updateQualityLabel(); App.post(mQualityMaintenance); App.post(this::onQualityCatalogChanged);
                mClock.setCallback(this);
                startPlaybackNotification();
                break;
            case PlayerEvent.SIZE:
                checkOrientation();
                updateQualityLabel(); App.post(mQualityMaintenance); App.post(this::onQualityCatalogChanged);
                break;
        }
    }

    private String playerStateName(int state) {
        if (state == PlayerEvent.PREPARE) return "PREPARE";
        if (state == Player.STATE_IDLE) return "IDLE";
        if (state == Player.STATE_BUFFERING) return "BUFFERING";
        if (state == Player.STATE_READY) return "READY";
        if (state == Player.STATE_ENDED) return "ENDED";
        if (state == PlayerEvent.TRACK) return "TRACK";
        if (state == PlayerEvent.SIZE) return "SIZE";
        return String.valueOf(state);
    }

    private void schedulePlaybackCache() {
        App.removeCallbacks(mCacheWarmup);
        if (isCasting() || !mPlayers.isVod() || mPlayers.hasDrm()) {
            stopPlaybackCache();
            return;
        }
        // seek 后短暂 BUFFERING 只是主播放器正在切分片，不能在这里 stop：stop 会清掉
        // 本集已经落盘的全部临时缓存，并让淡黄色进度从头刷新。等 READY 事件再续传即可。
        if (!mPlayers.isReady()) return;
        // 先留给主播放器一秒起播；若前向余量还不足 15 秒则继续等，避免后台预取
        // 和正在首缓冲的画面抢连接与带宽。
        App.post(mCacheWarmup, 1000);
    }

    private void startPlaybackCache() {
        if (isCasting() || !mPlayers.isReady() || !mPlayers.isVod() || mPlayers.hasDrm()) return;
        long duration = mPlayers.getDuration();
        long buffered = mPlayers.getBuffered();
        MediaItem item = mPlayers.getPreviewItem();
        if (duration <= 0) return;
        if (!PlaybackCache.isFullyLocal(item) && buffered < duration && buffered - mPlayers.getPosition() < 15000) {
            App.post(mCacheWarmup, 1000);
            return;
        }
        mPlaybackCache.start(item, mPlayers.getPlaybackCacheTrackParameters(), mPlayers.getPosition(), duration);
    }

    private void stopPlaybackCache() {
        if (mCacheWarmup != null) App.removeCallbacks(mCacheWarmup);
        if (mPlaybackCache != null) mPlaybackCache.stop();
        if (mBinding != null) mBinding.control.seek.setSessionCachedPercent(-1f);
    }

    /** 只有真正可播放且拿到有效总时长后，才创建顶部播放通知。 */
    private void startPlaybackNotification() {
        // 不用 isRunning 拦截：新片源 PREPARE 时 stopService 与 READY 可能紧挨发生，
        // 这时重新 start 才能消除服务正在退出造成的竞态。
        if (!isCasting() && mPlayers.isReady() && mPlayers.getDuration() > 0) PlaybackService.start(mPlayers);
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
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        checkNext(notify);
        checkPlayImg();
        flushProgress();
    }

    private void setTrackVisible() {
        int widthDp = getPlayerWidthDp();
        boolean portraitFull = isFullscreen() && !isLand();
        mBinding.control.action.text.setVisibility(View.GONE);
        mBinding.control.action.audio.setVisibility(View.GONE);
        mBinding.control.action.video.setVisibility(!portraitFull && isFullscreen() && widthDp >= 560 && mCurrentVod != null ? View.VISIBLE : View.GONE);
        updateQualityLabel();
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
        Logger.i("OfflineStart: error type=" + event.getType() + " msg=" + event.getMsg()
                + " local=" + (mPlayingDownload != null) + " fileExists=" + (mPlayingDownload != null && mPlayingDownload.isPlayable()));
        if (mPlayingDownload != null) {
            mFailedLocalPaths.add(mPlayingDownload.getLocalPath());
            mPlayingDownload = null;
            onRefresh();
            return;
        }
        if (!mPlaybackWanted) { showError(event.getMsg()); return; }
        com.fongmi.android.tv.search.SiteHealth.playback(getSite(), false, SystemClock.elapsedRealtime() - mPlaybackRequestedAt);
        captureSourceProgress();
        mPlaybackPolicy.failed(playbackRoute(), SystemClock.elapsedRealtime());
        Logger.i("PlayDecision: action=error session=" + mSessionId + " episode=" + mPlaybackEpisode
                + " positionMs=" + (mHistory == null ? -1 : mHistory.getPosition()) + " type=" + event.getType());
        if (mPlaybackWanted && recoverPlayback("playback-error", false, true)) return;
        if (mPlayers.retried()) onError(event);
        else onRefresh();
    }

    private void onError(ErrorEvent event) {
        // Playback failure is not a detail-page loading failure. Enabling pull-to-refresh
        // here makes the detail-card drag gesture trigger a home-style refresh instead.
        mBinding.swipeLayout.setEnabled(false);
        mScrubbing = false;
        cancelBufferingProgress();
        // 轨道偏好保存时使用的是影片历史 key，而不是解析后的临时播放地址。
        // 删除地址 key 会让不可用的清晰度偏好一直残留，重试后仍然重复应用。
        Track.delete(mPlayers.getKey());
        showError(event.getMsg());
        mClock.setCallback(null);
        mPlayers.resetTrack();
        mPlayers.reset();
        mPlayers.stop();
        startFlow();
    }

    private void startFlow() {
        if (!Util.isNetworkAvailable()) return;
        if (!getSite().isChangeable()) return;
        if (!mPlaybackPolicy.canSwitch(SystemClock.elapsedRealtime(), false, true)) return;
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
        int position = mFlagAdapter.isEmpty() ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    // ==================== 片源 ====================
    //
    // 同一部片在各站点的资源。从搜索结果进来时整组源已经在手上；从观看记录、收藏、首页推荐
    // 进来时，详情加载完再按片名搜一遍，只收同名同年的。排在前面的几个源在后台实测速度，
    // 播放失败自动换源、用户手动挑源都按这个顺序来。

    /**
     * 播放失败或详情拉不到时的兜底：列表里有能换的源就直接换；还在搜就等下一个源到了再换；
     * 都没有就按片名重新搜一遍。
     */
    private void checkSearch(boolean force) {
        if (nextSite()) return;
        if (mSourceTask != null && !mSourceTask.isFinished()) setInitAuto(true);
        else if (mSourceTask != null && mSourceTask.hasMore()) { setInitAuto(true); mSourceTask.searchMore(); }
        else startSourceSearch(true);
    }

    /** All enabled configurations contribute sources and qualities, even when the episode panel is open. */
    private void checkQuick() {
        if (!Util.isNetworkAvailable()) return;
        if (mSourceTask != null) return;
        App.post(mR5, 1000);
    }

    /** 从搜索结果页带过来的整组片源。 */
    private void initSources() {
        VodGroup group = GroupCache.get(getGroupToken());
        if (group != null) for (VodSource source : group.getSources()) if (isTarget(source.getVod())) mSourceAdapter.add(source);
        VodSource current = mSourceAdapter.find(getKey(), getId());
        if (current != null) mSourceAdapter.setCurrent(current);
        updateSourceView();
    }

    /** 换了一部片（通知栏、投屏接收等从外部再次打开详情页）：清掉上一部片的片源。 */
    private void resetSources() {
        logPlaybackSummary("new-entry");
        mClock.setCallback(null); mPlayers.reset(); mPlayers.stop();
        App.removeCallbacks(mQualityMaintenance, mPlaybackWatchdog, mFinishInitialSelection);
        if (mQualityCatalog != null) mQualityCatalog.release();
        mQualityCatalog = null; mSelectedQuality = null; mAutomaticQuality = true; mQualityHeight = 0; mQualityAnchor = null;
        mQualityPreferenceLoaded = false; mQualityRestorePending = false;
        mPendingResumeHistory = null;
        mCloudResumePosition = -1; mHistoryPlaybackConfirmed = false; mLoggedPlaybackReady = false;
        mFilmIdentity = null; mHistory = null;
        mTargetKey = ""; mTargetYear = 0; mTargetKind = TitleKey.KIND_UNKNOWN;
        mInitialSelection = false; mInitialSelectionDone = false; mInitialFlag = null;
        mRecoveryPending = false; mRecoveryManual = false; mRecoveryReason = "fallback"; mPlaybackEpisode = "";
        mAutoWidth = 0; mAutoHeight = 0; mPlaybackWanted = true;
        mSourceAnchor = null; mSourceProbeUntil = 0; mCurrentStartupMs = -1;
        mPlaybackPolicy = new com.fongmi.android.tv.search.PlaybackPolicy();
        mSessionId = Long.toHexString(System.nanoTime()); mManualSwitches = 0; mAutomaticSwitches = 0; mLastHealthLog = 0;
        mLocalDetailGeneration++;
        mRefreshingLocalDetail = false;
        mLocalDetail = false;
        mPlayingDownload = null;
        mFailedLocalPaths.clear();
        stopSourceSearch();
        mSourceAdapter.clear();
        mCurrentVod = null; mMetadataVod = null;
        mMetadataRenderKey = "";
        App.removeCallbacks(mDoubanRetry);
        mRatingGeneration++; mRatingAttempts = 0; mRatingKey = ""; mRatingRetryAt = 0; mDoubanLoading = false; mDoubanSubject = null;
        mDoubanGenres = new ArrayList<>();
        if (mRelatedAdapter != null) mRelatedAdapter.setItems(new ArrayList<>());
        mManualSourceSelection = false;
        setInitAuto(false);
        updateSourceView();
    }

    private void setTarget(String name, String year, String type) {
        if (mTargetKey.isEmpty()) mTargetKey = TitleKey.normalize(name);
        if (mTargetYear == 0) mTargetYear = TitleKey.year(year);
        if (mTargetKind == TitleKey.KIND_UNKNOWN) mTargetKind = TitleKey.kind(type);
    }

    private String metadataName(Vod item) {
        if (mFilmIdentity != null && !TextUtils.isEmpty(mFilmIdentity.getVodName())) return mFilmIdentity.getVodName();
        if (!getName().isEmpty()) return getName();
        return item == null ? "" : item.getVodName();
    }

    private String metadataYear(Vod item) {
        if (mFilmIdentity != null && !TextUtils.isEmpty(mFilmIdentity.getVodYear())) return mFilmIdentity.getVodYear();
        if (!getYear().isEmpty()) return getYear();
        return item == null ? "" : item.getVodYear();
    }

    /** Keep accepted facts stable and fill missing fields as matching providers become available. */
    private boolean mergeDetailMetadata(Vod item) {
        if (item == null || mMetadataVod != null && !acceptsDetail(item)) return false;
        Vod previous = mMetadataVod == null ? new Vod() : mMetadataVod;
        com.google.gson.JsonObject fields = new com.google.gson.JsonObject();
        fields.addProperty("vod_name", previous.getVodName().isEmpty() ? item.getVodName(getName()) : previous.getVodName());
        fields.addProperty("vod_year", metadataValue(previous.getVodYear(), item.getVodYear()));
        fields.addProperty("vod_area", metadataValue(previous.getVodArea(), item.getVodArea()));
        fields.addProperty("type_name", metadataValue(previous.getTypeName(), item.getTypeName()));
        fields.addProperty("vod_director", metadataValue(previous.getVodDirector(), item.getVodDirector()));
        fields.addProperty("vod_actor", metadataValue(previous.getVodActor(), item.getVodActor()));
        fields.addProperty("vod_content", metadataValue(previous.getVodContent(), item.getVodContent()));
        fields.addProperty("vod_pic", metadataValue(previous.getVodPic(), item.getVodPic()));
        fields.addProperty("vod_remarks", metadataValue(previous.getVodRemarks(), item.getVodRemarks()));
        Vod merged = App.gson().fromJson(fields, Vod.class);
        boolean changed = mMetadataVod == null || !App.gson().toJson(merged).equals(App.gson().toJson(mMetadataVod));
        mMetadataVod = merged;
        return changed;
    }

    private static String metadataValue(String existing, String incoming) {
        String current = cleanMetadataValue(existing);
        return current.isEmpty() ? cleanMetadataValue(incoming) : current;
    }

    private static String cleanMetadataValue(String value) {
        String text = value == null ? "" : value.trim();
        return text.matches("(?i)(无|未知|暂无|暂无简介|暂无资料|未知年份|未知地区|null|undefined|N/A|--)") ? "" : text;
    }

    private void loadDoubanDetails(String name, String year) {
        String titleKey = TitleKey.normalize(name);
        if (titleKey.isEmpty()) { mDoubanLoading = false; return; }
        String key = titleKey + "#" + TitleKey.year(year);
        long now = SystemClock.elapsedRealtime();
        if (key.equals(mRatingKey) && (mDoubanLoading || mDoubanSubject != null && mDoubanSubject.hasHeaderMetadata() || now < mRatingRetryAt || mRatingAttempts >= 2)) return;
        boolean changed = !key.equals(mRatingKey);
        if (changed) mRatingAttempts = 0;
        mRatingAttempts++;
        mRatingKey = key; mDoubanLoading = true;
        int generation = ++mRatingGeneration;
        if (changed) {
            mDoubanSubject = null; mDoubanGenres = new ArrayList<>();
            mRelatedAdapter.setItems(new ArrayList<>());
            mBinding.relatedSection.setVisibility(View.GONE);
            mBinding.ratingLayout.setVisibility(View.GONE);
        }
        if (mDoubanSubject == null) showMetadataPlaceholder();
        Logger.i("DoubanDetail: action=lookup session=" + mSessionId + " title=" + name + " year=" + year + " retry=" + !changed);
        App.execute(() -> {
            Douban.Subject subject = null;
            try { subject = Douban.subject(name, year); }
            catch (Exception error) { Logger.e("DoubanDetail", error); }
            Douban.Subject result = subject;
            App.post(() -> {
                if (isFinishing() || isDestroyed() || generation != mRatingGeneration) return;
                mDoubanLoading = false;
                if (result != null && !result.getId().isEmpty()) mDoubanSubject = result.retainMissing(mDoubanSubject);
                boolean complete = mDoubanSubject != null && mDoubanSubject.hasHeaderMetadata();
                mRatingRetryAt = complete ? Long.MAX_VALUE : SystemClock.elapsedRealtime() + 30000;
                App.removeCallbacks(mDoubanRetry);
                if (!complete && mRatingAttempts < 2) App.post(mDoubanRetry, 30000);
                mDoubanGenres = mDoubanSubject == null ? new ArrayList<>() : mDoubanSubject.getGenres();
                if (mDoubanSubject != null && mDoubanSubject.getRating() > 0) showDoubanRating(mDoubanSubject.getRating());
                Logger.i("DoubanDetail: action=" + (mDoubanSubject == null ? "miss" : "matched") + " session=" + mSessionId
                        + " title=" + name + " year=" + year + " id=" + (mDoubanSubject == null ? "" : mDoubanSubject.getId())
                        + " endpoint=" + (mDoubanSubject == null ? "" : mDoubanSubject.getEndpoint()) + " complete=" + complete
                        + " subjectYear=" + (mDoubanSubject == null ? "" : mDoubanSubject.getYear())
                        + " genres=" + (mDoubanSubject == null ? 0 : mDoubanSubject.getGenres().size())
                        + " countries=" + (mDoubanSubject == null ? 0 : mDoubanSubject.getCountries().size())
                        + " rating=" + (mDoubanSubject == null ? 0 : mDoubanSubject.getRating()));
                renderDetailMetadata();
            });
            // Recommendations must not delay the first metadata render.
            if (result == null || result.getId().isEmpty()) return;
            List<Douban.Item> related = new ArrayList<>();
            try { related = Douban.related(result.getId(), 12); }
            catch (Exception error) { Logger.e("DoubanRelated", error); }
            List<Douban.Item> recommendations = related;
            App.post(() -> {
                if (isFinishing() || isDestroyed() || generation != mRatingGeneration) return;
                mRelatedAdapter.setItems(recommendations);
                mBinding.relatedSection.setVisibility(recommendations.isEmpty() ? View.GONE : View.VISIBLE);
                Logger.i("DoubanDetail: action=related session=" + mSessionId + " id=" + result.getId()
                        + " count=" + recommendations.size());
            });
        });
    }

    private void showMetadataPlaceholder() {
        for (TextView view : new TextView[]{mBinding.meta, mBinding.castText, mBinding.content}) {
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(getColor(R.color.surface_secondary)); shape.setCornerRadius(ResUtil.dp2px(8));
            view.setBackground(shape); view.setText("");
            view.setMinimumHeight(ResUtil.dp2px(view == mBinding.content ? 64 : view == mBinding.castText ? 40 : 20));
            view.setVisibility(View.VISIBLE);
        }
        mBinding.metaScroll.setVisibility(View.VISIBLE); mBinding.contentLayout.setVisibility(View.VISIBLE);
        mBinding.castExpand.setVisibility(View.GONE); mBinding.contentExpand.setVisibility(View.GONE);
        mBinding.tags.removeAllViews();
        for (int i = 0; i < 3; i++) {
            TextView tag = new TextView(this); tag.setText("　　　"); tag.setBackgroundResource(R.drawable.shape_detail_tag);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, ResUtil.dp2px(20)); params.setMarginEnd(ResUtil.dp2px(8));
            mBinding.tags.addView(tag, params);
        }
        mBinding.tagScroll.setVisibility(View.VISIBLE);
        com.bumptech.glide.Glide.with(this).clear(mBinding.poster);
        mBinding.poster.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        mBinding.poster.setImageDrawable(new android.graphics.drawable.ColorDrawable(getColor(R.color.surface_secondary)));
    }

    private void renderDetailMetadata() {
        if (mDoubanLoading && mDoubanSubject == null) { showMetadataPlaceholder(); return; }
        if (mCurrentVod == null) return;
        for (TextView view : new TextView[]{mBinding.meta, mBinding.castText, mBinding.content}) {
            view.setBackground(null); view.setMinimumHeight(0);
        }
        Vod item = mMetadataVod == null ? mCurrentVod : mMetadataVod;
        String pic = mDoubanSubject != null && !mDoubanSubject.getPic().isEmpty() ? mDoubanSubject.getPic() : item.getVodPic(getPic());
        String intro = mDoubanSubject != null && !mDoubanSubject.getIntro().isEmpty() ? mDoubanSubject.getIntro() : Html.fromHtml(item.getVodContent()).toString();
        ImgUtil.rect(item.getVodName(getName()), pic, mBinding.poster);
        setText(mBinding.content, 0, intro);
        setCast(item); setMeta(item); setTags(mDoubanGenres);
        List<String> displayedTags = new ArrayList<>();
        for (int i = 0; i < mBinding.tags.getChildCount(); i++) {
            View tag = mBinding.tags.getChildAt(i);
            if (tag instanceof TextView) displayedTags.add(((TextView) tag).getText().toString());
        }
        String renderKey = mBinding.meta.getText() + "#" + displayedTags + "#" + !intro.isEmpty() + "#" + mBinding.castText.getVisibility();
        if (!renderKey.equals(mMetadataRenderKey)) {
            mMetadataRenderKey = renderKey;
            Logger.i("DetailMetadata: action=render session=" + mSessionId + " title=" + metadataName(item)
                    + " douban=" + (mDoubanSubject == null ? "" : mDoubanSubject.getId())
                    + " summary=" + mBinding.meta.getText() + " tags=" + displayedTags + " intro=" + !intro.isEmpty());
            mBinding.tagScroll.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                Logger.i("DetailMetadata: action=layout session=" + mSessionId + " tags=" + mBinding.tags.getChildCount()
                        + " visibility=" + mBinding.tagScroll.getVisibility() + " width=" + mBinding.tagScroll.getWidth()
                        + " height=" + mBinding.tagScroll.getHeight());
            });
        }
        updateContentExpand(); mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
        setArtwork(pic); mBinding.video.setTag(pic);
        if (mHistory != null && !pic.isEmpty() && TitleKey.normalize(mHistory.getVodName()).equals(TitleKey.normalize(item.getVodName()))) mHistory.setVodPic(pic);
    }

    private void applyDoubanMetadata() {
        if (mDoubanSubject == null) return;
        Vod item = mMetadataVod == null ? mCurrentVod : mMetadataVod;
        if (item != null) { setCast(item); setMeta(item); }
        setTags(mDoubanSubject.getGenres());
        if (!mDoubanSubject.getIntro().isEmpty()) {
            setText(mBinding.content, 0, mDoubanSubject.getIntro());
            updateContentExpand();
            mBinding.contentLayout.setVisibility(View.VISIBLE);
        }
        if (!mDoubanSubject.getPic().isEmpty()) {
            String pic = mDoubanSubject.getPic();
            ImgUtil.rect(mBinding.name.getText().toString(), pic, mBinding.poster);
            mBinding.video.setTag(pic);
            setArtwork(pic);
            if (mHistory != null) mHistory.setVodPic(pic);
        }
    }

    private void showDoubanRating(double rating) {
        mBinding.rating.setText(String.format(Locale.ROOT, "%.1f", rating));
        mBinding.ratingLayout.setVisibility(View.VISIBLE);
    }

    /** 同名（规整后）、年份相差不超过一年、片种不冲突，才算同一部片。续集和翻拍不收。 */
    private boolean isTarget(Vod item) {
        return !mTargetKey.isEmpty() && acceptsDetail(item);
    }

    /** 当前播放的源也放进列表并高亮。从观看记录进来时列表里原本没有它。 */
    private void setCurrentSource(Vod item) {
        if (getSite().isEmpty()) return;
        VodSource current = mSourceAdapter.find(getKey(), getId());
        if (current == null) {
            Vod vod = new Vod();
            vod.setVodId(getId());
            vod.setVodName(item.getVodName(getName()));
            vod.setVodPic(item.getVodPic());
            vod.setVodYear(item.getVodYear());
            vod.setTypeName(item.getTypeName());
            vod.setVodRemarks(item.getVodRemarks());
            vod.setSite(getSite());
            mSourceAdapter.add(current = new VodSource(vod, 0));
        }
        mSourceAdapter.setCurrent(current);
        updateSourceView();
    }

    /** 按片名搜全部站点，同名同年的收进片源列表。auto 为 true 时第一个合适的源一到就切过去。 */
    private void startSourceSearch(boolean auto) {
        if (!auto && mCurrentVod != null && mHistory != null) {
            prepareQualityCatalog(mHistory.getVodRemarks());
            if (mQualityCatalog == null || !mQualityCatalog.isReady() || qualityWorkBudget() == 0) { App.removeCallbacks(mR5); App.post(mR5, 2000); return; }
        }
        stopSourceSearch();
        setInitAuto(auto); mSourceBackground = !auto;
        mStrictUntil = SystemClock.elapsedRealtime() + 2500;
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (site.isSearchable() && !site.isCloudDrive()) sites.add(site);
        if (!auto && mQualityCatalog != null) sites = mQualityCatalog.pendingSites(sites);
        SearchTask.Callback callback = new SearchTask.Callback() {
            @Override public void onResult(List<Vod> items, long cost) { addSources(items, cost); }
            @Override public void onSiteComplete(Site site, boolean failed) {
                if (mQualityCatalog != null) mQualityCatalog.searchComplete(site, failed);
            }
            @Override public void onFinish() { onSourceSearchFinish(); }
        };
        mSourceTask = auto ? SearchTask.start(sites, mBinding.name.getText().toString(), false, callback)
                : SearchTask.background(sites, mBinding.name.getText().toString(), callback);
        App.post(mR6, 2500);
    }

    private void stopSourceSearch() {
        App.removeCallbacks(mR5, mR6);
        if (mSourceTask != null) mSourceTask.cancel();
        mSourceTask = null;
    }

    private void addSources(List<Vod> items, long cost) {
        List<VodSource> sources = new ArrayList<>();
        boolean metadataChanged = false;
        for (Vod item : items) {
            if (item.isFolder() || !isTarget(item)) continue;
            if (mCurrentVod != null) metadataChanged |= mergeDetailMetadata(item);
            if (mSourceAdapter.contains(item.getSiteKey(), item.getVodId())) continue;
            boolean duplicate = false;
            for (VodSource source : sources) duplicate |= source.same(item.getSiteKey(), item.getVodId());
            if (!duplicate) sources.add(new VodSource(item, cost));
        }
        if (metadataChanged) renderDetailMetadata();
        if (sources.isEmpty()) return;
        mSourceAdapter.addAll(sources);
        if (mHistory != null) prepareQualityCatalog(mHistory.getVodRemarks());
        updateSourceView();
        if (mSourceAnchor != null && mBinding.playbackPanel.isPanelVisible()) renderSourcePanel(mSourceAnchor, true);
        checkAutoSwitch();
    }

    private void onSourceSearchFinish() {
        checkAutoSwitch();
        if (isInitAuto() && mSourceTask != null && mSourceTask.hasMore()) { mSourceTask.searchMore(); return; }
        if (mSourceBackground && mSourceTask != null && mSourceTask.hasMore()) App.post(mQualityMaintenance, 1500);
        // 只有片名、搜完一个能播的源都没有：别让转圈一直转下去
        if (isInitAuto() && mCurrentVod == null && mSourceAdapter.next(0) == null) {
            App.removeCallbacks(mR4);
            showEmpty();
        }
    }

    /** 自动选源。有目标年份时开搜后 2.5 秒内只认年份对得上的，免得同名的老版本抢先。 */
    private void checkAutoSwitch() {
        if (mRecoveryPending && !isInitAuto()) { recoverPlayback(mRecoveryReason, mRecoveryManual, false); return; }
        if (!isInitAuto()) return;
        if (!mPlaybackWanted && mHistory != null) return;
        if (mHistory != null && !mPlaybackPolicy.canSwitch(SystemClock.elapsedRealtime(), false, true)) return;
        boolean strict = mTargetYear > 0 && mSourceTask != null && !mSourceTask.isFinished() && SystemClock.elapsedRealtime() < mStrictUntil;
        VodSource next = nextPlaybackSource(strict ? mTargetYear : 0);
        if (next != null) switchSource(next, true);
    }

    private boolean nextSite() {
        if (mHistory != null && !mPlaybackPolicy.canSwitch(SystemClock.elapsedRealtime(), false, true)) return false;
        VodSource next = nextPlaybackSource(0);
        if (next == null) return false;
        switchSource(next, true);
        return true;
    }

    private VodSource nextPlaybackSource(int year) {
        long now = SystemClock.elapsedRealtime();
        for (VodSource source : mSourceAdapter.getRanked()) {
            if (source.isBroken() || source.getSite().isCloudDrive() || !source.getSite().isChangeable()
                    || !mPlaybackPolicy.available(sourceRoute(source.getSiteKey(), source.getVodId()), now)) continue;
            if (year > 0 && TitleKey.year(source.getVod().getVodYear()) != year) continue;
            return source;
        }
        return null;
    }

    /** Internal provider fallback; the film, semantic episode and time remain pinned. */
    private void switchSource(VodSource next, boolean auto) {
        captureSourceProgress();
        if (mHistory != null) recordPlaybackSwitch(next, mRecoveryPending ? mRecoveryReason : auto ? "fallback" : "manual",
                !auto || mRecoveryManual && mRecoveryPending);
        mRecoveryPending = false; mSelectedQuality = null;
        if (mSourceTask != null && !mSourceTask.isBackground()) stopSourceSearch();
        mManualSourceSelection = !auto;
        setInitAuto(false); mSourceBackground = true;
        mSourceAdapter.setCurrent(next);
        updateSourceView();
        getDetail(next.getVod());
    }

    private void updateSourceView() {
        updateDetailPanels();
    }

    private void updateDetailPanels() {
        if (mBinding == null || mEpisodeAdapter == null || mSourceAdapter == null || mFlagAdapter == null) return;
        boolean hasEpisodes = !mEpisodeAdapter.isEmpty();
        mBinding.episodeTab.setActivated(true);
        mBinding.episodeTab.setTextColor(getColor(R.color.text_primary));
        mBinding.episode.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        mBinding.episodeActions.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Logger.i("PlayDecision: action=retry-parser session=" + mSessionId + " index=" + (position + 1));
        onItemClick(parse);
    }

    private void nextFlag(int position) {
        Flag flag = mFlagAdapter.get(position + 1);
        Logger.i("PlayDecision: action=retry-line session=" + mSessionId + " line=" + flag.getFlag());
        if (com.fongmi.android.tv.search.QualityCatalog.match(flag, mHistory.getVodRemarks(), isQualityMovie()) == null
                || flag.isCloudDrive() || PlaybackRoutePolicy.isRestricted(flag)) {
            if (position + 1 < mFlagAdapter.getItemCount() - 1) nextFlag(position + 1); else checkSearch(false); return;
        }
        captureSourceProgress();
        mPlaybackPolicy.switched(playbackRoute(), SystemClock.elapsedRealtime());
        mAutomaticSwitches++;
        if (!selectFlag(flag, true)) checkFlag();
    }

    private void onPaused() {
        mPlaybackWanted = false;
        mPlaybackPolicy.suspend(SystemClock.elapsedRealtime());
        if (mQualityCatalog != null) mQualityCatalog.budget(0);
        if (mSourceBackground && mSourceTask != null) mSourceTask.pause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPlayers.pause();
        checkPlayImg();
        flushProgress();
    }

    private void onPlay() {
        mPlaybackWanted = true;
        if (mSourceBackground && mSourceTask != null && qualityWorkBudget() > 0) mSourceTask.resume();
        if (mAwaitingCloudSync || mCloudChoicePending) { mResumeAfterCloudSync = true; return; }
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
        this.fullscreen = fullscreen;
        refreshBackHandling();
        applyDetailTopInset();
        Util.toggleFullscreen(this, fullscreen);
        if (!fullscreen) showDetailSystemUI();
        ViewCompat.requestApplyInsets(mBinding.getRoot());
    }

    /**
     * Android 15+ 强制 edge-to-edge 后，详情页会铺到状态栏下面。只给非全屏详情内容补顶部
     * inset，底部继续延伸到手势区，因此返回键和播放器顶栏不会被状态栏挡住，也不会重新
     * 引入小白条保护长条。
     */
    private void applyDetailWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (view, insets) -> {
            Insets statusBars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars());
            mStatusBarInset = statusBars.top;
            applyDetailTopInset();
            return insets;
        });
        ViewCompat.requestApplyInsets(mBinding.getRoot());
    }

    private void applyDetailTopInset() {
        if (mBinding == null) return;
        int top = isFullscreen() ? 0 : mStatusBarInset;
        View root = mBinding.getRoot();
        if (root.getPaddingTop() != top) {
            root.setPadding(root.getPaddingLeft(), top, root.getPaddingRight(), root.getPaddingBottom());
        }
    }

    /**
     * 详情页保留顶部状态栏，但让页面延伸到手势导航区，并关闭系统自动添加的
     * 导航栏对比度保护长条。与全屏切换分开处理，避免改变视频拖动使用的顶部坐标系。
     */
    private void showDetailSystemUI() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (!night && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    /**
     * 竖屏全屏以固定的观看中心线定位，而不是按视频高度或屏幕高度分别计算顶边。
     * 这样不同比例的画面都把垂直中心落在同一条视线上；左右侧操作和居中反馈也跟随画面中心。
     */
    private void applyPortraitViewingOffset(boolean portraitFull) {
        float offset = portraitFull ? -ResUtil.dp2px(PORTRAIT_VIEWING_CENTER_OFFSET_DP) : 0f;
        // Keep the SurfaceView inside its own bounds. A shorter top-aligned viewport puts its
        // center 28dp above the real screen center without translating or cropping the surface.
        FrameLayout.LayoutParams surface = (FrameLayout.LayoutParams) mBinding.playerSurface.getLayoutParams();
        surface.width = FrameLayout.LayoutParams.MATCH_PARENT;
        surface.height = portraitFull
                ? Math.max(1, getRealScreenHeight() - ResUtil.dp2px(PORTRAIT_VIEWING_CENTER_OFFSET_DP) * 2)
                : FrameLayout.LayoutParams.MATCH_PARENT;
        surface.gravity = Gravity.TOP;
        mBinding.playerSurface.setTranslationY(0f);
        mBinding.playerSurface.setLayoutParams(surface);
        mBinding.widget.error.setTranslationY(offset);
        mBinding.widget.progress.setTranslationY(offset);
        mBinding.widget.seek.setTranslationY(offset);
        mBinding.widget.gestureFeedback.setTranslationY(offset);
        // Move the whole left control group. Moving only the danmaku icon made it
        // leave its wrap-content parent and the parent clipped most of the icon.
        mBinding.control.danmaku.setTranslationY(0f);
        // 平板常从横屏布局进入，旋转后 Activity 不会重建。即使某个资源
        // 变体遗漏 left 容器，也要回退到弹幕按钮本身，不能因布局差异崩溃。
        View leftControls = mBinding.control.left != null ? mBinding.control.left : mBinding.control.danmaku;
        leftControls.setTranslationY(offset);
        mBinding.control.right.getRoot().setTranslationY(offset);
        Logger.i("VideoLayout: viewing-offset portrait=" + portraitFull
                + " controlsOffset=" + Math.round(offset)
                + " surfaceHeight=" + surface.height
                + " realScreenH=" + getRealScreenHeight());
    }

    private int getRealScreenHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        return metrics.heightPixels;
    }

    private int getRootScreenTop() {
        int[] location = new int[2];
        mBinding.getRoot().getLocationOnScreen(location);
        return location[1];
    }

    /** A compact geometry snapshot that can be shared from Settings -> Runtime logs. */
    private void logVideoLayout(String stage, float distance) {
        if (mBinding == null) return;
        Logger.i("VideoLayout: " + stage
                + " land=" + isLand()
                + " fullscreen=" + isFullscreen()
                + " distance=" + Math.round(distance)
                + " rootH=" + mBinding.getRoot().getHeight()
                + " rootScreenY=" + getRootScreenTop()
                + " sheetTop=" + mBinding.swipeLayout.getTop()
                + " sheetH=" + mBinding.swipeLayout.getHeight()
                + " sheetTY=" + Math.round(mBinding.swipeLayout.getTranslationY())
                + " videoTop=" + mBinding.video.getTop()
                + " videoH=" + mBinding.video.getHeight()
                + " videoTY=" + Math.round(mBinding.video.getTranslationY())
                + " surfaceTop=" + mBinding.playerSurface.getTop()
                + " surfaceH=" + mBinding.playerSurface.getHeight()
                + " surfaceTY=" + Math.round(mBinding.playerSurface.getTranslationY())
                + " travel=" + getSheetTravel(isLand()));
    }

    /** Six pre-draw samples plus a settled sample; no continuous per-frame logging. */
    private void traceTransition(String reason) {
        if (mBinding == null) return;
        final int trace = ++mTransitionTrace;
        logTransitionFrame(reason, trace);
        View root = mBinding.getRoot();
        android.view.ViewTreeObserver.OnPreDrawListener sample = new android.view.ViewTreeObserver.OnPreDrawListener() {
            int frames;
            @Override public boolean onPreDraw() {
                if (trace != mTransitionTrace || isDestroyed() || ++frames > 6) {
                    if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                } else logTransitionFrame("frame-" + frames, trace);
                return true;
            }
        };
        root.getViewTreeObserver().addOnPreDrawListener(sample);
        root.postDelayed(() -> {
            if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(sample);
            if (trace == mTransitionTrace && !isDestroyed()) logTransitionFrame("settled", trace);
        }, 600);
    }

    private void logTransitionFrame(String stage, int trace) {
        View surface = mBinding.exo.getVideoSurfaceView();
        Logger.i("VideoTransition: id=" + trace + " stage=" + stage + " full=" + isFullscreen() + " land=" + isLand()
                + " window=" + describeVideoLayer(getWindow().getDecorView()) + " root=" + describeVideoLayer(mBinding.getRoot())
                + " video=" + describeVideoLayer(mBinding.video) + " player=" + describeVideoLayer(mBinding.exo)
                + " surface=" + describeVideoLayer(surface) + " sheet=" + mBinding.swipeLayout.getVisibility());
        if (surface instanceof android.view.SurfaceView) {
            android.view.SurfaceHolder holder = ((android.view.SurfaceView) surface).getHolder();
            Logger.i("VideoTransition: id=" + trace + " surfaceFrame=" + holder.getSurfaceFrame() + " valid=" + holder.getSurface().isValid());
        }
    }

    private String describeVideoLayer(View view) {
        if (view == null) return "none";
        android.graphics.drawable.Drawable bg = view.getBackground();
        String color = bg instanceof android.graphics.drawable.ColorDrawable
                ? Integer.toHexString(((android.graphics.drawable.ColorDrawable) bg).getColor()) : bg == null ? "transparent" : bg.getClass().getSimpleName();
        return view.getWidth() + "x" + view.getHeight() + "/" + color + "/v" + view.getVisibility() + "/a" + view.getAlpha();
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
        castEnded = false;
        stopPlaybackCache();
        // 投给另一台装了本 App 的设备（fm 通道）走的是 HTTP 接力，这边没有可控的 DLNA 会话，
        // 维持原来的行为：本地停掉就完事
        if (isCasting()) enterCastMode();
        else onPaused();
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
        // 投屏时本地播放器是停的，得看对端在不在放，否则长按倍速永远不触发
        if (!(isCasting() ? CastManager.get().isPlaying() : mPlayers.isPlaying())) return;
        mBinding.control.action.speed.setText(mPlayers.setSpeed(Setting.getSpeed()));
        syncCastSpeed();
        View tip = mBinding.widget.speedLock;
        tip.animate().cancel();
        tip.setAlpha(1f);
        mBinding.widget.speedLockText.setText(R.string.speed_lock);
        // 先把文字和底色按到 0 再显示：否则第一次长按会闪一下完整的胶囊，
        // 因为这一帧文字还没测量出宽度，onSpeedProgress 会直接 return 到下一帧才归零。
        mBinding.widget.speedLockText.setAlpha(0f);
        tip.getBackground().mutate().setAlpha(0);
        tip.setVisibility(View.VISIBLE);
        onSpeedProgress(0);
        startSpeedIcon();
    }

    /**
     * 长按倍速 → 锁定的跟手过渡。progress 0 是一对箭头停在正中间，1 是完整的
     * 「锁定倍速中」胶囊，中间态跟着手指连续变化，用户才看得出还差多少。
     * <p>
     * 做法是让文字始终占位，只改透明度和整条的位移：progress 越小就把胶囊往右推得越多，
     * 推到 0 时箭头正好落回正中。这样箭头和文字的基线天然对齐，也不会因为宽度变化而跳。
     */
    @Override
    public void onSpeedProgress(float progress) {
        mSpeedProgress = progress;
        View tip = mBinding.widget.speedLock;
        TextView text = mBinding.widget.speedLockText;
        // 胶囊平时是 INVISIBLE 而不是 GONE，就是为了让它一直参与测量：
        // 等到显示那一帧才去拿宽度的话，第一次必然拿到 0，只能 post 到下一帧再补，
        // 表现就是胶囊连着箭头突然往右跳一下。真取不到时用 Paint 直接量文字兜底。
        float width = text.getWidth();
        if (width == 0) width = text.getPaint().measureText(text.getText().toString());
        text.setAlpha(progress);
        // 背景 drawable 是和别的胶囊共用的，不 mutate 会把它们一起改透明
        tip.getBackground().mutate().setAlpha((int) (progress * 255));
        tip.setTranslationX((1 - progress) * (width + ResUtil.dp2px(8)) / 2f);
    }

    @Override
    public void onSpeedEnd() {
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
        syncCastSpeed();
        View tip = mBinding.widget.speedLock;
        if (!isVisible(tip)) return;
        // 没滑到位就松手：胶囊还没显形，直接收掉，不弹"已恢复原速"
        if (mBinding.widget.speedLockText.getAlpha() < 0.9f) {
            hideSpeedTip(0);
            return;
        }
        mBinding.widget.speedLockText.setText(R.string.speed_unlock);
        onSpeedProgress(1);
        hideSpeedTip(600);
    }

    private void hideSpeedTip(long delay) {
        View tip = mBinding.widget.speedLock;
        tip.animate().cancel();
        tip.animate().alpha(0f).setStartDelay(delay).setDuration(180).withEndAction(() -> {
            tip.setVisibility(View.INVISIBLE);
            tip.setAlpha(1f);
            tip.setTranslationX(0f);
            stopSpeedIcon();
            mBinding.widget.speedLockText.setText(R.string.speed_lock);
        }).start();
    }

    private void startSpeedIcon() {
        mSpeedTime = SystemClock.uptimeMillis();
        // 相位 0 是波形最左端，从那儿起步箭头会先瞬移到左边再往右冲；
        // 0.25 正好是波形中点，箭头从中心平滑走起
        mSpeedPhase = 0.25f;
        View icon = mBinding.widget.speedIcon;
        icon.removeCallbacks(mSpeedTick);
        icon.postOnAnimation(mSpeedTick);
    }

    private void stopSpeedIcon() {
        View icon = mBinding.widget.speedIcon;
        icon.removeCallbacks(mSpeedTick);
        icon.setTranslationX(0f);
    }

    /**
     * 箭头的往返动画自己按帧驱动，不用 R.anim.forward——补间动画没法中途调速，
     * 而这里要的是"越接近锁定越慢，锁定那一刻刚好停住"。
     * <p>
     * 调速只靠收缩幅度，相位始终恒速推进。位移 = 波形 × 幅度 ×(1-progress)，
     * 走一个来回的距离随进度线性缩短，视觉速度也就随进度线性变化，
     * progress=1 时幅度归零，箭头正好静止在中心。
     * <p>
     * 别再把 (1-progress) 也乘到相位推进上：那样两处各乘一次，实际速度成了
     * (1-progress)²，刚脱离锁定时慢得几乎不动，接着突然窜起来，完全没有过渡感。
     */
    private void tickSpeedIcon() {
        View icon = mBinding.widget.speedIcon;
        if (!isVisible(mBinding.widget.speedLock)) return;
        long now = SystemClock.uptimeMillis();
        // 卡顿或熄屏回来时 dt 会很大，钳一下免得箭头瞬移
        float delta = Math.min(64, now - mSpeedTime) / (float) SPEED_CYCLE;
        mSpeedTime = now;
        mSpeedPhase = (mSpeedPhase + delta) % 1f;
        // 0→0.5 往右，0.5→1 往左，和原来 repeatMode="reverse" 的三角波一致
        float wave = mSpeedPhase < 0.5f ? mSpeedPhase * 2 : (1 - mSpeedPhase) * 2;
        icon.setTranslationX((wave - 0.5f) * 2 * ResUtil.dp2px(10) * (1 - mSpeedProgress));
        icon.postOnAnimation(mSpeedTick);
    }

    /** 倍速改在本地播放器上，投屏时还得把同一个值推给对端。 */
    private void syncCastSpeed() {
        if (isCasting()) CastManager.get().setSpeed(mPlayers.getSpeed());
    }

    /** 把剧集列表和当前集数推给电脑页面，让它自己也能选集。 */
    private void syncCastEpisodes() {
        if (!isCasting()) return;
        List<Episode> items = getFlag() == null ? new ArrayList<>() : getFlag().getEpisodes();
        List<String> names = new ArrayList<>();
        int index = -1;
        Episode current = getEpisode();
        for (int i = 0; i < items.size(); i++) {
            names.add(items.get(i).getName());
            if (current != null && items.get(i).equals(current)) index = i;
        }
        Pc.setEpisodes(names, index);
    }

    @Override
    public void onCastSelect(int index) {
        List<Episode> items = getFlag() == null ? null : getFlag().getEpisodes();
        if (items == null || index < 0 || index >= items.size()) return;
        Episode item = items.get(index);
        if (item.equals(getEpisode())) return;
        // 走和手机上点剧集同一条路，但不能带 shouldEnterFullscreen：
        // 投屏时把手机切进全屏毫无意义
        mFlagAdapter.toggle(item);
        notifyItemChanged(mEpisodeAdapter);
        mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
        onRefresh();
    }

    @Override
    public void onBright(int progress) {
        if (!mBrightnessAdjusting) {
            View leftControls = mBinding.control.left != null ? mBinding.control.left : mBinding.control.danmaku;
            mLeftControlsVisibility = leftControls.getVisibility();
            leftControls.setVisibility(View.INVISIBLE);
            mBrightnessAdjusting = true;
        }
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
        // 图标位于胶囊底部：亮色进度填到图标区域时用深色，否则保持白色。
        mBinding.widget.brightIcon.setColorFilter(ResUtil.getColor(progress >= 20 ? R.color.black_60 : R.color.white));
    }

    @Override
    public void onBrightEnd() {
        mBinding.widget.bright.setVisibility(View.GONE);
        if (!mBrightnessAdjusting) return;
        View leftControls = mBinding.control.left != null ? mBinding.control.left : mBinding.control.danmaku;
        leftControls.setVisibility(mLeftControlsVisibility);
        mBrightnessAdjusting = false;
    }

    @Override
    public void onVolume(int progress) {
        if (!mVolumeAdjusting) {
            View rightControls = mBinding.control.right.getRoot();
            mRightControlsVisibility = rightControls.getVisibility();
            rightControls.setVisibility(View.INVISIBLE);
            mVolumeAdjusting = true;
        }
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
        mBinding.widget.volumeIcon.setColorFilter(ResUtil.getColor(progress >= 20 ? R.color.black_60 : R.color.white));
    }

    @Override
    public void onVolumeEnd() {
        mBinding.widget.volume.setVisibility(View.GONE);
        if (!mVolumeAdjusting) return;
        mBinding.control.right.getRoot().setVisibility(mRightControlsVisibility);
        mVolumeAdjusting = false;
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
        if (mKeyDown.isSeeking() && !isCasting()) startGestureSeekPreview();
        if (mGestureSeeking) {
            mGestureSeekPosition = getGestureSeekPosition(time);
            mPreview.seek(mGestureSeekPosition);
        }
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(isCasting() ? castPositionTime(time)
                : mPlayers.stringToTime(mGestureSeeking ? mGestureSeekPosition : mPlayers.getPositionValue(time)));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        setSeekDuration();
        hideProgress();
    }

    private void startGestureSeekPreview() {
        if (mGestureSeeking || mPlayers.isEmpty()) return;
        mPlaybackPolicy.seek(SystemClock.elapsedRealtime());
        mGestureSeeking = true;
        mGestureSeekPlaying = mPlayers.isPlayRequested();
        mGestureSeekBasePosition = mPlayers.getPosition();
        mGestureSeekPosition = mGestureSeekBasePosition;
        App.removeCallbacks(mR1);
        if (mGestureSeekPlaying) mPlayers.pause();
        mPlaybackCache.pause();
        cancelBufferingProgress();
        hideProgress();
        mBinding.control.previewFrame.setAlpha(0f);
        mBinding.widget.seekPreviewImage.setVisibility(View.GONE);
        mBinding.widget.seekPreviewBox.setVisibility(View.VISIBLE);
        resizePreview(mBinding.widget.seekPreviewVideo, mBinding.widget.seekPreviewImage, mPreviewRatio);
        mPreview.attach(mBinding.widget.seekPreviewVideo, this);
        Logger.i("SeekGesture: start positionMs=" + mGestureSeekBasePosition
                + ", playing=" + mGestureSeekPlaying
                + ", local=" + PlaybackCache.isFullyLocal(mPlayers.getPreviewItem()));
    }

    private long getGestureSeekPosition(long delta) {
        long position = mGestureSeekBasePosition + delta;
        long duration = currentDuration();
        if (duration > 0 && position > duration) position = duration;
        return Math.max(0, position);
    }

    /** 只有拖动进度时才补总时长；双击快进那种一闪而过的提示不需要。 */
    private void setSeekDuration() {
        long duration = currentDuration();
        boolean show = mKeyDown.isSeeking() && duration > 0;
        mBinding.widget.duration.setText(show ? " / " + mPlayers.stringToTime(duration) : "");
        mBinding.widget.duration.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private long currentDuration() {
        return isCasting() ? CastManager.get().getDuration() : mPlayers.getDuration();
    }

    private void hideSeek() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.seekPreviewBox.setVisibility(View.GONE);
        mBinding.widget.duration.setVisibility(View.GONE);
    }

    // ---------- 拖动进度条的画面预览 ----------

    @Override
    public void onScrubStart(long position) {
        mPlaybackPolicy.seek(SystemClock.elapsedRealtime());
        mScrubbing = true;
        mPreview.attach(mBinding.control.previewVideo, this);
        Logger.i("Seek: start positionMs=" + position + ", playing=" + mPlayers.isPlaying()
                + ", local=" + PlaybackCache.isFullyLocal(mPlayers.getPreviewItem()));
        // 拖动期间把主播放器停下来：源站往往限同 IP 并发，两路一起拉的话预览要等十几秒
        // 才出得来。带宽和解码全让给预览，松手再接着放。
        mScrubPlaying = mPlayers.isPlaying();
        if (mScrubPlaying) mPlayers.pause();
        mPlaybackCache.pause();
        cancelBufferingProgress();
        hideProgress();
        mBinding.control.previewFrame.setAlpha(1f);
        onScrubMove(position);
    }

    @Override
    public void onScrubMove(long position) {
        // 人正按着进度条，控制层不能按"没人操作"自动隐退——这是 beta1/beta2 那个
        // 拖着拖着控制栏自己消失的原因：点屏幕时就已经 post 了一个定时隐藏，
        // 拖动全程没有任何东西取消它。
        App.removeCallbacks(mR1);
        movePreview(position);
        mPreview.seek(position);
    }

    @Override
    public void onScrubStop(long position, boolean canceled) {
        mPlaybackPolicy.seek(SystemClock.elapsedRealtime());
        Logger.i("Seek: stop positionMs=" + position + ", canceled=" + canceled
                + ", playerMs=" + mPlayers.getPosition() + ", bufferedMs=" + mPlayers.getBuffered()
                + ", ready=" + mPlayers.isReady()
                + ", local=" + PlaybackCache.isFullyLocal(mPlayers.getPreviewItem()));
        mBinding.control.previewFrame.setAlpha(0f);
        if (canceled) {
            mPreview.idle();
            mScrubbing = false;
            cancelBufferingProgress();
        } else {
            // The seek happens before this callback. Start the same buffering debounce used
            // by normal playback only after the preview has handed control back.
            mPreview.finish(position);
            mScrubbing = false;
            scheduleBufferingProgress("scrub-seek");
        }
        if (!isCasting()) {
            // CustomSeekView 已先把主播放器 seek 到同一个毫秒目标。保留此前落盘的所有
            // 分片，只让后台任务从新位置向结尾重排；若主画面进入 BUFFERING 会先暂停。
            if (!canceled) mPlaybackCache.focus(position, currentDuration());
            schedulePlaybackCache();
        }
        // CustomSeekView 已经 seek，这里只负责把暂停前的状态还原
        if (mScrubPlaying) mPlayers.play();
        mScrubPlaying = false;
        // 松手了才重新开始计时隐藏
        setR1Callback();
    }

    private void scheduleBufferingProgress(String reason) {
        if (mBufferingProgressPending || mBinding.widget.progress.getVisibility() == View.VISIBLE) return;
        mBufferingProgressPending = true;
        mBufferingProgressStartedAt = SystemClock.uptimeMillis();
        mBufferingProgressReason = reason;
        // Fully cached media normally prepares in a fraction of a second. Avoid flashing a spinner
        // during first-frame decoding, but retain feedback if local I/O actually stalls.
        boolean localStartup = !mLoggedPlaybackReady && (mPlayingDownload != null || OfflinePlayback.isLocal(mPlayers.getUrl()));
        App.post(mShowBufferingProgress, localStartup ? 2000L : BUFFERING_PROGRESS_DELAY_MS);
    }

    private void cancelBufferingProgress() {
        mBufferingProgressPending = false;
        if (mShowBufferingProgress != null) App.removeCallbacks(mShowBufferingProgress);
    }

    @Override
    public void onPreviewRatio(float ratio) {
        // 预览窗宽度固定，高度跟着片源比例走，不然 4:3 的片会被拉扁
        mPreviewRatio = ratio;
        resizePreview(mBinding.control.previewVideo, mBinding.control.previewImage, ratio);
        resizePreview(mBinding.widget.seekPreviewVideo, mBinding.widget.seekPreviewImage, ratio);
    }

    private void resizePreview(View video, View image, float ratio) {
        int width = video.getWidth() > 0 ? video.getWidth() : video.getLayoutParams().width;
        int height = (int) (width / Math.max(0.5f, ratio));
        if (width <= 0 || height <= 0 || height == video.getLayoutParams().height) return;
        video.getLayoutParams().height = height;
        image.getLayoutParams().height = height;
        video.requestLayout();
        image.requestLayout();
    }

    @Override
    public void onPreviewFrame(Bitmap bitmap) {
        if (mGestureSeeking) {
            mBinding.widget.seekPreviewImage.setImageBitmap(bitmap);
            mBinding.widget.seekPreviewImage.setVisibility(View.VISIBLE);
            mBinding.widget.seekPreviewLoading.setVisibility(View.GONE);
        } else {
            mBinding.control.previewImage.setImageBitmap(bitmap);
            mBinding.control.previewImage.setVisibility(View.VISIBLE);
            mBinding.control.previewLoading.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPreviewLoading() {
        // 新帧还没有完成时保留上一帧，并标明正在加载；只有渲染完成才撤掉图片。
        if (mGestureSeeking) mBinding.widget.seekPreviewLoading.setVisibility(View.VISIBLE);
        else mBinding.control.previewLoading.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPreviewReady() {
        if (mGestureSeeking) mBinding.widget.seekPreviewImage.setVisibility(View.GONE);
        else mBinding.control.previewImage.setVisibility(View.GONE);
        if (mGestureSeeking) mBinding.widget.seekPreviewLoading.setVisibility(View.GONE);
        else mBinding.control.previewLoading.setVisibility(View.GONE);
    }

    @Override
    public void onPreviewReset() {
        // 换集必须清掉旧画面，否则新片源首帧出来前会短暂显示上一集。
        mBinding.control.previewImage.setImageDrawable(null);
        mBinding.control.previewImage.setVisibility(View.GONE);
        mBinding.control.previewLoading.setVisibility(View.GONE);
        mBinding.widget.seekPreviewImage.setImageDrawable(null);
        mBinding.widget.seekPreviewImage.setVisibility(View.GONE);
        mBinding.widget.seekPreviewLoading.setVisibility(View.GONE);
    }

    @Override
    public void onPreviewFail() {
        // 预览这一路挂了就别占着地方，主播放器不受影响
        if (mGestureSeeking) mBinding.widget.seekPreviewBox.setVisibility(View.GONE);
        else mBinding.control.previewFrame.setAlpha(0f);
    }

    /**
     * 预览小窗停在滑块正上方，贴到两边时夹住不让它出框。
     * 横坐标要按 timeBar 自己的左边界和宽度算——进度条左右各有一个时间文本，
     * 拿整个 CustomSeekView 或者屏幕宽度算都会偏。
     */
    private void movePreview(long position) {
        long duration = currentDuration();
        if (duration <= 0) return;
        View box = mBinding.control.previewBox;
        int boxWidth = box.getWidth();
        // 第一帧还没测量出宽度，等布局完再摆一次。必须带上"还在拖"的判断，
        // 否则松手后 previewFrame 隐藏、宽度永远是 0，这里会无限重投下去。
        if (boxWidth == 0) {
            if (mBinding.control.previewFrame.getAlpha() > 0) box.post(() -> movePreview(position));
            return;
        }
        int[] location = new int[2];
        mBinding.control.previewFrame.getLocationInWindow(location);
        float ratio = Math.max(0f, Math.min(1f, position / (float) duration));
        float thumbX = mBinding.control.seek.getTimeBarLeftInWindow() + mBinding.control.seek.getTimeBarWidth() * ratio;
        float x = thumbX - location[0] - boxWidth / 2f;
        box.setTranslationX(Math.max(0f, Math.min(x, mBinding.control.previewFrame.getWidth() - boxWidth)));
    }

    /** 投屏时进度来自对端，不能拿本地播放器算滑动后的目标时间。 */
    private String castPositionTime(long delta) {
        long duration = CastManager.get().getDuration();
        long time = CastManager.get().getPosition() + delta;
        if (duration > 0 && time > duration) time = duration;
        if (time < 0) time = 0;
        return mPlayers.stringToTime(time);
    }

    @Override
    public void onSeekEnd(long time) {
        if (isCasting()) {
            hideSeek();
            CastManager.get().seekTo(CastManager.get().getPosition() + time);
            return;
        }
        if (mGestureSeeking) finishGestureSeekPreview(false);
        else handleLandscapeSeek(time);
    }

    @Override
    public void onSeekCancel() {
        if (mGestureSeeking) finishGestureSeekPreview(true);
        else hideSeek();
    }

    private void finishGestureSeekPreview(boolean canceled) {
        mPlaybackPolicy.seek(SystemClock.elapsedRealtime());
        long position = mGestureSeekPosition;
        boolean resume = mGestureSeekPlaying;
        Logger.i("SeekGesture: stop positionMs=" + position + ", canceled=" + canceled
                + ", playerMs=" + mPlayers.getPosition() + ", bufferedMs=" + mPlayers.getBuffered());
        hideSeek();
        if (canceled) {
            mPreview.idle();
            cancelBufferingProgress();
        } else {
            mPreview.finish(position);
        }
        mPreview.attach(mBinding.control.previewVideo, this);
        mGestureSeeking = false;
        mGestureSeekPlaying = false;
        if (!canceled) {
            mPlayers.seekTo(position);
            mPlaybackCache.focus(position, currentDuration());
            scheduleBufferingProgress("gesture-seek");
        }
        schedulePlaybackCache();
        if (resume) mPlayers.play();
        setR1Callback();
    }
    
    private void handleLandscapeSeek(long time) {
        mPlaybackPolicy.seek(SystemClock.elapsedRealtime());
        hideSeek();
        // time 是相对偏移量，先换算成唯一的绝对目标位置，再只跳转一次。
        // 原逻辑把绝对播放位置和相对偏移量比较，几乎必然误判失败并重复 seek，
        // 同一次手势会跳两遍、缓冲两遍，网络视频因此更容易短暂黑屏。
        mPlayers.seekTo(mPlayers.getPositionValue(time));
    }

    @Override
    public void onScaleChanged(boolean transformed) {
        mBinding.widget.screenRestore.setVisibility(transformed ? View.VISIBLE : View.GONE);
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
        if (isCasting()) {
            boolean wasPlaying = CastManager.get().isPlaying();
            CastManager.get().toggle();
            showGestureFeedback(wasPlaying ? R.drawable.exo_icon_play : R.drawable.exo_icon_pause);
            return;
        }
        if (mPlayers.isEmpty()) {
            checkPlay();
            return;
        }
        boolean wasPlaying = mPlayers.isPlayRequested();
        checkPlay();
        showGestureFeedback(wasPlaying ? R.drawable.exo_icon_play : R.drawable.exo_icon_pause);
    }

    @Override
    public void onDoubleTapLeft() {
        mPlaybackPolicy.seek(SystemClock.elapsedRealtime());
        long seekTime = -TimeUnit.SECONDS.toMillis(Setting.getGestureSeekSeconds());
        if (isCasting()) {
            CastManager.get().seekTo(Math.max(0, CastManager.get().getPosition() + seekTime));
        } else {
            long newPosition = Math.max(0, mPlayers.getPosition() + seekTime);
            mPlayers.seekTo(newPosition);
        }
        onSeek(seekTime);
        App.post(() -> mBinding.widget.seek.setVisibility(View.GONE), 800);
    }

    @Override
    public void onDoubleTapRight() {
        mPlaybackPolicy.seek(SystemClock.elapsedRealtime());
        long seekTime = TimeUnit.SECONDS.toMillis(Setting.getGestureSeekSeconds());
        if (isCasting()) {
            long duration = CastManager.get().getDuration();
            long newPosition = CastManager.get().getPosition() + seekTime;
            CastManager.get().seekTo(duration > 0 ? Math.min(duration, newPosition) : newPosition);
        } else {
            long duration = mPlayers.getDuration();
            long newPosition = Math.min(duration > 0 ? duration : Long.MAX_VALUE, mPlayers.getPosition() + seekTime);
            mPlayers.seekTo(newPosition);
        }
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
        // 投屏中本地没有画面，进画中画只会弹一个黑框出来
        if (isCasting()) return;
        if (mPlayers.haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, mPlayers.getVideoWidth(), mPlayers.getVideoHeight(), getScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (!isFullscreen()) setVideoView(isInPictureInPictureMode);
        applyPortraitViewingOffset(!isInPictureInPictureMode && isFullscreen() && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT);
        if (isInPictureInPictureMode) {
            hideControl();
            hideDanmaku();
            hideSheet();
        } else {
            // VPN/系统浮层切换网络时可能让 Activity 短暂离开前台并触发画中画生命周期。
            // 这里不能因为 onStop 已执行就 finish，否则会退回首页并触发整页历史刷新。
            mPausedByScreen = false;
            setStop(false);
            showDanmaku();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        traceTransition("configuration-" + newConfig.orientation);
        if (!isFullscreen()) applyOrientation();
        if (isFullscreen()) Util.hideSystemUI(this);
        applyPortraitViewingOffset(isFullscreen() && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && !mPiP.isInMode(this));
        // configChanges 不会重建布局；必须等 video 完成新尺寸测量后再算按钮，
        // 否则竖屏转横屏仍会拿旧窄宽度，把本该恢复的按钮继续藏到下一次点击。
        mBinding.video.postDelayed(() -> {
            if (isVisible(mBinding.control.getRoot())) showControl();
            else setActionVisible();
        }, 80);
        updateTimeBattery();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;
        if (isFullscreen()) Util.hideSystemUI(this);
        else showDetailSystemUI();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAwaitingCloudSync = mPlayingDownload == null && !OfflinePlayback.isLocal(mPlayers.getUrl())
                && Util.isNetworkAvailable() && !isCasting() && !mPlayers.isPlaying() && App.isAwaitingForegroundSync();
        mResumeAfterCloudSync = mAwaitingCloudSync;
        if (mAwaitingCloudSync && mHistory != null) Notify.show("正在同步观看进度…");
        mClock.stop();
        if (!mAwaitingCloudSync) mClock.start();
        setStop(false);
        if (!mAwaitingCloudSync && !isCasting() && offerCloudProgress()) return;
        if (!isCasting()) onPlay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        logFilePermissions("resume");
        startTimeBatteryUpdates();
        App.removeCallbacks(mPlaybackWatchdog); App.post(mPlaybackWatchdog, 1000);
        if (mInitialSelection) App.post(mFinishInitialSelection, 100);
        if (isCasting()) {
            // 从后台回来重新接上投屏会话：期间可能已经换过集或被电视停掉
            CastManager.get().setListener(this);
            enterCastMode();
        }
        if (isRedirect() && !isCasting()) onPlay();
        setRedirect(false);
        retryDetailAfterNetwork();
        if (mSourceTask != null) mSourceTask.resume();
        if (mHistory != null && mCurrentVod != null) prepareQualityCatalog(mHistory.getVodRemarks());
        App.removeCallbacks(mQualityMaintenance); App.post(mQualityMaintenance, 1500);
        if (mSourceTask == null && mCurrentVod != null) checkQuick();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimeBatteryUpdates();
        if (isRedirect()) onPaused();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Logger.i("FilePermission: action=result session=" + mSessionId + " requestCode=" + requestCode
                + " permissions=" + Arrays.toString(permissions) + " results=" + Arrays.toString(grantResults));
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        logFilePermissions("result");
    }

    @Override
    protected void onStop() {
        super.onStop();
        flushProgress();
        mPlaybackPolicy.suspend(SystemClock.elapsedRealtime());
        if (mSourceTask != null) mSourceTask.pause();
        App.removeCallbacks(mR5);
        if (mQualityCatalog != null) mQualityCatalog.setActive(false);
        App.removeCallbacks(mQualityMaintenance, mPlaybackWatchdog, mFinishInitialSelection);
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
        if (!mHistoryPlaybackConfirmed || mHistory == null || mAwaitingCloudSync || mCloudChoicePending || mSuppressHistorySaves || Setting.isIncognito()) return;
        if (isCasting() && isCastSwitching()) return;
        long position = isCasting() ? CastManager.get().getPosition() : mPlayers.getPosition();
        long duration = isCasting() ? CastManager.get().getDuration() : mPlayers.getDuration();
        boolean changed = position >= 0 && position != mHistory.getPosition();
        if (position >= 0) mHistory.setPosition(position);
        if (duration > 0) mHistory.setDuration(duration);
        queueHistorySnapshot(changed, true);
    }

    private void queueHistorySnapshot(boolean watched, boolean flush) {
        // Local video can start during sync, but must not overwrite a cloud position before it is compared.
        if (mPlayingDownload != null && App.isAwaitingForegroundSync()) return;
        if (!mHistoryPlaybackConfirmed || mHistory == null || mAwaitingCloudSync || mCloudChoicePending || mSuppressHistorySaves || Setting.isIncognito()) return;
        History snapshot = History.objectFrom(mHistory.toString());
        snapshot.setAccountId(mHistory.getAccountId());
        if (watched) {
            mLastHistoryCapture = Math.max(System.currentTimeMillis(), Math.max(mLastHistoryCapture, mKnownHistoryVersion) + 1);
            snapshot.setCreateTime(mLastHistoryCapture);
        }
        int generation = mHistoryWriteGeneration;
        App.execute(() -> {
            boolean written;
            synchronized (mHistoryWriteLock) {
                if (generation != mHistoryWriteGeneration || mAwaitingCloudSync || mCloudChoicePending || mSuppressHistorySaves
                        || snapshot.getCreateTime() < mKnownHistoryVersion) return;
                written = snapshot.savePlayback(mKnownHistoryVersion);
                if (written) mKnownHistoryVersion = snapshot.getCreateTime();
            }
            if (written) {
                App.post(() -> {
                    if (generation == mHistoryWriteGeneration && mHistory != null)
                        mHistory.setCreateTime(Math.max(mHistory.getCreateTime(), snapshot.getCreateTime()));
                });
                if (flush) com.fongmi.android.tv.utils.WebDAVSyncManager.get().flushPendingSync();
            } else App.post(this::offerCloudProgress);
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onForegroundSyncEvent(com.fongmi.android.tv.event.ForegroundSyncEvent event) {
        if (isFinishing() || isDestroyed() || isStop()) return;
        boolean resume = mResumeAfterCloudSync;
        mAwaitingCloudSync = false;
        mResumeAfterCloudSync = false;
        mClock.stop().start();
        if (event.success && offerCloudProgress()) return;
        if (!event.success && resume) Notify.show("本次云端同步未成功，继续本机进度");
        if (resume && !isCasting()) onPlay();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProfileChanged(com.fongmi.android.tv.event.ProfileChangedEvent event) {
        mSuppressHistorySaves = true;
        mHistoryWriteGeneration++;
        mPlayers.pause();
        finish();
    }

    private boolean offerCloudProgress() {
        if (mHistory == null || mCloudChoicePending || mAwaitingCloudSync || mSuppressHistorySaves || isStop() || isFinishing() || isDestroyed()) return false;
        History latest = null;
        for (History record : mHistory.find()) {
            if (record.getCreateTime() > mKnownHistoryVersion && (latest == null || record.getCreateTime() > latest.getCreateTime())) latest = record;
        }
        if (latest == null) return false;
        if (TextUtils.equals(latest.getVodRemarks(), mHistory.getVodRemarks()) && Math.abs(latest.getPosition() - mHistory.getPosition()) < 3000) {
            synchronized (mHistoryWriteLock) { mKnownHistoryVersion = latest.getCreateTime(); }
            return false;
        }
        History remote = latest;
        mCloudChoicePending = true;
        mPlayers.pause();
        checkPlayImg();
        String localTime = android.text.format.DateUtils.formatElapsedTime(Math.max(0, mHistory.getPosition()) / 1000);
        String cloudTime = android.text.format.DateUtils.formatElapsedTime(Math.max(0, remote.getPosition()) / 1000);
        mCloudProgressDialog = com.fongmi.android.tv.ui.dialog.CloudProgressDialog.show(this,
                mHistory.getVodName(), mHistory.getVodRemarks() + " · " + localTime,
                remote.getVodRemarks() + " · " + cloudTime, () -> {
                    synchronized (mHistoryWriteLock) {
                        mHistoryWriteGeneration++;
                        mKnownHistoryVersion = remote.getCreateTime();
                        mHistory.setCreateTime(remote.getCreateTime());
                    }
                    mCloudChoicePending = false;
                    onPlay();
                }, () -> {
                    if (seekToCloudProgress(remote)) return;
                    mSuppressHistorySaves = true;
                    mHistoryWriteGeneration++;
                    mPlayers.pause();
                    mClock.setCallback(null);
                    mPlayers.reset();
                    mPlayers.stop();
                    mHistoryPlaybackConfirmed = false;
                    mPendingCloudHistory = History.objectFrom(App.gson().toJson(remote));
                    mCloudResumePosition = Math.max(0, remote.getPosition());
                    GroupCache.remove(getGroupToken());
                    getIntent().removeExtra("group");
                    getIntent().removeExtra("mark");
                    getIntent().removeExtra("offline");
                    getIntent().putExtra("key", remote.getSiteKey()).putExtra("id", remote.getVodId())
                            .putExtra("name", remote.getVodName()).putExtra("pic", remote.getVodPic());
                    resetSources();
                    mCloudChoicePending = false;
                    mSuppressHistorySaves = false;
                    checkId();
                });
        return true;
    }

    /** Keep the active source and decoder when only the playback position changed. */
    private boolean seekToCloudProgress(History remote) {
        if (!mHistoryPlaybackConfirmed || mPlayers.isEmpty() || mPlayers.isIdle() || isCasting()
                || !isSameCloudEpisode(remote)) return false;
        long position = Math.max(0, remote.getPosition());
        long duration = mPlayers.getDuration();
        if (duration > 0) position = Math.min(position, duration);
        synchronized (mHistoryWriteLock) {
            // Discard queued writes from before the choice, but keep this device's source/episode URL.
            mHistoryWriteGeneration++;
            mKnownHistoryVersion = Math.max(mKnownHistoryVersion, remote.getCreateTime());
            mLastHistoryCapture = Math.max(mLastHistoryCapture, mKnownHistoryVersion);
            mHistory.setCreateTime(mKnownHistoryVersion);
            mHistory.setPosition(position);
            if (duration > 0) mHistory.setDuration(duration);
        }
        mPendingCloudHistory = null;
        mCloudResumePosition = -1;
        mPlaybackPolicy.seek(SystemClock.elapsedRealtime());
        mPlayers.seekTo(position);
        mCloudChoicePending = false;
        mResumeAfterCloudSync = false;
        // Do not use onPlay(): its ended-state shortcut can overwrite a seek with the opening time.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPlayers.play();
        mPlaybackWanted = true;
        checkPlayImg();
        mClock.stop().start();
        queueHistorySnapshot(true, true);
        return true;
    }

    private boolean isSameCloudEpisode(History remote) {
        if (mHistory == null || !TextUtils.equals(mHistory.getVodName(), remote.getVodName())) return false;
        int kind = mCurrentVod == null ? TitleKey.KIND_UNKNOWN : TitleKey.kind(mCurrentVod.getTypeName());
        if (kind == TitleKey.KIND_MOVIE && mHistory.getEpisodeCount() == 0 && remote.getEpisodeCount() == 0) return true;
        // Prefer named episode identity over source list positions (sources can omit episodes or extras).
        String local = mHistory.getVodRemarks().trim();
        String cloud = remote.getVodRemarks().trim();
        if (!local.isEmpty() && local.equalsIgnoreCase(cloud)) return true;
        if (!mHistory.getEpisodeUrl().isEmpty() && mHistory.getEpisodeUrl().equals(remote.getEpisodeUrl())) return true;
        int localNumber = com.fongmi.android.tv.search.EpisodeKey.number(local);
        int cloudNumber = com.fongmi.android.tv.search.EpisodeKey.number(cloud);
        boolean series = kind == TitleKey.KIND_TV || mHistory.getEpisodeCount() > 1;
        // Only ordinary numbered episode labels qualify; dates, specials and quality labels do not.
        return series && localNumber > 0 && localNumber == cloudNumber
                && com.fongmi.android.tv.search.EpisodeKey.key(local).equals(com.fongmi.android.tv.search.EpisodeKey.key(cloud));
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
                            hideSeek();
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
                            hideSeek();
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
    protected boolean shouldInterceptBack() {
        if (mBinding == null) return false;
        return mBinding.playbackPanel.isPanelVisible()
                || (isFullscreen() && !isLock())
                || isVisible(mBinding.control.getRoot())
                || isLock();
    }

    @Override
    protected View getPredictiveBackTarget() {
        if (mBinding.playbackPanel.isPanelVisible()) return mBinding.playbackPanel;
        if (isFullscreen()) return mBinding.getRoot();
        if (isVisible(mBinding.control.getRoot())) return mBinding.control.getRoot();
        return mBinding.getRoot();
    }

    @Override
    protected boolean shouldAnimatePredictiveBack() {
        if (isFullscreen()) return false;
        return !isLock() || mBinding.playbackPanel.isPanelVisible() || isVisible(mBinding.control.getRoot());
    }

    @Override
    protected void onBackPress() {
        if (mBinding.playbackPanel.isPanelVisible()) {
            mBinding.playbackPanel.dismiss();
            setR1Callback();
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (!isLock()) {
            stopSourceSearch();
            super.onBackPress();
        }
    }

    @Override
    protected void onDestroy() {
        logPlaybackSummary("exit");
        App.removeCallbacks(mQualityMaintenance, mPlaybackWatchdog, mFinishInitialSelection);
        if (mQualityCatalog != null) mQualityCatalog.release();
        if (mCloudProgressDialog != null) mCloudProgressDialog.dismiss();
        mRatingGeneration++;
        super.onDestroy();
        stopSourceSearch();
        // 横竖屏之外的配置变化会重建页面，重建后还要靠它找回片源组，只在真正关闭时清掉
        if (isFinishing()) GroupCache.remove(getGroupToken());
        // 只摘监听不断投屏：退出播放页时电视该继续放，常驻通知里还能暂停和退出投屏
        CastManager.get().removeListener(this);
        mPlayers.release();
        mPreview.release();
        mPlaybackCache.release();
        mClock.release();
        Timer.get().reset();
        RefreshEvent.history();
        PlaybackService.stop();
        mHandler.removeCallbacksAndMessages(null);
        App.removeCallbacks(mR1, mR2, mR3, mR4, mR5, mR6, mCacheWarmup, mShowBufferingProgress, mDoubanRetry, mRetryDetailConfig);
        EventBus.getDefault().unregister(this);
        mViewModel.result.removeObserver(mObserveDetail);
        mViewModel.player.removeObserver(mObservePlayer);
        stopTimeBatteryUpdates();
        com.fongmi.android.tv.utils.ToastFilter.leavePlayback(this);
    }

    private void logPlaybackSummary(String reason) {
        if (mHistory == null) return;
        Logger.i("PlaySummary: session=" + mSessionId + " reason=" + reason + " entry=" + mEntry
                + " film=" + mHistory.getFilmId() + " episode=" + mPlaybackEpisode + " positionMs=" + mHistory.getPosition()
                + " firstReadyMs=" + mPlaybackPolicy.firstReadyMs() + " attempts=" + mPlaybackPolicy.attempts()
                + " stalls=" + mPlaybackPolicy.stallCount() + " stalledMs=" + mPlaybackPolicy.stalledMs()
                + " longestStallMs=" + mPlaybackPolicy.longestStallMs() + " autoChanges=" + mAutomaticSwitches
                + " manualChanges=" + mManualSwitches + " confirmed=" + mHistoryPlaybackConfirmed);
    }
}
