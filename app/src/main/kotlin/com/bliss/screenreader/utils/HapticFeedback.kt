@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi


object HapticFeedback {

    @Volatile
    private var IsEnabled = true

    fun SetEnabled(EnabledVal: Boolean) {
        IsEnabled = EnabledVal
    }

    fun Tap(ViewRef: View?) {
        if (!IsEnabled) return
        val ContextRef = ViewRef?.context
        if (ContextRef != null && PlayOnVibrator(ContextRef = ContextRef, KindVal = Kind.Tap)) return
        PlayOnView(ViewRef = ViewRef, ConstantId = HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun Confirm(ViewRef: View?) {
        if (!IsEnabled) return
        val ContextRef = ViewRef?.context
        if (ContextRef != null && PlayOnVibrator(
                ContextRef = ContextRef,
                KindVal = Kind.Confirm
            )
        ) return
        PlayOnView(ViewRef = ViewRef, ConstantId = HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun Reject(ViewRef: View?) {
        if (!IsEnabled) return
        val ContextRef = ViewRef?.context
        if (ContextRef != null && PlayOnVibrator(
                ContextRef = ContextRef,
                KindVal = Kind.Reject
            )
        ) return
        PlayOnView(ViewRef = ViewRef, ConstantId = HapticFeedbackConstants.LONG_PRESS)
    }

    fun Tap(ContextRef: Context) {
        if (!IsEnabled) return
        PlayOnVibrator(ContextRef = ContextRef, KindVal = Kind.Tap)
    }

    fun Confirm(ContextRef: Context) {
        if (!IsEnabled) return
        PlayOnVibrator(ContextRef = ContextRef, KindVal = Kind.Confirm)
    }

    fun Reject(ContextRef: Context) {
        if (!IsEnabled) return
        PlayOnVibrator(ContextRef = ContextRef, KindVal = Kind.Reject)
    }

    fun Success(ContextRef: Context) {
        if (!IsEnabled) return
        PlayOnVibrator(ContextRef = ContextRef, KindVal = Kind.Success)
    }

    fun Failure(ContextRef: Context) {
        if (!IsEnabled) return
        PlayOnVibrator(ContextRef = ContextRef, KindVal = Kind.Failure)
    }

    private enum class Kind { Tap, Confirm, Reject, Success, Failure }

    private fun PlayOnVibrator(ContextRef: Context, KindVal: Kind): Boolean {
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return false
        return when (KindVal) {
            Kind.Tap -> PlayStrong(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 25L),
                AmplitudesArray = intArrayOf(0, 190)
            ) || PlayPredefined(
                VibratorRef = VibratorRef,
                EffectId = VibrationEffect.EFFECT_TICK
            ) || PlayComposition(
                VibratorRef = VibratorRef,
                ScaleValues = floatArrayOf(1.0f)
            ) || PlayPattern(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 30L)
            )

            Kind.Confirm -> PlayStrong(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 40L),
                AmplitudesArray = intArrayOf(0, 255)
            ) || PlayPredefined(
                VibratorRef = VibratorRef,
                EffectId = VibrationEffect.EFFECT_CLICK
            ) || PlayComposition(
                VibratorRef = VibratorRef,
                ScaleValues = floatArrayOf(1.0f)
            ) || PlayPattern(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 45L)
            )

            Kind.Reject -> PlayStrong(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 55L, 60L, 55L),
                AmplitudesArray = intArrayOf(0, 255, 0, 255)
            ) || PlayPredefined(
                VibratorRef = VibratorRef,
                EffectId = VibrationEffect.EFFECT_HEAVY_CLICK
            ) || PlayComposition(
                VibratorRef = VibratorRef,
                ScaleValues = floatArrayOf(1.0f, 1.0f),
                GapMs = 90
            ) || PlayPattern(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 60L, 80L, 60L)
            )

            Kind.Success -> PlayStrong(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 35L, 70L, 60L),
                AmplitudesArray = intArrayOf(0, 200, 0, 255)
            ) || PlayPredefined(
                VibratorRef = VibratorRef,
                EffectId = VibrationEffect.EFFECT_DOUBLE_CLICK
            ) || PlayPattern(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 40L, 90L, 50L)
            )

            Kind.Failure -> PlayStrong(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 220L),
                AmplitudesArray = intArrayOf(0, 255)
            ) || PlayPredefined(
                VibratorRef = VibratorRef,
                EffectId = VibrationEffect.EFFECT_HEAVY_CLICK
            ) || PlayPattern(
                VibratorRef = VibratorRef,
                TimingsArray = longArrayOf(0L, 200L)
            )
        }
    }

    private fun PlayOnView(ViewRef: View?, ConstantId: Int): Boolean {
        val TargetView = ViewRef ?: return false
        return try {
            TargetView.performHapticFeedback(
                ConstantId,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Exception) {
            false
        }
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

    private fun PlayStrong(
        VibratorRef: Vibrator,
        TimingsArray: LongArray,
        AmplitudesArray: IntArray
    ): Boolean {
        val HasAmplitude = try {
            VibratorRef.hasAmplitudeControl()
        } catch (_: Exception) {
            false
        }
        if (!HasAmplitude) return false
        val SafeAmplitudes = IntArray(AmplitudesArray.size) { IndexValue ->
            AmplitudesArray[IndexValue].coerceIn(0, 255)
        }
        return try {
            VibratorRef.vibrate(
                VibrationEffect.createWaveform(TimingsArray, SafeAmplitudes, -1)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun PlayPattern(VibratorRef: Vibrator, TimingsArray: LongArray): Boolean {
        return try {
            VibratorRef.vibrate(VibrationEffect.createWaveform(TimingsArray, -1))
            true
        } catch (_: Exception) {
            false
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
