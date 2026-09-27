package com.example.healthcare

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.BodyProfileStore
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodySex
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZFinalVisualQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun capturesRecommendationAndCompleteExerciseFlowOnQaDevice() {
        assertEquals(QA_APPLICATION_ID, BuildConfig.APPLICATION_ID)
        assertEquals(QA_APPLICATION_ID, targetContext.packageName)

        val app = targetContext.applicationContext as HealthcareApplication
        runBlocking {
            for (meal in app.mealRepository.allMeals.first()) {
                app.mealRepository.deleteMeal(meal)
            }
            app.goalRepository.insertGoal(
                CalorieGoal(targetCalories = TARGET_KCAL, startDate = LocalDate.now().toString())
            )
        }
        BodyProfileStore(targetContext).save(
            BodyProfile(BodySex.MALE, ageYears = 35, heightCm = 175.0, weightKg = 70.0)
        )
        composeRule.activityRule.scenario.recreate()

        waitFor(hasText("오늘 활동"))
        composeRule.onNodeWithTag("exercise-over-character", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("exercise-coach-cta").assertDoesNotExist()
        saveRootScreenshot("dashboard-under-target.png")

        composeRule.onNodeWithText("추천").performClick()
        waitFor(hasText("식사 추천"))
        waitFor(hasContentDescription("추천 이미지", substring = true), useUnmergedTree = true)
        saveRootScreenshot("recommendation-images-final.png")

        runBlocking {
            app.mealRepository.insertMeal(
                MealRecord(
                    date = LocalDate.now().toString(),
                    time = "12:00",
                    mealType = MealType.LUNCH,
                    foodName = "운동 환산 최종 QA",
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
