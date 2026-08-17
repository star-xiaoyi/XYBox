#!/bin/bash

# WebP颜色修改脚本
# 需要先安装 ImageMagick: brew install imagemagick

echo "=== WebP 颜色修改工具 ==="
echo ""

# 检查ImageMagick是否安装
if ! command -v convert &> /dev/null; then
    echo "❌ 未检测到 ImageMagick"
    echo "请先安装: brew install imagemagick"
    exit 1
fi

# 示例：修改颜色（色相旋转）
# 参数说明：
# -modulate brightness,saturation,hue
# 例如：-modulate 100,100,150 (色相旋转150度)

SOURCE_DIR="/Users/chen/Desktop/XMBOX/app/src/main/res/mipmap-hdpi"
OUTPUT_DIR="/Users/chen/Desktop/XMBOX/app/src/main/res/mipmap-hdpi/modified"

# 创建输出目录
mkdir -p "$OUTPUT_DIR"

echo "处理目录: $SOURCE_DIR"
echo "输出目录: $OUTPUT_DIR"
echo ""

# 示例1: 色相旋转（改变整体颜色）
echo "方式1: 色相旋转"
echo "  convert input.webp -modulate 100,100,180 output.webp  # 色相旋转180度"
echo ""

# 示例2: 颜色替换
echo "方式2: 颜色替换"
echo "  convert input.webp -fuzz 20% -fill '#新颜色' -opaque '#旧颜色' output.webp"
echo ""

# 示例3: 调整色调/饱和度/亮度
echo "方式3: HSL调整"
echo "  convert input.webp -modulate brightness,saturation,hue output.webp"
echo "  brightness: 亮度 (100=不变)"
echo "  saturation: 饱和度 (100=不变, 0=灰度)"
echo "  hue: 色相 (100=不变)"
echo ""

# 交互式处理
read -p "请选择处理方式 (1/2/3): " choice

case $choice in
    1)
        read -p "输入色相旋转角度 (0-200, 100=不变): " hue
        for file in "$SOURCE_DIR"/*.webp; do
            filename=$(basename "$file")
            echo "处理: $filename (色相旋转 ${hue}度)"
            convert "$file" -modulate 100,100,$hue "$OUTPUT_DIR/$filename"
        done
        ;;
    2)
        read -p "输入要替换的颜色 (HEX, 例如 #FF0000): " old_color
        read -p "输入新颜色 (HEX, 例如 #00FF00): " new_color
        for file in "$SOURCE_DIR"/*.webp; do
            filename=$(basename "$file")
            echo "处理: $filename ($old_color -> $new_color)"
            convert "$file" -fuzz 20% -fill "$new_color" -opaque "$old_color" "$OUTPUT_DIR/$filename"
        done
        ;;
    3)
        read -p "亮度 (100=不变): " brightness
        read -p "饱和度 (100=不变, 0=灰度): " saturation
        read -p "色相 (100=不变): " hue
        for file in "$SOURCE_DIR"/*.webp; do
            filename=$(basename "$file")
            echo "处理: $filename (亮度:$brightness 饱和度:$saturation 色相:$hue)"
            convert "$file" -modulate $brightness,$saturation,$hue "$OUTPUT_DIR/$filename"
        done
        ;;
    *)
        echo "无效选择"
        exit 1
        ;;
esac

echo ""
echo "✅ 处理完成！"
echo "处理后的文件保存在: $OUTPUT_DIR"

