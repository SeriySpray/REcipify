package com.example.recipefood.ui.main

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.recipefood.R
import com.example.recipefood.data.RecipeDatabase
import com.example.recipefood.model.NutritionInfo
import com.example.recipefood.model.UserSettings
import com.example.recipefood.viewmodel.ReportingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportingActivity : AppCompatActivity() {

    private val viewModel: ReportingViewModel by viewModels()

    private lateinit var tvAiTips: TextView
    private lateinit var btnBack: ImageView
    
    private var userTargets: UserSettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reporting)

        initViews()
        loadTargetsAndSetupObservers()
    }

    private fun initViews() {
        tvAiTips = findViewById(R.id.tvAiTips)
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
    }

    private fun loadTargetsAndSetupObservers() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = RecipeDatabase.getDatabase(applicationContext)
            userTargets = db.userSettingsDao().getUserSettingsSync()
            
            withContext(Dispatchers.Main) {
                setupObservers()
            }
        }
    }

    private fun setupObservers() {
        viewModel.weeklyAverages.observe(this) { stats ->
            updateProgressGroup(findViewById(R.id.weeklyStats), stats)
        }

        viewModel.monthlyAverages.observe(this) { stats ->
            updateProgressGroup(findViewById(R.id.monthlyStats), stats)
        }

        viewModel.aiTips.observe(this) { tips ->
            tvAiTips.text = tips
        }
    }

    private fun updateProgressGroup(groupView: View, stats: NutritionInfo) {
        val targets = userTargets ?: UserSettings()
        
        // Calories
        val pbCalories = groupView.findViewById<ProgressBar>(R.id.pbCalories)
        val tvValueCalories = groupView.findViewById<TextView>(R.id.tvValueCalories)
        
        val calProgress = if (targets.targetCalories > 0) (stats.calories / targets.targetCalories * 100).toInt() else 0
        pbCalories.progress = calProgress.coerceAtMost(100)
        tvValueCalories.text = "${stats.calories.toInt()} / ${targets.targetCalories.toInt()} ккал"

        // Proteins
        val pbProteins = groupView.findViewById<ProgressBar>(R.id.pbProteins)
        val tvValueProteins = groupView.findViewById<TextView>(R.id.tvValueProteins)
        
        val protProgress = if (targets.targetProteins > 0) (stats.proteins / targets.targetProteins * 100).toInt() else 0
        pbProteins.progress = protProgress.coerceAtMost(100)
        tvValueProteins.text = "${stats.proteins.toInt()}г / ${targets.targetProteins.toInt()}г"

        // Fats
        val pbFats = groupView.findViewById<ProgressBar>(R.id.pbFats)
        val tvValueFats = groupView.findViewById<TextView>(R.id.tvValueFats)
        
        val fatsProgress = if (targets.targetFats > 0) (stats.fats / targets.targetFats * 100).toInt() else 0
        pbFats.progress = fatsProgress.coerceAtMost(100)
        tvValueFats.text = "${stats.fats.toInt()}г / ${targets.targetFats.toInt()}г"

        // Carbs
        val pbCarbs = groupView.findViewById<ProgressBar>(R.id.pbCarbs)
        val tvValueCarbs = groupView.findViewById<TextView>(R.id.tvValueCarbs)
        
        val carbsProgress = if (targets.targetCarbs > 0) (stats.carbs / targets.targetCarbs * 100).toInt() else 0
        pbCarbs.progress = carbsProgress.coerceAtMost(100)
        tvValueCarbs.text = "${stats.carbs.toInt()}г / ${targets.targetCarbs.toInt()}г"
    }
}
