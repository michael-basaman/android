package com.example.freediskshredder

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        val lock = Any()
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var runCount: Int = 0
    private var isRunning: Boolean = true

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
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

        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val data: String? = intent.getStringExtra("data")

                data?.let {
                    val tokens = data.split(":")

                    if(tokens[0] == "isRunning") {
                        isRunning = tokens[1].toBoolean()
                    } else if(tokens[0] == "runCount") {
                        runCount = tokens[1].toInt()
                    }
                }
            }
        }

        registerReceiver(broadcastReceiver, IntentFilter("com.example.free_disk_shredder.activity"))

        serviceScope.launch {
            runBackgroundTask()
        }

        synchronized(lock) {
            serviceRunning = false
        }

        return START_NOT_STICKY
    }

    private suspend fun runBackgroundTask() {
        return withContext(Dispatchers.IO) {
            val intent = Intent("com.example.free_disk_shredder.service")

            val random = Random()
            val numBytes = 1024

            val bytes = ByteArray(numBytes)

            intent.putExtra("data", "isRunning:true")
            sendBroadcast(intent)

            val tinyFile = File(this@MainService.filesDir, "tiny.bin")
            val smallFile = File(this@MainService.filesDir, "small.bin")
            val mediumFile = File(this@MainService.filesDir, "medium.bin")
            val largeFile = File(this@MainService.filesDir, "large.bin")

            if (!tinyFile.exists()) {
                random.nextBytes(bytes)
                tinyFile.writeBytes(bytes)
            }

            while(isRunning && runCount <= 0) {
                try {
                    Thread.sleep(100)
                } catch(_: Exception) { }
            }

            for (runIndex in 1..runCount) {
                var bytesWritten = 0L

                if (tinyFile.exists()) {
                    val freeSpace: Long = tinyFile.freeSpace
                    intent.putExtra("data", "freeSpace:$freeSpace")
                    sendBroadcast(intent)
                }

                intent.putExtra("data", "runIndex:$runIndex")
                sendBroadcast(intent)

                var hasError: Boolean = deleteFiles()

                if (hasError) {
                    intent.putExtra("data", "debug:initial")
                    sendBroadcast(intent)
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

                            intent.putExtra("data", "bytesWritten:$bytesWritten")
                            sendBroadcast(intent)
                        }
                    } catch (e: Exception) {
                        intent.putExtra("data", "bytesWritten:$bytesWritten")
                        sendBroadcast(intent)
                        intent.putExtra("data", "debug:small")
                        sendBroadcast(intent)
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

                            intent.putExtra("data", "bytesWritten:$bytesWritten")
                            sendBroadcast(intent)
                        }
                    } catch (e: Exception) {
                        intent.putExtra("data", "bytesWritten:$bytesWritten")
                        sendBroadcast(intent)
                        intent.putExtra("data", "debug:medium")
                        sendBroadcast(intent)
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

                            intent.putExtra("data", "bytesWritten:$bytesWritten")
                            sendBroadcast(intent)
                        }
                    } catch (e: Exception) {
                        intent.putExtra("data", "bytesWritten:$bytesWritten")
                        sendBroadcast(intent)
                        intent.putExtra("data", "debug:large")
                        sendBroadcast(intent)
                    }
                }

                if (deleteFiles()) {
                    intent.putExtra("data", "debug:final")
                    sendBroadcast(intent)
                }
            }

            isRunning = false
            intent.putExtra("data", "isRunning:false")
            sendBroadcast(intent)
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
