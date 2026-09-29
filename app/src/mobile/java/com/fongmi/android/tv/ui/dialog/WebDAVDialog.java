package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.DialogWebdavBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.WebDAVSyncManager;
import com.github.catvod.utils.Logger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class WebDAVDialog {

    // 预设的WebDAV服务提供商
    private static final String[] PROVIDERS = {
        "坚果云",
        "Nextcloud",
        "ownCloud",
        "自定义"
    };
    
    private static final String[] PROVIDER_URLS = {
        "https://dav.jianguoyun.com/dav/XYBox/",
        "",  // Nextcloud（需要用户输入）
        "",  // ownCloud（需要用户输入）
        ""   // 自定义（需要用户输入）
    };

    private final DialogWebdavBinding binding;
    private final Fragment fragment;
    private AlertDialog dialog;
    private WebDAVSyncManager syncManager;
    private int selectedProvider = 0;  // 默认选择坚果云

    public static WebDAVDialog create(Fragment fragment) {
        return new WebDAVDialog(fragment);
    }

    public WebDAVDialog(Fragment fragment) {
        this.fragment = fragment;
        this.binding = DialogWebdavBinding.inflate(LayoutInflater.from(fragment.getContext()));
        this.syncManager = WebDAVSyncManager.get();
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext())
            .setTitle("WebDAV 配置")
            .setView(binding.getRoot())
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", this::onNegative)
            .create();
        dialog.getWindow().setDimAmount(0.32f);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        dialog.getWindow().setWindowAnimations(0);
        dialog.show();
        int width = Math.min((int) (fragment.getResources().getDisplayMetrics().widthPixels * 0.9f), com.fongmi.android.tv.utils.ResUtil.dp2px(440));
        dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> onPositive(dialog, DialogInterface.BUTTON_POSITIVE));
    }

    private void initView() {
        // 加载已保存的配置
        String url = Setting.getWebDAVUrl();
        String username = Setting.getWebDAVUsername();
        String password = Setting.getWebDAVPassword();

        selectedProvider = getProviderIndexByUrl(url);
        binding.providerText.setText(PROVIDERS[selectedProvider]);
        binding.urlInput.setVisibility(View.VISIBLE);
        binding.urlText.setText(url);
        binding.urlText.setHint("完整同步目录地址，例如 https://example.com/dav/me/");
        binding.usernameText.setText(username);
        binding.passwordText.setText(password);
        showStatus(syncManager.getLastStatus(), true);
    }
    
    /**
     * 根据URL判断是哪个服务提供商
     */
    private int getProviderIndexByUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return 0; // 默认坚果云
        }
        if (url.contains("jianguoyun.com")) {
            return 0; // 坚果云
        }
        if (url.contains("nextcloud")) {
            return 1; // Nextcloud
        }
        if (url.contains("owncloud")) {
            return 2; // ownCloud
        }
        return PROVIDERS.length - 1; // 自定义
    }
    
    /**
     * 获取当前选择的服务提供商的URL
     */
    private String getProviderUrl() {
        if (selectedProvider < PROVIDER_URLS.length && !TextUtils.isEmpty(PROVIDER_URLS[selectedProvider])) {
            return PROVIDER_URLS[selectedProvider];
        }
        return "";
    }

    private void initEvent() {
        // 服务提供商选择
        binding.providerText.setOnClickListener(v -> onSelectProvider());

        // 测试连接按钮
        binding.testButton.setOnClickListener(v -> onTestConnection());

        // 立即同步按钮
        binding.syncButton.setOnClickListener(v -> onSyncNow());

        // 密码输入框回车键
        binding.passwordText.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
    }
    
    private void onSelectProvider() {
        // 使用下拉菜单而非弹窗，交互更轻量
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(binding.getRoot().getContext(), binding.providerText);
        for (int i = 0; i < PROVIDERS.length; i++) {
            popup.getMenu().add(0, i, i, PROVIDERS[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            applyProvider(item.getItemId());
            return true;
        });
        popup.show();
    }

    /** 应用服务商选择：更新文案并按需显示/隐藏自定义地址输入框 */
    private void applyProvider(int which) {
        selectedProvider = which;
        binding.providerText.setText(PROVIDERS[which]);
        binding.urlInput.setVisibility(View.VISIBLE);
        if (binding.urlText.getText().toString().trim().isEmpty()
                && com.fongmi.android.tv.utils.LocalProfile.DEFAULT.equals(com.fongmi.android.tv.utils.LocalProfile.id()))
            binding.urlText.setText(getProviderUrl());
    }

    private boolean saveConfiguration(String url, String username, String password) {
        String error = syncManager.configure(url, username, password);
        if (error == null) return true;
        showStatus(error, false);
        return false;
    }

    private void onTestConnection() {
        String url = getServerUrl();
        String username = binding.usernameText.getText().toString().trim();
        String password = binding.passwordText.getText().toString().trim();

        if (TextUtils.isEmpty(url)) {
            showStatus("请选择服务提供商或输入服务器地址", false);
            return;
        }
        if (TextUtils.isEmpty(username)) {
            showStatus("请输入用户名", false);
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showStatus("请输入密码", false);
            return;
        }

        // 临时保存配置用于测试
        if (!saveConfiguration(url, username, password)) return;
        
        // 重新加载配置
        syncManager.reloadConfig();

        showStatus("正在测试连接...", true);
        binding.testButton.setEnabled(false);

        // 在后台线程测试连接
        App.execute(() -> {
            WebDAVSyncManager.TestResult result = syncManager.testConnectionWithMessage();
            App.post(() -> {
                // 检查对话框是否还存在
                if (binding == null || dialog == null || !dialog.isShowing()) {
                    return;
                }
                binding.testButton.setEnabled(true);
                showStatus(result.message, result.success);
            });
        });
    }

    private void onSyncNow() {
        // 先临时保存当前配置用于测试同步
        String url = getServerUrl();
        String username = binding.usernameText.getText().toString().trim();
        String password = binding.passwordText.getText().toString().trim();
        
        // 验证输入
        if (TextUtils.isEmpty(url)) {
            showStatus("请选择服务提供商或输入服务器地址", false);
            return;
        }
        if (TextUtils.isEmpty(username)) {
            showStatus("请输入用户名", false);
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showStatus("请输入密码", false);
            return;
        }
        
        // 临时保存配置用于同步
        if (!saveConfiguration(url, username, password)) return;
        syncManager.reloadConfig();
        
        if (!syncManager.isConfigured()) {
            showStatus("配置无效，无法同步", false);
            return;
        }

        showStatus("正在同步...", true);
        binding.syncButton.setEnabled(false);

        // 在后台线程执行同步
        App.execute(() -> {
            try {
                WebDAVSyncManager.SyncResult result = syncManager.syncNow();
                
                App.post(() -> {
                    // 检查对话框是否还存在
                    if (binding == null || dialog == null || !dialog.isShowing()) {
                        return;
                    }
                    binding.syncButton.setEnabled(true);
                    // 结果直接显示在按钮下方的状态栏里，不再额外弹 Toast
                    showStatus(result.message, result.success);
                });
            } catch (Exception e) {
                App.post(() -> {
                    // 检查对话框是否还存在
                    if (binding == null || dialog == null || !dialog.isShowing()) {
                        return;
                    }
                    binding.syncButton.setEnabled(true);
                    showStatus("同步失败：" + e.getMessage(), false);
                    Logger.e("WebDAV: 同步失败: " + e.getMessage());
                });
            }
        });
    }

    private void showStatus(String message, boolean isSuccess) {
        // 检查对话框是否还存在
        if (binding == null || dialog == null || !dialog.isShowing()) {
            return;
        }
        binding.statusText.setText(message);
        binding.statusText.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
        // 成功用次级文字色，失败用主题错误色，深浅色模式下都清晰可见
        binding.statusText.setTextColor(resolveThemeColor(isSuccess
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorError));
    }

    private int resolveThemeColor(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        binding.getRoot().getContext().getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    /**
     * 获取服务器URL（根据选择的服务提供商）
     */
    private String getServerUrl() {
        return binding.urlText.getText().toString().trim();
    }

    private void onPositive(DialogInterface dialog, int which) {
        String url = getServerUrl();
        String username = binding.usernameText.getText().toString().trim();
        String password = binding.passwordText.getText().toString().trim();

        // 验证输入
        if (TextUtils.isEmpty(url)) {
            Notify.tip("请选择服务提供商或输入服务器地址");
            return;
        }
        if (TextUtils.isEmpty(username)) {
            Notify.tip("请输入用户名");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            Notify.tip("请输入密码");
            return;
        }

        // 保存配置（配置了 WebDAV 即自动同步，不需要额外开关）
        if (!saveConfiguration(url, username, password)) return;

        // 重新加载配置
        syncManager.reloadConfig();

        // 配置保存后，立即执行一次同步（下载远程数据）
        // 这样新设备配置后就能立即看到其他设备的历史记录
        if (syncManager.isConfigured()) {
            App.execute(() -> {
                try {
                    Notify.tip(syncManager.syncNow().message);
                } catch (Exception e) {
                    Notify.tip("同步失败，请检查网络连接");
                }
            });
        } else {
            Notify.tip("WebDAV配置已保存");
        }
        
        dialog.dismiss();
        
        // 通知设置界面更新状态（通过RefreshEvent）
        // 使用App.post确保对话框关闭后再发送事件，让状态能及时更新
        App.post(() -> RefreshEvent.config());
    }

    private void onNegative(DialogInterface dialog, int which) {
        dialog.dismiss();
    }

    /**
     * 重新加载配置（用于外部调用）
     */
    public void reloadConfig() {
        syncManager.reloadConfig();
    }
}

