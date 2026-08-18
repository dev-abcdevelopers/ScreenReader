@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.update

object UpdateVersion {

    private const val BUILD_SEPARATOR = '|'
    private val DATE_SEPARATORS = charArrayOf('.', '-', '/')

    data class Stamp(
        val Year: Int,
        val Month: Int,
        val Day: Int,
        val Build: Int
    )

    fun ParseVersionName(VersionName: String): Stamp? {
        val TrimmedName = VersionName.trim()
        if (TrimmedName.isEmpty()) return null

        val SeparatorIndex = TrimmedName.indexOf(BUILD_SEPARATOR)
        val DateText =
            if (SeparatorIndex >= 0) TrimmedName.substring(0, SeparatorIndex).trim()
            else TrimmedName
        val BuildText =
            if (SeparatorIndex >= 0) TrimmedName.substring(SeparatorIndex + 1).trim()
            else ""

        val DateParts = DateText.split(*DATE_SEPARATORS).map { PartText -> PartText.trim() }
        if (DateParts.size != 3) return null

        val DayVal = DateParts[0].toIntOrNull() ?: return null
        val MonthVal = DateParts[1].toIntOrNull() ?: return null
        val YearVal = DateParts[2].toIntOrNull() ?: return null
        if (DayVal !in 1..31 || MonthVal !in 1..12 || YearVal < 1970) return null

        val BuildVal = BuildText.takeWhile { CharVal -> CharVal.isDigit() }.toIntOrNull() ?: 0

        return Stamp(Year = YearVal, Month = MonthVal, Day = DayVal, Build = BuildVal)
    }

    fun CompareStamps(LeftStamp: Stamp, RightStamp: Stamp): Int {
        if (LeftStamp.Year != RightStamp.Year) return LeftStamp.Year.compareTo(RightStamp.Year)
        if (LeftStamp.Month != RightStamp.Month) return LeftStamp.Month.compareTo(RightStamp.Month)
        if (LeftStamp.Day != RightStamp.Day) return LeftStamp.Day.compareTo(RightStamp.Day)
        return LeftStamp.Build.compareTo(RightStamp.Build)
    }

    fun IsRemoteNewer(
        LocalCode: Int,
        LocalName: String,
        RemoteCode: Int,
        RemoteName: String
    ): Boolean {
        if (RemoteCode > LocalCode) return true
        if (RemoteCode < LocalCode) return false

        val LocalStamp = ParseVersionName(VersionName = LocalName) ?: return false
        val RemoteStamp = ParseVersionName(VersionName = RemoteName) ?: return false
        return CompareStamps(LeftStamp = RemoteStamp, RightStamp = LocalStamp) > 0
    }

    fun Describe(VersionName: String, VersionCode: Int): String {
        val StampObj = ParseVersionName(VersionName = VersionName)
            ?: return VersionName.ifBlank { VersionCode.toString() }
        return "%02d.%02d.%04d · %d".format(
            StampObj.Day,
            StampObj.Month,
            StampObj.Year,
            StampObj.Build
        )
    }

    fun DescribeShort(VersionName: String, VersionCode: Int): String {
        val StampObj = ParseVersionName(VersionName = VersionName)
            ?: return VersionName.ifBlank { VersionCode.toString() }
        return "%02d.%02d.%04d".format(StampObj.Day, StampObj.Month, StampObj.Year)
    }
}
