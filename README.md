# YouRoom

北邮空闲教室自习筛选工具，原生 Android / Kotlin / Jetpack Compose。

已接入西土城（`campusId=01`）与沙河（`campusId=04`）的空闲教室查询，复用现有教务登录协议。不需要微信、自建后端或浏览器 Cookie。沙河参数来自[学校发布的课堂页面代码](https://jwglweixin.bupt.edu.cn/sjd/static/js/23.3486c5a076a22b47d095.1741852738390.js)，真实账号列表比对仍须在手机上验收。

## 1.2 分步开发

在 `design/campus-refresh` 分支按四个步骤分别验证、提交，不推送远程：

1. 双校区查询：缓存、请求状态与筛选设置分别管理。旧筛选设置只迁入西土城；数据库结构和既有收藏 ID 不变。快速切换会取消旧请求，过期返回不能覆盖当前校区。
2. 多节次选择：点击切换单个格子，直接划过可统一增选或取消；保留其他选择，支持不连续时段。候选教室必须在所有选中节次都空闲，不检查未选中的间隔。手势取消会恢复原选择，全部清空后提示选择时段。

## 1.1 界面更新

修改前的可用版保存为提交 `a0c7c06`、标签 `v1.0.0-baseline`；1.1 界面版为 `74d4e54`。这两个历史版本已推送 GitHub，本轮 1.2 的新增提交只保留在本机。

- 西土城墨蓝 `#263D59`，沙河松绿 `#315C4E`；共用暖白底色、深灰文字和细分隔线。
- 常规控件 4dp 小圆角，详情面板顶部 8dp；文本导航与直线选中标记替代胶囊式背景。
- 节次使用自适应矩形网格，点一下选起点，再点一下选终点，第三次重新开始；反向选择也自动规范为连续区间。
- 教室列表、详情和个人偏好统一使用开放式分区，去掉多余装饰。
- 校区色过渡、导航短线、节次选中反馈和列表重排采用简短动画。

1.1 曾将实时沙河标记为待接入；1.2 已接入 `04` 查询。演示模式依旧使用明确标注的虚构安排，沙河演示教室命名为“演示楼”，不保存到真实缓存或收藏。

主题通过 [Compose 自定义主题机制](https://developer.android.com/develop/ui/compose/designsystems/custom) 统一管理颜色、排版和形状，两校区共用页面结构。

包名、数据库版本、Token 密钥标识都保持不变。新 APK 的 `versionCode=2`、`versionName=1.1`，正常覆盖安装不需要删除旧版，也不会主动清除收藏。

## 运行

1. 在 Android Studio 中打开 `E:\Android\YouRoom`，等待 Gradle Sync 完成。
2. Gradle JDK 使用 Android Studio 自带的 JBR / JDK 21，不要使用系统中旧的 JDK 8。
3. 选择 Android 8.0（API 26）或更高版本的手机 / 模拟器，运行 `app`。
4. 可先点“先看看演示”体验筛选和个人标记；演示空闲安排是虚构的，标记不写入真实数据库。
5. 实际查询时，在能够访问教务服务的网络下输入自己的教务账号和密码。

调试安装包：`app/build/outputs/apk/debug/app-debug.apk`。

校外访问需要手机上的校园 VPN 确实接管此 App 的流量。网页登录式 WebVPN 往往只代理浏览器页面，不能假定它对原生 App 生效。应用不自行建立 VPN，也不关闭 HTTPS 证书校验。

## 已实现

- 教务账号登录；保存加密 Token，失效后提示重新登录。
- 查询今日空闲教室；解析节次、时间、楼宇和标注容量。
- 可选择不连续节次，对所有选定节次的教室列表取交集，并分段显示所选时间。
- 按楼宇、教室编号或备注、最低容量、收藏、个人安静 / 插座标记筛选。
- 收藏优先排序；支持排除教室及个人备注。
- 查询快照与个人偏好本地持久化；网络失败保留旧快照。
- 显示查询日、获取时间和缓存状态；跨日缓存不能作为今日空闲结果。
- 无账号演示模式；演示标记与真实数据隔离。
- 浅色 / 深色主题、键盘避让、可滚动表单和原生自适应应用图标。

当前按**教学节次**选择时段，而不是任意分钟区间。跨节次取交集不代表课间、午休期间也可用。容量不是实时余座，“课表空闲”也不能保证教室开门或现场无人。

## 架构

```text
Compose 页面
    ↓ 用户操作 / ↑ StateFlow 状态
YouRoomViewModel
    ├─ ClassroomFilter：纯 Kotlin 筛选、交集与排序
    ├─ SettingsStore：DataStore 保存筛选偏好
    └─ ClassroomRepository
         ├─ SchoolApi：OkHttp / HTTPS / Token，无 Cookie
         ├─ YouRoomDao：Room 保存快照、收藏、标签和备注
         └─ SecureSessionStore：Android Keystore + AES-GCM
```

使用单 `app` 模块、手动依赖注入和单向状态流，避免为首版引入多模块或远程服务的额外维护成本。

主要文件位于 `app/src/main/java/com/example/youroom/`：

| 位置 | 职责 |
| --- | --- |
| `data/SchoolApi.kt` | 登录、查询及 HTTP / 业务错误映射 |
| `data/LegacyPasswordEncoder.kt` | 兼容学校现有密码编码协议 |
| `data/SchoolJson.kt` | 严格解析和验证返回数据 |
| `data/ClassroomRepository.kt` | 会话、网络、缓存和跨午夜校验 |
| `data/local/` | Room、DataStore、Keystore |
| `domain/` | 数据模型、跨节次交集、个人偏好筛选 |
| `ui/YouRoomViewModel.kt` | 页面状态、登录与刷新、演示隔离 |
| `ui/` | 登录、找教室、个人偏好、教室详情 |

数据库 schema 已导出到 `app/schemas/`。以后改变表结构时应增加数据库版本并提供迁移，不应通过破坏性迁移删除用户收藏。

## 学校接口约定

基础地址：`https://jwglweixin.bupt.edu.cn/bjyddx/`。

| 操作 | 方法 / 路径 | 参数或认证 |
| --- | --- | --- |
| 登录 | `POST /login`，空请求体 | Query：`userNo`、编码后的 `pwd`、`encode=1`、空 `captchaData`、空 `codeVal` |
| 教室 | `POST /todayClassrooms`，空请求体 | Query：`campusId=01`（西土城）或 `04`（沙河）；请求头 `Token` |

登录成功读取 `data.token`；查询成功需要业务码 `code=1`。学校会用 **HTTP 500 + 业务码 401** 表示未授权，不能把所有 HTTP 500 都当成 Token 失效。

响应行包含 `NODENAME`、`NODETIME` 和 `CLASSROOMS`。解析器保留 `未来学习大楼-202-203` 等复合教室编号，只将末尾数字括号解析为容量。

接口没有日期字段。App 保存的是请求发生时的北京时间日期与获取时间，**不能据此证明学校已经刷新了当天数据**，也不会把缓存重新标记成今天。一次请求跨越北京时间午夜会被拒绝缓存，要求重新刷新。

`Origin` / `Referer` 沿用此前成功请求的值，其必要性未单独验证。如果学校增加验证码、调整认证或更改字段，首版会提示错误，不尝试绕过。

## 安全边界

- 不在源码、配置或测试中保存真实账号、密码、Token、Cookie。
- 密码仅参与本次登录，不写入 SharedPreferences、数据库或页面恢复状态；提交后清空输入框。
- 学校前端的固定 AES-ECB 密码编码只是协议兼容，**不是安全的密码存储方案**；通信安全依赖正常校验证书的 HTTPS。
- 登录协议把编码密码放在 URL Query 中。因此不要添加网络日志拦截器，也不要分享完整登录 URL、HAR 或登录响应。
- Token 用 Android Keystore 密钥进行 AES-GCM 加密，密文存入应用私有 SharedPreferences；无法解密时要求重新登录，没有明文回退。
- Token 文件从 Android 云备份和设备迁移中排除。系统备份可包含教室缓存和个人偏好；App 自身不上传这些数据到第三方服务器。
- 退出登录清除本机 Token，但保留收藏与备注；当前未调用学校的服务端 Token 撤销接口。
- 使用系统证书信任配置，不允许明文 HTTP，不自动追随携带凭据的重定向。

## 验证

2026-10-12 的 1.1 界面版：49 项单元测试通过；APK、界面测试 APK 和 `lintDebug` 构建成功，Lint 0 错误、30 警告。新增测试覆盖节次点选、双校区数据隔离及两种主题在浅色 / 深色模式下的文字对比度。本轮使用已缓存的依赖完成离线构建，没有新增库或变更学校登录协议。界面测试已编译，尚未在设备上执行。

2026-10-11 本机验证结果：34 项单元测试通过；`assembleDebug`、`assembleDebugAndroidTest` 和 `lintDebug` 均成功。Lint 为 0 错误、30 警告，涉及版本更新提示、模板未使用资源、SharedPreferences 风格建议，以及学校协议必须兼容的 AES-ECB。没有全局禁用检查。

Android Studio 中可以运行 `app/src/test` 的单元测试。使用 JDK 21 的终端也可执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

本机系统默认 Java 为旧版本时，可直接使用 Android Studio 的 Java，不必修改全局环境变量：

```powershell
& 'D:\develop\Android Studio\jbr\bin\java.exe' -classpath 'gradle\wrapper\gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

若 Gradle 下载依赖出现 TLS / 网络错误，先确认 Android Studio 的代理配置与当前网络。开发过程中只在构建命令中临时使用了本机已有的代理，没有改动项目仓库地址或写入固定代理配置。

测试覆盖：密码编码已知样例及特殊字符、无 Cookie / Token 请求、HTTP 与业务码错误、JSON 格式异常、复合编号、教室交集、排除和个人标签、跨日状态、午夜查询、缓存保护以及退出时保留个人偏好。网络测试只连接本机 MockWebServer，使用虚构凭据。

`app/src/androidTest/.../YouRoomUiTest.kt` 提供演示收藏、节次范围选择、校区切换的界面测试，需要连接设备后执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

上一版已经由用户确认可用。本轮开发环境没有连接 Android 设备，新界面的设备端测试仍待执行，不能将编译或模拟服务测试等同于真机验证。Android Studio Preview 已提供西土城、小屏、深色和沙河演示四种配置。

首次真机验收建议：

1. 演示模式中跨节次筛选、收藏、排除和备注正常；结束演示后真实偏好没有变化。
2. 实际登录后，与官方页面同一校区、同一节次的完整列表进行比对。
3. 切换多个节次，确认结果为每个节次均空闲的教室，而不是并集。
4. 收藏 / 备注后重启 App，确认持久化；退出并重新登录后仍保留。
5. 断网刷新，旧快照仍保留并展示错误；过期 Token 提示重新登录。
6. 第二天打开，前一日缓存不参与今日筛选。

后续优先项：真机联调、验证沙河校区参数、验证正常教学日数据更新规律，再考虑任意时间区间、地图或教室属性扩展。
