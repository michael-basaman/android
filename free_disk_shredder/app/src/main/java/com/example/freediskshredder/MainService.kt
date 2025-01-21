package com.example.freediskshredder

import android.app.Service
import android.content.Intent
import android.os.IBinder
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
        var isRunning: Boolean = false
        var runCount: Int = 0
        var serviceRunIndex = 0
        var bytesWritten: Long = 0L
        var freeSpace: Long = 0L
        var debugInt: Int = 0
        var debug: String = "no status"
        var done: Boolean = false
        val exception = ArrayList<String>()

        val lock = Any()
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val mainNotification = MainNotification()

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        try {
            mainNotification.createNotificationChannel(this)
            // val notification = mainNotification.createPersistentNotification(this)
            val notification = mainNotification.createNotification(this)

            startForeground(
                mainNotification.getNotificationId(this),
                notification
            )

            serviceScope.launch {
                mainNotification.updateNotifications(this@MainService)
            }
        } catch(e: Exception) {
            exception.add("onCreate")
            for(item in e.stackTrace) {
                exception.add(item.toString())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        isRunning = false
        serviceJob.cancel()
        done = true

        try {
            mainNotification.clearNotification(this)
        } catch(_: Exception) { }
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

        done = false
        isRunning = true

        serviceScope.launch {
            runBackgroundTask()
            isRunning = false
            done = true
            serviceRunning = false
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private suspend fun runBackgroundTask() {
        return withContext(Dispatchers.IO) {
            debug = "background"
            
            val random = Random()
            val numBytes = 1024L * 1024L

            val bytes = ByteArray(numBytes.toInt())
            val smallBytes = ByteArray(1024)

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

                            random.nextBytes(bytes)
                            smallFile.writeBytes(bytes)
                            bytesWritten += numBytes
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

                            random.nextBytes(bytes)
                            mediumFile.writeBytes(bytes)
                            bytesWritten += numBytes
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

                            random.nextBytes(bytes)
                            largeFile.writeBytes(bytes)
                            bytesWritten += numBytes
                        }
                    } catch (e: Exception) {
                        debug = "large"
                    }
                }

                try {
                    while (true) {
                        if(!isRunning) {
                            break
                        }

                        random.nextBytes(smallBytes)
                        largeFile.writeBytes(smallBytes)
                        bytesWritten += 1024L
                    }
                } catch (_: Exception) { }

                if (deleteFiles()) {
                    debug = "final"
                }
            }
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
