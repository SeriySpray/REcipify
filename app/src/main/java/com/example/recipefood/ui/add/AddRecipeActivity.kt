package com.example.recipefood.ui.add

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.recipefood.R
import com.example.recipefood.RecipeFoodApp
import com.example.recipefood.model.Recipe
import com.example.recipefood.viewmodel.RecipeViewModel
import android.widget.EditText
import com.example.recipefood.ui.camera.CameraActivity
import android.content.Intent
import com.google.gson.Gson
import kotlinx.coroutines.launch

class AddRecipeActivity : AppCompatActivity() {

    private lateinit var viewModel: RecipeViewModel

    private lateinit var nameEditText: EditText
    private lateinit var ingredientsEditText: EditText
    private lateinit var timeEditText: EditText
    private lateinit var instructionsEditText: EditText
    private lateinit var easyRadioButton: RadioButton
    private lateinit var mediumRadioButton: RadioButton
    private lateinit var hardRadioButton: RadioButton
    private lateinit var saveButton: android.view.View
    private lateinit var cancelButton: android.view.View
    private lateinit var calculateNutritionButton: Button
    private lateinit var caloriesEditText: EditText
    private lateinit var proteinsEditText: EditText
    private lateinit var fatsEditText: EditText
    private lateinit var carbsEditText: EditText

    private var folderId: Long = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_recipe)

        folderId = intent.getLongExtra("FOLDER_ID", 1L)

        nameEditText = findViewById(R.id.nameEditText)
        ingredientsEditText = findViewById(R.id.ingredientsEditText)
        timeEditText = findViewById(R.id.timeEditText)
        instructionsEditText = findViewById(R.id.instructionsEditText)
        easyRadioButton = findViewById(R.id.easyRadioButton)
        mediumRadioButton = findViewById(R.id.mediumRadioButton)
        hardRadioButton = findViewById(R.id.hardRadioButton)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        calculateNutritionButton = findViewById(R.id.calculateNutritionButton)
        caloriesEditText = findViewById(R.id.caloriesEditText)
        proteinsEditText = findViewById(R.id.proteinsEditText)
        fatsEditText = findViewById(R.id.fatsEditText)
        carbsEditText = findViewById(R.id.carbsEditText)

        viewModel = ViewModelProvider(this)[RecipeViewModel::class.java]

        // Перевірка чи прийшов рецепт через Intent (наприклад, з головного екрану)
        intent.getStringExtra("recipe_json")?.let { json ->
            val recipe = Gson().fromJson(json, Recipe::class.java)
            fillRecipeFields(recipe)
        }

        saveButton.setOnClickListener { saveRecipe() }
        cancelButton.setOnClickListener { finish() }
        calculateNutritionButton.setOnClickListener { calculateNutrition() }
    }

    private fun fillRecipeFields(recipe: Recipe) {
        nameEditText.setText(recipe.name)
        ingredientsEditText.setText(recipe.ingredients.joinToString("\n"))
        timeEditText.setText(recipe.cookingTime.toString())
        instructionsEditText.setText(recipe.instructions)
        
        when (recipe.difficulty) {
            Recipe.DIFFICULTY_EASY -> easyRadioButton.isChecked = true
            Recipe.DIFFICULTY_MEDIUM -> mediumRadioButton.isChecked = true
            Recipe.DIFFICULTY_HARD -> hardRadioButton.isChecked = true
        }

        recipe.calories?.let { caloriesEditText.setText(it.toInt().toString()) }
        recipe.proteins?.let { proteinsEditText.setText(it.toInt().toString()) }
        recipe.fats?.let { fatsEditText.setText(it.toInt().toString()) }
        recipe.carbs?.let { carbsEditText.setText(it.toInt().toString()) }
        
        if (recipe.calories != null) {
            findViewById<android.view.View>(R.id.kbzuSection).visibility = android.view.View.VISIBLE
        }
    }

    private fun calculateNutrition() {
        val ingredientsText = ingredientsEditText.text.toString().trim()
        if (ingredientsText.isEmpty()) {
            Toast.makeText(this, R.string.nutrition_error_no_ingredients, Toast.LENGTH_SHORT).show()
            return
        }
        val name = nameEditText.text.toString().trim().ifEmpty { "рецепт" }
        val ingredients = ingredientsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val instructions = instructionsEditText.text.toString().trim()

        calculateNutritionButton.isEnabled = false
        calculateNutritionButton.text = getString(R.string.nutrition_calculating)

        val groqService = (application as RecipeFoodApp).groqService
        lifecycleScope.launch {
            try {
                val nutrition = groqService.analyzeRecipeNutrition(name, ingredients, instructions)
                caloriesEditText.setText(nutrition.calories.toInt().toString())
                proteinsEditText.setText(nutrition.proteins.toInt().toString())
                fatsEditText.setText(nutrition.fats.toInt().toString())
                carbsEditText.setText(nutrition.carbs.toInt().toString())
                findViewById<android.view.View>(R.id.kbzuSection).visibility = android.view.View.VISIBLE
                Toast.makeText(this@AddRecipeActivity, R.string.nutrition_calculated, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val message = when {
                    e.message?.contains("429") == true -> getString(R.string.groq_rate_limit)
                    e.message?.contains("401") == true -> getString(R.string.groq_invalid_key)
                    else -> getString(R.string.nutrition_error_network)
                }
                Toast.makeText(this@AddRecipeActivity, message, Toast.LENGTH_LONG).show()
            } finally {
                calculateNutritionButton.isEnabled = true
                calculateNutritionButton.text = getString(R.string.calculate_nutrition)
            }
        }
    }

    private fun saveRecipe() {
        val name = nameEditText.text.toString().trim()
        val ingredientsText = ingredientsEditText.text.toString().trim()
        val timeText = timeEditText.text.toString().trim()
        val instructions = instructionsEditText.text.toString().trim()

        if (name.isEmpty()) { Toast.makeText(this, R.string.error_empty_name, Toast.LENGTH_SHORT).show(); return }
        if (ingredientsText.isEmpty()) { Toast.makeText(this, R.string.error_empty_ingredients, Toast.LENGTH_SHORT).show(); return }
        if (timeText.isEmpty()) { Toast.makeText(this, R.string.error_invalid_time, Toast.LENGTH_SHORT).show(); return }
        if (instructions.isEmpty()) { Toast.makeText(this, R.string.error_empty_instructions, Toast.LENGTH_SHORT).show(); return }

        val cookingTime = timeText.toIntOrNull()
        if (cookingTime == null || cookingTime <= 0) {
            Toast.makeText(this, R.string.error_invalid_time, Toast.LENGTH_SHORT).show(); return
        }

        val ingredients = ingredientsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (ingredients.isEmpty()) { Toast.makeText(this, R.string.error_empty_ingredients, Toast.LENGTH_SHORT).show(); return }

        val difficulty = when {
            easyRadioButton.isChecked -> Recipe.DIFFICULTY_EASY
            mediumRadioButton.isChecked -> Recipe.DIFFICULTY_MEDIUM
            hardRadioButton.isChecked -> Recipe.DIFFICULTY_HARD
            else -> Recipe.DIFFICULTY_EASY
        }

        val recipe = Recipe(
            name = name,
            ingredients = ingredients,
            difficulty = difficulty,
            cookingTime = cookingTime,
            instructions = instructions,
            folderId = folderId,
            calories = caloriesEditText.text.toString().toDoubleOrNull(),
            proteins = proteinsEditText.text.toString().toDoubleOrNull(),
            fats = fatsEditText.text.toString().toDoubleOrNull(),
            carbs = carbsEditText.text.toString().toDoubleOrNull()
        )

        viewModel.insert(recipe)
        Toast.makeText(this, R.string.recipe_added, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
