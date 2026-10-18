# YOURoom · Logo 概念预览

本轮只探索视觉方向，未替换应用启动图标、页面图标或字标样式。

- A · 开口房间：俯视的房间轮廓，入口与短路径指向一个空位。
- B · Y 形路径：字母 Y 与房间共用结构，强调 YOU 和规划。
- C · 课桌空位：俯视课桌、座位与开放边界，强调安静的自习空间。

三图由内置 imagegen 按各自独立提示生成，统一使用暖纸底、深卡其和单色几何轮廓；图片包含 YOURoom 字标，属于概念草案，不是最终矢量交付。精确提示见 `prompts.md`。

在 `app/build/design-preview/YOURoom-logo-options.html` 中并排查看，点击图片可以放大。原始图片保存在本目录 `logo-a.png`、`logo-b.png`、`logo-c.png`，不依赖生成工具的本机缓存。

## 命名范围

项目名、界面文案、Kotlin 类 / 函数 / 引用与对应源码文件、Android Theme 和 Room schema 目录统一使用 YOURoom。小驼峰属性使用 `yourRoomSettings`。

Android 包名 `com.example.youroom`、小写资源名、Gradle 测试隔离参数、数据库文件、DataStore / 会话文件、密钥别名保持原值。它们属于平台规范名称或持久化标识，不能仅因品牌大小写调整而让用户数据失联。数据库版本及 schema identity 未变化。

未移动本机工作目录，未修改远程 GitHub 仓库或 Git 配置；项目 / 反馈链接只统一品牌部分的字母大小写。
