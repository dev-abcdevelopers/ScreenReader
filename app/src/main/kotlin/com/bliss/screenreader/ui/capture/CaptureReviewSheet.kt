@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CaptureSession
import com.bliss.screenreader.data.model.PolicyResumeTrack
import com.bliss.screenreader.data.parser.CaptureParsers
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.SheetCaptureReviewBinding
import com.bliss.screenreader.service.CaptureDiagnostics
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.ui.adapter.ReviewRecordAdapter
import com.bliss.screenreader.ui.raw.RawCaptureActivity
import com.bliss.screenreader.ui.toast.AppToast
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CaptureReviewSheet : BottomSheetDialogFragment() {

    private var ViewBindingObj: SheetCaptureReviewBinding? = null
    private val AdapterObj = ReviewRecordAdapter()
    private var ResultListener: ((Int) -> Unit)? = null

    fun SetResultListener(ListenerRef: (Int) -> Unit) {
        ResultListener = ListenerRef
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val BindingObj = SheetCaptureReviewBinding.inflate(inflater, container, false)
        ViewBindingObj = BindingObj
        return BindingObj.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val BindingObj = ViewBindingObj ?: return
        val SessionObj = CaptureSessionState.PendingSession

        if (SessionObj == null) {
            dismissAllowingStateLoss()
            return
        }

        BindingObj.rvReviewRecords.layoutManager = LinearLayoutManager(requireContext())
        BindingObj.rvReviewRecords.adapter = AdapterObj
        AdapterObj.UpdateData(NewRecords = SessionObj.Records)
        CapListHeight(BindingObj = BindingObj, RecordCount = SessionObj.Records.size)

        BindRecordSummary(BindingObj = BindingObj, SessionObj = SessionObj)

        BindingObj.btnReviewRaw.text = getString(R.string.review_view_raw_format, SessionObj.NodeCount)
        BindingObj.btnReviewRaw.isEnabled = SessionObj.NodeCount > 0
        BindingObj.btnReviewRaw.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            startActivity(Intent(requireContext(), RawCaptureActivity::class.java))
        }

        BindingObj.btnReviewDiscard.setOnClickListener { ViewRef ->
            HapticFeedback.Reject(ViewRef = ViewRef)
            PolicyRepository.ClearPolicyResumeMark(
                ContextRef = requireContext().applicationContext,
                SessionId = SessionObj.SessionId,
                TrackVal = PolicyResumeTrack.OfMode(
                    ModeVal = SessionObj.Mode,
                    CapturePolicyDetails = SessionObj.CapturePolicyDetails
                )
            )
            CaptureSessionState.ConsumePending()
            ResultListener?.invoke(0)
            dismissAllowingStateLoss()
        }

        BindingObj.btnReviewSave.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            val AppContext = requireContext().applicationContext
            val SavedCount = try {
                CaptureParsers.Commit(
                    ContextRef = AppContext,
                    SessionId = SessionObj.SessionId,
                    ModeVal = SessionObj.Mode,
                    Nodes = SessionObj.RawNodes,
                    PolicyRecords = SessionObj.PolicyRecords,
                    FupRecords = SessionObj.FupRecords,
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
                AppToast.Warning(
                    ContextRef = activity,
                    MessageText = getString(
                        R.string.review_gaps_format, SessionObj.GapRecords.size
                    )
                )
            }
            CaptureSessionState.ConsumePending()
            ResultListener?.invoke(SavedCount)
            dismissAllowingStateLoss()
        }
    }

    private fun CapListHeight(BindingObj: SheetCaptureReviewBinding, RecordCount: Int) {
        if (RecordCount <= MAX_VISIBLE_ROWS) return
        val DensityVal = resources.displayMetrics.density
        BindingObj.rvReviewRecords.layoutParams = BindingObj.rvReviewRecords.layoutParams.apply {
            height = (ROW_HEIGHT_DP * MAX_VISIBLE_ROWS * DensityVal).toInt()
        }
    }

    private fun BindRecordSummary(BindingObj: SheetCaptureReviewBinding, SessionObj: CaptureSession) {
        val RecordCount = SessionObj.Records.size

        if (RecordCount == 0) {
            BindingObj.tvReviewTitle.setText(R.string.review_title_empty)
            BindingObj.ivReviewIcon.setImageResource(R.drawable.ic_alert)
            BindingObj.ivReviewIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_amber_text)
            )
            BindingObj.tvReviewSubtitle.text =
                getString(R.string.review_subtitle_empty, SessionObj.NodeCount)
            BindingObj.btnReviewSave.visibility = View.GONE
            BindingObj.btnReviewDiscard.setText(R.string.review_close)
            return
        }

        BindingObj.tvReviewTitle.setText(R.string.review_title)
        BindingObj.tvReviewSubtitle.text = getString(
            R.string.review_subtitle_format,
            SessionObj.Mode.DescribeCount(CountVal = RecordCount),
            SessionObj.DurationLabel
        )
        BindingObj.btnReviewSave.visibility = View.VISIBLE

        val ExistingCount = ExistingRecordCount(SessionObj = SessionObj)
        BindingObj.btnReviewSave.text = if (ExistingCount > 0) {
            val NewCount = (RecordCount - ExistingCount).coerceAtLeast(0)
            getString(R.string.review_save_resume_format, NewCount, RecordCount - NewCount)
        } else {
            getString(
                R.string.review_save_format,
                SessionObj.Mode.DescribeCount(CountVal = RecordCount)
            )
        }
        BindingObj.btnReviewDiscard.setText(R.string.review_discard)
    }

    private fun ExistingRecordCount(SessionObj: CaptureSession): Int {
        val ContextRef = requireContext().applicationContext
        return when (SessionObj.Mode) {
            CaptureMode.CUSTOMER,
            CaptureMode.POLICY -> PolicyRepository.GetCustomerPolicies(
                ContextRef = ContextRef,
                SessionId = SessionObj.SessionId
            ).size

            CaptureMode.FUP -> PolicyRepository.GetFupPolicies(
                ContextRef = ContextRef,
                SessionId = SessionObj.SessionId
            ).size

            CaptureMode.PS -> PolicyRepository.GetPsPolicies(
                ContextRef = ContextRef,
                SessionId = SessionObj.SessionId
            ).size
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ViewBindingObj = null
    }

    companion object {
        const val TAG = "CaptureReviewSheet"

        private const val MAX_VISIBLE_ROWS = 4
        private const val ROW_HEIGHT_DP = 78

        fun NewInstance(): CaptureReviewSheet = CaptureReviewSheet()
    }
}
