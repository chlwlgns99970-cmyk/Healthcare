package com.example.healthcare.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.ui.components.SectionHeader
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.MacroSummaryRow
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.ui.components.WellnessTopAppBar
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.theme.WellnessSpacing
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import com.example.healthcare.ui.viewmodel.CompanionFoodEntry
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun WellnessManualRecordScreen(
    uiState: AddRecordUiState,
    frequentFoods: List<FrequentFood>,
    favoriteFoods: List<FrequentFood>,
    recentMeals: List<MealRecord>,
    photoPath: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onPhotoRecord: () -> Unit,
    viewModel: AddRecordViewModel?,
    guided: Boolean = viewModel != null
) {
    val query = uiState.foodSearch.trim()
    val filteredFoods = frequentFoods.filter {
        query.isBlank() || it.foodName.contains(query, ignoreCase = true)
    }
    var step by rememberSaveable { mutableIntStateOf(if (uiState.selectedFood != null) 1 else 0) }
    var showAll by rememberSaveable { mutableStateOf(!guided) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = KeyboardActions(onDone = { keyboardController?.hide() })
    BackHandler(enabled = !showAll && step > 0) { step -= 1 }
    LaunchedEffect(uiState.nameError) {
        if (uiState.nameError != null && !showAll) step = 0
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WellnessTopAppBar(
                title = if (photoPath == null) "기록 추가" else "사진으로 기록",
                navigationIcon = {
                    IconButton(onClick = {
                        if (!showAll && step > 0) step -= 1 else onBack()
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로 가기")
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (showAll) "한 화면에서 자세히 입력" else "${step + 1} / 4  ${listOf("음식", "먹은 양", "식사 시간", "기록 확인")[step]}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary)
                    Text(if (showAll) "내 식사를 자세히 기록해요" else when (step) {
                        0 -> "무엇을 먹었나요?"
                        1 -> "얼마나 먹었나요?"
                        2 -> "언제 먹었나요?"
                        else -> "이대로 기록할까요?"
                    }, style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "단계별로 입력하기" else "한 화면에서 자세히 입력")
                    }
                }
            }
            if (showAll || step == 0 || photoPath != null) item {
                if (uiState.pendingProductBarcode != null) {
                    BarcodeRegistrationCard(uiState.pendingProductBarcode)
                } else if (photoPath == null) {
                    PhotoEntryCard(onPhotoRecord)
                } else {
                    PhotoReferenceCard(photoPath)
                }
            }
            if (showAll || step == 0) {
            item {
                SectionHeader(
                    title = "음식",
                    supportingText = "저장된 음식을 선택하거나 직접 입력하세요.",
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            item {
                FormSectionCard {
                    if (recentMeals.isNotEmpty()) {
                        ChoiceSection("최근 음식", Icons.Rounded.History) {
                            items(recentMeals, key = { "recent-${it.id}-${it.foodName}" }) { meal ->
                                FoodSuggestionChip("${meal.foodName} · ${meal.portionDisplayLabel ?: "이전과 같은 양"} · ${meal.calories} kcal") {
                                    viewModel?.selectRecentMeal(meal)
                                }
                            }
                        }
                    }
                    if (favoriteFoods.isNotEmpty()) {
                        ChoiceSection("즐겨찾기", Icons.Rounded.Star) {
                            items(favoriteFoods, key = { "favorite-${it.id}" }) { food ->
                                FoodSuggestionChip("${food.foodName} · ${food.calories} kcal") {
                                    viewModel?.selectFrequentFood(food)
                                }
                            }
                        }
                    }
                    if (frequentFoods.isNotEmpty()) {
                        OutlinedTextField(
                            value = uiState.foodSearch,
                            onValueChange = { viewModel?.onFoodSearchChange(it) },
                            label = { Text("저장된 음식 검색") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = if (uiState.foodSearch.isNotBlank()) {
                                {
                                    IconButton(onClick = { viewModel?.onFoodSearchChange("") }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "검색어 지우기")
                                    }
                                }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (filteredFoods.isEmpty()) {
                            Text(
                                "검색 결과가 없어요. 아래에서 직접 입력할 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filteredFoods, key = { "saved-${it.id}" }) { food ->
                                    FoodSuggestionChip("${food.foodName} · ${food.calories} kcal") {
                                        viewModel?.selectFrequentFood(food)
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = uiState.foodName,
                        onValueChange = { viewModel?.onFoodNameChange(it) },
                        label = { Text("음식 이름") },
                        placeholder = { Text("예: 현미밥과 닭가슴살") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = dismissKeyboard,
                        shape = MaterialTheme.shapes.medium,
                        isError = uiState.nameError != null,
                        supportingText = uiState.nameError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            }
            if (showAll || step == 1) {
            item {
                PortionSelector(
                    state = uiState,
                    onPreset = { viewModel?.selectPortionPreset(it) },
                    onReferenceRatio = { ratio, label -> viewModel?.selectReferencePortion(ratio, label) },
                    onUnknown = { viewModel?.startUnknownPortion() },
                    onVessel = { viewModel?.choosePortionVessel(it) },
                    onFraction = { viewModel?.choosePortionFraction(it) },
                    onPrecise = { viewModel?.useCustomServing() }
                )
            }
            if (uiState.preciseAmountOpen || uiState.pendingProductBarcode != null) {
                item {
                    val directFood = uiState.selectedFood
                        ?.takeIf(PortionGuide::requiresDirectAmount)
                    FormSectionCard {
                        if (directFood != null) {
                            val amount = uiState.servingAmount.trim().replace(',', '.').toDoubleOrNull()
                                ?.takeIf { it.isFinite() && it > 0.0 && uiState.calories.isNotBlank() }
                            val invalidAmount = uiState.servingAmount.isNotBlank() && amount == null
                            Text("실제 먹은 양", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${PortionGuide.resultServingSummary(directFood)}을 바탕으로 같은 ${directFood.unit} 단위에서 비례 계산해요.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = uiState.servingAmount,
                                onValueChange = { viewModel?.onServingAmountChange(it) },
                                label = { Text("먹은 양") },
                                placeholder = { Text("예: 250") },
                                suffix = { Text(directFood.unit) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = dismissKeyboard,
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                isError = invalidAmount,
                                supportingText = if (invalidAmount) {
                                    { Text("0보다 큰 숫자를 입력해 주세요.") }
                                } else {
                                    { Text("g과 ml는 서로 바꾸지 않고 원래 단위 그대로 계산합니다.") }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("direct-amount-input")
                            )
                            amount?.let {
                                Text("약 ${uiState.calories} kcal", style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                MacroSummaryRow(Macronutrients.forFood(directFood, it))
                            }
                        } else {
                            Text("직접 양 입력", style = MaterialTheme.typography.titleMedium)
                            Text("무게나 용량을 아는 경우에만 입력해 주세요.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = uiState.servingAmount,
                                    onValueChange = { viewModel?.onServingAmountChange(it) },
                                    label = { Text("양") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                    keyboardActions = dismissKeyboard,
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = uiState.servingUnit,
                                    onValueChange = { viewModel?.onServingUnitChange(it) },
                                    label = { Text("단위") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = dismissKeyboard,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            if (uiState.selectedFood != null) {
                item {
                    CompanionFoodsCard(
                        state = uiState,
                        onAdd = { viewModel?.showCompanionSearch(it) },
                        onPortionSelected = { id, portion -> viewModel?.updateCompanionPortion(id, portion) },
                        onRemove = { viewModel?.removeCompanionFood(it) }
                    )
                }
            }
            }
            if (showAll || step == 3) {
            if (!showAll) item {
                WellnessCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("오늘 먹은 음식", style = MaterialTheme.typography.titleLarge)
                        RecordReceiptRow(
                            name = uiState.foodName.ifBlank { "음식 이름을 입력해 주세요" },
                            portion = uiState.selectedPortion?.label,
                            calories = uiState.calories.toDoubleOrNull()?.roundToInt()
                        )
                        uiState.companionFoods.forEach { entry ->
                            RecordReceiptRow(
                                name = FoodSearchPolicy.displayName(entry.food),
                                portion = entry.portion.label,
                                calories = entry.calories
                            )
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("총 예상", style = MaterialTheme.typography.titleMedium)
                            Text("${estimatedTotalCalories(uiState)} kcal",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        MacroSummaryRow(estimatedNutrition(uiState))
                        Text("${uiState.mealType.displayName} · ${uiState.date} ${uiState.time}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("실제 음식 크기와 양에 따라 열량이 달라질 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionHeader("칼로리", modifier = Modifier.padding(top = 10.dp)) }
            item {
                WellnessCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (uiState.companionFoods.isEmpty()) "최종 섭취 칼로리" else "기본 음식 칼로리",
                            style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = uiState.calories,
                            onValueChange = { viewModel?.onCaloriesChange(it) },
                            label = { Text("칼로리") },
                            suffix = { Text("kcal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = dismissKeyboard,
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            isError = uiState.caloriesError != null,
                            supportingText = uiState.caloriesError?.let { { Text(it) } },
                            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (uiState.selectedPortion != null) "선택한 양을 바탕으로 한 예상값이에요. 실제 음식과 양에 따라 달라질 수 있고 직접 수정할 수 있어요."
                            else "영양정보 기준으로 계산된 값이며 직접 수정할 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.companionFoods.isNotEmpty()) {
                            WellnessCard(containerColor = MaterialTheme.colorScheme.surface) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("모든 음식 총 예상", style = MaterialTheme.typography.titleMedium)
                                    Text("${estimatedTotalCalories(uiState)} kcal",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            }
            if (showAll || step == 2) {
            item { SectionHeader("식사", modifier = Modifier.padding(top = 10.dp)) }
            item {
                FormSectionCard {
                    WellnessMealTypeSelector(uiState.mealType) { viewModel?.onMealTypeChange(it) }
                    WellnessDateTimeSelectors(
                        date = uiState.date,
                        time = uiState.time,
                        onDateChange = { viewModel?.onDateChange(it) },
                        onTimeChange = { viewModel?.onTimeChange(it) }
                    )
                }
            }
            }
            if (showAll || step == 3) {
            item { SectionHeader("메모", modifier = Modifier.padding(top = 10.dp)) }
            item {
                FormSectionCard {
                    OutlinedTextField(
                        value = uiState.memo,
                        onValueChange = { viewModel?.onMemoChange(it) },
                        label = { Text("메모 (선택)") },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = dismissKeyboard,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().testTag("manual-record-memo")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.saveAsFrequent,
                            onCheckedChange = { viewModel?.onSaveAsFrequentChange(it) }
                        )
                        Column {
                            Text("내 음식으로 저장", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "다음 기록에서 빠르게 선택할 수 있어요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            uiState.saveError?.let { error ->
                item {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            item {
                Button(
                    onClick = { viewModel?.saveRecord(onSaved) },
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("manual-record-save"),
                    enabled = !uiState.isSaving,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (uiState.pendingProductBarcode != null) "상품 정보와 기록 저장" else "기록 저장",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            }
            if (!showAll && step < 3) item {
                Button(
                    onClick = { step += 1 },
                    enabled = step != 0 || uiState.foodName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(when (step) {
                        0 -> "다음 · 먹은 양"
                        1 -> "다음 · 식사 시간"
                        else -> "기록 확인"
                    })
                }
            }
        }
    }
}

@Composable
private fun RecordReceiptRow(name: String, portion: String?, calories: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            portion?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            calories?.let { "$it kcal" } ?: "확인 필요",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun CompanionFoodsCard(
    state: AddRecordUiState,
    onAdd: (String) -> Unit,
    onPortionSelected: (String, com.example.healthcare.domain.PortionPreset) -> Unit,
    onRemove: (String) -> Unit
) {
    FormSectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("같이 먹은 음식", style = MaterialTheme.typography.titleMedium)
                Text("각 음식의 칼로리를 따로 계산해 합산해요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onAdd("") }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "같이 먹은 음식 추가")
            }
        }
        val quickQueries = FoodSearchPolicy.quickCompanionQueries(state.selectedFood)
        if (quickQueries.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickQueries, key = { it.second }) { (label, query) ->
                    SuggestionChip(onClick = { onAdd(query) }, label = { Text("+ $label") })
                }
            }
        }
        state.companionFoods.forEach { entry ->
            CompanionFoodRow(entry, onPortionSelected, onRemove)
        }
        OutlinedButton(onClick = { onAdd("") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text("다른 음식 검색", modifier = Modifier.padding(start = 8.dp))
        }
        if (state.companionFoods.isNotEmpty()) {
            Text("함께 추가한 음식 ${state.companionFoods.sumOf { it.calories }} kcal",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CompanionFoodRow(
    entry: CompanionFoodEntry,
    onPortionSelected: (String, com.example.healthcare.domain.PortionPreset) -> Unit,
    onRemove: (String) -> Unit
) {
    WellnessCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(FoodSearchPolicy.displayName(entry.food), style = MaterialTheme.typography.titleSmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    entry.food.brand?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("${entry.calories} kcal", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onRemove(entry.selectionId) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "${FoodSearchPolicy.displayName(entry.food)} 삭제")
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(PortionGuide.presets(entry.food), key = { it.id }) { preset ->
                    FilterChip(
                        selected = entry.portion.id == preset.id,
                        onClick = { onPortionSelected(entry.selectionId, preset) },
                        label = { Text(preset.label) }
                    )
                }
            }
            Text("근거: ${entry.portion.sourceReference}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun estimatedTotalCalories(state: AddRecordUiState): Int =
    (state.calories.toDoubleOrNull()?.roundToInt() ?: 0) + state.companionFoods.sumOf { it.calories }

private fun estimatedNutrition(state: AddRecordUiState): Macronutrients {
    val base = state.selectedFood?.let { food ->
        val amount = state.servingAmount.trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 && state.servingUnit.equals(food.unit, ignoreCase = true) }
        val calories = state.calories.toDoubleOrNull()
        if (amount != null) {
            Macronutrients.forFood(food, amount)
        } else if (calories != null && food.energyKcal > 0.0) {
            Macronutrients(food.carbohydrateGrams, food.proteinGrams, food.fatGrams)
                .scaled(calories / food.energyKcal)
        } else Macronutrients.Unknown
    } ?: Macronutrients(
        state.estimatedCarbohydrateGrams,
        state.estimatedProteinGrams,
        state.estimatedFatGrams
    )
    return Macronutrients.strictSum(
        listOf(base) + state.companionFoods.map { Macronutrients.forFood(it.food, it.portion.amount) }
    )
}

@Composable
private fun BarcodeRegistrationCard(barcode: String) {
    WellnessCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("바코드 상품 직접 등록", style = MaterialTheme.typography.titleMedium)
                Text("코드 $barcode", style = MaterialTheme.typography.bodySmall)
                Text(
                    "음식 이름, 영양정보에 표시된 양, 단위와 열량을 확인해 저장하면 다음 스캔부터 로컬 상품 정보로 사용합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
internal fun WellnessPhotoPreviewScreen(
    photoPath: String,
    onUsePhoto: () -> Unit,
    onAnalyze: () -> Unit,
    analysisAvailable: Boolean,
    onRetake: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WellnessTopAppBar(
                title = "사진 확인",
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "사진 사용 취소")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(
                start = WellnessSpacing.ScreenHorizontal,
                end = WellnessSpacing.ScreenHorizontal,
                bottom = WellnessSpacing.ScreenVertical
            ),
            verticalArrangement = Arrangement.spacedBy(WellnessSpacing.CardGap)
        ) {
            if (LocalInspectionMode.current) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(44.dp))
                        Text("촬영한 음식 사진", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = "촬영한 음식 사진 미리보기",
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Fit
                )
            }
            WellnessCard(containerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("사진을 보며 한 끼 기록하기", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (analysisAvailable) {
                            "사진 분석을 선택하면 압축한 사진을 설정된 서버로 전송해요. 결과는 저장 전에 확인할 수 있어요."
                        } else {
                            "사진을 참고해 음식과 먹은 양을 직접 선택해 주세요. 사진은 기록을 마치면 삭제돼요."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onUsePhoto, modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = MaterialTheme.shapes.medium) { Text("이 사진 사용") }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Spacer(Modifier.size(7.dp))
                        Text("다시 촬영")
                    }
                    if (analysisAvailable) {
                        androidx.compose.material3.TextButton(onClick = onAnalyze) { Text("음식 분석 선택") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoEntryCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("사진으로 기록", style = MaterialTheme.typography.titleMedium)
                Text(
                    "사진을 보면서 음식 정보를 직접 입력해요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Icon(Icons.Rounded.CameraAlt, contentDescription = "카메라 열기")
        }
    }
}

@Composable
private fun PhotoReferenceCard(photoPath: String) {
    WellnessCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (LocalInspectionMode.current) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(40.dp))
                        Text("촬영한 음식 사진", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = "기록 작성에 참고할 음식 사진",
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop
                )
            }
            Text("사진을 보면서 음식 이름과 섭취량을 입력해 주세요.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FormSectionCard(content: @Composable ColumnScope.() -> Unit) {
    WellnessCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun ChoiceSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.labelLarge)
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun FoodSuggestionChip(label: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp)
            )
        }
    )
}

@Composable
internal fun WellnessMealTypeSelector(selected: MealType, onChange: (MealType) -> Unit) {
    Text("식사 유형", style = MaterialTheme.typography.labelLarge)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MealType.entries) { type ->
            val isSelected = selected == type
            FilterChip(
                selected = isSelected,
                onClick = { onChange(type) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                label = { Text(type.displayName) }
            )
        }
    }
}

@Composable
internal fun WellnessDateTimeSelectors(
    date: LocalDate,
    time: LocalTime,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateTimePickerCard(
            label = "날짜",
            value = date.format(dateFormatter),
            icon = Icons.Rounded.CalendarMonth,
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onDateChange(LocalDate.of(year, month + 1, day)) },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth
                ).show()
            },
            modifier = Modifier.weight(1f)
        )
        DateTimePickerCard(
            label = "시간",
            value = time.format(timeFormatter),
            icon = Icons.Rounded.Schedule,
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onTimeChange(LocalTime.of(hour, minute)) },
                    time.hour,
                    time.minute,
                    true
                ).show()
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DateTimePickerCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

private fun formatRatio(ratio: Double): String =
    if (ratio % 1.0 == 0.0) ratio.toInt().toString() else ratio.toString()

private val previewRecentMeals = listOf(
    MealRecord(
        date = "2026-09-13",
        time = "08:10",
        mealType = MealType.BREAKFAST,
        foodName = "그릭 요거트와 제철 과일",
        calories = 320
    )
)

private val previewFrequentFoods = listOf(
    FrequentFood(id = 1, foodName = "현미밥과 닭가슴살 샐러드", defaultServing = "1인분", calories = 510, isFavorite = true),
    FrequentFood(id = 2, foodName = "바나나", defaultServing = "1개", calories = 105)
)

@Preview(name = "기록 추가 360", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ManualRecordPreview() {
    HealthCareTheme {
        WellnessManualRecordScreen(
            uiState = AddRecordUiState(
                foodName = "현미밥과 닭가슴살 샐러드",
                calories = "510",
                servingAmount = "1",
                servingUnit = "인분",
                referenceCalories = 510,
                selectedServingRatio = 1.0
            ),
            frequentFoods = previewFrequentFoods,
            favoriteFoods = previewFrequentFoods.take(1),
            recentMeals = previewRecentMeals,
            photoPath = null,
            onBack = {},
            onSaved = {},
            onPhotoRecord = {},
            viewModel = null
        )
    }
}

@Preview(name = "사진 참고 기록 390", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun PhotoManualRecordPreview() {
    HealthCareTheme {
        WellnessManualRecordScreen(
            uiState = AddRecordUiState(),
            frequentFoods = emptyList(),
            favoriteFoods = emptyList(),
            recentMeals = emptyList(),
            photoPath = "preview.jpg",
            onBack = {},
            onSaved = {},
            onPhotoRecord = {},
            viewModel = null
        )
    }
}

@Preview(name = "사진 미리보기 412", showBackground = true, widthDp = 412, heightDp = 820)
@Composable
private fun PhotoPreview() {
    HealthCareTheme { WellnessPhotoPreviewScreen("preview.jpg", {}, {}, true, {}, {}) }
}

@Preview(name = "단계형 기록 360", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun GuidedManualRecordPreview() {
    HealthCareTheme {
        WellnessManualRecordScreen(
            uiState = AddRecordUiState(foodName = "김밥", calories = "280"),
            frequentFoods = emptyList(), favoriteFoods = emptyList(), recentMeals = emptyList(),
            photoPath = null, onBack = {}, onSaved = {}, onPhotoRecord = {},
            viewModel = null, guided = true
        )
    }
}
