# AGENTS.md — XYBox 项目说明（给 AI 协作者）

XYBox 是一个安卓影视 App（点播 + 直播 + 投屏 + 下载），基于 FongMi/TV（XMBOX 3.1.6）二次开发维护。当前阶段**只做手机端**（触屏 APK，兼顾手机和平板），电视端（leanback）保留代码但不开发不发布。

- 仓库 https://github.com/star-xiaoyi/XYBox ，包名 `com.xybox.app`

## 铁律

1. **不清楚就问，不许自己猜着做。** 需求有歧义、方案有多种走法、要动没被提到的文件、发现要求本身有问题——先停下来问用户，说清楚卡在哪、有哪几个选项。做错方向比多问一句代价大得多。
2. **不许编造。** 版本号、测试结果、报错信息、数据一律照实说。没验证过的就说没验证过；测失败了就把失败原样贴出来，不要粉饰。
3. **编译发布一律走 `./build.sh`**，不要手敲 gradle 命令，否则版本号和产物命名会乱。
4. **改完的交付流程**：升版本号 → `./build.sh <版本号> --publish` → 用户下载真机测试。beta 发出去后要提醒用户**长按**设置页版本号才检查得到（点击只看正式版）。

## 编译发布

环境全在 `D:\dev`（JDK 17、Android SDK、Gradle 缓存），`build.sh` 已经配好，直接跑：

```bash
./build.sh                              # 只编译当前版本号
./build.sh 0.3.2-beta4                  # 改版本号再编译（versionCode 自动算）
./build.sh 0.3.2-beta4 --publish        # 编译后发到 GitHub（beta 自动带 --prerelease）
./build.sh 0.3.2 --publish -m "说明"     # 正式版
./build.sh --clean                      # 删过资源限定符变体后必须加
```

版本号规则：`0.3.2-beta1` → `0.3.2-beta2` → …→ `0.3.2`（正式）。beta 和正式版编译方式完全相同，**不要用 debug 包做测试**（签名不同装不上，且复现不了混淆类 bug）。

完整步骤、versionCode 算法、发布失败的补救见 **[docs/操作手册.md](docs/操作手册.md)**。

## 写代码前先看

- **[docs/代码地图.md](docs/代码地图.md)** — 源码结构（`mobile/AndroidManifest.xml` 不参与合并这个坑）、常改文件清单
- **[docs/踩坑记录.md](docs/踩坑记录.md)** — 按主题分类的实际教训：混淆打包、应用内更新、布局资源、触摸坐标。动播放器手势、图标资源、系统栏、布局变体之前**务必先查**，这几块每一条都是真崩过才写下来的。

## 提交规范

conventional commits + 中文描述。PowerShell 不支持 heredoc，多段提交信息用多个 `-m` 参数。
