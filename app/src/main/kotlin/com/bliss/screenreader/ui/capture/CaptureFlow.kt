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

object CaptureFlow {

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

    fun ShowPendingReview(
        ActivityRef: AppCompatActivity,
        ModeVal: CaptureMode,
        OnResult: (Int) -> Unit
    ) {
        val SessionObj = CaptureSessionState.PendingSession ?: return
        if (SessionObj.Mode != ModeVal) return
        if (ActivityRef.supportFragmentManager.isStateSaved) return

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
