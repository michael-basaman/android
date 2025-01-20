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
    }

    private val driveOptions = ArrayList<String>()
    private val repeatOptions = ArrayList<String>()

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

        val debug = debugInt.intValue
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

        if(isRunning.value) {
            text = "In progress: Cancel"
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
                            if(isRunning.value) {
                                debugInt.intValue = 1
                                debugString.value = "here " + MainService.debug
                                isRunning.value = false
                                MainService.isRunning = false
                            } else {
                                val handler = Handler(Looper.getMainLooper())

                                val runnable = object : Runnable {
                                    override fun run() {
                                        if(MainService.done) {
                                            isRunning.value = false
                                            percent.doubleValue = 0.0
                                            debugInt.intValue = 2
                                        } else if(isRunning.value) {
                                            val serviceBytesWritten = MainService.bytesWritten
                                            val serviceFreeSpace = MainService.freeSpace
                                            val serviceRunIndex = MainService.serviceRunIndex

                                            debugString.value = MainService.debug

                                            if(serviceRunIndex != runIndex.intValue) {
                                                runIndex.intValue = serviceRunIndex
                                            }

                                            if(serviceFreeSpace > 0L) {
                                                var servicePercent = serviceBytesWritten.toDouble() / serviceFreeSpace.toDouble()

                                                if(servicePercent < 0.0) {
                                                    servicePercent = 0.0
                                                } else if(servicePercent > 1.0) {
                                                    servicePercent = 1.0
                                                }

                                                if(servicePercent != percent.doubleValue) {
                                                    percent.doubleValue = servicePercent
                                                }

                                                debugInt.intValue = 3
                                            } else if(percent.doubleValue != 0.0) {
                                                percent.doubleValue = 0.0
                                                debugInt.intValue = 4
                                            }

                                            handler.postDelayed(this, 1000L)
                                        } else {
                                            percent.doubleValue = 0.0
                                            debugInt.intValue = 5
                                        }
                                    }
                                }

                                MainService.runCount = repeatOptions[repeatIndex.intValue].toInt()
                                MainService.isRunning = true
                                MainService.done = false
                                debugInt.intValue = 7

                                isRunning.value = true

                                debugString.value = "launching"

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


