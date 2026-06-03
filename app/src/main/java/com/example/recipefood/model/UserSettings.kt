package com.example.recipefood.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey
    val id: Int = 1,
    val targetCalories: Double = 0.0,
    val targetProteins: Double = 0.0,
    val targetFats: Double = 0.0,
    val targetCarbs: Double = 0.0,
    val currentStreak: Int = 0,
    val lastStreakDate: Long = 0,
    val lastAiTip: String? = null,
    val lastAiTipMealCount: Int = 0
)
