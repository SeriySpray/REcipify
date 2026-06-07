package com.example.recipefood.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.recipefood.R
import com.example.recipefood.data.RecipeDatabase
import com.example.recipefood.data.UserSettingsRepository
import com.example.recipefood.databinding.ActivitySurveyBinding
import kotlinx.coroutines.launch

class SurveyActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySurveyBinding
    private lateinit var repository: UserSettingsRepository
    private var currentStep = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = RecipeDatabase.getDatabase(this)
        repository = UserSettingsRepository(database.userSettingsDao())

        setupListeners()
        updateStepVisibility()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnNext.setOnClickListener {
            if (currentStep < 5) {
                if (validateCurrentStep()) {
                    currentStep++
                    updateStepVisibility()
                }
            } else {
                saveSurveyAndFinish()
            }
        }

        binding.btnPrev.setOnClickListener {
            if (currentStep > 1) {
                currentStep--
                updateStepVisibility()
            }
        }
    }

    private fun validateCurrentStep(): Boolean {
        return when (currentStep) {
            1 -> {
                val age = binding.etAge.text.toString().toIntOrNull()
                if (age == null || age !in 10..120) {
                    Toast.makeText(this, "Будь ласка, введіть коректний вік", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            2 -> {
                val height = binding.etHeight.text.toString().toDoubleOrNull()
                val weight = binding.etWeight.text.toString().toDoubleOrNull()
                if (height == null || height !in 100.0..250.0 || weight == null || weight !in 30.0..300.0) {
                    Toast.makeText(this, "Будь ласка, введіть коректний зріст та вагу", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            else -> true
        }
    }

    private fun updateStepVisibility() {
        binding.step1.visibility = if (currentStep == 1) View.VISIBLE else View.GONE
        binding.step2.visibility = if (currentStep == 2) View.VISIBLE else View.GONE
        binding.step3.visibility = if (currentStep == 3) View.VISIBLE else View.GONE
        binding.step4.visibility = if (currentStep == 4) View.VISIBLE else View.GONE
        binding.step5.visibility = if (currentStep == 5) View.VISIBLE else View.GONE

        updateProgressIndicators()
        binding.btnPrev.visibility = if (currentStep > 1) View.VISIBLE else View.INVISIBLE
        binding.btnNext.text = if (currentStep == 5) "Завершити" else "Далі"
    }

    private fun updateProgressIndicators() {
        binding.step1Indicator.setBackgroundResource(
            if (currentStep >= 1) R.drawable.survey_progress_active else R.drawable.survey_progress_inactive
        )
        binding.step2Indicator.setBackgroundResource(
            if (currentStep >= 2) R.drawable.survey_progress_active else R.drawable.survey_progress_inactive
        )
        binding.step3Indicator.setBackgroundResource(
            if (currentStep >= 3) R.drawable.survey_progress_active else R.drawable.survey_progress_inactive
        )
        binding.step4Indicator.setBackgroundResource(
            if (currentStep >= 4) R.drawable.survey_progress_active else R.drawable.survey_progress_inactive
        )
        binding.step5Indicator.setBackgroundResource(
            if (currentStep >= 5) R.drawable.survey_progress_active else R.drawable.survey_progress_inactive
        )
    }

    private fun saveSurveyAndFinish() {
        val age = binding.etAge.text.toString().toIntOrNull() ?: 0
        val height = binding.etHeight.text.toString().toDoubleOrNull() ?: 0.0
        val weight = binding.etWeight.text.toString().toDoubleOrNull() ?: 0.0
        val gender = if (binding.rbMale.isChecked) "male" else "female"
        
        val activityLevel = when (binding.rgActivity.checkedRadioButtonId) {
            R.id.rbSedentary -> "sedentary"
            R.id.rbLight -> "light"
            R.id.rbModerate -> "moderate"
            R.id.rbActive -> "active"
            R.id.rbExtra -> "extra"
            else -> "sedentary"
        }

        val goal = when (binding.rgGoal.checkedRadioButtonId) {
            R.id.rbLose -> "lose"
            R.id.rbMaintain -> "maintain"
            R.id.rbGain -> "gain"
            else -> "maintain"
        }

        val allergens = binding.etAllergens.text.toString()

        // Mifflin-St Jeor Formula
        val bmr = if (gender == "male") {
            10 * weight + 6.25 * height - 5 * age + 5
        } else {
            10 * weight + 6.25 * height - 5 * age - 161
        }

        val activityMultiplier = when (activityLevel) {
            "sedentary" -> 1.2
            "light" -> 1.375
            "moderate" -> 1.55
            "active" -> 1.725
            "extra" -> 1.9
            else -> 1.2
        }

        var tdee = bmr * activityMultiplier

        // Adjust based on goal
        tdee = when (goal) {
            "lose" -> tdee * 0.8 // 20% deficit
            "gain" -> tdee * 1.1 // 10% surplus
            else -> tdee
        }

        // Standard protein ratios: 1.8g per kg for maintain/gain, 2.0g for lose
        val pRatio = if (goal == "lose") 2.0 else 1.8
        val proteinsRaw = weight * pRatio
        
        // New Fat Calculation Formula:
        // 1g per kg for maintain/gain, 0.8g per kg for lose
        val fRatio = if (goal == "lose") 0.8 else 1.0
        val fatsRaw = weight * fRatio
        
        // Carbs: rest of the calories (TDEE - proteins*4 - fats*9)
        val carbsRaw = (tdee - (proteinsRaw * 4) - (fatsRaw * 9)) / 4

        // Round to nearest 5 for "cleaner" numbers
        val roundedCalories = roundTo5(tdee)
        val roundedProteins = roundTo5(proteinsRaw)
        val roundedFats = roundTo5(fatsRaw)
        val roundedCarbs = roundTo5(carbsRaw)

        lifecycleScope.launch {
            repository.updateSurveyData(
                age = age,
                weight = weight,
                height = height,
                gender = gender,
                activityLevel = activityLevel,
                goal = goal,
                allergens = allergens,
                targetCalories = roundedCalories,
                targetProteins = roundedProteins,
                targetFats = roundedFats,
                targetCarbs = roundedCarbs
            )
            Toast.makeText(this@SurveyActivity, "Норми розраховані!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun roundTo5(value: Double): Double {
        return (Math.round(value / 5.0) * 5).toDouble()
    }
}
