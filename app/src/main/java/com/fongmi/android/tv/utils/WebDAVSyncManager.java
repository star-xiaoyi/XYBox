package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.github.catvod.utils.Logger;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import com.google.gson.reflect.TypeToken;
import com.fongmi.android.tv.Setting;

/**
 * WebDAV同步管理器
 * 用于同步观看记录和设置到WebDAV服务器
 */
public class WebDAVSyncManager {
    
    private static final String HISTORY_FILE = "xmbox_history.json";
    private static final String SETTINGS_FILE = "xmbox_settings.json";
    private static final String BACKUP_FILE = "xmbox_backup.json";
    
    // 同步模式：ACCOUNT（账号模式）或 CODE（同步码模式）
    public enum SyncMode {
        ACCOUNT,  // 使用WebDAV账号
        CODE      // 使用同步码（无需账号）
    }
    
    private static WebDAVSyncManager instance;
    private Sardine sardine;
    private String baseUrl;
    private String username;
    private String password;
    private String syncCode;  // 同步码
    private SyncMode syncMode = SyncMode.ACCOUNT;  // 默认使用账号模式
    private volatile boolean isSyncing = false;  // 同步锁，防止重复同步
    
    public static WebDAVSyncManager get() {
        if (instance == null) {
            instance = new WebDAVSyncManager();
        }
        return instance;
    }
    
    private WebDAVSyncManager() {
        loadConfig();
    }
    
    /**
     * 加载WebDAV配置
     */
    private void loadConfig() {
        // 检查同步模式
        String modeStr = Setting.getWebDAVSyncMode();
        if ("CODE".equals(modeStr)) {
            syncMode = SyncMode.CODE;
            syncCode = Setting.getWebDAVSyncCode();
            // 同步码模式：使用公开的WebDAV服务器（如jsDelivr CDN的GitHub仓库）
            // 或者使用其他公开存储服务
            baseUrl = getPublicStorageUrl();
            username = null;
            password = null;
        } else {
            syncMode = SyncMode.ACCOUNT;
            baseUrl = Setting.getWebDAVUrl();
            username = Setting.getWebDAVUsername();
            password = Setting.getWebDAVPassword();
        }
        
        if (syncMode == SyncMode.ACCOUNT) {
            // 账号模式：需要账号密码
            if (!TextUtils.isEmpty(baseUrl) && !TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
                try {
                    sardine = new OkHttpSardine();
                    sardine.setCredentials(username, password);
                    Logger.d("WebDAV: 账号模式配置已加载");
                } catch (Exception e) {
                    Logger.e("WebDAV: 初始化失败: " + e.getMessage());
                    sardine = null;
                }
            } else {
                sardine = null;
            }
        } else {
            // 同步码模式：使用公开存储，无需认证
            if (!TextUtils.isEmpty(syncCode) && !TextUtils.isEmpty(baseUrl)) {
                try {
                    sardine = new OkHttpSardine();
                    // 公开存储不需要认证
                    Logger.d("WebDAV: 同步码模式配置已加载，同步码: " + syncCode);
                } catch (Exception e) {
                    Logger.e("WebDAV: 初始化失败: " + e.getMessage());
                    sardine = null;
                }
            } else {
                sardine = null;
            }
        }
    }
    
    /**
     * 获取公开存储URL（同步码模式使用）
     * 方案：使用GitHub Gist作为公开存储
     * 用户需要：
     * 1. 创建一个GitHub Gist（公开）
     * 2. 获取Gist的raw URL
     * 3. 输入同步码
     * 
     * 文件路径格式：{gist_raw_url}/{syncCode}/xmbox_history.json
     */
    private String getPublicStorageUrl() {
        // 获取用户配置的GitHub Gist raw URL
        // 例如：https://gist.githubusercontent.com/username/gist_id/raw/
        String gistBaseUrl = Setting.getWebDAVPublicUrl();
        
        if (TextUtils.isEmpty(gistBaseUrl)) {
            // 如果没有配置，返回null（需要用户配置）
            return null;
        }
        
        // 将同步码添加到路径中，作为子目录
        // 例如：https://gist.githubusercontent.com/username/gist_id/raw/ABC123XYZ/
        if (!TextUtils.isEmpty(syncCode)) {
            String url = gistBaseUrl.endsWith("/") ? gistBaseUrl : gistBaseUrl + "/";
            return url + syncCode + "/";
        }
        
        return gistBaseUrl;
    }
    
    /**
     * 检查WebDAV是否已配置
     */
    public boolean isConfigured() {
        if (syncMode == SyncMode.CODE) {
            // 同步码模式：需要同步码和公开存储URL
            return sardine != null && !TextUtils.isEmpty(baseUrl) && !TextUtils.isEmpty(syncCode);
        } else {
            // 账号模式：需要账号密码和URL
            return sardine != null && !TextUtils.isEmpty(baseUrl) && !TextUtils.isEmpty(username) && !TextUtils.isEmpty(password);
        }
    }
    
    /**
     * 生成同步码
     * @return 8位随机同步码（字母+数字）
     */
    public static String generateSyncCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random random = new java.util.Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
    
    /**
     * 测试WebDAV连接
     * @return 测试结果，包含成功状态和错误信息
     */
    public TestResult testConnectionWithMessage() {
        if (!isConfigured()) {
            return new TestResult(false, "WebDAV未配置，请检查URL、用户名和密码");
        }
        
        try {
            // 确保baseUrl以/结尾
            String testUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            Logger.d("WebDAV: 测试连接URL: " + testUrl);
            Logger.d("WebDAV: 用户名: " + (username != null ? username : "null"));
            
            // 尝试列出目录
            sardine.list(testUrl);
            Logger.d("WebDAV: 连接测试成功，可以访问目录");
            return new TestResult(true, "连接成功！");
        } catch (java.io.IOException e) {
            String errorMsg = e.getMessage();
            Logger.e("WebDAV: 连接测试失败: " + errorMsg);
            Logger.e("WebDAV: 异常类型: " + e.getClass().getName());
            e.printStackTrace();
            
            // 根据错误类型提供更详细的提示
            if (errorMsg != null) {
                if (errorMsg.contains("401") || errorMsg.contains("Unauthorized")) {
                    return new TestResult(false, "认证失败：用户名或密码错误，请检查账号密码。\n提示：坚果云需要使用应用密码，不是登录密码");
                } else if (errorMsg.contains("403") || errorMsg.contains("Forbidden")) {
                    return new TestResult(false, "访问被拒绝：账号可能没有WebDAV权限");
                } else if (errorMsg.contains("404") || errorMsg.contains("Not Found")) {
                    return new TestResult(false, "URL不存在：请检查WebDAV服务器地址是否正确");
                } else if (errorMsg.contains("SSL") || errorMsg.contains("Certificate")) {
                    return new TestResult(false, "SSL证书错误：请检查服务器证书是否有效");
                } else if (errorMsg.contains("timeout") || errorMsg.contains("Timeout")) {
                    return new TestResult(false, "连接超时：请检查网络连接或服务器地址");
                } else if (errorMsg.contains("UnknownHost") || errorMsg.contains("unreachable")) {
                    return new TestResult(false, "无法连接到服务器：请检查网络连接和服务器地址");
                }
            }
            return new TestResult(false, "连接失败：" + (errorMsg != null ? errorMsg : "未知错误"));
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            Logger.e("WebDAV: 连接测试失败: " + errorMsg);
            Logger.e("WebDAV: 异常类型: " + e.getClass().getName());
            e.printStackTrace();
            return new TestResult(false, "连接失败：" + (errorMsg != null ? errorMsg : e.getClass().getSimpleName()));
        }
    }
    
    /**
     * 测试WebDAV连接（兼容旧接口）
     */
    public boolean testConnection() {
        return testConnectionWithMessage().success;
    }
    
    /**
     * 测试结果类
     */
    public static class TestResult {
        public final boolean success;
        public final String message;
        
        public TestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
    
    /**
     * 确保目录存在
     */
    private void ensureDirectory(String path) throws Exception {
        try {
            if (!sardine.exists(path)) {
                sardine.createDirectory(path);
                Logger.d("WebDAV: 创建目录: " + path);
            }
        } catch (Exception e) {
            Logger.e("WebDAV: 创建目录失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 获取文件完整URL
     */
    private String getFileUrl(String filename) {
        if (syncMode == SyncMode.CODE) {
            // 同步码模式：使用GitHub Gist raw URL
            // 格式：https://gist.githubusercontent.com/username/gist_id/raw/{syncCode}/{filename}
            String url = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            return url + filename;
        } else {
            // 账号模式：使用WebDAV URL
            // 对于坚果云：https://dav.jianguoyun.com/dav/xmbox_history.json
            // 确保 baseUrl 以 / 结尾
            String url = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            return url + filename;
        }
    }
    
    /**
     * 上传观看记录
     */
    public boolean uploadHistory() {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法上传观看记录");
            return false;
        }
        
        try {
            // 获取所有观看记录 - 使用findAllRecent(0)来获取所有记录（包括旧记录）
            Logger.d("WebDAV: 开始查询数据库中的观看记录...");
            List<History> historyList = AppDatabase.get().getHistoryDao().findAllRecent(0);
            Logger.d("WebDAV: 数据库查询完成，结果: " + (historyList == null ? "null" : historyList.size() + " 条"));
            
            if (historyList == null) {
                Logger.w("WebDAV: 查询结果为null，创建空列表");
                historyList = new java.util.ArrayList<>();
            }
            
            // 修复数据中可能的编码问题（重点修复key中的站点名称部分）
            Logger.d("WebDAV: 开始修复上传数据的编码问题...");
            for (History h : historyList) {
                String originalKey = h.getKey();
                
                // key格式: 站点key$视频ID$cid，需要单独修复站点key部分
                String fixedKey = fixHistoryKey(originalKey);
                if (!originalKey.equals(fixedKey)) {
                    Logger.d("WebDAV: 修复key编码: '" + originalKey + "' -> '" + fixedKey + "'");
                    h.setKey(fixedKey);
                }
                
                String originalName = h.getVodName();
                String fixedName = fixEncodingIfNeeded(originalName);
                if (!originalName.equals(fixedName)) {
                    Logger.d("WebDAV: 修复vodName编码: '" + originalName + "' -> '" + fixedName + "'");
                    h.setVodName(fixedName);
                }
            }
            
            Logger.d("WebDAV: 准备上传观看记录，共 " + historyList.size() + " 条");
            
            // 记录前3条数据的详细信息
            for (int i = 0; i < Math.min(3, historyList.size()); i++) {
                History h = historyList.get(i);
                Logger.d("WebDAV: 上传记录[" + i + "] key=" + h.getKey() + ", vodName=" + h.getVodName());
                // 检查key中的每个字符
                String key = h.getKey();
                StringBuilder hexDump = new StringBuilder();
                for (int j = 0; j < Math.min(20, key.length()); j++) {
                    hexDump.append(String.format("%04x ", (int)key.charAt(j)));
                }
                Logger.d("WebDAV: key前20字符的Unicode: " + hexDump.toString());
            }
            
            String json = App.gson().toJson(historyList);
            if (TextUtils.isEmpty(json)) {
                Logger.w("WebDAV: JSON数据为空");
                json = "[]"; // 确保至少有一个有效的JSON数组
            }
            
            // 记录JSON的前500个字符
            Logger.d("WebDAV: JSON前500字符: " + json.substring(0, Math.min(500, json.length())));
            
            // 确保目录存在（如果baseUrl包含子目录）
            if (syncMode == SyncMode.ACCOUNT && !TextUtils.isEmpty(baseUrl)) {
                try {
                    String dirUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                    ensureDirectory(dirUrl);
                } catch (Exception e) {
                    Logger.w("WebDAV: 创建目录失败，尝试继续上传: " + e.getMessage());
                    // 继续尝试上传，某些WebDAV服务可能不需要预先创建目录
                }
            }
            
            // 上传文件
            String fileUrl = getFileUrl(HISTORY_FILE);
            Logger.d("WebDAV: 上传文件URL: " + fileUrl);
            Logger.d("WebDAV: 上传数据大小: " + json.length() + " 字节");
            
            byte[] data = json.getBytes("UTF-8");
            
            // 对于坚果云等WebDAV服务，直接上传文件即可（会自动创建文件）
            // 如果文件已存在，会被覆盖
            sardine.put(fileUrl, data);
            
            // 验证上传是否成功：检查文件是否存在
            if (sardine.exists(fileUrl)) {
                Logger.d("WebDAV: 观看记录上传成功，共 " + historyList.size() + " 条，文件已确认存在");
                return true;
            } else {
                Logger.e("WebDAV: 上传后文件不存在，可能上传失败");
                return false;
            }
        } catch (Exception e) {
            Logger.e("WebDAV: 观看记录上传失败: " + e.getMessage());
            Logger.e("WebDAV: 异常类型: " + e.getClass().getName());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 下载观看记录
     */
    public boolean downloadHistory() {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法下载观看记录");
            Logger.e("WebDAV: baseUrl=" + baseUrl + ", username=" + username);
            return false;
        }
        
        try {
            String fileUrl = getFileUrl(HISTORY_FILE);
            Logger.d("WebDAV: 检查文件是否存在: " + fileUrl);
            
            // 检查文件是否存在
            if (!sardine.exists(fileUrl)) {
                Logger.w("WebDAV: 观看记录文件不存在，跳过下载");
                return false;
            }
            
            Logger.d("WebDAV: 文件存在，开始下载");
            
            // 下载文件（使用循环读取，避免available()不准确的问题）
            InputStream is = sardine.get(fileUrl);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            is.close();
            byte[] data = baos.toByteArray();
            baos.close();
            
            String json = new String(data, "UTF-8");
            if (TextUtils.isEmpty(json)) {
                Logger.d("WebDAV: 观看记录文件为空");
                return true; // 文件存在但为空，也算同步成功
            }
            
            Type listType = new TypeToken<List<History>>(){}.getType();
            List<History> remoteHistoryList = App.gson().fromJson(json, listType);
            
            // 验证数据
            if (remoteHistoryList == null) {
                Logger.e("WebDAV: JSON解析失败，返回null");
                return false;
            }
            
            // 智能合并：比较本地和远程记录，保留较新的
            List<History> localHistoryList = AppDatabase.get().getHistoryDao().findAllRecent(0);
            Logger.d("WebDAV: 本地记录数: " + localHistoryList.size());
            Logger.d("WebDAV: 远程记录数: " + remoteHistoryList.size());
            
            // 修复远程记录的编码问题和时间戳
            Logger.d("WebDAV: 开始修复远程记录编码和时间戳...");
            long currentTime = System.currentTimeMillis();
            long historyTimeLimit = currentTime - com.fongmi.android.tv.Constant.HISTORY_TIME; // 60天前
            
            for (History remote : remoteHistoryList) {
                if (remote != null) {
                    String originalKey = remote.getKey();
                    // 修复key中的站点名称部分
                    String fixedKey = fixHistoryKey(originalKey);
                    if (!originalKey.equals(fixedKey)) {
                        Logger.d("WebDAV: 修复远程key: '" + originalKey + "' -> '" + fixedKey + "'");
                        remote.setKey(fixedKey);
                    }
                    
                    String originalName = remote.getVodName();
                    String fixedName = fixEncodingIfNeeded(originalName);
                    if (!originalName.equals(fixedName)) {
                        Logger.d("WebDAV: 修复远程vodName: '" + originalName + "' -> '" + fixedName + "'");
                        remote.setVodName(fixedName);
                    }
                    
                    // 关键修复：确保createTime在60天内，否则会被过滤掉！
                    long remoteCreateTime = remote.getCreateTime();
                    if (remoteCreateTime < historyTimeLimit) {
                        Logger.d("WebDAV: 修复过期时间戳: " + remote.getVodName() + 
                                 " createTime=" + remoteCreateTime + " -> " + currentTime + 
                                 " (已过期 " + ((currentTime - remoteCreateTime) / (24*60*60*1000)) + " 天)");
                        remote.setCreateTime(currentTime);
                    }
                    
                    // 记录前3条远程数据的详细信息
                    if (remoteHistoryList.indexOf(remote) < 3) {
                        Logger.d("WebDAV: 远程记录[" + remoteHistoryList.indexOf(remote) + "]: " + 
                                 remote.getVodName() + " (key=" + remote.getKey() + 
                                 ", cid=" + remote.getCid() + 
                                 ", createTime=" + remote.getCreateTime() + ")");
                    }
                }
            }
            
            // 修复本地记录的编码问题（重要！）
            Logger.d("WebDAV: 开始修复本地记录编码...");
            for (History local : localHistoryList) {
                if (local != null) {
                    String originalKey = local.getKey();
                    // 修复key中的站点名称部分
                    String fixedKey = fixHistoryKey(originalKey);
                    if (!originalKey.equals(fixedKey)) {
                        Logger.d("WebDAV: 修复本地key: '" + originalKey + "' -> '" + fixedKey + "'");
                        local.setKey(fixedKey);
                    }
                    
                    // 记录前3条本地数据的详细信息
                    if (localHistoryList.indexOf(local) < 3) {
                        Logger.d("WebDAV: 本地记录[" + localHistoryList.indexOf(local) + "]: " + 
                                 local.getVodName() + " (key=" + local.getKey() + 
                                 ", cid=" + local.getCid() + 
                                 ", createTime=" + local.getCreateTime() + ")");
                    }
                }
            }
            
            // 创建本地记录的映射（key -> History）
            java.util.Map<String, History> localMap = new java.util.HashMap<>();
            for (History local : localHistoryList) {
                if (local != null && local.getKey() != null) {
                    localMap.put(local.getKey(), local);
                }
            }
            Logger.d("WebDAV: 本地记录映射大小: " + localMap.size());
            
            // 合并远程记录
            List<History> toInsert = new java.util.ArrayList<>();
            List<History> toUpdate = new java.util.ArrayList<>();
            
            Logger.d("WebDAV: 开始合并 " + remoteHistoryList.size() + " 条远程记录...");
            
            for (History remote : remoteHistoryList) {
                // 验证远程记录
                if (remote == null || TextUtils.isEmpty(remote.getKey())) {
                    Logger.w("WebDAV: 跳过无效的远程记录（key为空）");
                    continue;
                }
                
                History local = localMap.get(remote.getKey());
                
                if (local == null) {
                    // 本地没有，直接添加
                    Logger.d("WebDAV: 发现新记录: " + remote.getVodName() + " (key=" + remote.getKey() + ")");
                    toInsert.add(remote);
                } else {
                    Logger.d("WebDAV: 本地已有记录: " + remote.getVodName() + ", 比较时间 remote=" + remote.getCreateTime() + " local=" + local.getCreateTime());
                    
                    // 改进的合并策略：优先保留较新的记录，但也要比较播放进度
                    long remotePos = remote.getPosition();
                    long localPos = local.getPosition();
                    long remoteTime = remote.getCreateTime();
                    long localTime = local.getCreateTime();
                    
                    boolean shouldUpdate = false;
                    String reason = "";
                    
                    // 策略1：如果远程时间更新，直接更新
                    if (remoteTime > localTime) {
                        shouldUpdate = true;
                        reason = "远程时间更新 (" + remoteTime + " > " + localTime + ")";
                    }
                    // 策略2：如果时间相同或相近（误差1秒内），比较播放进度
                    else if (Math.abs(remoteTime - localTime) <= 1000) {
                        if (remotePos >= 0 && localPos >= 0) {
                            if (remotePos > localPos) {
                                shouldUpdate = true;
                                reason = "播放进度更新 (" + remotePos + " > " + localPos + ")";
                            } else {
                                reason = "本地进度更新或相同";
                            }
                        } else if (remotePos >= 0 && localPos < 0) {
                            shouldUpdate = true;
                            reason = "远程有有效进度，本地无效";
                        } else {
                            reason = "保留本地";
                        }
                    }
                    // 策略3：即使本地时间更新，如果远程有更大的播放进度，也更新
                    else if (remoteTime < localTime) {
                        if (remotePos >= 0 && localPos >= 0 && remotePos > localPos + 60000) {
                            // 远程进度领先本地超过1分钟，可能是用户在另一台设备继续观看
                            shouldUpdate = true;
                            reason = "虽然本地时间更新，但远程进度显著领先 (" + remotePos + " > " + localPos + ")";
                        } else {
                            reason = "本地时间更新 (" + localTime + " > " + remoteTime + ")，保留本地";
                        }
                    }
                    
                    if (shouldUpdate) {
                        Logger.d("WebDAV: → 将更新本地 - " + reason);
                        toUpdate.add(remote);
                    } else {
                        Logger.d("WebDAV: → 保留本地 - " + reason);
                    }
                }
            }
            
            Logger.d("WebDAV: 合并完成，待插入 " + toInsert.size() + " 条，待更新 " + toUpdate.size() + " 条");
            
            // 执行插入和更新
            if (!toInsert.isEmpty()) {
                Logger.d("WebDAV: 开始插入 " + toInsert.size() + " 条新记录...");
                AppDatabase.get().getHistoryDao().insert(toInsert);
                Logger.d("WebDAV: 新增 " + toInsert.size() + " 条观看记录");
                for (History h : toInsert) {
                    Logger.d("WebDAV: ✓ 新增 - " + h.getVodName() + " (cid=" + h.getCid() + ", key=" + h.getKey() + ")");
                }
            } else {
                Logger.d("WebDAV: 没有需要插入的新记录");
            }
            
            if (!toUpdate.isEmpty()) {
                Logger.d("WebDAV: 开始更新 " + toUpdate.size() + " 条记录...");
                AppDatabase.get().getHistoryDao().update(toUpdate);
                Logger.d("WebDAV: 更新 " + toUpdate.size() + " 条观看记录");
                for (History h : toUpdate) {
                    Logger.d("WebDAV: ✓ 更新 - " + h.getVodName() + " (cid=" + h.getCid() + ")");
                }
            } else {
                Logger.d("WebDAV: 没有需要更新的记录");
            }
            
            Logger.d("WebDAV: 观看记录合并完成，远程 " + remoteHistoryList.size() + " 条，本地 " + localHistoryList.size() + " 条");
            
            // 验证数据库中的记录总数
            List<History> allInDb = AppDatabase.get().getHistoryDao().findAllRecent(0);
            Logger.d("WebDAV: 数据库中总共有 " + allInDb.size() + " 条观看记录");
            
            // 输出数据库中前5条记录的详细信息
            Logger.d("WebDAV: === 数据库中的记录（前5条）===");
            for (int i = 0; i < Math.min(5, allInDb.size()); i++) {
                History h = allInDb.get(i);
                Logger.d("WebDAV: [" + i + "] " + h.getVodName() + 
                         " (key=" + h.getKey() + 
                         ", cid=" + h.getCid() + 
                         ", createTime=" + h.getCreateTime() + ")");
            }
            Logger.d("WebDAV: =========================");
            
            // 强制触发UI刷新（即使没有新增或更新，也刷新一次以确保显示）
            Logger.d("WebDAV: 触发UI刷新事件");
            App.post(() -> {
                RefreshEvent.history();
                Logger.d("WebDAV: UI刷新事件已发送到主线程");
            });
            
            return true; // 即使远程为空，也算同步成功
        } catch (Exception e) {
            Logger.e("WebDAV: 观看记录下载失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 上传设置
     */
    public boolean uploadSettings() {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法上传设置");
            return false;
        }
        
        try {
            // 获取所有设置
            Map<String, ?> allPrefs = Prefers.getPrefers().getAll();
            String json = App.gson().toJson(allPrefs);
            
            // 确保目录存在（如果baseUrl包含子目录）
            if (syncMode == SyncMode.ACCOUNT && !TextUtils.isEmpty(baseUrl)) {
                try {
                    String dirUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                    ensureDirectory(dirUrl);
                } catch (Exception e) {
                    Logger.w("WebDAV: 创建目录失败，尝试继续上传: " + e.getMessage());
                }
            }
            
            // 上传文件
            String fileUrl = getFileUrl(SETTINGS_FILE);
            byte[] data = json.getBytes("UTF-8");
            sardine.put(fileUrl, data);
            
            Logger.d("WebDAV: 设置上传成功");
            return true;
        } catch (Exception e) {
            Logger.e("WebDAV: 设置上传失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 下载设置
     */
    public boolean downloadSettings() {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法下载设置");
            return false;
        }
        
        try {
            String fileUrl = getFileUrl(SETTINGS_FILE);
            
            // 检查文件是否存在
            if (!sardine.exists(fileUrl)) {
                Logger.d("WebDAV: 设置文件不存在，跳过下载");
                return false;
            }
            
            // 下载文件
            InputStream is = sardine.get(fileUrl);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            
            String json = new String(buffer, "UTF-8");
            Gson gson = App.gson();
            Map<String, Object> settings = gson.fromJson(json, Map.class);
            
            // 应用设置（合并，不覆盖已存在的）
            if (settings != null && !settings.isEmpty()) {
                for (Map.Entry<String, Object> entry : settings.entrySet()) {
                    // 只同步非敏感设置，跳过某些本地设置
                    String key = entry.getKey();
                    if (!shouldSkipSetting(key)) {
                        Prefers.put(key, entry.getValue());
                    }
                }
                Logger.d("WebDAV: 设置下载成功");
                return true;
            }
            
            return false;
        } catch (Exception e) {
            Logger.e("WebDAV: 设置下载失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 判断是否应该跳过某个设置项
     */
    private boolean shouldSkipSetting(String key) {
        // 跳过WebDAV相关设置，避免循环同步
        if (key.startsWith("webdav_")) {
            return true;
        }
        // 跳过设备特定设置
        if (key.equals("device_uuid") || key.equals("device_name")) {
            return true;
        }
        return false;
    }
    
    /**
     * 上传完整备份（包含所有数据）
     */
    public boolean uploadBackup() {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法上传备份");
            return false;
        }
        
        try {
            Backup backup = Backup.create();
            String json = backup.toString();
            
            // 确保目录存在（如果baseUrl包含子目录）
            if (syncMode == SyncMode.ACCOUNT && !TextUtils.isEmpty(baseUrl)) {
                try {
                    String dirUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                    ensureDirectory(dirUrl);
                } catch (Exception e) {
                    Logger.w("WebDAV: 创建目录失败，尝试继续上传: " + e.getMessage());
                }
            }
            
            // 上传文件
            String fileUrl = getFileUrl(BACKUP_FILE);
            byte[] data = json.getBytes("UTF-8");
            sardine.put(fileUrl, data);
            
            Logger.d("WebDAV: 完整备份上传成功");
            return true;
        } catch (Exception e) {
            Logger.e("WebDAV: 完整备份上传失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 下载完整备份
     */
    public boolean downloadBackup() {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法下载备份");
            return false;
        }
        
        try {
            String fileUrl = getFileUrl(BACKUP_FILE);
            
            // 检查文件是否存在
            if (!sardine.exists(fileUrl)) {
                Logger.d("WebDAV: 备份文件不存在，跳过下载");
                return false;
            }
            
            // 下载文件
            InputStream is = sardine.get(fileUrl);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            
            String json = new String(buffer, "UTF-8");
            Backup backup = Backup.objectFrom(json);
            
            // 恢复备份
            if (!backup.getConfig().isEmpty()) {
                backup.restore();
                Logger.d("WebDAV: 完整备份下载并恢复成功");
                return true;
            }
            
            return false;
        } catch (Exception e) {
            Logger.e("WebDAV: 完整备份下载失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 同步观看记录（上传+下载合并）
     * @param async 是否异步执行，true=异步，false=同步（阻塞）
     */
    public boolean syncHistory(boolean async) {
        if (!isConfigured()) {
            return false;
        }
        
        // 防止重复同步
        if (isSyncing) {
            Logger.w("WebDAV: 同步正在进行中，跳过本次请求");
            return false;
        }
        
        Runnable syncTask = () -> {
            try {
                isSyncing = true;
                // 先上传本地记录
                uploadHistory();
                // 再下载远程记录并合并
                downloadHistory();
            } finally {
                isSyncing = false;
            }
        };
        
        if (async) {
            App.execute(syncTask);
        } else {
            syncTask.run();
        }
        
        return true;
    }
    
    /**
     * 同步观看记录（异步执行，默认）
     */
    public boolean syncHistory() {
        return syncHistory(true);
    }
    
    /**
     * 同步设置（上传+下载合并）
     * @param async 是否异步执行
     */
    public boolean syncSettings(boolean async) {
        if (!isConfigured()) {
            return false;
        }
        
        Runnable syncTask = () -> {
            // 先上传本地设置
            uploadSettings();
            // 再下载远程设置并合并
            downloadSettings();
        };
        
        if (async) {
            App.execute(syncTask);
        } else {
            syncTask.run();
        }
        
        return true;
    }
    
    /**
     * 同步设置（异步执行，默认）
     */
    public boolean syncSettings() {
        return syncSettings(true);
    }
    
    /**
     * 完整同步（观看记录+设置）
     * @param async 是否异步执行
     */
    public boolean syncAll(boolean async) {
        if (!isConfigured()) {
            return false;
        }
        
        // 防止重复同步
        if (isSyncing) {
            Logger.w("WebDAV: 同步正在进行中，跳过本次请求");
            return false;
        }
        
        Runnable syncTask = () -> {
            try {
                isSyncing = true;
                // 先上传本地记录
                uploadHistory();
                // 再下载远程记录并合并
                downloadHistory();
                // 同步设置
                syncSettings(false); // 设置同步使用同步方式，避免嵌套异步
            } finally {
                isSyncing = false;
            }
        };
        
        if (async) {
            App.execute(syncTask);
        } else {
            syncTask.run();
        }
        
        return true;
    }
    
    /**
     * 完整同步（异步执行，默认）
     */
    public boolean syncAll() {
        return syncAll(true);
    }
    
    /**
     * 重新加载配置（配置更改后调用）
     */
    public void reloadConfig() {
        loadConfig();
    }
    
    /**
     * 修复History的key中的站点名称编码
     * key格式: 站点key$视频ID$cid
     */
    private String fixHistoryKey(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        
        try {
            // 使用AppDatabase.SYMBOL分隔
            String symbol = com.fongmi.android.tv.db.AppDatabase.SYMBOL;
            String[] parts = key.split(java.util.regex.Pattern.quote(symbol));
            
            if (parts.length >= 3) {
                // parts[0] = 站点key, parts[1] = 视频ID, parts[2] = cid
                String siteKey = parts[0];
                String fixedSiteKey = fixEncodingIfNeeded(siteKey);
                
                if (!siteKey.equals(fixedSiteKey)) {
                    // 重新组装key
                    StringBuilder newKey = new StringBuilder(fixedSiteKey);
                    for (int i = 1; i < parts.length; i++) {
                        newKey.append(symbol).append(parts[i]);
                    }
                    return newKey.toString();
                }
            }
        } catch (Exception e) {
            Logger.e("WebDAV: 修复History key失败: " + e.getMessage());
        }
        
        return key;
    }
    
    /**
     * 修复字符串编码问题
     * 尝试将错误编码的UTF-8字符串修复为正确的UTF-8
     */
    private String fixEncodingIfNeeded(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        try {
            // 检查字符串中是否包含明显的乱码特征
            // 1. 包含替换字符 U+FFFD
            // 2. 包含异常的低位控制字符
            boolean needsFix = false;
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c == '\uFFFD' || (c >= 0x80 && c < 0xA0)) {
                    needsFix = true;
                    break;
                }
            }
            
            if (needsFix) {
                // 尝试修复：假设原始数据是UTF-8，但被错误地当作ISO-8859-1解码
                byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                String fixed = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                Logger.d("WebDAV: 编码修复 '" + str + "' -> '" + fixed + "'");
                return fixed;
            }
        } catch (Exception e) {
            Logger.e("WebDAV: 编码修复失败: " + e.getMessage());
        }
        
        return str;
    }
}

