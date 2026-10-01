package com.example.healthcare.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.healthcare.ui.theme.NightHero
import com.example.healthcare.ui.theme.NightBackground
import com.example.healthcare.ui.theme.NightText
import com.example.healthcare.ui.theme.PaperSurface
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.R
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.data.repository.StepCounterRepository
import com.example.healthcare.ui.components.SectionHeader
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.WellnessTopAppBar
import com.example.healthcare.ui.components.BalancedCharacterIllustration
import com.example.healthcare.ui.components.MacroSummaryRow
import com.example.healthcare.ui.components.RecommendationPhoto
import com.example.healthcare.ui.components.OverCharacterIllustration
import com.example.healthcare.ui.components.SlimCharacterIllustration
import com.example.healthcare.ui.components.WarningCharacterIllustration
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.theme.HomeLavender
import com.example.healthcare.ui.theme.HomeLavenderDark
import com.example.healthcare.ui.theme.HomePurple
import com.example.healthcare.ui.theme.NutritionCarbohydrate
import com.example.healthcare.ui.theme.NutritionProtein
import com.example.healthcare.ui.theme.NutritionFat
import com.example.healthcare.ui.theme.WellnessAmber
import com.example.healthcare.ui.theme.WellnessAmberContainer
import com.example.healthcare.ui.theme.WellnessAmberContainerDark
import com.example.healthcare.ui.theme.WellnessAmberDark
import com.example.healthcare.ui.theme.WellnessCoral
import com.example.healthcare.ui.theme.WellnessCoralContainer
import com.example.healthcare.ui.theme.WellnessCoralContainerDark
import com.example.healthcare.ui.theme.WellnessCoralDark
import com.example.healthcare.ui.theme.WellnessOrange
import com.example.healthcare.ui.theme.WellnessOrangeContainer
import com.example.healthcare.ui.theme.WellnessOrangeContainerDark
import com.example.healthcare.ui.theme.WellnessOrangeDark
import com.example.healthcare.ui.theme.WellnessSpacing
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import com.example.healthcare.ui.viewmodel.DashboardViewModel
import com.example.healthcare.ui.viewmodel.TodayCoachUiState
import com.example.healthcare.ui.viewmodel.RecommendationPreviewUi
import com.example.healthcare.domain.DailyIntakeTimeline
import com.example.healthcare.domain.DailyIntakeStatus
import com.example.healthcare.domain.DailyIntakeSummary
import com.example.healthcare.domain.DashboardMealOrder
import com.example.healthcare.domain.DashboardMealSummary
import com.example.healthcare.domain.DashboardSummaryPolicy
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.ExerciseCoachCalculator
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.domain.StepCounterAction
import com.example.healthcare.domain.StepCounterStatus
import com.example.healthcare.domain.StepCounterUiState
import com.example.healthcare.domain.WalkingEnergyCalculator
import com.example.healthcare.ui.screens.QuickRecordAction
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel? = null,
    stepCounterRepository: StepCounterRepository? = null,
    bodyProfile: BodyProfile? = null,
    exerciseWeightKg: Double? = null,
    onOpenExerciseCoach: () -> Unit = {},
    onAddRecord: () -> Unit = {},
    onAddMealRecord: ((MealType) -> Unit)? = null,
    onQuickRecord: (QuickRecordAction) -> Unit = {},
    onOpenEnergySettings: () -> Unit = {},
    onOpenMealPlan: (MealType, Int) -> Unit = { _, _ -> },
    onOpenRecommendations: ((String?) -> Unit)? = null,
    onOpenHistory: () -> Unit = {},
    showActivityDetail: Boolean = false,
    onOpenActivityDetail: () -> Unit = {},
    onBackFromActivityDetail: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val stepCounterState by stepCounterRepository?.uiState?.collectAsState()
        ?: remember { mutableStateOf(StepCounterUiState(StepCounterStatus.PERMISSION_REQUIRED)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val canRequestAgain = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACTIVITY_RECOGNITION)
        } == true
        stepCounterRepository?.refreshAccess(granted, canRequestAgain)
    }

    DisposableEffect(stepCounterRepository, lifecycleOwner, activity) {
        fun refreshStepAccess() {
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            val canRequestAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.ACTIVITY_RECOGNITION
                )
            } == true
            stepCounterRepository?.refreshAccess(granted, canRequestAgain)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> refreshStepAccess()
                Lifecycle.Event.ON_PAUSE -> stepCounterRepository?.stopListening()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            refreshStepAccess()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stepCounterRepository?.stopListening()
        }
    }

    val onStepAction: () -> Unit = {
        when (stepCounterState.action) {
            StepCounterAction.REQUEST_PERMISSION -> {
                stepCounterRepository?.markPermissionRequested()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    stepCounterRepository?.refreshAccess(permissionGranted = true, canRequestAgain = true)
                }
            }
            StepCounterAction.OPEN_SETTINGS -> {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            StepCounterAction.NONE -> Unit
        }
        Unit
    }
    val selectedDate by viewModel?.selectedDate?.collectAsState() ?: remember { mutableStateOf(LocalDate.now()) }
    val meals by viewModel?.meals?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val totalCalories by viewModel?.totalCalories?.collectAsState() ?: remember { mutableIntStateOf(0) }
    val nutrition by viewModel?.dailyNutrition?.collectAsState()
        ?: remember { mutableStateOf(Macronutrients.Unknown) }
    val targetCalories by viewModel?.targetCalories?.collectAsState() ?: remember { mutableIntStateOf(2000) }
    val statusText by viewModel?.calorieStatusText?.collectAsState()
        ?: remember { mutableStateOf("목표까지 2,000kcal") }
    val energyState by viewModel?.energyUiState?.collectAsState()
        ?: remember { mutableStateOf(DashboardEnergyUiState()) }
    val coachState by viewModel?.todayCoachUiState?.collectAsState()
        ?: remember { mutableStateOf(TodayCoachUiState()) }
    val timeline by viewModel?.intakeTimeline?.collectAsState()
        ?: remember { mutableStateOf(DailyIntakeTimeline.Empty) }
    val situationMessage by viewModel?.situationMessage?.collectAsState()
        ?: remember { mutableStateOf("오늘 첫 식사를 기록해보세요.") }

    if (showActivityDetail) {
        ActivityDetailContent(
            state = stepCounterState,
            bodyProfile = bodyProfile,
            onStepAction = onStepAction,
            onOpenBodyProfile = onOpenEnergySettings,
            onBack = onBackFromActivityDetail
        )
        return
    }

    DashboardContent(
        selectedDate = selectedDate,
        meals = meals,
        totalCalories = totalCalories,
        nutrition = nutrition,
        targetCalories = targetCalories,
        statusText = statusText,
        energyState = energyState,
        coachState = coachState,
        situationMessage = situationMessage,
        stepCounterState = stepCounterState,
        onStepAction = onStepAction,
        onOpenActivityDetail = onOpenActivityDetail,
        onPreviousDay = { viewModel?.moveToPreviousDay() },
        onNextDay = { viewModel?.moveToNextDay() },
        onAddRecord = onAddRecord,
        onAddMealRecord = onAddMealRecord,
        onQuickRecord = onQuickRecord,
        onOpenEnergySettings = onOpenEnergySettings,
        onOpenMealPlan = onOpenMealPlan,
        onOpenRecommendations = onOpenRecommendations,
        timeline = timeline,
        onOpenHistory = onOpenHistory,
        exerciseWeightKg = exerciseWeightKg,
        bodyProfile = bodyProfile,
        onOpenExerciseCoach = onOpenExerciseCoach,
        onDateSelected = { viewModel?.selectDate(it) }
    )
}

@Composable
internal fun DashboardContent(
    selectedDate: LocalDate,
    meals: List<MealRecord>,
    totalCalories: Int,
    targetCalories: Int,
    statusText: String,
    energyState: DashboardEnergyUiState,
    coachState: TodayCoachUiState = TodayCoachUiState(),
    situationMessage: String = "오늘 첫 식사를 기록해보세요.",
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onAddRecord: () -> Unit,
    onOpenEnergySettings: () -> Unit,
    onOpenMealPlan: (MealType, Int) -> Unit = { _, _ -> },
    onOpenRecommendations: ((String?) -> Unit)? = null,
    onQuickRecord: (QuickRecordAction) -> Unit = {},
    timeline: DailyIntakeTimeline = DailyIntakeTimeline.Empty,
    onOpenHistory: () -> Unit = {},
    exerciseWeightKg: Double? = null,
    bodyProfile: BodyProfile? = null,
    onOpenExerciseCoach: () -> Unit = {},
    onDateSelected: (LocalDate) -> Unit = {},
    nutrition: Macronutrients = Macronutrients.Unknown,
    stepCounterState: StepCounterUiState = StepCounterUiState(StepCounterStatus.PERMISSION_REQUIRED),
    onStepAction: () -> Unit = {},
    onOpenActivityDetail: () -> Unit = {},
    onAddMealRecord: ((MealType) -> Unit)? = null
) {
    val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    val isToday = selectedDate == LocalDate.now()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val mealSummaries = remember(meals) { DashboardSummaryPolicy.mealSummaries(meals) }
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                val largeText = LocalDensity.current.fontScale >= 1.2f
                val extraLargeText = LocalDensity.current.fontScale >= 1.5f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(
                            when {
                                extraLargeText -> 116.dp
                                largeText -> 96.dp
                                else -> 92.dp
                            }
                        )
                        .padding(horizontal = 18.dp, vertical = if (extraLargeText) 4.dp else 7.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(top = if (extraLargeText) 0.dp else 7.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            situationMessage,
                            style = if (largeText) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Today,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                selectedDate.format(dateFormatter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(34.dp)
                            .testTag("dashboard-date-picker")
                    ) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = "날짜 선택",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("dashboard-root")
        ) {
            val dense = LocalDensity.current.fontScale >= 1.2f || maxHeight < 570.dp
            val gap = if (dense) 5.dp else 10.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    horizontal = 16.dp,
                    vertical = if (dense) 3.dp else 5.dp
                ),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                CompactActivityCard(
                    state = stepCounterState,
                    bodyProfile = bodyProfile,
                    dense = dense,
                    onClick = onOpenActivityDetail,
                    excessCalories = if (isToday) {
                        ExerciseCoachCalculator.targetExcess(totalCalories, targetCalories)
                    } else 0,
                    onOpenExerciseCoach = onOpenExerciseCoach,
                    modifier = Modifier.fillMaxWidth().height(if (dense) 92.dp else 106.dp)
                )
                CompactMealCard(
                    summaries = mealSummaries,
                    onClick = { mealType -> onAddMealRecord?.invoke(mealType) ?: onAddRecord() },
                    dense = dense,
                    modifier = Modifier.fillMaxWidth().height(if (dense) 140.dp else 154.dp)
                )
                CompactIntakeCard(
                    total = totalCalories,
                    target = targetCalories,
                    statusText = statusText,
                    nutrition = nutrition,
                    dense = dense,
                    onOpenReport = onOpenHistory,
                    modifier = Modifier.fillMaxWidth().height(if (dense) 152.dp else 178.dp)
                )
                CompactRecommendationCard(
                    state = coachState,
                    dense = dense,
                    onOpenRecommendation = { templateId ->
                        if (onOpenRecommendations != null) {
                            onOpenRecommendations(templateId)
                        } else {
                            val mealType = coachState.nextMealType
                            val budget = coachState.nextMealBudgetKcal
                            if (mealType != null && budget != null) onOpenMealPlan(mealType, budget)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(
                        if (LocalDensity.current.fontScale >= 1.5f) 148.dp else if (dense) 102.dp else 104.dp
                    )
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (showDatePicker) {
        DashboardDatePickerDialog(
            selectedDate = selectedDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                showDatePicker = false
                onDateSelected(date)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardDatePickerDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val todayUtcMillis = remember {
        LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = selectedDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = state.selectedDateMillis != null
            ) {
                Text("확인")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@Composable
private fun CompactIntakeCard(
    total: Int,
    target: Int,
    statusText: String,
    nutrition: Macronutrients,
    dense: Boolean,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val difference = target - total
    val progress = if (target > 0) (total.toFloat() / target).coerceIn(0f, 1f) else 0f
    Row(
        modifier = modifier
            .testTag("dashboard-calorie-target")
            .clickable(onClick = onOpenReport)
            .semantics {
                contentDescription = "오늘 식사 리포트 열기"
                stateDescription = "$statusText, 오늘 섭취 ${formatNumber(total)} kcal, 목표 ${formatNumber(target)} kcal"
                role = Role.Button
            }
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 15.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().weight(.48f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(if (dense) 112.dp else 132.dp),
                contentAlignment = Alignment.Center
            ) {
                val track = MaterialTheme.colorScheme.surfaceVariant
                val ring = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = if (dense) 6.dp.toPx() else 7.dp.toPx()
                    drawArc(track, -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(ring, -90f, progress * 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatNumber(total),
                        style = if (dense) {
                            MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp)
                        } else {
                            MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp, lineHeight = 32.sp)
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Text(
                        text = if (dense) "/${formatNumber(target)} kcal" else "/ ${formatNumber(target)} kcal",
                        style = if (dense) {
                            MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp)
                        } else {
                            MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 15.sp)
                        },
                        maxLines = 1
                    )
                }
            }
            Text(
                if (target <= 0) "목표 설정 필요" else if (difference >= 0) "남은 ${formatNumber(difference)} kcal" else "${formatNumber(-difference)} kcal 초과",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
        Column(
            modifier = Modifier.weight(.52f).fillMaxHeight().padding(vertical = if (dense) 5.dp else 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("오늘 리포트", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                }
                CompactMacro("탄수화물", nutrition.carbohydrateGrams, NutritionCarbohydrate)
                CompactMacro("단백질", nutrition.proteinGrams, NutritionProtein)
                CompactMacro("지방", nutrition.fatGrams, NutritionFat)
        }
    }
}

@Composable
private fun CompactMacro(label: String, grams: Double?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                grams?.takeIf { it.isFinite() && it >= 0.0 }?.roundToInt()?.let { "${it}g" } ?: "—",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        if (grams != null) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(color.copy(alpha = .72f)))
        }
    }
}

@Composable
private fun CompactMealCard(
    summaries: List<DashboardMealSummary>,
    onClick: (MealType) -> Unit,
    dense: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 3.dp)) {
        summaries.forEach { summary ->
            val type = summary.mealType
            val designImage = when (type) {
                MealType.BREAKFAST -> R.drawable.photo_breakfast_yogurt_bowl
                MealType.SNACK -> R.drawable.rec_kfind_snack_convenience_1
                MealType.LUNCH -> R.drawable.photo_sandwich
                MealType.DINNER -> R.drawable.photo_salmon_avocado_salad
                else -> R.drawable.photo_breakfast_yogurt_bowl
            }
            Card(
                onClick = { onClick(type) },
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .testTag("dashboard-meal-${type.name.lowercase()}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = .5.dp)
            ) {
                Row(
                    Modifier.fillMaxSize().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Image(
                        bitmap = ImageBitmap.imageResource(designImage),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxHeight().width(if (dense) 40.dp else 46.dp)
                    )
                    Text(type.displayName, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        summary.displayText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "${type.displayName} 기록",
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CompactActivityCard(
    state: StepCounterUiState,
    bodyProfile: BodyProfile?,
    dense: Boolean,
    onClick: () -> Unit,
    excessCalories: Int,
    onOpenExerciseCoach: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estimate = if (state.status == StepCounterStatus.AVAILABLE) {
        WalkingEnergyCalculator.estimate(state.todaySteps, bodyProfile)
    } else null
    Card(onClick = onClick, modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = .5.dp)) {
        Box(Modifier.fillMaxSize()) {
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.photo_walking_park),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary,
                            Color.Transparent
                        )
                    )
                )
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = if (dense) 11.dp else 14.dp, vertical = if (dense) 5.dp else 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (excessCalories > 0) "오늘 목표보다 약 ${formatNumber(excessCalories)} kcal 많아요"
                        else "오늘 활동",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = .9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                when (state.status) {
                    StepCounterStatus.AVAILABLE -> {
                        Text(
                            "${NumberFormat.getNumberInstance(Locale.KOREA).format(state.todaySteps)} 걸음",
                            style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            estimate?.let { "${formatWalkingDistance(it.estimatedDistanceKm)} · ${formatWalkingKcal(it.estimatedNetKcal)}" }
                                ?: "거리와 소모량 계산 준비",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = .9f),
                            maxLines = 1
                        )
                    }
                    StepCounterStatus.PERMISSION_REQUIRED, StepCounterStatus.PERMISSION_DENIED -> {
                        Text(
                            "활동 권한 필요",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("눌러서 설정", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .88f), maxLines = 1)
                    }
                    StepCounterStatus.SENSOR_UNAVAILABLE -> Text("센서 사용 불가", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                    StepCounterStatus.LOADING, StepCounterStatus.NO_BASELINE -> Text("걸음 확인 중", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                    StepCounterStatus.ERROR -> Text("걸음 확인 실패", style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                }
                }
                if (excessCalories > 0) {
                    Surface(
                        onClick = onOpenExerciseCoach,
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White.copy(alpha = .96f),
                        modifier = Modifier.testTag("exercise-coach-cta")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Image(
                                bitmap = ImageBitmap.imageResource(R.drawable.illustration_exercise_ready),
                                contentDescription = "가볍게 걷기를 준비하는 캐릭터",
                                modifier = Modifier.size(if (dense) 38.dp else 46.dp)
                                    .testTag("exercise-over-character")
                            )
                            Text(
                                "운동 추천\n보기",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2
                            )
                        }
                    }
                } else {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .96f), modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "활동 상세 열기",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactRecommendationCard(
    state: TodayCoachUiState,
    dense: Boolean,
    onOpenRecommendation: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.testTag("dashboard-recommendation-card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxSize().testTag("dashboard-open-daily-plan").clickable { onOpenRecommendation(null) }
            .semantics { role = Role.Button }.padding(if (dense) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.RestaurantMenu, contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("오늘의 추천", style = MaterialTheme.typography.labelSmall)
                Text(state.dailyThemeLabel?.let { "$it 식단" } ?: "오늘 식사 스타일을 골라보세요",
                    style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.dailyPlanKcal != null && state.dailyPlanTarget != null) Text(
                    "${formatNumber(state.dailyPlanKcal)} / ${formatNumber(state.dailyPlanTarget)} kcal",
                    style = MaterialTheme.typography.bodySmall)
                Text(if (state.dailyThemeLabel == null) "추천 고르기" else "오늘 식단 보기",
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("dashboard-more-recommendations"))
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "오늘의 추천 열기")
        }
    }
}

@Composable
private fun ActivityDetailContent(
    state: StepCounterUiState,
    bodyProfile: BodyProfile?,
    onStepAction: () -> Unit,
    onOpenBodyProfile: () -> Unit,
    onBack: () -> Unit
) {
    val estimate = if (state.status == StepCounterStatus.AVAILABLE) {
        WalkingEnergyCalculator.estimate(state.todaySteps, bodyProfile)
    } else null
    val updatedLabel = state.lastUpdatedAtMillis?.let { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WellnessTopAppBar(
                title = "오늘의 활동",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "홈으로 돌아가기")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(WellnessSpacing.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.status == StepCounterStatus.AVAILABLE && estimate != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatNumber(state.todaySteps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text("걸음", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Text("오늘 측정된 걸음", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WellnessCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        WalkingMetric("예상 거리", formatWalkingDistance(estimate.estimatedDistanceKm), Modifier.weight(1f))
                        WalkingMetric("걷기 예상 소모", formatWalkingKcal(estimate.estimatedNetKcal), Modifier.weight(1f))
                        WalkingMetric(
                            "한 걸음당",
                            if (state.todaySteps == 0L) "계산 준비" else "약 ${String.format(Locale.KOREA, "%.2f", estimate.estimatedKcalPerStep)} kcal",
                            Modifier.weight(1f)
                        )
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Text(
                        "이 수치는 입력한 키와 몸무게, 걸음 수를 기준으로 한 단순 추정치예요. 실제 거리와 소모 칼로리는 개인과 환경에 따라 다를 수 있어요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(13.dp)
                    )
                }
                Text("마지막 업데이트 ${updatedLabel ?: "확인 중"}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                StepCounterCard(
                    state = state,
                    onAction = onStepAction,
                    bodyProfile = bodyProfile,
                    onOpenBodyProfile = onOpenBodyProfile
                )
            }
        }
    }
}

@Composable
internal fun StepCounterCard(
    state: StepCounterUiState,
    onAction: () -> Unit,
    bodyProfile: BodyProfile? = null,
    onOpenBodyProfile: () -> Unit = {}
) {
    val walkingEstimate = if (state.status == StepCounterStatus.AVAILABLE) {
        WalkingEnergyCalculator.estimate(state.todaySteps, bodyProfile)
    } else null
    val stateDescriptionText = when (state.status) {
        StepCounterStatus.AVAILABLE -> "오늘 ${NumberFormat.getNumberInstance(Locale.KOREA).format(state.todaySteps)}걸음"
        StepCounterStatus.LOADING -> "걸음 수 확인 중"
        StepCounterStatus.PERMISSION_REQUIRED -> "활동 권한 필요"
        StepCounterStatus.PERMISSION_DENIED -> "활동 권한 꺼짐"
        StepCounterStatus.SENSOR_UNAVAILABLE -> "걸음 수 센서 사용 불가"
        StepCounterStatus.NO_BASELINE -> "걸음 수 측정 준비 중"
        StepCounterStatus.ERROR -> "걸음 수 불러오기 실패"
    }
    WellnessCard(
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = stateDescriptionText },
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Icon(
                        imageVector = if (state.status == StepCounterStatus.SENSOR_UNAVAILABLE) {
                            Icons.Rounded.SensorsOff
                        } else {
                            Icons.AutoMirrored.Rounded.DirectionsWalk
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("오늘 걸음 수", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("칼로리 목표와 별도로 보여드려요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            when (state.status) {
                StepCounterStatus.AVAILABLE -> {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            NumberFormat.getNumberInstance(Locale.KOREA).format(state.todaySteps),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text("걸음", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 5.dp))
                    }
                    if (walkingEstimate != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            WalkingMetric(
                                label = "예상 거리",
                                value = formatWalkingDistance(walkingEstimate.estimatedDistanceKm),
                                modifier = Modifier.weight(1f)
                            )
                            WalkingMetric(
                                label = "걷기 예상 소모",
                                value = formatWalkingKcal(walkingEstimate.estimatedNetKcal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            if (state.todaySteps == 0L) "걸음당 예상 · 걸음이 기록되면 계산해요."
                            else "걸음당 예상 · 약 ${String.format(Locale.KOREA, "%.2f", walkingEstimate.estimatedKcalPerStep)} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "키로 예상한 보폭과 몸무게를 이용한 단순 추정치예요. 실제 속도와 보행 환경에 따라 달라질 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "걸음 수는 측정 중이에요. 예상 소모 칼로리를 보려면 키와 몸무게를 입력해 주세요.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = onOpenBodyProfile, modifier = Modifier.fillMaxWidth()) {
                            Text("신체정보 입력")
                        }
                    }
                    state.lastUpdatedAtMillis?.let { updatedAt ->
                        val formatted = runCatching {
                            Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN))
                        }.getOrNull()
                        formatted?.let {
                            Text("마지막 업데이트 · $it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StepCounterStatus.LOADING -> {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text("걸음 수 확인 중", style = MaterialTheme.typography.bodyLarge)
                }
                StepCounterStatus.NO_BASELINE -> {
                    Text("지금부터 측정 시작", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(state.message ?: "첫 센서 값을 기다리고 있어요.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                StepCounterStatus.PERMISSION_REQUIRED -> {
                    Text("걸음 수를 확인하려면 활동 권한이 필요해요.",
                        style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                        Text("걸음 수 사용하기")
                    }
                }
                StepCounterStatus.PERMISSION_DENIED -> {
                    Text("걸음 수 권한이 꺼져 있어요. 다른 기능은 그대로 사용할 수 있어요.",
                        style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.action == StepCounterAction.OPEN_SETTINGS) "권한 설정" else "다시 요청")
                    }
                }
                StepCounterStatus.SENSOR_UNAVAILABLE -> {
                    Text("이 기기에서는 걸음 수 센서를 사용할 수 없어요.",
                        style = MaterialTheme.typography.bodyLarge)
                    Text("음식 기록과 다른 건강 기능은 계속 사용할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StepCounterStatus.ERROR -> {
                    Text("걸음 수를 불러오지 못했어요.", style = MaterialTheme.typography.bodyLarge)
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkingMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatWalkingDistance(distanceKm: Double): String = when {
    distanceKm <= 0.0 -> "0 km"
    distanceKm < 0.1 -> "0.1 km 미만"
    else -> "약 ${String.format(Locale.KOREA, "%.1f", distanceKm)} km"
}

private fun formatWalkingKcal(kcal: Double): String = when {
    kcal <= 0.0 -> "0 kcal"
    kcal < 1.0 -> "1 kcal 미만"
    else -> "약 ${kcal.roundToInt()} kcal"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun RecentDateStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val startDate = if (selectedDate in today.minusDays(4)..today) {
        today.minusDays(4)
    } else {
        selectedDate.minusDays(2)
    }
    val dates = remember(startDate) { List(5) { startDate.plusDays(it.toLong()) } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        dates.forEach { date ->
            val selected = date == selectedDate
            val isToday = date == today
            Surface(
                onClick = { onDateSelected(date) },
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) HomePurple else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) PaperSurface else MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        if (isToday) "오늘" else date.format(DateTimeFormatter.ofPattern("E", Locale.KOREAN)),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleMealRecommendationCard(
    state: TodayCoachUiState,
    onOpenRecommendations: () -> Unit
) {
    val mealType = state.nextMealType
    val budget = state.nextMealBudgetKcal
    val hasRecommendation = mealType != null && budget != null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("오늘 뭐 먹지?")
        WellnessCard(
            Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    Icon(
                        Icons.Rounded.RestaurantMenu,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(26.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        when {
                            hasRecommendation -> "${mealType!!.displayName} 추천"
                            state.targetExceeded -> "다음 식사는 가볍게"
                            else -> "다음 한 끼를 준비해요"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        when {
                            hasRecommendation -> "약 ${formatNumber(budget!!)} kcal"
                            state.targetExceeded -> "목표를 넘은 날도 식사를 거르지 말고 부담이 적은 메뉴를 살펴보세요."
                            else -> "식사를 기록하면 남은 양에 맞춰 추천해 드려요."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (hasRecommendation) {
                    TextButton(onClick = onOpenRecommendations) {
                        Text("식단 보기")
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun MealTypeSummarySection(
    meals: List<MealRecord>,
    isToday: Boolean,
    onAddRecord: () -> Unit
) {
    val primaryTypes = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
    val types = if (meals.any { it.mealType == MealType.OTHER }) primaryTypes + MealType.OTHER else primaryTypes
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = if (isToday) "오늘 먹은 음식" else "이날 먹은 음식",
            supportingText = if (meals.isEmpty()) "아직 기록이 없어요" else "식사별 섭취량을 한눈에 볼 수 있어요"
        )
        WellnessCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, mealType ->
                    val matchingMeals = meals.filter { it.mealType == mealType }
                    val calories = matchingMeals.sumOf { it.calories.toLong() }
                        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            mealType.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (matchingMeals.isEmpty()) "아직 기록 없음" else "${formatNumber(calories)} kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onAddRecord) {
                            Icon(Icons.Rounded.Add, contentDescription = "${mealType.displayName} 기록 추가")
                        }
                    }
                    if (index != types.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDailyStatusCard(summary: DailyIntakeSummary, isToday: Boolean) {
    val title = when (summary.status) {
        DailyIntakeStatus.OVER -> "목표보다 많은 날"
        DailyIntakeStatus.BALANCED -> "목표에 가까운 날"
        DailyIntakeStatus.UNDER -> "여유가 있는 날"
        DailyIntakeStatus.BELOW_BMR -> "섭취량을 확인해 보세요"
        DailyIntakeStatus.NO_RECORD -> "아직 기록이 없어요"
        DailyIntakeStatus.NO_TARGET -> "목표 설정이 필요해요"
    }
    WellnessCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (isToday) "오늘의 상태" else "이날의 상태",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            when (summary.status) {
                DailyIntakeStatus.OVER -> OverCharacterIllustration(Modifier.size(58.dp))
                DailyIntakeStatus.BALANCED -> BalancedCharacterIllustration(Modifier.size(58.dp))
                DailyIntakeStatus.UNDER -> SlimCharacterIllustration(Modifier.size(58.dp))
                DailyIntakeStatus.BELOW_BMR -> WarningCharacterIllustration(Modifier.size(58.dp))
                DailyIntakeStatus.NO_RECORD, DailyIntakeStatus.NO_TARGET -> Icon(
                    Icons.Rounded.RestaurantMenu,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompactExerciseCard(
    excessCalories: Int,
    weightKg: Double?,
    onOpenDetails: () -> Unit
) {
    WellnessCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("오늘의 움직임", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "목표보다 ${formatNumber(excessCalories)} kcal 많아요. 운동은 선택사항이에요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            TextButton(onClick = onOpenDetails) {
                Text(if (weightKg == null) "설정" else "운동 보기")
            }
        }
    }
}

@Composable
private fun DashboardDetailLinks(
    onOpenHistory: () -> Unit,
    onOpenEnergySettings: () -> Unit,
    energyConfigured: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
            Text("기록·통계 보기")
        }
        TextButton(onClick = onOpenEnergySettings, modifier = Modifier.weight(1f)) {
            Text(if (energyConfigured) "에너지 기준 보기" else "에너지 기준 설정")
        }
    }
}

@Composable
private fun CoachNote(state: TodayCoachUiState) {
    WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary)
            Text(
                when {
                    state.targetExceeded -> "목표를 넘은 날도 괜찮아요. 다음 한 끼는 부담이 적은 선택지부터 살펴보세요."
                    state.nextMealType != null && state.nextMealBudgetKcal != null ->
                        "다음 ${state.nextMealType.displayName}은 약 ${formatNumber(state.nextMealBudgetKcal)} kcal를 참고해 골라보세요."
                    else -> "오늘의 식사를 기록하면 다음 한 끼를 살펴보기 쉬워져요."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun TodayCoachCard(
    state: TodayCoachUiState,
    onOpenRecommendations: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("다음 한 끼", supportingText = "지금의 식사 흐름에서 고를 수 있어요.")
        WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(WellnessSpacing.CardContent),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("오늘 코치", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.semantics { heading() })
                        Text(
                            state.nextMealType?.let { "${it.displayName}을(를) 준비할까요?" }
                                ?: "오늘의 식사 흐름을 살펴봐요",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            state.nextMealBudgetKcal?.let { "다음 식사 제안 · 약 ${formatNumber(it)} kcal" }
                                ?: "다음 식사 권장량을 확인해 보세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Icon(Icons.Rounded.RestaurantMenu, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                Column(Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.targetExceeded) {
                        Text(
                            "오늘 목표는 이미 넘었어요. 다음 식사를 거르기보다 부담이 적은 식사를 선택하고, 내일부터 다시 조정해 보세요.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text("현재 남은 양 ${formatNumber(state.remainingCalories)} kcal · 음식과 양은 기록 전에 바꿀 수 있어요.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.nextMealType != null && state.nextMealBudgetKcal != null) {
                        Button(onClick = onOpenRecommendations, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                            Text("추천 보기")
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                    Text("식사 배분은 생활 패턴을 바탕으로 한 참고 정보이며 의료 처방이 아닙니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun CalorieSummaryCard(
    total: Int,
    target: Int,
    statusText: String,
    recordCount: Int = 0,
    title: String = "오늘 섭취",
    nutrition: Macronutrients = Macronutrients.Unknown
) {
    val progress = if (target > 0) (total.toFloat() / target).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(progress, animationSpec = tween(250), label = "오늘 섭취 진행")
    val dark = MaterialTheme.colorScheme.background == NightBackground
    val overTarget = target > 0 && total > target
    val difference = if (target > 0) target - total else 0
    val heroColor = when {
        dark -> HomeLavenderDark
        overTarget -> WellnessCoralContainer
        else -> HomeLavender
    }
    val heroText = if (dark) NightText else MaterialTheme.colorScheme.onSurface
    val accent = when {
        dark -> NightText
        overTarget -> WellnessCoral
        else -> HomePurple
    }
    Card(
        modifier = Modifier.fillMaxWidth().semantics {
            stateDescription = "$statusText, 섭취 ${formatNumber(total)} kcal, 목표 ${formatNumber(target)} kcal, 기록 ${recordCount}건"
        },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = heroColor)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.TrackChanges, contentDescription = null, tint = accent)
                Text(title, style = MaterialTheme.typography.titleMedium, color = heroText)
            }
            if (target > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        if (overTarget) "+${formatNumber(difference.absoluteValue)}" else formatNumber(difference),
                        style = MaterialTheme.typography.displaySmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (overTarget) "kcal 초과" else "kcal 남음",
                        style = MaterialTheme.typography.titleMedium,
                        color = heroText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text("목표를 설정해 주세요", style = MaterialTheme.typography.headlineSmall, color = heroText)
            }
            if (target > 0) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (total > 0) accent else heroText.copy(alpha = 0.18f),
                    trackColor = heroText.copy(alpha = 0.18f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalorieCardMetric("섭취", total, Modifier.weight(1f), heroText)
                CalorieCardMetric("목표", target, Modifier.weight(1f), heroText, missingWhenZero = true)
            }
            HorizontalDivider(color = heroText.copy(alpha = 0.16f))
            MacroSummaryRow(nutrition)
        }
    }
}

@Composable
private fun CalorieCardMetric(
    label: String,
    value: Int,
    modifier: Modifier,
    color: androidx.compose.ui.graphics.Color,
    missingWhenZero: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.72f))
        Text(
            if (!missingWhenZero || value > 0) "${formatNumber(value)} kcal" else "설정 필요",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SmartRecordSection(onAction: (QuickRecordAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("편하게 기록하기", supportingText = "무게를 몰라도 좋아요. 익숙한 방법으로 시작하세요.")
        SmartActionTile(
            action = QuickRecordAction.PHOTO,
            title = "사진으로 기록",
            supporting = "사진을 참고해 음식과 먹은 양을 직접 골라요.",
            modifier = Modifier.fillMaxWidth(),
            featured = true,
            onAction = onAction
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmartActionTile(
                action = QuickRecordAction.SEARCH,
                title = "음식 검색",
                supporting = "이름으로 찾아요.",
                modifier = Modifier.weight(1f),
                onAction = onAction
            )
            SmartActionTile(
                action = QuickRecordAction.BARCODE,
                title = "바코드",
                supporting = "포장식품을 확인해요.",
                modifier = Modifier.weight(1f),
                onAction = onAction
            )
        }
    }
}

@Composable
private fun SmartActionTile(
    action: QuickRecordAction,
    title: String,
    supporting: String,
    modifier: Modifier,
    featured: Boolean = false,
    onAction: (QuickRecordAction) -> Unit
) {
    val color = when (action) {
        QuickRecordAction.PHOTO -> MaterialTheme.colorScheme.tertiaryContainer
        QuickRecordAction.SEARCH -> MaterialTheme.colorScheme.secondaryContainer
        QuickRecordAction.BARCODE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val icon = when (action) {
        QuickRecordAction.PHOTO -> Icons.Rounded.CameraAlt
        QuickRecordAction.SEARCH -> Icons.Rounded.RestaurantMenu
        QuickRecordAction.BARCODE -> Icons.Rounded.QrCodeScanner
    }
    Card(
        onClick = { onAction(action) },
        modifier = modifier.heightIn(min = if (featured) 116.dp else 136.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        if (featured) {
            Row(Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.tertiary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(supporting, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(supporting, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EnergySetupCard(onOpenEnergySettings: () -> Unit) {
    WellnessCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.MonitorHeart, contentDescription = null)
                Text(
                    "기초대사량을 설정해 보세요",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Text(
                "기초대사량을 설정하면 활동량을 반영한 유지 칼로리와 초과 섭취 시나리오를 확인할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Button(onClick = onOpenEnergySettings, modifier = Modifier.fillMaxWidth()) {
                Text("기초대사량 설정")
            }
        }
    }
}

@Composable
private fun EnergyBasisCard(state: DashboardEnergyUiState, onOpenSettings: () -> Unit) {
    val profile = requireNotNull(state.profile)
    val maintenance = requireNotNull(state.maintenanceCalories)
    WellnessCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "에너지 기준",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("수정")
                }
            }
            EnergyMetricRow(
                firstLabel = "기초대사량",
                firstValue = "${formatNumber(profile.basalMetabolicRateKcal)} kcal",
                secondLabel = "예상 유지 칼로리",
                secondValue = "${formatNumber(maintenance)} kcal"
            )
            EnergyMetricRow(
                firstLabel = "현재 일일 목표",
                firstValue = "${formatNumber(state.effectiveTargetCalories)} kcal",
                secondLabel = "오늘 섭취",
                secondValue = "${formatNumber(state.intakeCalories)} kcal"
            )
            Text(
                "목표 기준 · ${targetModeLabel(state.targetMode)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val remaining = (maintenance - state.intakeCalories).coerceAtLeast(0)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (state.dailySurplusCalories == 0) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.TrendingUp,
                    contentDescription = null,
                    tint = if (state.dailySurplusCalories == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                Text(
                    if (state.dailySurplusCalories == 0) "유지 기준 이내 · ${formatNumber(remaining)} kcal 남음"
                    else "오늘 초과 ${formatNumber(state.dailySurplusCalories)} kcal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EnergyMetricRow(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EnergyMetric(firstLabel, firstValue, Modifier.weight(1f))
        EnergyMetric(secondLabel, secondValue, Modifier.weight(1f))
    }
}

@Composable
private fun EnergyMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WeightProjectionCard(state: DashboardEnergyUiState, onOpenSettings: () -> Unit) {
    val thirtyDay = requireNotNull(state.thirtyDayEquivalentKg)
    val isDark = MaterialTheme.colorScheme.background == NightBackground
    val statusLabel = when {
        thirtyDay < 0.5 -> "소폭 초과"
        thirtyDay < 1.0 -> "초과가 이어지는 중"
        else -> "지속 시 체중 증가 가능"
    }
    val accentColor = when {
        thirtyDay < 0.5 -> if (isDark) WellnessAmberDark else WellnessAmber
        thirtyDay < 1.0 -> if (isDark) WellnessOrangeDark else WellnessOrange
        else -> if (isDark) WellnessCoralDark else WellnessCoral
    }
    val containerColor = when {
        thirtyDay < 0.5 -> if (isDark) WellnessAmberContainerDark else WellnessAmberContainer
        thirtyDay < 1.0 -> if (isDark) WellnessOrangeContainerDark else WellnessOrangeContainer
        else -> if (isDark) WellnessCoralContainerDark else WellnessCoralContainer
    }
    WellnessCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = accentColor)
                Column {
                    Text(
                        "오늘과 같은 초과가 매일 이어질 경우",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(statusLabel, style = MaterialTheme.typography.labelLarge, color = accentColor)
                }
            }
            Text(
                "오늘 유지 기준 초과 ${formatNumber(state.dailySurplusCalories)} kcal",
                style = MaterialTheme.typography.bodyMedium
            )
            EnergyMetricRow(
                firstLabel = "7일 단순 환산",
                firstValue = formatWeightEquivalent(state.sevenDayEquivalentKg ?: 0.0),
                secondLabel = "30일 단순 환산",
                secondValue = formatWeightEquivalent(thirtyDay)
            )
            Text(
                "단순 에너지 환산치입니다. 실제 체중 변화는 활동량, 수분, 신체 상태와 식사 기록에 따라 달라질 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenSettings) { Text("에너지 기준 수정") }
        }
    }
}

@Composable
private fun QuickRecordCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = HomePurple, contentColor = PaperSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = PaperSurface.copy(alpha = 0.2f)) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = PaperSurface)
                }
            }
            Text("음식 기록하기", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = "음식 기록 열기")
        }
    }
}

@Composable
fun MealItemCard(meal: MealRecord) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(Icons.Rounded.RestaurantMenu, contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
            Text(meal.time, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(meal.mealType.displayName, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary)
            Text(meal.foodName, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            meal.portionDisplayLabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            meal.memo?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatNumber(meal.calories), style = MaterialTheme.typography.titleMedium)
            Text("kcal", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun targetModeLabel(mode: TargetMode): String = when (mode) {
    TargetMode.MANUAL -> "직접 설정한 목표"
    TargetMode.BMR -> "기초대사량 기준"
    TargetMode.MAINTENANCE -> "활동량 포함 유지 기준"
}

private fun formatWeightEquivalent(value: Double): String = when {
    value > 0.0 && value < 0.05 -> "약 0.1 kg 미만"
    else -> String.format(Locale.KOREA, "약 +%.1f kg", value)
}

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(value)

private val previewMeals = listOf(
    MealRecord(date = "2026-09-13", time = "08:10", mealType = MealType.BREAKFAST, foodName = "그릭 요거트와 블루베리", calories = 320),
    MealRecord(date = "2026-09-13", time = "12:30", mealType = MealType.LUNCH, foodName = "현미밥과 닭가슴살 샐러드", calories = 610),
    MealRecord(date = "2026-09-13", time = "19:00", mealType = MealType.DINNER, foodName = "저녁 식사", calories = 1695)
)

private val previewTimeline = DailyIntakeTimeline.build(
    previewMeals,
    listOf(CalorieGoal(targetCalories = 2000, startDate = "2026-09-01")),
    listOf(EnergyProfileHistory(
        basalMetabolicRateKcal = 1500,
        activityLevelCode = ActivityLevel.LIGHT,
        palMultiplier = 1.55,
        targetMode = TargetMode.MAINTENANCE,
        effectiveFromDate = "2026-09-01"
    ))
)

private val previewEnergyState = DashboardEnergyUiState(
    profile = EnergyProfileHistory(
        basalMetabolicRateKcal = 1500,
        activityLevelCode = ActivityLevel.LIGHT,
        palMultiplier = 1.55,
        targetMode = TargetMode.MAINTENANCE,
        effectiveFromDate = "2026-09-13"
    ),
    manualTargetCalories = 2000,
    effectiveTargetCalories = 2325,
    intakeCalories = 2625,
    maintenanceCalories = 2325,
    dailySurplusCalories = 300,
    sevenDayEquivalentKg = 0.2727,
    thirtyDayEquivalentKg = 1.1688
)

@Preview(name = "대시보드 360 · 큰 글자", widthDp = 360, heightDp = 800, showBackground = true, fontScale = 1.3f)
@Composable
private fun Dashboard360Preview() {
    HealthCareTheme(darkTheme = false) {
        DashboardContent(
            LocalDate.of(2026, 9, 13), previewMeals, 2625, 2325, "목표 대비 +300kcal", previewEnergyState,
            TodayCoachUiState(2325, 2625, 0, listOf(MealType.DINNER), MealType.DINNER, null, true),
            "오늘 기록은 목표보다 약 300 kcal 높아요. 기록 내용을 확인해보세요.",
            {}, {}, {}, {}, { _, _ -> }, timeline = previewTimeline
        )
    }
}

@Preview(name = "대시보드 BMR 미설정", widthDp = 390, heightDp = 820, showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    HealthCareTheme(darkTheme = false) {
        DashboardContent(
            LocalDate.now(), emptyList(), 0, 2000, "목표까지 2,000kcal", DashboardEnergyUiState(),
            TodayCoachUiState(2000, 0, 2000, listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER), MealType.BREAKFAST, 500),
            "좋은 아침이에요. 아침 식사를 기록해볼까요?",
            {}, {}, {}, {}, { _, _ -> }, timeline = DailyIntakeTimeline.Empty
        )
    }
}

@Preview(name = "대시보드 다크", widthDp = 412, heightDp = 860, showBackground = true)
@Composable
private fun DashboardDarkPreview() {
    HealthCareTheme(darkTheme = true) {
        DashboardContent(
            LocalDate.of(2026, 9, 13), previewMeals, 2625, 2325, "목표 대비 +300kcal", previewEnergyState,
            TodayCoachUiState(2325, 2625, 0, listOf(MealType.DINNER), MealType.DINNER, null, true),
            "오늘도 식사 기록을 이어가고 있어요.",
            {}, {}, {}, {}, { _, _ -> }, timeline = previewTimeline
        )
    }
}
