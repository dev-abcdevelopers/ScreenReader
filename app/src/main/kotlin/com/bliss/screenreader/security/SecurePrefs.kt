@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import androidx.core.content.edit
import java.io.File

class SecurePrefs private constructor(
    private val DelegateRef: SharedPreferences,
    private val BlobStore: BlobFileStore?,
    private val IsBlobKey: (String) -> Boolean
) : SharedPreferences {
    private val CacheLock = Any()
    private var CacheVersion = 0L
    private val PlainCache = object : LruCache<String, String>(PLAIN_CACHE_CHARS) {
        override fun sizeOf(KeyText: String, ValueText: String): Int =
            KeyText.length + ValueText.length
    }

    private fun IsRouted(KeyText: String?): Boolean =
        BlobStore != null && KeyText != null && IsBlobKey(KeyText)

    private fun ReadStored(KeyText: String): String? {
        val StoreRef = BlobStore
        if (StoreRef == null || !IsBlobKey(KeyText)) return DelegateRef.getString(KeyText, null)
        synchronized(StoreRef.LockFor(KeyText = KeyText)) {
            if (StoreRef.Exists(KeyText = KeyText)) {
                StoreRef.Read(KeyText = KeyText)?.let { return it }
            }
            return DelegateRef.getString(KeyText, null)
        }
    }

    override fun getString(KeyText: String?, DefaultText: String?): String? {
        if (KeyText == null) return DelegateRef.getString(null, DefaultText)
        val VersionAtRead = synchronized(CacheLock) {
            PlainCache.get(KeyText)?.let { return it }
            CacheVersion
        }
        val StartedAt = SystemClock.elapsedRealtime()
        val StoredText = ReadStored(KeyText = KeyText) ?: return DefaultText
        if (!KeyVault.IsEncrypted(StoredText = StoredText)) return StoredText
        val ReadDoneAt = SystemClock.elapsedRealtime()
        val PlainText = KeyVault.Decrypt(StoredText = StoredText) ?: return DefaultText
        synchronized(CacheLock) {
            if (CacheVersion == VersionAtRead) PlainCache.put(KeyText, PlainText)
        }
        ReportIfSlow(
            OpName = "read",
            KeyText = KeyText,
            CharCount = StoredText.length,
            CryptoMs = SystemClock.elapsedRealtime() - ReadDoneAt,
            IoMs = ReadDoneAt - StartedAt
        )
        return PlainText
    }

    private fun ApplyToCache(PendingMap: Map<String, String?>, Cleared: Boolean) {
        synchronized(CacheLock) {
            CacheVersion++
            if (Cleared) PlainCache.evictAll()
            for ((KeyText, ValueText) in PendingMap) {
                if (ValueText == null) PlainCache.remove(KeyText) else PlainCache.put(KeyText, ValueText)
            }
        }
    }

    private fun ApplyBlobWrites(
        BlobPending: Map<String, String?>,
        Cleared: Boolean,
        DelegateEditor: SharedPreferences.Editor
    ) {
        val StoreRef = BlobStore ?: return
        if (Cleared) StoreRef.DeleteAll()
        for ((KeyText, StoredText) in BlobPending) {
            synchronized(StoreRef.LockFor(KeyText = KeyText)) {
                if (StoredText == null) {
                    StoreRef.Delete(KeyText = KeyText)
                } else if (!StoreRef.Write(KeyText = KeyText, StoredText = StoredText)) {
                    StoreRef.Delete(KeyText = KeyText)
                    DelegateEditor.putString(KeyText, StoredText)
                }
            }
        }
    }

    override fun getStringSet(KeyText: String?, DefaultSet: MutableSet<String>?): MutableSet<String>? {
        val StoredSet = DelegateRef.getStringSet(KeyText, null) ?: return DefaultSet
        return StoredSet.mapNotNull { EntryText ->
            if (KeyVault.IsEncrypted(StoredText = EntryText)) {
                KeyVault.Decrypt(StoredText = EntryText)
            } else {
                EntryText
            }
        }.toMutableSet()
    }

    override fun getInt(KeyText: String?, DefaultValue: Int): Int =
        DelegateRef.getInt(KeyText, DefaultValue)

    override fun getLong(KeyText: String?, DefaultValue: Long): Long =
        DelegateRef.getLong(KeyText, DefaultValue)

    override fun getFloat(KeyText: String?, DefaultValue: Float): Float =
        DelegateRef.getFloat(KeyText, DefaultValue)

    override fun getBoolean(KeyText: String?, DefaultValue: Boolean): Boolean =
        DelegateRef.getBoolean(KeyText, DefaultValue)

    override fun contains(KeyText: String?): Boolean {
        if (IsRouted(KeyText = KeyText) && KeyText != null && BlobStore?.Exists(KeyText = KeyText) == true) {
            return true
        }
        return DelegateRef.contains(KeyText)
    }

    override fun getAll(): MutableMap<String, *> {
        val ResultMap = HashMap<String, Any?>()
        for ((KeyText, ValueRef) in DelegateRef.all) {
            ResultMap[KeyText] = if (ValueRef is String && KeyVault.IsEncrypted(StoredText = ValueRef)) {
                KeyVault.Decrypt(StoredText = ValueRef)
            } else {
                ValueRef
            }
        }
        BlobStore?.Keys()?.forEach { KeyText ->
            if (IsBlobKey(KeyText)) ResultMap[KeyText] = getString(KeyText, null)
        }
        return ResultMap
    }

    override fun edit(): SharedPreferences.Editor = SecureEditor(
        DelegateEditor = DelegateRef.edit(),
        IsRouted = { KeyText -> IsRouted(KeyText = KeyText) },
        OnBeforeCommit = { BlobPending, Cleared, EditorRef ->
            ApplyBlobWrites(BlobPending = BlobPending, Cleared = Cleared, DelegateEditor = EditorRef)
        },
        OnCommitted = { PendingMap, Cleared -> ApplyToCache(PendingMap = PendingMap, Cleared = Cleared) },
        OnTimed = { KeyList, CharCount, CryptoMs, IoMs ->
            ReportIfSlow(
                OpName = "write",
                KeyText = KeyList.joinToString(separator = "+"),
                CharCount = CharCount,
                CryptoMs = CryptoMs,
                IoMs = IoMs
            )
        }
    )

    private fun ReportIfSlow(
        OpName: String,
        KeyText: String,
        CharCount: Int,
        CryptoMs: Long,
        IoMs: Long
    ) {
        val TotalMs = CryptoMs + IoMs
        if (TotalMs < SLOW_MAIN_MS) return
        if (Looper.myLooper() != Looper.getMainLooper()) return
        val ListenerRef = SlowIoListener ?: return
        val ThrottleKey = "$OpName:$KeyText"
        val NowMs = SystemClock.elapsedRealtime()
        synchronized(LastReported) {
            val LastAt = LastReported[ThrottleKey]
            if (LastAt != null && NowMs - LastAt < SLOW_THROTTLE_MS) return
            LastReported[ThrottleKey] = NowMs
        }
        val MessageText = "op=$OpName key=${KeyText.take(MAX_KEY_REPORT)} chars=$CharCount " +
                "totalMs=$TotalMs cryptoMs=$CryptoMs ioMs=$IoMs thread=main"
        runCatching { ListenerRef(MessageText) }
    }

    override fun registerOnSharedPreferenceChangeListener(
        ListenerRef: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = DelegateRef.registerOnSharedPreferenceChangeListener(ListenerRef)

    override fun unregisterOnSharedPreferenceChangeListener(
        ListenerRef: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = DelegateRef.unregisterOnSharedPreferenceChangeListener(ListenerRef)

    fun MigrateBlobs(): Int {
        val StoreRef = BlobStore ?: return 0
        val CandidateKeys = DelegateRef.all.keys.filter { KeyText -> IsBlobKey(KeyText) }
        if (CandidateKeys.isEmpty()) return 0
        val MovedKeys = ArrayList<String>()
        for (KeyText in CandidateKeys) {
            val IsMoved = synchronized(StoreRef.LockFor(KeyText = KeyText)) {
                val StoredText = runCatching { DelegateRef.getString(KeyText, null) }.getOrNull()
                when {
                    StoredText == null -> false
                    StoreRef.Exists(KeyText = KeyText) && StoreRef.Read(KeyText = KeyText) != null -> true
                    else -> StoreRef.WriteVerified(KeyText = KeyText, StoredText = StoredText)
                }
            }
            if (IsMoved) MovedKeys.add(KeyText)
        }
        if (MovedKeys.isEmpty()) return 0
        val EditorRef = DelegateRef.edit()
        for (KeyText in MovedKeys) {
            synchronized(StoreRef.LockFor(KeyText = KeyText)) {
                if (StoreRef.Exists(KeyText = KeyText)) EditorRef.remove(KeyText)
            }
        }
        EditorRef.commit()
        return MovedKeys.size
    }

    private class SecureEditor(
        private val DelegateEditor: SharedPreferences.Editor,
        private val IsRouted: (String?) -> Boolean,
        private val OnBeforeCommit: (Map<String, String?>, Boolean, SharedPreferences.Editor) -> Unit,
        private val OnCommitted: (Map<String, String?>, Boolean) -> Unit,
        private val OnTimed: (List<String>, Int, Long, Long) -> Unit
    ) : SharedPreferences.Editor {
        private val PendingMap = LinkedHashMap<String, String?>()
        private val BlobPending = LinkedHashMap<String, String?>()
        private var Cleared = false
        private var EncryptMs = 0L
        private var EncryptedChars = 0

        override fun putString(KeyText: String?, ValueText: String?): SharedPreferences.Editor {
            if (KeyText != null && IsRouted(KeyText)) {
                DelegateEditor.remove(KeyText)
                BlobPending[KeyText] = ValueText?.let { SafeEncrypt(PlainText = it) }
            } else if (ValueText == null) {
                DelegateEditor.remove(KeyText)
            } else {
                DelegateEditor.putString(KeyText, SafeEncrypt(PlainText = ValueText))
            }
            if (KeyText != null) PendingMap[KeyText] = ValueText
            return this
        }

        override fun putStringSet(
            KeyText: String?,
            ValueSet: MutableSet<String>?
        ): SharedPreferences.Editor {
            if (ValueSet == null) {
                DelegateEditor.remove(KeyText)
            } else {
                DelegateEditor.putStringSet(
                    KeyText,
                    ValueSet.map { SafeEncrypt(PlainText = it) }.toMutableSet()
                )
            }
            if (KeyText != null) PendingMap[KeyText] = null
            return this
        }

        override fun putInt(KeyText: String?, ValueVal: Int) = apply { DelegateEditor.putInt(KeyText, ValueVal) }
        override fun putLong(KeyText: String?, ValueVal: Long) = apply { DelegateEditor.putLong(KeyText, ValueVal) }
        override fun putFloat(KeyText: String?, ValueVal: Float) = apply { DelegateEditor.putFloat(KeyText, ValueVal) }
        override fun putBoolean(KeyText: String?, ValueVal: Boolean) = apply { DelegateEditor.putBoolean(KeyText, ValueVal) }
        override fun remove(KeyText: String?) = apply {
            DelegateEditor.remove(KeyText)
            if (KeyText != null) {
                PendingMap[KeyText] = null
                if (IsRouted(KeyText)) BlobPending[KeyText] = null
            }
        }

        override fun clear() = apply {
            DelegateEditor.clear()
            Cleared = true
        }

        override fun commit(): Boolean {
            val StartedAt = SystemClock.elapsedRealtime()
            OnBeforeCommit(BlobPending.toMap(), Cleared, DelegateEditor)
            val Result = DelegateEditor.commit()
            OnCommitted(PendingMap.toMap(), Cleared)
            ReportTiming(IoMs = SystemClock.elapsedRealtime() - StartedAt)
            return Result
        }

        override fun apply() {
            val StartedAt = SystemClock.elapsedRealtime()
            OnBeforeCommit(BlobPending.toMap(), Cleared, DelegateEditor)
            if (Looper.myLooper() == Looper.getMainLooper()) {
                DelegateEditor.apply()
            } else {
                DelegateEditor.commit()
            }
            OnCommitted(PendingMap.toMap(), Cleared)
            ReportTiming(IoMs = SystemClock.elapsedRealtime() - StartedAt)
        }

        private fun ReportTiming(IoMs: Long) {
            if (PendingMap.isEmpty()) return
            OnTimed(PendingMap.keys.toList(), EncryptedChars, EncryptMs, IoMs)
        }

        private fun SafeEncrypt(PlainText: String): String {
            val StartedAt = SystemClock.elapsedRealtime()
            val StoredText = try {
                KeyVault.Encrypt(PlainText = PlainText)
            } catch (_: KeyVault.VaultUnavailable) {
                PlainText
            }
            EncryptMs += SystemClock.elapsedRealtime() - StartedAt
            EncryptedChars += PlainText.length
            return StoredText
        }
    }

    companion object {
        private const val MIGRATION_FLAG = "secure_prefs_migrated_v1"
        private const val PLAIN_CACHE_CHARS = 8 * 1024 * 1024
        private const val SLOW_MAIN_MS = 50L
        private const val SLOW_THROTTLE_MS = 5_000L
        private const val MAX_KEY_REPORT = 160

        private val LastReported = HashMap<String, Long>()

        @Volatile
        var SlowIoListener: ((String) -> Unit)? = null

        private val InstanceCache = HashMap<String, SecurePrefs>()

        fun Of(ContextRef: Context, PrefsName: String): SecurePrefs {
            synchronized(InstanceCache) {
                InstanceCache[PrefsName]?.let { return it }
                val AppContext = ContextRef.applicationContext
                val DelegateRef = AppContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                val UsesBlobs = PrefsName == BlobRouting.DATA_PREFS_NAME
                val InstanceRef = SecurePrefs(
                    DelegateRef = DelegateRef,
                    BlobStore = if (UsesBlobs) {
                        BlobFileStore(DirRef = File(AppContext.filesDir, BlobRouting.DATA_BLOB_DIR))
                    } else {
                        null
                    },
                    IsBlobKey = if (UsesBlobs) BlobRouting::IsBlobKey else { _ -> false }
                )
                InstanceCache[PrefsName] = InstanceRef
                return InstanceRef
            }
        }

        fun MigrateExisting(ContextRef: Context, PrefsName: String) {
            val DelegateRef = ContextRef.applicationContext
                .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            if (DelegateRef.getBoolean(MIGRATION_FLAG, false)) return

            val PlaintextEntries = DelegateRef.all.filter { (KeyText, ValueRef) ->
                KeyText != MIGRATION_FLAG &&
                    ValueRef is String &&
                    !KeyVault.IsEncrypted(StoredText = ValueRef)
            }

            try {
                DelegateRef.edit(commit = true) {
                    for ((KeyText, ValueRef) in PlaintextEntries) {
                        putString(KeyText, KeyVault.Encrypt(PlainText = ValueRef as String))
                    }
                    putBoolean(MIGRATION_FLAG, true)
                }
            } catch (_: KeyVault.VaultUnavailable) {
            }
        }
    }
}
