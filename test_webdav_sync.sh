#!/bin/bash

# WebDAV 同步测试脚本
# 用于排查 TV 版同步问题

echo "========================================="
echo "WebDAV 同步测试工具"
echo "========================================="
echo ""

# 检查 ADB 连接
echo "1. 检查 ADB 设备连接..."
adb devices
echo ""

# 选择设备类型
echo "请选择要测试的设备："
echo "1) 手机"
echo "2) TV"
read -p "请输入选择 (1 或 2): " device_type

if [ "$device_type" == "1" ]; then
    PACKAGE="com.fongmi.android.tv.debug"
    DEVICE_NAME="手机"
elif [ "$device_type" == "2" ]; then
    PACKAGE="com.fongmi.android.tv.debug"
    DEVICE_NAME="TV"
else
    echo "无效选择"
    exit 1
fi

echo ""
echo "========================================="
echo "测试 $DEVICE_NAME 设备"
echo "========================================="
echo ""

# 清除日志
echo "2. 清除旧日志..."
adb logcat -c
echo "   ✓ 日志已清除"
echo ""

# 启动应用
echo "3. 启动应用..."
adb shell am start -n $PACKAGE/.ui.activity.MainActivity
sleep 3
echo "   ✓ 应用已启动"
echo ""

# 开始监听日志
echo "4. 开始监听 WebDAV 日志..."
echo "   提示：请在设备上进行以下操作："
echo "   - 打开设置"
echo "   - 进入 WebDAV 配置"
echo "   - 点击'测试连接'或'立即同步'"
echo ""
echo "   按 Ctrl+C 停止监听"
echo ""
echo "========================================="
echo "日志输出："
echo "========================================="

# 监听 WebDAV 相关日志
adb logcat | grep --line-buffered -E "WebDAV|同步|RefreshEvent" | while read line; do
    # 高亮显示关键信息
    if echo "$line" | grep -q "成功"; then
        echo -e "\033[0;32m$line\033[0m"  # 绿色
    elif echo "$line" | grep -q "失败\|错误\|Error"; then
        echo -e "\033[0;31m$line\033[0m"  # 红色
    elif echo "$line" | grep -q "同步完成统计"; then
        echo -e "\033[0;33m$line\033[0m"  # 黄色
    elif echo "$line" | grep -q "=== WebDAV 同步状态 ==="; then
        echo -e "\033[0;36m$line\033[0m"  # 青色
    else
        echo "$line"
    fi
done
