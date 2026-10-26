package com.example.youroom

import com.example.youroom.domain.*
import com.example.youroom.ui.PlannerUtilityState
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PoetryCorpusTest {
    private fun corpus() = File("src/main/assets/${PoetryCorpus.asset}").reader(Charsets.UTF_8).use(PoetryCorpus::read)

    @Test fun bundledCorpusHasTenThousandDistinctAttributedQuotations() {
        val rows=corpus()
        assertEquals(10_000,rows.size)
        assertEquals(rows.size,rows.map { it.body.filter(Char::isLetterOrDigit) }.toSet().size)
        assertTrue(rows.all { it.body.isNotBlank() && '《' in it.source })
        assertTrue(rows.any { it.body=="一年好景君须记，最是橙黄橘绿时。" && it.source=="苏轼《赠刘景文 / 冬景》" })
        assertTrue(rows.none { '\uFFFD' in it.body || '\t' in it.body || '\n' in it.body })
    }
    @Test fun shuffledDeckVisitsEveryQuoteBeforeRepeatingAndDoesNotRepeatAtTheBoundary() {
        val deck=PoetryShuffle(corpus(),Random(17))
        val first=List(10_000) { deck.next().id }
        assertEquals(10_000,first.toSet().size)
        val second=List(10_000) { index -> deck.next(if(index==0) first.last() else null).id }
        assertEquals(first.toSet(),second.toSet())
        assertNotEquals(first.last(),second.first())
        assertNotEquals(first,second)
    }
    @Test fun normalReadingUsesOnlyTheBundledDeckWithoutInterleavingFallbackSeeds() {
        val rows=corpus()
        val deck=PoetryShuffle(rows,Random(1))
        var previous: String?=null
        val seen=List(10_000) {
            LeisureContent.next(previous,poetry=deck).also { previous=it.id }.id
        }
        assertEquals(rows.map { it.id }.toSet(),seen.toSet())
        assertTrue(seen.none { id -> LeisureContent.items().any { it.id==id } })
    }
    @Test fun revisitingCachedItemsDoesNotDrawAnotherQuoteOrChangeTheVisitedItem() {
        val utility=PlannerUtilityState()
        utility.installPoetry(corpus())
        val first=utility.nextItem()
        utility.item=first
        utility.moveReader(1)
        val second=utility.item
        utility.moveReader(-1)
        assertEquals(first,utility.item)
        utility.moveReader(1)
        assertEquals(second,utility.item)
    }
    @Test(expected=IllegalArgumentException::class)
    fun incompleteCorpusIsRejectedInsteadOfSilentlyReducingTheLibrary() {
        PoetryCorpus.read("verse-test\t测试句子\t作者《篇名》\n".reader())
    }
}
