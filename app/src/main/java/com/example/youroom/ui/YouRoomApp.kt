package com.example.youroom.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.youroom.ui.theme.YouRoomTheme

@Composable
fun YouRoomApp(model: YouRoomViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.onForeground() }
    YouRoomTheme(campus = state.campus) {
        Scaffold(
            bottomBar = {
                if (state.showWorkspace) WorkspaceNavigation(tab) { tab = it }
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
                    transitionSpec = {
                        (fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 36 }) togetherWith fadeOut(tween(90))
                    },
                    label = "workspace",
                ) { route ->
                    when (route) {
                        "loading" -> LoadingScreen()
                        "login" -> LoginScreen(state, model::login, { tab = 0; model.showDemo() },
                            { tab = 0; model.viewCache() })
                        "rooms" -> ClassroomScreen(state, model::setFilters, model::refresh,
                            model::selectRoom, model::toggleFavorite, model::setCampus)
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
}
