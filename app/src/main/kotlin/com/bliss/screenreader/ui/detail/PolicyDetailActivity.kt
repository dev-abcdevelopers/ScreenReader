@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.detail

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.bliss.screenreader.R


class PolicyDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ContainerView = FrameLayout(this).apply { id = R.id.detailHost }
        setContentView(ContainerView)

        if (savedInstanceState != null) return
        supportFragmentManager.beginTransaction()
            .add(
                R.id.detailHost,
                PolicyDetailFragment.NewInstance(
                    PolicyNumber = intent.getStringExtra(EXTRA_POLICY_NUMBER).orEmpty(),
                    SessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty(),
                    Embedded = false,
                    TwoColumn = false
                ),
                PolicyDetailFragment.TAG
            )
            .commit()
    }

    companion object {
        const val EXTRA_POLICY_NUMBER = "extra_policy_number"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
