@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.PolicyResumeTarget
import com.bliss.screenreader.data.model.PolicyResumeTrack
import com.bliss.screenreader.data.parser.RecordMerge
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.SheetCustomerResumeBinding
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.utils.AppLauncherUtils
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar

object CaptureFlow {

    fun StartCustomerCapture(ActivityRef: AppCompatActivity, SessionIdVal: String) {
        val VisitedNames = PolicyRepository.GetVisitedCustomers(
            ContextRef = ActivityRef,
            SessionId = SessionIdVal
        )
        val PolicyList = PolicyRepository.GetCustomerPolicies(
            ContextRef = ActivityRef,
            SessionId = SessionIdVal
        )
        val FilledCount = PolicyList.count { PolicyItem ->
            RecordMerge.HasPersonalDetails(PolicyItem = PolicyItem)
        }
        val OutstandingCount = PolicyList.size - FilledCount
        val CustomerMark = PolicyRepository.GetPolicyResumeMark(
            ContextRef = ActivityRef,
            SessionId = SessionIdVal,
            TrackVal = PolicyResumeTrack.CUSTOMER
        )
        val ResumePage = PolicyResumeTarget.ResolveForTrack(
            TrackVal = PolicyResumeTrack.CUSTOMER,
            FastMark = null,
            FullMark = null,
            CustomerMark = CustomerMark,
            StoredRecordCount = PolicyList.size
        )

        val SkipAheadPage = PolicyResumeTarget.CustomerSkipAheadPage(
            MarkObj = CustomerMark,
            StoredRecordCount = PolicyList.size
        )

        if (VisitedNames.isEmpty() && FilledCount == 0) {
            LaunchCustomerCapture(ActivityRef = ActivityRef, SessionIdVal = SessionIdVal)
            return
        }

        val SheetBinding = SheetCustomerResumeBinding.inflate(ActivityRef.layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)

        val BodyText = buildString {
            append(
                ActivityRef.getString(
                    R.string.customer_resume_body,
                    FilledCount,
                    PolicyList.size,
                    OutstandingCount
                )
            )
            if (VisitedNames.isNotEmpty()) {
                append(" ")
                append(
                    ActivityRef.getString(
                        R.string.customer_resume_visited,
                        VisitedNames.size
                    )
                )
            }
            if (ResumePage > 0 && CustomerMark != null) {
                append(" ")
                append(
                    ActivityRef.getString(
                        R.string.customer_resume_page,
                        ResumePage,
                        CustomerMark.TotalPages
                    )
                )
            } else if (SkipAheadPage > 0) {
                append(" ")
                append(ActivityRef.getString(R.string.customer_resume_page_blocked))
            }
        }
        SheetBinding.tvCustomerResumeBody.text = BodyText
        SheetBinding.btnCustomerResume.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            SheetDialog.dismiss()
            LaunchCustomerCapture(
                ActivityRef = ActivityRef,
                SessionIdVal = SessionIdVal,
                ResumeFromPage = ResumePage
            )
        }
        if (SkipAheadPage > 0) {
            SheetBinding.btnCustomerSkipAhead.text = ActivityRef.getString(
                R.string.customer_resume_skip_ahead,
                SkipAheadPage
            )
            SheetBinding.btnCustomerSkipAhead.visibility = View.VISIBLE
            SheetBinding.btnCustomerSkipAhead.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SheetDialog.dismiss()
                LaunchCustomerCapture(
                    ActivityRef = ActivityRef,
                    SessionIdVal = SessionIdVal,
                    ResumeFromPage = SkipAheadPage
                )
            }
        }
        SheetBinding.btnCustomerRestart.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
            PolicyRepository.ClearVisitedCustomers(
                ContextRef = ActivityRef,
                SessionId = SessionIdVal
            )
            LaunchCustomerCapture(
                ActivityRef = ActivityRef,
                SessionIdVal = SessionIdVal,
                RevisitFilled = true
            )
        }
        SheetBinding.btnCustomerResumeCancel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }

    private fun LaunchCustomerCapture(
        ActivityRef: AppCompatActivity,
        SessionIdVal: String,
        RevisitFilled: Boolean = false,
        ResumeFromPage: Int = 0
    ) {
        Start(
            ActivityRef = ActivityRef,
            ModeVal = CaptureMode.CUSTOMER,
            LaunchTarget = true,
            ResumeSessionId = SessionIdVal,
            RevisitFilled = RevisitFilled,
            ResumeFromPage = ResumeFromPage
        )
    }

    fun Start(
        ActivityRef: AppCompatActivity,
        ModeVal: CaptureMode,
        LaunchTarget: Boolean = false,
        CapturePolicyDetails: Boolean = false,
        OriginOverride: String = "",
        ResumeSessionId: String = "",
        RevisitFilled: Boolean = false,
        ResumeFromPage: Int = 0
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
            val RequiredMode = if (ModeVal == CaptureMode.CUSTOMER) {
                CaptureMode.POLICY
            } else {
                ModeVal
            }
            if (SessionRef == null || SessionRef.Mode != RequiredMode) {
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
            ResumeSessionIdVal = ResumeSessionId,
            RevisitFilledVal = RevisitFilled,
            ResumeFromPageVal = ResumeFromPage
        )

        if (LaunchTarget) {
            val TargetPackage = if (ModeVal == CaptureMode.PS) {
                AppLauncherUtils.ResolveAgentPackage(ContextRef = ActivityRef)
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
