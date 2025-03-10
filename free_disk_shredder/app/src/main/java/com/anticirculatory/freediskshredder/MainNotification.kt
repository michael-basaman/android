package com.anticirculatory.freediskshredder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service.NOTIFICATION_SERVICE
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.anticirculatory.freediskshredder.MainService.Companion.isRunning
import com.anticirculatory.freediskshredder.MainService.Companion.runCount
import com.anticirculatory.freediskshredder.MainService.Companion.serviceRunIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainNotification {
    private lateinit var channelId: String
    private var notificationId: Int = 0
    private lateinit var intent: Intent
    private lateinit var pendingIntent: PendingIntent

    fun createNotificationChannel(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        while(true) {
            val randomString = MainUtils.getRandomString(8)

            if (notificationManager.getNotificationChannel(randomString) == null) {
                channelId = randomString
                break
            }
        }

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

        notificationManager?.createNotificationChannel(channel)
    }

    fun getNotificationId(): Int {
        return notificationId
    }

    private fun getNotificationCode(): Long {
        return if(serviceRunIndex == 0) {
            -1
        } else if(MainService.freeSpaceLeft < 0L) {
            -2
        } else if(MainService.totalDiskSpace <= 0L) {
            -3
        } else {
            1000L *
                    (MainService.totalDiskSpace - MainService.freeSpaceLeft) /
                    MainService.totalDiskSpace
        }
    }

    fun createNotification(context: Context): Notification {
        val percent: Double = getPercent()
        val text: String =
            if(serviceRunIndex == 0) {
                "Starting"
            } else if(percent < 0.0) {
                if(runCount == 1) {
                    "Cleaning"
                } else {
                    "Run $serviceRunIndex: Cleaning"
                }
            } else {
                val percentValue = ((percent * 1000).toInt().toDouble() / 10.0).toString()

                if(runCount == 1) {
                    "$percentValue% complete"
                } else {
                    "Run $serviceRunIndex: $percentValue% complete"
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
        return withContext(Dispatchers.Default) {
            var lastNotificationCode = -1L

            while(isRunning) {
                delay( 250L)

                val notificationCode = getNotificationCode()

                if(notificationCode != lastNotificationCode
                        && isRunning) {
                    updateNotification(context)
                    lastNotificationCode = notificationCode
                }
            }
        }
    }

    private fun updateNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.notify(notificationId, createNotification(context))
    }

    fun clearNotification(context: Context) {
        if (notificationId > 0) {
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                    as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }

    private fun setNotificationId(context: Context, initial: Int): Int {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notifications: Array<StatusBarNotification>
                = notificationManager.getActiveNotifications()

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

    private fun getPercent(): Double {
        var percent: Double =
            if(MainService.totalDiskSpace > 0L) {
                (MainService.totalDiskSpace - MainService.freeSpaceLeft).toDouble() /
                        MainService.totalDiskSpace.toDouble()
            } else {
                0.0
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