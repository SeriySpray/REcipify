package com.example.recipefood.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.recipefood.R
import com.example.recipefood.data.RecipeDatabase
import com.example.recipefood.data.UserSettingsRepository
import com.example.recipefood.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: UserSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = RecipeDatabase.getDatabase(this)
        repository = UserSettingsRepository(database.userSettingsDao())

        setupToolbar()
        setupRangePreview()
        setupThemeSwitcher()
        loadSettings()

        binding.btnSaveSettings.setOnClickListener { saveSettings() }
        binding.btnOpenSurvey.setOnClickListener {
            val intent = android.content.Intent(this, SurveyActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupToolbar() {
        try {
            binding.btnBackLink.setOnClickListener { finish() }
        } catch (_: Exception) {}
    }

    private fun setupRangePreview() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etTargetCalories.addTextChangedListener(watcher)
    }

    private fun setupThemeSwitcher() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("theme_dark", true)
        updateThemeButtons(isDark)

        binding.btnThemeDark.setOnClickListener { applyTheme(dark = true) }
        binding.btnThemeLight.setOnClickListener { applyTheme(dark = false) }
    }

    private fun applyTheme(dark: Boolean) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentlyDark = prefs.getBoolean("theme_dark", true)
        if (currentlyDark == dark) return

        prefs.edit().putBoolean("theme_dark", dark).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun updateThemeButtons(isDark: Boolean) {
        val activeDrawable = ContextCompat.getDrawable(this, R.drawable.theme_btn_active)
        val activeTextColor = ContextCompat.getColor(this, R.color.button_text)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.text_muted)

        if (isDark) {
            binding.btnThemeDark.background = activeDrawable
            binding.btnThemeDark.setTextColor(activeTextColor)
            binding.btnThemeLight.background = null
            binding.btnThemeLight.setTextColor(inactiveTextColor)
        } else {
            binding.btnThemeLight.background = activeDrawable
            binding.btnThemeLight.setTextColor(activeTextColor)
            binding.btnThemeDark.background = null
            binding.btnThemeDark.setTextColor(inactiveTextColor)
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            repository.getUserSettings().collect { settings ->
                settings?.let {
                    if (it.targetCalories > 0) {
                        binding.etTargetCalories.setText(it.targetCalories.toInt().toString())
                    }
                    if (it.targetProteins > 0) binding.etTargetProteins.setText(it.targetProteins.toInt().toString())
                    if (it.targetFats > 0) binding.etTargetFats.setText(it.targetFats.toInt().toString())
                    if (it.targetCarbs > 0) binding.etTargetCarbs.setText(it.targetCarbs.toInt().toString())
                }
            }
        }
    }

    private fun saveSettings() {
        val target = binding.etTargetCalories.text.toString().toDoubleOrNull()
        if (target == null || target <= 0) {
            Toast.makeText(this, "Введіть ціль калорій", Toast.LENGTH_SHORT).show()
            return
        }
        val proteins = binding.etTargetProteins.text.toString().toDoubleOrNull() ?: 0.0
        val fats = binding.etTargetFats.text.toString().toDoubleOrNull() ?: 0.0
        val carbs = binding.etTargetCarbs.text.toString().toDoubleOrNull() ?: 0.0
        lifecycleScope.launch {
            repository.saveSettings(target, proteins, fats, carbs)
            Toast.makeText(this@SettingsActivity, "Збережено", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
