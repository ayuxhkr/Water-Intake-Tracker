package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.WaterDatabase
import com.example.data.WaterLog
import com.example.data.WaterRepository
import com.example.receiver.ReminderReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class WaterViewModel(
    application: Application,
    private val repository: WaterRepository
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences(
        ReminderReceiver.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // Current selected date for tracking (defaults to today)
    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate: StateFlow<Long> = _selectedDate.asStateFlow()

    // Retrieve today's logs reactively from database
    val todayLogs: StateFlow<List<WaterLog>> = _selectedDate
        .flatMapLatest { timestamp ->
            repository.getLogsForDay(timestamp)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Sum of logged water intake today
    val totalLoggedToday: StateFlow<Int> = todayLogs
        .map { logs -> logs.sumOf { it.amountMl } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // Current hydration target goal (ml)
    private val _waterGoal = MutableStateFlow(2000)
    val waterGoal: StateFlow<Int> = _waterGoal.asStateFlow()

    // Alarm preferences
    private val _remindersEnabled = MutableStateFlow(false)
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    private val _reminderInterval = MutableStateFlow(60)
    val reminderInterval: StateFlow<Int> = _reminderInterval.asStateFlow()

    private val _startHour = MutableStateFlow(8)
    val startHour: StateFlow<Int> = _startHour.asStateFlow()

    private val _endHour = MutableStateFlow(22)
    val endHour: StateFlow<Int> = _endHour.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            // Load goal from preference db, fallback to SharedPreferences or default 2000
            val savedGoalStr = repository.getPreference("daily_goal")
            val savedGoal = savedGoalStr?.toIntOrNull() ?: prefs.getInt("daily_goal_val", 2000)
            _waterGoal.value = savedGoal

            _remindersEnabled.value = prefs.getBoolean(ReminderReceiver.KEY_REMINDERS_ENABLED, false)
            _reminderInterval.value = prefs.getInt(ReminderReceiver.KEY_REMINDER_INTERVAL, 60)
            _startHour.value = prefs.getInt(ReminderReceiver.KEY_START_HOUR, 8)
            _endHour.value = prefs.getInt(ReminderReceiver.KEY_END_HOUR, 22)
        }
    }

    fun logWater(amountMl: Int) {
        viewModelScope.launch {
            repository.insertLog(amountMl)
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            repository.deleteLogById(id)
        }
    }

    fun setWaterGoal(goalMl: Int) {
        _waterGoal.value = goalMl
        viewModelScope.launch {
            repository.savePreference("daily_goal", goalMl.toString())
            prefs.edit().putInt("daily_goal_val", goalMl).apply()
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        _remindersEnabled.value = enabled
        prefs.edit().putBoolean(ReminderReceiver.KEY_REMINDERS_ENABLED, enabled).apply()
        updateAlarmService()
    }

    fun setReminderInterval(intervalMins: Int) {
        _reminderInterval.value = intervalMins
        prefs.edit().putInt(ReminderReceiver.KEY_REMINDER_INTERVAL, intervalMins).apply()
        if (_remindersEnabled.value) {
            updateAlarmService()
        }
    }

    fun setReminderHours(start: Int, end: Int) {
        _startHour.value = start
        _endHour.value = end
        prefs.edit()
            .putInt(ReminderReceiver.KEY_START_HOUR, start)
            .putInt(ReminderReceiver.KEY_END_HOUR, end)
            .apply()
        if (_remindersEnabled.value) {
            updateAlarmService()
        }
    }

    private fun updateAlarmService() {
        val app = getApplication<Application>()
        if (_remindersEnabled.value) {
            ReminderReceiver.scheduleNextAlarm(app)
            Log.d("WaterViewModel", "Reminders activated in AlarmManager")
        } else {
            ReminderReceiver.cancelAlarm(app)
            Log.d("WaterViewModel", "Reminders deactivated in AlarmManager")
        }
    }

    fun triggerTestNotification() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val intent = android.content.Intent(app, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_TRIGGER_REMINDER
            }
            app.sendBroadcast(intent)
            Log.d("WaterViewModel", "Test broadcast sent manually.")
        }
    }

    fun selectDate(timestamp: Long) {
        _selectedDate.value = timestamp
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = context.applicationContext as Application
            val db = WaterDatabase.getDatabase(app)
            val repository = WaterRepository(db.waterDao())
            return WaterViewModel(app, repository) as T
        }
    }
}
