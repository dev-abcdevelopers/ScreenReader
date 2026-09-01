@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.PolicyResumeTarget
import com.bliss.screenreader.data.model.PolicyResumeTrack
import com.bliss.screenreader.data.model.CaptureSession
import com.bliss.screenreader.data.parser.CaptureParsers
import com.bliss.screenreader.data.parser.RecordMerge
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.SheetCustomerResumeBinding
import com.bliss.screenreader.service.CaptureDiagnostics
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.ui.toast.AppToast
import com.bliss.screenreader.utils.AppLauncherUtils
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetDialog

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

        SheetBinding.tvCustomerResumeBody.text = ActivityRef.getString(
            R.string.customer_resume_body,
            FilledCount,
            PolicyList.size,
            OutstandingCount
        )

        SheetBinding.tvCustomerResumeDesc.text = buildString {
            if (VisitedNames.isNotEmpty()) {
                append(
                    ActivityRef.getString(
                        R.string.customer_resume_continue_desc,
                        VisitedNames.size
                    )
                )
            } else {
                append(
                    ActivityRef.getString(
                        R.string.customer_resume_continue_desc_filled,
                        OutstandingCount
                    )
                )
            }
            if (ResumePage > 0 && CustomerMark != null) {
                append(". ")
                append(
                    ActivityRef.getString(
                        R.string.customer_resume_page,
                        ResumePage,
                        CustomerMark.TotalPages
                    )
                )
            } else if (SkipAheadPage > 0) {
                append(". ")
                append(ActivityRef.getString(R.string.customer_resume_page_blocked))
            }
        }

        SheetBinding.tvCustomerRestartDesc.text = ActivityRef.getString(
            R.string.customer_resume_restart_desc,
            PolicyList.size
        )

        SheetBinding.rowCustomerResume.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            SheetDialog.dismiss()
            LaunchCustomerCapture(
                ActivityRef = ActivityRef,
                SessionIdVal = SessionIdVal,
                ResumeFromPage = ResumePage
            )
        }

        if (SkipAheadPage > 0) {
            SheetBinding.tvCustomerSkipAheadTitle.text = ActivityRef.getString(
                R.string.customer_resume_skip_ahead,
                SkipAheadPage
            )
            SheetBinding.rowCustomerSkipAhead.visibility = View.VISIBLE
            SheetBinding.rowCustomerSkipAhead.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SheetDialog.dismiss()
                LaunchCustomerCapture(
                    ActivityRef = ActivityRef,
                    SessionIdVal = SessionIdVal,
                    ResumeFromPage = SkipAheadPage
                )
            }
        }

        SheetBinding.rowCustomerRestart.setOnClickListener { ViewRef ->
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
        ResumeFromPage: Int = 0,
        TargetPolicyNumbers: List<String> = emptyList(),
        TargetNameHints: Map<String, String> = emptyMap(),
        TargetCustomerNames: List<String> = emptyList(),
        ChainCustomerName: String = ""
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
            ResumeFromPageVal = ResumeFromPage,
            TargetPolicyNumbersVal = TargetPolicyNumbers,
            TargetNameHintsVal = TargetNameHints,
            TargetCustomerNamesVal = TargetCustomerNames,
            ChainCustomerNameVal = ChainCustomerName
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

    data class CommitOutcome(val SavedCount: Int, val GapCount: Int)

    fun CommitPendingSession(ContextRef: Context, SessionObj: CaptureSession): CommitOutcome {
        val AppContext = ContextRef.applicationContext
        val SavedCount = try {
            CaptureParsers.Commit(
                ContextRef = AppContext,
                SessionId = SessionObj.SessionId,
                ModeVal = SessionObj.Mode,
                Nodes = SessionObj.RawNodes,
                PolicyRecords = SessionObj.PolicyRecords,
                FupRecords = SessionObj.FupRecords,
                RenewalDueRecords = SessionObj.RenewalDueRecords.orEmpty(),
                CapturePolicyDetails = SessionObj.CapturePolicyDetails,
                GapRecords = SessionObj.GapRecords
            )
        } catch (ExceptionObj: Exception) {
            CaptureDiagnostics.LogForSession(
                ContextObj = AppContext,
                SessionId = SessionObj.SessionId,
                EventName = "SESSION_COMMIT_FAILED",
                MessageText = "session=${SessionObj.SessionId} mode=${SessionObj.Mode.name} " +
                        "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
            0
        }

        val CommitBreakdown = CaptureParsers.LastCommitResult
        CaptureDiagnostics.LogForSession(
            ContextObj = AppContext,
            SessionId = SessionObj.SessionId,
            EventName = "SESSION_COMMIT",
            MessageText = "session=${SessionObj.SessionId} mode=${SessionObj.Mode.name} " +
                    "saved=$SavedCount added=${CommitBreakdown.AddedCount} " +
                    "updated=${CommitBreakdown.UpdatedCount} nodes=${SessionObj.NodeCount}"
        )

        if (SessionObj.GapRecords.isNotEmpty()) {
            val GapNumberText = SessionObj.GapRecords.joinToString(",") { GapItem ->
                GapItem.PolicyNumber
            }
            CaptureDiagnostics.LogForSession(
                ContextObj = AppContext,
                SessionId = SessionObj.SessionId,
                EventName = "SESSION_GAPS",
                MessageText = "session=${SessionObj.SessionId} " +
                        "gaps=${SessionObj.GapRecords.size} policies=$GapNumberText"
            )
        }

        return CommitOutcome(
            SavedCount = SavedCount,
            GapCount = SessionObj.GapRecords.size
        )
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

    fun ShowMessage(
        ActivityRef: AppCompatActivity,
        MessageVal: String,
        KindVal: AppToast.Kind = AppToast.Kind.Info
    ) {
        AppToast.Show(
            ContextRef = ActivityRef,
            MessageText = MessageVal,
            KindVal = KindVal,
            LongDuration = true
        )
    }

}
