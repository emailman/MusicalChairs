import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@Composable
fun GameScreen() {
    var gameState by remember { mutableStateOf(GameState.IDLE) }
    var resetToken by remember { mutableIntStateOf(0) }

    MaterialTheme {
        Scaffold(
            topBar = { GameHeader(gameState) },
            bottomBar = {
                GameControls(
                    gameState = gameState,
                    onStartStopClick = {
                        gameState = when (gameState) {
                            GameState.IDLE -> GameState.PLAYING
                            GameState.PLAYING -> GameState.STOPPING
                            GameState.STOPPING -> GameState.PLAYING
                        }
                    },
                    onResetClick = {
                        gameState = GameState.IDLE
                        resetToken++
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFFAFAFA)),
                contentAlignment = Alignment.Center
            ) {
                GameArena(gameState = gameState, resetToken = resetToken) { newState ->
                    gameState = newState
                }
            }
        }
    }
}

enum class GameState { IDLE, PLAYING, STOPPING }

@Composable
fun GameHeader(gameState: GameState) {
    TopAppBar(
        title = {
            Column {
                Text("Musical Chairs", fontSize = 20.sp)
                val statusText = when (gameState) {
                    GameState.IDLE -> "Status: Waiting to Start"
                    GameState.PLAYING -> "Status: Music Playing..."
                    GameState.STOPPING -> "Status: Scramble!"
                }
                Text(statusText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }
        },
        actions = {
            Text("Round: 1", modifier = Modifier.padding(end = 16.dp))
            Text("Players: 10", modifier = Modifier.padding(end = 16.dp))
        },
        backgroundColor = Color(0xFF6200EE),
        contentColor = Color.White,
        elevation = 4.dp
    )
}

@Composable
fun GameArena(
    gameState: GameState,
    resetToken: Int,
    onAnimationFinished: (GameState) -> Unit
) {
    val playerColors = remember {
        listOf(
            Color.Red, Color.Blue, Color.Green, Color.Magenta, Color.Cyan,
            Color.Yellow, Color.Black, Color.Gray, Color(0xFF6200EE), Color(0xFF03DAC5)
        )
    }

    var missingChairIndices by remember { mutableStateOf(setOf<Int>()) }

    // Keep ACTIVE players in order (this is what fixes round-2+ correctness)
    var activePlayerIndices by remember { mutableStateOf((0..9).toList()) }

    var currentRoundMissingChairIndex by remember { mutableStateOf<Int?>(null) }

    val mainProgress = remember { Animatable(0f) }

    LaunchedEffect(resetToken) {
        missingChairIndices = emptySet()
        activePlayerIndices = (0..9).toList()
        currentRoundMissingChairIndex = null
        mainProgress.snapTo(0f)
    }

    val rowYOffsets = listOf(-140f, -70f, 0f, 70f, 140f)
    val leftX = -100f
    val rightX = 100f

    val pathPoints = remember {
        listOf(
            Offset(leftX, rowYOffsets[0]),
            Offset(rightX, rowYOffsets[0]),
            Offset(rightX, rowYOffsets[1]),
            Offset(rightX, rowYOffsets[2]),
            Offset(rightX, rowYOffsets[3]),
            Offset(rightX, rowYOffsets[4]),
            Offset(leftX, rowYOffsets[4]),
            Offset(leftX, rowYOffsets[3]),
            Offset(leftX, rowYOffsets[2]),
            Offset(leftX, rowYOffsets[1])
        )
    }

    fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return (1 - fraction) * start + fraction * stop
    }

    fun quadraticBezier(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
        val x = (1 - t) * (1 - t) * p0.x + 2 * (1 - t) * t * p1.x + t * t * p2.x
        val y = (1 - t) * (1 - t) * p0.y + 2 * (1 - t) * t * p1.y + t * t * p2.y
        return Offset(x, y)
    }

    // IMPORTANT: index here is the *order among active players*, not the original player id
    fun getPosition(orderIndex: Int, progress: Float): Offset {
        val effectivePos = (progress + orderIndex) % 10f
        val currentSlot = effectivePos.toInt()
        val nextSlot = (currentSlot + 1) % 10
        val fraction = effectivePos - currentSlot

        val p1 = pathPoints[currentSlot]
        val p2 = pathPoints[nextSlot]

        if (currentSlot == 0) {
            val controlPoint = Offset(0f, -300f)
            return quadraticBezier(p1, controlPoint, p2, fraction)
        } else if (currentSlot == 5) {
            val controlPoint = Offset(0f, 300f)
            return quadraticBezier(p1, controlPoint, p2, fraction)
        }

        return Offset(
            x = lerp(p1.x, p2.x, fraction),
            y = lerp(p1.y, p2.y, fraction)
        )
    }

    LaunchedEffect(gameState) {
        if (gameState == GameState.PLAYING) {
            if (currentRoundMissingChairIndex == null) {
                launch {
                    delay(5000)
                    if (!isActive || currentRoundMissingChairIndex != null) return@launch

                    val availableChairs = (0..9).filter { it !in missingChairIndices }
                    if (availableChairs.isNotEmpty()) {
                        val picked = availableChairs.random()
                        currentRoundMissingChairIndex = picked
                        missingChairIndices = missingChairIndices + picked
                    }
                }
            }

            while (isActive) {
                mainProgress.animateTo(
                    targetValue = mainProgress.value + 10f,
                    animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                )
                mainProgress.snapTo(mainProgress.value % 10f)
            }
        } else if (gameState == GameState.STOPPING) {
            val current = mainProgress.value
            val target = kotlin.math.ceil(current)

            // Animate to the stopping point first (so "who lands where" is consistent)
            mainProgress.animateTo(
                targetValue = target,
                animationSpec = spring(stiffness = Spring.StiffnessLow)
            )
            mainProgress.snapTo(target % 10f)

            val roundMissing = currentRoundMissingChairIndex
            if (roundMissing != null && activePlayerIndices.isNotEmpty()) {
                // Path point -> chair index mapping (same as before)
                val pathToChairMap = listOf(0, 1, 3, 5, 7, 9, 8, 6, 4, 2)

                val targetMod = (target % 10).toInt()

                // Find which ACTIVE player (by order) lands on the missing chair
                var eliminatedOrderIndex: Int? = null
                for (orderIndex in activePlayerIndices.indices) {
                    val pathPoint = (targetMod + orderIndex) % 10
                    val chairIndex = pathToChairMap[pathPoint]
                    if (chairIndex == roundMissing) {
                        eliminatedOrderIndex = orderIndex
                        break
                    }
                }

                if (eliminatedOrderIndex != null) {
                    // Remove that player from the active list (this is the real "elimination")
                    activePlayerIndices = activePlayerIndices.toMutableList().also { it.removeAt(eliminatedOrderIndex) }
                }

                currentRoundMissingChairIndex = null
            }

            onAnimationFinished(GameState.IDLE)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val arenaBackgroundColor = Color(0xFFE0E0E0)
        Box(
            modifier = Modifier
                .size(600.dp)
                .clip(RectangleShape)
                .background(arenaBackgroundColor)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            var chairCounter = 0
            rowYOffsets.forEach { y ->
                val isLeftMissing = chairCounter in missingChairIndices
                val leftColor = if (isLeftMissing) arenaBackgroundColor else Color(0xFFFF9800)
                val leftBorderColor = if (isLeftMissing) arenaBackgroundColor else Color(0xFFE65100)

                Chair(
                    modifier = Modifier.offset(x = (-30).dp, y = y.dp),
                    color = leftColor,
                    borderColor = leftBorderColor
                )
                chairCounter++

                val isRightMissing = chairCounter in missingChairIndices
                val rightColor = if (isRightMissing) arenaBackgroundColor else Color(0xFFFF9800)
                val rightBorderColor = if (isRightMissing) arenaBackgroundColor else Color(0xFFE65100)

                Chair(
                    modifier = Modifier.offset(x = 30.dp, y = y.dp),
                    color = rightColor,
                    borderColor = rightBorderColor
                )
                chairCounter++
            }

            playerColors.forEachIndexed { originalIndex, color ->
                // (No change needed here; we will render based on active list below)
            }

            // Replace your player rendering loop with this:
            // (Keep it in the same place you currently render players)
            // Dynamic Layer: Players
            activePlayerIndices.forEachIndexed { orderIndex, playerIndex ->
                val pos = getPosition(orderIndex, mainProgress.value)
                Player(
                    modifier = Modifier.offset(x = pos.x.dp, y = pos.y.dp),
                    color = playerColors[playerIndex]
                )
            }
        }
    }
}

@Composable
fun Chair(modifier: Modifier = Modifier, color: Color, borderColor: Color) {
    Box(
        modifier = modifier
            .size(50.dp)
            .background(color, shape = RoundedCornerShape(4.dp))
            .border(2.dp, borderColor, shape = RoundedCornerShape(4.dp))
    )
}

@Composable
fun Player(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Color.White, CircleShape)
    )
}

@Composable
fun GameControls(
    gameState: GameState,
    onStartStopClick: () -> Unit,
    onResetClick: () -> Unit
) {
    BottomAppBar(
        backgroundColor = Color.White,
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* TODO: Settings */ }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }

            Button(
                onClick = onStartStopClick,
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp),
                enabled = gameState != GameState.STOPPING
            ) {
                val icon = if (gameState == GameState.PLAYING)
                    Icons.Default.Close else Icons.Default.PlayArrow
                val text = if (gameState == GameState.PLAYING) "Stop Music" else "Start Music"
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text)
            }

            IconButton(onClick = onResetClick) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
            }
        }
    }
}
