package com.example.healthcare

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test

/** Real app navigation, without saving or changing the user's settings. */
class TabRetapUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun recordSearchSelectionRetapReturnsRootAndSettingsRetapIsIdempotent() {
        compose.onNodeWithTag("bottom-tab-ADD").performClick()
        compose.onNodeWithText("음식 검색").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("백종원한줄김밥")
        Espresso.closeSoftKeyboard()
        val rollCard=SemanticsMatcher("verified roll search card") {
            it.config.contains(SemanticsProperties.TestTag) && it.config[SemanticsProperties.TestTag].startsWith("food-search-result-kfind-product-")
        }
        compose.waitUntil(15_000) { compose.onAllNodes(rollCard).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(rollCard).onFirst().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("음식 상세").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("bottom-tab-ADD").performClick()
        compose.onNodeWithText("음식 검색").assertIsDisplayed()
        compose.onNodeWithTag("food-amount-input").assertDoesNotExist()
        compose.onNodeWithTag("bottom-tab-SETTINGS").performClick()
        compose.onNodeWithText("내 신체정보").performClick()
        compose.onNodeWithTag("bottom-tab-SETTINGS").performClick()
        compose.onNodeWithText("앱 설정").assertIsDisplayed()
        compose.onNodeWithTag("bottom-tab-SETTINGS").performClick()
        compose.onNodeWithText("앱 설정").assertIsDisplayed()
        compose.onNodeWithTag("bottom-tab-DASHBOARD").performClick()
        compose.onNodeWithTag("dashboard-root").assertIsDisplayed()
    }
    @Test fun dirtyRecordRetapOffersKeepThenExplicitDiscard() {
        compose.onNodeWithTag("bottom-tab-ADD").performClick()
        compose.onNodeWithText("직접 입력").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("작성 중 음식")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("bottom-tab-ADD").performClick()
        compose.onNodeWithText("작성 중인 내용을 버릴까요?").assertIsDisplayed()
        compose.onNodeWithText("계속 작성").performClick()
        compose.onNode(hasSetTextAction() and hasText("작성 중 음식")).assertExists()
        compose.onNodeWithTag("bottom-tab-ADD").performClick()
        compose.onNodeWithText("버리고 이동").performClick()
        compose.onNodeWithText("음식 검색").assertIsDisplayed()
        compose.onNodeWithTag("bottom-tab-DASHBOARD").performClick()
    }
}
