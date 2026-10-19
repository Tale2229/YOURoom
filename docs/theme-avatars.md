# 十二主题头像正式接入

2026-10-19，版本 1.36 / 103。勃艮第采用 B「杯下铺布」，其他主题采用各自最终确认版本。只替换头像，不改变配色、主题 ID、排序、校区设置或登录数据。

| 主题 | 稳定 ID | 定稿原图版本 |
| --- | --- | --- |
| 砂岩 | sand | v1 |
| 松绿 | pine | v1 |
| 马蒂斯 | matisse | v1 |
| 敦煌 | dunhuang | v1 |
| 旧港来信 | old_port | v9：封泥波浪压印 |
| 蜜桃乌梅 | peach_plum | v2 |
| 勃艮第 | burgundy | v4：B，杯下铺布 |
| 莫兰迪 | morandi | v4 |
| 入骨相思 | red_bean | v4：平滑弧形背景分界 |
| 橙黄橘绿时 | autumn_citrus | v5 |
| 晨钟暮鼓 | morning_bell | v7：局部寺檐与挂钟、横向晨色天空 |
| 蒙德里安 | mondrian | v1 |

## 资源与显示

- 正式资源在 `app/src/main/res/drawable-nodpi/theme_avatar_<ID>.webp`，每张 384×384；十二张共 1,390,852 字节，约 1.33 MiB。
- 从定稿原图高质量缩小后无损编码为 WebP，完整保留构图，不增加裁切、着色或滤镜。原图与所有历史稿仍在本地 `docs/colors/<主题>/`，不被覆盖。该设计目录沿用既有 Git 忽略规则；正式资源、映射及说明可随项目正常跟踪。
- 维持 75dp 头像、10dp 圆角外框与原有交互。两校区、明暗模式共用同一张定稿图，外框和周围界面继续随主题变化。
- [资源映射](../app/src/main/java/com/example/youroom/ui/ThemeAvatarAssets.kt)集中管理十二张图。[头像组件](../app/src/main/java/com/example/youroom/ui/ThemeImpression.kt)解码并缓存后复用于中心头像、滑动条带、箭头切换与整页换色过渡，避免逐帧解码。

## 维护与验证

具备本地原图与 Pillow 时运行 `python scripts/prepare_theme_avatars.py`，检查全部资源与定稿缩小像素逐一匹配、原图未改变。`--write` 仅用于首次生成，不允许覆盖已有资源。正常 Android 构建不依赖 Python 或设计目录。

`ThemeAvatarAssetsTest` 检查十二主题映射；`ThemeAvatarUiTest` 在两校区、明暗模式下逐张验证实际显示内容和 75dp 尺寸。各主题原先针对旧矢量色块的断言改为定稿图片比对，周边界面配色断言保持不变。

本轮验证：572 项全量单元测试、26 项专项界面测试通过；其中头像测试包含 12 主题 × 2 校区 × 2 模式的 48 次显示比对。回归覆盖箭头连续选择、滑动惯性 / 停留 / 恢复、大字号、整页波浪末帧、明暗切换和保存恢复。已查看原生设置页截图，核对勃艮第 B、晨钟暮鼓和入骨相思的最终画面。普通与隔离 Debug APK 构建、十二项打包资源、正式包名 `com.example.youroom` 和版本 `1.36 / 103` 核对通过；未安装到用户手机，未访问学校接口，未执行全量设备测试或 Lint。
