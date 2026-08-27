@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor


object CustomerSheetOcr {

    const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.R

    private val Recogniser by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    sealed class Outcome {
        data class Lines(val TextLines: List<String>) : Outcome()
        data class Failed(val Reason: String) : Outcome()
    }

    data class TextBox(val TextValue: String, val Bounds: Rect)

    sealed class BoxOutcome {
        data class Boxes(val Items: List<TextBox>) : BoxOutcome()
        data class Failed(val Reason: String) : BoxOutcome()
    }

    fun IsSupported(): Boolean = Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK

    @RequiresApi(Build.VERSION_CODES.R)
    fun ReadSheet(
        ServiceRef: AccessibilityService,
        ExecutorRef: Executor,
        TopFraction: Float,
        OnResult: (Outcome) -> Unit
    ) {
        ServiceRef.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            ExecutorRef,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(ScreenshotResult: AccessibilityService.ScreenshotResult) {
                    val BufferRef = ScreenshotResult.hardwareBuffer
                    val BitmapObj = try {
                        Bitmap.wrapHardwareBuffer(BufferRef, ScreenshotResult.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (ErrorRef: Exception) {
                        OnResult(Outcome.Failed(Reason = "bitmap: ${ErrorRef.message.orEmpty()}"))
                        return
                    } finally {
                        try {
                            BufferRef.close()
                        } catch (_: Exception) {
                        }
                    }

                    if (BitmapObj == null) {
                        OnResult(Outcome.Failed(Reason = "screenshot produced no bitmap"))
                        return
                    }
                    Recognise(BitmapObj = BitmapObj, TopFraction = TopFraction, OnResult = OnResult)
                }

                override fun onFailure(ErrorCode: Int) {
                    OnResult(Outcome.Failed(Reason = "takeScreenshot error=$ErrorCode"))
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun ReadSheetBoxes(
        ServiceRef: AccessibilityService,
        ExecutorRef: Executor,
        TopFraction: Float,
        OnResult: (BoxOutcome) -> Unit
    ) {
        ServiceRef.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            ExecutorRef,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(ScreenshotResult: AccessibilityService.ScreenshotResult) {
                    val BufferRef = ScreenshotResult.hardwareBuffer
                    val BitmapObj = try {
                        Bitmap.wrapHardwareBuffer(BufferRef, ScreenshotResult.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (ErrorRef: Exception) {
                        OnResult(BoxOutcome.Failed(Reason = "bitmap: ${ErrorRef.message.orEmpty()}"))
                        return
                    } finally {
                        try {
                            BufferRef.close()
                        } catch (_: Exception) {
                        }
                    }

                    if (BitmapObj == null) {
                        OnResult(BoxOutcome.Failed(Reason = "screenshot produced no bitmap"))
                        return
                    }
                    RecogniseBoxes(
                        BitmapObj = BitmapObj,
                        TopFraction = TopFraction,
                        OnResult = OnResult
                    )
                }

                override fun onFailure(ErrorCode: Int) {
                    OnResult(BoxOutcome.Failed(Reason = "takeScreenshot error=$ErrorCode"))
                }
            }
        )
    }

    private fun RecogniseBoxes(
        BitmapObj: Bitmap,
        TopFraction: Float,
        OnResult: (BoxOutcome) -> Unit
    ) {
        val TopPixel = (BitmapObj.height * TopFraction)
            .toInt()
            .coerceIn(0, BitmapObj.height - 1)
        val CroppedBitmap = CropToSheet(BitmapObj = BitmapObj, TopFraction = TopFraction)
        val ImageObj = InputImage.fromBitmap(CroppedBitmap, 0)

        Recogniser.process(ImageObj)
            .addOnSuccessListener { VisionText ->
                val BoxList = VisionText.textBlocks
                    .flatMap { BlockRef -> BlockRef.lines }
                    .mapNotNull { LineRef ->
                        val BoxRef = LineRef.boundingBox ?: return@mapNotNull null
                        val LineText = LineRef.text.trim()
                        if (LineText.isEmpty()) return@mapNotNull null
                        TextBox(
                            TextValue = LineText,
                            Bounds = Rect(
                                BoxRef.left,
                                BoxRef.top + TopPixel,
                                BoxRef.right,
                                BoxRef.bottom + TopPixel
                            )
                        )
                    }
                    .sortedBy { BoxItem -> BoxItem.Bounds.top }
                OnResult(BoxOutcome.Boxes(Items = BoxList))
                Release(BitmapObj = BitmapObj, CroppedBitmap = CroppedBitmap)
            }
            .addOnFailureListener { ErrorRef ->
                OnResult(BoxOutcome.Failed(Reason = "ocr: ${ErrorRef.message.orEmpty()}"))
                Release(BitmapObj = BitmapObj, CroppedBitmap = CroppedBitmap)
            }
    }

    private fun Recognise(
        BitmapObj: Bitmap,
        TopFraction: Float,
        OnResult: (Outcome) -> Unit
    ) {
        val CroppedBitmap = CropToSheet(BitmapObj = BitmapObj, TopFraction = TopFraction)
        val ImageObj = InputImage.fromBitmap(CroppedBitmap, 0)

        Recogniser.process(ImageObj)
            .addOnSuccessListener { VisionText ->
                val TextLines = VisionText.textBlocks
                    .flatMap { BlockRef -> BlockRef.lines }
                    .sortedBy { LineRef -> LineRef.boundingBox?.top ?: 0 }
                    .map { LineRef -> LineRef.text.trim() }
                    .filter { LineText -> LineText.isNotEmpty() }
                OnResult(Outcome.Lines(TextLines = TextLines))
                Release(BitmapObj = BitmapObj, CroppedBitmap = CroppedBitmap)
            }
            .addOnFailureListener { ErrorRef ->
                OnResult(Outcome.Failed(Reason = "ocr: ${ErrorRef.message.orEmpty()}"))
                Release(BitmapObj = BitmapObj, CroppedBitmap = CroppedBitmap)
            }
    }


    private fun CropToSheet(BitmapObj: Bitmap, TopFraction: Float): Bitmap {
        val TopPixel = (BitmapObj.height * TopFraction)
            .toInt()
            .coerceIn(0, BitmapObj.height - 1)
        val HeightPixels = BitmapObj.height - TopPixel
        if (HeightPixels <= 0) return BitmapObj
        return Bitmap.createBitmap(BitmapObj, 0, TopPixel, BitmapObj.width, HeightPixels)
    }

    private fun Release(BitmapObj: Bitmap, CroppedBitmap: Bitmap) {
        if (CroppedBitmap !== BitmapObj) CroppedBitmap.recycle()
        BitmapObj.recycle()
    }
}
