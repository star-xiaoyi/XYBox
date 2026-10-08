package com.fongmi.android.tv.ui.fragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.VodConfigProbe;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.search.SourceIdentity;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ForegroundSyncEvent;
import com.fongmi.android.tv.event.ProfileChangedEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.activity.*;
import com.fongmi.android.tv.ui.custom.SecondaryGlassToolbarView;
import com.fongmi.android.tv.utils.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

/** Profile overview with separate identity, source and sync editors. */
public class ProfileFragment extends com.fongmi.android.tv.ui.base.BaseFragment {
    private LinearLayout body;
    private ImageView avatar, accountAvatar;
    private TextView nickname, signature, empty, keepCount, historyCount, downloadCount;
    private RecyclerView historyList;
    private RecentAdapter adapter;
    private AlertDialog accountDialog;
    private SourceManager sourceManager;
    private final Set<AlertDialog> dialogs = new HashSet<>();
    private final Map<AlertDialog, FrameLayout> dialogHosts = new HashMap<>();
    private String pickingProfile;
    private int posterWidth;
    private final ActivityResultLauncher<String> pickAvatar = registerForActivityResult(new ActivityResultContracts.GetContent(), this::saveAvatar);

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int color(int id) { return requireContext().getColor(id); }
    private boolean night() { return (getResources().getConfiguration().uiMode & 0x30) == 0x20; }
    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) pickingProfile = state.getString("picking_profile");
    }

    @Override protected androidx.viewbinding.ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(color(R.color.screen_background));
        NestedScrollView scroll = new NestedScrollView(context);
        scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        body = column(); body.setPadding(dp(16), 0, dp(16), dp(104));
        scroll.addView(body, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        SecondaryGlassToolbarView toolbar = new SecondaryGlassToolbarView(context);
        toolbar.setTitle("我的"); toolbar.setBackVisible(false); toolbar.setProfileStyle(true);
        toolbar.setPrimaryAction(R.drawable.ic_profile_settings, R.string.nav_setting);
        toolbar.setPrimaryActionClickListener(v -> ProfileSettingsActivity.start(requireActivity()));
        toolbar.attachContent(body, body);
        root.addView(toolbar, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout identity = row(); identity.setPadding(dp(8), 0, dp(8), dp(24));
        avatar = new ImageView(context); avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(rounded(color(R.color.surface_secondary), 32)); avatar.setClipToOutline(true);
        avatar.setContentDescription("打开账号");
        identity.addView(avatar, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout details = column();
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1); detailsParams.setMarginStart(dp(12));
        identity.addView(details, detailsParams);
        LinearLayout nameRow = row(); nickname = text("", 20, true, false);
        nickname.setMaxLines(1); nickname.setEllipsize(TextUtils.TruncateAt.END);
        android.graphics.drawable.Drawable arrow = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_arrow_right).mutate();
        arrow.setTint(color(R.color.text_secondary)); arrow.setBounds(0,0,dp(12),dp(12));
        nickname.setCompoundDrawablesRelative(null,null,arrow,null); nickname.setCompoundDrawablePadding(dp(4));
        nameRow.addView(nickname, new LinearLayout.LayoutParams(-2, -2));
        nameRow.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if(r>l && nickname.getMaxWidth()!=r-l) nickname.setMaxWidth(r-l); });
        details.addView(nameRow);
        signature = text("", 13, false, true); signature.setMaxLines(2); signature.setEllipsize(TextUtils.TruncateAt.END);
        signature.setPadding(0, dp(8), 0, 0); details.addView(signature);
        nickname.setOnClickListener(v -> showAccount()); avatar.setOnClickListener(v -> showAccount());
        LinearLayout actions = row();
        identityAction(actions,"点播源",R.drawable.ic_fab_link,this::showSource);
        identityAction(actions,"WebDAV",R.drawable.ic_action_sync,this::showCloud);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-2,-2); actionParams.setMarginStart(dp(8));
        identity.addView(actions,actionParams);
        body.addView(identity, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout library = row(); library.setPadding(dp(8), dp(15), dp(8), dp(15)); library.setBackground(surface(22));
        keepCount = libraryItem(library, "我的收藏", R.drawable.ic_profile_heart, 0xFFFFE8EF, () -> KeepActivity.start(requireActivity()));
        historyCount = libraryItem(library, "观看历史", R.drawable.ic_profile_history, 0xFFE2EFFF, () -> HistoryActivity.start(requireActivity()));
        downloadCount = libraryItem(library, "离线下载", R.drawable.ic_profile_download, 0xFFDDF9ED, () -> DownloadActivity.start(requireActivity()));
        body.addView(library, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout recent = column(); recent.setBackground(surface(22)); recent.setPadding(dp(12), dp(8), 0, dp(16));
        LinearLayout heading = row(); heading.setPadding(0, 0, dp(12), dp(2));
        heading.addView(text("最近在看", 18, true, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = text("查看全部  ›", 12, false, true); all.setGravity(Gravity.CENTER); all.setMinHeight(dp(44));
        all.setOnClickListener(v -> HistoryActivity.start(requireActivity())); heading.addView(all); recent.addView(heading);
        historyList = new RecyclerView(context); historyList.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
        historyList.setItemAnimator(null); historyList.setClipToPadding(false); historyList.setOverScrollMode(View.OVER_SCROLL_NEVER);
        posterWidth = dp(102); historyList.setAdapter(adapter = new RecentAdapter());
        recent.addView(historyList, new LinearLayout.LayoutParams(-1, -2));
        empty = text("还没有观看记录，去首页发现一部好电影吧", 13, false, true);
        empty.setPadding(dp(4), dp(20), dp(16), dp(24)); recent.addView(empty);
        LinearLayout.LayoutParams recentParams = new LinearLayout.LayoutParams(-1, -2); recentParams.topMargin = dp(12); body.addView(recent, recentParams);
        historyList.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int width = Math.max(dp(96), Math.min(dp(136), Math.round((r-l) / 3.45f)));
            if (r > l && posterWidth != width) { posterWidth = width; historyList.post(() -> { if (body != null) adapter.notifyDataSetChanged(); }); }
        });

        LinearLayout footer = row(); footer.setBackground(surface(22)); footer.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout version = footerItem(footer, "版本号", BuildConfig.VERSION_NAME, () -> Updater.create().force().release().start(requireActivity()));
        version.setOnLongClickListener(v -> { Updater.create().force().dev().start(requireActivity()); return true; });
        footerItem(footer, "关于", "XY影视  ›", this::showAbout);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2); footerParams.topMargin = dp(12); body.addView(footer, footerParams);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int margin = Math.max(dp(16), (r-l-dp(760))/2);
            if (body != null && body.getPaddingLeft() != margin) body.setPadding(margin, body.getPaddingTop(), margin, body.getPaddingBottom());
        });
        refresh(); return () -> root;
    }

    private LinearLayout column() { LinearLayout view = new LinearLayout(requireContext()); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(requireContext()); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private void identityAction(LinearLayout parent, String label, int icon, Runnable action) {
        LinearLayout tile = column(); tile.setGravity(Gravity.CENTER);
        ImageView image = new ImageView(requireContext()); image.setImageResource(icon); image.setColorFilter(color(R.color.text_primary));
        image.setPadding(dp(10),dp(10),dp(10),dp(10)); image.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(color(R.color.separator)),surface(22),null));
        tile.addView(image,new LinearLayout.LayoutParams(dp(40),dp(40)));
        parent.addView(tile,new LinearLayout.LayoutParams(dp(48),-2));
        tile.setContentDescription(label); tile.setOnClickListener(v -> action.run());
    }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable surface(int radius) { return rounded(night() ? 0xFF18181B : 0xFFFFFFFF, radius); }
    private TextView text(String value, int size, boolean bold, boolean secondary) {
        TextView view = new TextView(requireContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(color(secondary ? R.color.text_secondary : R.color.text_primary));
        if (bold) view.setTypeface(null, Typeface.BOLD); return view;
    }
    private TextView libraryItem(LinearLayout parent, String title, int icon, int halo, Runnable action) {
        LinearLayout tile = row(); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout group = row(); tile.addView(group,new LinearLayout.LayoutParams(-2,-2));
        ImageView image = new ImageView(requireContext()); image.setImageResource(icon);
        image.setPadding(dp(8),dp(8),dp(8),dp(8)); image.setBackground(rounded(night() ? (halo & 0x00FFFFFF) | 0x30000000 : halo, 22));
        group.addView(image, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout labels = column(); LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(-2, -2); labelsParams.setMarginStart(dp(4)); group.addView(labels, labelsParams);
        TextView label = text(title, 10, false, true); label.setSingleLine(true); label.setGravity(Gravity.CENTER); labels.addView(label,new LinearLayout.LayoutParams(-1,-2));
        TextView count = text("0", 21, true, false); count.setGravity(Gravity.CENTER); labels.addView(count,new LinearLayout.LayoutParams(-1,-2));
        parent.addView(tile, new LinearLayout.LayoutParams(0, -2, 1)); tile.setOnClickListener(v -> action.run()); return count;
    }
    private LinearLayout footerItem(LinearLayout parent, String title, String subtitle, Runnable action) {
        LinearLayout tile = column(); tile.setPadding(dp(14),dp(12),dp(14),dp(12));
        tile.addView(text(title,14,true,false)); TextView detail = text(subtitle,12,false,true); detail.setPadding(0,dp(5),0,0);tile.addView(detail);
        parent.addView(tile,new LinearLayout.LayoutParams(0,-2,1));tile.setOnClickListener(v -> action.run());return tile;
    }
    private void renderAvatar(ImageView target) {
        if (target == null) return;
        target.clearColorFilter(); String path = LocalProfile.avatar();
        if (!path.isEmpty() && new File(path).isFile()) target.setImageURI(Uri.fromFile(new File(path)));
        else { target.setImageResource(R.drawable.ic_nav_profile); target.setColorFilter(color(R.color.text_secondary)); }
    }
    private void refresh() {
        if (body == null) return;
        nickname.setText(LocalProfile.name()); signature.setText(LocalProfile.signature()); renderAvatar(avatar); renderAvatar(accountAvatar);
        List<History> histories = History.getAll(); adapter.items.clear(); adapter.items.addAll(histories.subList(0, Math.min(12, histories.size()))); adapter.notifyDataSetChanged();
        historyCount.setText(String.valueOf(histories.size())); keepCount.setText(String.valueOf(Keep.getVod().size()));
        int downloads = com.fongmi.android.tv.bean.Download.group(com.fongmi.android.tv.bean.Download.getAll()).size();
        downloadCount.setText(String.valueOf(downloads));
        boolean waiting = histories.isEmpty() && (App.isAwaitingForegroundSync() || WebDAVSyncManager.get().isSyncing());
        empty.setText(waiting ? "正在加载观看记录…" : "还没有观看记录，去首页发现一部好电影吧");
        empty.setVisibility(histories.isEmpty() ? View.VISIBLE : View.GONE); historyList.setVisibility(histories.isEmpty() ? View.GONE : View.VISIBLE);
        com.github.catvod.utils.Logger.i("HistoryScreen: page=profile state=" + (waiting ? "loading" : histories.isEmpty() ? "empty" : "ready")
                + " count=" + histories.size() + " profile=" + LocalProfile.id());
    }

    private LinearLayout dialogForm(String description) {
        LinearLayout form = column();
        form.setPadding(dp(24),dp(4),dp(24),dp(12));
        form.setFocusableInTouchMode(true);
        TextView caption = text(description,12,false,true);
        if (!description.isEmpty()) { caption.setPadding(0,0,0,dp(8)); form.addView(caption); }
        return form;
    }
    private NestedScrollView dialogScroll(LinearLayout form) {
        NestedScrollView scroll = new NestedScrollView(requireContext());
        scroll.setFillViewport(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(form,new FrameLayout.LayoutParams(-1,-2));
        form.requestFocus(); return scroll;
    }
    private TextView dialogAction(LinearLayout parent, String label, Runnable action) {
        TextView button = text(label,13,true,false); button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44)); button.setPadding(dp(12),dp(8),dp(12),dp(8));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(color(R.color.separator)),rounded(color(R.color.surface_secondary),14),null));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(12);
        parent.addView(button,params); if(action!=null)button.setOnClickListener(v->action.run()); return button;
    }
    private TextView dialogStatus(LinearLayout form) {
        TextView status=text("",12,false,true); status.setMinLines(2);status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END); status.setPadding(0,dp(12),0,0);form.addView(status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);return status;
    }
    private TextInputEditText field(LinearLayout parent, String hint, String value, int type) {
        TextView label=text(hint,12,true,true);label.setPadding(0,dp(12),0,dp(6));parent.addView(label);
        TextInputLayout box = new TextInputLayout(requireContext());
        box.setHintEnabled(false); box.setHintAnimationEnabled(false);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_FILLED); box.setBoxBackgroundColor(color(R.color.surface_secondary));
        box.setBoxCornerRadii(dp(14),dp(14),dp(14),dp(14)); box.setBoxStrokeWidth(0);box.setBoxStrokeWidthFocused(0);
        TextInputEditText input = new TextInputEditText(box.getContext()); input.setInputType(type); input.setTextSize(14);
        input.setTextColor(color(R.color.text_primary));input.setText(value);input.setSingleLine(true);
        input.setMinHeight(dp(48));input.setPadding(dp(14),dp(12),dp(14),dp(12));
        input.setId(View.generateViewId());label.setLabelFor(input.getId());
        box.addView(input, new LinearLayout.LayoutParams(-1,-2));
        if ((type & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD) box.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        parent.addView(box,new LinearLayout.LayoutParams(-1,-2));return input;
    }
    private String value(EditText input) { return input.getText().toString().trim(); }
    private void showAccount() {
        if (accountDialog != null && accountDialog.isShowing()) return;
        String profile = LocalProfile.id();
        LinearLayout form = dialogForm("头像、昵称和签名，让这里更像你。");
        LinearLayout portraitRow = row(); accountAvatar = new ImageView(requireContext());accountAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        accountAvatar.setBackground(surface(26));accountAvatar.setClipToOutline(true);portraitRow.addView(accountAvatar,new LinearLayout.LayoutParams(dp(52),dp(52)));renderAvatar(accountAvatar);
        TextView change = text("更换头像  ›",13,true,false);change.setPadding(dp(16),dp(14),dp(16),dp(14));portraitRow.addView(change);form.addView(portraitRow);
        View.OnClickListener picker=v->{pickingProfile=profile;pickAvatar.launch("image/*");};change.setOnClickListener(picker);accountAvatar.setOnClickListener(picker);
        TextInputEditText name=field(form,"昵称",LocalProfile.name(),InputType.TYPE_CLASS_TEXT);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        TextInputEditText bio=field(form,"个性签名",LocalProfile.signature(),InputType.TYPE_CLASS_TEXT);bio.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        dialogAction(form,"切换 / 添加账号",()->{accountDialog.dismiss();chooseAccount();});
        AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle("编辑账号").setView(dialogScroll(form))
            .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        accountDialog=dialog;showCentered(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!profile.equals(LocalProfile.id())){dialog.dismiss();return;}
            if(value(name).isEmpty()){name.setError("请输入昵称");name.requestFocus();return;}
            LocalProfile.rename(value(name));LocalProfile.setSignature(value(bio));dialog.dismiss();refresh();
        });
    }
    private void showSource() {
        if (sourceManager != null && sourceManager.dialog.isShowing()) return;
        sourceManager = new SourceManager(); sourceManager.show();
    }

    private TextView sourceButton(LinearLayout parent, String label, boolean primary, boolean destructive, Runnable action) {
        TextView button = text(label, 12, true, false); button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44)); button.setPadding(dp(6), dp(10), dp(6), dp(10));
        button.setTextColor(primary ? color(R.color.action_on_primary) : destructive ? 0xFFE5484D : color(R.color.text_primary));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(color(R.color.separator)),
                rounded(primary ? color(R.color.action_primary) : color(R.color.surface_secondary), 12), null));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (parent.getChildCount() > 0) params.setMarginStart(dp(8));
        parent.addView(button, params); button.setOnClickListener(v -> action.run()); return button;
    }

    private void sourceHeading(LinearLayout parent, String label) {
        LinearLayout header = row(); header.setTag("dialog-heading"); header.setMinimumHeight(dp(44));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(12);
        parent.addView(header, params);
        TextView title = text(label, 20, true, false); title.setIncludeFontPadding(false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private View sourceDialogContent(LinearLayout form, String title) {
        return sourceDialogContent(form, title, null);
    }

    private View sourceDialogContent(LinearLayout form, String title, View footer) {
        LinearLayout content = column(), header = column();
        header.setPadding(dp(24), dp(12), dp(24), 0);
        sourceHeading(header, title);
        content.addView(header);
        // Keep the title and close button together while only the source list/form scrolls.
        content.addView(dialogScroll(form), new LinearLayout.LayoutParams(-1, footer == null ? -2 : 0, footer == null ? 0 : 1));
        if (footer != null) content.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        return content;
    }

    private static String sourceError(Exception error) {
        if (error instanceof java.net.SocketTimeoutException || error instanceof java.io.InterruptedIOException)
            return "连接超时，请检查网络后重试";
        String message = error.getMessage();
        if (message == null || message.isEmpty()) return "无法读取配置，请检查地址和网络";
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    /**
     * 彩蛋：新增配置时地址栏填 XY，一次补齐作者自用的五个点播源；已有的跳过，只补缺的。
     * 地址写法必须和设备上保存的完全一致（含末尾斜杠），站点 key 才能对上已有的历史记录。
     */
    private static final String[][] PRESET_SOURCES = {
            {"饭太硬", "http://www.饭太硬.net/tv"},
            {"多仓源", "https://gh-proxy.com/raw.githubusercontent.com/yw88075/tvbox/main/yw.json"},
            {"小盒子", "http://xhztv.top/4k.json"},
            {"王小二", "http://new.王二小放牛娃.top"},
            {"肥猫", "http://肥猫.net/"},
    };

    private final class SourceManager {
        private final Map<String, SourceCheck> checks = new HashMap<>();
        private final Map<String, TextView> checkViews = new HashMap<>();
        private final Set<String> selected = new HashSet<>();
        private final Set<String> requestTags = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private AlertDialog dialog;
        private LinearLayout list;
        private TextView summary, scan, add, selectFailed, removeSelected;
        private ExecutorService executor;
        private int generation, completed, total;
        private boolean scanning, applying, managing;
        private LinearLayout selection;
        private FrameLayout menuLayer;
        private View menuCard;
        private android.animation.ValueAnimator menuAnimation;
        private com.fongmi.android.tv.ui.custom.SourceMenuLayout.Bounds menuBounds;
        private int menuGeneration;
        private boolean menuClosing;

        void show() {
            LinearLayout form = dialogForm("");
            LinearLayout actions = row(); form.addView(actions);
            add = sourceButton(actions, "＋ 添加配置", true, false, () -> new SourceEditor(this, null).show());
            scan = sourceButton(actions, "一键检测", false, false, this::scan);
            summary = dialogStatus(form); summary.setMinLines(1); summary.setMaxLines(4);
            selection = row(); selection.setPadding(0, dp(8), 0, dp(8)); form.addView(selection);
            selectFailed = sourceButton(selection, "选择失败项", false, false, () -> {
                for (Map.Entry<String, SourceCheck> entry : checks.entrySet()) if (entry.getValue().failed) selected.add(entry.getKey());
                render();
            });
            removeSelected = sourceButton(selection, "删除所选", false, true, () -> delete(selectedConfigs()));
            list = column(); form.addView(list);
            dialog = new MaterialAlertDialogBuilder(requireContext()).setView(sourceDialogContent(form, "点播源")).create();
            showCentered(dialog, 520);
            dialog.setOnDismissListener(d -> { closeMenu(false, null); cancelScan(); dialogs.remove(dialog); dialogHosts.remove(dialog); if (sourceManager == this) sourceManager = null; });
            render();
        }

        List<Config> configs() {
            List<Config> items = new ArrayList<>();
            for (Config item : Config.getAll(0)) if (!item.isEmpty()) items.add(item);
            return items;
        }

        List<Config> selectedConfigs() {
            List<Config> items = new ArrayList<>();
            for (Config item : configs()) if (selected.contains(item.getUrl())) items.add(item);
            return items;
        }

        void render() {
            if (!isAdded() || dialog == null || !dialog.isShowing()) return;
            List<Config> items = configs();
            Set<String> urls = new HashSet<>(); for (Config item : items) urls.add(item.getUrl());
            selected.retainAll(urls);
            int failed = 0; for (String url : urls) { SourceCheck check = checks.get(url); if (check != null && check.failed) failed++; }
            summary.setText(scanning ? "正在检测 " + completed + " / " + total + "，可随时停止"
                    : applying ? "正在更新来源…"
                    : "已保存 " + items.size() + " 个配置" + (failed > 0 ? " · " + failed + " 个检测失败，是否删除由你决定" : ""));
            scan.setText(scanning ? "停止检测" : "一键检测"); scan.setEnabled(!applying && !items.isEmpty());
            add.setEnabled(!scanning && !applying);
            selectFailed.setEnabled(!scanning && !applying && failed > 0);
            removeSelected.setText("删除所选" + (selected.isEmpty() ? "" : "（" + selected.size() + "）"));
            removeSelected.setEnabled(!scanning && !applying && !selected.isEmpty());
            for (View button : new View[]{scan, add, selectFailed, removeSelected}) button.setAlpha(button.isEnabled() ? 1f : 0.45f);
            selection.setVisibility(managing ? View.VISIBLE : View.GONE);
            closeMenu(false, null);
            list.removeAllViews();
            checkViews.clear();
            if (items.isEmpty()) {
                TextView hint = text("还没有配置\n点击上方“添加配置”，粘贴地址后先测试。", 13, false, true);
                hint.setGravity(Gravity.CENTER); hint.setPadding(dp(8), dp(24), dp(8), dp(24)); list.addView(hint); return;
            }
            String active = "";
            for (Config item : items) {
                boolean current = VodConfig.isEnabled(item);
                LinearLayout card = column(); card.setPadding(dp(14), dp(12), dp(14), dp(12));
                GradientDrawable background = rounded(color(current ? R.color.surface_primary : R.color.surface_secondary), 18);
                card.setAlpha(current ? 1f : 0.48f);
                background.setStroke(dp(1), selected.contains(item.getUrl()) ? color(R.color.action_primary) : color(R.color.separator)); card.setBackground(background);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2); cardParams.topMargin = dp(10); list.addView(card, cardParams);
                LinearLayout heading = row(); card.addView(heading);
                CheckBox checkbox = new CheckBox(requireContext()); checkbox.setContentDescription("选择配置 " + item.getDesc());
                checkbox.setChecked(selected.contains(item.getUrl())); checkbox.setEnabled(!scanning && !applying);
                checkbox.setVisibility(managing ? View.VISIBLE : View.GONE);
                heading.addView(checkbox, new LinearLayout.LayoutParams(dp(36), dp(36)));
                checkbox.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) selected.add(item.getUrl()); else selected.remove(item.getUrl());
                    removeSelected.setText("删除所选" + (selected.isEmpty() ? "" : "（" + selected.size() + "）"));
                    removeSelected.setEnabled(!selected.isEmpty()); removeSelected.setAlpha(selected.isEmpty() ? 0.45f : 1f);
                });
                TextView name = text(TextUtils.isEmpty(item.getName()) ? "未命名配置" : item.getName(), 15, true, false);
                name.setMaxLines(1); name.setEllipsize(TextUtils.TruncateAt.END); heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                TextView badge = text(current ? "已启用" : "已停用", 10, false, true); heading.addView(badge);
                TextView address = text(item.getUrl(), 12, false, true); address.setMaxLines(1); address.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                address.setPadding(0, dp(4), 0, 0); card.addView(address);
                TextView state = text(checkText(item), 12, false, true); state.setPadding(0, dp(8), 0, 0); state.setVisibility(checks.containsKey(item.getUrl()) ? View.VISIBLE : View.GONE); card.addView(state);
                checkViews.put(item.getUrl(), state);
                SourceCheck check = checks.get(item.getUrl()); if (check != null && check.failed) state.setTextColor(0xFFE5484D);
                ImageView more = new ImageView(requireContext()); more.setImageResource(R.drawable.ic_control_more);
                more.setColorFilter(color(R.color.text_primary)); more.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                more.setPadding(dp(12), dp(12), dp(12), dp(12)); more.setContentDescription(item.getDesc() + "的更多操作");
                more.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(color(R.color.separator)), null, rounded(android.graphics.Color.WHITE, 22)));
                LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(44), dp(44)); moreParams.setMarginStart(dp(4)); heading.addView(more, moreParams);
                more.setEnabled(!scanning && !applying); more.setAlpha(more.isEnabled() ? 1f : 0.45f);
                more.setOnClickListener(v -> { if (!scanning && !applying) showMenu(more, item); });
                card.setOnLongClickListener(v -> { if (scanning || applying) return true; managing = true; selected.add(item.getUrl()); render(); return true; });
                card.setOnClickListener(v -> { if (managing && !scanning && !applying) checkbox.setChecked(!checkbox.isChecked()); });

            }
        }

        void showMenu(View anchor, Config item) {
            if (!dialog.isShowing() || !anchor.isAttachedToWindow() || scanning || applying) return;
            closeMenu(false, null);
            FrameLayout host = dialogHosts.get(dialog);
            if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return;
            // Take one snapshot before any card can be rebuilt; animations never retain the anchor.
            int[] anchorLocation = new int[2]; anchor.getLocationInWindow(anchorLocation);
            int anchorWidth = anchor.getWidth(), anchorHeight = anchor.getHeight();
            int token = ++menuGeneration;
            FrameLayout overlay = new FrameLayout(requireContext());
            overlay.setClipChildren(false); overlay.setClipToPadding(false);
            overlay.setFocusableInTouchMode(true); overlay.setOnClickListener(v -> closeMenu(true, null));
            overlay.setOnKeyListener((v, key, event) -> {
                if (key != android.view.KeyEvent.KEYCODE_BACK) return false;
                if (event.getAction() == android.view.KeyEvent.ACTION_UP) closeMenu(true, null);
                return true;
            });
            menuLayer = overlay;
            FrameLayout panel = new FrameLayout(requireContext());
            GradientDrawable surface = rounded(color(R.color.surface_primary), 20);
            surface.setStroke(dp(1), color(R.color.separator));
            panel.setBackground(surface); panel.setElevation(dp(18)); panel.setClipToOutline(true);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                panel.setOutlineAmbientShadowColor(0xFF000000); panel.setOutlineSpotShadowColor(0xFF000000);
            }
            panel.setOnClickListener(v -> {}); menuCard = panel;
            LinearLayout menu = column(); menu.setPadding(dp(6), dp(6), dp(6), dp(6));
            addMenuItem(menu, "编辑", R.drawable.ic_profile_settings, false, item, "edit", () -> new SourceEditor(this, item).show());
            addMenuItem(menu, "站点管理", R.drawable.ic_action_site, false, item, "sites", () -> new SourceEditor(this, item).show(true));
            addMenuItem(menu, "检测", R.drawable.ic_action_refresh, false, item, "test", () -> scan(Collections.singletonList(item)));
            boolean enabled = VodConfig.isEnabled(item);
            addMenuItem(menu, enabled ? "停用此源" : "启用此源", R.drawable.ic_site_block, false, item, "toggle", () -> { VodConfig.setEnabled(item, !enabled); reload(); });
            addMenuItem(menu, "复制地址", R.drawable.ic_setting_copy, false, item, "copy", () -> {
                ((android.content.ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE))
                        .setPrimaryClip(android.content.ClipData.newPlainText("点播配置地址", item.getUrl())); Notify.tip("地址已复制");
            });
            addMenuItem(menu, "删除", R.drawable.ic_action_delete, true, item, "delete", () -> delete(Collections.singletonList(item)));
            NestedScrollView scroll = new NestedScrollView(requireContext()); scroll.setFillViewport(false);
            scroll.setClipToPadding(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.addView(menu, new ViewGroup.LayoutParams(-1, -2)); panel.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            int availableWidth = Math.max(1, host.getWidth() - host.getPaddingLeft() - host.getPaddingRight());
            int availableHeight = Math.max(1, host.getHeight() - host.getPaddingTop() - host.getPaddingBottom());
            int requestedWidth = Math.max(1, Math.min(dp(208), availableWidth - dp(24)));
            menu.measure(View.MeasureSpec.makeMeasureSpec(requestedWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int[] hostLocation = new int[2]; host.getLocationInWindow(hostLocation);
            menuBounds = com.fongmi.android.tv.ui.custom.SourceMenuLayout.calculate(availableWidth, availableHeight,
                    anchorLocation[0] - hostLocation[0] - host.getPaddingLeft(),
                    anchorLocation[1] - hostLocation[1] - host.getPaddingTop(), anchorWidth, anchorHeight,
                    requestedWidth, menu.getMeasuredHeight(), dp(12), dp(16));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(menuBounds.width, menuBounds.height);
            params.leftMargin = menuBounds.x; params.topMargin = menuBounds.y;
            panel.setPivotX(menuBounds.pivotX); panel.setPivotY(menuBounds.pivotY);
            panel.setScaleX(menuBounds.startScaleX); panel.setScaleY(menuBounds.startScaleY); panel.setAlpha(0f);
            overlay.addView(panel, params); host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
            overlay.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (oldRight > oldLeft && oldBottom > oldTop && (right-left != oldRight-oldLeft || bottom-top != oldBottom-oldTop)) closeMenu(false, null);
            });
            // Android's predictive back dispatches through the dialog rather than the focused View.
            androidx.activity.OnBackPressedCallback back = new androidx.activity.OnBackPressedCallback(true) {
                @Override public void handleOnBackPressed() { closeMenu(true, null); }
            };
            dialog.getOnBackPressedDispatcher().addCallback(dialog, back);
            overlay.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {}
                @Override public void onViewDetachedFromWindow(View view) { back.remove(); }
            });
            overlay.requestFocus();
            panel.post(() -> { if (token == menuGeneration && overlay.isAttachedToWindow()) animateMenu(true, token, null); });
            com.github.catvod.utils.Logger.i("SourceMenu: open configId=" + item.getId());
        }

        void animateMenu(boolean opening, int token, Runnable action) {
            View card = menuCard;
            com.fongmi.android.tv.ui.custom.SourceMenuLayout.Bounds bounds = menuBounds;
            if (card == null || bounds == null) return;
            float fromX = card.getScaleX(), fromY = card.getScaleY(), fromAlpha = card.getAlpha();
            menuAnimation = android.animation.ValueAnimator.ofFloat(0f, 1f);
            menuAnimation.setDuration(opening ? 280 : 160);
            menuAnimation.setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0.8f, 0.2f, 1f));
            menuAnimation.addUpdateListener(animation -> {
                if (token != menuGeneration || !card.isAttachedToWindow()) return;
                float fraction = (float) animation.getAnimatedValue();
                card.setScaleX(fromX + ((opening ? 1f : bounds.startScaleX)-fromX)*fraction);
                card.setScaleY(fromY + ((opening ? 1f : bounds.startScaleY)-fromY)*fraction);
                card.setAlpha(fromAlpha + ((opening ? 1f : 0f)-fromAlpha)*fraction);
            });
            menuAnimation.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (token != menuGeneration) return;
                    menuAnimation = null;
                    if (!opening) { closeMenu(false, null); if (action != null && isAdded() && dialog.isShowing()) action.run(); }
                }
            });
            menuAnimation.start();
        }

        void closeMenu(boolean animated, Runnable action) {
            if (menuLayer == null) return;
            if (animated && menuClosing) return;
            int token = ++menuGeneration;
            if (menuAnimation != null) { menuAnimation.cancel(); menuAnimation = null; }
            if (animated && menuCard != null && menuCard.isAttachedToWindow()) {
                menuClosing = true; animateMenu(false, token, action); return;
            }
            FrameLayout layer = menuLayer; menuLayer = null; menuCard = null; menuBounds = null; menuClosing = false;
            if (layer.getParent() instanceof ViewGroup) ((ViewGroup) layer.getParent()).removeView(layer);
        }

        void addMenuItem(LinearLayout menu, String label, int icon, boolean destructive, Config item, String event, Runnable action) {
            LinearLayout row = row(); row.setPadding(dp(12), dp(8), dp(12), dp(8)); row.setMinimumHeight(dp(44));
            row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(color(R.color.separator)), rounded(android.graphics.Color.TRANSPARENT, 12), null));
            ImageView image = new ImageView(requireContext()); image.setImageResource(icon); image.setColorFilter(destructive ? 0xFFE5484D : color(R.color.text_primary));
            row.addView(image, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView name = text(label, 14, false, false); name.setPadding(dp(12), 0, 0, 0);
            if (destructive) name.setTextColor(0xFFE5484D); row.addView(name);
            menu.addView(row, new LinearLayout.LayoutParams(-1, -2));
            row.setOnClickListener(v -> {
                if (menuClosing) return;
                com.github.catvod.utils.Logger.i("SourceMenu: action=" + event + " configId=" + item.getId());
                closeMenu(true, action);
            });
        }

        String checkText(Config item) {
            SourceCheck check = checks.get(item.getUrl());
            if (check != null) return check.message;
            try { return "未检测 · 上次保存含 " + VodConfigProbe.countSites(item.getJson()) + " 个站点"; }
            catch (Exception ignored) { return "未检测"; }
        }

        void updateCheck(String url) {
            SourceCheck check = checks.get(url); TextView view = checkViews.get(url);
            if (check != null && view != null) { view.setText(check.message); view.setTextColor(check.failed ? 0xFFE5484D : color(R.color.text_secondary)); }
            summary.setText("正在检测 " + completed + " / " + total + "，可随时停止");
        }

        void scan() {
            if (scanning) { cancelScan(); render(); return; }
            scan(configs());
        }

        void scan(List<Config> items) {
            if (scanning || applying || items.isEmpty()) return;
            scanning = true; completed = 0; total = items.size(); int token = ++generation;
            executor = Executors.newFixedThreadPool(3);
            for (Config item : items) {
                String url = item.getUrl(); String tag = "config-probe-" + UUID.randomUUID(); requestTags.add(tag);
                checks.put(url, new SourceCheck(false, "等待检测…"));
                executor.execute(() -> {
                    App.post(() -> { if (generation == token && dialog.isShowing()) { checks.put(url, new SourceCheck(false, "正在检测…")); updateCheck(url); } });
                    SourceCheck outcome;
                    try { VodConfigProbe.Result result = VodConfigProbe.test(url, tag); outcome = new SourceCheck(false, "地址有效 · " + result.siteCount + " 个站点 · " + result.elapsedMs + " ms"); }
                    catch (Exception error) { outcome = new SourceCheck(true, "检测失败：" + sourceError(error)); }
                    finally { requestTags.remove(tag); }
                    SourceCheck result = outcome;
                    App.post(() -> {
                        if (generation != token || !dialog.isShowing()) return;
                        checks.put(url, result); completed++;
                        if (completed == total) { scanning = false; executor.shutdown(); executor = null; render(); }
                        else updateCheck(url);
                    });
                });
            }
            render();
        }

        void cancelScan() {
            generation++; scanning = false;
            if (executor != null) { executor.shutdownNow(); executor = null; }
            for (String tag : requestTags) OkHttp.cancel(tag); requestTags.clear();
            checks.entrySet().removeIf(entry -> entry.getValue().message.equals("等待检测…") || entry.getValue().message.equals("正在检测…"));
        }

        void reload() {
            applying = true; render();
            VodConfig.get().load(new Callback() {
                @Override public void success() { done(); }
                @Override public void error(String message) { Notify.tip(message); done(); }
                private void done() { applying = false; RefreshEvent.config(); RefreshEvent.video(); render(); }
            });
        }

        void delete(List<Config> items) {
            if (items.isEmpty()) return;
            List<Config> deleting = new ArrayList<>(items);
            String message = "删除所选的 " + deleting.size() + " 个配置？收藏和观看记录会保留。";
            AlertDialog confirm = new MaterialAlertDialogBuilder(requireContext()).setTitle("删除点播配置").setMessage(message)
                    .setNegativeButton("取消", null).setPositiveButton("删除", (d, which) -> {
                        for (Config item : deleting) {
                            Config.delete(item.getUrl(), 0); selected.remove(item.getUrl()); checks.remove(item.getUrl());
                        }
                        WebDAVSyncManager.get().requestSync(); reload();
                    }).create(); showCentered(confirm);
        }
    }

    private static final class SourceCheck {
        final boolean failed; final String message;
        SourceCheck(boolean failed, String message) { this.failed = failed; this.message = message; }
    }

    private final class SourceEditor {
        private final SourceManager manager;
        private final Config original;
        private final String tag = "config-probe-" + UUID.randomUUID();
        private AlertDialog dialog;
        private TextInputEditText name, address;
        private TextView status, test, saveButton;
        private VodConfigProbe.Result tested;
        private long testedAt;
        private boolean busy, sitesExpanded;
        private TextView sitesHeading;
        private LinearLayout sitesList;
        private String sitesAddress = "";
        private List<Site> editorSites = Collections.emptyList();
        private final Map<String, Boolean> siteChoices = new HashMap<>();
        private final Map<String, Boolean> sitePriorities = new HashMap<>();
        private final Map<String, View> siteRows = new HashMap<>();

        SourceEditor(SourceManager manager, Config original) { this.manager = manager; this.original = original; }

        void show() { show(false); }

        void show(boolean manageSites) {
            sitesExpanded = manageSites;
            LinearLayout form = dialogForm("");
            name = field(form, "配置名称 · 选填", original == null ? "" : original.getName(), InputType.TYPE_CLASS_TEXT);
            name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
            address = field(form, "配置地址", original == null ? "" : original.getUrl(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            TextInputLayout addressBox = (TextInputLayout) address.getParent().getParent();
            addressBox.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            addressBox.setEndIconDrawable(R.drawable.ic_setting_paste);
            addressBox.setEndIconContentDescription("粘贴地址");
            addressBox.setEndIconOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
                    CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
                    if (text != null) { address.setText(text.toString().trim()); address.setSelection(address.length()); }
                }
            });
            sitesHeading = dialogAction(form, "站点管理", () -> { sitesExpanded = !sitesExpanded; renderSites(); if (sitesExpanded) run(0); });
            sitesHeading.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            sitesHeading.setCompoundDrawablePadding(dp(8));
            sitesList = column(); form.addView(sitesList);
            if (original != null) loadSites(original); else renderSites();
            status = dialogStatus(form); status.setMinLines(0); status.setMaxLines(8); status.setText("");
            LinearLayout actions = row(); actions.setPadding(dp(24), dp(12), dp(24), dp(16));
            test = sourceButton(actions, "测试", false, false, () -> run(0));
            saveButton = sourceButton(actions, "保存", true, false, () -> run(1));
            dialog = new MaterialAlertDialogBuilder(requireContext()).setView(sourceDialogContent(form, original == null ? "添加点播源" : "编辑点播源", actions)).create();
            showCentered(dialog, 520);
            dialog.setOnDismissListener(d -> { tested = null; OkHttp.cancel(tag); dialogs.remove(dialog); dialogHosts.remove(dialog); });
            address.addTextChangedListener(new com.fongmi.android.tv.ui.custom.CustomTextListener() {
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) { tested = null; editorSites = Collections.emptyList(); renderSites(); address.setError(null); status.setText("地址已更改，需要重新测试"); }
            });
            setBusy(false, false);
            if (manageSites) run(0);
        }

        void setBusy(boolean value, boolean applying) {
            busy = value; name.setEnabled(!value); address.setEnabled(!value); test.setEnabled(!value); saveButton.setEnabled(!value);
            sitesHeading.setEnabled(!value);
            for (int i=0; i<sitesList.getChildCount(); i++) {
                View station = sitesList.getChildAt(i); station.setEnabled(!value);
                if (station instanceof ViewGroup) for (int j=0; j<((ViewGroup) station).getChildCount(); j++) ((ViewGroup) station).getChildAt(j).setEnabled(!value);
            }
            test.setText(value ? "检测中…" : "测试"); test.setAlpha(value ? 0.45f : 1f);
            if (sitesExpanded) renderSites();
            dialog.setCancelable(!applying);
        }

        void run(int action) {
            if (busy) return;
            if (original == null && value(address).equalsIgnoreCase("XY")) { importPresets(); return; }
            String input;
            try { input = VodConfigProbe.normalize(value(address)); }
            catch (Exception error) { address.setError(error.getMessage()); address.requestFocus(); status.setText("未保存：地址格式不正确"); return; }
            if (tested != null && tested.url.equals(input) && android.os.SystemClock.elapsedRealtime() - testedAt < 60000) { if (action == 0) showResult(); else save(action); return; }
            setBusy(true, false); status.setText("正在读取配置并识别站点…");
            App.execute(() -> {
                try {
                    VodConfigProbe.Result result = VodConfigProbe.test(input, tag);
                    App.post(() -> {
                        if (!isAdded() || !dialog.isShowing()) return;
                        if (!result.url.equals(value(address))) address.setText(result.url);
                        if (value(name).isEmpty() && !result.name.isEmpty()) name.setText(result.name);
                        tested = result; testedAt = android.os.SystemClock.elapsedRealtime(); setBusy(false, false); showResult();
                        if (action != 0) save(action);
                    });
                } catch (Exception error) {
                    App.post(() -> { if (!isAdded() || !dialog.isShowing()) return; tested = null; setBusy(false, false); status.setText("检测失败：" + sourceError(error) + "\n未保存，当前配置保持不变。"); });
                }
            });
        }

        void importPresets() {
            setBusy(true, true); status.setText("正在导入 XY 内置的 5 个点播源…");
            App.execute(() -> {
                int added = 0, skipped = 0, unverified = 0;
                for (String[] preset : PRESET_SOURCES) {
                    String url = preset[1];
                    if (com.fongmi.android.tv.db.AppDatabase.get().getConfigDao().find(url, 0) != null) { skipped++; continue; }
                    String json = "";
                    // 读不到配置也照样保存：VodConfig.load 遇到空 json 会在加载时再取一次。
                    try { json = VodConfigProbe.test(url, tag).json; } catch (Exception error) { unverified++; }
                    Config target = Config.create(0).url(url).name(preset[0]).json(json);
                    target.setTime(System.currentTimeMillis());
                    target.insert();
                    VodConfig.setEnabled(target, true);
                    manager.checks.put(url, new SourceCheck(json.isEmpty(), json.isEmpty() ? "暂未读到配置，加载时会再试" : "地址有效"));
                    added++;
                }
                int done = added, existed = skipped, pending = unverified;
                App.post(() -> {
                    if (!isAdded() || !dialog.isShowing()) return;
                    setBusy(false, false);
                    if (done > 0) WebDAVSyncManager.get().requestSync();
                    Notify.show(done == 0 ? "五个源都已存在，无需导入" : "已导入 " + done + " 个源"
                            + (existed > 0 ? "，" + existed + " 个已存在" : "") + (pending > 0 ? "，" + pending + " 个暂未读到配置" : ""));
                    manager.reload(); dialog.dismiss();
                });
            });
        }

        void showResult() {
            loadSites(Config.create(0).url(tested.url).json(tested.json));
            status.setText("测试成功 · " + tested.siteCount + " 个站点 · " + tested.elapsedMs + " ms"
                    + (tested.fromCollection ? "\n已从地址合集展开首个配置，保存的是上方显示的地址。" : "")
    );
        }

        void loadSites(Config config) {
            if (!Objects.equals(sitesAddress, config.getUrl())) { siteChoices.clear(); sitePriorities.clear(); sitesAddress = config.getUrl(); }
            editorSites = VodConfig.configuredSites(config);
            for (Site site : editorSites) {
                siteChoices.putIfAbsent(site.getKey(), VodConfig.isSiteEnabled(site));
                sitePriorities.putIfAbsent(site.getKey(), com.fongmi.android.tv.search.SiteHealth.isPriority(site));
            }
            renderSites();
        }

        void renderSites() {
            if (sitesList == null) return;
            updateSitesHeading();
            sitesList.setVisibility(sitesExpanded ? View.VISIBLE : View.GONE); sitesList.removeAllViews(); siteRows.clear();
            if (!sitesExpanded) return;
            if (editorSites.isEmpty()) { TextView hint = text(busy ? "正在读取站点…" : "点击站点管理即可识别站点", 12, false, true); hint.setPadding(0, dp(12), 0, dp(8)); sitesList.addView(hint); return; }
            for (Site site : editorSites) {
                CheckBox toggle = new CheckBox(requireContext()); toggle.setText(site.getName()); toggle.setTextSize(13);
                toggle.setTextColor(color(R.color.text_primary)); toggle.setMinHeight(dp(44)); toggle.setChecked(siteChoices.getOrDefault(site.getKey(), true));
                LinearLayout station = row(); sitesList.addView(station, new LinearLayout.LayoutParams(-1, -2));
                siteRows.put(site.getKey(), station);
                toggle.setContentDescription("启用站点 " + site.getName()); toggle.setEnabled(!busy); station.addView(toggle, new LinearLayout.LayoutParams(0, -2, 1));
                TextView priority = text(sitePriorities.getOrDefault(site.getKey(), false) ? "★ 优先搜索" : "设为优先", 11, false, true);
                priority.setPadding(dp(8), dp(12), dp(8), dp(12)); priority.setEnabled(!busy); station.addView(priority);
                priority.setTextColor(color(sitePriorities.getOrDefault(site.getKey(), false) ? R.color.action_primary : R.color.text_secondary));
                priority.setOnClickListener(v -> {
                    boolean chosen = !sitePriorities.getOrDefault(site.getKey(), false);
                    sitePriorities.put(site.getKey(), chosen);
                    priority.setText(chosen ? "★ 优先搜索" : "设为优先");
                    priority.setTextColor(color(chosen ? R.color.action_primary : R.color.text_secondary));
                });
                toggle.setOnCheckedChangeListener((button, checked) -> {
                    siteChoices.put(site.getKey(), checked);
                    updateSitesHeading();
                    sortSiteRows();
                });
            }
            sortSiteRows();
        }

        void updateSitesHeading() {
            int enabled = 0; for (Site site : editorSites) if (siteChoices.getOrDefault(site.getKey(), true)) enabled++;
            sitesHeading.setText("站点管理" + (editorSites.isEmpty() ? (busy ? " · 正在识别…" : " · 点击识别") : " · " + enabled + " / " + editorSites.size()));
            android.graphics.drawable.Drawable arrow = androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(),
                    sitesExpanded ? R.drawable.ic_detail_collapse : R.drawable.ic_detail_expand);
            if (arrow != null) { arrow = arrow.mutate(); androidx.core.graphics.drawable.DrawableCompat.setTint(arrow, color(R.color.text_secondary)); }
            sitesHeading.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrow, null);
            sitesHeading.setContentDescription("站点管理，" + (sitesExpanded ? "收起" : "展开") + "，已启用 " + enabled + " / " + editorSites.size());
        }

        void sortSiteRows() {
            // Preserve the configuration order within each group and reuse the row views.
            int position = 0;
            for (boolean enabled : new boolean[]{true, false}) for (Site site : editorSites) {
                if (siteChoices.getOrDefault(site.getKey(), true) != enabled) continue;
                View station = siteRows.get(site.getKey());
                if (station == null) continue;
                if (sitesList.indexOfChild(station) != position) {
                    sitesList.removeView(station);
                    sitesList.addView(station, position);
                }
                position++;
            }
        }

        void save(int action) {
            Config duplicate = com.fongmi.android.tv.db.AppDatabase.get().getConfigDao().find(tested.url, 0);
            if (duplicate != null && (original == null || duplicate.getId() != original.getId())) { status.setText("该地址已保存，可从列表编辑。"); return; }
            Config target = original == null ? Config.create(0) : Config.find(original.getId());
            if (target == null) { status.setText("该配置已被删除，请重新添加。"); return; }
            if (original != null && !target.getUrl().equals(tested.url)) {
                WebDAVSyncManager.get().markConfigDeleted(target);
                target.home("").parse("");
            }
            target.url(tested.url).name(value(name)).json(tested.json);
            // Selection is stored separately, so recording a save timestamp no longer
            // switches sources. Sync needs it to retain edits and re-added addresses.
            target.setTime(System.currentTimeMillis());
            if (original == null) target.insert(); else target.save();
            for (Site site : editorSites) {
                VodConfig.setSiteEnabled(site, siteChoices.getOrDefault(site.getKey(), true));
                com.fongmi.android.tv.search.SiteHealth.setPriority(site, sitePriorities.getOrDefault(site.getKey(), false));
            }
            WebDAVSyncManager.get().requestSync();
            manager.checks.put(target.getUrl(), new SourceCheck(false, "地址有效 · " + tested.siteCount + " 个站点"));
            if (original == null) VodConfig.setEnabled(target, true);
            manager.reload(); dialog.dismiss();

        }
    }
    private void showCloud() {
        String profile=LocalProfile.id();WebDAVSyncManager manager=WebDAVSyncManager.get();
        LinearLayout form=dialogForm("当前账号："+LocalProfile.name()+"。同步配置独立，可随时关闭。");
        TextInputEditText url=field(form,"同步目录地址",Setting.getWebDAVUrl(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        TextInputEditText user=field(form,"用户名",Setting.getWebDAVUsername(),InputType.TYPE_CLASS_TEXT);
        TextInputEditText secret=field(form,"应用密码",Setting.getWebDAVPassword(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout actions=row();form.addView(actions);
        LinearLayout left=column(),right=column();actions.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams rightParams=new LinearLayout.LayoutParams(0,-2,1);rightParams.setMarginStart(dp(10));actions.addView(right,rightParams);
        TextView test=dialogAction(left,"测试连接",null),sync=dialogAction(right,"立即同步",null);
        TextView status=dialogStatus(form);status.setText(manager.isConfigured()?manager.getLastStatus():"选填；测试和同步会保存当前配置。");
        TextView disconnect=dialogAction(form,"关闭此账号的同步",null);disconnect.setEnabled(manager.isConfigured());disconnect.setAlpha(manager.isConfigured()?1f:0.45f);
        AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle("WebDAV 同步").setView(dialogScroll(form))
            .setNegativeButton("关闭",null).setPositiveButton("保存配置",null).create();showCentered(dialog);
        java.util.function.BooleanSupplier save=()->{
            if(!profile.equals(LocalProfile.id())){dialog.dismiss();return false;}
            if(value(url).isEmpty()){url.setError("请输入同步目录地址");url.requestFocus();return false;}
            if(value(user).isEmpty()){user.setError("请输入用户名");user.requestFocus();return false;}
            if(secret.getText().toString().isEmpty()){secret.setError("请输入应用密码");secret.requestFocus();return false;}
            String error=manager.configure(value(url),value(user),secret.getText().toString());
            if(error!=null){status.setText(error);return false;}disconnect.setEnabled(true);disconnect.setAlpha(1f);return true;
        };
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(save.getAsBoolean()){RefreshEvent.config();dialog.dismiss();}});
        disconnect.setOnClickListener(v->{
            if(!profile.equals(LocalProfile.id())){dialog.dismiss();return;}
            String error=manager.configure("","","");
            if(error!=null){status.setText(error);return;}RefreshEvent.config();dialog.dismiss();
        });
        java.util.function.Consumer<Boolean> run=testing->{
            if(!save.getAsBoolean())return;
            test.setEnabled(false);sync.setEnabled(false);disconnect.setEnabled(false);
            url.setEnabled(false);user.setEnabled(false);secret.setEnabled(false);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.setCancelable(false);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            status.setText(testing?"正在测试连接…":"正在同步…");
            App.execute(()->{
                String message;
                try{message=testing?manager.testConnectionWithMessage().message:manager.syncNow().message;}
                catch(Exception error){message=testing?"连接失败，请检查配置和网络":"同步失败，请检查配置和网络";}
                String result=message;
                App.post(()->{
                    if(!isAdded()||!dialog.isShowing()||!profile.equals(LocalProfile.id()))return;
                    status.setText(result);test.setEnabled(true);sync.setEnabled(true);disconnect.setEnabled(true);
                    url.setEnabled(true);user.setEnabled(true);secret.setEnabled(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    dialog.setCancelable(true);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                });
            });
        };
        test.setOnClickListener(v->run.accept(true));sync.setOnClickListener(v->run.accept(false));
    }
    private void showCentered(AlertDialog dialog) {
        showCentered(dialog,440);
    }
    private void showCentered(AlertDialog dialog, int maxWidthDp) {
        // Install the content and size the window before its first visible frame.
        dialog.create();
        android.view.Window window=dialog.getWindow();
        if(window!=null){
            window.setGravity(Gravity.CENTER);window.setWindowAnimations(0);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.32f);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if(android.os.Build.VERSION.SDK_INT>=29){window.setStatusBarContrastEnforced(false);window.setNavigationBarContrastEnforced(false);}
            androidx.core.view.WindowInsetsControllerCompat barsController=new androidx.core.view.WindowInsetsControllerCompat(window,window.getDecorView());
            barsController.setAppearanceLightStatusBars(!night());barsController.setAppearanceLightNavigationBars(!night());
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            int available=requireActivity().getWindow().getDecorView().getWidth();
            if(available<=0)available=getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(available-dp(24),dp(maxWidthDp)),ViewGroup.LayoutParams.WRAP_CONTENT);
            View panel = dialog.findViewById(androidx.appcompat.R.id.parentPanel);
            if (panel != null) {
                // A floating window can constrain the form twice when the IME opens.
                // Use one full-window host and apply the keyboard inset only here.
                View titlePanel = dialog.findViewById(androidx.appcompat.R.id.topPanel);
                if (titlePanel != null) titlePanel.setPaddingRelative(titlePanel.getPaddingStart(), titlePanel.getPaddingTop(), titlePanel.getPaddingEnd()+dp(36), titlePanel.getPaddingBottom());
                ((ViewGroup) panel.getParent()).removeView(panel);
                FrameLayout host = new FrameLayout(requireContext()) {
                    @Override protected void onMeasure(int widthSpec, int heightSpec) {
                        int width = Math.min(dp(maxWidthDp), Math.max(1, View.MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight()));
                        if (getChildCount() > 0) getChildAt(0).getLayoutParams().width = width;
                        super.onMeasure(widthSpec,heightSpec);
                    }
                };
                host.setClipChildren(false); host.setClipToPadding(false); host.setBackground(null);
                host.setOnClickListener(v -> {
                    android.widget.Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                    if (cancel == null || cancel.isEnabled()) dialog.cancel();
                });
                dialogHosts.put(dialog, host);
                panel.setBackground(null);
                for (int id : new int[]{androidx.appcompat.R.id.topPanel, androidx.appcompat.R.id.customPanel, androidx.appcompat.R.id.custom, androidx.appcompat.R.id.contentPanel, androidx.appcompat.R.id.buttonPanel}) {
                    View layer = dialog.findViewById(id); if (layer != null) layer.setBackground(null);
                }
                FrameLayout card = new FrameLayout(requireContext());
                card.setBackground(rounded(color(R.color.surface_primary),28)); card.setClipToOutline(true);
                card.setClickable(true); // Consume content touches; only the outside host closes the dialog.
                card.addView(panel, new FrameLayout.LayoutParams(-1,-2));
                card.setMinimumHeight(dp(64));
                View close = new com.fongmi.android.tv.ui.custom.DialogCloseButtonView(requireContext());
                FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.END);
                closeParams.topMargin=dp(12); closeParams.setMarginEnd(dp(14));
                LinearLayout heading = card.findViewWithTag("dialog-heading");
                if (heading != null) heading.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
                else {
                    card.addView(close,closeParams);
                    View title = card.findViewById(androidx.appcompat.R.id.alertTitle);
                    if (title != null) card.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
                        int[] titleAt = new int[2], cardAt = new int[2];
                        title.getLocationInWindow(titleAt); card.getLocationInWindow(cardAt);
                        int top = Math.max(dp(6), titleAt[1] - cardAt[1] + title.getHeight()/2 - dp(22));
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) close.getLayoutParams();
                        if (params.topMargin != top) { params.topMargin = top; close.setLayoutParams(params); }
                    });
                }
                close.setOnClickListener(v -> {
                    android.widget.Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                    if (cancel == null || cancel.isEnabled()) dialog.cancel();
                });
                host.addView(card,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
                window.setContentView(host);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                window.getDecorView().setPadding(0,0,0,0);
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING|android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(host,(view,insets)->{
                    androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    androidx.core.graphics.Insets ime=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime());
                    view.setPadding(bars.left+dp(24),bars.top+dp(16),bars.right+dp(24),Math.max(bars.bottom,ime.bottom)+dp(16));
                    return androidx.core.view.WindowInsetsCompat.CONSUMED;
                });
                host.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                    @Override public void onViewAttachedToWindow(View view){androidx.core.view.ViewCompat.requestApplyInsets(view);}
                    @Override public void onViewDetachedFromWindow(View view){}
                });
            }
        }
        dialogs.add(dialog);
        dialog.setOnDismissListener(d->{dialogs.remove(dialog);dialogHosts.remove(dialog);if(accountDialog==dialog){accountDialog=null;accountAvatar=null;}});
        dialog.show();
        android.widget.Button footerClose = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (footerClose != null && ("关闭".contentEquals(footerClose.getText()) || "取消".contentEquals(footerClose.getText()))) footerClose.setVisibility(View.GONE);
        android.widget.Button acknowledge = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (acknowledge != null && "知道了".contentEquals(acknowledge.getText())) acknowledge.setVisibility(View.GONE);
    }
    private void showAbout() {
        String message=getString(R.string.about_dev_title)+"\n"+getString(R.string.about_dev_body)+"\n\n"+getString(R.string.about_disclaimer_title)+"\n"+getString(R.string.about_disclaimer_body);
        showCentered(new MaterialAlertDialogBuilder(requireContext()).setTitle("关于 XY影视").setMessage(message).setPositiveButton("知道了",null)
            .setNeutralButton("项目主页",(d,w)->startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,Uri.parse("https://github.com/star-xiaoyi/XYBox")))).create());
    }
    private void chooseAccount() {
        List<String> ids=LocalProfile.ids();String[] labels=new String[ids.size()+1];
        for(int i=0;i<ids.size();i++)labels[i]=LocalProfile.name(ids.get(i))+(ids.get(i).equals(LocalProfile.id())?" · 当前":"");labels[ids.size()]="＋ 添加账号";
        showCentered(new MaterialAlertDialogBuilder(requireContext()).setTitle("切换账号").setItems(labels,(d,i)->{
            if(i==ids.size())createAccount();else if(ids.get(i).equals(LocalProfile.id()))showAccount();else switchAccount(ids.get(i));
        }).create());
    }
    private void createAccount() {
        LinearLayout form=dialogForm("为新账号取一个名字。");TextInputEditText name=field(form,"昵称","",InputType.TYPE_CLASS_TEXT);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle("添加本地账号").setView(dialogScroll(form)).setNegativeButton("取消",null).setPositiveButton("创建",null).create();showCentered(dialog);
        dialog.getButton(-1).setOnClickListener(v->{if(value(name).isEmpty()){name.setError("请输入昵称");return;}String id=LocalProfile.create(value(name));dialog.dismiss();switchAccount(id);});
    }
    private void switchAccount(String id) {
        if(!WebDAVSyncManager.get().switchProfile(id)){Notify.show("正在完成同步，请稍后切换");return;}
        EventBus.getDefault().post(new ProfileChangedEvent());RefreshEvent.history();RefreshEvent.keep();refresh();showAccount();
        if(WebDAVSyncManager.get().isConfigured())App.execute(()->WebDAVSyncManager.get().syncOnForeground());
    }
    private void saveAvatar(Uri uri){
        if(uri==null || pickingProfile==null)return;
        String profile=pickingProfile;Context context=requireContext().getApplicationContext();
        App.execute(()->{
            try{
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                try(InputStream input=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(input,null,options);}
                int sample=1;while(Math.max(options.outWidth,options.outHeight)/sample>1024)sample*=2;
                options.inJustDecodeBounds=false;options.inSampleSize=sample;Bitmap bitmap;
                try(InputStream input=context.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(input,null,options);}
                if(bitmap==null)throw new IOException("无法读取图片");
                File folder=new File(context.getFilesDir(),"avatars");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法保存头像");
                File output=new File(folder,profile+"-"+System.currentTimeMillis()+".jpg");
                try(OutputStream stream=new FileOutputStream(output)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,stream);}finally{bitmap.recycle();}
                LocalProfile.setAvatar(profile,output.getAbsolutePath());App.post(()->{if(isAdded())refresh();});
            }catch(Exception error){App.post(()->Notify.show("头像保存失败，请选择另一张图片"));}
        });
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state){super.onSaveInstanceState(state);state.putString("picking_profile",pickingProfile);}
    @Override public void onStart(){super.onStart();EventBus.getDefault().register(this);refresh();}
    @Override public void onStop(){EventBus.getDefault().unregister(this);super.onStop();}
    @Subscribe(threadMode=ThreadMode.MAIN) public void onRefresh(RefreshEvent event){refresh();}
    @Subscribe(threadMode=ThreadMode.MAIN) public void onForegroundSync(ForegroundSyncEvent event){refresh();}
    @Override public void onDestroyView(){for(AlertDialog dialog:new ArrayList<>(dialogs))dialog.dismiss();body=null;super.onDestroyView();}

    private class RecentAdapter extends RecyclerView.Adapter<RecentHolder> {
        final List<History> items=new ArrayList<>();
        @NonNull @Override public RecentHolder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
            LinearLayout card=column();RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(posterWidth,-2);params.setMarginEnd(dp(8));card.setLayoutParams(params);
            FrameLayout cover=new FrameLayout(requireContext());cover.setBackground(surface(10));cover.setClipToOutline(true);card.addView(cover,new LinearLayout.LayoutParams(-1,Math.round(posterWidth*1.2f)));
            ImageView poster=new ImageView(requireContext());poster.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.addView(poster,new FrameLayout.LayoutParams(-1,-1));
            TextView episode=text("",10,false,false);episode.setTextColor(android.graphics.Color.WHITE);episode.setSingleLine(true);episode.setEllipsize(TextUtils.TruncateAt.END);episode.setPadding(dp(6),dp(18),dp(6),dp(6));
            LinearLayout overlay = column();
            // One backdrop spans both the label and the progress track, including 0% and movies without episode text.
            overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xB3000000}));
            overlay.setMinimumHeight(dp(40));
            overlay.setGravity(Gravity.BOTTOM);
            overlay.addView(episode, new LinearLayout.LayoutParams(-1,-2));
            ProgressBar progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(color(R.color.progress_primary)));
            progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            progress.setPadding(0,0,0,0);
            overlay.addView(progress, new LinearLayout.LayoutParams(-1,dp(3)));
            cover.addView(overlay,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
            TextView title=text("",14,true,false);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,dp(7),0,dp(4));card.addView(title);
            TextView time=text("",11,false,true);time.setSingleLine(true);time.setEllipsize(TextUtils.TruncateAt.END);card.addView(time);
            return new RecentHolder(card,cover,poster,title,episode,time,progress);
        }
        @Override public int getItemCount(){return items.size();}
        @Override public void onBindViewHolder(@NonNull RecentHolder holder,int position) {
            History item=items.get(position);holder.itemView.getLayoutParams().width=posterWidth;holder.cover.getLayoutParams().height=Math.round(posterWidth*1.2f);holder.itemView.requestLayout();
            holder.title.setText(item.getVodName());ImgUtil.loadVod(item.getVodName(),item.getVodPic(),holder.poster);
            long watched=Math.max(0,item.getPosition());int percent=item.getDuration()>0?(int)Math.min(100,watched*100/item.getDuration()):-1;
            boolean series = item.getEpisodeCount() > 0 && item.getEpisodeNumber() > 0;
            holder.episode.setVisibility(series ? View.VISIBLE : View.GONE);
            holder.episode.setText(series ? "第"+item.getEpisodeNumber()+"集 / 共"+item.getEpisodeCount()+"集" : "");
            holder.progress.setProgress(Math.max(0,percent));
            holder.time.setText(percent>=0 ? "观看至 "+percent+"%" : "观看至 —%");
            holder.itemView.setOnClickListener(v->VideoActivity.resume(requireActivity(),item));
        }
    }
    private static class RecentHolder extends RecyclerView.ViewHolder {
        final FrameLayout cover;final ImageView poster;final TextView title,episode,time;final ProgressBar progress;
        RecentHolder(View root,FrameLayout c,ImageView p,TextView t,TextView e,TextView tm,ProgressBar bar){super(root);cover=c;poster=p;title=t;episode=e;time=tm;progress=bar;}
    }
}
