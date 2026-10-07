package com.example.healthcare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
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
    val pendingSave by viewModel.saveAcknowledgement.pending.collectAsState()
    if (pendingSave != null) {
        com.example.healthcare.ui.components.RecordSavedDialog(false, body = "저장되었습니다.") {
            viewModel.saveAcknowledgement.confirm()
        }
    }
    val registerExitGuard = com.example.healthcare.ui.LocalDraftExitGuardRegistration.current
    androidx.compose.runtime.SideEffect {
        registerExitGuard(com.example.healthcare.ui.DraftExitGuard(viewModel.hasUnsavedInput,
            state.isSaving || state.isResettingLearning, viewModel::discardPreferenceDraft))
    }
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
        onSave = viewModel::save,
        onTogglePreferredStyle = viewModel::togglePreferredStyle,
        onRequestLearningReset = viewModel::requestLearningReset,
        onCancelLearningReset = viewModel::cancelLearningReset,
        onConfirmLearningReset = viewModel::confirmLearningReset
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
    onSave: () -> Unit,
    onTogglePreferredStyle: (String) -> Unit = {},
    onRequestLearningReset: () -> Unit = {},
    onCancelLearningReset: () -> Unit = {},
    onConfirmLearningReset: () -> Unit = {}
) {
    val preference = state.preference
    val preferredFoodRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    var detailedSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var allergySettingsExpanded by rememberSaveable { mutableStateOf(false) }
    if (state.showLearningResetConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelLearningReset,
            title = { Text("추천 학습을 초기화할까요?") },
            text = { Text("실제 선택을 반영한 추천 신호만 초기화해요. 식사 기록, 즐겨찾기, 좋아하는 음식, 피하고 싶은 음식과 알레르기 설정은 유지돼요.") },
            confirmButton = { TextButton(onClick = onConfirmLearningReset,
                modifier = Modifier.heightIn(min = 48.dp).testTag("learning-reset-confirm")) { Text("초기화") } },
            dismissButton = { TextButton(onClick = onCancelLearningReset,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") } }
        )
    }
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
                    Text("추천에 반영할 취향", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() })
                    Text("좋아하는 음식과 피하고 싶은 음식을 간단히 정해요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SectionHeader(title = "좋아하는 음식", supportingText = "조건에 맞는 후보 중 선호하는 음식이 먼저 추천될 수 있어요.") }
            item {
                WellnessCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PreferredStyleChips(state.preferredStyles.map { it.name }.toSet(), onTogglePreferredStyle,
                            enabled = !state.isSaving)
                        OutlinedTextField(
                            value = state.preferredInput,
                            onValueChange = onPreferredInput,
                            label = { Text("선호 음식") },
                            supportingText = { Text("구체적인 음식은 쉼표로 구분해 적어 주세요. 선택한 음식만 추천하는 설정은 아니에요.") },
                            modifier = Modifier.fillMaxWidth()
                                .bringIntoViewRequester(preferredFoodRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) coroutineScope.launch {
                                        delay(180)
                                        preferredFoodRequester.bringIntoView()
                                    }
                                }
                        )
                    }
                }
            }
            item { SectionHeader(title = "피하고 싶은 음식·재료", supportingText = "등록한 음식명이나 재료와 일치하는 추천을 제외해요.") }
            item {
                WellnessCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExclusionInput("음식명 또는 재료", state.dislikeInput, onDislikeInput, onAddDislike)
                        if (state.broadDislikeWarning) {
                            Text("이 단어를 포함한 많은 메뉴가 추천에서 제외될 수 있어요. 원하는 경우 그대로 추가할 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.excludedFoods.filter { it.exclusionType != "ALLERGY" }.forEach { food ->
                                InputChip(selected = true, onClick = { onRemoveExcluded(food) },
                                    label = { Text(food.normalizedFoodName) },
                                    trailingIcon = { Icon(Icons.Rounded.Delete, contentDescription = "${food.normalizedFoodName} 삭제", Modifier.size(18.dp)) },
                                    modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                        Text("등록·삭제한 내용은 다음 추천부터 반영해요. 원재료 정보가 없는 메뉴는 재료가 없다고 단정하지 않아요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) } }
            item {
                Button(onClick = onSave, enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("preference-settings-save")) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Rounded.RestaurantMenu, contentDescription = null); Text(" 추천 설정 저장") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("기록과 즐겨찾기, 직접 누른 메뉴 교체를 이 기기에서만 약하게 반영해요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRequestLearningReset,
                        enabled = !state.isSaving && !state.isResettingLearning,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("recommendation-learning-reset")) {
                        Text(if (state.isResettingLearning) "추천 학습 초기화 중…" else "추천 학습 초기화")
                    }
                }
            }
            item {
                TextButton(onClick = { detailedSettingsExpanded = !detailedSettingsExpanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("하루 식사 구성", modifier = Modifier.weight(1f))
                    Icon(if (detailedSettingsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (detailedSettingsExpanded) "상세 조건 접기" else "상세 조건 펼치기")
                }
            }
            if (detailedSettingsExpanded) {
                item { SectionHeader(title = "식사 배분", supportingText = "앱 기본 배분은 25/35/30/10이며 의료 기준이 아닙니다.") }
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
                    supportingText = "식사 방식과 예산을 자세히 조정할 수 있어요."
                )
            }
            item {
                WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("식단 성향", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("GENERAL" to "일반", "VEGETARIAN" to "채식").forEach { (value, label) ->
                                PreferenceOptionChip(preference.dietType == value, { onDietType(value) }, label)
                            }
                        }
                        Text("식사 방식", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ANY" to "모두", "COOK" to "조리", "DINING_OUT" to "외식", "CONVENIENCE" to "편의점").forEach { (value, label) ->
                                PreferenceOptionChip(preference.cookingMode == value, { onCookingMode(value) }, label)
                            }
                        }
                        Text("예산", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ANY" to "상관없음", "LOW" to "낮음", "MEDIUM" to "보통", "HIGH" to "여유").forEach { (value, label) ->
                                PreferenceOptionChip(preference.budgetLevel == value, { onBudget(value) }, label)
                            }
                        }
                        Text("추천 다양성", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("FAMILIAR" to "익숙한 음식", "BALANCED" to "균형", "VARIED" to "다양하게").forEach { (value, label) ->
                                PreferenceOptionChip(preference.recommendationDiversity == value, { onDiversity(value) }, label)
                            }
                        }
                    }
                }
            }
            }

            item {
                TextButton(onClick = { allergySettingsExpanded = !allergySettingsExpanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("알레르기 원인", modifier = Modifier.weight(1f))
                    Icon(if (allergySettingsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (allergySettingsExpanded) "알레르기 설정 접기" else "알레르기 설정 펼치기")
                }
            }
            if (allergySettingsExpanded) {
                item { SectionHeader(title = "알레르기 주의 설정", supportingText = "메뉴명이 아니라 반응을 일으키는 원재료를 등록해 주세요.") }
                item {
                    WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                FoodAllergenPolicy.supportedCauses.forEach { cause ->
                                    FilterChip(
                                        selected = state.allergyInput == cause,
                                        onClick = { onAllergyInput(cause) },
                                        label = { Text(cause) },
                                        leadingIcon = if (state.allergyInput == cause) {{ Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }} else null,
                                        modifier = Modifier.heightIn(min = 48.dp)
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
private fun PreferenceOptionChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {{ Icon(Icons.Rounded.Check, contentDescription = null,
            modifier = Modifier.size(18.dp)) }} else null,
        modifier = Modifier.heightIn(min = 48.dp)
    )
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
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onAdd() }),
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onAdd, enabled = value.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
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
