package com.example.healthcare

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.MealCoachRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-1-2-test.db"

    @Before
    fun prepare() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun version1DataIsPreservedThroughVersion6AndCoachTablesAreCreated() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS meal_records (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "date TEXT NOT NULL, time TEXT NOT NULL, mealType TEXT NOT NULL, " +
                            "foodName TEXT NOT NULL, calories INTEGER NOT NULL, memo TEXT)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS calorie_goals (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "targetCalories INTEGER NOT NULL, startDate TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS frequent_foods (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "foodName TEXT NOT NULL, defaultServing TEXT NOT NULL, " +
                            "calories INTEGER NOT NULL, isFavorite INTEGER NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO meal_records(date, time, mealType, foodName, calories, memo) " +
                    "VALUES('2026-09-01', '12:00', 'LUNCH', '기존 기록', 500, NULL)"
            )
            helper.writableDatabase.execSQL(
                "INSERT INTO calorie_goals(targetCalories, startDate) VALUES(2000, '2026-09-01')"
            )
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .allowMainThreadQueries()
            .build()
        try {
            database.openHelper.writableDatabase.query(
                "SELECT foodName, calories, source, photoRequestId, plannedMealId, foodItemId, " +
                    "portionDisplayLabel, carbohydrateGrams, proteinGrams, fatGrams FROM meal_records"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("기존 기록", cursor.getString(0))
                assertEquals(500, cursor.getInt(1))
                assertEquals("MANUAL", cursor.getString(2))
                assertNull(cursor.getString(3))
                assertNull(cursor.getString(4))
                assertNull(cursor.getString(5))
                assertNull(cursor.getString(6))
                assertNull(cursor.getString(7))
                assertNull(cursor.getString(8))
                assertNull(cursor.getString(9))
            }
            database.openHelper.writableDatabase.query(
                "SELECT targetCalories, startDate FROM calorie_goals"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(2000, cursor.getInt(0))
                assertEquals("2026-09-01", cursor.getString(1))
            }
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO energy_profile_history(" +
                    "basalMetabolicRateKcal, activityLevelCode, palMultiplier, targetMode, " +
                    "effectiveFromDate, createdAt, updatedAt) " +
                    "VALUES(1500, 'LIGHT', 1.55, 'MANUAL', '2026-09-15', 1, 1)"
            )
            database.openHelper.writableDatabase.query(
                "SELECT basalMetabolicRateKcal, targetMode FROM energy_profile_history"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1500, cursor.getInt(0))
                assertEquals("MANUAL", cursor.getString(1))
            }
            listOf(
                "food_items",
                "user_meal_preferences",
                "user_excluded_foods",
                "meal_templates",
                "meal_template_ingredients",
                "daily_meal_plans",
                "planned_meals",
                "recognition_sessions",
                "recognition_candidates"
            ).forEach { table ->
                database.openHelper.writableDatabase.query(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                    arrayOf(table)
                ).use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("$table table", 1, cursor.getInt(0))
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun photoItemsUseCompositeIdentityAndConflictingBatchRollsBack() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val firstBatch = listOf(
                photoMeal("request-1", "item-1", "비빔밥"),
                photoMeal("request-1", "item-2", "김치")
            )
            database.mealRecordDao().insertPhotoMeals(firstBatch)

            var conflictThrown = false
            try {
                database.mealRecordDao().insertPhotoMeals(
                    listOf(
                        photoMeal("request-1", "item-3", "국"),
                        photoMeal("request-1", "item-1", "중복")
                    )
                )
            } catch (_: Exception) {
                conflictThrown = true
            }

            val count = database.openHelper.writableDatabase.query(
                "SELECT COUNT(*) FROM meal_records WHERE photoRequestId = 'request-1'"
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(true, conflictThrown)
            assertEquals(2, count)
        } finally {
            database.close()
        }
    }

    @Test
    fun energyProfilesApplyByDateAndSameDaySaveDoesNotDuplicateHistory() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = EnergyProfileRepository(database.energyProfileDao())
            repository.saveProfile(
                basalMetabolicRateKcal = 1500,
                activityLevel = ActivityLevel.LIGHT,
                palMultiplier = 1.55,
                targetMode = TargetMode.MANUAL,
                effectiveFromDate = "2026-09-15",
                nowEpochMillis = 1
            )
            repository.saveProfile(
                basalMetabolicRateKcal = 1600,
                activityLevel = ActivityLevel.MODERATE,
                palMultiplier = 1.75,
                targetMode = TargetMode.MAINTENANCE,
                effectiveFromDate = "2026-09-16",
                nowEpochMillis = 2
            )
            repository.saveProfile(
                basalMetabolicRateKcal = 1650,
                activityLevel = ActivityLevel.MODERATE,
                palMultiplier = 1.75,
                targetMode = TargetMode.BMR,
                effectiveFromDate = "2026-09-16",
                nowEpochMillis = 3
            )

            assertEquals(
                1500,
                repository.getProfileForDate("2026-09-15").first()?.basalMetabolicRateKcal
            )
            assertEquals(
                1650,
                repository.getProfileForDate("2026-09-16").first()?.basalMetabolicRateKcal
            )
            val count = database.openHelper.writableDatabase.query(
                "SELECT COUNT(*) FROM energy_profile_history"
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(2, count)
        } finally {
            database.close()
        }
    }

    @Test
    fun recommendationPlanIsNotIntakeUntilConfirmedAndCannotBeRecordedTwice() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val coachDao = database.mealCoachDao()
            coachDao.upsertTemplates(
                listOf(
                    MealTemplate(
                        id = "verified-lunch",
                        name = "검증 식단",
                        supportedMealTypes = "|LUNCH|",
                        totalKcal = 642,
                        preparationMinutes = 10,
                        costLevel = "MEDIUM",
                        source = "TEST_VERIFIED",
                        createdAt = 1,
                        updatedAt = 1
                    )
                )
            )
            val repository = MealCoachRepository(
                database,
                coachDao,
                database.foodItemDao(),
                database.mealRecordDao()
            )
            val preference = repository.ensureDefaultPreference()
            val (_, meals) = repository.ensureDailyPlan(LocalDate.of(2026, 9, 17), 2000, preference)
            val lunch = requireNotNull(meals.firstOrNull { it.mealType == "LUNCH" })

            assertEquals(null, database.mealRecordDao().getTotalCaloriesByDate("2026-09-17").first())
            repository.selectTemplate(lunch.id, "verified-lunch")
            repository.confirmConsumed(lunch.id, LocalDate.of(2026, 9, 17), LocalTime.NOON, 642)

            var duplicateRejected = false
            try {
                repository.confirmConsumed(lunch.id, LocalDate.of(2026, 9, 17), LocalTime.NOON, 642)
            } catch (_: IllegalStateException) {
                duplicateRejected = true
            }
            assertEquals(true, duplicateRejected)
            assertEquals(642, database.mealRecordDao().getTotalCaloriesByDate("2026-09-17").first())
        } finally {
            database.close()
        }
    }

    @Test
    fun legacyLinkedNutritionIsDerivedButUnlinkedHistoryStaysUnknown() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.foodItemDao().upsertAll(listOf(
                FoodItem(
                    id = "legacy-food", sourceType = "TEST", sourceFoodCode = "legacy-food",
                    name = "연결 음식", normalizedName = "연결음식", referenceAmount = 100.0,
                    unit = "g", energyKcal = 400.0, carbohydrateGrams = 80.0,
                    proteinGrams = 10.0, fatGrams = 16.0, servingDescription = "100g 기준",
                    dataVersion = "test", createdAt = 0, updatedAt = 0
                )
            ))
            database.mealRecordDao().insertMeal(
                MealRecord(date = "2026-09-23", time = "12:00", mealType = MealType.LUNCH,
                    foodName = "연결 음식", calories = 200, foodItemId = "legacy-food")
            )
            database.mealRecordDao().insertMeal(
                MealRecord(date = "2026-09-23", time = "13:00", mealType = MealType.LUNCH,
                    foodName = "연결 불가 기록", calories = 100)
            )

            val rows = database.mealRecordDao().getNutritionByDate("2026-09-23").first()
            assertEquals(40.0, rows[0].carbohydrateGrams!!, 0.0001)
            assertEquals(5.0, rows[0].proteinGrams!!, 0.0001)
            assertEquals(8.0, rows[0].fatGrams!!, 0.0001)
            assertNull(rows[1].carbohydrateGrams)
            assertNull(rows[1].proteinGrams)
            assertNull(rows[1].fatGrams)
        } finally {
            database.close()
        }
    }

    @Test
    fun repeatedSameDayGoalsAlwaysReturnTheNewestChange() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.calorieGoalDao()
            dao.insertGoal(CalorieGoal(targetCalories = 2000, startDate = "2026-09-24"))
            dao.insertGoal(CalorieGoal(targetCalories = 1800, startDate = "2026-09-24"))
            dao.insertGoal(CalorieGoal(targetCalories = 2100, startDate = "2026-09-24"))

            assertEquals(2100, dao.getLatestGoal().first()?.targetCalories)
            assertEquals(2100, dao.getGoalForDate("2026-09-24").first()?.targetCalories)
            assertEquals(2100, dao.getAllGoals().first().first().targetCalories)
        } finally {
            database.close()
        }
    }

    private fun photoMeal(requestId: String, itemId: String, name: String) = MealRecord(
        date = "2026-09-12",
        time = "12:00",
        mealType = MealType.LUNCH,
        foodName = name,
        calories = 100,
        source = RecordSource.PHOTO_AI,
        photoRequestId = requestId,
        photoItemId = itemId
    )
}
