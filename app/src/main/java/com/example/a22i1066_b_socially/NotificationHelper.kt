// file: `app/src/main/java/com/example/a22i1066_b_socially/NotificationHelper.kt`
package com.example.a22i1066_b_socially

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class NotificationHelper(private val ctx: Context) {
    companion object {
        private const val CHANNEL_ID = "app_notifications"
        private const val CHANNEL_NAME = "App Notifications"
        private const val TAG = "NotificationHelper"
    }

    init { createChannelIfNeeded() }

    private fun createChannelIfNeeded() {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for messages, follow requests, alerts"
                enableLights(true); lightColor = Color.MAGENTA; setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun resolveSmallIcon(): Int {
        val pkg = ctx.packageName
        val resId = ctx.resources.getIdentifier("ic_notification", "drawable", pkg)
        return if (resId != 0) resId else ctx.applicationInfo.icon
    }

    fun showNotification(id: Int, title: String, body: String, pendingIntent: PendingIntent, largeImage: Bitmap? = null) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!ok) { Log.w(TAG, "POST_NOTIFICATIONS not granted — skipping notification"); return }
        }

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(resolveSmallIcon())
            .setContentTitle(title.ifBlank { "App" })
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (body.length > 80) builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))

        if (largeImage != null) {
            builder.setLargeIcon(largeImage)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(largeImage)
                    .bigLargeIcon(null as Bitmap?)
            )
        }

        try {
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
        } catch (se: SecurityException) {
            Log.w(TAG, "Failed to notify — missing permission", se)
        }
    }
}
