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
        assertEquals("com.example.healthcare.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)

        openFoodSearch()
        waitForSearchField()
        composeRule.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
            .performTextInput("신라면")
        waitAndScrollToTag("food-search-result-kfind-product-p108-003000400-0138")
        composeRule.onNodeWithTag("food-search-result-kfind-product-p108-003000400-0138")
            .assertIsDisplayed().assertTextContains("1봉 120g", substring = true).performClick()

        waitForQuickRecord()
        waitAndScrollToText("신라면")
        composeRule.onNodeWithText("신라면").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("1봉"))
        composeRule.onNode(hasClickAction() and hasText("1봉")).assertIsDisplayed().performClick().assertIsSelected()
        waitAndScrollToText("500 kcal")
        composeRule.onNodeWithText("500 kcal").assertIsDisplayed()
        openDetailedRecordFromQuick()
        composeRule.onNodeWithText("한 화면에서 자세히 입력").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("더 정확히 입력하기"))
        composeRule.onNodeWithText("더 정확히 입력하기").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasSetTextAction() and hasText("양", substring = true))
        val amountField = composeRule.onNode(hasSetTextAction() and hasText("양", substring = true))
        amountField.assertTextContains("120").performTextReplacement("240")
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasSetTextAction() and hasText("칼로리", substring = true))
        composeRule.onNode(hasSetTextAction() and hasText("칼로리", substring = true))
            .assertTextContains("1001")
    }

    @Test
    fun tunaGimbapSearchShowsGramBasisInsteadOfEmptyResults() {
        assertEquals("com.example.healthcare.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        openFoodSearch()
        waitForSearchField()
        composeRule.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
            .performTextInput("참치김밥")
        waitAndScrollToTag("food-search-result-kfind-d101-007450000-0001")
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
        openFoodSearch()
        waitForSearchField()
        composeRule.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
            .performTextInput("햄버거")
        waitAndScrollToTag("food-search-result-kfind-d102-123000000-0001")
        composeRule.onNodeWithTag("food-search-result-kfind-d102-123000000-0001")
            .assertIsDisplayed().assertTextContains("햄버거")
            .assertTextContains("영양정보 100g 기준 · 약 264 kcal")
        showHamburgerAlternatives()
        composeRule.onNodeWithTag("food-search-result-kfind-product-p123-223020200-0284")
            .assertIsDisplayed().assertTextContains("브랜드·제조사 · (주)조이푸드")
            .assertTextContains("출처 K-FIND-PRODUCT", substring = true)
    }

    @Test
    fun brandBrowseShowsActualCompanyProductsAndReusesPortionFlow() {
        openFoodSearch()
        waitForSearchField()
        composeRule.onNodeWithText("브랜드·제조사").performClick()
        waitForText("(주)대정")
        composeRule.onNodeWithText("제품 154개").assertIsDisplayed()
        composeRule.onNode(hasClickAction() and hasText("(주)대정")).performClick()
        waitForText("공식 데이터에 등록된 제품 154개")
        composeRule.onNodeWithText("공식 데이터에 등록된 제품 154개").assertIsDisplayed()
        waitForSubstring("제품 전체")
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasClickAction() and hasText("제품 전체", substring = true))
        composeRule.onAllNodes(hasClickAction() and hasText("제품 전체", substring = true))
            .onFirst().performClick()
        waitForQuickRecord()
        waitAndScrollToText("(주)대정")
        composeRule.onNodeWithText("(주)대정").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("제품 전체"))
        composeRule.onNode(hasClickAction() and hasText("제품 전체"))
            .assertIsDisplayed().performClick().assertIsSelected()
        openDetailedRecordFromQuick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("제품 전체"))
        composeRule.onNode(hasClickAction() and hasText("제품 전체")).assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun hamburgerProductUsesWholeProductNotInventedPieceAndCapturesPortionScreen() {
        openFoodSearch()
        waitForSearchField()
        composeRule.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
            .performTextInput("햄버거")
        showHamburgerAlternatives()
        composeRule.onNodeWithTag("food-search-result-kfind-product-p123-223020200-0284")
            .assertIsDisplayed().assertTextContains("제품 전체 100g · 220 kcal").performClick()

        waitForQuickRecord()
        waitAndScrollToText("(주)조이푸드")
        composeRule.onNodeWithText("(주)조이푸드").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("제품 전체"))
        composeRule.onNode(hasClickAction() and hasText("제품 전체"))
            .assertIsDisplayed().performClick().assertIsSelected()
        waitAndScrollToText("220 kcal")
        composeRule.onNodeWithText("220 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("1개").assertDoesNotExist()
        openDetailedRecordFromQuick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("제품 전체"))
        composeRule.onNode(hasClickAction() and hasText("제품 전체")).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("1개").assertDoesNotExist()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshot = File(requireNotNull(context.getExternalFilesDir(null)), "hamburger-portion.png")
        composeRule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            screenshot.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        assertTrue(screenshot.length() > 0L)
    }

    @Test
    fun franchiseBrowseShowsOfficialMenusAndReusesExistingPortionFlow() {
        openFoodSearch()
        waitForSearchField()
        composeRule.onNodeWithText("프랜차이즈").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("프랜차이즈 브랜드", substring = true))
            .performTextInput("맥도날드")
        waitForText("맥도날드")
        composeRule.onNode(hasClickAction() and hasText("맥도날드") and hasText("메뉴 37개")).performClick()
        waitForText("공식 영양정보로 확인된 메뉴 37개")
        composeRule.onNodeWithText("공식 영양정보로 확인된 메뉴 37개").assertIsDisplayed()

        val field = composeRule.onNode(hasSetTextAction() and hasText("이 브랜드 메뉴 검색", substring = true))
        field.performTextInput("빅맥")
        waitForSubstring("빅맥")
        val bigMacResult = hasClickAction() and hasText("버거 · 빅맥 버거") and
            hasText("프랜차이즈 · 맥도날드", substring = true)
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(bigMacResult)
        composeRule.onNode(bigMacResult).assertIsDisplayed()
            .assertTextContains("영양정보 100g 기준 · 약 261 kcal")
        composeRule.onNodeWithTag("food-search-result-kfind-d202-091000000-0056").assertIsDisplayed()
        capture("franchise-mcdonalds-bigmac.png")
        composeRule.onNode(bigMacResult).performClick()
        waitForQuickRecord()
        waitAndScrollToText("버거 · 빅맥 버거")
        composeRule.onNodeWithText("버거 · 빅맥 버거").assertIsDisplayed()
        composeRule.onNodeWithText("맥도날드").assertIsDisplayed()
        waitAndScrollToText("확인된 단위 g 그대로 입력해요.")
        composeRule.onNodeWithText("확인된 단위 g 그대로 입력해요.").assertIsDisplayed()
        val eatenAmount = hasSetTextAction() and hasText("먹은 양", substring = true)
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(eatenAmount)
        composeRule.onNode(eatenAmount).assertIsDisplayed().performTextReplacement("200")
        composeRule.onNode(eatenAmount).performImeAction()
        waitAndScrollToText("522 kcal")
        composeRule.onNodeWithText("522 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("1개").assertDoesNotExist()
        openDetailedRecordFromQuick()
        waitAndScrollToTag("direct-amount-input")
        composeRule.onNodeWithTag("direct-amount-input").assertIsDisplayed().assertTextContains("200")
        waitAndScrollToText("약 522 kcal")
        composeRule.onNodeWithText("약 522 kcal").assertIsDisplayed()
    }

    @Test
    fun seaweedSoupSearchShowsOfficialVariantsAndDirectAmountCanBeRecorded() {
        assertEquals("com.example.healthcare.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        openFoodSearch()
        waitForSearchField()
        composeRule.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
            .performTextReplacement("미역국")
        waitAndScrollToTag("food-search-alternatives-kfind-d305-223200000-0001")
        composeRule.onNodeWithTag("food-search-alternatives-kfind-d305-223200000-0001").performClick()
        waitAndScrollToTag("food-search-result-kfind-d405-223200000-0001")
        val volumeDriedPollack = composeRule.onNodeWithTag("food-search-result-kfind-d405-223200000-0001")
        volumeDriedPollack.assertIsDisplayed().assertTextContains("북어미역국")
            .assertTextContains("영양정보 100ml 기준 · 약 11 kcal")
            .assertTextContains("자료 구분", substring = true)
        capture("seaweed-soup-search.png")

        volumeDriedPollack.performClick()
        waitForQuickRecord()
        waitAndScrollToText("확인된 단위 ml 그대로 입력해요.")
        composeRule.onNodeWithText("확인된 단위 ml 그대로 입력해요.").assertIsDisplayed()
        openDetailedRecordFromQuick()
        waitForText("실제 먹은 양")
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("실제 먹은 양"))
        val amount = composeRule.onNodeWithTag("direct-amount-input")
        amount.assertIsDisplayed().performTextInput("350")
        amount.performImeAction()
        waitForText("약 39 kcal")
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("약 39 kcal"))
        composeRule.onNodeWithText("약 39 kcal").assertIsDisplayed()
        composeRule.onNodeWithText("g과 ml는 서로 바꾸지 않고", substring = true).assertIsDisplayed()
        capture("dried-pollack-seaweed-soup-direct.png")

        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("다음 · 식사 시간"))
        composeRule.onNodeWithText("다음 · 식사 시간").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("기록 확인"))
        composeRule.onNodeWithText("기록 확인").performClick()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("기록 저장"))
        composeRule.onNodeWithText("기록 저장").performClick()
        waitForTag("dashboard-root")
        composeRule.onNodeWithTag("dashboard-root").assertIsDisplayed()
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
        waitForText("빠른 기록")
        composeRule.onNodeWithText("빠른 기록").assertIsDisplayed()
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
        waitAndScrollToTag("food-search-alternatives-kfind-d102-123000000-0001")
        composeRule.onNodeWithTag("food-search-alternatives-kfind-d102-123000000-0001").performClick()
        waitAndScrollToTag("food-search-result-kfind-product-p123-223020200-0284")
    }

    private fun openFoodSearch() {
        waitForText("기록")
        composeRule.onNode(hasClickAction() and hasText("기록")).performClick()
        waitForText("어떤 방식으로\n하루를 기록할까요?")
        composeRule.onNode(hasClickAction() and hasText("음식 검색", substring = true)).performClick()
    }

    private companion object {
        const val EXPECTED_TOTAL_FOOD_COUNT = 31_580
    }
}
