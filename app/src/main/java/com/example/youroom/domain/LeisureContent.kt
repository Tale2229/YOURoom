package com.example.youroom.domain

import kotlin.random.Random

enum class LeisureKind(val title: String) { TIP("使用小技巧"), VERSE("诗文一隅"), QUESTION("想一想"), THEME("主题小札"), CALM("轻轻放空") }
data class LeisureItem(val id: String, val kind: LeisureKind, val title: String, val body: String,
    val source: String = "YOURoom", val answer: String? = null, val theme: CampusTone? = null)

/** Curated offline seeds. Add content here; no network, generated attribution or lyrics. */
object LeisureContent {
    private val seeds = listOf(
        LeisureItem("tip-swipe", LeisureKind.TIP, "把四个操作藏在左边", "在教室条目上左滑，可以收藏、添加标签、写备注或排除教室。收藏和标签都是你的个人标记。"),
        LeisureItem("tip-template", LeisureKind.TIP, "常用时间，不必重拨", "长按规划滚轮旁的加号，可以导入常用时段组合。组合可以在设置中的“规划偏好”里管理。"),
        LeisureItem("tip-live", LeisureKind.TIP, "空闲，不等于空座", "空闲信息来自学校教务，表示课表中未安排课程，并不是教室里空座位的实时数量。"),
        LeisureItem("verse-1", LeisureKind.VERSE, "且看眼前", "一年好景君须记，最是橙黄橘绿时。", "苏轼《赠刘景文》"),
        LeisureItem("verse-2", LeisureKind.VERSE, "慢慢抵达", "不积跬步，无以至千里；不积小流，无以成江海。", "《荀子·劝学》"),
        LeisureItem("verse-3", LeisureKind.VERSE, "留一点余地", "行到水穷处，坐看云起时。", "王维《终南别业》"),
        LeisureItem("question-1", LeisureKind.QUESTION, "三盏灯", "有三盏灯，开始全灭。每次必须切换其中两盏的亮灭，能让三盏都亮起来吗？", answer = "不能。每次切换两盏，亮灯数量的奇偶性不变；从偶数盏出发，无法到达三盏。"),
        LeisureItem("question-2", LeisureKind.QUESTION, "时钟的小夹角", "在 3:15，时针与分针之间较小的夹角是多少度？", answer = "7.5°。分针指向 3，时针已经从 3 向 4 走了四分之一格，也就是 30° ÷ 4。"),
        LeisureItem("calm-1", LeisureKind.CALM, "让涟漪慢慢散开", "轻触空白处，看一圈涟漪展开。不必把它变成任何形状。"),
        LeisureItem("calm-2", LeisureKind.CALM, "这一小会儿", "目光可以暂时不寻找目标。轻轻点一下，让水面自己恢复平静。"),
    )
    private val themeNotes = mapOf(
        CampusTone.SAND to "圆日与斜向岩层相对，暖米色留下空间，深卡其让画面安定。",
        CampusTone.PINE to "高低错落的竖条与圆面，既像生长中的林木，也像校园里安静的楼群。",
        CampusTone.MATISSE to "浅绿让砖红、黄绿和灰蓝彼此呼应。不对称的色块，也可以形成稳定的平衡。",
        CampusTone.DUNHUANG to "石绿、沙金与赭红洞窟，把壁画和沙地的印象浓缩成几个大色面。",
        CampusTone.OLD_PORT to "一封信浮在低饱和的海色之间，温暖的信纸与冷静的港湾相互衬托。",
        CampusTone.PEACH_PLUM to "烟梅色茶杯与蜜桃色圆面相对，柔和的暖杏背景把温度留在画面里。",
        CampusTone.BURGUNDY to "少量酒红和杯下铺布，让日常器物带一点安静的仪式感。",
        CampusTone.MORANDI to "瓶、罐与碗保持高低和冷暖的细微差别。颜色不必浓烈，也可以彼此成全。",
        CampusTone.RED_BEAN to "清冷的银铃与一颗温润的红豆保持距离，留白让这份呼应更含蓄。",
        CampusTone.AUTUMN_CITRUS to "金黄的成熟与青绿的将熟未熟并置，秋意不是单一的颜色。",
        CampusTone.MORNING_BELL to "寺庙的一角与悬钟相伴，天青、灰蓝与雾白横向展开，朱色只作少量点缀。",
        CampusTone.MONDRIAN to "直线、留白和三原色形成不对称的秩序。大小不同的色面，承担不同的视觉重量。",
    )
    fun items(tone: CampusTone) = seeds + LeisureItem("theme-${tone.id}", LeisureKind.THEME, tone.title,
        themeNotes.getValue(tone), theme = tone)
    /** Choose categories uniformly, so the smaller seed categories are not buried. */
    fun next(tone: CampusTone, previous: String? = null, random: Random = Random.Default): LeisureItem {
        val pool = items(tone).filter { it.id != previous }
        val kind = pool.map { it.kind }.distinct().random(random)
        return pool.filter { it.kind == kind }.random(random)
    }
}
