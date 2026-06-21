package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_preferences")
data class AppPreference(
    @PrimaryKey val key: String,
    val value: String
)
