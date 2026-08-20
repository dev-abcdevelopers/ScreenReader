@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.settings

enum class PaceProfile(val StoredName: String, val Factor: Float) {

    FAST("fast", 0.75f),
    NORMAL("normal", 1.0f),
    PATIENT("patient", 1.6f);

    fun Scale(BaseMs: Long): Long {
        if (this == NORMAL) return BaseMs
        if (BaseMs <= 0L) return BaseMs
        return (BaseMs * Factor).toLong().coerceAtLeast(1L)
    }

    fun Scale(BaseCount: Int): Int {
        if (this == NORMAL) return BaseCount
        if (BaseCount <= 0) return BaseCount
        return (BaseCount * Factor).toInt().coerceAtLeast(1)
    }

    companion object {
        fun FromName(NameVal: String?): PaceProfile {
            if (NameVal == null) return NORMAL
            for (ProfileVal in entries) {
                if (ProfileVal.StoredName.equals(NameVal.trim(), ignoreCase = true)) return ProfileVal
            }
            return NORMAL
        }
    }
}
