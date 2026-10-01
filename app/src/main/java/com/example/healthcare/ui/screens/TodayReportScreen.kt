package com.example.healthcare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.healthcare.domain.DashboardSummaryPolicy
import com.example.healthcare.domain.TodayFoodReport
import com.example.healthcare.ui.components.MacroSummaryRow
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.WellnessEmptyState
import com.example.healthcare.ui.components.WellnessTopAppBar
import com.example.healthcare.ui.theme.WellnessSpacing
import com.example.healthcare.ui.viewmodel.DashboardViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayReportScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    onAddRecord: () -> Unit
) {
    val date by viewModel.selectedDate.collectAsState()
    val meals by viewModel.meals.collectAsState()
    val target by viewModel.targetCalories.collectAsState()
    val nutrition by viewModel.dailyNutrition.collectAsState()
    val report = remember(meals, target, nutrition) {
        DashboardSummaryPolicy.report(meals, target, nutrition)
    }
    TodayReportContent(date, report, onBack, onAddRecord)
}

@Composable
internal fun TodayReportContent(
    date: LocalDate,
    report: TodayFoodReport,
    onBack: () -> Unit,
    onAddRecord: () -> Unit
) {
    Scaffold(
        topBar = {
            WellnessTopAppBar(
                "오늘 식사 리포트",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("today-report"),
            contentPadding = PaddingValues(
                horizontal = WellnessSpacing.ScreenHorizontal,
                vertical = WellnessSpacing.Compact
            ),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("M월 d일 식사", Locale.KOREAN)),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(report.message, style = MaterialTheme.typography.bodyMedium)
                    if (report.hasPartialNutrition && report.foods.isNotEmpty()) {
                        Text(
                            "확인 가능한 기록 기준으로 계산했어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("오늘 섭취", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${report.totalCalories.formatted()} kcal",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("목표", style = MaterialTheme.typography.bodyMedium)
                            Text(report.targetCalories?.let { "${it.formatted()} kcal" } ?: "설정 없음")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if ((report.remainingCalories ?: 0) >= 0) "남은 양" else "목표 대비", style = MaterialTheme.typography.bodyMedium)
                            Text(report.remainingCalories?.let {
                                if (it >= 0) "${it.formatted()} kcal" else "+${(-it).formatted()} kcal"
                            } ?: "—")
                        }
                        MacroSummaryRow(report.nutrition)
                    }
                }
            }
            if (report.foods.isEmpty()) {
                item {
                    WellnessEmptyState(
                        Icons.Rounded.RestaurantMenu,
                        "오늘 기록된 식사가 없어요.",
                        "음식을 기록하면 식사별 합계와 음식별 kcal를 비교할 수 있어요."
                    )
                }
                item {
                    Button(onClick = onAddRecord, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text(" 음식 기록하기")
                    }
                }
            } else {
                item {
                    Text("식사별 kcal", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                }
                items(report.meals, key = { it.mealType.name }) { meal ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(meal.mealType.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            meal.calories?.let { calories ->
                                val suffix = if (meal.hasUnknownCalories) " · 일부 미확인" else ""
                                "${calories.formatted()} kcal$suffix"
                            } ?: "기록 없음",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Text("오늘 기록한 음식", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                }
                items(report.foods, key = { it.recordId }) { food ->
                    WellnessCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(food.name, style = MaterialTheme.typography.titleSmall)
                                Text(food.mealType.displayName, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(food.calories?.let { "${it.formatted()} kcal" } ?: "열량 정보 없음")
                        }
                    }
                }
            }
        }
    }
}

private fun Int.formatted(): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(this)
