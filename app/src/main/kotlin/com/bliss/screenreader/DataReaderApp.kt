@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader

import android.app.Application
import com.bliss.screenreader.data.repository.PolicyRepository
import com.bliss.screenreader.security.AppLockObserver
import com.bliss.screenreader.security.SecurePrefs
import com.bliss.screenreader.service.CaptureDiagnostics
import com.bliss.screenreader.settings.SettingsStore
import java.util.concurrent.Executors

class DataReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Instance = this
        SettingsStore.ApplyGlobals(ContextRef = this)
        registerActivityLifecycleCallbacks(AppLockObserver())
        val SlowIoWorker = Executors.newSingleThreadExecutor()
        SecurePrefs.SlowIoListener = { MessageText ->
            SlowIoWorker.execute {
                runCatching {
                    CaptureDiagnostics.Log(
                        ContextObj = this,
                        EventName = "SLOW_IO",
                        MessageText = MessageText
                    )
                }
            }
        }
        Thread {
            SecurePrefs.MigrateExisting(
                ContextRef = this,
                PrefsName = PolicyRepository.PREFS_NAME
            )
            runCatching {
                val MovedCount = SecurePrefs.Of(
                    ContextRef = this,
                    PrefsName = PolicyRepository.PREFS_NAME
                ).MigrateBlobs()
                if (MovedCount > 0) {
                    CaptureDiagnostics.Log(
                        ContextObj = this,
                        EventName = "BLOB_MIGRATE",
                        MessageText = "moved=$MovedCount"
                    )
                }
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    companion object {
        lateinit var Instance: DataReaderApp
            private set
    }
}
