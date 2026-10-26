# 历史浏览器检查脚本

2026-10-26 从 `app/build/` 救出 29 个手写脚本，避免构建清理将源码一起删除。这里保存的是各次设计阶段的检查，部分断言绑定旧版页面，不作为当前 Android 应用的完整回归入口。

- 原始脚本备份及迁出摘要在本地 `docs/archive/ui-experiments/`
- 已修正关于、弹窗、主页面、指引目录的 8 组旧 docs 入口；历史 HTML 入口改到本地 `docs/archive/ui-experiments/`
- 仓库根目录由 `paths.cjs` 统一定位，生成输出写入忽略目录 `artifacts/qa/`，不再写回脚本目录或 `app/build/`
- 依赖本地 docs 的检查须有对应素材；docs 按项目约定不分发，branding 的既有跟踪文件暂保留

按需从项目根目录运行，例如：

```powershell
node scripts/checks/check-main-pages.cjs
```

需要可用的 Node.js 及 Chrome / Edge。可用 `YOUROOM_BROWSER` 指定浏览器路径。使用 Playwright 的几个脚本需要本地安装 Node Playwright：

```powershell
npm install --no-save --no-package-lock playwright
```

`verify-close-options.cjs` 和 `check-proportional-time-strips.mjs` 沿用其历史调试端口连接方式，需先启动对应的专用预览页。旧版配色 / 布局断言可能需要按当前设计调整；本轮只验证全部脚本语法与迁移路径，没有批量启动历史检查或浏览器会话。
