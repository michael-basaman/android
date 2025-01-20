package com.example.freediskshredder

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.File
import java.util.Random

class MyService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var runCount = 0

        intent?.let {
            runCount = intent.getIntExtra("runCount", 0)
        }

        if(runCount <= 0) {
            return START_STICKY
        }

        val random = Random()
        val numBytes = 1024

        val bytes = ByteArray(numBytes)

        val tinyFile = File(this.filesDir, "tiny.bin")
        val smallFile = File(this.filesDir, "small.bin")
        val mediumFile = File(this.filesDir, "medium.bin")
        val largeFile = File(this.filesDir, "large.bin")

        if(!tinyFile.exists()) {
            random.nextBytes(bytes)
            tinyFile.writeBytes(bytes)
        }

        for(runIndex in 1..runCount) {
            var bytesWritten = 0L

            if(tinyFile.exists()) {
                intent?.putExtra("freeSpace", tinyFile.freeSpace)
            }

            intent?.putExtra("runIndex", runIndex)

            var error: Boolean = deleteFiles()

            if(error) {
                intent?.putExtra("error", "initial")
            }

            if(!error) {
                try {
                    for (i in 1..10) {
                        for (j in 1..1024) {
                            random.nextBytes(bytes)
                            smallFile.writeBytes(bytes)
                            bytesWritten += 1024L
                        }

                        intent?.putExtra("bytesWritten", bytesWritten)
                    }
                } catch (e: Exception) {
                    intent?.putExtra("bytesWritten", bytesWritten)
                    intent?.putExtra("error", "small")
                    error = true
                }
            }

            if(!error) {
                try {
                    for(i in 1..100) {
                        for (j in 1..1024) {
                            random.nextBytes(bytes)
                            mediumFile.writeBytes(bytes)
                            bytesWritten += 1024L
                        }

                        intent?.putExtra("bytesWritten", bytesWritten)
                    }
                } catch(e: Exception) {
                    intent?.putExtra("bytesWritten", bytesWritten)
                    intent?.putExtra("error", "medium")
                    error = true
                }
            }

            if(!error) {
                try {
                    while(true) {
                        for (j in 1..1024) {
                            random.nextBytes(bytes)
                            largeFile.writeBytes(bytes)
                            bytesWritten += 1024L
                        }

                        intent?.putExtra("bytesWritten", bytesWritten)
                    }
                } catch(e: Exception) {
                    intent?.putExtra("bytesWritten", bytesWritten)
                    intent?.putExtra("error", "large")
                }
            }

            if(deleteFiles()) {
                val intentError: String? = intent?.getStringExtra("error")

                intentError?.let {
                    if(intentError != "initial") {
                        intent.putExtra("error", "final")
                    }
                }
            }
        }

        intent?.putExtra("isRunning", false)

        return START_STICKY
    }

    private fun deleteFiles(): Boolean {
        val smallFile = File(this.filesDir, "small.bin")
        val mediumFile = File(this.filesDir, "medium.bin")
        val largeFile = File(this.filesDir, "large.bin")

        var error = false

        try {
            if (smallFile.exists()) {
                smallFile.delete()
            }
        } catch(e: Exception) {
            error = true
        }

        try {
            if (mediumFile.exists()) {
                mediumFile.delete()
            }
        } catch(e: Exception) {
            error = true
        }

        try {
            if (largeFile.exists()) {
                largeFile.delete()
            }
        } catch(e: Exception) {
            error = true
        }

        return error
    }
}
