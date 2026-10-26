package com.example.youroom.domain

/** Names are stable keys; ordered and tombstoned definitions do not erase room annotations. */
data class TagCatalog(
    val order: List<String> = PersonalTags.defaultOrder,
    val removed: Set<String> = emptySet(),
) {
    fun available(extra: Collection<String> = emptyList()): List<String> =
        (listOf(PersonalTags.favorite) + order + extra.sorted()).distinct()
            .filter { it == PersonalTags.favorite || it !in removed }
    fun added(name: String) = copy(order = available() + name, removed = removed - name)
    fun deleted(name: String) = if (name == PersonalTags.favorite) this else
        copy(order = available().filterNot { it == name }, removed = removed + name)
    fun reordered(names: List<String>) = copy(order =
        (listOf(PersonalTags.favorite) + names + available()).distinct()
            .filter { it == PersonalTags.favorite || it !in removed })
}

object TagOrder {
    fun move(order: List<String>, name: String, target: String): List<String> {
        val from = order.indexOf(name)
        val to = order.indexOf(target)
        if (name == PersonalTags.favorite || target == PersonalTags.favorite || from < 0 || to < 0 || from == to) return order
        return order.toMutableList().apply { removeAt(from); add(to, name) }
    }
}
