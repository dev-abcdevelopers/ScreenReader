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
        PerformViewFeedback(
            ViewRef = ViewRef,
            FeedbackConstant = HapticFeedbackConstants.CLOCK_TICK,
            FallbackEffectId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.EFFECT_TICK
            } else {
                null
            },
            FallbackDurationMs = 12L
        )
    }

    fun Confirm(ViewRef: View?) {
        val ConstantVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        PerformViewFeedback(
            ViewRef = ViewRef,
            FeedbackConstant = ConstantVal,
            FallbackEffectId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.EFFECT_CLICK
            } else {
                null
            },
            FallbackDurationMs = 30L
        )
    }

    fun Reject(ViewRef: View?) {
        val ConstantVal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        PerformViewFeedback(
            ViewRef = ViewRef,
            FeedbackConstant = ConstantVal,
            FallbackEffectId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.EFFECT_HEAVY_CLICK
            } else {
                null
            },
            FallbackDurationMs = 60L
        )
    }

    private fun PerformViewFeedback(
        ViewRef: View?,
        FeedbackConstant: Int,
        FallbackEffectId: Int?,
        FallbackDurationMs: Long
    ) {
        ViewRef ?: return
        val WasPerformed = try {
            ViewRef.performHapticFeedback(
                FeedbackConstant,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: Exception) {
            false
        }
        if (WasPerformed) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && FallbackEffectId != null) {
            PlayPredefined(
                ContextRef = ViewRef.context,
                EffectId = FallbackEffectId,
                FallbackDurationMs = FallbackDurationMs
            )
        } else {
            PlayOneShot(ContextRef = ViewRef.context, DurationMs = FallbackDurationMs)
        }
    }


    fun Success(ContextRef: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PlayPredefined(
                ContextRef = ContextRef,
                EffectId = VibrationEffect.EFFECT_DOUBLE_CLICK,
                FallbackDurationMs = 40L
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
                EffectId = VibrationEffect.EFFECT_HEAVY_CLICK,
                FallbackDurationMs = 160L
            )
        } else {
            PlayPattern(
                ContextRef = ContextRef,
                TimingsArray = longArrayOf(0L, 160L)
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun PlayPredefined(
        ContextRef: Context,
        EffectId: Int,
        FallbackDurationMs: Long
    ) {
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return
        try {
            VibratorRef.vibrate(VibrationEffect.createPredefined(EffectId))
        } catch (_: Exception) {
            // Some devices expose a vibrator but reject optional predefined effects.
            PlayOneShot(ContextRef = ContextRef, DurationMs = FallbackDurationMs)
        }
    }

    private fun PlayOneShot(ContextRef: Context, DurationMs: Long) {
        val VibratorRef = ResolveVibrator(ContextRef = ContextRef) ?: return
        try {
            VibratorRef.vibrate(
                VibrationEffect.createOneShot(
                    DurationMs,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } catch (_: Exception) {
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
