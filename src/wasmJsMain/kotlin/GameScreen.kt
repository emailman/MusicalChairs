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
    var debugMessages by remember { mutableStateOf(emptyList<String>()) }
    var isButtonEnabled by remember { mutableStateOf(true) }

    MaterialTheme {
        Scaffold(
            topBar = { GameHeader(gameState) },
            bottomBar = {
                GameControls(
                    gameState = gameState,
                    isButtonEnabled = isButtonEnabled,
                    onStartStopClick = {
                        gameState = when (gameState) {
                            GameState.IDLE -> {
                                isButtonEnabled = false
                                GameState.PLAYING
                            }
                            GameState.PLAYING -> GameState.STOPPING
                            GameState.STOPPING -> GameState.PLAYING
                        }
                    },
                    onResetClick = {
                        gameState = GameState.IDLE
                        isButtonEnabled = true
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
                GameArena(
                    gameState = gameState,
                    resetToken = resetToken,
                    onDebugMessage = { msg -> debugMessages = debugMessages + msg },
                    onAnimationFinished = { newState ->
                        gameState = newState
                    },
                    onChairRemoved = {
                        isButtonEnabled = true
                    }
                )

                // Debug panel on left side
                if (debugMessages.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .width(350.dp)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.95f))
                            .padding(8.dp)
                            .align(Alignment.CenterStart)
                    ) {
                        Text("Debug Log:", color = Color.White, fontSize = 14.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1E1E1E))
                                .padding(4.dp)
                        ) {
                            Column {
                                debugMessages.forEach { msg ->
                                    Text(msg, color = Color.Green, fontSize = 9.sp)
                                }
                            }
                        }
                    }
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
    onDebugMessage: (String) -> Unit = {},
    onAnimationFinished: (GameState) -> Unit,
    onChairRemoved: () -> Unit = {}
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

    // Calculate the lowest row that still has at least one chair
    val lowestActiveRowIndex = remember(missingChairIndices) {
        when {
            8 !in missingChairIndices || 9 !in missingChairIndices -> 4 // Bottom row
            6 !in missingChairIndices || 7 !in missingChairIndices -> 3 // 4th row
            4 !in missingChairIndices || 5 !in missingChairIndices -> 2 // Middle row
            2 !in missingChairIndices || 3 !in missingChairIndices -> 1 // 2nd row
            else -> 0 // Top row only
        }
    }

    // Dynamically calculate path points - keeping original structure but shortening from bottom
    val pathPoints = remember(lowestActiveRowIndex) {
        buildList {
            add(Offset(leftX, rowYOffsets[0]))   // 0: Top-left
            add(Offset(rightX, rowYOffsets[0]))  // 1: Top-right

            // Right side: add points for each active row below top
            for (rowIndex in 1..lowestActiveRowIndex) {
                add(Offset(rightX, rowYOffsets[rowIndex]))
            }

            // Left side bottom: add the corner point at lowest active row
            add(Offset(leftX, rowYOffsets[lowestActiveRowIndex]))

            // Left side: add points going back up (excluding top and bottom)
            for (rowIndex in (lowestActiveRowIndex - 1) downTo 1) {
                add(Offset(leftX, rowYOffsets[rowIndex]))
            }
        }
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
    // Progress is ALWAYS in 10-point space (0-10 for full loop)
    fun getPosition(orderIndex: Int, progress: Float): Offset {
        val pathSize = pathPoints.size
        if (pathSize < 2) return Offset.Zero

        // Map from 10-point space to actual path space
        // progress is in range [0, 10), we need to map to [0, pathSize)
        val progressInPathSpace = (progress / 10f) * pathSize.toFloat()
        val orderOffsetInPathSpace = (orderIndex.toFloat() / 10f) * pathSize.toFloat()

        val effectivePos = (progressInPathSpace + orderOffsetInPathSpace) % pathSize.toFloat()
        val currentSlot = effectivePos.toInt()
        val nextSlot = (currentSlot + 1) % pathSize
        val fraction = effectivePos - currentSlot

        val p1 = pathPoints[currentSlot]
        val p2 = pathPoints[nextSlot]

        // Curve at top (slot 0 -> slot 1: top-left to top-right)
        if (currentSlot == 0) {
            val controlPoint = Offset(0f, -300f)
            return quadraticBezier(p1, controlPoint, p2, fraction)
        }

        // Curve at bottom - this is the transition at the lowest active row
        // From right side to left side
        val bottomTransitionIndex = 1 + lowestActiveRowIndex
        if (currentSlot == bottomTransitionIndex) {
            val controlPoint = Offset(0f, rowYOffsets[lowestActiveRowIndex] + 200f)
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

                    // Define the elimination order: bottom to top, left then right in each row
                    val eliminationOrder = listOf(8, 9, 6, 7, 4, 5, 2, 3, 0, 1)

                    // Find the next chair to eliminate
                    val nextChair = eliminationOrder.firstOrNull { it !in missingChairIndices }

                    if (nextChair != null) {
                        currentRoundMissingChairIndex = nextChair
                        missingChairIndices = missingChairIndices + nextChair
                        onChairRemoved()
                    }
                }
            }

            while (isActive) {
                // Always animate in 10-point space, regardless of actual path size
                mainProgress.animateTo(
                    targetValue = mainProgress.value + 10f,
                    animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                )
                mainProgress.snapTo(mainProgress.value % 10f)
            }
        } else if (gameState == GameState.STOPPING) {
            val current = mainProgress.value
            // target is always in 10-point space
            val target = kotlin.math.ceil(current)

            // Animate to the stopping point first (so "who lands where" is consistent)
            mainProgress.animateTo(
                targetValue = target,
                animationSpec = spring(stiffness = Spring.StiffnessLow)
            )
            mainProgress.snapTo(target % 10f)

            val roundMissing = currentRoundMissingChairIndex
            if (roundMissing != null && activePlayerIndices.isNotEmpty()) {
                onDebugMessage("=== SIMPLIFIED ELIMINATION LOGIC ===")
                onDebugMessage("Chair that was just eliminated: $roundMissing")
                onDebugMessage("Active players: ${activePlayerIndices.toTypedArray().contentToString()}")
                onDebugMessage("Player colors: 0=Red,1=Blue,2=Green,3=Mag,4=Cyan,5=Yellow,6=Black,7=Gray,8=Purple,9=Teal")

                // Map of original path points (0-9) to chair indices
                // This is based on the ORIGINAL full path before any chairs were removed
                val originalPathToChairMap = listOf(0, 1, 3, 5, 7, 9, 8, 6, 4, 2)

                // Since players always stop at integer path positions, find which path point
                // on the ORIGINAL 10-point path corresponds to the eliminated chair
                val pathPointWithEliminatedChair = originalPathToChairMap.indexOf(roundMissing)

                onDebugMessage("Original path point for chair $roundMissing: $pathPointWithEliminatedChair")

                if (pathPointWithEliminatedChair < 0) {
                    onDebugMessage("ERROR: Invalid chair index")
                    currentRoundMissingChairIndex = null
                    onAnimationFinished(GameState.IDLE)
                    return@LaunchedEffect
                }

                // Now determine where each player is on the ORIGINAL 10-point path
                val targetMod = target.toInt() % 10
                onDebugMessage("Target position on original path: $targetMod")

                var eliminatedOrderIndex: Int? = null

                activePlayerIndices.forEachIndexed { orderIndex, playerIndex ->
                    // Calculate which original path point this player occupies
                    val originalPathPoint = (targetMod + orderIndex) % 10
                    val assignedChair = originalPathToChairMap[originalPathPoint]

                    onDebugMessage("Player $playerIndex (order $orderIndex) -> original path point $originalPathPoint -> chair $assignedChair")

                    if (assignedChair == roundMissing) {
                        onDebugMessage("  ^^^ ELIMINATED! This player's chair ($roundMissing) was removed")
                        eliminatedOrderIndex = orderIndex
                    }
                }

                if (eliminatedOrderIndex != null) {
                    val eliminatedPlayerId = activePlayerIndices[eliminatedOrderIndex]
                    onDebugMessage("Eliminating player ID: $eliminatedPlayerId")
                    activePlayerIndices = activePlayerIndices.toMutableList().also { it.removeAt(eliminatedOrderIndex) }
                } else {
                    onDebugMessage("No player eliminated (ERROR!)")
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
    isButtonEnabled: Boolean,
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
                enabled = gameState != GameState.STOPPING && isButtonEnabled
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