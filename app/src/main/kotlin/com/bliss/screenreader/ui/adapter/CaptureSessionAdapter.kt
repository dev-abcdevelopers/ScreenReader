@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ItemCaptureSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureSessionAdapter(
    private var SessionList: List<PolicyRepository.CaptureSessionReference> = emptyList(),
    private val OnRowClick: (PolicyRepository.CaptureSessionReference) -> Unit = {}
) : RecyclerView.Adapter<CaptureSessionAdapter.SessionViewHolder>() {

    class SessionViewHolder(val BindingRef: ItemCaptureSessionBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    private val DateFormatter = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

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
            if (SessionRef.CapturePolicyDetails) {
                R.string.sessions_type_full
            } else {
                R.string.sessions_type_fast
            }
        )
        BindingRef.tvSessionTitle.text = ContextRef.getString(
            R.string.sessions_title_format,
            SessionRef.RecordCount,
            CaptureType
        )
        BindingRef.tvSessionDate.text = ContextRef.getString(
            R.string.sessions_created_format,
            DateFormatter.format(Date(SessionRef.SavedAt))
        )
        BindingRef.tvSessionId.text = ContextRef.getString(
            R.string.sessions_id_format,
            SessionRef.SessionId
        )
        BindingRef.sessionRowRoot.setOnClickListener { OnRowClick(SessionRef) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateData(NewSessions: List<PolicyRepository.CaptureSessionReference>) {
        SessionList = NewSessions
        notifyDataSetChanged()
    }
}
