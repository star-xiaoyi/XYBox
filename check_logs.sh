#!/bin/bash

echo "=== 检查应用日志 ==="
echo ""

echo "1. 检查custom_spider.jar是否被加载："
echo "---"
adb logcat -d | grep -i "custom_spider" | tail -20
echo ""

echo "2. 检查JarLoader相关日志："
echo "---"
adb logcat -d | grep "JarLoader" | tail -20
echo ""

echo "3. 检查Spider初始化日志："
echo "---"
adb logcat -d | grep -E "Spider|Init\.init" | tail -30
echo ""

echo "4. 检查是否有错误："
echo "---"
adb logcat -d | grep -E "Error|Exception|Failed" | grep -i "fongmi\|spider\|jar" | tail -30
echo ""

echo "5. 检查DexNative相关问题："
echo "---"
adb logcat -d | grep -i "DexNative" | tail -20
echo ""

echo "6. 检查APK中的jar文件："
echo "---"
echo "检查已安装APK的assets目录..."
adb shell run-as com.fongmi.android.tv ls -la /data/data/com.fongmi.android.tv/cache/jar/ 2>/dev/null || echo "无权限访问，使用pull检查..."
echo ""

echo "=== 实时监控日志（按Ctrl+C停止）==="
adb logcat -c
adb logcat | grep -E "custom_spider|Spider|JarLoader|Init|fongmi" --color=always

