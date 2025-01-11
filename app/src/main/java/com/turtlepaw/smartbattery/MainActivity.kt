package com.turtlepaw.smartbattery

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.defaults.DefaultFadingTransitions
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.NavHostGraph
import com.ramcosta.composedestinations.annotation.parameters.CodeGenVisibility
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.ChargingModePageDestination
import com.ramcosta.composedestinations.generated.destinations.ModePageDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.rememberNavHostEngine
import com.turtlepaw.smartbattery.shizuku.ShizukuState
import com.turtlepaw.smartbattery.shizuku.ShizukuUtils
import com.turtlepaw.smartbattery.shizuku.grantSecureSettingsPermission
import com.turtlepaw.smartbattery.ui.theme.SmartBatteryTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.Calendar
import java.util.Locale
import kotlin.reflect.KProperty

@NavHostGraph(
    defaultTransitions = DefaultFadingTransitions::class,
    route = "preferred_route",
    visibility = CodeGenVisibility.PUBLIC,
)
annotation class MainGraph

class DialogController {
    private val _isDialogOpen = mutableStateOf(false)
    val isDialogOpen: State<Boolean> = _isDialogOpen

    fun openDialog() {
        _isDialogOpen.value = true
    }

    fun closeDialog() {
        _isDialogOpen.value = false
    }
}

val LocalDialogController =
    compositionLocalOf<DialogController> { error("No dialog controller provided") }

class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(this)
    }

    private val isGrantedState = mutableStateOf(ShizukuUtils.isPermissionGranted(this))
    private val dialogController = DialogController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shizukuState = ShizukuUtils.getShizukuState(this)

        Shizuku.addRequestPermissionResultListener(this)

        // check if work exists first
        val workManager = WorkManager.getInstance(this)
        val workQuery = WorkQuery.Builder.fromUniqueWorkNames(listOf("battery_schedule")).addStates(
            listOf(
                androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.RUNNING
            )
        ).build()

        val workInfos = workManager.getWorkInfosLiveData(workQuery).value
        if (workInfos == null || workInfos.isEmpty()) {
            // schedule at midnight
            val constraints = Constraints.Builder().build()

            workManager.enqueueUniquePeriodicWork(
                "battery_schedule",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SecureSettingsWorker>(
                    1,
                    TimeUnit.DAYS
                ).setBackoffCriteria(
                    androidx.work.BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS
                ).setConstraints(constraints)
                    .setInitialDelay(getDelayUntilNextMidnight(), TimeUnit.MILLISECONDS).build()
            )

            // Schedule a task tonight
            workManager.enqueue(
                OneTimeWorkRequestBuilder<SecureSettingsWorker>().setInitialDelay(
                    getDelayUntilNextMidnight(),
                    TimeUnit.MILLISECONDS
                ).setConstraints(constraints).addTag("battery_schedule_one_time")
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS
                    ).build()
            )

            // log the work manager's time
            val workInfo = workManager.getWorkInfosByTag("battery_schedule_one_time").get()
            if (workInfo.isNotEmpty()) {
                val time = workInfo[0].nextScheduleTimeMillis
                println("Next schedule time: $time")
                // print exact time in format HH:MM:SS
                Log.d("MainActivity", "Next schedule time: ${java.util.Date(time)}")
                // log day of month `time` will be
                Log.d(
                    "MainActivity", "Next schedule day of month: ${
                        java.util.Calendar.getInstance().apply { timeInMillis = time }
                            .get(java.util.Calendar.DAY_OF_MONTH)
                    }")
                // and compare to `getDelayUntilNextMidnight()` and log the day of month and time as HH:MM:SS
                val timeNow = System.currentTimeMillis()
                val time2 = getDelayUntilNextMidnight() + timeNow
                Log.d(
                    "MainActivity", "Next schedule day of month: ${
                        java.util.Calendar.getInstance()
                            .apply { timeInMillis = getDelayUntilNextMidnight() }
                            .get(java.util.Calendar.DAY_OF_MONTH)
                    }")
                Log.d("MainActivity", "Next schedule time: ${java.util.Date(time2)}")
            }
        }

        enableEdgeToEdge()
        setContent {
            val context = this@MainActivity;
            var schedule by remember { mutableStateOf(List(7) { false }) }
            var defaultMode by remember { mutableStateOf(BatteryState.Unknown) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val days = remember(context) { getDaysOfWeekShort(context) }

            val state by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
            val manager = BatteryStateManager()
            var _batteryState = StateHolder<BatteryState>()
            var batteryState by _batteryState
            var secureSettingsGranted = SecureSettingsStateHolder()
            var modesRepository = ModesRepository()

            LaunchedEffect(state, Unit) {
                modesRepository.getAllModes(context)
                batteryState = manager.getChargingMode(context.contentResolver)
                secureSettingsGranted.refresh(this@MainActivity)
                schedule = manager.getSchedule(context)
                defaultMode = manager.getDefaultMode(context)

                if (batteryState != BatteryState.Unknown && batteryState != null) {
                    manager.setDefaultMode(context, batteryState!!)
                }
            }

            SmartBatteryTheme {
                val isDialogOpen by dialogController.isDialogOpen

                if (isDialogOpen) {
                    AlertDialog(icon = {
                        Icon(
                            painterResource(R.drawable.download),
                            contentDescription = "Download"
                        )
                    }, title = {
                        Text(
                            text = stringResource(R.string.shizuku_not_installed),
                            textAlign = TextAlign.Center
                        )
                    }, text = {
                        Text(
                            text = stringResource(R.string.shizuku_not_installed_description),
                            textAlign = TextAlign.Center
                        )
                    }, onDismissRequest = {
                        dialogController.closeDialog()
                    }, confirmButton = {
                        TextButton(
                            onClick = {
                                dialogController.closeDialog()
                                ShizukuUtils.openPlayStoreListing(this@MainActivity)
                            }) {
                            Text(
                                stringResource(
                                    android.R.string.ok
                                )
                            )
                        }
                    },
                        dismissButton = {
                        TextButton(
                            onClick = {
                                dialogController.closeDialog()
                            }) {
                            Text(
                                stringResource(
                                    android.R.string.cancel
                                )
                            )
                        }
                    }
                    )
                }

                val navHostEngine = rememberNavHostEngine()
                val navigator = navHostEngine.rememberNavController()

                CompositionLocalProvider(
                    LocalIsGrantedState provides isGrantedState,
                    LocalDialogController provides dialogController,
                    LocalMainActivity provides this,
                    LocalSecureSettingsGranted provides secureSettingsGranted,
                    LocalChargingModeState provides _batteryState,
                    LocalDefaultModeState provides _batteryState,
                    LocalModesRepository provides modesRepository
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        DestinationsNavHost(
                            navGraph = NavGraphs.preferredRoute,
                            engine = navHostEngine,
                            navController = navigator
                        )
                    }
                }
            }
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        isGrantedState.value = grantResult == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        isGrantedState.value = ShizukuUtils.isPermissionGranted(this)
    }
}

val LocalIsGrantedState =
    compositionLocalOf<State<Boolean>> { error("No permission state provided") }
val LocalMainActivity = compositionLocalOf<ComponentActivity> { error("No activity provided") }
val LocalSecureSettingsGranted =
    compositionLocalOf<SecureSettingsStateHolder> { error("No secure settings granted state provided") }
val LocalChargingModeState =
    compositionLocalOf<StateHolder<BatteryState>> { error("No charging mode state provided") }
val LocalDefaultModeState =
    compositionLocalOf<StateHolder<BatteryState>> { error("No default mode state provided") }
val LocalModesRepository =
    compositionLocalOf<ModesRepository> { error("No modes controller provided") }

class StateHolder<T> {
    private val _state = mutableStateOf<T?>(null)
    val state: State<T?> = _state

    // For property delegation
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return _state.value
    }

    // For direct access if needed
    fun getValue(): T? {
        return _state.value
    }

    fun setValue(state: T?) {
        _state.value = state
    }

    // Optional: Add setValue operator for var properties
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        _state.value = value
    }
}

class SecureSettingsStateHolder {
    private val _isGranted = mutableStateOf(false)
    val isGranted: State<Boolean> = _isGranted

    fun refresh(context: Context) {
        _isGranted.value = ShizukuUtils.isSecureSettingsGranted(context)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<MainGraph>(start = true) // HomePage as the starting destination
@Composable
fun HomePage(navigator: DestinationsNavigator) {
    val context = LocalMainActivity.current
    val secureSettingsGranted = LocalSecureSettingsGranted.current
    val isSecureSettingsGranted by secureSettingsGranted.isGranted
    val _batteryState = LocalChargingModeState.current
    var batteryState by _batteryState
    var schedule by remember { mutableStateOf(List(7) { false }) }
    val manager = BatteryStateManager()
    val defaultMode by LocalDefaultModeState.current
//    val suggestions by remember { mutableStateOf<List<ModeEntity>>(emptyList()) }
//
//    LaunchedEffect(Unit) {
//        // if phone is a pixel >6
//        if(Build.MANUFACTURER == "Google" && Build.VERSION.SDK_INT >= 35){
//            suggestions = suggestions.plus(
//                ModeEntity(
//
//                )
//            )
//        }
//    }

    Scaffold(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { padding ->
        val isGranted by LocalIsGrantedState.current
        val dialogController = LocalDialogController.current

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(padding)
                .padding(top = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CategoryTitle(
                title = R.string.permissions_title
            )
            ConfigurationItem(
                onCheckedChange = {
                    val state = ShizukuUtils.getShizukuState(context)
                    Log.d("MainActivity", "Shizuku state: $state")
                    if (state == ShizukuState.NOT_INSTALLED) {
                        dialogController.openDialog()
                    } else if (state == ShizukuState.NOT_RUNNING) {
                        Toast.makeText(
                            context, R.string.shizuku_not_running, Toast.LENGTH_SHORT
                        ).show()
                        ShizukuUtils.startShizukuActivity(context)
                    } else if (state == ShizukuState.PERMISSION_DENIED) {
                        ShizukuUtils.requestPermission(context, 1)
                        // Argument type mismatch: actual type is 'android. content. Context', but 'androidx. activity. ComponentActivity' was expected.
                    }
                },
                checked = isGranted,
                leadingText = R.string.shizuku_title,
                supportingText = when (ShizukuUtils.getShizukuState(context)) {
                    ShizukuState.NOT_INSTALLED -> R.string.shizuku_not_installed
                    ShizukuState.NOT_RUNNING -> R.string.shizuku_not_running
                    ShizukuState.PERMISSION_DENIED -> R.string.shizuku_permission_denied
                    ShizukuState.RUNNING -> R.string.shizuku_running
                },
                enabled = !isGranted
            )

            ConfigurationItem(
                onCheckedChange = {
                    context.grantSecureSettingsPermission()
                    secureSettingsGranted.refresh(context)
                },
                leadingText = R.string.secure_settings_permission,
                supportingText = when (isSecureSettingsGranted) {
                    true -> R.string.secure_settings_permission_granted
                    false -> R.string.secure_settings_permission_denied
                },
                checked = isSecureSettingsGranted,
                enabled = isGranted && !isSecureSettingsGranted
            )

            CategoryTitle(
                title = R.string.charging_mode,
                //description = R.string.charging_mode_warning
            )

            ElevatedConfigurationItem(
                onCheckedChange = {
                    // navigate to charging mode page
                    navigator.navigate(ChargingModePageDestination)
                    //navigator.navigate(ModePageDestination(mode = ""))
                },
                leadingText = stringResource(R.string.charging_mode),
                enabled = isSecureSettingsGranted,
                icon = {
                    Icon(
                        Icons.Rounded.BatteryChargingFull,
                        contentDescription = "Charging mode",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                }
            )

            CategoryTitle(
                title = R.string.modes_title,
                //description = R.string.charging_mode_warning
            )

            val modesController = LocalModesRepository.current
            modesController.modes.map {
                ElevatedConfigurationItem(
                    onCheckedChange = { _ ->
                        navigator.navigate(ModePageDestination(mode = it.id))
                    },
                    leadingText = it.name, enabled = isSecureSettingsGranted,
                    icon = {
                        RenderIcon(it.iconName ?: "")
                    }
                )
            }

            ElevatedConfigurationItem(
                onCheckedChange = { _ ->
                    navigator.navigate(ModePageDestination())
                },
                leadingText = stringResource(R.string.new_mode), enabled = isSecureSettingsGranted,
                icon = {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            )

            Box(
                modifier = Modifier.clickable {
                    // enqueue temp task to test
                    val workManager = WorkManager.getInstance(context)
                    workManager.enqueue(
                        OneTimeWorkRequestBuilder<SecureSettingsWorker>().build()
                    )

                    Toast.makeText(context, "Task enqueued", Toast.LENGTH_SHORT).show()

                    // wait
                    Thread.sleep(4000)
                    batteryState = manager.getChargingMode(context.contentResolver)
                }

            ) {
                CategoryTitle(
                    title = R.string.schedule
                )
            }

            Schedule(
                daysSelected = schedule, onDaysSelected = {
                    manager.setSchedule(context, it)
                    schedule = it
                }, leadingText = context.getString(
                    R.string.schedule_title, context.getString(
                        when (getBatteryStateOpposite(defaultMode ?: BatteryState.Unknown)) {
                            BatteryState.Adaptive -> R.string.adaptive_charging_enabled
                            BatteryState.Limited -> R.string.charge_optimization_mode
                            BatteryState.Unknown -> R.string.unknown
                            null -> R.string.unknown
                        }
                    )
                ), enabled = isSecureSettingsGranted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<MainGraph>
@Composable
fun ChargingModePage(navigator: DestinationsNavigator) {
    val context = LocalMainActivity.current
    val secureSettingsGranted = LocalSecureSettingsGranted.current
    val isSecureSettingsGranted by secureSettingsGranted.isGranted
    val _batteryState = LocalChargingModeState.current
    var batteryState by _batteryState
    val manager = BatteryStateManager()
    var defaultMode by LocalDefaultModeState.current

    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Text(stringResource(R.string.charging_mode))
        }, navigationIcon = {
            IconButton(onClick = { navigator.navigateUp() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        })
    }) { padding ->
        val isGranted by LocalIsGrantedState.current
        val dialogController = LocalDialogController.current

        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
                .padding(padding)
                //.padding(top = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ConfigurationItem(
                onCheckedChange = {
                    manager.setChargingMode(context.contentResolver, BatteryState.Adaptive)
                    batteryState = manager.getChargingMode(context.contentResolver)
                    manager.setDefaultMode(context, BatteryState.Adaptive)
                    defaultMode = BatteryState.Adaptive
                },
                leadingText = R.string.adaptive_charging_enabled,
                supportingText = R.string.adaptive_charging_enabled_description,
                checked = batteryState == BatteryState.Adaptive,
                type = ConfigurationType.Radio,
                enabled = isSecureSettingsGranted
            )

            ConfigurationItem(
                onCheckedChange = {
                    manager.setChargingMode(context.contentResolver, BatteryState.Limited)
                    batteryState = manager.getChargingMode(context.contentResolver)
                    manager.setDefaultMode(context, BatteryState.Limited)
                    defaultMode = BatteryState.Limited
                },
                leadingText = R.string.charge_optimization_mode,
                supportingText = R.string.charge_optimization_mode_description,
                checked = batteryState == BatteryState.Limited,
                type = ConfigurationType.Radio,
                enabled = isSecureSettingsGranted
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 26.dp)
                    .clip(MaterialTheme.shapes.medium), horizontalAlignment = Alignment.Start
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = "Info",
                    modifier = Modifier.padding(vertical = 5.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.charging_mode_warning),
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<MainGraph>
@Composable
fun ModePage(navigator: DestinationsNavigator, mode: String? = null) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalMainActivity.current
    val secureSettingsGranted = LocalSecureSettingsGranted.current
    val isSecureSettingsGranted by secureSettingsGranted.isGranted
    val _batteryState = LocalChargingModeState.current
    var batteryState by _batteryState
    val manager = BatteryStateManager()
    var defaultMode by LocalDefaultModeState.current

    // Recording
    var start by remember { mutableStateOf(emptyMap<String, Pair<String, String>>()) }
    var end by remember { mutableStateOf(emptyMap<String, Pair<String, String>>()) }
    var isRecording by remember { mutableStateOf(false) }

    val modesController = LocalModesRepository.current
    val _mode = modesController.modes.find { it.id == mode }
    var mode by remember {
        mutableStateOf(
            _mode ?: ModeEntity(
                name = "",
                settings = emptyList(),
                scheduledTime = null,
                scheduleDays = listOf(false, false, false, false, false, false, false),
                )
        )
    }
    var settings by remember {
        mutableStateOf(mode.settings)
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = mode.scheduledTime?.hour ?: 0,
        initialMinute = mode.scheduledTime?.minute ?: 0
    )
    var scheduledTime by remember { mutableStateOf<LocalDateTime?>(null) }
    LaunchedEffect(Unit) {
        // get scheduled time from work manager
        val workManager = WorkManager.getInstance(context)
        val workInfo = workManager.getWorkInfosByTag("mode_${mode.id}").get()
        if (workInfo.isNotEmpty()) {
            scheduledTime = workInfo[0].run {
                val timeZone = ZoneId.systemDefault() // Use the system's default time zone

                LocalDateTime.ofInstant(Instant.ofEpochMilli(nextScheduleTimeMillis), timeZone)
            }
        }
    }

    // update settings automatically
    LaunchedEffect(mode) {
        settings = mode.settings
    }

    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        mode = mode.copy(
            scheduledTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
        )
    }

    var isValid by remember {
        mutableStateOf(
            mode.name.isNotEmpty() && mode.scheduledTime != null && mode.settings.isNotEmpty()
        )
    }

    LaunchedEffect(mode) {
        isValid = mode.name.isNotEmpty() && mode.scheduledTime != null && mode.settings.isNotEmpty()
    }

    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(
            title = {
                Text(stringResource(R.string.charging_mode))
            },
            navigationIcon = {
                IconButton(onClick = { navigator.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = {
                    // save mode
                    coroutineScope.launch {
                        // If it doesn't already exist
                        if (_mode != null) {
                            modesController.editMode(mode, context)
                        } else {
                            modesController.addMode(mode, context)
                        }

                        ModeScheduleManager(context, modesController).scheduleMode(mode)
                    }

                    navigator.navigateUp()
                }, enabled = isValid) {
                    Icon(Icons.Rounded.Save, contentDescription = "Save")
                }
            }
        )
    }) { padding ->
        val isGranted by LocalIsGrantedState.current
        val dialogController = LocalDialogController.current

        if (showDatePicker) {
            TimePickerDialog(
                onDismiss = {
                    showDatePicker = false
                },
                onConfirm = {
                    showDatePicker = false
                }
            ) {
                TimePicker(
                    state = timePickerState,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
                .padding(padding)
                //.padding(top = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier.padding(bottom = 16.dp, top = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.circle),
                    contentDescription = "Circle",
                    modifier = Modifier
                        .padding(vertical = 5.dp)
                        .size(100.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primaryContainer)
                )

                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    RenderIcon(
                        iconName = mode.iconName ?: "",
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .size(50.dp),
                    )
                }
            }

            // text input for name
            OutlinedTextField(
                value = mode.name,
                onValueChange = {
                    mode = mode.copy(name = it)
                },
                label = { Text("Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                isError = mode.name.isEmpty(),
                singleLine = true,
                maxLines = 1
            )

            CategoryTitle(
                title = R.string.icon_title,
            )

            IconPicker(
                selectedIcon = mode.iconName,
                onIconSelected = {
                    mode = mode.copy(iconName = it)
                }
            )

            CategoryTitle(
                title = R.string.trigger_title,
            )

            val timeFormat = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
            ElevatedConfigurationItem(
                onCheckedChange = {
                    showDatePicker = !showDatePicker
                },
                leadingText = "Run at time",
                supportingText = if (mode.scheduledTime != null) timeFormat.format(mode.scheduledTime) else "Never",
                icon = {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = "Schedule",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            )

            Schedule(
                daysSelected = mode.scheduleDays ?: List(7) { false },
                onDaysSelected = {
                    mode = mode.copy(scheduleDays = it)
                },
                leadingText = "Schedule",
                enabled = true
            )

            CategoryTitle(
                title = R.string.actions_title,
            )

            ElevatedConfigurationItem(
                onCheckedChange = {
                    if (!isRecording) {
                        start = CombinedSettings.getCombinedSettings(context)
                    } else {
                        end = CombinedSettings.getCombinedSettings(context)
                        settings = end.filter { (k, v) -> start[k] != v }.map {
                            SettingEntity(
                                id = it.key,
                                modeId = mode.id,
                                key = it.key,
                                value = it.value.first,
                                namespace = it.value.second
                            )
                        }

                        mode = mode.copy(settings = settings)
                    }
                    isRecording = !isRecording
                },
                leadingText = (if (!isRecording) "Start" else "Stop") + " Recording",
                icon = {
                    Icon(
                        if (!isRecording) Icons.Rounded.PlayArrow else Icons.Rounded.Stop,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            )

            settings.map {
                ElevatedConfigurationItem(
                    onCheckedChange = { _ ->
                        mode = mode.copy(
                            settings = mode.settings.map { setting ->
                                if (setting.id == it.id) {
                                    setting.copy(enabled = !setting.enabled)
                                } else {
                                    setting
                                }
                            }
                        )
                    },
                    leadingText = "${
                        it.namespace.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(
                                Locale.getDefault()
                            ) else it.toString()
                        }
                    }: ${it.key}",
                    supportingText = when (it.value) {
                        "true" -> "Enabled"
                        "false" -> "Disabled"
                        "0" -> "Turn off"
                        "1" -> "Turn on"
                        else -> it.value
                    },
                    icon = {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                        )
                    },
                    checked = it.enabled,
                )
            }

            // compare start and end and show the differences in a list
            if (start.isNotEmpty() && end.isNotEmpty()) {
                val diff = end.filter { (k, v) -> start[k] != v }

                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(16.dp)
                ) {
                    //Text(diff.toString())
                    Text(
                        Json {
                            prettyPrint = true
                        }.encodeToString(diff.mapValues { it.value.first }),
                        overflow = TextOverflow.Visible,
                        // set font to mono
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (scheduledTime != null) {
                val timeFormat =
                    DateTimeFormatter.ofPattern("hh:mm a dd/MM/yyyy", Locale.getDefault())
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 26.dp)
                        .clip(MaterialTheme.shapes.medium), horizontalAlignment = Alignment.Start
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = "Info",
                        modifier = Modifier.padding(vertical = 5.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        "Scheduled to run at ${getFormattedDateTime(scheduledTime!!)}",
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
            }
        }
    }
}

fun getFormattedDateTime(dateTime: LocalDateTime): String {
    val day = dateTime.dayOfMonth
    val monthYearFormat = DateTimeFormatter.ofPattern("MMMM yyyy hh:mm a", Locale.getDefault())
    val suffix = getDaySuffix(day)

    return "$day$suffix ${dateTime.format(monthYearFormat)}"
}

fun getDaySuffix(day: Int): String {
    return when {
        day in 11..13 -> "th" // Special case for 11th, 12th, 13th
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
}

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Dismiss")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm() }) {
                Text("OK")
            }
        },
        text = { content() }
    )
}

enum class ConfigurationType {
    Switch, Radio
}

@Composable
fun ElevatedConfigurationItem(
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit,
    leadingText: String,
    supportingText: String? = null,
    icon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    type: ConfigurationType = ConfigurationType.Switch
) {
    val interactionSource = remember { MutableInteractionSource() }
    var modifier = Modifier
        .padding(vertical = 4.dp)
        .padding(horizontal = 16.dp)
        .clip(MaterialTheme.shapes.medium)
        .clickable(
            enabled = enabled, onClick = {
                onCheckedChange(checked != true)
            }, interactionSource = interactionSource, indication = ripple()
        )

    val onColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        ),
        headlineContent = {
            Text(
                leadingText,
                color = onColor
            )
        },
        supportingContent = {
            if (supportingText != null) {
                Text(supportingText, color = onColor)
            }
        },
        trailingContent = {
            if (checked != null) {
                when (type) {
                    ConfigurationType.Switch -> {
                        Switch(
                            checked = checked,
                            onCheckedChange = onCheckedChange,
                            enabled = enabled,
                            interactionSource = interactionSource
                        )
                    }

                    ConfigurationType.Radio -> {
                        RadioButton(
                            selected = checked, onClick = {
                                onCheckedChange(!checked)
                            }, enabled = enabled, interactionSource = interactionSource
                        )
                    }
                }
            }
        },
        leadingContent = {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides onColor) {
                    icon()
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun ConfigurationItem(
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit,
    leadingText: Int,
    supportingText: Int? = null,
    icon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    child: Boolean = false,
    type: ConfigurationType = ConfigurationType.Switch
) {
    ConfigurationItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        leadingText = stringResource(leadingText),
        supportingText = supportingText?.let { stringResource(it) },
        icon = icon,
    )
}

@Composable
fun ConfigurationItem(
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit,
    leadingText: String,
    supportingText: String? = null,
    icon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    child: Boolean = false,
    type: ConfigurationType = ConfigurationType.Switch
) {
    val interactionSource = remember { MutableInteractionSource() }
    var modifier = Modifier
        .then(
            if (child) Modifier
                .padding(start = 20.dp, end = 15.dp)
                .clip(MaterialTheme.shapes.medium) else Modifier
        )
        .clickable(
            enabled = enabled, onClick = {
                onCheckedChange(checked != true)
            }, interactionSource = interactionSource, indication = ripple()
        )
        .padding(vertical = 4.dp)

    ListItem(
        headlineContent = { Text(leadingText) }, supportingContent = {
            if (supportingText != null) {
                Text(supportingText)
            }
        }, trailingContent = {
            if (checked != null) {
                when (type) {
                    ConfigurationType.Switch -> {
                        Switch(
                            checked = checked,
                            onCheckedChange = onCheckedChange,
                            enabled = enabled,
                            interactionSource = interactionSource
                        )
                    }

                    ConfigurationType.Radio -> {
                        RadioButton(
                            selected = checked, onClick = {
                                onCheckedChange(!checked)
                            }, enabled = enabled, interactionSource = interactionSource
                        )
                    }
                }
            }
        }, leadingContent = {
            if (icon != null) {
                icon()
            }
        }, modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Schedule(
    daysSelected: List<Boolean>,
    onDaysSelected: (List<Boolean>) -> Unit,
    leadingText: String,
    supportingText: Int? = null,
    icon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    child: Boolean = false,
    days: List<String> = getDaysOfWeekShort(LocalContext.current)
) {
    ListItem(
        headlineContent = { Text(leadingText) }, supportingContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                //verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                days.mapIndexed { index, it ->
                    var selected = daysSelected.elementAt(index)

                    FilterChip(
                        onClick = {
                            selected = !selected
                            onDaysSelected(daysSelected.mapIndexed { index, b ->
                                if (index == days.indexOf(it)) selected else b
                            })
                        },
                        label = {
                            Text(it)
                        },
                        enabled = enabled,
                        selected = selected,
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Done,
                                    contentDescription = "Done icon",
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }, leadingContent = {
            if (icon != null) {
                icon()
            }
        }, modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun CategoryTitle(title: Int, description: Int? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium)
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.primary
            ),
            textAlign = TextAlign.Start,
        )

        if (description != null) {
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Start,
            )
        }
    }
}

fun getDelayUntilNextMidnight(): Long {
    val calendar = Calendar.getInstance()
    val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
    val currentMinute = calendar.get(Calendar.MINUTE)

    // if time is around midnight, schedule it for the next day
    if (currentHour >= 23 && currentMinute >= 30) {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    // Schedule for 00:30:00
    // using 00:00:00 is risky if the phone is turned off during that time
    with(calendar) {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 5)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val now = System.currentTimeMillis()

    val nextMidnight = calendar.timeInMillis

    return nextMidnight - now
}

fun getDaysOfWeekShort(context: Context): List<String> {
    val calendar = Calendar.getInstance()
    val daysOfWeek = mutableListOf<String>()

    for (i in Calendar.SUNDAY..Calendar.SATURDAY) {
        calendar.set(Calendar.DAY_OF_WEEK, i)
        daysOfWeek.add(
            calendar.getDisplayName(
                Calendar.DAY_OF_WEEK, Calendar.SHORT, context.resources.configuration.locales.get(0)
            )!!
        )
    }

    return daysOfWeek
}