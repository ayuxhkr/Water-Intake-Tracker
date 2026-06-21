package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.WaterDatabase
import com.example.data.WaterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.example.watertrack.ACTION_TRIGGER_REMINDER"
        const val ACTION_QUICK_LOG_WATER = "com.example.watertrack.ACTION_QUICK_LOG_WATER"
        
        const val PREFS_NAME = "WaterTrackerPrefs"
        const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        const val KEY_REMINDER_INTERVAL = "reminder_interval_mins"
        const val KEY_START_HOUR = "reminder_start_hour"
        const val KEY_END_HOUR = "reminder_end_hour"
        
        const val NOTIFICATION_ID = 4096
        const val CHANNEL_ID = "hydration_reminders_channel"

        private val HYDRATION_TIPS = listOf(
            "Time to hydrate! 💧 Take a quick sip of water now.",
            "Stay fresh! 🌊 A glass of water is waiting for you.",
            "Your body needs some H2O! 💧 Keep up the active tracking!",
            "Hydration check! 🐳 Drink a cup of water to keep your energy high!",
            "Healthy minds drink water! 🧠💧 Take a break and drink some water.",
            "Dehydrated? 🥵 Just a few sips can boost your energy and focus instantly!",
            "Water is life! 🌱 Give your body the hydration it deserves."
        )

        fun scheduleNextAlarm(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_REMINDERS_ENABLED, false)
            if (!enabled) {
                cancelAlarm(context)
                return
            }

            val intervalMins = prefs.getInt(KEY_REMINDER_INTERVAL, 60).coerceAtLeast(1)
            val startHour = prefs.getInt(KEY_START_HOUR, 8)
            val endHour = prefs.getInt(KEY_END_HOUR, 22)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_TRIGGER_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val now = System.currentTimeMillis()
            val nextCal = Calendar.getInstance()
            nextCal.timeInMillis = now + intervalMins * 60 * 1000L

            val targetHour = nextCal.get(Calendar.HOUR_OF_DAY)
            val finalTriggerTime: Long

            if (targetHour >= endHour || targetHour < startHour) {
                // Schedule for morning startHour
                val morningCal = Calendar.getInstance().apply {
                    if (get(Calendar.HOUR_OF_DAY) >= endHour) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                    set(Calendar.HOUR_OF_DAY, startHour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (morningCal.timeInMillis <= now) {
                    morningCal.add(Calendar.DAY_OF_YEAR, 1)
                }
                finalTriggerTime = morningCal.timeInMillis
                Log.d("ReminderReceiver", "Scheduling alarm for tomorrow morning: ${morningCal.time}")
            } else {
                finalTriggerTime = nextCal.timeInMillis
                Log.d("ReminderReceiver", "Scheduling alarm for next interval: ${nextCal.time}")
            }

            try {
                // setAndAllowWhileIdle ensures it triggers in standby / battery saver modes
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    finalTriggerTime,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                Log.e("ReminderReceiver", "Cannot schedule alarm due to security restrictions", e)
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_TRIGGER_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d("ReminderReceiver", "Reminders calendar alarm cancelled.")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ReminderReceiver", "onReceive triggered action: ${intent.action}")
        
        if (intent.action == ACTION_QUICK_LOG_WATER) {
            // Dismiss notification immediately
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)

            // Trigger log write in database asynchronously
            val pendingResult = goAsync()
            val database = WaterDatabase.getDatabase(context)
            val repository = WaterRepository(database.waterDao())
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Log water (e.g. 250ml for quick cup add)
                    repository.insertLog(250)
                    Log.d("ReminderReceiver", "Logged 250ml via notification button clicked.")
                } finally {
                    // Reschedule next alarm
                    scheduleNextAlarm(context)
                    pendingResult.finish()
                }
            }
            return
        }

        if (intent.action == ACTION_TRIGGER_REMINDER || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_REMINDERS_ENABLED, false)
            if (!enabled) return

            if (intent.action == ACTION_TRIGGER_REMINDER) {
                val startHour = prefs.getInt(KEY_START_HOUR, 8)
                val endHour = prefs.getInt(KEY_END_HOUR, 22)
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                // Only trigger if within awake window as secondary protection
                if (currentHour in startHour until endHour) {
                    sendNotification(context)
                }
            }

            // Always chain schedule the next alarm so the system queue stays active
            scheduleNextAlarm(context)
        }
    }

    private fun sendNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Setup channels in newer Android levels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action when notification clicked -> Open MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Log 250ml" quick action button inside notification
        val quickLogIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_QUICK_LOG_WATER
        }
        val quickLogPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            quickLogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val tipText = HYDRATION_TIPS.random()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System info icon fallback, gets customized inside Android adaptive setup
            .setContentTitle("Stay Hydrated! 💦")
            .setContentText(tipText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .apply {
                // Add quick action button
                addAction(
                    android.R.drawable.ic_menu_add,
                    "Log Drink (+250ml)",
                    quickLogPendingIntent
                )
            }
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d("ReminderReceiver", "Notification posted successfully!")
        } catch (e: SecurityException) {
            Log.e("ReminderReceiver", "Failed to send notification: missing POST_NOTIFICATIONS permission", e)
        }
    }
}
