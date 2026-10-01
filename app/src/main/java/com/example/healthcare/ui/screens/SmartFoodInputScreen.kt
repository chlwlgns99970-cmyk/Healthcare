package com.example.healthcare.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.R
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.NutritionBasisCandidate
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.FoodBrowseCategory
import com.example.healthcare.domain.FranchiseCatalog
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.ui.components.SectionHeader
import com.example.healthcare.ui.components.WellnessCard
import com.example.healthcare.ui.components.WellnessEmptyState
import com.example.healthcare.ui.components.WellnessTopAppBar
import com.example.healthcare.ui.theme.WellnessSpacing
import com.example.healthcare.ui.viewmodel.FoodSearchMode
import com.example.healthcare.ui.viewmodel.SmartInputMode
import com.example.healthcare.ui.viewmodel.SmartInputUiState
import java.text.NumberFormat

@Composable
internal fun SmartFoodInputScreen(
    state: SmartInputUiState,
    recentMeals: List<MealRecord>,
    dateMeals: List<MealRecord> = emptyList(),
    onBack: () -> Unit,
    onPhoto: () -> Unit,
    onBarcode: () -> Unit,
    onNutritionLabel: () -> Unit,
    onSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFoodSelected: (FoodItem) -> Unit,
    onUseBarcodeItem: () -> Unit,
    onOcrCandidateSelected: (Int) -> Unit,
    onOcrAmountChange: (String) -> Unit,
    onOcrPortionSelected: (Double, String) -> Unit = { _, _ -> },
    onOcrPrecise: () -> Unit = {},
    onOcrUnknown: () -> Unit = {},
    onConfirmOcr: () -> Unit,
    onManual: () -> Unit,
    onRegisterBarcode: () -> Unit,
    onRepeatRecent: (MealRecord) -> Unit,
    onSearchModeSelected: (FoodSearchMode) -> Unit = {},
    onBrandSelected: (FoodBrandSummary) -> Unit = {},
    onBrandBack: () -> Unit = {},
    onBrandCategorySelected: (String?) -> Unit = {},
    frequentFoods: List<FrequentFood> = emptyList(),
    onFoodCategorySelected: (FoodBrowseCategory) -> Unit = {},
    onFrequentFoodSelected: (FrequentFood) -> Unit = {},
    onFrequentFoodDeleted: (FrequentFood) -> Unit = {},
    favoriteFoods: List<FrequentFood> = emptyList(),
    onFavoriteFoodSelected: (FrequentFood) -> Unit = {},
    onFavoriteToggle: (FoodItem) -> Unit = {},
    onFavoriteRemoved: (FrequentFood) -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            if (state.mode != SmartInputMode.HUB) {
                WellnessTopAppBar(
                    title = when (state.mode) {
                        SmartInputMode.SEARCH -> if (state.isCompanionSearch) "같이 먹은 음식" else "음식 검색"
                        SmartInputMode.QUICK_RECORD -> "빠른 기록"
                        SmartInputMode.BARCODE_LOADING, SmartInputMode.BARCODE_RESULT, SmartInputMode.BARCODE_NOT_FOUND -> "바코드 기록"
                        SmartInputMode.OCR_PROCESSING, SmartInputMode.OCR_RESULT -> "영양성분표 확인"
                        else -> "기록"
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (state.mode) {
            SmartInputMode.HUB -> SmartInputHub(
                state = state,
                recentMeals = recentMeals,
                dateMeals = dateMeals,
                onPhoto = onPhoto,
                onBarcode = onBarcode,
                onNutritionLabel = onNutritionLabel,
                onSearch = onSearch,
                onManual = onManual,
                onOpenHistory = onOpenHistory,
                onRepeatRecent = onRepeatRecent,
                modifier = Modifier.padding(innerPadding)
            )
            SmartInputMode.SEARCH -> FoodSearchContent(
                state = state,
                onQueryChange = onSearchQueryChange,
                onFoodSelected = onFoodSelected,
                onManual = onManual,
                onSearchModeSelected = onSearchModeSelected,
                onBrandSelected = onBrandSelected,
                onBrandBack = onBrandBack,
                onBrandCategorySelected = onBrandCategorySelected,
                frequentFoods = frequentFoods,
                favoriteFoods = favoriteFoods,
                onFoodCategorySelected = onFoodCategorySelected,
                onFrequentFoodSelected = onFrequentFoodSelected,
                onFrequentFoodDeleted = onFrequentFoodDeleted,
                onFavoriteFoodSelected = onFavoriteFoodSelected,
                onFavoriteToggle = onFavoriteToggle,
                onFavoriteRemoved = onFavoriteRemoved,
                modifier = Modifier.padding(innerPadding)
            )
            SmartInputMode.QUICK_RECORD -> LoadingContent("빠른 기록을 준비하고 있어요", Modifier.padding(innerPadding))
            SmartInputMode.BARCODE_LOADING -> LoadingContent("상품 영양정보를 확인하고 있어요", Modifier.padding(innerPadding))
            SmartInputMode.BARCODE_RESULT -> BarcodeResultContent(
                item = state.barcodeItem,
                onUse = onUseBarcodeItem,
                onNutritionLabel = onNutritionLabel,
                modifier = Modifier.padding(innerPadding)
            )
            SmartInputMode.BARCODE_NOT_FOUND -> BarcodeNotFoundContent(
                barcode = state.barcode,
                message = state.message,
                onNutritionLabel = onNutritionLabel,
                onSearch = onSearch,
                onManual = onRegisterBarcode,
                modifier = Modifier.padding(innerPadding)
            )
            SmartInputMode.OCR_PROCESSING -> LoadingContent("영양성분표의 숫자와 단위를 읽고 있어요", Modifier.padding(innerPadding))
            SmartInputMode.OCR_RESULT -> NutritionLabelResultContent(
                state = state,
                onCandidateSelected = onOcrCandidateSelected,
                onAmountChange = onOcrAmountChange,
                onPortionSelected = onOcrPortionSelected,
                onPrecise = onOcrPrecise,
                onUnknown = onOcrUnknown,
                onConfirm = onConfirmOcr,
                onRetake = onNutritionLabel,
                onManual = onManual,
                modifier = Modifier.padding(innerPadding)
            )
            SmartInputMode.MANUAL -> Unit
        }
    }
}

@Composable
private fun SmartInputHub(
    state: SmartInputUiState,
    recentMeals: List<MealRecord>,
    dateMeals: List<MealRecord>,
    onPhoto: () -> Unit,
    onBarcode: () -> Unit,
    onNutritionLabel: () -> Unit,
    onSearch: () -> Unit,
    onManual: () -> Unit,
    onOpenHistory: () -> Unit,
    onRepeatRecent: (MealRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val dense = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f || maxHeight < 600.dp
        val gap = if (dense) 6.dp else 10.dp
        Column(
            modifier = Modifier.fillMaxSize().padding(
                horizontal = WellnessSpacing.ScreenHorizontal,
                vertical = if (dense) 5.dp else WellnessSpacing.Compact
            ),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("어떤 방식으로\n하루를 기록할까요?", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            Row(Modifier.fillMaxWidth().height(if (dense) 142.dp else 164.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                RecordHubTile(
                    icon = Icons.Rounded.Search,
                    title = "음식 검색",
                    supporting = "이름으로 찾기",
                    imageRes = R.drawable.photo_sushi,
                    dense = dense,
                    onClick = onSearch,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                RecordHubTile(
                    icon = Icons.Rounded.QrCodeScanner,
                    title = "바코드 스캔",
                    supporting = "포장식품 바로 기록",
                    imageRes = R.drawable.photo_chicken_sandwich,
                    dense = dense,
                    onClick = onBarcode,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(Modifier.fillMaxWidth().height(if (dense) 142.dp else 164.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                RecordHubTile(
                    icon = Icons.Rounded.ImageSearch,
                    title = "사진으로 기록",
                    supporting = "사진 보며 직접 기록",
                    imageRes = R.drawable.photo_salmon_avocado_salad,
                    dense = dense,
                    onClick = onPhoto,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                RecordHubTile(
                    icon = Icons.Rounded.Edit,
                    title = "직접 입력",
                    supporting = "직접 음식 정보 입력",
                    imageRes = R.drawable.photo_food_journal,
                    dense = dense,
                    onClick = onManual,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                OutlinedButton(onClick = onNutritionLabel, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.TextSnippet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("영양성분표 OCR", maxLines = 1)
                }
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("오늘 기록", maxLines = 1)
                }
            }
            SelectedDateRecordSummary(dateMeals = dateMeals, onOpenHistory = onOpenHistory)
            if (recentMeals.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("최근 음식", style = MaterialTheme.typography.labelLarge)
                    recentMeals.take(3).forEach { meal ->
                        Surface(
                            onClick = { onRepeatRecent(meal) },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Text(meal.foodName, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp))
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SelectedDateRecordSummary(
    dateMeals: List<MealRecord>,
    onOpenHistory: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("오늘의 기록", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onOpenHistory) {
                val total = dateMeals.sumOf { it.calories }
                Text(if (dateMeals.isEmpty()) "기록 없음" else "총 ${NumberFormat.getIntegerInstance().format(total)} kcal")
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        if (dateMeals.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                val representatives = MealType.entries.mapNotNull { type ->
                    dateMeals.firstOrNull { it.mealType == type }
                }.take(4)
                representatives.forEach { meal ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Image(
                            bitmap = ImageBitmap.imageResource(
                                when (meal.mealType) {
                                    MealType.BREAKFAST -> R.drawable.photo_breakfast_yogurt_bowl
                                    MealType.LUNCH -> R.drawable.photo_sandwich
                                    MealType.DINNER -> R.drawable.photo_salmon_avocado_salad
                                    MealType.SNACK, MealType.OTHER -> R.drawable.photo_breakfast_yogurt_bowl
                                }
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(45.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Text(meal.mealType.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordHubTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    supporting: String,
    dense: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageRes: Int? = null,
    featured: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (featured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            if (imageRes != null) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().weight(1f)) {
                    Image(
                        bitmap = ImageBitmap.imageResource(imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(6.dp).size(if (dense) 18.dp else 21.dp)
                        )
                    }
                }
            } else {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(if (dense) 8.dp else 12.dp).size(if (dense) 22.dp else 26.dp))
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = if (dense) 10.dp else 12.dp, vertical = if (dense) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(supporting, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SmartInputAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
    compact: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = if (compact) 152.dp else 94.dp),
        colors = CardDefaults.cardColors(containerColor = when {
            featured -> MaterialTheme.colorScheme.primaryContainer
            compact -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        })
    ) {
        if (compact) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(WellnessSpacing.CardContent),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium,
                    color = if (featured) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(status, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun FoodSearchContent(
    state: SmartInputUiState,
    onQueryChange: (String) -> Unit,
    onFoodSelected: (FoodItem) -> Unit,
    onManual: () -> Unit,
    onSearchModeSelected: (FoodSearchMode) -> Unit,
    onBrandSelected: (FoodBrandSummary) -> Unit,
    onBrandBack: () -> Unit,
    onBrandCategorySelected: (String?) -> Unit,
    frequentFoods: List<FrequentFood>,
    favoriteFoods: List<FrequentFood>,
    onFoodCategorySelected: (FoodBrowseCategory) -> Unit,
    onFrequentFoodSelected: (FrequentFood) -> Unit,
    onFrequentFoodDeleted: (FrequentFood) -> Unit,
    onFavoriteFoodSelected: (FrequentFood) -> Unit,
    onFavoriteToggle: (FoodItem) -> Unit,
    onFavoriteRemoved: (FrequentFood) -> Unit,
    modifier: Modifier = Modifier
) {
    val brandFoods = state.brandProducts.filter { food ->
        state.selectedBrandCategory == null || food.category == state.selectedBrandCategory
    }
    val officialMenuReferences = state.selectedBrand?.let {
        FranchiseCatalog.officialMenuNames(it.brand, state.searchQuery)
    }.orEmpty()
    val groupedResults = FoodSearchPolicy.groupSearchResults(state.searchResults, state.searchQuery)
    val expandedGroups = remember(state.searchQuery, state.searchResults) { mutableStateMapOf<String, Boolean>() }
    val favoriteIds = favoriteFoods.mapNotNull(FrequentFood::foodItemId).toSet()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(WellnessSpacing.ScreenHorizontal, WellnessSpacing.Compact, WellnessSpacing.ScreenHorizontal, WellnessSpacing.Section),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                when {
                    state.isCompanionSearch -> "무엇을 같이 먹었나요?"
                    state.selectedBrand != null -> state.selectedBrand.brand
                    state.searchMode == FoodSearchMode.FRANCHISE -> "프랜차이즈로 찾아요"
                    state.searchMode == FoodSearchMode.BRAND -> "브랜드·제조사로 찾아요"
                    else -> "먹은 음식을 찾아요"
                },
                supportingText = when {
                    state.isCompanionSearch -> "각 음식의 실제 영양정보를 따로 계산해 합산해요."
                    state.selectedBrand != null && state.searchMode == FoodSearchMode.FRANCHISE &&
                        state.selectedBrand.productCount == 0 && officialMenuReferences.isNotEmpty() ->
                        "공식 메뉴명 ${officialMenuReferences.size}개 · 영양정보 미확인"
                    state.selectedBrand != null && state.searchMode == FoodSearchMode.FRANCHISE ->
                        "공식 영양정보로 확인된 메뉴 ${state.selectedBrand.productCount}개"
                    state.selectedBrand != null -> "공식 데이터에 등록된 제품 ${state.selectedBrand.productCount}개"
                    state.searchMode == FoodSearchMode.FRANCHISE -> "공식 영양정보가 확인된 외식 브랜드만 보여드려요."
                    state.searchMode == FoodSearchMode.BRAND -> "공식 데이터의 업체명을 그대로 보여드려요."
                    else -> "음식·제품·프랜차이즈 이름을 입력해도 괜찮아요."
                }
            )
        }
        if (!state.isCompanionSearch && state.selectedBrand == null) {
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = state.searchMode == FoodSearchMode.FOOD,
                        onClick = { onSearchModeSelected(FoodSearchMode.FOOD) },
                        label = { Text("음식·제품") }
                    )
                    FilterChip(
                        selected = state.searchMode == FoodSearchMode.BRAND,
                        onClick = { onSearchModeSelected(FoodSearchMode.BRAND) },
                        label = { Text("브랜드·제조사") }
                    )
                    FilterChip(
                        selected = state.searchMode == FoodSearchMode.FRANCHISE,
                        onClick = { onSearchModeSelected(FoodSearchMode.FRANCHISE) },
                        label = { Text("프랜차이즈") }
                    )
                }
            }
        }
        if (state.searchMode == FoodSearchMode.FRANCHISE && state.selectedBrand == null) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedBrandCategory == null,
                            onClick = { onBrandCategorySelected(null) },
                            label = { Text("전체") }
                        )
                    }
                    items(FranchiseCatalog.categories, key = { it }) { category ->
                        FilterChip(
                            selected = state.selectedBrandCategory == category,
                            onClick = { onBrandCategorySelected(category) },
                            label = { Text(category) }
                        )
                    }
                }
            }
        }
        if (state.selectedBrand != null) {
            item {
                TextButton(onClick = onBrandBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Text(if (state.searchMode == FoodSearchMode.FRANCHISE) "프랜차이즈 목록으로" else "업체 목록으로")
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(when {
                        state.selectedBrand != null && state.searchMode == FoodSearchMode.FRANCHISE -> "이 브랜드 메뉴 검색"
                        state.selectedBrand != null -> "이 업체 제품 검색"
                        state.searchMode == FoodSearchMode.FRANCHISE -> "프랜차이즈 브랜드"
                        state.searchMode == FoodSearchMode.BRAND -> "브랜드 또는 제조사"
                        else -> "음식·제품 또는 브랜드 이름"
                    })
                },
                placeholder = {
                    Text(when {
                        state.searchMode == FoodSearchMode.FRANCHISE && state.selectedBrand == null -> "예: 맥도날드"
                        state.searchMode == FoodSearchMode.BRAND && state.selectedBrand == null -> "예: 농심"
                        else -> "예: 김밥"
                    })
                },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (state.searchQuery.isNotBlank()) {
                    { IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "검색어 지우기")
                    } }
                } else null,
                singleLine = true
            )
        }
        if (!state.isCompanionSearch && state.selectedBrand == null && state.searchMode == FoodSearchMode.FOOD) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FoodBrowseCategory.entries, key = FoodBrowseCategory::name) { category ->
                        FilterChip(
                            selected = state.selectedFoodCategory == category,
                            onClick = { onFoodCategorySelected(category) },
                            label = { Text(category.label) }
                        )
                    }
                }
            }
            if (state.searchQuery.isBlank()) {
                item {
                    Text(
                        "즐겨찾기",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (favoriteFoods.isEmpty()) {
                    item {
                        Text(
                            "즐겨찾기한 음식이 없어요. 자주 먹는 음식을 검색해 즐겨찾기에 추가해보세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(favoriteFoods, key = { "favorite-${it.id}" }) { food ->
                                OutlinedCard(
                                    onClick = { onFavoriteFoodSelected(food) },
                                    modifier = Modifier.testTag("favorite-food-${food.foodItemId ?: food.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.padding(end = 4.dp)) {
                                            Text(food.foodName, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                                            Text(
                                                listOfNotNull(food.brand, "${food.defaultServing} · ${food.calories} kcal")
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onFavoriteRemoved(food) }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Rounded.Star, contentDescription = "${food.foodName} 즐겨찾기 해제")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (frequentFoods.isNotEmpty() && state.searchQuery.isBlank()) {
                item {
                    Text(
                        "자주 먹는 음식",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(frequentFoods, key = FrequentFood::id) { food ->
                            OutlinedCard(onClick = { onFrequentFoodSelected(food) }) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.padding(end = 4.dp)) {
                                        Text(food.foodName, style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            "${food.defaultServing} · ${food.calories} kcal",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onFrequentFoodDeleted(food) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "${food.foodName} 자주 먹는 음식에서 삭제")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        state.message?.let { message ->
            item { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
        if (state.isSearching) item { CircularProgressIndicator(Modifier.padding(20.dp)) }
        when {
            state.searchMode in setOf(FoodSearchMode.BRAND, FoodSearchMode.FRANCHISE) && state.selectedBrand == null -> {
                if (state.searchQuery.isNotBlank() && !state.isSearching && state.brandResults.isEmpty()) {
                    item {
                        WellnessEmptyState(
                            icon = Icons.Rounded.RestaurantMenu,
                            title = if (state.searchMode == FoodSearchMode.FRANCHISE) "해당 프랜차이즈를 찾지 못했어요"
                                else "해당 브랜드·제조사를 찾지 못했어요",
                            message = if (state.searchMode == FoodSearchMode.FRANCHISE)
                                "공식 K-FIND 영양정보가 확인된 브랜드만 제공해요."
                            else "공식 데이터의 업체명과 다를 수 있어요. 이름을 짧게 다시 검색해 주세요."
                        )
                    }
                }
                items(state.brandResults, key = FoodBrandSummary::brand) { brand ->
                    OutlinedCard(onClick = { onBrandSelected(brand) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(brand.brand, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                Text(if (state.searchMode == FoodSearchMode.FRANCHISE && brand.productCount == 0)
                                    "공식 영양정보가 확인된 메뉴 없음"
                                else "${if (state.searchMode == FoodSearchMode.FRANCHISE) "메뉴" else "제품"} ${brand.productCount}개", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
            state.selectedBrand != null -> {
                if (state.brandCategories.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = state.selectedBrandCategory == null,
                                    onClick = { onBrandCategorySelected(null) },
                                    label = { Text("전체") }
                                )
                            }
                            items(state.brandCategories, key = { it }) { category ->
                                FilterChip(
                                    selected = state.selectedBrandCategory == category,
                                    onClick = { onBrandCategorySelected(category) },
                                    label = { Text(category) }
                                )
                            }
                        }
                    }
                }
                if (!state.isSearching && brandFoods.isEmpty() && officialMenuReferences.isEmpty()) {
                    item {
                        WellnessEmptyState(
                            icon = Icons.Rounded.RestaurantMenu,
                            title = "조건에 맞는 제품이 없어요",
                            message = "검색어를 지우거나 다른 분류를 선택해 주세요."
                        )
                    }
                }
                items(brandFoods, key = FoodItem::id) { food ->
                    FoodSearchResultCard(
                        food = food,
                        isFavorite = food.id in favoriteIds,
                        onFoodSelected = onFoodSelected,
                        onFavoriteToggle = onFavoriteToggle
                    )
                }
                if (officialMenuReferences.isNotEmpty()) {
                    item {
                        Text("공식 메뉴명 참고", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    items(officialMenuReferences, key = { "official-menu-$it" }) { menuName ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(menuName, style = MaterialTheme.typography.titleMedium)
                                Text("영양정보 없음 · 기록 항목으로 선택할 수 없어요",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                            Text("영양정보를 확인해 직접 입력")
                        }
                    }
                }
                if (brandFoods.isNotEmpty()) item { OfficialFoodDataSourceText() }
            }
            else -> {
                if (state.searchResults.isNotEmpty()) item {
                    Text("이름이 비슷한 음식도 포함될 수 있어요. 실제 먹은 음식과 표시된 양을 확인해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.searchQuery.isNotBlank() && !state.isSearching && state.searchResults.isEmpty() &&
                    state.brandResults.isEmpty()) {
                    item {
                        WellnessEmptyState(
                            icon = Icons.Rounded.RestaurantMenu,
                            title = "검색 결과가 없어요",
                            message = "다른 이름으로 등록되었을 수 있어요. 음식 이름을 짧게 검색하거나 직접 입력해 주세요."
                        )
                    }
                    FoodSearchPolicy.broaderSuggestion(state.searchQuery)?.let { suggestion ->
                        item { OutlinedButton(onClick = { onQueryChange(suggestion) }, modifier = Modifier.fillMaxWidth()) {
                            Text("‘$suggestion’ 결과 보기")
                        } }
                    }
                    item { OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.isCompanionSearch) "기록 화면으로 돌아가기" else "직접 입력으로 전환")
                    } }
                }
                groupedResults.forEach { group ->
                    val expanded = expandedGroups[group.key] == true
                    item(key = "name-group-${group.key}") {
                        FoodSearchResultCard(
                            food = group.representative,
                            isFavorite = group.representative.id in favoriteIds,
                            alternativeCount = group.alternatives.size,
                            alternativesExpanded = expanded,
                            onAlternativesToggle = {
                                expandedGroups[group.key] = !expanded
                            },
                            onFoodSelected = onFoodSelected,
                            onFavoriteToggle = onFavoriteToggle
                        )
                    }
                    if (expanded) {
                        items(group.alternatives, key = { "alternative-${it.id}" }) { food ->
                            FoodSearchResultCard(
                                food = food,
                                isFavorite = food.id in favoriteIds,
                                onFoodSelected = onFoodSelected,
                                onFavoriteToggle = onFavoriteToggle
                            )
                        }
                    }
                }
                if (state.brandResults.isNotEmpty()) {
                    item {
                        Text("관련 프랜차이즈", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    items(state.brandResults.take(8), key = { "related-${it.brand}" }) { brand ->
                        OutlinedCard(onClick = { onBrandSelected(brand) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(brand.brand, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        FranchiseCatalog.categoryOf(brand.brand).orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    if (brand.productCount > 0) "메뉴 ${brand.productCount}개" else "영양정보 미확인",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
                if (state.searchResults.any(FoodSearchPolicy::needsBasisReview)) {
                    item {
                        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                            Text("음식 직접 등록")
                        }
                    }
                }
                if (state.searchResults.any(FoodSearchPolicy::isOfficialKfind)) item { OfficialFoodDataSourceText() }
            }
        }
    }
}

@Composable
private fun FoodSearchResultCard(
    food: FoodItem,
    isFavorite: Boolean,
    onFoodSelected: (FoodItem) -> Unit,
    onFavoriteToggle: (FoodItem) -> Unit,
    alternativeCount: Int = 0,
    alternativesExpanded: Boolean = false,
    onAlternativesToggle: () -> Unit = {}
) {
    val needsReview = FoodSearchPolicy.needsBasisReview(food)
    Card(
        onClick = { onFoodSelected(food) },
        enabled = !needsReview,
        modifier = Modifier.fillMaxWidth().testTag("food-search-result-${food.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (needsReview) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    FoodSearchPolicy.displayName(food),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onFavoriteToggle(food) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (isFavorite) {
                            "${FoodSearchPolicy.displayName(food)} 즐겨찾기 해제"
                        } else {
                            "${FoodSearchPolicy.displayName(food)} 즐겨찾기 추가"
                        }
                    )
                    Text(if (isFavorite) "즐겨찾기됨" else "즐겨찾기")
                }
            }
            food.brand?.takeIf(String::isNotBlank)?.let { brand ->
                Text("${if (FranchiseCatalog.isFranchise(food)) "프랜차이즈" else "브랜드·제조사"} · $brand", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FoodSearchPolicy.sourceVariantLabel(food)?.let { sourceVariant ->
                Text("자료 구분 · $sourceVariant", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary)
            }
            if (needsReview) {
                Text("영양정보 단위 확인 필요", style = MaterialTheme.typography.bodyLarge)
                Text("공식 영양정보 단위를 일상적인 섭취량으로 바로 바꾸기 어려워요. 다른 항목을 선택하거나 직접 입력해 주세요.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text(PortionGuide.resultServingSummary(food), style = MaterialTheme.typography.bodyLarge)
            }
            Text("${food.category?.let { "$it · " }.orEmpty()}출처 ${food.sourceType} · 데이터 ${food.dataVersion}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (alternativeCount > 0) {
                TextButton(
                    onClick = onAlternativesToggle,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .align(Alignment.End)
                        .testTag("food-search-alternatives-${food.id}")
                ) {
                    Text(if (alternativesExpanded) "다른 제품 접기" else "다른 제품 ${alternativeCount}개")
                }
            }
        }
    }
}

@Composable
private fun OfficialFoodDataSourceText() {
    Text(
        "데이터 출처: 식품영양성분 데이터베이스 · Korean Food Composition Database system(K-FCDB)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BarcodeResultContent(item: FoodItem?, onUse: () -> Unit, onNutritionLabel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(WellnessSpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (item == null) {
            WellnessEmptyState(Icons.Rounded.QrCodeScanner, "상품 정보가 없어요", "영양성분표를 촬영해 직접 확인해 주세요.")
        } else {
            SectionHeader(title = "등록 상품을 찾았어요", supportingText = "영양정보와 실제 먹은 양을 확인한 뒤 저장합니다.")
            WellnessCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(WellnessSpacing.CardContent), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    item.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text("${item.servingDescription} · ${formatKcal(item.energyKcal.toInt())} kcal", style = MaterialTheme.typography.headlineSmall)
                    Text("출처 ${item.sourceType} · ${item.dataVersion}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text("섭취량 확인하기") }
        }
        OutlinedButton(onClick = onNutritionLabel, modifier = Modifier.fillMaxWidth()) { Text("영양성분표 촬영") }
    }
}

@Composable
private fun BarcodeNotFoundContent(barcode: String, message: String?, onNutritionLabel: () -> Unit, onSearch: () -> Unit, onManual: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(WellnessSpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        WellnessEmptyState(Icons.Rounded.QrCodeScanner, "등록되지 않은 바코드예요", message ?: "임의의 상품 정보는 만들지 않습니다.")
        if (barcode.isNotBlank()) Text("인식 코드 $barcode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onNutritionLabel, modifier = Modifier.fillMaxWidth()) { Text("영양성분표 촬영") }
        OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) { Text("음식 검색") }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("제품 직접 등록") }
    }
}

@Composable
private fun NutritionLabelResultContent(
    state: SmartInputUiState,
    onCandidateSelected: (Int) -> Unit,
    onAmountChange: (String) -> Unit,
    onPortionSelected: (Double, String) -> Unit,
    onPrecise: () -> Unit,
    onUnknown: () -> Unit,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val result = state.ocrResult
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(WellnessSpacing.ScreenHorizontal, WellnessSpacing.Compact, WellnessSpacing.ScreenHorizontal, WellnessSpacing.Section),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionHeader(title = "인식 결과 확인", supportingText = "숫자가 여러 개면 올바른 열량 기준을 직접 선택해 주세요.") }
        if (result == null || result.energyCandidates.isEmpty()) {
            item { WellnessEmptyState(Icons.AutoMirrored.Rounded.TextSnippet, "열량 기준을 찾지 못했어요", state.message ?: "빛 반사를 줄이고 표 전체가 보이게 다시 촬영해 주세요.") }
            item { Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) { Text("다시 촬영") } }
            item { OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("직접 입력") } }
        } else {
            val details = buildList {
                result.productNameCandidate?.let { add("제품명 후보 · $it") }
                if (result.totalAmount != null && result.totalAmountUnit != null) {
                    add("총내용량 · ${result.totalAmount.formatAmount()} ${result.totalAmountUnit}")
                }
                if (result.servingAmount != null && result.servingAmountUnit != null) {
                    add("1회 제공량 · ${result.servingAmount.formatAmount()} ${result.servingAmountUnit}")
                }
                result.servingCount?.let { add("총 제공 횟수 · ${it.formatAmount()}회") }
                result.carbohydrateGrams?.let { add("탄수화물 · ${it.formatAmount()} g") }
                result.proteinGrams?.let { add("단백질 · ${it.formatAmount()} g") }
                result.fatGrams?.let { add("지방 · ${it.formatAmount()} g") }
                result.sodiumMilligrams?.let { add("나트륨 · ${it.formatAmount()} mg") }
            }
            if (details.isNotEmpty()) {
                item {
                    WellnessCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("함께 인식된 정보", style = MaterialTheme.typography.titleMedium)
                            details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
            items(result.energyCandidates.indices.toList(), key = { it }) { index ->
                OcrCandidateCard(
                    candidate = result.energyCandidates[index],
                    selected = state.selectedOcrCandidateIndex == index,
                    onClick = { onCandidateSelected(index) }
                )
            }
            item {
                val selected = state.selectedOcrCandidateIndex?.let { result.energyCandidates.getOrNull(it) }
                val hasTotal = selected != null && result.totalAmount != null &&
                    result.totalAmountUnit == selected.unit
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("얼마나 먹었나요?", style = MaterialTheme.typography.titleLarge)
                    Text(if (hasTotal) "포장 전체에서 먹은 정도를 골라 주세요. 대략 골라도 괜찮아요."
                        else "총내용량을 찾지 못했어요. 영양표에 표시된 양과 비교해 대략 골라 주세요.",
                        style = MaterialTheme.typography.bodyMedium)
                    listOf(
                        0.25 to "조금 먹었어요", 0.5 to "절반 정도 먹었어요",
                        0.9 to "거의 다 먹었어요", 1.0 to "다 먹었어요"
                    ).chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (ratio, label) ->
                                FilterChip(
                                    selected = state.ocrPortionPreset?.id == "ocr-fraction-$ratio",
                                    onClick = { onPortionSelected(ratio, label) },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    state.ocrPortionPreset?.let { portion ->
                        Text("${portion.label} · ${portion.description}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onPrecise) { Text("더 정확히 입력하기") }
                    if (state.ocrPreciseOpen) {
                        OutlinedTextField(
                            value = state.ocrConsumedAmount,
                            onValueChange = onAmountChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("직접 양 입력") },
                            suffix = { Text(selected?.unit.orEmpty()) },
                            singleLine = true
                        )
                    }
                }
            }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            item { Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("계산 결과 확인") } }
            item { OutlinedButton(onClick = onUnknown, modifier = Modifier.fillMaxWidth()) {
                Text("양을 잘 모르겠어요 · 그릇으로 선택")
            } }
            item { OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) { Text("다시 촬영") } }
            item { Text("OCR 결과는 자동 저장되지 않습니다. 영양정보에 표시된 양·실제 섭취량·최종 칼로리를 확인해 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun OcrCandidateCard(candidate: NutritionBasisCandidate, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text("${candidate.referenceAmount.formatAmount()} ${candidate.unit}당 ${candidate.energyKcal.formatAmount()} kcal", style = MaterialTheme.typography.titleMedium)
                Text(candidate.evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LoadingContent(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Text(message, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatKcal(value: Int): String = NumberFormat.getNumberInstance().format(value)
private fun Double.formatAmount(): String = if (this == toLong().toDouble()) toLong().toString() else toString()
