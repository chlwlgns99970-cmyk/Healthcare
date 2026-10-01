package com.example.healthcare.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.healthcare.HealthcareApplication
import com.example.healthcare.BuildConfig
import com.example.healthcare.data.appupdate.AppUpdatePhase
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.ExerciseCoachCalculator
import com.example.healthcare.ui.components.FloatingNavigationDock
import com.example.healthcare.ui.components.AppUpdateOverlay
import com.example.healthcare.ui.components.WellnessEmptyState
import com.example.healthcare.ui.components.WellnessTopAppBar
import com.example.healthcare.ui.screens.AddRecordScreen
import com.example.healthcare.ui.screens.BodyProfileSetupScreen
import com.example.healthcare.ui.screens.DashboardScreen
import com.example.healthcare.ui.screens.ExerciseCoachScreen
import com.example.healthcare.ui.screens.HistoryScreen
import com.example.healthcare.ui.screens.TodayRecommendationScreen
import com.example.healthcare.ui.screens.MealPlanScreen
import com.example.healthcare.ui.screens.MealPreferenceScreen
import com.example.healthcare.ui.screens.MealTasteSetupScreen
import com.example.healthcare.ui.screens.QuickRecordAction
import com.example.healthcare.ui.screens.SettingsScreen
import com.example.healthcare.ui.screens.TodayReportScreen
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import com.example.healthcare.ui.viewmodel.DashboardViewModel
import com.example.healthcare.ui.viewmodel.HistoryViewModel
import com.example.healthcare.ui.viewmodel.TodayMealPlanViewModel
import com.example.healthcare.ui.viewmodel.MealPlanViewModel
import com.example.healthcare.ui.viewmodel.MealPreferenceViewModel
import com.example.healthcare.ui.viewmodel.SettingsViewModel
import com.example.healthcare.ui.viewmodel.ViewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun HealthcareApp() {
    val backStack = rememberNavBackStack(DashboardRoute)
    var pendingQuickAction by remember { mutableStateOf<QuickRecordAction?>(null) }
    var pendingRecordDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingMealType by remember { mutableStateOf<MealType?>(null) }
    var pendingSettingsSection by remember { mutableStateOf<String?>(null) }
    var pendingRecommendedTemplateId by remember { mutableStateOf<String?>(null) }
    var openSavedDayPlan by remember { mutableStateOf(false) }
    var immersiveRecord by remember { mutableStateOf(false) }
    val localContext = LocalContext.current
    val app = localContext.applicationContext as HealthcareApplication
    val hostActivity = localContext as? Activity
    val appFontSize by app.appFontSizeStore.fontSize.collectAsState()
    val appUpdateState by app.appUpdateManager.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    LaunchedEffect(app) {
        app.appUpdateManager.checkAutomatically()
    }
    LaunchedEffect(appUpdateState.phase) {
        if (appUpdateState.phase == AppUpdatePhase.READY_TO_INSTALL) {
            hostActivity?.let(app.appUpdateManager::continueInstallation)
        }
    }
    val viewModelFactory = remember {
        ViewModelFactory(
            app.mealRepository,
            app.goalRepository,
            app.energyProfileRepository,
            app.foodRepository,
            app.foodPhotoAnalysisRepository,
            app.foodPhotoProcessor,
            app.nutritionRepository,
            app.mealCoachRepository,
            app.nutritionLabelRecognizer,
            app.recognitionRepository,
            app.bodyProfileStore,
            app.weightGoalStore,
            app.foodDataUpdateCoordinator,
            app.todayMealPlanRepository
        )
    }
    val dashboardViewModel: DashboardViewModel = viewModel(factory = viewModelFactory)
    val dailyPlanViewModel: TodayMealPlanViewModel = viewModel(factory = viewModelFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
    val bodyProfileState by settingsViewModel.bodyProfileState.collectAsState()
    val coachState by dashboardViewModel.todayCoachUiState.collectAsState()
    val dashboardSelectedDate by dashboardViewModel.selectedDate.collectAsState()
    var bodyProfile by remember(app) { mutableStateOf(app.bodyProfileStore.read()) }
    var tasteSetupPending by remember(app) { mutableStateOf(app.bodyProfileStore.isTasteSetupPending()) }
    val exerciseWeightKg = bodyProfile?.weightKg

    if (bodyProfile == null) {
        BodyProfileSetupScreen(
            state = bodyProfileState,
            onSexSelected = settingsViewModel::onBodySexSelected,
            onAgeChange = settingsViewModel::onBodyAgeChange,
            onHeightChange = settingsViewModel::onBodyHeightChange,
            onWeightChange = settingsViewModel::onBodyWeightChange,
            onSave = {
                app.bodyProfileStore.setTasteSetupPending(true)
                tasteSetupPending = true
                settingsViewModel.saveBodyProfile {
                    bodyProfile = app.bodyProfileStore.read()
                }
            }
        )
        return
    }
    if (tasteSetupPending) {
        val preferenceViewModel: MealPreferenceViewModel = viewModel(factory = viewModelFactory)
        MealTasteSetupScreen(preferenceViewModel) {
            app.bodyProfileStore.setTasteSetupPending(false)
            tasteSetupPending = false
        }
        return
    }
    val currentRoute = backStack.lastOrNull()
    val recordKeyboardVisible = (currentRoute == AddRecordRoute || currentRoute == HistoryRoute) &&
        WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val selectedDestination = when (currentRoute) {
        is MealPlanRoute, RecommendationsRoute -> TopLevelDestination.RECOMMENDATIONS
        MealPreferenceRoute -> TopLevelDestination.SETTINGS
        ActivityDetailRoute, TodayReportRoute -> TopLevelDestination.DASHBOARD
        else -> TopLevelDestination.entries.find { it.route == currentRoute }
            ?: TopLevelDestination.DASHBOARD
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!immersiveRecord && !recordKeyboardVisible && currentRoute != ExerciseCoachRoute) {
            FloatingNavigationDock(selectedDestination) { destination ->
                if (currentRoute != destination.route) {
                    if (destination == TopLevelDestination.ADD) {
                        pendingRecordDate = null
                        pendingMealType = null
                    }
                    if (destination == TopLevelDestination.RECOMMENDATIONS) {
                        pendingRecommendedTemplateId = null
                        openSavedDayPlan = false
                    }
                    backStack.clear()
                    backStack.add(destination.route)
                }
            }
            }
        }
    ) { outerPadding ->
        Box(Modifier.fillMaxSize().padding(outerPadding)) {
            NavDisplay(backStack = backStack) { key ->
                NavEntry(key) {
                    when (key) {
                        DashboardRoute -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            stepCounterRepository = app.stepCounterRepository,
                            bodyProfile = bodyProfile,
                            exerciseWeightKg = exerciseWeightKg,
                            onOpenExerciseCoach = { backStack.add(ExerciseCoachRoute) },
                            onAddRecord = {
                                pendingRecordDate = dashboardSelectedDate
                                pendingMealType = null
                                backStack.add(AddRecordRoute)
                            },
                            onAddMealRecord = { mealType ->
                                pendingRecordDate = dashboardSelectedDate
                                pendingMealType = mealType
                                pendingQuickAction = QuickRecordAction.SEARCH
                                backStack.add(AddRecordRoute)
                            },
                            onQuickRecord = { action ->
                                pendingQuickAction = action
                                pendingRecordDate = dashboardSelectedDate
                                pendingMealType = null
                                backStack.add(AddRecordRoute)
                            },
                            onOpenEnergySettings = {
                                pendingSettingsSection = "ENERGY"
                                backStack.clear()
                                backStack.add(SettingsRoute)
                            },
                            onOpenMealPlan = { mealType, budget ->
                                backStack.add(MealPlanRoute(mealType.name, budget, coachState.targetCalories))
                            },
                            onOpenRecommendations = { templateId ->
                                pendingRecommendedTemplateId = null
                                openSavedDayPlan = coachState.dailyThemeLabel != null
                                if (backStack.lastOrNull() != RecommendationsRoute) {
                                    backStack.add(RecommendationsRoute)
                                }
                            },
                            onOpenHistory = { backStack.add(TodayReportRoute) }
                            ,onOpenActivityDetail = { backStack.add(ActivityDetailRoute) }
                        )
                        ActivityDetailRoute -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            stepCounterRepository = app.stepCounterRepository,
                            bodyProfile = bodyProfile,
                            exerciseWeightKg = exerciseWeightKg,
                            showActivityDetail = true,
                            onOpenEnergySettings = {
                                pendingSettingsSection = "ENERGY"
                                backStack.clear()
                                backStack.add(SettingsRoute)
                            },
                            onBackFromActivityDetail = {
                                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            }
                        )
                        TodayReportRoute -> TodayReportScreen(
                            viewModel = dashboardViewModel,
                            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                            onAddRecord = {
                                pendingQuickAction = QuickRecordAction.SEARCH
                                pendingRecordDate = dashboardSelectedDate
                                pendingMealType = null
                                backStack.add(AddRecordRoute)
                            }
                        )
                        RecommendationsRoute -> {
                            TodayRecommendationScreen(dailyPlanViewModel,
                                openSaved = openSavedDayPlan,
                                onBack = { backStack.clear(); backStack.add(DashboardRoute) },
                                onOpenPreferences = { backStack.add(MealPreferenceRoute) },
                                onOpenEnergy = { pendingSettingsSection = "ENERGY"; backStack.add(SettingsRoute) },
                                onOpenMeal = { meal, target ->
                                    openSavedDayPlan = true
                                    backStack.add(MealPlanRoute(meal.mealType.name, meal.kcal, target, meal.templateId))
                                })
                        }
                        AddRecordRoute -> {
                            val addViewModel: AddRecordViewModel = viewModel(factory = viewModelFactory)
                            AddRecordScreen(
                                viewModel = addViewModel,
                                initialAction = pendingQuickAction,
                                initialDate = pendingRecordDate,
                                initialMealType = pendingMealType,
                                returnToParentFromSearch = backStack.size > 1,
                                onRecordSaved = { mealType ->
                                    if (finishRecordFlow(backStack)) {
                                        pendingQuickAction = null
                                        pendingRecordDate = null
                                        pendingMealType = null
                                        appScope.launch {
                                            snackbarHostState.showSnackbar("${mealType.displayName} 식사에 기록했어요")
                                        }
                                    }
                                },
                                onInitialActionConsumed = { pendingQuickAction = null },
                                onImmersiveChanged = { immersiveRecord = it },
                                onOpenHistory = {
                                    backStack.clear()
                                    backStack.add(HistoryRoute)
                                },
                                onBack = {
                                    finishRecordFlow(backStack)
                                }
                            )
                        }
                        HistoryRoute -> {
                            val historyViewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                            HistoryScreen(viewModel = historyViewModel)
                        }
                        SettingsRoute -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                appFontSize = appFontSize,
                                appUpdateState = appUpdateState,
                                onCheckAppUpdate = app.appUpdateManager::checkManually,
                                onAppFontSizeSelected = app.appFontSizeStore::set,
                                onBodyWeightSaved = {
                                    bodyProfile = app.bodyProfileStore.read()
                                },
                                onOpenMealPreference = { backStack.add(MealPreferenceRoute) },
                                initialSection = pendingSettingsSection,
                                onInitialSectionConsumed = { pendingSettingsSection = null }
                            )
                        }
                        ExerciseCoachRoute -> ExerciseCoachScreen(
                            excessCalories = ExerciseCoachCalculator.targetExcess(coachState.intakeCalories, coachState.targetCalories),
                            weightKg = exerciseWeightKg,
                            onSaveWeight = { weight ->
                                app.bodyProfileStore.saveExerciseWeight(weight)
                                bodyProfile = app.bodyProfileStore.read()
                            },
                            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                        )
                        is MealPlanRoute -> {
                            val planViewModel: MealPlanViewModel = viewModel(factory = viewModelFactory)
                            MealPlanScreen(
                                mealType = runCatching { MealType.valueOf(key.mealType) }.getOrDefault(MealType.LUNCH),
                                budgetKcal = key.budgetKcal,
                                targetKcal = key.targetKcal,
                                dailyTemplateId = key.dailyTemplateId,
                                dailyRemainingKcal = coachState.remainingCalories,
                                viewModel = planViewModel,
                                onSearchFood = {
                                    pendingQuickAction = QuickRecordAction.SEARCH
                                    pendingRecordDate = null
                                    pendingMealType = runCatching { MealType.valueOf(key.mealType) }
                                        .getOrDefault(MealType.LUNCH)
                                    backStack.add(AddRecordRoute)
                                },
                                onOpenPreferences = { backStack.add(MealPreferenceRoute) },
                                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                                onSaved = {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            )
                        }
                        MealPreferenceRoute -> {
                            val preferenceViewModel: MealPreferenceViewModel = viewModel(factory = viewModelFactory)
                            MealPreferenceScreen(
                                viewModel = preferenceViewModel,
                                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                            )
                        }
                    }
                }
            }
        }
    }
    AppUpdateOverlay(
        state = appUpdateState,
        currentVersionName = BuildConfig.VERSION_NAME,
        onUpdate = app.appUpdateManager::startDownload,
        onRetry = app.appUpdateManager::retryDownload,
        onLater = app.appUpdateManager::dismissAvailableUpdate,
        onOpenInstallPermission = {
            hostActivity?.let(app.appUpdateManager::openInstallPermissionSettings)
        },
        onDismissStatus = app.appUpdateManager::dismissStatus
    )
}
