@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

object PolicyDeletion {

    fun NormaliseNumbers(NumberList: Collection<String>): Set<String> {
        return NumberList
            .map { NumberText -> NumberText.trim() }
            .filter { NumberText -> NumberText.isNotEmpty() }
            .toSet()
    }

    fun RemainingPolicies(
        PolicyList: List<CustomerPolicy>,
        NumberList: Collection<String>
    ): List<CustomerPolicy> {
        val TargetSet = NormaliseNumbers(NumberList = NumberList)
        if (TargetSet.isEmpty()) return PolicyList
        return PolicyList.filterNot { PolicyItem ->
            TargetSet.contains(PolicyItem.PolicyNumber.trim())
        }
    }

    fun RemovedCount(
        PolicyList: List<CustomerPolicy>,
        NumberList: Collection<String>
    ): Int {
        return PolicyList.size - RemainingPolicies(
            PolicyList = PolicyList,
            NumberList = NumberList
        ).size
    }

    fun RemainingChanges(
        ChangeList: List<RecordFieldChange>,
        NumberList: Collection<String>
    ): List<RecordFieldChange> {
        val TargetSet = NormaliseNumbers(NumberList = NumberList)
        if (TargetSet.isEmpty()) return ChangeList
        return ChangeList.filterNot { ChangeItem ->
            TargetSet.contains(ChangeItem.RecordKey.trim())
        }
    }

    fun RemainingGaps(
        GapList: List<SessionGap>,
        NumberList: Collection<String>
    ): List<SessionGap> {
        val TargetSet = NormaliseNumbers(NumberList = NumberList)
        if (TargetSet.isEmpty()) return GapList
        return GapList.filterNot { GapItem ->
            TargetSet.contains(GapItem.PolicyNumber.trim())
        }
    }

    fun RemainingVisitedCustomers(
        VisitedNames: List<String>,
        RemainingPolicyList: List<CustomerPolicy>
    ): List<String> {
        val LiveNames = RemainingPolicyList
            .map { PolicyItem -> PolicyItem.HolderName.trim().uppercase() }
            .filter { NameText -> NameText.isNotEmpty() }
            .toSet()
        return VisitedNames.filter { NameText ->
            LiveNames.contains(NameText.trim().uppercase())
        }
    }
}
