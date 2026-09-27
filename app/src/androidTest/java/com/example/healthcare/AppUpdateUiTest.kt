package com.example.healthcare

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.healthcare.data.appupdate.AppReleaseMetadata
import com.example.healthcare.data.appupdate.AppUpdatePhase
import com.example.healthcare.data.appupdate.AppUpdateUiState
import com.example.healthcare.ui.components.AppUpdateOverlay
import com.example.healthcare.ui.theme.HealthCareTheme
import org.junit.Rule
import org.junit.Test

class AppUpdateUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun availableUpdateShowsVersionsNotesAndActions() {
        composeRule.setContent {
            HealthCareTheme {
                AppUpdateOverlay(
                    state = AppUpdateUiState(
                        phase = AppUpdatePhase.AVAILABLE,
                        release = release()
                    ),
                    currentVersionName = "1.0.3",
                    onUpdate = {},
                    onLater = {},
                    onOpenInstallPermission = {},
                    onDismissStatus = {}
                )
            }
        }

        composeRule.onNodeWithText("새 버전이 있어요").assertIsDisplayed()
        composeRule.onNodeWithText("현재 버전  1.0.3").assertIsDisplayed()
        composeRule.onNodeWithText("새 버전  1.0.4").assertIsDisplayed()
        composeRule.onNodeWithText("업데이트").assertIsDisplayed()
        composeRule.onNodeWithText("나중에").assertIsDisplayed()
    }

    @Test
    fun downloadingUpdateShowsProgress() {
        composeRule.setContent {
            HealthCareTheme {
                AppUpdateOverlay(
                    state = AppUpdateUiState(
                        phase = AppUpdatePhase.DOWNLOADING,
                        release = release(),
                        downloadProgress = 42
                    ),
                    currentVersionName = "1.0.3",
                    onUpdate = {},
                    onLater = {},
                    onOpenInstallPermission = {},
                    onDismissStatus = {}
                )
            }
        }

        composeRule.onNodeWithText("업데이트 다운로드 중").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()
    }

    private fun release() = AppReleaseMetadata(
        versionName = "1.0.4",
        versionCode = 5,
        apkUrl = "https://example.com/update.apk",
        fileName = "update.apk",
        fileSizeBytes = 100L,
        sha256 = "A".repeat(64),
        releaseNotes = listOf("안전한 앱 업데이트"),
        releasedAt = "2026-09-27T00:00:00Z"
    )
}
