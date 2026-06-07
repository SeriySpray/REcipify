package com.example.recipefood

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.recipefood.api.GroqAIService
import com.example.recipefood.data.RecipeDatabase
import com.example.recipefood.data.UserSettingsRepository
import com.example.recipefood.model.Recipe
import com.example.recipefood.model.SavedMeal
import com.example.recipefood.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class RecipeFoodApp : Application() {
    val groqService = GroqAIService()

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isDark = prefs.getBoolean("theme_dark", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        CoroutineScope(Dispatchers.IO).launch { seedDatabaseIfEmpty() }
    }

    private suspend fun seedDatabaseIfEmpty() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("seeded_v10", false)) return

        val db = RecipeDatabase.getDatabase(this)
        val recipeDao = db.recipeDao()
        val mealDao = db.mealDao()
        val settingsDao = db.userSettingsDao()

        recipeDao.deleteAll()
        mealDao.deleteAllMeals()

        // Скидаємо налаштування, щоб активувати опитування
        settingsDao.insertSettings(UserSettings(
            id = 1,
            targetCalories = 0.0,
            targetProteins = 0.0,
            targetFats = 0.0,
            targetCarbs = 0.0,
            lastAiTip = null,
            lastAiTipMealCount = 0
        ))

        // --- Рецепти ---
        val recipes = listOf(
            Recipe(
                name = "Вівсянка з бананом і медом",
                ingredients = listOf("вівсянка 80г", "молоко 200мл", "банан 1шт", "мед 1 ч.л.", "кориця щіпка"),
                difficulty = Recipe.DIFFICULTY_EASY, cookingTime = 10,
                instructions = "Залити вівсянку молоком і варити 5 хв на середньому вогні. Зняти з вогню, нарізати банан, додати мед і корицю.",
                calories = 320.0, proteins = 9.0, fats = 6.0, carbs = 58.0
            ),
            Recipe(
                name = "Борщ з пампушками",
                ingredients = listOf("буряк 2шт", "картопля 3шт", "капуста 300г", "морква 1шт", "цибуля 1шт", "томатна паста 2 ст.л.", "свинина 400г", "часник 3 зубчики", "сметана"),
                difficulty = Recipe.DIFFICULTY_HARD, cookingTime = 90,
                instructions = "Зварити м'ясний бульйон. Натерти буряк, обсмажити з томатною пастою 10 хв. Додати картоплю і капусту, варити 15 хв. Додати зажарку. Заправити часником.",
                calories = 280.0, proteins = 18.0, fats = 10.0, carbs = 28.0
            ),
            Recipe(
                name = "Грецький салат",
                ingredients = listOf("томати 3шт", "огірок 1шт", "перець болгарський 1шт", "маслини 100г", "сир фета 150г", "оливкова олія 3 ст.л.", "орегано 1 ч.л."),
                difficulty = Recipe.DIFFICULTY_EASY, cookingTime = 15,
                instructions = "Нарізати овочі великими шматками. Додати маслини і кубики фети. Заправити оливковою олією і посипати орегано.",
                calories = 220.0, proteins = 8.0, fats = 16.0, carbs = 10.0
            ),
            Recipe(
                name = "Паста карбонара",
                ingredients = listOf("спагеті 200г", "бекон 150г", "яйця 3шт", "пармезан 80г", "чорний перець", "сіль"),
                difficulty = Recipe.DIFFICULTY_MEDIUM, cookingTime = 25,
                instructions = "Відварити спагеті. Обсмажити бекон. Збити яйця з пармезаном. Змішати гарячі спагеті з беконом, зняти з вогню і вилити яєчну суміш.",
                calories = 580.0, proteins = 28.0, fats = 24.0, carbs = 62.0
            ),
            Recipe(
                name = "Омлет з овочами",
                ingredients = listOf("яйця 3шт", "молоко 50мл", "помідор 1шт", "болгарський перець половина", "шпинат жменя", "вершкове масло 10г"),
                difficulty = Recipe.DIFFICULTY_EASY, cookingTime = 12,
                instructions = "Збити яйця з молоком. Обсмажити овочі 3 хв. Залити яєчною сумішшю і готувати під кришкою 5–7 хв.",
                calories = 240.0, proteins = 16.0, fats = 15.0, carbs = 8.0
            ),
            Recipe(
                name = "Куряче філе в духовці",
                ingredients = listOf("куряче філе 500г", "часник 4 зубчики", "лимон 1шт", "розмарин 2 гілочки", "оливкова олія 2 ст.л.", "паприка 1 ч.л."),
                difficulty = Recipe.DIFFICULTY_MEDIUM, cookingTime = 55,
                instructions = "Натерти філе маринадом з олії, часнику, паприки і лимону. Маринувати 20 хв. Запікати при 200°C 30–35 хв.",
                calories = 360.0, proteins = 48.0, fats = 14.0, carbs = 4.0
            ),
            Recipe(
                name = "Гречана каша з грибами",
                ingredients = listOf("гречка 200г", "печериці 300г", "цибуля 1шт", "морква 1шт", "вершкове масло 20г", "петрушка"),
                difficulty = Recipe.DIFFICULTY_EASY, cookingTime = 30,
                instructions = "Зварити гречку. Обсмажити цибулю і моркву, додати гриби і смажити 7 хв. Змішати з гречкою і маслом.",
                calories = 210.0, proteins = 8.0, fats = 7.0, carbs = 30.0
            ),
            Recipe(
                name = "Стейк рибай з пюре",
                ingredients = listOf("стейк рибай 350г", "картопля 400г", "молоко 100мл", "вершкове масло 40г", "часник 2 зубчики", "чебрець"),
                difficulty = Recipe.DIFFICULTY_MEDIUM, cookingTime = 40,
                instructions = "Дістати стейк за 30 хв. Смажити на розпеченій сковороді по 3 хв з кожного боку. Відпочинок 5 хв. Подавати з картопляним пюре.",
                calories = 680.0, proteins = 52.0, fats = 36.0, carbs = 32.0
            ),
            Recipe(
                name = "Тірамісу",
                ingredients = listOf("маскарпоне 500г", "яйця 4шт", "цукор 100г", "савоярді 200г", "еспресо 200мл", "какао"),
                difficulty = Recipe.DIFFICULTY_HARD, cookingTime = 40,
                instructions = "Збити жовтки з цукром, додати маскарпоне. Окремо збити білки і вмішати в крем. Просочити печиво кавою. Викласти шарами і охолодити 4 години.",
                calories = 430.0, proteins = 10.0, fats = 26.0, carbs = 40.0
            ),
            Recipe(
                name = "Смузі боул з ягодами",
                ingredients = listOf("заморожені ягоди 200г", "банан 1шт", "грецький йогурт 150г", "гранола 50г", "ківі 1шт", "мед 1 ч.л.", "насіння чіа 1 ст.л."),
                difficulty = Recipe.DIFFICULTY_EASY, cookingTime = 8,
                instructions = "Збити блендером ягоди, банан і йогурт. Вилити в миску. Зверху викласти ківі, гранолу, чіа і полити медом.",
                calories = 310.0, proteins = 12.0, fats = 5.0, carbs = 55.0
            ),
            Recipe(
                name = "Лосось на грилі",
                ingredients = listOf("філе лосося 300г", "лимон 1шт", "часник 2 зубчики", "кріп жменя", "оливкова олія 1 ст.л.", "сіль", "перець"),
                difficulty = Recipe.DIFFICULTY_EASY, cookingTime = 20,
                instructions = "Замаринувати філе в олії, часнику і лимонному соку на 10 хв. Обсмажити на грилі по 4–5 хв з кожного боку. Подавати з кропом.",
                calories = 420.0, proteins = 42.0, fats = 22.0, carbs = 2.0
            ),
            Recipe(
                name = "Піца Маргарита",
                ingredients = listOf("тісто для піци 300г", "томатний соус 100г", "моцарела 200г", "томати 2шт", "базилік свіжий", "оливкова олія"),
                difficulty = Recipe.DIFFICULTY_MEDIUM, cookingTime = 35,
                instructions = "Розкатати тісто. Нанести соус, викласти скибочки томатів і моцарелу. Запікати при 220°C 12–15 хв. Прикрасити базиліком.",
                calories = 520.0, proteins = 22.0, fats = 18.0, carbs = 68.0
            ),
            Recipe(
                name = "Суп-крем з гарбуза",
                ingredients = listOf("гарбуз 600г", "цибуля 1шт", "морква 1шт", "часник 3 зубчики", "вершки 200мл", "бульйон 500мл", "імбир 1 ч.л.", "насіння гарбуза"),
                difficulty = Recipe.DIFFICULTY_EASY, cookingTime = 35,
                instructions = "Обсмажити цибулю і часник. Додати нарізаний гарбуз і моркву, залити бульйоном. Варити 20 хв до м'якості. Збити блендером, додати вершки та імбир.",
                calories = 190.0, proteins = 5.0, fats = 10.0, carbs = 22.0
            ),
            Recipe(
                name = "Шоколадний фондан",
                ingredients = listOf("чорний шоколад 150г", "вершкове масло 100г", "яйця 3шт", "цукор 80г", "борошно 40г", "какао 1 ст.л."),
                difficulty = Recipe.DIFFICULTY_HARD, cookingTime = 25,
                instructions = "Розтопити шоколад з маслом. Збити яйця з цукром, додати шоколадну суміш і борошно. Вилити у форми і запікати при 200°C рівно 10–12 хв — центр має залишитись рідким.",
                calories = 480.0, proteins = 8.0, fats = 28.0, carbs = 52.0
            ),
            Recipe(
                name = "Курячий суп з вермішеллю",
                ingredients = listOf("курка 500г", "вермішель 100г", "картопля 2шт", "морква 1шт", "цибуля 1шт", "петрушка", "лавровий лист", "сіль"),
                difficulty = Recipe.DIFFICULTY_EASY, cookingTime = 60,
                instructions = "Зварити курячий бульйон з лавровим листом 40 хв. Дістати курку, відокремити м'ясо. Додати в бульйон картоплю і зажарку з моркви і цибулі. Через 10 хв додати вермішель і м'ясо. Варити 5 хв.",
                calories = 245.0, proteins = 22.0, fats = 8.0, carbs = 20.0
            )
        )
        recipes.forEach { recipeDao.insert(it) }

        // --- Журнал за останні 7 днів ---
        fun daysAgo(d: Int, hour: Int, min: Int): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -d)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0)
            return cal.timeInMillis
        }

        val meals = listOf(
            SavedMeal(name = "Вівсянка з бананом і медом", date = daysAgo(0, 8, 30), totalCalories = 320.0, totalProteins = 9.0, totalFats = 6.0, totalCarbs = 58.0, products = "[]"),
            SavedMeal(name = "Грецький салат", date = daysAgo(0, 13, 0), totalCalories = 220.0, totalProteins = 8.0, totalFats = 16.0, totalCarbs = 10.0, products = "[]"),
            SavedMeal(name = "Куряче філе в духовці", date = daysAgo(0, 19, 30), totalCalories = 360.0, totalProteins = 48.0, totalFats = 14.0, totalCarbs = 4.0, products = "[]"),
            SavedMeal(name = "Омлет з овочами", date = daysAgo(1, 9, 0), totalCalories = 240.0, totalProteins = 16.0, totalFats = 15.0, totalCarbs = 8.0, products = "[]"),
            SavedMeal(name = "Суп-крем з гарбуза", date = daysAgo(1, 13, 30), totalCalories = 190.0, totalProteins = 5.0, totalFats = 10.0, totalCarbs = 22.0, products = "[]"),
            SavedMeal(name = "Паста карбонара", date = daysAgo(1, 20, 0), totalCalories = 580.0, totalProteins = 28.0, totalFats = 24.0, totalCarbs = 62.0, products = "[]"),
            SavedMeal(name = "Смузі боул з ягодами", date = daysAgo(2, 8, 0), totalCalories = 310.0, totalProteins = 12.0, totalFats = 5.0, totalCarbs = 55.0, products = "[]"),
            SavedMeal(name = "Борщ з пампушками", date = daysAgo(2, 14, 0), totalCalories = 280.0, totalProteins = 18.0, totalFats = 10.0, totalCarbs = 28.0, products = "[]"),
            SavedMeal(name = "Лосось на грилі", date = daysAgo(2, 19, 0), totalCalories = 420.0, totalProteins = 42.0, totalFats = 22.0, totalCarbs = 2.0, products = "[]"),
            SavedMeal(name = "Вівсянка з бананом і медом", date = daysAgo(3, 7, 45), totalCalories = 320.0, totalProteins = 9.0, totalFats = 6.0, totalCarbs = 58.0, products = "[]"),
            SavedMeal(name = "Гречана каша з грибами", date = daysAgo(3, 13, 0), totalCalories = 210.0, totalProteins = 8.0, totalFats = 7.0, totalCarbs = 30.0, products = "[]"),
            SavedMeal(name = "Стейк рибай з пюре", date = daysAgo(3, 19, 30), totalCalories = 680.0, totalProteins = 52.0, totalFats = 36.0, totalCarbs = 32.0, products = "[]"),
            SavedMeal(name = "Омлет з овочами", date = daysAgo(4, 8, 15), totalCalories = 240.0, totalProteins = 16.0, totalFats = 15.0, totalCarbs = 8.0, products = "[]"),
            SavedMeal(name = "Піца Маргарита", date = daysAgo(4, 14, 30), totalCalories = 520.0, totalProteins = 22.0, totalFats = 18.0, totalCarbs = 68.0, products = "[]"),
            SavedMeal(name = "Грецький салат", date = daysAgo(4, 19, 0), totalCalories = 220.0, totalProteins = 8.0, totalFats = 16.0, totalCarbs = 10.0, products = "[]"),
            SavedMeal(name = "Курячий суп з вермішеллю", date = daysAgo(5, 12, 0), totalCalories = 245.0, totalProteins = 22.0, totalFats = 8.0, totalCarbs = 20.0, products = "[]"),
            SavedMeal(name = "Куряче філе в духовці", date = daysAgo(5, 19, 0), totalCalories = 360.0, totalProteins = 48.0, totalFats = 14.0, totalCarbs = 4.0, products = "[]"),
            SavedMeal(name = "Смузі боул з ягодами", date = daysAgo(6, 8, 30), totalCalories = 310.0, totalProteins = 12.0, totalFats = 5.0, totalCarbs = 55.0, products = "[]"),
            SavedMeal(name = "Тірамісу", date = daysAgo(6, 20, 0), totalCalories = 430.0, totalProteins = 10.0, totalFats = 26.0, totalCarbs = 40.0, products = "[]")
        )
        meals.forEach { mealDao.insertMeal(it) }

        prefs.edit().putBoolean("seeded_v10", true).apply()
    }
}
