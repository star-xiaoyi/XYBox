# XYBox

一个安卓影视播放器，支持点播、直播、投屏与下载，需自行添加源。

当前只维护**手机端**（同一个 APK 适配手机和平板），电视端（leanback）代码保留但不在发布范围内。

## 下载

前往 [Releases](https://github.com/star-xiaoyi/XYBox/releases) 下载最新的 `arm64-v8a` APK。应用内也可以直接检查更新。

## 功能

- 点播：多源聚合、线路与剧集切换、播放失败自动换源、演职人员检索
- 直播：分组频道、EPG 节目单
- 播放器：ExoPlayer / IJKPlayer 双内核，硬解软解切换，倍速、缩放、片头片尾跳过、字幕与音轨选择、弹幕
- 手势：亮度、音量、进度、上下滑切集，各项可在设置中单独开关
- 形态：竖屏卡片式详情页，横屏自动切换为左右分栏；拖动把手可进入全屏
- 其他：投屏（DLNA）、离线下载、观看记录与收藏、WebDAV 云同步、深浅色主题

## 构建

需要 JDK 17 与 Android SDK。

```bash
./gradlew assembleRelease
```

产物在 `app/build/outputs/apk/release/`。

## 开源协议

本项目基于 [FongMi/TV](https://github.com/FongMi/TV) 二次开发，遵循 **GNU General Public License v3.0** 协议开源，完整条款见 [LICENSE.md](LICENSE.md)。

任何基于本项目的分发或修改版本，同样必须以 GPL-3.0 协议开源。

## 免责声明

- 本项目仅供学习交流使用，不得用于商业用途。
- 项目不提供、不内置任何影视资源，所有内容均由用户自行添加的第三方源提供，与本项目无关。
- 如相关内容侵犯了您的权益，请联系对应的源提供方处理。
- 使用本项目产生的一切后果由使用者自行承担。
