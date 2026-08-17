# 贡献指南

## 项目结构

这是一个Android应用项目，主要使用Java开发。

### 语言组成
- **Java (78.4%)** - 主要应用代码
- **JavaScript (9.9%)** - WebView内嵌脚本和爬虫引擎
- **CSS (7.3%)** - WebView样式文件
- **GLSL (2.5%)** - Media3视频渲染着色器
- **Shell (1.1%)** - 构建和部署脚本
- **HTML (0.8%)** - WebView页面

### 目录说明

```
XMBOX/
├── app/                    # 主应用模块
│   ├── src/main/          # 通用代码
│   ├── src/leanback/      # 电视版UI代码
│   └── src/mobile/        # 手机版UI代码
├── catvod/                # 爬虫核心库
├── quickjs/               # JavaScript引擎
├── thunder/               # 迅雷下载模块
├── forcetech/             # P2P模块
├── jianpian/              # 减片模块
├── tvbus/                 # TVBus模块
├── zlive/                 # 直播模块
└── docs/                  # 文档

```

## 代码规范

### Java代码
- 遵循Android开发规范
- 使用驼峰命名法
- 类名首字母大写
- 方法和变量名首字母小写
- 常量全大写，用下划线分隔

### 资源文件
- JavaScript/CSS/HTML位于 `app/src/main/assets/`
- 这些文件用于WebView解析和内容抓取，不可删除

### GLSL着色器
- 由Media3库提供，用于视频渲染
- 自动生成，不需要手动修改

## 清理项目

运行清理脚本：
```bash
./clean_project.sh
```

这将清理：
- 构建产物（build目录）
- 临时文件
- 系统文件（.DS_Store等）
- IDE配置文件

## 提交代码

1. 清理项目：`./clean_project.sh`
2. 查看改动：`git status`
3. 添加文件：`git add .`
4. 提交代码：`git commit -m "描述"`
5. 推送代码：`git push`

## 注意事项

1. **不要删除assets目录**中的JS/CSS/HTML文件，这些是应用必需的
2. **不要删除GLSL文件**，这些是视频播放器需要的
3. 提交前运行 `./gradlew clean` 清理构建产物
4. 确保新增的临时文件已添加到 `.gitignore`
