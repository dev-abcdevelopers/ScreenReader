@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.export

import android.content.Context
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.PsPolicy
import com.bliss.screenreader.service.CaptureDiagnostics
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {

    fun ExportCustomerPolicies(ContextRef: Context, Policies: List<CustomerPolicy>): File {
        ExportFormat.ResetDiagnostics()

        val WorkbookObj = XSSFWorkbook()
        val SheetObj = WorkbookObj.createSheet("Customer Policies")

        WriteHeaders(
            RowRef = SheetObj.createRow(0),
            HeadersList = arrayOf(
                "Policy Number", "Holder Name", "Plan Code", "Plan Name", "Status",
                "Premium Amount", "Premium Frequency", "Auto Pay", "Renewal Type",
                "Renewal Due Date", "KYC Status", "NEFT Status", "Sum Assured",
                "Term Years", "PPT Years", "Date of Commencement",
                "End of Premium Paying Term", "Date of Maturity", "Mobile", "DOB",
                "Address", "Date of Premium Payment", "Date of Commission Payment",
                "Commission Type", "Bonus Commission", "Commission Paid Amount"
            )
        )

        var RowIdx = 1
        for (PolicyItem in Policies) {
            val DataRow = SheetObj.createRow(RowIdx++)
            WriteText(DataRow, 0, ExportFormat.Identifier(PolicyItem.PolicyNumber))
            WriteText(DataRow, 1, PolicyItem.HolderName)
            WriteText(DataRow, 2, PolicyItem.PlanCode)
            WriteText(DataRow, 3, PolicyItem.PlanName)
            WriteText(DataRow, 4, PolicyItem.NormalizedStatus)
            WriteNumber(DataRow, 5, ExportFormat.PlainNumber(PolicyItem.PremiumAmount))
            WriteText(
                DataRow, 6,
                PolicyItem.PremiumFrequency.ifEmpty {
                    ExportFormat.AmountFrequency(PolicyItem.PremiumAmount)
                }
            )
            WriteText(DataRow, 7, PolicyItem.AutoPay)
            WriteText(DataRow, 8, PolicyItem.RenewalType)
            WriteText(DataRow, 9, ExportFormat.IsoDate(PolicyItem.RenewalDueDate))
            WriteText(DataRow, 10, PolicyItem.KycStatus)
            WriteText(DataRow, 11, PolicyItem.NeftStatus)
            WriteNumber(DataRow, 12, ExportFormat.PlainNumber(PolicyItem.SumAssured))
            WriteNumber(DataRow, 13, ExportFormat.TermYears(PolicyItem.TermPPT))
            WriteNumber(DataRow, 14, ExportFormat.PptYears(PolicyItem.TermPPT))
            WriteText(DataRow, 15, ExportFormat.IsoDate(PolicyItem.DateOfCommencement))
            WriteText(DataRow, 16, ExportFormat.IsoDate(PolicyItem.EndOfPremiumPayingTerm))
            WriteText(DataRow, 17, ExportFormat.IsoDate(PolicyItem.DateOfMaturity))
            WriteText(DataRow, 18, ExportFormat.Identifier(PolicyItem.MobileNumber))
            WriteText(DataRow, 19, ExportFormat.IsoDate(PolicyItem.Dob))
            WriteText(DataRow, 20, PolicyItem.Address)
            WriteText(DataRow, 21, ExportFormat.IsoDate(PolicyItem.CommissionDateOfPremiumPayment))
            WriteText(DataRow, 22, ExportFormat.IsoDate(PolicyItem.CommissionDateOfPayment))
            WriteText(DataRow, 23, PolicyItem.CommissionType)
            WriteNumber(DataRow, 24, ExportFormat.PlainNumber(PolicyItem.BonusCommission))
            WriteNumber(DataRow, 25, ExportFormat.PlainNumber(PolicyItem.CommissionPaidAmount))
        }

        return FinishWorkbook(
            ContextRef = ContextRef,
            WorkbookObj = WorkbookObj,
            FilePrefix = "Policy_Export"
        )
    }

    fun ExportFupPolicies(ContextRef: Context, Policies: List<FupPolicy>): File {
        ExportFormat.ResetDiagnostics()

        val WorkbookObj = XSSFWorkbook()
        val SheetObj = WorkbookObj.createSheet("FUP Renewal History")

        WriteHeaders(
            RowRef = SheetObj.createRow(0),
            HeadersList = arrayOf(
                "Policy Number", "Plan Code", "Plan Name", "Holder Name",
                "Premium Amount", "Premium Frequency", "Due Date", "Payment Date",
                "Mode of Payment", "Status at Time of Payment"
            )
        )

        var RowIdx = 1
        for (PolicyItem in Policies) {
            val DataRow = SheetObj.createRow(RowIdx++)
            WriteText(DataRow, 0, ExportFormat.Identifier(PolicyItem.PolicyNumber))
            WriteText(DataRow, 1, ExportFormat.Identifier(PolicyItem.PlanCode))
            WriteText(DataRow, 2, PolicyItem.PlanName)
            WriteText(DataRow, 3, PolicyItem.HolderName)
            WriteNumber(DataRow, 4, ExportFormat.PlainNumber(PolicyItem.PremiumAmount))
            WriteText(DataRow, 5, ExportFormat.AmountFrequency(PolicyItem.PremiumAmount))
            WriteText(DataRow, 6, ExportFormat.IsoDate(PolicyItem.DueDate))
            WriteText(DataRow, 7, ExportFormat.IsoDate(PolicyItem.PaymentDate))
            WriteText(DataRow, 8, PolicyItem.ModeOfPayment)
            WriteText(DataRow, 9, PolicyItem.Status)
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
