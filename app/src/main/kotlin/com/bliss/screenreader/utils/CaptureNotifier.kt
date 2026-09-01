@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.CaptureMode
import com.bliss.screenreader.ui.main.MainActivity

object CaptureNotifier {

    private const val CHANNEL_ID = "capture_results"
    private const val NOTIFICATION_ID = 1002

    fun AreNotificationsOn(ContextRef: Context): Boolean {
        return runCatching {
            NotificationManagerCompat.from(ContextRef).areNotificationsEnabled()
        }.getOrDefault(false)
    }

    fun NotifySessionSaved(
        ContextRef: Context,
        ModeVal: CaptureMode,
        SavedCount: Int,
        GapCount: Int
    ) {
        val AppContext = ContextRef.applicationContext
        if (!AreNotificationsOn(ContextRef = AppContext)) return

        runCatching {
            EnsureChannel(ContextRef = AppContext)

            val ContentIntent = PendingIntent.getActivity(
                AppContext,
                0,
                Intent(AppContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val BodyText = if (GapCount > 0) {
                AppContext.getString(
                    R.string.notify_saved_body_gaps,
                    ModeVal.DescribeCount(CountVal = SavedCount),
                    GapCount
                )
            } else {
                AppContext.getString(
                    R.string.notify_saved_body,
                    ModeVal.DescribeCount(CountVal = SavedCount)
                )
            }

            val NotificationObj = NotificationCompat.Builder(AppContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check_circle)
                .setContentTitle(AppContext.getString(R.string.notify_saved_title))
                .setContentText(BodyText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(BodyText))
                .setContentIntent(ContentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(AppContext)
                .notify(NOTIFICATION_ID, NotificationObj)
        }
    }

    private fun EnsureChannel(ContextRef: Context) {
        val ManagerRef = ContextRef.getSystemService(Context.NOTIFICATION_SERVICE)
                as? NotificationManager ?: return
        if (ManagerRef.getNotificationChannel(CHANNEL_ID) != null) return
        ManagerRef.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ContextRef.getString(R.string.notify_channel_results),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = ContextRef.getString(R.string.notify_channel_results_desc)
            }
        )
    }
}
