package com.turtlepaw.smartbattery

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun TimePickerDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Pair<Int, Int>) -> Unit,
    initialTime: Pair<Int, Int> = Pair(0, 0)
) {
    if (showDialog) {
        var selectedHour by remember { mutableStateOf(initialTime.first) }
        var selectedMinute by remember { mutableStateOf(initialTime.second) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select Time") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Hours picker (0-23)
                    NumberPicker(
                        value = selectedHour,
                        onValueChange = { selectedHour = it },
                        range = 0..23
                    )
                    Text(":", modifier = Modifier.padding(horizontal = 8.dp))
                    // Minutes picker (0-59)
                    NumberPicker(
                        value = selectedMinute,
                        onValueChange = { selectedMinute = it },
                        range = 0..59
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(Pair(selectedHour, selectedMinute))
                    onDismiss()
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        IconButton(onClick = {
            if (value < range.last) onValueChange(value + 1)
        }) {
            Icon(Icons.Rounded.KeyboardArrowUp, "Increase")
        }

        Text(
            text = value.toString().padStart(2, '0'),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        IconButton(onClick = {
            if (value > range.first) onValueChange(value - 1)
        }) {
            Icon(Icons.Rounded.KeyboardArrowDown, "Decrease")
        }
    }
}

val roundedIconMap = mapOf(
    "Star" to Icons.Rounded.Star,
    "Home" to Icons.Rounded.Home,
    "Favorite" to Icons.Rounded.Favorite,
    "Settings" to Icons.Rounded.Settings,
    "Battery" to Icons.Rounded.BatteryFull,
    "Bolt" to Icons.Rounded.Bolt,
    "Bedtime" to Icons.Rounded.Bedtime,

)

@Composable
fun RenderIcon(iconName: String, modifier: Modifier = Modifier) {
    val imageVector = roundedIconMap[iconName] ?: Icons.AutoMirrored.Rounded.Help // Default icon

    Icon(
        imageVector = imageVector,
        contentDescription = iconName,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    selectedIcon: String? = null,
    onIconSelected: (String) -> Unit
){
    FlowRow(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        roundedIconMap.forEach { (iconName, imageVector) ->
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(
                        40.dp
                    )
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable {
                        onIconSelected(iconName)
                    }
                    .then(
                        if (selectedIcon == iconName) Modifier.border(
                            3.dp,
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ){
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    RenderIcon(iconName)
                }
            }
        }
    }
}