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
                GameArena(gameState = gameState) { newState ->
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
fun GameArena(gameState: GameState, onAnimationFinished: (GameState) -> Unit) {
    val playerColors = remember {
        listOf(
            Color.Red, Color.Blue, Color.Green, Color.Magenta, Color.Cyan,
            Color.Yellow, Color.Black, Color.Gray, Color(0xFF6200EE), Color(0xFF03DAC5)
        )
    }

    var missingChairIndex by remember { mutableStateOf<Int?>(null) } // [NEW] Track missing chair
    var eliminatedPlayerIndex by remember { mutableStateOf<Int?>(null) } // [NEW] Track eliminated player

    val mainProgress = remember { Animatable(0f) }

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

    LaunchedEffect(gameState) {
        if (gameState == GameState.PLAYING) {
            // [NEW] Reset and start timer for disappearing chair
            missingChairIndex = null
            eliminatedPlayerIndex = null
            launch {
                delay(5000)
                if (isActive) {
                    missingChairIndex = (0..9).random()
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
            
            // Calculate which player will land on the missing chair
            if (missingChairIndex != null) {
                // Map from path point index to chair index
                // Path: top-left(0) → top-right(1) → right-side-down(2-5) → bottom-left(6) → left-side-up(7-9)
                // Chairs: left-right pairs from top to bottom (0-9)
                val pathToChairMap = listOf(0, 1, 3, 5, 7, 9, 8, 6, 4, 2)
                
                // When animation stops at 'target', player at index 'i' will be at path point (target + i) % 10
                // We need to find which player's path point maps to the missing chair
                val targetMod = (target % 10).toInt()
                
                // Find which player index lands on the missing chair
                for (playerIndex in 0..9) {
                    val pathPoint = (targetMod + playerIndex) % 10
                    val chairIndex = pathToChairMap[pathPoint]
                    if (chairIndex == missingChairIndex) {
                        eliminatedPlayerIndex = playerIndex
                        break
                    }
                }
            }

            mainProgress.animateTo(
                targetValue = target,
                animationSpec = spring(stiffness = Spring.StiffnessLow)
            )
            mainProgress.snapTo(target % 10f)
            onAnimationFinished(GameState.IDLE)
        }
    }

    fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return (1 - fraction) * start + fraction * stop
    }

    // Cubic Bezier helper for cleaner arcs, or simple Quadratic
    fun quadraticBezier(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
        val x = (1 - t) * (1 - t) * p0.x + 2 * (1 - t) * t * p1.x + t * t * p2.x
        val y = (1 - t) * (1 - t) * p0.y + 2 * (1 - t) * t * p1.y + t * t * p2.y
        return Offset(x, y)
    }

    fun getPosition(index: Int, progress: Float): Offset {
        val effectivePos = (progress + index) % 10f
        val currentSlot = effectivePos.toInt()
        val nextSlot = (currentSlot + 1) % 10
        val fraction = effectivePos - currentSlot
        
        val p1 = pathPoints[currentSlot]
        val p2 = pathPoints[nextSlot]

        // Top Crossing (0 -> 1)
        if (currentSlot == 0) {
            val controlPoint = Offset(0f, -300f) // Arc much higher
            return quadraticBezier(p1, controlPoint, p2, fraction)
        }
        // Bottom Crossing (5 -> 6)
        else if (currentSlot == 5) {
            val controlPoint = Offset(0f, 300f) // Arc much lower
            return quadraticBezier(p1, controlPoint, p2, fraction)
        }
        
        // Linear interpolation for side movements
        return Offset(
            x = lerp(p1.x, p2.x, fraction),
            y = lerp(p1.y, p2.y, fraction)
        )
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
            // Render Chairs Absolutely to match the Rows
            // Map 0..9 indices to the chairs in order (Top-Left down, then Bottom-Right up? Or simple row order?)
            // Based on pathPoints, logic for 'chairs' isn't explicitly indexed 0..9 in the original code, 
            // but let's assign them indices 0..9 for the purpose of "random chair".
            // Left col: 0, 1, 2, 3, 4 (top to bottom) -> Y offsets -140..140
            // Right col: 5, 6, 7, 8, 9 (top to bottom) -> Y offsets -140..140
            // Wait, original code:
            // rowYOffsets.forEach { y ->
            //    Chair(left)
            //    Chair(right)
            // }
            // That renders 10 chairs. Let's assign indices:
            // i=0: left, y[0]; i=1: right, y[0]
            // i=2: left, y[1]; i=3: right, y[1]
            // ...
            
            var chairCounter = 0
            rowYOffsets.forEach { y ->
                // Left Chair
                val isLeftMissing = missingChairIndex == chairCounter
                val leftColor = if (isLeftMissing) arenaBackgroundColor else Color(0xFFFF9800)
                val leftBorderColor = if (isLeftMissing) arenaBackgroundColor else Color(0xFFE65100) // Hide border too
                
                Chair(
                    modifier = Modifier.offset(x = (-30).dp, y = y.dp),
                    color = leftColor,
                    borderColor = leftBorderColor
                )
                chairCounter++

                // Right Chair
                val isRightMissing = missingChairIndex == chairCounter
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
            playerColors.forEachIndexed { index, color ->
                if (index != eliminatedPlayerIndex) {
                    val pos = getPosition(index, mainProgress.value)
                    Player(
                        modifier = Modifier.offset(x = pos.x.dp, y = pos.y.dp),
                        color = color
                    )
                }
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
fun GameControls(gameState: GameState, onStartStopClick: () -> Unit) {
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
                val icon = if (gameState == GameState.PLAYING) Icons.Default.Close else Icons.Default.PlayArrow
                val text = if (gameState == GameState.PLAYING) "Stop Music" else "Start Music"
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text)
            }

            IconButton(onClick = { /* TODO: Reset */ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
            }
        }
    }
}
