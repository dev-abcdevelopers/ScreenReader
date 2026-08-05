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
            "Policy Number", "Holder Name", "Plan Name", "Status",
            "Premium Amount", "Sum Assured", "Renewal Due Date", "Mobile", "DOB"
        )

        for (Idx in HeadersList.indices) {
            HeaderRow.createCell(Idx).setCellValue(HeadersList[Idx])
        }

        var RowIdx = 1
        for (PolicyItem in Policies) {
            val DataRow = SheetObj.createRow(RowIdx++)
            DataRow.createCell(0).setCellValue(PolicyItem.PolicyNumber)
            DataRow.createCell(1).setCellValue(PolicyItem.HolderName)
            DataRow.createCell(2).setCellValue(PolicyItem.PlanName)
            DataRow.createCell(3).setCellValue(PolicyItem.NormalizedStatus)
            DataRow.createCell(4).setCellValue(PolicyItem.PremiumAmount)
            DataRow.createCell(5).setCellValue(PolicyItem.SumAssured)
            DataRow.createCell(6).setCellValue(PolicyItem.RenewalDueDate)
            DataRow.createCell(7).setCellValue(PolicyItem.MobileNumber)
            DataRow.createCell(8).setCellValue(PolicyItem.Dob)
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
