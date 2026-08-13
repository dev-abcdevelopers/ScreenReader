@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import android.content.Intent
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
import com.bliss.screenreader.databinding.FragmentCaptureBinding
import com.bliss.screenreader.databinding.PartialModeRowBinding
import com.bliss.screenreader.databinding.PartialPreflightRowBinding
import com.bliss.screenreader.databinding.SheetPolicyCaptureModeBinding
import com.bliss.screenreader.service.CaptureSessionState
import com.bliss.screenreader.service.ScreenReaderService
import com.bliss.screenreader.utils.AppLauncherUtils
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Home. One mode picker, one button, and warnings that appear only when a
 * prerequisite is genuinely missing — the old screen kept two permission rows
 * on display forever, long after they stopped being interesting.
 */
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
            // A state bundle saved before PS was hidden would otherwise restore
            // a mode that no longer has a visible card to select.
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

        BindingObj.cardModePolicy.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SelectMode(ModeVal = CaptureMode.POLICY)
        }
        BindingObj.cardModeFup.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SelectMode(ModeVal = CaptureMode.FUP)
        }

        // The haptic lives in OnPrimaryAction, not here: this button either
        // commits or opens a sheet, and those should not feel the same.
        BindingObj.btnPrimaryAction.setOnClickListener { OnPrimaryAction() }

        ObserveCaptureState()
        RenderSelection()
    }

    override fun onResume() {
        super.onResume()
        RenderPreflight()
        RenderActionState()
        ShowPendingReviewIfAny()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_MODE, SelectedMode.name)
    }

    // ------------------------------------------------------------ selection

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

        // PS is hidden from the picker for now; its card stays in the layout
        // with visibility=gone so nothing else has to change to bring it back.
        val Entries = listOf(
            Triple(CaptureMode.POLICY, BindingObj.cardModePolicy, BindingObj.rowModePolicy),
            Triple(CaptureMode.FUP, BindingObj.cardModeFup, BindingObj.rowModeFup)
        )

        // strokeWidth is in pixels, so the dp values have to be converted.
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

    // ------------------------------------------------------------ preflight

    /**
     * Rebuilds the banner stack from scratch each resume. When everything is
     * granted the container is empty and the picker moves up the screen.
     */
    private fun RenderPreflight() {
        val BindingObj = ViewBindingObj ?: return
        val ContextRef = BindingObj.root.context
        BindingObj.preflightGroup.removeAllViews()

        if (!ScreenReaderService.IsServiceRunning()) {
            AddPreflightRow(
                TitleRes = R.string.preflight_accessibility_title,
                BodyRes = R.string.preflight_accessibility_body
            ) { OpenAccessibilitySettings() }
        }

        if (AppLauncherUtils.IsBatteryOptimized(ContextRef = ContextRef)) {
            AddPreflightRow(
                TitleRes = R.string.preflight_battery_title,
                BodyRes = R.string.preflight_battery_body
            ) { AppLauncherUtils.RequestBatteryOptimizationExemption(ContextRef = ContextRef) }
        }
    }

    private fun AddPreflightRow(TitleRes: Int, BodyRes: Int, OnFix: () -> Unit) {
        val BindingObj = ViewBindingObj ?: return
        val RowBinding = PartialPreflightRowBinding.inflate(
            layoutInflater, BindingObj.preflightGroup, false
        )
        RowBinding.tvPreflightTitle.setText(TitleRes)
        RowBinding.tvPreflightBody.setText(BodyRes)
        RowBinding.btnPreflightFix.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnFix()
        }
        BindingObj.preflightGroup.addView(RowBinding.root)
    }

    private fun OpenAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
        }
    }

    // --------------------------------------------------------- capture state

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
            // Opening the picker is not yet a commitment.
            HapticFeedback.Tap(ViewRef = BindingObj?.btnPrimaryAction)
            ShowPolicyCaptureModeSheet(ActivityRef = ActivityRef)
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
            StartSelectedCapture(CapturePolicyDetails = false)
        }
        SheetBinding.cardFullCapture.setOnClickListener {
            SheetDialog.dismiss()
            StartSelectedCapture(CapturePolicyDetails = true)
        }
        SheetBinding.btnCancelCaptureMode.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }
        SheetDialog.show()
    }

    private fun StartSelectedCapture(CapturePolicyDetails: Boolean) {
        val ActivityRef = activity as? androidx.appcompat.app.AppCompatActivity ?: return
        // The single commit point for both the direct and the sheet path.
        HapticFeedback.Confirm(ViewRef = ViewBindingObj?.btnPrimaryAction)

        // The accessibility service waits until the correct target screen is
        // visible before starting any mode-specific automation.
        val StartedOk = CaptureFlow.Start(
            ActivityRef = ActivityRef,
            ModeVal = SelectedMode,
            LaunchTarget = true,
            CapturePolicyDetails = CapturePolicyDetails
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
