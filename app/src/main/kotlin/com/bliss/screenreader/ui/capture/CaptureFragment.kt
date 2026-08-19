@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CaptureSession
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.FragmentCaptureBinding
import com.bliss.screenreader.databinding.ItemSessionPickBinding
import com.bliss.screenreader.databinding.PartialModeRowBinding
import com.bliss.screenreader.databinding.PartialPreflightRowBinding
import com.bliss.screenreader.databinding.SheetPolicyCaptureModeBinding
import com.bliss.screenreader.databinding.SheetSessionPickerBinding
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.service.CustomerSheetOcr
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.security.MpinStore
import com.bliss.screenreader.ui.mpin.MpinActivity
import com.bliss.screenreader.utils.AppLauncherUtils
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureFragment : Fragment() {

    private var ViewBindingObj: FragmentCaptureBinding? = null
    private var SelectedMode: CaptureMode = CaptureMode.POLICY

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val BindingObj = FragmentCaptureBinding.inflate(inflater, container, false)
        ViewBindingObj = BindingObj
        return BindingObj.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val BindingObj = ViewBindingObj ?: return

        savedInstanceState?.getString(KEY_MODE)?.let {
            val RestoredMode = CaptureMode.FromName(NameVal = it)
            SelectedMode = if (RestoredMode == CaptureMode.PS) CaptureMode.POLICY else RestoredMode
        }

        BindModeRow(
            RowBinding = BindingObj.rowModePolicy,
            IconRes = R.drawable.ic_policy,
            TitleRes = R.string.capture_mode_policy,
            DescRes = R.string.capture_mode_policy_desc
        )
        BindModeRow(
            RowBinding = BindingObj.rowModeFup,
            IconRes = R.drawable.ic_calendar_repeat,
            TitleRes = R.string.capture_mode_fup,
            DescRes = R.string.capture_mode_fup_desc
        )
        BindModeRow(
            RowBinding = BindingObj.rowModeCustomer,
            IconRes = R.drawable.ic_person,
            TitleRes = R.string.capture_mode_customer,
            DescRes = R.string.capture_mode_customer_desc
        )

        BindingObj.cardModePolicy.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SelectMode(ModeVal = CaptureMode.POLICY)
        }
        BindingObj.cardModeFup.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SelectMode(ModeVal = CaptureMode.FUP)
        }
        BindingObj.cardModeCustomer.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SelectMode(ModeVal = CaptureMode.CUSTOMER)
        }

        BindingObj.btnPrimaryAction.setOnClickListener { OnPrimaryAction() }

        BindingObj.tvMpinLink.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            startActivity(Intent(requireContext(), MpinActivity::class.java))
        }

        ObserveCaptureState()
        RenderSelection()
    }

    override fun onResume() {
        super.onResume()
        RenderPreflight()
        RenderActionState()
        RenderMpinLink()
        ShowPendingReviewIfAny()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_MODE, SelectedMode.name)
    }


    private fun BindModeRow(
        RowBinding: PartialModeRowBinding,
        IconRes: Int,
        TitleRes: Int,
        DescRes: Int
    ) {
        RowBinding.ivModeIcon.setImageResource(IconRes)
        RowBinding.tvModeTitle.setText(TitleRes)
        RowBinding.tvModeDesc.setText(DescRes)
    }

    private fun SelectMode(ModeVal: CaptureMode) {
        if (ScreenReaderService.IsCapturing) return
        SelectedMode = ModeVal
        RenderSelection()
    }

    private fun RenderSelection() {
        val BindingObj = ViewBindingObj ?: return
        val ContextRef = BindingObj.root.context

        val AccentColor = ContextCompat.getColor(ContextRef, R.color.text_accent)
        val StrokeAccent = ContextCompat.getColor(ContextRef, R.color.text_accent)
        val StrokeDefault = ContextCompat.getColor(ContextRef, R.color.card_stroke)
        val FillAccent = ContextCompat.getColor(ContextRef, R.color.primary_container)
        val FillDefault = ContextCompat.getColor(ContextRef, R.color.surface_light)
        val TextDefault = ContextCompat.getColor(ContextRef, R.color.text_primary)
        val IconDefault = ContextCompat.getColor(ContextRef, R.color.text_secondary)

        val Entries = listOf(
            Triple(CaptureMode.POLICY, BindingObj.cardModePolicy, BindingObj.rowModePolicy),
            Triple(CaptureMode.FUP, BindingObj.cardModeFup, BindingObj.rowModeFup),
            Triple(CaptureMode.CUSTOMER, BindingObj.cardModeCustomer, BindingObj.rowModeCustomer)
        )

        val DensityVal = resources.displayMetrics.density
        val StrokeSelectedPx = (2 * DensityVal).toInt()
        val StrokeDefaultPx = (1 * DensityVal).toInt().coerceAtLeast(1)

        for ((ModeVal, CardRef, RowRef) in Entries) {
            val IsSelected = ModeVal == SelectedMode
            CardRef.setCardBackgroundColor(if (IsSelected) FillAccent else FillDefault)
            CardRef.strokeColor = if (IsSelected) StrokeAccent else StrokeDefault
            CardRef.strokeWidth = if (IsSelected) StrokeSelectedPx else StrokeDefaultPx
            RowRef.ivModeCheck.visibility = if (IsSelected) View.VISIBLE else View.INVISIBLE
            RowRef.ivModeIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                if (IsSelected) AccentColor else IconDefault
            )
            RowRef.tvModeTitle.setTextColor(if (IsSelected) AccentColor else TextDefault)
        }
    }


    private fun RenderMpinLink() {
        val BindingObj = ViewBindingObj ?: return
        val ContextRef = BindingObj.root.context
        val LabelRes = when {
            !MpinStore.HasMpin(ContextRef = ContextRef) -> R.string.mpin_link_not_set
            MpinStore.IsAutoEnterOn(ContextRef = ContextRef) -> R.string.mpin_link_auto
            else -> R.string.mpin_link_saved
        }
        BindingObj.tvMpinLink.setText(LabelRes)
    }


    private fun RenderPreflight() {
        val BindingObj = ViewBindingObj ?: return
        val ContextRef = BindingObj.root.context
        BindingObj.preflightGroup.removeAllViews()

        if (!ScreenReaderService.IsServiceRunning()) {
            AddPreflightRow(
                TitleRes = R.string.preflight_accessibility_title,
                BodyText = getString(R.string.preflight_accessibility_body)
            ) { OpenAccessibilitySettings() }
        }

        if (AppLauncherUtils.IsBatteryOptimized(ContextRef = ContextRef)) {
            AddPreflightRow(
                TitleRes = R.string.preflight_battery_title,
                BodyText = getString(R.string.preflight_battery_body)
            ) { AppLauncherUtils.RequestBatteryOptimizationExemption(ContextRef = ContextRef) }
        }

        if (!CustomerSheetOcr.IsSupported()) {
            AddPreflightRow(
                TitleRes = R.string.preflight_screenshot_title,
                BodyText = getString(
                    R.string.preflight_screenshot_body,
                    Build.VERSION.RELEASE.orEmpty()
                ),
                OnFix = null
            )
        }
    }

    private fun AddPreflightRow(
        TitleRes: Int,
        BodyText: CharSequence,
        OnFix: (() -> Unit)?
    ) {
        val BindingObj = ViewBindingObj ?: return
        val RowBinding = PartialPreflightRowBinding.inflate(
            layoutInflater, BindingObj.preflightGroup, false
        )
        RowBinding.tvPreflightTitle.setText(TitleRes)
        RowBinding.tvPreflightBody.text = BodyText

        if (OnFix == null) {
            RowBinding.btnPreflightFix.visibility = View.GONE
        } else {
            RowBinding.btnPreflightFix.visibility = View.VISIBLE
            RowBinding.btnPreflightFix.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                OnFix()
            }
        }

        BindingObj.preflightGroup.addView(RowBinding.root)
    }

    private fun OpenAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
        }
    }


    private fun ObserveCaptureState() {
        CaptureSessionState.IsCapturingLive.observe(viewLifecycleOwner) { RenderActionState() }
        CaptureSessionState.RecordCountLive.observe(viewLifecycleOwner) { RenderLiveState() }
        CaptureSessionState.ElapsedMsLive.observe(viewLifecycleOwner) { RenderLiveState() }
        CaptureSessionState.PendingSessionLive.observe(viewLifecycleOwner) { SessionObj ->
            if (SessionObj != null) ShowPendingReviewIfAny()
        }
    }

    private fun RenderActionState() {
        val BindingObj = ViewBindingObj ?: return
        val IsRunning = ScreenReaderService.IsCapturing

        BindingObj.btnPrimaryAction.setText(
            if (IsRunning) R.string.capture_finish_now else R.string.capture_start
        )
        BindingObj.tvActionHint.setText(
            if (IsRunning) R.string.capture_in_progress_hint else R.string.capture_start_hint
        )
        BindingObj.cardLiveState.visibility = if (IsRunning) View.VISIBLE else View.GONE

        BindingObj.cardModePolicy.isEnabled = !IsRunning
        BindingObj.cardModeFup.isEnabled = !IsRunning

        if (IsRunning) RenderLiveState()
    }

    private fun RenderLiveState() {
        val BindingObj = ViewBindingObj ?: return
        if (!ScreenReaderService.IsCapturing) return

        val RecordCount = CaptureSessionState.RecordCountLive.value ?: 0
        val ElapsedValue = CaptureSessionState.ElapsedMsLive.value ?: 0L
        BindingObj.tvLiveState.text = getString(
            R.string.capture_live_format,
            CaptureSessionState.ActiveMode.DescribeCount(CountVal = RecordCount),
            CaptureSession.FormatClock(DurationMsVal = ElapsedValue)
        )
    }

    private fun OnPrimaryAction() {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return

        val BindingObj = ViewBindingObj

        if (ScreenReaderService.IsCapturing) {
            HapticFeedback.Confirm(ViewRef = BindingObj?.btnPrimaryAction)
            CaptureFlow.Finish(ActivityRef = ActivityRef)
            return
        }

        if (SelectedMode == CaptureMode.POLICY) {
            HapticFeedback.Tap(ViewRef = BindingObj?.btnPrimaryAction)
            ShowPolicyCaptureModeSheet(ActivityRef = ActivityRef)
            return
        }

        if (SelectedMode == CaptureMode.CUSTOMER) {
            HapticFeedback.Tap(ViewRef = BindingObj?.btnPrimaryAction)
            ShowCustomerSessionPicker(ActivityRef = ActivityRef)
            return
        }

        StartSelectedCapture(CapturePolicyDetails = false)
    }

    private fun ShowPolicyCaptureModeSheet(ActivityRef: androidx.appcompat.app.AppCompatActivity) {
        val SheetBinding = SheetPolicyCaptureModeBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)
        SheetBinding.cardFastCapture.setOnClickListener {
            SheetDialog.dismiss()
            ContinueToCapture(
                ActivityRef = ActivityRef,
                CapturePolicyDetails = false,
                ImportDueDates = SheetBinding.swImportDueDates.isChecked
            )
        }
        SheetBinding.cardFullCapture.setOnClickListener {
            SheetDialog.dismiss()
            ContinueToCapture(
                ActivityRef = ActivityRef,
                CapturePolicyDetails = true,
                ImportDueDates = SheetBinding.swImportDueDates.isChecked
            )
        }
        SheetBinding.btnCancelCaptureMode.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }

    private fun ContinueToCapture(
        ActivityRef: androidx.appcompat.app.AppCompatActivity,
        CapturePolicyDetails: Boolean,
        ImportDueDates: Boolean
    ) {
        if (!ImportDueDates) {
            StartSelectedCapture(CapturePolicyDetails = CapturePolicyDetails)
            return
        }

        val RenewalSessions = PolicyRepository.GetSessionHistory(ContextRef = ActivityRef)
            .filter { SessionRef -> SessionRef.Mode == CaptureMode.FUP }
            .sortedByDescending { SessionRef -> SessionRef.SavedAt }

        if (RenewalSessions.isEmpty()) {
            CaptureFlow.ShowMessage(
                ActivityRef = ActivityRef,
                MessageVal = getString(R.string.capture_due_no_sessions)
            )
            return
        }

        ShowRenewalSessionPicker(
            ActivityRef = ActivityRef,
            SessionList = RenewalSessions
        ) { SessionIdVal ->
            StartSelectedCapture(
                CapturePolicyDetails = CapturePolicyDetails,
                DueDateSessionId = SessionIdVal
            )
        }
    }

    private fun ShowRenewalSessionPicker(
        ActivityRef: androidx.appcompat.app.AppCompatActivity,
        SessionList: List<PolicyRepository.CaptureSessionReference>,
        OnPicked: (String) -> Unit
    ) {
        val DateFormatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        val SheetBinding = SheetSessionPickerBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)

        SheetBinding.tvSessionPickHeading.setText(R.string.capture_due_pick_session)
        SheetBinding.tvSessionPickBody.setText(R.string.capture_due_pick_body)

        for (SessionRef in SessionList) {
            val RowBinding = ItemSessionPickBinding.inflate(
                layoutInflater,
                SheetBinding.sessionPickContainer,
                false
            )
            RowBinding.tvSessionPickTitle.text = SessionRef.Mode.DescribeCount(
                CountVal = SessionRef.RecordCount
            )
            RowBinding.tvSessionPickMeta.text = getString(
                R.string.capture_customer_session_format,
                DateFormatter.format(Date(SessionRef.SavedAt)),
                SessionRef.SessionId.take(8)
            )
            RowBinding.sessionPickCard.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SheetDialog.dismiss()
                OnPicked(SessionRef.SessionId)
            }
            SheetBinding.sessionPickContainer.addView(RowBinding.root)
        }

        SheetBinding.btnSessionPickCancel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }

    private fun ShowCustomerSessionPicker(ActivityRef: androidx.appcompat.app.AppCompatActivity) {
        val SessionList = PolicyRepository.GetSessionHistory(ContextRef = ActivityRef)
            .filter { SessionRef -> SessionRef.Mode == CaptureMode.POLICY }
            .sortedByDescending { SessionRef -> SessionRef.SavedAt }

        if (SessionList.isEmpty()) {
            CaptureFlow.ShowMessage(
                ActivityRef = ActivityRef,
                MessageVal = getString(R.string.capture_customer_no_sessions)
            )
            return
        }

        val DateFormatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        val SheetBinding = SheetSessionPickerBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(ActivityRef)
        SheetDialog.setContentView(SheetBinding.root)

        for (SessionRef in SessionList) {
            val RowBinding = ItemSessionPickBinding.inflate(
                layoutInflater,
                SheetBinding.sessionPickContainer,
                false
            )
            RowBinding.tvSessionPickTitle.text = SessionRef.Mode.DescribeCount(
                CountVal = SessionRef.RecordCount
            )
            RowBinding.tvSessionPickMeta.text = getString(
                R.string.capture_customer_session_format,
                DateFormatter.format(Date(SessionRef.SavedAt)),
                SessionRef.SessionId.take(8)
            )
            RowBinding.sessionPickCard.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SheetDialog.dismiss()
                StartCustomerCapture(SessionIdVal = SessionRef.SessionId)
            }
            SheetBinding.sessionPickContainer.addView(RowBinding.root)
        }

        SheetBinding.btnSessionPickCancel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }

    private fun StartCustomerCapture(SessionIdVal: String) {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnPrimaryAction)
        CaptureFlow.StartCustomerCapture(ActivityRef = ActivityRef, SessionIdVal = SessionIdVal)
    }

    private fun StartSelectedCapture(
        CapturePolicyDetails: Boolean,
        DueDateSessionId: String = ""
    ) {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnPrimaryAction)

        val StartedOk = CaptureFlow.Start(
            ActivityRef = ActivityRef,
            ModeVal = SelectedMode,
            LaunchTarget = true,
            CapturePolicyDetails = CapturePolicyDetails,
            DueDateSessionId = DueDateSessionId
        )
        if (!StartedOk) RenderPreflight()
    }

    private fun ShowPendingReviewIfAny() {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        val SessionObj = CaptureSessionState.PendingSession ?: return
        CaptureFlow.ShowPendingReview(ActivityRef = ActivityRef, ModeVal = SessionObj.Mode) { SavedCount ->
            RenderActionState()
            if (SavedCount > 0) {
                CaptureFlow.ShowMessage(
                    ActivityRef = ActivityRef,
                    MessageVal = getString(
                        R.string.review_saved_format,
                        SessionObj.Mode.DescribeCount(CountVal = SavedCount)
                    )
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ViewBindingObj = null
    }

    companion object {
        private const val KEY_MODE = "selected_mode"
    }
}
