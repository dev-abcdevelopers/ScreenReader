@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.annotation.RequiresApi


object HapticFeedback {

    @Volatile
    private var IsEnabled = true

    fun SetEnabled(EnabledVal: Boolean) {
        IsEnabled = EnabledVal
    }

    fun Tap(ViewRef: View?) {
        val ContextRef = ViewRef?.context ?: return
        Tap(ContextRef = ContextRef)
    }

    fun Confirm(ViewRef: View?) {
        val ContextRef = ViewRef?.context ?: return
        Confirm(ContextRef = ContextRef)
    }

    fun Reject(ViewRef: View?) {
        val ContextRef = ViewRef?.context ?: return
        Reject(ContextRef = ContextRef)
    }

    fun Tap(ContextRef: Context) {
        if (!IsEnabled) return
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return
        if (PlayComposition(VibratorRef = VibratorRef, ScaleValues = floatArrayOf(0.5f))) return
        PlayOneShot(VibratorRef = VibratorRef, DurationMs = 16L, AmplitudeValue = 110)
    }

    fun Confirm(ContextRef: Context) {
        if (!IsEnabled) return
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return
        if (PlayComposition(VibratorRef = VibratorRef, ScaleValues = floatArrayOf(1.0f))) return
        if (PlayPredefined(VibratorRef = VibratorRef, EffectId = VibrationEffect.EFFECT_CLICK)) return
        PlayOneShot(VibratorRef = VibratorRef, DurationMs = 28L, AmplitudeValue = null)
    }

    fun Reject(ContextRef: Context) {
        if (!IsEnabled) return
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return
        if (PlayComposition(
                VibratorRef = VibratorRef,
                ScaleValues = floatArrayOf(1.0f, 0.7f),
                GapMs = 90
            )
        ) return
        if (PlayPredefined(
                VibratorRef = VibratorRef,
                EffectId = VibrationEffect.EFFECT_HEAVY_CLICK
            )
        ) return
        PlayPattern(VibratorRef = VibratorRef, TimingsArray = longArrayOf(0L, 60L, 80L, 60L))
    }

    fun Success(ContextRef: Context) {
        if (!IsEnabled) return
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return
        if (PlayPredefined(
                VibratorRef = VibratorRef,
                EffectId = VibrationEffect.EFFECT_DOUBLE_CLICK
            )
        ) return
        PlayPattern(VibratorRef = VibratorRef, TimingsArray = longArrayOf(0L, 40L, 90L, 40L))
    }

    fun Failure(ContextRef: Context) {
        if (!IsEnabled) return
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return
        if (PlayPredefined(
                VibratorRef = VibratorRef,
                EffectId = VibrationEffect.EFFECT_HEAVY_CLICK
            )
        ) return
        PlayPattern(VibratorRef = VibratorRef, TimingsArray = longArrayOf(0L, 160L))
    }

    private fun PlayComposition(
        VibratorRef: Vibrator,
        ScaleValues: FloatArray,
        GapMs: Int = 0
    ): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && PlayCompositionApi30(
            VibratorRef = VibratorRef,
            ScaleValues = ScaleValues,
            GapMs = GapMs
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun PlayCompositionApi30(
        VibratorRef: Vibrator,
        ScaleValues: FloatArray,
        GapMs: Int
    ): Boolean {
        return try {
            val Supported = VibratorRef.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK
            )
            if (!Supported) return false
            var CompositionRef = VibrationEffect.startComposition()
            ScaleValues.forEachIndexed { IndexValue, ScaleValue ->
                CompositionRef = CompositionRef.addPrimitive(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    ScaleValue,
                    if (IndexValue == 0) 0 else GapMs
                )
            }
            VibratorRef.vibrate(CompositionRef.compose())
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun PlayPredefined(VibratorRef: Vibrator, EffectId: Int): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && try {
            VibratorRef.vibrate(VibrationEffect.createPredefined(EffectId))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun PlayOneShot(
        VibratorRef: Vibrator,
        DurationMs: Long,
        AmplitudeValue: Int?
    ) {
        val UseAmplitude = try {
            AmplitudeValue != null && VibratorRef.hasAmplitudeControl()
        } catch (_: Exception) {
            false
        }
        val FinalAmplitude = if (UseAmplitude && AmplitudeValue != null) {
            AmplitudeValue.coerceIn(1, 255)
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }
        try {
            VibratorRef.vibrate(VibrationEffect.createOneShot(DurationMs, FinalAmplitude))
        } catch (_: Exception) {
        }
    }

    private fun PlayPattern(VibratorRef: Vibrator, TimingsArray: LongArray) {
        try {
            VibratorRef.vibrate(VibrationEffect.createWaveform(TimingsArray, -1))
        } catch (_: Exception) {
        }
    }

    private fun ResolveVibrator(ContextRef: Context): Vibrator? {
        val VibratorRef = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val ManagerRef = ContextRef.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as? VibratorManager
                ManagerRef?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ContextRef.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
        return VibratorRef?.takeIf { VibratorObj -> VibratorObj.hasVibrator() }
    }
}
