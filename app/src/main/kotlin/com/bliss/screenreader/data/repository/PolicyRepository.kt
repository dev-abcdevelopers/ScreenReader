@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.repository

import android.content.Context
import androidx.core.content.edit
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.DueDateReport
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.PsPolicy
import com.bliss.screenreader.data.model.RecordFieldChange
import com.bliss.screenreader.data.model.SessionGap
import com.bliss.screenreader.security.SecurePrefs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PolicyRepository {

    const val PREFS_NAME = "data_reader_prefs"

    private const val KEY_CUSTOMER_POLICIES = "key_customer_policies"
    private const val KEY_FUP_POLICIES = "key_fup_policies"
    private const val KEY_PS_POLICIES = "key_ps_policies"

    private const val KEY_LATEST_POLICY_SESSION = "latest_policy_session"
    private const val KEY_LATEST_FUP_SESSION = "latest_fup_session"
    private const val KEY_LATEST_PS_SESSION = "latest_ps_session"
    private const val KEY_SESSION_HISTORY = "capture_session_history"
    private const val SESSION_KEY_PREFIX = "capture_session"
    private const val CHANGE_KEY_PREFIX = "capture_changes"
    private const val GAP_KEY_PREFIX = "capture_gaps"
    private const val AGENCY_KEY_PREFIX = "capture_agency"
    private const val VISITED_KEY_PREFIX = "capture_visited_customers"
    private const val KEY_LAST_AGENCY_CODE = "last_agency_code"

    private const val MAX_CHANGE_ENTRIES = 500
    private const val MAX_GAP_ENTRIES = 500

    private val GsonInstance = Gson()

    data class CaptureSessionReference(
        val SessionId: String,
        val Mode: CaptureMode,
        val SavedAt: Long,
        val RecordCount: Int,
        val CapturePolicyDetails: Boolean = false,
        val LastResumedAt: Long = 0L,
        val ChangeCount: Int = 0
    )

    fun SaveCustomerPolicies(
        ContextRef: Context,
        Policies: List<CustomerPolicy>,
        SessionId: String = "",
        CapturePolicyDetails: Boolean = false
    ) {
        SaveSessionRecords(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.POLICY,
            SessionId = SessionId,
            Records = Policies,
            LegacyKey = KEY_CUSTOMER_POLICIES,
            LatestSessionKey = KEY_LATEST_POLICY_SESSION,
            CapturePolicyDetails = CapturePolicyDetails
        )
    }

    fun GetCustomerPolicies(
        ContextRef: Context,
        SessionId: String = ""
    ): List<CustomerPolicy> {
        return ReadSessionRecords(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.POLICY,
            SessionId = SessionId,
            LegacyKey = KEY_CUSTOMER_POLICIES,
            LatestSessionKey = KEY_LATEST_POLICY_SESSION,
            DataType = object : TypeToken<List<CustomerPolicy>>() {}.type
        )
    }

    fun SaveFupPolicies(
        ContextRef: Context,
        Policies: List<FupPolicy>,
        SessionId: String = ""
    ) {
        SaveSessionRecords(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.FUP,
            SessionId = SessionId,
            Records = Policies,
            LegacyKey = KEY_FUP_POLICIES,
            LatestSessionKey = KEY_LATEST_FUP_SESSION
        )
    }

    fun GetFupPolicies(
        ContextRef: Context,
        SessionId: String = ""
    ): List<FupPolicy> {
        return ReadSessionRecords(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.FUP,
            SessionId = SessionId,
            LegacyKey = KEY_FUP_POLICIES,
            LatestSessionKey = KEY_LATEST_FUP_SESSION,
            DataType = object : TypeToken<List<FupPolicy>>() {}.type
        )
    }

    fun SavePsPolicies(
        ContextRef: Context,
        Policies: List<PsPolicy>,
        SessionId: String = ""
    ) {
        SaveSessionRecords(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.PS,
            SessionId = SessionId,
            Records = Policies,
            LegacyKey = KEY_PS_POLICIES,
            LatestSessionKey = KEY_LATEST_PS_SESSION
        )
    }

    fun GetPsPolicies(
        ContextRef: Context,
        SessionId: String = ""
    ): List<PsPolicy> {
        return ReadSessionRecords(
            ContextRef = ContextRef,
            ModeVal = CaptureMode.PS,
            SessionId = SessionId,
            LegacyKey = KEY_PS_POLICIES,
            LatestSessionKey = KEY_LATEST_PS_SESSION,
            DataType = object : TypeToken<List<PsPolicy>>() {}.type
        )
    }

    fun GetLatestSessionId(ContextRef: Context, ModeVal: CaptureMode): String {
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        return PrefsObj.getString(LatestSessionKey(ModeVal = ModeVal), "").orEmpty()
    }

    fun GetSessionHistory(
        ContextRef: Context,
        ModeVal: CaptureMode? = null
    ): List<CaptureSessionReference> {
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        val HistoryJson = PrefsObj.getString(KEY_SESSION_HISTORY, null) ?: return emptyList()
        val HistoryType = object : TypeToken<List<CaptureSessionReference>>() {}.type
        val HistoryList: List<CaptureSessionReference> = try {
            GsonInstance.fromJson(HistoryJson, HistoryType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return if (ModeVal == null) {
            HistoryList
        } else {
            HistoryList.filter { SessionRef -> SessionRef.Mode == ModeVal }
        }
    }

    fun GetSessionReference(
        ContextRef: Context,
        SessionId: String
    ): CaptureSessionReference? {
        if (SessionId.isBlank()) return null
        return GetSessionHistory(ContextRef = ContextRef)
            .firstOrNull { SessionRef -> SessionRef.SessionId == SessionId }
    }

    fun SaveFieldChanges(
        ContextRef: Context,
        ModeVal: CaptureMode,
        SessionId: String,
        Changes: List<RecordFieldChange>,
        SourceName: String = ""
    ) {
        if (SessionId.isBlank() || Changes.isEmpty()) return
        val ExistingChanges = GetFieldChanges(
            ContextRef = ContextRef,
            ModeVal = ModeVal,
            SessionId = SessionId
        )
        val StampedAt = System.currentTimeMillis()
        val StampedChanges = Changes.map { ChangeItem ->
            ChangeItem.copy(ChangedAt = StampedAt, SourceName = SourceName)
        }
        val CombinedChanges = (StampedChanges + ExistingChanges).take(MAX_CHANGE_ENTRIES)
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        PrefsObj.edit {
            putString(
                ChangeStorageKey(ModeVal = ModeVal, SessionId = SessionId),
                GsonInstance.toJson(CombinedChanges)
            )
        }
    }

    fun GetFieldChanges(
        ContextRef: Context,
        ModeVal: CaptureMode,
        SessionId: String
    ): List<RecordFieldChange> {
        if (SessionId.isBlank()) return emptyList()
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        val JsonText = PrefsObj.getString(
            ChangeStorageKey(ModeVal = ModeVal, SessionId = SessionId),
            null
        ) ?: return emptyList()
        val ChangeType = object : TypeToken<List<RecordFieldChange>>() {}.type
        return try {
            GsonInstance.fromJson(JsonText, ChangeType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun SaveDueDateReport(ContextRef: Context, SessionId: String, ReportObj: DueDateReport) {
        if (SessionId.isBlank()) return
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        PrefsObj.edit {
            putString(DueReportStorageKey(SessionId = SessionId), GsonInstance.toJson(ReportObj))
        }
    }

    fun GetDueDateReport(ContextRef: Context, SessionId: String): DueDateReport? {
        if (SessionId.isBlank()) return null
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        val JsonText = PrefsObj.getString(
            DueReportStorageKey(SessionId = SessionId),
            null
        ) ?: return null
        return try {
            GsonInstance.fromJson(JsonText, DueDateReport::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun DueReportStorageKey(SessionId: String): String =
        "due_report_${SafeSessionId(SessionId = SessionId)}"

    fun SaveSessionGaps(ContextRef: Context, SessionId: String, Gaps: List<SessionGap>) {
        if (SessionId.isBlank() || Gaps.isEmpty()) return
        val ExistingGaps = ReadStoredGaps(ContextRef = ContextRef, SessionId = SessionId)
        val MergedGaps = linkedMapOf<String, SessionGap>()
        for (GapItem in ExistingGaps + Gaps) {
            if (GapItem.PolicyNumber.isBlank()) continue
            MergedGaps[GapItem.PolicyNumber] = GapItem
        }
        val TrimmedGaps = MergedGaps.values.toList().takeLast(MAX_GAP_ENTRIES)
        SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME).edit {
            putString(GapStorageKey(SessionId = SessionId), GsonInstance.toJson(TrimmedGaps))
        }
    }

    fun GetSessionGaps(ContextRef: Context, SessionId: String): List<SessionGap> {
        if (SessionId.isBlank()) return emptyList()
        val StoredGaps = ReadStoredGaps(ContextRef = ContextRef, SessionId = SessionId)
        if (StoredGaps.isEmpty()) return emptyList()
        val CapturedNumbers = GetCustomerPolicies(ContextRef = ContextRef, SessionId = SessionId)
            .map { PolicyItem -> PolicyItem.PolicyNumber }
            .toSet()
        return StoredGaps.filterNot { GapItem -> CapturedNumbers.contains(GapItem.PolicyNumber) }
    }

    fun GetStoredSessionGaps(ContextRef: Context, SessionId: String): List<SessionGap> {
        if (SessionId.isBlank()) return emptyList()
        return ReadStoredGaps(ContextRef = ContextRef, SessionId = SessionId)
    }

    fun RestoreSession(
        ContextRef: Context,
        SessionRef: CaptureSessionReference,
        Policies: List<CustomerPolicy>,
        Renewals: List<FupPolicy>,
        Servicing: List<PsPolicy>,
        Gaps: List<SessionGap>,
        Changes: Map<CaptureMode, List<RecordFieldChange>>,
        VisitedCustomers: List<String>,
        AgencyCode: String
    ) {
        val SessionId = SessionRef.SessionId
        if (SessionId.isBlank()) return

        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        PrefsObj.edit {
            if (Policies.isNotEmpty()) {
                putString(
                    SessionStorageKey(ModeVal = CaptureMode.POLICY, SessionId = SessionId),
                    GsonInstance.toJson(Policies)
                )
            }
            if (Renewals.isNotEmpty()) {
                putString(
                    SessionStorageKey(ModeVal = CaptureMode.FUP, SessionId = SessionId),
                    GsonInstance.toJson(Renewals)
                )
            }
            if (Servicing.isNotEmpty()) {
                putString(
                    SessionStorageKey(ModeVal = CaptureMode.PS, SessionId = SessionId),
                    GsonInstance.toJson(Servicing)
                )
            }
            if (Gaps.isNotEmpty()) {
                putString(GapStorageKey(SessionId = SessionId), GsonInstance.toJson(Gaps))
            }
            for ((ModeVal, ChangeList) in Changes) {
                if (ChangeList.isEmpty()) continue
                putString(
                    ChangeStorageKey(ModeVal = ModeVal, SessionId = SessionId),
                    GsonInstance.toJson(ChangeList.take(MAX_CHANGE_ENTRIES))
                )
            }
            if (VisitedCustomers.isNotEmpty()) {
                putString(
                    VisitedStorageKey(SessionId = SessionId),
                    GsonInstance.toJson(VisitedCustomers)
                )
            }
            if (AgencyCode.isNotBlank()) {
                putString(AgencyStorageKey(SessionId = SessionId), AgencyCode)
            }
            putString(LatestSessionKey(ModeVal = SessionRef.Mode), SessionId)
        }

        RegisterSession(ContextRef = ContextRef, SessionRef = SessionRef)
    }

    private fun ReadStoredGaps(ContextRef: Context, SessionId: String): List<SessionGap> {
        val JsonText = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
            .getString(GapStorageKey(SessionId = SessionId), null) ?: return emptyList()
        val GapType = object : TypeToken<List<SessionGap>>() {}.type
        return try {
            GsonInstance.fromJson(JsonText, GapType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun DeleteSession(ContextRef: Context, SessionId: String, ModeVal: CaptureMode): Boolean {
        if (SessionId.isBlank()) return false

        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        val RemainingHistory = GetSessionHistory(ContextRef = ContextRef)
            .filterNot { SessionRef -> SessionRef.SessionId == SessionId }

        val LatestKey = LatestSessionKey(ModeVal = ModeVal)
        val WasLatest = PrefsObj.getString(LatestKey, "").orEmpty() == SessionId
        val ReplacementSessionId = if (WasLatest) {
            RemainingHistory
                .filter { SessionRef -> SessionRef.Mode == ModeVal }
                .maxByOrNull { SessionRef -> SessionRef.SavedAt }
                ?.SessionId
                .orEmpty()
        } else {
            ""
        }

        PrefsObj.edit {
            remove(SessionStorageKey(ModeVal = ModeVal, SessionId = SessionId))
            remove(ChangeStorageKey(ModeVal = ModeVal, SessionId = SessionId))
            remove(GapStorageKey(SessionId = SessionId))
            remove(AgencyStorageKey(SessionId = SessionId))
            remove(VisitedStorageKey(SessionId = SessionId))
            remove(DueReportStorageKey(SessionId = SessionId))
            putString(KEY_SESSION_HISTORY, GsonInstance.toJson(RemainingHistory))
            if (WasLatest) {
                if (ReplacementSessionId.isEmpty()) {
                    remove(LatestKey)
                } else {
                    putString(LatestKey, ReplacementSessionId)
                }
            }
        }
        return true
    }

    private fun <RecordType> SaveSessionRecords(
        ContextRef: Context,
        ModeVal: CaptureMode,
        SessionId: String,
        Records: List<RecordType>,
        LegacyKey: String,
        LatestSessionKey: String,
        CapturePolicyDetails: Boolean = false
    ) {
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        val JsonText = GsonInstance.toJson(Records)
        if (SessionId.isBlank()) {
            PrefsObj.edit { putString(LegacyKey, JsonText) }
            return
        }

        val StorageKey = SessionStorageKey(ModeVal = ModeVal, SessionId = SessionId)
        PrefsObj.edit {
            putString(StorageKey, JsonText)
            putString(LatestSessionKey, SessionId)
        }

        val ExistingRef = GetSessionReference(ContextRef = ContextRef, SessionId = SessionId)
        val CurrentTime = System.currentTimeMillis()
        RegisterSession(
            ContextRef = ContextRef,
            SessionRef = CaptureSessionReference(
                SessionId = SessionId,
                Mode = ModeVal,
                SavedAt = CurrentTime,
                RecordCount = Records.size,
                CapturePolicyDetails = CapturePolicyDetails ||
                        ExistingRef?.CapturePolicyDetails == true,
                LastResumedAt = if (ExistingRef == null) 0L else CurrentTime,
                ChangeCount = GetFieldChanges(
                    ContextRef = ContextRef,
                    ModeVal = ModeVal,
                    SessionId = SessionId
                ).size
            )
        )
    }

    private fun <RecordType> ReadSessionRecords(
        ContextRef: Context,
        ModeVal: CaptureMode,
        SessionId: String,
        LegacyKey: String,
        LatestSessionKey: String,
        DataType: java.lang.reflect.Type
    ): List<RecordType> {
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        val ResolvedSessionId = SessionId.ifBlank {
            PrefsObj.getString(LatestSessionKey, "").orEmpty()
        }
        val StorageKey = if (ResolvedSessionId.isBlank()) {
            LegacyKey
        } else {
            SessionStorageKey(ModeVal = ModeVal, SessionId = ResolvedSessionId)
        }
        val JsonText = PrefsObj.getString(StorageKey, null) ?: return emptyList()
        return try {
            GsonInstance.fromJson<List<RecordType>>(JsonText, DataType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun RegisterSession(ContextRef: Context, SessionRef: CaptureSessionReference) {
        val ExistingHistory = GetSessionHistory(ContextRef = ContextRef).toMutableList()
        ExistingHistory.removeAll { ExistingRef -> ExistingRef.SessionId == SessionRef.SessionId }
        ExistingHistory.add(0, SessionRef)
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        PrefsObj.edit {
            putString(KEY_SESSION_HISTORY, GsonInstance.toJson(ExistingHistory))
        }
    }

    private fun SessionStorageKey(ModeVal: CaptureMode, SessionId: String): String {
        return "${SESSION_KEY_PREFIX}_${ModeVal.name.lowercase()}_${SafeSessionId(SessionId = SessionId)}"
    }

    fun SaveVisitedCustomers(ContextRef: Context, SessionId: String, Names: Set<String>) {
        if (SessionId.isBlank()) return
        SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME).edit {
            putString(VisitedStorageKey(SessionId = SessionId), GsonInstance.toJson(Names.toList()))
        }
    }

    fun GetVisitedCustomers(ContextRef: Context, SessionId: String): Set<String> {
        if (SessionId.isBlank()) return emptySet()
        val JsonText = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
            .getString(VisitedStorageKey(SessionId = SessionId), null) ?: return emptySet()
        val NameType = object : TypeToken<List<String>>() {}.type
        return try {
            (GsonInstance.fromJson<List<String>>(JsonText, NameType) ?: emptyList()).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun ClearVisitedCustomers(ContextRef: Context, SessionId: String) {
        if (SessionId.isBlank()) return
        SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME).edit {
            remove(VisitedStorageKey(SessionId = SessionId))
        }
    }

    private fun VisitedStorageKey(SessionId: String): String {
        return "${VISITED_KEY_PREFIX}_${SafeSessionId(SessionId = SessionId)}"
    }

    fun SaveAgencyCode(ContextRef: Context, SessionId: String, AgencyCode: String) {
        val TrimmedCode = AgencyCode.trim()
        SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME).edit {
            putString(KEY_LAST_AGENCY_CODE, TrimmedCode)
            if (SessionId.isNotBlank()) {
                putString(AgencyStorageKey(SessionId = SessionId), TrimmedCode)
            }
        }
    }

    fun GetAgencyCode(ContextRef: Context, SessionId: String): String {
        val PrefsObj = SecurePrefs.Of(ContextRef = ContextRef, PrefsName = PREFS_NAME)
        if (SessionId.isNotBlank()) {
            val SessionCode = PrefsObj
                .getString(AgencyStorageKey(SessionId = SessionId), "")
                .orEmpty()
            if (SessionCode.isNotEmpty()) return SessionCode
        }
        return PrefsObj.getString(KEY_LAST_AGENCY_CODE, "").orEmpty()
    }

    private fun AgencyStorageKey(SessionId: String): String {
        return "${AGENCY_KEY_PREFIX}_${SafeSessionId(SessionId = SessionId)}"
    }

    private fun GapStorageKey(SessionId: String): String {
        return "${GAP_KEY_PREFIX}_${SafeSessionId(SessionId = SessionId)}"
    }

    private fun ChangeStorageKey(ModeVal: CaptureMode, SessionId: String): String {
        return "${CHANGE_KEY_PREFIX}_${ModeVal.name.lowercase()}_${SafeSessionId(SessionId = SessionId)}"
    }

    private fun SafeSessionId(SessionId: String): String {
        return SessionId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    private fun LatestSessionKey(ModeVal: CaptureMode): String {
        return when (ModeVal) {
            CaptureMode.POLICY -> KEY_LATEST_POLICY_SESSION
            CaptureMode.PS -> KEY_LATEST_PS_SESSION
            CaptureMode.FUP -> KEY_LATEST_FUP_SESSION
            CaptureMode.CUSTOMER -> KEY_LATEST_POLICY_SESSION
        }
    }
}
