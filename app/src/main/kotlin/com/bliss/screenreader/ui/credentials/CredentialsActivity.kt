@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.credentials

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ActivityCredentialsBinding
import com.bliss.screenreader.databinding.PartialSettingsChoiceRowBinding
import com.bliss.screenreader.databinding.PartialSettingsRowBinding
import com.bliss.screenreader.databinding.SheetSettingsDetailBinding
import com.bliss.screenreader.security.CredentialStore
import com.bliss.screenreader.ui.SetupEdgeToEdge
import com.bliss.screenreader.utils.HapticFeedback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.bliss.screenreader.ui.toast.AppToast

class CredentialsActivity : AppCompatActivity() {

    private lateinit var ViewBindingObj: ActivityCredentialsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewBindingObj = ActivityCredentialsBinding.inflate(layoutInflater)
        setContentView(ViewBindingObj.root)

        SetupEdgeToEdge(RootView = ViewBindingObj.root, AppBarView = ViewBindingObj.toolbar)
        ViewBindingObj.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        RenderAll()
    }

    private fun RenderAll() {
        val MethodVal = CredentialStore.MethodOf(ContextRef = this)
        val AutoOn = CredentialStore.IsAutoEnterOn(ContextRef = this)
        val HasMpin = CredentialStore.HasSecretFor(
            ContextRef = this,
            MethodVal = CredentialStore.Method.MPIN
        )
        val HasPassword = CredentialStore.HasSecretFor(
            ContextRef = this,
            MethodVal = CredentialStore.Method.PASSWORD
        )
        val HasChosen = if (MethodVal == CredentialStore.Method.MPIN) HasMpin else HasPassword

        BindRow(
            RowBinding = ViewBindingObj.rowAuto,
            TitleText = getString(R.string.credentials_auto_title),
            DescText = when {
                !AutoOn -> getString(R.string.credentials_auto_off)
                MethodVal == CredentialStore.Method.MPIN ->
                    getString(R.string.credentials_auto_on_mpin)

                else -> getString(R.string.credentials_auto_on_password)
            },
            SwitchState = AutoOn
        ) { OnAutoToggled(NextVal = !AutoOn) }

        val FooterText = if (HasChosen) {
            getString(R.string.credentials_auto_footer)
        } else {
            NeedsSecretText(MethodVal = MethodVal) + " " +
                    getString(R.string.credentials_auto_footer)
        }
        ViewBindingObj.tvAutoFooter.visibility = View.VISIBLE
        ViewBindingObj.tvAutoFooter.text = FooterText

        BindRow(
            RowBinding = ViewBindingObj.rowMethod,
            TitleText = getString(R.string.credentials_method_title),
            ValueText = MethodLabel(MethodVal = MethodVal),
            ShowChevron = true
        ) { ShowMethodSheet() }

        BindRow(
            RowBinding = ViewBindingObj.rowMpin,
            TitleText = getString(R.string.credentials_mpin),
            DescText = if (HasMpin) {
                getString(R.string.credentials_mpin_saved)
            } else {
                getString(R.string.credentials_not_saved)
            },
            BadgeText = if (MethodVal == CredentialStore.Method.MPIN) {
                getString(R.string.credentials_badge_in_use)
            } else {
                ""
            },
            ShowChevron = true
        ) { OpenPageFor(MethodVal = CredentialStore.Method.MPIN) }

        BindRow(
            RowBinding = ViewBindingObj.rowPassword,
            TitleText = getString(R.string.credentials_password),
            DescText = if (HasPassword) {
                getString(R.string.credentials_password_saved)
            } else {
                getString(R.string.credentials_not_saved)
            },
            BadgeText = if (MethodVal == CredentialStore.Method.PASSWORD) {
                getString(R.string.credentials_badge_in_use)
            } else {
                ""
            },
            ShowChevron = true,
            ShowDivider = true
        ) { OpenPageFor(MethodVal = CredentialStore.Method.PASSWORD) }

        RenderAlert()
    }

    private fun RenderAlert() {
        val RejectedVal = CredentialStore.RejectedMethod(ContextRef = this)
        if (RejectedVal == null) {
            ViewBindingObj.tvCredentialsAlert.visibility = View.GONE
            return
        }
        ViewBindingObj.tvCredentialsAlert.visibility = View.VISIBLE
        ViewBindingObj.tvCredentialsAlert.setText(
            if (RejectedVal == CredentialStore.Method.MPIN) {
                R.string.credentials_rejected_mpin
            } else {
                R.string.credentials_rejected_password
            }
        )
    }

    private fun BindRow(
        RowBinding: PartialSettingsRowBinding,
        TitleText: CharSequence,
        DescText: CharSequence = "",
        ValueText: CharSequence = "",
        BadgeText: CharSequence = "",
        SwitchState: Boolean? = null,
        ShowChevron: Boolean = false,
        ShowDivider: Boolean = false,
        OnClick: () -> Unit
    ) {
        RowBinding.rowDivider.visibility = if (ShowDivider) View.VISIBLE else View.GONE
        RowBinding.ivRowIcon.visibility = View.GONE
        RowBinding.tvRowTitle.text = TitleText

        RowBinding.tvRowDesc.visibility = if (DescText.isEmpty()) View.GONE else View.VISIBLE
        RowBinding.tvRowDesc.text = DescText

        RowBinding.tvRowValue.visibility = if (ValueText.isEmpty()) View.GONE else View.VISIBLE
        RowBinding.tvRowValue.text = ValueText

        if (BadgeText.isEmpty()) {
            RowBinding.tvRowBadge.visibility = View.GONE
        } else {
            RowBinding.tvRowBadge.visibility = View.VISIBLE
            RowBinding.tvRowBadge.text = BadgeText
            RowBinding.tvRowBadge.setBackgroundResource(R.drawable.bg_badge_inforce)
            RowBinding.tvRowBadge.setTextColor(
                ContextCompat.getColor(this, R.color.status_green_text)
            )
        }

        if (SwitchState == null) {
            RowBinding.swRow.visibility = View.GONE
        } else {
            RowBinding.swRow.visibility = View.VISIBLE
            RowBinding.swRow.isChecked = SwitchState
        }

        RowBinding.ivRowChevron.visibility = if (ShowChevron) View.VISIBLE else View.GONE
        RowBinding.ivRowChevron.imageTintList =
            ContextCompat.getColorStateList(this, R.color.text_faint)

        RowBinding.settingsRow.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            OnClick()
        }
    }

    private fun MethodLabel(MethodVal: CredentialStore.Method): String = getString(
        if (MethodVal == CredentialStore.Method.MPIN) {
            R.string.credentials_mpin
        } else {
            R.string.credentials_password
        }
    )

    private fun NeedsSecretText(MethodVal: CredentialStore.Method): String = getString(
        if (MethodVal == CredentialStore.Method.MPIN) {
            R.string.credentials_needs_mpin
        } else {
            R.string.credentials_needs_password
        }
    )

    private fun OnAutoToggled(NextVal: Boolean) {
        val MethodVal = CredentialStore.MethodOf(ContextRef = this)
        if (NextVal && !CredentialStore.HasSecretFor(ContextRef = this, MethodVal = MethodVal)) {
            ShowMessage(
                MessageText = NeedsSecretText(MethodVal = MethodVal),
                KindVal = AppToast.Kind.Warning
            )
            OpenPageFor(MethodVal = MethodVal)
            return
        }
        CredentialStore.SetAutoEnter(ContextRef = this, EnabledVal = NextVal)
        RenderAll()
    }

    private fun OpenPageFor(MethodVal: CredentialStore.Method) {
        startActivity(
            CredentialDetailActivity.IntentFor(ContextRef = this, MethodVal = MethodVal)
        )
    }

    private fun ShowMethodSheet() {
        val Options = listOf(CredentialStore.Method.MPIN, CredentialStore.Method.PASSWORD)
        val CurrentVal = CredentialStore.MethodOf(ContextRef = this)
        val SheetBinding = SheetSettingsDetailBinding.inflate(layoutInflater)
        val SheetDialog = BottomSheetDialog(this)
        SheetDialog.setContentView(SheetBinding.root)
        SheetBinding.tvDetailTitle.setText(R.string.credentials_method_title)
        SheetBinding.tvDetailBody.visibility = View.VISIBLE
        SheetBinding.tvDetailBody.setText(R.string.credentials_method_body)
        SheetBinding.btnDetailClose.setOnClickListener { ViewRef ->
            HapticFeedback.Tap(ViewRef = ViewRef)
            SheetDialog.dismiss()
        }

        for (MethodVal in Options) {
            val ChoiceBinding = PartialSettingsChoiceRowBinding.inflate(
                layoutInflater, SheetBinding.detailContainer, false
            )
            ChoiceBinding.tvChoiceTitle.text = MethodLabel(MethodVal = MethodVal)
            ChoiceBinding.tvChoiceDesc.visibility = View.VISIBLE
            ChoiceBinding.tvChoiceDesc.setText(
                if (MethodVal == CredentialStore.Method.MPIN) {
                    R.string.credentials_method_mpin_desc
                } else {
                    R.string.credentials_method_password_desc
                }
            )
            ChoiceBinding.rbChoice.isChecked = MethodVal == CurrentVal
            ChoiceBinding.choiceRow.setOnClickListener { ViewRef ->
                HapticFeedback.Tap(ViewRef = ViewRef)
                SheetDialog.dismiss()
                CredentialStore.SetMethod(ContextRef = this, MethodVal = MethodVal)
                RenderAll()
                if (!CredentialStore.HasSecretFor(ContextRef = this, MethodVal = MethodVal)) {
                    OpenPageFor(MethodVal = MethodVal)
                }
            }
            SheetBinding.detailContainer.addView(ChoiceBinding.root)
        }

        SheetDialog.show()
    }

    private fun ShowMessage(
        MessageText: String,
        KindVal: AppToast.Kind = AppToast.Kind.Info
    ) {
        AppToast.Show(ContextRef = this, MessageText = MessageText, KindVal = KindVal)
    }
}
