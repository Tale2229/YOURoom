/*
 * Copyright 2024 The Chromium Authors
 * Copyright 2025 The Chromium Authors
 * BSD-style license: assets/licenses/chromium_dino/LICENSE.txt.
 *
 * YOURoom native adaptation of Chromium Dino's sprite geometry, collision boxes,
 * distance meter and horizon. Uses a local, time-based simulation and lifecycle
 * pause instead of Chromium's TypeScript / browser runtime. See NOTICE.txt.
 */
package com.example.youroom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

internal enum class RunnerPhase { READY, RUNNING, PAUSED, OVER }
internal enum class RunnerObstacleKind { SMALL_CACTUS, LARGE_CACTUS, BIRD }
internal data class RunnerObstacle(
    val x: Float, val kind: RunnerObstacleKind, val count: Int = 1, val bottom: Float = 0f,
) {
    val width get() = when (kind) { RunnerObstacleKind.SMALL_CACTUS -> 17f * count; RunnerObstacleKind.LARGE_CACTUS -> 25f * count; RunnerObstacleKind.BIRD -> 46f }
    val height get() = when (kind) { RunnerObstacleKind.SMALL_CACTUS -> 35f; RunnerObstacleKind.LARGE_CACTUS -> 50f; RunnerObstacleKind.BIRD -> 40f }
}

private data class RunnerBox(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun offset(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
    fun overlaps(other: RunnerBox) = x + width > other.x && x < other.x + other.width &&
        y + height > other.y && y < other.y + other.height
}

/** Chromium's individual head / torso / feet boxes, in the same coordinates as its sprites. */
private object RunnerGeometry {
    const val dinoX = 24f
    const val dinoHeight = 47f
    val dino = listOf(
        RunnerBox(22f, 0f, 17f, 16f), RunnerBox(1f, 18f, 30f, 9f),
        RunnerBox(10f, 35f, 14f, 8f), RunnerBox(1f, 24f, 29f, 5f),
        RunnerBox(5f, 30f, 21f, 4f), RunnerBox(9f, 34f, 15f, 4f),
    )
    private val small = listOf(RunnerBox(0f, 7f, 5f, 27f), RunnerBox(4f, 0f, 6f, 34f), RunnerBox(10f, 4f, 7f, 14f))
    private val large = listOf(RunnerBox(0f, 12f, 7f, 38f), RunnerBox(8f, 0f, 7f, 49f), RunnerBox(13f, 10f, 10f, 38f))
    private val bird = listOf(
        RunnerBox(15f, 15f, 16f, 5f), RunnerBox(18f, 21f, 24f, 6f),
        RunnerBox(2f, 14f, 4f, 3f), RunnerBox(6f, 10f, 4f, 7f), RunnerBox(10f, 8f, 6f, 9f),
    )
    fun obstacle(o: RunnerObstacle): List<RunnerBox> {
        val template = when (o.kind) { RunnerObstacleKind.SMALL_CACTUS -> small; RunnerObstacleKind.LARGE_CACTUS -> large; RunnerObstacleKind.BIRD -> bird }
        return template.mapIndexed { index, box ->
            val grouped = if (o.count <= 1) box else when (index) {
                1 -> box.copy(width = o.width - template[0].width - template[2].width)
                2 -> box.copy(x = o.width - box.width)
                else -> box
            }
            grouped.offset(o.x, o.bottom - o.height)
        }
    }
}

// Fit the original-sized dinosaur plus its complete jump arc in a compact planner panel.
private fun runnerScale(width: Float, height: Float, density: Float) =
    min(density, min(width / 240f, height / 184f)).coerceAtLeast(.01f)

/** Rounds stay in the workspace; the caller restores / persists the installation's best score. */
@Stable
internal class DinosaurRun(private val random: Random = Random.Default,
    private val onBestScore: (Int) -> Unit = {}) {
    var phase by mutableStateOf(RunnerPhase.READY)
    var elapsed by mutableFloatStateOf(0f)
    var altitude by mutableFloatStateOf(0f)
    var distance by mutableFloatStateOf(0f)
        private set
    var bestScore by mutableIntStateOf(0)
        private set
    var scoreFlash by mutableFloatStateOf(0f)
        private set
    private var velocity = 0f
    private var viewportWidth = 320f
    private var currentSpeed = 216f
    private var nextObstacle = 2.3f
    private var scoreMilestone = 0
    private var crashedAt = 0L
    var obstacles by mutableStateOf(emptyList<RunnerObstacle>())
    val score get() = (distance * .025f).roundToInt().coerceAtMost(99_999)

    fun updateViewport(width: Float) { if (width.isFinite() && width > 120f) viewportWidth = width }
    private fun initialSpeed() = min(6f, 6f * viewportWidth / 600f * 1.2f).coerceAtLeast(3f) * 60f
    fun restoreBestScore(saved: Int) { bestScore=maxOf(bestScore,saved.coerceIn(0,99_999)) }
    private fun rememberBestScore() {
        if(score>bestScore) { bestScore=score;onBestScore(bestScore) }
    }
    fun pause() {
        if (phase == RunnerPhase.RUNNING) { rememberBestScore();phase = RunnerPhase.PAUSED }
    }
    fun jump() {
        if (phase == RunnerPhase.PAUSED) { phase = RunnerPhase.RUNNING; return }
        // A rapid second tap must not immediately hide the crashed pose and GAME OVER.
        if (phase == RunnerPhase.OVER && System.nanoTime() - crashedAt < 350_000_000L) return
        if (phase == RunnerPhase.OVER || phase == RunnerPhase.READY) {
            elapsed = 0f; distance = 0f; altitude = 0f; velocity = 0f
            currentSpeed = initialSpeed()
            scoreFlash = 0f; scoreMilestone = 0; obstacles = emptyList(); nextObstacle = 2.3f
        }
        phase = RunnerPhase.RUNNING
        if (altitude <= .01f) velocity = 600f + currentSpeed / 10f
    }
    fun tick(dt: Float, width: Float) {
        if (phase != RunnerPhase.RUNNING || !width.isFinite() || width <= 120f || !dt.isFinite() || dt <= 0f) return
        updateViewport(width)
        var remaining = dt.coerceAtMost(.05f)
        while (remaining > 0f && phase == RunnerPhase.RUNNING) {
            val step = min(remaining, 1f / 120f)
            advance(step, width)
            remaining -= step
        }
    }
    private fun advance(dt: Float, width: Float) {
        // Chromium's 6px/frame base speed, 13px/frame cap and mobile-width adjustment.
        val mobileBase = initialSpeed()
        // 1.5x the previous acceleration: new 40s == previous 60s, same base / cap.
        currentSpeed = min(780f, mobileBase + elapsed * 5.4f)
        elapsed += dt
        distance += currentSpeed * dt
        scoreFlash = (scoreFlash - dt).coerceAtLeast(0f)
        if (score / 100 > scoreMilestone) { scoreMilestone = score / 100; scoreFlash = 1.5f }
        if (altitude > 0f || velocity > 0f) {
            altitude = (altitude + velocity * dt - 1080f * dt * dt).coerceAtLeast(0f)
            velocity = if (altitude > 0f) velocity - 2160f * dt else 0f
        }
        obstacles = obstacles.map { it.copy(x = it.x - currentSpeed * dt) }.filter { it.x + it.width > -8f }
        nextObstacle -= dt
        if (nextObstacle <= 0f) {
            val speedFrames = currentSpeed / 60f
            val kind = when {
                speedFrames >= 8.5f && random.nextInt(5) == 0 -> RunnerObstacleKind.BIRD
                random.nextBoolean() -> RunnerObstacleKind.LARGE_CACTUS
                else -> RunnerObstacleKind.SMALL_CACTUS
            }
            val multipleSpeed = if (kind == RunnerObstacleKind.LARGE_CACTUS) 7f else 4f
            val count = if (kind == RunnerObstacleKind.BIRD || speedFrames < multipleSpeed) 1 else random.nextInt(1, 4)
            // Mobile birds can be jumped over or run under; no mandatory hidden duck gesture.
            val bottom = if (kind == RunnerObstacleKind.BIRD && random.nextBoolean()) -50f else 0f
            val obstacle = RunnerObstacle(width + 15f, kind, count, bottom)
            obstacles = obstacles + obstacle
            val minGap = obstacle.width * speedFrames + 120f
            val gap = minGap * (1f + random.nextFloat() * .5f)
            nextObstacle = ((gap + obstacle.width) / currentSpeed).coerceAtLeast(.85f)
        }
        val dino = RunnerGeometry.dino.map { it.offset(RunnerGeometry.dinoX, -altitude - RunnerGeometry.dinoHeight) }
        if (obstacles.any { o -> RunnerGeometry.obstacle(o).any { box -> dino.any { it.overlaps(box) } } }) {
            phase = RunnerPhase.OVER
            rememberBestScore()
            scoreFlash = 0f
            crashedAt = System.nanoTime()
        }
    }
}

@Composable
internal fun DinosaurRunner(run: DinosaurRun, active: Boolean, modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface) {
    val guide=LocalUsageGuide.current
    val sheet = rememberDinoSpriteSheet()
    val ink = MaterialTheme.colorScheme.primary
    val colors = remember(ink, backgroundColor) { dinoSpriteColors(ink, backgroundColor) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) run.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); run.pause() }
    }
    var viewport by remember { mutableFloatStateOf(320f) }
    val pixelDensity = LocalDensity.current.density
    LaunchedEffect(active, resumed, run.phase) {
        if (!active || !resumed) { run.pause(); return@LaunchedEffect }
        if (run.phase != RunnerPhase.RUNNING) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (isActive && run.phase == RunnerPhase.RUNNING) {
            val frame = withFrameNanos { it }
            val delta = ((frame - last) / 1_000_000_000f).coerceIn(0f, .04f)
            last = frame
            run.tick(delta, viewport)
        }
    }
    Box(modifier.fillMaxSize().padding(horizontal = 16.dp).clipToBounds()) {
        Canvas(Modifier.fillMaxSize().guideTag("planner_dinosaur")
            .onSizeChanged {
                viewport = it.width / runnerScale(it.width.toFloat(), it.height.toFloat(), pixelDensity)
                run.updateViewport(viewport)
            }
            .pointerInput(active,guide) { detectTapGestures(onTap = { if (active) { run.jump();guide?.jumped() } }) }
            .onKeyEvent { event ->
                if (active && event.type == KeyEventType.KeyDown && event.key in listOf(Key.Spacebar, Key.DirectionUp)) { run.jump();guide?.jumped(); true } else false
            }.semantics {
                contentDescription = "Google Dino，点击跳跃"
                stateDescription = when (run.phase) {
                    RunnerPhase.READY -> "尚未开始"
                    RunnerPhase.RUNNING -> "进行中，得分 ${run.score}"
                    RunnerPhase.PAUSED -> "已暂停"
                    RunnerPhase.OVER -> "GAME OVER，本局 ${run.score} 分，最高分 ${run.bestScore} 分"
                }
                onClick("跳跃或开始") { if (active) { run.jump();guide?.jumped() }; active }
            }.focusable(active)) {
            val scale = runnerScale(size.width, size.height, density)
            val width = size.width / scale
            val height = size.height / scale
            val foot = height * .86f
            repeat(3) { index ->
                val cycle = width + 180f
                val x = ((index * cycle / 3f + 110f - run.distance * .13f) % cycle + cycle) % cycle - 46f
                drawDinoSprite(sheet, ChromiumDinoSprites.cloud, x, foot - 110f + index % 2 * 20f,
                    scale, colors, .42f)
            }
            // The native ground tile already contains irregular bumps, hollows and small specks.
            val tile = floor(run.distance / 600f).toInt()
            val offset = -(run.distance % 600f)
            repeat(ceil(width / 600f).toInt() + 1) { index ->
                val id = tile + index
                drawDinoSprite(sheet, ChromiumDinoSprites.horizon(((id xor (id ushr 1)) and 1) == 1),
                    offset + index * 600f, foot - 13f, scale, colors, .82f)
            }
            val dino = when {
                run.phase == RunnerPhase.OVER -> ChromiumDinoSprites.crashed
                run.altitude > .01f || run.phase == RunnerPhase.READY -> ChromiumDinoSprites.standing
                else -> ChromiumDinoSprites.running[(run.elapsed * 12f).toInt() % 2]
            }
            // Feet and cactus bases sit below the distant horizon line, as in Chromium.
            drawDinoSprite(sheet, dino, RunnerGeometry.dinoX, foot - run.altitude - 47f, scale, colors)
            run.obstacles.forEach { obstacle ->
                val sprite = if (obstacle.kind == RunnerObstacleKind.BIRD)
                    ChromiumDinoSprites.birds[(run.elapsed * 6f).toInt() % 2]
                else ChromiumDinoSprites.cactus(obstacle.kind == RunnerObstacleKind.LARGE_CACTUS, obstacle.count)
                drawDinoSprite(sheet, sprite, obstacle.x, foot + obstacle.bottom - obstacle.height, scale, colors)
            }
            drawDinoText(sheet, "HI " + run.bestScore.toString().padStart(5, '0'), width - 165f, 8f, scale, colors, .72f)
            if (run.scoreFlash <= 0f || ((1.5f - run.scoreFlash) * 4f).toInt() % 2 == 1)
                drawDinoText(sheet, run.score.toString().padStart(5, '0'), width - 65f, 8f, scale, colors)
            if (run.phase == RunnerPhase.OVER) {
                val textY = (height - 25f) / 3f
                drawDinoSprite(sheet, ChromiumDinoSprites.gameOver, (width - 191f) / 2f, textY, scale, colors)
                drawDinoSprite(sheet, ChromiumDinoSprites.restart, (width - 36f) / 2f, textY + 24f, scale, colors)
            }
        }
        if (run.phase == RunnerPhase.READY || run.phase == RunnerPhase.PAUSED) Text(
            if (run.phase == RunnerPhase.READY) "轻点开始" else "轻点继续",
            Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

