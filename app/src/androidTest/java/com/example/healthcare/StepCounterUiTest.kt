package com.example.healthcare

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.domain.StepCounterAction
import com.example.healthcare.domain.StepCounterStatus
import com.example.healthcare.domain.StepCounterUiState
import com.example.healthcare.ui.screens.StepCounterCard
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StepCounterUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun availableStepsFitAt360DpWithSystemAndAppLargeText() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme(darkTheme = false, appFontScale = 1.3f) {
                    Box(Modifier.width(360.dp).height(700.dp)) {
                        StepCounterCard(
                            state = StepCounterUiState(
                                status = StepCounterStatus.AVAILABLE,
                                todaySteps = 6_428L,
                                lastUpdatedAtMillis = 1_790_200_920_000L,
                                message = "지금부터 걸음 수를 측정해요."
                            ),
                            onAction = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("오늘 걸음 수").assertIsDisplayed()
        composeRule.onNodeWithText("6,428").assertIsDisplayed()
        composeRule.onNodeWithText("걸음").assertIsDisplayed()
        composeRule.onNodeWithText("마지막 업데이트", substring = true).assertIsDisplayed()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = File(requireNotNull(context.getExternalFilesDir(null)), "step-counter-360-large.png")
        composeRule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            screenshot.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        assertTrue(screenshot.length() > 0L)
    }

    @Test fun permissionAndUnavailableStatesRemainActionable() {
        var actionCount = 0
        var state by mutableStateOf(
            StepCounterUiState(
                status = StepCounterStatus.PERMISSION_REQUIRED,
                action = StepCounterAction.REQUEST_PERMISSION
            )
        )
        composeRule.setContent {
            HealthCareTheme {
                StepCounterCard(
                    state = state,
                    onAction = { actionCount++ }
                )
            }
        }
        composeRule.onNodeWithText("걸음 수 사용하기").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, actionCount) }

        composeRule.runOnIdle { state = StepCounterUiState(StepCounterStatus.SENSOR_UNAVAILABLE) }
        composeRule.onNodeWithText("이 기기에서는 걸음 수 센서를 사용할 수 없어요.").assertIsDisplayed()

        composeRule.runOnIdle {
            state = StepCounterUiState(
                status = StepCounterStatus.PERMISSION_DENIED,
                action = StepCounterAction.OPEN_SETTINGS
            )
        }
        composeRule.onNodeWithText("걸음 수 권한이 꺼져 있어요.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("권한 설정").assertIsDisplayed()
    }

    @Test fun stepsRemainSeparateFromCalorieTarget() {
        composeRule.setContent {
            HealthCareTheme(darkTheme = false) {
                Box(Modifier.width(390.dp).height(900.dp)) {
                    DashboardContent(
                        selectedDate = LocalDate.now(),
                        meals = emptyList(),
                        totalCalories = 0,
                        targetCalories = 2_200,
                        statusText = "목표까지 2,200kcal",
                        energyState = DashboardEnergyUiState(),
                        onPreviousDay = {},
                        onNextDay = {},
                        onAddRecord = {},
                        onOpenEnergySettings = {},
                        stepCounterState = StepCounterUiState(
                            status = StepCounterStatus.AVAILABLE,
                            todaySteps = 8_000L
                        )
                    )
                }
            }
        }

        composeRule.onNodeWithText("/ 2,200 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("8,000 걸음").assertIsDisplayed()
        assertTrue(composeRule.onAllNodes(hasScrollAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes().isEmpty())
    }
}
