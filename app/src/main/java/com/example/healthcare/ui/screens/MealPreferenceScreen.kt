package com.example.healthcare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.healthcare.data.entity.UserExcludedFood
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.FoodAllergenPolicy
import com.example.healthcare.ui.components.SectionHeader
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.WellnessTopAppBar
import com.example.healthcare.ui.theme.WellnessSpacing
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.MealPreferenceUiState
import com.example.healthcare.ui.viewmodel.MealPreferenceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MealPreferenceScreen(viewModel: MealPreferenceViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    MealPreferenceContent(
        state = state,
        onBack = onBack,
        onMealEnabled = viewModel::setMealEnabled,
        onRatioChange = viewModel::setRatio,
        onDietType = viewModel::setDietType,
        onCookingMode = viewModel::setCookingMode,
        onBudget = viewModel::setBudgetLevel,
        onDiversity = viewModel::setDiversity,
        onAllergyInput = viewModel::onAllergyInput,
        onAddAllergy = viewModel::addAllergy,
        onDislikeInput = viewModel::onDislikeInput,
        onAddDislike = viewModel::addDislike,
        onPreferredInput = viewModel::onPreferredInput,
        onRemoveExcluded = viewModel::removeExcluded,
        onSave = viewModel::save
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealPreferenceContent(
    state: MealPreferenceUiState,
    onBack: () -> Unit,
    onMealEnabled: (MealType, Boolean) -> Unit,
    onRatioChange: (MealType, String) -> Unit,
    onDietType: (String) -> Unit,
    onCookingMode: (String) -> Unit,
    onBudget: (String) -> Unit,
    onDiversity: (String) -> Unit,
    onAllergyInput: (String) -> Unit,
    onAddAllergy: () -> Unit,
    onDislikeInput: (String) -> Unit,
    onAddDislike: () -> Unit,
    onPreferredInput: (String) -> Unit,
    onRemoveExcluded: (UserExcludedFood) -> Unit,
    onSave: () -> Unit
) {
    val preference = state.preference
    val preferredFoodRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            WellnessTopAppBar(
                "식사 추천 설정",
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로가기") }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            contentPadding = PaddingValues(WellnessSpacing.ScreenHorizontal, WellnessSpacing.Compact, WellnessSpacing.ScreenHorizontal, WellnessSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("내가 편한 한 끼를 위해", style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() })
                    Text("식사 시간과 취향을 정하면 추천에 반영해요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SectionHeader(title = "하루 식사 구성", supportingText = "앱 기본 배분은 25/35/30/10이며 의료 기준이 아닙니다.") }
            item {
                WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MealRatioRow(MealType.BREAKFAST, preference.breakfastEnabled, preference.breakfastRatio, onMealEnabled, onRatioChange)
                        MealRatioRow(MealType.LUNCH, preference.lunchEnabled, preference.lunchRatio, onMealEnabled, onRatioChange)
                        MealRatioRow(MealType.DINNER, preference.dinnerEnabled, preference.dinnerRatio, onMealEnabled, onRatioChange)
                        MealRatioRow(MealType.SNACK, preference.snackEnabled, preference.snackRatio, onMealEnabled, onRatioChange)
                        Text(
                            "활성 식사 합계 ${state.enabledRatioTotal}%",
                            color = if (state.enabledRatioTotal == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "식단 조건",
                    supportingText = "등록한 이름이 음식명 또는 식단의 알레르기 표기와 일치하면 후보에서 제외됩니다. 실제 제품과 메뉴의 원재료 표시는 반드시 다시 확인해 주세요."
                )
            }
            item {
                WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("식단 성향", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("GENERAL" to "일반", "VEGETARIAN" to "채식").forEach { (value, label) ->
                                FilterChip(selected = preference.dietType == value, onClick = { onDietType(value) }, label = { Text(label) })
                            }
                        }
                        Text("식사 방식", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ANY" to "모두", "COOK" to "조리", "DINING_OUT" to "외식", "CONVENIENCE" to "편의점").forEach { (value, label) ->
                                FilterChip(selected = preference.cookingMode == value, onClick = { onCookingMode(value) }, label = { Text(label) })
                            }
                        }
                        Text("예산", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ANY" to "상관없음", "LOW" to "낮음", "MEDIUM" to "보통", "HIGH" to "여유").forEach { (value, label) ->
                                FilterChip(selected = preference.budgetLevel == value, onClick = { onBudget(value) }, label = { Text(label) })
                            }
                        }
                        Text("추천 다양성", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("FAMILIAR" to "익숙한 음식", "BALANCED" to "균형", "VARIED" to "다양하게").forEach { (value, label) ->
                                FilterChip(selected = preference.recommendationDiversity == value, onClick = { onDiversity(value) }, label = { Text(label) })
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = "알레르기 원인", supportingText = "메뉴명이 아니라 반응을 일으키는 원재료를 등록해 주세요.") }
            item {
                WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            FoodAllergenPolicy.supportedCauses.forEach { cause ->
                                FilterChip(
                                    selected = state.allergyInput == cause,
                                    onClick = { onAllergyInput(cause) },
                                    label = { Text(cause) }
                                )
                            }
                        }
                        ExclusionInput("알레르기 원인", state.allergyInput, onAllergyInput, onAddAllergy)
                        Text(
                            "등록한 원인과 확인된 원재료가 일치하면 추천 카드에 알레르기 주의를 표시합니다. 원재료 정보가 일부 미확인인 메뉴도 숨기지 않고 그 상태를 함께 알려드립니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.excludedFoods.filter { it.exclusionType == "ALLERGY" }.forEach { food ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "알레르기 · ${food.normalizedFoodName}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(onClick = { onRemoveExcluded(food) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "${food.normalizedFoodName} 삭제")
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader(title = "먹지 않는 음식", supportingText = "음식명이나 제외 키워드를 등록하면 그 단어가 포함된 메뉴를 추천에서 뺍니다.") }
            item {
                WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExclusionInput("음식명 또는 키워드", state.dislikeInput, onDislikeInput, onAddDislike)
                        if (state.broadDislikeWarning) {
                            Text(
                                "이 단어를 포함한 많은 메뉴가 추천에서 제외될 수 있어요. 원하는 경우 그대로 추가할 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        state.excludedFoods.filter { it.exclusionType != "ALLERGY" }.forEach { food ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("제외 · ${food.normalizedFoodName}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { onRemoveExcluded(food) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "${food.normalizedFoodName} 삭제")
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.preferredInput,
                    onValueChange = onPreferredInput,
                    label = { Text("선호 음식") },
                    supportingText = { Text("여러 음식은 쉼표로 구분하세요. 선호는 하드 필터가 아닙니다.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(preferredFoodRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(180)
                                    preferredFoodRequester.bringIntoView()
                                }
                            }
                        }
                )
            }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) } }
            item {
                Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Rounded.RestaurantMenu, contentDescription = null)
                        Text(" 추천 설정 저장")
                    }
                }
            }
            item {
                Text(
                    "추천은 일반적인 식사 선택을 돕는 도구이며 의료 처방이 아닙니다. 임신·수유, 미성년자 또는 질환 치료 중이라면 전문가 상담이 필요할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MealRatioRow(
    type: MealType,
    enabled: Boolean,
    ratio: Int,
    onEnabled: (MealType, Boolean) -> Unit,
    onRatioChange: (MealType, String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = enabled, onCheckedChange = { onEnabled(type, it) })
        Text(type.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = ratio.toString(),
            onValueChange = { onRatioChange(type, it) },
            modifier = Modifier.fillMaxWidth(0.34f),
            enabled = enabled,
            suffix = { Text("%") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

@Composable
private fun ExclusionInput(label: String, value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onAdd, enabled = value.isNotBlank()) {
            Icon(Icons.Rounded.Add, contentDescription = "$label 추가")
        }
    }
}

@Preview(name = "추천 취향 360", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun MealPreferenceEditorialPreview() {
    HealthCareTheme {
        MealPreferenceContent(
            state = MealPreferenceUiState(), onBack = {},
            onMealEnabled = { _, _ -> }, onRatioChange = { _, _ -> },
            onDietType = {}, onCookingMode = {},
            onBudget = {}, onDiversity = {}, onAllergyInput = {},
            onAddAllergy = {}, onDislikeInput = {}, onAddDislike = {},
            onPreferredInput = {}, onRemoveExcluded = {}, onSave = {}
        )
    }
}
