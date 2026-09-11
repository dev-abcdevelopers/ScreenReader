@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.data.model

data class SessionNameEntry(
    val FromName: String? = null,
    val ToName: String? = null,
    val StampVal: Long? = null
) {
    val FromText: String get() = FromName.orEmpty().trim()

    val ToText: String get() = ToName.orEmpty().trim()

    val StampAt: Long get() = StampVal ?: 0L

    val IsRestore: Boolean get() = ToText.isEmpty()
}

object SessionNameRules {

    const val MAX_NAME_LENGTH = 40

    fun Normalize(NameText: String): String = NameText.trim().take(MAX_NAME_LENGTH).trim()

    fun CurrentName(HistoryList: List<SessionNameEntry>): String =
        HistoryList.lastOrNull()?.ToText.orEmpty()

    fun Append(
        HistoryList: List<SessionNameEntry>,
        NameText: String,
        FallbackLabel: String,
        StampVal: Long
    ): List<SessionNameEntry>? {
        val TrimmedName = Normalize(NameText = NameText)
        val PreviousName = CurrentName(HistoryList = HistoryList)
        if (PreviousName == TrimmedName) return null

        return HistoryList + SessionNameEntry(
            FromName = PreviousName.ifEmpty { FallbackLabel.trim() },
            ToName = TrimmedName,
            StampVal = StampVal
        )
    }
}
