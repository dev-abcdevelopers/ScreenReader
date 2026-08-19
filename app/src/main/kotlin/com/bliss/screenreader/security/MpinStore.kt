@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.security

import android.content.Context
import androidx.core.content.edit

object MpinStore {

    const val MPIN_LENGTH = 4

    private const val PREFS_NAME = "screenreader_mpin"
    private const val KEY_MPIN = "lic_mpin"
    private const val KEY_AUTO_ENTER = "lic_mpin_auto_enter"

    private fun Prefs(ContextRef: Context) =
        SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)

    fun IsWellFormed(CodeText: String): Boolean =
        CodeText.length == MPIN_LENGTH && CodeText.all { CharValue -> CharValue.isDigit() }

    fun MpinOrNull(ContextRef: Context): String? {
        val StoredText = Prefs(ContextRef = ContextRef)
            .getString(KEY_MPIN, null)
            ?.trim()
            .orEmpty()
        if (!IsWellFormed(CodeText = StoredText)) return null
        return StoredText
    }

    fun HasMpin(ContextRef: Context): Boolean = MpinOrNull(ContextRef = ContextRef) != null

    fun IsAutoEnterOn(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_AUTO_ENTER, false)

    fun IsAutoEnterReady(ContextRef: Context): Boolean =
        IsAutoEnterOn(ContextRef = ContextRef) && HasMpin(ContextRef = ContextRef)

    fun Save(ContextRef: Context, CodeText: String, AutoEnterVal: Boolean): Boolean {
        val TrimmedText = CodeText.trim()
        if (!IsWellFormed(CodeText = TrimmedText)) return false
        Prefs(ContextRef = ContextRef).edit {
            putString(KEY_MPIN, TrimmedText)
            putBoolean(KEY_AUTO_ENTER, AutoEnterVal)
        }
        return true
    }

    fun SetAutoEnter(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_AUTO_ENTER, EnabledVal) }
    }

    fun Clear(ContextRef: Context) {
        Prefs(ContextRef = ContextRef).edit {
            remove(KEY_MPIN)
            putBoolean(KEY_AUTO_ENTER, false)
        }
    }
}
