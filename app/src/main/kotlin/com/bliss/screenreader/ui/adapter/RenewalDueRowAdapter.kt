@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.RenewalDueKind
import com.bliss.screenreader.data.model.RenewalDuePolicy
import com.bliss.screenreader.data.parser.FupDataParser
import com.bliss.screenreader.databinding.ItemRenewalDueGroupBinding
import com.bliss.screenreader.databinding.ItemRenewalDueRowBinding
import com.bliss.screenreader.utils.HapticFeedback
import java.util.Locale

class RenewalDueRowAdapter(
    private var DueList: List<RenewalDuePolicy> = emptyList()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class RowItem {
        data class Header(
            val HolderName: String,
            val PolicyCount: Int,
            val IsExpanded: Boolean,
            val CanExpand: Boolean
        ) : RowItem()

        data class Card(val DueItem: RenewalDuePolicy) : RowItem()
    }

    private class HeaderHolder(val BindingRef: ItemRenewalDueGroupBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    private class CardHolder(val BindingRef: ItemRenewalDueRowBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    private val ExpandedNames = linkedSetOf<String>()
    private var VisibleRows: List<RowItem> = emptyList()

    init {
        RebuildRows()
    }

    private fun GroupKey(NameText: String): String =
        NameText.trim().uppercase(Locale.ROOT)

    private fun GroupedByHolder(): List<Pair<String, List<RenewalDuePolicy>>> {
        val GroupMap = linkedMapOf<String, MutableList<RenewalDuePolicy>>()
        for (DueItem in DueList) {
            val KeyText = GroupKey(NameText = DueItem.HolderName)
            GroupMap.getOrPut(KeyText) { mutableListOf() }.add(DueItem)
        }
        return GroupMap.map { EntryRef ->
            val DisplayName = EntryRef.value
                .firstOrNull { DueItem -> DueItem.HolderName.isNotBlank() }
                ?.HolderName
                .orEmpty()
            DisplayName to EntryRef.value.toList()
        }
    }

    private fun RebuildRows() {
        val ResultRows = mutableListOf<RowItem>()
        for ((DisplayName, PolicyList) in GroupedByHolder()) {
            val KeyText = GroupKey(NameText = DisplayName)
            val IsExpanded = ExpandedNames.contains(KeyText)
            val CanExpand = PolicyList.size > 1
            ResultRows.add(
                RowItem.Header(
                    HolderName = DisplayName,
                    PolicyCount = PolicyList.size,
                    IsExpanded = IsExpanded,
                    CanExpand = CanExpand
                )
            )
            val ShownPolicies = if (IsExpanded || !CanExpand) PolicyList else PolicyList.take(1)
            for (DueItem in ShownPolicies) ResultRows.add(RowItem.Card(DueItem = DueItem))
        }
        VisibleRows = ResultRows
    }

    override fun getItemCount(): Int = VisibleRows.size

    override fun getItemViewType(position: Int): Int = when (VisibleRows[position]) {
        is RowItem.Header -> VIEW_TYPE_HEADER
        is RowItem.Card -> VIEW_TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val InflaterRef = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderHolder(
                BindingRef = ItemRenewalDueGroupBinding.inflate(InflaterRef, parent, false)
            )
        } else {
            CardHolder(
                BindingRef = ItemRenewalDueRowBinding.inflate(InflaterRef, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val RowRef = VisibleRows[position]) {
            is RowItem.Header -> BindHeader(
                HolderRef = holder as HeaderHolder,
                RowRef = RowRef
            )

            is RowItem.Card -> BindCard(
                HolderRef = holder as CardHolder,
                DueItem = RowRef.DueItem
            )
        }
    }

    private fun BindHeader(HolderRef: HeaderHolder, RowRef: RowItem.Header) {
        val BindingRef = HolderRef.BindingRef
        val ContextRef = BindingRef.root.context

        BindingRef.tvDueGroupName.text = RowRef.HolderName.ifBlank {
            ContextRef.getString(R.string.status_unknown)
        }
        BindingRef.tvDueGroupCount.text = ContextRef.getString(
            R.string.due_group_policy_count,
            RowRef.PolicyCount
        )

        if (!RowRef.CanExpand) {
            BindingRef.tvDueGroupViewAll.visibility = View.GONE
            BindingRef.tvDueGroupViewAll.setOnClickListener(null)
            return
        }

        BindingRef.tvDueGroupViewAll.visibility = View.VISIBLE
        BindingRef.tvDueGroupViewAll.setText(
            if (RowRef.IsExpanded) R.string.due_group_show_less else R.string.due_group_view_all
        )
        BindingRef.tvDueGroupViewAll.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            ToggleGroup(HolderName = RowRef.HolderName)
        }
    }

    private fun ToggleGroup(HolderName: String) {
        val KeyText = GroupKey(NameText = HolderName)
        if (!ExpandedNames.remove(KeyText)) ExpandedNames.add(KeyText)
        RebuildRows()
        notifyDataSetChanged()
    }

    private fun BindCard(HolderRef: CardHolder, DueItem: RenewalDuePolicy) {
        val BindingRef = HolderRef.BindingRef
        val ContextRef = BindingRef.root.context
        val MissingText = ContextRef.getString(R.string.detail_missing)

        val IsGrace = DueItem.Kind == RenewalDueKind.GRACE_EXPIRY
        val UrgencyText = DueItem.UrgencyText
        BindingRef.dueUrgencyRow.visibility =
            if (UrgencyText.isEmpty()) View.GONE else View.VISIBLE
        BindingRef.tvDueUrgency.text = UrgencyText
        val UrgencyColour = ContextCompat.getColor(ContextRef, R.color.status_red_text)
        BindingRef.tvDueUrgency.setTextColor(UrgencyColour)
        BindingRef.imgDueUrgency.imageTintList = ColorStateList.valueOf(UrgencyColour)

        BindingRef.tvDuePolicyNumber.text = DueItem.PolicyNumber.ifEmpty { MissingText }

        val PlanText = if (DueItem.PlanCode.isNotEmpty()) {
            "${DueItem.PlanCode} — ${DueItem.PlanName}"
        } else {
            DueItem.PlanName
        }
        BindingRef.tvDuePlanName.text = PlanText
        BindingRef.tvDuePlanName.visibility =
            if (PlanText.isBlank()) View.GONE else View.VISIBLE

        BindingRef.tvDueHolderName.text = DueItem.HolderName.ifEmpty {
            ContextRef.getString(R.string.status_unknown)
        }

        BindingRef.tvDueAutoPay.text = DueItem.AutoPay.ifEmpty { MissingText }
        BindingRef.tvDueDateLabel.text = DueItem.DateLabel.ifEmpty {
            ContextRef.getString(R.string.due_cell_date)
        }
        BindingRef.tvDueDateValue.text = DueItem.DateValue.ifEmpty { MissingText }
        BindingRef.tvDueDateValue.setTextColor(
            ContextCompat.getColor(
                ContextRef,
                if (IsGrace) R.color.status_red_text else R.color.text_primary
            )
        )

        val AmountText = FupDataParser.AmountOf(PremiumText = DueItem.PremiumAmount)
        val FrequencyText = DueItem.PremiumFrequency.orEmpty().ifEmpty {
            FupDataParser.FrequencyOf(PremiumText = DueItem.PremiumAmount)
        }
        BindingRef.tvDuePremium.text = when {
            AmountText.isEmpty() -> MissingText
            FrequencyText.isEmpty() -> AmountText
            else -> ContextRef.getString(
                R.string.renewal_premium_format,
                AmountText,
                FrequencyText
            )
        }
    }

    fun UpdateData(NewList: List<RenewalDuePolicy>) {
        DueList = NewList
        val LiveKeys = NewList.map { DueItem -> GroupKey(NameText = DueItem.HolderName) }.toSet()
        ExpandedNames.retainAll(LiveKeys)
        RebuildRows()
        notifyDataSetChanged()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_CARD = 2
    }
}
