@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.FupPolicy
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

        BindingRef.tvRenewalPolicyNumber.text = RenewalItem.PolicyNumber.ifEmpty {
            ContextRef.getString(R.string.detail_missing)
        }
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

        BindingRef.tvRenewalPremium.text = RenewalItem.PremiumAmount.ifEmpty {
            ContextRef.getString(R.string.detail_missing)
        }

        BindingRef.tvRenewalDueDate.text = if (RenewalItem.DueDate.isBlank()) {
            ""
        } else {
            ContextRef.getString(R.string.renewal_due_format, RenewalItem.DueDate)
        }
        BindingRef.tvRenewalDueDate.visibility =
            if (RenewalItem.DueDate.isBlank()) View.GONE else View.VISIBLE

        BindingRef.tvRenewalPaymentDate.text = if (RenewalItem.PaymentDate.isBlank()) {
            ""
        } else {
            ContextRef.getString(R.string.renewal_paid_format, RenewalItem.PaymentDate)
        }
        BindingRef.tvRenewalPaymentDate.visibility =
            if (RenewalItem.PaymentDate.isBlank()) View.GONE else View.VISIBLE

        BindingRef.tvRenewalMode.text = RenewalItem.ModeOfPayment
        BindingRef.tvRenewalMode.visibility =
            if (RenewalItem.ModeOfPayment.isBlank()) View.GONE else View.VISIBLE

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

        val IsConcerning = listOf("grace", "late", "unpaid", "not paid", "lapsed", "pending")
            .any { Marker -> StatusText.contains(Marker, ignoreCase = true) }
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

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateData(NewRenewals: List<FupPolicy>) {
        RenewalList = NewRenewals
        notifyDataSetChanged()
    }
}
