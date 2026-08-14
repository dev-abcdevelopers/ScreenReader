@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import android.content.Context
import androidx.core.content.edit

object BlissLicenceStore {

    private const val PREFS_NAME = "bliss_licence"
    private const val KEY_LAST_OK = "last_ok_at"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_MAX_SEEN = "max_seen_at"

    const val VALID_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
    const val GRACE_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
    private const val CLOCK_SLACK_MS = 24L * 60L * 60L * 1000L

    enum class CacheState { None, Fresh, InGrace, Expired, RolledBack }

    @Volatile
    private var Loaded = false

    @Volatile
    private var CachedLastOk = 0L

    @Volatile
    private var CachedMaxSeen = 0L

    @Volatile
    private var CachedId = ""

    private fun Prefs(ContextRef: Context) =
        SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)

    private fun EnsureLoaded(ContextRef: Context) {
        if (Loaded) return
        synchronized(this) {
            if (Loaded) return
            val PrefsObj = Prefs(ContextRef = ContextRef)
            CachedLastOk = PrefsObj.getLong(KEY_LAST_OK, 0L)
            CachedMaxSeen = PrefsObj.getLong(KEY_MAX_SEEN, 0L)
            CachedId = PrefsObj.getString(KEY_DEVICE_ID, null).orEmpty()
            Loaded = true
        }
    }

    fun Evaluate(
        LastOkAt: Long,
        MaxSeenAt: Long,
        NowMillis: Long,
        StoredId: String,
        CurrentId: String
    ): CacheState {
        if (LastOkAt <= 0L) return CacheState.None
        if (StoredId.isEmpty() || StoredId != CurrentId) return CacheState.None
        if (NowMillis < LastOkAt) return CacheState.RolledBack
        if (MaxSeenAt > 0L && NowMillis < MaxSeenAt - CLOCK_SLACK_MS) return CacheState.RolledBack

        val AgeMillis = NowMillis - LastOkAt
        return when {
            AgeMillis < VALID_WINDOW_MS -> CacheState.Fresh
            AgeMillis < VALID_WINDOW_MS + GRACE_WINDOW_MS -> CacheState.InGrace
            else -> CacheState.Expired
        }
    }

    fun GraceDaysLeft(LastOkAt: Long, NowMillis: Long): Int {
        val DeadlineMillis = LastOkAt + VALID_WINDOW_MS + GRACE_WINDOW_MS
        val RemainingMillis = DeadlineMillis - NowMillis
        if (RemainingMillis <= 0L) return 0
        return ((RemainingMillis + 86_399_999L) / 86_400_000L).toInt()
    }

    fun DaysSinceLastCheck(LastOkAt: Long, NowMillis: Long): Int {
        if (LastOkAt <= 0L || NowMillis < LastOkAt) return 0
        return ((NowMillis - LastOkAt) / 86_400_000L).toInt()
    }

    fun StateOf(ContextRef: Context): CacheState {
        EnsureLoaded(ContextRef = ContextRef)
        return Evaluate(
            LastOkAt = CachedLastOk,
            MaxSeenAt = CachedMaxSeen,
            NowMillis = System.currentTimeMillis(),
            StoredId = CachedId,
            CurrentId = DeviceIdentity.RegistrationId(ContextRef = ContextRef)
        )
    }

    fun LastOkAt(ContextRef: Context): Long {
        EnsureLoaded(ContextRef = ContextRef)
        return CachedLastOk
    }

    fun IsFresh(ContextRef: Context): Boolean = StateOf(ContextRef = ContextRef) == CacheState.Fresh

    fun IsUsable(ContextRef: Context): Boolean = when (StateOf(ContextRef = ContextRef)) {
        CacheState.Fresh, CacheState.InGrace -> true
        else -> false
    }

    fun RecordSuccess(ContextRef: Context) {
        EnsureLoaded(ContextRef = ContextRef)
        val NowMillis = System.currentTimeMillis()
        val DeviceIdText = DeviceIdentity.RegistrationId(ContextRef = ContextRef)
        val MaxSeenMillis = maxOf(CachedMaxSeen, NowMillis)
        Prefs(ContextRef = ContextRef).edit {
            putLong(KEY_LAST_OK, NowMillis)
            putString(KEY_DEVICE_ID, DeviceIdText)
            putLong(KEY_MAX_SEEN, MaxSeenMillis)
        }
        CachedLastOk = NowMillis
        CachedId = DeviceIdText
        CachedMaxSeen = MaxSeenMillis
    }

    fun NoteClockSeen(ContextRef: Context) {
        EnsureLoaded(ContextRef = ContextRef)
        val NowMillis = System.currentTimeMillis()
        if (NowMillis > CachedMaxSeen) {
            Prefs(ContextRef = ContextRef).edit { putLong(KEY_MAX_SEEN, NowMillis) }
            CachedMaxSeen = NowMillis
        }
    }

    fun Clear(ContextRef: Context) {
        EnsureLoaded(ContextRef = ContextRef)
        Prefs(ContextRef = ContextRef).edit {
            remove(KEY_LAST_OK)
            remove(KEY_DEVICE_ID)
        }
        CachedLastOk = 0L
        CachedId = ""
    }
}
