@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.ActivityPolicyDetailBinding
import com.bliss.screenreader.databinding.PartialDetailSectionBinding
import com.bliss.screenreader.databinding.PartialFieldRowBinding
import com.bliss.screenreader.databinding.PartialMetricCellBinding
import com.bliss.screenreader.export.ExcelExporter
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.ui.capture.CaptureFlow
import com.bliss.screenreader.ui.main.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The record view the app was missing. CustomerPolicy carries 27 fields and the
 * old list surfaced six of them, so everything else was captured, stored and
 * invisible unless you exported to Excel.
 *
 * Sections that have no data are not hidden — they are shown with a Capture
 * action, which is how the next capture gets started from a policy rather than
 * from a mode menu.
 */
class PolicyDetailActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityPolicyDetailBinding
    private var PolicyItem: CustomerPolicy? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityPolicyDetailBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        val PolicyNumber = intent.getStringExtra(EXTRA_POLICY_NUMBER).orEmpty()
        PolicyItem = PolicyRepository.GetCustomerPolicies(ContextRef = this)
            .firstOrNull { it.PolicyNumber == PolicyNumber }

        val ResolvedPolicy = PolicyItem
        if (ResolvedPolicy == null) {
            finish()
            return
        }

        BindHeader(PolicyRef = ResolvedPolicy)
        BindMetrics(PolicyRef = ResolvedPolicy)
        BindSections(PolicyRef = ResolvedPolicy)
        BindMoreFields(PolicyRef = ResolvedPolicy)

        ViewBindingObj.btnExportPolicy.setOnClickListener { ExportSingle(PolicyRef = ResolvedPolicy) }
    }

    // ---------------------------------------------------------------- header

    private fun BindHeader(PolicyRef: CustomerPolicy) {
        ViewBindingObj.tvDetailNumber.text =
            PolicyRef.PolicyNumber.ifEmpty { getString(R.string.detail_missing) }
        ViewBindingObj.tvDetailHolder.text = buildString {
            append(PolicyRef.HolderName.ifEmpty { getString(R.string.status_unknown) })
            if (PolicyRef.Age.isNotEmpty()) append(" · ").append(PolicyRef.Age)
        }

        val PlanText = if (PolicyRef.PlanCode.isNotEmpty()) {
            "${PolicyRef.PlanCode} — ${PolicyRef.PlanName}"
        } else {
            PolicyRef.PlanName
        }
        ViewBindingObj.tvDetailPlan.text = PlanText
        ViewBindingObj.tvDetailPlan.visibility = if (PlanText.isBlank()) View.GONE else View.VISIBLE

        val StatusText = PolicyRef.NormalizedStatus
        if (StatusText.isEmpty()) {
            ViewBindingObj.tvDetailStatus.visibility = View.GONE
        } else {
            val IsLapsed = StatusText.equals("Lapsed", ignoreCase = true)
            ViewBindingObj.tvDetailStatus.text = StatusText
            ViewBindingObj.tvDetailStatus.setBackgroundResource(
                if (IsLapsed) R.drawable.bg_badge_lapsed else R.drawable.bg_badge_inforce
            )
            ViewBindingObj.tvDetailStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (IsLapsed) R.color.status_red_text else R.color.status_green_text
                )
            )
        }
    }

    private fun BindMetrics(PolicyRef: CustomerPolicy) {
        BindMetricCell(
            CellBinding = ViewBindingObj.cellPremium,
            LabelRes = R.string.detail_premium,
            ValueText = PolicyRef.PremiumAmount
        )
        BindMetricCell(
            CellBinding = ViewBindingObj.cellSumAssured,
            LabelRes = R.string.detail_sum_assured,
            ValueText = PolicyRef.SumAssured
        )
        BindMetricCell(
            CellBinding = ViewBindingObj.cellNextDue,
            LabelRes = R.string.detail_next_due,
            ValueText = PolicyRef.RenewalDueDate
        )
        BindMetricCell(
            CellBinding = ViewBindingObj.cellTermPpt,
            LabelRes = R.string.detail_term_ppt,
            ValueText = PolicyRef.TermPPT
        )
    }

    /** A missing figure shows a dash in faint type rather than being hidden. */
    private fun BindMetricCell(
        CellBinding: PartialMetricCellBinding,
        LabelRes: Int,
        ValueText: String
    ) {
        CellBinding.tvMetricLabel.setText(LabelRes)
        CellBinding.tvMetricValue.text = ValueText.ifEmpty { getString(R.string.detail_missing) }
        CellBinding.tvMetricValue.setTextColor(
            ContextCompat.getColor(
                this,
                if (ValueText.isEmpty()) R.color.text_faint else R.color.text_primary
            )
        )
    }

    // -------------------------------------------------------------- sections

    private fun BindSections(PolicyRef: CustomerPolicy) {
        ViewBindingObj.sectionGroup.removeAllViews()

        val ProfileFieldCount = listOf(
            PolicyRef.HolderName, PolicyRef.Dob, PolicyRef.MobileNumber, PolicyRef.Address
        ).count { it.isNotEmpty() }

        AddSection(
            IconRes = R.drawable.ic_person,
            TitleRes = R.string.detail_section_profile,
            MetaText = if (ProfileFieldCount > 0) {
                getString(R.string.detail_fields_format, ProfileFieldCount)
            } else {
                ""
            },
            ModeVal = CaptureMode.POLICY
        )

        val ServicingCount = PolicyRepository.GetPsPolicies(ContextRef = this)
            .count { it.PolicyNumber == PolicyRef.PolicyNumber }
        AddSection(
            IconRes = R.drawable.ic_history,
            TitleRes = R.string.detail_section_servicing,
            MetaText = if (ServicingCount > 0) {
                getString(R.string.detail_entries_format, ServicingCount)
            } else {
                ""
            },
            ModeVal = CaptureMode.PS
        )

        val RenewalCount = PolicyRepository.GetFupPolicies(ContextRef = this)
            .count { it.PolicyNumber == PolicyRef.PolicyNumber }
        AddSection(
            IconRes = R.drawable.ic_calendar_repeat,
            TitleRes = R.string.detail_section_renewals,
            MetaText = if (RenewalCount > 0) {
                getString(R.string.detail_entries_format, RenewalCount)
            } else {
                ""
            },
            ModeVal = CaptureMode.FUP
        )
    }

    /** Empty sections show a Capture action instead of just reading "0". */
    private fun AddSection(IconRes: Int, TitleRes: Int, MetaText: String, ModeVal: CaptureMode) {
        val SectionBinding = PartialDetailSectionBinding.inflate(
            layoutInflater, ViewBindingObj.sectionGroup, false
        )
        SectionBinding.ivSectionIcon.setImageResource(IconRes)
        SectionBinding.tvSectionTitle.setText(TitleRes)

        val HasData = MetaText.isNotEmpty()
        SectionBinding.tvSectionMeta.text = MetaText
        SectionBinding.tvSectionMeta.visibility = if (HasData) View.VISIBLE else View.GONE
        SectionBinding.tvSectionAction.visibility = if (HasData) View.GONE else View.VISIBLE

        if (!HasData) {
            SectionBinding.sectionRoot.setOnClickListener { StartCaptureFor(ModeVal = ModeVal) }
        }

        ViewBindingObj.sectionGroup.addView(SectionBinding.root)
    }

    private fun StartCaptureFor(ModeVal: CaptureMode) {
        val StartedOk = CaptureFlow.Start(
            ActivityRef = this,
            ModeVal = ModeVal,
            LaunchTarget = ModeVal == CaptureMode.POLICY,
            OriginOverride = MainActivity::class.java.name
        )
        if (StartedOk) finish()
    }

    /** Everything captured that the headline figures do not already show. */
    private fun BindMoreFields(PolicyRef: CustomerPolicy) {
        ViewBindingObj.moreFieldsGroup.removeAllViews()

        val Rows = listOf(
            getString(R.string.detail_dob) to PolicyRef.Dob,
            getString(R.string.detail_mobile) to PolicyRef.MobileNumber,
            getString(R.string.detail_doc) to PolicyRef.DateOfCommencement,
            getString(R.string.detail_eppt) to PolicyRef.EndOfPremiumPayingTerm,
            getString(R.string.detail_maturity) to PolicyRef.DateOfMaturity,
            getString(R.string.detail_address) to PolicyRef.Address
        ).filter { it.second.isNotEmpty() }

        if (Rows.isEmpty()) {
            ViewBindingObj.tvMoreHeading.visibility = View.GONE
            return
        }

        ViewBindingObj.tvMoreHeading.visibility = View.VISIBLE
        for ((LabelText, ValueText) in Rows) {
            val RowBinding = PartialFieldRowBinding.inflate(
                layoutInflater, ViewBindingObj.moreFieldsGroup, false
            )
            RowBinding.tvFieldLabel.text = LabelText
            RowBinding.tvFieldValue.text = ValueText
            ViewBindingObj.moreFieldsGroup.addView(RowBinding.root)
        }

        val CapturedLabel = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            .format(Date(PolicyRef.CapturedAt))
        ViewBindingObj.toolbar.subtitle = getString(R.string.detail_captured_at_format, CapturedLabel)
    }

    private fun ExportSingle(PolicyRef: CustomerPolicy) {
        val ExportedFile = ExcelExporter.ExportCustomerPolicies(
            ContextRef = this, Policies = listOf(PolicyRef)
        )
        val FileUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", ExportedFile)
        val ShareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, FileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(ShareIntent, getString(R.string.exports_share)))
    }

    companion object {
        const val EXTRA_POLICY_NUMBER = "extra_policy_number"
    }
}
