package com.example.healthcare.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.R
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.PortionPreset
import com.example.healthcare.domain.DailyIntakeTimeline
import com.example.healthcare.ui.components.DailyIntakeChart
import com.example.healthcare.ui.components.MonthIntakeCalendar
import com.example.healthcare.ui.components.DateNavigator
import com.example.healthcare.ui.components.MetricValue
import com.example.healthcare.ui.components.SectionHeader
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.WellnessEmptyState
import com.example.healthcare.ui.components.WellnessTopAppBar
import com.example.healthcare.ui.components.MacroSummaryRow
import com.example.healthcare.ui.components.RecommendationPhoto
import com.example.healthcare.ui.theme.NutritionCarbohydrate
import com.example.healthcare.ui.theme.NutritionProtein
import com.example.healthcare.ui.theme.NutritionFat
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.data.model.MealNutritionRow
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.theme.WellnessSpacing
import com.example.healthcare.ui.viewmodel.HistoryViewModel
import com.example.healthcare.ui.viewmodel.MealEditUiState
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel? = null) {
    // The adaptive navigator saves its content key in the activity state. Keep only the
    // stable Room ID there; a MealRecord is not Bundle-saveable and crashes on teardown.
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val scope = rememberCoroutineScope()
    val selectedDate by viewModel?.selectedDate?.collectAsState() ?: remember { mutableStateOf(LocalDate.now()) }
    val meals by viewModel?.dailyMeals?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val totalCalories by viewModel?.dailyTotalCalories?.collectAsState() ?: remember { mutableIntStateOf(0) }
    val nutrition by viewModel?.dailyNutrition?.collectAsState()
        ?: remember { mutableStateOf(Macronutrients.Unknown) }
    val nutritionRows by viewModel?.dailyNutritionRows?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) }
    val targetCalories by viewModel?.dailyTargetCalories?.collectAsState() ?: remember { mutableIntStateOf(2000) }
    val statusText by viewModel?.dailyCalorieStatus?.collectAsState() ?: remember { mutableStateOf("") }
    val editState by viewModel?.editState?.collectAsState() ?: remember { mutableStateOf(MealEditUiState()) }
    val timeline by viewModel?.intakeTimeline?.collectAsState() ?: remember { mutableStateOf(DailyIntakeTimeline.Empty) }
    var selectedHubSection by rememberSaveable { mutableStateOf<String?>(null) }

    if (selectedHubSection == null) {
        HistoryHubContent(
            selectedDate = selectedDate,
            timeline = timeline,
            nutrition = nutrition,
            onSelected = { selectedHubSection = it.name }
        )
        return
    }
    val hubSection = runCatching { HistoryHubSection.valueOf(selectedHubSection!!) }
        .getOrDefault(HistoryHubSection.SEVEN_DAYS)
    BackHandler { selectedHubSection = null }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            HistoryListPane(
                selectedDate = selectedDate,
                meals = meals,
                totalCalories = totalCalories,
                nutrition = nutrition,
                targetCalories = targetCalories,
                statusText = statusText,
                onDateSelected = { viewModel?.onDateSelected(it) },
                timeline = timeline,
                initialChartDays = if (hubSection == HistoryHubSection.THIRTY_DAYS) 30 else 7,
                screenTitle = hubSection.title,
                onBackToHub = { selectedHubSection = null },
                onItemClick = { meal ->
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, meal.id) }
                }
            )
        },
        detailPane = {
            val selectedMeal = navigator.currentDestination?.contentKey?.let { id ->
                meals.firstOrNull { it.id == id }
            }
            if (selectedMeal == null) {
                EmptyDetailPane()
            } else if (editState.original?.id == selectedMeal.id) {
                HistoryEditPane(
                    state = editState,
                    onFoodNameChange = { viewModel?.onEditFoodNameChange(it) },
                    onCaloriesChange = { viewModel?.onEditCaloriesChange(it) },
                    onMealTypeChange = { viewModel?.onEditMealTypeChange(it) },
                    onDateChange = { viewModel?.onEditDateChange(it) },
                    onTimeChange = { viewModel?.onEditTimeChange(it) },
                    onServingAmountChange = { viewModel?.onEditServingAmountChange(it) },
                    onServingUnitChange = { viewModel?.onEditServingUnitChange(it) },
                    onPortionSelected = { viewModel?.selectEditPortion(it) },
                    onPortionRatio = { ratio, label -> viewModel?.selectEditRatio(ratio, label) },
                    onPreciseEdit = { viewModel?.showPreciseEdit() },
                    onMemoChange = { viewModel?.onEditMemoChange(it) },
                    onSave = {
                        viewModel?.saveMealEdit {
                            scope.launch { navigator.navigateBack() }
                        }
                    },
                    onBack = { viewModel?.cancelEditing() }
                )
            } else {
                HistoryDetailPane(
                    meal = selectedMeal,
                    nutrition = nutritionRows.firstOrNull { it.mealId == selectedMeal.id }?.asMacronutrients()
                        ?: Macronutrients.Unknown,
                    onEdit = { viewModel?.startEditing(selectedMeal) },
                    onDelete = {
                        viewModel?.deleteMeal(selectedMeal)
                        viewModel?.cancelEditing()
                        scope.launch { navigator.navigateBack() }
                    },
                    onBack = { scope.launch { navigator.navigateBack() } }
                )
            }
        }
    )
}

private enum class HistoryHubSection(val title: String, val description: String) {
    SEVEN_DAYS("최근 7일", "일주일 섭취 흐름"),
    THIRTY_DAYS("최근 30일", "한 달 섭취 흐름"),
    INTAKE("섭취 통계", "날짜별 kcal와 탄단지"),
    MEALS("식사 기록", "저장한 식사와 상세 기록")
}

@Composable
private fun HistoryHubContent(
    selectedDate: LocalDate,
    timeline: DailyIntakeTimeline,
    nutrition: Macronutrients,
    onSelected: (HistoryHubSection) -> Unit
) {
    var chartDays by rememberSaveable { mutableIntStateOf(7) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(WellnessSpacing.ScreenHorizontal, 13.dp, WellnessSpacing.ScreenHorizontal, WellnessSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("내 건강 기록", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    Text("한눈에 확인하세요", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7일", 30 to "30일").forEach { (days, label) ->
                        FilterChip(
                            selected = chartDays == days,
                            onClick = { chartDays = days },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { onSelected(HistoryHubSection.MEALS) },
                        label = { Text("식사 기록") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                WellnessCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("일일 섭취 칼로리", style = MaterialTheme.typography.titleMedium)
                        DailyIntakeChart(timeline.recentDays(selectedDate, chartDays), compact = true)
                    }
                }
            }
            item {
                WellnessCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("영양소 비율", style = MaterialTheme.typography.titleMedium)
                        NutritionDonut(nutrition)
                    }
                }
            }
            item {
                WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("체중 변화", style = MaterialTheme.typography.titleMedium)
                        Text("현재 앱은 날짜별 체중 이력을 저장하지 않아 임의의 그래프를 만들지 않아요. 체중 목표는 설정에서 관리할 수 있어요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionDonut(nutrition: Macronutrients) {
    val carbs = (nutrition.carbohydrateGrams ?: 0.0).coerceAtLeast(0.0) * 4.0
    val protein = (nutrition.proteinGrams ?: 0.0).coerceAtLeast(0.0) * 4.0
    val fat = (nutrition.fatGrams ?: 0.0).coerceAtLeast(0.0) * 9.0
    val total = carbs + protein + fat
    if (total <= 0.0) {
        Text("영양소가 포함된 기록이 생기면 비율을 보여드려요.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Canvas(Modifier.size(92.dp)) {
            var start = -90f
            listOf(carbs to NutritionCarbohydrate, protein to NutritionProtein, fat to NutritionFat).forEach { (value, color) ->
                val sweep = (value / total * 360.0).toFloat()
                drawArc(color, start, sweep, false, style = Stroke(14.dp.toPx(), cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("탄수화물", nutrition.carbohydrateGrams, NutritionCarbohydrate),
                Triple("단백질", nutrition.proteinGrams, NutritionProtein),
                Triple("지방", nutrition.fatGrams, NutritionFat)
            ).forEach { (label, grams, color) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
                    Text(grams?.let { "${it.roundToInt()}g" } ?: "—", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun HistoryListPane(
    selectedDate: LocalDate,
    meals: List<MealRecord>,
    totalCalories: Int,
    targetCalories: Int,
    statusText: String,
    onDateSelected: (LocalDate) -> Unit,
    onItemClick: (MealRecord) -> Unit,
    timeline: DailyIntakeTimeline = DailyIntakeTimeline.Empty,
    nutrition: Macronutrients = Macronutrients.Unknown,
    initialChartDays: Int = 7,
    screenTitle: String = "기록과 통계",
    onBackToHub: (() -> Unit)? = null
) {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일")
    val mealsOnly = screenTitle == HistoryHubSection.MEALS.title
    var chartDays by rememberSaveable(initialChartDays) { mutableIntStateOf(initialChartDays) }
    var groupByMeal by rememberSaveable { mutableStateOf(true) }
    var displayedMonth by rememberSaveable { mutableStateOf(YearMonth.from(selectedDate).toString()) }
    val month = runCatching { YearMonth.parse(displayedMonth) }.getOrDefault(YearMonth.from(selectedDate))
    val selectDate: (LocalDate) -> Unit = { date ->
        displayedMonth = YearMonth.from(date).toString()
        onDateSelected(date)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (onBackToHub != null) {
                WellnessTopAppBar(
                    screenTitle,
                    navigationIcon = {
                        IconButton(onClick = onBackToHub) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "통계 메뉴로 돌아가기")
                        }
                    }
                )
            } else {
                WellnessTopAppBar(screenTitle)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = WellnessSpacing.ScreenHorizontal,
                end = WellnessSpacing.ScreenHorizontal,
                top = 16.dp,
                bottom = WellnessSpacing.Section
            ),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(vertical = 3.dp)
                        .testTag("history-today-header"),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(if (mealsOnly) "오늘의 기록" else "나의 식사 일지",
                        style = MaterialTheme.typography.headlineLarge.copy(lineHeight = 42.sp),
                        modifier = Modifier.semantics { heading() })
                    Text(if (mealsOnly) "식사별 기록과 섭취량을 확인하세요." else "날짜를 움직이며 식사 흐름을 살펴보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                DateNavigator(
                    label = selectedDate.format(dateFormatter),
                    onPrevious = { selectDate(selectedDate.minusDays(1)) },
                    onNext = { selectDate(selectedDate.plusDays(1)) },
                    previousIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                    nextIcon = Icons.AutoMirrored.Rounded.ArrowForward
                )
            }
            if (mealsOnly) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("오늘 합계", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${NumberFormat.getNumberInstance(Locale.KOREA).format(totalCalories)} kcal",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            if (!mealsOnly) {
                item {
                    WellnessCard {
                        Box(Modifier.padding(WellnessSpacing.CardContent)) {
                        MonthIntakeCalendar(
                            month = month,
                            selectedDate = selectedDate,
                            timeline = timeline,
                            onDateSelected = selectDate,
                            onMonthChange = { offset ->
                                displayedMonth = month.plusMonths(offset.toLong()).toString()
                            }
                        )
                    }
                }
                }
                item {
                    DailySummaryCard(totalCalories, targetCalories, statusText, meals.size, nutrition)
                }
                item {
                    WellnessCard {
                        Column(Modifier.padding(WellnessSpacing.CardContent),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader(stringResource(R.string.timeline_history_title),
                                supportingText = stringResource(R.string.timeline_empty_policy))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = chartDays == 7, onClick = { chartDays = 7 },
                                    label = { Text(stringResource(R.string.timeline_recent_7_days)) })
                                FilterChip(selected = chartDays == 30, onClick = { chartDays = 30 },
                                    label = { Text(stringResource(R.string.timeline_recent_30_days)) })
                            }
                            DailyIntakeChart(timeline.recentDays(selectedDate, chartDays))
                        }
                    }
                }
            }
            if (!mealsOnly) item {
                SectionHeader(
                    title = "이날의 식사",
                    supportingText = if (meals.isEmpty()) null else "시간순으로 저장된 식사 기록이에요.",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = groupByMeal, onClick = { groupByMeal = true },
                        label = { Text("식사별") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = !groupByMeal, onClick = { groupByMeal = false },
                        label = { Text("전체") }, modifier = Modifier.weight(1f))
                }
            }
            if (meals.isEmpty()) {
                item {
                    WellnessEmptyState(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = "이날은 기록이 없어요",
                        message = "다른 날짜를 보거나 오늘의 식사를 기록해 보세요."
                    )
                }
            } else {
                if (groupByMeal) {
                    MealType.entries.forEach { type ->
                        val group = meals.filter { it.mealType == type }.sortedBy { it.time }
                        if (group.isNotEmpty()) {
                            item {
                                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(type.displayName, style = MaterialTheme.typography.titleLarge)
                                    Text("${NumberFormat.getNumberInstance(Locale.KOREA).format(group.sumOf { it.calories })} kcal", style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            items(group, key = { it.id }) { meal ->
                                HistoryItemCard(meal = meal, onClick = { onItemClick(meal) })
                            }
                        }
                    }
                } else {
                    items(meals.sortedBy { it.time }, key = { it.id }) { meal ->
                        HistoryItemCard(meal = meal, onClick = { onItemClick(meal) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DailySummaryCard(
    totalCalories: Int,
    targetCalories: Int,
    statusText: String,
    recordCount: Int,
    nutrition: Macronutrients
) {
    val progress = if (targetCalories > 0) (totalCalories.toFloat() / targetCalories).coerceIn(0f, 1f) else 0f
    WellnessCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("하루 섭취량", style = MaterialTheme.typography.titleMedium)
                Text("${recordCount}건", style = MaterialTheme.typography.labelLarge)
            }
            MetricValue(NumberFormat.getNumberInstance().format(totalCalories), "kcal")
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(statusText.ifBlank { "오늘의 기록을 차곡차곡 모아보세요." }, style = MaterialTheme.typography.bodyMedium)
                Text("목표 ${NumberFormat.getNumberInstance().format(targetCalories)}", style = MaterialTheme.typography.labelLarge)
            }
            MacroSummaryRow(nutrition)
        }
    }
}

@Composable
fun HistoryItemCard(meal: MealRecord, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecommendationPhoto(
                templateId = null,
                foodName = meal.foodName,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    meal.foodName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${meal.time} · ${meal.mealType.displayName}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                meal.portionDisplayLabel?.let { label ->
                    Text(label, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(NumberFormat.getNumberInstance().format(meal.calories), style = MaterialTheme.typography.titleLarge)
                Text("kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "상세 보기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HistoryDetailPane(
    meal: MealRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    nutrition: Macronutrients = Macronutrients(
        meal.carbohydrateGrams, meal.proteinGrams, meal.fatGrams
    )
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WellnessTopAppBar(
                title = "기록 상세",
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "기록 편집")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(WellnessSpacing.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            item {
                WellnessCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("식사 메모", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary)
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(meal.mealType.displayName, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
                        }
                        Text(meal.foodName, style = MaterialTheme.typography.headlineMedium)
                        MetricValue(NumberFormat.getNumberInstance().format(meal.calories), "kcal")
                        MacroSummaryRow(nutrition)
                        Text(meal.portionDisplayLabel ?: "기록한 양을 확인해 보세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                DetailCard(
                    title = "기록 정보",
                    rows = listOf("날짜" to meal.date, "시간" to meal.time,
                        "기록 방법" to recordSourceLabel(meal.source)) +
                        listOfNotNull(meal.portionDisplayLabel?.let { "선택한 양" to it }) +
                        if (meal.servingAmount != null || !meal.servingUnit.isNullOrBlank()) {
                            listOf((if (meal.portionDisplayLabel == null) "섭취량" else "계산 기준") to
                                listOfNotNull(meal.servingAmount?.let { formatServing(it) }, meal.servingUnit).joinToString(" "))
                        } else emptyList()
                )
            }
            if (!meal.memo.isNullOrBlank()) {
                item {
                    WellnessCard {
                        Column(modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent)) {
                            Text("메모", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(meal.memo, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (meal.portionEstimationType == "VISUAL_ESTIMATE") {
                item {
                    Text("먹은 양을 눈대중으로 고른 대략적인 값이에요. 실제 양과 차이가 날 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Text("기록 편집")
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "기록 삭제",
                            tint = MaterialTheme.colorScheme.error)
                        Text("기록 삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryEditPane(
    state: MealEditUiState,
    onFoodNameChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (java.time.LocalTime) -> Unit,
    onServingAmountChange: (String) -> Unit,
    onServingUnitChange: (String) -> Unit,
    onPortionSelected: (PortionPreset) -> Unit = {},
    onPortionRatio: (Double, String) -> Unit = { _, _ -> },
    onPreciseEdit: () -> Unit = {},
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = KeyboardActions(onDone = { keyboardController?.hide() })
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WellnessTopAppBar(
                title = "기록 편집",
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isSaving, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "편집 취소")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.imePadding().fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = WellnessSpacing.ScreenHorizontal,
                end = WellnessSpacing.ScreenHorizontal,
                top = WellnessSpacing.Compact,
                bottom = WellnessSpacing.Section
            ),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            item { SectionHeader("음식과 칼로리") }
            item {
                WellnessCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.foodName,
                            onValueChange = onFoodNameChange,
                            label = { Text("음식 이름") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            isError = state.foodNameError != null,
                            supportingText = state.foodNameError?.let { message -> { Text(message) } },
                            modifier = Modifier.fillMaxWidth().withErrorSemantics(state.foodNameError)
                        )
                        OutlinedTextField(
                            value = state.calories,
                            onValueChange = onCaloriesChange,
                            label = { Text("칼로리") },
                            suffix = { Text("kcal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            singleLine = true,
                            isError = state.caloriesError != null,
                            supportingText = state.caloriesError?.let { message -> { Text(message) } },
                            modifier = Modifier.fillMaxWidth().withErrorSemantics(state.caloriesError)
                        )
                    }
                }
            }
            item { SectionHeader("식사 정보", modifier = Modifier.padding(top = 8.dp)) }
            item {
                WellnessCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        WellnessMealTypeSelector(state.mealType, onMealTypeChange)
                        WellnessDateTimeSelectors(state.date, state.time, onDateChange, onTimeChange)
                    }
                }
            }
            item { SectionHeader("섭취량", modifier = Modifier.padding(top = 8.dp)) }
            item {
                WellnessCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("얼마나 먹었나요?", style = MaterialTheme.typography.titleMedium)
                        state.portionDisplayLabel?.let {
                            Text("저장한 양 · $it", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        val presets = state.selectedFood?.let(PortionGuide::presets).orEmpty()
                        if (presets.isNotEmpty()) {
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = state.portionPresetId == preset.id,
                                    onClick = { onPortionSelected(preset) },
                                    label = { Text(preset.label) },
                                    leadingIcon = if (state.portionPresetId == preset.id) {
                                        { Icon(Icons.Rounded.ChevronRight, contentDescription = "선택됨") }
                                    } else null
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.5 to "절반", 1.0 to "같은 양", 1.5 to "한 번 반").forEach { (ratio, label) ->
                                FilterChip(
                                    selected = state.portionPresetId == "edit-ratio-$ratio",
                                    onClick = { onPortionRatio(ratio, label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text("선택한 양을 바탕으로 한 예상 칼로리예요.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onPreciseEdit) { Text("더 정확히 입력하기") }
                        if (state.preciseAmountOpen || state.portionDisplayLabel == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = state.servingAmount,
                                    onValueChange = onServingAmountChange,
                                    label = { Text("양") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                    keyboardActions = dismissKeyboard,
                                    singleLine = true,
                                    isError = state.servingAmountError != null,
                                    supportingText = state.servingAmountError?.let { message -> { Text(message) } },
                                    modifier = Modifier.weight(1f).withErrorSemantics(state.servingAmountError)
                                )
                                OutlinedTextField(
                                    value = state.servingUnit,
                                    onValueChange = onServingUnitChange,
                                    label = { Text("단위") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = dismissKeyboard,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            item { SectionHeader("메모", modifier = Modifier.padding(top = 8.dp)) }
            item {
                WellnessCard {
                    OutlinedTextField(
                        value = state.memo,
                        onValueChange = onMemoChange,
                        label = { Text("메모 (선택)") },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = dismissKeyboard,
                        modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent).testTag("history-edit-memo")
                    )
                }
            }
            state.saveError?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { error(message) }) }
            }
            item {
                Button(
                    onClick = onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("history-edit-save"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text("수정 저장")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(title: String, rows: List<Pair<String, String>>) {
    WellnessCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
fun EmptyDetailPane() {
    Box(
        modifier = Modifier.fillMaxSize().padding(WellnessSpacing.ScreenHorizontal),
        contentAlignment = Alignment.Center
    ) {
        WellnessEmptyState(
            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
            title = "기록을 선택해 주세요",
            message = "선택한 식사의 상세 내용이 여기에 표시돼요."
        )
    }
}

private fun formatServing(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun recordSourceLabel(source: RecordSource): String = when (source) {
    RecordSource.MANUAL -> "직접 입력"
    RecordSource.SAVED_FOOD -> "내 음식"
    RecordSource.PHOTO_AI -> "사진 분석"
    RecordSource.FOOD_SEARCH -> "음식 검색"
    RecordSource.BARCODE -> "바코드"
    RecordSource.NUTRITION_LABEL -> "영양성분표"
    RecordSource.RECOMMENDATION -> "식단 추천"
    RecordSource.RECENT_REPEAT -> "최근 음식"
}

private fun Modifier.withErrorSemantics(message: String?): Modifier =
    if (message == null) this else semantics { error(message) }

private val previewMeals = listOf(
    MealRecord(date = "2026-09-13", time = "08:15", mealType = MealType.BREAKFAST, foodName = "그릭 요거트와 제철 과일", calories = 320, servingAmount = 1.0, servingUnit = "인분"),
    MealRecord(date = "2026-09-13", time = "12:30", mealType = MealType.LUNCH, foodName = "현미밥과 닭가슴살 샐러드 아주 긴 음식 이름 확인", calories = 580, memo = "드레싱은 조금만")
)

private val previewTimeline = DailyIntakeTimeline.build(
    previewMeals,
    listOf(CalorieGoal(targetCalories = 2000, startDate = "2026-09-01")),
    emptyList()
)

@Preview(name = "기록 목록 360", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HistoryListPreview() {
    HealthCareTheme(darkTheme = false) {
        HistoryListPane(LocalDate.of(2026, 9, 13), previewMeals, 900, 2000, "목표까지 1,100 kcal 남았어요.", {}, {}, previewTimeline)
    }
}

@Preview(name = "기록 빈 상태 390", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun HistoryEmptyPreview() {
    HealthCareTheme {
        HistoryListPane(LocalDate.of(2026, 9, 13), emptyList(), 0, 2000, "", {}, {})
    }
}

@Preview(name = "기록 상세 412", showBackground = true, widthDp = 412, heightDp = 820)
@Composable
private fun HistoryDetailPreview() {
    HealthCareTheme { HistoryDetailPane(previewMeals.last(), {}, {}, {}) }
}

@Preview(name = "기록 편집 360", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun HistoryEditPreview() {
    val meal = previewMeals.last()
    HealthCareTheme {
        HistoryEditPane(
            state = MealEditUiState(
                original = meal,
                foodName = meal.foodName,
                calories = meal.calories.toString(),
                mealType = meal.mealType,
                date = LocalDate.parse(meal.date),
                time = java.time.LocalTime.parse(meal.time),
                servingAmount = meal.servingAmount?.let(::formatServing).orEmpty(),
                servingUnit = meal.servingUnit.orEmpty(),
                memo = meal.memo.orEmpty()
            ),
            onFoodNameChange = {},
            onCaloriesChange = {},
            onMealTypeChange = {},
            onDateChange = {},
            onTimeChange = {},
            onServingAmountChange = {},
            onServingUnitChange = {},
            onMemoChange = {},
            onSave = {},
            onBack = {}
        )
    }
}
