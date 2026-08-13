@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader

import android.app.Application
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.security.AppLockObserver
import com.bliss.screenreader.security.SecurePrefs

class DataReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Instance = this

        registerActivityLifecycleCallbacks(AppLockObserver())

        Thread {
            SecurePrefs.MigrateExisting(
                ContextRef = this,
                PrefsName = PolicyRepository.PREFS_NAME
            )
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    companion object {
        lateinit var Instance: DataReaderApp
            private set
    }
}
