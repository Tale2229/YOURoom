# DinoCompose 移植记录 · 1.41.1

此文件保留 1.41.1 的历史设计记录。1.41.4 起运行中的游戏已替换为 [Chromium 官方资源的原生适配](chromium-dino-port.md)，不再加载此处的旧矢量路径；旧版许可与来源记录保留备查，不表示当前仍使用这些图形。

## 来源与范围

- 项目：[wajahatkarim3/DinoCompose](https://github.com/wajahatkarim3/DinoCompose)，作者 Wajahat Karim。
- 固定提交：`10ee4069d57c3c15c47161fcf88a07107f6e83c6`，不在构建或运行时拉取上游。
- 复用文件：[AssetPaths.kt](https://github.com/wajahatkarim3/DinoCompose/blob/10ee4069d57c3c15c47161fcf88a07107f6e83c6/app/src/main/java/com/wajahatkarim3/dino/compose/AssetPaths.kt) 中的两帧恐龙、仙人掌与云朵路径。
- 许可：[Apache-2.0](https://github.com/wajahatkarim3/DinoCompose/blob/10ee4069d57c3c15c47161fcf88a07107f6e83c6/LICENSE)。许可正文及作者 / 修改声明分别存于 `app/src/main/assets/licenses/dinocompose/LICENSE.txt` 和 `NOTICE.txt`，标准 Android assets 随 APK 分发，无需联网。
- 不声明这些图形属于 Chrome 官方授权素材，也不代表 Google 认可。上游所查看文件未提供图形的独立原始来源记录；本次按其仓库标示的许可保留来源，后续如发现另有权利声明，应补充核对及处理。

## 实现边界

- `ui/DinosaurArtwork.kt` 保留上游路径数据，注明作者、提交、许可及本地修改；将路径原点归零、解析结果归当前挂载实例所有，不沿用全局可变路径或 Android Matrix 缩放。
- `ui/DinosaurRunner.kt` 继续维护 YOURoom 的世界坐标、时间循环与状态。每帧最多推进 40ms，再分成不超过 1/120 秒的物理小步；按秒计算重力、加速度和障碍速度，不依赖 Composable 重组次数。过长帧不追赶，以免卡顿后突然碰撞。
- 以游戏面板实际尺寸计算缩放，不使用整屏宽度或固定 500px 地面；绘制与碰撞坐标一起缩放。短面板缩小整幅场景以保留完整跳跃弧线，不改变周围规划布局。
- 恐龙地面跑动切换两帧，跳跃固定一帧；云朵慢速滚动，地面按累计路程移动。保留渐进加速与飞鸟，后期适当出现双株仙人掌。
- 仙人掌路径的实际宽高比、实例间距同时用于绘制和判定；恐龙使用头、躯干与腿部的内缩判定区域，不把留白尾部算作满矩形。
- 游戏只显示本局分数，不移植上游最高分、调试边界开关、Activity、Material 2 主题、旧 Gradle 配置或旧依赖，不增加持久成长或外部服务。
- 继续复用 `PlannerUtilityState`、生命周期观察与面板暂停：切页 / 切面板 / 后台暂停，用户主动继续才恢复；继续时不改跳跃速度。计算结果仅更新既有固定提示栏，不重置本局、不切回结果、不抢焦点。

## 验证状态

按用户要求，未运行单元测试、设备测试、模拟器或真机演示。仅进行源码核对与 `assembleDebug` 编译打包，构建成功；已核对输出元数据为 `com.example.youroom / 1.41.1 / 144`，APK 内包含完整 LICENSE.txt 与 NOTICE.txt。编译不代表交互效果已验收。

安装包：`app/build/outputs/apk/debug/app-debug.apk`。仅有既存 Tooltip 定位 API 弃用警告。未操作用户手机、账号、教务服务或历史数据，未提交或推送 Git。
