@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.databinding.ItemSessionDateHeaderBinding
import com.bliss.screenreader.utils.HapticFeedback
import kotlin.math.abs

class SessionStickyHeaderDecoration(
    private val AdapterRef: CaptureSessionAdapter
) : RecyclerView.ItemDecoration(), RecyclerView.OnItemTouchListener {

    private var HeaderBindingRef: ItemSessionDateHeaderBinding? = null
    private var PinnedPosition = RecyclerView.NO_POSITION
    private var PinnedTopPx = 0f
    private var PinnedBottomPx = 0f

    private var DownXPx = 0f
    private var DownYPx = 0f
    private var DownAtMs = 0L

    override fun onDrawOver(
        CanvasRef: Canvas,
        ParentRef: RecyclerView,
        StateRef: RecyclerView.State
    ) {
        PinnedBottomPx = 0f
        PinnedPosition = RecyclerView.NO_POSITION
        if (ParentRef.adapter !== AdapterRef) return

        val TopChild = ParentRef.getChildAt(0) ?: return
        val TopPosition = ParentRef.getChildAdapterPosition(TopChild)
        if (TopPosition == RecyclerView.NO_POSITION) return

        val HeaderPosition = AdapterRef.HeaderPositionFor(PositionVal = TopPosition)
        if (HeaderPosition == RecyclerView.NO_POSITION) return

        val BindingRef = HeaderViewFor(ParentRef = ParentRef, PositionVal = HeaderPosition)
        val HeaderView = BindingRef.root
        if (HeaderView.height <= 0) return

        val AnchorView = ParentRef.findViewHolderForAdapterPosition(HeaderPosition)?.itemView
        var OffsetTop = if (AnchorView == null) 0f else AnchorView.top.toFloat().coerceAtLeast(0f)

        val NextChild = ParentRef.findChildViewUnder(
            ParentRef.width / 2f,
            OffsetTop + HeaderView.height + 1f
        )
        if (NextChild != null) {
            val NextPosition = ParentRef.getChildAdapterPosition(NextChild)
            if (NextPosition != RecyclerView.NO_POSITION &&
                NextPosition != HeaderPosition &&
                AdapterRef.IsHeaderAt(PositionVal = NextPosition) &&
                NextChild.top < OffsetTop + HeaderView.height
            ) {
                OffsetTop = (NextChild.top - HeaderView.height).toFloat()
            }
        }

        PinnedPosition = HeaderPosition
        PinnedTopPx = OffsetTop
        PinnedBottomPx = OffsetTop + HeaderView.height

        CanvasRef.save()
        CanvasRef.translate(ParentRef.paddingLeft.toFloat(), OffsetTop)
        HeaderView.draw(CanvasRef)
        CanvasRef.restore()
    }

    private fun HeaderViewFor(
        ParentRef: RecyclerView,
        PositionVal: Int
    ): ItemSessionDateHeaderBinding {
        var BindingRef = HeaderBindingRef
        if (BindingRef == null) {
            BindingRef = ItemSessionDateHeaderBinding.inflate(
                LayoutInflater.from(ParentRef.context), ParentRef, false
            )
            HeaderBindingRef = BindingRef
        }
        AdapterRef.BindHeader(BindingRef = BindingRef, PositionVal = PositionVal)

        val HeaderView = BindingRef.root
        val TargetWidth = ParentRef.width - ParentRef.paddingLeft - ParentRef.paddingRight
        if (HeaderView.width != TargetWidth || HeaderView.height <= 0) {
            val DeclaredHeight = HeaderView.layoutParams?.height ?: 0
            val HeightSpec = if (DeclaredHeight > 0) {
                View.MeasureSpec.makeMeasureSpec(DeclaredHeight, View.MeasureSpec.EXACTLY)
            } else {
                View.MeasureSpec.makeMeasureSpec(ParentRef.height, View.MeasureSpec.AT_MOST)
            }
            HeaderView.measure(
                View.MeasureSpec.makeMeasureSpec(TargetWidth, View.MeasureSpec.EXACTLY),
                HeightSpec
            )
            HeaderView.layout(0, 0, HeaderView.measuredWidth, HeaderView.measuredHeight)
        }
        return BindingRef
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (PinnedBottomPx <= 0f || PinnedPosition == RecyclerView.NO_POSITION) return false

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                DownXPx = e.x
                DownYPx = e.y
                DownAtMs = e.eventTime
            }

            MotionEvent.ACTION_UP -> {
                if (!InPinnedBand(YPx = DownYPx) || !InPinnedBand(YPx = e.y)) return false
                val SlopPx = ViewConfiguration.get(rv.context).scaledTouchSlop
                if (abs(e.x - DownXPx) > SlopPx || abs(e.y - DownYPx) > SlopPx) return false
                if (e.eventTime - DownAtMs > ViewConfiguration.getLongPressTimeout()) return false

                val HeaderPosition = PinnedPosition
                HapticFeedback.Tap(ViewRef = rv)
                AdapterRef.ToggleGroupAt(PositionVal = HeaderPosition)
                rv.post { rv.scrollToPosition(HeaderPosition) }
                return true
            }
        }
        return false
    }

    private fun InPinnedBand(YPx: Float): Boolean =
        YPx >= PinnedTopPx && YPx <= PinnedBottomPx

    fun Reset() {
        HeaderBindingRef = null
        PinnedPosition = RecyclerView.NO_POSITION
        PinnedTopPx = 0f
        PinnedBottomPx = 0f
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) = Unit

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit
}
