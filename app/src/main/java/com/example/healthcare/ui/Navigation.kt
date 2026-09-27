package com.example.healthcare.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Dashboard as DashboardOutlined
import androidx.compose.material.icons.outlined.History as HistoryOutlined
import androidx.compose.material.icons.outlined.Settings as SettingsOutlined
import androidx.compose.material.icons.outlined.AutoAwesome as AutoAwesomeOutlined
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 대시보드 화면 경로
 */
@Serializable
data object DashboardRoute : NavKey

/**
 * 기록 추가 화면 경로
 */
@Serializable
data object AddRecordRoute : NavKey

/**
 * 기록 내역 화면 경로
 */
@Serializable
data object HistoryRoute : NavKey

/**
 * 설정 화면 경로
 */
@Serializable
data object SettingsRoute : NavKey

@Serializable
data object ExerciseCoachRoute : NavKey

@Serializable
data object ActivityDetailRoute : NavKey

@Serializable
data object RecommendationsRoute : NavKey

@Serializable
data class MealPlanRoute(
    val mealType: String,
    val budgetKcal: Int,
    val targetKcal: Int
) : NavKey

@Serializable
data object MealPreferenceRoute : NavKey

/**
 * 하단 탭 또는 네비게이션 레일에 표시될 주요 목적지 정의
 */
enum class TopLevelDestination(
    val route: NavKey,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val iconTextId: String
) {
    DASHBOARD(
        route = DashboardRoute,
        selectedIcon = Icons.Rounded.Dashboard,
        unselectedIcon = Icons.Outlined.DashboardOutlined,
        iconTextId = "홈"
    ),
    RECOMMENDATIONS(
        route = RecommendationsRoute,
        selectedIcon = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesomeOutlined,
        iconTextId = "추천"
    ),
    ADD(
        route = AddRecordRoute,
        selectedIcon = Icons.Rounded.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircleOutline,
        iconTextId = "기록"
    ),
    HISTORY(
        route = HistoryRoute,
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.HistoryOutlined,
        iconTextId = "통계"
    ),
    SETTINGS(
        route = SettingsRoute,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.SettingsOutlined,
        iconTextId = "설정"
    );

    companion object {
        val DASHBOARD_DEST = DASHBOARD
        val HISTORY_DEST = HISTORY
        val ADD_DEST = ADD
        val SETTINGS_DEST = SETTINGS
    }
}
