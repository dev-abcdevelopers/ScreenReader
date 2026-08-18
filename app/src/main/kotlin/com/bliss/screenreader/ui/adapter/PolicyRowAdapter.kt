@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.databinding.ItemPolicyRowBinding
import com.bliss.screenreader.utils.HapticFeedback

class PolicyRowAdapter(
    private var PolicyList: List<CustomerPolicy> = emptyList(),
    private val OnRowClick: (CustomerPolicy) -> Unit = {}
) : RecyclerView.Adapter<PolicyRowAdapter.PolicyViewHolder>() {

    class PolicyViewHolder(val BindingRef: ItemPolicyRowBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PolicyViewHolder {
        val BindingObj = ItemPolicyRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PolicyViewHolder(BindingRef = BindingObj)
    }

    override fun getItemCount(): Int = PolicyList.size

    override fun onBindViewHolder(holder: PolicyViewHolder, position: Int) {
        val PolicyItem = PolicyList[position]
        val ContextRef = holder.BindingRef.root.context
        val BindingRef = holder.BindingRef

        BindingRef.tvPolicyNumber.text = PolicyItem.PolicyNumber.ifEmpty {
            ContextRef.getString(R.string.detail_missing)
        }
        BindingRef.tvHolderName.text = PolicyItem.HolderName.ifEmpty {
            ContextRef.getString(R.string.status_unknown)
        }

        val PlanText = if (PolicyItem.PlanCode.isNotEmpty()) {
            "${PolicyItem.PlanCode} — ${PolicyItem.PlanName}"
        } else {
            PolicyItem.PlanName
        }
        BindingRef.tvPlanName.text = PlanText
        BindingRef.tvPlanName.visibility = if (PlanText.isBlank()) View.GONE else View.VISIBLE

        BindingRef.tvPremium.text = PolicyItem.PremiumAmount.ifEmpty {
            ContextRef.getString(R.string.detail_missing)
        }
        BindingRef.tvDueDate.text = PolicyItem.RenewalDueDate
        BindingRef.tvDueDate.visibility =
            if (PolicyItem.RenewalDueDate.isBlank()) View.GONE else View.VISIBLE

        BindStatus(BindingRef = BindingRef, PolicyItem = PolicyItem)

        BindingRef.rowRoot.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnRowClick(PolicyItem)
        }
    }

    private fun BindStatus(BindingRef: ItemPolicyRowBinding, PolicyItem: CustomerPolicy) {
        val ContextRef = BindingRef.root.context
        val StatusText = PolicyItem.NormalizedStatus

        if (StatusText.isEmpty()) {
            BindingRef.tvStatus.visibility = View.GONE
            return
        }

        BindingRef.tvStatus.visibility = View.VISIBLE
        BindingRef.tvStatus.text = StatusText

        val IsLapsed = StatusText.equals("Lapsed", ignoreCase = true)
        BindingRef.tvStatus.setBackgroundResource(
            if (IsLapsed) R.drawable.bg_badge_lapsed else R.drawable.bg_badge_inforce
        )
        BindingRef.tvStatus.setTextColor(
            ContextCompat.getColor(
                ContextRef,
                if (IsLapsed) R.color.status_red_text else R.color.status_green_text
            )
        )
    }

    fun UpdateData(NewPolicies: List<CustomerPolicy>) {
        val DiffResult = DiffUtil.calculateDiff(
            PolicyDiffCallback(OldList = PolicyList, NewList = NewPolicies)
        )
        PolicyList = NewPolicies
        DiffResult.dispatchUpdatesTo(this)
    }

    private class PolicyDiffCallback(
        private val OldList: List<CustomerPolicy>,
        private val NewList: List<CustomerPolicy>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = OldList.size

        override fun getNewListSize(): Int = NewList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            OldList[oldItemPosition].PolicyNumber == NewList[newItemPosition].PolicyNumber

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            OldList[oldItemPosition] == NewList[newItemPosition]
    }
}
