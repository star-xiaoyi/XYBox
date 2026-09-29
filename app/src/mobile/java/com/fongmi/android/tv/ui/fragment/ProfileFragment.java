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
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.event.RefreshEvent;
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

/** Profile overview with separate identity, source and sync editors. */
public class ProfileFragment extends com.fongmi.android.tv.ui.base.BaseFragment {
    private LinearLayout body;
    private ImageView avatar, accountAvatar;
    private TextView nickname, signature, empty, keepCount, historyCount, downloadCount;
    private RecyclerView historyList;
    private RecentAdapter adapter;
    private AlertDialog accountDialog;
    private final Set<AlertDialog> dialogs = new HashSet<>();
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
        LinearLayout tile = row(); tile.setPadding(dp(4), dp(4), dp(4), dp(4));
        ImageView image = new ImageView(requireContext()); image.setImageResource(icon);
        image.setPadding(dp(8),dp(8),dp(8),dp(8)); image.setBackground(rounded(night() ? (halo & 0x00FFFFFF) | 0x30000000 : halo, 22));
        tile.addView(image, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout labels = column(); LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1); labelsParams.setMarginStart(dp(6)); tile.addView(labels, labelsParams);
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
        empty.setVisibility(histories.isEmpty() ? View.VISIBLE : View.GONE); historyList.setVisibility(histories.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private LinearLayout dialogForm(String description) {
        LinearLayout form = column();
        form.setPadding(dp(24),dp(4),dp(24),dp(12));
        form.setFocusableInTouchMode(true);
        TextView caption = text(description,12,false,true);
        caption.setPadding(0,0,0,dp(8)); form.addView(caption);
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
        LinearLayout form=dialogForm("接口供所有本地账号共用。");
        form.setPadding(dp(20),0,dp(20),dp(8));
        Config current=VodConfig.get().getConfig();
        TextInputEditText sourceName=field(form,"接口名称 · 选填",current==null?"":current.getName(),InputType.TYPE_CLASS_TEXT);
        TextInputEditText sourceUrl=field(form,"接口地址",current==null?"":current.getUrl(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        TextView choose=dialogAction(form,"选择已保存的接口  ›",null);
        choose.setOnClickListener(v->{
            List<Config> configs=Config.getAll(0);
            if(configs.isEmpty()){sourceUrl.requestFocus();Notify.show("还没有保存的接口，请输入接口地址");return;}
            androidx.appcompat.widget.PopupMenu menu=new androidx.appcompat.widget.PopupMenu(requireContext(),choose);
            for(int i=0;i<configs.size();i++)menu.getMenu().add(0,i,i,configs.get(i).getDesc());
            menu.setOnMenuItemClickListener(item->{Config config=configs.get(item.getItemId());sourceName.setText(config.getName());sourceUrl.setText(config.getUrl());return true;});menu.show();
        });
        TextView status=dialogStatus(form);
        status.setMinLines(1);status.setVisibility(View.GONE);
        AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle("点播源").setView(dialogScroll(form))
            .setNegativeButton("取消",null).setPositiveButton("保存并使用",null).create();showCentered(dialog,360);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(value(sourceUrl).isEmpty()){sourceUrl.setError("请输入或选择接口地址");sourceUrl.requestFocus();return;}
            if(current!=null&&value(sourceUrl).equals(current.getUrl())){current.name(value(sourceName)).update();RefreshEvent.config();dialog.dismiss();return;}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);choose.setEnabled(false);sourceName.setEnabled(false);sourceUrl.setEnabled(false);
            dialog.setCancelable(false);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);status.setVisibility(View.VISIBLE);status.setText("正在加载接口…");
            Config selected=Config.find(value(sourceUrl),value(sourceName),0);
            VodConfig.load(selected,new Callback(){
                @Override public void success(){App.post(()->{RefreshEvent.video();RefreshEvent.config();if(dialog.isShowing())dialog.dismiss();});}
                @Override public void error(String message){App.post(()->{
                    if(!isAdded()||!dialog.isShowing())return;
                    status.setText("加载失败："+message);dialog.setCancelable(true);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    choose.setEnabled(true);sourceName.setEnabled(true);sourceUrl.setEnabled(true);
                });}
            });
        });
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
            ViewGroup custom = dialog.findViewById(androidx.appcompat.R.id.custom);
            if (custom != null && custom.getChildCount() > 0) {
                // A floating window can constrain the form twice when the IME opens.
                // Use one full-window host and apply the keyboard inset only here.
                View panel = dialog.findViewById(androidx.appcompat.R.id.parentPanel);
                ((ViewGroup) panel.getParent()).removeView(panel);
                FrameLayout host = new FrameLayout(requireContext()) {
                    @Override protected void onMeasure(int widthSpec, int heightSpec) {
                        int width = Math.min(dp(maxWidthDp), Math.max(1, View.MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight()));
                        panel.getLayoutParams().width = width;
                        super.onMeasure(widthSpec,heightSpec);
                    }
                };
                panel.setBackground(rounded(color(R.color.surface_primary),28));panel.setClipToOutline(true);
                host.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
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
        dialog.setOnDismissListener(d->{dialogs.remove(dialog);if(accountDialog==dialog){accountDialog=null;accountAvatar=null;}});
        dialog.show();
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
    @Override public void onDestroyView(){for(AlertDialog dialog:new ArrayList<>(dialogs))dialog.dismiss();body=null;super.onDestroyView();}

    private class RecentAdapter extends RecyclerView.Adapter<RecentHolder> {
        final List<History> items=new ArrayList<>();
        @NonNull @Override public RecentHolder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
            LinearLayout card=column();RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(posterWidth,-2);params.setMarginEnd(dp(8));card.setLayoutParams(params);
            FrameLayout cover=new FrameLayout(requireContext());cover.setBackground(surface(10));cover.setClipToOutline(true);card.addView(cover,new LinearLayout.LayoutParams(-1,Math.round(posterWidth*1.2f)));
            ImageView poster=new ImageView(requireContext());poster.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.addView(poster,new FrameLayout.LayoutParams(-1,-1));
            TextView episode=text("",10,false,false);episode.setTextColor(android.graphics.Color.WHITE);episode.setSingleLine(true);episode.setEllipsize(TextUtils.TruncateAt.END);episode.setPadding(dp(6),dp(18),dp(6),dp(6));
            episode.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00000000,0xB3000000}));
            LinearLayout overlay = column();
            overlay.addView(episode, new LinearLayout.LayoutParams(-1,-2));
            ProgressBar progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(color(R.color.accent_blue)));
            progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x66FFFFFF));
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
            holder.itemView.setOnClickListener(v->VideoActivity.start(requireActivity(),item.getSiteKey(),item.getVodId(),item.getVodName(),item.getVodPic()));
        }
    }
    private static class RecentHolder extends RecyclerView.ViewHolder {
        final FrameLayout cover;final ImageView poster;final TextView title,episode,time;final ProgressBar progress;
        RecentHolder(View root,FrameLayout c,ImageView p,TextView t,TextView e,TextView tm,ProgressBar bar){super(root);cover=c;poster=p;title=t;episode=e;time=tm;progress=bar;}
    }
}
