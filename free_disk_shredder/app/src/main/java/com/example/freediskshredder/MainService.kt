package com.example.freediskshredder

import android.app.NotificationManager
import android.app.Service
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.os.storage.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.Random


class MainService : Service() {
    companion object {
        var serviceRunning: Boolean = false
        var isRunning: Boolean = false
        var runCount: Int = 0
        var serviceRunIndex = 0
        var freeSpace: Long = 0L
        var freeSpaceLeft: Long = 0L
        var debugInt: Int = 0
        var done: Boolean = false
        var totalDiskSpace: Long = 0L

        var lastCompleteTimestamp: Long = 0L
        var lastCompleteRunCount: Int = 0
        var lastCompleteChanged: Boolean = true

        val lock = Any()

        fun deleteFiles(context: Context) {
            try {
                val largeFile = File(context.filesDir, "large.bin")

                if (largeFile.exists()) {
                    largeFile.delete()
                }
            } catch(_: Exception) { }
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val mainNotification = MainNotification()

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        mainNotification.createNotificationChannel(this)

        val notificationId = mainNotification.getNotificationId()

        val notification = mainNotification.createNotification(this)
        val notificationManager = getSystemService(NotificationManager::class.java)

        notificationManager?.notify(notificationId, notification)

        startForeground(
            notificationId,
            notification
        )

        serviceScope.launch {
            mainNotification.updateNotifications(this@MainService)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        isRunning = false

        serviceJob.cancel()
        mainNotification.clearNotification(this)

        deleteFiles(this)

        synchronized(lock) {
            serviceRunning = false
            done = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alreadyRunning: Boolean

        synchronized(lock) {
            alreadyRunning = serviceRunning
            serviceRunning = true
            done = false
        }

        if(alreadyRunning) {
            return START_NOT_STICKY
        }

        isRunning = true

        serviceScope.launch {
            runBackgroundTask()
        }

        return START_NOT_STICKY
    }

    private suspend fun runBackgroundTask() {
        return withContext(Dispatchers.IO) {
            val random = Random()

            val bytes = ByteArray(1024)

            val tinyFile = File(this@MainService.filesDir, "tiny.bin")
            val largeFile = File(this@MainService.filesDir, "large.bin")

            for (runIndex in 1..runCount) {
                if(!isRunning) {
                    break
                }

                if (!tinyFile.exists()) {
                    random.nextBytes(bytes)
                    tinyFile.writeBytes(bytes)
                }

                totalDiskSpace = getTotalDiskSpace(this@MainService)

                freeSpace = tinyFile.freeSpace
                freeSpaceLeft = -1L
                
                serviceRunIndex = runIndex

                deleteFiles(this@MainService)

                freeSpaceLeft = tinyFile.freeSpace

                var writeCount = 0

                if (isRunning) {
                    try {
                        val fileOutputStream = FileOutputStream(largeFile)
                        val bufferedOutputStream = BufferedOutputStream(fileOutputStream)

                        try {
                            var writing = true

                            var lastFileSize = 0L

                            while (writing) {
                                if (!isRunning) {
                                    break
                                }

                                for (j in 1..1024) {
                                    random.nextBytes(bytes)
                                    bufferedOutputStream.write(bytes)
                                }
                                bufferedOutputStream.flush()
                                writeCount++

                                if(writeCount % 16 == 0) {
                                    freeSpaceLeft = tinyFile.freeSpace
                                }

                                if(writeCount % 32 == 0) {
                                    if (largeFile.exists()) {
                                        val fileSize: Long = largeFile.length()

                                        if (fileSize <= lastFileSize) {
                                            writing = false
                                        } else {
                                            lastFileSize = fileSize
                                        }
                                    } else {
                                        writing = false
                                    }
                                }
                            }
                        } catch(_: IOException) { }

                        bufferedOutputStream.close()
                    } catch (_: IOException) { }
                }

                freeSpaceLeft = -1L
                deleteFiles(this@MainService)
            }

            lastCompleteTimestamp = Instant.now().epochSecond
            lastCompleteRunCount = runCount

            val saveFile = File(this@MainService.filesDir, "state.txt")

            val data = "lastCompleteTimestamp:$lastCompleteTimestamp,lastCompleteRunCount:$lastCompleteRunCount"
            saveFile.writeText(data)

            lastCompleteChanged = true
            isRunning = false

            stopSelf()
        }
    }

    private fun getTotalDiskSpace(context: Context): Long {
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val uuid = storageManager.getUuidForPath(Environment.getDataDirectory())
            val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager

            return storageStatsManager.getTotalBytes(uuid)
        } catch (_: Exception) {
            return 0L
        }
    }


}
