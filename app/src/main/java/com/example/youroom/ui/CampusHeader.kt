package com.example.youroom.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*
import kotlin.math.abs

@Composable
fun CampusHeader(state: YouRoomState, onRefresh: () -> Unit, onCampus: (Campus) -> Unit) {
    val density = LocalDensity.current.density
    val focus = LocalFocusManager.current
    val latestCampus by rememberUpdatedState(state.campus)
    val latestSwitch by rememberUpdatedState(onCampus)
    val campus = state.campus
    val other = if (campus == Campus.XITUCHENG) Campus.SHAHE else Campus.XITUCHENG
    fun switch(target: Campus) { focus.clearFocus(); latestSwitch(target) }
    Row(Modifier.fillMaxWidth().testTag("campus_header"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).testTag("campus_swipe_region")
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
            }.padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("YouRoom", style = MaterialTheme.typography.headlineMedium)
            AnimatedContent(campus, transitionSpec = {
                val direction = if (targetState == Campus.SHAHE) 1 else -1
                (fadeIn(tween(150)) + slideInHorizontally(tween(150)) { direction * it / 5 }) togetherWith
                    (fadeOut(tween(90)) + slideOutHorizontally(tween(90)) { -direction * it / 5 })
            }, label = "campus_subtitle") { displayed ->
                Text(displayed.label, modifier = Modifier.testTag("campus_label"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.refreshing) Box(Modifier.padding(end = 24.dp).size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else IconAction(onClick = onRefresh, enabled = !state.demo,
            modifier = Modifier.padding(end = 24.dp).testTag("refresh_rooms")) {
            Icon(if (state.viewingCache) Icons.AutoMirrored.Outlined.Login else Icons.Outlined.Refresh,
                if (state.viewingCache) "登录并刷新" else "刷新教室", Modifier.size(22.dp))
        }
    }
}
