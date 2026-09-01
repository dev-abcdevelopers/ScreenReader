@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ItemCaptureSessionBinding
import com.bliss.screenreader.databinding.ItemSessionDateHeaderBinding
import com.bliss.screenreader.utils.HapticFeedback
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CaptureSessionAdapter(
    private var SessionList: List<PolicyRepository.CaptureSessionReference> = emptyList(),
    private val OnRowClick: (PolicyRepository.CaptureSessionReference) -> Unit = {},
    private val OnResumeClick: (PolicyRepository.CaptureSessionReference) -> Unit = {},
    private val OnDeleteClick: (PolicyRepository.CaptureSessionReference) -> Unit = {},
    private val OnShareLogClick: (PolicyRepository.CaptureSessionReference) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class RowItem {

        data class Header(val GroupKey: Long, val SessionCount: Int) : RowItem()

        data class Session(
            val SessionRef: PolicyRepository.CaptureSessionReference
        ) : RowItem()
    }

    class SessionViewHolder(val BindingRef: ItemCaptureSessionBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    class HeaderViewHolder(val BindingRef: ItemSessionDateHeaderBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    private val TimeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val WeekdayFormatter = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    private val FullDateFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    private val CollapsedKeys = LinkedHashSet<Long>()
    private var SeedDone = false
    private var RowItems: List<RowItem> = emptyList()

    private var OpenSessionId: String = ""

    init {
        RebuildRows()
    }

    fun IsRowOpen(SessionId: String): Boolean = OpenSessionId == SessionId

    fun SessionAt(PositionVal: Int): PolicyRepository.CaptureSessionReference? {
        return (RowItems.getOrNull(PositionVal) as? RowItem.Session)?.SessionRef
    }

    fun IsHeaderAt(PositionVal: Int): Boolean = RowItems.getOrNull(PositionVal) is RowItem.Header

    fun HeaderPositionFor(PositionVal: Int): Int {
        var IndexVal = PositionVal.coerceAtMost(RowItems.size - 1)
        while (IndexVal >= 0) {
            if (RowItems[IndexVal] is RowItem.Header) return IndexVal
            IndexVal -= 1
        }
        return RecyclerView.NO_POSITION
    }

    fun OpenRow(SessionId: String) {
        if (OpenSessionId == SessionId) return
        val PreviousId = OpenSessionId
        OpenSessionId = SessionId
        NotifyRowChanged(SessionId = PreviousId)
        NotifyRowChanged(SessionId = SessionId)
    }

    fun CloseOpenRow() {
        if (OpenSessionId.isEmpty()) return
        val PreviousId = OpenSessionId
        OpenSessionId = ""
        NotifyRowChanged(SessionId = PreviousId)
    }

    private fun NotifyRowChanged(SessionId: String) {
        if (SessionId.isEmpty()) return
        val PositionVal = RowItems.indexOfFirst { RowRef ->
            RowRef is RowItem.Session && RowRef.SessionRef.SessionId == SessionId
        }
        if (PositionVal >= 0) notifyItemChanged(PositionVal)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun ToggleGroupAt(PositionVal: Int) {
        val HeaderItem = RowItems.getOrNull(PositionVal) as? RowItem.Header ?: return
        ToggleGroup(GroupKey = HeaderItem.GroupKey)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun ToggleGroup(GroupKey: Long) {
        if (!CollapsedKeys.remove(GroupKey)) CollapsedKeys.add(GroupKey)
        OpenSessionId = ""
        RebuildRows()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = RowItems.size

    override fun getItemViewType(position: Int): Int {
        return if (RowItems[position] is RowItem.Header) VIEW_TYPE_HEADER else VIEW_TYPE_SESSION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val InflaterRef = LayoutInflater.from(parent.context)
        if (viewType == VIEW_TYPE_HEADER) {
            return HeaderViewHolder(
                BindingRef = ItemSessionDateHeaderBinding.inflate(InflaterRef, parent, false)
            )
        }
        return SessionViewHolder(
            BindingRef = ItemCaptureSessionBinding.inflate(InflaterRef, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> BindHeader(
                BindingRef = holder.BindingRef,
                PositionVal = position
            )

            is SessionViewHolder -> BindSession(
                HolderRef = holder,
                SessionRef = SessionAt(PositionVal = position) ?: return
            )
        }
    }

    fun BindHeader(BindingRef: ItemSessionDateHeaderBinding, PositionVal: Int) {
        val HeaderItem = RowItems.getOrNull(PositionVal) as? RowItem.Header ?: return
        val ContextRef = BindingRef.root.context
        val LabelText = GroupLabel(ContextRef = ContextRef, GroupKey = HeaderItem.GroupKey)
        val IsExpanded = !CollapsedKeys.contains(HeaderItem.GroupKey)

        BindingRef.tvSessionGroupLabel.text = LabelText
        BindingRef.tvSessionGroupCount.text = HeaderItem.SessionCount.toString()
        BindingRef.ivSessionGroupCaret.rotation = if (IsExpanded) 0f else -90f
        BindingRef.sessionGroupRoot.contentDescription = ContextRef.getString(
            if (IsExpanded) R.string.sessions_group_collapse else R.string.sessions_group_expand,
            LabelText
        )
        BindingRef.sessionGroupRoot.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ToggleGroup(GroupKey = HeaderItem.GroupKey)
        }
    }

    private fun BindSession(
        HolderRef: SessionViewHolder,
        SessionRef: PolicyRepository.CaptureSessionReference
    ) {
        val BindingRef = HolderRef.BindingRef
        val ContextRef = BindingRef.root.context

        val CaptureType = ContextRef.getString(
            when {
                SessionRef.Mode == CaptureMode.FUP -> R.string.sessions_type_renewals
                SessionRef.Mode == CaptureMode.RENEWAL_DUE -> R.string.sessions_type_renewals_due
                SessionRef.CapturePolicyDetails -> R.string.sessions_type_full
                else -> R.string.sessions_type_fast
            }
        )
        BindingRef.tvSessionTitle.text = ContextRef.getString(
            R.string.sessions_title_mode_format,
            SessionRef.Mode.DescribeCount(CountVal = SessionRef.RecordCount),
            CaptureType
        )
        BindingRef.tvSessionDate.text = if (SessionRef.LastResumedAt > 0L) {
            ContextRef.getString(
                R.string.sessions_resumed_format,
                TimeFormatter.format(Date(SessionRef.LastResumedAt))
            )
        } else {
            ContextRef.getString(
                R.string.sessions_created_format,
                TimeFormatter.format(Date(SessionRef.SavedAt))
            )
        }
        BindingRef.tvSessionId.text = ContextRef.getString(
            R.string.sessions_id_format,
            SessionRef.SessionId
        )
        val IsOpen = OpenSessionId == SessionRef.SessionId
        BindingRef.sessionRowRoot.translationX = if (IsOpen) RevealWidthPx(holder = HolderRef) else 0f

        BindingRef.sessionRowRoot.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            if (OpenSessionId == SessionRef.SessionId) {
                CloseOpenRow()
            } else {
                OnRowClick(SessionRef)
            }
        }
        BindingRef.btnSessionResume.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            CloseOpenRow()
            OnResumeClick(SessionRef)
        }
        BindingRef.btnSessionDelete.setOnClickListener { ViewRef ->
            HapticFeedback.Reject(ViewRef = ViewRef)
            OnDeleteClick(SessionRef)
        }
        BindingRef.btnSessionShareLog.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnShareLogClick(SessionRef)
        }
    }

    private fun RevealWidthPx(holder: SessionViewHolder): Float {
        val ResourcesRef = holder.BindingRef.root.resources
        return ResourcesRef.getDimensionPixelSize(R.dimen.session_reveal_width).toFloat()
    }

    private fun GroupLabel(ContextRef: Context, GroupKey: Long): String {
        val TodayKey = MidnightOf(StampVal = System.currentTimeMillis())
        val DayGap = (TodayKey - GroupKey + HALF_DAY_MILLIS) / DAY_MILLIS
        return when {
            DayGap <= 0L -> ContextRef.getString(R.string.sessions_group_today)
            DayGap == 1L -> ContextRef.getString(R.string.sessions_group_yesterday)
            DayGap < 7L -> WeekdayFormatter.format(Date(GroupKey))
            else -> FullDateFormatter.format(Date(GroupKey))
        }
    }

    private fun DisplayStampOf(SessionRef: PolicyRepository.CaptureSessionReference): Long {
        return if (SessionRef.LastResumedAt > 0L) SessionRef.LastResumedAt else SessionRef.SavedAt
    }

    private fun MidnightOf(StampVal: Long): Long {
        val CalendarRef = Calendar.getInstance()
        CalendarRef.timeInMillis = StampVal
        CalendarRef.set(Calendar.HOUR_OF_DAY, 0)
        CalendarRef.set(Calendar.MINUTE, 0)
        CalendarRef.set(Calendar.SECOND, 0)
        CalendarRef.set(Calendar.MILLISECOND, 0)
        return CalendarRef.timeInMillis
    }

    private fun RebuildRows() {
        val GroupedSessions = LinkedHashMap<Long, MutableList<PolicyRepository.CaptureSessionReference>>()
        val OrderedSessions = SessionList.sortedByDescending { SessionRef ->
            DisplayStampOf(SessionRef = SessionRef)
        }
        for (SessionRef in OrderedSessions) {
            val GroupKey = MidnightOf(StampVal = DisplayStampOf(SessionRef = SessionRef))
            GroupedSessions.getOrPut(GroupKey) { mutableListOf() }.add(SessionRef)
        }

        if (!SeedDone && GroupedSessions.isNotEmpty()) {
            SeedDone = true
            var IsNewest = true
            for (GroupKey in GroupedSessions.keys) {
                if (IsNewest) IsNewest = false else CollapsedKeys.add(GroupKey)
            }
        } else {
            CollapsedKeys.retainAll(GroupedSessions.keys)
        }

        val NewRows = mutableListOf<RowItem>()
        for ((GroupKey, GroupSessions) in GroupedSessions) {
            NewRows.add(RowItem.Header(GroupKey = GroupKey, SessionCount = GroupSessions.size))
            if (CollapsedKeys.contains(GroupKey)) continue
            for (SessionRef in GroupSessions) {
                NewRows.add(RowItem.Session(SessionRef = SessionRef))
            }
        }
        RowItems = NewRows
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateData(NewSessions: List<PolicyRepository.CaptureSessionReference>) {
        SessionList = NewSessions
        if (NewSessions.none { SessionRef -> SessionRef.SessionId == OpenSessionId }) {
            OpenSessionId = ""
        }
        RebuildRows()
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SESSION = 1
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        private const val HALF_DAY_MILLIS = DAY_MILLIS / 2L
    }
}
