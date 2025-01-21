package com.example.freediskshredder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service.NOTIFICATION_SERVICE
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.freediskshredder.MainService.Companion.isRunning
import com.example.freediskshredder.MainService.Companion.runCount
import com.example.freediskshredder.MainService.Companion.serviceRunIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainNotification {
    private val channelId: String = "anticirculatory.diskshredder"
    private var notificationId: Int = 0
    private lateinit var intent: Intent
    private lateinit var pendingIntent: PendingIntent

    fun createNotificationChannel(context: Context) {
        val name = "Anticirculatory Disk Shredder"
        val importance = NotificationManager.IMPORTANCE_LOW

        val channel = NotificationChannel(channelId, name, importance)

        notificationId = setNotificationId(context, channel.hashCode())

        intent = Intent(context, MainActivity::class.java)

        pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    fun getNotificationId(): Int {
        return notificationId
    }

    fun createNotification(context: Context): Notification {
        val percent: Double = getPercent()
        val text: String

        if(serviceRunIndex == 0) {
            text = "Starting"
        } else if(percent < 0.0) {
            if(runCount == 1) {
                text = "Cleaning"
            } else {
                text = "Run $serviceRunIndex: Cleaning"
            }
        } else {
            val percentValue = ((percent * 1000).toInt().toDouble() / 10.0).toString()

            if(runCount == 1) {
                text = "$percentValue% complete"
            } else {
                text = "Run $serviceRunIndex: $percentValue% complete"
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Disk Shredder")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        return builder.build()
    }

    suspend fun updateNotifications(context: Context) {
        return withContext(Dispatchers.IO) {
            while(isRunning) {
                delay(5000L)

                if(isRunning) {
                    updateNotification(context)
                }
            }
        }
    }

    fun clearNotification(context: Context) {
        if (notificationId > 0) {
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }

    private fun setNotificationId(context: Context, initial: Int): Int {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notifications: Array<StatusBarNotification> = notificationManager.getActiveNotifications()

        val existingNotifications: HashSet<Int> = HashSet()
        for(notification in notifications) {
            existingNotifications.add(notification.id)
        }

        var id = initial
        while(true) {
            if(!existingNotifications.contains(id)) {
                return id
            }

            id += 1
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

    private fun getPercent(): Double {
        var percent: Double

        if(MainService.totalDiskSpace > 0L) {
            percent = (MainService.totalDiskSpace - MainService.freeSpaceLeft).toDouble() / MainService.totalDiskSpace.toDouble()
        } else {
            percent = 0.0
        }

        if(MainService.freeSpaceLeft < 0L) {
            percent = -1.0
        } else if(percent < 0.0) {
            percent = 0.0
        } else if(percent > 1.0) {
            percent = 1.0
        }

        return percent
    }
}