package cz.mares.spacefury

import androidx.compose.runtime.Composable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

enum class SfxType {
    LASER_A,
    LASER_SHELL_1,
    LASER_SUPER,
    THRUST,
    ENEMY_PART_HIT,
    ENEMY_ASSEMBLE,
    ENEMY_FIRE,
    EXPLOSION_SMALL,
    EXPLOSION_BIG,
    SHELL_STRIPPED,
    DOCK_BEEP,
    DOCK_LOCK,
    ALIEN_INTRO_FANFARE
}

enum class AlienSpeechId {
    INTRO,
    TAUNT_1,
    TAUNT_2,
    TAUNT_3,
    GAME_OVER
}

interface RetroSoundEngine {
    var soundEnabled: Boolean
    var vibrationEnabled: Boolean
    fun playSfx(type: SfxType)
    fun speakAlien(id: AlienSpeechId)
    fun vibrate(heavy: Boolean = false)
    fun release()
}

@Composable
expect fun rememberRetroSoundEngine(): RetroSoundEngine

object RetroAudioSynth {
    const val SAMPLE_RATE = 16000

    fun generateSfxPcm(type: SfxType): ShortArray {
        return when (type) {
            SfxType.LASER_A -> synthSweep(85, 1680.0, 270.0, 0.72, 0.0, 0.42)
            SfxType.LASER_SHELL_1 -> synthDualLaser(105, 1920.0, 240.0, 960.0, 155.0, 0.46)
            SfxType.LASER_SUPER -> synthDualLaser(128, 2280.0, 195.0, 1140.0, 115.0, 0.50)
            SfxType.THRUST -> synthThrustNoise(115, 0.26)
            SfxType.ENEMY_PART_HIT -> synthNotes(doubleArrayOf(1120.0, 1680.0, 560.0), 30, 0.38)
            SfxType.ENEMY_ASSEMBLE -> synthNotes(doubleArrayOf(440.0, 587.3, 880.0, 1174.6), 52, 0.40)
            SfxType.ENEMY_FIRE -> synthSweep(110, 840.0, 295.0, 0.45, 36.0, 0.34)
            SfxType.EXPLOSION_SMALL -> synthExplosion(270, 175.0, 0.48)
            SfxType.EXPLOSION_BIG -> synthExplosion(620, 130.0, 0.62)
            SfxType.SHELL_STRIPPED -> synthNotes(doubleArrayOf(940.0, 470.0, 940.0, 470.0, 880.0, 420.0), 68, 0.50)
            SfxType.DOCK_BEEP -> synthNotes(doubleArrayOf(1174.6), 55, 0.28)
            SfxType.DOCK_LOCK -> synthNotes(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 88, 0.45)
            SfxType.ALIEN_INTRO_FANFARE -> synthFanfare()
        }
    }

    fun generateSpeechPcm(id: AlienSpeechId): ShortArray {
        val syllables: Array<FormantSyllable> = when (id) {
            AlienSpeechId.INTRO -> arrayOf(
                FormantSyllable(450.0, 1150.0, 165, 98.0),
                FormantSyllable(0.0, 0.0, 55, 98.0),
                FormantSyllable(700.0, 1250.0, 95, 104.0),
                FormantSyllable(320.0, 2150.0, 140, 108.0),
                FormantSyllable(520.0, 1500.0, 130, 100.0),
                FormantSyllable(0.0, 0.0, 70, 96.0),
                FormantSyllable(380.0, 1950.0, 120, 106.0),
                FormantSyllable(620.0, 1720.0, 155, 112.0),
                FormantSyllable(480.0, 1050.0, 120, 98.0),
                FormantSyllable(740.0, 1340.0, 185, 90.0)
            )
            AlienSpeechId.TAUNT_1 -> arrayOf(
                FormantSyllable(330.0, 2050.0, 130, 106.0),
                FormantSyllable(490.0, 1350.0, 125, 102.0),
                FormantSyllable(420.0, 1120.0, 160, 96.0),
                FormantSyllable(0.0, 0.0, 65, 96.0),
                FormantSyllable(640.0, 1220.0, 115, 104.0),
                FormantSyllable(680.0, 1180.0, 135, 110.0),
                FormantSyllable(430.0, 1080.0, 130, 100.0),
                FormantSyllable(360.0, 1980.0, 115, 96.0),
                FormantSyllable(620.0, 1320.0, 185, 88.0)
            )
            AlienSpeechId.TAUNT_2 -> arrayOf(
                FormantSyllable(340.0, 2100.0, 125, 108.0),
                FormantSyllable(710.0, 1180.0, 130, 104.0),
                FormantSyllable(370.0, 2020.0, 120, 108.0),
                FormantSyllable(540.0, 1650.0, 145, 112.0),
                FormantSyllable(390.0, 1020.0, 110, 100.0),
                FormantSyllable(520.0, 1080.0, 175, 114.0),
                FormantSyllable(310.0, 2220.0, 195, 92.0)
            )
            AlienSpeechId.TAUNT_3 -> arrayOf(
                FormantSyllable(690.0, 1280.0, 145, 105.0),
                FormantSyllable(340.0, 2180.0, 175, 112.0),
                FormantSyllable(390.0, 1450.0, 120, 104.0),
                FormantSyllable(470.0, 1780.0, 130, 108.0),
                FormantSyllable(560.0, 1120.0, 165, 114.0),
                FormantSyllable(350.0, 2050.0, 190, 90.0)
            )
            AlienSpeechId.GAME_OVER -> arrayOf(
                FormantSyllable(740.0, 1280.0, 175, 118.0),
                FormantSyllable(0.0, 0.0, 55, 110.0),
                FormantSyllable(720.0, 1240.0, 175, 108.0),
                FormantSyllable(0.0, 0.0, 55, 98.0),
                FormantSyllable(700.0, 1200.0, 210, 94.0),
                FormantSyllable(0.0, 0.0, 75, 90.0),
                FormantSyllable(560.0, 1680.0, 140, 102.0),
                FormantSyllable(490.0, 1520.0, 155, 96.0),
                FormantSyllable(360.0, 1980.0, 205, 86.0)
            )
        }

        val totalSamples = syllables.sumOf { (it.durationMs * SAMPLE_RATE) / 1000 }
        val out = ShortArray(totalSamples)
        var cursor = 0
        var carrierPhase = 0.0
        var f1Phase = 0.0
        var f2Phase = 0.0
        var lfsr = 0x5A3Cu

        for (syl in syllables) {
            val count = (syl.durationMs * SAMPLE_RATE) / 1000
            for (i in 0 until count) {
                if (cursor >= out.size) break
                if (syl.f1 <= 1.0) {
                    out[cursor++] = 0
                    continue
                }
                val env = when {
                    i < 90 -> i / 90.0
                    i > count - 140 -> (count - i).coerceAtLeast(0) / 140.0
                    else -> 1.0
                }
                carrierPhase += syl.pitchHz / SAMPLE_RATE
                if (carrierPhase >= 1.0) {
                    carrierPhase -= 1.0
                    f1Phase = 0.0
                    f2Phase = 0.0
                }
                f1Phase += syl.f1 / SAMPLE_RATE
                f2Phase += syl.f2 / SAMPLE_RATE

                val glottalDecay = exp(-3.4 * carrierPhase)
                val formantWave = (sin(2.0 * PI * f1Phase) * 0.68 + sin(2.0 * PI * f2Phase) * 0.42) * glottalDecay

                val bit = ((lfsr shr 0) xor (lfsr shr 1)) and 1u
                lfsr = (lfsr shr 1) or (bit shl 14)
                val consonant = if (i < 160) ((if (bit == 1u) 1.0 else -1.0) * 0.14) else 0.0

                val sample = ((formantWave + consonant) * env * 0.52).coerceIn(-0.98, 0.98)
                out[cursor++] = (sample * 32760.0).toInt().toShort()
            }
        }
        return out
    }

    fun pcmToWavBytes(pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val totalSize = 44 + dataSize
        val bytes = ByteArray(totalSize)

        fun writeAscii(offset: Int, text: String) {
            for (i in text.indices) {
                bytes[offset + i] = text[i].code.toByte()
            }
        }

        fun writeIntLE(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        }

        fun writeShortLE(offset: Int, value: Int) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeIntLE(4, 36 + dataSize)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeIntLE(16, 16)
        writeShortLE(20, 1)
        writeShortLE(22, 1)
        writeIntLE(24, SAMPLE_RATE)
        writeIntLE(28, SAMPLE_RATE * 2)
        writeShortLE(32, 2)
        writeShortLE(34, 16)
        writeAscii(36, "data")
        writeIntLE(40, dataSize)

        var pos = 44
        for (s in pcm) {
            val v = s.toInt()
            bytes[pos] = (v and 0xFF).toByte()
            bytes[pos + 1] = ((v ushr 8) and 0xFF).toByte()
            pos += 2
        }
        return bytes
    }

    private class FormantSyllable(
        val f1: Double,
        val f2: Double,
        val durationMs: Int,
        val pitchHz: Double
    )

    private fun synthSweep(
        durationMs: Int,
        startHz: Double,
        endHz: Double,
        squareMix: Double,
        vibratoHz: Double,
        volume: Double
    ): ShortArray {
        val n = (durationMs * SAMPLE_RATE) / 1000
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n.coerceAtLeast(1)
            val vib = if (vibratoHz > 0.0) 1.0 + 0.08 * sin(2.0 * PI * vibratoHz * (i.toDouble() / SAMPLE_RATE)) else 1.0
            val freq = (startHz + (endHz - startHz) * t) * vib
            phase += freq / SAMPLE_RATE
            val sine = sin(2.0 * PI * phase)
            val sq = if (sine >= 0.0) 1.0 else -1.0
            val env = (1.0 - t * 0.85) * (if (i < 48) i / 48.0 else 1.0)
            val v = ((sq * squareMix + sine * (1.0 - squareMix)) * env * volume).coerceIn(-0.98, 0.98)
            out[i] = (v * 32760.0).toInt().toShort()
        }
        return out
    }

    private fun synthDualLaser(
        durationMs: Int,
        startHz1: Double,
        endHz1: Double,
        startHz2: Double,
        endHz2: Double,
        volume: Double
    ): ShortArray {
        val n = (durationMs * SAMPLE_RATE) / 1000
        val out = ShortArray(n)
        var p1 = 0.0
        var p2 = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n.coerceAtLeast(1)
            p1 += (startHz1 + (endHz1 - startHz1) * t) / SAMPLE_RATE
            p2 += (startHz2 + (endHz2 - startHz2) * t) / SAMPLE_RATE
            val w1 = if (sin(2.0 * PI * p1) >= 0.0) 1.0 else -1.0
            val w2 = sin(2.0 * PI * p2)
            val env = (1.0 - t * 0.82) * (if (i < 40) i / 40.0 else 1.0)
            val v = ((w1 * 0.58 + w2 * 0.42) * env * volume).coerceIn(-0.98, 0.98)
            out[i] = (v * 32760.0).toInt().toShort()
        }
        return out
    }

    private fun synthThrustNoise(durationMs: Int, volume: Double): ShortArray {
        val n = (durationMs * SAMPLE_RATE) / 1000
        val out = ShortArray(n)
        var lfsr = 0x7FFFu
        var filtered = 0.0
        var humPhase = 0.0
        for (i in 0 until n) {
            if (i % 7 == 0) {
                val bit = ((lfsr shr 0) xor (lfsr shr 1)) and 1u
                lfsr = (lfsr shr 1) or (bit shl 14)
            }
            val raw = if ((lfsr and 1u) == 1u) 1.0 else -1.0
            filtered = filtered * 0.78 + raw * 0.22
            humPhase += 92.0 / SAMPLE_RATE
            val env = when {
                i < 120 -> i / 120.0
                i > n - 180 -> (n - i).coerceAtLeast(0) / 180.0
                else -> 1.0
            }
            val v = ((filtered * 0.75 + sin(2.0 * PI * humPhase) * 0.25) * env * volume).coerceIn(-0.98, 0.98)
            out[i] = (v * 32760.0).toInt().toShort()
        }
        return out
    }

    private fun synthExplosion(durationMs: Int, subStartHz: Double, volume: Double): ShortArray {
        val n = (durationMs * SAMPLE_RATE) / 1000
        val out = ShortArray(n)
        var lfsr = 0xACE1u
        var subPhase = 0.0
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n.coerceAtLeast(1)
            val holdDiv = 3 + (t * 14.0).toInt()
            if (i % holdDiv == 0) {
                val bit = ((lfsr shr 0) xor (lfsr shr 2) xor (lfsr shr 3) xor (lfsr shr 5)) and 1u
                lfsr = (lfsr shr 1) or (bit shl 15)
            }
            val noise = if ((lfsr and 1u) == 1u) 1.0 else -1.0
            lp = lp * 0.65 + noise * 0.35
            val subHz = subStartHz * (1.0 - 0.76 * t)
            subPhase += subHz / SAMPLE_RATE
            val sub = sin(2.0 * PI * subPhase)
            val env = exp(-3.1 * t)
            val v = ((lp * 0.68 + sub * 0.48) * env * volume).coerceIn(-0.98, 0.98)
            out[i] = (v * 32760.0).toInt().toShort()
        }
        return out
    }

    private fun synthNotes(freqs: DoubleArray, stepMs: Int, volume: Double): ShortArray {
        val stepSamples = (stepMs * SAMPLE_RATE) / 1000
        val out = ShortArray(stepSamples * freqs.size)
        var phase = 0.0
        var idx = 0
        for (f in freqs) {
            for (i in 0 until stepSamples) {
                phase += f / SAMPLE_RATE
                val s = sin(2.0 * PI * phase)
                val sq = if (s >= 0.0) 1.0 else -1.0
                val env = when {
                    i < 40 -> i / 40.0
                    i > stepSamples - 80 -> (stepSamples - i).coerceAtLeast(0) / 80.0
                    else -> 1.0
                }
                val v = ((sq * 0.62 + s * 0.38) * env * volume).coerceIn(-0.98, 0.98)
                out[idx++] = (v * 32760.0).toInt().toShort()
            }
        }
        return out
    }

    private fun synthFanfare(): ShortArray {
        val notes = doubleArrayOf(293.66, 220.00, 233.08, 220.00, 146.83)
        val durations = intArrayOf(150, 130, 160, 140, 310)
        val total = durations.sumOf { (it * SAMPLE_RATE) / 1000 }
        val out = ShortArray(total)
        var cursor = 0
        var p1 = 0.0
        var p2 = 0.0
        for (nIdx in notes.indices) {
            val f = notes[nIdx]
            val count = (durations[nIdx] * SAMPLE_RATE) / 1000
            for (i in 0 until count) {
                if (cursor >= out.size) break
                p1 += f / SAMPLE_RATE
                p2 += (f * 2.003) / SAMPLE_RATE
                val saw1 = 2.0 * (p1 - floor(p1 + 0.5))
                val sq2 = if (cos(2.0 * PI * p2) >= 0.0) 1.0 else -1.0
                val env = when {
                    i < 60 -> i / 60.0
                    i > count - 160 -> (count - i).coerceAtLeast(0) / 160.0
                    else -> 0.92
                }
                val v = ((saw1 * 0.58 + sq2 * 0.42) * env * 0.46).coerceIn(-0.98, 0.98)
                out[cursor++] = (v * 32760.0).toInt().toShort()
            }
        }
        return out
    }
}
