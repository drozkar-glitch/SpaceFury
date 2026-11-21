package cz.mares.spacefury

enum class GamePhase {
    COMMANDER_INTRO,
    COMBAT,
    DOCKING,
    COMMANDER_TAUNT,
    GAME_OVER
}

enum class ControlMode {
    ARCADE_BUTTONS,
    VIRTUAL_STICK
}

enum class ShellType(val titleCz: String, val subtitleCz: String) {
    SPREAD("VĚJÍŘ A", "Trojitá palba do šířky"),
    CANNON("DVOJKANÓN B", "Dvojitý průrazný laser"),
    DUAL_AXIS("PŘEDOZADNÍ C", "Střelba vpřed i vzad")
}

enum class EnemyArchetype(val points: Int, val partPoints: Int) {
    SCOUT(500, 120),
    DESTROYER(750, 150),
    STAR_HUNTER(650, 140)
}

enum class EnemyPartKind {
    CORE,
    WING_L,
    WING_R
}

data class PlayerShip(
    var x: Float = SpaceFuryEngine.ARENA_W * 0.5f,
    var y: Float = SpaceFuryEngine.ARENA_H * 0.56f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var angleDeg: Float = -90f,
    var isThrusting: Boolean = false,
    var primaryShell: ShellType? = null,
    var secondaryShell: ShellType? = null,
    var crossbarDamaged: Boolean = false,
    var crossbarBlinkTimer: Float = 0f,
    var invulnTimer: Float = 0f,
    var alive: Boolean = true,
    var respawnTimer: Float = 0f
)

data class DockingPod(
    val shellType: ShellType,
    val x: Float,
    val y: Float,
    var pulsePhase: Float = 0f,
    var available: Boolean = true
)

data class EnemyComponentPart(
    val id: Int,
    val groupId: Int,
    val archetype: EnemyArchetype,
    val kind: EnemyPartKind,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val targetX: Float,
    val targetY: Float,
    var spinDeg: Float = 0f,
    var isRogue: Boolean = false,
    var arrived: Boolean = false
)

data class EnemyWarship(
    val id: Int,
    val archetype: EnemyArchetype,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angleDeg: Float = 0f,
    var hp: Int = 2,
    var fireCooldown: Float = 1.6f,
    var hitFlash: Float = 0f
)

data class LaserBolt(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var life: Float,
    val fromPlayer: Boolean,
    val colorCode: Int
)

data class VectorDebrisLine(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var angleDeg: Float,
    val spinSpeed: Float,
    val halfLen: Float,
    var life: Float,
    val maxLife: Float,
    val colorCode: Int
)

data class StarDot(
    val x: Float,
    val y: Float,
    val brightness: Float,
    val colorCode: Int
)

fun formatArcadeScore(value: Int): String {
    return value.coerceAtLeast(0).toString().padStart(6, '0')
}
