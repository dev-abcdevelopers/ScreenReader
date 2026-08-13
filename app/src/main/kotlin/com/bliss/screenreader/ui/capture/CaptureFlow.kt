@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import androidx.appcompat.app.AppCompatActivity
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.utils.AppLauncherUtils
import com.google.android.material.snackbar.Snackbar

/**
 * Shared entry and exit points for the three capture screens, so each activity
 * only has to say which [CaptureMode] it wants.
 */
object CaptureFlow {

    /**
     * Starts a capture and hands control to the bubble. Returns false when a
     * prerequisite is missing, having already told the user which one.
     */
    fun Start(
        ActivityRef: AppCompatActivity,
        ModeVal: CaptureMode,
        LaunchTarget: Boolean = false,
        CapturePolicyDetails: Boolean = false,
        OriginOverride: String = "",
        ResumeSessionId: String = ""
    ): Boolean {
        val PendingSession = CaptureSessionState.PendingSession
        if (PendingSession != null) {
            ShowMessage(
                ActivityRef = ActivityRef,
                MessageVal = ActivityRef.getString(R.string.capture_review_pending)
            )
            return false
        }

        val ServiceInstance = ScreenReaderService.Instance
        if (ServiceInstance == null) {
            ShowMessage(ActivityRef = ActivityRef, MessageVal = ActivityRef.getString(R.string.capture_service_disabled))
            return false
        }

        // Merging a renewal capture into a policy session, or vice versa, would
        // write records the reader for that mode cannot parse.
        if (ResumeSessionId.isNotBlank()) {
            val SessionRef = PolicyRepository.GetSessionReference(
                ContextRef = ActivityRef,
                SessionId = ResumeSessionId
            )
            if (SessionRef == null || SessionRef.Mode != ModeVal) {
                ShowMessage(
                    ActivityRef = ActivityRef,
                    MessageVal = ActivityRef.getString(R.string.capture_resume_mismatch)
                )
                return false
            }
        }

        // A screen that finishes itself on start must nominate somewhere else to
        // come back to, or the service reopens a dead activity.
        ServiceInstance.StartCaptureSession(
            ModeVal = ModeVal,
            CapturePolicyDetailsVal = CapturePolicyDetails,
            OriginActivityVal = OriginOverride.ifEmpty { ActivityRef.javaClass.name },
            ResumeSessionIdVal = ResumeSessionId
        )

        if (LaunchTarget) {
            val TargetPackage = if (ModeVal == CaptureMode.PS) {
                AppLauncherUtils.PS_AGENT_APP_PACKAGE
            } else {
                AppLauncherUtils.LIC_SUPER_APP_PACKAGE
            }
            val LaunchSucceeded = AppLauncherUtils.LaunchTargetApp(
                ContextRef = ActivityRef,
                PackageNameVal = TargetPackage,
                FreshStartVal = true
            )
            if (!LaunchSucceeded) {
                // StartCaptureSession runs first so accessibility events cannot
                // be missed while Android switches applications. Roll it back
                // when the requested target is unavailable.
                ServiceInstance.DiscardCaptureSession()
                return false
            }
        }
        return true
    }

    fun Finish(ActivityRef: AppCompatActivity): Boolean {
        val ServiceInstance = ScreenReaderService.Instance
        if (ServiceInstance == null || !ScreenReaderService.IsCapturing) {
            ShowMessage(ActivityRef = ActivityRef, MessageVal = ActivityRef.getString(R.string.capture_no_session))
            return false
        }
        ServiceInstance.FinishCaptureSession()
        return true
    }

    /**
     * Call from onResume. Presents the review sheet if a finished capture for
     * this mode is waiting, and reports back how many records were saved.
     */
    fun ShowPendingReview(
        ActivityRef: AppCompatActivity,
        ModeVal: CaptureMode,
        OnResult: (Int) -> Unit
    ) {
        val SessionObj = CaptureSessionState.PendingSession ?: return
        if (SessionObj.Mode != ModeVal) return
        if (ActivityRef.supportFragmentManager.isStateSaved) return

        // A sheet can survive a rotation while its callback does not, so an
        // existing instance is re-attached rather than skipped.
        val ExistingSheet = ActivityRef.supportFragmentManager
            .findFragmentByTag(CaptureReviewSheet.TAG) as? CaptureReviewSheet
        if (ExistingSheet != null) {
            ExistingSheet.SetResultListener { SavedCount -> OnResult(SavedCount) }
            return
        }

        val SheetObj = CaptureReviewSheet.NewInstance()
        SheetObj.SetResultListener { SavedCount -> OnResult(SavedCount) }
        SheetObj.show(ActivityRef.supportFragmentManager, CaptureReviewSheet.TAG)
    }

    fun ShowMessage(ActivityRef: AppCompatActivity, MessageVal: String) {
        Snackbar.make(
            ActivityRef.findViewById(android.R.id.content),
            MessageVal,
            Snackbar.LENGTH_LONG
        ).show()
    }

}
