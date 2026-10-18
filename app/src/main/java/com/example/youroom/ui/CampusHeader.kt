package com.example.youroom.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.themeRoles
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val headerDateFormat = DateTimeFormatter.ofPattern("M月d日 EEE.", Locale.ENGLISH)
internal fun campusHeaderDate(date: LocalDate): String = date.format(headerDateFormat)

@Composable
fun CampusHeader(state: YOURoomState, onCampus: (Campus) -> Unit) {
    val roles = themeRoles()
    val density = LocalDensity.current.density
    val focus = LocalFocusManager.current
    val latestCampus by rememberUpdatedState(state.campus)
    val latestSwitch by rememberUpdatedState(onCampus)
    val campus = state.campus
    val other = if (campus == Campus.XITUCHENG) Campus.SHAHE else Campus.XITUCHENG
    fun switch(target: Campus) { focus.clearFocus(); latestSwitch(target) }
    Box(Modifier.fillMaxWidth().background(roles.header).testTag("campus_header")) {
        Row(Modifier.fillMaxWidth().testTag("campus_swipe_region")
                .semantics {
                    customActions = listOf(CustomAccessibilityAction("切换到${other.label}校区") {
                        switch(other); true
                    })
                }.focusable()
                .pointerInput(campus, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var ownsGesture = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            val delta = change.position - down.position
                            if (!ownsGesture && abs(delta.x) > viewConfiguration.touchSlop &&
                                abs(delta.x) > abs(delta.y) * CampusSwipe.horizontalDominance) ownsGesture = true
                            if (ownsGesture) change.consume()
                            if (!change.pressed) {
                                if (ownsGesture && latestCampus == campus) {
                                    CampusSwipe.target(campus, delta.x / density, delta.y / density)?.let { switch(it) }
                                }
                                break
                            }
                        }
                    }
                }.padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YOURoom", Modifier.weight(1f).testTag("campus_brand"),
                style = MaterialTheme.typography.headlineMedium, color = roles.onHeader, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.testTag("campus_context"), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(campus.label, modifier = Modifier.testTag("campus_label"), style = MaterialTheme.typography.bodySmall,
                    color = roles.headerMuted, maxLines = 1)
                Text(campusHeaderDate(state.today), Modifier.testTag("campus_date"),
                    style = MaterialTheme.typography.bodySmall, color = roles.headerMuted,
                    maxLines = 1)
            }
        }
    }
}
