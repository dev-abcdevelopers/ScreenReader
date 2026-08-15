@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ItemCaptureSessionBinding
import com.bliss.screenreader.utils.HapticFeedback

class SessionSwipeCallback(
    private val AdapterRef: CaptureSessionAdapter
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

    private var TickedHolderRef: RecyclerView.ViewHolder? = null

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (viewHolder !is CaptureSessionAdapter.SessionViewHolder) return 0
        return super.getMovementFlags(recyclerView, viewHolder)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.25f

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val SessionRef = AdapterRef.SessionAt(PositionVal = viewHolder.bindingAdapterPosition)
            ?: return
        HapticFeedback.Confirm(ViewRef = viewHolder.itemView)
        AdapterRef.OpenRow(SessionId = SessionRef.SessionId)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val ForegroundView = ForegroundOf(viewHolder = viewHolder)
        if (ForegroundView == null) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        val MaxRevealPx = ForegroundView.resources
            .getDimensionPixelSize(R.dimen.session_reveal_width)
            .toFloat()
        val ClampedDx = dX.coerceIn(0f, MaxRevealPx)

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCurrentlyActive) {
            if (ClampedDx >= MaxRevealPx * TICK_FRACTION) {
                if (TickedHolderRef !== viewHolder) {
                    TickedHolderRef = viewHolder
                    HapticFeedback.Tap(ViewRef = ForegroundView)
                }
            } else if (TickedHolderRef === viewHolder) {
                TickedHolderRef = null
            }
        }

        getDefaultUIUtil().onDraw(
            c, recyclerView, ForegroundView, ClampedDx, dY, actionState, isCurrentlyActive
        )
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (TickedHolderRef === viewHolder) TickedHolderRef = null
        val ForegroundView = ForegroundOf(viewHolder = viewHolder) ?: return
        getDefaultUIUtil().clearView(ForegroundView)
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        val ForegroundView = viewHolder?.let { HolderRef -> ForegroundOf(viewHolder = HolderRef) }
        if (ForegroundView != null) getDefaultUIUtil().onSelected(ForegroundView)
    }

    companion object {
        private const val TICK_FRACTION = 0.55f
    }

    private fun ForegroundOf(viewHolder: RecyclerView.ViewHolder) =
        (viewHolder as? CaptureSessionAdapter.SessionViewHolder)
            ?.BindingRef
            ?.let { BindingRef: ItemCaptureSessionBinding -> BindingRef.sessionRowRoot }
}
