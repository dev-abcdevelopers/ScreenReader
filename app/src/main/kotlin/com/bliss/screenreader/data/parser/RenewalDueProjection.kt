@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.RecordFieldChange
import java.time.LocalDate
import java.util.Locale

object RenewalDueProjection {

    const val FIELD_NAME = "Renewal due date"

    data class Outcome(
        val Policies: List<CustomerPolicy>,
        val Changes: List<RecordFieldChange>,
        val MatchedCount: Int = 0,
        val UpdatedCount: Int = 0,
        val UnchangedCount: Int = 0,
        val SkippedCount: Int = 0
    )

    private val MONTH_LABELS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private val MONTH_NUMBERS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val DAY_MONTH_YEAR_REGEX = Regex("^(\\d{1,2})[\\s-]+([A-Za-z]{3,})[\\s-]+(\\d{4})$")
    private val NUMERIC_DMY_REGEX = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$")
    private val ISO_REGEX = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")

    fun MonthsForFrequency(FrequencyText: String): Int {
        val KeyText = FrequencyText
            .lowercase(Locale.ROOT)
            .replace("-", "")
            .replace(" ", "")
        if (KeyText.isEmpty()) return 0
        if (KeyText.contains("single") || KeyText.contains("onetime")) return 0
        if (KeyText.contains("half") || KeyText.contains("semi") || KeyText == "hly") return 6
        if (KeyText.contains("quarter") || KeyText == "qly" || KeyText == "qtr") return 3
        if (KeyText.contains("month") || KeyText == "mly") return 1
        if (KeyText.contains("year") || KeyText.contains("annual") || KeyText == "yly") return 12
        return 0
    }

    fun ParseDate(RawText: String): LocalDate? {
        val TrimmedText = RawText.trim()
        if (TrimmedText.isEmpty()) return null

        ISO_REGEX.find(TrimmedText)?.let { MatchResult ->
            return BuildDate(
                YearValue = MatchResult.groupValues[1].toInt(),
                MonthValue = MatchResult.groupValues[2].toInt(),
                DayValue = MatchResult.groupValues[3].toInt()
            )
        }

        DAY_MONTH_YEAR_REGEX.find(TrimmedText)?.let { MatchResult ->
            val MonthValue = MONTH_NUMBERS[
                MatchResult.groupValues[2].take(3).lowercase(Locale.ROOT)
            ] ?: return null
            return BuildDate(
                YearValue = MatchResult.groupValues[3].toInt(),
                MonthValue = MonthValue,
                DayValue = MatchResult.groupValues[1].toInt()
            )
        }

        NUMERIC_DMY_REGEX.find(TrimmedText)?.let { MatchResult ->
            return BuildDate(
                YearValue = MatchResult.groupValues[3].toInt(),
                MonthValue = MatchResult.groupValues[2].toInt(),
                DayValue = MatchResult.groupValues[1].toInt()
            )
        }

        return null
    }

    fun FormatDate(DateObj: LocalDate): String {
        val MonthLabel = MONTH_LABELS[DateObj.monthValue - 1]
        return String.format(
            Locale.ENGLISH,
            "%02d %s %d",
            DateObj.dayOfMonth,
            MonthLabel,
            DateObj.year
        )
    }

    fun NextDueDate(PaidForDate: String, FrequencyText: String): String {
        val MonthStep = MonthsForFrequency(FrequencyText = FrequencyText)
        if (MonthStep <= 0) return ""
        val PaidForObj = ParseDate(RawText = PaidForDate) ?: return ""
        return FormatDate(DateObj = PaidForObj.plusMonths(MonthStep.toLong()))
    }

    fun LatestByPolicy(Renewals: List<FupPolicy>): Map<String, FupPolicy> {
        val ResultMap = linkedMapOf<String, FupPolicy>()
        for (RenewalItem in Renewals) {
            val KeyText = RenewalItem.PolicyNumber.trim()
            if (KeyText.isEmpty()) continue
            val ExistingItem = ResultMap[KeyText]
            if (ExistingItem == null || IsNewerRenewal(
                    CandidateItem = RenewalItem,
                    ExistingItem = ExistingItem
                )
            ) {
                ResultMap[KeyText] = RenewalItem
            }
        }
        return ResultMap
    }

    fun Apply(
        Policies: List<CustomerPolicy>,
        Renewals: List<FupPolicy>
    ): Outcome {
        if (Policies.isEmpty() || Renewals.isEmpty()) {
            return Outcome(Policies = Policies, Changes = emptyList())
        }

        val LatestMap = LatestByPolicy(Renewals = Renewals)
        val ChangeList = mutableListOf<RecordFieldChange>()
        var MatchedCount = 0
        var UpdatedCount = 0
        var UnchangedCount = 0
        var SkippedCount = 0

        val UpdatedPolicies = Policies.map { PolicyItem ->
            val KeyText = PolicyItem.PolicyNumber.trim()
            val RenewalItem = LatestMap[KeyText] ?: return@map PolicyItem
            MatchedCount++

            val FrequencyText = RenewalItem.PremiumFrequency.ifEmpty {
                PolicyItem.PremiumFrequency
            }
            val NextDueText = NextDueDate(
                PaidForDate = RenewalItem.DueDate,
                FrequencyText = FrequencyText
            )
            val NextDueObj = ParseDate(RawText = NextDueText)
            if (NextDueObj == null) {
                SkippedCount++
                return@map PolicyItem
            }

            val ExistingObj = ParseDate(RawText = PolicyItem.RenewalDueDate)
            if (ExistingObj != null && !NextDueObj.isAfter(ExistingObj)) {
                UnchangedCount++
                return@map PolicyItem
            }

            UpdatedCount++
            ChangeList.add(
                RecordFieldChange(
                    RecordKey = KeyText,
                    FieldName = FIELD_NAME,
                    OldValue = PolicyItem.RenewalDueDate,
                    NewValue = NextDueText
                )
            )
            PolicyItem.copy(RenewalDueDate = NextDueText)
        }

        return Outcome(
            Policies = UpdatedPolicies,
            Changes = ChangeList,
            MatchedCount = MatchedCount,
            UpdatedCount = UpdatedCount,
            UnchangedCount = UnchangedCount,
            SkippedCount = SkippedCount
        )
    }

    private fun IsNewerRenewal(CandidateItem: FupPolicy, ExistingItem: FupPolicy): Boolean {
        val CandidateDue = ParseDate(RawText = CandidateItem.DueDate)
        val ExistingDue = ParseDate(RawText = ExistingItem.DueDate)
        if (CandidateDue != null && ExistingDue != null) {
            if (CandidateDue.isAfter(ExistingDue)) return true
            if (ExistingDue.isAfter(CandidateDue)) return false
        } else if (CandidateDue != null) {
            return true
        } else if (ExistingDue != null) {
            return false
        }

        val CandidatePaid = ParseDate(RawText = CandidateItem.PaymentDate)
        val ExistingPaid = ParseDate(RawText = ExistingItem.PaymentDate)
        if (CandidatePaid != null && ExistingPaid != null) return CandidatePaid.isAfter(ExistingPaid)
        return CandidatePaid != null && ExistingPaid == null
    }

    private fun BuildDate(YearValue: Int, MonthValue: Int, DayValue: Int): LocalDate? {
        if (MonthValue !in 1..12) return null
        if (DayValue !in 1..31) return null
        return try {
            LocalDate.of(YearValue, MonthValue, DayValue)
        } catch (_: Exception) {
            null
        }
    }
}
