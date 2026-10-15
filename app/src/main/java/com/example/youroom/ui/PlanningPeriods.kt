package com.example.youroom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.PlanningDraft
import com.example.youroom.domain.PlanningPolicy
import com.example.youroom.domain.StudyWindow
import com.example.youroom.domain.planningTime

/** Fixed third-width cells: confirmation does not stretch existing periods. */
@Composable
internal fun PlanningPeriods(goal: PlanningDraft, enabled: Boolean, modifier: Modifier,
    onRemove: (Int) -> Unit) {
    BoxWithConstraints(modifier.testTag("planner_saved_periods")) {
        val periodWidth = (maxWidth - 8.dp) / PlanningPolicy.periodLimit
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            goal.savedWindows.withIndex().sortedBy { it.value.start }.forEach { (index, window) -> key(window) {
                PlanningPeriod(window, "planner_saved_period_$index", Modifier.width(periodWidth), enabled,
                    removeTag = "planner_remove_period_$index", onRemove = { onRemove(index) })
            } }
        }
    }
}

@Composable
private fun PlanningPeriod(window: StudyWindow, tag: String, modifier: Modifier,
    enabled: Boolean, removeTag: String, onRemove: () -> Unit) {
    Box(modifier.height(48.dp).testTag(tag).semantics(mergeDescendants = true) {}) {
        CompactChipFrame(true,"${tag}_face",Modifier.fillMaxWidth().align(Alignment.Center),
            onDelete = onRemove,deleteDescription = "删除已保存时段 ${planningTime(window.start)}–${planningTime(window.end)}",
            deleteTag = removeTag,enabled = enabled) {
            WidthFittedText("${planningTime(window.start)}–${planningTime(window.end)}",
                Modifier.fillMaxSize().padding(horizontal = 4.dp), MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center)
        }
    }
}
