@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.utils

import android.content.Context
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.PolicyCompleteness

object CompletenessLabels {

    fun From(ContextRef: Context): PolicyCompleteness.LabelSet {
        return PolicyCompleteness.LabelSet(
            CardTitle = ContextRef.getString(R.string.detail_group_card),
            PolicyDetailsTitle = ContextRef.getString(R.string.detail_group_policy_details),
            CommissionsTitle = ContextRef.getString(R.string.detail_group_commissions),
            KeyDatesTitle = ContextRef.getString(R.string.detail_group_key_dates),
            CustomerTitle = ContextRef.getString(R.string.detail_group_customer),
            PlanCode = ContextRef.getString(R.string.detail_plan_code),
            PlanName = ContextRef.getString(R.string.detail_plan_name),
            Status = ContextRef.getString(R.string.detail_status),
            Premium = ContextRef.getString(R.string.detail_premium),
            PremiumFrequency = ContextRef.getString(R.string.detail_premium_frequency),
            AutoPay = ContextRef.getString(R.string.detail_auto_pay),
            RenewalType = ContextRef.getString(R.string.detail_renewal_type),
            RenewalDue = ContextRef.getString(R.string.detail_renewal_due),
            SumAssured = ContextRef.getString(R.string.detail_sum_assured),
            TermPpt = ContextRef.getString(R.string.detail_term_ppt),
            CommissionType = ContextRef.getString(R.string.detail_commission_type),
            CommissionPaid = ContextRef.getString(R.string.detail_commission_paid),
            BonusCommission = ContextRef.getString(R.string.detail_bonus_commission),
            CommissionPaymentDate = ContextRef.getString(R.string.detail_commission_payment_date),
            CommissionPremiumDate = ContextRef.getString(R.string.detail_commission_premium_date),
            Commenced = ContextRef.getString(R.string.detail_commenced),
            PremiumsEnd = ContextRef.getString(R.string.detail_premiums_end),
            Matures = ContextRef.getString(R.string.detail_matures),
            Mobile = ContextRef.getString(R.string.detail_mobile),
            Dob = ContextRef.getString(R.string.detail_dob),
            Address = ContextRef.getString(R.string.detail_address),
            Email = ContextRef.getString(R.string.detail_email),
            Gender = ContextRef.getString(R.string.detail_gender),
            Education = ContextRef.getString(R.string.detail_education),
            Occupation = ContextRef.getString(R.string.detail_occupation),
            MaritalStatus = ContextRef.getString(R.string.detail_marital_status),
            AnnualIncome = ContextRef.getString(R.string.detail_annual_income),
            FlagKyc = ContextRef.getString(R.string.detail_flag_kyc),
            FlagNeft = ContextRef.getString(R.string.detail_flag_neft),
            FlagNominee = ContextRef.getString(R.string.detail_flag_nominee),
            FlagMobile = ContextRef.getString(R.string.detail_flag_mobile),
            FlagAddress = ContextRef.getString(R.string.detail_flag_address)
        )
    }
}
