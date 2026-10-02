# XYBox 0.5.0 APK 体积分析

日期：2026-10-01。基线提交：0685cfbd0ccfd72a60fc0e34b111299c00a01502。
分析对象：已发布 0.5.0 的本地正式 APK，38,093,529 字节，即 36.33 MiB（约 38.09 MB）。
SHA-256：`80b3dbed0c818733e76bf87f9e210eb4b2436beb16e85ad4dc0185acef4ed06e`。

只分析，未修改源代码、依赖、打包配置或 APK，未构建/发布新版本。本轮仅生成分析资料与本报告。下列预计新包大小基于 ZIP 条目实际占用和逐文件 DEFLATE 测算，不是已编译成功的新 APK。

## 结论与优先级

1. **首先做不适用平台/无对应代码的数据清理**：JNA 其他操作系统文件 0.50 MiB，加三份 Picnic 数据 1.16 MiB，合计 1.66 MiB。理论 APK 从 36.33 降至约 34.67 MiB。这两项候选有较强依据，仍需排除动态片源对精确资源路径的访问并做完整包核验。
2. **保留 mitv 内容，改为压缩 asset**：实测可再省 1.80 MiB，合计约 3.46 MiB，理论 APK 约 32.87 MiB。无需先删除 mitv 能力，也无需改变其他 JNI 库的安装方式。
3. **如果更在意下载包大小，可以另测压缩 JNI**：所有 JNI 从 22.35 压到约 8.81 MiB，单这项约省 13.53 MiB。仅改 JNI 的理论 APK 约 22.79 MiB；再合并前两项约 19.34 MiB。该方案会改变安装时解压行为，必须单独决定和验证。
4. 收紧第三方 keep 规则属于后续精细优化。不能根据源码行数估算可省几 MB，尤其不能随意删播放器、SMB、DLNA、JS/JAR 片源能力。

以上收益不能重复相加：如果直接删除 libmitv.so，就不能再叠加该文件的压缩收益。本报告优先考虑保留其内容。

## 实际 APK 体积构成

以 ZIP 中压缩后的占用计，签名、目录和对齐开销另列；MB 为十进制，MiB 为二进制。

| 类别 | 文件数 | 包内占用 MiB | 占 APK 比例 |
|---|---:|---:|---:|
| JNI 原生库 | 13 | 22.35 | 61.5% |
| Java/Kotlin 编译代码 | 3 | 5.40 | 14.9% |
| assets（含 libmitv.so） | 48 | 4.36 | 12.0% |
| Android 资源与资源表 | 1215 | 1.94 | 5.4% |
| 依赖自带文件、签名清单等 | 186 | 1.98 | 5.5% |
| ZIP 目录、签名块及对齐等 | — | 0.30 | 0.8% |

## 具体候选与证据

### A. 排除 JNA 中非安卓平台文件

APK 带有如下 Windows、macOS、AIX 原生文件；与本地缓存的 jna-5.2.0.jar 资源相符。它们是 Java 资源路径下的文件，不能靠 arm64 ABI 过滤自动去掉。

- `com/sun/jna/aix-ppc/libjnidispatch.a`：包内 128,828 字节。
- `com/sun/jna/aix-ppc64/libjnidispatch.a`：包内 133,563 字节。
- `com/sun/jna/darwin/libjnidispatch.jnilib`：包内 67,637 字节。
- `com/sun/jna/win32-x86-64/jnidispatch.dll`：包内 102,853 字节。
- `com/sun/jna/win32-x86/jnidispatch.dll`：包内 95,625 字节。

合计 528,506 字节（0.50 MiB）。建议精确排除这些非安卓路径，保留 JNA Java 类及安卓所需内容，不必先整模块删除 zlive。

### B. 排除没有对应运行代码的 Picnic 数据

- `org/bouncycastle/pqc/crypto/picnic/lowmcL1.bin.properties`：包内 117,380 字节。
- `org/bouncycastle/pqc/crypto/picnic/lowmcL3.bin.properties`：包内 348,178 字节。
- `org/bouncycastle/pqc/crypto/picnic/lowmcL5.bin.properties`：包内 749,257 字节。

合计 1,214,815 字节（1.16 MiB）。在本次正式版 mapping 中，Picnic 包没有保留类；usage 中该包 26 个类被整类移除；当前 main/mobile/片源框架源码未发现对这些文件的直接引用。算法代码已裁掉，但普通 Java 资源仍被合入 APK。

这是“代码已移除，数据残留”的强候选，建议只排除这三个精确文件，不要删除整个 Bouncy Castle：mapping 仍保留 43 个 Bouncy Castle 类，密码学底层可能服务 SMB 等现有能力。动态插件可按路径读取任意资源，完整功能不变仍需针对实际片源回归。

### C. 压缩保留 libmitv.so asset

当前 `assets/libmitv.so` 是 3,745,900 字节 ARM 32 位文件，ZIP 中未压缩。DEFLATE level 6 测算为 1,863,500 字节，省 1,882,400 字节（1.80 MiB）。

`forcetech/.../com/gsoft/mitv/MainActivity.java` 的 checkLibrary 通过 `Asset.open` 获取流并复制到缓存文件 `libmitv.so`；`Asset.open` 使用 AssetManager.open，读取压缩 asset 会自动解压。

可验证方案：把包内资源换成不触发 .so 默认免压缩规则的文件名，修改读取来源，落盘缓存名仍保持 libmitv.so。需搜索全部引用，并比较解压前后的 SHA-256，确认原始二进制不变。asset 不属于系统直接 mmap 的 JNI 库，不要将它与下节 JNI 安装策略混在一起。

为何不直接删：虽然是 32 位库，现有 Force 服务仍存在读取/调用路径。删除前要确认协议支持边界；“当前设备没用过”不能证明“现有功能完全不变”。本轮不将删除它计入保守减重方案。

### D. 压缩 JNI：收益最大，但有安装方式取舍

实际 Manifest 为 `android:extractNativeLibs=false`，13 个 `lib/arm64-v8a/*.so` 均 STORED 未压缩。这允许系统直接从 APK 映射加载，也避免另外保存一份解压库，不是打包失误。

可在 Android Gradle Plugin 的 JNI packaging 配置中验证 legacy packaging 路径，让 APK 压缩保存、安装时解压。不能仅强行压 ZIP 而保留 extractNativeLibs=false，也不能随便重打已签名 APK。

- 仅 JNI 压缩理论减少下载 13.53 MiB。
- 安装后会额外放置约 22.35 MiB 的解压库。与当前模式比较，仅此项的 APK＋解压库合计理论反而增加约 8.81 MiB；未计系统文件块、DEX 优化和安装临时空间。
- 安装增加解压工作，实际安装耗时和运行加载效果需要真机测量，不能承诺零变化。
- 需核对所有播放器、网络和特殊链接原生库均可加载，升级覆盖安装和不同 Android 版本正常。

适合“下载文件越小越好”的目标；若更在意安装后占用、安装速度和现有加载行为，先做 A–C。

## 原生库占用及压缩测算

下面是同一份二进制直接进行 raw DEFLATE level 6 测算；没有裁剪功能。最终签名/对齐开销与构建器压缩参数可能使新包大小略有差异。

| 库 | 现占 MiB | 压缩后 MiB | 可省 MiB |
|---|---:|---:|---:|
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 0.01 | 0.00 | 0.01 |
| `lib/arm64-v8a/libavcodec.so` | 3.84 | 1.83 | 2.01 |
| `lib/arm64-v8a/libavif_android.so` | 0.86 | 0.42 | 0.44 |
| `lib/arm64-v8a/libavutil.so` | 0.57 | 0.27 | 0.30 |
| `lib/arm64-v8a/libcronet.76.0.3809.111.so` | 3.65 | 1.69 | 1.96 |
| `lib/arm64-v8a/libjpa.so` | 5.04 | 1.41 | 3.64 |
| `lib/arm64-v8a/libmedia3ext.so` | 0.22 | 0.07 | 0.15 |
| `lib/arm64-v8a/libquickjs-android-wrapper.so` | 1.27 | 0.53 | 0.74 |
| `lib/arm64-v8a/librtmp-jni.so` | 0.09 | 0.04 | 0.05 |
| `lib/arm64-v8a/libswresample.so` | 0.08 | 0.04 | 0.05 |
| `lib/arm64-v8a/libswscale.so` | 0.57 | 0.23 | 0.34 |
| `lib/arm64-v8a/libxl_stat.so` | 0.80 | 0.28 | 0.52 |
| `lib/arm64-v8a/libxl_thunder_sdk.so` | 5.35 | 2.01 | 3.34 |
| `assets/libmitv.so` | 3.57 | 1.78 | 1.80 |

## DEX 与依赖保留规则

DEX 总原始体积 11.48 MiB，ZIP 压缩后 5.40 MiB；不能拿某个源码库原始大小当成可缩 APK 大小。

apkanalyzer 按包输出的定义字节量（不是 ZIP 占用，也不是删除收益）显示：Rhino 约 669 KiB、Cling 约 470 KiB、OkHttp 约 273 KiB、SMBJ 约 272 KiB、Okio 约 140 KiB、JNA 约 92 KiB。若要进一步收紧，必须保持反射、SPI 和外部片源调用。

- 项目自身和 catvod 的保留名字规则已经带 allowshrinking/allowoptimization，不是整个自有代码都被强制保留。修改名字规则的收益不应夸大。
- OkHttp、Okio、Gson、Cling、SMBJ、Rhino、JNA 有较宽 keep 规则，可分组试验，但网络、投屏、共享目录、JS 解析、序列化是现有功能，不适合一次性删规则。
- Cronet 库占约 3.65 MiB，当前仓库源码未找到直接 Cronet 调用，但 catvod 暴露相关依赖，运行时会加载外部 JAR，且当前有整包 keep。只能列为需单独核对的依赖候选，不能保证去掉仍兼容所有片源。
- libjpa、迅雷库、FFmpeg/Media3、QuickJS、AVIF 分别对应特殊链接播放、解码、JS 片源和图片能力。删除它们通常意味着能力缩水，不在“功能全保留”的首批方案中。
- 已检查 13 个已打包 JNI ELF：没有额外 .debug/.zdebug/.symtab/.strtab 段可供明显缩减。不能期待再运行一次 strip 就省很多。

## 不会明显缩小 APK 的操作

删除 leanback 源码、停用 chaquo、被排除的 sherpa AAR、源码重复 SO、R8 已移除的旧界面，主要改善源码/磁盘空间。当前 APK 只有 arm64 一种 ABI，不能再靠“去掉其他 ABI”获得大幅收益。mapping-archive、构建日志、源码文档也不在 APK 中。

## 后续验证条件

建议先执行 A–C，逐项比较前后 APK，而不是先大范围删代码。

1. 保留基准 APK 与本报告；每个改动记录包内具体路径及字节变化。
2. 所有构建只走 build.sh；先 --check，再完整 release，不能以 debug 验证。
3. 对非预期变动做阻断：Manifest/组件/权限、ABI、剩余原生库哈希、数据库结构及签名应符合预期；压缩 asset 解压后必须与原文件完全相同。
4. 回归播放、直播、JS/JAR 片源、特殊协议链接、投屏、SMB、WebDAV 和 AI。若选择 JNI 压缩，再验证安装/升级、库加载与安装占用。
5. 报告中的 34.67/32.87/22.79/19.34 MiB 级别均是字节测算，不是已构建与真机验证的成果；最终以新 release APK 和用户真机结果为准。

分析原始数据：`build/ai-validation/apk-size-0.5.0.json`；DEX 包统计：`build/ai-validation/dex-packages-0.5.0.txt`。这些是本地分析资料，清理 build 后会消失。

