package cz.mares.spacefury

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
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
    var dockingCountdown by mutableStateOf(14.0f)
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

    private var phaseTimer = 2.9f
    private var fireCooldown = 0f
    private var thrustSfxTimer = 0f
    private var dockBeepSecond = 14
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
        ship.y = ARENA_H * 0.62f
        ship.vx = 0f
        ship.vy = 0f
        ship.angleDeg = -90f
        ship.primaryShell = null
        ship.secondaryShell = null
        ship.crossbarDamaged = false
        ship.crossbarBlinkTimer = 0f
        ship.invulnTimer = 2.0f
        ship.alive = true
        ship.respawnTimer = 0f

        commanderMessage = "TAKŽE PILOT S VESMÍRNÝM ÁČKEM? DO BOJE!"
        phase = GamePhase.COMMANDER_INTRO
        phaseTimer = 2.9f
        sound.playSfx(SfxType.ALIEN_INTRO_FANFARE)
        sound.speakAlien(AlienSpeechId.INTRO)
    }

    fun step(rawDt: Float) {
        if (isPaused) return
        val dt = rawDt.coerceIn(0.001f, 0.05f)
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
            ship.y = ARENA_H * 0.58f
        }
        ship.invulnTimer = maxOf(ship.invulnTimer, 1.8f)

        val groupCount = (3 + wave).coerceAtMost(8)
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

        val angle = (2.0 * PI * index.toDouble() / total.coerceAtLeast(1)) + rng.nextFloat() * 0.45
        val targetX = (ARENA_W * 0.5f + cos(angle).toFloat() * 255f).coerceIn(140f, ARENA_W - 140f)
        val targetY = (ARENA_H * 0.42f + sin(angle).toFloat() * 175f).coerceIn(120f, ARENA_H - 150f)

        val kinds = EnemyPartKind.entries
        for (kIdx in kinds.indices) {
            val edgeAngle = angle + (kIdx - 1) * 0.65
            val startX = (ARENA_W * 0.5f + cos(edgeAngle).toFloat() * 520f).coerceIn(12f, ARENA_W - 12f)
            val startY = (ARENA_H * 0.5f + sin(edgeAngle).toFloat() * 410f).coerceIn(12f, ARENA_H - 12f)
            val speed = 78f + wave * 11f + kIdx * 9f
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
        dockingCountdown = 14.0f
        dockBeepSecond = 14
        bullets.clear()
        enemyParts.clear()
        enemies.clear()

        ship.x = ARENA_W * 0.5f
        ship.y = ARENA_H * 0.72f
        ship.vx = 0f
        ship.vy = 0f
        ship.angleDeg = -90f
        ship.isThrusting = false

        dockingPods.clear()
        dockingPods.add(DockingPod(ShellType.SPREAD, ARENA_W * 0.22f, 220f))
        dockingPods.add(DockingPod(ShellType.CANNON, ARENA_W * 0.50f, 175f))
        dockingPods.add(DockingPod(ShellType.DUAL_AXIS, ARENA_W * 0.78f, 220f))
    }

    private fun enterCommanderTaunt() {
        phase = GamePhase.COMMANDER_TAUNT
        phaseTimer = 2.4f
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
            pod.pulsePhase += dt * 4.2f
            if (pod.available && hypot(ship.x - pod.x, ship.y - pod.y) < 54f) {
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
                spawnExplosionDebris(pod.x, pod.y, count = 16, colorCode = 2, speedScale = 0.85f)
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
                    ship.y = ARENA_H * 0.56f
                    ship.vx = 0f
                    ship.vy = 0f
                    ship.angleDeg = -90f
                    ship.crossbarDamaged = false
                    ship.crossbarBlinkTimer = 0f
                    ship.invulnTimer = 2.6f
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
            if (waveClearDelay >= 1.05f) {
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
            val maxTurn = 310f * dt
            if (diff > maxTurn) diff = maxTurn
            if (diff < -maxTurn) diff = -maxTurn
            ship.angleDeg = (ship.angleDeg + diff + 360f) % 360f
            ship.isThrusting = stickMagnitude > 0.42f || inputThrust
        } else {
            val turnSpeed = 235f
            if (inputLeft) ship.angleDeg -= turnSpeed * dt
            if (inputRight) ship.angleDeg += turnSpeed * dt
            ship.isThrusting = inputThrust
        }

        if (ship.isThrusting) {
            val rad = ship.angleDeg * (PI.toFloat() / 180f)
            val accel = 370f
            ship.vx += cos(rad) * accel * dt
            ship.vy += sin(rad) * accel * dt
            val speed = hypot(ship.vx, ship.vy)
            val maxSpeed = 385f
            if (speed > maxSpeed) {
                ship.vx = (ship.vx / speed) * maxSpeed
                ship.vy = (ship.vy / speed) * maxSpeed
            }
            thrustSfxTimer -= dt
            if (thrustSfxTimer <= 0f) {
                thrustSfxTimer = 0.12f
                sound.playSfx(SfxType.THRUST)
            }
        } else {
            thrustSfxTimer = 0f
        }

        val drag = 1f - 0.62f * dt
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

        fireCooldown = if (isSuper) 0.14f else if (ship.primaryShell != null) 0.16f else 0.18f
        val baseRad = ship.angleDeg * (PI.toFloat() / 180f)
        val shotSpeed = 640f

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
                    life = 0.92f,
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
            p.spinDeg = (p.spinDeg + 150f * dt) % 360f
            if (p.isRogue) {
                val dx = ship.x - p.x
                val dy = ship.y - p.y
                val d = hypot(dx, dy).coerceAtLeast(1f)
                val spd = 145f + wave * 10f
                p.vx = p.vx * 0.96f + (dx / d) * spd * 0.04f
                p.vy = p.vy * 0.96f + (dy / d) * spd * 0.04f
                p.x = wrapCoord(p.x + p.vx * dt, ARENA_W)
                p.y = wrapCoord(p.y + p.vy * dt, ARENA_H)
            } else if (!p.arrived) {
                val dx = p.targetX - p.x
                val dy = p.targetY - p.y
                val dist = hypot(dx, dy)
                if (dist <= 16f) {
                    p.x = p.targetX
                    p.y = p.targetY
                    p.arrived = true
                } else {
                    val spd = 92f + wave * 12f
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
                val spd = 98f + wave * 13f
                enemies.add(
                    EnemyWarship(
                        id = nextEntityId++,
                        archetype = sample.archetype,
                        x = sample.targetX,
                        y = sample.targetY,
                        vx = cos(baseAngle) * spd,
                        vy = sin(baseAngle) * spd,
                        hp = if (sample.archetype == EnemyArchetype.DESTROYER) 2 else 1,
                        fireCooldown = 0.9f + rng.nextFloat() * 1.1f
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
            e.angleDeg = (e.angleDeg + 115f * dt) % 360f
            if (e.hitFlash > 0f) e.hitFlash = (e.hitFlash - dt).coerceAtLeast(0f)

            if (ship.alive) {
                val dx = ship.x - e.x
                val dy = ship.y - e.y
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                val chaseWeight = if (e.archetype == EnemyArchetype.STAR_HUNTER) 0.045f else 0.018f
                val targetSpd = 115f + wave * 14f
                e.vx = e.vx * (1f - chaseWeight) + (dx / dist) * targetSpd * chaseWeight
                e.vy = e.vy * (1f - chaseWeight) + (dy / dist) * targetSpd * chaseWeight

                e.fireCooldown -= dt
                if (e.fireCooldown <= 0f && phase == GamePhase.COMBAT) {
                    e.fireCooldown = (2.35f - wave * 0.14f).coerceAtLeast(1.05f) + rng.nextFloat() * 0.8f
                    val aimAngle = atan2(dy, dx) + (rng.nextFloat() - 0.5f) * 0.24f
                    val bSpeed = 290f + wave * 18f
                    bullets.add(
                        LaserBolt(
                            x = e.x,
                            y = e.y,
                            vx = cos(aimAngle) * bSpeed,
                            vy = sin(aimAngle) * bSpeed,
                            life = 2.1f,
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
            val hitPart = enemyParts.firstOrNull { hypot(it.x - b.x, it.y - b.y) < 22f }
            if (hitPart != null) {
                bullets.remove(b)
                enemyParts.remove(hitPart)
                addPoints(hitPart.archetype.partPoints)
                spawnExplosionDebris(hitPart.x, hitPart.y, count = 8, colorCode = 1, speedScale = 0.7f)
                sound.playSfx(SfxType.ENEMY_PART_HIT)
                continue
            }

            val hitEnemy = enemies.firstOrNull { hypot(it.x - b.x, it.y - b.y) < 30f }
            if (hitEnemy != null) {
                bullets.remove(b)
                hitEnemy.hp -= 1
                hitEnemy.hitFlash = 0.18f
                if (hitEnemy.hp <= 0) {
                    enemies.remove(hitEnemy)
                    addPoints(hitEnemy.archetype.points)
                    spawnExplosionDebris(hitEnemy.x, hitEnemy.y, count = 18, colorCode = 3, speedScale = 1.0f)
                    sound.playSfx(SfxType.EXPLOSION_SMALL)
                    sound.vibrate(heavy = false)
                } else {
                    sound.playSfx(SfxType.ENEMY_PART_HIT)
                }
            }
        }

        if (!ship.alive || ship.invulnTimer > 0f) return
        val hitRadius = if (ship.primaryShell != null) 25f else 18f

        val enemyShot = bullets.firstOrNull { !it.fromPlayer && hypot(it.x - ship.x, it.y - ship.y) < hitRadius }
        if (enemyShot != null) {
            bullets.remove(enemyShot)
            handlePlayerHit()
            return
        }

        val crashPart = enemyParts.firstOrNull { hypot(it.x - ship.x, it.y - ship.y) < hitRadius + 14f }
        if (crashPart != null) {
            enemyParts.remove(crashPart)
            spawnExplosionDebris(crashPart.x, crashPart.y, 10, 1, 0.8f)
            handlePlayerHit()
            return
        }

        val crashShip = enemies.firstOrNull { hypot(it.x - ship.x, it.y - ship.y) < hitRadius + 22f }
        if (crashShip != null) {
            enemies.remove(crashShip)
            spawnExplosionDebris(crashShip.x, crashShip.y, 16, 3, 1.0f)
            handlePlayerHit()
        }
    }

    private fun handlePlayerHit() {
        if (ship.secondaryShell != null) {
            ship.secondaryShell = null
            ship.invulnTimer = 2.2f
            ship.crossbarBlinkTimer = 2.8f
            spawnExplosionDebris(ship.x, ship.y, count = 16, colorCode = 2, speedScale = 0.9f)
            sound.playSfx(SfxType.SHELL_STRIPPED)
            sound.vibrate(heavy = true)
            return
        }

        if (ship.primaryShell != null) {
            ship.primaryShell = null
            ship.invulnTimer = 2.4f
            ship.crossbarBlinkTimer = 3.4f
            spawnExplosionDebris(ship.x, ship.y, count = 18, colorCode = 1, speedScale = 0.95f)
            sound.playSfx(SfxType.SHELL_STRIPPED)
            sound.vibrate(heavy = true)
            return
        }

        if (crossbarShieldEnabled && !ship.crossbarDamaged) {
            ship.crossbarDamaged = true
            ship.invulnTimer = 2.3f
            ship.crossbarBlinkTimer = 4.0f
            spawnExplosionDebris(ship.x, ship.y, count = 10, colorCode = 2, speedScale = 0.7f)
            sound.playSfx(SfxType.SHELL_STRIPPED)
            sound.vibrate(heavy = true)
            return
        }

        ship.alive = false
        lives = (lives - 1).coerceAtLeast(0)
        ship.respawnTimer = 2.2f
        spawnShipADisintegration(ship.x, ship.y, ship.angleDeg)
        spawnExplosionDebris(ship.x, ship.y, count = 24, colorCode = 0, speedScale = 1.15f)
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
                    vx = cos(dir) * 58f,
                    vy = sin(dir) * 58f,
                    angleDeg = angleDeg + i * 55f,
                    spinSpeed = if (i % 2 == 0) 140f else -155f,
                    halfLen = if (i == 2) 13f else 22f,
                    life = 1.9f,
                    maxLife = 1.9f,
                    colorCode = if (i == 2) 2 else 0
                )
            )
        }
    }

    private fun spawnExplosionDebris(cx: Float, cy: Float, count: Int, colorCode: Int, speedScale: Float) {
        for (i in 0 until count) {
            val a = (2f * PI.toFloat() * i / count.coerceAtLeast(1)) + rng.nextFloat() * 0.35f
            val spd = (65f + rng.nextFloat() * 165f) * speedScale
            val life = 0.45f + rng.nextFloat() * 0.65f
            debris.add(
                VectorDebrisLine(
                    x = cx,
                    y = cy,
                    vx = cos(a) * spd,
                    vy = sin(a) * spd,
                    angleDeg = rng.nextFloat() * 360f,
                    spinSpeed = (rng.nextFloat() - 0.5f) * 380f,
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
