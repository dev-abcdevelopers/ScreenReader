@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

import java.util.Locale

/**
 * Which screen of the target app a capture session is reading, and therefore
 * which parser runs over the collected nodes. The three capture flows differ
 * only by this value.
 */
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

/**
 * One row in the post-capture review sheet. Deliberately display-only: it carries
 * enough to let the user judge whether the parse worked, not the record itself.
 * The typed model is rebuilt from the raw nodes at save time.
 */
data class ParsedRecord(
    val PolicyNumber: String,
    val PrimaryLine: String,
    val SecondaryLine: String,
    val FieldCount: Int,
    val Warning: String = ""
) {
    val HasWarning: Boolean get() = Warning.isNotEmpty()
}

/**
 * The result of a capture, held in memory between the service finishing and the
 * user accepting or discarding it. Typed policy records are retained here so
 * details collected on separate screens cannot become associated with another
 * policy. Nothing reaches storage until the review is confirmed.
 */
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
