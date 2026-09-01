@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CaptureSession
import com.bliss.screenreader.data.model.ParsedRecord
import com.bliss.screenreader.data.model.PolicyResumeTrack
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.SheetCaptureReviewBinding
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.ui.adapter.ReviewRecordAdapter
import com.bliss.screenreader.ui.raw.RawCaptureActivity
import com.bliss.screenreader.ui.toast.AppToast
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable

class CaptureReviewSheet : BottomSheetDialogFragment() {

    private enum class ReviewFilter { All, Due, Partial }

    private var ViewBindingObj: SheetCaptureReviewBinding? = null
    private val AdapterObj = ReviewRecordAdapter()
    private var ResultListener: ((Int) -> Unit)? = null
    private var AllRecords: List<ParsedRecord> = emptyList()
    private var ActiveFilter = ReviewFilter.All

    fun SetResultListener(ListenerRef: (Int) -> Unit) {
        ResultListener = ListenerRef
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun getTheme(): Int = R.style.Theme_DataReaderApp_BottomSheet_Review

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val DialogRef = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        DialogRef.setCanceledOnTouchOutside(false)
        DialogRef.setOnShowListener {
            val SheetView = DialogRef.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            SheetView.layoutParams = SheetView.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            val BehaviorRef = BottomSheetBehavior.from(SheetView)
            BehaviorRef.isFitToContents = false
            BehaviorRef.skipCollapsed = true
            BehaviorRef.expandedOffset = 0
            BehaviorRef.isDraggable = false
            BehaviorRef.isHideable = false
            BehaviorRef.state = BottomSheetBehavior.STATE_EXPANDED
            PaintSheetBackground(SheetView = SheetView)
            ApplyBarAppearance(DialogRef = DialogRef)
        }
        return DialogRef
    }

    private fun ApplyBarAppearance(DialogRef: BottomSheetDialog) {
        val WindowRef = DialogRef.window ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            WindowRef.isNavigationBarContrastEnforced = false
        }
        val NightMode = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val ControllerRef = WindowCompat.getInsetsController(WindowRef, WindowRef.decorView)
        ControllerRef.isAppearanceLightStatusBars = false
        ControllerRef.isAppearanceLightNavigationBars = !NightMode
    }

    private fun PaintSheetBackground(SheetView: View) {
        val HasRecords = CaptureSessionState.PendingSession?.Records?.isNotEmpty() == true
        val BandColorId = if (HasRecords) {
            R.color.review_hero_start
        } else {
            R.color.update_hero_forced_start
        }
        val BandColor = ContextCompat.getColor(requireContext(), BandColorId)
        SheetView.post {
            val ShapeBackground = SheetView.background as? MaterialShapeDrawable
            if (ShapeBackground != null) {
                ShapeBackground.fillColor = ColorStateList.valueOf(BandColor)
            } else {
                SheetView.setBackgroundColor(BandColor)
            }
        }
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

        ApplySheetInsets(BindingObj = BindingObj)

        AllRecords = SessionObj.Records
        BindingObj.rvReviewRecords.layoutManager = LinearLayoutManager(requireContext())
        BindingObj.rvReviewRecords.adapter = AdapterObj

        BindRecordSummary(BindingObj = BindingObj, SessionObj = SessionObj)
        BindFilterChips(BindingObj = BindingObj)
        ApplyFilter(BindingObj = BindingObj)

        BindingObj.btnReviewRaw.isEnabled = SessionObj.NodeCount > 0
        BindingObj.btnReviewRaw.contentDescription =
            getString(R.string.review_view_raw_format, SessionObj.NodeCount)
        BindingObj.btnReviewRaw.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            startActivity(Intent(requireContext(), RawCaptureActivity::class.java))
        }

        BindingObj.btnReviewDiscard.setOnClickListener { ViewRef ->
            HapticFeedback.Reject(ViewRef = ViewRef)
            if (SessionObj.TargetedPolicyNumbers.isEmpty()) {
                PolicyRepository.ClearPolicyResumeMark(
                    ContextRef = requireContext().applicationContext,
                    SessionId = SessionObj.SessionId,
                    TrackVal = PolicyResumeTrack.OfMode(
                        ModeVal = SessionObj.Mode,
                        CapturePolicyDetails = SessionObj.CapturePolicyDetails
                    )
                )
            }
            CaptureSessionState.ConsumePending()
            ResultListener?.invoke(0)
            dismissAllowingStateLoss()
        }

        BindingObj.btnReviewSave.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            val OutcomeObj = CaptureFlow.CommitPendingSession(
                ContextRef = requireContext().applicationContext,
                SessionObj = SessionObj
            )
            if (OutcomeObj.GapCount > 0) {
                AppToast.Warning(
                    ContextRef = activity,
                    MessageText = getString(R.string.review_gaps_format, OutcomeObj.GapCount)
                )
            }
            CaptureSessionState.ConsumePending()
            ResultListener?.invoke(OutcomeObj.SavedCount)
            dismissAllowingStateLoss()
        }
    }

    private fun ApplySheetInsets(BindingObj: SheetCaptureReviewBinding) {
        val HeroTopPadding = BindingObj.reviewHeroBand.paddingTop
        val ActionBottomPadding = BindingObj.reviewActionBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(BindingObj.root) { RootView, WindowInsetsObj ->
            val BarInsets = WindowInsetsObj.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val SheetView = RootView.parent as? View
            val SheetTop = SheetView?.paddingTop ?: 0
            val SheetBottom = SheetView?.paddingBottom ?: 0
            BindingObj.reviewHeroBand.updatePadding(
                top = HeroTopPadding + (BarInsets.top - SheetTop).coerceAtLeast(0)
            )
            BindingObj.reviewActionBar.updatePadding(
                bottom = ActionBottomPadding + (BarInsets.bottom - SheetBottom).coerceAtLeast(0)
            )
            WindowInsetsObj
        }
    }

    private fun BindFilterChips(BindingObj: SheetCaptureReviewBinding) {
        val DueCount = AllRecords.count { RecordItem -> RecordItem.HasDue }
        val PartialCount = AllRecords.count { RecordItem -> RecordItem.HasWarning }

        BindingObj.chipReviewAll.text = getString(R.string.review_filter_all, AllRecords.size)
        BindingObj.chipReviewDue.text = getString(R.string.review_filter_due, DueCount)
        BindingObj.chipReviewPartial.text =
            getString(R.string.review_filter_partial, PartialCount)

        BindingObj.chipReviewDue.visibility = if (DueCount > 0) View.VISIBLE else View.GONE
        BindingObj.chipReviewPartial.visibility = if (PartialCount > 0) View.VISIBLE else View.GONE

        val HasChoice = DueCount > 0 || PartialCount > 0
        BindingObj.reviewFilterScroll.visibility =
            if (AllRecords.isNotEmpty() && HasChoice) View.VISIBLE else View.GONE

        BindingObj.chipGroupReview.setOnCheckedStateChangeListener { _, CheckedIds ->
            ActiveFilter = when (CheckedIds.firstOrNull()) {
                R.id.chipReviewDue -> ReviewFilter.Due
                R.id.chipReviewPartial -> ReviewFilter.Partial
                else -> ReviewFilter.All
            }
            ApplyFilter(BindingObj = BindingObj)
        }
    }

    private fun ApplyFilter(BindingObj: SheetCaptureReviewBinding) {
        val VisibleRecords = when (ActiveFilter) {
            ReviewFilter.All -> AllRecords
            ReviewFilter.Due -> AllRecords.filter { RecordItem -> RecordItem.HasDue }
            ReviewFilter.Partial -> AllRecords.filter { RecordItem -> RecordItem.HasWarning }
        }
        AdapterObj.UpdateData(NewRecords = VisibleRecords)
        BindingObj.rvReviewRecords.scrollToPosition(0)
        BindingObj.tvReviewFilterEmpty.setText(R.string.review_filter_empty)
        BindingObj.tvReviewFilterEmpty.visibility =
            if (AllRecords.isNotEmpty() && VisibleRecords.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun BindRecordSummary(BindingObj: SheetCaptureReviewBinding, SessionObj: CaptureSession) {
        val RecordCount = SessionObj.Records.size

        if (RecordCount == 0) {
            BindingObj.tvReviewTitle.setText(R.string.review_title_empty)
            BindingObj.ivReviewArt.setImageResource(R.drawable.art_capture_empty)
            BindingObj.reviewHeroBand.setBackgroundResource(R.drawable.bg_review_hero_empty)
            BindingObj.tvReviewSubtitle.text =
                getString(R.string.review_subtitle_empty, SessionObj.NodeCount)
            BindingObj.btnReviewSave.visibility = View.GONE
            ShowDiscardAsClose(BindingObj = BindingObj)
            return
        }

        BindingObj.tvReviewTitle.setText(R.string.review_title)
        BindingObj.ivReviewArt.setImageResource(R.drawable.art_capture_complete)
        BindingObj.reviewHeroBand.setBackgroundResource(R.drawable.bg_review_hero)
        BindingObj.tvReviewSubtitle.text = getString(
            R.string.review_subtitle_stats_format,
            SessionObj.Mode.DescribeCount(CountVal = RecordCount),
            SessionObj.NodeCount,
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
    }

    private fun ShowDiscardAsClose(BindingObj: SheetCaptureReviewBinding) {
        val DensityVal = resources.displayMetrics.density
        BindingObj.btnReviewDiscard.setText(R.string.review_close)
        BindingObj.btnReviewDiscard.contentDescription = null
        BindingObj.btnReviewDiscard.iconPadding = (8 * DensityVal).toInt()
        BindingObj.btnReviewDiscard.setPadding(
            (16 * DensityVal).toInt(), 0, (16 * DensityVal).toInt(), 0
        )
        BindingObj.btnReviewDiscard.layoutParams =
            (BindingObj.btnReviewDiscard.layoutParams as LinearLayout.LayoutParams).apply {
                width = 0
                weight = 1f
            }
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

            CaptureMode.RENEWAL_DUE -> PolicyRepository.GetRenewalDuePolicies(
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

        fun NewInstance(): CaptureReviewSheet = CaptureReviewSheet()
    }
}
