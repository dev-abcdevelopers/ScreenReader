@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.auth

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ActivityAuthBinding
import com.bliss.screenreader.security.AuthManager
import com.bliss.screenreader.security.DeviceIdentity
import com.bliss.screenreader.security.IntegrityGuard
import com.bliss.screenreader.ui.main.MainActivity

class AuthActivity : AppCompatActivity() {
    private lateinit var ViewBindingObj: ActivityAuthBinding

    private val CountdownHandler = Handler(Looper.getMainLooper())
    private val CountdownRunnable = object : Runnable {
        override fun run() {
            val SecondsLeft = AuthManager.LockoutSecondsLeft(ContextRef = this@AuthActivity)
            if (SecondsLeft > 0L) {
                ViewBindingObj.btnUnlock.isEnabled = false
                ViewBindingObj.btnUnlock.text = getString(R.string.auth_locked_countdown, FormatDuration(SecondsVal = SecondsLeft))
                CountdownHandler.postDelayed(this, 1000L)
            } else {
                ViewBindingObj.btnUnlock.isEnabled = true
                ViewBindingObj.btnUnlock.setText(R.string.auth_unlock)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthManager.BypassActive) {
            GoToApp()
            return
        }

        enableEdgeToEdge()
        ViewBindingObj = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        ApplyInsets()

        if (IntegrityGuard.IsTampered(ContextRef = this)) {
            ShowTamperBlock()
            return
        }

        WireActivationCard()
        WireUnlockCard()
    }

    override fun onResume() {
        super.onResume()
        if (AuthManager.IsUnlocked()) {
            GoToApp()
            return
        }
        RenderState()
    }

    override fun onPause() {
        super.onPause()
        CountdownHandler.removeCallbacks(CountdownRunnable)
    }

    private fun ApplyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(ViewBindingObj.root) { _, WindowInsetsObj ->
            val BarInsets = WindowInsetsObj.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            ViewBindingObj.authScroll.updatePadding(top = BarInsets.top, bottom = BarInsets.bottom)
            WindowInsetsObj
        }
    }

    private fun RenderState() {
        val IsActivated = AuthManager.IsActivated(ContextRef = this)

        ViewBindingObj.cardActivate.visibility = if (IsActivated) View.GONE else View.VISIBLE
        ViewBindingObj.cardUnlock.visibility = if (IsActivated) View.VISIBLE else View.GONE

        ViewBindingObj.txtDeviceId.text = DeviceIdentity.DisplayId(ContextRef = this)
        ViewBindingObj.txtSubtitle.setText(
            if (IsActivated) R.string.auth_subtitle_unlock else R.string.auth_subtitle_activate
        )

        ViewBindingObj.txtClockWarning.visibility =
            if (AuthManager.IsAutomaticTimeOff(ContextRef = this)) View.VISIBLE else View.GONE

        if (IsActivated) {
            val LicenseObj = AuthManager.LicenseOrNull(ContextRef = this)
            val LabelText = LicenseObj?.LabelText.orEmpty()
            ViewBindingObj.txtLicenseFooter.text = if (LabelText.isBlank()) {
                AuthManager.ExpiryText(ContextRef = this)
            } else {
                getString(
                    R.string.auth_licence_footer,
                    LabelText,
                    AuthManager.ExpiryText(ContextRef = this)
                )
            }
            ViewBindingObj.edtOtp.setText("")
            ViewBindingObj.txtUnlockError.visibility = View.GONE
            CountdownHandler.post(CountdownRunnable)
            ViewBindingObj.edtOtp.requestFocus()
        }
    }

    private fun WireActivationCard() {
        ViewBindingObj.btnCopyDeviceId.setOnClickListener {
            val ClipboardRef = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            ClipboardRef.setPrimaryClip(
                ClipData.newPlainText(
                    getString(R.string.auth_device_id_label),
                    DeviceIdentity.DisplayId(ContextRef = this)
                )
            )
            ShowActivationMessage(MessageRes = R.string.auth_device_id_copied, IsError = false)
        }

        ViewBindingObj.btnPasteBlob.setOnClickListener {
            val ClipboardRef = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val ClipText = ClipboardRef.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                .orEmpty()
            if (ClipText.isBlank()) {
                ShowActivationMessage(MessageRes = R.string.auth_clipboard_empty, IsError = true)
            } else {
                ViewBindingObj.edtActivation.setText(ClipText.trim())
            }
        }

        ViewBindingObj.btnActivate.setOnClickListener { AttemptActivation() }
    }

    private fun AttemptActivation() {
        val BlobText = ViewBindingObj.edtActivation.text?.toString().orEmpty().trim()
        if (BlobText.isEmpty()) {
            ShowActivationMessage(MessageRes = R.string.auth_error_blob_empty, IsError = true)
            return
        }

        when (AuthManager.Activate(ContextRef = this, BlobText = BlobText)) {
            is AuthManager.ActivationOutcome.Activated -> {
                HideKeyboard()
                ViewBindingObj.edtActivation.setText("")
                ShowActivationMessage(MessageRes = R.string.auth_activated, IsError = false)
                RenderState()
            }

            AuthManager.ActivationOutcome.WrongDevice ->
                ShowActivationMessage(MessageRes = R.string.auth_error_wrong_device, IsError = true)

            AuthManager.ActivationOutcome.AlreadyExpired ->
                ShowActivationMessage(MessageRes = R.string.auth_error_expired_blob, IsError = true)

            AuthManager.ActivationOutcome.BadSignature ->
                ShowActivationMessage(MessageRes = R.string.auth_error_bad_signature, IsError = true)

            AuthManager.ActivationOutcome.Malformed ->
                ShowActivationMessage(MessageRes = R.string.auth_error_malformed, IsError = true)

            AuthManager.ActivationOutcome.NoSigningKey ->
                ShowActivationMessage(MessageRes = R.string.auth_error_no_signing_key, IsError = true)
        }
    }

    private fun ShowActivationMessage(MessageRes: Int, IsError: Boolean) {
        ViewBindingObj.txtActivateError.visibility = View.VISIBLE
        ViewBindingObj.txtActivateError.setText(MessageRes)
        ViewBindingObj.txtActivateError.setTextColor(
            getColor(if (IsError) R.color.status_red_text else R.color.status_green_text)
        )
    }

    private fun WireUnlockCard() {
        ViewBindingObj.edtOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(TextRef: CharSequence?, StartVal: Int, CountVal: Int, AfterVal: Int) = Unit
            override fun onTextChanged(TextRef: CharSequence?, StartVal: Int, BeforeVal: Int, CountVal: Int) = Unit
            override fun afterTextChanged(EditableRef: Editable?) {
                ViewBindingObj.txtUnlockError.visibility = View.GONE
                if (EditableRef?.length == OTP_LENGTH && ViewBindingObj.btnUnlock.isEnabled) {
                    AttemptUnlock()
                }
            }
        })

        ViewBindingObj.btnUnlock.setOnClickListener { AttemptUnlock() }

        ViewBindingObj.btnClearActivation.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.auth_clear_title)
                .setMessage(R.string.auth_clear_message)
                .setNegativeButton(R.string.auth_cancel, null)
                .setPositiveButton(R.string.auth_clear_confirm) { _, _ ->
                    AuthManager.ClearActivation(ContextRef = this)
                    RenderState()
                }
                .show()
        }
    }

    private fun AttemptUnlock() {
        val CodeText = ViewBindingObj.edtOtp.text?.toString().orEmpty().trim()
        if (CodeText.length != OTP_LENGTH) {
            ShowUnlockError(MessageText = getString(R.string.auth_error_code_length))
            return
        }

        when (val OutcomeRef = AuthManager.VerifyCode(ContextRef = this, CodeText = CodeText)) {
            AuthManager.UnlockOutcome.Unlocked -> {
                HideKeyboard()
                GoToApp()
            }

            is AuthManager.UnlockOutcome.WrongCode -> {
                ViewBindingObj.edtOtp.setText("")
                ShowUnlockError(
                    MessageText = resources.getQuantityString(
                        R.plurals.auth_error_wrong_code,
                        OutcomeRef.AttemptsLeft,
                        OutcomeRef.AttemptsLeft
                    )
                )
            }

            AuthManager.UnlockOutcome.AlreadyUsed -> {
                ViewBindingObj.edtOtp.setText("")
                ShowUnlockError(MessageText = getString(R.string.auth_error_code_used))
            }

            is AuthManager.UnlockOutcome.LockedOut -> {
                ViewBindingObj.edtOtp.setText("")
                ShowUnlockError(MessageText = getString(R.string.auth_error_locked_out))
                CountdownHandler.post(CountdownRunnable)
            }

            AuthManager.UnlockOutcome.LicenceExpired ->
                ShowUnlockError(MessageText = getString(R.string.auth_error_licence_expired))

            AuthManager.UnlockOutcome.ClockRolledBack ->
                ShowUnlockError(MessageText = getString(R.string.auth_error_clock))

            AuthManager.UnlockOutcome.NotActivated -> RenderState()
        }
    }

    private fun ShowUnlockError(MessageText: String) {
        ViewBindingObj.txtUnlockError.visibility = View.VISIBLE
        ViewBindingObj.txtUnlockError.text = MessageText
    }

    private fun GoToApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun ShowTamperBlock() {
        ViewBindingObj.cardActivate.visibility = View.GONE
        ViewBindingObj.cardUnlock.visibility = View.GONE
        ViewBindingObj.txtClockWarning.visibility = View.VISIBLE
        ViewBindingObj.txtClockWarning.setText(R.string.auth_error_tampered)
        ViewBindingObj.txtSubtitle.setText(R.string.auth_error_tampered_subtitle)
    }

    private fun HideKeyboard() {
        val ManagerRef = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        ManagerRef.hideSoftInputFromWindow(ViewBindingObj.root.windowToken, 0)
    }

    @SuppressLint("DefaultLocale")
    private fun FormatDuration(SecondsVal: Long): String {
        val MinutesVal = SecondsVal / 60
        val RemainderSeconds = SecondsVal % 60
        return if (MinutesVal > 0) {
            String.format("%d:%02d", MinutesVal, RemainderSeconds)
        } else {
            "${RemainderSeconds}s"
        }
    }

    companion object {
        private const val OTP_LENGTH = 6
    }
}
