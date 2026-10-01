package com.example.healthcare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.healthcare.domain.FoodPreferencePolicy
import com.example.healthcare.ui.viewmodel.MealPreferenceViewModel

@Composable
fun MealTasteSetupScreen(viewModel: MealPreferenceViewModel, onComplete: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    TasteSetupScreen(
        selectedStyles = state.preferredStyles.map { it.name }.toSet(),
        isSaving = state.isSaving,
        onToggle = viewModel::togglePreferredStyle,
        onSkip = {
            viewModel.discardTasteDraft()
            onComplete()
        },
        onSave = { viewModel.saveTaste(onComplete) },
        error = state.error
    )
}

@Composable
fun TasteSetupScreen(
    selectedStyles: Set<String>,
    isSaving: Boolean,
    onToggle: (String) -> Unit,
    onSkip: () -> Unit,
    onSave: () -> Unit,
    error: String? = null
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding()
                .testTag("preference-onboarding"),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("어떤 음식을 더 좋아하세요?", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() })
                    Text("여러 개를 골라도 좋아요. 선택한 취향은 조건에 맞는 추천의 순서에 반영해요.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { PreferredStyleChips(selectedStyles, onToggle, enabled = !isSaving) }
            item {
                Text("피하고 싶은 음식과 자세한 취향은 설정 → 식사 추천 설정에서 언제든 바꿀 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSave, enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("preference-save")) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Text("이 취향으로 시작하기")
                    }
                    TextButton(onClick = onSkip, enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("preference-skip")) {
                        Text("건너뛰기")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreferredStyleChips(
    selectedStyles: Set<String>,
    onToggle: (String) -> Unit,
    enabled: Boolean = true
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FoodPreferencePolicy.styles.forEach { style ->
            val selected = style.name in selectedStyles || style.token in selectedStyles
            FilterChip(
                selected = selected,
                onClick = { onToggle(style.name) },
                enabled = enabled,
                label = { Text(style.label) },
                leadingIcon = if (selected) {{ Icon(Icons.Rounded.Check, contentDescription = null,
                    modifier = Modifier.size(18.dp)) }} else null,
                modifier = Modifier.heightIn(min = 48.dp).testTag("preference-style-${style.name}")
            )
        }
        FilterChip(
            selected = selectedStyles.isEmpty(),
            onClick = { selectedStyles.forEach(onToggle) },
            enabled = enabled,
            label = { Text("특별히 없음") },
            leadingIcon = if (selectedStyles.isEmpty()) {{ Icon(Icons.Rounded.Check, contentDescription = null,
                modifier = Modifier.size(18.dp)) }} else null,
            modifier = Modifier.heightIn(min = 48.dp).testTag("preference-style-NONE")
        )
    }
}
