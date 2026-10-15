package com.example.youroom

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.youroom.ui.RoomMetadataMotion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RoomMetadataMotionUiTest {
    @get:Rule val compose=createComposeRule()
    private val motion=RoomMetadataMotion(listOf("有插座"),"旧备注")
    private var labels by mutableStateOf(listOf("有插座"))
    private var note by mutableStateOf("旧备注")
    private var editing by mutableStateOf(true)
    private var closed by mutableStateOf(false)
    private var enabled by mutableStateOf(true)
    private fun render() {
        compose.setContent { motion.Run(labels,note,editing,enabled) { closed } }
        compose.mainClock.autoAdvance=false
    }
    @Test fun savedDataStaysHiddenUntilDismissalClosureAndTheSmallDelayThenFadesIn() {
        render()
        compose.runOnIdle { labels=listOf("桌椅舒适");note="新备注" }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals("旧备注",motion.note);assertEquals(listOf("有插座"),motion.labels);editing=false }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals("旧备注",motion.note);closed=true }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertEquals("旧备注",motion.note) }
        compose.mainClock.advanceTimeBy(112)
        compose.runOnIdle {
            assertEquals("新备注",motion.note);assertEquals(listOf("桌椅舒适"),motion.labels)
            assertTrue(motion.noteAlpha.value in .001f.. .999f)
            assertTrue(motion.labelsAlpha.value in .001f.. .999f)
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(1f,motion.noteAlpha.value,0f);assertEquals(1f,motion.labelsAlpha.value,0f) }
        compose.mainClock.autoAdvance=true
    }
    @Test fun reopeningDuringTheDelayDoesNotRevealStaleIntermediateEdits() {
        render()
        compose.runOnIdle { note="中间值";editing=false;closed=true }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { editing=true;note="最终值" }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals("旧备注",motion.note);editing=false }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals("最终值",motion.note);assertEquals(1f,motion.noteAlpha.value,0f) }
        compose.mainClock.autoAdvance=true
    }
    @Test fun switchingAwaySynchronizesWithoutLeavingAnInvisibleNote() {
        render()
        compose.runOnIdle { note="新备注";labels=emptyList();enabled=false }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle {
            assertEquals("新备注",motion.note);assertTrue(motion.labels.isEmpty())
            assertEquals(1f,motion.noteAlpha.value,0f)
        }
        compose.mainClock.autoAdvance=true
    }
    @Test fun unchangedMetadataNeverFlashesAndOnlyChangedLabelsFade() {
        render()
        compose.runOnIdle { editing=false;closed=true }
        compose.mainClock.advanceTimeBy(150)
        compose.runOnIdle { assertEquals(1f,motion.noteAlpha.value,0f);assertEquals(1f,motion.labelsAlpha.value,0f);labels=listOf("人少") }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertEquals("旧备注",motion.note);assertEquals(1f,motion.noteAlpha.value,0f);assertTrue(motion.labelsAlpha.value<1f) }
        compose.mainClock.autoAdvance=true
    }
}
