package com.example.youroom.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import com.example.youroom.ui.theme.themeRoles
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.youroom.domain.PlanningDraft

@Composable
fun YOURoomApp(model: YOURoomViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val plans by model.planning.executions.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable(stateSaver = PlanningGoalSaver) { mutableStateOf(PlanningDraft()) }
    var planningDate by rememberSaveable { mutableStateOf(state.today.toString()) }
    var planningDemo by rememberSaveable { mutableStateOf(state.demo) }
    var planningDefaultsApplied by rememberSaveable { mutableStateOf(false) }
    var planningDefaultsCampus by rememberSaveable { mutableStateOf(state.campus.dataKey) }
    var roomEditor by rememberSaveable { mutableStateOf(RoomEditor.ACTIONS) }
    fun editRoom(id: String, editor: RoomEditor) { roomEditor = editor; model.selectRoom(id) }
    fun manageRoom(id: String?) { roomEditor = RoomEditor.ACTIONS; model.selectRoom(id) }
    LaunchedEffect(state.today, state.demo, state.initializing, state.campus) {
        if (state.initializing) return@LaunchedEffect
        if (!planningDefaultsApplied) {
            goal = goal.copy(flexible = state.planningPreferences.forCampus(state.campus).flexible)
            planningDefaultsApplied = true
        }
        if (planningDefaultsCampus != state.campus.dataKey && goal.savedWindows.isEmpty() && plans[state.campus] == null) {
            goal = goal.copy(flexible = state.planningPreferences.forCampus(state.campus).flexible)
        }
        planningDefaultsCampus = state.campus.dataKey
        if (planningDate != state.today.toString() || planningDemo != state.demo) {
            goal = goal.copy(savedWindows = emptyList(), flexible = state.planningPreferences.forCampus(state.campus).flexible,
                expectedRooms = if (planningDemo != state.demo) emptySet() else goal.expectedRooms)
        }
        planningDate = state.today.toString(); planningDemo = state.demo
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.onForeground() }
    ThemeTransitionHost(state.campus, state.appearance, tab to state.showWorkspace,
        enabled = state.showWorkspace, onTone = model::setCampusTone) { requestTone ->
        val view = LocalView.current
        val roles = themeRoles()
        val statusSurface = if (state.showWorkspace) roles.header else MaterialTheme.colorScheme.surface
        val waveMoving = LocalThemePreview.current?.busy == true
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null && !waveMoving) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = statusSurface.luminance() > .5f
                    isAppearanceLightNavigationBars = roles.navigation.luminance() > .5f
                }
            }
        }
        CompositionLocalProvider(LocalPeriodStripPreferences provides state.periodStrip) {
        RoomSearchFocusBoundary(Modifier.fillMaxSize()) {
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
                        else -> "workspace"
                    },
                    transitionSpec = {
                        (fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 36 }) togetherWith fadeOut(tween(90))
                    },
                    label = "workspace",
                ) { route ->
                    when (route) {
                        "loading" -> LoadingScreen()
                        "login" -> LoginScreen(state, model::login, { tab = 0; model.showDemo() },
                            { tab = 0; model.viewCache() }, onRememberedLogin = model::login)
                        else -> WorkspacePages(state, tab, model, goal, plans,
                            { goal = it }, { model.generatePlanning(goal) }, ::editRoom, ::manageRoom, requestTone)
                    }
                }
            }
        }
        }
        state.selectedRoom?.let { room ->
            val dismiss = { model.selectRoom(null) }
            when (roomEditor) {
                RoomEditor.TAGS -> RoomTagsDialog(room, state, dismiss, model::saveProfile, model::addCustomTag,
                    model::removePersonalTag, model::reorderPersonalTags)
                RoomEditor.NOTE -> RoomNoteDialog(room, dismiss, model::saveProfile)
                RoomEditor.ACTIONS -> RoomActionsDialog(room, dismiss,
                    { model.toggleFavorite(room.classroom) }, { roomEditor = RoomEditor.TAGS },
                    { roomEditor = RoomEditor.NOTE }, { model.toggleExcluded(room.classroom) })
            }
        }
        }
    }
}

@Composable
private fun WorkspacePages(state: YOURoomState, tab: Int, model: YOURoomViewModel,
    goal: PlanningDraft, plans: Map<com.example.youroom.domain.Campus,PlanningExecution>,
    onGoal: (PlanningDraft) -> Unit, onGenerate: () -> Unit,
    editRoom: (String, RoomEditor) -> Unit, manageRoom: (String?) -> Unit,
    onTone: (com.example.youroom.domain.Campus, com.example.youroom.domain.CampusTone) -> Unit) {
    val header = rememberCampusHeaderMotion(state.campus)
    val headerMoving by remember(header) { derivedStateOf { header.moving } }
    val savedPages = rememberSaveableStateHolder()
    val navigation = updateTransition(tab, label = "workspace navigation")
    Column(Modifier.fillMaxSize()) {
        // A single header stays mounted and outside every tab transition.
        CampusHeaderPager(state, header, model::setCampus)
        CampusHeaderDivider()
        navigation.AnimatedContent(
            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("workspace_pages"),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = .86f, stiffness = 180f)) { direction * it } +
                    fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(spring(dampingRatio = .95f, stiffness = 240f)) { -direction * it } +
                        fadeOut(tween(160))) using SizeTransform(clip = false)
            }, contentKey = { it }) { page ->
            val active = page == tab && !navigation.isRunning
            savedPages.SaveableStateProvider(page) {
                Box(Modifier.fillMaxSize().testTag("workspace_page_$page")
                    .then(if (page == tab) Modifier else Modifier.clearAndSetSemantics {})) {
                    when (page) {
                        0 -> ClassroomScreen(state, { model.setFiltersFor(state.campus, it) }, model::refresh,
                            editRoom, model::toggleFavorite, model::setCampus, model::addCustomTag,
                            model::removePersonalTag, model::reorderPersonalTags, model::toggleExcluded,
                            showHeader = false, sharedHeaderMoving = headerMoving, interactive = active)
                        1 -> StudyPlanningScreen(state, goal, plans, onGoal, onGenerate, model::setCampus,
                            model::refresh, manageRoom, { campus,id,index -> model.planning.select(campus,id,index) },showHeader = false,
                            sharedHeaderMoving = headerMoving, interactive = active, onCancel = model::cancelPlanning,
                            onPlanningPreferences = model::setPlanningPreferences)
                        else -> SettingsScreen(state, manageRoom, model::clearProfile, model::logout,
                            model::setCampus, showHeader = false, sharedHeaderMoving = headerMoving, interactive = page == tab,
                            onPeriodStrip = model::setPeriodStrip,
                            onTone = onTone, onDefaultCampus = model::setDefaultCampus,
                            onPlanningPreferences = model::setPlanningPreferences, onDarkMode = model::setDarkMode)
                    }
                }
            }
        }
    }
}
