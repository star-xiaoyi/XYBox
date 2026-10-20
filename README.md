<div align="center">

# XY影视 · XYBox

**发现想看的，接着上次的。**

面向 Android 手机与平板的开源影视播放器。<br>
把影片发现、AI 找片、多源播放与观看记录，放进一个清爽的界面。

[![Release](https://img.shields.io/github/v/release/star-xiaoyi/XYBox?style=flat-square&label=Release&color=0ea5e9)](https://github.com/star-xiaoyi/XYBox/releases/latest)
![Android](https://img.shields.io/badge/Android-7.0%2B-34a853?style=flat-square)
![Architecture](https://img.shields.io/badge/ABI-arm64--v8a-64748b?style=flat-square)
[![License](https://img.shields.io/badge/License-GPL--3.0-64748b?style=flat-square)](LICENSE.md)

[下载正式版](https://github.com/star-xiaoyi/XYBox/releases/latest) · [版本记录](https://github.com/star-xiaoyi/XYBox/releases) · [反馈问题](https://github.com/star-xiaoyi/XYBox/issues)

</div>

## 界面预览

浅色卡片、玻璃质感底栏，以及围绕观看体验展开的页面。点击截图可查看原图。

<table>
  <tr>
    <th align="center">发现首页</th>
    <th align="center">AI 找片</th>
    <th align="center">播放详情</th>
    <th align="center">我的</th>
    <th align="center">设置</th>
  </tr>
  <tr>
    <td align="center"><a href="docs/images/screenshots/discover.jpg"><img src="docs/images/screenshots/discover.jpg" width="160" alt="发现首页：影片推荐、分类与榜单"></a></td>
    <td align="center"><a href="docs/images/screenshots/ai-search.jpg"><img src="docs/images/screenshots/ai-search.jpg" width="160" alt="AI 找片：文字与语音输入入口"></a></td>
    <td align="center"><a href="docs/images/screenshots/player.jpg"><img src="docs/images/screenshots/player.jpg" width="160" alt="播放详情：播放器、影片资料、选集与相关推荐"></a></td>
    <td align="center"><a href="docs/images/screenshots/profile.jpg"><img src="docs/images/screenshots/profile.jpg" width="160" alt="我的：收藏、观看历史、离线下载与最近在看"></a></td>
    <td align="center"><a href="docs/images/screenshots/settings.jpg"><img src="docs/images/screenshots/settings.jpg" width="160" alt="设置：播放、手势、AI、外观与网络选项"></a></td>
  </tr>
</table>

<sub>截图来自 0.5.5-beta5，部分版本标记与界面文案可能与正式版不同；截图中的影片信息仅用于展示界面。</sub>

## 从找片到续看

| | 体验 |
| --- | --- |
| **发现好片** | 浏览推荐、分类与榜单，查看影片简介、演职人员、评分和相关推荐。 |
| **AI 找片** | 用文字或语音描述想看的内容；使用前需自行配置 AI 服务密钥。 |
| **多源点播** | 聚合用户添加的来源，切换线路与剧集；播放失败时尝试同片同集的其他可用来源。 |
| **自在播放** | ExoPlayer / IJKPlayer 双内核，搭配倍速、缩放、字幕、音轨、弹幕及片头片尾设置。 |
| **顺手操作** | 调节亮度、音量与进度，拖动查看进度预览；手势可按习惯单独配置。 |
| **接着上次看** | 管理观看记录、收藏与离线下载，支持 DLNA 投屏和 WebDAV 云同步。 |
| **自己的界面** | 深浅色主题、玻璃质感底栏，以及适配手机和平板的触屏布局。 |

> 当前专注手机与平板的点播体验，手机端不包含直播。电视端代码保留，但不开发、不发布电视端安装包。

## 开始使用

1. 在 [最新正式版](https://github.com/star-xiaoyi/XYBox/releases/latest) 下载 `XYBox-mobile-arm64-v8a.apk` 并安装。
2. 添加你有权使用的点播源。应用本身不提供影视内容，能否搜索和播放取决于所添加来源及网络状态。
3. 从首页或搜索进入影片，观看记录可在“我的”中继续；AI 找片与 WebDAV 同步按需配置。

应用内**点击版本号**检查正式版，**长按版本号**检查包含 beta 的测试通道。请仅从本仓库 Releases 获取安装包。

## 构建与文档

需要 **JDK 17** 与 **Android SDK**，在仓库根目录通过 Git Bash 执行项目脚本：

```bash
./build.sh --check   # 编译与回归检查，不生成安装包
./build.sh           # 构建当前版本的 release 安装包
```

安装包位于 `app/build/outputs/apk/release/`。构建与发布统一使用 `build.sh`，不使用 debug 包作为验收产物。

- [编译与发布操作手册](docs/操作手册.md)：环境、版本号、签名、更新与发布流程。
- [代码地图](docs/代码地图.md)：源码结构及主要模块。
- [踩坑记录](docs/踩坑记录.md)：混淆、布局资源与手势等已知注意事项。
- [播放验收清单](docs/自动播放测试.md)：多源失效、历史续播与异常恢复测试。

## 致谢与协议

基于 [FongMi/TV](https://github.com/FongMi/TV)，参考 [Tosencen/XMBOX](https://github.com/Tosencen/XMBOX)（v3.1.6）二次开发。感谢上游项目及所有贡献者。

本项目以 **GNU GPL v3.0** 协议开源，分发与修改请遵循 [LICENSE.md](LICENSE.md) 中的完整条款。

## 免责声明

- 本项目仅供学习交流使用，不得用于商业用途。
- 项目不提供、不内置任何影视资源，所有内容均由用户自行添加的第三方源提供，与本项目无关。
- 如相关内容侵犯了您的权益，请联系对应的源提供方处理。
- 使用本项目产生的一切后果由使用者自行承担。
- 截图中的影片海报与资料仅用于界面展示，相关权利归各自权利人所有。
