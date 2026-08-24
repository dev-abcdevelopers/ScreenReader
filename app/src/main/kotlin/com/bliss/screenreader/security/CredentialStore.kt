@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.security

import android.content.Context
import androidx.core.content.edit

object CredentialStore {
    const val MPIN_LENGTH = 4
    const val PASSWORD_MIN_LENGTH = 8
    const val PASSWORD_MAX_LENGTH = 64
    enum class Method { MPIN, PASSWORD }
    private const val PREFS_NAME = "screenreader_mpin"
    private const val KEY_MPIN = "lic_mpin"
    private const val KEY_PASSWORD = "lic_password"
    private const val KEY_METHOD = "lic_method"
    private const val KEY_AUTO_ENTER = "lic_mpin_auto_enter"
    private const val KEY_REJECTED_AT = "lic_mpin_rejected_at"
    private const val KEY_REJECTED_METHOD = "lic_rejected_method"
    private const val KEY_MPIN_SAVED_AT = "lic_mpin_saved_at"
    private const val KEY_PASSWORD_SAVED_AT = "lic_password_saved_at"
    private const val METHOD_MPIN = "mpin"
    private const val METHOD_PASSWORD = "password"

    private fun Prefs(ContextRef: Context) =
        SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)

    fun IsMpinWellFormed(CodeText: String): Boolean =
        CodeText.length == MPIN_LENGTH && CodeText.all { CharValue -> CharValue.isDigit() }

    fun IsPasswordWellFormed(PasswordText: String): Boolean =
        PasswordText.length in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH &&
                PasswordText.any { CharValue -> CharValue.isUpperCase() } &&
                PasswordText.any { CharValue -> CharValue.isLowerCase() } &&
                PasswordText.any { CharValue -> CharValue.isDigit() } &&
                PasswordText.any { CharValue ->
                    !CharValue.isLetterOrDigit() && !CharValue.isWhitespace()
                }

    fun MpinOrNull(ContextRef: Context): String? {
        val StoredText = Prefs(ContextRef = ContextRef).getString(KEY_MPIN, null)?.trim().orEmpty()
        if (!IsMpinWellFormed(CodeText = StoredText)) return null
        return StoredText
    }

    fun PasswordOrNull(ContextRef: Context): String? {
        val StoredText = Prefs(ContextRef = ContextRef).getString(KEY_PASSWORD, null).orEmpty()
        if (!IsPasswordWellFormed(PasswordText = StoredText)) return null
        return StoredText
    }

    fun SecretFor(ContextRef: Context, MethodVal: Method): String? = when (MethodVal) {
        Method.MPIN -> MpinOrNull(ContextRef = ContextRef)
        Method.PASSWORD -> PasswordOrNull(ContextRef = ContextRef)
    }

    fun HasSecretFor(ContextRef: Context, MethodVal: Method): Boolean =
        SecretFor(ContextRef = ContextRef, MethodVal = MethodVal) != null

    fun MethodOf(ContextRef: Context): Method {
        val StoredText = Prefs(ContextRef = ContextRef).getString(KEY_METHOD, null).orEmpty()
        return if (StoredText == METHOD_PASSWORD) Method.PASSWORD else Method.MPIN
    }

    fun SetMethod(ContextRef: Context, MethodVal: Method) {
        Prefs(ContextRef = ContextRef).edit {
            putString(KEY_METHOD, NameOf(MethodVal = MethodVal))
        }
    }

    fun IsAutoEnterOn(ContextRef: Context): Boolean =
        Prefs(ContextRef = ContextRef).getBoolean(KEY_AUTO_ENTER, false)

    fun SetAutoEnter(ContextRef: Context, EnabledVal: Boolean) {
        Prefs(ContextRef = ContextRef).edit { putBoolean(KEY_AUTO_ENTER, EnabledVal) }
    }

    fun IsAutoEnterReady(ContextRef: Context): Boolean =
        IsAutoEnterOn(ContextRef = ContextRef) &&
                HasSecretFor(ContextRef = ContextRef, MethodVal = MethodOf(ContextRef = ContextRef))

    fun SaveMpin(ContextRef: Context, CodeText: String): Boolean {
        val TrimmedText = CodeText.trim()
        if (!IsMpinWellFormed(CodeText = TrimmedText)) return false
        Prefs(ContextRef = ContextRef).edit {
            putString(KEY_MPIN, TrimmedText)
            putLong(KEY_MPIN_SAVED_AT, System.currentTimeMillis())
        }
        ClearRejection(ContextRef = ContextRef, MethodVal = Method.MPIN)
        return true
    }

    fun SavePassword(ContextRef: Context, PasswordText: String): Boolean {
        if (!IsPasswordWellFormed(PasswordText = PasswordText)) return false
        Prefs(ContextRef = ContextRef).edit {
            putString(KEY_PASSWORD, PasswordText)
            putLong(KEY_PASSWORD_SAVED_AT, System.currentTimeMillis())
        }
        ClearRejection(ContextRef = ContextRef, MethodVal = Method.PASSWORD)
        return true
    }

    fun ClearMpin(ContextRef: Context) {
        Prefs(ContextRef = ContextRef).edit {
            remove(KEY_MPIN)
            remove(KEY_MPIN_SAVED_AT)
        }
        ClearRejection(ContextRef = ContextRef, MethodVal = Method.MPIN)
        TurnOffIfNothingToUse(ContextRef = ContextRef)
    }

    fun ClearPassword(ContextRef: Context) {
        Prefs(ContextRef = ContextRef).edit {
            remove(KEY_PASSWORD)
            remove(KEY_PASSWORD_SAVED_AT)
        }
        ClearRejection(ContextRef = ContextRef, MethodVal = Method.PASSWORD)
        TurnOffIfNothingToUse(ContextRef = ContextRef)
    }

    fun Clear(ContextRef: Context) {
        Prefs(ContextRef = ContextRef).edit {
            remove(KEY_MPIN)
            remove(KEY_PASSWORD)
            remove(KEY_MPIN_SAVED_AT)
            remove(KEY_PASSWORD_SAVED_AT)
            remove(KEY_REJECTED_AT)
            remove(KEY_REJECTED_METHOD)
            putBoolean(KEY_AUTO_ENTER, false)
        }
    }

    fun MarkRejected(ContextRef: Context, MethodVal: Method) {
        Prefs(ContextRef = ContextRef).edit {
            putLong(KEY_REJECTED_AT, System.currentTimeMillis())
            putString(KEY_REJECTED_METHOD, NameOf(MethodVal = MethodVal))
            putBoolean(KEY_AUTO_ENTER, false)
        }
    }

    fun SavedAt(ContextRef: Context, MethodVal: Method): Long {
        if (!HasSecretFor(ContextRef = ContextRef, MethodVal = MethodVal)) return 0L
        val KeyName = if (MethodVal == Method.MPIN) KEY_MPIN_SAVED_AT else KEY_PASSWORD_SAVED_AT
        return Prefs(ContextRef = ContextRef).getLong(KeyName, 0L)
    }

    fun RejectedAt(ContextRef: Context): Long =
        Prefs(ContextRef = ContextRef).getLong(KEY_REJECTED_AT, 0L)

    fun RejectedMethod(ContextRef: Context): Method? {
        if (RejectedAt(ContextRef = ContextRef) <= 0L) return null
        val StoredText = Prefs(ContextRef = ContextRef).getString(KEY_REJECTED_METHOD, null).orEmpty()
        return if (StoredText == METHOD_PASSWORD) Method.PASSWORD else Method.MPIN
    }

    fun WasRejected(ContextRef: Context, MethodVal: Method): Boolean =
        RejectedMethod(ContextRef = ContextRef) == MethodVal

    private fun ClearRejection(ContextRef: Context, MethodVal: Method) {
        if (RejectedMethod(ContextRef = ContextRef) != MethodVal) return
        Prefs(ContextRef = ContextRef).edit {
            remove(KEY_REJECTED_AT)
            remove(KEY_REJECTED_METHOD)
        }
    }

    private fun TurnOffIfNothingToUse(ContextRef: Context) {
        val MethodVal = MethodOf(ContextRef = ContextRef)
        if (HasSecretFor(ContextRef = ContextRef, MethodVal = MethodVal)) return
        SetAutoEnter(ContextRef = ContextRef, EnabledVal = false)
    }

    private fun NameOf(MethodVal: Method): String = when (MethodVal) {
        Method.MPIN -> METHOD_MPIN
        Method.PASSWORD -> METHOD_PASSWORD
    }
}
