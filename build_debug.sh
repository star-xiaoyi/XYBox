#!/bin/bash

echo "=== 构建v8a手机测试版 ==="

# 清理之前的构建
echo "清理之前的构建..."
./gradlew clean

# 构建mobile arm64-v8a debug版本
echo "构建mobile arm64-v8a debug版本..."
./gradlew assembleMobileArm64_v8aDebug

# 检查构建结果
if [ $? -eq 0 ]; then
    echo "=== 构建成功 ==="
    
    # 查找生成的APK文件
    APK_PATH=$(find app/build/outputs/apk/mobile/arm64-v8a/debug -name "*.apk" 2>/dev/null | head -1)
    
    if [ -n "$APK_PATH" ]; then
        echo "APK文件位置: $APK_PATH"
        echo "文件大小: $(ls -lh "$APK_PATH" | awk '{print $5}')"
        echo "文件信息:"
        ls -la "$APK_PATH"
        
        # 显示APK详细信息
        echo ""
        echo "=== APK详细信息 ==="
        aapt dump badging "$APK_PATH" | grep -E "(package|application-label|native-code|sdkVersion|targetSdkVersion)"
        
    else
        echo "未找到生成的APK文件"
        find app/build/outputs -name "*.apk" 2>/dev/null
    fi
else
    echo "=== 构建失败 ==="
    echo "请检查错误信息"
fi
