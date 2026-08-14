@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.ParsedRecord
import com.bliss.screenreader.databinding.ItemReviewRecordBinding

class ReviewRecordAdapter(
    private var RecordList: List<ParsedRecord> = emptyList()
) : RecyclerView.Adapter<ReviewRecordAdapter.RecordViewHolder>() {

    class RecordViewHolder(val BindingRef: ItemReviewRecordBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val BindingObj = ItemReviewRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecordViewHolder(BindingRef = BindingObj)
    }

    override fun getItemCount(): Int = RecordList.size

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val RecordItem = RecordList[position]
        val ContextRef = holder.BindingRef.root.context

        holder.BindingRef.tvRecordNumber.text = RecordItem.PolicyNumber.ifEmpty { "—" }
        holder.BindingRef.tvRecordPrimary.text = RecordItem.PrimaryLine
        holder.BindingRef.tvRecordSecondary.text = RecordItem.SecondaryLine
        holder.BindingRef.tvRecordFields.text = ContextRef.getString(
            R.string.review_fields_format, RecordItem.FieldCount
        )

        if (RecordItem.HasWarning) {
            holder.BindingRef.recordContainer.setBackgroundResource(R.drawable.bg_warning_row)
            holder.BindingRef.tvRecordWarning.text = RecordItem.Warning
            holder.BindingRef.tvRecordWarning.visibility = android.view.View.VISIBLE
            holder.BindingRef.tvRecordFields.setTextColor(
                ContextCompat.getColor(ContextRef, R.color.status_amber_text)
            )
            holder.BindingRef.tvRecordNumber.setTextColor(
                ContextCompat.getColor(ContextRef, R.color.status_amber_text)
            )
        } else {
            holder.BindingRef.recordContainer.setBackgroundResource(R.drawable.bg_review_row)
            holder.BindingRef.tvRecordWarning.visibility = android.view.View.GONE
            holder.BindingRef.tvRecordFields.setTextColor(
                ContextCompat.getColor(ContextRef, R.color.status_green_text)
            )
            holder.BindingRef.tvRecordNumber.setTextColor(
                ContextCompat.getColor(ContextRef, R.color.text_primary)
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateData(NewRecords: List<ParsedRecord>) {
        RecordList = NewRecords
        notifyDataSetChanged()
    }
}
