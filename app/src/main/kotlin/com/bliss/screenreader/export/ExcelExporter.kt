@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.export

import android.content.Context
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.PsPolicy
import com.bliss.screenreader.data.parser.ContactValueSplit
import com.bliss.screenreader.service.CaptureDiagnostics
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {

    fun ExportCustomerPolicies(
        ContextRef: Context,
        Policies: List<CustomerPolicy>,
        AgencyCode: String = ""
    ): File {
        ExportFormat.ResetDiagnostics()

        val MobileValues = Policies.map { PolicyItem ->
            ContactValueSplit.Explode(
                PrimaryValue = PolicyItem.MobileNumber,
                OtherValues = PolicyItem.MobileNumberOthers
            )
        }
        val AddressValues = Policies.map { PolicyItem ->
            ContactValueSplit.Explode(
                PrimaryValue = PolicyItem.Address,
                OtherValues = PolicyItem.AddressOthers
            )
        }
        val EmailValues = Policies.map { PolicyItem ->
            ContactValueSplit.Explode(
                PrimaryValue = PolicyItem.Email,
                OtherValues = PolicyItem.EmailOthers
            )
        }

        val MobileExtras = ExtraColumnCount(ValueLists = MobileValues)
        val AddressExtras = ExtraColumnCount(ValueLists = AddressValues)
        val EmailExtras = ExtraColumnCount(ValueLists = EmailValues)

        val WorkbookObj = XSSFWorkbook()
        val SheetObj = WorkbookObj.createSheet("Customer Policies")

        val HeadersList = mutableListOf(
            "Agency Code", "Policy Number", "Holder Name", "Plan Code", "Plan Name",
            "Status",
            "Premium Amount", "Premium Frequency", "Auto Pay", "Renewal Type",
            "Renewal Due Date", "KYC Status", "NEFT Status", "Sum Assured",
            "Term Years", "PPT Years", "Date of Commencement",
            "End of Premium Paying Term", "Date of Maturity"
        )
        AddContactHeaders(HeadersList = HeadersList, BaseName = "Mobile", ExtraCount = MobileExtras)
        HeadersList.add("DOB")
        AddContactHeaders(
            HeadersList = HeadersList,
            BaseName = "Address",
            ExtraCount = AddressExtras
        )
        AddContactHeaders(HeadersList = HeadersList, BaseName = "Email", ExtraCount = EmailExtras)
        HeadersList.addAll(
            listOf(
                "Gender", "Education", "Occupation", "Marital Status", "Annual Income",
                "Date of Premium Payment", "Date of Commission Payment", "Commission Type",
                "Bonus Commission", "Commission Paid Amount"
            )
        )

        WriteHeaders(RowRef = SheetObj.createRow(0), HeadersList = HeadersList.toTypedArray())

        var RowIdx = 1
        for (PolicyIndex in Policies.indices) {
            val PolicyItem = Policies[PolicyIndex]
            val DataRow = SheetObj.createRow(RowIdx++)
            var ColumnIdx = 0
            WriteText(DataRow, ColumnIdx++, AgencyCode)
            WriteText(DataRow, ColumnIdx++, ExportFormat.Identifier(PolicyItem.PolicyNumber))
            WriteText(DataRow, ColumnIdx++, PolicyItem.HolderName)
            WriteText(DataRow, ColumnIdx++, PolicyItem.PlanCode)
            WriteText(DataRow, ColumnIdx++, PolicyItem.PlanName)
            WriteText(DataRow, ColumnIdx++, PolicyItem.NormalizedStatus)
            WriteNumber(DataRow, ColumnIdx++, ExportFormat.PlainNumber(PolicyItem.PremiumAmount))
            WriteText(
                DataRow, ColumnIdx++,
                PolicyItem.PremiumFrequency.ifEmpty {
                    ExportFormat.AmountFrequency(PolicyItem.PremiumAmount)
                }
            )
            WriteText(DataRow, ColumnIdx++, PolicyItem.AutoPay)
            WriteText(DataRow, ColumnIdx++, PolicyItem.RenewalType)
            WriteText(DataRow, ColumnIdx++, ExportFormat.IsoDate(PolicyItem.RenewalDueDate))
            WriteText(DataRow, ColumnIdx++, PolicyItem.KycStatus)
            WriteText(DataRow, ColumnIdx++, PolicyItem.NeftStatus)
            WriteNumber(DataRow, ColumnIdx++, ExportFormat.PlainNumber(PolicyItem.SumAssured))
            WriteNumber(DataRow, ColumnIdx++, ExportFormat.TermYears(PolicyItem.TermPPT))
            WriteNumber(DataRow, ColumnIdx++, ExportFormat.PptYears(PolicyItem.TermPPT))
            WriteText(DataRow, ColumnIdx++, ExportFormat.IsoDate(PolicyItem.DateOfCommencement))
            WriteText(DataRow, ColumnIdx++, ExportFormat.IsoDate(PolicyItem.EndOfPremiumPayingTerm))
            WriteText(DataRow, ColumnIdx++, ExportFormat.IsoDate(PolicyItem.DateOfMaturity))
            ColumnIdx = WriteContactGroup(
                RowRef = DataRow,
                StartColumn = ColumnIdx,
                ValuesList = MobileValues[PolicyIndex],
                ExtraCount = MobileExtras,
                AsIdentifier = true
            )
            WriteText(DataRow, ColumnIdx++, ExportFormat.IsoDate(PolicyItem.Dob))
            ColumnIdx = WriteContactGroup(
                RowRef = DataRow,
                StartColumn = ColumnIdx,
                ValuesList = AddressValues[PolicyIndex],
                ExtraCount = AddressExtras,
                AsIdentifier = false
            )
            ColumnIdx = WriteContactGroup(
                RowRef = DataRow,
                StartColumn = ColumnIdx,
                ValuesList = EmailValues[PolicyIndex],
                ExtraCount = EmailExtras,
                AsIdentifier = false
            )
            WriteText(DataRow, ColumnIdx++, PolicyItem.Gender)
            WriteText(DataRow, ColumnIdx++, PolicyItem.Education)
            WriteText(DataRow, ColumnIdx++, PolicyItem.Occupation)
            WriteText(DataRow, ColumnIdx++, PolicyItem.MaritalStatus)
            WriteText(DataRow, ColumnIdx++, PolicyItem.AnnualIncome)
            WriteText(
                DataRow, ColumnIdx++,
                ExportFormat.IsoDate(PolicyItem.CommissionDateOfPremiumPayment)
            )
            WriteText(
                DataRow, ColumnIdx++,
                ExportFormat.IsoDate(PolicyItem.CommissionDateOfPayment)
            )
            WriteText(DataRow, ColumnIdx++, PolicyItem.CommissionType)
            WriteNumber(
                DataRow, ColumnIdx++,
                ExportFormat.PlainNumber(PolicyItem.BonusCommission)
            )
            WriteNumber(
                DataRow, ColumnIdx,
                ExportFormat.PlainNumber(PolicyItem.CommissionPaidAmount)
            )
        }

        return FinishWorkbook(
            ContextRef = ContextRef,
            WorkbookObj = WorkbookObj,
            FilePrefix = "Policy_Export"
        )
    }

    private fun ExtraColumnCount(ValueLists: List<List<String>>): Int {
        val Largest = ValueLists.maxOfOrNull { ValuesList -> ValuesList.size } ?: 0
        return if (Largest > 1) Largest - 1 else 0
    }

    private fun AddContactHeaders(
        HeadersList: MutableList<String>,
        BaseName: String,
        ExtraCount: Int
    ) {
        HeadersList.add(BaseName)
        for (ExtraIndex in 1..ExtraCount) HeadersList.add("$BaseName $ExtraIndex")
    }

    private fun WriteContactGroup(
        RowRef: Row,
        StartColumn: Int,
        ValuesList: List<String>,
        ExtraCount: Int,
        AsIdentifier: Boolean
    ): Int {
        var ColumnIdx = StartColumn
        for (ValueIndex in 0..ExtraCount) {
            val ValueText = ValuesList.getOrNull(ValueIndex).orEmpty()
            WriteText(
                RowRef,
                ColumnIdx++,
                if (AsIdentifier) ExportFormat.Identifier(ValueText) else ValueText
            )
        }
        return ColumnIdx
    }

    fun ExportFupPolicies(
        ContextRef: Context,
        Policies: List<FupPolicy>,
        AgencyCode: String = ""
    ): File {
        ExportFormat.ResetDiagnostics()

        val WorkbookObj = XSSFWorkbook()
        val SheetObj = WorkbookObj.createSheet("FUP Renewal History")

        WriteHeaders(
            RowRef = SheetObj.createRow(0),
            HeadersList = arrayOf(
                "Agency Code", "Policy Number", "Plan Code", "Plan Name", "Holder Name",
                "Premium Amount", "Premium Frequency", "Due Date", "Payment Date",
                "Mode of Payment", "Status at Time of Payment"
            )
        )

        var RowIdx = 1
        for (PolicyItem in Policies) {
            val DataRow = SheetObj.createRow(RowIdx++)
            WriteText(DataRow, 0, AgencyCode)
            WriteText(DataRow, 1, ExportFormat.Identifier(PolicyItem.PolicyNumber))
            WriteText(DataRow, 2, ExportFormat.Identifier(PolicyItem.PlanCode))
            WriteText(DataRow, 3, PolicyItem.PlanName)
            WriteText(DataRow, 4, PolicyItem.HolderName)
            WriteNumber(DataRow, 5, ExportFormat.PlainNumber(PolicyItem.PremiumAmount))
            WriteText(DataRow, 6, ExportFormat.AmountFrequency(PolicyItem.PremiumAmount))
            WriteText(DataRow, 7, ExportFormat.IsoDate(PolicyItem.DueDate))
            WriteText(DataRow, 8, ExportFormat.IsoDate(PolicyItem.PaymentDate))
            WriteText(DataRow, 9, PolicyItem.ModeOfPayment)
            WriteText(DataRow, 10, PolicyItem.Status)
        }

        return FinishWorkbook(
            ContextRef = ContextRef,
            WorkbookObj = WorkbookObj,
            FilePrefix = "FUP_Export"
        )
    }

    fun ExportPsPolicies(ContextRef: Context, Policies: List<PsPolicy>): File {
        ExportFormat.ResetDiagnostics()

        val WorkbookObj = XSSFWorkbook()
        val SheetObj = WorkbookObj.createSheet("PS Servicing Data")

        WriteHeaders(
            RowRef = SheetObj.createRow(0),
            HeadersList = arrayOf(
                "Policy Number", "Holder Name", "Date of Commencement", "Premium Amount",
                "Premium Frequency", "FUP", "Status"
            )
        )

        var RowIdx = 1
        for (PolicyItem in Policies) {
            val DataRow = SheetObj.createRow(RowIdx++)
            WriteText(DataRow, 0, ExportFormat.Identifier(PolicyItem.PolicyNumber))
            WriteText(DataRow, 1, PolicyItem.HolderName)
            WriteText(DataRow, 2, ExportFormat.IsoDate(PolicyItem.Doc))
            WriteNumber(DataRow, 3, ExportFormat.PlainNumber(PolicyItem.PremiumAmount))
            WriteText(DataRow, 4, ExportFormat.AmountFrequency(PolicyItem.PremiumAmount))
            WriteText(DataRow, 5, ExportFormat.IsoDate(PolicyItem.Fup))
            WriteText(DataRow, 6, PolicyItem.Status)
        }

        return FinishWorkbook(
            ContextRef = ContextRef,
            WorkbookObj = WorkbookObj,
            FilePrefix = "PS_Export"
        )
    }


    private fun WriteHeaders(RowRef: Row, HeadersList: Array<String>) {
        for (Idx in HeadersList.indices) {
            RowRef.createCell(Idx).setCellValue(HeadersList[Idx])
        }
    }

    private fun WriteText(RowRef: Row, ColumnIdx: Int, ValueText: String) {
        RowRef.createCell(ColumnIdx).setCellValue(ValueText)
    }

    private fun WriteNumber(RowRef: Row, ColumnIdx: Int, ValueNumber: Double?) {
        val CellRef = RowRef.createCell(ColumnIdx)
        if (ValueNumber != null) CellRef.setCellValue(ValueNumber)
    }

    private fun FinishWorkbook(
        ContextRef: Context,
        WorkbookObj: XSSFWorkbook,
        FilePrefix: String
    ): File {
        if (ExportFormat.UnparsedValues.isNotEmpty()) {
            CaptureDiagnostics.Log(
                ContextObj = ContextRef,
                EventName = "EXPORT_UNPARSED_VALUES",
                MessageText = "file=$FilePrefix count=${ExportFormat.UnparsedValues.size} " +
                        "samples=${ExportFormat.UnparsedValues.take(10)}"
            )
        }

        val TimeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val TargetFile = File(
            ContextRef.getExternalFilesDir(null),
            "${FilePrefix}_$TimeStamp.xlsx"
        )
        FileOutputStream(TargetFile).use { OutStream ->
            WorkbookObj.write(OutStream)
        }
        WorkbookObj.close()
        return TargetFile
    }
}
