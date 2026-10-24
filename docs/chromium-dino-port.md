# Google Dino · Chromium 原生适配 · 1.41.4

## 来源

本轮检查当前 Chromium 官方源码，选择它的经典 Dino 资源与定义，替代 DinoCompose 教学示例中的图形；不是更换成另一个未经核对的第三方复刻，也不是将网页整体嵌入应用。

- 上游：[Chromium Dino 源码目录](https://chromium.googlesource.com/chromium/src/+/2e8c19f4f602c8756a42d6d5a5c5c37a69facfab/components/neterror/resources/dino_game/)。
- 固定提交：`2e8c19f4f602c8756a42d6d5a5c5c37a69facfab`（2026-10-24）；不是把开发主线称为某一 Chrome 稳定发行版。
- 官方 2x 精灵图：[200-offline-sprite.png](https://chromium.googlesource.com/chromium/src/+/2e8c19f4f602c8756a42d6d5a5c5c37a69facfab/components/neterror/resources/images/default_200_percent/offline/200-offline-sprite.png)，图像 blob `2270e89ae5e58e45305d6a8ac47df62d69fe0d57`。
- 原图 2446×194，PNG 4758 字节，不改图像内容、无损 Base64 编码后存于 `app/src/main/res/raw/chromium_dino_sprite.b64`。在游戏挂载时解码一次，不在帧循环中解码或联网下载。
- BSD 许可全文及来源 / 修改声明位于 `app/src/main/assets/licenses/chromium_dino/LICENSE.txt` 和 `NOTICE.txt`，随 APK 离线打包。名称用于识别原作，不表示 Google 认可、推广或提供本应用；不显示未使用的 Chrome 标志帧。

## 原生适配

- `ui/DinosaurArtwork.kt`：官方精灵坐标、像素字符、地面 / 云 / 飞鸟 / 仙人掌 / 失败角色与 GAME OVER / 重开图案；保留版权与 BSD 声明。
- `ui/DinosaurRunner.kt`：原生 Compose Canvas 渲染与独立状态，参考 `trex.ts`、`offline_sprite_definitions.ts`、`distance_meter.ts`、`horizon_line.ts`、`cloud.ts`、`obstacle.ts`、`game_over_panel.ts`。
- 这是基于官方资源和部分定义的原生适配，不声称完整移植浏览器 TypeScript 引擎或与 Chrome 一模一样的输入物理。保留 YOURoom 的点击跳跃、空格 / 上键、暂停与工作区内状态。
- 所有角色与障碍使用同一世界坐标；头、躯干、腿部和障碍采用官方分块碰撞框。成组仙人掌扩展中间碰撞框，不把独立的稀疏图案错误算成一个固定宽矩形。
- 跳跃以秒推进，参考原作初速度与重力；速度从原作 6px/frame 及移动端宽度修正开始，逐步增加至 13px/frame 上限。原生积分最大 1/120 秒小步，不随 Compose 重组频率加速；过长帧不追赶。
- 根据速度允许一至三株小 / 大仙人掌，后期加入飞鸟；飞鸟使用移动端可跳过 / 可从下方跑过的两个高度，不要求用户发现隐藏的下蹲操作。
- 官方两种地面图块连续滚动，包含起伏、凹坑与碎点；云使用原版轮廓，不再是实心圆云。角色和仙人掌以脚底为基准，脚底比远处地平线稍低，不额外添加写实 3D 阴影。
- 原图的 `#535353` 与白色通过绘制时的色彩矩阵映射到主题主色 / 面板底色；保留眼睛及镂空，不将全部不透明像素简单染成同一种颜色。像素贴图使用最近邻采样。

## 记分与交互

- 右上使用官方数字和 HI 字形，五位补零；以累计路程 × 0.025 换算得分，逢百短暂闪动，不播放声音。
- HI 为当前工作区内已结束轮次的最高分，仅内存保存。退出工作区 / 结束演示 / 进程重建后不保证恢复，不增加历史入库、奖励、解锁或局外成长。
- 失败显示官方失败姿态和 GAME OVER 精灵，中央绘制静态重开箭头帧，点击场地重开。失败后 350ms 内的连点不立即略过失败画面；不改变正常跳跃与暂停后的恢复速度。
- 游戏题头为 Google Dino，画布内不再重复“小恐龙”；左上只保留开始 / 暂停 / 继续图标。计分与 GAME OVER 在同一画布绘制，不新增状态行压缩游戏区域。
- 切页 / 关闭面板 / 后台暂停；计算完成仍仅更新场地外已预留的通知，不停止游戏、不抢焦点、不关闭面板。用户主动查看规划结果才暂停。

## 其他布局调整

- 历史 / 等待入口水平外边距 8dp，触控范围仍 48dp；选中时增加 36dp 深色底托与反色图标，历史外层回看继续点亮历史入口。
- 随手一翻标题左、类别右，类别仍为 `labelSmall`；标题改为 `titleLarge`，正文为 `bodyMedium`；技巧不再提供使用指引跳转，其他题目答案、诗文署名和换一则保留。

## 验证状态

按用户要求，未运行单元测试、设备测试、模拟器 / 真机演示或真实账号操作。仅进行来源、静态资源、源码核对与 `assembleDebug` 编译打包，最终构建成功；元数据为 `com.example.youroom / 1.41.4 / 147`，路径 `app/build/outputs/apk/debug/app-debug.apk`。

已核对 APK 包含精灵资源、BSD LICENSE 与 NOTICE；本地 Base64 解码后的 Git blob ID 与官方 `2270e89ae5e58e45305d6a8ac47df62d69fe0d57` 一致。仅保留现有 Gradle sourceSets / Tooltip 弃用警告。编译与静态核对不代表实际交互已验收；未操作手机、用户账号、教务服务或提交 / 推送 Git。
