@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

data class ContactValue(
    val Value: String,
    val RelatedPolicies: List<String> = emptyList(),
    val IsDefault: Boolean = false,
    val IsPartial: Boolean = false
)

data class CustomerProfile(
    val CustomerName: String = "",
    val Dob: String = "",
    val Gender: String = "",
    val Education: String = "",
    val Occupation: String = "",
    val MaritalStatus: String = "",
    val AnnualIncome: String = "",
    val Mobiles: List<ContactValue> = emptyList(),
    val Emails: List<ContactValue> = emptyList(),
    val Addresses: List<ContactValue> = emptyList(),
    val PolicyNumbers: List<String> = emptyList(),
    val CapturedAt: Long = System.currentTimeMillis()
) {
    val FieldCount: Int
        get() = listOf(Dob, Gender, Education, Occupation, MaritalStatus, AnnualIncome)
            .count { FieldValue -> FieldValue.isNotEmpty() } +
                listOf(Mobiles, Emails, Addresses).count { ListValue -> ListValue.isNotEmpty() }

    val HasAnyData: Boolean get() = FieldCount > 0

    fun ValueFor(PolicyNumber: String, Values: List<ContactValue>): String {
        if (Values.isEmpty()) return ""
        if (PolicyNumber.isNotEmpty()) {
            val RelatedMatch = Values.firstOrNull { ContactItem ->
                ContactItem.RelatedPolicies.any { RelatedNumber -> RelatedNumber == PolicyNumber }
            }
            if (RelatedMatch != null) return RelatedMatch.Value
        }
        val DefaultMatch = Values.firstOrNull { ContactItem -> ContactItem.IsDefault }
        return (DefaultMatch ?: Values.first()).Value
    }

    fun ToPolicyPatch(PolicyNumber: String): CustomerPolicy {
        return CustomerPolicy(
            HolderName = "",
            PolicyNumber = PolicyNumber,
            MobileNumber = ValueFor(PolicyNumber = PolicyNumber, Values = Mobiles),
            Email = ValueFor(PolicyNumber = PolicyNumber, Values = Emails),
            Address = ValueFor(PolicyNumber = PolicyNumber, Values = Addresses),
            Dob = Dob,
            Gender = Gender,
            Education = Education,
            Occupation = Occupation,
            MaritalStatus = MaritalStatus,
            AnnualIncome = AnnualIncome
        )
    }
}

data class SessionGap(
    val PolicyNumber: String,
    val CustomerName: String,
    val SeenAt: Long
)
