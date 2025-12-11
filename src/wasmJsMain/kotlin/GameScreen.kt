import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameScreen() {
    MaterialTheme {
        Scaffold(
            topBar = { GameHeader() },
            bottomBar = { GameControls() }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFFFAFAFA)), // Light gray background
                contentAlignment = Alignment.Center
            ) {
                GameArena()
            }
        }
    }
}

@Composable
fun GameHeader() {
    TopAppBar(
        title = {
            Column {
                Text("Musical Chairs", fontSize = 20.sp)
                Text("Status: Waiting to Start", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }
        },
        actions = {
            Text("Round: 1", modifier = Modifier.padding(end = 16.dp))
            Text("Players: 5", modifier = Modifier.padding(end = 16.dp))
        },
        backgroundColor = Color(0xFF6200EE),
        contentColor = Color.White,
        elevation = 4.dp
    )
}

@Composable
fun GameArena() {
    val playerColors = remember {
        listOf(
            Color.Red, Color.Blue, Color.Green, Color.Magenta, Color.Cyan,
            Color.Yellow, Color.Black, Color.Gray, Color(0xFF6200EE), Color(0xFF03DAC5)
        )
    }

    // Arena Container
    Box(
        modifier = Modifier
            .size(600.dp) // Verified larger size to fit content
            .clip(CircleShape)
            .background(Color(0xFFE0E0E0))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Players (First 5)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                playerColors.take(5).forEach { color ->
                    Player(color = color)
                }
            }

            // Chairs (Central Area - 2 Columns)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Column 1
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(5) { Chair() }
                }
                // Column 2
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(5) { Chair() }
                }
            }

            // Right Players (Last 5)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                playerColors.takeLast(5).forEach { color ->
                    Player(color = color)
                }
            }
        }
    }
}

@Composable
fun Chair(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(Color(0xFFFF9800), shape = RoundedCornerShape(4.dp)) // Square-ish with slight rounded corners
            .border(2.dp, Color(0xFFE65100), shape = RoundedCornerShape(4.dp))
    )
}

@Composable
fun Player(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(40.dp) // Slightly larger player
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Color.White, CircleShape)
    )
}

@Composable
fun GameControls() {
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
                onClick = { /* TODO: Start Game */ },
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start Music")
            }

            IconButton(onClick = { /* TODO: Reset */ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
            }
        }
    }
}
