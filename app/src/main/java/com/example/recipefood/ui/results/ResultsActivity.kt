package com.example.recipefood.ui.results

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.recipefood.R
import com.example.recipefood.RecipeFoodApp
import com.example.recipefood.data.MealRepository
import com.example.recipefood.data.RecipeDatabase
import com.example.recipefood.databinding.ActivityResultsBinding
import com.example.recipefood.model.Food
import com.example.recipefood.model.SavedMeal
import com.example.recipefood.ui.add.AddRecipeActivity
import com.example.recipefood.ui.main.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class ResultsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultsBinding
    private lateinit var food: Food
    private lateinit var repository: MealRepository
    private val groqService by lazy { (application as RecipeFoodApp).groqService }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = RecipeDatabase.getDatabase(this)
        repository = MealRepository(database.mealDao())

        val foodJson = intent.getStringExtra("food_json")
        food = Gson().fromJson(foodJson, Food::class.java)

        displayResults()

        binding.btnSave.setOnClickListener {
            saveMeal()
        }

        binding.btnClose.setOnClickListener {
            generateRecipe()
        }

        binding.btnBackLink.setOnClickListener {
            finish()
        }
    }

    private fun generateRecipe() {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Генеруємо рецепт...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val recipe = withContext(Dispatchers.IO) {
                    groqService.generateRecipeFromFood(food)
                }
                progressDialog.dismiss()
                
                val intent = Intent(this@ResultsActivity, AddRecipeActivity::class.java)
                intent.putExtra("recipe_json", Gson().toJson(recipe))
                startActivity(intent)
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@ResultsActivity, "Помилка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayResults() {
        binding.tvFoodName.text = food.name

        food.nutrition?.let { nutrition ->
            binding.tvCalories.text = "${String.format("%.1f", nutrition.calories)} ккал"
            binding.tvProteins.text = "${String.format("%.1f", nutrition.proteins)} г"
            binding.tvFats.text = "${String.format("%.1f", nutrition.fats)} г"
            binding.tvCarbs.text = "${String.format("%.1f", nutrition.carbs)} г"
        }

        binding.productsContainer.removeAllViews()

        food.products.forEach { product ->
            val itemView = layoutInflater.inflate(
                R.layout.item_product,
                binding.productsContainer,
                false
            )

            itemView.findViewById<android.widget.TextView>(R.id.tvProductName).text = product.name
            itemView.findViewById<android.widget.TextView>(R.id.tvProductWeight).text = "${product.weight} г"

            val nutritionLayout = itemView.findViewById<android.widget.LinearLayout>(R.id.nutritionLayout)
            nutritionLayout.visibility = android.view.View.VISIBLE

            itemView.findViewById<android.widget.ImageButton>(R.id.btnEdit).visibility = android.view.View.GONE
            itemView.findViewById<android.widget.ImageButton>(R.id.btnDelete).visibility = android.view.View.GONE

            product.nutrition?.let { nutrition ->
                itemView.findViewById<android.widget.TextView>(R.id.tvCalories).text =
                    "Калорії: ${String.format("%.1f", nutrition.calories)} ккал"
                itemView.findViewById<android.widget.TextView>(R.id.tvProteins).text =
                    "Білки: ${String.format("%.1f", nutrition.proteins)} г"
                itemView.findViewById<android.widget.TextView>(R.id.tvFats).text =
                    "Жири: ${String.format("%.1f", nutrition.fats)} г"
                itemView.findViewById<android.widget.TextView>(R.id.tvCarbs).text =
                    "Вуглеводи: ${String.format("%.1f", nutrition.carbs)} г"
            }

            binding.productsContainer.addView(itemView)
        }
    }

    private fun saveMeal() {
        food.nutrition?.let { nutrition ->
            val savedMeal = SavedMeal(
                name = food.name,
                date = System.currentTimeMillis(),
                totalCalories = nutrition.calories,
                totalProteins = nutrition.proteins,
                totalFats = nutrition.fats,
                totalCarbs = nutrition.carbs,
                products = Gson().toJson(food.products)
            )

            lifecycleScope.launch {
                try {
                    repository.insertMeal(savedMeal)
                    Toast.makeText(this@ResultsActivity, "Страву збережено!", Toast.LENGTH_SHORT).show()
                    
                    // Перехід на головний екран (трекер)
                    val intent = Intent(this@ResultsActivity, MainActivity::class.java)
                    intent.putExtra("goto_tracker", true)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@ResultsActivity, "Помилка збереження: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
