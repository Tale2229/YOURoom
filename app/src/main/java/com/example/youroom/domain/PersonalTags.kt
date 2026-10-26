package com.example.youroom.domain

object PersonalTags {
    const val favorite = "已收藏"
    const val outlets = "有插座"
    const val comfortable = "桌椅舒适"
    const val uncrowded = "人少"
    const val legacyQuiet = "安静"
    val defaultOrder = listOf(favorite, outlets, comfortable, uncrowded)
    val builtIn = defaultOrder.toSet()
    fun validate(raw: String, existing: Set<String>): String? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> "请输入词条名称"
            name.length > 20 -> "词条最多 20 个字"
            name.any { it.isISOControl() } -> "词条不能包含换行或控制字符"
            name == favorite || name in setOf("全部楼宇", "全部教学楼", "+", "＋") || name in existing -> "这个词条已经存在"
            else -> null
        }
    }
}

val RoomProfile.allTags: Set<String> get() = if (quiet) tags + PersonalTags.legacyQuiet else tags

fun RoomProfile.hasTerm(name: String) = when (name) {
    PersonalTags.favorite -> favorite
    PersonalTags.outlets -> outlets
    else -> name in allTags
}

fun SearchFilters.hasTerm(name: String) = when (name) {
    PersonalTags.favorite -> favoritesOnly
    PersonalTags.outlets -> outletsOnly
    else -> name in tags
}

fun SearchFilters.toggleTerm(name: String) = when (name) {
    PersonalTags.favorite -> copy(favoritesOnly = !favoritesOnly)
    PersonalTags.outlets -> copy(outletsOnly = !outletsOnly)
    else -> copy(tags = if (name in tags) tags - name else tags + name)
}

fun SearchFilters.withoutTerm(name: String) = when (name) {
    PersonalTags.favorite -> this
    PersonalTags.outlets -> copy(outletsOnly = false)
    else -> copy(tags = tags - name)
}
