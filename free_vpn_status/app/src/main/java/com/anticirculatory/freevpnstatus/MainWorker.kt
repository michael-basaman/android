package com.anticirculatory.freevpnstatus

import android.app.WallpaperManager
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class MainWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    companion object {
        val exceptions = ArrayList<String>()
    }

    private var active: Boolean = true
    private var vpnRunning: Boolean = false
    private var backgroundSet: Boolean = false

    private val workerJob = Job()
    private val workerScope = CoroutineScope(Dispatchers.Main + workerJob)

    override suspend fun doWork(): Result {
        return try {
            monitorVpnStatus()
            Result.success()
        } catch (e: Exception) {
            exceptions.clear()

            exceptions.add("doWork")
            for(s in e.stackTrace) {
                exceptions.add(s.toString())
            }

            Result.failure()
        } finally {
            active = false
            workerJob.cancel()
        }
    }

    private suspend fun monitorVpnStatus() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val connectivityManager = applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback (networkCallback)

        while(active) {
            delay(30000L)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)

            //val isRunning: Boolean = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

            workerScope.launch {
                updateBackground()
            }
        }
    }

    private suspend fun updateBackground() {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return

        val isRunning: Boolean = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        if (!backgroundSet || isRunning != vpnRunning) {
            val filename: String = if (isRunning) {
                "on.jpg"
            } else {
                "off.jpg"
            }

            val imgFile = File(applicationContext.filesDir, filename)

            if (imgFile.exists()) {
                val inputStream =
                    withContext(Dispatchers.IO) {
                        FileInputStream(imgFile)
                    }

                WallpaperManager.getInstance(applicationContext)
                    .setStream(inputStream, null, true, WallpaperManager.FLAG_SYSTEM)
            }

            backgroundSet = true
            vpnRunning = isRunning
        }
    }
}