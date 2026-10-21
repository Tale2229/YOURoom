# 启动图标试用记录

## 当前：g66.svg · 透明底 · 1.37.3 / 112

2026-10-21，按用户要求将 [g66.svg](g66.svg) 接入启动图标。40 条 path 的坐标、顺序、原填色与 fill-opacity 原样转换；其中一条 `fill:none` 路径保留为透明路径，浅青色细节保留 `0.841346` 不透明度，微小白色高光属于原图细节而不是背景。

沿用普通透明矢量入口，避免自适应图标强制黑底。原 108dp 前景与 72dp 启动矢量均最长边 60dp 等比居中；十张兼容 WebP 同步更新，留白不填白。备用单色蒙版按原图 alpha 生成，当前启动入口不提供自适应单色主题图层。部分桌面仍可能自行补底。

维护脚本 `scripts/prepare_launcher_icon.py` 当前校验 g66，包含路径、颜色、不透明度、矢量位置关系、安全区与十张兼容图的源图渲染比对。预览在 `app/build/launcher-verification/launcher-masks.png`；原生渲染文件以 `launcher-g66-` 开头。原 SVG 不改动，历史稿均保留。

g66 原 SVG SHA-256：`38D4B6BF2D1088AB734030DEB2103A0A43E0F0A5A22F9DDE9A67E498C196C848`。

### 验证（g66）

单元测试任务、普通 / 隔离 Debug 构建及 5 项图标 / 品牌设备测试通过；核对了原色、半透明浅青细节、普通 / 圆形入口、蓝色 / 米色背景透出及备用单色轮廓。矢量 / 源路径、fill-opacity、安全区及十张 WebP 校验通过，已查看原生渲染与棋盘格预览。测试仅安装至本次启动的模拟器，未操作用户手机；原 SVG 哈希未变，未运行全量设备测试或 Lint，未提交或推送。

## 此前：g101.svg · 透明底 · 1.37.2 / 111

2026-10-20，使用用户指定的 [g101.svg](g101.svg)，不重绘、不修改源文件。8 条原始路径及其黑色 / #808080 填色逐条保留，最长边 60dp 等比居中于 108dp 图层，完整处于 66dp 直径安全区内。

原生测试发现，仅把 `youroom_background.xml` 设为透明不够：Android 的 `AdaptiveIconDrawable` 合成时先填黑，透明处会变成黑底。因此普通 / 圆形入口均改用 `youroom_launcher.xml` 普通透明矢量，而非 adaptive-icon；其 72dp 视口与原自适应可见区域对应，保留相同图案大小。十张密度 WebP 也同步去底。

顶面空白及四周留白均保持 alpha 透明，圆形兼容裁切只乘以蒙版，不替换原始 alpha。原 108dp 前景与更新后的单色蒙版作为可复用资源保留，但当前启动入口不暴露自适应单色主题图层。部分桌面仍可能自行添加底板，不能保证所有桌面都直接透出壁纸。

平台实现参考：[AOSP AdaptiveIconDrawable.draw](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/graphics/java/android/graphics/drawable/AdaptiveIconDrawable.java)。

`scripts/prepare_launcher_icon.py` 当前校验 g101；`--write` 从只读 SVG 生成备用单色蒙版和兼容 WebP，检查两份矢量的路径、填色、缩放、安全区与透明留白。透明棋盘格预览位于 `app/build/launcher-verification/launcher-masks.png`；原生渲染文件以 `launcher-g101-` 开头。

g101 原 SVG SHA-256：`0B03EA0BABC9A4D35AA4FDB5F8F7199A60765CF0B3A85B1CFA62104320758819`。

### 验证（g101）

584 项单元测试、5 项图标 / 品牌设备测试通过；包括实际安装入口、黑灰原色、透明背景与顶面镂空，以及蓝色 / 米色底衬透出检查。源路径 / 填色、两份矢量位置关系、安全区、十张透明 WebP 校验通过，已查看原生有色衬底与棋盘格预览。普通 / 隔离 Debug 构建通过，最终 APK 核对为 `com.example.youroom`、`1.37.2 / 111`。原 SVG 哈希未变，仅安装到自建模拟器，未操作用户手机、未访问学校接口，未运行全量设备测试或 Lint。未提交或推送。

## 此前：g100.svg · 1.37.1 / 110

2026-10-20，按用户要求换用 [g100.svg](g100.svg)。保留 22 条原始 path 的顺序、坐标和填色，不重绘、不修改原 SVG。形状比 g99 更高，改为最长边 60dp 等比缩放，居中于 108dp 画布：宽约 51.96dp、高 60dp，所有细节仍在 66dp 直径安全区内。白底、应用名、包名及应用内界面不变。

普通 / 圆形入口仍使用 `youroom_foreground.xml`。原图包含白色遮盖路径，所以系统主题图标改用单独的透明 `drawable-nodpi/youroom_monochrome.png`：非白形状统一为实心黑，白色遮盖转为镂空，避免主题着色后补实原本的空隙。十张密度 WebP 同步由源 SVG 渲染生成。

`scripts/prepare_launcher_icon.py` 当前校验 g100；`--write` 重新生成主题蒙版与十张密度 WebP，两个源 SVG 均只读。当前 Android 原生预览文件为 `launcher-g100-adaptive.png`、`launcher-g100-48px.png`、`launcher-g100-monochrome.png`，位于 `app/build/launcher-verification/`。

g100 原 SVG SHA-256：`894BD3F3E614A1348B8CBEB00B6636B8BD66256D76807B1BD45BDAE2B9F20459`。

### 验证（g100）

584 项单元测试、4 项图标 / 品牌设备测试全部通过。核对 22 条原路径与填色、66dp 安全区、十张 WebP、普通 / 圆形入口，以及主题蒙版的白色镂空。已查看多形状裁切与 Android 原生渲染预览。普通 / 隔离 Debug 构建通过，正式包为 `com.example.youroom`、`1.37.1 / 110`；两份原 SVG 的哈希均未变。仅在本次启动的模拟器安装隔离包，未操作用户手机或访问学校接口，未运行全量设备测试或 Lint。未提交或推送。

## 此前：g99.svg · 1.37 / 109

2026-10-20，版本 `1.37 / 109`。按用户要求把 [g99.svg](g99.svg) 设为应用启动图标，不使用生图模型、不重绘、不改原始 SVG。

### 资源（g99 历史状态）

- `app/src/main/res/drawable/youroom_foreground.xml`：逐条保留 SVG 的 10 个 path、顺序和原填色，采用均匀缩放与原始平移转换为 VectorDrawable。图案宽 60dp，高约 58.15dp，居中于 108dp 画布，完整落在 66dp 直径安全区内。
- `youroom_background.xml`：纯白背景，衬托原图的黑色、#333333、#666666、#999999 层次；透明镂空不填成其他颜色。
- `mipmap-anydpi/ic_launcher.xml` 与 `ic_launcher_round.xml` 保持原引用，两种入口均使用新前景；monochrome 沿用同一透明轮廓。若启用系统主题图标，系统可能统一着色，原灰阶不保证保留。
- mdpi / hdpi / xhdpi / xxhdpi / xxxhdpi 的普通和圆形 WebP 同步更新，共十张。原图仍在 SVG 中，其他历史设计稿不变。
- Android 包名、应用名、主题头像、应用内界面与用户数据不变；只替换启动图标。

原 SVG SHA-256：`A58A295708DDEAFC19D6DEE86D05CC4CA44EEF2D90169EAEA62CC17FA985C206`。

### 维护（g99 历史状态）

使用本机 Inkscape 与 Pillow 时运行 `python scripts/prepare_launcher_icon.py` 校验源 path / 填色、安全区、adaptive 引用，以及十张 WebP 与源图渲染是否匹配。`--write` 仅重新生成这十张启动图标，不写 SVG；常规 Android 构建不依赖这些工具。

脚本预览保存在 `app/build/launcher-verification/launcher-masks.png`；Android 原生渲染预览为同目录 `launcher-g99-adaptive.png`、`launcher-g99-48px.png`。构建目录未纳入 Git。

安全区与主题图标参考：[Android Developers — Adaptive icons](https://developer.android.google.cn/develop/ui/compose/system/icon_design_adaptive?hl=en)。

### 验证（g99）

584 项单元测试、4 项图标 / 品牌专项设备测试通过。核对 SVG 十个路径及填色、66dp 安全区、实际安装包的普通 / 圆形入口、白底、单色图层、48px 与 256px 原生渲染。查看多种裁切预览及模拟器桌面显示。

普通 / 隔离 Debug 构建通过，正式 APK 核对为 `com.example.youroom`、`1.37 / 109`。仅在本次启动的模拟器安装隔离测试包，未操作用户手机，未访问学校接口，未运行全量设备测试或 Lint。未提交或推送。
