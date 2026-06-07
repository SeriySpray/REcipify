package com.example.recipefood.data

import com.example.recipefood.model.UserSettings
import kotlinx.coroutines.flow.Flow

class UserSettingsRepository(private val userSettingsDao: UserSettingsDao) {

    fun getUserSettings(): Flow<UserSettings?> = userSettingsDao.getUserSettings()

    suspend fun getUserSettingsSync(): UserSettings? = userSettingsDao.getUserSettingsSync()

    suspend fun saveSettings(
        targetCalories: Double,
        targetProteins: Double = 0.0,
        targetFats: Double = 0.0,
        targetCarbs: Double = 0.0
    ) {
        val current = userSettingsDao.getUserSettingsSync()
        val settings = current?.copy(
            targetCalories = targetCalories,
            targetProteins = targetProteins,
            targetFats = targetFats,
            targetCarbs = targetCarbs
        ) ?: UserSettings(
            id = 1,
            targetCalories = targetCalories,
            targetProteins = targetProteins,
            targetFats = targetFats,
            targetCarbs = targetCarbs
        )
        userSettingsDao.insertSettings(settings)
    }

    suspend fun updateSurveyData(
        age: Int,
        weight: Double,
        height: Double,
        gender: String,
        activityLevel: String,
        goal: String,
        allergens: String,
        targetCalories: Double,
        targetProteins: Double,
        targetFats: Double,
        targetCarbs: Double
    ) {
        val current = userSettingsDao.getUserSettingsSync()
        val settings = (current ?: UserSettings(id = 1)).copy(
            age = age,
            weight = weight,
            height = height,
            gender = gender,
            activityLevel = activityLevel,
            goal = goal,
            allergens = allergens,
            targetCalories = targetCalories,
            targetProteins = targetProteins,
            targetFats = targetFats,
            targetCarbs = targetCarbs
        )
        userSettingsDao.insertSettings(settings)
    }

    suspend fun updateStreak(streak: Int, lastDate: Long) {
        val currentSettings = userSettingsDao.getUserSettingsSync() ?: return
        val updatedSettings = currentSettings.copy(
            currentStreak = streak,
            lastStreakDate = lastDate
        )
        userSettingsDao.updateSettings(updatedSettings)
    }
}
