package com.example.freediskshredder

import android.os.Bundle
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.freediskshredder.ui.theme.FreeDiskShredderTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreeDiskShredder(false)
        }

    }

    @Composable
    fun FreeDiskShredder(isPreview: Boolean) {
        var percent: Double by remember { mutableDoubleStateOf(0.0) }

        if(isPreview) {
            percent += 0.15
        }
//        if(!isPreview) {
//            val handler = Handler(Looper.getMainLooper())
//
//            val runnable = object : Runnable {
//                override fun run() {
//                    percent += 0.01
//                    if(percent > 1.0) {
//                        percent = 0.0
//                    }
//
//                    //handler.postDelayed(this, 1000L)
//                }
//            }
//
//            //handler.postDelayed(runnable, 0)
//        }

        FreeDiskShredderTheme {
            val driveOptions = ArrayList<String>()
            driveOptions.add("Internal Storage")
            driveOptions.add("SD Card")

            val repeatOptions = ArrayList<String>()
            repeatOptions.add("1")
            repeatOptions.add("2")
            repeatOptions.add("4")
            repeatOptions.add("8")

            val isRunning: MutableState<Boolean> = remember { mutableStateOf(false) }

            Column(modifier = Modifier
                .safeDrawingPadding()
                .fillMaxSize()) {

                OptionItem("Drive", driveOptions, isRunning)
                OptionItem("Repeat", repeatOptions, isRunning)
                ListSurfaceItem(isRunning)
                Box {
                    ProgressBar(percent)
                    ProgressText(percent)
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
        FreeDiskShredder(true)
    }

//    @Composable
//    fun Spacer() {
//        Surface(
//            color = MaterialTheme.colorScheme.tertiary,
//            modifier = Modifier
//                .fillMaxWidth()) {
//            Text(
//                text = "",
//                modifier = Modifier
//                    .size(8.dp)
//            )
//        }
//    }

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
                    text = "  ",
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
    fun ProgressBar(percent: Double) {
        val surfaceWidth = remember { mutableIntStateOf(0) }

        val percentPixels = surfaceWidth.intValue.toDouble() * percent

        val dpValue = percentPixels / LocalDensity.current.density

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
    fun ProgressText(percent: Double) {
        var percentText = ((percent * 1000).toInt().toDouble() / 10.0).toString() + "% complete"

        if(percent < 0.001) {
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
    fun OptionItem(label: String, options: List<String>, isRunning: MutableState<Boolean>) {
        var index: Int by remember { mutableIntStateOf(0) }

        Surface(
            color = colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if(!isRunning.value) {
                        var nextIndex = index + 1

                        if(nextIndex >= options.size) {
                            nextIndex = 0
                        }

                        index = nextIndex
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
                            text = options[index],
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
    fun ListSurfaceItem(isRunning: MutableState<Boolean>) {
        var text = "Start"
        if(isRunning.value) {
            text = "Stop"
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
                            isRunning.value = !isRunning.value
                        }) {
                    ScaledText(text)
                }
            }
        }
    }
}


