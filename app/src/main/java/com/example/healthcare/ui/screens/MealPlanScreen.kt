package com.example.healthcare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.repository.MealRecommendationWithIngredients
import com.example.healthcare.domain.ScoredMealRecommendation
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.RecommendationStage
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.MealRecommendationEngine
import com.example.healthcare.ui.components.SectionHeader
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.WellnessEmptyState
import com.example.healthcare.ui.components.MacroSummaryRow
import com.example.healthcare.ui.components.RecommendationPhoto
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.ui.theme.WellnessSpacing
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.MealPlanUiState
import com.example.healthcare.ui.viewmodel.MealPlanViewModel
import com.example.healthcare.ui.viewmodel.RecommendedIngredientUi
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.collect

@Composable
fun MealPlanScreen(
    mealType: MealType,
    budgetKcal: Int,
    targetKcal: Int,
    viewModel: MealPlanViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    dailyRemainingKcal: Int = targetKcal,
    onSearchFood: () -> Unit = {},
    onOpenPreferences: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(mealType, budgetKcal, targetKcal) {
        viewModel.load(mealType, budgetKcal, targetKcal, dailyRemainingKcal)
    }
    LaunchedEffect(viewModel) { viewModel.saveCompletedEvents.collect { onSaved() } }
    MealPlanContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onAdvanceFallback = viewModel::advanceFallback,
        onExpandCookingMode = viewModel::expandCookingMode,
        onSearchFood = onSearchFood,
        onOpenPreferences = onOpenPreferences,
        onSelect = viewModel::selectRecommendation,
        onAdjustPortion = viewModel::adjustPortion,
        onIngredientIncluded = viewModel::setIngredientIncluded,
        onShowReplacements = viewModel::showReplacements,
        onReplace = viewModel::replaceIngredient,
        onConfirm = viewModel::requestConsumptionConfirmation,
        onSaveConsumption = viewModel::confirmConsumed,
        onConsumedRatio = viewModel::setConsumedRatio,
        onFoodSpecific = viewModel::chooseFoodSpecificConsumption,
        onIngredientConsumedRatio = viewModel::setIngredientConsumedRatio
    )
}

@Composable
internal fun MealPlanContent(
    state: MealPlanUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (Int) -> Unit,
    onAdjustPortion: (Double) -> Unit,
    onIngredientIncluded: (Long, Boolean) -> Unit,
    onShowReplacements: (Long) -> Unit,
    onReplace: (Long, com.example.healthcare.data.entity.FoodItem) -> Unit,
    onConfirm: () -> Unit,
    onSaveConsumption: () -> Unit = {},
    onConsumedRatio: (Double) -> Unit = {},
    onFoodSpecific: () -> Unit = {},
    onIngredientConsumedRatio: (Long, Double) -> Unit = { _, _ -> },
    onAdvanceFallback: () -> Unit = {},
    onExpandCookingMode: (String) -> Unit = {},
    onSearchFood: () -> Unit = {},
    onOpenPreferences: () -> Unit = {}
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(WellnessSpacing.ScreenHorizontal, 12.dp, WellnessSpacing.ScreenHorizontal, WellnessSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("식사 추천") }, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = false,
                        onClick = onRefresh,
                        enabled = !state.isLoading,
                        label = { Text(if (state.isLoading) "교체 중" else "식단 교체") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (state.stage != RecommendationStage.EXACT) {
                item {
                    val description = when (state.stage) {
                        RecommendationStage.WIDER_CALORIES -> "칼로리 허용 범위를 ±20%(최소 ±150 kcal)로 넓혔어요."
                        RecommendationStage.EXPANDED_MODE -> "선택한 식사 방식도 포함해서 찾았어요."
                        RecommendationStage.CLOSEST_VERIFIED -> "정확히 맞는 식단 대신 칼로리가 가까운 검증 식단을 보여드려요."
                        RecommendationStage.EXACT -> ""
                    }
                    Text(description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.isLoading) {
                item {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.loadError) {
                item {
                    WellnessEmptyState(Icons.Rounded.RestaurantMenu, "추천을 불러오지 못했어요",
                        state.message ?: "저장 데이터 상태를 확인한 뒤 다시 시도해 주세요.")
                }
                if (state.budgetKcal > 0) item {
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("다시 시도") }
                }
                item { OutlinedButton(onClick = onSearchFood, modifier = Modifier.fillMaxWidth()) { Text("음식 검색으로 직접 기록") } }
                item { OutlinedButton(onClick = onOpenPreferences, modifier = Modifier.fillMaxWidth()) { Text("추천 조건 설정") } }
            } else if (state.recommendations.isEmpty()) {
                item {
                    WellnessEmptyState(
                        Icons.Rounded.RestaurantMenu,
                        when {
                            state.templateCount == 0 -> "추천 데이터가 준비되지 않았어요"
                            state.verifiedCandidateCount == 0 -> "조건에 맞는 검증 식단이 아직 없어요"
                            state.stage == RecommendationStage.EXACT -> "딱 맞는 식단은 없어요"
                            state.stage == RecommendationStage.WIDER_CALORIES -> "넓힌 칼로리 범위에도 새 식단이 없어요"
                            else -> "더 보여드릴 검증 식단이 없어요"
                        },
                        if (state.templateCount == 0) "검증 식단을 불러온 뒤 다시 확인해 주세요."
                        else if (state.verifiedCandidateCount == 0) "이 식사에 사용할 수 있는 검증 식단이 없습니다. 음식 검색을 이용해 주세요."
                        else "알레르기·제외 음식·식단 제한은 그대로 지키며 다음 조건을 직접 선택해 넓힐 수 있어요."
                    )
                }
            } else {
                item { ReferenceRecommendationLayout(state = state, onSelect = onSelect) }
            }
            if (!state.isLoading && !state.loadError && !state.hasMore && state.selectedMeal == null) {
                item {
                    RecommendationFallbackActions(state, onAdvanceFallback, onExpandCookingMode,
                        onSearchFood, onOpenPreferences)
                }
            }
            state.selectedMeal?.let { selected ->
                item { SectionHeader(title = "먹은 내용 확인", supportingText = "먹지 않은 항목은 선택 해제하거나 다른 음식으로 교체하고 양을 조절한 뒤 기록하세요.") }
                item {
                    WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(selected.templateName, style = MaterialTheme.typography.titleLarge)
                            Text(selected.reason, style = MaterialTheme.typography.bodySmall)
                            RecommendationDataNotices(
                                matchedAllergens = selected.matchedAllergens,
                                ingredientInfoComplete = selected.ingredientInfoComplete,
                                allergenInfoComplete = selected.allergenInfoComplete
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0.75, 1.0, 1.25).forEach { ratio ->
                                    FilterChip(
                                        selected = selected.portionRatio == ratio,
                                        onClick = { onAdjustPortion(ratio) },
                                        label = { Text("${(ratio * 100).toInt()}%") }
                                    )
                                }
                            }
                            selected.ingredients.forEach { ingredient ->
                                IngredientConfirmation(
                                    ingredient = ingredient,
                                    onIncludedChange = { onIngredientIncluded(ingredient.ingredientId, it) },
                                    onShowReplacements = { onShowReplacements(ingredient.ingredientId) },
                                    onReplace = { onReplace(ingredient.ingredientId, it) }
                                )
                            }
                            Text("추천한 양 · 약 ${formatKcal(selected.totalCalories)} kcal", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            MacroSummaryRow(selected.consumedNutrition)
                            missingMacroMessage(selected.consumedNutrition.carbohydrateGrams,
                                selected.consumedNutrition.proteinGrams, selected.consumedNutrition.fatGrams)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!state.showConsumptionConfirm) Button(onClick = onConfirm,
                                enabled = !state.isSaving && !state.saved, modifier = Modifier.fillMaxWidth()) {
                                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                    Text(if (state.saved) "기록됨" else "먹었어요")
                                }
                            }
                            if (state.showConsumptionConfirm && !state.saved) {
                                Text("추천한 양만큼 드셨나요?", style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.semantics { heading() })
                                Text("대략 골라도 괜찮아요. 실제 먹은 정도로 기록할게요.",
                                    style = MaterialTheme.typography.bodyMedium)
                                listOf(
                                    1.0 to "다 먹었어요", 0.9 to "거의 다 먹었어요",
                                    0.5 to "절반 정도 먹었어요", 0.25 to "조금 먹었어요"
                                ).chunked(2).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { (ratio, label) ->
                                            FilterChip(
                                                selected = !state.foodSpecificConsumption && state.consumedRatio == ratio,
                                                onClick = { onConsumedRatio(ratio) },
                                                label = { Text(label) },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            )
                                        }
                                    }
                                }
                                OutlinedButton(onClick = onFoodSpecific, modifier = Modifier.fillMaxWidth()) {
                                    Text("음식별로 다르게 먹었어요")
                                }
                                if (state.foodSpecificConsumption) {
                                    selected.ingredients.filter { it.included }.forEach { ingredient ->
                                        Text(FoodSearchPolicy.displayName(ingredient.foodItem), style = MaterialTheme.typography.titleSmall)
                                        listOf(0.25 to "조금", 0.5 to "절반", 0.9 to "거의 다", 1.0 to "전부")
                                            .chunked(2).forEach { choices ->
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    choices.forEach { (ratio, label) ->
                                                        FilterChip(
                                                            selected = ingredient.consumedRatio == ratio,
                                                            onClick = { onIngredientConsumedRatio(ingredient.ingredientId, ratio) },
                                                            label = { Text(label) },
                                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                                        )
                                                    }
                                                }
                                            }
                                    }
                                }
                                Text("기록할 예상 섭취", style = MaterialTheme.typography.labelLarge)
                                Text("약 ${formatKcal(selected.consumedCalories)} kcal",
                                    style = MaterialTheme.typography.headlineMedium)
                                MacroSummaryRow(selected.consumedNutrition)
                                Button(onClick = onSaveConsumption, enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                    Text("이 양으로 기록")
                                }
                            }
                        }
                    }
                }
            }
            state.message?.let { message ->
                item { Text(message, style = MaterialTheme.typography.bodyMedium, color = if (state.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            }
            item {
                Text(
                    "원재료·알레르기 정보는 확인 가능한 공식·공공 자료를 기준으로 제공하며 실제 조리·제품 변경에 따라 달라질 수 있습니다. 추천은 의료 처방이 아닙니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ReferenceRecommendationLayout(
    state: MealPlanUiState,
    onSelect: (Int) -> Unit
) {
    val main = state.recommendations.first()
    val template = main.recommendation.template
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(
            onClick = { onSelect(0) },
            modifier = Modifier.fillMaxWidth().height(238.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            RecommendationPhoto(
                templateId = template.id,
                foodName = template.name,
                contentDescription = "${template.name} 추천 이미지",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text("오늘의 추천 식사", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            template.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            main.recommendation.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        RecommendationDataNotices(
            matchedAllergens = main.matchedAllergens,
            ingredientInfoComplete = main.ingredientInfoComplete,
            allergenInfoComplete = main.allergenInfoComplete
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RecommendationMetric("약 ${formatKcal(template.totalKcal)} kcal")
            RecommendationMetric("탄 ${formatMacroGrams(template.carbohydrateGrams)}")
            RecommendationMetric("단 ${formatMacroGrams(template.proteinGrams)}")
            RecommendationMetric("지 ${formatMacroGrams(template.fatGrams)}")
        }
        missingMacroMessage(template.carbohydrateGrams, template.proteinGrams, template.fatGrams)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("다른 추천도 확인해보세요", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            state.recommendations.drop(1).take(2).forEachIndexed { alternativeIndex, item ->
                val alternativeTemplate = item.recommendation.template
                Surface(
                    onClick = { onSelect(alternativeIndex + 1) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        RecommendationPhoto(
                            templateId = alternativeTemplate.id,
                            foodName = alternativeTemplate.name,
                            contentDescription = "${alternativeTemplate.name} 추천 이미지",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(79.dp)
                        )
                        Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
                            Text(alternativeTemplate.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1)
                            Text("약 ${formatKcal(alternativeTemplate.totalKcal)} kcal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.RecommendationMetric(text: String) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp)
        )
    }
}

private fun formatMacroGrams(value: Double?): String =
    value?.let { "${String.format(Locale.KOREA, "%.1f", it)}g" } ?: "미확인"

private fun missingMacroMessage(carbohydrate: Double?, protein: Double?, fat: Double?): String? {
    val missing = buildList {
        if (carbohydrate == null) add("탄수화물")
        if (protein == null) add("단백질")
        if (fat == null) add("지방")
    }
    return missing.takeIf { it.isNotEmpty() }?.joinToString("·", prefix = "일부 영양정보 없음 · ")
}

@Composable
private fun RecommendationDataNotices(
    matchedAllergens: Set<String>,
    ingredientInfoComplete: Boolean,
    allergenInfoComplete: Boolean
) {
    val shownAllergens = matchedAllergens.filterNot {
        it.equals("UNKNOWN", ignoreCase = true)
    }
    if (shownAllergens.isNotEmpty()) {
        Text(
            "알레르기 주의 · ${shownAllergens.joinToString("·")} 포함",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (!ingredientInfoComplete) {
        Text(
            "원재료 정보 일부 미확인",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (!allergenInfoComplete) {
        Text(
            "알레르기 정보 일부 미확인",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecommendationFallbackActions(
    state: MealPlanUiState,
    onAdvance: () -> Unit,
    onExpandMode: (String) -> Unit,
    onSearchFood: () -> Unit,
    onOpenPreferences: () -> Unit
) {
    WellnessCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("다른 선택지", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text("현재 조건의 식단을 모두 본 뒤에는 직전 식단을 제외하고 다시 순환해요. 알레르기·제외 음식·식단 제한과 검증 데이터 조건은 유지합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val nextLabel = if (state.templateCount == 0 || state.verifiedCandidateCount == 0) null else when (state.stage) {
                RecommendationStage.EXACT -> "칼로리 범위 넓혀 보기"
                RecommendationStage.WIDER_CALORIES -> "가장 가까운 검증 식단 보기"
                RecommendationStage.EXPANDED_MODE -> "가장 가까운 검증 식단 보기"
                RecommendationStage.CLOSEST_VERIFIED -> null
            }
            nextLabel?.let {
                OutlinedButton(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) { Text(it) }
            }
            if (state.stage in setOf(RecommendationStage.WIDER_CALORIES,
                    RecommendationStage.CLOSEST_VERIFIED) && state.cookingMode != "ANY" &&
                state.extraCookingMode == null && state.verifiedCandidateCount > 0) {
                Text("원한다면 조리 방식도 직접 넓힐 수 있어요.", style = MaterialTheme.typography.bodySmall)
                listOf("COOK" to "집밥", "DINING_OUT" to "외식", "CONVENIENCE" to "편의점")
                    .filter { it.first != state.cookingMode }.forEach { (mode, label) ->
                        OutlinedButton(onClick = { onExpandMode(mode) }, modifier = Modifier.fillMaxWidth()) {
                            Text("$label 식단도 포함")
                        }
                    }
            }
            OutlinedButton(onClick = onSearchFood, modifier = Modifier.fillMaxWidth()) {
                Text("음식 검색으로 직접 기록")
            }
            OutlinedButton(onClick = onOpenPreferences, modifier = Modifier.fillMaxWidth()) {
                Text("추천 조건 설정")
            }
        }
    }
}

@Composable
private fun IngredientConfirmation(
    ingredient: RecommendedIngredientUi,
    onIncludedChange: (Boolean) -> Unit,
    onShowReplacements: () -> Unit,
    onReplace: (com.example.healthcare.data.entity.FoodItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ingredient.included,
                onCheckedChange = onIncludedChange
            )
            Column(Modifier.weight(1f)) {
                Text(FoodSearchPolicy.displayName(ingredient.foodItem), style = MaterialTheme.typography.titleSmall)
                Text("${PortionGuide.recommendationLabel(ingredient.foodItem, ingredient.amount)} · 약 ${formatKcal(ingredient.calories)} kcal",
                    style = MaterialTheme.typography.bodySmall)
                Text("약 ${ingredient.amount.formatAmount()}${ingredient.unit} 기준 · 실제 음식 양에 따라 달라질 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onShowReplacements, enabled = ingredient.included) { Text("교체") }
        }
        if (ingredient.showReplacements) {
            if (ingredient.replacements.isEmpty()) {
                Text("비슷한 검증 음식이 없습니다.", style = MaterialTheme.typography.bodySmall)
            } else {
                ingredient.replacements.forEach { replacement ->
                    OutlinedButton(onClick = { onReplace(replacement) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${FoodSearchPolicy.displayName(replacement)} · ${replacement.servingDescription} · ${formatKcal(replacement.energyKcal.toInt())} kcal")
                    }
                }
            }
        }
    }
}

private fun formatKcal(value: Int): String = NumberFormat.getNumberInstance().format(value)
private fun Double.formatAmount(): String = if (this == toLong().toDouble()) {
    toLong().toString()
} else {
    String.format(Locale.getDefault(), "%.1f", this)
}

@Preview(name = "추천 카드 390", widthDp = 390, heightDp = 820, showBackground = true)
@Composable
private fun MealPlanEditorialPreview() {
    val template = MealTemplate(
        id = "preview-meal", name = "따뜻한 한 끼", supportedMealTypes = "|LUNCH|",
        totalKcal = 620, preparationMinutes = 20, costLevel = "MEDIUM",
        source = "UI_PREVIEW", createdAt = 0, updatedAt = 0
    )
    val recommendation = ScoredMealRecommendation(
        template = template, score = 90.0, calorieDifference = 0,
        toleranceKcal = 80, reason = "현재 남은 칼로리에 가까워요."
    )
    HealthCareTheme {
        MealPlanContent(
            state = MealPlanUiState(
                budgetKcal = 620,
                recommendations = listOf(MealRecommendationWithIngredients(
                    recommendation, emptyList(), listOf("현미밥", "구운 채소"))
                ),
                recommendationPortionLabels = mapOf("preview-meal" to listOf("현미밥 2/3공기", "채소 반찬 작은 접시"))
            ),
            onBack = {}, onRefresh = {}, onSelect = {}, onAdjustPortion = {},
            onIngredientIncluded = { _, _ -> }, onShowReplacements = {},
            onReplace = { _, _ -> }, onConfirm = {}
        )
    }
}
