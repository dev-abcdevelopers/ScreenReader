@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SameParameterValue", "SpellCheckingInspection", "UsePropertyAccessSyntax", "unused"
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
import com.bliss.screenreader.databinding.PartialCaseContactBinding
import com.bliss.screenreader.databinding.PartialCaseStopBinding
import com.bliss.screenreader.databinding.PartialFieldRowBinding
import com.bliss.screenreader.databinding.PartialRenewalRowBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.export.ExportFormat
import com.bliss.screenreader.settings.SettingsStore
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.ui.capture.CaptureFlow
import com.bliss.screenreader.ui.main.MainActivity
import com.bliss.screenreader.ui.toast.AppToast
import com.bliss.screenreader.utils.HapticFeedback
import com.bliss.screenreader.data.parser.PolicyStatusRules
import com.bliss.screenreader.data.parser.StatusChipRules
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.isNotEmpty


class PolicyDetailActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityPolicyDetailBinding
    private var SessionIdVal: String = ""
    private var ActivePolicyObj: CustomerPolicy? = null
    private var RenewalRowBinding: PartialRenewalRowBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityPolicyDetailBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }
        ViewBindingObj.toolbar.inflateMenu(R.menu.menu_policy_detail)
        ViewBindingObj.toolbar.setOnMenuItemClickListener { MenuItemRef ->
            if (MenuItemRef.itemId != R.id.actionExportPolicy) {
                return@setOnMenuItemClickListener false
            }
            val PolicyRef = ActivePolicyObj
            if (PolicyRef != null) ExportSingle(PolicyRef = PolicyRef)
            true
        }

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

        ActivePolicyObj = ResolvedPolicy
        BindHeader(PolicyRef = ResolvedPolicy)
        BindVerdict(PolicyRef = ResolvedPolicy)
        BindActions(PolicyRef = ResolvedPolicy)
        BindLife(PolicyRef = ResolvedPolicy)
        BindCustomer(PolicyRef = ResolvedPolicy)
        BindRecord(PolicyRef = ResolvedPolicy)

        val SummaryVal = PolicyCompleteness.Describe(
            PolicyItem = ResolvedPolicy,
            Labels = BuildLabels()
        )
        BindFooter(PolicyRef = ResolvedPolicy, SummaryVal = SummaryVal)
    }

    override fun onResume() {
        super.onResume()
        RenderAdvancedVisibility()
    }


    private fun RenderAdvancedVisibility() {
        ViewBindingObj.toolbar.menu.findItem(R.id.actionExportPolicy)?.isVisible =
            SettingsStore.IsSessionExportVisible(ContextRef = this)
        RenewalRowBinding?.root?.visibility =
            if (SettingsStore.IsRenewalHistoryVisible(ContextRef = this)) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }


    private fun BindHeader(PolicyRef: CustomerPolicy) {
        ViewBindingObj.toolbar.title =
            PolicyRef.PolicyNumber.ifEmpty { getString(R.string.detail_toolbar_title) }

        ViewBindingObj.tvDetailHolder.text =
            PolicyRef.HolderName.ifEmpty { getString(R.string.status_unknown) }

        val PlanText = if (PolicyRef.PlanCode.isNotEmpty()) {
            "${PolicyRef.PlanCode} · ${PolicyRef.PlanName}"
        } else {
            PolicyRef.PlanName
        }
        ViewBindingObj.tvDetailPlan.text = PlanText
        ViewBindingObj.tvDetailPlan.visibility = if (PlanText.isBlank()) View.GONE else View.VISIBLE

        BindStatusChip(PolicyRef = PolicyRef)
        BindFlagChips(PolicyRef = PolicyRef)
    }

    private fun BindStatusChip(PolicyRef: CustomerPolicy) {
        val StatusText = PolicyRef.NormalizedStatus
        if (StatusText.isEmpty()) {
            ViewBindingObj.chipStatus.visibility = View.GONE
            return
        }
        ViewBindingObj.chipStatus.visibility = View.VISIBLE
        ViewBindingObj.chipStatus.text = StatusText
        ViewBindingObj.chipStatus.chipBackgroundColor =
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, StatusBackgroundFor(StatusText = StatusText))
            )
        ViewBindingObj.chipStatus.setTextColor(
            ContextCompat.getColor(this, StatusTextFor(StatusText = StatusText))
        )
    }

    private fun StatusBackgroundFor(StatusText: String): Int {
        return when {
            PolicyStatusRules.IsAdverse(StatusText = StatusText) -> R.color.status_red_bg
            PolicyStatusRules.IsAttention(StatusText = StatusText) -> R.color.status_amber_bg
            else -> R.color.status_green_bg
        }
    }

    private fun StatusTextFor(StatusText: String): Int {
        return when {
            PolicyStatusRules.IsAdverse(StatusText = StatusText) -> R.color.status_red_text
            PolicyStatusRules.IsAttention(StatusText = StatusText) -> R.color.status_amber_text
            else -> R.color.status_green_text
        }
    }


    private fun BindFlagChips(PolicyRef: CustomerPolicy) {
        ViewBindingObj.chipGroupFlags.removeAllViews()

        val CapturedChips = PolicyRef.StatusChips.orEmpty()
        if (CapturedChips.isNotEmpty()) {
            for (ChipText in CapturedChips) {
                when (StatusChipRules.PolarityOf(TextValue = ChipText)) {
                    StatusChipRules.Polarity.POSITIVE -> AddChip(
                        LabelText = ChipText,
                        BackgroundRes = R.color.status_green_bg,
                        TextColorRes = R.color.status_green_text
                    )

                    StatusChipRules.Polarity.NEGATIVE -> AddChip(
                        LabelText = ChipText,
                        BackgroundRes = R.color.status_red_bg,
                        TextColorRes = R.color.status_red_text
                    )

                    StatusChipRules.Polarity.NEUTRAL -> AddChip(
                        LabelText = ChipText,
                        BackgroundRes = R.color.status_blue_bg,
                        TextColorRes = R.color.status_blue_text
                    )
                }
            }
        } else {
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
        }

        ViewBindingObj.scrollFlags.visibility =
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


    private fun BindVerdict(PolicyRef: CustomerPolicy) {
        val StatusText = PolicyRef.NormalizedStatus
        val DueText = DisplayDate(RawText = PolicyRef.RenewalDueDate)
        val CardDateText = DisplayDate(RawText = PolicyRef.RenewalDateValue)
        val PremiumText = DisplayAmount(RawText = PolicyRef.PremiumAmount)
        val MissingText = getString(R.string.detail_missing)

        val BackgroundRes: Int
        val LeadColourRes: Int
        val LeadText: String
        val ValueText: String
        val SubText: String

        when {
            StatusText.equals(PolicyStatusRules.LAPSED, ignoreCase = true) -> {
                BackgroundRes = R.drawable.bg_verdict_bad
                LeadColourRes = R.color.status_red_text
                LeadText = PolicyRef.RenewalType
                    .ifEmpty { PolicyRef.RenewalDateLabel }
                    .ifEmpty { getString(R.string.detail_revival_default) }
                ValueText = CardDateText.ifEmpty { DueText }.ifEmpty { MissingText }
                SubText = StatusText
            }

            StatusText.equals(PolicyStatusRules.GRACE, ignoreCase = true) ||
                    StatusText.equals(PolicyStatusRules.OUTSTANDING, ignoreCase = true) -> {
                BackgroundRes = R.drawable.bg_verdict_warn
                LeadColourRes = R.color.status_amber_text
                LeadText = getString(
                    if (StatusText.equals(PolicyStatusRules.GRACE, ignoreCase = true)) {
                        R.string.detail_verdict_grace
                    } else {
                        R.string.detail_verdict_overdue
                    }
                )
                ValueText = if (PremiumText.isEmpty()) {
                    DueText.ifEmpty { CardDateText }.ifEmpty { MissingText }
                } else {
                    getString(R.string.detail_verdict_overdue_amount, PremiumText)
                }
                SubText = when {
                    DueText.isNotEmpty() -> getString(R.string.detail_verdict_due_on, DueText)
                    CardDateText.isNotEmpty() -> CardDateLine(
                        PolicyRef = PolicyRef,
                        DateText = CardDateText
                    )

                    else -> ""
                }
            }

            StatusText.equals(PolicyStatusRules.PAID_UP, ignoreCase = true) ||
                    StatusText.equals(PolicyStatusRules.REDUCED_PAID_UP, ignoreCase = true) -> {
                BackgroundRes = R.drawable.bg_verdict_neutral
                LeadColourRes = R.color.text_muted
                LeadText = StatusText
                ValueText = getString(R.string.detail_verdict_settled)
                SubText = DisplayAmount(RawText = PolicyRef.SumAssured)
            }

            PolicyStatusRules.IsSinglePremium(
                FrequencyText = PolicyRef.PremiumFrequency
            ) -> {
                BackgroundRes = R.drawable.bg_verdict_neutral
                LeadColourRes = R.color.text_muted
                LeadText = getString(R.string.detail_verdict_single_lead)
                ValueText = getString(R.string.detail_verdict_all_paid)
                SubText = SinglePremiumLine(PolicyRef = PolicyRef)
            }

            DueText.isNotEmpty() -> {
                BackgroundRes = R.drawable.bg_verdict_neutral
                LeadColourRes = R.color.text_muted
                LeadText = getString(R.string.detail_verdict_next)
                ValueText = DueText
                SubText = PremiumFrequencyLine(PolicyRef = PolicyRef)
            }

            CardDateText.isNotEmpty() -> {
                BackgroundRes = R.drawable.bg_verdict_neutral
                LeadColourRes = R.color.text_muted
                LeadText = PolicyRef.RenewalDateLabel
                    .ifEmpty { getString(R.string.detail_verdict_next) }
                ValueText = CardDateText
                SubText = PremiumFrequencyLine(PolicyRef = PolicyRef)
            }

            else -> {
                BackgroundRes = R.drawable.bg_verdict_neutral
                LeadColourRes = R.color.text_muted
                LeadText = getString(R.string.detail_verdict_next)
                ValueText = MissingText
                SubText = PremiumFrequencyLine(PolicyRef = PolicyRef)
            }
        }

        ViewBindingObj.verdictBlock.setBackgroundResource(BackgroundRes)
        ViewBindingObj.tvVerdictLead.text = LeadText
        ViewBindingObj.tvVerdictLead.setTextColor(ContextCompat.getColor(this, LeadColourRes))
        ViewBindingObj.tvVerdictValue.text = ValueText
        ViewBindingObj.tvVerdictSub.text = SubText
        ViewBindingObj.tvVerdictSub.visibility = if (SubText.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun CardDateLine(PolicyRef: CustomerPolicy, DateText: String): String {
        val LabelText = PolicyRef.RenewalDateLabel
        if (LabelText.isEmpty()) return DateText
        return getString(R.string.detail_verdict_card_date_format, LabelText, DateText)
    }

    private fun SinglePremiumLine(PolicyRef: CustomerPolicy): String {
        val PremiumText = DisplayAmount(RawText = PolicyRef.PremiumAmount)
        val MaturesText = DisplayDate(RawText = PolicyRef.DateOfMaturity)
        val MaturesLine = if (MaturesText.isEmpty()) {
            ""
        } else {
            getString(R.string.detail_verdict_matures_on, MaturesText)
        }
        if (PremiumText.isEmpty()) return MaturesLine
        if (MaturesLine.isEmpty()) return PremiumText
        return getString(R.string.detail_verdict_card_date_format, PremiumText, MaturesLine)
    }

    private fun PremiumFrequencyLine(PolicyRef: CustomerPolicy): String {
        val PremiumText = DisplayAmount(RawText = PolicyRef.PremiumAmount)
        if (PremiumText.isEmpty()) return ""
        if (PolicyRef.PremiumFrequency.isEmpty()) return PremiumText
        return getString(
            R.string.detail_verdict_premium_format,
            PremiumText,
            PolicyRef.PremiumFrequency
        )
    }


    private fun BindActions(PolicyRef: CustomerPolicy) {
        val MobileNumber = DialableNumber(RawText = PolicyRef.MobileNumber)
        val HasMobile = MobileNumber.length >= MIN_DIALABLE_DIGITS

        ViewBindingObj.btnCallCustomer.isEnabled = HasMobile
        ViewBindingObj.btnMessageCustomer.isEnabled = HasMobile

        ViewBindingObj.btnCallCustomer.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            DialNumber(NumberText = MobileNumber)
        }

        ViewBindingObj.btnMessageCustomer.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            LaunchIntentSafely(
                IntentObj = Intent(Intent.ACTION_SENDTO, "smsto:$MobileNumber".toUri())
            )
        }
    }

    private fun DialableNumber(RawText: String): String {
        return RawText.filter { CharValue -> CharValue.isDigit() || CharValue == '+' }
    }

    private fun DialNumber(NumberText: String) {
        LaunchIntentSafely(IntentObj = Intent(Intent.ACTION_DIAL, "tel:$NumberText".toUri()))
    }


    private fun BindLife(PolicyRef: CustomerPolicy) {
        ViewBindingObj.railContainer.removeAllViews()
        RenewalRowBinding = null

        val Labels = BuildLabels()
        val IsSingle = PolicyStatusRules.IsSinglePremium(
            FrequencyText = PolicyRef.PremiumFrequency
        )
        val IsOverdue = PolicyStatusRules.IsAdverse(StatusText = PolicyRef.NormalizedStatus) ||
                PolicyStatusRules.IsAttention(StatusText = PolicyRef.NormalizedStatus)
        val HasRealDue = PolicyRef.RenewalDueDate.isNotEmpty()
        val NowValue = if (HasRealDue) PolicyRef.RenewalDueDate else PolicyRef.RenewalDateValue
        val NowLabel = if (!HasRealDue && PolicyRef.RenewalDateValue.isNotEmpty() &&
            PolicyRef.RenewalDateLabel.isNotEmpty()
        ) {
            PolicyRef.RenewalDateLabel
        } else {
            getString(
                if (IsOverdue) R.string.detail_stop_overdue else R.string.detail_stop_next
            )
        }

        val StopList = buildList {
            add(Labels.Commenced to PolicyRef.DateOfCommencement)
            if (!IsSingle) add(NowLabel to NowValue)
            add(Labels.PremiumsEnd to PolicyRef.EndOfPremiumPayingTerm)
            add(Labels.Matures to PolicyRef.DateOfMaturity)
        }

        val TodayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val IsoList = StopList.map { StopItem -> ExportFormat.IsoDate(RawText = StopItem.second) }
        val PassedList = IsoList.map { IsoText ->
            IsoText.isNotEmpty() && IsoText <= TodayIso
        }
        val DueIndex = if (IsSingle) -1 else 1
        val NowIndex = when {
            DueIndex >= 0 && IsOverdue -> DueIndex
            else -> IsoList.indices.firstOrNull { StopIndex ->
                IsoList[StopIndex].isNotEmpty() && !PassedList[StopIndex]
            } ?: PassedList.indexOfLast { PassedVal -> PassedVal }
        }

        for ((StopIndex, StopItem) in StopList.withIndex()) {
            val StopBinding = PartialCaseStopBinding.inflate(
                layoutInflater, ViewBindingObj.railContainer, false
            )
            val ValueText = DisplayDate(RawText = StopItem.second)
            val IsGap = ValueText.isEmpty()
            val IsPassed = PassedList[StopIndex]
            val IsNow = StopIndex == NowIndex && !IsGap

            StopBinding.tvStopLabel.text = StopItem.first
            StopBinding.tvStopValue.text =
                ValueText.ifEmpty { getString(R.string.detail_capture_action) }

            val DotColourRes = when {
                IsGap -> R.color.text_faint
                IsNow -> R.color.text_accent
                IsPassed -> R.color.text_secondary
                else -> R.color.text_faint
            }
            StopBinding.railDot.backgroundTintList =
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, DotColourRes)
                )

            val ValueColourRes = when {
                IsGap -> R.color.text_accent
                IsNow -> R.color.text_accent
                else -> R.color.text_primary
            }
            StopBinding.tvStopValue.setTextColor(ContextCompat.getColor(this, ValueColourRes))
            if (IsNow) {
                StopBinding.tvStopLabel.setTextColor(
                    ContextCompat.getColor(this, R.color.text_accent)
                )
            }

            StopBinding.railTop.visibility =
                if (StopIndex == 0) View.INVISIBLE else View.VISIBLE
            StopBinding.railBottom.visibility =
                if (StopIndex == StopList.size - 1) View.INVISIBLE else View.VISIBLE
            PaintRail(
                RailView = StopBinding.railTop,
                IsTravelled = StopIndex > 0 && PassedList[StopIndex - 1] && IsPassed
            )
            PaintRail(
                RailView = StopBinding.railBottom,
                IsTravelled = IsPassed && PassedList.getOrElse(StopIndex + 1) { false }
            )

            if (IsGap) {
                StopBinding.stopRoot.setOnClickListener { ViewRef ->
                    HapticFeedback.Confirm(ViewRef = ViewRef)
                    ResumeCaptureForPolicy()
                }
            }

            ViewBindingObj.railContainer.addView(StopBinding.root)
        }

        val RowBinding = PartialRenewalRowBinding.inflate(
            layoutInflater, ViewBindingObj.railContainer, false
        )
        ViewBindingObj.railContainer.addView(RowBinding.root)
        RenewalRowBinding = RowBinding
        BindRenewalHistory(PolicyRef = PolicyRef)
    }

    private fun PaintRail(RailView: View, IsTravelled: Boolean) {
        RailView.setBackgroundColor(
            ContextCompat.getColor(
                this,
                if (IsTravelled) R.color.text_faint else R.color.divider
            )
        )
    }

    private fun BindRenewalHistory(PolicyRef: CustomerPolicy) {
        val RowBinding = RenewalRowBinding ?: return
        val RenewalList = PolicyRepository.GetFupPolicies(ContextRef = this)
            .filter { RenewalItem -> RenewalItem.PolicyNumber == PolicyRef.PolicyNumber }

        RowBinding.tvRenewalMeta.text = if (RenewalList.isEmpty()) {
            getString(R.string.detail_renewals_none)
        } else {
            getString(R.string.detail_entries_format, RenewalList.size)
        }

        RowBinding.rowRenewalHistory.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            if (RenewalList.isEmpty()) {
                StartCaptureFor(ModeVal = CaptureMode.FUP)
            } else {
                RenewalHistorySheet.NewInstance(PolicyNumber = PolicyRef.PolicyNumber)
                    .show(supportFragmentManager, RenewalHistorySheet.TAG)
            }
        }
    }


    private fun BindCustomer(PolicyRef: CustomerPolicy) {
        ViewBindingObj.contactContainer.removeAllViews()
        ViewBindingObj.personalContainer.removeAllViews()

        val Labels = BuildLabels()
        AddContactGroup(
            IconRes = R.drawable.ic_phone,
            KindLabel = Labels.Mobile,
            PrimaryValue = PolicyRef.MobileNumber,
            OtherValues = PolicyRef.MobileNumberOthers.orEmpty(),
            IsDialable = true
        )
        AddContactGroup(
            IconRes = R.drawable.ic_message,
            KindLabel = Labels.Email,
            PrimaryValue = PolicyRef.Email,
            OtherValues = PolicyRef.EmailOthers.orEmpty(),
            IsDialable = false
        )
        AddContactGroup(
            IconRes = R.drawable.ic_person,
            KindLabel = Labels.Address,
            PrimaryValue = PolicyRef.Address,
            OtherValues = PolicyRef.AddressOthers.orEmpty(),
            IsDialable = false
        )

        AddRecordRow(
            Container = ViewBindingObj.personalContainer,
            LabelText = Labels.Dob,
            ValueText = DisplayDate(RawText = PolicyRef.Dob),
            ShowGap = true,
            OnGap = { RefreshCustomerDetails() }
        )
        AddRecordRow(
            Container = ViewBindingObj.personalContainer,
            LabelText = Labels.Gender,
            ValueText = PolicyRef.Gender
        )
        AddRecordRow(
            Container = ViewBindingObj.personalContainer,
            LabelText = Labels.Education,
            ValueText = PolicyRef.Education
        )
        AddRecordRow(
            Container = ViewBindingObj.personalContainer,
            LabelText = Labels.Occupation,
            ValueText = PolicyRef.Occupation
        )
        AddRecordRow(
            Container = ViewBindingObj.personalContainer,
            LabelText = Labels.MaritalStatus,
            ValueText = PolicyRef.MaritalStatus
        )
        AddRecordRow(
            Container = ViewBindingObj.personalContainer,
            LabelText = Labels.AnnualIncome,
            ValueText = DisplayAmount(RawText = PolicyRef.AnnualIncome)
        )

        val HasContacts = ViewBindingObj.contactContainer.isNotEmpty()
        val HasPersonal = ViewBindingObj.personalContainer.isNotEmpty()
        ViewBindingObj.contactCard.visibility = if (HasContacts) View.VISIBLE else View.GONE
        ViewBindingObj.personalCard.visibility = if (HasPersonal) View.VISIBLE else View.GONE
        ViewBindingObj.customerEmpty.visibility =
            if (HasContacts || HasPersonal) View.GONE else View.VISIBLE
        ViewBindingObj.btnCaptureCustomer.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            RefreshCustomerDetails()
        }
        ViewBindingObj.btnRefreshCustomer.visibility =
            if (HasContacts || HasPersonal) View.VISIBLE else View.GONE
        ViewBindingObj.btnRefreshCustomer.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            RefreshCustomerDetails()
        }
    }

    private fun AddContactGroup(
        IconRes: Int,
        KindLabel: String,
        PrimaryValue: String,
        OtherValues: List<String>,
        IsDialable: Boolean
    ) {
        if (PrimaryValue.isEmpty()) return
        AddContactRow(
            IconRes = IconRes,
            KindLabel = KindLabel,
            ValueText = PrimaryValue,
            MetaText = if (OtherValues.isEmpty()) {
                ""
            } else {
                getString(R.string.detail_contact_this_policy)
            },
            IsDialable = IsDialable
        )
        for (OtherValue in OtherValues) {
            AddContactRow(
                IconRes = IconRes,
                KindLabel = KindLabel,
                ValueText = OtherValue,
                MetaText = getString(R.string.detail_contact_other),
                IsDialable = IsDialable
            )
        }
    }

    private fun AddContactRow(
        IconRes: Int,
        KindLabel: String,
        ValueText: String,
        MetaText: String,
        IsDialable: Boolean
    ) {
        val RowBinding = PartialCaseContactBinding.inflate(
            layoutInflater, ViewBindingObj.contactContainer, false
        )
        RowBinding.ivContactIcon.setImageResource(IconRes)
        RowBinding.tvContactValue.text = ValueText
        RowBinding.tvContactMeta.text = MetaText
        RowBinding.tvContactMeta.visibility = if (MetaText.isEmpty()) View.GONE else View.VISIBLE
        RowBinding.tvContactAction.setText(
            if (IsDialable) R.string.detail_action_call else R.string.detail_action_copy
        )
        RowBinding.contactRoot.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            if (IsDialable) {
                DialNumber(NumberText = DialableNumber(RawText = ValueText))
            } else {
                CopyValue(LabelText = KindLabel, ValueText = ValueText)
            }
        }
        ViewBindingObj.contactContainer.addView(RowBinding.root)
    }


    private fun BindRecord(PolicyRef: CustomerPolicy) {
        ViewBindingObj.recordContainer.removeAllViews()
        val Labels = BuildLabels()
        val Container = ViewBindingObj.recordContainer

        AddRecordRow(
            Container = Container,
            LabelText = Labels.Premium,
            ValueText = DisplayAmount(RawText = PolicyRef.PremiumAmount),
            ShowGap = true,
            OnGap = { ResumeCaptureForPolicy() }
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.PremiumFrequency,
            ValueText = PolicyRef.PremiumFrequency
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.AutoPay,
            ValueText = PolicyRef.AutoPay
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.SumAssured,
            ValueText = DisplayAmount(RawText = PolicyRef.SumAssured),
            ShowGap = true,
            OnGap = { ResumeCaptureForPolicy() }
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.TermPpt,
            ValueText = PolicyRef.TermPPT,
            ShowGap = true,
            OnGap = { ResumeCaptureForPolicy() }
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.RenewalType,
            ValueText = PolicyRef.RenewalType
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.CommissionType,
            ValueText = PolicyRef.CommissionType,
            ShowGap = true,
            OnGap = { ResumeCaptureForPolicy() }
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.CommissionPaid,
            ValueText = DisplayAmount(RawText = PolicyRef.CommissionPaidAmount)
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.BonusCommission,
            ValueText = DisplayAmount(RawText = PolicyRef.BonusCommission)
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.CommissionPaymentDate,
            ValueText = DisplayDate(RawText = PolicyRef.CommissionDateOfPayment)
        )
        AddRecordRow(
            Container = Container,
            LabelText = Labels.CommissionPremiumDate,
            ValueText = DisplayDate(RawText = PolicyRef.CommissionDateOfPremiumPayment)
        )

        ViewBindingObj.recordCard.visibility =
            if (Container.childCount == 0) View.GONE else View.VISIBLE
        ViewBindingObj.headerRecord.visibility = ViewBindingObj.recordCard.visibility
    }

    private fun AddRecordRow(
        Container: LinearLayout,
        LabelText: String,
        ValueText: String,
        ShowGap: Boolean = false,
        OnGap: (() -> Unit)? = null
    ) {
        val IsGap = ValueText.isEmpty()
        if (IsGap && !ShowGap) return

        val RowBinding = PartialFieldRowBinding.inflate(layoutInflater, Container, false)
        RowBinding.tvFieldLabel.text = LabelText
        RowBinding.tvFieldValue.text =
            ValueText.ifEmpty { getString(R.string.detail_capture_action) }
        RowBinding.tvFieldValue.setTextColor(
            ContextCompat.getColor(
                this,
                if (IsGap) R.color.text_accent else R.color.text_primary
            )
        )
        if (IsGap && OnGap != null) {
            RowBinding.root.setOnClickListener { ViewRef ->
                HapticFeedback.Confirm(ViewRef = ViewRef)
                OnGap()
            }
        }
        Container.addView(RowBinding.root)
    }


    private fun BindFooter(PolicyRef: CustomerPolicy, SummaryVal: PolicyCompleteness.Summary) {
        val CapturedLabel = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            .format(Date(PolicyRef.CapturedAt))
        ViewBindingObj.tvProvenance.text = getString(
            R.string.detail_provenance_short,
            SummaryVal.CapturedCount,
            SummaryVal.TotalCount,
            CapturedLabel
        )
        ViewBindingObj.btnUpdate.visibility =
            if (SummaryVal.IsComplete) View.GONE else View.VISIBLE
        ViewBindingObj.btnUpdate.setOnClickListener { ViewRef ->
            HapticFeedback.Confirm(ViewRef = ViewRef)
            ResumeCaptureForPolicy()
        }
        ViewBindingObj.toolbar.subtitle =
            getString(R.string.detail_captured_at_format, CapturedLabel)
    }

    private fun DisplayAmount(RawText: String): String {
        val Trimmed = RawText.trim()
        if (Trimmed.isEmpty()) return ""
        if (Trimmed.contains('₹')) return Trimmed
        if (!Trimmed.first().isDigit()) return Trimmed
        return getString(R.string.detail_amount_format, Trimmed)
    }

    private fun DisplayDate(RawText: String): String {
        val IsoText = ExportFormat.IsoDate(RawText = RawText)
        if (IsoText.isEmpty()) return RawText.trim()
        val ParsedDate = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(IsoText)
        } catch (_: Exception) {
            null
        }
        if (ParsedDate == null) return IsoText
        return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(ParsedDate)
    }


    private fun ResumeCaptureForPolicy() {
        val PolicyRef = ActivePolicyObj
        val TargetNumber = PolicyRef?.PolicyNumber.orEmpty()
        val StartedOk = CaptureFlow.Start(
            ActivityRef = this,
            ModeVal = CaptureMode.POLICY,
            LaunchTarget = true,
            CapturePolicyDetails = true,
            OriginOverride = MainActivity::class.java.name,
            ResumeSessionId = ResolveSessionId(),
            TargetPolicyNumbers = if (TargetNumber.isEmpty()) {
                emptyList()
            } else {
                listOf(TargetNumber)
            },
            TargetNameHints = if (TargetNumber.isEmpty()) {
                emptyMap()
            } else {
                mapOf(TargetNumber to PolicyRef?.HolderName.orEmpty())
            },
            ChainCustomerName = if (TargetNumber.isEmpty()) {
                ""
            } else {
                PolicyRef?.HolderName.orEmpty()
            }
        )
        if (StartedOk) finish()
    }

    private fun RefreshCustomerDetails() {
        val HolderName = ActivePolicyObj?.HolderName.orEmpty().trim()
        if (HolderName.isEmpty()) {
            ShowMessage(
                MessageVal = getString(R.string.detail_refresh_no_holder),
                KindVal = AppToast.Kind.Warning
            )
            return
        }
        val StartedOk = CaptureFlow.Start(
            ActivityRef = this,
            ModeVal = CaptureMode.CUSTOMER,
            LaunchTarget = true,
            OriginOverride = MainActivity::class.java.name,
            ResumeSessionId = ResolveSessionId(),
            RevisitFilled = true,
            TargetCustomerNames = listOf(HolderName)
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
        ShowMessage(
            MessageVal = getString(R.string.detail_copied_format, LabelText),
            KindVal = AppToast.Kind.Success
        )
    }

    private fun LaunchIntentSafely(IntentObj: Intent) {
        try {
            startActivity(IntentObj)
        } catch (_: Exception) {
            ShowMessage(
                MessageVal = getString(R.string.detail_missing),
                KindVal = AppToast.Kind.Error
            )
        }
    }

    private fun ShowMessage(
        MessageVal: String,
        KindVal: AppToast.Kind = AppToast.Kind.Info
    ) {
        AppToast.Show(ContextRef = this, MessageText = MessageVal, KindVal = KindVal)
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
        private const val STOP_PAST = 0
        private const val STOP_NOW = 1
        private const val STOP_FUTURE = 2
        private const val SESSION_ID_PREVIEW_LENGTH = 8
    }
}
