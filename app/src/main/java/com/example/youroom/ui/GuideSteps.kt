package com.example.youroom.ui

import com.example.youroom.domain.PlanningPolicy

internal enum class GuideStage(val chapter: Int, val page: Int, val target: String,
    val title: String, val instruction: String, val window: String = "root", val practice: Boolean = false) {
    INTRO(0,0,"", "先在练习页面试一试", "欢迎使用YOURoom！来看看有哪些功能以及如何操作吧"),
    NAV(0,0,"workspace_navigation", "三个页面", "教室页查询与标记空闲教室；规划页获取推荐的教室安排；设置页管理配置与偏好"),
    CAMPUS(0,0,"campus_swipe_region", "切换校区", "顶部区域左滑或右滑可切换校区", practice=true),
    SLOTS(0,0,"slot_grid", "选择节次", "点击可选中或取消，按住划过多个节次可批量选择，垃圾桶可清除选择", practice=true),
    INSPECT(0,0,"slot_grid", "长按查看时间", "长按节次可查看起止时间，按住后移动可查看其他节次；这种操作不会更改选择"),
    SEARCH(0,0,"room_search_frame", "搜索框", "根据输入查询匹配的教室，右侧叉号可清空输入"),
    FILTER(0,0,"extra_filters", "更多筛选", "点此选择教学楼与标签，筛选仅用于查看，不用于后续规划"),
    BUILDINGS(0,0,"filter_choices", "筛选教学楼和标签", "点击选中教学楼或标签，垃圾桶可清除选择", "filter"),
    FILTER_TAGS(0,0,"tag_editor", "添加和整理标签", "输入名称并添加标签，通过‘×’删除标签，长按标签可调整次序", "filter"),
    SWIPE(1,0,"room_swipe_01:2-201", "展开操作按钮", "在教室条目上左滑，展开收藏、标签、备注和排除四个按钮", practice=true),
    FAVORITE(1,0,"room_action_01:2-201_0", "收藏教室", "点星星收藏这间教室。收藏标识会出现在条目上，也能用于筛选和规划偏好", practice=true),
    TAGS(1,0,"room_tag_dialog", "为教室添加标签", "标签信息为个人标记，不是真实情况，垃圾桶和“+”按钮功能与筛选教室部分相同", "room-editor"),
    NOTE(1,0,"room_note_dialog", "添加备注", "点击输入备注，上限20字符，垃圾桶可清空内容", "room-editor"),
    EXCLUDE(1,0,"room_action_01:2-201_3", "排除与恢复", "排除会让该教室不参与查询和规划，再次点击可恢复"),
    MARKS(1,2,"settings_marks_list", "集中管理标记", "收藏、标签、备注和排除记录都能在这里管理。清除标记不等于删除教室", "sheet"),
    WHEELS(2,1,"planner_wheels", "设定开始和结束时间", "现在展示教室规划功能，在滚轮上设置自习的时间段，不少于30分钟"),
    ADD(2,1,"planner_add_period", "确认一个时段", "点击加号后确认添加一个时段，最多同时添加三个时段。现在添加11:00-12:00", practice=true),
    PERIOD(2,1,"planner_period_actions_0", "编辑、删除和保存", "点击已添加的时段可打开菜单，编辑会让时间回到滚轮上，删除会移除这段时间，保存可保存已有的全部时段为一个组合记录，此处暂时禁用这三个按钮", "period"),
    SAVE_TEMPLATE(2,1,"template_editor_content", "保存时段组合", "添加你的常用时段组合，方便后续一键导入", "sheet"),
    PAST(2,1,"planner_add_period", "如果目标时间早于现在", "假设现在是10:00，轮盘上开始时间早于现在，点击加号看看提示"),
    FROM_NOW(2,1,"planner_from_now", "从现在起", "点击“从现在起”把开始时间设为当前时间，此处假设是10:01，此操作不会修改结束时间", practice=true),
    DURATION(2,1,"planner_duration_buttons", "快捷修正结束时间", "结束早于开始时间或者时长不足30min时，可点击按钮快捷设置结束时间"),
    IMPORT(2,1,"planner_add_period", "长按加号导入组合", "加号还有长按操作。现在长按它，打开常用时段组合", practice=true),
    IMPORT_PICK(2,1,"template_import_guide", "选择组合", "点击想导入的时段，可选择跳过组合中不合法的时段", "sheet"),
    REPLACE(2,1,"template_replace_confirm", "替换已有时段", "已有时段时需要确认替换。只替换时段，不自动重新规划，也不清空期望教室", "alert"),
    EXPECT(2,1,"planner_expected", "期望教室", "书签按钮可选择临时想去的教室，但优先级低于收藏的教室。图钉按钮用于添加或取消期望教室，垃圾桶可清除全部选择", practice=true),
    FLEXIBLE(2,1,"planner_adjust", "宽松模式", "允许小幅调整时段来寻找更合适的安排：每段前后平移不超过${PlanningPolicy.shiftLimit}分钟，单段最多缩短${PlanningPolicy.lossLimit}分钟、多段合计${PlanningPolicy.multiLossLimit}分钟"),
    GENERATE(2,1,"planner_generate", "开始规划", "点击启动按钮，根据已添加的时段与当前偏好生成方案，度过充实的一天吧~", practice=true),
    STOP(2,1,"planner_generate", "终止计算", "计算中同一位置变为停止按钮，可终止本次计算。这里特意保留计算中状态，方便试用"),
    WAIT_CONTENT(2,1,"planner_leisure", "等待时也能看看别的", "计算时可点击右下角入口，看看内容或玩一会儿游戏，不会打断规划", practice=true),
    READER(2,1,"planner_utility_panel", "随手一翻", "这里会随机展示诗文，点击刷新按钮或左右滑动来切换", practice=true),
    DINO(2,1,"planner_game_switch", "Google Dino", "点击游戏图标，可以游玩经典的谷歌小恐龙", practice=true),
    WAIT_RESULT(2,1,"planner_utility_panel", "完成后再去看结果", "计算完成会在上方提示，不会打断游戏，准备好后点击“查看结果”", practice=true),
    RESULTS(2,1,"planner_alternatives", "查看规划结果", "这里显示当前导向与方案编号，点击展开所有方案"),
    ALTERNATIVES(2,1,"planner_drawer", "比较其他方案", "展开后可切换五种导向，横向滑动来查看其他方案"),
    HISTORY(2,1,"planner_history", "历史记录", "点击左下角查看当天计算过的历史记录，两个校区合计最多十条，次日自动清理", practice=true),
    HISTORY_REPLAY(2,1,"history_guide-current", "回放一条记录", "点击记录查看完整方案", practice=true),
    THEME(3,2,"settings_theme_belt", "切换配色", "进入设置页面，点击左右箭头或者滑动来切换配色。合适的色彩能带来愉悦、共鸣，也是一种自我表达哦", practice=true),
    DARK(3,2,"settings_dark_mode", "深色模式", "深色模式应用于整个应用，包括两个校区"),
    STRIP(3,2,"settings_strip_layout_group", "时段条样式", "可切换两种时段条样式，“等间距”更清爽，“等比例”更符合直觉"),
    DEFAULT_CAMPUS(3,2,"settings_default_campus_group", "默认校区", "选择进入时默认打开的校区，不会立即切换当前页面"),
    NUMBERS(3,2,"settings_strip_numbers", "节次编号", "开启后时段条显示节次数字，关闭则保持简洁"),
    PLANNING(3,2,"planning_default_direction_group", "规划偏好", "点击管理规划偏好，默认偏好影响优先展示的方案导向", "sheet"),
    TEMPLATES(3,2,"template_list", "管理常用时段", "点击管理常用时段组合", "sheet"),
    REPLAY(3,2,"settings_usage_guide", "随时重新查看", "以后在设置页点“使用指引”，可以重新查看或直接选择章节"),
    ABOUT(3,2,"settings_about", "关于与帮助", "这里有代码库、问题反馈链接和相关说明"),
    LOGOUT(3,2,"settings_logout", "退出登录", "退出登录仍保留个人使用数据和游戏最高分，规划历史仍会在次日清理"),
    CLEAR_CACHE(3,2,"settings_clear_cache", "清除全部本地数据", "清除账号密码及全部个人数据，包括规划历史和游戏最高分，需要登录账密校验，不可恢复，请谨慎选择"),
    FINISH(3,2,"", "可以开始使用了", "感谢使用本应用，希望能够帮助到您，祝您学习生活愉快顺利！");

    val spotlightTargets:List<String> get() = listOf(target).filter { it.isNotEmpty() } + when(this) {
        BUILDINGS -> listOf("reset_filters")
        IMPORT_PICK -> listOf("template_skip_invalid_option")
        else -> emptyList()
    }
}

internal val GuideChapters = listOf("查询教室", "管理教室", "规划自习", "设置与帮助")
