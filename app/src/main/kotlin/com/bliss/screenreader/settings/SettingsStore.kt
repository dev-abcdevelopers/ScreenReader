@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.bliss.screenreader.security.AuthManager
import com.bliss.screenreader.security.SecurePrefs
import com.bliss.screenreader.utils.HapticFeedback
import com.bliss.screenreader.data.parser.RenewalDateRange

object SettingsStore {
    const val PREFS_NAME = "screenreader_settings"
    private const val KEY_CAPTURE_DEPTH = "capture_depth"
    private const val KEY_PACE = "pace_profile"
    private const val KEY_OFFLINE_WAIT_MS = "offline_wait_ms"
    private const val KEY_RENEWAL_RANGE_DAYS = "renewal_range_days"
    private const val KEY_ERROR_RETRY_LIMIT = "error_retry_limit"
    private const val KEY_ERROR_GIVEUP_LIMIT = "error_giveup_limit"
    private const val KEY_ERROR_SLOW_DOWN = "error_slow_down"
    private const val KEY_CONTACT_OCR = "contact_ocr"
    private const val KEY_PS_MODE = "ps_mode_visible"
    private const val KEY_THEME = "theme_choice"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_SECURE_WINDOW = "secure_window"
    private const val KEY_IDLE_LOCK_MS = "idle_lock_ms"
    private const val KEY_ADVANCED_UNLOCKED = "advanced_unlocked"
    private const val KEY_SESSION_EXPORT = "session_export_visible"
    private const val KEY_RENEWAL_HISTORY = "renewal_history_visible"

    const val DEFAULT_OFFLINE_WAIT_MS = 120_000L
    const val DEFAULT_ERROR_RETRY_LIMIT = 3
    const val DEFAULT_ERROR_GIVEUP_LIMIT = 3
    const val DEFAULT_IDLE_LOCK_MS = 300_000L
    const val ADVANCED_TAP_TARGET = 5

    val OFFLINE_WAIT_CHOICES = listOf(0L, 60_000L, 120_000L, 300_000L, 600_000L)
    val RETRY_CHOICES = listOf(1, 2, 3, 4, 5)
    val GIVEUP_CHOICES = listOf(1, 2, 3, 5, 8)
    val IDLE_LOCK_CHOICES = listOf(60_000L, 300_000L, 900_000L, 3_600_000L)

    enum class CaptureDepth(val StoredName: String) {
        ASK("ask"),
        FAST("fast"),
        FULL("full");

        companion object {
            fun FromName(NameVal: String?): CaptureDepth {
                if (NameVal == null) return ASK
                for (DepthVal in entries) {
                    if (DepthVal.StoredName.equals(NameVal.trim(), ignoreCase = true)) return DepthVal
                }
                return ASK
            }
        }
    }

    enum class ThemeChoice(val StoredName: String, val NightMode: Int) {
        SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

        companion object {
            fun FromName(NameVal: String?): ThemeChoice {
                if (NameVal == null) return SYSTEM
                for (ThemeVal in entries) {
                    if (ThemeVal.StoredName.equals(NameVal.trim(), ignoreCase = true)) return ThemeVal
                }
                return SYSTEM
            }
        }
    }

    private fun Prefs(ContextRef: Context) =
        SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)

    fun DepthOf(ContextRef: Context): CaptureDepth = CaptureDepth.FromName(
        NameVal = Prefs(ContextRef = ContextRef).getString(KEY_CAPTURE_DEPTH, null)
    )

    fun SetDepth(ContextRef: Context, DepthVal: CaptureDepth) {
        Prefs(ContextRef = ContextRef).edit { putString(KEY_CAPTURE_DEPTH, DepthVal.StoredName) }
    }

    fun PaceOf(ContextRef: Context): PaceProfile = PaceProfile.FromName(
        NameVal = Prefs(ContextRef = ContextRef).getString(KEY_PACE, null)
    )

    fun SetPace(ContextRef: Context, ProfileVal: PaceProfile) {
        Prefs(ContextRef = ContextRef).edit { putString(KEY_PACE, ProfileVal.StoredName) }
    }

    fun OfflineWaitMs(ContextRef: Context): Long =
        Prefs(ContextRef = ContextRef).getLong(KEY_OFFLINE_WAIT_MS, DEFAULT_OFFLINE_WAIT_MS)

    fun SetOfflineWaitMs(ContextRef: Context, ValueMs: Long) {
        Prefs(ContextRef = ContextRef).edit { putLong(KEY_OFFLINE_WAIT_MS, ValueMs) }
    }

    fun RenewalRangeDays(ContextRef: Context): Int = Prefs(ContextRef = ContextRef)
        .getInt(KEY_RENEWAL_RANGE_DAYS, RenewalDateRange.DEFAULT_SPAN_DAYS)

    fun SetRenewalRangeDays(ContextRef: Context, ValueVal: Int) {
        Prefs(ContextRef = ContextRef).edit { putInt(KEY_RENEWAL_RANGE_DAYS, ValueVal) }
    }

    fun ErrorRetryLimit(ContextRef: Context): Int =
        Prefs(ContextRef = ContextRef).getInt(KEY_ERROR_RETRY_LIMIT, DEFAULT_ERROR_RETRY_LIMIT)

    fun SetErrorRetryLimit(ContextRef: Context, ValueVal: Int) {
        Prefs(ContextRef = ContextRef).edit { putInt(KEY_ERROR_RETRY_LIMIT, ValueVal) }
    }

    fun ErrorGiveUpLimit(ContextRef: Context): Int =
        Prefs(ContextRef = ContextRef).getInt(KEY_ERROR_GIVEUP_LIMIT, DEFAULT_ERROR_GIVEUP_LIMIT)

    fun SetErrorGiveUpLimit(ContextRef: Context, ValueVal: Int) {
        Prefs(ContextRef = ContextRef).edit { putInt(KEY_ERROR_GIVEUP_LIMIT, ValueVal) }
    }

    fun IsErrorSlowDownOn(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_ERROR_SLOW_DOWN, true)

    fun SetErrorSlowDown(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_ERROR_SLOW_DOWN, EnabledVal) }
    }

    fun IsContactOcrOn(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_CONTACT_OCR, true)

    fun SetContactOcr(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_CONTACT_OCR, EnabledVal) }
    }

    fun IsPsModeVisible(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_PS_MODE, false)

    fun SetPsModeVisible(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_PS_MODE, EnabledVal) }
    }

    fun ThemeOf(ContextRef: Context): ThemeChoice = ThemeChoice.FromName(
        NameVal = Prefs(ContextRef = ContextRef).getString(KEY_THEME, null)
    )

    fun SetTheme(ContextRef: Context, ThemeVal: ThemeChoice) {
        Prefs(ContextRef = ContextRef).edit { putString(KEY_THEME, ThemeVal.StoredName) }
        AppCompatDelegate.setDefaultNightMode(ThemeVal.NightMode)
    }

    fun IsHapticsOn(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_HAPTICS, true)

    fun SetHaptics(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_HAPTICS, EnabledVal) }
        HapticFeedback.SetEnabled(EnabledVal = EnabledVal)
    }

    fun IsSecureWindowOn(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_SECURE_WINDOW, false)

    fun SetSecureWindow(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_SECURE_WINDOW, EnabledVal) }
    }

    fun IdleLockMs(ContextRef: Context): Long =
        Prefs(ContextRef = ContextRef).getLong(KEY_IDLE_LOCK_MS, DEFAULT_IDLE_LOCK_MS)

    fun SetIdleLockMs(ContextRef: Context, ValueMs: Long) {
        Prefs(ContextRef = ContextRef).edit { putLong(KEY_IDLE_LOCK_MS, ValueMs) }
        AuthManager.SetIdleLockMs(ValueMs = ValueMs)
    }

    fun IsAdvancedUnlocked(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_ADVANCED_UNLOCKED, false)

    fun SetAdvancedUnlocked(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_ADVANCED_UNLOCKED, EnabledVal) }
    }

    fun IsSessionExportVisible(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_SESSION_EXPORT, false)

    fun SetSessionExportVisible(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_SESSION_EXPORT, EnabledVal) }
    }

    fun IsRenewalHistoryVisible(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_RENEWAL_HISTORY, false)

    fun SetRenewalHistoryVisible(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_RENEWAL_HISTORY, EnabledVal) }
    }

    fun ApplyGlobals(ContextRef: Context) {
        AppCompatDelegate.setDefaultNightMode(ThemeOf(ContextRef = ContextRef).NightMode)
        HapticFeedback.SetEnabled(EnabledVal = IsHapticsOn(ContextRef = ContextRef))
        AuthManager.SetIdleLockMs(ValueMs = IdleLockMs(ContextRef = ContextRef))
    }
}
