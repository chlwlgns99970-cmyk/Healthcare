package com.example.healthcare

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.healthcare.domain.NutritionBasisCandidate
import com.example.healthcare.domain.NutritionLabelParseResult
import com.example.healthcare.ui.screens.SmartFoodInputScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.SmartInputMode
import com.example.healthcare.ui.viewmodel.SmartInputUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OcrPortionUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun ocrResultOffersPackageFractionAndUnknownFlowBeforeNumericWeight() {
        var unknownClicks = 0
        composeRule.setContent {
            HealthCareTheme {
                SmartFoodInputScreen(
                    state = SmartInputUiState(
                        mode = SmartInputMode.OCR_RESULT,
                        ocrResult = NutritionLabelParseResult(totalAmount = 80.0, totalAmountUnit = "g",
                            energyCandidates = listOf(NutritionBasisCandidate(100.0, "g", 320.0, "100g당"))),
                        selectedOcrCandidateIndex = 0
                    ),
                    recentMeals = emptyList(), onBack = {}, onPhoto = {}, onBarcode = {},
                    onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = {},
                    onFoodSelected = {}, onUseBarcodeItem = {}, onOcrCandidateSelected = {},
                    onOcrAmountChange = {}, onConfirmOcr = {}, onManual = {},
                    onRegisterBarcode = {}, onRepeatRecent = {},
                    onOcrUnknown = { unknownClicks++ }
                )
            }
        }
        composeRule.onNodeWithText("직접 양 입력").assertDoesNotExist()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("양을 잘 모르겠어요 · 그릇으로 선택"))
        composeRule.onNodeWithText("양을 잘 모르겠어요 · 그릇으로 선택").performClick()
        assertEquals(1, unknownClicks)
    }
}
