package com.example.healthcare

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
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
        waitForText("제품")
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("1봉 120g", substring = true))
        composeRule.onAllNodes(hasClickAction() and hasText("1봉 120g", substring = true))
            .onFirst().assertIsDisplayed().performClick()

        waitForText("기록 추가")
        composeRule.onNodeWithText("기록 추가").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("1봉"))
        composeRule.onNode(hasClickAction() and hasText("1봉")).assertIsDisplayed().performClick()
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
        waitForText("제품")
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("김밥 · 참치"))
        composeRule.onAllNodesWithText("김밥 · 참치").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("영양정보 100g 기준 · 약 174 kcal").assertIsDisplayed()
    }

    @Test
    fun basicFoodCategoriesBrowseFruitAndCombineVegetableSearch() {
        openFoodSearch()
        waitForSearchField()

        composeRule.onNodeWithText("과일").assertIsDisplayed().performClick()
        waitForSubstring("이름이 비슷한 음식도")
        waitAndScrollToText("사과 · 껍질 포함 · 생것")
        composeRule.onNodeWithText("사과 · 껍질 포함 · 생것").assertIsDisplayed()

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
    fun hamburgerSearchSeparatesGeneralTypesFromActualBrandProducts() {
        openFoodSearch()
        waitForSearchField()
        composeRule.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
            .performTextInput("햄버거")
        waitForText("제품")
        composeRule.onNodeWithText("제품").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("기본·종류"))
        composeRule.onNodeWithText("기본·종류").assertIsDisplayed()
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
        waitForText("기록 추가")
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("제품 전체"))
        composeRule.onNodeWithText("제품 전체").assertIsDisplayed()
    }

    @Test
    fun hamburgerProductUsesWholeProductNotInventedPieceAndCapturesPortionScreen() {
        openFoodSearch()
        waitForSearchField()
        composeRule.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
            .performTextInput("햄버거")
        waitForText("제품")
        waitForSubstring("제품 전체")
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasClickAction() and hasText("제품 전체", substring = true))
        composeRule.onAllNodes(hasClickAction() and hasText("제품 전체", substring = true))
            .onFirst().performClick()

        waitForText("기록 추가")
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("제품 전체"))
        composeRule.onNodeWithText("제품 전체").assertIsDisplayed()
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
        capture("franchise-mcdonalds-bigmac.png")
        composeRule.onNode(bigMacResult).performClick()
        waitForText("기록 추가")
        composeRule.onNodeWithText("기록 추가").assertIsDisplayed()
    }

    @Test
    fun seaweedSoupSearchShowsOfficialVariantsAndDirectAmountCanBeRecorded() {
        assertEquals("com.example.healthcare.qa", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        openFoodSearch()
        waitForSearchField()
        composeRule.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
            .performTextReplacement("미역국")
        waitForText("기본·종류")
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("기본·종류"))
        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("영양정보 100ml 기준", substring = true))
        composeRule.onAllNodes(hasText("영양정보 100ml 기준", substring = true)).onFirst().assertIsDisplayed()
        capture("seaweed-soup-search.png")
        val volumeDriedPollack = hasClickAction() and hasText("북어미역국") and
            hasText("영양정보 100ml 기준", substring = true)
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(volumeDriedPollack)
        composeRule.onAllNodesWithText("북어미역국").onFirst().assertIsDisplayed()
        composeRule.onAllNodes(hasText("자료 구분", substring = true)).onFirst().assertIsDisplayed()

        composeRule.onAllNodes(volumeDriedPollack).onFirst().performClick()
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
        waitForText("좋은 아침이에요.")
        composeRule.onNodeWithText("좋은 아침이에요.").assertIsDisplayed()
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
