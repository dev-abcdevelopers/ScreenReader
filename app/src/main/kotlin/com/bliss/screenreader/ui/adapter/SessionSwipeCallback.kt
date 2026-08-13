@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ItemCaptureSessionBinding
import com.bliss.screenreader.utils.HapticFeedback

/**
 * Right-swipe on a session row to reveal its delete action.
 *
 * The row is never dismissed by the swipe itself. [onSwiped] parks it open and
 * hands control back to the adapter, so deleting always takes a second,
 * deliberate tap on the revealed icon - a swipe alone cannot destroy a session.
 *
 * Only the foreground is translated; the delete layer underneath stays put.
 */
class SessionSwipeCallback(
    private val AdapterRef: CaptureSessionAdapter
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    /**
     * The same RecyclerView also shows policy and renewal rows, which have no
     * delete action, so swipe is enabled only for session rows.
     */
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (viewHolder !is CaptureSessionAdapter.SessionViewHolder) return 0
        return super.getMovementFlags(recyclerView, viewHolder)
    }

    /** A short drag should reveal; the row is small and easy to overshoot. */
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.25f

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val SessionRef = AdapterRef.SessionAt(PositionVal = viewHolder.bindingAdapterPosition)
            ?: return
        // A light tick confirms the reveal landed. Deliberately not the
        // destructive pattern - nothing has been deleted yet.
        HapticFeedback.Tap(ViewRef = viewHolder.itemView)
        // Re-binds the row, which resets the swipe animation and applies the
        // parked translation from the adapter instead.
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

        // Clamped to the same width the adapter parks the row at, so releasing
        // the swipe does not visibly jump to a different offset.
        val MaxRevealPx = ForegroundView.resources
            .getDimensionPixelSize(R.dimen.touch_min)
            .toFloat()
        val ClampedDx = dX.coerceIn(0f, MaxRevealPx)

        getDefaultUIUtil().onDraw(
            c, recyclerView, ForegroundView, ClampedDx, dY, actionState, isCurrentlyActive
        )
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        val ForegroundView = ForegroundOf(viewHolder = viewHolder) ?: return
        getDefaultUIUtil().clearView(ForegroundView)
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        val ForegroundView = viewHolder?.let { HolderRef -> ForegroundOf(viewHolder = HolderRef) }
        if (ForegroundView != null) getDefaultUIUtil().onSelected(ForegroundView)
    }

    private fun ForegroundOf(viewHolder: RecyclerView.ViewHolder) =
        (viewHolder as? CaptureSessionAdapter.SessionViewHolder)
            ?.BindingRef
            ?.let { BindingRef: ItemCaptureSessionBinding -> BindingRef.sessionRowRoot }
}
