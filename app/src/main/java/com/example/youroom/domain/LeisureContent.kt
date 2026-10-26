package com.example.youroom.domain

import kotlin.random.Random

/** Poetry carries only its original text and attribution; no editorial title or category badge. */
data class LeisureItem(val id: String, val body: String, val source: String)

/** Small offline fallback and deterministic guide fixtures; normal reading uses the bundled deck. */
object LeisureContent {
    private val seeds = listOf(
        LeisureItem("verse-1", "一年好景君须记，最是橙黄橘绿时。", "苏轼《赠刘景文》"),
        LeisureItem("verse-2", "不积跬步，无以至千里；不积小流，无以成江海。", "《荀子·劝学》"),
        LeisureItem("verse-3", "行到水穷处，坐看云起时。", "王维《终南别业》"),
    )
    fun items(): List<LeisureItem> = seeds

    fun next(previous: String? = null, random: Random = Random.Default,
        poetry: PoetryShuffle? = null): LeisureItem =
        poetry?.next(previous) ?: seeds.filter { it.id != previous }.random(random)
}
