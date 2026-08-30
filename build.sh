#!/bin/bash
#
# XYBox 一键编译/发布脚本（Windows + Git Bash）
#
#   ./build.sh                                  只编译当前版本号的 release 包
#   ./build.sh 0.3.2-beta4                      改成该版本号再编译（versionCode 自动算）
#   ./build.sh 0.3.2-beta4 --publish            编译后发到 GitHub
#   ./build.sh 0.3.2 --publish -m "更新说明"     正式版 + 自定义发布说明
#   ./build.sh --clean                          先 clean 再编译
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
NOTES=""

while [ $# -gt 0 ]; do
    case "$1" in
        --publish) PUBLISH=true; shift ;;
        --clean) CLEAN=true; shift ;;
        -m|--notes) NOTES="$2"; shift 2 ;;
        -h|--help) sed -n '3,15p' "$0" | sed 's/^# \?//'; exit 0 ;;
        -*) echo "未知参数: $1"; exit 1 ;;
        *) VERSION="$1"; shift ;;
    esac
done

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

echo "开始编译（$TASKS）……"
./gradlew.bat $TASKS --no-daemon

APK="$OUT_DIR/XYBox-release.apk"
[ -f "$APK" ] || { echo "编译产物不存在: $APK"; exit 1; }

cp "$APK" "$OUT_DIR/$ASSET_NAME"
SIZE=$(du -h "$APK" | cut -f1)
echo "编译完成: $OUT_DIR/$ASSET_NAME ($SIZE)"

[ "$PUBLISH" = false ] && { echo "如需发布，加 --publish"; exit 0; }

# ---------- 发布 ----------
if [ -n "$(git status --porcelain)" ]; then
    echo
    echo "工作区还有未提交的改动，发出去的包将对不上任何一个提交："
    git status --short
    # 非交互环境（AI 后台跑、CI）读不到输入，直接拒绝并说清怎么办，
    # 不要停在一个永远等不到回答的提示上。
    if [ ! -t 0 ]; then
        echo
        echo "当前是非交互环境，无法确认。请先提交这些改动，或在终端里手动重跑。"
        exit 1
    fi
    printf "仍要发布？(y/N) "
    read -r reply
    [ "$reply" = "y" ] || [ "$reply" = "Y" ] || exit 1
fi

TAG="v$VERSION"
if echo "$VERSION" | grep -q -- '-beta'; then
    PRE_FLAG="--prerelease"
    CHANNEL="测试版（用户需长按设置页版本号，走 dev 通道才能检查到）"
else
    PRE_FLAG="--latest"
    CHANNEL="正式版"
fi
[ -n "$NOTES" ] || NOTES="$CHANNEL"

# 分三步而不是一条 gradle release create 带资产：36MB 的包上传常常超时，
# 一旦中断 release 会卡在 Draft 且没有资产，用户什么也收不到。
# 拆开以后每步都可单独重跑，最后一步才让它对用户可见。
echo "创建 $TAG（草稿）……"
gh release create "$TAG" --draft --title "$TAG" --notes "$NOTES"

echo "上传 $ASSET_NAME……"
gh release upload "$TAG" "$OUT_DIR/$ASSET_NAME" --clobber

echo "转为正式发布……"
gh release edit "$TAG" --draft=false $PRE_FLAG

gh release view "$TAG" --json isDraft,isPrerelease,assets \
    --jq '"draft=\(.isDraft) prerelease=\(.isPrerelease) assets=\([.assets[].name]|join(","))"'

echo "已发布: https://github.com/$REPO/releases/tag/$TAG"
echo "$CHANNEL"
