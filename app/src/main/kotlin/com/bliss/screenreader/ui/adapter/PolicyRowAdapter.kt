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
    private val OnRowClick: (CustomerPolicy) -> Unit = {},
    private val OnDeleteClick: (CustomerPolicy) -> Unit = {},
    private val OnSelectionChanged: () -> Unit = {}
) : RecyclerView.Adapter<PolicyRowAdapter.PolicyViewHolder>() {

    private var OpenRowNumber: String = ""
    private val SelectedNumbers = linkedSetOf<String>()

    var IsSelectionMode: Boolean = false
        private set

    class PolicyViewHolder(val BindingRef: ItemPolicyRowBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PolicyViewHolder {
        val BindingObj = ItemPolicyRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PolicyViewHolder(BindingRef = BindingObj)
    }

    override fun getItemCount(): Int = PolicyList.size

    fun PolicyAt(PositionVal: Int): CustomerPolicy? = PolicyList.getOrNull(PositionVal)

    fun OpenRow(PolicyNumber: String) {
        if (IsSelectionMode) return
        val PreviousNumber = OpenRowNumber
        OpenRowNumber = PolicyNumber
        NotifyRowChanged(PolicyNumber = PreviousNumber)
        NotifyRowChanged(PolicyNumber = PolicyNumber)
    }

    fun CloseOpenRow() {
        if (OpenRowNumber.isEmpty()) return
        val PreviousNumber = OpenRowNumber
        OpenRowNumber = ""
        NotifyRowChanged(PolicyNumber = PreviousNumber)
    }

    fun IsRowOpen(PolicyNumber: String): Boolean = OpenRowNumber == PolicyNumber

    private fun NotifyRowChanged(PolicyNumber: String) {
        if (PolicyNumber.isEmpty()) return
        val IndexVal = PolicyList.indexOfFirst { PolicyItem ->
            PolicyItem.PolicyNumber == PolicyNumber
        }
        if (IndexVal >= 0) notifyItemChanged(IndexVal)
    }

    fun SelectedPolicyNumbers(): List<String> = SelectedNumbers.toList()

    fun SelectedPolicies(): List<CustomerPolicy> = PolicyList.filter { PolicyItem ->
        SelectedNumbers.contains(PolicyItem.PolicyNumber)
    }

    fun SelectedCount(): Int = SelectedNumbers.size

    fun StartSelection(PolicyNumber: String) {
        CloseOpenRow()
        IsSelectionMode = true
        SelectedNumbers.add(PolicyNumber)
        notifyDataSetChanged()
        OnSelectionChanged()
    }

    fun ToggleSelection(PolicyNumber: String) {
        if (!SelectedNumbers.remove(PolicyNumber)) SelectedNumbers.add(PolicyNumber)
        if (SelectedNumbers.isEmpty()) {
            EndSelection()
            return
        }
        NotifyRowChanged(PolicyNumber = PolicyNumber)
        OnSelectionChanged()
    }

    fun SelectAllVisible() {
        SelectedNumbers.addAll(
            PolicyList
                .map { PolicyItem -> PolicyItem.PolicyNumber }
                .filter { NumberText -> NumberText.isNotEmpty() }
        )
        notifyDataSetChanged()
        OnSelectionChanged()
    }

    fun EndSelection() {
        if (!IsSelectionMode && SelectedNumbers.isEmpty()) return
        IsSelectionMode = false
        SelectedNumbers.clear()
        notifyDataSetChanged()
        OnSelectionChanged()
    }

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

        val IsSelected = SelectedNumbers.contains(PolicyItem.PolicyNumber)
        BindingRef.cbPolicySelect.visibility =
            if (IsSelectionMode) View.VISIBLE else View.GONE
        BindingRef.cbPolicySelect.isChecked = IsSelected
        BindingRef.rowRoot.isActivated = IsSelected

        val RevealPx = if (!IsSelectionMode && IsRowOpen(PolicyNumber = PolicyItem.PolicyNumber)) {
            BindingRef.root.resources.getDimensionPixelSize(R.dimen.policy_reveal_width).toFloat()
        } else {
            0f
        }
        BindingRef.rowRoot.translationX = RevealPx

        BindingRef.btnPolicyDelete.setOnClickListener { ViewRef ->
            HapticFeedback.Reject(ViewRef = ViewRef)
            OnDeleteClick(PolicyItem)
        }

        BindingRef.rowRoot.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            if (IsSelectionMode) {
                ToggleSelection(PolicyNumber = PolicyItem.PolicyNumber)
                return@setOnClickListener
            }
            if (OpenRowNumber.isNotEmpty()) {
                CloseOpenRow()
                return@setOnClickListener
            }
            OnRowClick(PolicyItem)
        }

        BindingRef.rowRoot.setOnLongClickListener { ViewRef ->
            if (PolicyItem.PolicyNumber.isEmpty()) return@setOnLongClickListener false
            HapticFeedback.Confirm(ViewRef = ViewRef)
            if (IsSelectionMode) {
                ToggleSelection(PolicyNumber = PolicyItem.PolicyNumber)
            } else {
                StartSelection(PolicyNumber = PolicyItem.PolicyNumber)
            }
            true
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
        val LiveNumbers = NewPolicies.map { PolicyItem -> PolicyItem.PolicyNumber }.toSet()
        if (!LiveNumbers.contains(OpenRowNumber)) OpenRowNumber = ""
        val HadSelection = SelectedNumbers.isNotEmpty()
        SelectedNumbers.retainAll(LiveNumbers)
        if (SelectedNumbers.isEmpty()) IsSelectionMode = false
        DiffResult.dispatchUpdatesTo(this)
        if (HadSelection) OnSelectionChanged()
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
