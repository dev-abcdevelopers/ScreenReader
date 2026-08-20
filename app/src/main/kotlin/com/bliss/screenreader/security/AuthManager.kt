@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused",
    "SpellCheckingInspection", "SimplifyBooleanWithConstants"
)

package com.bliss.screenreader.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import com.bliss.screenreader.BuildConfig
import com.bliss.screenreader.service.CaptureSessionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AuthManager {
    private const val PREFS_NAME = "screenreader_auth"
    private const val KEY_LICENSE = "license_blob"
    private const val KEY_LAST_COUNTER = "otp_last_counter"
    private const val KEY_FAIL_COUNT = "otp_fail_count"
    private const val KEY_LOCK_UNTIL = "otp_lock_until"
    private const val KEY_MAX_SEEN_TIME = "clock_max_seen"
    private const val IDLE_LOCK_MS = 5L * 60L * 1000L
    private const val FREE_ATTEMPTS = 5
    private const val CLOCK_SLACK_MS = 24L * 60L * 60L * 1000L

    @Volatile
    private var IdleLockMs = IDLE_LOCK_MS

    @Volatile
    private var UnlockedFlag = false

    @Volatile
    private var BackgroundedAtElapsed = 0L

    @Volatile
    private var CachedLicense: LicenseCodec.ActivationLicense? = null

    sealed class ActivationOutcome {
        data class Activated(val LabelText: String) : ActivationOutcome()
        object WrongDevice : ActivationOutcome()
        object AlreadyExpired : ActivationOutcome()
        object BadSignature : ActivationOutcome()
        object Malformed : ActivationOutcome()
        object NoSigningKey : ActivationOutcome()
    }

    sealed class UnlockOutcome {
        object Unlocked : UnlockOutcome()
        data class WrongCode(val AttemptsLeft: Int) : UnlockOutcome()
        object AlreadyUsed : UnlockOutcome()
        data class LockedOut(val SecondsLeft: Long) : UnlockOutcome()
        object LicenceExpired : UnlockOutcome()
        object ClockRolledBack : UnlockOutcome()
        object NotActivated : UnlockOutcome()
    }

    private fun Prefs(ContextRef: Context) =
        SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)

    fun LicenseOrNull(ContextRef: Context): LicenseCodec.ActivationLicense? {
        CachedLicense?.let { return it }
        val BlobText = Prefs(ContextRef = ContextRef).getString(KEY_LICENSE, null) ?: return null
        val ParsedResult = LicenseCodec.Parse(BlobText = BlobText)

        val LicenseObj = (ParsedResult as? LicenseCodec.ParseResult.Valid)?.LicenseObj ?: return null
        if (!DeviceIdentity.Matches(
                ContextRef = ContextRef,
                CandidateBytes = LicenseObj.DeviceIdBytes
            )
        ) {
            return null
        }
        CachedLicense = LicenseObj
        return LicenseObj
    }

    fun IsActivated(ContextRef: Context): Boolean = LicenseOrNull(ContextRef = ContextRef) != null

    fun Activate(ContextRef: Context, BlobText: String): ActivationOutcome {
        when (val ParsedResult = LicenseCodec.Parse(BlobText = BlobText)) {
            is LicenseCodec.ParseResult.NoSigningKey -> return ActivationOutcome.NoSigningKey
            is LicenseCodec.ParseResult.Malformed -> return ActivationOutcome.Malformed
            is LicenseCodec.ParseResult.BadSignature -> return ActivationOutcome.BadSignature
            is LicenseCodec.ParseResult.Valid -> {
                val LicenseObj = ParsedResult.LicenseObj
                if (!DeviceIdentity.Matches(
                        ContextRef = ContextRef,
                        CandidateBytes = LicenseObj.DeviceIdBytes
                    )
                ) {
                    return ActivationOutcome.WrongDevice
                }
                val NowMillis = System.currentTimeMillis()
                if (LicenseObj.IsExpired(NowMillis = NowMillis)) {
                    return ActivationOutcome.AlreadyExpired
                }

                Prefs(ContextRef = ContextRef).edit {
                    putString(KEY_LICENSE, BlobText.filterNot { it.isWhitespace() })
                    putLong(KEY_LAST_COUNTER, Totp.NO_MATCH)
                    putInt(KEY_FAIL_COUNT, 0)
                    putLong(KEY_LOCK_UNTIL, 0L)

                    putLong(KEY_MAX_SEEN_TIME, NowMillis)
                }
                CachedLicense = LicenseObj
                return ActivationOutcome.Activated(LabelText = LicenseObj.LabelText)
            }
        }
    }

    fun ClearActivation(ContextRef: Context) {
        Prefs(ContextRef = ContextRef).edit {
            remove(KEY_LICENSE)
            remove(KEY_LAST_COUNTER)
            remove(KEY_FAIL_COUNT)
            remove(KEY_LOCK_UNTIL)
            remove(KEY_MAX_SEEN_TIME)
        }
        CachedLicense = null
        UnlockedFlag = false
    }

    fun VerifyCode(ContextRef: Context, CodeText: String): UnlockOutcome {
        val LicenseObj = LicenseOrNull(ContextRef = ContextRef) ?: return UnlockOutcome.NotActivated
        val PrefsObj = Prefs(ContextRef = ContextRef)
        val NowMillis = System.currentTimeMillis()

        val LockUntilMillis = PrefsObj.getLong(KEY_LOCK_UNTIL, 0L)
        if (NowMillis < LockUntilMillis) {
            return UnlockOutcome.LockedOut(SecondsLeft = (LockUntilMillis - NowMillis + 999L) / 1000L)
        }

        val MaxSeenMillis = PrefsObj.getLong(KEY_MAX_SEEN_TIME, 0L)
        if (MaxSeenMillis > 0L && NowMillis < MaxSeenMillis - CLOCK_SLACK_MS) {
            return UnlockOutcome.ClockRolledBack
        }

        if (LicenseObj.IsExpired(NowMillis = NowMillis)) return UnlockOutcome.LicenceExpired

        val LastCounter = PrefsObj.getLong(KEY_LAST_COUNTER, Totp.NO_MATCH)
        val MatchedCounter = Totp.FindMatchingCounter(
            SeedBytes = LicenseObj.SeedBytes,
            CodeText = CodeText.trim(),
            EpochSeconds = NowMillis / 1000L,
            MinCounterExclusive = LastCounter
        )

        if (MatchedCounter == Totp.REPLAYED) {
            return UnlockOutcome.AlreadyUsed
        }

        if (MatchedCounter == Totp.NO_MATCH) {
            val FailCount = PrefsObj.getInt(KEY_FAIL_COUNT, 0) + 1
            val PenaltyMillis = LockoutMillisFor(FailCount = FailCount)
            PrefsObj.edit {
                putInt(KEY_FAIL_COUNT, FailCount)
                putLong(KEY_MAX_SEEN_TIME, maxOf(MaxSeenMillis, NowMillis))
                if (PenaltyMillis > 0L) putLong(KEY_LOCK_UNTIL, NowMillis + PenaltyMillis)
            }
            return if (PenaltyMillis > 0L) {
                UnlockOutcome.LockedOut(SecondsLeft = PenaltyMillis / 1000L)
            } else {
                UnlockOutcome.WrongCode(AttemptsLeft = FREE_ATTEMPTS - FailCount)
            }
        }

        PrefsObj.edit {
            putLong(KEY_LAST_COUNTER, MatchedCounter)
            putInt(KEY_FAIL_COUNT, 0)
            putLong(KEY_LOCK_UNTIL, 0L)
            putLong(KEY_MAX_SEEN_TIME, maxOf(MaxSeenMillis, NowMillis))
        }
        UnlockedFlag = true
        BackgroundedAtElapsed = 0L
        return UnlockOutcome.Unlocked
    }

    private fun LockoutMillisFor(FailCount: Int): Long = when {
        FailCount < FREE_ATTEMPTS -> 0L
        FailCount == FREE_ATTEMPTS -> 30_000L
        FailCount == FREE_ATTEMPTS + 1 -> 120_000L
        FailCount == FREE_ATTEMPTS + 2 -> 600_000L
        else -> 1_800_000L
    }

    fun LockoutSecondsLeft(ContextRef: Context): Long {
        val LockUntilMillis = Prefs(ContextRef = ContextRef).getLong(KEY_LOCK_UNTIL, 0L)
        val RemainingMillis = LockUntilMillis - System.currentTimeMillis()
        return if (RemainingMillis > 0L) (RemainingMillis + 999L) / 1000L else 0L
    }

    val BypassActive: Boolean
        get() = BuildConfig.DEBUG && BuildConfig.BYPASS_AUTH

    fun IsUnlocked(): Boolean {
        if (BypassActive) return true
        if (!UnlockedFlag) return false
        if (BackgroundedAtElapsed == 0L) return true

        if (CaptureSessionState.IsCapturingLive.value == true) {
            BackgroundedAtElapsed = SystemClock.elapsedRealtime()
            return true
        }

        if (SystemClock.elapsedRealtime() - BackgroundedAtElapsed > IdleLockMs) {
            UnlockedFlag = false
            return false
        }
        return true
    }

    fun SetIdleLockMs(ValueMs: Long) {
        IdleLockMs = if (ValueMs > 0L) ValueMs else IDLE_LOCK_MS
    }

    fun NoteForegrounded() {
        if (IsUnlocked()) BackgroundedAtElapsed = 0L
    }

    fun NoteBackgrounded() {
        if (BackgroundedAtElapsed == 0L) BackgroundedAtElapsed = SystemClock.elapsedRealtime()
    }

    fun Lock() {
        UnlockedFlag = false
        BackgroundedAtElapsed = 0L
    }

    fun ExpiryText(ContextRef: Context): String {
        val LicenseObj = LicenseOrNull(ContextRef = ContextRef) ?: return ""
        if (LicenseObj.NeverExpires) return "No expiry"
        val ExpiryMillis = LicenseObj.ExpiryDays.toLong() * 86_400_000L
        val FormatterObj = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        return "Valid till " + FormatterObj.format(Date(ExpiryMillis))
    }

    fun DaysLeft(ContextRef: Context): Int {
        val LicenseObj = LicenseOrNull(ContextRef = ContextRef) ?: return 0
        if (LicenseObj.NeverExpires) return Int.MAX_VALUE
        return LicenseObj.ExpiryDays - LicenseCodec.TodayDays(NowMillis = System.currentTimeMillis())
    }

    fun IsAutomaticTimeOff(ContextRef: Context): Boolean = try {
        Settings.Global.getInt(ContextRef.contentResolver, Settings.Global.AUTO_TIME, 0) == 0
    } catch (_: Exception) {
        false
    }
}
