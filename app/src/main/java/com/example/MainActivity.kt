package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.WaterLog
import com.example.ui.WaterViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val viewModel: WaterViewModel = viewModel(
        factory = WaterViewModel.Factory(context)
    )

    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val logs by viewModel.todayLogs.collectAsStateWithLifecycle()
    val totalLogged by viewModel.totalLoggedToday.collectAsStateWithLifecycle()
    val waterGoal by viewModel.waterGoal.collectAsStateWithLifecycle()

    val remindersEnabled by viewModel.remindersEnabled.collectAsStateWithLifecycle()
    val reminderInterval by viewModel.reminderInterval.collectAsStateWithLifecycle()
    val startHour by viewModel.startHour.collectAsStateWithLifecycle()
    val endHour by viewModel.endHour.collectAsStateWithLifecycle()

    var showCustomDialog by remember { mutableStateOf(false) }
    var currentTab by remember { mutableIntStateOf(0) }

    // Check & Request Permission for Push Notifications (Android 13+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.setRemindersEnabled(true)
                Toast.makeText(context, "Hydration reminders active!", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.setRemindersEnabled(false)
                Toast.makeText(context, "Notifications permission is required to send reminders.", Toast.LENGTH_LONG).show()
            }
        }
    )

    fun handleReminderSwitch(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.setRemindersEnabled(true)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.setRemindersEnabled(enabled)
            if (!enabled) {
                Toast.makeText(context, "Reminders turned off.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val progress = if (waterGoal > 0) (totalLogged.toFloat() / waterGoal.toFloat()).coerceIn(0f, 2f) else 0f
    val latestLogTime = logs.firstOrNull()?.timestamp

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            CleanBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header styled precisely like Clean Minimalism HTML
            CleanHeader(
                selectedDate = selectedDate,
                onConfigureGoal = { currentTab = 2 } // redirects to settings
            )

            // Dynamic view selector based on the tab chosen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "tab_navigation"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> DashboardView(
                            progress = progress,
                            totalLogged = totalLogged,
                            waterGoal = waterGoal,
                            remindersEnabled = remindersEnabled,
                            reminderInterval = reminderInterval,
                            startHour = startHour,
                            endHour = endHour,
                            latestLogTime = latestLogTime,
                            onToggleReminders = { handleReminderSwitch(it) },
                            onLogWater = { ml -> viewModel.logWater(ml) },
                            onShowCustomInput = { showCustomDialog = true },
                            onConfigureSettings = { currentTab = 2 }
                        )
                        1 -> HistoryView(
                            selectedDate = selectedDate,
                            logs = logs,
                            totalLogged = totalLogged,
                            waterGoal = waterGoal,
                            onDateSelected = { viewModel.selectDate(it) },
                            onDeleteLog = { id -> viewModel.deleteLog(id) }
                        )
                        2 -> SettingsView(
                            waterGoal = waterGoal,
                            reminderInterval = reminderInterval,
                            startHour = startHour,
                            endHour = endHour,
                            remindersEnabled = remindersEnabled,
                            onGoalChange = { viewModel.setWaterGoal(it) },
                            onReminderHoursChange = { start, end -> viewModel.setReminderHours(start, end) },
                            onReminderIntervalChange = { viewModel.setReminderInterval(it) },
                            onTriggerTestAlert = { viewModel.triggerTestNotification() }
                        )
                    }
                }
            }
        }
    }

    // Custom ML Intake Dialog
    if (showCustomDialog) {
        CustomAddDialog(
            onDismiss = { showCustomDialog = false },
            onAdd = { customAmount ->
                viewModel.logWater(customAmount)
                showCustomDialog = false
                Toast.makeText(context, "Logged ${customAmount}ml!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun CleanHeader(
    selectedDate: Long,
    onConfigureGoal: () -> Unit
) {
    val dateLabel = remember(selectedDate) {
        val cal = Calendar.getInstance().apply { setTimeRangeToDefault() }
        val checkCal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        when {
            cal.get(Calendar.YEAR) == checkCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == checkCal.get(Calendar.DAY_OF_YEAR) -> "Today"
            cal.apply { add(Calendar.DAY_OF_YEAR, -1) }.get(Calendar.DAY_OF_YEAR) == checkCal.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
            else -> {
                val sdf = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
                sdf.format(Date(selectedDate))
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = dateLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                ),
                color = Color(0xFF3F484B),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = "Hydrate",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF001F24)
            )
        }

        // Circular styling badge representing active goal settings trigger click from HTML
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFCCE8ED))
                .border(1.dp, Color(0xFFAFCBD0), CircleShape)
                .clickable { onConfigureGoal() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF006874)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Adjust Goal Settings",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun DashboardView(
    progress: Float,
    totalLogged: Int,
    waterGoal: Int,
    remindersEnabled: Boolean,
    reminderInterval: Int,
    startHour: Int,
    endHour: Int,
    latestLogTime: Long?,
    onToggleReminders: (Boolean) -> Unit,
    onLogWater: (Int) -> Unit,
    onShowCustomInput: () -> Unit,
    onConfigureSettings: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Circular gauge
        Spacer(modifier = Modifier.height(8.dp))
        WaterWaveProgress(
            percentage = progress,
            totalLogged = totalLogged,
            waterGoal = waterGoal,
            modifier = Modifier.testTag("water_progress_gauge")
        )

        // Asymmetric Stats Double-pill Row from HTML layout specs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Level Card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFFCCE8ED))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LEVEL",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = Color(0xFF001F24).copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = Color(0xFF001F24),
                    fontWeight = FontWeight.Bold
                )
            }

            // Last logged cup time indicator card
            val lastDrinkText = remember(latestLogTime) {
                if (latestLogTime != null) {
                    val sdf = SimpleDateFormat("H:mm", Locale.getDefault())
                    sdf.format(Date(latestLogTime))
                } else "--:--"
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFFD1E5F3))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LAST DRINK",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = Color(0xFF001F24).copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = lastDrinkText,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = Color(0xFF001F24),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Smart reminder configuration widget holding precise style of HTML block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(32.dp), clip = true)
                .background(Color.White)
                .border(1.dp, Color(0xFFE1E3E4), RoundedCornerShape(32.dp))
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Styled Icon container: w-10 h-10 bg-[#006874]
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF006874)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                        
                        Column {
                            Text(
                                text = "Next Reminder",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF191C1E)
                            )
                            Text(
                                text = if (remindersEnabled) "Stay on track" else "Reminders off",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF3F484B)
                            )
                        }
                    }

                    val nextTime = getNextReminderTimeStr(remindersEnabled, reminderInterval, startHour, endHour, latestLogTime)
                    Text(
                        text = nextTime,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF006874)
                    )
                }

                // Inner sub-bar background [#F0F4F7] containing active toggler
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF0F4F7))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Smart Reminders",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = { onToggleReminders(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF006874),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFDCE4E9)
                        ),
                        modifier = Modifier.testTag("reminder_switch")
                    )
                }
                
                if (remindersEnabled) {
                    Button(
                        onClick = onConfigureSettings,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDCE4E9),
                            contentColor = Color(0xFF191C1E)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 6.dp))
                        Text(text = "Configure Schedule", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Tactile bottom-level Quick Add Controls matching HTML footer-style action flow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // First option: + 250ml pill button
            Button(
                onClick = {
                    onLogWater(250)
                    Toast.makeText(context, "Added 250ml!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(56.dp)
                    .testTag("add_250_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDCE4E9),
                    contentColor = Color(0xFF191C1E)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "+ 250ml",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Second custom addition overlay button
            Button(
                onClick = onShowCustomInput,
                modifier = Modifier
                    .size(width = 96.dp, height = 56.dp)
                    .testTag("add_custom_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF006874),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Log custom amount of water",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun HistoryView(
    selectedDate: Long,
    logs: List<WaterLog>,
    totalLogged: Int,
    waterGoal: Int,
    onDateSelected: (Long) -> Unit,
    onDeleteLog: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Horizontal calendar time limits
        CalendarPeriodSelector(
            selectedDate = selectedDate,
            onDateSelected = onDateSelected
        )

        // Compact total summarized block
        val remaining = (waterGoal - totalLogged).coerceAtLeast(0)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFF1F4F5))
                .border(1.dp, Color(0xFFE1E3E4), RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Intake",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F484B)
                    )
                    Text(
                        text = "${totalLogged} ml",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF006874)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (remaining > 0) "Remaining" else "Clear Goal Met!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F484B)
                    )
                    Text(
                        text = if (remaining > 0) "${remaining} ml" else "🎉 Completed",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (remaining > 0) Color(0xFF3F484B) else Color(0xFF4CAF50)
                    )
                }
            }
        }

        // Scrollable logs list
        Text(
            text = "Logged History Records",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF191C1E)
        )

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocalCafe,
                        contentDescription = null,
                        tint = Color(0xFF3F484B).copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "No intake recorded for this day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F484B)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogItemCard(
                        log = log,
                        onDelete = { onDeleteLog(log.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsView(
    waterGoal: Int,
    reminderInterval: Int,
    startHour: Int,
    endHour: Int,
    remindersEnabled: Boolean,
    onGoalChange: (Int) -> Unit,
    onReminderHoursChange: (Int, Int) -> Unit,
    onReminderIntervalChange: (Int) -> Unit,
    onTriggerTestAlert: () -> Unit
) {
    var localGoal by remember { mutableFloatStateOf(waterGoal.toFloat()) }
    var localInterval by remember { mutableFloatStateOf(reminderInterval.toFloat()) }
    var localStart by remember { mutableFloatStateOf(startHour.toFloat()) }
    var localEnd by remember { mutableFloatStateOf(endHour.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Goal Config Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E3E4))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Daily Target Water Goal",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF191C1E)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${localGoal.toInt()} ml",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                        color = Color(0xFF006874)
                    )
                    Text(
                        text = "Suggested 2,000ml",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F484B)
                    )
                }

                Slider(
                    value = localGoal,
                    onValueChange = { localGoal = it },
                    valueRange = 1000f..4000f,
                    steps = 29,
                    onValueChangeFinished = { onGoalChange(localGoal.toInt()) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF006874),
                        activeTrackColor = Color(0xFF006874),
                        inactiveTrackColor = Color(0xFFDCE4E9)
                    )
                )
            }
        }

        // Reminders intervals cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E3E4))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Smart Frequency Planner",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF191C1E)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Interval: ${formatInterval(localInterval.toInt())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F484B)
                    )
                    Slider(
                        value = localInterval,
                        onValueChange = { localInterval = it },
                        valueRange = 15f..180f,
                        steps = 10,
                        onValueChangeFinished = { onReminderIntervalChange(localInterval.toInt()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF006874),
                            activeTrackColor = Color(0xFF006874)
                        )
                    )
                }

                HorizontalDivider(color = Color(0xFFE1E3E4).copy(alpha = 0.5f))

                // Schedule wake limits boundaries
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Start Morning Hour: ${localStart.toInt()}:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F484B)
                    )
                    Slider(
                        value = localStart,
                        onValueChange = { localStart = it.coerceAtMost(localEnd - 1f) },
                        valueRange = 5f..12f,
                        steps = 7,
                        onValueChangeFinished = { onReminderHoursChange(localStart.toInt(), localEnd.toInt()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF006874),
                            activeTrackColor = Color(0xFF006874)
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Bed Night Sleep Hour: ${localEnd.toInt()}:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F484B)
                    )
                    Slider(
                        value = localEnd,
                        onValueChange = { localEnd = it.coerceAtLeast(localStart + 1f) },
                        valueRange = 18f..23f,
                        steps = 5,
                        onValueChangeFinished = { onReminderHoursChange(localStart.toInt(), localEnd.toInt()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF006874),
                            activeTrackColor = Color(0xFF006874)
                        )
                    )
                }
            }
        }

        // Test Reminders instant broadcast
        if (remindersEnabled) {
            Button(
                onClick = onTriggerTestAlert,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("test_alert_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF006874),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Trigger Test Reminder Now",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun CleanBottomNavigation(
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color(0xFFF1F4F5),
        border = BorderStroke(1.dp, Color(0xFFE1E3E4))
    ) {
        Row(
            modifier = Modifier
                .height(80.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple(0, "Dashboard", Icons.Default.Dashboard),
                Triple(1, "History", Icons.Default.History),
                Triple(2, "Settings", Icons.Default.Settings)
            )
            
            tabs.forEach { (tabIndex, label, icon) ->
                val isSelected = currentTab == tabIndex
                val contentColor = if (isSelected) Color(0xFF006874) else Color(0xFF3F484B)
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tabIndex) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF006874))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = contentColor,
                        modifier = Modifier
                            .size(24.dp)
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(
                                            Color(0xFF006874).copy(alpha = 0.12f),
                                            CircleShape
                                        )
                                        .padding(2.dp)
                                } else Modifier
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarPeriodSelector(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit
) {
    val today = Calendar.getInstance().apply { setTimeRangeToDefault() }.timeInMillis
    val yesterday = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
        setTimeRangeToDefault()
    }.timeInMillis
    val dayBefore = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -2)
        setTimeRangeToDefault()
    }.timeInMillis

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF1F4F5))
            .border(1.dp, Color(0xFFE1E3E4), RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val days = listOf<Triple<Long, String, ImageVector>>(
            Triple(dayBefore, "2 Days Ago", Icons.Default.CalendarToday),
            Triple(yesterday, "Yesterday", Icons.Default.History),
            Triple(today, "Today", Icons.Default.Today)
        )

        days.forEach { (time, label, icon) ->
            val isSelected = isSameDay(selectedDate, time)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFF006874) else Color.Transparent)
                    .clickable { onDateSelected(time) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Color(0xFF3F484B),
                        modifier = Modifier.size(14.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) Color.White else Color(0xFF3F484B)
                    )
                }
            }
        }
    }
}

private fun Calendar.setTimeRangeToDefault() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun WaterWaveProgress(
    percentage: Float,
    totalLogged: Int = 1300,
    waterGoal: Int = 2000,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "water_wave")

    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_1_phase"
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -(2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_2_phase"
    )

    val waterColor = Color(0xFF006874)
    val backdropWaterColor = Color(0x33CCE8ED)
    val ringColor = Color(0xFFDCE4E9) // CleanTrack
    val innerCanvasColor = Color(0xFFF8FDFF)

    Box(
        modifier = modifier
            .size(240.dp)
            .shadow(2.dp, CircleShape)
            .background(innerCanvasColor, CircleShape)
            .border(12.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val circlePath = Path().apply {
                addOval(Rect(Offset.Zero, size))
            }

            // Clip the wavy flows inside the central circle bounds
            clipPath(circlePath) {
                // Target water Y level based on daily percentage reached
                val cappedProgress = percentage.coerceIn(0f, 1f)
                val targetWaterY = height * (1f - cappedProgress)

                // Background secondary wave (slower)
                val wavePath2 = Path().apply {
                    moveTo(0f, height)
                    for (x in 0..width.toInt()) {
                        val radians = (x / width) * (2 * Math.PI) * 1.1 + wavePhase2
                        val y = targetWaterY + 10.dp.toPx() * kotlin.math.sin(radians).toFloat()
                        lineTo(x.toFloat(), y)
                    }
                    lineTo(width, height)
                    close()
                }
                drawPath(wavePath2, color = backdropWaterColor)

                // Foreground primary wave (faster)
                val wavePath1 = Path().apply {
                    moveTo(0f, height)
                    for (x in 0..width.toInt()) {
                        val radians = (x / width) * (2 * Math.PI) * 1.4 + wavePhase1
                        val y = targetWaterY + 6.dp.toPx() * kotlin.math.sin(radians).toFloat()
                        lineTo(x.toFloat(), y)
                    }
                    lineTo(width, height)
                    close()
                }
                drawPath(wavePath1, color = waterColor)
            }

            // Overlay high-contrast active progress track borders on the outer edge (arc starting at -90 degrees)
            val outerStrokeWidth = 12.dp.toPx()
            val strokeOffset = outerStrokeWidth / 2f
            drawArc(
                color = Color(0xFF006874),
                startAngle = -90f,
                sweepAngle = (percentage.coerceIn(0f, 1f) * 360f),
                useCenter = false,
                topLeft = Offset(strokeOffset, strokeOffset),
                size = Size(width - outerStrokeWidth, height - outerStrokeWidth),
                style = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round)
            )
        }

        // Hydration percentage text overlay styling
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val displayPct = (percentage * 100).toInt()
            Text(
                text = "${totalLogged}",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.5).sp
                ),
                color = if (percentage > 0.45f) Color.White else Color(0xFF001F24)
            )
            Text(
                text = "of ${waterGoal} ml",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (percentage > 0.45f) Color.White.copy(alpha = 0.8f) else Color(0xFF3F484B)
            )
        }
    }
}

@Composable
fun LogItemCard(
    log: WaterLog,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(0.5.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE1E3E4))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFCCE8ED), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = Color(0xFF006874),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "${log.amountMl} ml",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF191C1E)
                    )
                    Text(
                        text = formatTime(log.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF3F484B)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_log_${log.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CustomAddDialog(
    onDismiss: () -> Unit,
    onAdd: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf("250") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E3E4))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Log Custom Intake",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF001F24)
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 4) {
                            textValue = input
                        }
                    },
                    label = { Text("Volume (ml)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("custom_ml_text_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF006874),
                        focusedLabelColor = Color(0xFF006874)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF3F484B))
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val ml = textValue.toIntOrNull() ?: 250
                            onAdd(ml)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_add_custom_save"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006874))
                    ) {
                        Text("Log Water")
                    }
                }
            }
        }
    }
}

private fun formatInterval(intervalMins: Int): String {
    return if (intervalMins >= 60) {
        val hrs = intervalMins / 60f
        if (hrs == 1.0f) "1 Hour" else "${"%.1f".format(hrs).removeSuffix(".0")} Hours"
    } else {
        "$intervalMins Minutes"
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getNextReminderTimeStr(
    enabled: Boolean,
    intervalMins: Int,
    startHour: Int,
    endHour: Int,
    latestLogTime: Long?
): String {
    if (!enabled) return "--:--"
    
    val now = Calendar.getInstance()
    // Base next reminder off of either the latest log today, or current time
    val baseTime = if (latestLogTime != null && latestLogTime > (System.currentTimeMillis() - 4 * 3600 * 1000L)) {
        latestLogTime
    } else {
        System.currentTimeMillis()
    }
    
    val nextCal = Calendar.getInstance().apply {
        timeInMillis = baseTime + intervalMins * 60 * 1000L
    }
    
    val hour = nextCal.get(Calendar.HOUR_OF_DAY)
    if (hour >= endHour || hour < startHour) {
        return String.format(Locale.getDefault(), "%02d:00", startHour)
    }
    val minutes = nextCal.get(Calendar.MINUTE)
    return String.format(Locale.getDefault(), "%02d:%02d", hour, minutes)
}
