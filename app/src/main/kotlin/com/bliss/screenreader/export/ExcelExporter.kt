@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.export

import android.content.Context
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.PsPolicy
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelExporter {

    fun ExportCustomerPolicies(ContextRef: Context, Policies: List<CustomerPolicy>): File {
        val WorkbookObj = XSSFWorkbook()
        val SheetObj = WorkbookObj.createSheet("Customer Policies")

        val HeaderRow = SheetObj.createRow(0)
        val HeadersList = arrayOf(
            "Policy Number", "Holder Name", "Plan Code", "Plan Name", "Status",
            "Premium Amount", "Premium Frequency", "Auto Pay", "Renewal Type",
            "Renewal Due Date", "KYC Status", "NEFT Status", "Sum Assured",
            "Term/PPT", "Date of Commencement", "End of Premium Paying Term",
            "Date of Maturity", "Mobile", "DOB", "Address",
            "Date of Premium Payment", "Date of Commission Payment", "Commission Type",
            "Bonus Commission", "Commission Paid Amount"
        )

        for (Idx in HeadersList.indices) {
            HeaderRow.createCell(Idx).setCellValue(HeadersList[Idx])
        }

        var RowIdx = 1
        for (PolicyItem in Policies) {
            val DataRow = SheetObj.createRow(RowIdx++)
            DataRow.createCell(0).setCellValue(PolicyItem.PolicyNumber)
            DataRow.createCell(1).setCellValue(PolicyItem.HolderName)
            DataRow.createCell(2).setCellValue(PolicyItem.PlanCode)
            DataRow.createCell(3).setCellValue(PolicyItem.PlanName)
            DataRow.createCell(4).setCellValue(PolicyItem.NormalizedStatus)
            DataRow.createCell(5).setCellValue(PolicyItem.PremiumAmount)
            DataRow.createCell(6).setCellValue(PolicyItem.PremiumFrequency)
            DataRow.createCell(7).setCellValue(PolicyItem.AutoPay)
            DataRow.createCell(8).setCellValue(PolicyItem.RenewalType)
            DataRow.createCell(9).setCellValue(PolicyItem.RenewalDueDate)
            DataRow.createCell(10).setCellValue(PolicyItem.KycStatus)
            DataRow.createCell(11).setCellValue(PolicyItem.NeftStatus)
            DataRow.createCell(12).setCellValue(PolicyItem.SumAssured)
            DataRow.createCell(13).setCellValue(PolicyItem.TermPPT)
            DataRow.createCell(14).setCellValue(PolicyItem.DateOfCommencement)
            DataRow.createCell(15).setCellValue(PolicyItem.EndOfPremiumPayingTerm)
            DataRow.createCell(16).setCellValue(PolicyItem.DateOfMaturity)
            DataRow.createCell(17).setCellValue(PolicyItem.MobileNumber)
            DataRow.createCell(18).setCellValue(PolicyItem.Dob)
            DataRow.createCell(19).setCellValue(PolicyItem.Address)
            DataRow.createCell(20).setCellValue(PolicyItem.CommissionDateOfPremiumPayment)
            DataRow.createCell(21).setCellValue(PolicyItem.CommissionDateOfPayment)
            DataRow.createCell(22).setCellValue(PolicyItem.CommissionType)
            DataRow.createCell(23).setCellValue(PolicyItem.BonusCommission)
            DataRow.createCell(24).setCellValue(PolicyItem.CommissionPaidAmount)
        }

        val TimeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val TargetFile = File(ContextRef.getExternalFilesDir(null), "Policy_Export_$TimeStamp.xlsx")
        FileOutputStream(TargetFile).use { OutStream ->
            WorkbookObj.write(OutStream)
        }
        WorkbookObj.close()
        return TargetFile
    }

    fun ExportFupPolicies(ContextRef: Context, Policies: List<FupPolicy>): File {
        val WorkbookObj = XSSFWorkbook()
        val SheetObj = WorkbookObj.createSheet("FUP Renewal History")

        val HeaderRow = SheetObj.createRow(0)
        val HeadersList = arrayOf("Policy Number", "Plan Name", "Holder Name", "Premium Amount", "Due Date", "Status")

        for (Idx in HeadersList.indices) {
            HeaderRow.createCell(Idx).setCellValue(HeadersList[Idx])
        }

        var RowIdx = 1
        for (PolicyItem in Policies) {
            val DataRow = SheetObj.createRow(RowIdx++)
            DataRow.createCell(0).setCellValue(PolicyItem.PolicyNumber)
            DataRow.createCell(1).setCellValue(PolicyItem.PlanName)
            DataRow.createCell(2).setCellValue(PolicyItem.HolderName)
            DataRow.createCell(3).setCellValue(PolicyItem.PremiumAmount)
            DataRow.createCell(4).setCellValue(PolicyItem.DueDate)
            DataRow.createCell(5).setCellValue(PolicyItem.Status)
        }

        val TimeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val TargetFile = File(ContextRef.getExternalFilesDir(null), "FUP_Export_$TimeStamp.xlsx")
        FileOutputStream(TargetFile).use { OutStream ->
            WorkbookObj.write(OutStream)
        }
        WorkbookObj.close()
        return TargetFile
    }

    fun ExportPsPolicies(ContextRef: Context, Policies: List<PsPolicy>): File {
        val WorkbookObj = XSSFWorkbook()
        val SheetObj = WorkbookObj.createSheet("PS Servicing Data")

        val HeaderRow = SheetObj.createRow(0)
        val HeadersList = arrayOf("Policy Number", "Holder Name", "DOC", "Premium Amount", "FUP", "Status")

        for (Idx in HeadersList.indices) {
            HeaderRow.createCell(Idx).setCellValue(HeadersList[Idx])
        }

        var RowIdx = 1
        for (PolicyItem in Policies) {
            val DataRow = SheetObj.createRow(RowIdx++)
            DataRow.createCell(0).setCellValue(PolicyItem.PolicyNumber)
            DataRow.createCell(1).setCellValue(PolicyItem.HolderName)
            DataRow.createCell(2).setCellValue(PolicyItem.Doc)
            DataRow.createCell(3).setCellValue(PolicyItem.PremiumAmount)
            DataRow.createCell(4).setCellValue(PolicyItem.Fup)
            DataRow.createCell(5).setCellValue(PolicyItem.Status)
        }

        val TimeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val TargetFile = File(ContextRef.getExternalFilesDir(null), "PS_Export_$TimeStamp.xlsx")
        FileOutputStream(TargetFile).use { OutStream ->
            WorkbookObj.write(OutStream)
        }
        WorkbookObj.close()
        return TargetFile
    }
}
