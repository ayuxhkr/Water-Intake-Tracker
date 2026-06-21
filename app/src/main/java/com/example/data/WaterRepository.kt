package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class WaterRepository(private val waterDao: WaterDao) {

    fun getLogsForDay(dayTimestamp: Long): Flow<List<WaterLog>> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dayTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endTime = calendar.timeInMillis

        return waterDao.getLogsInTimeRange(startTime, endTime)
    }

    fun getAllLogs(): Flow<List<WaterLog>> = waterDao.getAllLogs()

    suspend fun insertLog(amountMl: Int) {
        waterDao.insertLog(WaterLog(amountMl = amountMl))
    }

    suspend fun insertLogAtTime(amountMl: Int, timestamp: Long) {
        waterDao.insertLog(WaterLog(amountMl = amountMl, timestamp = timestamp))
    }

    suspend fun deleteLogById(id: Int) {
        waterDao.deleteLogById(id)
    }

    suspend fun savePreference(key: String, value: String) {
        waterDao.insertPreference(AppPreference(key, value))
    }

    suspend fun getPreference(key: String): String? {
        return waterDao.getPreference(key)?.value
    }
}
