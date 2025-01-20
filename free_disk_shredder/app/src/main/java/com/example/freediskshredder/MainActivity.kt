package com.example.freediskshredder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.example.freediskshredder.MainService.Companion.notificationId
import com.example.freediskshredder.ui.theme.FreeDiskShredderTheme
import java.io.File


class MainActivity : ComponentActivity() {
    private fun cleanup() {
        isRunning.value = false
        MainService.isRunning = false

        try {
            if (notificationId > 0
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val notificationManager = NotificationManagerCompat.from(this)
                notificationManager.cancel(notificationId)
            }
        } catch(_: Exception) { }

        try {
            val serviceIntent = Intent(this, MainService::class.java)
            stopService(serviceIntent)
        } catch(_: Exception) { }

        deleteFiles()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // cleanup()
    }

    override fun onDestroy() {
        super.onDestroy()

        // cleanup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeDiskShredder()
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }

        deleteFiles()

        isReady = true
    }

    private val driveOptions = ArrayList<String>()
    private val repeatOptions = ArrayList<String>()
    private var isReady = false

    private lateinit var runCount: MutableIntState
    private lateinit var runIndex: MutableIntState
    private lateinit var percent: MutableDoubleState
    private lateinit var isRunning: MutableState<Boolean>
    private lateinit var driveIndex: MutableIntState
    private lateinit var repeatIndex: MutableIntState
    private lateinit var debugString: MutableState<String>
    private lateinit var debugInt: MutableIntState
    private lateinit var bytesWritten: MutableLongState
    private lateinit var freeSpace: MutableLongState
    private lateinit var waiting: MutableState<Boolean>

    @Composable
    fun FreeDiskShredder() {
        runCount = remember { mutableIntStateOf(0) }
        runIndex = remember { mutableIntStateOf(0) }
        percent = remember { mutableDoubleStateOf(0.0) }
        isRunning = remember { mutableStateOf(false) }
        driveIndex = remember { mutableIntStateOf(0) }
        repeatIndex = remember { mutableIntStateOf(0) }
        debugString = remember { mutableStateOf("uninitialized") }
        debugInt = remember { mutableIntStateOf(0) }
        bytesWritten = remember { mutableLongStateOf(0L) }
        freeSpace = remember { mutableLongStateOf(0L) }
        waiting = remember { mutableStateOf(false) }

        FreeDiskShredderTheme {
            val driveNames: List<String> = getDriveNames()

            driveOptions.clear()

            for (driveName in driveNames) {
                driveOptions.add(driveName)
            }

            repeatOptions.clear()
            repeatOptions.add("1")
            repeatOptions.add("2")
            repeatOptions.add("4")
            repeatOptions.add("8")

            Column(modifier = Modifier
                .safeDrawingPadding()
                .fillMaxSize()) {

                OptionItem("Drive", driveOptions, driveIndex)
                OptionItem("Repeat", repeatOptions, repeatIndex)
                ListSurfaceItem()
                Box {
                    ProgressBar()
                    ProgressText()
                }
                Surface(
                    color = colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                ) {

                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun FreeDiskShredderPreview() {
        FreeDiskShredder()
    }

    @Composable
    fun ScaledText(text: String) {
        Column {
            Text(
                text = " ",
                fontSize = 16.sp,
                color = colorScheme.primary
            )
            Row {
                Text(
                    text = " ",
                    fontSize = 32.sp,
                    color = colorScheme.primary
                )
                Text(
                    text = text,
                    fontSize = 32.sp,
                    color = colorScheme.primary
                )
            }

            Text(
                text = " ",
                fontSize = 16.sp,
                color = colorScheme.primary
            )
        }
    }

    @Composable
    fun ProgressBar() {
        val surfaceWidth: MutableIntState = remember { mutableIntStateOf(0) }

        val percentPixels = surfaceWidth.intValue.toDouble() * percent.doubleValue

        val dpValue = percentPixels / LocalDensity.current.density.toDouble()

        Surface(
            color = colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    surfaceWidth.intValue = coordinates.size.width
                },
            border = BorderStroke(
                width = 1.dp,
                color = colorScheme.primary
            ),

            ) {
            Row {
                Surface(
                    color = colorScheme.secondary,
                    modifier = Modifier
                        .width(dpValue.dp),
                ) {
                    ScaledText("")
                }
            }
        }
    }

    @Composable
    fun ProgressText() {
        val runIndexValue = runIndex.intValue.toString()
        val percentValue = ((percent.doubleValue * 1000).toInt().toDouble() / 10.0).toString()

        var percentText = "Run $runIndexValue: $percentValue% complete"

        if(percent.doubleValue < 0.0001) {
            percentText = " "
        }

        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()) {
            ScaledText(percentText)
        }
    }

    @Composable
    fun OptionItem(label: String, options: List<String>, index: MutableIntState) {
        Surface(
            color = colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if(!isRunning.value) {
                        var nextIndex = index.intValue + 1

                        if(nextIndex >= options.size) {
                            nextIndex = 0
                        }

                        index.intValue = nextIndex
                    }
                },
            border = BorderStroke(
                    width=1.dp,
            color = colorScheme.primary),
        ) {
            Column {
                Surface(
                    color = colorScheme.background
                ) {
                    Text(
                        text = " ",
                        fontSize = 16.sp,
                        color = colorScheme.primary
                    )
                }
                Surface(
                    color = colorScheme.background
                ) {
                    Row {
                        Text(
                            text = " ",
                            fontSize = 32.sp,
                            color = colorScheme.primary
                        )
                        Text(
                            text = label,
                            fontSize = 32.sp,
                            color = colorScheme.primary
                        )
                        Text(
                            text = ": ",
                            fontSize = 32.sp,
                            color = colorScheme.primary
                        )
                        Text(
                            text = options[index.intValue],
                            fontSize = 32.sp,
                            color = colorScheme.primary
                        )
                    }
                }
                Surface(
                    color = colorScheme.background
                ) {
                    Text(
                        text = " ",
                        fontSize = 16.sp,
                        color = colorScheme.primary
                    )
                }
            }

        }
    }

    @Composable
    fun ListSurfaceItem() {
        var text = "Shred empty space"

        if(waiting.value) {
            text = "Still cleaning old files.."
        } else if(isRunning.value) {
            text = "In progress.. Cancel"
        }

        Surface(
            color = colorScheme.tertiary,
            modifier = Modifier
                .fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                border = BorderStroke(
                    width=1.dp,
                    color = colorScheme.primary),
            ) {
                Surface(
                    color = colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if(waiting.value) {
                                // do nothing
                            } else if(!isReady) {
                                val handler = Handler(Looper.getMainLooper())
                                
                                val runnable = Runnable { waiting.value = false }

                                waiting.value = true

                                handler.postDelayed(runnable, 3000L)
                            } else if(isRunning.value) {
                                isRunning.value = false
                                MainService.isRunning = false
                            } else {
                                val handler = Handler(Looper.getMainLooper())

                                val runnable = object : Runnable {
                                    override fun run() {
                                        if(MainService.done) {
                                            isRunning.value = false
                                            percent.doubleValue = 0.0
                                        } else if(isRunning.value) {
                                            if(MainService.serviceRunIndex != runIndex.intValue) {
                                                runIndex.intValue = MainService.serviceRunIndex
                                            }

                                            if(MainService.debugInt != debugInt.intValue) {
                                                debugInt.intValue = MainService.debugInt
                                            }

                                            if(MainService.freeSpace > 0L) {
                                                var servicePercent = MainService.bytesWritten.toDouble() / MainService.freeSpace.toDouble()

                                                if(servicePercent < 0.0) {
                                                    servicePercent = 0.0
                                                } else if(servicePercent > 1.0) {
                                                    servicePercent = 1.0
                                                }

                                                if(servicePercent != percent.doubleValue) {
                                                    percent.doubleValue = servicePercent
                                                }
                                            } else if(percent.doubleValue != 0.0) {
                                                percent.doubleValue = 0.0
                                            }

                                            handler.postDelayed(this, 1000L)
                                        } else {
                                            percent.doubleValue = 0.0
                                        }
                                    }
                                }

                                MainService.runCount = repeatOptions[repeatIndex.intValue].toInt()
                                MainService.isRunning = true
                                MainService.done = false

                                isRunning.value = true

                                val serviceIntent = Intent(this, MainService::class.java)
                                startService(serviceIntent)

                                handler.postDelayed(runnable, 1000L)
                            }
                        }) {
                    ScaledText(text)
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
        } catch(e2: Exception) {
            hasError = true
        }

        return hasError
    }

    private fun getDriveNames(): List<String> {
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
}


