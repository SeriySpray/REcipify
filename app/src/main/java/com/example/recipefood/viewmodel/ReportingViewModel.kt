package com.example.recipefood.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.recipefood.api.GroqAIService
import com.example.recipefood.data.RecipeDatabase
import com.example.recipefood.data.MealRepository
import com.example.recipefood.model.NutritionInfo
import com.example.recipefood.model.SavedMeal
import com.example.recipefood.model.UserSettings
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.*

class ReportingViewModel(application: Application) : AndroidViewModel(application) {

    private val database = RecipeDatabase.getDatabase(application)
    private val repository = MealRepository(database.mealDao())
    private val settingsDao = database.userSettingsDao()
    private val groqService = GroqAIService()

    private val _weeklyAverages = MutableLiveData<NutritionInfo>()
    val weeklyAverages: LiveData<NutritionInfo> = _weeklyAverages

    private val _monthlyAverages = MutableLiveData<NutritionInfo>()
    val monthlyAverages: LiveData<NutritionInfo> = _monthlyAverages

    private val _dailyStats = MutableLiveData<NutritionInfo>()
    val dailyStats: LiveData<NutritionInfo> = _dailyStats

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _aiTips = MutableLiveData<String>()
    val aiTips: LiveData<String> = _aiTips

    init {
        loadStatistics()
    }

    fun loadStatistics() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val allMeals = repository.getAllMeals()
                    .catch { e -> 
                        Log.e("ReportingViewModel", "Error fetching meals", e)
                        emit(emptyList()) 
                    }
                    .firstOrNull() ?: emptyList()
                
                val daily = calculateAverages(allMeals, 1)
                val weekly = calculateAverages(allMeals, 7)
                val monthly = calculateAverages(allMeals, 30)
                
                _dailyStats.value = daily
                _weeklyAverages.value = weekly
                _monthlyAverages.value = monthly
                
                val settings = settingsDao.getUserSettingsSync() ?: UserSettings()
                val targetInfo = NutritionInfo(
                    settings.targetCalories,
                    settings.targetProteins,
                    settings.targetFats,
                    settings.targetCarbs
                )
                
                checkAndGenerateTips(allMeals.size, daily, targetInfo, settings)
            } catch (e: Exception) {
                Log.e("ReportingViewModel", "Critical error in loadStatistics", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun checkAndGenerateTips(
        currentMealCount: Int, 
        today: NutritionInfo,
        targets: NutritionInfo,
        settings: UserSettings
    ) {
        // Ми кешуємо поради на основі загальної кількості страв, 
        // щоб оновлювати їх при додаванні нових записів.
        if (settings.lastAiTip != null && settings.lastAiTipMealCount == currentMealCount) {
            _aiTips.value = settings.lastAiTip
            Log.d("ReportingViewModel", "Using cached AI tips")
        } else {
            _aiTips.value = "Аналізуємо ваш сьогоднішній прогрес..."
            // Викликаємо оновлену версію з двома параметрами
            val newTips = groqService.getNutritionTips(today, targets) 
            _aiTips.value = newTips
            
            val updatedSettings = settings.copy(
                lastAiTip = newTips,
                lastAiTipMealCount = currentMealCount
            )
            settingsDao.insertSettings(updatedSettings)
            Log.d("ReportingViewModel", "Generated and cached new AI tips for today")
        }
    }

    private fun calculateAverages(meals: List<SavedMeal>, days: Int): NutritionInfo {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        // Для 1 дня беремо тільки сьогоднішні страви (від 00:00 сьогодні).
        // Для інших періодів (тиждень/місяць) беремо відповідну кількість днів, включаючи сьогодні.
        val cutoffDate = if (days == 1) {
            calendar.timeInMillis
        } else {
            calendar.timeInMillis - ((days - 1).toLong() * 24 * 60 * 60 * 1000)
        }
        
        val filteredMeals = meals.filter { it.date >= cutoffDate }
        
        if (filteredMeals.isEmpty()) {
            return NutritionInfo(0.0, 0.0, 0.0, 0.0)
        }
        
        val totalCalories = filteredMeals.sumOf { it.totalCalories }
        val totalProteins = filteredMeals.sumOf { it.totalProteins }
        val totalFats = filteredMeals.sumOf { it.totalFats }
        val totalCarbs = filteredMeals.sumOf { it.totalCarbs }
        
        return NutritionInfo(
            totalCalories / days,
            totalProteins / days,
            totalFats / days,
            totalCarbs / days
        )
    }
}
