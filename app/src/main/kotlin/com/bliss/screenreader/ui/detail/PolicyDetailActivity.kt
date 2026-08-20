@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SameParameterValue", "SpellCheckingInspection", "UsePropertyAccessSyntax"
)

package com.bliss.screenreader.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isEmpty
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.PolicyCompleteness
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ActivityPolicyDetailBinding
import com.bliss.screenreader.databinding.PartialFieldGroupBinding
import com.bliss.screenreader.databinding.PartialFieldRowBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.export.ExportFormat
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.ui.capture.CaptureFlow
import com.bliss.screenreader.ui.main.MainActivity
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class PolicyDetailActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityPolicyDetailBinding
    private var SessionIdVal: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityPolicyDetailBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        val PolicyNumber = intent.getStringExtra(EXTRA_POLICY_NUMBER).orEmpty()
        SessionIdVal = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()

        val ResolvedPolicy = PolicyRepository.GetCustomerPolicies(
            ContextRef = this,
            SessionId = SessionIdVal
        ).firstOrNull { PolicyItem -> PolicyItem.PolicyNumber == PolicyNumber }

        if (ResolvedPolicy == null) {
            finish()
            return
        }

        BindHeader(PolicyRef = ResolvedPolicy)
        BindActions(PolicyRef = ResolvedPolicy)
        BindRevival(PolicyRef = ResolvedPolicy)

        val SummaryVal = PolicyCompleteness.Describe(
            PolicyItem = ResolvedPolicy,
            Labels = BuildLabels()
        )
        BindCompleteness(SummaryVal = SummaryVal)
        BindGroups(SummaryVal = SummaryVal)
        BindRenewalHistory(PolicyRef = ResolvedPolicy)
        BindProvenance(PolicyRef = ResolvedPolicy)
    }


    private fun BindHeader(PolicyRef: CustomerPolicy) {
        ViewBindingObj.tvDetailNumber.text =
            PolicyRef.PolicyNumber.ifEmpty { getString(R.string.detail_missing) }

        ViewBindingObj.tvDetailHolder.text =
            PolicyRef.HolderName.ifEmpty { getString(R.string.status_unknown) }

        val PlanText = if (PolicyRef.PlanCode.isNotEmpty()) {
            "${PolicyRef.PlanCode} · ${PolicyRef.PlanName}"
        } else {
            PolicyRef.PlanName
        }
        ViewBindingObj.tvDetailPlan.text = PlanText
        ViewBindingObj.tvDetailPlan.visibility = if (PlanText.isBlank()) View.GONE else View.VISIBLE

        BindFlagChips(PolicyRef = PolicyRef)

        ViewBindingObj.btnCopyNumber.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            CopyValue(
                LabelText = getString(R.string.detail_copy_number),
                ValueText = PolicyRef.PolicyNumber
            )
        }
    }


    private fun BindFlagChips(PolicyRef: CustomerPolicy) {
        ViewBindingObj.chipGroupFlags.removeAllViews()

        val StatusText = PolicyRef.NormalizedStatus
        if (StatusText.isNotEmpty()) {
            val IsLapsed = StatusText.equals("Lapsed", ignoreCase = true)
            AddChip(
                LabelText = StatusText,
                BackgroundRes = if (IsLapsed) R.color.status_red_bg else R.color.status_green_bg,
                TextColorRes = if (IsLapsed) R.color.status_red_text else R.color.status_green_text
            )
        }

        val FlagList = PolicyCompleteness.StatusFlags(
            PolicyItem = PolicyRef,
            Labels = BuildLabels()
        )
        for (FlagText in FlagList) {
            AddChip(
                LabelText = FlagText,
                BackgroundRes = R.color.status_amber_bg,
                TextColorRes = R.color.status_amber_text
            )
        }

        ViewBindingObj.chipGroupFlags.visibility =
            if (ViewBindingObj.chipGroupFlags.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun AddChip(LabelText: String, BackgroundRes: Int, TextColorRes: Int) {
        val ChipRef = Chip(this)
        ChipRef.text = LabelText
        ChipRef.isClickable = false
        ChipRef.isCheckable = false
        ChipRef.chipBackgroundColor =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, BackgroundRes))
        ChipRef.setTextColor(ContextCompat.getColor(this, TextColorRes))
        ViewBindingObj.chipGroupFlags.addView(ChipRef)
    }


    private fun BindActions(PolicyRef: CustomerPolicy) {
        val MobileNumber = PolicyRef.MobileNumber.filter { CharValue ->
            CharValue.isDigit() || CharValue == '+'
        }
        val HasMobile = MobileNumber.length >= MIN_DIALABLE_DIGITS

        ViewBindingObj.btnCallCustomer.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            if (!HasMobile) {
                ShowMessage(MessageVal = getString(R.string.detail_no_mobile))
                return@setOnClickListener
            }
            LaunchIntentSafely(
                IntentObj = Intent(Intent.ACTION_DIAL, "tel:$MobileNumber".toUri())
            )
        }

        ViewBindingObj.btnMessageCustomer.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            if (!HasMobile) {
                ShowMessage(MessageVal = getString(R.string.detail_no_mobile))
                return@setOnClickListener
            }
            LaunchIntentSafely(
                IntentObj = Intent(Intent.ACTION_SENDTO, "smsto:$MobileNumber".toUri())
            )
        }

        ViewBindingObj.btnExportPolicy.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            ExportSingle(PolicyRef = PolicyRef)
        }
    }

    private fun BindRevival(PolicyRef: CustomerPolicy) {
        val IsLapsed = PolicyRef.NormalizedStatus.equals("Lapsed", ignoreCase = true)
        val CardDateText = PolicyRef.RenewalDueDate.ifEmpty { PolicyRef.RenewalDateValue }
        val DueDateIso = ExportFormat.IsoDate(RawText = CardDateText)

        if (!IsLapsed || DueDateIso.isEmpty()) {
            ViewBindingObj.revivalBlock.visibility = View.GONE
            return
        }

        ViewBindingObj.revivalBlock.visibility = View.VISIBLE
        ViewBindingObj.tvRevivalLabel.text = PolicyRef.RenewalType
            .ifEmpty { PolicyRef.RenewalDateLabel }
            .ifEmpty { getString(R.string.detail_revival_default) }
        ViewBindingObj.tvRevivalDate.text = DueDateIso
    }


    private fun BindCompleteness(
        SummaryVal: PolicyCompleteness.Summary
    ) {
        ViewBindingObj.tvCompletenessLabel.text = getString(
            R.string.detail_completeness_format,
            SummaryVal.CapturedCount,
            SummaryVal.TotalCount
        )
        ViewBindingObj.tvCompletenessPercent.text =
            getString(R.string.detail_completeness_percent_format, SummaryVal.Percent)
        ViewBindingObj.tvCompletenessPercent.setTextColor(
            ContextCompat.getColor(
                this,
                if (SummaryVal.IsComplete) R.color.status_green_text else R.color.status_amber_text
            )
        )

        ViewBindingObj.progressCompleteness.max = 100
        ViewBindingObj.progressCompleteness.setProgressCompat(SummaryVal.Percent, false)
        ViewBindingObj.progressCompleteness.setIndicatorColor(
            ContextCompat.getColor(
                this,
                if (SummaryVal.IsComplete) R.color.status_green_text else R.color.status_amber_text
            )
        )

        if (SummaryVal.IsComplete) {
            ViewBindingObj.btnCaptureMissing.visibility = View.GONE
            return
        }

        ViewBindingObj.btnCaptureMissing.visibility = View.VISIBLE
        ViewBindingObj.btnCaptureMissing.text = if (SummaryVal.MissingCount == 1) {
            getString(R.string.detail_capture_missing_one)
        } else {
            getString(R.string.detail_capture_missing_format, SummaryVal.MissingCount)
        }
        ViewBindingObj.btnCaptureMissing.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            ResumeCaptureForPolicy()
        }
    }

    private fun BindGroups(
        SummaryVal: PolicyCompleteness.Summary
    ) {
        ViewBindingObj.groupContainer.removeAllViews()

        for (GroupRef in SummaryVal.Groups) {
            val GroupBinding = PartialFieldGroupBinding.inflate(
                layoutInflater, ViewBindingObj.groupContainer, false
            )
            GroupBinding.tvGroupTitle.text = GroupRef.Title

            val ShowCapture = GroupRef.IsCapturable && !GroupRef.IsComplete
            GroupBinding.tvGroupAction.visibility = if (ShowCapture) View.VISIBLE else View.GONE
            GroupBinding.ivGroupChevron.visibility = if (ShowCapture) View.VISIBLE else View.GONE

            GroupBinding.tvGroupCount.text = if (GroupRef.IsEmpty && GroupRef.IsCapturable) {
                getString(R.string.detail_group_not_captured)
            } else {
                getString(
                    R.string.detail_group_count_format,
                    GroupRef.CapturedCount,
                    GroupRef.TotalCount
                )
            }
            GroupBinding.tvGroupCount.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (GroupRef.IsComplete) R.color.text_secondary else R.color.status_amber_text
                )
            )

            if (ShowCapture) {
                GroupBinding.groupHeader.setOnClickListener { ViewRef ->
                    HapticFeedback.Confirm(ViewRef = ViewRef)
                    ResumeCaptureForPolicy()
                }
            }

            AddGroupFields(FieldContainer = GroupBinding.groupFields, GroupRef = GroupRef)

            if (GroupRef.IsEmpty && !GroupRef.IsCapturable) {
                GroupBinding.tvGroupEmpty.visibility = View.VISIBLE
                GroupBinding.tvGroupEmpty.setText(R.string.detail_group_empty_customer)
            }

            ViewBindingObj.groupContainer.addView(GroupBinding.root)
        }
    }

    private fun AddGroupFields(
        FieldContainer: LinearLayout,
        GroupRef: PolicyCompleteness.FieldGroup
    ) {
        FieldContainer.removeAllViews()
        for (FieldRef in GroupRef.Fields) {
            if (FieldRef.Value.isEmpty()) continue

            val RowBinding = PartialFieldRowBinding.inflate(layoutInflater, FieldContainer, false)
            RowBinding.tvFieldLabel.text = FieldRef.Label
            RowBinding.tvFieldValue.text = FormatForDisplay(FieldRef = FieldRef)


            RowBinding.root.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                CopyValue(LabelText = FieldRef.Label, ValueText = FieldRef.Value)
            }
            FieldContainer.addView(RowBinding.root)
        }
    }


    private fun FormatForDisplay(FieldRef: PolicyCompleteness.FieldEntry): String {
        if (!FieldRef.IsDate) return FieldRef.Value
        return ExportFormat.IsoDate(RawText = FieldRef.Value).ifEmpty { FieldRef.Value }
    }


    private fun BindRenewalHistory(PolicyRef: CustomerPolicy) {
        val RenewalList = PolicyRepository.GetFupPolicies(ContextRef = this)
            .filter { RenewalItem -> RenewalItem.PolicyNumber == PolicyRef.PolicyNumber }

        ViewBindingObj.tvRenewalMeta.text = if (RenewalList.isEmpty()) {
            getString(R.string.detail_renewals_none)
        } else {
            getString(R.string.detail_entries_format, RenewalList.size)
        }

        ViewBindingObj.rowRenewalHistory.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            if (RenewalList.isEmpty()) {
                StartCaptureFor(ModeVal = CaptureMode.FUP)
            } else {
                RenewalHistorySheet.NewInstance(PolicyNumber = PolicyRef.PolicyNumber)
                    .show(supportFragmentManager, RenewalHistorySheet.TAG)
            }
        }
    }


    private fun BindProvenance(PolicyRef: CustomerPolicy) {
        val CapturedLabel = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            .format(Date(PolicyRef.CapturedAt))
        val SessionRef = PolicyRepository.GetSessionReference(
            ContextRef = this,
            SessionId = ResolveSessionId()
        )
        val ModeLabel = getString(
            if (SessionRef?.CapturePolicyDetails == true) {
                R.string.detail_capture_full
            } else {
                R.string.detail_capture_fast
            }
        )

        ViewBindingObj.tvProvenance.text = if (SessionRef == null) {
            getString(R.string.detail_provenance_format, ModeLabel, CapturedLabel)
        } else {
            getString(
                R.string.detail_provenance_session_format,
                ModeLabel,
                CapturedLabel,
                SessionRef.SessionId.take(SESSION_ID_PREVIEW_LENGTH)
            )
        }
        ViewBindingObj.toolbar.subtitle =
            getString(R.string.detail_captured_at_format, CapturedLabel)
    }


    private fun ResumeCaptureForPolicy() {
        val StartedOk = CaptureFlow.Start(
            ActivityRef = this,
            ModeVal = CaptureMode.POLICY,
            LaunchTarget = true,
            CapturePolicyDetails = true,
            OriginOverride = MainActivity::class.java.name,
            ResumeSessionId = ResolveSessionId()
        )
        if (StartedOk) finish()
    }

    private fun StartCaptureFor(ModeVal: CaptureMode) {
        val StartedOk = CaptureFlow.Start(
            ActivityRef = this,
            ModeVal = ModeVal,
            LaunchTarget = true,
            OriginOverride = MainActivity::class.java.name
        )
        if (StartedOk) finish()
    }

    private fun ResolveSessionId(): String {
        return SessionIdVal.ifEmpty {
            PolicyRepository.GetLatestSessionId(ContextRef = this, ModeVal = CaptureMode.POLICY)
        }
    }


    private fun CopyValue(LabelText: String, ValueText: String) {
        if (ValueText.isEmpty()) return
        val ClipboardRef = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        ClipboardRef.setPrimaryClip(ClipData.newPlainText(LabelText, ValueText))
        ShowMessage(MessageVal = getString(R.string.detail_copied_format, LabelText))
    }

    private fun LaunchIntentSafely(IntentObj: Intent) {
        try {
            startActivity(IntentObj)
        } catch (_: Exception) {
            ShowMessage(MessageVal = getString(R.string.detail_missing))
        }
    }

    private fun ShowMessage(MessageVal: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            MessageVal,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun ExportSingle(PolicyRef: CustomerPolicy) {
        val ExportedFile = ExcelExporter.ExportCustomerPolicies(
            ContextRef = this,
            Policies = listOf(PolicyRef),
            AgencyCode = PolicyRepository.GetAgencyCode(
                ContextRef = this,
                SessionId = ResolveSessionId()
            )
        )
        val FileUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", ExportedFile)
        val ShareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, FileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(ShareIntent, getString(R.string.exports_share)))
    }

    private fun BuildLabels(): PolicyCompleteness.LabelSet {
        return PolicyCompleteness.LabelSet(
            CardTitle = getString(R.string.detail_group_card),
            PolicyDetailsTitle = getString(R.string.detail_group_policy_details),
            CommissionsTitle = getString(R.string.detail_group_commissions),
            KeyDatesTitle = getString(R.string.detail_group_key_dates),
            CustomerTitle = getString(R.string.detail_group_customer),
            PlanCode = getString(R.string.detail_plan_code),
            PlanName = getString(R.string.detail_plan_name),
            Status = getString(R.string.detail_status),
            Premium = getString(R.string.detail_premium),
            PremiumFrequency = getString(R.string.detail_premium_frequency),
            AutoPay = getString(R.string.detail_auto_pay),
            RenewalType = getString(R.string.detail_renewal_type),
            RenewalDue = getString(R.string.detail_renewal_due),
            SumAssured = getString(R.string.detail_sum_assured),
            TermPpt = getString(R.string.detail_term_ppt),
            CommissionType = getString(R.string.detail_commission_type),
            CommissionPaid = getString(R.string.detail_commission_paid),
            BonusCommission = getString(R.string.detail_bonus_commission),
            CommissionPaymentDate = getString(R.string.detail_commission_payment_date),
            CommissionPremiumDate = getString(R.string.detail_commission_premium_date),
            Commenced = getString(R.string.detail_commenced),
            PremiumsEnd = getString(R.string.detail_premiums_end),
            Matures = getString(R.string.detail_matures),
            Mobile = getString(R.string.detail_mobile),
            Dob = getString(R.string.detail_dob),
            Address = getString(R.string.detail_address),
            Email = getString(R.string.detail_email),
            Gender = getString(R.string.detail_gender),
            Education = getString(R.string.detail_education),
            Occupation = getString(R.string.detail_occupation),
            MaritalStatus = getString(R.string.detail_marital_status),
            AnnualIncome = getString(R.string.detail_annual_income),
            FlagKyc = getString(R.string.detail_flag_kyc),
            FlagNeft = getString(R.string.detail_flag_neft),
            FlagNominee = getString(R.string.detail_flag_nominee),
            FlagMobile = getString(R.string.detail_flag_mobile),
            FlagAddress = getString(R.string.detail_flag_address)
        )
    }

    companion object {
        const val EXTRA_POLICY_NUMBER = "extra_policy_number"
        const val EXTRA_SESSION_ID = "extra_session_id"
        private const val MIN_DIALABLE_DIGITS = 6
        private const val SESSION_ID_PREVIEW_LENGTH = 8
    }
}
