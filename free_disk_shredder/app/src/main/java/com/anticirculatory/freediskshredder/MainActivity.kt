package com.anticirculatory.freediskshredder

import android.content.Intent
import android.os.Bundle
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
import com.anticirculatory.freediskshredder.ui.theme.FreeDiskShredderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val activityJob = Job()
    private val activityScope = CoroutineScope(Dispatchers.Main + activityJob)
    private var active = true

    override fun onDestroy() {
        super.onDestroy()

        active = false
        activityJob.cancel()

        if(!MainService.isRunning) {
            MainUtils.deleteFiles(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        enableEdgeToEdge()

        setContent {
            FreeDiskShredder()
        }

        activityScope.launch {
            progressHandler()
        }

        activityScope.launch {
            saveHandler()
        }

        if(!MainService.isRunning) {
            MainUtils.deleteFiles(this)
        }

        isReady = true
    }

    private fun startMainService() {
        var hasAllPermissions = MainUtils.hasPermissions(this)

        if(!hasAllPermissions) {
            MainUtils.requestPermissions(this)
        }

        hasAllPermissions = MainUtils.hasPermissions(this)

        if(hasAllPermissions) {
            MainService.runCount = repeatOptions[repeatIndex.intValue].toInt()
            MainService.isRunning = true
            MainService.done = false

            isRunning.value = true

            val serviceIntent = Intent(this, MainService::class.java)
            startForegroundService(serviceIntent)
        } else {
            needsPermissions.value = true
        }
    }

    private suspend fun saveHandler() {
        return withContext(Dispatchers.IO) {
            val saveFile = File(this@MainActivity.filesDir, "state.txt")

            while (active) {
                delay(5000L)

                if(!MainService.lastCompleteChanged
                        && MainService.lastCompleteTimestamp > 0L
                        && !saveFile.exists()) {
                    val serviceLastCompleteTimestamp = MainService.lastCompleteTimestamp
                    val serviceLastCompleteRunCount = MainService.lastCompleteRunCount

                    val data = "lastCompleteTimestamp:$serviceLastCompleteTimestamp,lastCompleteRunCount:$serviceLastCompleteRunCount"
                    saveFile.writeText(data)
                }
            }
        }
    }

    private suspend fun progressHandler() {
        return withContext(Dispatchers.IO) {
            while(active) {
                delay(250L)

                if (MainService.isRunning != serviceRunning.value) {
                    serviceRunning.value = MainService.isRunning
                }

                if (MainService.done) {
                    isRunning.value = false
                    percent.doubleValue = 0.0
                } else if (MainService.isRunning) {
                    if (!isRunning.value) {
                        isRunning.value = true
                    }

                    if (MainService.serviceRunIndex != runIndex.intValue) {
                        runIndex.intValue = MainService.serviceRunIndex
                    }

                    if (MainService.totalDiskSpace > 0L) {
                        var servicePercent =
                            (MainService.totalDiskSpace - MainService.freeSpaceLeft).toDouble() / MainService.totalDiskSpace.toDouble()

                        if (MainService.freeSpaceLeft < 0L) {
                            servicePercent = -1.0
                        } else if (servicePercent < 0.0) {
                            servicePercent = 0.0
                        } else if (servicePercent > 1.0) {
                            servicePercent = 1.0
                        }

                        if (servicePercent != percent.doubleValue) {
                            percent.doubleValue = servicePercent
                        }
                    } else if (percent.doubleValue != 0.0) {
                        percent.doubleValue = 0.0
                    }
                } else if (percent.doubleValue != 0.0) {
                    percent.doubleValue = 0.0
                }

                if (MainService.lastCompleteChanged) {
                    try {
                        if(MainService.lastCompleteTimestamp > 0L) {
                            lastCompleteTimestamp.longValue = MainService.lastCompleteTimestamp
                            lastCompleteRunCount.intValue = MainService.lastCompleteRunCount
                        } else {
                            val saveFile = File(this@MainActivity.filesDir, "state.txt")

                            if(saveFile.exists()) {
                                val data = saveFile.readText()
                                val tokens1 = data.split(",")

                                for (token1 in tokens1) {
                                    val tokens2 = token1.split(":")

                                    if (tokens2[0] == "lastCompleteTimestamp") {
                                        lastCompleteTimestamp.longValue = tokens2[1].toLong()
                                    } else if (tokens2[0] == "lastCompleteRunCount") {
                                        lastCompleteRunCount.intValue = tokens2[1].toInt()
                                    }
                                }
                            }
                        }
                    } catch(_: Exception) { }

                    MainService.lastCompleteChanged = false
                }
            }
        }
    }

    private fun actionHandler() {
        if(needsPermissions.value) {
            MainUtils.requestPermissions(this)

            if(MainUtils.hasPermissions(this)) {
                needsPermissions.value = false
            }
        } else if(waiting.value) {
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
    }

    private val driveOptions = ArrayList<String>()
    private val repeatOptions = ArrayList<String>()
    private var isReady = false

    private lateinit var runCount: MutableIntState
    private lateinit var runIndex: MutableIntState
    private lateinit var percent: MutableDoubleState
    private lateinit var isRunning: MutableState<Boolean>
    private lateinit var serviceRunning: MutableState<Boolean>
    private lateinit var driveIndex: MutableIntState
    private lateinit var repeatIndex: MutableIntState
    private lateinit var freeSpace: MutableLongState
    private lateinit var waiting: MutableState<Boolean>
    private lateinit var needsPermissions: MutableState<Boolean>

    private lateinit var lastCompleteTimestamp: MutableLongState
    private lateinit var lastCompleteRunCount: MutableIntState

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
        freeSpace = remember { mutableLongStateOf(0L) }
        waiting = remember { mutableStateOf(false) }
        exceptionInt = remember { mutableStateOf(false) }
        needsPermissions = remember { mutableStateOf(false) }
        lastCompleteTimestamp = remember { mutableLongStateOf(0L) }
        lastCompleteRunCount = remember { mutableIntStateOf(1) }

        FreeDiskShredderTheme {
            val driveNames: List<String> = MainUtils.getDriveNames()

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
                fontSize = 12.sp,
                color = colorScheme.primary
            )
            Row {
                Text(
                    text = " ",
                    fontSize = 24.sp,
                    color = colorScheme.primary
                )
                Text(
                    text = text,
                    fontSize = 24.sp,
                    color = colorScheme.primary
                )
            }
            Text(
                text = " ",
                fontSize = 12.sp,
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

        var percentText: String

        if(percent.doubleValue < 0.0) {
            percentText = "Deleting temporary files.."
        } else if(percent.doubleValue < 0.0001) {
            if(lastCompleteTimestamp.longValue > 0L) {
                try {
                    val locale: Locale = resources.configuration.getLocales().get(0)
                    val sdf = SimpleDateFormat("MM/dd/yyyy hh:mmaa", locale)
                    val lastCompleteDate = Date(lastCompleteTimestamp.longValue * 1000L)
                    val dateFormat = sdf.format(lastCompleteDate)

                    if(lastCompleteRunCount.intValue > 1) {
                        val runCountInt = lastCompleteRunCount.intValue
                        percentText = "Ran $runCountInt $dateFormat"
                    } else {
                        percentText = "Ran $dateFormat"
                    }

                } catch (_: Exception) {
                    percentText = " "
                }
            } else {
                percentText = " "
            }
        } else if(runIndex.intValue == 1) {
            percentText = "$percentValue% complete"
        } else {
            percentText = "Run $runIndexValue: $percentValue% complete"
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
                        fontSize = 12.sp,
                        color = colorScheme.primary
                    )
                }
                Surface(
                    color = colorScheme.background
                ) {
                    Row {
                        Text(
                            text = " ",
                            fontSize = 24.sp,
                            color = colorScheme.primary
                        )
                        Text(
                            text = label,
                            fontSize = 24.sp,
                            color = colorScheme.primary
                        )
                        Text(
                            text = ": ",
                            fontSize = 24.sp,
                            color = colorScheme.primary
                        )
                        Text(
                            text = options[index.intValue],
                            fontSize = 24.sp,
                            color = colorScheme.primary
                        )
                    }
                }
                Surface(
                    color = colorScheme.background
                ) {
                    Text(
                        text = " ",
                        fontSize = 12.sp,
                        color = colorScheme.primary
                    )
                }
            }
        }
    }

    @Composable
    fun ActionButton() {
        val text: String =
            if(waiting.value) {
                "Still cleaning old files.."
            } else if(serviceRunning.value) {
                "In progress.. Cancel"
            } else if(isRunning.value) {
                "Starting.."
            } else if(needsPermissions.value) {
                "Check Permissions"
            } else {
                "Shred empty space"
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
                            actionHandler()
                        }) {
                    ScaledText(text)
                }
            }
        }
    }
}
