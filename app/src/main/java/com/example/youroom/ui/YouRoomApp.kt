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
import com.example.youroom.domain.PlanningGoal

@Composable
fun YouRoomApp(model: YouRoomViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val displayedCampus = state.campus
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable(stateSaver = PlanningGoalSaver) { mutableStateOf(PlanningGoal()) }
    var planRequested by rememberSaveable { mutableStateOf(false) }
    var planningCampus by rememberSaveable { mutableStateOf(state.campus.dataKey) }
    var generationMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var planningDate by rememberSaveable { mutableStateOf(state.today.toString()) }
    var planningDemo by rememberSaveable { mutableStateOf(state.demo) }
    var roomEditor by rememberSaveable { mutableStateOf(RoomEditor.ACTIONS) }
    fun editRoom(id: String, editor: RoomEditor) { roomEditor = editor; model.selectRoom(id) }
    fun manageRoom(id: String?) { roomEditor = RoomEditor.ACTIONS; model.selectRoom(id) }
    LaunchedEffect(state.today, state.demo) {
        if (planningDate != state.today.toString() || planningDemo != state.demo) {
            planRequested = false; generationMinute = null
        }
        planningDate = state.today.toString(); planningDemo = state.demo
    }
    LaunchedEffect(state.campus) {
        if (planningCampus != state.campus.dataKey && goal.origin != null) {
            goal = goal.copy(origin = null); planRequested = false
        }
        planningCampus = state.campus.dataKey
    }
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
                        tab == 1 -> "planner"
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
                        "rooms" -> ClassroomScreen(state, { model.setFiltersFor(displayedCampus, it) }, model::refresh,
                            ::editRoom, model::toggleFavorite, model::setCampus, model::addCustomTag,
                            model::removePersonalTag, model::reorderPersonalTags, model::toggleExcluded)
                        "planner" -> StudyPlanningScreen(state, goal, planRequested,
                            { goal = it; planRequested = false }, {
                                generationMinute = if (state.demo) null else java.time.LocalTime.now(com.example.youroom.domain.SchoolZone)
                                    .let { (it.toSecondOfDay() + 59) / 60 }
                                planRequested = true
                            }, model::setCampus, model::refresh, ::manageRoom, generationMinute)
                        else -> PreferencesScreen(state, ::manageRoom, model::clearProfile, model::logout)
                    }
                }
            }
        }
        state.selectedRoom?.let { room ->
            val dismiss = { model.selectRoom(null) }
            when (roomEditor) {
                RoomEditor.TAGS -> RoomTagsDialog(room, state, dismiss, model::saveProfile, model::addCustomTag)
                RoomEditor.NOTE -> RoomNoteDialog(room, dismiss, model::saveProfile)
                RoomEditor.ACTIONS -> RoomActionsDialog(room, dismiss,
                    { model.toggleFavorite(room.classroom) }, { roomEditor = RoomEditor.TAGS },
                    { roomEditor = RoomEditor.NOTE }, { model.toggleExcluded(room.classroom) })
            }
        }
    }
}
