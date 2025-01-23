package com.anticirculatory.freevpnstatus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.anticirculatory.freevpnstatus.ui.theme.FreeVPNStatusTheme
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


class MainActivity : ComponentActivity() {
    private val activityJob = Job()
    private val activityScope = CoroutineScope(Dispatchers.Main + activityJob)

    private val uniqueWorkName: String  = "anticirculatory.freevpnstatus"

    private var active: Boolean = true
    private var checking: Boolean = false
    private var isPreview: Boolean = false

    private val exceptions = ArrayList<String>()

    private lateinit var isRunning: MutableState<Boolean>
    private lateinit var refreshKey: MutableIntState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FreeVPNStatus()
        }

        val onFile = File(this.filesDir, "on.jpg")
        val offFile = File(this.filesDir, "off.jpg")

        if(!onFile.exists() || ! offFile.exists()) {
            stopWorker()
        }

        activityScope.launch {
            checkWorkerRunning()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        active = false
        activityJob.cancel()
    }

    private fun startWorker() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_NETWORK_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_NETWORK_STATE), 1)
        } else {
            val workRequest: OneTimeWorkRequest = OneTimeWorkRequestBuilder<MainWorker>()
                .build()

            WorkManager
                .getInstance(this)
                .enqueueUniqueWork(uniqueWorkName,
                    ExistingWorkPolicy.REPLACE,
                    workRequest)
        }
    }

    private fun stopWorker() {
        if(isWorkerRunning()) {
            WorkManager
                .getInstance(this)
                .cancelUniqueWork(uniqueWorkName)
        }
    }

    private fun isWorkerRunning(): Boolean {
        var running = false

        val workInfos: ListenableFuture<List<WorkInfo>> = WorkManager
            .getInstance(this)
            .getWorkInfosForUniqueWork(uniqueWorkName)

        for(workInfo in workInfos.get()) {
            val state: WorkInfo.State = workInfo.state

            if(state == WorkInfo.State.RUNNING
                    || state == WorkInfo.State.ENQUEUED) {
                running = true
            }
        }

        return running
    }

    private suspend fun checkWorkerRunning() {
        return withContext(Dispatchers.IO) {
            while(active) {
                delay(1000L)

                val running = isWorkerRunning()

                if(running != isRunning.value) {
                    isRunning.value = running
                }

                if(MainWorker.exceptions.size > 0) {
                    exceptions.clear()
                    for(s in MainWorker.exceptions) {
                        exceptions.add(s)
                    }
                    MainWorker.exceptions.clear()
                    refreshKey.intValue++
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun FreeVPNStatusPreview() {
        isPreview = true

        FreeVPNStatus()
    }

    @Composable
    private fun FreeVPNStatus() {
        isRunning = remember { mutableStateOf(false) }
        refreshKey = remember { mutableIntStateOf(0) }

        FreeVPNStatusTheme {
            Surface(
                color = colorScheme.background,
                modifier = Modifier
                    .safeDrawingPadding()
                    .fillMaxWidth()) {
                Column {
                    ActionButton()
                    Row {
                        VPNOnChooser()
                        VPNOffChooser()
                    }
                    Surface(
                        color = colorScheme.background,
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        key(refreshKey.intValue) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                for(s in exceptions) {
                                    Text(
                                        text = s,
                                        color = colorScheme.primary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val uri: Uri? = data?.data

            uri?.let {
                val contentResolver = contentResolver
                val inputStream = contentResolver.openInputStream(uri)

                val outputStream = FileOutputStream(File(this.filesDir, filePickerFilename))

                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                refreshKey.intValue++
            }
        }

        filePickerOpen = false
    }

    private var filePickerOpen: Boolean = false
    private var filePickerFilename: String = ""

    @Composable
    private fun VPNChooser(description: String, filename: String, fillWidth: Float) {
        var width: Float

        if(isPreview) {
            width = 1080.0f / LocalDensity.current.density
        } else {
            val displayMetrics = resources.displayMetrics
            width = displayMetrics.widthPixels / LocalDensity.current.density
        }

        width /= 2.0f

        val height: Float = width * 16.0f / 9.0f

        val refreshKeyInt = refreshKey.intValue

        var request: ImageRequest? = null

        key(refreshKey.intValue) {
            val cacheKey = refreshKey.intValue.toString() + "." + filename

            if (isPreview) {
                request = ImageRequest.Builder(this)
                    .data(R.drawable.ic_launcher_background)
                    .crossfade(true)
                    .diskCacheKey(cacheKey)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .setHeader("Cache-Control", "max-age=31536000")
                    .build()
            } else {
                val imgFile = File(this.filesDir, filename)

                if (imgFile.exists()) {
                    request = ImageRequest.Builder(this)
                        .data(imgFile)
                        .crossfade(true)
                        .diskCacheKey(cacheKey)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .setHeader("Cache-Control", "max-age=31536000")
                        .build()
                } else {
                    request = ImageRequest.Builder(this)
                        .data(R.drawable.ic_launcher_background)
                        .crossfade(true)
                        .diskCacheKey(cacheKey)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .setHeader("Cache-Control", "max-age=31536000")
                        .build()
                }
            }
        }

        Surface(
            color = colorScheme.background,
            border = BorderStroke(
                width = 1.dp,
                color = colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth(fraction = fillWidth)
        ) {
            Column {
                AsyncImage(
                    model = request,
                    contentDescription = description,
                    modifier = Modifier
                        .requiredSize(
                            Dp(width),
                            Dp(height)
                        )
                )
                Surface(
                    color = colorScheme.background,
                    border = BorderStroke(
                        width = 1.dp,
                        color = colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if(!filePickerOpen) {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    type = "image/jpeg"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }

                                filePickerFilename = filename
                                filePickerOpen = true
                                pickFileLauncher.launch(intent)
                            }
                        }
                ) {
                    PaddedText(
                        text = description
                    )
                }
            }
        }
    }

    @Composable
    private fun PaddedText(text: String) {
        Surface(
            color = colorScheme.background,
            modifier = Modifier
                .padding(24.dp, 12.dp, 24.dp, 12.dp)
        ) {
            Text(
                text = text,
                fontSize = 24.sp
            )
        }
    }

    @Composable
    private fun ActionButton() {
        val text: String = if(isRunning.value) {
            "Active.. Stop"
        } else {
            "Not Active.. Start"
        }

        Surface(
            color = colorScheme.background,
            border = BorderStroke(
                width = 1.dp,
                color = colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isRunning.value) {
                        stopWorker()
                    } else {
                        startWorker()
                    }
                }
        ) {
            PaddedText(
                text = text
            )
        }
    }

    @Composable
    private fun VPNOnChooser() {
        VPNChooser(
            description = "VPN On",
            filename = "on.jpg",
            fillWidth = 0.5f
        )
    }

    @Composable
    private fun VPNOffChooser() {
        VPNChooser(
            description = "VPN Off",
            filename = "off.jpg",
            fillWidth = 1.0f
        )
    }
}

