#!/bin/bash
# XMBOX 项目清理脚本
# 清理构建产物和临时文件

echo "开始清理项目..."

# 清理构建目录
echo "清理构建目录..."
./gradlew clean

# 删除系统文件
echo "删除系统文件..."
find . -name ".DS_Store" -type f -delete
find . -name "Thumbs.db" -type f -delete
find . -name "*.swp" -type f -delete
find . -name "*~" -type f -delete

# 删除临时文件
echo "删除临时文件..."
find . -name "*.tmp" -type f -delete
find . -name "*.temp" -type f -delete

# 删除构建缓存
echo "删除构建缓存..."
rm -rf .gradle
rm -rf build
rm -rf app/build
rm -rf quickjs/build
rm -rf catvod/build
rm -rf */build

# 删除IDE配置文件
echo "删除IDE配置文件..."
rm -rf .vscode
rm -rf .vs
rm -rf .idea/workspace.xml
rm -rf .idea/tasks.xml

echo "✅ 清理完成！"
echo "项目已清理，可以提交到Git"
