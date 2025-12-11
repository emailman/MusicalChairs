import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        Box(
            modifier = Modifier
                .size(600.dp)
                .clip(RectangleShape)
                .background(Color(0xFFE0E0E0)) 
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Render Chairs Absolutely to match the Rows
            rowYOffsets.forEach { y ->
                Chair(modifier = Modifier.offset(x = (-30).dp, y = y.dp))
                Chair(modifier = Modifier.offset(x = 30.dp, y = y.dp))
            }

            // Dynamic Layer: Players
            playerColors.forEachIndexed { index, color ->
                val pos = getPosition(index, mainProgress.value)
                Player(
                    modifier = Modifier.offset(x = pos.x.dp, y = pos.y.dp),
                    color = color
                )
            }
        }
    }
}

@Composable
fun Chair(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(50.dp) 
            .background(Color(0xFFFF9800), shape = RoundedCornerShape(4.dp))
            .border(2.dp, Color(0xFFE65100), shape = RoundedCornerShape(4.dp))
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
