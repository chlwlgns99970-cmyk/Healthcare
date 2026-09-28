package com.example.healthcare.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.healthcare.R
import com.example.healthcare.BuildConfig
import com.example.healthcare.data.AppFontSize
import com.example.healthcare.data.appupdate.AppUpdatePhase
import com.example.healthcare.data.appupdate.AppUpdateUiState
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.data.update.FoodDataUpdateState
import com.example.healthcare.data.update.FoodDataUpdateStatus
import com.example.healthcare.domain.BodySex
import com.example.healthcare.domain.IntakePaceStatus
import com.example.healthcare.ui.components.MetricValue
import com.example.healthcare.ui.components.SectionHeader
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.WellnessTopAppBar
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.theme.WellnessSpacing
import com.example.healthcare.ui.viewmodel.EnergySettingsUiState
import com.example.healthcare.ui.viewmodel.BodyProfileUiState
import com.example.healthcare.ui.viewmodel.SettingsViewModel
import com.example.healthcare.ui.viewmodel.WeightGoalUiState
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel? = null,
    appFontSize: AppFontSize = AppFontSize.NORMAL,
    onAppFontSizeSelected: (AppFontSize) -> Unit = {},
    onOpenMealPreference: () -> Unit = {},
    onBodyWeightSaved: (Double) -> Unit = {},
    appUpdateState: AppUpdateUiState = AppUpdateUiState(),
    onCheckAppUpdate: () -> Unit = {},
    initialSection: String? = null,
    onInitialSectionConsumed: () -> Unit = {}
) {
    val currentGoal by viewModel?.currentGoal?.collectAsState() ?: remember { mutableStateOf(null) }
    val isGoalSaving by viewModel?.isSaving?.collectAsState() ?: remember { mutableStateOf(false) }
    val energyState by viewModel?.energyState?.collectAsState()
        ?: remember { mutableStateOf(EnergySettingsUiState()) }
    val bodyProfileState by viewModel?.bodyProfileState?.collectAsState()
        ?: remember { mutableStateOf(BodyProfileUiState()) }
    val weightGoalState by viewModel?.weightGoalState?.collectAsState()
        ?: remember { mutableStateOf(WeightGoalUiState()) }
    val foodDataUpdateState by viewModel?.foodDataUpdateState?.collectAsState()
        ?: remember { mutableStateOf(FoodDataUpdateState()) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf<String?>(null) }
    var newGoalText by remember { mutableStateOf("") }
    val targetCalories = currentGoal?.targetCalories ?: 2000

    LaunchedEffect(initialSection) {
        if (initialSection != null && runCatching { SettingsSection.valueOf(initialSection) }.isSuccess) {
            selectedSection = initialSection
            onInitialSectionConsumed()
        }
    }

    if (showGoalDialog) {
        GoalDialog(
            value = newGoalText,
            isSaving = isGoalSaving,
            onValueChange = { newGoalText = it.filter(Char::isDigit).take(5) },
            onDismiss = { showGoalDialog = false },
            onSave = {
                viewModel?.updateGoal(newGoalText.toIntOrNull() ?: 0) {
                    showGoalDialog = false
                }
            }
        )
    }

    val section = selectedSection?.let { runCatching { SettingsSection.valueOf(it) }.getOrNull() }
    BackHandler(enabled = section != null) {
        selectedSection = null
    }
    if (section == null) {
        SettingsMenuContent(
            bodyProfileState = bodyProfileState,
            energyState = energyState,
            targetCalories = targetCalories,
            appFontSize = appFontSize,
            foodDataUpdateState = foodDataUpdateState,
            onSectionSelected = { selectedSection = it.name }
        )
        return
    }

    SettingsSectionContent(
        section = section,
        targetCalories = targetCalories,
        appFontSize = appFontSize,
        energyState = energyState,
        bodyProfileState = bodyProfileState,
        weightGoalState = weightGoalState,
        foodDataUpdateState = foodDataUpdateState,
        appUpdateState = appUpdateState,
        onGoalClick = {
            newGoalText = targetCalories.toString()
            showGoalDialog = true
        },
        onBmrChange = { viewModel?.onBmrChange(it) },
        onActivityLevelSelected = { viewModel?.onActivityLevelSelected(it) },
        onCustomPalChange = { viewModel?.onCustomPalChange(it) },
        onTargetModeSelected = { viewModel?.onTargetModeSelected(it) },
        onSaveEnergyProfile = { viewModel?.saveEnergyProfile() },
        onBodySexSelected = { viewModel?.onBodySexSelected(it) },
        onBodyAgeChange = { viewModel?.onBodyAgeChange(it) },
        onBodyHeightChange = { viewModel?.onBodyHeightChange(it) },
        onBodyWeightChange = { viewModel?.onBodyWeightChange(it) },
        onSaveBodyProfile = { viewModel?.saveBodyProfile(onBodyWeightSaved) },
        onUseEstimatedBmr = { viewModel?.useEstimatedBmr() },
        onWeightGoalTargetChange = { viewModel?.onWeightGoalTargetChange(it) },
        onWeightGoalWeeksChange = { viewModel?.onWeightGoalWeeksChange(it) },
        onCalculateWeightGoal = { viewModel?.calculateWeightGoal() },
        onApplyWeightGoal = { viewModel?.applyWeightGoal() },
        onIntakeTargetChange = { viewModel?.onIntakeTargetChange(it) },
        onCalculateIntakePace = { viewModel?.calculateIntakePace() },
        onApplyIntakeTarget = { viewModel?.applyIntakeTarget() },
        onAppFontSizeSelected = onAppFontSizeSelected,
        onOpenMealPreference = onOpenMealPreference,
        onCheckFoodData = { viewModel?.checkFoodDataNow() },
        onCheckAppUpdate = onCheckAppUpdate,
        onBack = { selectedSection = null }
    )
}

internal enum class SettingsSection(val title: String, val description: String) {
    BODY("내 신체정보", "성별, 나이, 키와 몸무게"),
    ENERGY("에너지 목표", "BMR, 활동 수준, 유지 칼로리와 섭취 목표"),
    WEIGHT("체중 목표", "목표 체중과 기간별 섭취 계획"),
    RECOMMENDATION("추천 설정", "식사 배분, 알레르기와 선호 음식"),
    FOOD_DATA("음식 데이터", "공식 데이터 버전과 자동 업데이트"),
    DISPLAY("화면 설정", "글씨 크기"),
    APP_INFO("앱 정보", "버전과 이용 안내")
}

@Composable
internal fun SettingsMenuContent(
    bodyProfileState: BodyProfileUiState,
    energyState: EnergySettingsUiState,
    targetCalories: Int,
    appFontSize: AppFontSize,
    foodDataUpdateState: FoodDataUpdateState = FoodDataUpdateState(),
    onSectionSelected: (SettingsSection) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding()
        ) {
            val dense = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f || maxHeight < 590.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    horizontal = WellnessSpacing.ScreenHorizontal,
                    vertical = if (dense) 5.dp else WellnessSpacing.Compact
                ),
                verticalArrangement = Arrangement.spacedBy(if (dense) 6.dp else 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("앱 설정", style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() })
                    Text("나에게 맞게 관리하세요", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WellnessCard(Modifier.fillMaxWidth()) {
                    SettingsSection.entries.forEachIndexed { index, section ->
                        val summary = when (section) {
                            SettingsSection.BODY -> if (bodyProfileState.estimatedBmrKcal != null) {
                                "키 ${bodyProfileState.heightInput} cm · 몸무게 ${bodyProfileState.weightInput} kg"
                            } else section.description
                            SettingsSection.ENERGY -> energyState.maintenancePreviewKcal?.let {
                                "목표 ${NumberFormat.getIntegerInstance().format(targetCalories)} kcal · 유지 ${NumberFormat.getIntegerInstance().format(it)}"
                            } ?: section.description
                            SettingsSection.DISPLAY -> "현재 ${appFontSize.label}"
                            SettingsSection.FOOD_DATA -> "${foodDataUpdateState.activeVersion} 기준 · 자동 확인 사용 중"
                            SettingsSection.APP_INFO -> "버전, 이용약관과 안내"
                            else -> section.description
                        }
                        SettingsMenuCard(
                            section = section,
                            summary = summary,
                            dense = dense,
                            modifier = Modifier.fillMaxWidth().height(if (dense) 52.dp else 59.dp),
                            onClick = { onSectionSelected(section) }
                        )
                        if (index < SettingsSection.entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = if (dense) 48.dp else 55.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .48f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuCard(
    section: SettingsSection,
    summary: String,
    dense: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val icon = when (section) {
        SettingsSection.BODY -> Icons.Rounded.Person
        SettingsSection.ENERGY -> Icons.Rounded.MonitorHeart
        SettingsSection.WEIGHT -> Icons.Rounded.Flag
        SettingsSection.RECOMMENDATION -> Icons.Rounded.RestaurantMenu
        SettingsSection.FOOD_DATA -> Icons.Rounded.CloudSync
        SettingsSection.DISPLAY -> Icons.Rounded.TextFields
        SettingsSection.APP_INFO -> Icons.Rounded.Info
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (dense) 9.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 13.dp)
        ) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(if (dense) 6.dp else 7.dp).size(if (dense) 18.dp else 20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "${section.title} 열기", modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsSectionContent(
    section: SettingsSection,
    targetCalories: Int,
    energyState: EnergySettingsUiState,
    appFontSize: AppFontSize,
    bodyProfileState: BodyProfileUiState,
    weightGoalState: WeightGoalUiState,
    foodDataUpdateState: FoodDataUpdateState,
    appUpdateState: AppUpdateUiState,
    onGoalClick: () -> Unit,
    onBmrChange: (String) -> Unit,
    onActivityLevelSelected: (ActivityLevel) -> Unit,
    onCustomPalChange: (String) -> Unit,
    onTargetModeSelected: (TargetMode) -> Unit,
    onSaveEnergyProfile: () -> Unit,
    onBodySexSelected: (BodySex) -> Unit,
    onBodyAgeChange: (String) -> Unit,
    onBodyHeightChange: (String) -> Unit,
    onBodyWeightChange: (String) -> Unit,
    onSaveBodyProfile: () -> Unit,
    onUseEstimatedBmr: () -> Unit,
    onWeightGoalTargetChange: (String) -> Unit,
    onWeightGoalWeeksChange: (String) -> Unit,
    onCalculateWeightGoal: () -> Unit,
    onApplyWeightGoal: () -> Unit,
    onIntakeTargetChange: (String) -> Unit,
    onCalculateIntakePace: () -> Unit,
    onApplyIntakeTarget: () -> Unit,
    onAppFontSizeSelected: (AppFontSize) -> Unit,
    onOpenMealPreference: () -> Unit,
    onCheckFoodData: () -> Unit,
    onCheckAppUpdate: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WellnessTopAppBar(
                title = section.title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "설정 메뉴로 돌아가기")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(
                horizontal = WellnessSpacing.ScreenHorizontal,
                vertical = WellnessSpacing.Compact
            ),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            item {
                Text(section.description, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (section) {
                SettingsSection.BODY -> item {
                    BodyProfileCard(
                        state = bodyProfileState,
                        onSexSelected = onBodySexSelected,
                        onAgeChange = onBodyAgeChange,
                        onHeightChange = onBodyHeightChange,
                        onWeightChange = onBodyWeightChange,
                        onSave = onSaveBodyProfile,
                        onUseEstimatedBmr = onUseEstimatedBmr
                    )
                }
                SettingsSection.ENERGY -> {
                    item {
                        EnergySettingsCard(
                            state = energyState,
                            onBmrChange = onBmrChange,
                            onActivityLevelSelected = onActivityLevelSelected,
                            onCustomPalChange = onCustomPalChange,
                            onTargetModeSelected = onTargetModeSelected,
                            onSave = onSaveEnergyProfile,
                            targetCalories = targetCalories
                        )
                    }
                    item { ManualCalorieGoalCard(targetCalories, onGoalClick) }
                    item { HealthInformationCard() }
                }
                SettingsSection.WEIGHT -> item {
                    WeightGoalCard(
                        state = weightGoalState,
                        currentWeightText = bodyProfileState.weightInput,
                        energyState = energyState,
                        onTargetWeightChange = onWeightGoalTargetChange,
                        onWeeksChange = onWeightGoalWeeksChange,
                        onCalculate = onCalculateWeightGoal,
                        onApply = onApplyWeightGoal,
                        onIntakeTargetChange = onIntakeTargetChange,
                        onCalculateIntakePace = onCalculateIntakePace,
                        onApplyIntakeTarget = onApplyIntakeTarget
                    )
                }
                SettingsSection.RECOMMENDATION -> item {
                    MealPreferenceLinkCard(onOpenMealPreference)
                }
                SettingsSection.FOOD_DATA -> item {
                    FoodDataUpdateCard(foodDataUpdateState, onCheckFoodData)
                }
                SettingsSection.DISPLAY -> {
                    item { AppFontSizeCard(appFontSize, onAppFontSizeSelected) }
                }
                SettingsSection.APP_INFO -> {
                    item { AppInformationCard(appUpdateState, onCheckAppUpdate) }
                }
            }
        }
    }
}

@Composable
private fun ManualCalorieGoalCard(targetCalories: Int, onClick: () -> Unit) {
    WellnessCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
            ListItem(
                headlineContent = { Text("직접 설정한 일일 목표") },
                supportingContent = {
                    Text("${NumberFormat.getIntegerInstance().format(targetCalories)} kcal · 기존 목표 이력은 보존됩니다.")
                },
                leadingContent = { Icon(Icons.Rounded.Flag, contentDescription = null) },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = "직접 목표 변경") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}

@Composable
private fun MealPreferenceLinkCard(onClick: () -> Unit) {
    WellnessCard {
        Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
            ListItem(
                headlineContent = { Text("식사 추천 설정") },
                supportingContent = { Text("식사 배분, 알레르기 주의와 선호 조건") },
                leadingContent = { Icon(Icons.Rounded.RestaurantMenu, contentDescription = null) },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = "식사 추천 설정 열기") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    }
}

@Composable
private fun AppInformationCard(
    updateState: AppUpdateUiState,
    onCheckAppUpdate: () -> Unit
) {
    val updateSummary = when (updateState.phase) {
        AppUpdatePhase.CHECKING -> "업데이트 확인 중…"
        AppUpdatePhase.UP_TO_DATE -> "현재 최신 버전을 사용 중이에요."
        AppUpdatePhase.AVAILABLE -> "새 버전 ${updateState.release?.versionName.orEmpty()}을 사용할 수 있어요."
        AppUpdatePhase.FAILED -> updateState.message ?: "업데이트 정보를 확인하지 못했어요."
        else -> "현재 버전 ${BuildConfig.VERSION_NAME}"
    }
    WellnessCard {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text("식사와 칼로리 기록을 한곳에서 관리합니다.") },
                leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                trailingContent = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelLarge) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .48f))
            Surface(
                onClick = onCheckAppUpdate,
                enabled = updateState.phase != AppUpdatePhase.CHECKING,
                color = androidx.compose.ui.graphics.Color.Transparent
            ) {
                ListItem(
                    headlineContent = { Text("앱 업데이트") },
                    supportingContent = { Text(updateSummary) },
                    leadingContent = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
                    trailingContent = {
                        if (updateState.phase == AppUpdatePhase.CHECKING) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "앱 업데이트 확인")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

@Composable
internal fun FoodDataUpdateCard(state: FoodDataUpdateState, onCheckNow: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm") }
    fun formatted(epochMillis: Long?): String = epochMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(formatter)
    } ?: "아직 확인하지 않음"
    WellnessCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("공식 음식 데이터", style = MaterialTheme.typography.titleMedium)
            }
            Text("${state.activeVersion} 기준", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold)
            Text("마지막 업데이트 확인 · ${formatted(state.lastCheckedAt)}",
                style = MaterialTheme.typography.bodyMedium)
            Text("자동 업데이트 · 사용 중 (약 7일 주기)", style = MaterialTheme.typography.bodyMedium)
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.status == FoodDataUpdateStatus.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCheckNow,
                enabled = state.status != FoodDataUpdateStatus.CHECKING,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                if (state.status == FoodDataUpdateStatus.CHECKING) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else Text("지금 업데이트 확인")
            }
            Text(
                "공식 파일에 인증이 필요한 경우 현재 검증 스냅샷을 유지합니다. 업데이트 실패 시 기존 검색 데이터와 과거 기록은 변경되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SettingsContent(
    targetCalories: Int,
    energyState: EnergySettingsUiState,
    appFontSize: AppFontSize = AppFontSize.NORMAL,
    bodyProfileState: BodyProfileUiState = BodyProfileUiState(),
    weightGoalState: WeightGoalUiState = WeightGoalUiState(),
    onGoalClick: () -> Unit,
    onBmrChange: (String) -> Unit,
    onActivityLevelSelected: (ActivityLevel) -> Unit,
    onCustomPalChange: (String) -> Unit,
    onTargetModeSelected: (TargetMode) -> Unit,
    onSaveEnergyProfile: () -> Unit,
    onBodySexSelected: (BodySex) -> Unit = {},
    onBodyAgeChange: (String) -> Unit = {},
    onBodyHeightChange: (String) -> Unit = {},
    onBodyWeightChange: (String) -> Unit = {},
    onSaveBodyProfile: () -> Unit = {},
    onUseEstimatedBmr: () -> Unit = {},
    onWeightGoalTargetChange: (String) -> Unit = {},
    onWeightGoalWeeksChange: (String) -> Unit = {},
    onCalculateWeightGoal: () -> Unit = {},
    onApplyWeightGoal: () -> Unit = {},
    onIntakeTargetChange: (String) -> Unit = {},
    onCalculateIntakePace: () -> Unit = {},
    onApplyIntakeTarget: () -> Unit = {},
    onAppFontSizeSelected: (AppFontSize) -> Unit = {},
    onOpenMealPreference: () -> Unit = {}
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { WellnessTopAppBar("설정") }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding().navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = WellnessSpacing.ScreenHorizontal,
                end = WellnessSpacing.ScreenHorizontal,
                top = WellnessSpacing.Compact,
                bottom = WellnessSpacing.Section
            ),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            item {
                WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primary) {
                    Column(Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("나에게 맞춘 한 끼", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text("내 식사 설정", style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.semantics { heading() })
                        Text("목표와 추천 기준을 내 생활에 맞게 조정해요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            item {
                SectionHeader(
                    title = "글씨 크기",
                    supportingText = "앱 안의 글씨 크기를 선택하면 바로 적용되고 다음 실행에도 유지됩니다."
                )
            }
            item {
                AppFontSizeCard(
                    selected = appFontSize,
                    onSelected = onAppFontSizeSelected
                )
            }
            item {
                SectionHeader(
                    title = "내 정보",
                    supportingText = "신체정보를 저장하고 필요할 때 기초대사량 계산에 사용할 수 있어요."
                )
            }
            item {
                BodyProfileCard(
                    state = bodyProfileState,
                    onSexSelected = onBodySexSelected,
                    onAgeChange = onBodyAgeChange,
                    onHeightChange = onBodyHeightChange,
                    onWeightChange = onBodyWeightChange,
                    onSave = onSaveBodyProfile,
                    onUseEstimatedBmr = onUseEstimatedBmr
                )
            }
            item {
                SectionHeader(
                    title = "에너지와 활동",
                    supportingText = "쉬는 동안의 에너지와 평소 움직임을 함께 살펴봐요."
                )
            }
            item {
                EnergySettingsCard(
                    state = energyState,
                    onBmrChange = onBmrChange,
                    onActivityLevelSelected = onActivityLevelSelected,
                    onCustomPalChange = onCustomPalChange,
                    onTargetModeSelected = onTargetModeSelected,
                    onSave = onSaveEnergyProfile,
                    targetCalories = targetCalories
                )
            }
            item {
                SectionHeader(
                    title = "체중 감량 목표",
                    supportingText = "목표 체중과 기간으로 단순 에너지 적자를 계산해요. 계산만으로 기존 목표는 바뀌지 않습니다.",
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            item {
                WeightGoalCard(
                    state = weightGoalState,
                    currentWeightText = bodyProfileState.weightInput,
                    energyState = energyState,
                    onTargetWeightChange = onWeightGoalTargetChange,
                    onWeeksChange = onWeightGoalWeeksChange,
                    onCalculate = onCalculateWeightGoal,
                    onApply = onApplyWeightGoal,
                    onIntakeTargetChange = onIntakeTargetChange,
                    onCalculateIntakePace = onCalculateIntakePace,
                    onApplyIntakeTarget = onApplyIntakeTarget
                )
            }
            item {
                SectionHeader(
                    title = "내가 정한 목표",
                    supportingText = "목표 기준을 ‘직접 설정한 목표’로 선택했을 때 사용합니다.",
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            item {
                WellnessCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Surface(
                        onClick = onGoalClick,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("일일 목표 칼로리", style = MaterialTheme.typography.titleMedium)
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = "직접 목표 변경")
                            }
                            MetricValue(NumberFormat.getNumberInstance().format(targetCalories), "kcal")
                            Text("기존 목표 이력은 그대로 보존됩니다.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    title = "추천과 식사 취향",
                    supportingText = "식사 배분, 알레르기, 선호와 조리 조건을 관리합니다.",
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            item {
                WellnessCard {
                    Surface(
                        onClick = onOpenMealPreference,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("식사 추천 설정") },
                            supportingContent = { Text("기본 25/35/30/10 배분과 알레르기 주의") },
                            leadingContent = {
                                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Icon(Icons.Rounded.RestaurantMenu, null, Modifier.padding(10.dp))
                                }
                            },
                            trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = "식사 추천 설정 열기") },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            }
            item {
                HealthInformationCard()
            }
            item {
                SectionHeader(title = "앱 정보", modifier = Modifier.padding(top = 14.dp))
            }
            item {
                WellnessCard {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.app_name)) },
                        supportingContent = { Text("식사와 칼로리 기록을 한곳에서 관리합니다.") },
                        leadingContent = {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(Icons.Rounded.Info, null, Modifier.padding(10.dp))
                            }
                        },
                        trailingContent = {
                            Text("1.0.0", style = MaterialTheme.typography.labelLarge)
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppFontSizeCard(
    selected: AppFontSize,
    onSelected: (AppFontSize) -> Unit
) {
    WellnessCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("읽기 편한 크기", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppFontSize.entries.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelected(option) },
                        label = { Text(option.label) },
                        leadingIcon = if (selected == option) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }
            Text(
                "Android의 시스템 글씨 크기와 함께 적용됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun WeightGoalCard(
    state: WeightGoalUiState,
    currentWeightText: String,
    energyState: EnergySettingsUiState = EnergySettingsUiState(),
    onTargetWeightChange: (String) -> Unit,
    onWeeksChange: (String) -> Unit,
    onCalculate: () -> Unit,
    onApply: () -> Unit,
    onIntakeTargetChange: (String) -> Unit = {},
    onCalculateIntakePace: () -> Unit = {},
    onApplyIntakeTarget: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val weeksFocusRequester = remember { FocusRequester() }
    var calculationExpanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)) {
        WellnessCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("감량 목표 입력", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
            }
            Text(
                if (currentWeightText.isBlank()) "현재 몸무게를 내 정보에서 먼저 저장해 주세요."
                else "현재 몸무게 ${currentWeightText}kg",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
                BoxWithConstraints {
                    if (maxWidth < 500.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            WeightTargetField(state, onTargetWeightChange, Modifier.fillMaxWidth())
                            WeightWeeksField(state, onWeeksChange, focusManager, onCalculate,
                                Modifier.fillMaxWidth().focusRequester(weeksFocusRequester))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            WeightTargetField(state, onTargetWeightChange, Modifier.weight(1f))
                            WeightWeeksField(state, onWeeksChange, focusManager, onCalculate,
                                Modifier.weight(1f).focusRequester(weeksFocusRequester))
                        }
                    }
                }
                state.calculationError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = {
                    focusManager.clearFocus()
                    onCalculate()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("결과 확인")
                }
            }
        }

        state.calculation?.let { result ->
            WeightGoalResultCard(
                state = state,
                result = result,
                expanded = calculationExpanded,
                onExpandedChange = { calculationExpanded = it },
                onWeeksChange = onWeeksChange,
                onRequestWeeksInput = { weeksFocusRequester.requestFocus() },
                onApply = onApply
            )
        }

        EnergyBasisCard(energyState)
        IntakePaceCard(state, energyState, onIntakeTargetChange, onCalculateIntakePace, onApplyIntakeTarget)

        state.applyMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium) }
        state.applyError?.let { Text(it, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium) }
        state.storedGoal?.let { stored ->
            Text(
                "적용된 목표 ${formatWeight(stored.currentWeightKg)}kg → ${formatWeight(stored.targetWeightKg)}kg · ${stored.durationWeeks}주 · ${NumberFormat.getNumberInstance().format(stored.appliedTargetKcal)} kcal/일",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "7,700 kcal/kg 기반의 단순 에너지 환산이며 실제 체중 변화를 보장하지 않습니다. 성인 일반 안내용으로, 의료 진단·개인별 영양 처방을 대신하지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeightTargetField(
    state: WeightGoalUiState,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = state.targetWeightInput,
        onValueChange = onValueChange,
        label = { Text("목표 체중") },
        suffix = { Text("kg") },
        isError = state.targetWeightError != null,
        supportingText = state.targetWeightError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun WeightWeeksField(
    state: WeightGoalUiState,
    onValueChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onCalculate: () -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = state.durationWeeksInput,
        onValueChange = onValueChange,
        label = { Text("목표 기간") },
        suffix = { Text("주") },
        isError = state.durationError != null,
        supportingText = state.durationError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
            onCalculate()
        }),
        singleLine = true,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeightGoalResultCard(
    state: WeightGoalUiState,
    result: com.example.healthcare.domain.WeightGoalCalculation,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onWeeksChange: (String) -> Unit,
    onRequestWeeksInput: () -> Unit,
    onApply: () -> Unit
) {
    val needsAdjustment = !result.canApply
    WellnessCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (needsAdjustment) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${formatWeight(result.currentWeightKg)}kg → ${formatWeight(result.targetWeightKg)}kg",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            Text("${result.durationWeeks}주 동안 ${formatWeight(result.lossKg)}kg 감량 목표",
                style = MaterialTheme.typography.bodyLarge)
            if (needsAdjustment) {
                Text("이 기간은 너무 짧아요", style = MaterialTheme.typography.titleLarge)
                Text(
                    "현재 설정으로는 식사량 조절만으로 맞추기 어려운 목표예요. 기간을 늘리면 하루 목표를 더 현실적으로 맞출 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("기간을 늘려보세요", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(8, 12, 16).forEach { weeks ->
                        AssistChip(onClick = { onWeeksChange(weeks.toString()) }, label = { Text("${weeks}주") })
                    }
                    AssistChip(onClick = onRequestWeeksInput, label = { Text("직접 입력") })
                }
            } else {
                Text("현재 설정에서 적용할 수 있는 단순 목표예요", style = MaterialTheme.typography.titleMedium)
                Text("하루 섭취 목표", style = MaterialTheme.typography.labelLarge)
                MetricValue(NumberFormat.getNumberInstance().format(result.proposedIntakeKcal), "kcal/일")
                Text(
                    "하루 평균 약 ${NumberFormat.getNumberInstance().format(result.dailyDeficitKcal)} kcal의 차이를 기준으로 계산했어요.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onApply, enabled = !state.isApplying, modifier = Modifier.fillMaxWidth()) {
                    if (state.isApplying) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("이 목표로 설정")
                }
            }
            TextButton(onClick = { onExpandedChange(!expanded) }) {
                Text(if (expanded) "계산 방법 접기" else "계산 방법 보기")
            }
        }
    }
    if (expanded) WeightGoalCalculationDetails(result)
}

@Composable
private fun WeightGoalCalculationDetails(result: com.example.healthcare.domain.WeightGoalCalculation) {
    WellnessCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val totalDifference = (result.lossKg * 7_700).toInt()
            val totalDays = result.durationWeeks * 7
            Text("왜 ${NumberFormat.getNumberInstance().format(result.dailyDeficitKcal)} kcal인가요?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text("감량 목표 ${formatWeight(result.lossKg)}kg", style = MaterialTheme.typography.bodyMedium)
            Text("1kg ≈ 7,700 kcal 단순 환산", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatWeight(result.lossKg)}kg × 7,700 = 총 ${NumberFormat.getNumberInstance().format(totalDifference)} kcal",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${NumberFormat.getNumberInstance().format(totalDifference)} ÷ ${totalDays}일 = 하루 평균 약 ${NumberFormat.getNumberInstance().format(result.dailyDeficitKcal)} kcal",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "이 수치는 먹는 칼로리가 아니라 목표 기간을 맞추기 위해 필요한 하루 평균 에너지 차이입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (result.dailyDeficitKcal >= result.maintenanceKcal) {
                Text(
                    "계산상 필요한 적자가 현재 유지 예상보다 큽니다. 음식 섭취만으로는 이 기간을 맞출 수 없어요.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun EnergyBasisCard(state: EnergySettingsUiState) {
    WellnessCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("내 계산 기준", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            EnergyBasisRow("기초대사량", state.bmrInput.toIntOrNull())
            Text("활동량  ${state.activityLevel?.let(::activityLevelLabel) ?: "설정 필요"}",
                style = MaterialTheme.typography.bodyLarge)
            EnergyBasisRow("하루 유지 예상", state.maintenancePreviewKcal)
            Text("기초대사량에 평소 활동 수준을 반영한 추정값이에요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EnergyBasisRow(label: String, value: Int?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value?.let { "${NumberFormat.getNumberInstance().format(it)} kcal" } ?: "설정 필요",
            style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun IntakePaceCard(
    state: WeightGoalUiState,
    energyState: EnergySettingsUiState,
    onInputChange: (String) -> Unit,
    onCalculate: () -> Unit,
    onApply: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    WellnessCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("하루 섭취량으로 예상해 보기", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text("하루에 먹을 칼로리를 정하면 현재 유지 예상 기준으로 1kg 변화까지 걸리는 기간을 단순 계산해 드려요.",
                style = MaterialTheme.typography.bodyMedium)
            Text(
                "현재 하루 유지 예상  ${energyState.maintenancePreviewKcal?.let { NumberFormat.getNumberInstance().format(it) + " kcal" } ?: "설정 필요"}",
                style = MaterialTheme.typography.bodyLarge
            )
            TextField(
                value = state.intakeTargetInput,
                onValueChange = onInputChange,
                label = { Text("하루 섭취 목표") },
                suffix = { Text("kcal") },
                isError = state.intakeTargetError != null,
                supportingText = state.intakeTargetError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onCalculate() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            state.intakePaceCalculation?.let { pace ->
                Surface(
                    color = if (pace.status == IntakePaceStatus.TOO_LOW) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (pace.status) {
                            IntakePaceStatus.DEFICIT -> {
                                Text("현재 설정을 유지한다고 단순 계산하면", style = MaterialTheme.typography.bodyMedium)
                                Text("1kg 감량까지", style = MaterialTheme.typography.labelLarge)
                                Text(formatEstimatedDays(requireNotNull(pace.estimatedDaysForOneKg)),
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.semantics { heading() })
                                Text("약 ${pace.estimatedDaysForOneKg}일", style = MaterialTheme.typography.bodyMedium)
                                Text("현재 유지 예상 ${NumberFormat.getNumberInstance().format(pace.maintenanceKcal)} kcal",
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("하루 약 ${NumberFormat.getNumberInstance().format(kotlin.math.abs(pace.dailyBalanceKcal))} kcal가 유지 예상보다 적어요.",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            IntakePaceStatus.MAINTENANCE -> {
                                Text("현재 설정은 유지 예상과 같아요.", style = MaterialTheme.typography.titleMedium)
                                Text("단순 계산상 체중 변화 방향을 만드는 에너지 차이가 없습니다.",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            IntakePaceStatus.SURPLUS -> {
                                Text("현재 설정을 유지한다고 단순 계산하면", style = MaterialTheme.typography.bodyMedium)
                                Text("1kg 증가 상당까지", style = MaterialTheme.typography.labelLarge)
                                Text(formatEstimatedDays(requireNotNull(pace.estimatedDaysForOneKg)),
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.semantics { heading() })
                                Text("약 ${pace.estimatedDaysForOneKg}일", style = MaterialTheme.typography.bodyMedium)
                                Text("현재 유지 예상 ${NumberFormat.getNumberInstance().format(pace.maintenanceKcal)} kcal",
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("하루 약 ${NumberFormat.getNumberInstance().format(pace.dailyBalanceKcal)} kcal가 유지 예상보다 많아요.",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            IntakePaceStatus.TOO_LOW -> {
                                Text("이 섭취 목표는 너무 낮아요", style = MaterialTheme.typography.titleMedium)
                                Text(pace.guidance, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text("실제 체중 변화는 달라질 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(onClick = { focusManager.clearFocus(); onCalculate() }, modifier = Modifier.fillMaxWidth()) {
                Text("예상 기간 확인")
            }
            OutlinedButton(
                onClick = onApply,
                enabled = state.intakePaceCalculation?.canApply == true && !state.isApplyingIntakeTarget,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isApplyingIntakeTarget) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("이 섭취 목표로 설정")
            }
            state.intakeApplyMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.intakeApplyError?.let { ValidationText(it) }
        }
    }
}

private fun formatEstimatedDays(days: Int): String {
    val weeks = days / 7
    val remainingDays = days % 7
    return when {
        weeks == 0 -> "약 ${remainingDays}일"
        remainingDays == 0 -> "약 ${weeks}주"
        else -> "약 ${weeks}주 ${remainingDays}일"
    }
}

private fun formatWeight(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.KOREA, "%.1f", value)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BodyProfileCard(
    state: BodyProfileUiState,
    onSexSelected: (BodySex) -> Unit,
    onAgeChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onUseEstimatedBmr: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("내 신체정보", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text("더 정확한 맞춤 관리를 위해 관리하세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("성별", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(62.dp))
            listOf(BodySex.MALE to "남성", BodySex.FEMALE to "여성").forEach { (sex, label) ->
                FilterChip(
                    selected = state.sex == sex,
                    onClick = { onSexSelected(sex) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        state.sexError?.let { ValidationText(it) }
        SettingsProfileField("나이", state.ageInput, onAgeChange, "세", KeyboardType.Number, state.ageError)
        SettingsProfileField("키", state.heightInput, onHeightChange, "cm", KeyboardType.Decimal, state.heightError)
        SettingsProfileField("몸무게", state.weightInput, onWeightChange, "kg", KeyboardType.Decimal, state.weightError)
        Text(
            "이 정보는 칼로리 계산과 맞춤 추천에만 사용돼요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.estimatedBmrKcal?.let { estimated ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("예상 기초대사량", style = MaterialTheme.typography.labelSmall)
                        Text("${NumberFormat.getNumberInstance().format(estimated)} kcal/일",
                            style = MaterialTheme.typography.titleSmall)
                    }
                    TextButton(onClick = onUseEstimatedBmr) { Text("BMR에 사용") }
                }
            }
        }
        state.saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.saveError?.let { ValidationText(it) }
        Button(onClick = onSave, enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("저장하기")
        }
    }
}

@Composable
private fun SettingsProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    keyboardType: KeyboardType,
    errorMessage: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(90.dp))
            TextField(
                value = value,
                onValueChange = onValueChange,
                suffix = { Text(unit) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                isError = errorMessage != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { errorMessage?.let { error(it) } },
                shape = MaterialTheme.shapes.medium,
                colors = settingsFilledFieldColors()
            )
        }
        errorMessage?.let { ValidationText(it) }
    }
}

@Composable
private fun settingsFilledFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
)

@Composable
private fun ValidationText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { error(message) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EnergySettingsCard(
    state: EnergySettingsUiState,
    onBmrChange: (String) -> Unit,
    onActivityLevelSelected: (ActivityLevel) -> Unit,
    onCustomPalChange: (String) -> Unit,
    onTargetModeSelected: (TargetMode) -> Unit,
    onSave: () -> Unit,
    targetCalories: Int? = null
) {
    WellnessCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "나의 에너지 기준",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Text(
                "기초대사량은 휴식 상태에서 기본적인 생명 기능을 유지하는 데 필요한 에너지입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val bmrPreview = state.bmrInput.toIntOrNull()
            val effectiveTargetPreview = when (state.targetMode) {
                TargetMode.MANUAL -> targetCalories
                TargetMode.BMR -> bmrPreview
                TargetMode.MAINTENANCE -> state.maintenancePreviewKcal
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "나의 하루 에너지",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    EnergySummaryRow(
                        label = "기초대사량",
                        value = bmrPreview,
                        description = "몸이 기본적으로 사용하는 에너지"
                    )
                    EnergySummaryRow(
                        label = "하루 유지 예상",
                        value = state.maintenancePreviewKcal,
                        description = "평소 활동량을 고려한 예상값"
                    )
                    EnergySummaryRow(
                        label = "하루 섭취 목표",
                        value = effectiveTargetPreview,
                        description = targetModeDescription(state.targetMode)
                    )
                }
            }
            Text(
                "에너지 기준 조정",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )
            OutlinedTextField(
                value = state.bmrInput,
                onValueChange = onBmrChange,
                label = { Text("기초대사량 직접 수정") },
                suffix = { Text("kcal/일") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = state.bmrError != null,
                supportingText = {
                    Text(state.bmrError ?: state.bmrReviewMessage ?: "내 정보의 계산값을 사용하거나 알고 있는 값을 입력할 수 있어요.")
                },
                modifier = Modifier.fillMaxWidth().semantics {
                    state.bmrError?.let { message -> error(message) }
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("활동 수준", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityLevel.entries.forEach { level ->
                        FilterChip(
                            selected = state.activityLevel == level,
                            onClick = { onActivityLevelSelected(level) },
                            label = { Text(activityLevelLabel(level)) },
                            leadingIcon = if (state.activityLevel == level) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
                state.activityLevel?.let { level ->
                    Text(
                        activityLevelDescription(level),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.activityError?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { error(message) }
                    )
                }
                Text(
                    "활동 수준은 정밀한 측정값이 아닌 추정값입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.activityLevel == ActivityLevel.CUSTOM) {
                OutlinedTextField(
                    value = state.customPalInput,
                    onValueChange = onCustomPalChange,
                    label = { Text("나만의 활동 계수") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = state.customPalError != null,
                    supportingText = { Text(state.customPalError ?: "활동 계수(PAL) 1.40 이상 2.40 이하") },
                    modifier = Modifier.fillMaxWidth().semantics {
                        state.customPalError?.let { message -> error(message) }
                    }
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("예상 유지 칼로리", style = MaterialTheme.typography.labelLarge)
                    if (state.maintenancePreviewKcal != null) {
                        MetricValue(NumberFormat.getNumberInstance().format(state.maintenancePreviewKcal), "kcal/일")
                    } else {
                        Text("기초대사량과 활동 수준을 입력해 주세요.", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "입력한 기초대사량과 활동 수준을 이용한 추정값입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("목표 기준", style = MaterialTheme.typography.titleSmall)
                TargetMode.entries.forEach { mode ->
                    val selected = state.targetMode == mode
                    val accent = when (mode) {
                        TargetMode.MANUAL -> MaterialTheme.colorScheme.primary
                        TargetMode.BMR -> MaterialTheme.colorScheme.tertiary
                        TargetMode.MAINTENANCE -> MaterialTheme.colorScheme.secondary
                    }
                    val container = when (mode) {
                        TargetMode.MANUAL -> MaterialTheme.colorScheme.primaryContainer
                        TargetMode.BMR -> MaterialTheme.colorScheme.tertiaryContainer
                        TargetMode.MAINTENANCE -> MaterialTheme.colorScheme.secondaryContainer
                    }
                    Surface(
                        onClick = { onTargetModeSelected(mode) },
                        color = if (selected) container else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(if (selected) 2.dp else 1.dp,
                            if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(targetModeLabel(mode), style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                Text(targetModeDescription(mode), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                when (mode) {
                                    TargetMode.MANUAL -> targetCalories
                                    TargetMode.BMR -> bmrPreview
                                    TargetMode.MAINTENANCE -> state.maintenancePreviewKcal
                                }?.let { "${NumberFormat.getNumberInstance().format(it)} kcal" } ?: "계산 필요",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = "선택됨", tint = accent)
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("현재 목표", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(targetCalories?.let { "${NumberFormat.getNumberInstance().format(it)} kcal" } ?: "설정 필요",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("변경 후", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(effectiveTargetPreview?.let { "${NumberFormat.getNumberInstance().format(it)} kcal" } ?: "계산 필요",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            state.saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            state.saveError?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { error(message) }
                )
            }
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("에너지 기준 저장")
                }
            }
        }
    }
}

@Composable
private fun EnergySummaryRow(label: String, value: Int?, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            value?.let { "${NumberFormat.getNumberInstance().format(it)} kcal" } ?: "설정 전",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HealthInformationCard() {
    WellnessCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("안내", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(
                "이 기능은 일반적인 에너지 추정 도구이며 의료적 진단이나 개인별 영양 처방을 제공하지 않습니다.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "18세 미만, 임신·수유 중이거나 질환 치료 중인 경우, 또는 식사와 체중 관리로 어려움을 겪는 경우에는 전문가와 상담해 주세요.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun GoalDialog(
    value: String,
    isSaving: Boolean,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
        title = { Text("직접 목표 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("‘직접 설정한 목표’ 모드에서 사용할 값을 입력하세요.")
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("목표 칼로리") },
                    suffix = { Text("kcal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving && (value.toIntOrNull() ?: 0) > 0) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("저장")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

private fun activityLevelLabel(level: ActivityLevel): String = when (level) {
    ActivityLevel.LIGHT -> "가벼운 활동"
    ActivityLevel.MODERATE -> "보통 활동"
    ActivityLevel.HIGH -> "높은 활동"
    ActivityLevel.CUSTOM -> "직접 설정"
}

private fun activityLevelDescription(level: ActivityLevel): String = when (level) {
    ActivityLevel.LIGHT -> "주로 앉아서 생활하며 규칙적인 고강도 활동이 적음 · PAL 1.55"
    ActivityLevel.MODERATE -> "걷기나 운동을 규칙적으로 하거나 일상 활동량이 보통 수준임 · PAL 1.75"
    ActivityLevel.HIGH -> "강도 높은 운동이나 육체 활동을 자주 수행함 · PAL 2.20"
    ActivityLevel.CUSTOM -> "직접 알고 있는 PAL 값을 입력"
}

private fun targetModeLabel(mode: TargetMode): String = when (mode) {
    TargetMode.MANUAL -> "직접 설정한 목표"
    TargetMode.BMR -> "기초대사량 기준"
    TargetMode.MAINTENANCE -> "활동량 포함 유지 기준"
}

private fun targetModeDescription(mode: TargetMode): String = when (mode) {
    TargetMode.MANUAL -> "기존에 직접 설정한 일일 목표를 그대로 사용합니다."
    TargetMode.BMR -> "일일 목표를 입력한 기초대사량과 같게 설정합니다. 일반적인 권장 섭취량을 뜻하지 않습니다."
    TargetMode.MAINTENANCE -> "일일 목표를 기초대사량 × PAL로 계산한 예상 유지 칼로리로 설정합니다."
}

@Preview(name = "설정 에너지 360", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun SettingsPreview() {
    HealthCareTheme {
        SettingsContent(
            targetCalories = 2000,
            energyState = EnergySettingsUiState(
                bmrInput = "1500",
                activityLevel = ActivityLevel.LIGHT,
                targetMode = TargetMode.MAINTENANCE,
                maintenancePreviewKcal = 2325
            ),
            onGoalClick = {},
            onBmrChange = {},
            onActivityLevelSelected = {},
            onCustomPalChange = {},
            onTargetModeSelected = {},
            onSaveEnergyProfile = {},
            onOpenMealPreference = {}
        )
    }
}

@Preview(name = "설정 다크", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun SettingsDarkPreview() {
    HealthCareTheme(darkTheme = true) {
        EnergySettingsCard(
            state = EnergySettingsUiState(),
            onBmrChange = {},
            onActivityLevelSelected = {},
            onCustomPalChange = {},
            onTargetModeSelected = {},
            onSave = {}
        )
    }
}
