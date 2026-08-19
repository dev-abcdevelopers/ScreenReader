@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.parser.FupDataParser
import com.bliss.screenreader.data.parser.RenewalDueProjection
import com.bliss.screenreader.databinding.ItemRenewalRowBinding

class RenewalRowAdapter(
    private var RenewalList: List<FupPolicy> = emptyList()
) : RecyclerView.Adapter<RenewalRowAdapter.RenewalViewHolder>() {

    class RenewalViewHolder(val BindingRef: ItemRenewalRowBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RenewalViewHolder {
        return RenewalViewHolder(
            BindingRef = ItemRenewalRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun getItemCount(): Int = RenewalList.size

    override fun onBindViewHolder(holder: RenewalViewHolder, position: Int) {
        val RenewalItem = RenewalList[position]
        val BindingRef = holder.BindingRef
        val ContextRef = BindingRef.root.context
        val MissingText = ContextRef.getString(R.string.detail_missing)

        BindingRef.tvRenewalPolicyNumber.text = RenewalItem.PolicyNumber.ifEmpty { MissingText }
        BindingRef.tvRenewalHolderName.text = RenewalItem.HolderName.ifEmpty {
            ContextRef.getString(R.string.status_unknown)
        }

        val PlanText = if (RenewalItem.PlanCode.isNotEmpty()) {
            "${RenewalItem.PlanCode} — ${RenewalItem.PlanName}"
        } else {
            RenewalItem.PlanName
        }
        BindingRef.tvRenewalPlanName.text = PlanText
        BindingRef.tvRenewalPlanName.visibility =
            if (PlanText.isBlank()) View.GONE else View.VISIBLE

        val AmountText = FupDataParser.AmountOf(PremiumText = RenewalItem.PremiumAmount)
        val FrequencyText = RenewalItem.PremiumFrequency.ifEmpty {
            FupDataParser.FrequencyOf(PremiumText = RenewalItem.PremiumAmount)
        }
        BindingRef.tvRenewalPremium.text = when {
            AmountText.isEmpty() -> MissingText
            FrequencyText.isEmpty() -> AmountText
            else -> ContextRef.getString(
                R.string.renewal_premium_format,
                AmountText,
                FrequencyText
            )
        }

        BindingRef.tvRenewalPaidFor.text = RenewalItem.DueDate.ifEmpty { MissingText }
        BindingRef.tvRenewalPaymentDate.text = RenewalItem.PaymentDate.ifEmpty { MissingText }
        BindingRef.tvRenewalMode.text = RenewalItem.ModeOfPayment.ifEmpty { MissingText }

        val NextDueText = RenewalDueProjection.NextDueDate(
            PaidForDate = RenewalItem.DueDate,
            FrequencyText = FrequencyText
        )
        BindingRef.tvRenewalNextDue.text = if (NextDueText.isEmpty()) {
            ""
        } else {
            ContextRef.getString(R.string.renewal_next_due_format, NextDueText)
        }
        BindingRef.tvRenewalNextDue.visibility =
            if (NextDueText.isEmpty()) View.GONE else View.VISIBLE

        BindStatus(BindingRef = BindingRef, StatusText = RenewalItem.Status)
    }

    private fun BindStatus(BindingRef: ItemRenewalRowBinding, StatusText: String) {
        if (StatusText.isEmpty()) {
            BindingRef.tvRenewalStatus.visibility = View.GONE
            return
        }

        val ContextRef = BindingRef.root.context
        BindingRef.tvRenewalStatus.visibility = View.VISIBLE
        BindingRef.tvRenewalStatus.text = StatusText

        val IsConcerning = IsConcerningStatus(StatusText = StatusText)
        BindingRef.tvRenewalStatus.setBackgroundResource(
            if (IsConcerning) R.drawable.bg_badge_lapsed else R.drawable.bg_badge_inforce
        )
        BindingRef.tvRenewalStatus.setTextColor(
            ContextCompat.getColor(
                ContextRef,
                if (IsConcerning) R.color.status_red_text else R.color.status_green_text
            )
        )
    }

    fun UpdateData(NewRenewals: List<FupPolicy>) {
        val DiffResult = DiffUtil.calculateDiff(
            RenewalDiffCallback(OldList = RenewalList, NewList = NewRenewals)
        )
        RenewalList = NewRenewals
        DiffResult.dispatchUpdatesTo(this)
    }

    companion object {
        private val CONCERNING_MARKERS = listOf(
            "grace", "late", "unpaid", "not paid", "lapsed", "pending"
        )

        fun IsConcerningStatus(StatusText: String): Boolean = CONCERNING_MARKERS.any { Marker ->
            StatusText.contains(Marker, ignoreCase = true)
        }
    }

    private class RenewalDiffCallback(
        private val OldList: List<FupPolicy>,
        private val NewList: List<FupPolicy>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = OldList.size

        override fun getNewListSize(): Int = NewList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val OldItem = OldList[oldItemPosition]
            val NewItem = NewList[newItemPosition]
            return OldItem.PolicyNumber == NewItem.PolicyNumber &&
                    OldItem.DueDate == NewItem.DueDate
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            OldList[oldItemPosition] == NewList[newItemPosition]
    }
}
