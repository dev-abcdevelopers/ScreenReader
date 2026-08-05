@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.databinding.ItemCustomerPolicyBinding

class CustomerPolicyAdapter(
    private var PolicyList: List<CustomerPolicy> = emptyList()
) : RecyclerView.Adapter<CustomerPolicyAdapter.ViewHolder>() {

    fun UpdateData(NewPolicies: List<CustomerPolicy>) {
        this.PolicyList = NewPolicies
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val ViewBindingObj = ItemCustomerPolicyBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(BindingRef = ViewBindingObj)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.Bind(PolicyItem = PolicyList[position])
    }

    override fun getItemCount(): Int = PolicyList.size

    class ViewHolder(private val BindingRef: ItemCustomerPolicyBinding) :
        RecyclerView.ViewHolder(BindingRef.root) {

        fun Bind(PolicyItem: CustomerPolicy) {
            BindingRef.tvPolicyNumber.text = PolicyItem.PolicyNumber.ifEmpty { "N/A" }
            BindingRef.tvHolderName.text = PolicyItem.HolderName.ifEmpty { "Customer Record" }
            BindingRef.tvPlanName.text = if (PolicyItem.PlanCode.isNotEmpty()) "${PolicyItem.PlanCode} - ${PolicyItem.PlanName}" else PolicyItem.PlanName.ifEmpty { "Standard Policy" }
            BindingRef.tvPremium.text = PolicyItem.PremiumAmount.ifEmpty { "-" }
            BindingRef.tvSumAssured.text = PolicyItem.SumAssured.ifEmpty { "-" }
            BindingRef.tvDueDate.text = PolicyItem.RenewalDueDate.ifEmpty { "-" }

            val IsLapsedVal = PolicyItem.NormalizedStatus.equals("Lapsed", ignoreCase = true)
            BindingRef.tvStatus.text = PolicyItem.NormalizedStatus.ifEmpty { "Inforce" }
            if (IsLapsedVal) {
                BindingRef.tvStatus.setBackgroundResource(R.drawable.bg_badge_lapsed)
                BindingRef.tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_red_text))
            } else {
                BindingRef.tvStatus.setBackgroundResource(R.drawable.bg_badge_inforce)
                BindingRef.tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_green_text))
            }
        }
    }
}
