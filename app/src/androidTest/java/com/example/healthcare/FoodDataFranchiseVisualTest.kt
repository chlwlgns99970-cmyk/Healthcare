package com.example.healthcare

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.repository.MealRecommendationWithIngredients
import com.example.healthcare.data.update.FoodDataUpdateState
import com.example.healthcare.data.update.FoodDataUpdateStatus
import com.example.healthcare.domain.FranchiseCatalog
import com.example.healthcare.domain.ScoredMealRecommendation
import com.example.healthcare.ui.screens.FoodDataUpdateCard
import com.example.healthcare.ui.screens.ReferenceRecommendationLayout
import com.example.healthcare.ui.screens.SmartFoodInputScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.FoodSearchMode
import com.example.healthcare.ui.viewmodel.MealPlanUiState
import com.example.healthcare.ui.viewmodel.SmartInputMode
import com.example.healthcare.ui.viewmodel.SmartInputUiState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodDataFranchiseVisualTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun captureFoodDataStatus() {
        rule.setContent {
            HealthCareTheme {
                Box(Modifier.fillMaxSize()) {
                    FoodDataUpdateCard(
                        FoodDataUpdateState(
                            activeVersion = "2026-08-28",
                            lastCheckedAt = System.currentTimeMillis(),
                            status = FoodDataUpdateStatus.SNAPSHOT_ONLY,
                            message = "새 공식 버전을 확인했지만 자동 파일 갱신은 공식 인증이 필요합니다"
                        ),
                        onCheckNow = {}
                    )
                }
            }
        }
        capture("food-data-update-status.png")
    }

    @Test fun captureFranchiseCategories() {
        var state by mutableStateOf(franchiseState())
        rule.setContent {
            HealthCareTheme {
                SmartFoodInputScreen(
                    state = state,
                    recentMeals = emptyList(),
                    onBack = {}, onPhoto = {}, onBarcode = {}, onNutritionLabel = {}, onSearch = {},
                    onSearchQueryChange = {}, onFoodSelected = {}, onUseBarcodeItem = {},
                    onOcrCandidateSelected = {}, onOcrAmountChange = {}, onConfirmOcr = {},
                    onManual = {}, onRegisterBarcode = {}, onRepeatRecent = {}
                )
            }
        }
        capture("franchise-categories.png")
        listOf(
            "찜닭" to "franchise-jjimdak.png",
            "국밥" to "franchise-gukbap.png",
            "마라탕" to "franchise-malatang.png",
            "돈가스" to "franchise-tonkatsu.png",
            "토스트" to "franchise-toast.png"
        ).forEach { (category, fileName) ->
            rule.runOnIdle { state = franchiseState(category) }
            capture(fileName)
        }
    }

    @Test fun captureRecommendationIngredientAndAllergenStates() {
        var state by mutableStateOf(recommendationState(matchedAllergens = setOf("우유")))
        rule.setContent {
            HealthCareTheme {
                ReferenceRecommendationLayout(state, onSelect = {})
            }
        }
        rule.onNodeWithText("알레르기 주의 · 우유 포함").assertIsDisplayed()
        rule.onNodeWithText("알레르기 주의 · 우유·밀 포함").assertDoesNotExist()
        capture("recommendation-allergen-known.png")

        rule.runOnIdle { state = recommendationState(matchedAllergens = linkedSetOf("우유", "밀")) }
        rule.onNodeWithText("알레르기 주의 · 우유·밀 포함").assertIsDisplayed()
        capture("recommendation-allergen-multiple.png")

        rule.runOnIdle {
            state = recommendationState(
                matchedAllergens = emptySet(),
                ingredientInfoComplete = false,
                allergenInfoComplete = false,
                completeMacros = false
            )
        }
        rule.onNodeWithText("원재료 정보 일부 미확인").assertIsDisplayed()
        rule.onNodeWithText("알레르기 정보 일부 미확인").assertIsDisplayed()
        rule.onNodeWithText("일부 영양정보 없음 · 탄수화물·지방").assertIsDisplayed()
        capture("recommendation-ingredient-partial.png")

        rule.runOnIdle { state = recommendationState(matchedAllergens = emptySet()) }
        rule.onNodeWithText("원재료 정보 일부 미확인").assertDoesNotExist()
        rule.onNodeWithText("알레르기 정보 일부 미확인").assertDoesNotExist()
        capture("recommendation-ingredient-complete.png")
    }

    private fun recommendationState(
        matchedAllergens: Set<String>,
        ingredientInfoComplete: Boolean = true,
        allergenInfoComplete: Boolean = true,
        completeMacros: Boolean = true
    ): MealPlanUiState {
        val template = MealTemplate(
            id = "kfind-breakfast-cook-2",
            name = "카레라이스",
            supportedMealTypes = "|LUNCH|",
            totalKcal = 620,
            carbohydrateGrams = if (completeMacros) 88.0 else null,
            proteinGrams = 22.0,
            fatGrams = if (completeMacros) 18.0 else null,
            preparationMinutes = 20,
            costLevel = "MEDIUM",
            tags = "|COOK|",
            allergens = "|우유|밀|",
            excludedDietTypes = "",
            source = "K-FIND 2026-08-28",
            createdAt = 1L,
            updatedAt = 1L
        )
        val recommendation = MealRecommendationWithIngredients(
            recommendation = ScoredMealRecommendation(template, 100.0, 0, 80, "다음 한 끼의 참고 범위에 가까워요."),
            ingredients = emptyList(),
            ingredientNames = listOf("카레라이스"),
            declaredAllergens = setOf("우유", "밀"),
            matchedAllergens = matchedAllergens,
            ingredientInfoComplete = ingredientInfoComplete,
            allergenInfoComplete = allergenInfoComplete
        )
        return MealPlanUiState(recommendations = listOf(recommendation), hasLoaded = true)
    }

    @Test fun captureDistinctRecordHubImages() {
        rule.setContent {
            HealthCareTheme {
                SmartFoodInputScreen(
                    state = SmartInputUiState(mode = SmartInputMode.HUB),
                    recentMeals = emptyList(),
                    onBack = {}, onPhoto = {}, onBarcode = {}, onNutritionLabel = {}, onSearch = {},
                    onSearchQueryChange = {}, onFoodSelected = {}, onUseBarcodeItem = {},
                    onOcrCandidateSelected = {}, onOcrAmountChange = {}, onConfirmOcr = {},
                    onManual = {}, onRegisterBarcode = {}, onRepeatRecent = {}
                )
            }
        }
        listOf("음식 검색", "바코드 스캔", "사진으로 기록", "직접 입력").forEach { title ->
            rule.onNodeWithText(title).assertIsDisplayed()
        }
        capture("record-hub-distinct-images.png")
    }

    private fun franchiseState(category: String? = null): SmartInputUiState {
        val matches = FranchiseCatalog.entries.filter { category == null || it.category == category }
        return SmartInputUiState(
            mode = SmartInputMode.SEARCH,
            searchMode = FoodSearchMode.FRANCHISE,
            searchQuery = category.orEmpty(),
            brandResults = matches.map { FoodBrandSummary(it.name, 0) },
            selectedBrandCategory = category
        )
    }

    private fun capture(fileName: String) {
        rule.waitForIdle()
        val bitmap = rule.onNode(isRoot()).captureToImage().asAndroidBitmap()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HealthcareQaEvidence")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull(uri)
        requireNotNull(resolver.openOutputStream(requireNotNull(uri))).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
