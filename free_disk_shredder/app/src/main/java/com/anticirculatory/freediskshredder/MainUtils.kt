package com.anticirculatory.freediskshredder

import android.Manifest
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.app.ActivityCompat
import java.io.File

class MainUtils {
    companion object {
        fun deleteFiles(context: Context) {
            try {
                val largeFile = File(context.filesDir, "large.bin")

                if (largeFile.exists()) {
                    largeFile.delete()
                }
            } catch(_: Exception) { }
        }

        fun getDriveNames(): List<String> {
            val driveNames = mutableListOf<String>()

            driveNames.add("Internal Storage")

            try {
                val externalStorageDirectory = Environment.getExternalStorageDirectory()

                val rootDirectory = externalStorageDirectory.parentFile

                if (rootDirectory != null && rootDirectory.isDirectory) {
                    val files: Array<File>? = rootDirectory.listFiles()

                    files?.let {
                        for(file in files) {
                            if (file.isDirectory && file.canRead()) {
                                driveNames.add(file.name)
                            }
                        }
                    }
                }
            } catch(_: Exception) { }

            return driveNames
        }

        fun getTotalDiskSpace(context: Context): Long {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val uuid = storageManager.getUuidForPath(Environment.getDataDirectory())
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager

                return storageStatsManager.getTotalBytes(uuid)
            } catch (_: Exception) {
                return 0L
            }
        }

        fun hasPermissions(activity: MainActivity): Boolean {
            var hasAllPermissions = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    hasAllPermissions = false
                }
            }

            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.FOREGROUND_SERVICE)
                != PackageManager.PERMISSION_GRANTED) {
                hasAllPermissions = false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
                    != PackageManager.PERMISSION_GRANTED) {
                    hasAllPermissions = false
                }
            }

            return hasAllPermissions
        }

        fun requestPermissions(activity: MainActivity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        1
                    )
                }
            }

            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.FOREGROUND_SERVICE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.FOREGROUND_SERVICE),
                    2
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC),
                        3
                    )
                }
            }
        }

        fun getRandomString(length: Int) : String {
            val allowedChars = ('a'..'z')
            return (1..length)
                .map { allowedChars.random() }
                .joinToString("")
        }
    }
}