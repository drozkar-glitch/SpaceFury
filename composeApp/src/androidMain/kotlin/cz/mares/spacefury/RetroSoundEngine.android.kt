package cz.mares.spacefury

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class AndroidRetroSoundEngine(private val context: Context) : RetroSoundEngine {
    override var soundEnabled: Boolean = true
    override var vibrationEnabled: Boolean = true

    private val sfxPools: Array<Array<AudioTrack?>> = Array(SfxType.entries.size) { idx ->
        val pcm = RetroAudioSynth.generateSfxPcm(SfxType.entries[idx])
        Array(2) { buildStaticTrack(pcm) }
    }
    private val sfxRobin = IntArray(SfxType.entries.size)

    private val speechTracks: Array<AudioTrack?> = Array(AlienSpeechId.entries.size) { idx ->
        val pcm = RetroAudioSynth.generateSpeechPcm(AlienSpeechId.entries[idx])
        buildStaticTrack(pcm)
    }

    private fun buildStaticTrack(pcm: ShortArray): AudioTrack? {
        return try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RetroAudioSynth.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack(
                attrs,
                format,
                pcm.size * 2,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            track.write(pcm, 0, pcm.size)
            track
        } catch (_: Throwable) {
            null
        }
    }

    override fun playSfx(type: SfxType) {
        if (!soundEnabled) return
        val ord = type.ordinal
        val pool = sfxPools[ord]
        val slot = sfxRobin[ord] % pool.size
        sfxRobin[ord] = (slot + 1) % pool.size
        val track = pool[slot] ?: return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.setPlaybackHeadPosition(0)
            track.play()
        } catch (_: Throwable) {
        }
    }

    override fun speakAlien(id: AlienSpeechId) {
        if (!soundEnabled) return
        val track = speechTracks[id.ordinal] ?: return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.setPlaybackHeadPosition(0)
            track.play()
        } catch (_: Throwable) {
        }
    }

    override fun vibrate(heavy: Boolean) {
        if (!vibrationEnabled) return
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            val ms = if (heavy) 55L else 18L
            val amp = if (heavy) 210 else 95
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (_: Throwable) {
        }
    }

    override fun release() {
        for (pool in sfxPools) {
            for (t in pool) {
                try {
                    t?.stop()
                    t?.release()
                } catch (_: Throwable) {
                }
            }
        }
        for (t in speechTracks) {
            try {
                t?.stop()
                t?.release()
            } catch (_: Throwable) {
            }
        }
    }
}

@Composable
actual fun rememberRetroSoundEngine(): RetroSoundEngine {
    val context = LocalContext.current
    val engine = remember(context) { AndroidRetroSoundEngine(context.applicationContext) }
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    return engine
}
