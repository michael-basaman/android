package com.example.freediskshredder

import android.Manifest
import android.annotation.SuppressLint
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
        var debug: String = "no status"
        var done: Boolean = false

        val lock = Any()
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var notificationId = 0

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun createNotificationChannel(context: Context) {
        val name = "Free Disk Shredder"
        val descriptionText = "Status"
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        val channel = NotificationChannel("channel_id", name, importance).apply {
            description = descriptionText
        }

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channel)
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

    private fun createNotification(context: Context): NotificationCompat.Builder {
        val percentValue = getPercentValue()

        return NotificationCompat.Builder(context, "free_disk_shredder")
            .setContentTitle("Free Disk Shredder")
            .setContentText("Run $serviceRunIndex/$runCount: $percentValue%")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    @SuppressLint("MissingPermission")
    private suspend fun updateNotification() {
        return withContext(Dispatchers.IO) {
            while (isRunning) {
                if (notificationId > 0) {
                    val notificationManager = NotificationManagerCompat.from(this@MainService)

                    try {
                        notificationManager.notify(
                            notificationId,
                            createNotification(this@MainService).build()
                        )
                    } catch (e: Exception) {
                        break
                    }
                }

                try {
                    Thread.sleep(5000L)
                } catch (_: Exception) { }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var shouldAbort = true

        debug = "started"

        synchronized(lock) {
            if(!serviceRunning) {
                serviceRunning = true
                shouldAbort = false
            }
        }

        if(shouldAbort) {
            return START_NOT_STICKY
        }



        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            createNotificationChannel(this)

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val notifications: Array<StatusBarNotification> =
                notificationManager.getActiveNotifications()

            val r = Random()
            val s = HashSet<Int>()
            for (n in notifications) {
                s.add(n.id)
            }
            var c = 0
            var i = 0
            while (c < 10000) {
                i = r.nextInt(9_000_000) + 1_000_000
                if (!s.contains(i)) {
                    break
                }
                c++
            }

            if (!s.contains(i)) {
                notificationId = i
                notificationManager.notify(notificationId, createNotification(this).build())
            }
        }

         // 1 is the notification ID

        debug = "not aborted"

        serviceScope.launch {
            done = false
            runBackgroundTask()
            done = true
            serviceRunning = false
            debug = "stopped"
        }

        serviceScope.launch {
            updateNotification()
        }

        return START_NOT_STICKY
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
        val smallFile = File(this.filesDir, "small.bin")
        val mediumFile = File(this.filesDir, "medium.bin")
        val largeFile = File(this.filesDir, "large.bin")

        var hasError = false

        try {
            if (smallFile.exists()) {
                smallFile.delete()
            }
        } catch(e: Exception) {
            hasError = true
        }

        try {
            if (mediumFile.exists()) {
                mediumFile.delete()
            }
        } catch(e: Exception) {
            hasError = true
        }

        try {
            if (largeFile.exists()) {
                largeFile.delete()
            }
        } catch(e: Exception) {
            hasError = true
        }

        return hasError
    }
}
