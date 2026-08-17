#!/bin/bash

echo "=== 替换Spider Jar文件 ==="

# 删除旧的fm.jar
echo "删除旧的fm.jar..."
rm -f /Users/chen/Desktop/XMBOX/app/src/main/assets/jar/fm.jar

# 复制新的custom_spider.jar
echo "复制custom_spider.jar..."
cp /Users/chen/Desktop/custom_spider.jar /Users/chen/Desktop/XMBOX/app/src/main/assets/jar/custom_spider.jar

# 验证
echo ""
echo "=== 验证结果 ==="
ls -lh /Users/chen/Desktop/XMBOX/app/src/main/assets/jar/
echo ""
md5 /Users/chen/Desktop/XMBOX/app/src/main/assets/jar/custom_spider.jar

echo ""
echo "=== 清理并重新构建 ==="
cd /Users/chen/Desktop/XMBOX
./gradlew clean assembleMobileArm64_v8aDebug

echo ""
echo "=== 完成！ ==="

