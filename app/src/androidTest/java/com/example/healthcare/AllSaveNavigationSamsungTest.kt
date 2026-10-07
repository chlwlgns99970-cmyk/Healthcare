package com.example.healthcare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Rule
import org.junit.Test

class AllSaveNavigationSamsungTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun popup() = compose.waitUntil(15000) { compose.onAllNodesWithText("저장 완료").fetchSemanticsNodes().isNotEmpty() }
    @Test fun settingsPopupSurvivesRealActivityRotationAndForeground() {
        compose.waitUntil(30000) { compose.onAllNodesWithTag("dashboard-root").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("설정") and hasClickAction()).performClick()
        compose.onNode(hasText("내 신체정보") and hasClickAction()).performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("저장하기"))
        compose.onNodeWithText("저장하기").performClick(); popup()
        compose.activityRule.scenario.recreate(); popup()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED); popup()
        compose.onAllNodesWithTag("record-saved-dialog").assertCountEquals(1)
        compose.onNodeWithText("저장 완료").assertIsDisplayed()
        compose.onNodeWithTag("record-saved-confirm").performClick()
        compose.onNodeWithTag("record-saved-dialog").assertDoesNotExist()
        compose.onAllNodesWithText("내 신체정보").onFirst().assertExists()
    }
}
