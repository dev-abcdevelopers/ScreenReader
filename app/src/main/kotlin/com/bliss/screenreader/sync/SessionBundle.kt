@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.sync

import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.RenewalDuePolicy
import com.bliss.screenreader.data.model.PsPolicy
import com.bliss.screenreader.data.model.RecordFieldChange
import com.bliss.screenreader.data.model.SessionGap

data class SessionBundleEntry(
    val SessionId: String = "",
    val Mode: String = "",
    val SavedAt: Long = 0L,
    val LastResumedAt: Long = 0L,
    val RecordCount: Int = 0,
    val CapturePolicyDetails: Boolean = false,
    val ChangeCount: Int = 0,
    val AgencyCode: String = "",
    val Policies: List<CustomerPolicy>? = null,
    val Renewals: List<FupPolicy>? = null,
    val RenewalsDue: List<RenewalDuePolicy>? = null,
    val Servicing: List<PsPolicy>? = null,
    val Gaps: List<SessionGap>? = null,
    val Changes: Map<String, List<RecordFieldChange>>? = null,
    val VisitedCustomers: List<String>? = null
) {
    val TotalRecordCount: Int
        get() = Policies.orEmpty().size + Renewals.orEmpty().size +
                RenewalsDue.orEmpty().size + Servicing.orEmpty().size
}

data class SessionBundle(
    val SchemaVersion: Int = 0,
    val ExportedAt: Long = 0L,
    val App: UploadAppInfo? = null,
    val Device: UploadDeviceInfo? = null,
    val Sessions: List<SessionBundleEntry>? = null
)
