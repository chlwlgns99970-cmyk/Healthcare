package com.example.healthcare.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.healthcare.data.dao.CalorieGoalDao
import com.example.healthcare.data.dao.EnergyProfileDao
import com.example.healthcare.data.dao.FrequentFoodDao
import com.example.healthcare.data.dao.FoodItemDao
import com.example.healthcare.data.dao.MealCoachDao
import com.example.healthcare.data.dao.MealRecordDao
import com.example.healthcare.data.dao.RecognitionDao
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.DailyMealPlan
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.entity.MealTemplateIngredient
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.PlannedMeal
import com.example.healthcare.data.entity.RecognitionCandidate
import com.example.healthcare.data.entity.RecognitionSession
import com.example.healthcare.data.entity.UserExcludedFood
import com.example.healthcare.data.entity.UserMealPreference

/**
 * 앱의 메인 데이터베이스 클래스
 */
@Database(
    entities = [
        MealRecord::class,
        CalorieGoal::class,
        FrequentFood::class,
        EnergyProfileHistory::class,
        FoodItem::class,
        UserMealPreference::class,
        UserExcludedFood::class,
        MealTemplate::class,
        MealTemplateIngredient::class,
        DailyMealPlan::class,
        PlannedMeal::class,
        RecognitionSession::class,
        RecognitionCandidate::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealRecordDao(): MealRecordDao
    abstract fun calorieGoalDao(): CalorieGoalDao
    abstract fun frequentFoodDao(): FrequentFoodDao
    abstract fun energyProfileDao(): EnergyProfileDao
    abstract fun foodItemDao(): FoodItemDao
    abstract fun mealCoachDao(): MealCoachDao
    abstract fun recognitionDao(): RecognitionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "healthcare_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_records ADD COLUMN servingAmount REAL")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN servingUnit TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN photoAnalysisId TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN photoRequestId TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN photoItemId TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN aiFoodName TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN aiEstimatedCalories INTEGER")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN aiMinimumCalories INTEGER")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN aiMaximumCalories INTEGER")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN aiConfidence REAL")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN analysisModelVersion TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN analysisRequestedAt TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN wasAiResultEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN createdAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_meal_records_photoRequestId_photoItemId " +
                        "ON meal_records(photoRequestId, photoItemId)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `energy_profile_history` (" +
                        "`profileId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`basalMetabolicRateKcal` INTEGER NOT NULL, " +
                        "`activityLevelCode` TEXT NOT NULL, " +
                        "`palMultiplier` REAL NOT NULL, " +
                        "`targetMode` TEXT NOT NULL, " +
                        "`effectiveFromDate` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_energy_profile_history_effectiveFromDate` " +
                        "ON `energy_profile_history` (`effectiveFromDate`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_records ADD COLUMN plannedMealId INTEGER")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN foodItemId TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN barcode TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_meal_records_plannedMealId " +
                        "ON meal_records(plannedMealId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_meal_records_foodItemId ON meal_records(foodItemId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_meal_records_barcode ON meal_records(barcode)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS food_items (" +
                        "id TEXT NOT NULL PRIMARY KEY, sourceType TEXT NOT NULL, " +
                        "sourceFoodCode TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, " +
                        "aliases TEXT NOT NULL, category TEXT, referenceAmount REAL NOT NULL, " +
                        "unit TEXT NOT NULL, energyKcal REAL NOT NULL, carbohydrateGrams REAL, " +
                        "proteinGrams REAL, fatGrams REAL, sodiumMilligrams REAL, " +
                        "servingDescription TEXT NOT NULL, brand TEXT, barcode TEXT, " +
                        "dataVersion TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_items_normalizedName ON food_items(normalizedName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_food_items_barcode ON food_items(barcode)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_food_items_sourceType_sourceFoodCode " +
                        "ON food_items(sourceType, sourceFoodCode)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS user_meal_preferences (" +
                        "id INTEGER NOT NULL PRIMARY KEY, mealScheduleType TEXT NOT NULL, " +
                        "breakfastEnabled INTEGER NOT NULL, lunchEnabled INTEGER NOT NULL, " +
                        "dinnerEnabled INTEGER NOT NULL, snackEnabled INTEGER NOT NULL, " +
                        "breakfastRatio INTEGER NOT NULL, lunchRatio INTEGER NOT NULL, " +
                        "dinnerRatio INTEGER NOT NULL, snackRatio INTEGER NOT NULL, " +
                        "dietType TEXT NOT NULL, maxPreparationMinutes INTEGER NOT NULL, " +
                        "cookingMode TEXT NOT NULL, budgetLevel TEXT NOT NULL, " +
                        "preferredFoods TEXT NOT NULL, recommendationDiversity TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS user_excluded_foods (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "normalizedFoodName TEXT NOT NULL, exclusionType TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_user_excluded_foods_normalizedFoodName_exclusionType " +
                        "ON user_excluded_foods(normalizedFoodName, exclusionType)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS meal_templates (" +
                        "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, supportedMealTypes TEXT NOT NULL, " +
                        "totalKcal INTEGER NOT NULL, proteinGrams REAL, carbohydrateGrams REAL, fatGrams REAL, " +
                        "preparationMinutes INTEGER NOT NULL, costLevel TEXT NOT NULL, tags TEXT NOT NULL, " +
                        "allergens TEXT NOT NULL, excludedDietTypes TEXT NOT NULL, cuisineType TEXT, " +
                        "source TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS meal_template_ingredients (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mealTemplateId TEXT NOT NULL, " +
                        "foodItemId TEXT NOT NULL, amount REAL NOT NULL, unit TEXT NOT NULL, " +
                        "adjustable INTEGER NOT NULL, minimumAmount REAL, maximumAmount REAL, adjustmentStep REAL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_meal_template_ingredients_mealTemplateId " +
                        "ON meal_template_ingredients(mealTemplateId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_meal_template_ingredients_foodItemId " +
                        "ON meal_template_ingredients(foodItemId)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS daily_meal_plans (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, localDate TEXT NOT NULL, " +
                        "targetKcalSnapshot INTEGER NOT NULL, planStatus TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_daily_meal_plans_localDate " +
                        "ON daily_meal_plans(localDate)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS planned_meals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dailyMealPlanId INTEGER NOT NULL, " +
                        "mealType TEXT NOT NULL, plannedKcal INTEGER NOT NULL, selectedTemplateId TEXT, " +
                        "status TEXT NOT NULL, consumedAt INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_meals_dailyMealPlanId ON planned_meals(dailyMealPlanId)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_planned_meals_dailyMealPlanId_mealType " +
                        "ON planned_meals(dailyMealPlanId, mealType)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recognition_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, requestId TEXT NOT NULL, " +
                        "sourceType TEXT NOT NULL, capturedAt INTEGER NOT NULL, status TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, completedAt INTEGER)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_recognition_sessions_requestId " +
                        "ON recognition_sessions(requestId)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recognition_candidates (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL, " +
                        "detectedName TEXT NOT NULL, matchedFoodItemId TEXT, estimatedAmount REAL, " +
                        "unit TEXT, estimatedKcal INTEGER, minimumKcal INTEGER, maximumKcal INTEGER, " +
                        "confidenceLevel TEXT, selected INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recognition_candidates_sessionId ON recognition_candidates(sessionId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recognition_candidates_matchedFoodItemId " +
                        "ON recognition_candidates(matchedFoodItemId)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_records ADD COLUMN portionPresetId TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN portionDisplayLabel TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN portionEstimationType TEXT")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN portionSourceReference TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_records ADD COLUMN carbohydrateGrams REAL")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN proteinGrams REAL")
                db.execSQL("ALTER TABLE meal_records ADD COLUMN fatGrams REAL")
            }
        }
    }
}
