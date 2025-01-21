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
import com.example.freediskshredder.ui.theme.FreeDiskShredderTheme
import java.io.File


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()
        setContent {
            FreeDiskShredder()
        }

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        1
                    )
                }
            }
        } catch(e: Exception) {
            exception.add("POST_NOTIFICATIONS")
            for(item in e.stackTrace) {
                exception.add(item.toString())
            }
        }

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.FOREGROUND_SERVICE),
                    2
                )
            }
        } catch(e: Exception) {
            exception.add("FOREGROUND_SERVICE")
            for(item in e.stackTrace) {
                exception.add(item.toString())
            }
        }

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
                    != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC),
                        3
                    )
                }
            }
        } catch(e: Exception) {
            exception.add("FOREGROUND_SERVICE_DATA_SYNC")
            for(item in e.stackTrace) {
                exception.add(item.toString())
            }
        }

        try {
            deleteFiles()
        } catch(e: Exception) {
            exception.add("deleteFiles")
            for(item in e.stackTrace) {
                exception.add(item.toString())
            }
        }

        isReady = true

        startExceptionHandler()
        startProgressHandler()
    }

    private fun startProgressHandler() {
        val handler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                if(MainService.isRunning != serviceRunning.value) {
                    serviceRunning.value = MainService.isRunning
                }

                if(MainService.done) {
                    isRunning.value = false
                    percent.doubleValue = 0.0
                } else if(MainService.isRunning) {
                    if(!isRunning.value) {
                        isRunning.value = true
                    }

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
                } else {
                    percent.doubleValue = 0.0
                }

                handler.postDelayed(this, 1000L)
            }
        }

        handler.postDelayed(runnable, 0L)
    }

    private fun startExceptionHandler() {
        val handler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                val arr = ArrayList<String>()

                if (exception.size > 0) {
                    for(item in exception) {
                        arr.add(item)
                    }
                } else if (MainService.exception.size > 0) {
                    for(item in MainService.exception) {
                        arr.add(item)
                    }
                }

                if(arr.size > 0) {
                    setContent {
                        Column(
                            modifier = Modifier
                                .safeDrawingPadding()
                                .fillMaxSize()) {
                            arr.forEach { item ->
                                Text(text = item)
                            }
                        }
                    }
                } else {
                    handler.postDelayed(this, 1000L)
                }
            }
        }

        handler.postDelayed(runnable, 1000L)
    }

    private fun startMainService() {
        try {
            MainService.runCount = repeatOptions[repeatIndex.intValue].toInt()
            MainService.isRunning = true
            MainService.done = false

            isRunning.value = true

            val serviceIntent = Intent(this, MainService::class.java)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE)
                == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
                )
                == PackageManager.PERMISSION_GRANTED
            ) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch(e: Exception) {
            exception.add("startMainService")
            for(item in e.stackTrace) {
                exception.add(item.toString())
            }
        }
    }

    private val driveOptions = ArrayList<String>()
    private val repeatOptions = ArrayList<String>()
    private var isReady = false
    private val exception = ArrayList<String>()

    private lateinit var runCount: MutableIntState
    private lateinit var runIndex: MutableIntState
    private lateinit var percent: MutableDoubleState
    private lateinit var isRunning: MutableState<Boolean>
    private lateinit var serviceRunning: MutableState<Boolean>
    private lateinit var driveIndex: MutableIntState
    private lateinit var repeatIndex: MutableIntState
    private lateinit var debugString: MutableState<String>
    private lateinit var debugInt: MutableIntState
    private lateinit var bytesWritten: MutableLongState
    private lateinit var freeSpace: MutableLongState
    private lateinit var waiting: MutableState<Boolean>

    private lateinit var exceptionInt: MutableState<Boolean>

    @Composable
    fun FreeDiskShredder() {
        runCount = remember { mutableIntStateOf(0) }
        runIndex = remember { mutableIntStateOf(0) }
        percent = remember { mutableDoubleStateOf(0.0) }
        isRunning = remember { mutableStateOf(false) }
        serviceRunning = remember { mutableStateOf(false) }
        driveIndex = remember { mutableIntStateOf(0) }
        repeatIndex = remember { mutableIntStateOf(0) }
        debugString = remember { mutableStateOf("uninitialized") }
        debugInt = remember { mutableIntStateOf(0) }
        bytesWritten = remember { mutableLongStateOf(0L) }
        freeSpace = remember { mutableLongStateOf(0L) }
        waiting = remember { mutableStateOf(false) }
        exceptionInt = remember { mutableStateOf(false) }

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
                ActionButton()
                Box {
                    ProgressBar()
                    ProgressText()
                }
                Surface(
                    color = colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                ) { }
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
    fun ActionButton() {
        var text = "Shred empty space"

        if(waiting.value) {
            text = "Still cleaning old files.."
        } else if(serviceRunning.value) {
            text = "In progress.. Cancel"
        } else if(isRunning.value) {
            text = "Starting.."
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
                                waiting.value = true

                                val handler = Handler(Looper.getMainLooper())
                                handler.postDelayed({ waiting.value = false }, 3000L)
                            } else if(MainService.isRunning) {
                                isRunning.value = false
                                MainService.isRunning = false
                            } else {
                                startMainService()
                            }
                        }) {
                    ScaledText(text)
                }
            }
        }
    }

    private fun deleteFiles() {
        try {
            val smallFile = File(this.filesDir, "small.bin")
            val mediumFile = File(this.filesDir, "medium.bin")
            val largeFile = File(this.filesDir, "large.bin")

            try {
                if (smallFile.exists()) {
                    smallFile.delete()
                }
            } catch(_: Exception) { }

            try {
                if (mediumFile.exists()) {
                    mediumFile.delete()
                }
            } catch(_: Exception) { }

            try {
                if (largeFile.exists()) {
                    largeFile.delete()
                }
            } catch(_: Exception) { }
        } catch(_: Exception) { }
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


