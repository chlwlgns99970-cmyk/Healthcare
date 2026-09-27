package com.example.healthcare.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.healthcare.domain.BodySex
import com.example.healthcare.R
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.BodyProfileUiState
import java.text.NumberFormat

@Composable
fun BodyProfileSetupScreen(
    state: BodyProfileUiState,
    onSexSelected: (BodySex) -> Unit,
    onAgeChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    Image(
                        bitmap = ImageBitmap.imageResource(R.drawable.onboarding_hero_fixed),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize().testTag("onboarding-fixed-hero")
                    )
                    Column(
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "건강한\n변화를 함께\n시작해요",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            "정확한 맞춤 관리를 위해\n몇 가지 정보를 알려주세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text("성별", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.width(54.dp))
                            FilterChip(
                                selected = state.sex == BodySex.MALE,
                                onClick = { onSexSelected(BodySex.MALE) },
                                label = { Text("남성") },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            )
                            FilterChip(
                                selected = state.sex == BodySex.FEMALE,
                                onClick = { onSexSelected(BodySex.FEMALE) },
                                label = { Text("여성") },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            )
                        }
                        state.sexError?.let { SetupValidationText(it) }
                        ProfileInputRow(
                            icon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = "나이",
                            value = state.ageInput,
                            onValueChange = onAgeChange,
                            unit = "세",
                            error = state.ageError,
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                        ProfileInputRow(
                            icon = { Icon(Icons.Rounded.Straighten, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = "키",
                            value = state.heightInput,
                            onValueChange = onHeightChange,
                            unit = "cm",
                            error = state.heightError,
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        )
                        ProfileInputRow(
                            icon = { Icon(Icons.Rounded.MonitorWeight, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            label = "몸무게",
                            value = state.weightInput,
                            onValueChange = onWeightChange,
                            unit = "kg",
                            error = state.weightError,
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        state.estimatedBmrKcal?.let { bmr ->
                            Text(
                                "예상 기초대사량 · ${NumberFormat.getIntegerInstance().format(bmr)} kcal/일",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        state.saveError?.let { SetupValidationText(it) }
                        Button(
                            onClick = onSave,
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            } else {
                                Text("시작하기")
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                            }
                        }
                    }
            }
            item {
                Text(
                    "입력한 정보는 기기에만 저장되며 설정의 ‘내 신체정보’에서 언제든 수정할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileInputRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    error: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(54.dp))
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                suffix = { Text(unit) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = keyboardActions,
                shape = MaterialTheme.shapes.medium,
                colors = setupFieldColors()
            )
        }
        error?.let { SetupValidationText(it) }
    }
}

@Composable
private fun setupFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
)

@Composable
private fun SetupValidationText(message: String) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Preview(name = "최초 신체정보 · 360 큰 글자", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun BodyProfileSetupPreview() {
    HealthCareTheme(appFontScale = 1.15f) {
        BodyProfileSetupScreen(
            state = BodyProfileUiState(
                sex = BodySex.FEMALE,
                ageInput = "35",
                heightInput = "165.5",
                weightInput = "58.2",
                estimatedBmrKcal = 1_302
            ),
            onSexSelected = {},
            onAgeChange = {},
            onHeightChange = {},
            onWeightChange = {},
            onSave = {}
        )
    }
}
