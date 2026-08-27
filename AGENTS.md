# AGENTS.md — XYBox 项目说明（给 AI 协作者）

## 这个项目是什么

XYBox 是一个安卓影视 App（点播 + 直播 + 投屏 + 下载），基于 FongMi/TV（XMBOX 3.1.6）二次开发维护。当前阶段**只做手机端**（触屏 APK，同时适配手机和平板），电视端（leanback）保留代码但不在开发发布范围内。

- 仓库：https://github.com/star-xiaoyi/XYBox
- 包名：`com.xybox.app`

## 构建环境

编译环境不在默认位置，全部在 `D:\dev`：JDK 17 `D:\dev\runtimes\jdk-17`、Android SDK `D:/dev/tools/android-sdk`、Gradle 缓存 `D:\dev\gradle-cache`（`GRADLE_USER_HOME` 必须指向它，否则重新下载依赖）、adb `D:\dev\tools\android-sdk\platform-tools\adb.exe`。

标准编译命令（PowerShell）：

```powershell
$env:JAVA_HOME='D:\dev\runtimes\jdk-17'; $env:GRADLE_USER_HOME='D:\dev\gradle-cache'; .\gradlew.bat assembleRelease --no-daemon
```

产物 `app/build/outputs/apk/release/XYBox-release.apk`（约 36MB，release 开启 minify + shrinkResources）。gradle 命令可能被沙箱拦截（无法写 `D:\dev\gradle-cache\daemon` 日志），遇到 `TRAE Sandbox Error` 用需要用户授权的方式重跑。

**完整的编译发布步骤见 [docs/操作手册.md](docs/操作手册.md)。**

## 源码结构（最容易踩的坑）

`app/build.gradle` 的 sourceSets 把 **mobile 目录作为 java/res 源码目录**合并进 debug/release 构建：

```
app/src/main/     → 主源码 + 主 AndroidManifest（实际生效的 manifest）
app/src/mobile/   → 手机端 UI（java + res，通过 sourceSets 合并）
app/src/leanback/ → 电视端（当前不构建）
```

**关键陷阱：`app/src/mobile/AndroidManifest.xml` 不参与构建合并！** 新增 Activity/Service 必须注册到 `app/src/main/AndroidManifest.xml`，否则运行时 `ActivityNotFoundException` 崩溃（已实际踩过）。

## 重要文件路径

| 路径 | 说明 |
|---|---|
| `app/src/main/java/com/fongmi/android/tv/utils/WebDAVSyncManager.java` | WebDAV 云同步核心（v2 格式，含合并/墓碑/冲突处理） |
| `app/src/main/java/com/fongmi/android/tv/Setting.java` | 所有偏好设置的读写入口 |
| `app/src/main/java/com/fongmi/android/tv/Updater.java` | 应用内更新（release / dev 双通道） |
| `app/src/main/java/com/fongmi/android/tv/utils/ResUtil.java` | 尺寸/坐标工具，含手势边缘判定 `isEdge` |
| `app/src/mobile/java/.../ui/custom/CustomKeyDownVod.java` + `CustomKeyDownLive.java` | 播放器触摸手势（单击/双击/滑动/捏合/拖动切集） |
| `app/src/mobile/java/.../ui/fragment/SettingFragment.java` | 设置页主 Fragment |
| `app/src/mobile/java/.../ui/fragment/VodFragment.java` | 首页（搜索、历史、下拉刷新） |
| `app/src/mobile/java/.../ui/activity/SettingPlayerActivity.java` | 播放设置二级页 |
| `app/src/mobile/java/.../ui/activity/SettingOperationActivity.java` | 操作设置（手势）二级页 |
| `app/src/mobile/java/.../ui/custom/CustomSwitch.java` | 自定义开关（自绘，勿用系统样式） |
| `app/src/mobile/java/.../ui/custom/CustomSwipeRefreshLayout.java` | 防横滑误触发的下拉刷新 |
| `app/src/mobile/res/values/styles.xml` | 主题与 SettingsCard/SettingsRow 等设置页样式 |
| `app/src/mobile/res/values/colors.xml` + `values-night/colors.xml` | iOS 风格语义色板（深浅两套） |
| `app/proguard-rules.pro` | R8 混淆规则 |

## 经验

1. **Gson + R8 混淆**：release 包中任何被 Gson 反序列化的内部数据类（如 `WebDAVSyncManager$SyncEnvelope`）必须在 `proguard-rules.pro` 里 keep，否则字段名和泛型被擦除，列表元素退化成 `LinkedTreeMap`，强转 Bean 时 ClassCastException。`bean` 包已整体 keep，`utils` 包里的同步类要手动加规则。验证方法：查 `app/build/outputs/mapping/release/mapping.txt`。
2. **测试连接成功 ≠ 同步逻辑正常**：WebDAV 测试连接不经过 Gson 解析，同步才经过。排查同步问题要直接看同步路径。
3. **真机验证必须用 release 包**：混淆相关问题 debug 包永远复现不了。
4. **安装签名冲突**：`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 说明手机上旧包签名不同，需先卸载（会清本地数据，WebDAV 云端数据不受影响）。
5. **删除资源限定符变体后必须 clean 重建**：删掉 `layout-sw600dp/xxx.xml` 这类变体文件后，Gradle 的资源增量合并**不会**把它从 `app/build/intermediates/merged-not-compiled-resources/` 里清掉，陈旧副本会继续被打进 APK。表现极具迷惑性：源码只剩一份布局，手机怎么测都正常，但平板（sw>=600dp）运行时仍命中幽灵变体，`ViewBinding.inflate` 报 `Missing required view with ID: xxx`。v0.2.2 删了源文件却没 clean，v0.2.3 仍在崩，直到 v0.2.4 执行 `gradlew clean` 才真正生效。验证方法：`find app/build -name "<布局名>*.xml"`，确认没有多余限定符目录。
6. **发预发布版用户收不到更新**：GitHub 的 `/releases/latest` 接口按设计跳过所有 prerelease。v0.3.2-beta 发出去手机检查不到更新就是这个原因。现在 release 通道（点击版本号）查 `/releases/latest` 只看正式版，dev 通道（**长按**版本号）查 `/releases` 列表含 beta。发 beta 后要提醒用户长按。
7. **应用内更新的资产命名**：`Updater.findApk` 要求文件名同时含 `mode`（mobile）和 `abi`（`arm64` / `arm64-v8a`），例如 `XYBox-mobile-arm64-v8a.apk`。不含 abi 时有「唯一 apk」兜底，但不要依赖。tag 形如 `v0.3.3`，`needUpdate` 逐段比数字，段内非数字部分按 0 处理。
8. **窗口坐标 vs 屏幕坐标**：`MotionEvent.getRawX/getRawY` 是屏幕坐标（含窗口偏移），`ResUtil.getScreenWidth/Height` 是窗口自身尺寸（不含偏移）。全屏时偏移为 0 两者重合，分屏/小窗时不重合。v0.3.1 的手势边缘判定混用了这两者，导致分屏下整个播放区被误判成系统手势边缘、触摸全灭。**凡是涉及触摸坐标的判断，一律用窗口内坐标（`getX` + `getLocationInWindow`），不碰 raw 坐标**；播放器左右分区要用视频 View 的实际宽度，不是屏幕宽度。改完必须在分屏和小窗下真机回归。
9. **提交规范**：conventional commits 中文描述；PowerShell 不支持 heredoc，多段提交信息用多个 `-m` 参数。

## 用户偏好

- 项目数据必须真实，不允许编造。
- 改完通常要求：升版本号 → 编译 release → 发布到 GitHub → 用户下载真机测试。具体命令见 [docs/操作手册.md](docs/操作手册.md)。
- 发 beta（`--prerelease`）时务必告诉用户要**长按**版本号才能检查到。
