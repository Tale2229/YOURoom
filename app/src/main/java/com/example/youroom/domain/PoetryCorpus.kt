package com.example.youroom.domain

import java.io.Reader
import kotlin.random.Random

/** Bundled quotations with original attribution; no generated authors or commentary. */
object PoetryCorpus {
    const val size = 10_000
    const val asset = "poetry/quotes.tsv"

    fun read(reader: Reader): List<LeisureItem> {
        val rows = reader.buffered().lineSequence().mapIndexed { index, line ->
            val fields = line.split('\t')
            require(fields.size == 3 && fields.all { it.isNotBlank() }) { "Invalid poetry row ${index + 1}" }
            val (id, body, source) = fields
            require(id.startsWith("verse-") && '《' in source && source.endsWith('》'))
            LeisureItem(id, body, source)
        }.toList()
        require(rows.size == size && rows.map { it.id }.toSet().size == size) { "Incomplete poetry corpus" }
        return rows
    }
}

/** A shuffled deck draws every quote once before reshuffling, within this workspace. */
class PoetryShuffle(private val items: List<LeisureItem>, private val random: Random = Random.Default) {
    init {
        require(items.size >= 2)
    }
    private val order = IntArray(items.size) { it }
    private var position = items.size

    fun next(previous: String? = null): LeisureItem {
        if (position == order.size) {
            order.shuffle(random)
            position = 0
        }
        if (items[order[position]].id == previous && position < order.lastIndex) {
            val swap = random.nextInt(position + 1, order.size)
            val current = order[position]; order[position] = order[swap]; order[swap] = current
        }
        return items[order[position++]]
    }
}
