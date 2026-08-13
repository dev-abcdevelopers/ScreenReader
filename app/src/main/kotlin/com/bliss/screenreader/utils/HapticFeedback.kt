@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptics for actions worth feeling.
 *
 * Two families, because the app acts in two places:
 *
 * - [Tap] / [Confirm] / [Reject] run through the view, so Android's own
 *   "touch feedback" setting is respected and nothing fires for a user who
 *   turned it off.
 * - [Success] / [Failure] run through the vibrator, because they report the
 *   end of an automated capture. By then the phone is showing the target app
 *   and the agent is not looking at this one, so the signal has to be
 *   noticeable rather than polite.
 *
 * Nothing here is load-bearing: every call degrades to silence if the device
 * has no vibrator or the API is unavailable.
 */
object HapticFeedback {

    /** A light tick for a reversible state change, such as revealing a row. */
    fun Tap(ViewRef: View?) {
        ViewRef?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** A committed positive action: capture started, records saved, resumed. */
    fun Confirm(ViewRef: View?) {
        val ConstantVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        ViewRef?.performHapticFeedback(ConstantVal)
    }

    /** A destructive or discarding action: session deleted, capture thrown away. */
    fun Reject(ViewRef: View?) {
        val ConstantVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        ViewRef?.performHapticFeedback(ConstantVal)
    }

    /** Automation finished on its own. Two pulses: "it worked, come back". */
    fun Success(ContextRef: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PlayPredefined(
                ContextRef = ContextRef,
                EffectId = VibrationEffect.EFFECT_DOUBLE_CLICK
            )
        } else {
            PlayPattern(
                ContextRef = ContextRef,
                TimingsArray = longArrayOf(0L, 40L, 90L, 40L)
            )
        }
    }

    /** Automation stopped or paused itself. One longer, heavier pulse. */
    fun Failure(ContextRef: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PlayPredefined(
                ContextRef = ContextRef,
                EffectId = VibrationEffect.EFFECT_HEAVY_CLICK
            )
        } else {
            PlayPattern(
                ContextRef = ContextRef,
                TimingsArray = longArrayOf(0L, 160L)
            )
        }
    }

    private fun PlayPredefined(ContextRef: Context, EffectId: Int) {
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return
        try {
            VibratorRef.vibrate(VibrationEffect.createPredefined(EffectId))
        } catch (_: Exception) {
            // A device can advertise a vibrator and still reject an effect.
        }
    }

    private fun PlayPattern(ContextRef: Context, TimingsArray: LongArray) {
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return
        try {
            VibratorRef.vibrate(
                VibrationEffect.createWaveform(TimingsArray, -1)
            )
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
