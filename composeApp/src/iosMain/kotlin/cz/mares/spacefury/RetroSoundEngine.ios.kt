package cz.mares.spacefury

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

@OptIn(ExperimentalForeignApi::class)
private class IosRetroSoundEngine : RetroSoundEngine {
    override var soundEnabled: Boolean = true
    override var vibrationEnabled: Boolean = true

    private val lightImpact = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val heavyImpact = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)

    private val sfxPools: Array<Array<AVAudioPlayer?>>
    private val sfxRobin = IntArray(SfxType.entries.size)
    private val speechPlayers: Array<AVAudioPlayer?>

    init {
        val tmpDir = NSTemporaryDirectory()
        sfxPools = Array(SfxType.entries.size) { idx ->
            val type = SfxType.entries[idx]
            val wav = RetroAudioSynth.pcmToWavBytes(RetroAudioSynth.generateSfxPcm(type))
            val path = "${tmpDir}sf_sfx_${type.name.lowercase()}.wav"
            writeFileBytes(path, wav)
            val url = NSURL.fileURLWithPath(path)
            Array(2) {
                val player = AVAudioPlayer(contentsOfURL = url, error = null)
                player?.prepareToPlay()
                player
            }
        }

        speechPlayers = Array(AlienSpeechId.entries.size) { idx ->
            val id = AlienSpeechId.entries[idx]
            val wav = RetroAudioSynth.pcmToWavBytes(RetroAudioSynth.generateSpeechPcm(id))
            val path = "${tmpDir}sf_voice_${id.name.lowercase()}.wav"
            writeFileBytes(path, wav)
            val url = NSURL.fileURLWithPath(path)
            val player = AVAudioPlayer(contentsOfURL = url, error = null)
            player?.prepareToPlay()
            player
        }
    }

    private fun writeFileBytes(path: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val file = fopen(path, "wb") ?: return
        try {
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
            }
        } finally {
            fclose(file)
        }
    }

    override fun playSfx(type: SfxType) {
        if (!soundEnabled) return
        val ord = type.ordinal
        val pool = sfxPools[ord]
        val slot = sfxRobin[ord] % pool.size
        sfxRobin[ord] = (slot + 1) % pool.size
        val player = pool[slot] ?: return
        player.currentTime = 0.0
        player.play()
    }

    override fun speakAlien(id: AlienSpeechId) {
        if (!soundEnabled) return
        val player = speechPlayers[id.ordinal] ?: return
        player.currentTime = 0.0
        player.play()
    }

    override fun vibrate(heavy: Boolean) {
        if (!vibrationEnabled) return
        if (heavy) {
            heavyImpact.prepare()
            heavyImpact.impactOccurred()
        } else {
            lightImpact.prepare()
            lightImpact.impactOccurred()
        }
    }

    override fun release() {
        for (pool in sfxPools) {
            for (p in pool) {
                p?.stop()
            }
        }
        for (p in speechPlayers) {
            p?.stop()
        }
    }
}

@Composable
actual fun rememberRetroSoundEngine(): RetroSoundEngine {
    val engine = remember { IosRetroSoundEngine() }
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    return engine
}
