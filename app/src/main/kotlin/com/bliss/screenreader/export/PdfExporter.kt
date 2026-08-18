@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.toColorInt
import com.bliss.screenreader.data.model.CustomerPolicy
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun GeneratePolicyPdf(ContextRef: Context, Policies: List<CustomerPolicy>): File {
        val PdfDoc = PdfDocument()
        val BodyPaint = Paint()
        val TitlePaint = Paint()

        val TimeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val FileName = "Policy_Report_$TimeStamp.pdf"
        val TargetFile = File(ContextRef.getExternalFilesDir(null), FileName)

        var PageNum = 1
        val PageInfo = PdfDocument.PageInfo.Builder(595, 842, PageNum).create()
        var PageRef = PdfDoc.startPage(PageInfo)
        var CanvasRef: Canvas = PageRef.canvas

        TitlePaint.color = "#1E40AF".toColorInt()
        TitlePaint.textSize = 20f
        TitlePaint.isFakeBoldText = true
        CanvasRef.drawText("Data Reader App - Policy Executive Summary", 40f, 50f, TitlePaint)

        BodyPaint.color = "#475569".toColorInt()
        BodyPaint.textSize = 11f
        CanvasRef.drawText("Generated on: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", 40f, 70f, BodyPaint)

        BodyPaint.color = "#E2E8F0".toColorInt()
        BodyPaint.strokeWidth = 2f
        CanvasRef.drawLine(40f, 85f, 555f, 85f, BodyPaint)

        var YPos = 110f
        BodyPaint.textSize = 11f

        for (PolicyItem in Policies) {
            if (YPos > 780f) {
                PdfDoc.finishPage(PageRef)
                PageNum++
                val NextPageInfo = PdfDocument.PageInfo.Builder(595, 842, PageNum).create()
                PageRef = PdfDoc.startPage(NextPageInfo)
                CanvasRef = PageRef.canvas
                YPos = 50f
            }

            BodyPaint.color = "#0F172A".toColorInt()
            BodyPaint.isFakeBoldText = true
            CanvasRef.drawText("Policy No: ${PolicyItem.PolicyNumber} | Holder: ${PolicyItem.HolderName}", 40f, YPos, BodyPaint)

            BodyPaint.color = "#475569".toColorInt()
            BodyPaint.isFakeBoldText = false
            YPos += 18f
            CanvasRef.drawText("Plan: ${PolicyItem.PlanName} | Status: ${PolicyItem.NormalizedStatus}", 40f, YPos, BodyPaint)

            YPos += 18f
            CanvasRef.drawText("Premium: ${PolicyItem.PremiumAmount} | Sum Assured: ${PolicyItem.SumAssured} | Due: ${PolicyItem.RenewalDueDate}", 40f, YPos, BodyPaint)

            YPos += 24f
            BodyPaint.color = "#F1F5F9".toColorInt()
            CanvasRef.drawRect(40f, YPos - 10f, 555f, YPos - 8f, BodyPaint)
            YPos += 10f
        }

        PdfDoc.finishPage(PageRef)

        FileOutputStream(TargetFile).use { OutStream ->
            PdfDoc.writeTo(OutStream)
        }
        PdfDoc.close()

        return TargetFile
    }
}
