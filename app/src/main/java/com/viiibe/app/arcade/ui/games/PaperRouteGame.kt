package com.viiibe.app.arcade.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viiibe.app.arcade.data.*
import com.viiibe.app.data.model.RideMetrics
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PaperRouteGame(
    state: PaperRouteState,
    playerMetrics: RideMetrics,
    onStartGame: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF87CEEB)) // Sky blue background
    ) {
        when (state.gameState.phase) {
            GamePhase.WAITING -> {
                WaitingScreen(onStartGame)
            }
            GamePhase.COUNTDOWN -> {
                GameCanvas(state)
                PaperRouteCountdown(state.gameState.countdownValue)
            }
            GamePhase.PLAYING, GamePhase.PAUSED -> {
                GameCanvas(state)
                GameHUD(state)
                if (state.gameState.phase == GamePhase.PAUSED) {
                    PaperRoutePaused()
                }
            }
            GamePhase.FINISHED -> {
                GameCanvas(state)
                // Results handled by parent
            }
        }
    }
}

@Composable
private fun WaitingScreen(onStartGame: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🗞️ PAPER ROUTE",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Controls",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                ControlRow("🚴 Cadence", "Forward Speed")
                ControlRow("🔴 Resistance Knob", "Steer Left/Right")
                ControlRow("⬆️ Quick Resistance UP", "Throw Paper RIGHT")
                ControlRow("⬇️ Quick Resistance DOWN", "Throw Paper LEFT")

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Deliver papers to mailboxes!",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Don't miss houses or hit obstacles!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartGame,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "START PEDALING",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ControlRow(control: String, action: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = control, fontWeight = FontWeight.Medium)
        Text(text = action, color = Color.Gray)
    }
}

@Composable
private fun GameCanvas(state: PaperRouteState) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Draw grass (sides)
        drawRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset.Zero,
            size = Size(width * 0.2f, height)
        )
        drawRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(width * 0.8f, 0f),
            size = Size(width * 0.2f, height)
        )

        // Draw road
        drawRect(
            color = Color(0xFF424242),
            topLeft = Offset(width * 0.2f, 0f),
            size = Size(width * 0.6f, height)
        )

        // Draw road lines (animated based on distance)
        val lineOffset = (state.distanceTraveled * 50) % 100
        for (i in -1..15) {
            val lineY = (i * 100 - lineOffset) * (height / 800)
            drawRect(
                color = Color.Yellow,
                topLeft = Offset(width * 0.49f, lineY),
                size = Size(width * 0.02f, 40f)
            )
        }

        // Draw sidewalks
        drawRect(
            color = Color(0xFFBDBDBD),
            topLeft = Offset(width * 0.15f, 0f),
            size = Size(width * 0.05f, height)
        )
        drawRect(
            color = Color(0xFFBDBDBD),
            topLeft = Offset(width * 0.8f, 0f),
            size = Size(width * 0.05f, height)
        )

        // Draw houses
        for (house in state.houses) {
            drawHouse(house, width, height)
        }

        // Draw obstacles
        for (obstacle in state.obstacles) {
            drawObstacle(obstacle, width, height)
        }

        // Draw thrown papers
        for (paper in state.thrownPapers) {
            drawPaper(paper, width, height)
        }

        // Draw player (bicycle from top-down view)
        drawPlayer(state.playerX, state.playerY, width, height, state.speed)
    }
}

private fun DrawScope.drawHouse(house: House, width: Float, height: Float) {
    val houseWidth = width * 0.12f
    val houseHeight = height * 0.08f
    val houseX = if (house.side == HouseSide.LEFT) {
        width * 0.02f
    } else {
        width * 0.86f
    }
    val houseY = house.y * height

    // House body
    val houseColor = Color(house.type.color)
    drawRect(
        color = if (house.delivered) houseColor.copy(alpha = 0.5f) else houseColor,
        topLeft = Offset(houseX, houseY),
        size = Size(houseWidth, houseHeight)
    )

    // Roof
    val roofPath = Path().apply {
        moveTo(houseX - 5, houseY)
        lineTo(houseX + houseWidth / 2, houseY - houseHeight * 0.4f)
        lineTo(houseX + houseWidth + 5, houseY)
        close()
    }
    drawPath(
        path = roofPath,
        color = Color(0xFF795548)
    )

    // Door
    drawRect(
        color = Color(0xFF5D4037),
        topLeft = Offset(houseX + houseWidth * 0.4f, houseY + houseHeight * 0.4f),
        size = Size(houseWidth * 0.2f, houseHeight * 0.6f)
    )

    // Window
    val windowColor = if (house.broken) Color.Red else Color(0xFF81D4FA)
    drawRect(
        color = windowColor,
        topLeft = Offset(houseX + houseWidth * 0.1f, houseY + houseHeight * 0.2f),
        size = Size(houseWidth * 0.25f, houseHeight * 0.3f)
    )

    // Mailbox
    if (house.hasMailbox) {
        val mailboxX = if (house.side == HouseSide.LEFT) {
            width * 0.14f
        } else {
            width * 0.84f
        }
        val mailboxColor = if (house.delivered) Color.Green else Color(0xFF1565C0)
        drawRect(
            color = mailboxColor,
            topLeft = Offset(mailboxX, houseY + houseHeight * 0.5f),
            size = Size(width * 0.015f, height * 0.025f)
        )
        // Mailbox post
        drawRect(
            color = Color(0xFF5D4037),
            topLeft = Offset(mailboxX + width * 0.005f, houseY + houseHeight * 0.5f + height * 0.025f),
            size = Size(width * 0.005f, height * 0.02f)
        )
    }

    // Delivery indicator
    if (house.delivered) {
        drawCircle(
            color = Color.Green,
            radius = 15f,
            center = Offset(houseX + houseWidth / 2, houseY + houseHeight / 2)
        )
    }
}

private fun DrawScope.drawObstacle(obstacle: StreetObstacleItem, width: Float, height: Float) {
    val obsX: Float = obstacle.x * width
    val obsY: Float = obstacle.y * height
    val obsSize: Float = obstacle.type.size * width

    when (obstacle.type) {
        StreetObstacle.CAR -> {
            // Draw car shape
            drawRoundRect(
                color = Color.Red,
                topLeft = Offset(obsX - obsSize * 0.5f, obsY - obsSize),
                size = Size(obsSize, obsSize * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f)
            )
            // Windows
            drawRect(
                color = Color(0xFF81D4FA),
                topLeft = Offset(obsX - obsSize * 0.3f, obsY - obsSize * 0.6f),
                size = Size(obsSize * 0.6f, obsSize * 0.4f)
            )
        }
        StreetObstacle.DOG -> {
            // Simple dog shape
            drawCircle(
                color = Color(0xFF8D6E63),
                radius = obsSize * 0.5f,
                center = Offset(obsX, obsY)
            )
            // Ears
            drawCircle(
                color = Color(0xFF5D4037),
                radius = obsSize * 0.25f,
                center = Offset(obsX - obsSize * 0.33f, obsY - obsSize * 0.33f)
            )
            drawCircle(
                color = Color(0xFF5D4037),
                radius = obsSize * 0.25f,
                center = Offset(obsX + obsSize * 0.33f, obsY - obsSize * 0.33f)
            )
        }
        StreetObstacle.TRASH_CAN -> {
            drawRect(
                color = Color(0xFF455A64),
                topLeft = Offset(obsX - obsSize * 0.5f, obsY - obsSize),
                size = Size(obsSize, obsSize * 1.5f)
            )
        }
        StreetObstacle.SKATEBOARD_KID -> {
            // Person on skateboard
            drawCircle(
                color = Color(0xFFFFCC80),
                radius = obsSize * 0.5f,
                center = Offset(obsX, obsY - obsSize)
            )
            drawRect(
                color = Color.Blue,
                topLeft = Offset(obsX - obsSize * 0.33f, obsY - obsSize * 0.5f),
                size = Size(obsSize * 0.7f, obsSize)
            )
            // Skateboard
            drawRoundRect(
                color = Color(0xFF795548),
                topLeft = Offset(obsX - obsSize * 0.5f, obsY + obsSize * 0.5f),
                size = Size(obsSize, obsSize * 0.25f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f)
            )
        }
        StreetObstacle.PUDDLE -> {
            drawOval(
                color = Color(0xFF42A5F5).copy(alpha = 0.7f),
                topLeft = Offset(obsX - obsSize, obsY - obsSize * 0.33f),
                size = Size(obsSize * 2f, obsSize * 0.7f)
            )
        }
        StreetObstacle.CONSTRUCTION -> {
            // Construction barrier
            val halfSize: Float = obsSize * 0.5f
            val stripeHeight: Float = obsSize * 0.333f
            val stripeWidth: Float = obsSize * 2f
            val baseY: Float = obsY - halfSize
            val leftX: Float = obsX - obsSize
            drawRect(
                color = Color(0xFFFF9800),
                topLeft = Offset(leftX, baseY),
                size = Size(stripeWidth, stripeHeight)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(leftX, baseY + halfSize),
                size = Size(stripeWidth, stripeHeight)
            )
            drawRect(
                color = Color(0xFFFF9800),
                topLeft = Offset(leftX, baseY + halfSize + halfSize),
                size = Size(stripeWidth, stripeHeight)
            )
        }
    }
}

private fun DrawScope.drawPaper(paper: ThrownPaper, width: Float, height: Float) {
    val paperX = paper.x * width
    val paperY = paper.y * height

    rotate(paper.rotation, pivot = Offset(paperX, paperY)) {
        // Newspaper
        drawRect(
            color = Color(0xFFFAFAFA),
            topLeft = Offset(paperX - 15f, paperY - 8f),
            size = Size(30f, 16f)
        )
        // Text lines
        drawRect(
            color = Color.Gray,
            topLeft = Offset(paperX - 12f, paperY - 5f),
            size = Size(24f, 2f)
        )
        drawRect(
            color = Color.Gray,
            topLeft = Offset(paperX - 12f, paperY),
            size = Size(24f, 2f)
        )
    }
}

private fun DrawScope.drawPlayer(playerX: Float, playerY: Float, width: Float, height: Float, speed: Float) {
    val x = width * 0.2f + playerX * width * 0.6f
    val y = playerY * height

    // Bicycle from top-down view
    val bikeLength = 60f
    val bikeWidth = 25f

    // Frame
    drawRoundRect(
        color = Color(0xFF1565C0),
        topLeft = Offset(x - bikeWidth / 2, y - bikeLength / 2),
        size = Size(bikeWidth, bikeLength),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
    )

    // Wheels
    drawOval(
        color = Color.Black,
        topLeft = Offset(x - bikeWidth / 2 - 3, y - bikeLength / 2 - 5),
        size = Size(bikeWidth + 6, 20f)
    )
    drawOval(
        color = Color.Black,
        topLeft = Offset(x - bikeWidth / 2 - 3, y + bikeLength / 2 - 15),
        size = Size(bikeWidth + 6, 20f)
    )

    // Rider
    drawCircle(
        color = Color(0xFFFFCC80),
        radius = 15f,
        center = Offset(x, y - 10)
    )
    // Helmet
    drawArc(
        color = Color.Red,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(x - 15f, y - 25f),
        size = Size(30f, 20f)
    )

    // Speed lines when moving fast
    if (speed > 0.5f) {
        val alpha = (speed * 0.5f).coerceIn(0f, 0.7f)
        for (i in 1..3) {
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(x - 10f, y + bikeLength / 2 + i * 15),
                end = Offset(x - 10f, y + bikeLength / 2 + i * 15 + 20),
                strokeWidth = 3f
            )
            drawLine(
                color = Color.White.copy(alpha = alpha),
                start = Offset(x + 10f, y + bikeLength / 2 + i * 15),
                end = Offset(x + 10f, y + bikeLength / 2 + i * 15 + 20),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
private fun GameHUD(state: PaperRouteState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Score
            HUDCard {
                Text(
                    text = "SCORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = "${state.score}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }

            // Papers left
            HUDCard {
                Text(
                    text = "PAPERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = "🗞️ ${state.papersLeft}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Combo
            if (state.combo > 1) {
                HUDCard {
                    Text(
                        text = "COMBO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = "${state.combo}x",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                }
            }

            // Subscribers
            HUDCard {
                Text(
                    text = "SUBS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = "📬 ${state.subscribers}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.subscribers < 5) Color.Red else Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Streak indicator
        if (state.streak > 2) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "🔥 ${state.streak} STREAK!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // Resistance indicator (steering feedback)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("◀", color = Color.White, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(8.dp)
                        .background(Color.Gray, shape = MaterialTheme.shapes.small)
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = (state.currentResistance * 0.9f).dp)
                            .size(10.dp)
                            .background(Color.Red, shape = MaterialTheme.shapes.small)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("▶", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun HUDCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun PaperRouteCountdown(count: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 120.sp
        )
    }
}

@Composable
private fun PaperRoutePaused() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "PAUSED",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
