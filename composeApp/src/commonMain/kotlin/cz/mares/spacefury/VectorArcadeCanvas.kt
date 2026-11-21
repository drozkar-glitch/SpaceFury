package cz.mares.spacefury

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val VecCyan = Color(0xFF00F5FF)
private val VecYellow = Color(0xFFFFEA00)
private val VecMagenta = Color(0xFFFF3D8B)
private val VecGreen = Color(0xFF2BFF88)
private val VecOrange = Color(0xFFFF7B00)
private val VecRed = Color(0xFFFF2A4B)
private val VecWhite = Color(0xFFEFFFFF)

@Composable
fun VectorArcadeScreen(
    engine: SpaceFuryEngine,
    modifier: Modifier = Modifier
) {
    val tick = engine.frameTick
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF02040B))
            .border(1.5.dp, Color(0xFF1A3A66), RoundedCornerShape(18.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (tick < 0) return@Canvas
            val scale = min(
                size.width / SpaceFuryEngine.ARENA_W,
                size.height / SpaceFuryEngine.ARENA_H
            )
            val ox = (size.width - SpaceFuryEngine.ARENA_W * scale) * 0.5f
            val oy = (size.height - SpaceFuryEngine.ARENA_H * scale) * 0.5f

            fun mapPt(ax: Float, ay: Float): Offset = Offset(ox + ax * scale, oy + ay * scale)

            // Hvězdný prach v pozadí CRT obrazovky
            for (s in engine.stars) {
                val c = when (s.colorCode) {
                    0 -> VecCyan
                    1 -> VecYellow
                    2 -> VecMagenta
                    else -> VecWhite
                }.copy(alpha = s.brightness * 0.55f)
                drawCircle(c, radius = 1.6f * scale.coerceAtLeast(0.5f), center = mapPt(s.x, s.y))
            }

            // Vektorové ikony zbývajících životů (malá písmena A vpravo nahoře)
            for (i in 0 until engine.lives) {
                val lx = SpaceFuryEngine.ARENA_W - 44f - i * 34f
                val ly = 42f
                drawPlayerShipVector(
                    cx = lx,
                    cy = ly,
                    angleDeg = -90f,
                    shipScale = 0.62f * scale,
                    ox = ox,
                    oy = oy,
                    arenaScale = scale,
                    isThrusting = false,
                    primary = null,
                    secondary = null,
                    crossbarBlinkState = 0,
                    frameTick = tick
                )
            }

            // Vektorová hlava mimozemského velitele Zorgona (Intro, Taunt, Game Over)
            if (engine.phase == GamePhase.COMMANDER_INTRO ||
                engine.phase == GamePhase.COMMANDER_TAUNT ||
                engine.phase == GamePhase.GAME_OVER
            ) {
                drawAlienCommanderHead(
                    ox = ox,
                    oy = oy,
                    scale = scale,
                    timeSec = engine.elapsedSec,
                    isGameOver = engine.phase == GamePhase.GAME_OVER
                )
            }

            // Dokovací moduly mezi koly
            if (engine.phase == GamePhase.DOCKING) {
                for (pod in engine.dockingPods) {
                    if (!pod.available) continue
                    drawDockingPodVector(pod, ox, oy, scale)
                }
            }

            // Přilétající části nepřátelských lodí před složením
            for (part in engine.enemyParts) {
                if (!part.isRogue && !part.arrived) {
                    drawGlowSeg(
                        mapPt(part.x, part.y),
                        mapPt(part.targetX, part.targetY),
                        VecCyan.copy(alpha = 0.14f),
                        1.0f * scale,
                        withGlow = false
                    )
                }
                drawEnemyPartVector(part, ox, oy, scale)
            }

            // Složené nepřátelské křižníky
            for (enemy in engine.enemies) {
                drawEnemyWarshipVector(enemy, ox, oy, scale)
            }

            // Střely
            for (b in engine.bullets) {
                val col = when (b.colorCode) {
                    0 -> VecCyan
                    1 -> VecMagenta
                    2 -> VecYellow
                    else -> VecRed
                }
                val len = if (b.fromPlayer) 16f else 12f
                val spd = kotlin.math.hypot(b.vx, b.vy).coerceAtLeast(1f)
                val tx = b.x - (b.vx / spd) * len
                val ty = b.y - (b.vy / spd) * len
                drawGlowSeg(mapPt(tx, ty), mapPt(b.x, b.y), col, 2.4f * scale)
            }

            // Hráčova raketa ve tvaru písmene A
            if (engine.ship.alive && (engine.phase == GamePhase.COMBAT || engine.phase == GamePhase.DOCKING)) {
                val invulnHide = engine.ship.invulnTimer > 0f && (tick / 3) % 2 == 1
                if (!invulnHide || engine.ship.crossbarBlinkTimer > 0f) {
                    val blinkState = if (engine.ship.crossbarDamaged || engine.ship.crossbarBlinkTimer > 0f) {
                        if ((tick / 3) % 2 == 0) 1 else 2
                    } else {
                        0
                    }
                    drawPlayerShipVector(
                        cx = engine.ship.x,
                        cy = engine.ship.y,
                        angleDeg = engine.ship.angleDeg,
                        shipScale = 1.15f * scale,
                        ox = ox,
                        oy = oy,
                        arenaScale = scale,
                        isThrusting = engine.ship.isThrusting,
                        primary = engine.ship.primaryShell,
                        secondary = engine.ship.secondaryShell,
                        crossbarBlinkState = blinkState,
                        frameTick = tick
                    )
                }
            }

            // Vektorové úlomky explozí a rozpadu písmene A
            for (d in engine.debris) {
                val alpha = (d.life / d.maxLife).coerceIn(0f, 1f)
                val col = when (d.colorCode) {
                    0 -> VecCyan
                    1 -> VecMagenta
                    2 -> VecYellow
                    else -> VecOrange
                }.copy(alpha = alpha)
                val r = d.angleDeg * (PI.toFloat() / 180f)
                val dx = cos(r) * d.halfLen
                val dy = sin(r) * d.halfLen
                drawGlowSeg(mapPt(d.x - dx, d.y - dy), mapPt(d.x + dx, d.y + dy), col, 2.0f * scale)
            }
        }

        // Horní vektorový HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "1UP ${formatArcadeScore(engine.score)}",
                color = VecCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "REKORD ${formatArcadeScore(engine.highScore)}",
                color = VecYellow,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "VLNA ${engine.wave}",
                color = VecGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        // Dokovací nápověda a odpočet
        if (engine.phase == GamePhase.DOCKING) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DOKOVÁNÍ ZBRANÍ: ${engine.dockingCountdown.toInt()} s",
                    color = VecYellow,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "NALEŤ RAKETOU A DO VYBRANÉHO MODULU (VĚJÍŘ / DVOJKANÓN / VPŘED+VZAD)",
                    color = VecWhite.copy(alpha = 0.88f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        // Hláška mimozemského velitele
        if (engine.phase == GamePhase.COMMANDER_INTRO ||
            engine.phase == GamePhase.COMMANDER_TAUNT ||
            engine.phase == GamePhase.GAME_OVER
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = engine.commanderMessage,
                    color = if (engine.phase == GamePhase.GAME_OVER) VecRed else VecGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun DrawScope.drawGlowSeg(
    p1: Offset,
    p2: Offset,
    color: Color,
    strokePx: Float,
    withGlow: Boolean = true
) {
    val w = strokePx.coerceAtLeast(1.3f)
    if (withGlow) {
        drawLine(
            color = color.copy(alpha = color.alpha * 0.26f),
            start = p1,
            end = p2,
            strokeWidth = w * 3.1f,
            cap = StrokeCap.Round
        )
    }
    drawLine(
        color = color,
        start = p1,
        end = p2,
        strokeWidth = w,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawPlayerShipVector(
    cx: Float,
    cy: Float,
    angleDeg: Float,
    shipScale: Float,
    ox: Float,
    oy: Float,
    arenaScale: Float,
    isThrusting: Boolean,
    primary: ShellType?,
    secondary: ShellType?,
    crossbarBlinkState: Int,
    frameTick: Int
) {
    val rad = angleDeg * (PI.toFloat() / 180f)
    val cosA = cos(rad)
    val sinA = sin(rad)
    val sx = ox + cx * arenaScale
    val sy = oy + cy * arenaScale

    fun tr(lx: Float, ly: Float): Offset {
        val rx = (lx * cosA - ly * sinA) * shipScale
        val ry = (lx * sinA + ly * cosA) * shipScale
        return Offset(sx + rx, sy + ry)
    }

    val lineW = 2.3f * arenaScale

    // Připojené přídavné bojové pláště kolem rakety A
    val hasSpread = primary == ShellType.SPREAD || secondary == ShellType.SPREAD
    val hasCannon = primary == ShellType.CANNON || secondary == ShellType.CANNON
    val hasDual = primary == ShellType.DUAL_AXIS || secondary == ShellType.DUAL_AXIS

    if (hasSpread) {
        drawGlowSeg(tr(6f, -8f), tr(-8f, -26f), VecMagenta, lineW)
        drawGlowSeg(tr(-8f, -26f), tr(-20f, -20f), VecMagenta, lineW)
        drawGlowSeg(tr(-20f, -20f), tr(-12f, -12f), VecMagenta, lineW)

        drawGlowSeg(tr(6f, 8f), tr(-8f, 26f), VecMagenta, lineW)
        drawGlowSeg(tr(-8f, 26f), tr(-20f, 20f), VecMagenta, lineW)
        drawGlowSeg(tr(-20f, 20f), tr(-12f, 12f), VecMagenta, lineW)
    }

    if (hasCannon) {
        drawGlowSeg(tr(-4f, -13f), tr(23f, -11f), VecGreen, lineW)
        drawGlowSeg(tr(23f, -11f), tr(14f, -7f), VecGreen, lineW)
        drawGlowSeg(tr(-4f, 13f), tr(23f, 11f), VecGreen, lineW)
        drawGlowSeg(tr(23f, 11f), tr(14f, 7f), VecGreen, lineW)
    }

    if (hasDual) {
        drawGlowSeg(tr(-14f, -11f), tr(-25f, -5f), VecYellow, lineW)
        drawGlowSeg(tr(-25f, -5f), tr(-25f, 5f), VecYellow, lineW)
        drawGlowSeg(tr(-25f, 5f), tr(-14f, 11f), VecYellow, lineW)
    }

    // Dvě šikmé nožky čistého písmene A
    val nose = tr(22f, 0f)
    val leftFoot = tr(-18f, -14f)
    val rightFoot = tr(-18f, 14f)
    drawGlowSeg(nose, leftFoot, VecCyan, lineW * 1.1f)
    drawGlowSeg(nose, rightFoot, VecCyan, lineW * 1.1f)

    // Spodní příčka v písmenu A (po zásahu výrazně bliká!)
    val barLeft = tr(-9f, -9.8f)
    val barRight = tr(-9f, 9.8f)
    when (crossbarBlinkState) {
        0 -> drawGlowSeg(barLeft, barRight, VecYellow, lineW * 1.15f)
        1 -> drawGlowSeg(barLeft, barRight, VecRed, lineW * 1.55f)
        else -> {
            // Ve sudé fázi blikání příčka pohasne přesně jako na vektorovém automatu
            drawGlowSeg(barLeft, barRight, VecYellow.copy(alpha = 0.16f), lineW * 0.8f, withGlow = false)
        }
    }

    // Blikající plamen trysky pod spodní příčkou písmene A při tahu motoru
    if (isThrusting && frameTick % 2 == 0) {
        val jetLen = if ((frameTick / 2) % 2 == 0) -26f else -21f
        val jL = tr(-9f, -5.2f)
        val jR = tr(-9f, 5.2f)
        val jTip = tr(jetLen, 0f)
        drawGlowSeg(jL, jTip, VecOrange, lineW)
        drawGlowSeg(jR, jTip, VecOrange, lineW)
    }
}

private fun DrawScope.drawDockingPodVector(
    pod: DockingPod,
    ox: Float,
    oy: Float,
    scale: Float
) {
    val pulse = 1f + 0.10f * sin(pod.pulsePhase)
    val cx = ox + pod.x * scale
    val cy = oy + pod.y * scale
    val r = 38f * scale * pulse
    val col = when (pod.shellType) {
        ShellType.SPREAD -> VecMagenta
        ShellType.CANNON -> VecGreen
        ShellType.DUAL_AXIS -> VecYellow
    }

    // Osmiúhelníkový dokovací prstenec
    var prev: Offset? = null
    for (i in 0..8) {
        val a = (i * 45.0) * (PI / 180.0)
        val pt = Offset(cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r)
        if (prev != null) {
            drawGlowSeg(prev, pt, col.copy(alpha = 0.72f), 1.8f * scale)
        }
        prev = pt
    }

    // Ukázka tvaru zbraňového pláště uvnitř doku
    drawPlayerShipVector(
        cx = pod.x,
        cy = pod.y,
        angleDeg = -90f,
        shipScale = 0.92f * scale,
        ox = ox,
        oy = oy,
        arenaScale = scale,
        isThrusting = false,
        primary = pod.shellType,
        secondary = null,
        crossbarBlinkState = 0,
        frameTick = 0
    )
}

private fun DrawScope.drawEnemyPartVector(
    part: EnemyComponentPart,
    ox: Float,
    oy: Float,
    scale: Float
) {
    val cx = ox + part.x * scale
    val cy = oy + part.y * scale
    val rad = part.spinDeg * (PI.toFloat() / 180f)
    val c = cos(rad)
    val s = sin(rad)
    fun tr(lx: Float, ly: Float): Offset = Offset(
        cx + (lx * c - ly * s) * scale,
        cy + (lx * s + ly * c) * scale
    )

    val col = when (part.kind) {
        EnemyPartKind.CORE -> VecYellow
        EnemyPartKind.WING_L -> VecMagenta
        EnemyPartKind.WING_R -> VecGreen
    }
    val w = 2.0f * scale
    when (part.kind) {
        EnemyPartKind.CORE -> {
            val p1 = tr(0f, -13f)
            val p2 = tr(13f, 0f)
            val p3 = tr(0f, 13f)
            val p4 = tr(-13f, 0f)
            drawGlowSeg(p1, p2, col, w)
            drawGlowSeg(p2, p3, col, w)
            drawGlowSeg(p3, p4, col, w)
            drawGlowSeg(p4, p1, col, w)
        }
        EnemyPartKind.WING_L, EnemyPartKind.WING_R -> {
            val p1 = tr(-14f, -9f)
            val p2 = tr(15f, 0f)
            val p3 = tr(-8f, 11f)
            drawGlowSeg(p1, p2, col, w)
            drawGlowSeg(p2, p3, col, w)
            drawGlowSeg(p3, p1, col, w)
        }
    }
}

private fun DrawScope.drawEnemyWarshipVector(
    enemy: EnemyWarship,
    ox: Float,
    oy: Float,
    scale: Float
) {
    val cx = ox + enemy.x * scale
    val cy = oy + enemy.y * scale
    val rad = enemy.angleDeg * (PI.toFloat() / 180f)
    val c = cos(rad)
    val s = sin(rad)
    fun tr(lx: Float, ly: Float): Offset = Offset(
        cx + (lx * c - ly * s) * scale,
        cy + (lx * s + ly * c) * scale
    )

    val flash = enemy.hitFlash > 0f
    val primaryCol = if (flash) VecWhite else when (enemy.archetype) {
        EnemyArchetype.SCOUT -> VecGreen
        EnemyArchetype.DESTROYER -> VecRed
        EnemyArchetype.STAR_HUNTER -> VecMagenta
    }
    val coreCol = if (flash) VecWhite else VecYellow
    val w = 2.2f * scale

    when (enemy.archetype) {
        EnemyArchetype.SCOUT -> {
            for (k in 0 until 3) {
                val a1 = (k * 120.0) * (PI / 180.0)
                val a2 = (k * 120.0 + 50.0) * (PI / 180.0)
                val pIn = tr(cos(a1).toFloat() * 10f, sin(a1).toFloat() * 10f)
                val pTip = tr(cos(a1).toFloat() * 28f, sin(a1).toFloat() * 28f)
                val pSide = tr(cos(a2).toFloat() * 18f, sin(a2).toFloat() * 18f)
                drawGlowSeg(pIn, pTip, primaryCol, w)
                drawGlowSeg(pTip, pSide, primaryCol, w)
                drawGlowSeg(pSide, pIn, coreCol, w)
            }
        }
        EnemyArchetype.DESTROYER -> {
            val hull = arrayOf(
                tr(0f, -26f), tr(24f, -12f), tr(28f, 14f),
                tr(0f, 24f), tr(-28f, 14f), tr(-24f, -12f)
            )
            for (i in hull.indices) {
                drawGlowSeg(hull[i], hull[(i + 1) % hull.size], primaryCol, w)
            }
            drawGlowSeg(tr(-12f, -6f), tr(12f, -6f), coreCol, w)
            drawGlowSeg(tr(-14f, 6f), tr(14f, 6f), coreCol, w)
        }
        EnemyArchetype.STAR_HUNTER -> {
            for (k in 0 until 4) {
                val a = (k * 90.0) * (PI / 180.0)
                val aNext = (k * 90.0 + 90.0) * (PI / 180.0)
                val tip = tr(cos(a).toFloat() * 29f, sin(a).toFloat() * 29f)
                val valley = tr(cos(a + 0.78).toFloat() * 11f, sin(a + 0.78).toFloat() * 11f)
                val nextTip = tr(cos(aNext).toFloat() * 29f, sin(aNext).toFloat() * 29f)
                drawGlowSeg(tip, valley, primaryCol, w)
                drawGlowSeg(valley, nextTip, coreCol, w)
            }
        }
    }
}

private fun DrawScope.drawAlienCommanderHead(
    ox: Float,
    oy: Float,
    scale: Float,
    timeSec: Float,
    isGameOver: Boolean
) {
    val cx = ox + SpaceFuryEngine.ARENA_W * 0.5f * scale
    val cy = oy + SpaceFuryEngine.ARENA_H * 0.44f * scale
    val jawOpen = (6f + 12f * ((sin(timeSec * 20f) + 1f) * 0.5f)) * scale
    val skin = if (isGameOver) VecRed else VecGreen
    val eyes = if (isGameOver) VecYellow else VecRed
    val crest = VecCyan
    val w = 2.5f * scale

    fun pt(dx: Float, dy: Float): Offset = Offset(cx + dx * scale, cy + dy * scale)

    // Lebka a lícní hrany mimozemského velitele ze Space Fury (1981)
    val crown = pt(0f, -150f)
    val templeL = pt(-115f, -85f)
    val templeR = pt(115f, -85f)
    val cheekL = pt(-130f, 15f)
    val cheekR = pt(130f, 15f)
    val jawHingeL = pt(-78f, 82f)
    val jawHingeR = pt(78f, 82f)

    drawGlowSeg(crown, templeL, skin, w)
    drawGlowSeg(crown, templeR, skin, w)
    drawGlowSeg(templeL, cheekL, skin, w)
    drawGlowSeg(templeR, cheekR, skin, w)
    drawGlowSeg(cheekL, jawHingeL, skin, w)
    drawGlowSeg(cheekR, jawHingeR, skin, w)

    // Rohy / antény na hlavě
    drawGlowSeg(pt(-55f, -118f), pt(-142f, -195f), crest, w)
    drawGlowSeg(pt(-142f, -195f), pt(-95f, -96f), crest, w)
    drawGlowSeg(pt(55f, -118f), pt(142f, -195f), crest, w)
    drawGlowSeg(pt(142f, -195f), pt(95f, -96f), crest, w)
    drawGlowSeg(pt(0f, -150f), pt(0f, -65f), crest, w * 0.85f)

    // Šikmé planoucí oči
    drawGlowSeg(pt(-88f, -42f), pt(-24f, -18f), eyes, w * 1.2f)
    drawGlowSeg(pt(-24f, -18f), pt(-68f, 4f), eyes, w * 1.2f)
    drawGlowSeg(pt(-68f, 4f), pt(-88f, -42f), eyes, w * 1.2f)

    drawGlowSeg(pt(88f, -42f), pt(24f, -18f), eyes, w * 1.2f)
    drawGlowSeg(pt(24f, -18f), pt(68f, 4f), eyes, w * 1.2f)
    drawGlowSeg(pt(68f, 4f), pt(88f, -42f), eyes, w * 1.2f)

    // Pohyblivá vektorová čelist mluvící v rytmu syntetizátoru
    val upperLipL = pt(-58f, 58f)
    val upperLipM = pt(0f, 48f)
    val upperLipR = pt(58f, 58f)
    val lowerLipL = Offset(cx - 58f * scale, cy + 58f * scale + jawOpen)
    val lowerLipM = Offset(cx, cy + 76f * scale + jawOpen)
    val lowerLipR = Offset(cx + 58f * scale, cy + 58f * scale + jawOpen)
    val chin = Offset(cx, cy + 122f * scale + jawOpen)

    drawGlowSeg(upperLipL, upperLipM, VecYellow, w)
    drawGlowSeg(upperLipM, upperLipR, VecYellow, w)
    drawGlowSeg(lowerLipL, lowerLipM, VecYellow, w)
    drawGlowSeg(lowerLipM, lowerLipR, VecYellow, w)
    drawGlowSeg(jawHingeL, chin, skin, w)
    drawGlowSeg(jawHingeR, chin, skin, w)
}
