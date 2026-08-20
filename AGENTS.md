# AGENTS.md — XYBox 项目说明（给 AI 协作者）

## 这个项目是什么

XYBox 是一个安卓影视 App（点播 + 直播 + 投屏 + 下载），基于 FongMi/TV（XMBOX 3.1.6）二次开发维护。当前阶段**只做手机端**（触屏 APK，同时适配手机和平板），电视端（leanback）保留代码但不在开发发布范围内。

- 仓库：https://github.com/star-xiaoyi/XYBox
- 包名：`com.xybox.app`，当前版本 0.1.1

## 构建环境（重要）

编译环境不在默认位置，全部在 `D:\dev`：

- JDK 17：`D:\dev\runtimes\jdk-17`
- Android SDK：`D:/dev/tools/android-sdk`（已写入 `local.properties`）
- Gradle 缓存：`D:\dev\gradle-cache`（`GRADLE_USER_HOME` 必须指向它，否则重新下载依赖）
- adb：`D:\dev\tools\android-sdk\platform-tools\adb.exe`

标准编译命令（PowerShell）：

```powershell
$env:JAVA_HOME='D:\dev\runtimes\jdk-17'; $env:GRADLE_USER_HOME='D:\dev\gradle-cache'; .\gradlew.bat assembleRelease --no-daemon
```

产物：`app/build/outputs/apk/release/XYBox-release.apk`（约 40MB，release 开启 minify + shrinkResources）。

注意：gradle 编译命令可能被沙箱拦截（无法写 `D:\dev\gradle-cache\daemon` 日志），遇到 `TRAE Sandbox Error` 时用需要用户授权的方式重跑即可。

## 源码结构（最容易踩的坑）

`app/build.gradle` 的 sourceSets 把 **mobile 目录作为 java/res 源码目录**合并进 debug/release 构建：

```
app/src/main/     → 主源码 + 主 AndroidManifest（实际生效的 manifest）
app/src/mobile/   → 手机端 UI（java + res，通过 sourceSets 合并）
app/src/leanback/ → 电视端（当前不构建）
```

**关键陷阱：`app/src/mobile/AndroidManifest.xml` 不参与构建合并！** 新增 Activity/Service 必须注册到 `app/src/main/AndroidManifest.xml`，否则运行时 `ActivityNotFoundException` 崩溃（本项目已实际踩过这个坑）。

## 重要文件路径

| 路径 | 说明 |
|---|---|
| `app/src/main/java/com/fongmi/android/tv/utils/WebDAVSyncManager.java` | WebDAV 云同步核心（v2 格式，含合并/墓碑/冲突处理） |
| `app/src/main/java/com/fongmi/android/tv/Setting.java` | 所有偏好设置的读写入口 |
| `app/src/main/java/com/fongmi/android/tv/Updater.java` | 应用内更新（查 GitHub releases/latest） |
| `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/SettingFragment.java` | 设置页主 Fragment |
| `app/src/mobile/java/com/fongmi/android/tv/ui/fragment/VodFragment.java` | 首页（搜索、历史、下拉刷新） |
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/SettingPlayerActivity.java` | 播放设置二级页 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/activity/SettingOperationActivity.java` | 操作设置（手势）二级页 |
| `app/src/mobile/java/com/fongmi/android/tv/ui/custom/CustomSwitch.java` | 自定义开关（自绘，勿用系统样式） |
| `app/src/mobile/java/com/fongmi/android/tv/ui/custom/CustomSwipeRefreshLayout.java` | 防横滑误触发的下拉刷新 |
| `app/src/mobile/res/values/styles.xml` | 主题与 SettingsCard/SettingsRow 等设置页样式 |
| `app/src/mobile/res/values/colors.xml` + `values-night/colors.xml` | iOS 风格语义色板（深浅两套） |
| `app/proguard-rules.pro` | R8 混淆规则（Gson 相关 keep 规则见下） |
| `XYBox修改计划书.md` | 开发规范与阶段计划 |


## 经验

1. **Gson + R8 混淆**：release 包中任何被 Gson 反序列化的内部数据类（如 `WebDAVSyncManager$SyncEnvelope`）必须在 `proguard-rules.pro` 里 keep，否则字段名和泛型被擦除，列表元素退化成 `LinkedTreeMap`，强转 Bean 时 ClassCastException。`bean` 包已整体 keep，`utils` 包里的同步类要手动加规则。验证方法：查 `app/build/outputs/mapping/release/mapping.txt`。
2. **测试连接成功 ≠ 同步逻辑正常**：WebDAV 测试连接不经过 Gson 解析，同步才经过。排查同步问题要直接看同步路径。
3. **真机验证必须用 release 包**：混淆相关问题 debug 包永远复现不了。
4. **安装签名冲突**：`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 说明手机上旧包签名不同，需先卸载（会清本地数据，WebDAV 云端数据不受影响）。
5. **检查更新逻辑**：`needUpdate` 按版本号逐段比较；release 的 tag 形如 `v0.1.1`。应用内更新要求资产文件名同时包含 `mode`（mobile）和 `abi`（arm64 / arm64-v8a）字样，例如 `XYBox-mobile-arm64-v8a.apk`，否则报"未找到匹配的APK"。注意：当前 v0.1.0/v0.1.1 上传的资产名是 `XYBox-release.apk`，不满足该匹配规则——版本相同时走"已是最新版本"分支不会触发资产匹配，但**发布更高版本时必须用符合规则的文件名**，否则用户无法应用内更新。
6. **删除资源限定符变体后必须 clean 重建**：删掉 `layout-sw600dp/xxx.xml` 这类变体文件后，Gradle 的资源增量合并**不会**把它从 `app/build/intermediates/merged-not-compiled-resources/` 里清掉，陈旧副本会继续被打进 APK。表现极具迷惑性：源码只剩一份布局，手机怎么测都正常，但平板（sw>=600dp）运行时仍命中幽灵变体，`ViewBinding.inflate` 报 `Missing required view with ID: xxx`。本项目实际踩过——v0.2.2 删了源文件却没 clean，v0.2.3 仍在崩，直到 v0.2.4 执行 `gradlew clean` 才真正生效。验证方法：`find app/build -name "<布局名>*.xml"`，确认没有多余限定符目录。
7. **提交规范**： conventional commits 中文描述；PowerShell 不支持 heredoc，多段提交信息用多个 `-m` 参数。

## 用户偏好（务必遵守）

- 项目数据必须真实，不允许编造。
- 改完通常要求：编译 release → adb 安装到手机（设备已连接时）→ 用户真机验收。
- 发布流程：升 `app/build.gradle` 的 versionName/versionCode → 编译 → git 提交推送 → `gh release create vX.Y.Z <apk路径>` 发布。
