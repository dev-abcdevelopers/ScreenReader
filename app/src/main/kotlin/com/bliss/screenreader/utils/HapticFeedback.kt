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
    fun Tap(ViewRef: View?) {
        ViewRef?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun Confirm(ViewRef: View?) {
        val ConstantVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        ViewRef?.performHapticFeedback(ConstantVal)
    }

    fun Reject(ViewRef: View?) {
        val ConstantVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        ViewRef?.performHapticFeedback(ConstantVal)
    }


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

    @RequiresApi(Build.VERSION_CODES.Q)
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
