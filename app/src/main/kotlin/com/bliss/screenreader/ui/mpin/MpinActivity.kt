@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.ui.mpin

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityMpinBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        ViewBindingObj.edtMpin.setText(MpinStore.MpinOrNull(ContextRef = this).orEmpty())
        ViewBindingObj.swAutoEnter.isChecked = MpinStore.IsAutoEnterOn(ContextRef = this)

        ViewBindingObj.edtMpin.doAfterTextChanged { ClearError() }
        ViewBindingObj.swAutoEnter.setOnCheckedChangeListener { ViewRef, _ ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            RenderAutoBody()
        }
        ViewBindingObj.btnSaveMpin.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnSave()
        }
        ViewBindingObj.btnClearMpin.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnClear()
        }

        RenderAutoBody()
        RenderClearButton()
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
        val CodeText = ViewBindingObj.edtMpin.text?.toString()?.trim().orEmpty()
        if (!MpinStore.IsWellFormed(CodeText = CodeText)) {
            ShowError(MessageRes = R.string.mpin_error_length)
            HapticFeedback.Reject(ViewRef = ViewBindingObj.btnSaveMpin)
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
        ViewBindingObj.edtMpin.setText("")
        ViewBindingObj.swAutoEnter.isChecked = false
        ClearError()
        HideKeyboard()
        RenderAutoBody()
        RenderClearButton()
        Snackbar.make(ViewBindingObj.root, R.string.mpin_cleared, Snackbar.LENGTH_SHORT).show()
    }

    private fun HideKeyboard() {
        val ManagerRef = getSystemService<InputMethodManager>() ?: return
        ManagerRef.hideSoftInputFromWindow(ViewBindingObj.root.windowToken, 0)
        ViewBindingObj.edtMpin.clearFocus()
    }
}
