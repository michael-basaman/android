package com.example.freediskshredder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service.NOTIFICATION_SERVICE
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.freediskshredder.MainService.Companion.bytesWritten
import com.example.freediskshredder.MainService.Companion.freeSpace
import com.example.freediskshredder.MainService.Companion.isRunning
import com.example.freediskshredder.MainService.Companion.serviceRunIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainNotification {
//    constructor(context: Context) : this() {
//        // this.context = context
//        this.notificationId = setNotificationId()
//    }

    fun createNotificationChannel(context: Context) {
        val name = "Free Disk Shredder"
        val importance = NotificationManager.IMPORTANCE_LOW

        val channel = NotificationChannel(channelId, name, importance)

        val notificationManager = context.getSystemService(NotificationManager::class.java)

        notificationManager?.createNotificationChannel(channel)
    }

    fun getNotificationId(context: Context): Int {
        if(notificationId == 0) {
            notificationId = setNotificationId(context)
        }
        return notificationId
    }

    fun createPersistentNotification(context: Context): Notification {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        var notification: Notification = createNotification(context)

        notificationManager?.notify(notificationId, notification)

        return notification
    }

    fun createNotification(context: Context): Notification {
        val percentValue: String = getPercentValue()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your notification icon
            .setContentTitle("Free Disk Shredder")
            .setContentText("Run $serviceRunIndex: $percentValue% complete")
            .setOngoing(true)

        return builder.build()
    }

    suspend fun updateNotifications(context: Context) {
        return withContext(Dispatchers.IO) {
            try {
                while(isRunning) {
                    delay(500L)

                    if(isRunning) {
                        updateNotification(context)
                    }
                }
            } catch(_: Exception) { }
        }
    }

    fun clearNotification(context: Context) {
        if (notificationId > 0) {
            try {
                val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)

            } catch(_: Exception) { }
        }
    }

    private fun setNotificationId(context: Context): Int {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notifications: Array<StatusBarNotification> = notificationManager.getActiveNotifications()

        val existingNotifications: HashSet<Int> = HashSet()
        for(notification in notifications) {
            existingNotifications.add(notification.id)
        }

        var id = 0
        while(true) {
            id += 1

            if(!existingNotifications.contains(id)) {
                return id
            }
        }
    }

    private fun updateNotification(context: Context) {
        if (notificationId > 0
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, createNotification(context))
        }
    }

    private fun getPercentValue(): String {
        var percent = 0.0

        if(freeSpace > 0) {
            percent = bytesWritten.toDouble() / freeSpace.toDouble()
        }

        if(percent < 0.0) {
            percent = 0.0
        } else if(percent > 1.0) {
            percent = 1.0
        }

        return ((percent * 1000).toInt().toDouble() / 10.0).toString()
    }

    private val channelId = "free_disk_shredder"
    private var notificationId: Int = 0
}