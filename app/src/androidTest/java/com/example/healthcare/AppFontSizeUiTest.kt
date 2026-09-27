package com.example.healthcare

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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.AppFontSize
import com.example.healthcare.data.AppFontSizeStore
import com.example.healthcare.ui.screens.SettingsContent
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.EnergySettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppFontSizeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalLargeAndExtraLargeApplyImmediatelyAndRemainScrollableAt360Dp() {
        var selected by mutableStateOf(AppFontSize.NORMAL)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                HealthCareTheme(appFontScale = selected.scale) {
                    Box(Modifier.width(360.dp).height(800.dp)) {
                        SettingsContent(
                            targetCalories = 2000,
                            energyState = EnergySettingsUiState(),
                            appFontSize = selected,
                            onGoalClick = {},
                            onBmrChange = {},
                            onActivityLevelSelected = {},
                            onCustomPalChange = {},
                            onTargetModeSelected = {},
                            onSaveEnergyProfile = {},
                            onAppFontSizeSelected = { selected = it }
                        )
                    }
                }
            }
        }

        scrollTo("보통")
        composeRule.onNodeWithText("보통").assertIsSelected()
        composeRule.onNodeWithText("크게").performClick().assertIsSelected()
        composeRule.onNodeWithText("아주 크게").performClick().assertIsSelected()
        scrollTo("일일 목표 칼로리")
        composeRule.onNodeWithText("일일 목표 칼로리").assertIsDisplayed()
        scrollTo("하루 섭취량으로 예상해 보기")
        composeRule.onNodeWithText("하루 섭취량으로 예상해 보기").assertIsDisplayed()
    }

    @Test
    fun selectedFontSizePersistsAcrossStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "font-size-test-${System.nanoTime()}"
        try {
            AppFontSizeStore(context, preferencesName).set(AppFontSize.EXTRA_LARGE)
            assertEquals(
                AppFontSize.EXTRA_LARGE,
                AppFontSizeStore(context, preferencesName).fontSize.value
            )
        } finally {
            context.deleteSharedPreferences(preferencesName)
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    }
}
