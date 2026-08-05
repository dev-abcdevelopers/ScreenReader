@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.repository

import android.content.Context
import com.bliss.screenreader.data.model.CustomerPolicy
import com.bliss.screenreader.data.model.FupPolicy
import com.bliss.screenreader.data.model.PsPolicy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

object PolicyRepository {

    private const val PREFS_NAME = "data_reader_prefs"
    private const val KEY_CUSTOMER_POLICIES = "key_customer_policies"
    private const val KEY_FUP_POLICIES = "key_fup_policies"
    private const val KEY_PS_POLICIES = "key_ps_policies"

    private val GsonInstance = Gson()

    fun SaveCustomerPolicies(ContextRef: Context, Policies: List<CustomerPolicy>) {
        val Prefs = ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val JsonStr = GsonInstance.toJson(Policies)
        Prefs.edit { putString(KEY_CUSTOMER_POLICIES, JsonStr) }
    }

    fun GetCustomerPolicies(ContextRef: Context): List<CustomerPolicy> {
        val Prefs = ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val JsonStr = Prefs.getString(KEY_CUSTOMER_POLICIES, null) ?: return emptyList()
        val DataType = object : TypeToken<List<CustomerPolicy>>() {}.type
        return try {
            GsonInstance.fromJson(JsonStr, DataType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun SaveFupPolicies(ContextRef: Context, Policies: List<FupPolicy>) {
        val Prefs = ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val JsonStr = GsonInstance.toJson(Policies)
        Prefs.edit { putString(KEY_FUP_POLICIES, JsonStr) }
    }

    fun GetFupPolicies(ContextRef: Context): List<FupPolicy> {
        val Prefs = ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val JsonStr = Prefs.getString(KEY_FUP_POLICIES, null) ?: return emptyList()
        val DataType = object : TypeToken<List<FupPolicy>>() {}.type
        return try {
            GsonInstance.fromJson(JsonStr, DataType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun SavePsPolicies(ContextRef: Context, Policies: List<PsPolicy>) {
        val Prefs = ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val JsonStr = GsonInstance.toJson(Policies)
        Prefs.edit { putString(KEY_PS_POLICIES, JsonStr) }
    }

    fun GetPsPolicies(ContextRef: Context): List<PsPolicy> {
        val Prefs = ContextRef.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val JsonStr = Prefs.getString(KEY_PS_POLICIES, null) ?: return emptyList()
        val DataType = object : TypeToken<List<PsPolicy>>() {}.type
        return try {
            GsonInstance.fromJson(JsonStr, DataType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
