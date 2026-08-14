@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

import java.util.Locale

enum class CaptureMode(val DisplayName: String, val RecordNounSingular: String, val RecordNounPlural: String) {

    POLICY("Policy Details", "policy", "policies"),

    PS("Servicing History", "record", "records"),
    FUP("Renewal History", "renewal", "renewals");

    fun DescribeCount(CountVal: Int): String {
        val Noun = if (CountVal == 1) RecordNounSingular else RecordNounPlural
        return "$CountVal $Noun"
    }

    companion object {
        fun FromName(NameVal: String?): CaptureMode {
            if (NameVal == null) return POLICY
            for (ModeVal in entries) {
                if (ModeVal.name == NameVal) return ModeVal
            }
            return POLICY
        }
    }
}

data class ParsedRecord(
    val PolicyNumber: String,
    val PrimaryLine: String,
    val SecondaryLine: String,
    val FieldCount: Int,
    val Warning: String = ""
) {
    val HasWarning: Boolean get() = Warning.isNotEmpty()
}

data class CaptureSession(
    val SessionId: String,
    val Mode: CaptureMode,
    val StartedAt: Long,
    val EndedAt: Long,
    val RawNodes: List<String>,
    val Records: List<ParsedRecord>,
    val PolicyRecords: List<CustomerPolicy> = emptyList(),
    val FupRecords: List<FupPolicy> = emptyList(),
    val CapturePolicyDetails: Boolean = false,
    val TargetPackage: String = "",
    val OriginActivity: String = ""
) {
    val DurationMs: Long get() = (EndedAt - StartedAt).coerceAtLeast(0L)

    val NodeCount: Int get() = RawNodes.size

    val WarningCount: Int get() = Records.count { it.HasWarning }

    val DurationLabel: String get() = FormatDuration(DurationMsVal = DurationMs)

    companion object {
        fun FormatDuration(DurationMsVal: Long): String {
            val TotalSeconds = DurationMsVal / 1000L
            val Minutes = TotalSeconds / 60L
            val Seconds = TotalSeconds % 60L
            return if (Minutes > 0L) {
                String.format(Locale.US, "%dm %02ds", Minutes, Seconds)
            } else {
                String.format(Locale.US, "%ds", Seconds)
            }
        }

        fun FormatClock(DurationMsVal: Long): String {
            val TotalSeconds = DurationMsVal / 1000L
            return String.format(Locale.US, "%02d:%02d", TotalSeconds / 60L, TotalSeconds % 60L)
        }
    }
}
