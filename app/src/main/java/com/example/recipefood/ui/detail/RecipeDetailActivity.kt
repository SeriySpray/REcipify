package com.example.recipefood.ui.detail

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.recipefood.R
import com.example.recipefood.model.Recipe
import com.example.recipefood.ui.edit.EditRecipeActivity
import com.example.recipefood.viewmodel.RecipeViewModel
import android.graphics.Typeface
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlinx.coroutines.launch

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var viewModel: RecipeViewModel
    private var currentRecipe: Recipe? = null

    private lateinit var recipeNameTextView: TextView
    private lateinit var metaTextView: TextView
    private lateinit var cookingTimeTextView: TextView
    private lateinit var difficultyTextView: TextView
    private lateinit var ingredientsTextView: TextView
    private lateinit var instructionsTextView: TextView
    private lateinit var editButton: android.view.View
    private lateinit var deleteButton: android.view.View
    private lateinit var cookingCounterTextView: TextView
    private lateinit var incrementButton: TextView
    private lateinit var decrementButton: TextView
    private lateinit var caloriesDetailTextView: TextView
    private lateinit var proteinsDetailTextView: TextView
    private lateinit var fatsDetailTextView: TextView
    private lateinit var carbsDetailTextView: TextView
    private lateinit var nutritionPieChart: PieChart
    private lateinit var nutritionChartLayout: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)

        // Ініціалізація UI
        recipeNameTextView = findViewById(R.id.recipeNameTextView)
        metaTextView = findViewById(R.id.metaTextView)
        cookingTimeTextView = findViewById(R.id.cookingTimeTextView)
        difficultyTextView = findViewById(R.id.difficultyTextView)
        ingredientsTextView = findViewById(R.id.ingredientsTextView)
        instructionsTextView = findViewById(R.id.instructionsTextView)
        editButton = findViewById(R.id.editButton)
        deleteButton = findViewById(R.id.deleteButton)
        cookingCounterTextView = findViewById(R.id.cookingCounterTextView)
        incrementButton = findViewById(R.id.incrementButton)
        decrementButton = findViewById(R.id.decrementButton)
        caloriesDetailTextView = findViewById(R.id.caloriesTextView)
        proteinsDetailTextView = findViewById(R.id.proteinsTextView)
        fatsDetailTextView = findViewById(R.id.fatsTextView)
        carbsDetailTextView = findViewById(R.id.carbsTextView)
        nutritionPieChart = findViewById(R.id.nutritionPieChart)
        nutritionChartLayout = findViewById(R.id.nutritionChartLayout)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Ініціалізація ViewModel
        viewModel = ViewModelProvider(this)[RecipeViewModel::class.java]

        // Отримання ID рецепту
        val recipeId = intent.getLongExtra("RECIPE_ID", -1L)
        if (recipeId == -1L) {
            Toast.makeText(this, "Помилка завантаження рецепту", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Завантаження рецепту
        loadRecipe(recipeId)

        // Кнопка редагування
        editButton.setOnClickListener {
            currentRecipe?.let { recipe ->
                val intent = Intent(this, EditRecipeActivity::class.java)
                intent.putExtra("RECIPE_ID", recipe.id)
                startActivity(intent)
            }
        }

        // Кнопка видалення
        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Кнопка збільшення лічильника
        incrementButton.setOnClickListener {
            currentRecipe?.let { recipe ->
                val updatedRecipe = recipe.copy(
                    timesCooked = recipe.timesCooked + 1,
                    wasCooked = true
                )
                viewModel.update(updatedRecipe)
                currentRecipe = updatedRecipe
                updateCookingCounter(updatedRecipe)
            }
        }

        // Кнопка зменшення лічильника
        decrementButton.setOnClickListener {
            currentRecipe?.let { recipe ->
                if (recipe.timesCooked > 0) {
                    val newCount = recipe.timesCooked - 1
                    val updatedRecipe = recipe.copy(
                        timesCooked = newCount,
                        wasCooked = newCount > 0
                    )
                    viewModel.update(updatedRecipe)
                    currentRecipe = updatedRecipe
                    updateCookingCounter(updatedRecipe)
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        // Перезавантажуємо рецепт при поверненні (на випадок редагування)
        val recipeId = intent.getLongExtra("RECIPE_ID", -1L)
        if (recipeId != -1L) {
            loadRecipe(recipeId)
        }
    }

    private fun loadRecipe(recipeId: Long) {
        lifecycleScope.launch {
            val recipe = viewModel.getRecipeById(recipeId)
            if (recipe != null) {
                currentRecipe = recipe
                displayRecipe(recipe)
            } else {
                Toast.makeText(this@RecipeDetailActivity, "Рецепт не знайдено", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun displayRecipe(recipe: Recipe) {
        recipeNameTextView.text = recipe.name

        val difficultyColor = when (recipe.difficulty) {
            Recipe.DIFFICULTY_EASY -> ContextCompat.getColor(this, R.color.difficulty_easy)
            Recipe.DIFFICULTY_MEDIUM -> ContextCompat.getColor(this, R.color.difficulty_medium)
            Recipe.DIFFICULTY_HARD -> ContextCompat.getColor(this, R.color.difficulty_hard)
            else -> ContextCompat.getColor(this, R.color.text_muted)
        }
        val timeColor = ContextCompat.getColor(this, R.color.text_primary)
        val metaFull = "${recipe.cookingTime} хв · ${recipe.difficulty}"
        val spannable = SpannableString(metaFull)
        val diffStart = metaFull.indexOf(recipe.difficulty)
        spannable.setSpan(ForegroundColorSpan(timeColor), 0, diffStart, 0)
        spannable.setSpan(ForegroundColorSpan(difficultyColor), diffStart, metaFull.length, 0)
        metaTextView.text = spannable

        cookingTimeTextView.text = "${recipe.cookingTime} хв"
        difficultyTextView.text = recipe.difficulty

        val ingredientsFormatted = recipe.ingredients.joinToString("\n") { "• $it" }
        ingredientsTextView.text = ingredientsFormatted
        instructionsTextView.text = recipe.instructions

        updateCookingCounter(recipe)

        if (recipe.calories != null) {
            caloriesDetailTextView.text = "${recipe.calories.toInt()}"
            proteinsDetailTextView.text = "${recipe.proteins?.toInt() ?: "—"}г"
            fatsDetailTextView.text = "${recipe.fats?.toInt() ?: "—"}г"
            carbsDetailTextView.text = "${recipe.carbs?.toInt() ?: "—"}г"
            proteinsDetailTextView.setTextColor(Color.parseColor("#4CAF50"))
            fatsDetailTextView.setTextColor(Color.parseColor("#FF9800"))
            carbsDetailTextView.setTextColor(Color.parseColor("#2196F3"))
            setupNutritionChart(recipe.proteins, recipe.fats, recipe.carbs)
        } else {
            caloriesDetailTextView.text = "—"
            proteinsDetailTextView.text = "—"
            fatsDetailTextView.text = "—"
            carbsDetailTextView.text = "—"
            nutritionChartLayout.visibility = View.GONE
        }
    }

    private fun setupNutritionChart(proteins: Double?, fats: Double?, carbs: Double?) {
        val p = (proteins ?: 0.0).toFloat()
        val f = (fats ?: 0.0).toFloat()
        val c = (carbs ?: 0.0).toFloat()
        if (p + f + c <= 0f) {
            nutritionChartLayout.visibility = View.GONE
            return
        }
        nutritionChartLayout.visibility = View.VISIBLE

        val total = p + f + c
        val pPct = if (total > 0) (p / total) * 100 else 0f
        val fPct = if (total > 0) (f / total) * 100 else 0f
        val cPct = if (total > 0) (c / total) * 100 else 0f

        val entries = listOf(
            PieEntry(p, if (pPct >= 10f) "Білки" else ""),
            PieEntry(f, if (fPct >= 10f) "Жири" else ""),
            PieEntry(c, if (cPct >= 10f) "Вугл." else "")
        )
        val sliceColors = listOf(
            Color.parseColor("#4CAF50"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#2196F3")
        )
        val dataSet = PieDataSet(entries, "").apply {
            colors = sliceColors
            valueTextColor = Color.WHITE
            valueTextSize = 12f
            valueTypeface = Typeface.DEFAULT_BOLD
            sliceSpace = 2f
            yValuePosition = PieDataSet.ValuePosition.INSIDE_SLICE
            xValuePosition = PieDataSet.ValuePosition.INSIDE_SLICE
        }
        val data = PieData(dataSet).apply {
            setValueFormatter(object : PercentFormatter(nutritionPieChart) {
                override fun getFormattedValue(value: Float): String {
                    return if (value < 10f) "" else super.getFormattedValue(value)
                }
            })
            setValueTextColor(Color.WHITE)
            setValueTextSize(12f)
            setValueTypeface(Typeface.DEFAULT_BOLD)
        }
        nutritionPieChart.apply {
            this.data = data
            setUsePercentValues(true)
            description.isEnabled = false
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 40f
            transparentCircleRadius = 44f
            setTransparentCircleColor(Color.parseColor("#22FFFFFF"))
            setDrawCenterText(false)
            // Зменшуємо відступи, щоб діаграма була більшою
            setExtraOffsets(0f, 0f, 0f, 8f)
            legend.apply {
                textColor = Color.parseColor("#AAAAAA")
                textSize = 11f
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                isWordWrapEnabled = false
                xEntrySpace = 12f
                
                val lEntries = mutableListOf<com.github.mikephil.charting.components.LegendEntry>()
                lEntries.add(com.github.mikephil.charting.components.LegendEntry("Білки ${pPct.toInt()}%", com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#4CAF50")))
                lEntries.add(com.github.mikephil.charting.components.LegendEntry("Жири ${fPct.toInt()}%", com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#FF9800")))
                lEntries.add(com.github.mikephil.charting.components.LegendEntry("Вугл. ${cPct.toInt()}%", com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#2196F3")))
                setCustom(lEntries)
            }
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(11f)
            setEntryLabelTypeface(Typeface.DEFAULT_BOLD)
            // Не показувати назви всередині дуже малих секторів — лише назовні
            setDrawEntryLabels(true)
            animateY(600)
            invalidate()
        }
    }

    private fun updateCookingCounter(recipe: Recipe) {
        cookingCounterTextView.text = recipe.timesCooked.toString()
    }

    private fun showDeleteConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirmation, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.dialogTitle).text = getString(R.string.delete_recipe_title)
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = getString(R.string.delete_recipe_message)

        val confirmBtn = dialogView.findViewById<Button>(R.id.confirmButton)
        confirmBtn.text = getString(R.string.yes)
        confirmBtn.setOnClickListener {
            deleteRecipe()
            dialog.dismiss()
        }

        val cancelBtn = dialogView.findViewById<Button>(R.id.cancelButton)
        cancelBtn.text = getString(R.string.no)
        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteRecipe() {
        currentRecipe?.let { recipe ->
            viewModel.delete(recipe)
            Toast.makeText(this, R.string.recipe_deleted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
