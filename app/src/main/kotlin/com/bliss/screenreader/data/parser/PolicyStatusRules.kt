@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.parser

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

object PolicyStatusRules {

    const val IN_FORCE = "In Force"
    const val GRACE = "Grace"
    const val OUTSTANDING = "Outstanding"
    const val LAPSED = "Lapsed"
    const val PAID_UP = "Paid Up"
    const val REDUCED_PAID_UP = "Reduced Paid Up"
    const val UNKNOWN = ""

    private const val MONTHLY_GRACE_DAYS = 15L
    private const val DEFAULT_GRACE_DAYS = 30L
    private const val OUTSTANDING_LIMIT_DAYS = 180L

    private const val LEGACY_PAID_UP_YEARS = 3L
    private const val CURRENT_PAID_UP_YEARS = 2L

    private val REGULATION_BOUNDARY: LocalDate = LocalDate.of(2014, 6, 12)

    private val MONTH_NUMBERS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val ISO_REGEX = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val DAY_MONTH_YEAR_REGEX = Regex("(\\d{1,2})\\s*[-/ ]\\s*([A-Za-z]{3,})\\s*[-/, ]\\s*(\\d{4})")
    private val NUMERIC_DMY_REGEX = Regex("(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})")

    fun IsSinglePremium(FrequencyText: String?): Boolean {
        val LowerValue = FrequencyText.orEmpty().lowercase(Locale.ROOT)
        return LowerValue.contains("single")
    }

    fun HasNoRealDueDate(
        FrequencyText: String?,
        FupText: String?,
        CommencementText: String?
    ): Boolean {
        if (IsSinglePremium(FrequencyText = FrequencyText)) return true
        val FupDate = ParseDate(RawText = FupText) ?: return false
        val CommencementDate = ParseDate(RawText = CommencementText) ?: return false
        return FupDate == CommencementDate
    }

    fun RealDueDateOrBlank(
        FrequencyText: String?,
        FupText: String?,
        CommencementText: String?
    ): String {
        val TrimmedFup = FupText.orEmpty().trim()
        if (TrimmedFup.isEmpty()) return ""
        val IsArtefact = HasNoRealDueDate(
            FrequencyText = FrequencyText,
            FupText = TrimmedFup,
            CommencementText = CommencementText
        )
        return if (IsArtefact) "" else TrimmedFup
    }

    fun GraceDaysFor(FrequencyText: String?): Long {
        val LowerValue = FrequencyText.orEmpty().lowercase(Locale.ROOT).trim()
        if (LowerValue.isEmpty()) return DEFAULT_GRACE_DAYS
        val IsMonthly = LowerValue.startsWith("month") ||
            LowerValue == "mly" ||
            LowerValue == "m"
        return if (IsMonthly) MONTHLY_GRACE_DAYS else DEFAULT_GRACE_DAYS
    }

    fun ParseDate(RawText: String?): LocalDate? {
        val TrimmedText = RawText.orEmpty().trim()
        if (TrimmedText.isEmpty()) return null

        ISO_REGEX.find(TrimmedText)?.let { MatchResult ->
            return SafeDate(
                YearValue = MatchResult.groupValues[1].toIntOrNull(),
                MonthValue = MatchResult.groupValues[2].toIntOrNull(),
                DayValue = MatchResult.groupValues[3].toIntOrNull()
            )
        }

        DAY_MONTH_YEAR_REGEX.find(TrimmedText)?.let { MatchResult ->
            return SafeDate(
                YearValue = MatchResult.groupValues[3].toIntOrNull(),
                MonthValue = MONTH_NUMBERS[
                    MatchResult.groupValues[2].take(3).lowercase(Locale.ROOT)
                ],
                DayValue = MatchResult.groupValues[1].toIntOrNull()
            )
        }

        NUMERIC_DMY_REGEX.find(TrimmedText)?.let { MatchResult ->
            return SafeDate(
                YearValue = MatchResult.groupValues[3].toIntOrNull(),
                MonthValue = MatchResult.groupValues[2].toIntOrNull(),
                DayValue = MatchResult.groupValues[1].toIntOrNull()
            )
        }

        return null
    }

    private fun SafeDate(YearValue: Int?, MonthValue: Int?, DayValue: Int?): LocalDate? {
        if (YearValue == null || MonthValue == null || DayValue == null) return null
        if (MonthValue !in 1..12 || DayValue !in 1..31) return null
        return runCatching { LocalDate.of(YearValue, MonthValue, DayValue) }.getOrNull()
    }

    fun QualifiesForPaidUp(CommencementDate: LocalDate, FupDate: LocalDate): Boolean {
        if (FupDate.isBefore(CommencementDate)) return false
        val YearsPaid = ChronoUnit.YEARS.between(CommencementDate, FupDate)
        val RequiredYears = if (CommencementDate.isAfter(REGULATION_BOUNDARY)) {
            CURRENT_PAID_UP_YEARS
        } else {
            LEGACY_PAID_UP_YEARS
        }
        return YearsPaid >= RequiredYears
    }

    fun Compute(
        FupText: String?,
        FrequencyText: String?,
        CommencementText: String?,
        Today: LocalDate = LocalDate.now()
    ): String {
        if (
            HasNoRealDueDate(
                FrequencyText = FrequencyText,
                FupText = FupText,
                CommencementText = CommencementText
            )
        ) {
            return IN_FORCE
        }

        val FupDate = ParseDate(RawText = FupText) ?: return UNKNOWN
        val DaysPastDue = ChronoUnit.DAYS.between(FupDate, Today)

        if (DaysPastDue <= 0L) return IN_FORCE
        if (DaysPastDue <= GraceDaysFor(FrequencyText = FrequencyText)) return GRACE
        if (DaysPastDue <= OUTSTANDING_LIMIT_DAYS) return OUTSTANDING

        val CommencementDate = ParseDate(RawText = CommencementText) ?: return LAPSED
        return if (QualifiesForPaidUp(CommencementDate = CommencementDate, FupDate = FupDate)) {
            PAID_UP
        } else {
            REDUCED_PAID_UP
        }
    }

    fun IsAdverse(StatusText: String): Boolean {
        return when (StatusText) {
            LAPSED, REDUCED_PAID_UP -> true
            else -> false
        }
    }

    fun IsAttention(StatusText: String): Boolean {
        return when (StatusText) {
            GRACE, OUTSTANDING, PAID_UP -> true
            else -> false
        }
    }
}
