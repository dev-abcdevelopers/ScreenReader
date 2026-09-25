@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.util.LruCache
import androidx.core.content.edit

class SecurePrefs private constructor(
    private val DelegateRef: SharedPreferences
) : SharedPreferences {
    private val CacheLock = Any()
    private var CacheVersion = 0L
    private val PlainCache = object : LruCache<String, String>(PLAIN_CACHE_CHARS) {
        override fun sizeOf(KeyText: String, ValueText: String): Int =
            KeyText.length + ValueText.length
    }

    override fun getString(KeyText: String?, DefaultText: String?): String? {
        if (KeyText == null) return DelegateRef.getString(null, DefaultText)
        val VersionAtRead = synchronized(CacheLock) {
            PlainCache.get(KeyText)?.let { return it }
            CacheVersion
        }
        val StoredText = DelegateRef.getString(KeyText, null) ?: return DefaultText
        if (!KeyVault.IsEncrypted(StoredText = StoredText)) return StoredText
        val PlainText = KeyVault.Decrypt(StoredText = StoredText) ?: return DefaultText
        synchronized(CacheLock) {
            if (CacheVersion == VersionAtRead) PlainCache.put(KeyText, PlainText)
        }
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

    override fun contains(KeyText: String?): Boolean = DelegateRef.contains(KeyText)

    override fun getAll(): MutableMap<String, *> {
        val ResultMap = HashMap<String, Any?>()
        for ((KeyText, ValueRef) in DelegateRef.all) {
            ResultMap[KeyText] = if (ValueRef is String && KeyVault.IsEncrypted(StoredText = ValueRef)) {
                KeyVault.Decrypt(StoredText = ValueRef)
            } else {
                ValueRef
            }
        }
        return ResultMap
    }

    override fun edit(): SharedPreferences.Editor = SecureEditor(
        DelegateEditor = DelegateRef.edit(),
        OnCommitted = { PendingMap, Cleared -> ApplyToCache(PendingMap = PendingMap, Cleared = Cleared) }
    )

    override fun registerOnSharedPreferenceChangeListener(
        ListenerRef: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = DelegateRef.registerOnSharedPreferenceChangeListener(ListenerRef)

    override fun unregisterOnSharedPreferenceChangeListener(
        ListenerRef: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = DelegateRef.unregisterOnSharedPreferenceChangeListener(ListenerRef)

    private class SecureEditor(
        private val DelegateEditor: SharedPreferences.Editor,
        private val OnCommitted: (Map<String, String?>, Boolean) -> Unit
    ) : SharedPreferences.Editor {
        private val PendingMap = LinkedHashMap<String, String?>()
        private var Cleared = false

        override fun putString(KeyText: String?, ValueText: String?): SharedPreferences.Editor {
            if (ValueText == null) {
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
            if (KeyText != null) PendingMap[KeyText] = null
        }

        override fun clear() = apply {
            DelegateEditor.clear()
            Cleared = true
        }

        override fun commit(): Boolean {
            val Result = DelegateEditor.commit()
            OnCommitted(PendingMap.toMap(), Cleared)
            return Result
        }

        override fun apply() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                DelegateEditor.apply()
            } else {
                DelegateEditor.commit()
            }
            OnCommitted(PendingMap.toMap(), Cleared)
        }

        private fun SafeEncrypt(PlainText: String): String = try {
            KeyVault.Encrypt(PlainText = PlainText)
        } catch (_: KeyVault.VaultUnavailable) {
            PlainText
        }
    }

    companion object {
        private const val MIGRATION_FLAG = "secure_prefs_migrated_v1"
        private const val PLAIN_CACHE_CHARS = 8 * 1024 * 1024

        private val InstanceCache = HashMap<String, SecurePrefs>()

        fun Of(ContextRef: Context, PrefsName: String): SecurePrefs {
            synchronized(InstanceCache) {
                InstanceCache[PrefsName]?.let { return it }
                val DelegateRef = ContextRef.applicationContext
                    .getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                val InstanceRef = SecurePrefs(DelegateRef = DelegateRef)
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
                DelegateRef.edit {
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
