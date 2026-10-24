package com.example.youroom.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModelStore
import com.example.youroom.data.*
import com.example.youroom.domain.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class GuideAnchor(val window: String, val bounds: Rect, val owner: Any)

/** Everything owned by this object is disposable practice state. */
internal class GuideRuntime(val position: MutableIntState, appearance: AppearancePreferences,
    strip: PeriodStripPreferences,
    directory:MutableState<Boolean> = mutableStateOf(false),
    private val completed:MutableState<List<String>> = mutableStateOf(emptyList())) {
    val clock = GuideData.clock()
    val data = GuideData(clock)
    val settings = GuideSettings(appearance,strip)
    @Volatile var holdCalculation = false
    val model = YOURoomViewModel(data,settings,clock,training=true,trainingCalculation = { input ->
        if (holdCalculation) awaitCancellation()
        val context = currentCoroutineContext()
        StudyPlanner.portfolio(input.snapshot,input.campus,input.today,input.profiles,input.goal) { context.ensureActive() }
    })
    private val store = ViewModelStore().apply { put("practice",model) }
    val anchors = mutableStateMapOf<String,GuideAnchor>()
    val dialogs = mutableStateMapOf<Any,GuideModal>()
    private var dialogOrder=0
    fun attachDialog(owner:Any,dialog:GuideModal) { dialog.order=++dialogOrder;dialogs[owner]=dialog }
    // The read-only import preview and the following choice lesson share one
    // mounted sheet. Changing only its permissions must not recreate its UI.
    val dialogStage get() = when(stage) {
        GuideStage.FILTER_TAGS -> GuideStage.BUILDINGS
        GuideStage.IMPORT_PICK, GuideStage.REPLACE -> GuideStage.IMPORT
        GuideStage.TEMPLATES -> GuideStage.PLANNING
        else -> stage
    }
    val fixedSettingsPosition get() = stage in listOf(GuideStage.STRIP,GuideStage.DEFAULT_CAMPUS)
    var panelHeightPx by mutableIntStateOf(0)
    var retainedPanelAnchor by mutableStateOf<GuideAnchor?>(null)
        private set
    // Caller-owned open flags are cleaned by child effects after a step change.
    // Never let those stale callers adopt the destination step for even one frame.
    fun acceptsDialog(dialogWindow:String) = dialogWindow==window ||
        (stage==GuideStage.REPLACE && dialogWindow=="sheet")
    val visibleDialogs get() = dialogs.entries.filter {
        it.value.stage==dialogStage && acceptsDialog(it.value.window)
    }.sortedBy { it.value.order }
    val presentedWindow get() = visibleDialogs.lastOrNull()?.value?.window ?: "root"
    var appearanceLayoutStage by mutableStateOf<GuideStage?>(null)
    val layoutReady get() = stage !in listOf(GuideStage.STRIP,GuideStage.DEFAULT_CAMPUS,GuideStage.NUMBERS) ||
        appearanceLayoutStage==stage
    var preparedStage by mutableStateOf<GuideStage?>(null)
    var preparing by mutableStateOf(true)
        private set
    // Keep unchanged practice content visible across a shared grid or an open
    // import-sheet hand-off. Input remains fully blocked until ready.
    var retainedOpening by mutableStateOf<GuideAnchor?>(null)
        private set
    fun finishPreparation(expected:GuideStage) {
        if(stage==expected && preparedStage==expected && ready && layoutReady && presentedWindow==window) {
            preparing=false;retainedOpening=null
        }
    }
    var goal by mutableStateOf(PlanningDraft(start=660,end=720))
    // Completion is a learning milestone, not the current value of a setting.
    // Returning to the original campus/theme or revisiting a step cannot undo it.
    val done get() = stage.name in completed.value
    var demonstration by mutableIntStateOf(0)
    private var actedStage by mutableStateOf<GuideStage?>(null)
    var events by mutableIntStateOf(0)
    var menu by directory
    var entry by mutableIntStateOf(0)
        private set
    var importTemplateId by mutableStateOf("guide")
        private set
    private var importedGoal:PlanningDraft?=null
    val target get() = when {
        stage==GuideStage.WHEELS && entry==0 -> "tab_planner"
        stage==GuideStage.THEME && entry==0 -> "tab_settings"
        stage==GuideStage.FILTER_TAGS && entry==0 -> "edit_tags"
        stage==GuideStage.PLANNING && entry==0 -> "settings_planning"
        stage==GuideStage.TEMPLATES && entry==0 -> "planning_templates_entry"
        stage==GuideStage.TAGS && entry==0 -> "room_action_01:2-201_1"
        stage==GuideStage.NOTE && entry==0 -> "room_action_01:2-201_2"
        stage==GuideStage.MARKS && entry==0 -> "tab_settings"
        stage==GuideStage.MARKS && entry==1 -> "settings_room_marks"
        stage==GuideStage.PERIOD && entry==0 -> "planner_target_range_0"
        stage==GuideStage.SAVE_TEMPLATE && entry==0 -> "planner_save_periods_0"
        stage==GuideStage.FROM_NOW && entry>0 -> "planner_start_center"
        stage==GuideStage.DURATION && entry==0 -> "planner_add_period"
        stage==GuideStage.DURATION && entry>1 -> "planner_start_center"
        stage==GuideStage.IMPORT && entry>0 -> "template_import_dialog"
        stage==GuideStage.EXPECT && entry>0 -> "expected_room_dialog"
        else -> stage.target
    }
    val window get() = if((stage in listOf(GuideStage.TAGS,GuideStage.NOTE,GuideStage.PLANNING) && entry==0) ||
        (stage==GuideStage.MARKS && entry<2) || (stage==GuideStage.PERIOD && entry==0)) "root"
        else if(stage==GuideStage.SAVE_TEMPLATE && entry==0) "period"
        else if(stage==GuideStage.IMPORT && entry>0) "sheet"
        else if(stage==GuideStage.EXPECT && entry>0) "expected" else stage.window
    val page get() = when {
        stage==GuideStage.MARKS && entry==0 -> 0
        stage==GuideStage.WHEELS && entry==0 -> 2
        stage==GuideStage.THEME && entry==0 -> 1
        else -> stage.page
    }
    val spotlightTargets get() = when {
        stage==GuideStage.ADD && entry>0 -> listOf(target,"planner_target_strip")
        stage==GuideStage.PAST -> listOf(target,"planner_start_center")
        stage==GuideStage.DURATION -> listOf(target,"planner_start_center","planner_end_center").distinct()
        target!=stage.target -> listOf(target)
        else -> stage.spotlightTargets
    }
    var onFinish: (GuideOutcome) -> Unit = {}
    var initialTone = appearance.westTone
    private val signals = mutableStateListOf<String>()
    val stage get() = GuideStage.entries[position.intValue.coerceIn(0,GuideStage.entries.lastIndex)]
    val ready get() = spotlightTargets.all { id -> anchors[id]?.let {
        it.window == window && it.bounds.width > 1 && it.bounds.height > 1
    } == true }

    fun close() = store.clear()
    fun signal(value: String) { if (value !in signals) { signals += value; events++ } }
    fun select(index: Int) {
        val next=index.coerceIn(0,GuideStage.entries.lastIndex)
        if(next!=position.intValue) {
            val footerStages=listOf(GuideStage.LOGOUT,GuideStage.CLEAR_CACHE)
            retainedPanelAnchor=when {
                GuideStage.entries[next] !in footerStages -> null
                anchors[GuideStage.entries[next].target]!=null -> anchors[GuideStage.entries[next].target]
                stage in footerStages -> anchors[target] ?: retainedPanelAnchor
                else -> null
            }
            actedStage=null
            retainedOpening=when {
                setOf(stage,GuideStage.entries[next])==setOf(GuideStage.SLOTS,GuideStage.INSPECT) ->
                    retainedOpening ?: anchors["slot_grid"]
                stage==GuideStage.IMPORT && next==GuideStage.IMPORT_PICK.ordinal && entry>0 && presentedWindow=="sheet" ->
                    anchors["template_import_dialog"]
                stage==GuideStage.PLANNING && next==GuideStage.TEMPLATES.ordinal && entry>0 && presentedWindow=="sheet" ->
                    anchors["planning_default_direction_group"]
                else -> null
            }
            if(stage==GuideStage.DARK) model.setDarkMode(false)
            importedGoal=null
            if(next!=GuideStage.REPLACE.ordinal || stage!=GuideStage.IMPORT_PICK) importTemplateId="guide"
            if(GuideStage.entries[next] !in listOf(GuideStage.RESULTS,GuideStage.ALTERNATIVES)) model.planning.clear()
            appearanceLayoutStage=null
            preparing=true;entry=0
        }
        position.intValue=next
    }
    fun next() { if(stage==GuideStage.FINISH) onFinish(GuideOutcome.COMPLETED) else select(position.intValue+1) }
    fun recordAction(expected:GuideStage) {
        if(stage==expected && preparedStage==expected && !preparing) actedStage=expected
    }
    // Practice must never launch from a stale control callback in another lesson.
    // STOP / RESULTS fixtures are explicitly prepared below, not user actions.
    fun requestPlanning() {
        if(stage==GuideStage.GENERATE && !preparing && preparedStage==stage) {
            recordAction(GuideStage.GENERATE);model.generatePlanning(goal)
        }
    }
    fun editingFilterTags(editing:Boolean) { if(stage==GuideStage.FILTER_TAGS) entry=if(editing) 1 else 0 }
    fun enteredPlanning() { if(stage==GuideStage.PLANNING) entry=1 }
    fun enteredTemplates() { if(stage==GuideStage.TEMPLATES) entry=1 }
    fun exitedTemplates() { if(stage==GuideStage.TEMPLATES) entry=0 }
    fun pickedImportTemplate(id:String) {
        if(stage==GuideStage.IMPORT_PICK) { importTemplateId=id;select(GuideStage.REPLACE.ordinal) }
    }
    fun importedTemplate() {
        if(stage==GuideStage.REPLACE) {
            val imported=goal
            select(GuideStage.EXPECT.ordinal)
            importedGoal=imported
        }
    }
    fun enteredEditor() { if(stage in listOf(GuideStage.TAGS,GuideStage.NOTE)) entry=1 }
    fun exitedEditor() { if(stage in listOf(GuideStage.TAGS,GuideStage.NOTE)) entry=0 }
    fun enteredPlannerPage() { if(stage==GuideStage.WHEELS && entry==0) entry=1 }
    fun enteredSettings() { if(stage in listOf(GuideStage.MARKS,GuideStage.THEME) && entry==0) entry=1 }
    fun enteredMarks() { if(stage==GuideStage.MARKS) entry=2 }
    fun exitedMarks() { if(stage==GuideStage.MARKS) entry=1 }
    fun openedPeriod() { if(stage==GuideStage.PERIOD) entry=1 }
    fun openedTemplate() { if(stage==GuideStage.SAVE_TEMPLATE) entry=1 }
    fun closedTemplate() { if(stage==GuideStage.SAVE_TEMPLATE) entry=0 }
    fun openedImport() { if(stage==GuideStage.IMPORT) { recordAction(GuideStage.IMPORT);entry=1 } }
    fun openedExpected() { if(stage==GuideStage.EXPECT) entry=1 }
    fun openedAlternatives() { if(stage==GuideStage.RESULTS) select(GuideStage.ALTERNATIVES.ordinal) }
    fun rejectedPeriod(problem:PlanningProblem?) {
        if(stage==GuideStage.PAST && problem==PlanningProblem.PAST_TIME) select(GuideStage.FROM_NOW.ordinal)
        else if(stage==GuideStage.DURATION && problem in listOf(PlanningProblem.INVALID_TIME,PlanningProblem.TOO_SHORT)) entry=1
    }
    fun updateGoal(value:PlanningDraft) {
        if(stage in listOf(GuideStage.ADD,GuideStage.FROM_NOW,GuideStage.EXPECT)) recordAction(stage)
        goal=value
        when {
            stage==GuideStage.ADD && value.savedWindows.isNotEmpty() -> entry=1
            stage==GuideStage.FROM_NOW && value.start==601 -> entry=1
            stage==GuideStage.DURATION && entry>0 && value.end-value.start>=PlanningPolicy.minimumPeriodMinutes -> entry=2
        }
    }

    fun prepare() {
        val retainedResult=stage in listOf(GuideStage.RESULTS,GuideStage.ALTERNATIVES) &&
            model.planning.executions.value[Campus.XITUCHENG]?.result?.options?.isNotEmpty()==true
        preparedStage=null
        signals.clear();demonstration=0;actedStage=null;entry=0
        holdCalculation=false
        if(!retainedResult) model.planning.clear()
        if (stage != GuideStage.CAMPUS) model.setCampus(Campus.XITUCHENG)
        model.setFilters(SearchFilters())
        model.selectRoom(null)
        if (stage == GuideStage.SEARCH) model.setFilters(SearchFilters(query="2-"))
        val room=data.snapshot(Campus.XITUCHENG).classrooms.first()
        // Never let practice favorites from another chapter displace the target
        // row. Only this room is used for marking; planning uses availability,
        // not seeded favorites, to produce its alternative routes.
        model.state.value.profiles.values.filter {
            it.favorite && (it.roomId!=room.id || stage.page==1)
        }.forEach { model.saveProfile(it.copy(favorite=false)) }
        if (stage in listOf(GuideStage.SWIPE,GuideStage.FAVORITE))
            model.saveProfile(room.emptyProfile())
        if (stage in listOf(GuideStage.TAGS,GuideStage.NOTE,GuideStage.EXCLUDE,GuideStage.MARKS))
            model.saveProfile(room.emptyProfile().copy(favorite=true,note="靠窗，记得带水",tags=setOf("安静")))
        when(stage) {
            GuideStage.WHEELS, GuideStage.ADD -> goal=PlanningDraft(start=660,end=720)
            // This is exactly the result of confirming 11:00–12:00 in ADD.
            // Do not introduce a second, unrelated wheel animation on the next step.
            GuideStage.PERIOD, GuideStage.SAVE_TEMPLATE ->
                goal=PlanningDraft(start=720,end=780,savedWindows=listOf(StudyWindow(660,720)))
            GuideStage.IMPORT,
            GuideStage.IMPORT_PICK, GuideStage.REPLACE -> goal=PlanningDraft(start=840,end=960,savedWindows=listOf(GuideData.windows.first()))
            GuideStage.PAST, GuideStage.FROM_NOW -> goal=PlanningDraft(start=540,end=720)
            GuideStage.DURATION -> goal=PlanningDraft(start=601,end=600)
            GuideStage.EXPECT, GuideStage.FLEXIBLE,
            GuideStage.GENERATE, GuideStage.STOP, GuideStage.RESULTS, GuideStage.ALTERNATIVES -> {
                // Three always-free rooms yield distinct, fast alternatives
                // without adding favorites that could reorder classroom lessons.
                if(!retainedResult) {
                    goal=importedGoal?.takeIf { stage==GuideStage.EXPECT } ?: PlanningDraft(start=660,end=720,savedWindows=GuideData.windows,
                        expectedRooms=if(stage.ordinal>GuideStage.EXPECT.ordinal) setOf(room.id) else emptySet())
                }
                if (stage==GuideStage.STOP || (stage in listOf(GuideStage.RESULTS,GuideStage.ALTERNATIVES) && !retainedResult)) {
                    holdCalculation=stage==GuideStage.STOP
                    model.generatePlanning(goal)
                }
            }
            else -> Unit
        }
        if(stage==GuideStage.THEME) initialTone=model.state.value.appearance.westTone
        preparedStage=stage
    }

    fun observe() {
        if (!stage.practice || preparedStage!=stage || preparing || actedStage!=stage || done) return
        val state=model.state.value
        val performed = when(stage) {
            GuideStage.CAMPUS -> state.campus==Campus.SHAHE
            GuideStage.SLOTS -> state.filters.selectedSlots.size>=2
            GuideStage.SWIPE -> "swipe" in signals
            GuideStage.FAVORITE -> state.profiles["01:2-201"]?.favorite==true
            GuideStage.ADD -> goal.savedWindows.isNotEmpty()
            GuideStage.FROM_NOW -> goal.start==601
            GuideStage.IMPORT -> anchors["template_import_list"]?.window=="sheet"
            GuideStage.EXPECT -> entry>0 && goal.expectedRooms.isNotEmpty()
            GuideStage.GENERATE -> model.planning.executions.value[Campus.XITUCHENG]?.result!=null
            GuideStage.THEME -> state.appearance.westTone!=initialTone
            else -> false
        }
        if(performed) completed.value=completed.value+stage.name
    }

    fun demonstrate() {
        recordAction(stage)
        demonstration++
        when(stage) {
            GuideStage.CAMPUS -> model.setCampus(Campus.SHAHE)
            GuideStage.SLOTS -> model.setFilters(model.state.value.filters.copy(selectedSlots=setOf(1,2,3)))
            GuideStage.SEARCH -> model.setFilters(SearchFilters())
            GuideStage.FILTER_TAGS -> editingFilterTags(true)
            GuideStage.PLANNING -> enteredPlanning()
            GuideStage.TEMPLATES -> enteredTemplates()
            GuideStage.WHEELS -> if(entry==0) {
                enteredPlannerPage()
            } else goal=goal.copy(start=630,end=750)
            GuideStage.FAVORITE -> model.toggleFavorite(data.snapshot(Campus.XITUCHENG).classrooms.first())
            GuideStage.TAGS,GuideStage.NOTE -> {
                enteredEditor();model.selectRoom(data.snapshot(Campus.XITUCHENG).classrooms.first().id)
            }
            GuideStage.MARKS -> {
                if(entry==0) enteredSettings() else enteredMarks()
            }
            GuideStage.EXCLUDE -> model.toggleExcluded(data.snapshot(Campus.XITUCHENG).classrooms.first())
            GuideStage.ADD -> PlanningSchedule.add(goal,600).goal?.let(::updateGoal)
            GuideStage.PERIOD -> openedPeriod()
            GuideStage.SAVE_TEMPLATE -> openedTemplate()
            GuideStage.PAST -> rejectedPeriod(PlanningSchedule.add(goal,600).error?.problem)
            GuideStage.IMPORT -> openedImport()
            GuideStage.EXPECT -> {
                openedExpected()
                goal=goal.copy(expectedRooms=goal.expectedRooms+data.snapshot(Campus.XITUCHENG).classrooms.first().id)
            }
            GuideStage.FROM_NOW -> PlanningTimeActions.startFromNow(goal,clock)?.let(::updateGoal)
            GuideStage.DURATION -> if(entry==0) {
                rejectedPeriod(PlanningSchedule.add(goal,600).error?.problem)
            } else PlanningTimeActions.endAfterHours(goal,2)?.let(::updateGoal)
            GuideStage.FLEXIBLE -> goal=goal.copy(flexible=!goal.flexible)
            GuideStage.GENERATE -> requestPlanning()
            GuideStage.STOP -> model.cancelPlanning()
            GuideStage.THEME -> {
                enteredSettings()
                model.setCampusTone(Campus.XITUCHENG,model.state.value.appearance.westTone.step(1))
            }
            GuideStage.DARK -> model.setDarkMode(!model.state.value.appearance.isDark(false))
            GuideStage.STRIP -> model.setPeriodStrip(model.state.value.periodStrip.copy(layout=PeriodStripLayout.TIME_PROPORTIONAL))
            GuideStage.NUMBERS -> model.setPeriodStrip(model.state.value.periodStrip.copy(showNumbers=!model.state.value.periodStrip.showNumbers))
            GuideStage.DEFAULT_CAMPUS -> model.setDefaultCampus(Campus.SHAHE)
            else -> Unit // Modal/gesture demonstrations are handled by the mounted component.
        }
    }
}

internal val LocalUsageGuide = staticCompositionLocalOf<GuideRuntime?> { null }
internal val LocalGuideWindow = staticCompositionLocalOf { "root" }
