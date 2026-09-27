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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.healthcare.ui.screens.MealPlanScreen
import com.example.healthcare.ui.screens.MealPreferenceScreen
import com.example.healthcare.ui.screens.QuickRecordAction
import com.example.healthcare.ui.screens.SettingsScreen
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import com.example.healthcare.ui.viewmodel.DashboardViewModel
import com.example.healthcare.ui.viewmodel.HistoryViewModel
import com.example.healthcare.ui.viewmodel.MealPlanViewModel
import com.example.healthcare.ui.viewmodel.MealPreferenceViewModel
import com.example.healthcare.ui.viewmodel.SettingsViewModel
import com.example.healthcare.ui.viewmodel.ViewModelFactory
import java.time.LocalDate

@Composable
fun HealthcareApp() {
    val backStack = rememberNavBackStack(DashboardRoute)
    var pendingQuickAction by remember { mutableStateOf<QuickRecordAction?>(null) }
    var pendingRecordDate by remember { mutableStateOf<LocalDate?>(null) }
    var immersiveRecord by remember { mutableStateOf(false) }
    val localContext = LocalContext.current
    val app = localContext.applicationContext as HealthcareApplication
    val hostActivity = localContext as? Activity
    val appFontSize by app.appFontSizeStore.fontSize.collectAsState()
    val appUpdateState by app.appUpdateManager.state.collectAsState()
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
            app.foodDataUpdateCoordinator
        )
    }
    val dashboardViewModel: DashboardViewModel = viewModel(factory = viewModelFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
    val bodyProfileState by settingsViewModel.bodyProfileState.collectAsState()
    val coachState by dashboardViewModel.todayCoachUiState.collectAsState()
    val dashboardSelectedDate by dashboardViewModel.selectedDate.collectAsState()
    var bodyProfile by remember(app) { mutableStateOf(app.bodyProfileStore.read()) }
    val exerciseWeightKg = bodyProfile?.weightKg

    if (bodyProfile == null) {
        BodyProfileSetupScreen(
            state = bodyProfileState,
            onSexSelected = settingsViewModel::onBodySexSelected,
            onAgeChange = settingsViewModel::onBodyAgeChange,
            onHeightChange = settingsViewModel::onBodyHeightChange,
            onWeightChange = settingsViewModel::onBodyWeightChange,
            onSave = {
                settingsViewModel.saveBodyProfile {
                    bodyProfile = app.bodyProfileStore.read()
                }
            }
        )
        return
    }
    val currentRoute = backStack.lastOrNull()
    val recordKeyboardVisible = (currentRoute == AddRecordRoute || currentRoute == HistoryRoute) &&
        WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val selectedDestination = when (currentRoute) {
        is MealPlanRoute, RecommendationsRoute -> TopLevelDestination.RECOMMENDATIONS
        MealPreferenceRoute -> TopLevelDestination.SETTINGS
        ActivityDetailRoute -> TopLevelDestination.DASHBOARD
        else -> TopLevelDestination.entries.find { it.route == currentRoute }
            ?: TopLevelDestination.DASHBOARD
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!immersiveRecord && !recordKeyboardVisible && currentRoute != ExerciseCoachRoute) {
            FloatingNavigationDock(selectedDestination) { destination ->
                if (currentRoute != destination.route) {
                    if (destination == TopLevelDestination.ADD) pendingRecordDate = null
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
                                backStack.add(AddRecordRoute)
                            },
                            onQuickRecord = { action ->
                                pendingQuickAction = action
                                pendingRecordDate = dashboardSelectedDate
                                backStack.add(AddRecordRoute)
                            },
                            onOpenEnergySettings = {
                                backStack.clear()
                                backStack.add(SettingsRoute)
                            },
                            onOpenMealPlan = { mealType, budget ->
                                backStack.add(MealPlanRoute(mealType.name, budget, coachState.targetCalories))
                            },
                            onOpenRecommendations = {
                                if (backStack.lastOrNull() != RecommendationsRoute) {
                                    backStack.add(RecommendationsRoute)
                                }
                            },
                            onOpenHistory = { backStack.add(HistoryRoute) }
                            ,onOpenActivityDetail = { backStack.add(ActivityDetailRoute) }
                        )
                        ActivityDetailRoute -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            stepCounterRepository = app.stepCounterRepository,
                            bodyProfile = bodyProfile,
                            exerciseWeightKg = exerciseWeightKg,
                            showActivityDetail = true,
                            onOpenEnergySettings = {
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
                        RecommendationsRoute -> {
                            val nextMeal = coachState.nextMealType
                            val budget = coachState.nextMealBudgetKcal
                            if (nextMeal != null && budget != null) {
                                val planViewModel: MealPlanViewModel = viewModel(factory = viewModelFactory)
                                MealPlanScreen(
                                    mealType = nextMeal,
                                    budgetKcal = budget,
                                    targetKcal = coachState.targetCalories,
                                    dailyRemainingKcal = coachState.remainingCalories,
                                    viewModel = planViewModel,
                                    onSearchFood = {
                                        pendingQuickAction = QuickRecordAction.SEARCH
                                        pendingRecordDate = null
                                        backStack.add(AddRecordRoute)
                                    },
                                    onOpenPreferences = { backStack.add(MealPreferenceRoute) },
                                    onBack = {
                                        backStack.clear()
                                        backStack.add(DashboardRoute)
                                    },
                                    onSaved = {
                                        backStack.clear()
                                        backStack.add(DashboardRoute)
                                    }
                                )
                            } else {
                                Scaffold(topBar = { WellnessTopAppBar("오늘의 추천") }) { padding ->
                                    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                                        WellnessEmptyState(
                                            icon = Icons.Rounded.RestaurantMenu,
                                            title = "지금 추천할 다음 식사가 없어요",
                                            message = "오늘의 식사 흐름이 바뀌면 여기서 다음 한 끼를 살펴볼 수 있어요."
                                        )
                                    }
                                }
                            }
                        }
                        AddRecordRoute -> {
                            val addViewModel: AddRecordViewModel = viewModel(factory = viewModelFactory)
                            AddRecordScreen(
                                viewModel = addViewModel,
                                initialAction = pendingQuickAction,
                                initialDate = pendingRecordDate,
                                onInitialActionConsumed = { pendingQuickAction = null },
                                onImmersiveChanged = { immersiveRecord = it },
                                onOpenHistory = {
                                    backStack.clear()
                                    backStack.add(HistoryRoute)
                                },
                                onBack = {
                                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    else {
                                        backStack.clear()
                                        backStack.add(DashboardRoute)
                                    }
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
                                onOpenMealPreference = { backStack.add(MealPreferenceRoute) }
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
                                dailyRemainingKcal = coachState.remainingCalories,
                                viewModel = planViewModel,
                                onSearchFood = {
                                    pendingQuickAction = QuickRecordAction.SEARCH
                                    pendingRecordDate = null
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
        onLater = app.appUpdateManager::dismissAvailableUpdate,
        onOpenInstallPermission = {
            hostActivity?.let(app.appUpdateManager::openInstallPermissionSettings)
        },
        onDismissStatus = app.appUpdateManager::dismissStatus
    )
}
