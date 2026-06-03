package com.example.recipefood.api

import android.util.Log
import com.example.recipefood.model.Food
import com.example.recipefood.model.NutritionInfo
import com.example.recipefood.model.Recipe
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GroqAIService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val apiKey = com.example.recipefood.BuildConfig.API_KEY

    private val apiUrl = "https://api.groq.com/openai/v1/chat/completions"

    // Модель з підтримкою vision для аналізу фото
    private val visionModel = "meta-llama/llama-4-scout-17b-16e-instruct"

    // Модель для текстового аналізу КБЖВ
    private val textModel = "llama-3.3-70b-versatile"

    private var lastRequestTime = 0L
    private val minRequestInterval = 1000L // 1 секунда між запитами

    init {
        Log.d("GroqService", "Initialized with API key length: ${apiKey.length}")
    }

    private suspend fun waitForRateLimit() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime
        if (timeSinceLastRequest < minRequestInterval) {
            val waitTime = minRequestInterval - timeSinceLastRequest
            Log.d("GroqService", "Rate limit: waiting ${waitTime}ms")
            kotlinx.coroutines.delay(waitTime)
        }
        lastRequestTime = System.currentTimeMillis()
    }

    private fun extractContent(responseBody: String): String {
        val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
        return jsonResponse
            .getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString
            ?.trim()
            ?.removePrefix("```json")
            ?.removePrefix("```")
            ?.removeSuffix("```")
            ?.trim()
            ?: throw Exception("Не вдалося отримати текст відповіді з JSON")
    }

    private fun handleErrorCode(code: Int, body: String): Nothing {
        when (code) {
            401 -> throw Exception("Невірний API ключ Groq (401)")
            429 -> throw Exception("Перевищено ліміт запитів Groq (429). Зачекайте хвилину")
            400 -> throw Exception("Невірний формат запиту (400): ${body.take(200)}")
            413 -> throw Exception("Зображення занадто велике (413). Спробуйте менший файл")
            else -> throw Exception("Помилка Groq API (${code}): ${body.take(200)}")
        }
    }

    suspend fun analyzeFood(base64Image: String): Food = withContext(Dispatchers.IO) {
        Log.d("GroqService", "analyzeFood() called")
        waitForRateLimit()

        if (apiKey.isEmpty()) {
            throw Exception("API ключ порожній! Перевірте keys.properties")
        }

        val prompt = """
            Проаналізуй це фото страви. 
            ВАЖЛИВО: Якщо на фото НЕМАЄ їжі (наприклад, це просто кімната, людина, текст, чи будь-який предмет, який не можна з'їсти), поверни JSON з полем "is_food": false.
            
            Якщо їжа є:
            1. Визнач інгредієнти та їхню вагу для ОДНІЄЇ ПОРЦІЇ.
            2. ПРАВИЛО МАСШТАБУ: Використовуй розмір тарілки/виделки. Одна порція ≈ 300-500г.
            3. ПРИХОВАНІ ІНГРЕДІЄНТИ: Враховуй олію, соуси, цукор.

            Поверни результат ТІЛЬКИ у форматі JSON українською мовою:
            {
              "is_food": true,
              "name": "Назва страви",
              "products": [
                {"name": "Інгредієнт", "weight": вага_в_грамах}
              ]
            }
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", visionModel)
            addProperty("temperature", 0.4)
            addProperty("max_tokens", 2048)
            add("messages", gson.toJsonTree(listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "text", "text" to prompt),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf(
                                "url" to "data:image/jpeg;base64,$base64Image"
                            )
                        )
                    )
                )
            )))
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь від сервера")

            if (!response.isSuccessful) {
                handleErrorCode(response.code, responseBody)
            }

            val content = extractContent(responseBody)
            val json = JsonParser.parseString(content).asJsonObject
            
            if (json.has("is_food") && !json.get("is_food").asBoolean) {
                throw Exception("NOT_FOOD")
            }

            gson.fromJson(content, Food::class.java)
        } catch (e: Exception) {
            Log.e("GroqService", "Exception in analyzeFood", e)
            throw e
        }
    }

    suspend fun validateMealName(name: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (name.length < 2) return@withContext true to null
        waitForRateLimit()
        
        val prompt = """
            Ти - кулінарний критик-гуморист. Проаналізуй назву страви: "$name".
            Твоє завдання:
            1. Якщо назва - це адекватна їжа (навіть якщо проста, як "хліб"), поверни JSON { "is_okay": true }.
            2. Якщо назва - це повна дурня, неїстівні речі (цвяхи, бетон, шкарпетки) або щось відверто огидне/незрозуміле, поверни { "is_okay": false, "comment": "Жартівливий короткий коментар українською про те, чому це не варто їсти" }.
            
            Відповідай ТІЛЬКИ JSON.
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", textModel)
            addProperty("temperature", 0.7)
            add("messages", gson.toJsonTree(listOf(mapOf("role" to "user", "content" to prompt))))
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val content = extractContent(response.body?.string() ?: "")
            val json = JsonParser.parseString(content).asJsonObject
            val isOkay = json.get("is_okay").asBoolean
            val comment = if (!isOkay) json.get("comment").asString else null
            isOkay to comment
        } catch (e: Exception) {
            true to null
        }
    }

    suspend fun analyzeRecipeFromImage(base64Image: String): Recipe = withContext(Dispatchers.IO) {
        Log.d("GroqService", "analyzeRecipeFromImage() called")
        waitForRateLimit()

        if (apiKey.isEmpty()) {
            throw Exception("API ключ порожній! Перевірте keys.properties")
        }

        val prompt = """
            Проаналізуй це фото страви та запропонуй детальний рецепт для її ВЛАСНОГО приготування в домашніх умовах з нуля.
            ВАЖЛИВО: Навіть якщо на фото готовий магазинний продукт, ти ПОВИНЕН написати рецепт, як відтворити його аналог вдома самостійно. 
            КАТЕГОРИЧНО ЗАБОРОНЕНО радити просто купити продукт або вживати його готовим. Твоє завдання — надати інструкцію для готування.
            
            Визнач назву, необхідні інгредієнти з кількістю, складність, приблизний час приготування та покрокову інструкцію.
            Також розрахуй загальний КБЖВ для всього рецепту.
            
            Поверни результат ТІЛЬКИ у форматі JSON на українській мові:
            {
              "name": "Назва страви (домашній варіант)",
              "ingredients": ["Продукт 1 (кількість)", "Продукт 2 (кількість)"],
              "difficulty": "Легкий" або "Середній" або "Складний",
              "cookingTime": 45,
              "instructions": "1. Перший крок...\n2. Другий крок...",
              "calories": 450.0,
              "proteins": 20.0,
              "fats": 15.0,
              "carbs": 50.0
            }
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", visionModel)
            addProperty("temperature", 0.5)
            addProperty("max_tokens", 4096)
            add("messages", gson.toJsonTree(listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "text", "text" to prompt),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf(
                                "url" to "data:image/jpeg;base64,$base64Image"
                            )
                        )
                    )
                )
            )))
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь")

            if (!response.isSuccessful) {
                handleErrorCode(response.code, responseBody)
            }

            val content = extractContent(responseBody)
            gson.fromJson(content, Recipe::class.java)
        } catch (e: Exception) {
            Log.e("GroqService", "Exception in analyzeRecipeFromImage", e)
            throw e
        }
    }

    suspend fun generateRecipeFromFood(food: Food): Recipe = withContext(Dispatchers.IO) {
        Log.d("GroqService", "generateRecipeFromFood() called for: ${food.name}")
        waitForRateLimit()

        val productsInfo = food.products.joinToString("\n") { "- ${it.name}: ${it.weight}г" }

        val prompt = """
            На основі наступної страви та інгредієнтів створи повноцінний ДОМАШНІЙ рецепт для приготування з нуля.
            ВАЖЛИВО: Навіть якщо страва виглядає як магазинний продукт, ти ПОВИНЕН написати інструкцію, як приготувати її аналог самостійно вдома.
            ЗАБОРОНЕНО писати "купити" або "готовий до вживання". Потрібен саме процес приготування.
            
            Страва: ${food.name}
            Інгредієнти:
            $productsInfo

            Запропонуй:
            1. Детальний список інгредієнтів (можеш додати спеції, олію тощо, якщо вони потрібні для домашнього приготування)
            2. Складність (Легкий, Середній, Складний)
            3. Час приготування у хвилинах
            4. Покрокову інструкцію приготування
            5. Загальний КБЖВ
            
            Поверни результат ТІЛЬКИ у форматі JSON на українській мові:
            {
              "name": "${food.name} (домашнє приготування)",
              "ingredients": ["Продукт 1 (кількість)", "Продукт 2 (кількість)"],
              "difficulty": "Легкий" або "Середній" або "Складний",
              "cookingTime": 45,
              "instructions": "1. Перший крок...\n2. Другий крок...",
              "calories": 450.0,
              "proteins": 20.0,
              "fats": 15.0,
              "carbs": 50.0
            }
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", textModel)
            addProperty("temperature", 0.5)
            addProperty("max_tokens", 4096)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to prompt)
            )))
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь")

            if (!response.isSuccessful) {
                handleErrorCode(response.code, responseBody)
            }

            val content = extractContent(responseBody)
            gson.fromJson(content, Recipe::class.java)
        } catch (e: Exception) {
            Log.e("GroqService", "Exception in generateRecipeFromFood", e)
            throw e
        }
    }

    suspend fun analyzeRecipeNutrition(recipeName: String, ingredients: List<String>, instructions: String = ""): NutritionInfo = withContext(Dispatchers.IO) {
        Log.d("GroqService", "analyzeRecipeNutrition() called for: $recipeName")
        waitForRateLimit()

        if (apiKey.isEmpty()) {
            throw Exception("API ключ порожній! Перевірте keys.properties")
        }

        val ingredientsText = ingredients.joinToString("\n") { "- $it" }
        val instructionsSection = if (instructions.isNotBlank()) {
            "\n\nСпосіб приготування (врахуй вплив термообробки на харчову цінність):\n$instructions"
        } else ""

        val prompt = """
            Розрахуй КБЖВ для рецепту "$recipeName" з такими інгредієнтами:
            $ingredientsText$instructionsSection

            Визнач загальну харчову цінність всього рецепту з урахуванням способу приготування:
            - Калорії (ккал)
            - Білки (г)
            - Жири (г)
            - Вуглеводи (г)

            Аналіз виконуй максимально точно. Поверни результат ТІЛЬКИ у форматі JSON без додаткового тексту:
            {
              "calories": 450.0,
              "proteins": 25.0,
              "fats": 12.0,
              "carbs": 55.0
            }
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", textModel)
            addProperty("temperature", 0.3)
            addProperty("max_tokens", 256)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to prompt)
            )))
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь")

            Log.d("GroqService", "RecipeNutrition response code: ${response.code}")

            if (!response.isSuccessful) {
                handleErrorCode(response.code, responseBody)
            }

            val content = extractContent(responseBody)
            Log.d("GroqService", "RecipeNutrition content: $content")
            gson.fromJson(content, NutritionInfo::class.java)
        } catch (e: Exception) {
            Log.e("GroqService", "Exception in analyzeRecipeNutrition", e)
            throw e
        }
    }

    suspend fun analyzeNutrition(food: Food): Food = withContext(Dispatchers.IO) {
        Log.d("GroqService", "analyzeNutrition() called for: ${food.name}")
        waitForRateLimit()

        val productsInfo = food.products.joinToString("\n") { "- ${it.name}: ${it.weight}г" }

        val prompt = """
            Проаналізуй харчову цінність страви "${food.name}" з таких продуктів:
            $productsInfo

            Порахуй для КОЖНОГО продукту та для ВСІЄЇ страви загалом:
            - Калорії (ккал)
            - Білки (г)
            - Жири (г)
            - Вуглеводи (г)
            Аналіз виконуй максимально точно опираючись на надійні джерела. Назви страв та продуктів вписуй на українській мові.
            Поверни результат у форматі JSON:
            {
              "name": "${food.name}",
              "nutrition": {
                "calories": 500.0,
                "proteins": 25.0,
                "fats": 15.0,
                "carbs": 60.0
              },
              "products": [
                {
                  "name": "Продукт 1",
                  "weight": 100,
                  "nutrition": {
                    "calories": 200.0,
                    "proteins": 10.0,
                    "fats": 5.0,
                    "carbs": 25.0
                  }
                }
              ]
            }

            Відповідай ТІЛЬКИ валідним JSON без додаткового тексту.
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", textModel)
            addProperty("temperature", 0.4)
            addProperty("max_tokens", 4096)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to prompt)
            )))
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь")

            Log.d("GroqService", "Nutrition response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.e("GroqService", "Nutrition API Error: ${response.code}")
                handleErrorCode(response.code, responseBody)
            }

            val content = extractContent(responseBody)
            Log.d("GroqService", "Nutrition analysis complete")
            gson.fromJson(content, Food::class.java)
        } catch (e: Exception) {
            Log.e("GroqService", "Exception in analyzeNutrition", e)
            throw e
        }
    }

    suspend fun getNutritionTips(
        todayStats: NutritionInfo, 
        targets: NutritionInfo
    ): String = withContext(Dispatchers.IO) {
        Log.d("GroqService", "getNutritionTips() called for strict math analysis")
        waitForRateLimit()

        val prompt = """
            Ти — професійний дієтолог. Твоє завдання: математично точно порівняти споживання за сьогодні з цілями.
            
            ДАНІ ДЛЯ АНАЛІЗУ:
            1. Твоя ціль: ${targets.calories.toInt()} ккал.
            2. Реально спожито сьогодні: ${todayStats.calories.toInt()} ккал.
            3. БЖВ (Реальне): Б:${todayStats.proteins.toInt()}г, Ж:${todayStats.fats.toInt()}г, В:${todayStats.carbs.toInt()}г.
            4. БЖВ (Ціль): Б:${targets.proteins.toInt()}г, Ж:${targets.fats.toInt()}г, В:${targets.carbs.toInt()}г.
            
            МАТЕМАТИЧНІ ПРАВИЛА:
            - Якщо ${todayStats.calories.toInt()} менше або дорівнює ${targets.calories.toInt()} — це НЕ ПЕРЕВИЩЕННЯ. Заборонено писати, що користувач з'їв більше норми.
            - ДІАПАЗОН НОРМИ: від ${(targets.calories * 0.85).toInt()} до ${(targets.calories * 1.1).toInt()} ккал. Якщо споживання в цих межах, пиши, що прогрес ідеальний.
            - ДЕФІЦИТ: Якщо споживання менше ${(targets.calories * 0.8).toInt()} ккал.
            - НАДЛИШОК: Тільки якщо споживання більше ${(targets.calories * 1.15).toInt()} ккал.
            
            ПРАВИЛА ВІДПОВІДІ:
            1. Мова: ВИКЛЮЧНО літературна українська. Жодних "орехов", "сортов", "сахаров".
            2. Стиль: Тільки чистий текст, повні речення. Без зірочок, списків та жирного шрифту.
            3. Логіка: Похвали, якщо користувач у діапазоні норми. Дай пораду, тільки якщо є реальний дефіцит або реальний надлишок.
            
            Поверни ТІЛЬКИ текст поради.
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", textModel)
            addProperty("temperature", 0.6)
            addProperty("max_tokens", 1024)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to prompt)
            )))
        }.toString()

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь")

            if (!response.isSuccessful) {
                handleErrorCode(response.code, responseBody)
            }

            val rawContent = extractContent(responseBody)
            // Видаляємо маркдаун (зірочки, решітки, підкреслення) та зайві пробіли
            rawContent.replace(Regex("[*#_~`>]+"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        } catch (e: Exception) {
            "На жаль, не вдалося згенерувати поради зараз. Спробуйте пізніше!"
        }
    }
}
