# 项目维护脚本

正常 Android 构建使用 Gradle，不依赖 Python、Node 或本地 docs 素材。以下脚本用于维护资源及设计验证。

| 入口 | 用途 | 条件 |
| --- | --- | --- |
| `check_docs.py` | 检查本机文档链接与静态资源引用 | 标准库、本地 docs |
| `prepare_launcher_icon.py` | 校验 / 生成启动图标 | Pillow、Inkscape、`assets/branding/LOGO.svg` |
| `prepare_theme_avatars.py` | 校验 / 生成十二主题头像资源 | Pillow、本地 `docs/colors/` 原图 |
| `prepare_poetry_corpus.py` | 从固定资料版本生成诗文资源和来源记录 | 本地 `artifacts/poetry-source/` 输入 |
| `prepare_poetry_font.py` | 裁剪并重命名诗文字体，保留 OFL | fonttools、固定版本上游字体及许可 |
| `checks/` | 保留的历史浏览器验收源码 | 见该目录 README |
| `prepare_thinking_review.py`、`check_thinking_review.py`、`thinking_content.py` | 已取消题库的归档维护工具 | 本地 `docs/archive/thinking/`；检查页另需 Python Playwright |

可选 Python 工具环境：

```powershell
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements-dev.txt
```

依赖版本按本次使用环境记录。字体生成先验证固定输入摘要；重新下载时请使用脚本中的固定版本和文件名。`artifacts/` 的输入与依赖缓存已在清理中移除，不能将其视作随仓库分发的文件。正式诗文、字体和许可仍保存在 `app/src/main/`。

docs 整体作为本地资料忽略，包括品牌设计历史稿。最终 Logo 随仓库保存在 `assets/branding/LOGO.svg`，启动图标维护脚本从此读取。缺少其他设计原图 / 本地文档时，可直接构建 Android 应用，按需获取对应素材后再执行资源维护或设计检查。
