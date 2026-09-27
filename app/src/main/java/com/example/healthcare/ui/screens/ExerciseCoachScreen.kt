package com.example.healthcare.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.healthcare.domain.ExerciseActivity
import com.example.healthcare.domain.ExerciseCoachCalculator
import com.example.healthcare.ui.components.SectionHeader
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.WellnessTopAppBar
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.theme.WellnessSpacing

private fun ExerciseActivity.icon(): ImageVector = when (this) {
    ExerciseActivity.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
    ExerciseActivity.BRISK_WALK -> Icons.Rounded.Speed
    ExerciseActivity.LIGHT_JOG -> Icons.AutoMirrored.Rounded.DirectionsRun
    ExerciseActivity.BICYCLE -> Icons.AutoMirrored.Rounded.DirectionsBike
    ExerciseActivity.STAIRS -> Icons.Rounded.Stairs
    ExerciseActivity.HOME_EXERCISE -> Icons.Rounded.FitnessCenter
}

@Composable
internal fun ExerciseCoachScreen(
    excessCalories: Int,
    weightKg: Double?,
    onSaveWeight: (Double) -> Unit,
    onBack: () -> Unit
) {
    var weightInput by remember(weightKg) { mutableStateOf(weightKg?.toString().orEmpty()) }
    var weightError by remember { mutableStateOf(false) }
    var selectedActivity by remember { mutableStateOf<ExerciseActivity?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    Scaffold(
        topBar = {
            WellnessTopAppBar("오늘의 움직임", navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로가기")
                }
            })
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(
                start = WellnessSpacing.ScreenHorizontal, end = WellnessSpacing.ScreenHorizontal,
                top = WellnessSpacing.Compact, bottom = WellnessSpacing.Section
            ),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            item {
                SectionHeader("활동으로 환산해 보기", supportingText = "운동은 선택사항이며, 오늘 섭취를 반드시 운동으로 상쇄할 필요는 없어요.")
            }
            if (excessCalories > 0) {
                item {
                    WellnessCard(
                        Modifier.fillMaxWidth().testTag("exercise-excess-summary"),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("오늘 초과분", style = MaterialTheme.typography.labelLarge)
                            Text("약 $excessCalories kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("목표 초과는 유지 칼로리 초과와 다를 수 있어요.", style = MaterialTheme.typography.bodySmall)
                            Text("활동으로 환산한 단순 예상치예요.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                WellnessCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("현재 몸무게", style = MaterialTheme.typography.titleMedium)
                        Text("운동 소모량을 대략 계산하는 데만 사용해요.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it; weightError = false },
                            label = { Text("몸무게") },
                            suffix = { Text("kg") },
                            isError = weightError,
                            supportingText = if (weightError) { { Text("20~300 kg 사이의 숫자를 입력해 주세요.") } } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = {
                            val parsed = ExerciseCoachCalculator.validateWeight(weightInput)
                            if (parsed == null) weightError = true else {
                                onSaveWeight(parsed)
                                keyboard?.hide()
                            }
                        }) { Text("몸무게 저장") }
                    }
                }
            }
            item {
                ExerciseExamplesCard(
                    title = "100 kcal 활동 예시",
                    targetCalories = 100,
                    weightKg = weightKg,
                    selectedActivity = selectedActivity,
                    onSelectActivity = { selectedActivity = it },
                    testTag = "exercise-100-section"
                )
            }
            if (excessCalories > 0) item {
                ExerciseExamplesCard(
                    title = "오늘 초과분 활동 예시",
                    targetCalories = excessCalories,
                    weightKg = weightKg,
                    selectedActivity = selectedActivity,
                    onSelectActivity = { selectedActivity = it },
                    testTag = "exercise-excess-section"
                )
            }
            item {
                Text("계산은 2024 성인 신체활동 Compendium의 대표 강도값에서 휴식 시 소모량을 뺀 추정입니다. 실제 소모량은 개인의 체력, 속도, 환경에 따라 달라지며 의료 조언이 아니에요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExerciseExamplesCard(
    title: String,
    targetCalories: Int,
    weightKg: Double?,
    selectedActivity: ExerciseActivity?,
    onSelectActivity: (ExerciseActivity) -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(
            title,
            supportingText = if (weightKg == null) {
                "운동 시간을 계산하려면 신체정보에서 몸무게를 입력해 주세요."
            } else {
                "현재 몸무게 기준의 대략적인 참고값이에요."
            }
        )
        WellnessCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = WellnessSpacing.CardContent)) {
                ExerciseActivity.entries.forEachIndexed { index, activity ->
                    val estimate = weightKg?.let { ExerciseCoachCalculator.estimate(targetCalories, it, activity) }
                    val selected = selectedActivity == activity
                    Column(
                        Modifier.fillMaxWidth()
                            .testTag("$testTag-${activity.name}")
                            .clickable { onSelectActivity(activity) }
                            .semantics { this.selected = selected }
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(activity.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column(Modifier.weight(1f)) {
                                Text(activity.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(activity.intensity, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                if (estimate == null) "몸무게 필요"
                                else "약 ${estimate.minutes}분\n예상 ${estimate.estimatedKcal} kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (selected) {
                            Text(activity.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "계산 참고: ${activity.met} MET (표준 강도)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (index < ExerciseActivity.entries.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Preview(name = "운동 코치 360", widthDp = 360, heightDp = 800)
@Composable
private fun ExerciseCoachPreview() {
    HealthCareTheme { ExerciseCoachScreen(150, 70.0, {}, {}) }
}
