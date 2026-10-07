package com.example.healthcare.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.healthcare.domain.*
import com.example.healthcare.data.model.MealType
import com.example.healthcare.ui.components.RecommendationPhoto
import com.example.healthcare.ui.components.ContextualAllergyNotice
import com.example.healthcare.ui.viewmodel.TodayMealPlanUiState
import com.example.healthcare.ui.viewmodel.TodayMealPlanViewModel

@Composable
fun TodayRecommendationScreen(viewModel: TodayMealPlanViewModel, onBack: () -> Unit,
    onOpenPreferences: () -> Unit, onOpenEnergy: () -> Unit, onOpenMeal: (DailyPlanMeal, Int) -> Unit,
    openSaved: Boolean = false, focusedMeal: MealType? = null) {
    val state by viewModel.uiState.collectAsState()
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        if (!entered) { entered = true; if (openSaved) viewModel.showSavedPlan() else viewModel.showThemes() }
    }
    BackHandler(state.selectedTheme != null) { viewModel.showThemes() }
    TodayRecommendationContent(state, viewModel::selectTheme, viewModel::showThemes,
        viewModel::replace, viewModel::alternate, viewModel::retry, onOpenPreferences, onOpenEnergy, onOpenMeal, onBack,
        focusedMeal = focusedMeal)
}

@Composable
internal fun TodayRecommendationContent(state: TodayMealPlanUiState, onSelect: (DailyRecommendationTheme) -> Unit,
    onThemes: () -> Unit, onReplace: (MealType) -> Unit, onAlternate: () -> Unit, onRetry: () -> Unit,
    onPreferences: () -> Unit, onEnergy: () -> Unit, onOpenMeal: (DailyPlanMeal, Int) -> Unit,
    onBack: () -> Unit = {}, focusedMeal: MealType? = null) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusedMeal, state.plan?.signature, state.loading) {
        val plan = state.plan
        if (focusedMeal != null && plan != null && !state.loading) {
            val index = plan.meals.indexOfFirst { it.mealType == focusedMeal && !it.recorded }
            if (index >= 0) {
                // Header, context hint, optional message and plan total precede the meal cards.
                listState.animateScrollToItem(3 + (if (state.message != null) 1 else 0) + index)
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("daily-plan-list"), state = listState, contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = if (state.selectedTheme == null) onBack else onThemes,
                enabled = !state.loading, modifier = Modifier.heightIn(min = 48.dp).testTag("daily-plan-back")) {
                Text(if (state.selectedTheme == null) "홈으로" else "다른 테마 선택")
            }
            Text(if (state.selectedTheme == null) "오늘은 어떻게 먹고 싶나요?" else "오늘의 ${state.selectedTheme.label} 식단",
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag("daily-plan-title"))
        }
        if (focusedMeal != null) item(key = "next-meal-context") {
            Text("${focusedMeal.displayName} 추천 · 이미 기록한 식사는 유지하고 남은 하루 목표로 골라요.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("daily-next-meal-context"))
        }
        if (state.selectedTheme == null) {
            DailyRecommendationTheme.entries.forEach { theme ->
                item {
                    Surface(onClick = { onSelect(theme) }, shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .testTag("daily-theme-${theme.name}")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(theme.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                            Text(theme.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item { Text("선택한 스타일은 하루 목표 칼로리를 바꾸지 않아요.", style = MaterialTheme.typography.bodySmall) }
        } else {
            if (state.loading) item {
                CircularProgressIndicator()
                Text("오늘 식단을 고르고 있어요.", modifier = Modifier.testTag("daily-plan-loading"))
            }
            state.message?.let { message -> item {
                Text(message, modifier = Modifier.testTag("daily-plan-message"))
                if (state.target == null) OutlinedButton(onClick = onEnergy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("에너지 목표 설정")
                }
                if (state.error) OutlinedButton(onClick = onRetry, enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("다시 시도") }
                OutlinedButton(onClick = onPreferences, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("피하고 싶은 음식 설정 확인") }
            } }
            state.plan?.let { plan ->
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("총 ${plan.totalKcal} / ${plan.targetKcal} kcal", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().testTag("daily-plan-total"))
                    Text(plan.differenceText)
                    if (plan.recordedKcal > 0) Text("이미 기록한 ${plan.recordedKcal} kcal · 남은 예산 ${plan.remainingBudgetKcal} kcal")
                    plan.reasons.forEach { reason ->
                        key(reason) { Text(reason, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(), softWrap = true) }
                    }
                    val n = plan.nutrition
                    Text("${if (plan.macroComplete) "하루 영양 합계" else "확인 가능한 영양정보 기준"} · 탄 ${MacronutrientFormatter.grams(n.carbohydrateGrams)} · 단 ${MacronutrientFormatter.grams(n.proteinGrams)} · 지 ${MacronutrientFormatter.grams(n.fatGrams)}",
                        style = MaterialTheme.typography.bodySmall)
                    if (plan.macroComplete) {
                        val c = requireNotNull(n.carbohydrateGrams) * 4
                        val p = requireNotNull(n.proteinGrams) * 4
                        val f = requireNotNull(n.fatGrams) * 9
                        if (c + p + f > 0) Text("탄단지 열량비 ${(c * 100 / (c+p+f)).toInt()}% · ${(p * 100 / (c+p+f)).toInt()}% · ${(f * 100 / (c+p+f)).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    }
                }
                plan.meals.forEach { meal -> item(key = "meal-${meal.mealType.name}") {
                    Card(Modifier.fillMaxWidth().testTag("daily-meal-${meal.mealType.name}")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(meal.mealType.displayName, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                            if (meal.mealType == focusedMeal && !meal.recorded) Text("다음 식사 추천",
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("daily-focused-${meal.mealType.name}"))
                            if (meal.recorded) Text("이미 기록한 식사", modifier = Modifier.testTag("daily-recorded-${meal.mealType.name}"))
                            else RecommendationPhoto(templateId = meal.templateId, foodName = meal.name,
                                contentDescription = "${meal.name} 추천 이미지", modifier = Modifier.fillMaxWidth().height(128.dp))
                            Text(meal.name, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth().testTag("daily-name-${meal.mealType.name}"))
                            meal.amountLabels.forEach { label -> Text(label, modifier = Modifier.fillMaxWidth()
                                .testTag("daily-amount-${meal.mealType.name}"), style = MaterialTheme.typography.bodyMedium) }
                            Text("${meal.kcal} kcal", modifier = Modifier.testTag("daily-kcal-${meal.mealType.name}"))
                            Text("탄 ${MacronutrientFormatter.grams(meal.nutrition.carbohydrateGrams)} · 단 ${MacronutrientFormatter.grams(meal.nutrition.proteinGrams)} · 지 ${MacronutrientFormatter.grams(meal.nutrition.fatGrams)}",
                                style = MaterialTheme.typography.bodySmall)
                            if (!meal.recorded) {
                                ContextualAllergyNotice(state.configuredAllergies, meal.matchedAllergens,
                                    meal.allergenInfoComplete, decisionPoint = false)
                                OutlinedButton(onClick = { onOpenMeal(meal, plan.targetKcal) }, enabled = !state.loading,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("daily-detail-${meal.mealType.name}")) {
                                    Text("양·상세 확인 / 먹었어요")
                                }
                                OutlinedButton(onClick = { onReplace(meal.mealType) }, enabled = !state.loading,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("daily-replace-${meal.mealType.name}")) { Text("다른 메뉴") }
                            }
                        }
                    }
                } }
                item { Button(onClick = onAlternate, enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("daily-plan-alternate")) { Text("다른 조합 보기") } }
                item { Text("하루 식단을 고르는 것만으로 기록되지 않아요. 실제로 먹은 식사만 상세에서 기록해 주세요.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
