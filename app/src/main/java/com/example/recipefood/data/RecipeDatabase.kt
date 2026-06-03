package com.example.recipefood.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import com.example.recipefood.model.Converters
import com.example.recipefood.model.Recipe
import com.example.recipefood.model.Folder
import com.example.recipefood.model.SavedMeal
import com.example.recipefood.model.UserSettings
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [Recipe::class, Folder::class, SavedMeal::class, UserSettings::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun folderDao(): FolderDao
    abstract fun mealDao(): MealDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: RecipeDatabase? = null

        fun getDatabase(context: Context): RecipeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecipeDatabase::class.java,
                    "recipe_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY dateCreated DESC")
    fun getAllRecipes(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeById(id: Long): Recipe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    @Delete
    suspend fun delete(recipe: Recipe)

    @Query("DELETE FROM recipes")
    suspend fun deleteAll()

    @Query("SELECT * FROM recipes WHERE name LIKE '%' || :searchQuery || '%'")
    fun searchByName(searchQuery: String): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE difficulty = :difficulty")
    fun filterByDifficulty(difficulty: String): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes ORDER BY cookingTime ASC")
    fun getAllRecipesSortedByTime(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun getAllRecipesSortedByName(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE folderId = :folderId ORDER BY dateCreated DESC")
    fun getRecipesByFolder(folderId: Long): Flow<List<Recipe>>
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY dateCreated ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder): Long

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCount(): Int
}
