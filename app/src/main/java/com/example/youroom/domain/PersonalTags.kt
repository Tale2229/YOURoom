package com.example.youroom.domain

object PersonalTags {
    const val comfortable = "桌椅舒适"
    const val uncrowded = "人少"
    const val legacyQuiet = "安静"
    val builtIn = setOf("已收藏", "有插座", comfortable, uncrowded)
    fun validate(raw: String, existing: Set<String>): String? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> "请输入词条名称"
            name.length > 20 -> "词条最多 20 个字"
            name.any { it.isISOControl() } -> "词条不能包含换行或控制字符"
            name in builtIn || name == "全部楼宇" || name in existing -> "这个词条已经存在"
            else -> null
        }
    }
}

val RoomProfile.allTags: Set<String> get() = if (quiet) tags + PersonalTags.legacyQuiet else tags
