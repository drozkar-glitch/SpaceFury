package cz.mares.spacefury

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class SpaceFuryEngine(private val sound: RetroSoundEngine) {

    companion object {
        const val ARENA_W = 1000f
        const val ARENA_H = 780f
    }

    var score by mutableIntStateOf(0)
        private set
    var highScore by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
        private set
    var wave by mutableIntStateOf(1)
        private set
    var phase by mutableStateOf(GamePhase.COMMANDER_INTRO)
        private set
    var isPaused by mutableStateOf(false)
    var crossbarShieldEnabled by mutableStateOf(true)

    var commanderMessage by mutableStateOf("TAKŽE PILOT S VESMÍRNÝM ÁČKEM? DO BOJE!")
        private set
    var dockingCountdown by mutableStateOf(16.0f)
        private set
    var frameTick by mutableIntStateOf(0)
        private set
    var elapsedSec by mutableStateOf(0f)
        private set

    val ship = PlayerShip()
    val bullets = mutableListOf<LaserBolt>()
    val enemyParts = mutableListOf<EnemyComponentPart>()
    val enemies = mutableListOf<EnemyWarship>()
    val debris = mutableListOf<VectorDebrisLine>()
    val dockingPods = mutableListOf<DockingPod>()
    val stars: List<StarDot> = List(52) { i ->
        val r = Random(1981 + i * 37)
        StarDot(
            x = r.nextFloat() * ARENA_W,
            y = r.nextFloat() * ARENA_H,
            brightness = 0.25f + r.nextFloat() * 0.55f,
            colorCode = i % 4
        )
    }

    var inputLeft: Boolean = false
    var inputRight: Boolean = false
    var inputThrust: Boolean = false
    var inputFire: Boolean = false
    var stickAngleDeg: Float? = null
    var stickMagnitude: Float = 0f

    private var phaseTimer = 3.1f
    private var fireCooldown = 0f
    private var thrustSfxTimer = 0f
    private var dockBeepSecond = 16
    private var nextEntityId = 1
    private var waveClearDelay = 0f
    private var bonusLifeNextScore = 15000
    private val rng = Random(198405)

    fun startNewGame(currentBest: Int) {
        highScore = maxOf(highScore, currentBest)
        score = 0
        lives = 3
        wave = 1
        bonusLifeNextScore = 15000
        isPaused = false
        bullets.clear()
        enemyParts.clear()
        enemies.clear()
        debris.clear()
        dockingPods.clear()

        ship.x = ARENA_W * 0.5f
        ship.y = ARENA_H * 0.64f
        ship.vx = 0f
        ship.vy = 0f
        ship.angleDeg = -90f
        ship.primaryShell = null
        ship.secondaryShell = null
        ship.crossbarDamaged = false
        ship.crossbarBlinkTimer = 0f
        ship.invulnTimer = 2.2f
        ship.alive = true
        ship.respawnTimer = 0f

        commanderMessage = "TAKŽE PILOT S VESMÍRNÝM ÁČKEM? DO BOJE!"
        phase = GamePhase.COMMANDER_INTRO
        phaseTimer = 3.1f
        sound.playSfx(SfxType.ALIEN_INTRO_FANFARE)
        sound.speakAlien(AlienSpeechId.INTRO)
    }

    fun step(rawDt: Float) {
        if (isPaused) return
        val dt = rawDt.coerceIn(0.001f, 0.04f)
        elapsedSec += dt
        frameTick++

        updateDebris(dt)
        if (fireCooldown > 0f) fireCooldown = (fireCooldown - dt).coerceAtLeast(0f)
        if (ship.invulnTimer > 0f) ship.invulnTimer = (ship.invulnTimer - dt).coerceAtLeast(0f)
        if (ship.crossbarBlinkTimer > 0f) ship.crossbarBlinkTimer = (ship.crossbarBlinkTimer - dt).coerceAtLeast(0f)

        when (phase) {
            GamePhase.COMMANDER_INTRO -> {
                phaseTimer -= dt
                if (phaseTimer <= 0f || (inputFire && phaseTimer < 2.2f)) {
                    beginCombatWave()
                }
            }
            GamePhase.COMMANDER_TAUNT -> {
                phaseTimer -= dt
                if (phaseTimer <= 0f || (inputFire && phaseTimer < 1.8f)) {
                    wave++
                    beginCombatWave()
                }
            }
            GamePhase.DOCKING -> updateDockingPhase(dt)
            GamePhase.COMBAT -> updateCombatPhase(dt)
            GamePhase.GAME_OVER -> {
                updateBullets(dt)
                updateEnemies(dt)
            }
        }
    }

    private fun beginCombatWave() {
        phase = GamePhase.COMBAT
        waveClearDelay = 0f
        bullets.clear()
        enemyParts.clear()
        enemies.clear()
        dockingPods.clear()

        if (ship.x < 120f || ship.x > ARENA_W - 120f || ship.y < 120f || ship.y > ARENA_H - 120f) {
            ship.x = ARENA_W * 0.5f
            ship.y = ARENA_H * 0.62f
        }
        ship.vx *= 0.35f
        ship.vy *= 0.35f
        ship.invulnTimer = maxOf(ship.invulnTimer, 2.0f)

        val groupCount = (2 + wave).coerceAtMost(7)
        for (g in 0 until groupCount) {
            spawnAssemblyGroup(g, groupCount)
        }
    }

    private fun spawnAssemblyGroup(index: Int, total: Int) {
        val groupId = nextEntityId++
        val archetype = when {
            wave == 1 && index % 2 == 0 -> EnemyArchetype.SCOUT
            (index + wave) % 3 == 0 -> EnemyArchetype.DESTROYER
            (index + wave) % 3 == 1 -> EnemyArchetype.STAR_HUNTER
            else -> EnemyArchetype.SCOUT
        }

        val angle = (2.0 * PI * index.toDouble() / total.coerceAtLeast(1)) + rng.nextFloat() * 0.38
        val targetX = (ARENA_W * 0.5f + cos(angle).toFloat() * 240f).coerceIn(145f, ARENA_W - 145f)
        val targetY = (ARENA_H * 0.38f + sin(angle).toFloat() * 155f).coerceIn(115f, ARENA_H - 195f)

        val kinds = EnemyPartKind.entries
        for (kIdx in kinds.indices) {
            val edgeAngle = angle + (kIdx - 1) * 0.72
            val startX = (ARENA_W * 0.5f + cos(edgeAngle).toFloat() * 485f).coerceIn(16f, ARENA_W - 16f)
            val startY = (ARENA_H * 0.45f + sin(edgeAngle).toFloat() * 375f).coerceIn(16f, ARENA_H - 16f)
            val speed = 44f + (wave - 1) * 5.5f + kIdx * 4f
            val dx = targetX - startX
            val dy = targetY - startY
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            enemyParts.add(
                EnemyComponentPart(
                    id = nextEntityId++,
                    groupId = groupId,
                    archetype = archetype,
                    kind = kinds[kIdx],
                    x = startX,
                    y = startY,
                    vx = (dx / dist) * speed,
                    vy = (dy / dist) * speed,
                    targetX = targetX,
                    targetY = targetY,
                    spinDeg = rng.nextFloat() * 360f
                )
            )
        }
    }

    private fun enterDockingPhase() {
        phase = GamePhase.DOCKING
        dockingCountdown = 16.0f
        dockBeepSecond = 16
        bullets.clear()
        enemyParts.clear()
        enemies.clear()

        ship.x = ARENA_W * 0.5f
        ship.y = ARENA_H * 0.74f
        ship.vx = 0f
        ship.vy = 0f
        ship.angleDeg = -90f
        ship.isThrusting = false

        dockingPods.clear()
        dockingPods.add(DockingPod(ShellType.SPREAD, ARENA_W * 0.22f, 225f))
        dockingPods.add(DockingPod(ShellType.CANNON, ARENA_W * 0.50f, 180f))
        dockingPods.add(DockingPod(ShellType.DUAL_AXIS, ARENA_W * 0.78f, 225f))
    }

    private fun enterCommanderTaunt() {
        phase = GamePhase.COMMANDER_TAUNT
        phaseTimer = 2.6f
        val pick = wave % 3
        when (pick) {
            1 -> {
                commanderMessage = "MÉ KŘIŽNÍKY SE PRÁVĚ SKLÁDAJÍ. ZNIČTE HO!"
                sound.speakAlien(AlienSpeechId.TAUNT_1)
            }
            2 -> {
                commanderMessage = "TVŮJ PŘÍDAVNÝ PLÁŠŤ TĚ NEZACHRÁNÍ, POZEMŠŤANE!"
                sound.speakAlien(AlienSpeechId.TAUNT_2)
            }
            else -> {
                commanderMessage = "PŘEKVAPIVĚ DOBRÝ PILOT... ALE TEĎ PŘITVRDÍME!"
                sound.speakAlien(AlienSpeechId.TAUNT_3)
            }
        }
    }

    private fun updateDockingPhase(dt: Float) {
        updatePlayerMovement(dt, allowWrap = false)
        dockingCountdown = (dockingCountdown - dt).coerceAtLeast(0f)
        val wholeSec = dockingCountdown.toInt()
        if (wholeSec < dockBeepSecond && wholeSec >= 0) {
            dockBeepSecond = wholeSec
            sound.playSfx(SfxType.DOCK_BEEP)
        }

        for (pod in dockingPods) {
            pod.pulsePhase += dt * 3.2f
            if (pod.available && hypot(ship.x - pod.x, ship.y - pod.y) < 56f) {
                pod.available = false
                if (ship.primaryShell == null) {
                    ship.primaryShell = pod.shellType
                } else if (ship.secondaryShell == null && ship.primaryShell != pod.shellType) {
                    ship.secondaryShell = pod.shellType
                } else {
                    addPoints(1000)
                }
                ship.crossbarDamaged = false
                ship.crossbarBlinkTimer = 0f
                spawnExplosionDebris(pod.x, pod.y, count = 16, colorCode = 2, speedScale = 0.72f)
                sound.playSfx(SfxType.DOCK_LOCK)
                sound.vibrate(heavy = true)
                enterCommanderTaunt()
                return
            }
        }

        if (dockingCountdown <= 0f) {
            enterCommanderTaunt()
        }
    }

    private fun updateCombatPhase(dt: Float) {
        if (ship.alive) {
            updatePlayerMovement(dt, allowWrap = true)
            if (inputFire && fireCooldown <= 0f) {
                firePlayerWeapons()
            }
        } else {
            ship.respawnTimer -= dt
            if (ship.respawnTimer <= 0f) {
                if (lives > 0) {
                    ship.alive = true
                    ship.x = ARENA_W * 0.5f
                    ship.y = ARENA_H * 0.60f
                    ship.vx = 0f
                    ship.vy = 0f
                    ship.angleDeg = -90f
                    ship.crossbarDamaged = false
                    ship.crossbarBlinkTimer = 0f
                    ship.invulnTimer = 2.8f
                } else {
                    phase = GamePhase.GAME_OVER
                    commanderMessage = "HA HA HA! TVOJE RAKETA JE PRACH VESMÍRU!"
                    sound.speakAlien(AlienSpeechId.GAME_OVER)
                    return
                }
            }
        }

        updateBullets(dt)
        updateEnemyParts(dt)
        updateEnemies(dt)
        checkCollisions()

        if (enemyParts.isEmpty() && enemies.isEmpty() && ship.alive) {
            waveClearDelay += dt
            if (waveClearDelay >= 1.2f) {
                addPoints(500 * wave)
                if (ship.primaryShell == null || ship.secondaryShell == null || ship.crossbarDamaged) {
                    enterDockingPhase()
                } else {
                    enterCommanderTaunt()
                }
            }
        }
    }

    private fun updatePlayerMovement(dt: Float, allowWrap: Boolean) {
        val stickAngle = stickAngleDeg
        if (stickAngle != null && stickMagnitude > 0.14f) {
            var diff = ((stickAngle - ship.angleDeg + 540f) % 360f) - 180f
            val maxTurn = 220f * dt
            if (diff > maxTurn) diff = maxTurn
            if (diff < -maxTurn) diff = -maxTurn
            ship.angleDeg = (ship.angleDeg + diff + 360f) % 360f
            ship.isThrusting = stickMagnitude > 0.45f || inputThrust
        } else {
            val turnSpeed = 175f
            if (inputLeft) ship.angleDeg -= turnSpeed * dt
            if (inputRight) ship.angleDeg += turnSpeed * dt
            ship.isThrusting = inputThrust
        }

        if (ship.isThrusting) {
            val rad = ship.angleDeg * (PI.toFloat() / 180f)
            val accel = 215f
            ship.vx += cos(rad) * accel * dt
            ship.vy += sin(rad) * accel * dt
            val speed = hypot(ship.vx, ship.vy)
            val maxSpeed = 230f
            if (speed > maxSpeed) {
                ship.vx = (ship.vx / speed) * maxSpeed
                ship.vy = (ship.vy / speed) * maxSpeed
            }
            thrustSfxTimer -= dt
            if (thrustSfxTimer <= 0f) {
                thrustSfxTimer = 0.14f
                sound.playSfx(SfxType.THRUST)
            }
        } else {
            thrustSfxTimer = 0f
        }

        // Časově spojité přibrzďování nezávislé na 60/120 Hz
        val drag = exp(-0.85f * dt)
        ship.vx *= drag
        ship.vy *= drag
        ship.x += ship.vx * dt
        ship.y += ship.vy * dt

        if (allowWrap) {
            ship.x = wrapCoord(ship.x, ARENA_W)
            ship.y = wrapCoord(ship.y, ARENA_H)
        } else {
            ship.x = ship.x.coerceIn(45f, ARENA_W - 45f)
            ship.y = ship.y.coerceIn(55f, ARENA_H - 55f)
        }
    }

    private fun firePlayerWeapons() {
        val hasSpread = ship.primaryShell == ShellType.SPREAD || ship.secondaryShell == ShellType.SPREAD
        val hasCannon = ship.primaryShell == ShellType.CANNON || ship.secondaryShell == ShellType.CANNON
        val hasDualAxis = ship.primaryShell == ShellType.DUAL_AXIS || ship.secondaryShell == ShellType.DUAL_AXIS
        val isSuper = ship.primaryShell != null && ship.secondaryShell != null

        fireCooldown = if (isSuper) 0.16f else if (ship.primaryShell != null) 0.18f else 0.21f
        val baseRad = ship.angleDeg * (PI.toFloat() / 180f)
        val shotSpeed = 480f

        fun emitBolt(offsetDeg: Float, lateralOffset: Float, colorCode: Int) {
            val r = (ship.angleDeg + offsetDeg) * (PI.toFloat() / 180f)
            val perpR = baseRad + (PI.toFloat() * 0.5f)
            val sx = wrapCoord(ship.x + cos(r) * 22f + cos(perpR) * lateralOffset, ARENA_W)
            val sy = wrapCoord(ship.y + sin(r) * 22f + sin(perpR) * lateralOffset, ARENA_H)
            bullets.add(
                LaserBolt(
                    x = sx,
                    y = sy,
                    vx = cos(r) * shotSpeed + ship.vx * 0.25f,
                    vy = sin(r) * shotSpeed + ship.vy * 0.25f,
                    life = 1.15f,
                    fromPlayer = true,
                    colorCode = colorCode
                )
            )
        }

        if (!hasSpread && !hasCannon && !hasDualAxis) {
            emitBolt(0f, 0f, 0)
            sound.playSfx(SfxType.LASER_A)
            return
        }

        if (hasCannon) {
            emitBolt(0f, -9f, 2)
            emitBolt(0f, 9f, 2)
        } else {
            emitBolt(0f, 0f, 1)
        }
        if (hasSpread) {
            emitBolt(-19f, -6f, 1)
            emitBolt(19f, 6f, 1)
        }
        if (hasDualAxis) {
            emitBolt(180f, 0f, 3)
        }
        sound.playSfx(if (isSuper) SfxType.LASER_SUPER else SfxType.LASER_SHELL_1)
    }

    private fun updateBullets(dt: Float) {
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.life -= dt
            if (b.life <= 0f) {
                it.remove()
            } else {
                b.x = wrapCoord(b.x + b.vx * dt, ARENA_W)
                b.y = wrapCoord(b.y + b.vy * dt, ARENA_H)
            }
        }
    }

    private fun updateEnemyParts(dt: Float) {
        for (p in enemyParts) {
            p.spinDeg = (p.spinDeg + 95f * dt) % 360f
            if (p.isRogue) {
                val dx = ship.x - p.x
                val dy = ship.y - p.y
                val d = hypot(dx, dy).coerceAtLeast(1f)
                val spd = 82f + (wave - 1) * 7f
                val blend = (1.15f * dt).coerceIn(0f, 1f)
                p.vx += ((dx / d) * spd - p.vx) * blend
                p.vy += ((dy / d) * spd - p.vy) * blend
                p.x = wrapCoord(p.x + p.vx * dt, ARENA_W)
                p.y = wrapCoord(p.y + p.vy * dt, ARENA_H)
            } else if (!p.arrived) {
                val dx = p.targetX - p.x
                val dy = p.targetY - p.y
                val dist = hypot(dx, dy)
                if (dist <= 12f) {
                    p.x = p.targetX
                    p.y = p.targetY
                    p.arrived = true
                } else {
                    val spd = 48f + (wave - 1) * 6f
                    p.vx = (dx / dist) * spd
                    p.vy = (dy / dist) * spd
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                }
            }
        }

        val groupedIds = enemyParts.map { it.groupId }.distinct()
        for (gid in groupedIds) {
            val members = enemyParts.filter { it.groupId == gid }
            if (members.size == 3 && members.all { it.arrived }) {
                val sample = members[0]
                enemyParts.removeAll { it.groupId == gid }
                val baseAngle = rng.nextFloat() * 2f * PI.toFloat()
                val spd = 64f + (wave - 1) * 7.5f
                enemies.add(
                    EnemyWarship(
                        id = nextEntityId++,
                        archetype = sample.archetype,
                        x = sample.targetX,
                        y = sample.targetY,
                        vx = cos(baseAngle) * spd,
                        vy = sin(baseAngle) * spd,
                        hp = if (sample.archetype == EnemyArchetype.DESTROYER) 2 else 1,
                        fireCooldown = 1.4f + rng.nextFloat() * 1.4f
                    )
                )
                sound.playSfx(SfxType.ENEMY_ASSEMBLE)
            } else if (members.size < 3) {
                for (m in members) {
                    m.isRogue = true
                }
            }
        }
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            e.angleDeg = (e.angleDeg + 68f * dt) % 360f
            if (e.hitFlash > 0f) e.hitFlash = (e.hitFlash - dt).coerceAtLeast(0f)

            if (ship.alive) {
                val dx = ship.x - e.x
                val dy = ship.y - e.y
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                // Navádění plně násobené dt (stejná rychlost na 60 Hz i 120 Hz)
                val chaseRate = if (e.archetype == EnemyArchetype.STAR_HUNTER) 1.35f else 0.62f
                val blend = (chaseRate * dt).coerceIn(0f, 1f)
                val targetSpd = 68f + (wave - 1) * 8f
                e.vx += ((dx / dist) * targetSpd - e.vx) * blend
                e.vy += ((dy / dist) * targetSpd - e.vy) * blend

                e.fireCooldown -= dt
                if (e.fireCooldown <= 0f && phase == GamePhase.COMBAT) {
                    e.fireCooldown = (3.15f - (wave - 1) * 0.16f).coerceAtLeast(1.45f) + rng.nextFloat() * 1.05f
                    val aimAngle = atan2(dy, dx) + (rng.nextFloat() - 0.5f) * 0.28f
                    val bSpeed = 185f + (wave - 1) * 12f
                    bullets.add(
                        LaserBolt(
                            x = e.x,
                            y = e.y,
                            vx = cos(aimAngle) * bSpeed,
                            vy = sin(aimAngle) * bSpeed,
                            life = 2.6f,
                            fromPlayer = false,
                            colorCode = 3
                        )
                    )
                    sound.playSfx(SfxType.ENEMY_FIRE)
                }
            }

            e.x = wrapCoord(e.x + e.vx * dt, ARENA_W)
            e.y = wrapCoord(e.y + e.vy * dt, ARENA_H)
        }
    }

    private fun checkCollisions() {
        val pBullets = bullets.filter { it.fromPlayer }
        for (b in pBullets) {
            val hitPart = enemyParts.firstOrNull { hypot(it.x - b.x, it.y - b.y) < 23f }
            if (hitPart != null) {
                bullets.remove(b)
                enemyParts.remove(hitPart)
                addPoints(hitPart.archetype.partPoints)
                spawnExplosionDebris(hitPart.x, hitPart.y, count = 8, colorCode = 1, speedScale = 0.62f)
                sound.playSfx(SfxType.ENEMY_PART_HIT)
                continue
            }

            val hitEnemy = enemies.firstOrNull { hypot(it.x - b.x, it.y - b.y) < 31f }
            if (hitEnemy != null) {
                bullets.remove(b)
                hitEnemy.hp -= 1
                hitEnemy.hitFlash = 0.18f
                if (hitEnemy.hp <= 0) {
                    enemies.remove(hitEnemy)
                    addPoints(hitEnemy.archetype.points)
                    spawnExplosionDebris(hitEnemy.x, hitEnemy.y, count = 18, colorCode = 3, speedScale = 0.82f)
                    sound.playSfx(SfxType.EXPLOSION_SMALL)
                    sound.vibrate(heavy = false)
                } else {
                    sound.playSfx(SfxType.ENEMY_PART_HIT)
                }
            }
        }

        if (!ship.alive || ship.invulnTimer > 0f) return
        val hitRadius = if (ship.primaryShell != null) 24f else 17f

        val enemyShot = bullets.firstOrNull { !it.fromPlayer && hypot(it.x - ship.x, it.y - ship.y) < hitRadius }
        if (enemyShot != null) {
            bullets.remove(enemyShot)
            handlePlayerHit()
            return
        }

        val crashPart = enemyParts.firstOrNull { hypot(it.x - ship.x, it.y - ship.y) < hitRadius + 13f }
        if (crashPart != null) {
            enemyParts.remove(crashPart)
            spawnExplosionDebris(crashPart.x, crashPart.y, 10, 1, 0.7f)
            handlePlayerHit()
            return
        }

        val crashShip = enemies.firstOrNull { hypot(it.x - ship.x, it.y - ship.y) < hitRadius + 21f }
        if (crashShip != null) {
            enemies.remove(crashShip)
            spawnExplosionDebris(crashShip.x, crashShip.y, 16, 3, 0.85f)
            handlePlayerHit()
        }
    }

    private fun handlePlayerHit() {
        if (ship.secondaryShell != null) {
            ship.secondaryShell = null
            ship.invulnTimer = 2.4f
            ship.crossbarBlinkTimer = 3.2f
            spawnExplosionDebris(ship.x, ship.y, count = 16, colorCode = 2, speedScale = 0.78f)
            sound.playSfx(SfxType.SHELL_STRIPPED)
            sound.vibrate(heavy = true)
            return
        }

        if (ship.primaryShell != null) {
            ship.primaryShell = null
            ship.invulnTimer = 2.6f
            ship.crossbarBlinkTimer = 3.8f
            spawnExplosionDebris(ship.x, ship.y, count = 18, colorCode = 1, speedScale = 0.82f)
            sound.playSfx(SfxType.SHELL_STRIPPED)
            sound.vibrate(heavy = true)
            return
        }

        if (crossbarShieldEnabled && !ship.crossbarDamaged) {
            ship.crossbarDamaged = true
            ship.invulnTimer = 2.5f
            ship.crossbarBlinkTimer = 4.5f
            spawnExplosionDebris(ship.x, ship.y, count = 10, colorCode = 2, speedScale = 0.65f)
            sound.playSfx(SfxType.SHELL_STRIPPED)
            sound.vibrate(heavy = true)
            return
        }

        ship.alive = false
        lives = (lives - 1).coerceAtLeast(0)
        ship.respawnTimer = 2.3f
        spawnShipADisintegration(ship.x, ship.y, ship.angleDeg)
        spawnExplosionDebris(ship.x, ship.y, count = 24, colorCode = 0, speedScale = 0.92f)
        sound.playSfx(SfxType.EXPLOSION_BIG)
        sound.vibrate(heavy = true)
    }

    private fun spawnShipADisintegration(cx: Float, cy: Float, angleDeg: Float) {
        for (i in 0 until 3) {
            val dir = (angleDeg + i * 120f) * (PI.toFloat() / 180f)
            debris.add(
                VectorDebrisLine(
                    x = cx,
                    y = cy,
                    vx = cos(dir) * 44f,
                    vy = sin(dir) * 44f,
                    angleDeg = angleDeg + i * 55f,
                    spinSpeed = if (i % 2 == 0) 105f else -120f,
                    halfLen = if (i == 2) 13f else 22f,
                    life = 2.1f,
                    maxLife = 2.1f,
                    colorCode = if (i == 2) 2 else 0
                )
            )
        }
    }

    private fun spawnExplosionDebris(cx: Float, cy: Float, count: Int, colorCode: Int, speedScale: Float) {
        for (i in 0 until count) {
            val a = (2f * PI.toFloat() * i / count.coerceAtLeast(1)) + rng.nextFloat() * 0.35f
            val spd = (52f + rng.nextFloat() * 135f) * speedScale
            val life = 0.55f + rng.nextFloat() * 0.70f
            debris.add(
                VectorDebrisLine(
                    x = cx,
                    y = cy,
                    vx = cos(a) * spd,
                    vy = sin(a) * spd,
                    angleDeg = rng.nextFloat() * 360f,
                    spinSpeed = (rng.nextFloat() - 0.5f) * 280f,
                    halfLen = 6f + rng.nextFloat() * 10f,
                    life = life,
                    maxLife = life,
                    colorCode = colorCode
                )
            )
        }
    }

    private fun updateDebris(dt: Float) {
        val it = debris.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.life -= dt
            if (d.life <= 0f) {
                it.remove()
            } else {
                d.x = wrapCoord(d.x + d.vx * dt, ARENA_W)
                d.y = wrapCoord(d.y + d.vy * dt, ARENA_H)
                d.angleDeg = (d.angleDeg + d.spinSpeed * dt) % 360f
            }
        }
    }

    private fun addPoints(pts: Int) {
        score += pts
        if (score > highScore) {
            highScore = score
        }
        if (score >= bonusLifeNextScore) {
            lives = (lives + 1).coerceAtMost(6)
            bonusLifeNextScore += 15000
            sound.playSfx(SfxType.DOCK_LOCK)
        }
    }

    private fun wrapCoord(v: Float, max: Float): Float {
        var res = v % max
        if (res < 0f) res += max
        return res
    }
}
