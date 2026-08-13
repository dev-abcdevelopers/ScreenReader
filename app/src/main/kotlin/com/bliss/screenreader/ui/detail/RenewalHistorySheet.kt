@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.bliss.screenreader.R
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.databinding.SheetRenewalHistoryBinding
import com.bliss.screenreader.ui.adapter.RenewalRowAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * A policy's renewal rows, reachable from the detail screen.
 *
 * The old detail page showed "4 entries" as dead text with no way to open
 * them, which advertised data the app then refused to show.
 */
class RenewalHistorySheet : BottomSheetDialogFragment() {

    private var ViewBindingObj: SheetRenewalHistoryBinding? = null
    private val AdapterObj = RenewalRowAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val BindingObj = SheetRenewalHistoryBinding.inflate(inflater, container, false)
        ViewBindingObj = BindingObj
        return BindingObj.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val BindingObj = ViewBindingObj ?: return

        val PolicyNumber = arguments?.getString(ARG_POLICY_NUMBER).orEmpty()
        val RenewalList = PolicyRepository.GetFupPolicies(ContextRef = requireContext())
            .filter { RenewalItem -> RenewalItem.PolicyNumber == PolicyNumber }
            .sortedByDescending { RenewalItem -> RenewalItem.PaymentDate }

        BindingObj.rvRenewalSheet.layoutManager = LinearLayoutManager(requireContext())
        BindingObj.rvRenewalSheet.adapter = AdapterObj
        AdapterObj.UpdateData(NewRenewals = RenewalList)

        BindingObj.tvRenewalSheetMeta.text = getString(
            R.string.detail_entries_format,
            RenewalList.size
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ViewBindingObj = null
    }

    companion object {
        const val TAG = "RenewalHistorySheet"
        private const val ARG_POLICY_NUMBER = "arg_policy_number"

        fun NewInstance(PolicyNumber: String): RenewalHistorySheet {
            return RenewalHistorySheet().apply {
                arguments = Bundle().apply { putString(ARG_POLICY_NUMBER, PolicyNumber) }
            }
        }
    }
}
