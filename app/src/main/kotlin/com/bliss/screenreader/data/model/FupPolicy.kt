@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.model

data class FupPolicy(
    val PolicyNumber: String,
    val PlanName: String = "",
    val HolderName: String = "",
    val PremiumAmount: String = "",
    val DueDate: String = "",
    val PaymentDate: String = "",
    val ModeOfPayment: String = "",
    val Status: String = ""
)
