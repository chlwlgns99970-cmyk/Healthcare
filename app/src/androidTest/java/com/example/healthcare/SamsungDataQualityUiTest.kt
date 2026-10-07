package com.example.healthcare

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.repository.MealRecommendationWithIngredients
import com.example.healthcare.domain.FoodMetadata
import com.example.healthcare.domain.FoodMetadataPolicy
import com.example.healthcare.domain.FranchiseCatalog
import com.example.healthcare.domain.FranchiseMenu
import com.example.healthcare.domain.ScoredMealRecommendation
import com.example.healthcare.ui.components.RecordFoodMetadataNotice
import com.example.healthcare.ui.screens.MealPlanContent
import com.example.healthcare.ui.screens.SmartFoodInputScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.MealPlanUiState
import com.example.healthcare.ui.viewmodel.RecommendedIngredientUi
import com.example.healthcare.ui.viewmodel.SelectedMealUi
import com.example.healthcare.ui.viewmodel.SmartInputMode
import com.example.healthcare.ui.viewmodel.SmartInputUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Read-only generated data and isolated UI fixtures; no user records or preferences are changed. */
class SamsungDataQualityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var originalMetadata: Collection<FoodMetadata>

    @Before fun rememberGeneratedEvidence() {
        val application = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        runBlocking { application.foodMetadataStore.ensureLoaded() }
        originalMetadata = FoodMetadataPolicy.snapshot()
    }
    @After fun restoreGeneratedEvidence() { FoodMetadataPolicy.install(originalMetadata) }

    private fun compact(content: @Composable () -> Unit) {
        compose.setContent {
            HealthCareTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.30f)) {
                    Box(Modifier.width(360.dp).height(720.dp)) { content() }
                }
            }
        }
    }
    private fun scroll(matcher: SemanticsMatcher) = compose.onAllNodes(hasScrollAction()).onFirst()
        .performScrollToNode(matcher)

    private fun food(id: String, name: String = "검증 김밥") = FoodItem(id = id,
        sourceType = "K-FIND", sourceFoodCode = id, name = name, normalizedName = name.replace(" ", ""),
        category = "밥류", referenceAmount = 100.0, unit = "g", energyKcal = 137.0,
        servingDescription = "100g 기준", dataVersion = "isolated", createdAt = 0, updatedAt = 0)

    @Composable private fun search(state: SmartInputUiState, onMenu: (FranchiseMenu) -> Unit = {}) {
        SmartFoodInputScreen(state = state, recentMeals = emptyList(),
            onBack = {}, onPhoto = {}, onBarcode = {}, onNutritionLabel = {}, onSearch = {},
            onSearchQueryChange = {}, onFoodSelected = {}, onUseBarcodeItem = {},
            onOcrCandidateSelected = {}, onOcrAmountChange = {}, onConfirmOcr = {},
            onManual = {}, onRegisterBarcode = {}, onRepeatRecent = {},
            onOfficialMenuSelected = onMenu, configuredAllergies = emptySet())
    }

    @Test fun A_generalSearchUsesVerifiedServingRepresentativeWithoutFakeZeroOrGenericWarnings() {
        val bare = food("quality-bare")
        val verified = bare.copy(id = "quality-verified", sourceFoodCode = "quality-verified",
            sourceType = "K-FIND-PRODUCT", servingDescription = "100g 기준 · 공식 총내용량 216g · 포장단위 줄",
            carbohydrateGrams = 25.95, proteinGrams = 4.57, fatGrams = 1.64)
        compact { search(SmartInputUiState(mode = SmartInputMode.SEARCH,
            searchQuery = "검증 김밥", searchResults = listOf(bare, verified))) }
        scroll(hasTestTag("food-search-result-quality-verified"))
        compose.onNodeWithTag("food-search-result-quality-verified").assertIsDisplayed()
        compose.onNodeWithText("1줄 · 296 kcal", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1줄 216g 기준").assertIsDisplayed()
        compose.onNodeWithTag("food-search-result-quality-bare").assertDoesNotExist()
        compose.onNodeWithText("0 kcal").assertDoesNotExist()
        compose.onNodeWithText("원재료·알레르기 정보가 일부 확인되지 않았어요.").assertDoesNotExist()
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
    }

    @Test fun B_exactBonjukSearchShowsActualMenusBeforeUnrelatedFoodAndNeverInventsCalories() {
        val menus = FranchiseCatalog.officialMenus("본죽")
        assertTrue(menus.size > 10)
        val menu = menus.first { it.energyKcal == null }
        var selected: FranchiseMenu? = null
        compact { search(SmartInputUiState(mode = SmartInputMode.SEARCH, searchQuery = "본죽",
            brandResults = listOf(FoodBrandSummary("본죽", menus.size), FoodBrandSummary("빈 브랜드", 0)),
            searchResults = listOf(food("unrelated", "본죽맛 간편죽"))), onMenu = { selected = it }) }
        scroll(hasTestTag("franchise-menu-${menu.id}"))
        compose.onNodeWithTag("franchise-menu-${menu.id}").assertIsDisplayed()
            .assert(hasText("영양정보 미제공 · 메뉴 이름을 넣고 직접 기록"))
        compose.onNodeWithTag("food-search-result-unrelated").assertDoesNotExist()
        compose.onNodeWithText("0 kcal").assertDoesNotExist()
        compose.onNodeWithText("빈 브랜드").assertDoesNotExist()
        compose.onNodeWithTag("franchise-menu-${menu.id}").performClick()
        assertEquals(menu.id, selected?.id)
        assertEquals("본죽 ${menu.name}", selected?.recordName)
        assertNull(selected?.energyKcal)
    }

    private fun recommendation(configured: Set<String>, declared: Set<String>, complete: Boolean,
        selected: Boolean = true): MealPlanUiState {
        val food = food("quality-recommendation", "검증 식사")
        val template = MealTemplate(id = "quality-template", name = "검증 식사", supportedMealTypes = "|LUNCH|",
            totalKcal = 137, preparationMinutes = 10, costLevel = "LOW", source = "isolated",
            createdAt = 0, updatedAt = 0)
        val bundle = MealRecommendationWithIngredients(ScoredMealRecommendation(template, 10.0, 0, 80, "확인한 자료"),
            emptyList(), listOf(food.name), declaredAllergens = declared,
            matchedAllergens = declared.intersect(configured), ingredientInfoComplete = false,
            allergenInfoComplete = complete)
        val ingredient = RecommendedIngredientUi(1, food, 100.0, 100.0, "g", 137,
            adjustable = false, minimumAmount = null, maximumAmount = null, adjustmentStep = null)
        return MealPlanUiState(recommendations = listOf(bundle), hasLoaded = true,
            configuredAllergies = configured, selectedMeal = if (selected) SelectedMealUi("quality-template",
                "검증 식사", "확인한 자료", listOf(ingredient), 137, declaredAllergens = declared,
                matchedAllergens = declared.intersect(configured), ingredientInfoComplete = false,
                allergenInfoComplete = complete) else null)
    }
    @Composable private fun detail(state: MealPlanUiState) {
        MealPlanContent(state, onBack = {}, onRefresh = {}, onSelect = {}, onAdjustPortion = {},
            onIngredientIncluded = { _, _ -> }, onShowReplacements = {}, onReplace = { _, _ -> }, onConfirm = {},
            detailOnly = state.selectedMeal != null)
    }

    @Test fun C_unconfiguredRecommendationDetailDoesNotRepeatUnknownIngredientAllergenBanner() {
        compact { detail(recommendation(emptySet(), emptySet(), false)) }
        scroll(hasText("먹은 내용 확인"))
        compose.onNodeWithText("먹은 내용 확인").assertIsDisplayed()
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
        compose.onNodeWithTag("allergy-notice-CONFIRMED").assertDoesNotExist()
        compose.onNodeWithText("원재료 정보 일부 미확인").assertDoesNotExist()
        compose.onNodeWithText("알레르기 정보 일부 미확인").assertDoesNotExist()
    }

    @Test fun D_configuredConfirmedAllergenIsClearAndNoIntersectionNeedsNoCaution() {
        var state by mutableStateOf(recommendation(setOf("우유"), setOf("우유", "밀"), true))
        compact { detail(state) }
        scroll(hasTestTag("allergy-notice-CONFIRMED"))
        compose.onNodeWithTag("allergy-notice-CONFIRMED").assertTextEquals("알레르기 주의 · 우유 포함").assertIsDisplayed()
        compose.runOnIdle { state = recommendation(setOf("우유"), setOf("밀"), true) }
        compose.onNodeWithTag("allergy-notice-CONFIRMED").assertDoesNotExist()
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
    }

    @Test fun E_unresolvedCautionOnlyAppearsAtConfiguredUsersDecisionPoint() {
        var state by mutableStateOf(recommendation(setOf("우유"), emptySet(), false, selected = false))
        compact { detail(state) }
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
        compose.runOnIdle { state = recommendation(setOf("우유"), emptySet(), false) }
        scroll(hasTestTag("allergy-notice-UNRESOLVED"))
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertTextEquals("알레르기 정보 확인 필요").assertIsDisplayed()
        compose.onNodeWithText("알레르기 없음").assertDoesNotExist()
        compose.runOnIdle { state = recommendation(emptySet(), emptySet(), false) }
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
    }

    @Test fun officialCrossContactNoticeStaysDifferentFromConfirmedContainmentAtRecordDecision() {
        val metadata = FoodMetadata(foodId = "quality-contact", mayContainAllergens = setOf("땅콩"),
            allergens = setOf("우유"), allergenInfoComplete = true, allergenStatus = "CONFIRMED_LABEL",
            sourceReference = "https://official.example/product/quality-contact", checkedAt = "2026-10-04")
        FoodMetadataPolicy.install(originalMetadata.filterNot { it.foodId == metadata.foodId } + metadata)
        compact { RecordFoodMetadataNotice(metadata.foodId, "공식 표시 제품", configuredAllergies = setOf("땅콩")) }
        compose.onNodeWithTag("allergy-notice-CROSS_CONTACT")
            .assertTextEquals("알레르기 교차접촉 주의 · 땅콩").assertIsDisplayed()
        compose.onNodeWithTag("allergy-notice-CONFIRMED").assertDoesNotExist()
        compose.onNodeWithText("땅콩 포함", substring = true).assertDoesNotExist()
    }

    @Test fun sourcedIngredientsRemainVisibleWithoutAllergyPreferences() {
        val metadata = FoodMetadata(foodId = "quality-ingredients", ingredients = setOf("쌀", "기장"),
            ingredientStatus = "PARTIAL_DESCRIPTION", foodGroupEvidenceScope = "REFERENCE_RECIPE_COMPOSITION",
            sourceReference = "https://public.example/recipe/quality-ingredients", checkedAt = "2026-10-04")
        FoodMetadataPolicy.install(originalMetadata.filterNot { it.foodId == metadata.foodId } + metadata)
        compact { RecordFoodMetadataNotice(metadata.foodId, "확인한 식사", configuredAllergies = emptySet()) }
        compose.onNodeWithText("공공 조리자료의 주요 재료", substring = true).assertIsDisplayed()
        compose.onNodeWithText("실제 조리법과 재료 구성은 달라질 수 있어요.").assertIsDisplayed()
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
    }

    @Test fun actualMaeilFullLabelKeepsSoyAndCrossContactSeparateWithoutUnconfiguredBanner() {
        val products=originalMetadata.filter { it.productReportNumber in setOf("19810227007211","20000441043301") }
        assertEquals(2,products.size)
        products.forEach {
            assertTrue(it.ingredientInfoComplete)
            assertTrue(it.completeIngredientText.contains("원액두유 99.9%"))
            assertEquals(setOf("대두"),it.allergens)
            assertTrue(it.mayContainAllergens.containsAll(setOf("우유","땅콩","밀","달걀","견과류")))
            assertTrue(it.crossContactText.contains("토마토"))
            assertEquals("팩",it.householdUnit)
        }
        val product=products.first()
        var configured by mutableStateOf(emptySet<String>())
        compact { RecordFoodMetadataNotice(product.foodId,"매일두유99.9",configuredAllergies=configured) }
        compose.onNodeWithText("공식 원재료",substring=true).assertIsDisplayed()
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
        compose.runOnIdle { configured=setOf("대두") }
        compose.onNodeWithTag("allergy-notice-CONFIRMED").assertTextEquals("알레르기 주의 · 대두 포함").assertIsDisplayed()
        compose.onNodeWithTag("allergy-notice-CROSS_CONTACT").assertDoesNotExist()
        compose.runOnIdle { configured=setOf("우유") }
        compose.onNodeWithTag("allergy-notice-CROSS_CONTACT").assertTextEquals("알레르기 교차접촉 주의 · 우유").assertIsDisplayed()
        compose.onNodeWithTag("allergy-notice-CONFIRMED").assertDoesNotExist()
        compose.runOnIdle { configured=setOf("새우") }
        compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
        compose.onNodeWithTag("allergy-notice-CONFIRMED").assertDoesNotExist()
        compose.onNodeWithTag("allergy-notice-CROSS_CONTACT").assertDoesNotExist()
    }
}
