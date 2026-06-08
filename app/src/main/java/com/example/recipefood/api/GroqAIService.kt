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

    private fun sanitizeUkrainianText(text: String): String {
        // 1. Видаляємо маркдаун та зайві символи
        var filtered = text.replace(Regex("[*#_~`>]+"), "")
        
        // 2. Видаляємо англійські літери та будь-які інші некириличні символи
        // Залишаємо тільки українські літери, цифри та базову пунктуацію
        filtered = filtered.replace(Regex("[^а-яА-ЯіїєґІЇЄҐ0-9\\s.,!?:;()\\-\"\'%+=/\\u2013\\u2014]"), "")
        
        // 3. Видаляємо подвійні пробіли
        return filtered.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractContent(responseBody: String): String {
        val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
        val rawContent = jsonResponse
            .getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString
            ?.trim() ?: throw Exception("Не вдалося отримати текст відповіді")

        // Очищаємо від маркдауну, якщо він є
        return rawContent
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun handleErrorCode(code: Int, body: String): Nothing {
        when (code) {
            401 -> throw Exception("Невірний API ключ Groq (401)")
            429 -> throw Exception("Перевищено ліміт запитів Groq (429). Зачекайте хвилину")
            400 -> throw Exception("Невірний формат запиту (400): ${body.take(300)}")
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
            1. Визнач інгредієнти та їхню ПРИБЛИЗНУ вагу на око. 
            2. НЕ ОБМЕЖУЙСЯ стандартними порціями (300-500г). Якщо на фото велика тарілка або декілька порцій - вказуй реальну вагу, яку бачиш.
            3. ПРИХОВАНІ ІНГРЕДІЄНТИ: Враховуй олію, соуси, цукор.

            ПРАВИЛА ВІДПОВІДІ:
            - Відповідь має бути ТІЛЬКИ у форматі JSON.
            - ЖОДНОГО тексту до або після JSON.
            - Мова результатів: СУТО УКРАЇНСЬКА.
            - КАТЕГОРИЧНО ЗАБОРОНЕНО використовувати російські слова.

            ФОРМАТ JSON:
            {
              "is_food": true,
              "name": "Назва страви",
              "products": [
                {"name": "Інгредієнт", "weight": вага_в_грамах}
              ]
            }
        """.trimIndent()

        val responseFormat = JsonObject().apply {
            addProperty("type", "json_object")
        }

        val requestBody = JsonObject().apply {
            addProperty("model", visionModel)
            addProperty("temperature", 0.0)
            addProperty("max_tokens", 2048)
            add("response_format", responseFormat)
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

            val food = gson.fromJson(content, Food::class.java)
            food.copy(
                name = sanitizeUkrainianText(food.name),
                products = food.products.map { it.copy(name = sanitizeUkrainianText(it.name)) }.toMutableList()
            )
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
            
            ПРАВИЛА МОВИ:
            - Відповідай ТІЛЬКИ JSON СУТО УКРАЇНСЬКОЮ МОВОЮ. 
            - КАТЕГОРИЧНО ЗАБОРОНЕНО використовувати російську мову або іноземні ієрогліфи.
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
            val comment = if (!isOkay) sanitizeUkrainianText(json.get("comment").asString) else null
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
            
            ПРАВИЛА JSON (СУВОРО):
            - Поверни ТІЛЬКИ валідний JSON.
            - Поле "instructions" ПОВИННО бути одним рядком (String), де кроки розділені символом \n. КАТЕГОРИЧНО ЗАБОРОНЕНО повертати масив [].
            - Поле "ingredients" повинно бути масивом рядків.
            
            ПРАВИЛА МОВИ:
            - Результат СУТО УКРАЇНСЬКОЮ МОВОЮ. 
            - ЗАБОРОНЕНО використовувати російські слова, ієрогліфи або латиницю.

            {
              "name": "Назва страви",
              "ingredients": ["Продукт 1 (кількість)", "Продукт 2 (кількість)"],
              "difficulty": "Легкий" або "Середній" або "Складний",
              "cookingTime": 45,
              "instructions": "1. Крок один...\n2. Крок два...",
              "calories": 450.0,
              "proteins": 20.0,
              "fats": 15.0,
              "carbs": 50.0
            }
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", visionModel)
            addProperty("temperature", 0.0)
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
            val recipe = gson.fromJson(content, Recipe::class.java)
            recipe.copy(
                name = sanitizeUkrainianText(recipe.name),
                ingredients = recipe.ingredients.map { sanitizeUkrainianText(it) },
                instructions = sanitizeUkrainianText(recipe.instructions),
                difficulty = sanitizeUkrainianText(recipe.difficulty)
            )
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
            
            ПРАВИЛА МОВИ:
            - Поверни результат ТІЛЬКИ у форматі JSON СУТО УКРАЇНСЬКОЮ МОВОЮ. 
            - КАТЕГОРИЧНО ЗАБОРОНЕНО використовувати російську мову або будь-які ієрогліфи.

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
            addProperty("temperature", 0.0)
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
            val recipe = gson.fromJson(content, Recipe::class.java)
            recipe.copy(
                name = sanitizeUkrainianText(recipe.name),
                ingredients = recipe.ingredients.map { sanitizeUkrainianText(it) },
                instructions = sanitizeUkrainianText(recipe.instructions),
                difficulty = sanitizeUkrainianText(recipe.difficulty)
            )
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
            Проаналізуй та розрахуй загальну харчову цінність (КБЖВ) для всього рецепту "$recipeName".
            
            Інгредієнти:
            $ingredientsText$instructionsSection

            МЕТОДИКА РОЗРАХУНКУ:
            1. Визнач кількість кожного інгредієнта. Якщо кількість не вказана, використовуй середньостатистичну порцію для даного рецепту.
            2. Розрахуй калорії, білки, жири та вуглеводи для кожного інгредієнта окремо.
            3. Додай всі значення, щоб отримати загальний КБЖВ для ВСІЄЇ страви.
            4. Врахуй втрати при термообробці (уварювання, смаження тощо).
            5. БУДЬ КОНСЕРВАТИВНИМ ТА ТОЧНИМ. Результат повинен бути максимально реалістичним.
            6. ВАЖЛИВО: Оскільки ти ШІ, твій розрахунок повинен бути стабільним. Для одних і тих самих інгредієнтів завжди давай однакову відповідь.

            ПРАВИЛА:
            - Відповідай ТІЛЬКИ валідним JSON.
            - ЗАБОРОНЕНО використовувати ієрогліфи або російську мову.

            {
              "calories": 450.0,
              "proteins": 25.0,
              "fats": 12.0,
              "carbs": 55.0
            }
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", textModel)
            addProperty("temperature", 0.0)
            addProperty("max_tokens", 512)
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
            
            ПРАВИЛА МОВИ ТА ФОРМАТУ:
            - Аналіз виконуй максимально точно та стабільно (однакові вхідні дані = однаковий результат). 
            - Назви страв та продуктів вписуй СУТО УКРАЇНСЬКОЮ МОВОЮ.
            - КАТЕГОРИЧНО ЗАБОРОНЕНО використовувати російські слова, китайські або японські ієрогліфи.

            Поверни результат у форматі JSON:
            {
              "name": "Назва страви",
              "nutrition": {
                "calories": 500.0,
                "proteins": 25.0,
                "fats": 15.0,
                "carbs": 60.0
              },
              "products": [
                {
                  "name": "Назва продукту",
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
            addProperty("temperature", 0.0)
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
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь від сервера")

            Log.d("GroqService", "Nutrition response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.e("GroqService", "Nutrition API Error: ${response.code}")
                handleErrorCode(response.code, responseBody)
            }

            val content = extractContent(responseBody)
            val analyzedFood = gson.fromJson(content, Food::class.java)
            analyzedFood.copy(
                name = sanitizeUkrainianText(analyzedFood.name),
                products = analyzedFood.products.map { it.copy(name = sanitizeUkrainianText(it.name)) }.toMutableList()
            )
        } catch (e: Exception) {
            Log.e("GroqService", "Exception in analyzeNutrition", e)
            throw e
        }
    }

    suspend fun getNutritionTips(
        todayStats: NutritionInfo, 
        targets: NutritionInfo,
        allergens: String = ""
    ): String = withContext(Dispatchers.IO) {
        Log.d("GroqService", "getNutritionTips() called for positive data analysis")
        waitForRateLimit()

        val allergensInstruction = if (allergens.isNotBlank()) {
            "\nКАТЕГОРИЧНО ЗАБОРОНЕНО радити продукти, на які у користувача АЛЕРГІЯ: $allergens."
        } else ""

        val prompt = """
            Ти — позитивний та професійний дієтолог-ментор. Твоє завдання: проаналізувати сьогоднішні показники БЖВ і дати мотивуючу ПРАКТИЧНУ пораду.
            $allergensInstruction
            
            ДАНІ ДЛЯ АНАЛІЗУ:
            1. Ціль: ${targets.calories.toInt()} ккал. Спожито: ${todayStats.calories.toInt()} ккал.
            2. БЖВ (Реальне): Б:${todayStats.proteins.toInt()}г, Ж:${todayStats.fats.toInt()}г, В:${todayStats.carbs.toInt()}г.
            3. БЖВ (Ціль): Б:${targets.proteins.toInt()}г, Ж:${targets.fats.toInt()}г, В:${targets.carbs.toInt()}г.
            
            ТВОЯ ЛОГІКА:
            1. ПОЗИТИВ ПЕРШ ЗА ВСЕ: Обов'язково почни з похвали. Знайди, що користувач зробив добре (наприклад, "чудовий старт дня", "гарний рівень білка", або просто "ви молодець, що ведете щоденник").
            2. ПРАВИЛО ДОБОРУ: ЗАБОРОНЕНО радити добирати той нутрієнт (білки, жири чи вуглеводи), по якому план вже виконано або перевищено, ДАВАЙ ПОРАДУ ТІЛЬКИ ТОДІ КОЛИ ТОЧНО БАЧИШ ЩО КОРИСТУВАЧ НЕ ДОБИРАЄ ДЕННУ НОРМУ НУТРІЄНТУ.
            3. БАЛАНС, А НЕ ТІЛЬКИ ВЕЧЕРЯ: Не зациклюйся лише на порадах про вечір. "Ви чудово тримаєтеся, можна ще додати трохи...".
            КОНКРЕТНІ ПРОДУКТИ: 
               - Мало білка: порадь сир, яйця, рибу або м'ясо.
               - Мало жирів: КАТЕГОРИЧНО ЗАБОРОНЕНО радити горіхи, олії, авокадо чи будь-які "здорові жири". Якщо жирів мало, просто проігноруй це і зосередився на білках чи вуглеводах.
               - Мало вуглеводів: порадь крупи, цільнозерновий хліб або ягоди. 
            4. СТИЛЬ: Будь натхненним, коротким і конкретним. 
            
            КАТЕГОРИЧНІ ПРАВИЛА МОВИ ТА ДАНИХ:
            - Тільки чиста українська мова. СУВОРО ЗАБОРОНЕНО використовувати російські або англійські слова.
            - Використовуй тільки українські відповідники: "білки", "калорії", "вуглеводи", "жири", "харчування", "поради".
            - ВИКОРИСТОВУЙ ТІЛЬКИ ТІ ЧИСЛА, ЯКІ НАДАНІ В ДАНИХ ДЛЯ АНАЛІЗУ. 
            - Один короткий абзац (2-4 речення).
            
            Напиши теплу та професійну пораду ВИКЛЮЧНО УКРАЇНСЬКОЮ МОВОЮ.
        """.trimIndent()

        val requestBody = JsonObject().apply {
            addProperty("model", textModel)
            addProperty("temperature", 0.7)
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
            val responseBody = response.body?.string() ?: throw Exception("Порожня відповідь від сервера")

            if (!response.isSuccessful) {
                handleErrorCode(response.code, responseBody)
            }

            val rawContent = extractContent(responseBody)
            sanitizeUkrainianText(rawContent)
        } catch (e: Exception) {
            "На жаль, не вдалося згенерувати поради зараз. Спробуйте пізніше!"
        }
    }
}
