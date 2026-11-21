package cz.mares.spacefury

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

private enum class AppScreen {
    MENU,
    PLAYING,
    LEADERBOARD,
    ACCOUNT,
    SETTINGS
}

@Composable
fun App() {
    SpaceFuryTheme {
        val net = rememberPlatformNetStorage()
        val sound = rememberRetroSoundEngine()
        val credentialBridge = rememberCredentialBridge()
        val scope = rememberCoroutineScope()

        var screen by remember { mutableStateOf(AppScreen.MENU) }
        var bestScore by remember { mutableIntStateOf(net.getString("best_score", "0").toIntOrNull() ?: 0) }
        var bestWave by remember { mutableIntStateOf(net.getString("best_wave", "1").toIntOrNull() ?: 1) }
        var savedNick by remember { mutableStateOf(net.getString("pilot_nick", "")) }
        var savedPin by remember { mutableStateOf(net.getString("pilot_pin", "")) }

        var soundOn by remember { mutableStateOf(net.getString("sound_on", "1") != "0") }
        var vibOn by remember { mutableStateOf(net.getString("vib_on", "1") != "0") }
        var crossbarShield by remember { mutableStateOf(net.getString("crossbar_shield", "1") != "0") }
        var controlMode by remember {
            mutableStateOf(
                if (net.getString("control_mode", "buttons") == "stick") ControlMode.VIRTUAL_STICK
                else ControlMode.ARCADE_BUTTONS
            )
        }

        val engine = remember(sound) {
            SpaceFuryEngine(sound).apply {
                highScore = bestScore
                crossbarShieldEnabled = crossbarShield
            }
        }

        LaunchedEffect(soundOn, vibOn, crossbarShield) {
            sound.soundEnabled = soundOn
            sound.vibrationEnabled = vibOn
            engine.crossbarShieldEnabled = crossbarShield
        }

        // 60/120 FPS herní smyčka
        LaunchedEffect(screen) {
            if (screen != AppScreen.PLAYING) return@LaunchedEffect
            var lastNanos = 0L
            var submittedThisRun = false
            while (screen == AppScreen.PLAYING) {
                withFrameNanos { now ->
                    val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1_000_000_000f)
                    lastNanos = now
                    engine.step(dt)

                    if (engine.score > bestScore) {
                        bestScore = engine.score
                        net.putString("best_score", bestScore.toString())
                    }
                    if (engine.wave > bestWave) {
                        bestWave = engine.wave
                        net.putString("best_wave", bestWave.toString())
                    }
                    if (engine.phase == GamePhase.GAME_OVER && !submittedThisRun) {
                        submittedThisRun = true
                        if (savedNick.isNotBlank() && savedPin.isNotBlank() && engine.score > 0) {
                            val finalScore = engine.score
                            val finalWave = engine.wave
                            scope.launch {
                                LeaderboardApi.submitScoreIfHigher(net, savedNick, savedPin, finalScore, finalWave)
                            }
                        }
                    } else if (engine.phase != GamePhase.GAME_OVER) {
                        submittedThisRun = false
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                when (screen) {
                    AppScreen.MENU -> MainMenuScreen(
                        bestScore = bestScore,
                        pilotNick = savedNick,
                        onStartGame = {
                            engine.startNewGame(bestScore)
                            screen = AppScreen.PLAYING
                        },
                        onOpenLeaderboard = { screen = AppScreen.LEADERBOARD },
                        onOpenAccount = { screen = AppScreen.ACCOUNT },
                        onOpenSettings = { screen = AppScreen.SETTINGS }
                    )

                    AppScreen.PLAYING -> GamePlayScreen(
                        engine = engine,
                        controlMode = controlMode,
                        soundOn = soundOn,
                        onToggleSound = {
                            soundOn = !soundOn
                            net.putString("sound_on", if (soundOn) "1" else "0")
                        },
                        onRestart = { engine.startNewGame(bestScore) },
                        onOpenLeaderboard = { screen = AppScreen.LEADERBOARD },
                        onExitToMenu = { screen = AppScreen.MENU }
                    )

                    AppScreen.LEADERBOARD -> LeaderboardScreen(
                        net = net,
                        currentNick = savedNick,
                        onBack = { screen = AppScreen.MENU }
                    )

                    AppScreen.ACCOUNT -> PilotAccountScreen(
                        net = net,
                        credentialBridge = credentialBridge,
                        savedNick = savedNick,
                        savedPin = savedPin,
                        bestScore = bestScore,
                        bestWave = bestWave,
                        onAccountUpdated = { newNick, newPin, syncedScore, syncedWave ->
                            savedNick = newNick
                            savedPin = newPin
                            if (newNick.isBlank()) {
                                net.removeKey("pilot_nick")
                                net.removeKey("pilot_pin")
                            } else {
                                net.putString("pilot_nick", newNick)
                                net.putString("pilot_pin", newPin)
                            }
                            if (syncedScore > bestScore) {
                                bestScore = syncedScore
                                engine.highScore = syncedScore
                                net.putString("best_score", syncedScore.toString())
                            }
                            if (syncedWave > bestWave) {
                                bestWave = syncedWave
                                net.putString("best_wave", syncedWave.toString())
                            }
                        },
                        onBack = { screen = AppScreen.MENU }
                    )

                    AppScreen.SETTINGS -> SettingsScreen(
                        soundOn = soundOn,
                        vibOn = vibOn,
                        crossbarShield = crossbarShield,
                        controlMode = controlMode,
                        onSoundChange = {
                            soundOn = it
                            net.putString("sound_on", if (it) "1" else "0")
                        },
                        onVibChange = {
                            vibOn = it
                            net.putString("vib_on", if (it) "1" else "0")
                        },
                        onShieldChange = {
                            crossbarShield = it
                            net.putString("crossbar_shield", if (it) "1" else "0")
                        },
                        onControlModeChange = {
                            controlMode = it
                            net.putString(
                                "control_mode",
                                if (it == ControlMode.VIRTUAL_STICK) "stick" else "buttons"
                            )
                        },
                        onBack = { screen = AppScreen.MENU }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainMenuScreen(
    bestScore: Int,
    pilotNick: String,
    onStartGame: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Vektorový emblém rakety A
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF04091A))
                    .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(84.dp)) {
                    val w = size.width
                    val h = size.height
                    drawLine(Color(0xFFFF3D8B), Offset(w * 0.28f, h * 0.45f), Offset(w * 0.14f, h * 0.78f), 6f, StrokeCap.Round)
                    drawLine(Color(0xFFFF3D8B), Offset(w * 0.72f, h * 0.45f), Offset(w * 0.86f, h * 0.78f), 6f, StrokeCap.Round)
                    drawLine(Color(0xFF00F5FF), Offset(w * 0.50f, h * 0.12f), Offset(w * 0.24f, h * 0.82f), 7f, StrokeCap.Round)
                    drawLine(Color(0xFF00F5FF), Offset(w * 0.50f, h * 0.12f), Offset(w * 0.76f, h * 0.82f), 7f, StrokeCap.Round)
                    drawLine(Color(0xFFFFEA00), Offset(w * 0.34f, h * 0.62f), Offset(w * 0.66f, h * 0.62f), 7f, StrokeCap.Round)
                    drawLine(Color(0xFFFF6D00), Offset(w * 0.43f, h * 0.70f), Offset(w * 0.50f, h * 0.90f), 5f, StrokeCap.Round)
                    drawLine(Color(0xFFFF6D00), Offset(w * 0.57f, h * 0.70f), Offset(w * 0.50f, h * 0.90f), 5f, StrokeCap.Round)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "HVĚZDNÁ ZUŘIVOST",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Barevná vektorová arkáda z roku 1981",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PILOT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = pilotNick.ifBlank { "Host (offline režim)" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "NEJLEPŠÍ SKÓRE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatArcadeScore(bestScore),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("SPUSTIT HRU", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onOpenLeaderboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("ŽEBŘÍČEK TOP 50", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAccount,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("ÚČET PILOTA", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("NASTAVENÍ", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "JAK HRÁT VESMÍRNÉ ÁČKO",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "• Rozstřílej přilétající díly dřív, než se spojí v nepřátelské křižníky.\n" +
                            "• Po vyčištění vlny vdokuj raketou A do 1 ze 3 zbraňových plášťů.\n" +
                            "• Při zásahu odletí přídavný plášť a spodní příčka v písmenu A se rozbliká.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "© 2026 Created by Pavel Mareš",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GamePlayScreen(
    engine: SpaceFuryEngine,
    controlMode: ControlMode,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onRestart: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onExitToMenu: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                val down = ev.type == KeyEventType.KeyDown
                val up = ev.type == KeyEventType.KeyUp
                if (!down && !up) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft, Key.A -> {
                        engine.inputLeft = down; true
                    }
                    Key.DirectionRight, Key.D -> {
                        engine.inputRight = down; true
                    }
                    Key.DirectionUp, Key.W -> {
                        engine.inputThrust = down; true
                    }
                    Key.Spacebar, Key.Enter -> {
                        engine.inputFire = down; true
                    }
                    else -> false
                }
            }
            .padding(8.dp)
    ) {
        val isLandscape = maxWidth > maxHeight * 1.15f

        Column(modifier = Modifier.fillMaxSize()) {
            // Kompaktní horní ovládací lišta kabinetu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onExitToMenu,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("MENU", fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onToggleSound,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (soundOn) "ZVUK ZAP" else "ZVUK VYP", fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick = { engine.isPaused = !engine.isPaused },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (engine.isPaused) "POKRAČOVAT" else "PAUZA", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(175.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        LeftControlsCluster(engine = engine, controlMode = controlMode)
                    }
                    VectorArcadeScreen(
                        engine = engine,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Box(
                        modifier = Modifier
                            .width(175.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        RightControlsCluster(engine = engine, verticalLayout = true)
                    }
                }
            } else {
                VectorArcadeScreen(
                    engine = engine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        LeftControlsCluster(engine = engine, controlMode = controlMode)
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        RightControlsCluster(engine = engine, verticalLayout = false)
                    }
                }
            }
        }

        if (engine.phase == GamePhase.GAME_OVER) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "KONEC HRY",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF3D8B)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Skóre: ${formatArcadeScore(engine.score)}  •  Vlna: ${engine.wave}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRestart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("HRÁT ZNOVU", fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = onOpenLeaderboard,
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("TOP 50")
                        }
                        OutlinedButton(
                            onClick = onExitToMenu,
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("MENU")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeftControlsCluster(
    engine: SpaceFuryEngine,
    controlMode: ControlMode
) {
    if (controlMode == ControlMode.VIRTUAL_STICK) {
        VirtualJoystickPad(
            onVectorChanged = { angleDeg, mag ->
                engine.stickAngleDeg = angleDeg
                engine.stickMagnitude = mag
            }
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArcadeHoldPad(
                label = "◀ VLEVO",
                accent = Color(0xFF00E5FF),
                modifier = Modifier.size(width = 80.dp, height = 88.dp),
                onHoldChanged = { engine.inputLeft = it }
            )
            ArcadeHoldPad(
                label = "VPRAVO ▶",
                accent = Color(0xFF00E5FF),
                modifier = Modifier.size(width = 80.dp, height = 88.dp),
                onHoldChanged = { engine.inputRight = it }
            )
        }
    }
}

@Composable
private fun RightControlsCluster(
    engine: SpaceFuryEngine,
    verticalLayout: Boolean
) {
    if (verticalLayout) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArcadeHoldPad(
                label = "PALBA",
                accent = Color(0xFFFF3D8B),
                modifier = Modifier.size(width = 145.dp, height = 92.dp),
                onHoldChanged = { engine.inputFire = it }
            )
            ArcadeHoldPad(
                label = "TRYSKA ▲",
                accent = Color(0xFFFFB300),
                modifier = Modifier.size(width = 145.dp, height = 82.dp),
                onHoldChanged = { engine.inputThrust = it }
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArcadeHoldPad(
                label = "TRYSKA ▲",
                accent = Color(0xFFFFB300),
                modifier = Modifier.size(width = 78.dp, height = 88.dp),
                onHoldChanged = { engine.inputThrust = it }
            )
            ArcadeHoldPad(
                label = "PALBA",
                accent = Color(0xFFFF3D8B),
                modifier = Modifier.size(width = 88.dp, height = 96.dp),
                onHoldChanged = { engine.inputFire = it }
            )
        }
    }
}

@Composable
private fun ArcadeHoldPad(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onHoldChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (pressed) accent.copy(alpha = 0.34f) else Color(0xFF101B33))
            .border(
                width = if (pressed) 2.5.dp else 1.5.dp,
                color = if (pressed) accent else accent.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onHoldChanged(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        pressed = false
                        onHoldChanged(false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VirtualJoystickPad(
    onVectorChanged: (Float?, Float) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .size(132.dp)
            .clip(CircleShape)
            .background(Color(0xFF101B33))
            .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.55f), CircleShape)
            .pointerInput(Unit) {
                val radius = size.width * 0.36f
                detectDragGestures(
                    onDragStart = { pos ->
                        val c = Offset(size.width * 0.5f, size.height * 0.5f)
                        val d = pos - c
                        val dist = hypot(d.x, d.y)
                        val clamped = if (dist > radius) d * (radius / dist) else d
                        knobOffset = clamped
                        val mag = (dist / radius).coerceIn(0f, 1f)
                        val deg = atan2(d.y, d.x) * (180f / PI.toFloat())
                        onVectorChanged(deg, mag)
                    },
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onVectorChanged(null, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onVectorChanged(null, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = knobOffset + dragAmount
                        val dist = hypot(next.x, next.y)
                        val clamped = if (dist > radius) next * (radius / dist) else next
                        knobOffset = clamped
                        val mag = (hypot(clamped.x, clamped.y) / radius).coerceIn(0f, 1f)
                        val deg = atan2(clamped.y, clamped.x) * (180f / PI.toFloat())
                        onVectorChanged(deg, mag)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            drawCircle(Color(0xFF00E5FF).copy(alpha = 0.22f), radius = size.width * 0.22f, center = center)
            drawCircle(Color(0xFF00E5FF), radius = 22.dp.toPx(), center = center + knobOffset)
        }
    }
}

@Composable
private fun LeaderboardScreen(
    net: PlatformNetStorage,
    currentNick: String,
    onBack: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun reload() {
        loading = true
        scope.launch {
            entries = LeaderboardApi.fetchTop50(net)
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("ZPĚT", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "ŽEBŘÍČEK TOP 50",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            FilledTonalButton(
                onClick = { reload() },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("OBNOVIT")
            }
        }

        Spacer(Modifier.height(12.dp))
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Zatím zde nejsou žádné záznamy nebo není dostupné připojení.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val myKey = LeaderboardApi.normalizeKey(currentNick)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(entries) { idx, item ->
                    val isMe = myKey.isNotBlank() && item.key == myKey
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${idx + 1}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.width(42.dp)
                                )
                                Column {
                                    Text(item.nick, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Vlna ${item.wave}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = formatArcadeScore(item.score),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PilotAccountScreen(
    net: PlatformNetStorage,
    credentialBridge: CredentialBridge,
    savedNick: String,
    savedPin: String,
    bestScore: Int,
    bestWave: Int,
    onAccountUpdated: (String, String, Int, Int) -> Unit,
    onBack: () -> Unit
) {
    var nick by remember { mutableStateOf(savedNick) }
    var pin by remember { mutableStateOf(savedPin) }
    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("ZPĚT", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "ÚČET PILOTA (TOP 50)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Text(
            text = "Hru můžeš hrát naplno i bez přihlášení. Zvolíš-li si přezdívku a PIN/heslo, tvoje skóre se zapíše do online žebříčku TOP 50.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (credentialBridge.isSupported) {
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        val loaded = credentialBridge.loadCredentials()
                        if (loaded != null) {
                            nick = loaded.first
                            pin = loaded.second
                            statusMessage = "Údaje načteny ze Správce hesel."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("🔑 Vyplnit uložený účet", fontWeight = FontWeight.Bold)
            }
        }

        OutlinedTextField(
            value = nick,
            onValueChange = { nick = it.take(20) },
            label = { Text("Přezdívka pilota") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.take(24) },
            label = { Text("PIN nebo heslo (min. 4 znaky)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                statusMessage = ""
                scope.launch {
                    val res = LeaderboardApi.loginOrRegister(net, nick, pin, bestScore, bestWave)
                    busy = false
                    res.onSuccess { entry ->
                        onAccountUpdated(entry.nick, pin.trim(), entry.score, entry.wave)
                        credentialBridge.saveCredentials(entry.nick, pin.trim())
                        statusMessage = "Účet pilota '${entry.nick}' je aktivní a synchronizovaný."
                    }.onFailure { err ->
                        statusMessage = err.message ?: "Přihlášení se nezdařilo."
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (busy) "OVĚŘUJI..." else "ULOŽIT A PŘIHLÁSIT", fontWeight = FontWeight.ExtraBold)
        }

        if (savedNick.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onAccountUpdated("", "", bestScore, bestWave)
                        nick = ""
                        pin = ""
                        statusMessage = "Byl jsi odhlášen do offline režimu."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("ODHLÁSIT SE")
                }

                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3D8B))
                ) {
                    Text("SMAZAT ÚČET")
                }
            }
        }

        if (statusMessage.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(14.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Smazat účet a online skóre?") },
            text = { Text("Tvůj účet '$savedNick' i záznam v online žebříčku TOP 50 budou trvale odstraněny.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        busy = true
                        scope.launch {
                            val ok = LeaderboardApi.deleteAccount(net, savedNick, savedPin)
                            busy = false
                            onAccountUpdated("", "", bestScore, bestWave)
                            nick = ""
                            pin = ""
                            statusMessage = if (ok) {
                                "Účet a online záznam byly trvale smazány."
                            } else {
                                "Lokální účet byl odstraněn."
                            }
                        }
                    }
                ) {
                    Text("TRVALE SMAZAT", color = Color(0xFFFF3D8B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("ZRUŠIT")
                }
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    soundOn: Boolean,
    vibOn: Boolean,
    crossbarShield: Boolean,
    controlMode: ControlMode,
    onSoundChange: (Boolean) -> Unit,
    onVibChange: (Boolean) -> Unit,
    onShieldChange: (Boolean) -> Unit,
    onControlModeChange: (ControlMode) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("ZPĚT", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "NASTAVENÍ KABINETU",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        SettingSwitchCard(
            title = "Syntetizátor zvuku a hlasu (1981)",
            subtitle = "Vektorové lasery, tryska a robotický vokodér velitele",
            checked = soundOn,
            onCheckedChange = onSoundChange
        )

        SettingSwitchCard(
            title = "Haptická odezva",
            subtitle = "Vibrace při dokování zbraní a zásahu rakety",
            checked = vibOn,
            onCheckedChange = onVibChange
        )

        SettingSwitchCard(
            title = "Nouzový štít příčky v A (Maringotka 1984)",
            subtitle = "Holé A přežije ještě jeden zásah a rozbliká spodní příčku",
            checked = crossbarShield,
            onCheckedChange = onShieldChange
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Způsob ovládání rakety", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (controlMode == ControlMode.ARCADE_BUTTONS) {
                        Button(
                            onClick = { onControlModeChange(ControlMode.ARCADE_BUTTONS) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Tlačítka") }
                        OutlinedButton(
                            onClick = { onControlModeChange(ControlMode.VIRTUAL_STICK) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Joystick") }
                    } else {
                        OutlinedButton(
                            onClick = { onControlModeChange(ControlMode.ARCADE_BUTTONS) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Tlačítka") }
                        Button(
                            onClick = { onControlModeChange(ControlMode.VIRTUAL_STICK) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Joystick") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
