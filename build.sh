#!/bin/bash
#
# XYBox 一键编译/发布脚本（Windows + Git Bash）
#
#   ./build.sh                                  只编译当前版本号的 release 包
#   ./build.sh 0.3.2-beta4                      改成该版本号再编译（versionCode 自动算）
#   ./build.sh 0.3.2-beta4 --publish            编译后发到 GitHub
#   ./build.sh 0.3.2 --publish -m "更新说明"     正式版 + 自定义发布说明
#   ./build.sh --clean                          先 clean 再编译
#   ./build.sh --check                          只编译 java 看编不编得过，不出包不改版本号
#
# 工作流：beta 随便发，验收通过了才提交 git。
#
#   改代码 → --check 看编不编得过 → 发 beta1 → 真机测 → 没过就接着改、发 beta2……
#   → 测通过 → 提交一次 git（这条提交是验证过的）→ 发正式版（自动推送）
#
# 所以 git 历史里每条提交都是真机验证过的，随便挑一条回退都是安全的。
# beta 期间工作区一直脏着，这是预期的，脚本不会拦。
#
# 提交 git 和发正式版这两步都由用户开口才做，脚本不替他决定。
# 正式版必须用 -m 写发布说明（几条重点变化即可）。发布时脚本会自己提交版本号、
# 把代码推到远程、发 release、再删掉同版本号的所有 beta release 和 tag——
# 推送是发布的一部分，因为 gh 打 tag 是在服务端按远程 main 打的，不推就指向旧提交。
#
# --check 是给改完代码做快速验证用的：走的仍然是 release 那套源码和配置，
# 只是停在 javac，不跑 R8、不打包、不签名，几十秒出结果。
# 它只能回答"编不编得过"，回答不了"能不能跑"——混淆、资源合并、装机这些
# 一律要靠完整的 release 包。debug 变体任何情况下都不用。
#
# --clean 什么时候必须加：删过 layout-land/、values-night/ 这类资源限定符变体之后。
# Gradle 的增量资源合并不会清掉已删的变体，陈旧副本会继续打进 APK。
#
set -e

JAVA_HOME_DIR='D:\dev\runtimes\jdk-17'
GRADLE_CACHE_DIR='D:\dev\gradle-cache'
REPO="star-xiaoyi/XYBox"
OUT_DIR="app/build/outputs/apk/release"
# 应用内更新要求资产名同时含 mode(mobile) 和 abi，否则用户端报「未找到匹配的APK」
ASSET_NAME="XYBox-mobile-arm64-v8a.apk"

cd "$(dirname "$0")"

VERSION=""
PUBLISH=false
CLEAN=false
CHECK=false
NOTES=""

while [ $# -gt 0 ]; do
    case "$1" in
        --publish) PUBLISH=true; shift ;;
        --clean) CLEAN=true; shift ;;
        --check) CHECK=true; shift ;;
        -m|--notes) NOTES="$2"; shift 2 ;;
        -h|--help) sed -n '3,31p' "$0" | sed 's/^# \?//'; exit 0 ;;
        -*) echo "未知参数: $1"; exit 1 ;;
        *) VERSION="$1"; shift ;;
    esac
done

# ---------- 只做编译检查 ----------
# 刻意不碰版本号、不出包、不发布：这条路是给"改完代码看编不编得过"用的，
# 任何会产生可安装产物的组合都直接拒绝，免得有人拿它当发布用。
if [ "$CHECK" = true ]; then
    if [ "$PUBLISH" = true ] || [ -n "$VERSION" ]; then
        echo "--check 只做编译检查，不能和版本号或 --publish 一起用"
        exit 1
    fi
    export JAVA_HOME="$JAVA_HOME_DIR"
    export GRADLE_USER_HOME="$GRADLE_CACHE_DIR"
    echo "编译检查（compileReleaseJavaWithJavac，不出包）……"
    # 这里留着守护进程，图的就是快；后面真要出包时 build.sh 开头会先 --stop 掉，
    # 不会出现守护进程占着 app/build/ 导致 R8 写文件失败的情况。
    ./gradlew.bat :app:compileReleaseJavaWithJavac
    echo "编译通过。注意这只说明编得过，混淆和装机问题仍要靠完整 release 包验证。"
    exit 0
fi

# ---------- 发布前的检查 ----------
# beta 和正式版走两套标准，因为它们对"包要对得上代码"的要求根本不同：
#
#   beta   是丢给用户试的一次性产物，验收不通过就作废、发正式版时连 release 带 tag
#          一起删掉。它的 tag 只是 GitHub 用来挂 APK 的容器，不是源码指针。
#          所以工作区脏不脏无所谓，只提醒一句。
#   正式版 是要长期留在历史里的，tag 必须真的指向包里那份代码。
#
# 这里必须在改版本号之前查：放到发布那一步再查的话，脚本会看见自己刚写进
# app/build.gradle 的版本号，把自己判定为"有未提交的改动"。
#
# 另外要知道 gh release create 打 tag 是在**服务端**做的，指向远程 main 的最新提交，
# 跟本地 HEAD 无关。所以正式版光"工作区干净"不够，还必须确认本地已经推上去了，
# 否则 tag 会指向一个不含本次改动的旧提交。
IS_BETA=false
PRE_VERSION="$VERSION"
[ -n "$PRE_VERSION" ] || PRE_VERSION=$(grep -oE '^ *versionName "[^"]+"' app/build.gradle | sed 's/.*"\(.*\)"/\1/')
echo "$PRE_VERSION" | grep -q -- '-beta' && IS_BETA=true

if [ "$PUBLISH" = true ] && [ "$IS_BETA" = true ] && [ -n "$(git status --porcelain)" ]; then
    echo "提示：工作区有未提交的改动，beta 包不要求提交，继续发布。"
    echo "     （验收通过后再提交，届时 git 历史里每条提交都是验证过的）"
    echo
fi

if [ "$PUBLISH" = true ] && [ "$IS_BETA" = false ]; then
    # 正式版的发布说明必须自己写。默认那句"正式版"对用户毫无信息量，
    # 而这恰恰是用户在 releases 页面唯一会读的东西。
    if [ -z "$NOTES" ]; then
        echo "发正式版必须用 -m 写发布说明。"
        echo "把这轮 beta 累积的改动归纳成几条重点变化就行，不要长篇——"
        echo "详细的原因和取舍留在 git 提交信息里。"
        exit 1
    fi
    if [ -n "$(git status --porcelain)" ]; then
        echo "发正式版前工作区必须干净——tag 要真的指向包里这份代码："
        git status --short
        echo
        echo "这些改动要先提交（提交由用户开口，见 AGENTS.md 铁律 4）。"
        exit 1
    fi
fi

# ---------- 版本号 ----------
# 规则：betaN 是测试版，无后缀是正式版。
#
#   versionCode = major*1000000 + minor*10000 + patch*100 + 尾数
#   尾数：beta 用序号 N（1-98），正式版固定 99
#
#   0.3.3-beta1 = 30301      0.3.3-beta2 = 30302      0.3.3 = 30399
#   0.10.0      = 100099     1.0.0       = 1000099
#
# 每段独占两位，major/minor/patch 各自能到 99、beta 能到 98，互不串位。
# 尾数 99 保证正式版的 code 高于自己所有的 beta，否则从 beta 装正式版会被系统当降级拒掉。
if [ -n "$VERSION" ]; then
    if ! echo "$VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+(-beta([1-9]|[1-8][0-9]|9[0-8]))?$'; then
        echo "版本号格式不对: $VERSION"
        echo "只接受 0.3.2 或 0.3.2-beta1（beta 序号 1-98，正式版占用尾数 99）"
        exit 1
    fi

    MAJOR=$(echo "$VERSION" | cut -d. -f1)
    MINOR=$(echo "$VERSION" | cut -d. -f2)
    PATCH=$(echo "$VERSION" | cut -d. -f3 | cut -d- -f1)
    if [ "$MINOR" -gt 99 ] || [ "$PATCH" -gt 99 ]; then
        echo "minor 和 patch 都不能超过 99（每段只留了两位）: $VERSION"
        exit 1
    fi
    if echo "$VERSION" | grep -q -- '-beta'; then
        TAIL=$(echo "$VERSION" | sed 's/.*-beta//')
    else
        TAIL=99
    fi
    CODE=$(( MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + TAIL ))

    OLD_CODE=$(grep -oE '^ *versionCode [0-9]+' app/build.gradle | grep -oE '[0-9]+')
    OLD_NAME=$(grep -oE '^ *versionName "[^"]+"' app/build.gradle | sed 's/.*"\(.*\)"/\1/')
    # 传的版本号跟当前完全一致 = 重跑（上次发布中途失败了），放行。
    # 只有真的往回退才拒绝——手机装不上比已装版本 code 低的包。
    if [ "$CODE" -eq "$OLD_CODE" ] && [ "$VERSION" = "$OLD_NAME" ]; then
        echo "版本号未变（$VERSION，versionCode $CODE），按重跑处理"
    elif [ "$CODE" -le "$OLD_CODE" ]; then
        echo "versionCode 不能倒退: 当前 $OLD_CODE，新值 $CODE"
        echo "手机装不上比已装版本 code 低的包，请提高版本号"
        exit 1
    fi

    sed -i "s/^\( *\)versionCode [0-9]\+/\1versionCode $CODE/" app/build.gradle
    sed -i "s/^\( *\)versionName \".*\"/\1versionName \"$VERSION\"/" app/build.gradle
    echo "版本号已改为 $VERSION (versionCode $CODE)"
else
    VERSION=$(grep -oE '^ *versionName "[^"]+"' app/build.gradle | sed 's/.*"\(.*\)"/\1/')
    echo "沿用当前版本号 $VERSION"
fi

# ---------- 编译 ----------
export JAVA_HOME="$JAVA_HOME_DIR"
export GRADLE_USER_HOME="$GRADLE_CACHE_DIR"

TASKS="assembleRelease"
[ "$CLEAN" = true ] && TASKS="clean assembleRelease"

# --no-daemon 起的是一次性进程，但 IDE 或手动跑过的 gradlew 会留下常驻守护进程，
# 它们一直占着 app/build/ 里的输出文件，R8 写 classes.dex 时就撞上「文件被占用」
# 而整个构建失败。先请它们退出，这一步很快，且对没开守护进程的情况无副作用。
echo "停止残留的 Gradle 守护进程……"
./gradlew.bat --stop >/dev/null 2>&1 || true

echo "开始编译（$TASKS）……"
./gradlew.bat $TASKS --no-daemon

APK="$OUT_DIR/XYBox-release.apk"
[ -f "$APK" ] || { echo "编译产物不存在: $APK"; exit 1; }

cp "$APK" "$OUT_DIR/$ASSET_NAME"
SIZE=$(du -h "$APK" | cut -f1)
echo "编译完成: $OUT_DIR/$ASSET_NAME ($SIZE)"

[ "$PUBLISH" = false ] && { echo "如需发布，加 --publish"; exit 0; }

# ---------- 发布 ----------
# 工作区检查已经在改版本号之前做过了，这里不再重复——此刻唯一的改动就是脚本
# 自己写进 app/build.gradle 的版本号。

TAG="v$VERSION"
if echo "$VERSION" | grep -q -- '-beta'; then
    PRE_FLAG="--prerelease"
    CHANNEL="测试版（用户需长按设置页版本号，走 dev 通道才能检查到）"
else
    PRE_FLAG="--latest"
    CHANNEL="正式版"
fi
[ -n "$NOTES" ] || NOTES="$CHANNEL"

# ---------- 正式版：先把代码推上去 ----------
# gh 打 tag 是在服务端按远程 main 的最新提交打的，本地没推等于 tag 指向旧提交。
# 所以推送是发布动作的一部分，不该甩给人工。
# 放在编译成功之后：编译失败的版本号没必要推到云端。
# beta 不走这里——它连提交都不做。
if [ "$IS_BETA" = false ]; then
    if [ -n "$(git status --porcelain app/build.gradle)" ]; then
        git add app/build.gradle
        git commit -q -m "chore: 发布 $VERSION"
        echo "已提交版本号（chore: 发布 $VERSION）"
    fi
    echo "推送到远程……"
    git push -q origin HEAD:main
    echo "已推送，远程 main = $(git rev-parse --short HEAD)"
fi

# 分三步而不是一条 gradle release create 带资产：36MB 的包上传常常超时，
# 一旦中断 release 会卡在 Draft 且没有资产，用户什么也收不到。
# 拆开以后每步都可单独重跑，最后一步才让它对用户可见。
echo "创建 $TAG（草稿）……"
# 上一轮若在上传中断，tag 已经存在，create 会直接失败——那样"每步可单独重跑"就是空话。
# 已存在就跳过，让重跑从上传接着走。
if gh release view "$TAG" >/dev/null 2>&1; then
    echo "$TAG 已存在，跳过创建"
else
    gh release create "$TAG" --draft --title "$TAG" --notes "$NOTES"
fi

echo "上传 $ASSET_NAME……"
gh release upload "$TAG" "$OUT_DIR/$ASSET_NAME" --clobber

echo "转为正式发布……"
gh release edit "$TAG" --draft=false $PRE_FLAG

gh release view "$TAG" --json isDraft,isPrerelease,assets \
    --jq '"draft=\(.isDraft) prerelease=\(.isPrerelease) assets=\([.assets[].name]|join(","))"'

echo "已发布: https://github.com/$REPO/releases/tag/$TAG"
echo "$CHANNEL"

# ---------- 正式版发布后清掉同版本的 beta ----------
# beta 是一次性测试产物，正式版一出它们就没有存在意义了：留着只会让 releases 页面
# 和 tag 列表越堆越长，用户下载时还得辨认哪个是最新的。
# --cleanup-tag 连 tag 一起删，否则 release 没了 tag 还留着，更乱。
# 只删自己这个版本号的 beta（0.3.4-beta*），不碰别的版本。
if [ "$IS_BETA" = false ]; then
    BETAS=$(gh release list --limit 100 --json tagName --jq ".[].tagName" 2>/dev/null | grep -E "^v${VERSION}-beta[0-9]+$" || true)
    if [ -n "$BETAS" ]; then
        echo
        echo "清理 $VERSION 的测试版……"
        for t in $BETAS; do
            gh release delete "$t" --yes --cleanup-tag >/dev/null 2>&1 && echo "  已删除 $t" || echo "  删除失败 $t（可稍后手动清理）"
        done
    fi
fi

# beta 期间版本号改动是故意不提交的（见 AGENTS.md 铁律 4），这里不做任何提醒——
# 提醒只会诱导人在没验收通过时就提交。正式版的版本号上面已经提交并推送了。
