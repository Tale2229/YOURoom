package com.example.youroom.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun YouRoomApp(model: YouRoomViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.onForeground() }
    Scaffold(
        bottomBar = {
            if (state.showWorkspace) NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Search, null) }, label = { Text("找教室") },
                    modifier = Modifier.testTag("tab_rooms"))
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.StarBorder, null) }, label = { Text("个人偏好") },
                    modifier = Modifier.testTag("tab_preferences"))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(state.error != null) {
                StatusNotice(state.error.orEmpty(), isError = true, onDismiss = model::dismissError)
            }
            AnimatedContent(
                targetState = when {
                    state.initializing -> "loading"
                    !state.showWorkspace -> "login"
                    tab == 0 -> "rooms"
                    else -> "preferences"
                },
                transitionSpec = { fadeIn() + slideInVertically { it / 24 } togetherWith fadeOut() },
                label = "workspace",
            ) { route ->
                when (route) {
                    "loading" -> LoadingScreen()
                    "login" -> LoginScreen(state, model::login, { tab = 0; model.showDemo() },
                        { tab = 0; model.viewCache() })
                    "rooms" -> ClassroomScreen(state, model::setFilters, model::refresh,
                        model::selectRoom, model::toggleFavorite)
                    else -> PreferencesScreen(state, model::selectRoom, model::clearProfile, model::logout)
                }
            }
        }
    }
    state.selectedRoom?.let { room ->
        RoomDetailSheet(room, state, { model.selectRoom(null) }) { profile ->
            model.saveProfile(profile)
            model.selectRoom(null)
        }
    }
}
