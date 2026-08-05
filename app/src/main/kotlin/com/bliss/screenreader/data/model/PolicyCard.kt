@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

data class PolicyCard(
    val PolicyNumber: String,
    val HolderName: String,
    val PlanName: String = "",
    val Status: String = "",
    val RenewalDueDate: String = "",
    val PremiumAmount: String = "",
    val LastUpdated: Long = System.currentTimeMillis()
)
