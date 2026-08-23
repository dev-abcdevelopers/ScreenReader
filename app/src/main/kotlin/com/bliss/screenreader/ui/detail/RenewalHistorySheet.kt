@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.bliss.screenreader.R
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ItemRenewalRowBinding
import com.bliss.screenreader.databinding.SheetRenewalHistoryBinding
import com.bliss.screenreader.ui.adapter.RenewalRowAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class RenewalHistorySheet : BottomSheetDialogFragment() {

    private var ViewBindingObj: SheetRenewalHistoryBinding? = null
    private val AdapterObj = RenewalRowAdapter(PageMode = true)
    private var PageCount = 0

    private val PageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            MarkSelectedPage(SelectedIndex = position)
            ApplyPagerHeight()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val BindingObj = SheetRenewalHistoryBinding.inflate(inflater, container, false)
        ViewBindingObj = BindingObj
        return BindingObj.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val BindingObj = ViewBindingObj ?: return

        val PolicyNumber = arguments?.getString(ARG_POLICY_NUMBER).orEmpty()
        val RenewalList = PolicyRepository.GetFupPolicies(ContextRef = requireContext())
            .filter { RenewalItem -> RenewalItem.PolicyNumber == PolicyNumber }
            .sortedByDescending { RenewalItem -> RenewalItem.PaymentDate }

        PageCount = RenewalList.size

        val PagerView = BindingObj.vpRenewalSheet
        PagerView.adapter = AdapterObj
        PagerView.offscreenPageLimit = 1
        PagerView.setPageTransformer(MarginPageTransformer(DensityPx(ValueDp = PAGE_GAP_DP)))
        PagerView.registerOnPageChangeCallback(PageChangeCallback)
        AdapterObj.UpdateData(NewRenewals = RenewalList)

        PagerView.visibility = if (PageCount == 0) View.GONE else View.VISIBLE
        ApplyPeekInsets()
        SetPagerHeight(HeightPx = EstimatedPageHeight())

        PagerView.doOnLayout {
            ApplyPeekInsets()
            ApplyPagerHeight()
        }

        BuildPageIndicator(Count = PageCount)

        BindingObj.tvRenewalSheetMeta.text = getString(
            R.string.detail_entries_format,
            RenewalList.size
        )
    }

    private fun ApplyPeekInsets() {
        val BindingObj = ViewBindingObj ?: return
        val PagerView = BindingObj.vpRenewalSheet
        val InnerRecycler = PagerView.getChildAt(0) as? RecyclerView ?: return

        val EdgeMarginPx = resources.getDimensionPixelSize(R.dimen.screen_margin)
        val GapPx = DensityPx(ValueDp = PAGE_GAP_DP)
        val PagerWidthPx = if (PagerView.width > 0) {
            PagerView.width
        } else {
            resources.displayMetrics.widthPixels
        }
        val PeekPx = (PagerWidthPx * PEEK_RATIO).toInt()
        val EndPaddingPx = if (PageCount > 1) PeekPx + GapPx else EdgeMarginPx

        InnerRecycler.clipToPadding = false
        InnerRecycler.clipChildren = false
        InnerRecycler.setPadding(EdgeMarginPx, 0, EndPaddingPx, 0)
    }

    private fun ApplyPagerHeight() {
        val BindingObj = ViewBindingObj ?: return
        val PagerView = BindingObj.vpRenewalSheet
        val InnerRecycler = PagerView.getChildAt(0) as? RecyclerView ?: return
        if (InnerRecycler.childCount == 0) return

        val PageWidthPx = InnerRecycler.getChildAt(0).width
        if (PageWidthPx <= 0) return

        var TallestPx = 0
        InnerRecycler.children.forEach { PageView ->
            PageView.measure(
                View.MeasureSpec.makeMeasureSpec(PageWidthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            if (PageView.measuredHeight > TallestPx) TallestPx = PageView.measuredHeight
        }

        SetPagerHeight(HeightPx = TallestPx)
    }

    private fun SetPagerHeight(HeightPx: Int) {
        val BindingObj = ViewBindingObj ?: return
        val PagerView = BindingObj.vpRenewalSheet
        if (HeightPx <= 0 || PagerView.layoutParams.height == HeightPx) return
        PagerView.layoutParams = PagerView.layoutParams.also { LayoutParamsRef ->
            LayoutParamsRef.height = HeightPx
        }
    }

    private fun EstimatedPageHeight(): Int {
        val EdgeMarginPx = resources.getDimensionPixelSize(R.dimen.screen_margin)
        val GapPx = DensityPx(ValueDp = PAGE_GAP_DP)
        val ScreenWidthPx = resources.displayMetrics.widthPixels
        val EndPaddingPx = if (PageCount > 1) {
            (ScreenWidthPx * PEEK_RATIO).toInt() + GapPx
        } else {
            EdgeMarginPx
        }
        val PageWidthPx = ScreenWidthPx - EdgeMarginPx - EndPaddingPx
        if (PageWidthPx <= 0) return 0

        val SampleView = ItemRenewalRowBinding.inflate(layoutInflater, null, false).root
        SampleView.measure(
            View.MeasureSpec.makeMeasureSpec(PageWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return SampleView.measuredHeight
    }

    private fun BuildPageIndicator(Count: Int) {
        val BindingObj = ViewBindingObj ?: return
        val DotsRow = BindingObj.dotsRenewalSheet
        val PageLabel = BindingObj.tvRenewalPageLabel
        DotsRow.removeAllViews()

        if (Count <= 1) {
            DotsRow.visibility = View.GONE
            PageLabel.visibility = View.GONE
            return
        }

        if (Count > MAX_DOTS) {
            DotsRow.visibility = View.GONE
            PageLabel.visibility = View.VISIBLE
            MarkSelectedPage(SelectedIndex = 0)
            return
        }

        DotsRow.visibility = View.VISIBLE
        PageLabel.visibility = View.GONE

        val DotSizePx = resources.getDimensionPixelSize(R.dimen.page_dot_size)
        val DotGapPx = resources.getDimensionPixelSize(R.dimen.page_dot_gap)
        repeat(Count) {
            val DotView = View(requireContext())
            DotView.layoutParams = LinearLayout.LayoutParams(DotSizePx, DotSizePx).apply {
                marginStart = DotGapPx
                marginEnd = DotGapPx
            }
            DotView.setBackgroundResource(R.drawable.bg_page_dot)
            DotsRow.addView(DotView)
        }
        MarkSelectedPage(SelectedIndex = 0)
    }

    private fun MarkSelectedPage(SelectedIndex: Int) {
        val BindingObj = ViewBindingObj ?: return

        if (BindingObj.tvRenewalPageLabel.visibility == View.VISIBLE) {
            BindingObj.tvRenewalPageLabel.text = getString(
                R.string.renewal_page_position_format,
                SelectedIndex + 1,
                PageCount
            )
            return
        }

        val DotsRow = BindingObj.dotsRenewalSheet
        val DotSizePx = resources.getDimensionPixelSize(R.dimen.page_dot_size)
        val ActiveWidthPx = resources.getDimensionPixelSize(R.dimen.page_dot_active_width)

        DotsRow.children.forEachIndexed { DotIndex, DotView ->
            val IsSelected = DotIndex == SelectedIndex
            DotView.layoutParams = DotView.layoutParams.also { LayoutParamsRef ->
                LayoutParamsRef.width = if (IsSelected) ActiveWidthPx else DotSizePx
            }
            DotView.setBackgroundResource(
                if (IsSelected) R.drawable.bg_page_dot_active else R.drawable.bg_page_dot
            )
        }
    }

    private fun DensityPx(ValueDp: Int): Int =
        (ValueDp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        ViewBindingObj?.vpRenewalSheet?.unregisterOnPageChangeCallback(PageChangeCallback)
        super.onDestroyView()
        ViewBindingObj = null
    }

    companion object {
        const val TAG = "RenewalHistorySheet"
        private const val ARG_POLICY_NUMBER = "arg_policy_number"
        private const val PAGE_GAP_DP = 12
        private const val PEEK_RATIO = 0.10f
        private const val MAX_DOTS = 10

        fun NewInstance(PolicyNumber: String): RenewalHistorySheet {
            return RenewalHistorySheet().apply {
                arguments = Bundle().apply { putString(ARG_POLICY_NUMBER, PolicyNumber) }
            }
        }
    }
}
