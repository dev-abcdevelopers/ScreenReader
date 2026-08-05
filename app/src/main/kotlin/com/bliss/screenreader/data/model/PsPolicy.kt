@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.model

data class PsPolicy(
    val PolicyNumber: String,
    val HolderName: String = "",
    val PlanTermPPT: String = "",
    val Doc: String = "",
    val PremiumAmount: String = "",
    val Fup: String = "",
    val SumAssured: String = "",
    val MaturityDate: String = "",
    val Phone: String = "",
    val Dob: String = "",
    val Age: String = "",
    val TotalPaid: String = "",
    val Status: String = "",
    val AgencyCode: String = "",
    val NeftStatus: String = "",
    val Address: String = "",
    val CapturedAt: Long = System.currentTimeMillis()
)
