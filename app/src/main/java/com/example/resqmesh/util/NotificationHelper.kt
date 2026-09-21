package com.example.resqmesh.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.resqmesh.MainActivity
import com.example.resqmesh.R

object NotificationHelper {
    private const val EMERGENCY_CHANNEL_ID = "emergency_alerts"
    private const val EMERGENCY_NOTIFICATION_ID = 911

    fun showEmergencyNotification(context: Context, senderName: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "Emergency SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts from nearby ResQmesh nodes"
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 EMERGENCY SOS RECEIVED")
            .setContentText("$senderName: $message")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setFullScreenIntent(pendingIntent, true) // Makes it pop up on screen
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(EMERGENCY_NOTIFICATION_ID, notification)
    }
}
