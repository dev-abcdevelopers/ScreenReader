@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.mpin

import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.widget.doAfterTextChanged
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ActivityMpinBinding
import com.bliss.screenreader.security.MpinStore
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.snackbar.Snackbar

class MpinActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityMpinBinding
    private lateinit var BoxViews: List<EditText>
    private var IsRevealed = false
    private var IsSpreadingDigits = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityMpinBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        BoxViews = listOf(
            ViewBindingObj.edtMpin1,
            ViewBindingObj.edtMpin2,
            ViewBindingObj.edtMpin3,
            ViewBindingObj.edtMpin4
        )
        BindBoxes()

        ViewBindingObj.swAutoEnter.isChecked = MpinStore.IsAutoEnterOn(ContextRef = this)
        ViewBindingObj.swAutoEnter.setOnCheckedChangeListener { ViewRef, _ ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            RenderAutoBody()
        }
        ViewBindingObj.btnRevealMpin.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            IsRevealed = !IsRevealed
            RenderReveal()
        }
        ViewBindingObj.btnSaveMpin.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnSave()
        }
        ViewBindingObj.btnClearMpin.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnClear()
        }

        RenderReveal()
        RenderAutoBody()
        RenderClearButton()
        ShowSavedMpin()
    }

    private fun BindBoxes() {
        for ((IndexVal, BoxRef) in BoxViews.withIndex()) {
            BoxRef.contentDescription = getString(R.string.mpin_digit_format, IndexVal + 1)

            BoxRef.doAfterTextChanged { EditableRef ->
                if (IsSpreadingDigits) return@doAfterTextChanged
                ClearError()

                val TypedText = EditableRef?.toString().orEmpty()
                if (TypedText.length > 1) {
                    SpreadDigits(StartIndex = IndexVal, TextValue = TypedText)
                    return@doAfterTextChanged
                }
                if (TypedText.isNotEmpty() && IndexVal < BoxViews.lastIndex) {
                    BoxViews[IndexVal + 1].requestFocus()
                }
            }

            BoxRef.setOnKeyListener { _, KeyCodeVal, EventRef ->
                val IsBackspace = KeyCodeVal == KeyEvent.KEYCODE_DEL &&
                        EventRef.action == KeyEvent.ACTION_DOWN
                if (IsBackspace && BoxRef.text.isNullOrEmpty() && IndexVal > 0) {
                    val PreviousBox = BoxViews[IndexVal - 1]
                    PreviousBox.setText("")
                    PreviousBox.requestFocus()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun SpreadDigits(StartIndex: Int, TextValue: String) {
        val DigitsText = TextValue.filter { CharValue -> CharValue.isDigit() }
        IsSpreadingDigits = true
        var BoxIndex = StartIndex
        for (DigitChar in DigitsText) {
            if (BoxIndex > BoxViews.lastIndex) break
            BoxViews[BoxIndex].setText(DigitChar.toString())
            BoxIndex++
        }
        IsSpreadingDigits = false

        val FocusIndex = BoxIndex.coerceAtMost(BoxViews.lastIndex)
        BoxViews[FocusIndex].requestFocus()
        BoxViews[FocusIndex].setSelection(BoxViews[FocusIndex].text?.length ?: 0)
    }

    private fun ShowSavedMpin() {
        val SavedText = MpinStore.MpinOrNull(ContextRef = this).orEmpty()
        if (!MpinStore.IsWellFormed(CodeText = SavedText)) {
            BoxViews.first().requestFocus()
            return
        }
        IsSpreadingDigits = true
        for (IndexVal in SavedText.indices) {
            BoxViews[IndexVal].setText(SavedText[IndexVal].toString())
        }
        IsSpreadingDigits = false
        BoxViews.last().requestFocus()
        BoxViews.last().setSelection(BoxViews.last().text?.length ?: 0)
    }

    private fun CurrentCode(): String = BoxViews.joinToString(separator = "") { BoxRef ->
        BoxRef.text?.toString()?.trim().orEmpty()
    }

    private fun RenderReveal() {
        for (BoxRef in BoxViews) {
            BoxRef.transformationMethod = if (IsRevealed) {
                null
            } else {
                PasswordTransformationMethod.getInstance()
            }
            BoxRef.setSelection(BoxRef.text?.length ?: 0)
        }
        ViewBindingObj.btnRevealMpin.setText(
            if (IsRevealed) R.string.mpin_hide else R.string.mpin_show
        )
    }

    private fun RenderAutoBody() {
        ViewBindingObj.txtAutoBody.setText(
            if (ViewBindingObj.swAutoEnter.isChecked) {
                R.string.mpin_auto_on_body
            } else {
                R.string.mpin_auto_off_body
            }
        )
    }

    private fun RenderClearButton() {
        ViewBindingObj.btnClearMpin.visibility =
            if (MpinStore.HasMpin(ContextRef = this)) View.VISIBLE else View.GONE
    }

    private fun ClearError() {
        ViewBindingObj.txtMpinError.visibility = View.GONE
    }

    private fun ShowError(MessageRes: Int) {
        ViewBindingObj.txtMpinError.setText(MessageRes)
        ViewBindingObj.txtMpinError.visibility = View.VISIBLE
    }

    private fun OnSave() {
        val CodeText = CurrentCode()
        if (!MpinStore.IsWellFormed(CodeText = CodeText)) {
            ShowError(MessageRes = R.string.mpin_error_length)
            HapticFeedback.Reject(ViewRef = ViewBindingObj.btnSaveMpin)
            BoxViews.firstOrNull { BoxRef -> BoxRef.text.isNullOrEmpty() }?.requestFocus()
            return
        }

        val AutoEnterVal = ViewBindingObj.swAutoEnter.isChecked
        MpinStore.Save(ContextRef = this, CodeText = CodeText, AutoEnterVal = AutoEnterVal)
        ClearError()
        HideKeyboard()
        RenderClearButton()
        HapticFeedback.Confirm(ViewRef = ViewBindingObj.btnSaveMpin)
        Snackbar.make(
            ViewBindingObj.root,
            if (AutoEnterVal) R.string.mpin_saved_auto else R.string.mpin_saved,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun OnClear() {
        MpinStore.Clear(ContextRef = this)
        IsSpreadingDigits = true
        for (BoxRef in BoxViews) BoxRef.setText("")
        IsSpreadingDigits = false
        ViewBindingObj.swAutoEnter.isChecked = false
        ClearError()
        HideKeyboard()
        RenderAutoBody()
        RenderClearButton()
        BoxViews.first().requestFocus()
        Snackbar.make(ViewBindingObj.root, R.string.mpin_cleared, Snackbar.LENGTH_SHORT).show()
    }

    private fun HideKeyboard() {
        val ManagerRef = getSystemService<InputMethodManager>() ?: return
        ManagerRef.hideSoftInputFromWindow(ViewBindingObj.root.windowToken, 0)
        for (BoxRef in BoxViews) BoxRef.clearFocus()
    }
}
