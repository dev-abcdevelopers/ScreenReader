@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.licence

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bliss.screenreader.BuildConfig
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ActivityLicenceBinding
import com.bliss.screenreader.security.AuthManager
import com.bliss.screenreader.security.BlissLicenceClient
import com.bliss.screenreader.security.BlissLicenceStore
import com.bliss.screenreader.security.DeviceIdentity
import com.bliss.screenreader.security.IntegrityGuard
import com.bliss.screenreader.ui.main.MainActivity
import java.util.concurrent.Executors

class LicenceActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityLicenceBinding

    private val MainHandler = Handler(Looper.getMainLooper())
    private val WorkerRef = Executors.newSingleThreadExecutor()

    @Volatile
    private var CheckRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthManager.BypassActive) {
            GoToApp()
            return
        }

        enableEdgeToEdge()
        ViewBindingObj = ActivityLicenceBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        ApplyInsets()

        BlissLicenceStore.NoteClockSeen(ContextRef = this)

        val DeviceIdText = DeviceIdentity.RegistrationId(ContextRef = this)
        ViewBindingObj.txtDeviceId.text = DeviceIdentity.GroupForDisplay(IdText = DeviceIdText)

        if (IntegrityGuard.IsTampered(ContextRef = this)) {
            ShowTampered()
            return
        }

        if (BlissLicenceStore.IsFresh(ContextRef = this)) {
            GoToApp()
            return
        }

        StartCheck()
    }

    override fun onDestroy() {
        super.onDestroy()
        MainHandler.removeCallbacksAndMessages(null)
        WorkerRef.shutdownNow()
    }

    private fun ApplyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(ViewBindingObj.root) { _, WindowInsetsObj ->
            val BarInsets = WindowInsetsObj.getInsets(WindowInsetsCompat.Type.systemBars())
            ViewBindingObj.root.updatePadding(top = BarInsets.top, bottom = BarInsets.bottom)
            WindowInsetsObj
        }
    }

    private fun StartCheck() {
        if (CheckRunning) return
        CheckRunning = true
        ShowChecking()
        WorkerRef.execute {
            val VerdictRef = BlissLicenceClient.Check(ContextRef = applicationContext)
            MainHandler.post {
                CheckRunning = false
                if (!isFinishing && !isDestroyed) HandleVerdict(VerdictRef = VerdictRef)
            }
        }
    }

    private fun HandleVerdict(VerdictRef: BlissLicenceClient.Verdict) {
        when (VerdictRef) {
            BlissLicenceClient.Verdict.Valid -> {
                BlissLicenceStore.RecordSuccess(ContextRef = this)
                ShowValid()
                MainHandler.postDelayed({ if (!isFinishing) GoToApp() }, SUCCESS_DWELL_MS)
            }

            BlissLicenceClient.Verdict.NotLicensed -> {
                BlissLicenceStore.Clear(ContextRef = this)
                ShowBlocked(
                    TitleRes = R.string.licence_blocked_title,
                    BodyRes = R.string.licence_blocked_body
                )
            }

            BlissLicenceClient.Verdict.NoDeviceId -> {
                BlissLicenceStore.Clear(ContextRef = this)
                ShowBlocked(
                    TitleRes = R.string.licence_no_device_id_title,
                    BodyRes = R.string.licence_no_device_id_body
                )
            }

            BlissLicenceClient.Verdict.Unreachable -> ShowOffline()
        }
    }

    private fun ShowChecking() {
        ViewBindingObj.imgShield.visibility = View.VISIBLE
        ViewBindingObj.imgResult.visibility = View.GONE
        ViewBindingObj.txtHelpline.visibility = View.GONE
        ViewBindingObj.btnStack.visibility = View.GONE
        ViewBindingObj.txtTitle.setText(R.string.licence_checking_title)
        ViewBindingObj.txtBody.setText(R.string.licence_checking_body)
        (ViewBindingObj.imgShield.drawable as? Animatable)?.start()
    }

    private fun ShowValid() {
        StopShield()
        ViewBindingObj.imgResult.setImageResource(R.drawable.ic_licence_ok)
        ViewBindingObj.imgResult.visibility = View.VISIBLE
        ViewBindingObj.txtHelpline.visibility = View.GONE
        ViewBindingObj.btnStack.visibility = View.GONE
        ViewBindingObj.txtTitle.setText(R.string.licence_valid_title)
        ViewBindingObj.txtBody.setText(R.string.licence_valid_body)
    }

    private fun ShowBlocked(TitleRes: Int, BodyRes: Int) {
        StopShield()
        ViewBindingObj.imgResult.setImageResource(R.drawable.ic_licence_blocked)
        ViewBindingObj.imgResult.visibility = View.VISIBLE
        ViewBindingObj.txtTitle.setText(TitleRes)
        ViewBindingObj.txtBody.setText(BodyRes)

        ViewBindingObj.txtHelpline.visibility = View.VISIBLE
        ViewBindingObj.txtHelpline.text = BuildConfig.SUPPORT_PHONE_DISPLAY

        ViewBindingObj.btnStack.visibility = View.VISIBLE
        ViewBindingObj.btnPrimary.setText(R.string.licence_call_support)
        ViewBindingObj.btnPrimary.setOnClickListener { DialSupport() }
        ViewBindingObj.btnSecondary.visibility = View.VISIBLE
        ViewBindingObj.btnSecondary.setText(R.string.licence_copy_device_id)
        ViewBindingObj.btnSecondary.setOnClickListener { CopyDeviceId() }
    }

    private fun ShowOffline() {
        StopShield()
        ViewBindingObj.imgResult.setImageResource(R.drawable.ic_licence_offline)
        ViewBindingObj.imgResult.visibility = View.VISIBLE
        ViewBindingObj.txtHelpline.visibility = View.GONE
        ViewBindingObj.txtTitle.setText(R.string.licence_offline_title)

        val LastOkAt = BlissLicenceStore.LastOkAt(ContextRef = this)
        val NowMillis = System.currentTimeMillis()
        ViewBindingObj.txtBody.text = when {
            LastOkAt <= 0L -> getString(R.string.licence_offline_body_never)
            else -> {
                val DaysAgo = BlissLicenceStore.DaysSinceLastCheck(
                    LastOkAt = LastOkAt,
                    NowMillis = NowMillis
                )
                if (DaysAgo == 0) {
                    getString(R.string.licence_offline_body_today)
                } else {
                    resources.getQuantityString(
                        R.plurals.licence_offline_body_days,
                        DaysAgo,
                        DaysAgo
                    )
                }
            }
        }

        ViewBindingObj.btnStack.visibility = View.VISIBLE
        ViewBindingObj.btnPrimary.setText(R.string.licence_retry)
        ViewBindingObj.btnPrimary.setOnClickListener { StartCheck() }

        if (BlissLicenceStore.IsUsable(ContextRef = this)) {
            val DaysLeft = BlissLicenceStore.GraceDaysLeft(
                LastOkAt = LastOkAt,
                NowMillis = NowMillis
            )
            ViewBindingObj.btnSecondary.visibility = View.VISIBLE
            ViewBindingObj.btnSecondary.text = resources.getQuantityString(
                R.plurals.licence_continue_offline,
                DaysLeft,
                DaysLeft
            )
            ViewBindingObj.btnSecondary.setOnClickListener { GoToApp() }
        } else {
            ViewBindingObj.btnSecondary.visibility = View.GONE
        }
    }

    private fun ShowTampered() {
        StopShield()
        ViewBindingObj.imgResult.setImageResource(R.drawable.ic_licence_blocked)
        ViewBindingObj.imgResult.visibility = View.VISIBLE
        ViewBindingObj.btnStack.visibility = View.GONE
        ViewBindingObj.txtHelpline.visibility = View.GONE
        ViewBindingObj.txtTitle.setText(R.string.licence_tampered_title)
        ViewBindingObj.txtBody.setText(R.string.licence_tampered_body)
    }

    private fun StopShield() {
        (ViewBindingObj.imgShield.drawable as? Animatable)?.stop()
        ViewBindingObj.imgShield.visibility = View.GONE
    }

    private fun DialSupport() {
        val NumberText = BuildConfig.SUPPORT_PHONE
        if (NumberText.isEmpty()) return
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$NumberText")))
        } catch (_: Exception) {
            CopySupportNumber()
        }
    }

    private fun CopySupportNumber() {
        Clipboard().setPrimaryClip(
            ClipData.newPlainText("support", BuildConfig.SUPPORT_PHONE_DISPLAY)
        )
    }

    private fun CopyDeviceId() {
        Clipboard().setPrimaryClip(
            ClipData.newPlainText(
                "device id",
                DeviceIdentity.RegistrationId(ContextRef = this)
            )
        )
        Toast.makeText(this, R.string.licence_device_id_copied, Toast.LENGTH_SHORT).show()
    }

    private fun Clipboard(): ClipboardManager =
        getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

    private fun GoToApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        private const val SUCCESS_DWELL_MS = 700L
    }
}
