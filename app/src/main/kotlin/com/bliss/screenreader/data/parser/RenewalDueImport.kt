@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.DueDateOutcome
import com.bliss.screenreader.data.model.DueDateSkip
import com.bliss.screenreader.data.model.DueDateSkipReason
import com.bliss.screenreader.data.model.DueDateUpdate
import com.bliss.screenreader.data.model.RecordFieldChange
import com.bliss.screenreader.data.model.RenewalDueKind
import com.bliss.screenreader.data.model.RenewalDuePolicy
import java.time.LocalDate

object RenewalDueImport {

    const val FIELD_NAME = RenewalDueProjection.FIELD_NAME

    fun BestByPolicy(DueRecords: List<RenewalDuePolicy>): Map<String, RenewalDuePolicy> {
        val ResultMap = linkedMapOf<String, RenewalDuePolicy>()
        for (RecordItem in DueRecords) {
            val KeyText = RecordItem.PolicyNumber.trim()
            if (KeyText.isEmpty()) continue
            val ExistingItem = ResultMap[KeyText]
            if (ExistingItem == null || IsBetterRecord(
                    CandidateItem = RecordItem,
                    ExistingItem = ExistingItem
                )
            ) {
                ResultMap[KeyText] = RecordItem
            }
        }
        return ResultMap
    }

    fun Apply(
        Policies: List<CustomerPolicy>,
        DueRecords: List<RenewalDuePolicy>
    ): DueDateOutcome {
        if (Policies.isEmpty() || DueRecords.isEmpty()) {
            return DueDateOutcome(Policies = Policies, Changes = emptyList())
        }

        val BestMap = BestByPolicy(DueRecords = DueRecords)
        val ChangeList = mutableListOf<RecordFieldChange>()
        val UpdateList = mutableListOf<DueDateUpdate>()
        val SkipList = mutableListOf<DueDateSkip>()
        var MatchedCount = 0

        val UpdatedPolicies = Policies.map { PolicyItem ->
            val KeyText = PolicyItem.PolicyNumber.trim()
            val DueItem = BestMap[KeyText] ?: return@map PolicyItem
            MatchedCount++

            if (DueItem.Kind == RenewalDueKind.GRACE_EXPIRY) {
                SkipList.add(
                    SkipFor(
                        PolicyItem = PolicyItem,
                        ReasonVal = DueDateSkipReason.GRACE_DATE
                    )
                )
                return@map PolicyItem
            }

            val NextDueObj = RenewalDueProjection.ParseDate(RawText = DueItem.DueDateOrBlank)
            if (NextDueObj == null) {
                SkipList.add(
                    SkipFor(
                        PolicyItem = PolicyItem,
                        ReasonVal = DueDateSkipReason.NO_DUE_DATE
                    )
                )
                return@map PolicyItem
            }

            val ExistingObj = RenewalDueProjection.ParseDate(RawText = PolicyItem.RenewalDueDate)
            if (ExistingObj != null && !NextDueObj.isAfter(ExistingObj)) {
                SkipList.add(
                    SkipFor(
                        PolicyItem = PolicyItem,
                        ReasonVal = DueDateSkipReason.ALREADY_CURRENT
                    )
                )
                return@map PolicyItem
            }

            val NextDueText = RenewalDueProjection.FormatDate(DateObj = NextDueObj)
            ChangeList.add(
                RecordFieldChange(
                    RecordKey = KeyText,
                    FieldName = FIELD_NAME,
                    OldValue = PolicyItem.RenewalDueDate,
                    NewValue = NextDueText
                )
            )
            UpdateList.add(
                DueDateUpdate(
                    PolicyNumber = KeyText,
                    HolderName = PolicyItem.HolderName.ifEmpty { DueItem.HolderName },
                    PlanCode = PolicyItem.PlanCode.ifEmpty { DueItem.PlanCode },
                    OldDate = PolicyItem.RenewalDueDate,
                    NewDate = NextDueText,
                    PaidForDate = "",
                    Frequency = FrequencyFor(DueItem = DueItem, PolicyItem = PolicyItem)
                )
            )
            PolicyItem.copy(RenewalDueDate = NextDueText)
        }

        return DueDateOutcome(
            Policies = UpdatedPolicies,
            Changes = ChangeList,
            Updates = UpdateList,
            Skips = SkipList,
            MatchedCount = MatchedCount,
            AnchoredCount = UpdateList.size,
            UpdatedCount = UpdateList.size,
            UnchangedCount = SkipList.count { SkipItem ->
                SkipItem.Reason == DueDateSkipReason.ALREADY_CURRENT
            },
            SkippedCount = SkipList.count { SkipItem ->
                SkipItem.Reason != DueDateSkipReason.ALREADY_CURRENT
            }
        )
    }

    private fun SkipFor(
        PolicyItem: CustomerPolicy,
        ReasonVal: DueDateSkipReason
    ): DueDateSkip {
        return DueDateSkip(
            PolicyNumber = PolicyItem.PolicyNumber.trim(),
            HolderName = PolicyItem.HolderName,
            PlanCode = PolicyItem.PlanCode,
            CurrentDate = PolicyItem.RenewalDueDate,
            Reason = ReasonVal
        )
    }

    private fun FrequencyFor(DueItem: RenewalDuePolicy, PolicyItem: CustomerPolicy): String {
        val RowFrequency = DueItem.PremiumFrequency.orEmpty()
        if (RowFrequency.isNotEmpty()) return RowFrequency
        return PolicyItem.PremiumFrequency
    }

    private fun IsBetterRecord(
        CandidateItem: RenewalDuePolicy,
        ExistingItem: RenewalDuePolicy
    ): Boolean {
        val CandidateIsDue = CandidateItem.Kind == RenewalDueKind.RENEWAL_DUE
        val ExistingIsDue = ExistingItem.Kind == RenewalDueKind.RENEWAL_DUE
        if (CandidateIsDue != ExistingIsDue) return CandidateIsDue

        val CandidateDate = DateOf(RecordItem = CandidateItem)
        val ExistingDate = DateOf(RecordItem = ExistingItem)
        if (CandidateDate != null && ExistingDate != null) {
            return CandidateDate.isAfter(ExistingDate)
        }
        return CandidateDate != null && ExistingDate == null
    }

    private fun DateOf(RecordItem: RenewalDuePolicy): LocalDate? {
        return RenewalDueProjection.ParseDate(RawText = RecordItem.DateValue)
    }
}
