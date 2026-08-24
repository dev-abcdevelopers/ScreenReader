@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.credentials

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ActivityCredentialDetailBinding
import com.bliss.screenreader.security.CredentialStore
import com.bliss.screenreader.utils.HapticFeedback
import com.bliss.screenreader.ui.toast.AppToast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CredentialDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_METHOD = "credential_method"
        private const val METHOD_PASSWORD = "password"
        private const val TICK_MARK = "✓  "
        private const val CROSS_MARK = "✕  "

        fun IntentFor(ContextRef: Context, MethodVal: CredentialStore.Method): Intent {
            val IntentObj = Intent(ContextRef, CredentialDetailActivity::class.java)
            if (MethodVal == CredentialStore.Method.PASSWORD) {
                IntentObj.putExtra(EXTRA_METHOD, METHOD_PASSWORD)
            }
            return IntentObj
        }
    }

    private lateinit var ViewBindingObj: ActivityCredentialDetailBinding
    private lateinit var MethodVal: CredentialStore.Method
    private var BoxViews: List<EditText> = emptyList()
    private var IsEditing = false
    private var IsRevealed = false
    private var IsSpreadingDigits = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityCredentialDetailBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        MethodVal = if (intent.getStringExtra(EXTRA_METHOD) == METHOD_PASSWORD) {
            CredentialStore.Method.PASSWORD
        } else {
            CredentialStore.Method.MPIN
        }
        val IsMpin = MethodVal == CredentialStore.Method.MPIN

        enableEdgeToEdge()
        ApplyInsets()

        ViewBindingObj.toolbar.setTitle(
            if (IsMpin) R.string.credentials_mpin else R.string.credentials_password
        )
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }

        ViewBindingObj.tvHeroTitle.setText(
            if (IsMpin) {
                R.string.credentials_hero_mpin_title
            } else {
                R.string.credentials_hero_password_title
            }
        )
        ViewBindingObj.tvFieldLabel.setText(
            if (IsMpin) R.string.credentials_field_mpin else R.string.credentials_field_password
        )
        ViewBindingObj.tvDangerTitle.setText(
            if (IsMpin) {
                R.string.credentials_remove_mpin
            } else {
                R.string.credentials_remove_password
            }
        )
        ViewBindingObj.tvDangerDesc.setText(
            if (IsMpin) {
                R.string.credentials_danger_desc_mpin
            } else {
                R.string.credentials_danger_desc_password
            }
        )
        RenderRuleLines(IsMpin = IsMpin)

        ViewBindingObj.boxRow.visibility = if (IsMpin) View.VISIBLE else View.GONE
        ViewBindingObj.tilPassword.visibility = if (IsMpin) View.GONE else View.VISIBLE
        ViewBindingObj.btnShow.visibility = if (IsMpin) View.VISIBLE else View.GONE

        BoxViews = listOf(
            ViewBindingObj.edtMpin1,
            ViewBindingObj.edtMpin2,
            ViewBindingObj.edtMpin3,
            ViewBindingObj.edtMpin4
        )
        if (IsMpin) BindBoxes() else BindPasswordField()

        ViewBindingObj.btnShow.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            IsRevealed = !IsRevealed
            RenderReveal()
        }
        ViewBindingObj.btnEdit.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            BeginEditing()
        }
        ViewBindingObj.btnCancel.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnCancel()
        }
        ViewBindingObj.btnSave.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnSave()
        }
        ViewBindingObj.btnRemove.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnRemove()
        }

        ShowStoredSecret()
        if (CredentialStore.HasSecretFor(ContextRef = this, MethodVal = MethodVal)) {
            EnterRestState()
        } else {
            BeginEditing()
        }
    }

    private fun ApplyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(ViewBindingObj.root) { ViewRef, WindowInsetsObj ->
            val BarInsets = WindowInsetsObj.getInsets(WindowInsetsCompat.Type.systemBars())
            val ImeInsets = WindowInsetsObj.getInsets(WindowInsetsCompat.Type.ime())
            ViewBindingObj.heroBand.updatePadding(top = BarInsets.top)
            ViewRef.updatePadding(bottom = maxOf(BarInsets.bottom, ImeInsets.bottom))
            WindowInsetsObj
        }
    }

    private fun RenderRuleLines(IsMpin: Boolean) {
        val DoLines = if (IsMpin) {
            listOf(
                R.string.credentials_do_mpin_1,
                R.string.credentials_do_mpin_2,
                R.string.credentials_do_mpin_3
            )
        } else {
            listOf(
                R.string.credentials_do_password_1,
                R.string.credentials_do_password_2,
                R.string.credentials_do_password_3
            )
        }
        val DontLines = if (IsMpin) {
            listOf(
                R.string.credentials_dont_mpin_1,
                R.string.credentials_dont_mpin_2,
                R.string.credentials_dont_mpin_3
            )
        } else {
            listOf(
                R.string.credentials_dont_password_1,
                R.string.credentials_dont_password_2,
                R.string.credentials_dont_password_3
            )
        }

        val DoViews = listOf(
            ViewBindingObj.tvDo1,
            ViewBindingObj.tvDo2,
            ViewBindingObj.tvDo3
        )
        val DontViews = listOf(
            ViewBindingObj.tvDont1,
            ViewBindingObj.tvDont2,
            ViewBindingObj.tvDont3
        )
        for (IndexVal in DoViews.indices) {
            DoViews[IndexVal].text = TICK_MARK + getString(DoLines[IndexVal])
            DontViews[IndexVal].text = CROSS_MARK + getString(DontLines[IndexVal])
        }
    }

    private fun ShowStoredSecret() {
        if (MethodVal == CredentialStore.Method.PASSWORD) {
            val SavedText = CredentialStore.PasswordOrNull(ContextRef = this).orEmpty()
            ViewBindingObj.etPassword.setText(SavedText)
            return
        }
        val SavedText = CredentialStore.MpinOrNull(ContextRef = this).orEmpty()
        IsSpreadingDigits = true
        for (BoxRef in BoxViews) BoxRef.setText("")
        for (IndexVal in SavedText.indices) {
            BoxViews[IndexVal].setText(SavedText[IndexVal].toString())
        }
        IsSpreadingDigits = false
    }

    private fun BindPasswordField() {
        ViewBindingObj.etPassword.doAfterTextChanged { ClearError() }
    }

    private fun BindBoxes() {
        for ((IndexVal, BoxRef) in BoxViews.withIndex()) {
            BoxRef.contentDescription = getString(R.string.credentials_digit_format, IndexVal + 1)

            BoxRef.doAfterTextChanged { EditableRef ->
                if (IsSpreadingDigits) return@doAfterTextChanged
                ClearError()
                val TypedText = EditableRef?.toString().orEmpty()
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

    private fun BeginEditing() {
        IsEditing = true
        ClearError()
        RenderState()

        if (MethodVal == CredentialStore.Method.PASSWORD) {
            FocusAndOpenKeyboard(TargetView = ViewBindingObj.etPassword)
            return
        }
        val FocusBox = BoxViews.firstOrNull { BoxRef -> BoxRef.text.isNullOrEmpty() }
            ?: BoxViews.first()
        FocusAndOpenKeyboard(TargetView = FocusBox)
    }

    private fun EnterRestState() {
        IsEditing = false
        IsRevealed = false
        ClearError()
        HideKeyboard()
        RenderState()
    }

    private fun RenderState() {
        val HasSecret = CredentialStore.HasSecretFor(ContextRef = this, MethodVal = MethodVal)

        for (BoxRef in BoxViews) {
            BoxRef.isFocusable = IsEditing
            BoxRef.isFocusableInTouchMode = IsEditing
            BoxRef.isCursorVisible = IsEditing
            BoxRef.alpha = if (IsEditing) 1f else 0.6f
        }
        ViewBindingObj.etPassword.isFocusable = IsEditing
        ViewBindingObj.etPassword.isFocusableInTouchMode = IsEditing
        ViewBindingObj.etPassword.isCursorVisible = IsEditing
        ViewBindingObj.tilPassword.alpha = if (IsEditing) 1f else 0.6f

        ViewBindingObj.btnEdit.visibility = if (IsEditing) View.GONE else View.VISIBLE
        ViewBindingObj.actionDock.visibility = if (IsEditing) View.VISIBLE else View.GONE
        ViewBindingObj.cardDanger.visibility =
            if (!IsEditing && HasSecret) View.VISIBLE else View.GONE

        ViewBindingObj.tvHeroPill.setText(PillTextRes(HasSecret = HasSecret))

        val WasRejected = CredentialStore.WasRejected(ContextRef = this, MethodVal = MethodVal)
        ViewBindingObj.tvRejected.visibility = if (WasRejected) View.VISIBLE else View.GONE
        if (WasRejected) {
            ViewBindingObj.tvRejected.setText(
                if (MethodVal == CredentialStore.Method.MPIN) {
                    R.string.credentials_rejected_mpin
                } else {
                    R.string.credentials_rejected_password
                }
            )
        }

        RenderLastSaved()
        RenderReveal()
    }

    private fun PillTextRes(HasSecret: Boolean): Int = when {
        IsEditing -> R.string.credentials_pill_editing
        CredentialStore.WasRejected(ContextRef = this, MethodVal = MethodVal) ->
            R.string.credentials_pill_rejected

        !HasSecret -> R.string.credentials_pill_none
        CredentialStore.MethodOf(ContextRef = this) == MethodVal ->
            R.string.credentials_pill_in_use

        else -> R.string.credentials_pill_saved
    }

    private fun RenderLastSaved() {
        val SavedAtMs = CredentialStore.SavedAt(ContextRef = this, MethodVal = MethodVal)
        if (SavedAtMs <= 0L) {
            ViewBindingObj.cardLastSaved.visibility = View.GONE
            return
        }
        ViewBindingObj.cardLastSaved.visibility = View.VISIBLE
        ViewBindingObj.tvLastSaved.text = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(SavedAtMs))
    }

    private fun RenderReveal() {
        val TransformationRef = if (IsRevealed) {
            null
        } else {
            PasswordTransformationMethod.getInstance()
        }
        for (BoxRef in BoxViews) {
            BoxRef.transformationMethod = TransformationRef
            BoxRef.setSelection(BoxRef.text?.length ?: 0)
        }
        ViewBindingObj.btnShow.setText(
            if (IsRevealed) R.string.credentials_hide else R.string.credentials_show
        )
    }

    private fun CurrentMpin(): String = BoxViews.joinToString(separator = "") { BoxRef ->
        BoxRef.text?.toString()?.trim().orEmpty()
    }

    private fun OnSave() {
        if (MethodVal == CredentialStore.Method.PASSWORD) {
            val PasswordText = ViewBindingObj.etPassword.text?.toString().orEmpty()
            if (!CredentialStore.SavePassword(ContextRef = this, PasswordText = PasswordText)) {
                ShowError(MessageRes = R.string.credentials_error_password_rules)
                HapticFeedback.Reject(ViewRef = ViewBindingObj.btnSave)
                return
            }
            HapticFeedback.Confirm(ViewRef = ViewBindingObj.btnSave)
            EnterRestState()
            ShowMessage(
                MessageText = getString(R.string.credentials_password_saved_toast),
                KindVal = AppToast.Kind.Success
            )
            return
        }

        val CodeText = CurrentMpin()
        if (!CredentialStore.SaveMpin(ContextRef = this, CodeText = CodeText)) {
            ShowError(MessageRes = R.string.credentials_error_mpin_length)
            HapticFeedback.Reject(ViewRef = ViewBindingObj.btnSave)
            BoxViews.firstOrNull { BoxRef -> BoxRef.text.isNullOrEmpty() }?.requestFocus()
            return
        }
        HapticFeedback.Confirm(ViewRef = ViewBindingObj.btnSave)
        EnterRestState()
        ShowMessage(
            MessageText = getString(R.string.credentials_mpin_saved_toast),
            KindVal = AppToast.Kind.Success
        )
    }

    private fun OnCancel() {
        if (!CredentialStore.HasSecretFor(ContextRef = this, MethodVal = MethodVal)) {
            finish()
            return
        }
        ShowStoredSecret()
        EnterRestState()
    }

    private fun OnRemove() {
        if (MethodVal == CredentialStore.Method.PASSWORD) {
            CredentialStore.ClearPassword(ContextRef = this)
            ViewBindingObj.etPassword.setText("")
            ShowMessage(
                MessageText = getString(R.string.credentials_password_removed),
                KindVal = AppToast.Kind.Warning
            )
        } else {
            CredentialStore.ClearMpin(ContextRef = this)
            IsSpreadingDigits = true
            for (BoxRef in BoxViews) BoxRef.setText("")
            IsSpreadingDigits = false
            ShowMessage(
                MessageText = getString(R.string.credentials_mpin_removed),
                KindVal = AppToast.Kind.Warning
            )
        }
        BeginEditing()
    }

    private fun FocusAndOpenKeyboard(TargetView: View) {
        TargetView.requestFocus()
        TargetView.post {
            val ManagerRef = getSystemService<InputMethodManager>() ?: return@post
            ManagerRef.showSoftInput(TargetView, 0)
        }
    }

    private fun HideKeyboard() {
        val ManagerRef = getSystemService<InputMethodManager>()
        ManagerRef?.hideSoftInputFromWindow(ViewBindingObj.root.windowToken, 0)
        for (BoxRef in BoxViews) BoxRef.clearFocus()
        ViewBindingObj.etPassword.clearFocus()
    }

    private fun ClearError() {
        ViewBindingObj.tvFieldError.visibility = View.GONE
    }

    private fun ShowError(MessageRes: Int) {
        ViewBindingObj.tvFieldError.setText(MessageRes)
        ViewBindingObj.tvFieldError.visibility = View.VISIBLE
    }

    private fun ShowMessage(
        MessageText: String,
        KindVal: AppToast.Kind = AppToast.Kind.Info
    ) {
        AppToast.Show(ContextRef = this, MessageText = MessageText, KindVal = KindVal)
    }
}
