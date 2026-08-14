@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import android.content.Context
import com.bliss.screenreader.BuildConfig
import com.bliss.screenreader.ui.auth.AuthActivity
import com.bliss.screenreader.ui.licence.LicenceActivity

object LicenceGate {

    val IsUrlGate: Boolean
        get() = BuildConfig.URL_LICENCE

    fun EntryActivity(): Class<*> =
        if (IsUrlGate) LicenceActivity::class.java else AuthActivity::class.java

    fun IsGateActivity(CandidateRef: Any): Boolean =
        CandidateRef is LicenceActivity || CandidateRef is AuthActivity

    fun IsUnlocked(ContextRef: Context): Boolean {
        if (AuthManager.BypassActive) return true
        return if (IsUrlGate) {
            BlissLicenceStore.IsUsable(ContextRef = ContextRef)
        } else {
            AuthManager.IsUnlocked()
        }
    }

    fun NoteForegrounded() {
        if (!IsUrlGate) AuthManager.NoteForegrounded()
    }

    fun NoteBackgrounded() {
        if (!IsUrlGate) AuthManager.NoteBackgrounded()
    }
}
