@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureSession
import com.bliss.screenreader.data.parser.CaptureParsers
import com.bliss.screenreader.databinding.SheetCaptureReviewBinding
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.ui.adapter.ReviewRecordAdapter
import com.bliss.screenreader.ui.raw.RawCaptureActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Shown after every capture. This is the only place a capture reaches storage,
 * so a parse that came back wrong costs a tap rather than the whole session.
 * The raw nodes stay reachable from here for the same reason.
 */
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
        BindingObj.btnReviewRaw.setOnClickListener {
            startActivity(Intent(requireContext(), RawCaptureActivity::class.java))
        }

        BindingObj.btnReviewDiscard.setOnClickListener {
            CaptureSessionState.ConsumePending()
            ResultListener?.invoke(0)
            dismissAllowingStateLoss()
        }

        BindingObj.btnReviewSave.setOnClickListener {
            val SavedCount = try {
                CaptureParsers.Commit(
                    ContextRef = requireContext().applicationContext,
                    ModeVal = SessionObj.Mode,
                    Nodes = SessionObj.RawNodes
                )
            } catch (_: Exception) {
                0
            }
            CaptureSessionState.ConsumePending()
            ResultListener?.invoke(SavedCount)
            dismissAllowingStateLoss()
        }
    }

    /**
     * Long captures would otherwise push the action buttons off the bottom of
     * the sheet, so past four rows the list scrolls instead of growing.
     */
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
        BindingObj.btnReviewSave.text = getString(
            R.string.review_save_format,
            SessionObj.Mode.DescribeCount(CountVal = RecordCount)
        )
        BindingObj.btnReviewDiscard.setText(R.string.review_discard)
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
