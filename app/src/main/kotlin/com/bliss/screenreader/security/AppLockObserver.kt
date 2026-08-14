@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.security

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager

class AppLockObserver : Application.ActivityLifecycleCallbacks {
    private var StartedCount = 0

    private val SecureWindow = false

    override fun onActivityCreated(ActivityRef: Activity, SavedState: Bundle?) {
        if (SecureWindow && !LicenceGate.IsGateActivity(CandidateRef = ActivityRef)) {
            ActivityRef.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    override fun onActivityStarted(ActivityRef: Activity) {
        StartedCount++

        LicenceGate.NoteForegrounded()

        if (LicenceGate.IsGateActivity(CandidateRef = ActivityRef)) return
        if (LicenceGate.IsUnlocked(ContextRef = ActivityRef)) return

        ActivityRef.startActivity(
            Intent(ActivityRef, LicenceGate.EntryActivity()).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        ActivityRef.finish()
    }

    override fun onActivityStopped(ActivityRef: Activity) {
        StartedCount--
        if (StartedCount <= 0) {
            StartedCount = 0
            LicenceGate.NoteBackgrounded()
        }
    }

    override fun onActivityResumed(ActivityRef: Activity) = Unit
    override fun onActivityPaused(ActivityRef: Activity) = Unit
    override fun onActivitySaveInstanceState(ActivityRef: Activity, OutState: Bundle) = Unit
    override fun onActivityDestroyed(ActivityRef: Activity) = Unit
}
