package com.example.healthcare

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.domain.FoodPreferencePolicy
import com.example.healthcare.domain.FoodPreferenceStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses named fixture databases only; the installed app's database is never opened. */
@RunWith(AndroidJUnit4::class)
class RecommendationPreferenceMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseNames = listOf(
        "recommendation-preference-migration-6-test.db",
        "recommendation-preference-migration-7-test.db"
    )

    @Before fun prepare() { databaseNames.forEach(context::deleteDatabase) }
    @After fun cleanUp() { databaseNames.forEach(context::deleteDatabase) }

    @Test
    fun version6To8PreservesPreferencesExclusionsRecordsGoalsAndRecommendationData() = runBlocking {
        val storedPreference = "|된장찌개|사과|STYLE:RICE|STYLE:KOREAN|"
        val snapshot = createFixture(databaseNames[0], 6, storedPreference)
        val database = openVersion8(databaseNames[0])
        try {
            assertPreserved(database, snapshot, storedPreference)
            val foods = database.frequentFoodDao().getAllFoods().first()
            assertEquals(listOf(31L, 32L), foods.map { it.id }.sorted())
            foods.forEach { food ->
                assertFalse(food.isFavorite)
                assertTrue(food.isFrequent)
                assertNull(food.carbohydrateGrams)
                assertNull(food.proteinGrams)
                assertNull(food.fatGrams)
                assertNull(food.foodItemId)
                assertNull(food.sourceType)
                assertNull(food.sourceFoodCode)
                assertNull(food.brand)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun version7To8KeepsLegacyFoodsAndMacrosAndNewFavoritesHaveStableIndependentIdentity() = runBlocking {
        val storedPreference = "|비빔밥|STYLE:NOODLE|"
        val snapshot = createFixture(databaseNames[1], 7, storedPreference)
        var database = openVersion8(databaseNames[1])
        try {
            assertPreserved(database, snapshot, storedPreference)
            val foods = database.frequentFoodDao().getAllFoods().first()
            foods.forEach { food ->
                // v7's automatic 'saved food' flag is not a new explicit favorite.
                assertFalse(food.isFavorite)
                assertTrue(food.isFrequent)
                assertNull(food.foodItemId)
                assertEquals(35.0, food.carbohydrateGrams!!, 0.0)
                assertEquals(12.0, food.proteinGrams!!, 0.0)
                assertEquals(6.0, food.fatGrams!!, 0.0)
            }
            database.frequentFoodDao().insertFood(FrequentFood(
                foodName = "같은 이름 김밥", defaultServing = "180g", calories = 420,
                isFavorite = true, isFrequent = false, foodItemId = "fixture-product-a",
                sourceType = "TEST_PRODUCT", sourceFoodCode = "product-a", brand = "브랜드 A"
            ))
            database.frequentFoodDao().insertFood(FrequentFood(
                foodName = "같은 이름 김밥", defaultServing = "200g", calories = 450,
                isFavorite = false, isFrequent = true, foodItemId = "fixture-product-b",
                sourceType = "TEST_PRODUCT", sourceFoodCode = "product-b", brand = "브랜드 B"
            ))
        } finally {
            database.close()
        }

        database = openVersion8(databaseNames[1])
        try {
            assertPreserved(database, snapshot, storedPreference)
            val foods = database.frequentFoodDao().getAllFoods().first()
            val products = foods.filter { it.foodName == "같은 이름 김밥" }
            assertEquals(2, products.size)
            assertEquals(setOf("fixture-product-a", "fixture-product-b"), products.map { it.foodItemId }.toSet())
            assertEquals(setOf("브랜드 A", "브랜드 B"), products.map { it.brand }.toSet())
            assertEquals("fixture-product-a", database.frequentFoodDao().getFavoriteFoods().first().single().foodItemId)
            assertFalse(products.single { it.foodItemId == "fixture-product-a" }.isFrequent)
            assertTrue(products.single { it.foodItemId == "fixture-product-b" }.isFrequent)
            assertEquals(4, foods.size)
        } finally {
            database.close()
        }
    }

    private fun createFixture(name: String, version: Int, preferredFoods: String): FixtureSnapshot {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createVersion1Schema(db)
                    listOf(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                        AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
                        AppDatabase.MIGRATION_5_6).forEach { it.migrate(db) }
                    if (version == 7) AppDatabase.MIGRATION_6_7.migrate(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val db = helper.writableDatabase
            seedFixture(db, version, preferredFoods)
            FixtureSnapshot(readPreservedTables(db), readLegacyFoodValues(db))
        }
    }

    private fun createVersion1Schema(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE meal_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "date TEXT NOT NULL, time TEXT NOT NULL, mealType TEXT NOT NULL, foodName TEXT NOT NULL, calories INTEGER NOT NULL, memo TEXT)")
        db.execSQL("CREATE TABLE calorie_goals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "targetCalories INTEGER NOT NULL, startDate TEXT NOT NULL)")
        db.execSQL("CREATE TABLE frequent_foods (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "foodName TEXT NOT NULL, defaultServing TEXT NOT NULL, calories INTEGER NOT NULL, isFavorite INTEGER NOT NULL)")
    }

    private fun seedFixture(db: SupportSQLiteDatabase, version: Int, preferredFoods: String) {
        db.execSQL("INSERT INTO meal_records(id,date,time,mealType,foodName,calories,memo,servingAmount,servingUnit," +
            "source,foodItemId,portionPresetId,portionDisplayLabel,portionEstimationType,portionSourceReference," +
            "carbohydrateGrams,proteinGrams,fatGrams,createdAtEpochMillis,updatedAtEpochMillis) " +
            "VALUES(20,'2026-09-24','12:30','LUNCH','보존할 식사',520,'기존 메모',180,'g','FOOD_SEARCH'," +
            "'fixture-food','regular','보통 1인분','REFERENCE_SERVING','fixture-reference',65,18,7,100,200)")
        db.execSQL("INSERT INTO calorie_goals(id,targetCalories,startDate) VALUES(10,1850,'2026-09-24')")
        db.execSQL("INSERT INTO frequent_foods(id,foodName,defaultServing,calories,isFavorite) VALUES(31,'기존 저장 음식','1인분',300,1)")
        db.execSQL("INSERT INTO frequent_foods(id,foodName,defaultServing,calories,isFavorite) VALUES(32,'기존 일반 음식','100g',150,0)")
        if (version == 7) db.execSQL("UPDATE frequent_foods SET carbohydrateGrams=35,proteinGrams=12,fatGrams=6")
        db.execSQL("INSERT INTO energy_profile_history(profileId,basalMetabolicRateKcal,activityLevelCode,palMultiplier," +
            "targetMode,effectiveFromDate,createdAt,updatedAt) VALUES(11,1450,'LIGHT',1.55,'MANUAL','2026-09-24',100,200)")
        db.execSQL("INSERT INTO user_meal_preferences(id,mealScheduleType,breakfastEnabled,lunchEnabled,dinnerEnabled,snackEnabled," +
            "breakfastRatio,lunchRatio,dinnerRatio,snackRatio,dietType,maxPreparationMinutes,cookingMode,budgetLevel," +
            "preferredFoods,recommendationDiversity,createdAt,updatedAt) " +
            "VALUES(1,'FOUR_MEALS',1,1,1,1,20,40,30,10,'GENERAL',25,'COOK','LOW',?,'VARIED',100,200)",
            arrayOf(preferredFoods))
        db.execSQL("INSERT INTO user_excluded_foods(id,normalizedFoodName,exclusionType,createdAt) VALUES(101,'오이','DISLIKE',100)")
        db.execSQL("INSERT INTO user_excluded_foods(id,normalizedFoodName,exclusionType,createdAt) VALUES(102,'대두','ALLERGY',200)")
        db.execSQL("INSERT INTO food_items(id,sourceType,sourceFoodCode,name,normalizedName,aliases,category,referenceAmount,unit," +
            "energyKcal,carbohydrateGrams,proteinGrams,fatGrams,sodiumMilligrams,servingDescription,brand,barcode,dataVersion,createdAt,updatedAt) " +
            "VALUES('fixture-food','TEST','fixture-food','보존 밥','보존밥','','밥류',180,'g',520,65,18,7,500,'180g 기준',NULL,NULL,'fixture',100,200)")
        db.execSQL("INSERT INTO meal_templates(id,name,supportedMealTypes,totalKcal,proteinGrams,carbohydrateGrams,fatGrams," +
            "preparationMinutes,costLevel,tags,allergens,excludedDietTypes,cuisineType,source,createdAt,updatedAt) " +
            "VALUES('fixture-template','보존 추천','|LUNCH|',520,18,65,7,15,'LOW','|COOK|INGREDIENTS_COMPLETE|'," +
            "'|대두|','','KOREAN','TEST_VERIFIED',100,200)")
        db.execSQL("INSERT INTO meal_template_ingredients(id,mealTemplateId,foodItemId,amount,unit,adjustable,minimumAmount,maximumAmount,adjustmentStep) " +
            "VALUES(40,'fixture-template','fixture-food',180,'g',1,90,270,10)")
        db.execSQL("INSERT INTO daily_meal_plans(id,localDate,targetKcalSnapshot,planStatus,createdAt,updatedAt) " +
            "VALUES(50,'2026-09-24',1850,'ACTIVE',100,200)")
        db.execSQL("INSERT INTO planned_meals(id,dailyMealPlanId,mealType,plannedKcal,selectedTemplateId,status,consumedAt,createdAt,updatedAt) " +
            "VALUES(60,50,'LUNCH',740,'fixture-template','SELECTED',NULL,100,200)")
        db.execSQL("INSERT INTO recognition_sessions(id,requestId,sourceType,capturedAt,status,createdAt,completedAt) " +
            "VALUES(70,'fixture-request','FOOD_PHOTO',100,'COMPLETED',100,200)")
        db.execSQL("INSERT INTO recognition_candidates(id,sessionId,detectedName,matchedFoodItemId,estimatedAmount,unit,estimatedKcal," +
            "minimumKcal,maximumKcal,confidenceLevel,selected) VALUES(80,70,'보존 밥','fixture-food',180,'g',520,500,550,'HIGH',1)")
    }

    private fun openVersion8(name: String): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
        .allowMainThreadQueries().build()

    private suspend fun assertPreserved(database: AppDatabase, snapshot: FixtureSnapshot, preferredFoods: String) {
        val db = database.openHelper.writableDatabase // Opening verifies the Room v8 schema.
        db.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(8, cursor.getInt(0))
        }
        assertEquals(snapshot.tables, readPreservedTables(db))
        assertEquals(snapshot.legacyFoodValues, readLegacyFoodValues(db))
        val preference = requireNotNull(database.mealCoachDao().getPreference())
        assertEquals(preferredFoods, preference.preferredFoods)
        assertTrue(FoodPreferencePolicy.keywordsFromStored(preference.preferredFoods).isNotEmpty())
        val expectedStyles = if (preferredFoods.contains("STYLE:NOODLE")) setOf(FoodPreferenceStyle.NOODLE)
            else setOf(FoodPreferenceStyle.RICE, FoodPreferenceStyle.KOREAN)
        assertEquals(expectedStyles, FoodPreferencePolicy.stylesFromStored(preference.preferredFoods))
        assertEquals(setOf("오이" to "DISLIKE", "대두" to "ALLERGY"),
            database.mealCoachDao().getExcludedFoods().map { it.normalizedFoodName to it.exclusionType }.toSet())
        assertEquals(520, database.mealRecordDao().getAllMeals().first().single().calories)
        assertEquals(1850, database.calorieGoalDao().getLatestGoal().first()?.targetCalories)
    }

    private fun readPreservedTables(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> = listOf(
        "meal_records", "calorie_goals", "energy_profile_history", "user_meal_preferences", "user_excluded_foods",
        "food_items", "meal_templates", "meal_template_ingredients", "daily_meal_plans", "planned_meals",
        "recognition_sessions", "recognition_candidates"
    ).associateWith { table -> readRows(db, "SELECT * FROM $table ORDER BY 1") }

    private fun readLegacyFoodValues(db: SupportSQLiteDatabase): List<List<String?>> = readRows(db,
        "SELECT id,foodName,defaultServing,calories FROM frequent_foods WHERE id IN (31,32) ORDER BY id")

    private fun readRows(db: SupportSQLiteDatabase, sql: String): List<List<String?>> = db.query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                if (cursor.isNull(index)) null else cursor.getString(index)
            })
        }
    }

    private data class FixtureSnapshot(
        val tables: Map<String, List<List<String?>>>,
        val legacyFoodValues: List<List<String?>>
    )
}
