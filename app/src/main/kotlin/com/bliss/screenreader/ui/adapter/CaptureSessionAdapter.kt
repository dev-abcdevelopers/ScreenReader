@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ItemCaptureSessionBinding
import com.bliss.screenreader.utils.HapticFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureSessionAdapter(
    private var SessionList: List<PolicyRepository.CaptureSessionReference> = emptyList(),
    private val OnRowClick: (PolicyRepository.CaptureSessionReference) -> Unit = {},
    private val OnResumeClick: (PolicyRepository.CaptureSessionReference) -> Unit = {},
    private val OnDeleteClick: (PolicyRepository.CaptureSessionReference) -> Unit = {}
) : RecyclerView.Adapter<CaptureSessionAdapter.SessionViewHolder>() {

    class SessionViewHolder(val BindingRef: ItemCaptureSessionBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    private val DateFormatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

    private var OpenSessionId: String = ""

    fun IsRowOpen(SessionId: String): Boolean = OpenSessionId == SessionId

    fun SessionAt(PositionVal: Int): PolicyRepository.CaptureSessionReference? {
        return SessionList.getOrNull(PositionVal)
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
        val PositionVal = SessionList.indexOfFirst { SessionRef ->
            SessionRef.SessionId == SessionId
        }
        if (PositionVal >= 0) notifyItemChanged(PositionVal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        return SessionViewHolder(
            BindingRef = ItemCaptureSessionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun getItemCount(): Int = SessionList.size

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val SessionRef = SessionList[position]
        val BindingRef = holder.BindingRef
        val ContextRef = BindingRef.root.context

        val CaptureType = ContextRef.getString(
            when {
                SessionRef.Mode == CaptureMode.FUP -> R.string.sessions_type_renewals
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
                DateFormatter.format(Date(SessionRef.LastResumedAt))
            )
        } else {
            ContextRef.getString(
                R.string.sessions_created_format,
                DateFormatter.format(Date(SessionRef.SavedAt))
            )
        }
        BindingRef.tvSessionId.text = ContextRef.getString(
            R.string.sessions_id_format,
            SessionRef.SessionId
        )
        val IsOpen = OpenSessionId == SessionRef.SessionId
        BindingRef.sessionRowRoot.translationX = if (IsOpen) RevealWidthPx(holder) else 0f

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
    }

    private fun RevealWidthPx(holder: SessionViewHolder): Float {
        val ResourcesRef = holder.BindingRef.root.resources
        return ResourcesRef.getDimensionPixelSize(R.dimen.touch_min).toFloat()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateData(NewSessions: List<PolicyRepository.CaptureSessionReference>) {
        SessionList = NewSessions
        if (NewSessions.none { SessionRef -> SessionRef.SessionId == OpenSessionId }) {
            OpenSessionId = ""
        }
        notifyDataSetChanged()
    }
}
