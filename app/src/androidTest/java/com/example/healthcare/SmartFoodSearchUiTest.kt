package com.example.healthcare

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.BodyProfileStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodySex
import kotlinx.coroutines.flow.first
import androidx.test.espresso.Espresso
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SmartFoodSearchUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun ensureCompletedProfileForSearchTests() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (BodyProfileStore(context).read() == null) {
            BodyProfileStore(context).save(
                BodyProfile(BodySex.MALE, ageYears = 35, heightCm = 175.0, weightKg = 70.0)
            )
            composeRule.activityRule.scenario.recreate()
        }
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
    }

    @Test
    fun officialFoodSearchCalculatesCaloriesWithoutManualEntry() {
        search("신라면")
        selectResult("kfind-product-p108-003000400-0138")
        setAmount("1", "봉")
        waitAndScrollToText("500 kcal")
        composeRule.onNodeWithText("500 kcal").assertIsDisplayed()
        setAmount("240", "g")
        waitAndScrollToText("1001 kcal")
        composeRule.onNodeWithText("1001 kcal").assertIsDisplayed()
        openDetailedRecordFromQuick()
        waitAndScrollToTag("food-amount-quantity")
        composeRule.onNodeWithTag("food-amount-quantity").assertTextContains("240")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains("g", substring = true)
    }

    @Test
    fun tunaGimbapSearchShowsGramBasisInsteadOfEmptyResults() {
        assertEquals("com.example.healthcare.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        search("참치김밥")
        exposeExactResult("참치김밥", "kfind-d101-007450000-0001")
        composeRule.onNodeWithTag("food-search-result-kfind-d101-007450000-0001")
            .assertIsDisplayed().assertTextContains("참치김밥")
            .assertTextContains("영양정보 100g 기준 · 약 174 kcal")
    }

    @Test
    fun basicFoodCategoriesBrowseFruitAndCombineVegetableSearch() {
        openFoodSearch()
        waitForSearchField()

        composeRule.onNodeWithText("과일").assertIsDisplayed().performClick()
        waitForSubstring("이름이 비슷한 음식도")
        waitAndScrollToText("사과")
        composeRule.onNodeWithText("사과").assertIsDisplayed()

        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("채소·야채"))
        composeRule.onNodeWithText("채소·야채").performClick()
        val searchField = composeRule.onNode(
            hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true)
        )
        searchField.performTextReplacement("오이")
        waitForSubstring("이름이 비슷한 음식도")
        waitAndScrollToText("오이 · 껍질 포함 · 생것")
        composeRule.onNodeWithText("오이 · 껍질 포함 · 생것").assertIsDisplayed()
        capture("basic-food-category-search.png")
    }

    @Test
    fun hamburgerSearchGroupsGeneralKindAndKeepsActualBrandProductsAccessible() {
        search("햄버거")
        exposeExactResult("햄버거", "kfind-d102-123000000-0001")
        composeRule.onNodeWithTag("food-search-result-kfind-d102-123000000-0001")
            .assertIsDisplayed().assertTextContains("햄버거")
            .assertTextContains("영양정보 100g 기준 · 약 264 kcal")
        showHamburgerAlternatives()
        composeRule.onNodeWithTag("food-search-result-kfind-product-p123-223020200-0284")
            .assertIsDisplayed().assertTextContains("브랜드·제조사 · (주)조이푸드")
            .assertTextContains("식약처 식품영양자료", substring = true)
    }

    @Test
    fun brandBrowseShowsActualCompanyProductsAndReusesPortionFlow() {
        openFoodSearch(); waitForSearchField()
        composeRule.onNodeWithText("브랜드·제조사").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("브랜드 또는 제조사", substring = true)).performTextReplacement("대정")
        Espresso.closeSoftKeyboard()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HealthcareApplication
        val company = runBlocking { app.nutritionRepository.searchProductBrands("대정").first() }.single { it.brand == "(주)대정" }
        waitForText(company.brand)
        composeRule.onNode(hasClickAction() and hasText(company.brand)).performClick()
        waitForText("공식 데이터에 등록된 제품 ${company.productCount}개")
        val foods = runBlocking { app.nutritionRepository.searchProductsByBrand(company.brand, "").first() }
        assertTrue(foods.isNotEmpty()); assertTrue(foods.all { it.brand == company.brand })
        val food = foods.first { com.example.healthcare.domain.FoodAmountPolicy.canCalculate(it) }
        selectResult(food.id)
        waitAndScrollToText(company.brand); composeRule.onNodeWithText(company.brand).assertIsDisplayed()
        val choice = com.example.healthcare.domain.FoodAmountPolicy.defaultChoice(food)!!
        waitAndScrollToTag("food-amount-unit")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains(choice.unit, substring = true)
        openDetailedRecordFromQuick()
        waitAndScrollToTag("food-amount-unit")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains(choice.unit, substring = true)
    }

    @Test
    fun hamburgerProductUsesWholeProductNotInventedPieceAndCapturesPortionScreen() {
        search("햄버거"); showHamburgerAlternatives()
        composeRule.onNodeWithTag("food-search-result-kfind-product-p123-223020200-0284")
            .assertIsDisplayed().assertTextContains("제품 전체 · 220 kcal").assertTextContains("제품 전체 100g 기준").performClick()
        waitForQuickRecord(); waitAndScrollToTag("food-amount-unit")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains("제품 전체", substring = true)
        waitAndScrollToText("220 kcal"); composeRule.onNodeWithText("220 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("1개").assertDoesNotExist()
        openDetailedRecordFromQuick(); waitAndScrollToTag("food-amount-unit")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains("제품 전체", substring = true)
        composeRule.onNodeWithText("1개").assertDoesNotExist()
        capture("hamburger-portion.png")
    }

    @Test
    fun franchiseBrowseShowsOfficialMenusAndReusesExistingPortionFlow() {
        openFoodSearch(); waitForSearchField()
        composeRule.onNodeWithText("프랜차이즈").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("프랜차이즈 브랜드", substring = true)).performTextInput("맥도날드")
        waitForText("맥도날드")
        composeRule.onNode(hasClickAction() and hasText("맥도날드") and hasText("메뉴 37개")).performClick()
        waitForText("메뉴 37개 · 영양정보 제공 여부를 표시해요.")
        composeRule.onNode(hasSetTextAction() and hasText("이 브랜드 메뉴 검색", substring = true)).performTextInput("빅맥")
        Espresso.closeSoftKeyboard()
        waitAndScrollToTag("food-search-result-kfind-d202-091000000-0056")
        composeRule.onNodeWithTag("food-search-result-kfind-d202-091000000-0056")
            .assertTextContains("영양정보 100g 기준 · 약 261 kcal").performClick()
        waitForQuickRecord(); setAmount("200", "g")
        waitAndScrollToText("522 kcal"); composeRule.onNodeWithText("522 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("1개").assertDoesNotExist()
        openDetailedRecordFromQuick(); waitAndScrollToTag("food-amount-quantity")
        composeRule.onNodeWithTag("food-amount-quantity").assertTextContains("200")
        composeRule.onNodeWithTag("food-amount-unit").assertTextContains("g", substring = true)
        capture("franchise-mcdonalds-bigmac.png")
    }

    @Test
    fun seaweedSoupSearchShowsOfficialVariantsAndDirectAmountCanBeRecorded() {
        search("미역국")
        exposeExactResult("미역국", "kfind-d405-223200000-0001")
        composeRule.onNodeWithTag("food-search-result-kfind-d405-223200000-0001")
            .assertTextContains("북어미역국").assertTextContains("영양정보 100ml 기준 · 약 11 kcal").performClick()
        waitForQuickRecord(); setAmount("350", "ml")
        waitAndScrollToText("39 kcal"); composeRule.onNodeWithText("39 kcal").assertIsDisplayed()
        capture("dried-pollack-seaweed-soup-direct.png")
        waitAndScrollToText("아침에 기록"); composeRule.onNodeWithText("아침에 기록").performClick()
        waitForTag("record-saved-dialog"); composeRule.onNodeWithTag("record-saved-confirm").performClick()
        waitForTag("record-completion"); composeRule.onNodeWithTag("completion-home").performClick()
        waitForTag("dashboard-root"); composeRule.onNodeWithTag("dashboard-root").assertIsDisplayed()
    }

    private fun exposeExactResult(query: String, id: String) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HealthcareApplication
        val results = runBlocking { app.nutritionRepository.search(query).first() }
        val group = com.example.healthcare.domain.FoodSearchPolicy.groupSearchResults(results, query)
            .single { it.representative.id == id || it.alternatives.any { row -> row.id == id } }
        waitAndScrollToTag("food-search-result-${group.representative.id}")
        if (group.representative.id != id && composeRule.onAllNodesWithTag("food-search-result-$id").fetchSemanticsNodes().isEmpty()) {
            waitAndScrollToTag("food-search-alternatives-${group.representative.id}")
            composeRule.onNodeWithTag("food-search-alternatives-${group.representative.id}").performClick()
        }
        waitAndScrollToTag("food-search-result-$id")
    }

    private fun search(query: String) {
        openFoodSearch(); waitForSearchField()
        composeRule.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true)).performTextReplacement(query)
        Espresso.closeSoftKeyboard()
    }
    private fun selectResult(id: String) {
        waitAndScrollToTag("food-search-result-$id")
        composeRule.onNodeWithTag("food-search-result-$id").performClick(); waitForQuickRecord()
    }
    private fun setAmount(quantity: String, unit: String) {
        waitAndScrollToTag("food-amount-unit")
        composeRule.onNodeWithTag("food-amount-unit").performClick()
        composeRule.onNodeWithTag("food-amount-choice-$unit").performClick()
        waitAndScrollToTag("food-amount-quantity")
        composeRule.onNodeWithTag("food-amount-quantity").performTextReplacement(quantity)
        Espresso.closeSoftKeyboard()
    }

    private fun capture(fileName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = File(requireNotNull(context.getExternalFilesDir(null)), fileName)
        composeRule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            screenshot.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        assertTrue(screenshot.length() > 0L)
    }

    private fun waitForQuickRecord() {
        waitForText("음식 상세")
        composeRule.onNodeWithText("음식 상세").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction() and hasText("단위", substring = true)).assertDoesNotExist()
    }

    private fun openDetailedRecordFromQuick() {
        waitForQuickRecord()
        waitAndScrollToText("자세히 입력")
        composeRule.onNodeWithText("자세히 입력").assertIsDisplayed().performClick()
        waitForText("기록 추가")
        composeRule.onNodeWithText("기록 추가").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(60_000) {
            runCatching { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }

    private fun waitForSubstring(text: String) {
        composeRule.waitUntil(60_000) {
            runCatching {
                composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun waitForSearchField() {
        composeRule.waitUntil(60_000) {
            runCatching {
                composeRule.onAllNodes(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun waitAndScrollToText(text: String) {
        composeRule.waitUntil(60_000) {
            runCatching {
                composeRule.onAllNodes(hasScrollAction()).onFirst()
                    .performScrollToNode(hasText(text))
                true
            }.getOrDefault(false)
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(60_000) {
            runCatching { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }

    private fun waitAndScrollToTag(tag: String) {
        composeRule.waitUntil(60_000) {
            runCatching {
                composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag(tag))
                true
            }.getOrDefault(false)
        }
    }

    private fun showHamburgerAlternatives() {
        exposeExactResult("햄버거", "kfind-product-p123-223020200-0284")
    }

    private fun openFoodSearch() {
        waitForTag("dashboard-root")
        composeRule.onNodeWithTag("dashboard-meal-breakfast").performClick()
        waitForText("음식 검색")
    }

    private companion object {
        const val EXPECTED_TOTAL_FOOD_COUNT = 67_357
    }
}
