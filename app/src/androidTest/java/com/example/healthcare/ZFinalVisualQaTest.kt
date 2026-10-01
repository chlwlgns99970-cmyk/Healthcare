package com.example.healthcare

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.BodyProfileStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodySex
import com.example.healthcare.domain.EnergyBalanceCalculator
import com.example.healthcare.domain.DailyRecommendationTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZFinalVisualQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var app: HealthcareApplication
    private lateinit var database: AppDatabase
    private val fixtureDate = LocalDate.now().toString()
    private val fixtureMealName = "운동 환산 최종 QA ${System.nanoTime()}"
    private var originalTodayMeals = emptyList<MealRecord>()
    private var originalGoals = emptyList<CalorieGoal>()
    private var originalEnergyProfiles = emptyList<EnergyProfileHistory>()
    private var originalTodayProfile: EnergyProfileHistory? = null
    private var preferenceSnapshots: Map<String, Map<String, *>> = emptyMap()
    private var fixtureGoalId: Long? = null
    private var fixtureProfileId: Long? = null
    private var fixturePrepared = false

    @Before
    fun prepareIsolatedQaFixture() = runBlocking {
        assertEquals(QA_APPLICATION_ID, BuildConfig.APPLICATION_ID)
        assertEquals(QA_APPLICATION_ID, targetContext.packageName)
        app = targetContext.applicationContext as HealthcareApplication
        database = AppDatabase.getDatabase(targetContext)
        originalTodayMeals = app.mealRepository.getMealsByDate(fixtureDate).first()
        originalGoals = app.goalRepository.allGoals.first()
        originalEnergyProfiles = app.energyProfileRepository.allProfiles.first()
        originalTodayProfile = database.energyProfileDao().getProfileStartingOn(fixtureDate)
        preferenceSnapshots = listOf("body_profile", "exercise_coach", "recommendation_cycle_v2").associateWith { name ->
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues { (_, value) ->
                if (value is Set<*>) value.toSet() else value
            }
        }
        fixturePrepared = true
        originalTodayMeals.forEach { app.mealRepository.deleteMeal(it) }
        app.goalRepository.insertGoal(CalorieGoal(targetCalories = TARGET_KCAL, startDate = fixtureDate))
        fixtureGoalId = requireNotNull(app.goalRepository.getGoalForDate(fixtureDate).first()).id
        app.energyProfileRepository.saveProfile(
            basalMetabolicRateKcal = 1_500, activityLevel = ActivityLevel.LIGHT,
            palMultiplier = 1.55, targetMode = TargetMode.MANUAL, effectiveFromDate = fixtureDate
        )
        val profile = requireNotNull(database.energyProfileDao().getProfileStartingOn(fixtureDate))
        fixtureProfileId = profile.profileId
        val goal = requireNotNull(app.goalRepository.getGoalForDate(fixtureDate).first())
        assertEquals(TARGET_KCAL, EnergyBalanceCalculator.resolveDailyTargetKcal(
            goal.targetCalories, profile.basalMetabolicRateKcal, profile.palMultiplier, profile.targetMode
        ))
        BodyProfileStore(targetContext).apply {
            save(BodyProfile(BodySex.MALE, ageYears = 35, heightCm = 175.0, weightKg = 70.0))
            setTasteSetupPending(false)
        }
        composeRule.activityRule.scenario.recreate()
        Unit
    }

    @After
    fun restoreOriginalQaData() {
        if (!fixturePrepared) return
        // Stop this activity's collectors before restoring the rows and preference files.
        composeRule.activityRule.scenario.close()
        try {
            runBlocking {
                database.withTransaction {
                    app.mealRepository.getMealsByDate(fixtureDate).first()
                        .filter { it.foodName == fixtureMealName }
                        .forEach { app.mealRepository.deleteMeal(it) }
                    originalTodayMeals.forEach { app.mealRepository.insertMeal(it) }
                    fixtureGoalId?.let { id ->
                        database.openHelper.writableDatabase.execSQL("DELETE FROM calorie_goals WHERE id = ?", arrayOf(id))
                    }
                    val original = originalTodayProfile
                    if (original != null) database.energyProfileDao().upsertProfile(original)
                    else fixtureProfileId?.let { id ->
                        database.openHelper.writableDatabase.execSQL("DELETE FROM energy_profile_history WHERE profileId = ?", arrayOf(id))
                    }
                }
                assertEquals(originalTodayMeals.toSet(), app.mealRepository.getMealsByDate(fixtureDate).first().toSet())
                assertEquals(originalGoals.toSet(), app.goalRepository.allGoals.first().toSet())
                assertEquals(originalEnergyProfiles.toSet(), app.energyProfileRepository.allProfiles.first().toSet())
            }
        } finally {
            preferenceSnapshots.forEach { (name, values) ->
                val editor = targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                values.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                assertTrue("Could not restore $name", editor.commit())
            }
        }
    }

    @Test
    fun capturesRecommendationAndCompleteExerciseFlowOnQaDevice() {
        assertEquals(QA_APPLICATION_ID, BuildConfig.APPLICATION_ID)
        assertEquals(QA_APPLICATION_ID, targetContext.packageName)

        waitFor(hasText("오늘 활동"))
        composeRule.onNodeWithTag("exercise-over-character", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("exercise-coach-cta").assertDoesNotExist()
        saveRootScreenshot("dashboard-under-target.png")

        composeRule.onNodeWithText("추천").performClick()
        waitFor(hasText("오늘은 어떻게 먹고 싶나요?"))
        composeRule.onNodeWithTag("daily-plan-total").assertDoesNotExist()
        val dailyMeals = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
        dailyMeals.forEach { meal ->
            composeRule.onNodeWithTag("daily-meal-${meal.name}").assertDoesNotExist()
        }
        composeRule.onAllNodes(hasContentDescription("추천 이미지", substring = true), useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("daily-plan-list")
            .performScrollToNode(hasTestTag("daily-theme-DIET"))
        composeRule.onNodeWithTag("daily-theme-DIET").performClick()
        composeRule.onNodeWithTag("daily-theme-DIET").assertDoesNotExist()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            app.todayMealPlanRepository.currentPlan.value?.let {
                it.theme == DailyRecommendationTheme.DIET && it.targetKcal == TARGET_KCAL
            } == true
        }
        composeRule.onNodeWithTag("daily-plan-list")
            .performScrollToNode(hasTestTag("daily-meal-BREAKFAST"))
        waitFor(hasContentDescription("추천 이미지", substring = true), useUnmergedTree = true)
        val plan = requireNotNull(app.todayMealPlanRepository.currentPlan.value)
        assertEquals(DailyRecommendationTheme.DIET, plan.theme)
        assertEquals(TARGET_KCAL, plan.targetKcal)
        assertEquals(dailyMeals, plan.meals.map { it.mealType })
        composeRule.onNodeWithTag("daily-plan-list")
            .performScrollToNode(hasTestTag("daily-plan-total"))
        composeRule.onNodeWithTag("daily-plan-total")
            .assertTextEquals("총 ${plan.totalKcal} / $TARGET_KCAL kcal")
        dailyMeals.forEach { meal ->
            val tag = "daily-meal-${meal.name}"
            composeRule.onNodeWithTag("daily-plan-list").performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            composeRule.onNode(hasContentDescription("추천 이미지", substring = true) and
                hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("daily-plan-list")
            .performScrollToNode(hasTestTag("daily-meal-BREAKFAST"))
        saveRootScreenshot("recommendation-images-final.png")

        runBlocking {
            app.mealRepository.insertMeal(
                MealRecord(
                    date = fixtureDate,
                    time = "12:00",
                    mealType = MealType.LUNCH,
                    foodName = fixtureMealName,
                    calories = OVER_TARGET_INTAKE
                )
            )
        }
        composeRule.onNodeWithText("홈").performClick()
        waitFor(hasTestTag("exercise-coach-cta"))
        composeRule.onNodeWithText("오늘 목표보다 약 180 kcal 많아요").assertIsDisplayed()
        composeRule.onNodeWithTag("exercise-over-character", useUnmergedTree = true)
            .assertExists()
        saveRootScreenshot("dashboard-over-target-character.png")

        composeRule.onNodeWithTag("exercise-coach-cta").performClick()
        waitFor(hasText("활동으로 환산해 보기"))
        composeRule.onNodeWithText("오늘 초과분").assertIsDisplayed()
        composeRule.onNodeWithText("약 180 kcal").assertIsDisplayed()

        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("exercise-100-section"))
        composeRule.onNodeWithTag("exercise-100-section").assertExists()
        saveRootScreenshot("exercise-100kcal.png")

        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("exercise-excess-section"))
        composeRule.onNodeWithTag("exercise-excess-section").assertExists()
        saveRootScreenshot("exercise-excess-kcal.png")
    }

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher, useUnmergedTree: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun saveRootScreenshot(fileName: String) {
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertEquals(QA_APPLICATION_ID, activity.packageName)
            assertTrue("PRIVACY_GUARD_TRIGGERED", activity.hasWindowFocus())
        }
        val bitmap = composeRule.onNode(isRoot()).captureToImage().asAndroidBitmap()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/HealthcareQaEvidence"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = targetContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull("Could not create $fileName", uri)
        requireNotNull(resolver.openOutputStream(requireNotNull(uri))) {
            "Could not open $fileName"
        }.use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private companion object {
        const val QA_APPLICATION_ID = "com.example.healthcare.qa"
        const val TARGET_KCAL = 2_000
        const val OVER_TARGET_INTAKE = 2_180
        const val TIMEOUT_MILLIS = 30_000L
    }
}
