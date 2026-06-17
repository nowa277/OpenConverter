package com.openconverter.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.openconverter.app.R

object ProgressNotification {
    const val CHANNEL_ID = "openconverter_conversion"
    const val NOTIFICATION_ID = 100

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "转换进度",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    fun build(context: Context, title: String, progress: Int, total: Int): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("$progress / $total")
            .setProgress(total, progress, false)
            .setOngoing(true)
            .build()
    }
}
