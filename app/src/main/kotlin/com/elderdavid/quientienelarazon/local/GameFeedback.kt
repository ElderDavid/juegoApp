package com.elderdavid.quientienelarazon.local

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.elderdavid.quientienelarazon.settings.AppSettings

enum class FeedbackEvent { TICK, TAP, CORRECT, WRONG, TIMEOUT }

/**
 * Sonidos y vibraciones del juego. Usa tonos del sistema, así no hay archivos de audio que
 * descargar ni empaquetar. Respeta los ajustes de sonido y vibración.
 */
class GameFeedback(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    // Puede fallar en algunos dispositivos sin salida de audio; entonces el juego sigue sin sonido.
    private val tones: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()

    fun play(event: FeedbackEvent, settings: AppSettings) {
        if (settings.soundEnabled) {
            val (tone, millis) = when (event) {
                FeedbackEvent.TICK -> ToneGenerator.TONE_PROP_BEEP to 30
                FeedbackEvent.TAP -> ToneGenerator.TONE_PROP_BEEP2 to 60
                FeedbackEvent.CORRECT -> ToneGenerator.TONE_PROP_ACK to 200
                FeedbackEvent.WRONG -> ToneGenerator.TONE_PROP_NACK to 250
                FeedbackEvent.TIMEOUT -> ToneGenerator.TONE_CDMA_LOW_L to 300
            }
            tones?.startTone(tone, millis)
        }
        if (settings.vibrationEnabled && event != FeedbackEvent.TICK) {
            val millis = when (event) {
                FeedbackEvent.TAP -> 20L
                FeedbackEvent.CORRECT -> 40L
                else -> 120L
            }
            vibrator?.takeIf { it.hasVibrator() }
                ?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun release() {
        tones?.release()
    }
}
