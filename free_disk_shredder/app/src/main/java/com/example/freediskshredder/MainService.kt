package com.example.freediskshredder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.service.notification.StatusBarNotification
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Random


class MainService : Service() {
    companion object {
        var serviceRunning: Boolean = false
        var isRunning: Boolean = true
        var runCount: Int = 0
        var serviceRunIndex = 0
        var bytesWritten: Long = 0L
        var freeSpace: Long = 0L
        var debugInt: Int = 0
        var debug: String = "no status"
        var done: Boolean = false
        var notificationId = 0

        val lock = Any()
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val channelId = "free_disk_shredder"

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun clearNotification() {
        if (notificationId > 0) {
            try {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)

            } catch(_: Exception) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        serviceJob.cancel()
        clearNotification()
    }

    private fun createNotificationChannel(context: Context) {
        val name = "Free Disk Shredder"
        val importance = NotificationManager.IMPORTANCE_LOW

        val channel = NotificationChannel(channelId, name, importance)

        val notificationManager = context.getSystemService(NotificationManager::class.java)

        notificationManager?.createNotificationChannel(channel)
    }

    private fun createNotification(context: Context): Notification {
        val percentValue: String = getPercentValue()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your notification icon
            .setContentTitle("Free Disk Shredder")
            .setContentText("Run $serviceRunIndex: $percentValue% complete")
            .setOngoing(true)

        return builder.build()
    }

    private fun createPersistentNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.notify(notificationId, createNotification(this))
    }

    private fun updateNotification(context: Context) {
        if (notificationId > 0
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
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

    private fun getNotificationId(context: Context): Int {
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var shouldAbort = true

        synchronized(lock) {
            if(!serviceRunning) {
                serviceRunning = true
                shouldAbort = false
            }
        }

        if(shouldAbort) {
            return START_NOT_STICKY
        }

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                createNotificationChannel(this)

                notificationId = getNotificationId(this)

                createPersistentNotification(this)
            }
        } catch(_: Exception) { }

        serviceScope.launch {
            done = false
            runBackgroundTask()
            done = true
            serviceRunning = false
        }

        serviceScope.launch {
            updateNotifications()
            clearNotification()
        }

        return START_NOT_STICKY
    }

    private suspend fun updateNotifications() {
        return withContext(Dispatchers.IO) {
            try {
                while(isRunning) {
                    try {
                        Thread.sleep(1000L)
                    } catch(_: Exception) { }

                    updateNotification(this@MainService)
                }
            } catch(_: Exception) { }
        }
    }

    private suspend fun runBackgroundTask() {
        return withContext(Dispatchers.IO) {
            debug = "background"
            
            val random = Random()
            val numBytes = 1024

            val bytes = ByteArray(numBytes)

            val tinyFile = File(this@MainService.filesDir, "tiny.bin")
            val smallFile = File(this@MainService.filesDir, "small.bin")
            val mediumFile = File(this@MainService.filesDir, "medium.bin")
            val largeFile = File(this@MainService.filesDir, "large.bin")

            if (!tinyFile.exists()) {
                random.nextBytes(bytes)
                tinyFile.writeBytes(bytes)
            }

            for (runIndex in 1..runCount) {
                bytesWritten = 0L

                if (tinyFile.exists()) {
                    freeSpace = tinyFile.freeSpace
                }

                serviceRunIndex = runIndex

                var hasError: Boolean = deleteFiles()

                if (hasError) {
                    debug = "initial"
                }

                if (!hasError) {
                    try {
                        for (i in 1..10) {
                            if(!isRunning) {
                                break
                            }

                            for (j in 1..1024) {
                                if(!isRunning) {
                                    break
                                }

                                random.nextBytes(bytes)
                                smallFile.writeBytes(bytes)
                                bytesWritten += 1024L
                            }
                        }
                    } catch (e: Exception) {
                        debug = "small"
                        hasError = true
                    }
                }

                if (!hasError) {
                    try {
                        for (i in 1..100) {
                            if(!isRunning) {
                                break
                            }

                            for (j in 1..1024) {
                                if(!isRunning) {
                                    break
                                }

                                random.nextBytes(bytes)
                                mediumFile.writeBytes(bytes)
                                bytesWritten += 1024L
                            }
                        }
                    } catch (e: Exception) {
                        debug = "medium"
                        hasError = true
                    }
                }

                if (!hasError) {
                    try {
                        while (true) {
                            if(!isRunning) {
                                break
                            }

                            for (j in 1..1024) {
                                if(!isRunning) {
                                    break
                                }

                                random.nextBytes(bytes)
                                largeFile.writeBytes(bytes)
                                bytesWritten += 1024L
                            }
                        }
                    } catch (e: Exception) {
                        debug = "large"
                    }
                }

                if (deleteFiles()) {
                    debug = "final"
                }
            }

            isRunning = false
        }
    }

    private fun deleteFiles(): Boolean {
        var hasError = false

        try {
            val smallFile = File(this.filesDir, "small.bin")
            val mediumFile = File(this.filesDir, "medium.bin")
            val largeFile = File(this.filesDir, "large.bin")

            try {
                if (smallFile.exists()) {
                    smallFile.delete()
                }
            } catch (e: Exception) {
                hasError = true
            }

            try {
                if (mediumFile.exists()) {
                    mediumFile.delete()
                }
            } catch (e: Exception) {
                hasError = true
            }

            try {
                if (largeFile.exists()) {
                    largeFile.delete()
                }
            } catch (e: Exception) {
                hasError = true
            }
        } catch(e2: Exception) {
            hasError = true
        }

        return hasError
    }
}
