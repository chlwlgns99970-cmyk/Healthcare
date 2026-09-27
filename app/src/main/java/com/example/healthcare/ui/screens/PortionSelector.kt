package com.example.healthcare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.healthcare.domain.PortionFraction
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.PortionPreset
import com.example.healthcare.domain.PortionVessel
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.MacroSummaryRow
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import kotlin.math.roundToInt

@Composable
internal fun PortionSelector(
    state: AddRecordUiState,
    onPreset: (PortionPreset) -> Unit,
    onReferenceRatio: (Double, String) -> Unit,
    onUnknown: () -> Unit,
    onVessel: (PortionVessel) -> Unit,
    onFraction: (PortionFraction) -> Unit,
    onPrecise: () -> Unit
) {
    val presets = state.selectedFood?.let(PortionGuide::presets).orEmpty()
    val requiresDirectAmount = state.selectedFood?.let(PortionGuide::requiresDirectAmount) == true
    WellnessCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("02 / 얼마나 먹었나요?", style = MaterialTheme.typography.titleLarge)
            Text("대략 골라도 괜찮아요. 평소 사용하는 그릇이나 개수로 선택해 주세요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (requiresDirectAmount) {
                val food = requireNotNull(state.selectedFood)
                Text(
                    "확인된 공기·그릇·포장 단위가 없어 실제 먹은 양을 ${food.unit}로 직접 입력해요.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${food.servingDescription}은 영양정보 계산 기준이며 기본 1인분이 아니에요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (presets.isNotEmpty()) {
                presets.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { preset ->
                            PortionChoiceCard(
                                label = preset.label,
                                selected = state.selectedPortion?.id == preset.id,
                                onClick = { onPreset(preset) },
                                modifier = Modifier.weight(1f),
                                supporting = state.selectedFood?.let { food -> PortionGuide.presetSupportingText(food, preset) }
                            )
                        }
                        if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
                if (presets.all { it.estimationType == com.example.healthcare.domain.PortionEstimationType.VISUAL_ESTIMATE }) {
                    Text("이 음식은 공기·그릇 환산 자료가 없어 영양정보에 표시된 양과 비교해요. 1배는 일반적인 1인분을 뜻하지 않아요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            } else if (state.referenceCalories != null) {
                listOf(0.5 to "조금", 1.0 to "보통", 1.5 to "많이").chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (ratio, label) ->
                            PortionChoiceCard(
                                label = label,
                                selected = state.selectedPortion?.id == "reference-$ratio",
                                onClick = { onReferenceRatio(ratio, label) },
                                modifier = Modifier.weight(1f),
                                supporting = state.referenceCalories?.let { "선택 시 ${(it * ratio).roundToInt()} kcal" }
                            )
                        }
                        if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
                Text("저장된 영양정보에 표시된 양을 바탕으로 한 대략적인 선택이에요. 일반적인 1인분을 뜻하지 않아요.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("음식을 선택하면 알맞은 분량을 보여드려요. 직접 입력한 음식은 칼로리를 확인해 주세요.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (state.referenceCalories != null && !requiresDirectAmount) {
                OutlinedButton(onClick = onUnknown, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("잘 모르겠어요")
                }
            }
            if (state.portionHelpOpen) {
                Text("어떤 그릇에 담겨 있었나요?", style = MaterialTheme.typography.titleLarge)
                PortionVessel.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { vessel ->
                            PortionChoiceCard(
                                label = vessel.label,
                                selected = state.selectedVessel == vessel,
                                onClick = { onVessel(vessel) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
                if (state.selectedVessel != null) {
                    Text("그중 얼마나 먹었나요?", style = MaterialTheme.typography.titleLarge)
                    PortionFraction.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { fraction ->
                                PortionChoiceCard(
                                    label = fraction.label,
                                    selected = state.selectedPortion?.id == "visual-${state.selectedVessel.name}-${fraction.name}",
                                    onClick = { onFraction(fraction) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Text("그릇 크기와 담긴 양을 재지 않았으므로 계산 결과는 매우 대략적이에요.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            state.selectedPortion?.let { portion ->
                WellnessCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(portion.label, style = MaterialTheme.typography.titleMedium)
                        Text("약 ${state.calories} kcal", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        state.selectedFood?.let { food ->
                            MacroSummaryRow(Macronutrients.forFood(food, portion.amount))
                        }
                        Text(portion.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!requiresDirectAmount) {
                TextButton(onClick = onPrecise, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                    Text("더 정확히 입력하기")
                }
            }
        }
    }
}

@Composable
private fun PortionChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 102.dp).testTag("portion-$label").semantics { this.selected = selected },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.Rounded.RestaurantMenu, contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary)
                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "선택됨",
                    tint = MaterialTheme.colorScheme.primary)
            }
            Text(label, style = MaterialTheme.typography.titleSmall)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
