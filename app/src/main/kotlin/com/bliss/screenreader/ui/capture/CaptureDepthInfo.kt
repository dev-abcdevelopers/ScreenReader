@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.capture

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ItemCaptureDepthLineBinding
import com.bliss.screenreader.databinding.PartialCaptureDepthInfoBinding
import com.bliss.screenreader.databinding.SheetPolicyCaptureModeBinding
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

object CaptureDepthInfo {

    private const val PULSE_DELAY_MS = 450L
    private const val PULSE_STAGGER_MS = 160L
    private const val PULSE_UP_MS = 220L
    private const val PULSE_DOWN_MS = 260L
    private const val PULSE_SCALE = 1.35f
    private const val PULSE_ROUNDS = 2

    fun Bind(SheetBinding: SheetPolicyCaptureModeBinding, SheetDialog: BottomSheetDialog) {
        FillPanel(
            PanelBinding = SheetBinding.panelFastInfo,
            PaceRes = R.string.depth_info_fast_pace,
            IncludesRes = R.array.depth_info_fast_includes,
            ExcludesRes = R.array.depth_info_fast_excludes,
            HintRes = R.string.depth_info_fast_hint
        )
        FillPanel(
            PanelBinding = SheetBinding.panelFullInfo,
            PaceRes = R.string.depth_info_full_pace,
            IncludesRes = R.array.depth_info_full_includes,
            ExcludesRes = R.array.depth_info_full_excludes,
            HintRes = R.string.depth_info_full_hint
        )

        SheetBinding.btnFastInfo.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            Toggle(
                SheetBinding = SheetBinding,
                SheetDialog = SheetDialog,
                OpenPanel = SheetBinding.panelFastInfo,
                OtherPanel = SheetBinding.panelFullInfo
            )
        }
        SheetBinding.btnFullInfo.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            Toggle(
                SheetBinding = SheetBinding,
                SheetDialog = SheetDialog,
                OpenPanel = SheetBinding.panelFullInfo,
                OtherPanel = SheetBinding.panelFastInfo
            )
        }

        if (!ValueAnimator.areAnimatorsEnabled()) return
        SheetBinding.root.postDelayed({
            Pulse(IconView = SheetBinding.btnFastInfo, Round = 0)
        }, PULSE_DELAY_MS)
        SheetBinding.root.postDelayed({
            Pulse(IconView = SheetBinding.btnFullInfo, Round = 0)
        }, PULSE_DELAY_MS + PULSE_STAGGER_MS)
    }

    private fun Toggle(
        SheetBinding: SheetPolicyCaptureModeBinding,
        SheetDialog: BottomSheetDialog,
        OpenPanel: PartialCaptureDepthInfoBinding,
        OtherPanel: PartialCaptureDepthInfoBinding
    ) {
        val WillOpen = OpenPanel.root.visibility != View.VISIBLE
        OtherPanel.root.visibility = View.GONE
        OpenPanel.root.visibility = if (WillOpen) View.VISIBLE else View.GONE
        if (!WillOpen) return
        SheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        OpenPanel.root.post {
            SheetBinding.captureDepthScroll.smoothScrollTo(
                0,
                (OpenPanel.root.bottom - SheetBinding.captureDepthScroll.height).coerceAtLeast(0)
            )
        }
    }

    private fun FillPanel(
        PanelBinding: PartialCaptureDepthInfoBinding,
        PaceRes: Int,
        IncludesRes: Int,
        ExcludesRes: Int,
        HintRes: Int
    ) {
        val ContextRef = PanelBinding.root.context
        PanelBinding.tvDepthPace.setText(PaceRes)
        PanelBinding.tvDepthHint.setText(HintRes)
        PanelBinding.depthIncludes.removeAllViews()
        PanelBinding.depthExcludes.removeAllViews()
        for (LineText in ContextRef.resources.getStringArray(IncludesRes)) {
            AddLine(
                PanelBinding = PanelBinding,
                LineText = LineText,
                IsIncluded = true
            )
        }
        for (LineText in ContextRef.resources.getStringArray(ExcludesRes)) {
            AddLine(
                PanelBinding = PanelBinding,
                LineText = LineText,
                IsIncluded = false
            )
        }
    }

    private fun AddLine(
        PanelBinding: PartialCaptureDepthInfoBinding,
        LineText: String,
        IsIncluded: Boolean
    ) {
        val ContextRef = PanelBinding.root.context
        val Container = if (IsIncluded) PanelBinding.depthIncludes else PanelBinding.depthExcludes
        val LineBinding = ItemCaptureDepthLineBinding.inflate(
            LayoutInflater.from(ContextRef), Container, false
        )
        LineBinding.tvDepthLine.text = LineText
        LineBinding.ivDepthLine.setImageResource(
            if (IsIncluded) R.drawable.ic_check_circle else R.drawable.ic_close
        )
        LineBinding.ivDepthLine.setColorFilter(
            ContextCompat.getColor(
                ContextRef,
                if (IsIncluded) R.color.status_green_text else R.color.status_red_text
            )
        )
        if (!IsIncluded) {
            LineBinding.tvDepthLine.setTextColor(
                ContextCompat.getColor(ContextRef, R.color.text_secondary)
            )
        }
        Container.addView(LineBinding.root)
    }

    private fun Pulse(IconView: View, Round: Int) {
        if (!IconView.isAttachedToWindow || Round >= PULSE_ROUNDS) return
        IconView.animate()
            .scaleX(PULSE_SCALE)
            .scaleY(PULSE_SCALE)
            .setDuration(PULSE_UP_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                IconView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(PULSE_DOWN_MS)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction { Pulse(IconView = IconView, Round = Round + 1) }
                    .start()
            }
            .start()
    }
}
