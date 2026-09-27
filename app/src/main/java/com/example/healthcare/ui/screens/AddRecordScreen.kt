package com.example.healthcare.ui.screens

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.photo.model.PhotoAnalysisPolicy
import com.example.healthcare.data.photo.model.PhotoAnalysisProgress
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordUiState
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import com.example.healthcare.ui.viewmodel.CapturePurpose
import com.example.healthcare.ui.viewmodel.EditablePhotoFoodItem
import com.example.healthcare.ui.viewmodel.PhotoAnalysisError
import com.example.healthcare.ui.viewmodel.PhotoAnalysisUiState
import com.example.healthcare.ui.viewmodel.SmartInputMode
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    viewModel: AddRecordViewModel? = null,
    onBack: () -> Unit = {},
    initialAction: QuickRecordAction? = null,
    initialDate: LocalDate? = null,
    onInitialActionConsumed: () -> Unit = {},
    onImmersiveChanged: (Boolean) -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiStateHolder = viewModel?.uiState?.collectAsStateCompat() ?: remember { mutableStateOf(AddRecordUiState()) }
    val foodsHolder = viewModel?.frequentFoods?.collectAsStateCompat()
        ?: remember { mutableStateOf(emptyList<FrequentFood>()) }
    val favoritesHolder = viewModel?.favoriteFoods?.collectAsStateCompat()
        ?: remember { mutableStateOf(emptyList<FrequentFood>()) }
    val recentMealsHolder = viewModel?.recentMeals?.collectAsStateCompat()
        ?: remember { mutableStateOf(emptyList<MealRecord>()) }
    val selectedDateMealsHolder = viewModel?.selectedDateMeals?.collectAsStateCompat()
        ?: remember { mutableStateOf(emptyList<MealRecord>()) }
    val photoHolder = viewModel?.photoState?.collectAsStateCompat()
        ?: remember { mutableStateOf<PhotoAnalysisUiState>(PhotoAnalysisUiState.Idle) }
    val smartHolder = viewModel?.smartInputState?.collectAsStateCompat()
        ?: remember { mutableStateOf(com.example.healthcare.ui.viewmodel.SmartInputUiState()) }
    val uiState = uiStateHolder.value
    val photoState = photoHolder.value
    val smartState = smartHolder.value

    LaunchedEffect(viewModel, initialDate) {
        initialDate?.let { viewModel?.onDateChange(it) }
    }
    LaunchedEffect(photoState) {
        onImmersiveChanged(photoState is PhotoAnalysisUiState.Camera || photoState is PhotoAnalysisUiState.Preview)
    }
    DisposableEffect(Unit) {
        onDispose { onImmersiveChanged(false) }
    }
    var showPermissionExplanation by rememberSaveable { mutableStateOf(false) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var pendingCapturePurpose by rememberSaveable { mutableStateOf(CapturePurpose.FOOD_PHOTO) }

    BackHandler(enabled = photoState !is PhotoAnalysisUiState.Idle || smartState.mode != SmartInputMode.HUB) {
        when {
            smartState.isCompanionSearch -> viewModel?.cancelCompanionSearch()
            photoState !is PhotoAnalysisUiState.Idle -> viewModel?.cancelPhotoFlow()
            else -> viewModel?.showSmartInputHub()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel?.openCamera(pendingCapturePurpose)
        } else {
            val activity = context.findActivity()
            val permanentlyDenied = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.CAMERA
            )
            viewModel?.showCameraPermissionDenied(permanentlyDenied)
        }
    }

    val requestCapture: (CapturePurpose) -> Unit = { purpose ->
        pendingCapturePurpose = purpose
        when {
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> {
                viewModel?.showCameraUnavailable()
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                viewModel?.openCamera(purpose)
            }
            permissionRequested && context.findActivity()?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } == true -> {
                viewModel?.showCameraPermissionDenied(permanently = true)
            }
            else -> showPermissionExplanation = true
        }
        Unit
    }
    val requestPhotoCapture: () -> Unit = { requestCapture(CapturePurpose.FOOD_PHOTO) }
    val requestNutritionLabelCapture: () -> Unit = { requestCapture(CapturePurpose.NUTRITION_LABEL) }

    val barcodeOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .enableAutoZoom()
            .build()
    }
    val barcodeScanner = remember(context, barcodeOptions) {
        GmsBarcodeScanning.getClient(context, barcodeOptions)
    }
    val launchBarcodeScanner: () -> Unit = {
        if (viewModel?.beginBarcodeScan() == true) {
            barcodeScanner.startScan()
                .addOnSuccessListener { barcode -> viewModel.lookupBarcode(barcode.rawValue.orEmpty()) }
                .addOnCanceledListener { viewModel.cancelBarcodeScan() }
                .addOnFailureListener { viewModel.showBarcodeScanError() }
        }
        Unit
    }
    LaunchedEffect(initialAction) {
        when (initialAction) {
            QuickRecordAction.PHOTO -> requestPhotoCapture()
            QuickRecordAction.SEARCH -> viewModel?.showFoodSearch()
            QuickRecordAction.BARCODE -> launchBarcodeScanner()
            null -> Unit
        }
        if (initialAction != null) onInitialActionConsumed()
    }
    val smartInputBack: () -> Unit = {
        when {
            smartState.isCompanionSearch -> viewModel?.cancelCompanionSearch()
            smartState.mode == SmartInputMode.HUB -> onBack()
            else -> viewModel?.showSmartInputHub()
        }
    }
    val searchQueryChanged: (String) -> Unit = { query ->
        if (smartState.isCompanionSearch) viewModel?.onCompanionSearchChange(query)
        else viewModel?.onFoodSearchChange(query)
    }

    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text("카메라 권한이 필요합니다") },
            text = { Text("음식 사진을 촬영해 기록을 작성할 때만 카메라를 사용합니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionExplanation = false
                        permissionRequested = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                ) { Text("계속") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) { Text("취소") }
            }
        )
    }

    when (photoState) {
        PhotoAnalysisUiState.Camera -> FoodCameraScreen(
            onPhotoCaptured = { viewModel?.onPhotoCaptured(it) },
            onCancel = { viewModel?.cancelPhotoFlow() },
            onCameraUnavailable = { viewModel?.showCameraUnavailable() },
            onCaptureError = { viewModel?.showCameraCaptureError() },
            onFileError = { viewModel?.showCameraFileError() }
        )
        is PhotoAnalysisUiState.Preview -> WellnessPhotoPreviewScreen(
            photoPath = photoState.photoPath,
            onUsePhoto = { viewModel?.usePhotoForManualRecord() },
            onAnalyze = {
                viewModel?.analyzePhoto(Locale.getDefault().toLanguageTag(), ZoneId.systemDefault().id)
            },
            analysisAvailable = smartState.photoAnalysisAvailable,
            onRetake = { viewModel?.retakePhoto() },
            onCancel = { viewModel?.cancelPhotoFlow() }
        )
        is PhotoAnalysisUiState.ManualEntry -> if (smartState.isCompanionSearch) {
            SmartFoodInputScreen(
                state = smartState, recentMeals = recentMealsHolder.value,
                dateMeals = selectedDateMealsHolder.value, onBack = smartInputBack,
                onPhoto = requestPhotoCapture, onBarcode = launchBarcodeScanner,
                onNutritionLabel = requestNutritionLabelCapture, onSearch = { viewModel?.showFoodSearch() },
                onSearchQueryChange = searchQueryChanged,
                onFoodSelected = { viewModel?.selectSearchFood(it) },
                onUseBarcodeItem = { viewModel?.useBarcodeItem() },
                onOcrCandidateSelected = { viewModel?.selectOcrCandidate(it) },
                onOcrAmountChange = { viewModel?.onOcrConsumedAmountChange(it) },
                onConfirmOcr = { viewModel?.confirmOcrResult() },
                onManual = { viewModel?.cancelCompanionSearch() },
                onRegisterBarcode = { viewModel?.showBarcodeManualRegistration() },
                onRepeatRecent = {},
                onSearchModeSelected = { viewModel?.selectFoodSearchMode(it) },
                onBrandSelected = { viewModel?.selectProductBrand(it) },
                onBrandBack = { viewModel?.clearSelectedProductBrand() },
                onBrandCategorySelected = { viewModel?.selectBrandCategory(it) },
                onOpenHistory = onOpenHistory
            )
        } else WellnessManualRecordScreen(
                uiState = uiState, frequentFoods = foodsHolder.value, favoriteFoods = favoritesHolder.value,
                recentMeals = recentMealsHolder.value, photoPath = photoState.photoPath,
                onBack = { viewModel?.cancelPhotoFlow() }, onSaved = onBack,
                onPhotoRecord = requestPhotoCapture, viewModel = viewModel
            )
        is PhotoAnalysisUiState.InProgress -> PhotoAnalysisProgressScreen(
            state = photoState,
            onCancel = { viewModel?.cancelPhotoFlow() }
        )
        is PhotoAnalysisUiState.Result -> PhotoAnalysisResultScreen(
            state = photoState,
            entryState = uiState,
            onNameChange = { id, value -> viewModel?.updatePhotoItemName(id, value) },
            onAmountChange = { id, value -> viewModel?.updatePhotoItemAmount(id, value) },
            onUnitChange = { id, value -> viewModel?.updatePhotoItemUnit(id, value) },
            onCaloriesChange = { id, value -> viewModel?.updatePhotoItemCalories(id, value) },
            onSelectedChange = { id, selected -> viewModel?.togglePhotoItem(id, selected) },
            onRemove = { viewModel?.removePhotoItem(it) },
            onAddMissing = { viewModel?.addMissingPhotoItem() },
            onMealTypeChange = { viewModel?.onMealTypeChange(it) },
            onDateChange = { viewModel?.onDateChange(it) },
            onTimeChange = { viewModel?.onTimeChange(it) },
            onMemoChange = { viewModel?.onMemoChange(it) },
            onSave = { viewModel?.savePhotoAnalysisRecords(onBack) },
            onRetryAnalysis = {
                viewModel?.analyzePhoto(Locale.getDefault().toLanguageTag(), ZoneId.systemDefault().id)
            },
            onRetake = { viewModel?.retakePhoto() },
            onManual = { viewModel?.returnToManualEntry() }
        )
        is PhotoAnalysisUiState.Error -> {
            val isEntryLevelError = photoState.photoPath == null
            if (isEntryLevelError) {
                SmartFoodInputScreen(
                    state = smartState,
                    recentMeals = recentMealsHolder.value,
                    dateMeals = selectedDateMealsHolder.value,
                    onBack = smartInputBack,
                    onPhoto = requestPhotoCapture,
                    onBarcode = launchBarcodeScanner,
                    onNutritionLabel = requestNutritionLabelCapture,
                    onSearch = { viewModel?.showFoodSearch() },
                    onSearchQueryChange = searchQueryChanged,
                    onFoodSelected = { viewModel?.selectSearchFood(it) },
                    onUseBarcodeItem = { viewModel?.useBarcodeItem() },
                    onOcrCandidateSelected = { viewModel?.selectOcrCandidate(it) },
                    onOcrAmountChange = { viewModel?.onOcrConsumedAmountChange(it) },
                    onOcrPortionSelected = { ratio, label -> viewModel?.selectOcrPortion(ratio, label) },
                    onOcrPrecise = { viewModel?.showPreciseOcrAmount() },
                    onOcrUnknown = { viewModel?.continueOcrWithUnknownAmount() },
                    onConfirmOcr = { viewModel?.confirmOcrResult() },
                    onManual = { viewModel?.showManualEntry() },
                    onRegisterBarcode = { viewModel?.showBarcodeManualRegistration() },
                    onRepeatRecent = { viewModel?.repeatRecentMeal(it, onBack) },
                    onSearchModeSelected = { viewModel?.selectFoodSearchMode(it) },
                    onBrandSelected = { viewModel?.selectProductBrand(it) },
                    onBrandBack = { viewModel?.clearSelectedProductBrand() },
                    onBrandCategorySelected = { viewModel?.selectBrandCategory(it) },
                    onOpenHistory = onOpenHistory
                )
                PhotoEntryErrorDialog(
                    error = photoState.type,
                    onDismiss = { viewModel?.returnToManualEntry() },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:${context.packageName}".toUri()
                            )
                        )
                    }
                )
            } else {
                PhotoAnalysisErrorScreen(
                    state = photoState,
                    onRetry = {
                        viewModel?.analyzePhoto(Locale.getDefault().toLanguageTag(), ZoneId.systemDefault().id)
                    },
                    onRetake = { viewModel?.retakePhoto() },
                    onManual = { viewModel?.returnToManualEntry() },
                    onCancel = { viewModel?.cancelPhotoFlow() }
                )
            }
        }
        PhotoAnalysisUiState.Idle -> if (smartState.mode == SmartInputMode.MANUAL) {
            WellnessManualRecordScreen(
                uiState = uiState,
                frequentFoods = foodsHolder.value,
                favoriteFoods = favoritesHolder.value,
                recentMeals = recentMealsHolder.value,
                photoPath = null,
                onBack = { viewModel?.showSmartInputHub() },
                onSaved = onBack,
                onPhotoRecord = requestPhotoCapture,
                viewModel = viewModel
            )
        } else {
            SmartFoodInputScreen(
                state = smartState,
                recentMeals = recentMealsHolder.value,
                dateMeals = selectedDateMealsHolder.value,
                onBack = smartInputBack,
                onPhoto = requestPhotoCapture,
                onBarcode = launchBarcodeScanner,
                onNutritionLabel = requestNutritionLabelCapture,
                onSearch = { viewModel?.showFoodSearch() },
                onSearchQueryChange = searchQueryChanged,
                onFoodSelected = { viewModel?.selectSearchFood(it) },
                onUseBarcodeItem = { viewModel?.useBarcodeItem() },
                onOcrCandidateSelected = { viewModel?.selectOcrCandidate(it) },
                onOcrAmountChange = { viewModel?.onOcrConsumedAmountChange(it) },
                onOcrPortionSelected = { ratio, label -> viewModel?.selectOcrPortion(ratio, label) },
                onOcrPrecise = { viewModel?.showPreciseOcrAmount() },
                onOcrUnknown = { viewModel?.continueOcrWithUnknownAmount() },
                onConfirmOcr = { viewModel?.confirmOcrResult() },
                onManual = { viewModel?.showManualEntry() },
                onRegisterBarcode = { viewModel?.showBarcodeManualRegistration() },
                onRepeatRecent = { viewModel?.repeatRecentMeal(it, onBack) },
                onSearchModeSelected = { viewModel?.selectFoodSearchMode(it) },
                onBrandSelected = { viewModel?.selectProductBrand(it) },
                onBrandBack = { viewModel?.clearSelectedProductBrand() },
                onBrandCategorySelected = { viewModel?.selectBrandCategory(it) },
                onOpenHistory = onOpenHistory
            )
        }
    }
}

enum class QuickRecordAction { PHOTO, SEARCH, BARCODE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualRecordScreen(
    uiState: AddRecordUiState,
    frequentFoods: List<FrequentFood>,
    favoriteFoods: List<FrequentFood>,
    recentMeals: List<MealRecord>,
    photoPath: String?,
    onBack: () -> Unit,
    onPhotoRecord: () -> Unit,
    viewModel: AddRecordViewModel?
) {
    val query = uiState.foodSearch.trim()
    val filteredFoods = frequentFoods.filter {
        query.isBlank() || it.foodName.contains(query, ignoreCase = true)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (photoPath == null) "식사 기록 추가" else "사진으로 기록") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (photoPath == null) {
                item {
                    Text("음식 사진을 찍고 기록을 작성해 보세요.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onPhotoRecord, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = "사진으로 음식 기록")
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("사진으로 기록")
                    }
                }
            } else {
                item {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = "기록 작성에 참고할 음식 사진",
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "사진을 참고해 음식 이름과 섭취량을 입력해 주세요.",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            if (recentMeals.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("최근 음식", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentMeals, key = { it.foodName }) { meal ->
                                SuggestionChip(
                                    onClick = { viewModel?.selectRecentMeal(meal) },
                                    label = { Text("${meal.foodName} (${meal.calories}kcal)") }
                                )
                            }
                        }
                    }
                }
            }
            if (favoriteFoods.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("즐겨찾기", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(favoriteFoods, key = { it.id }) { food ->
                                SuggestionChip(
                                    onClick = { viewModel?.selectFrequentFood(food) },
                                    label = { Text("${food.foodName} (${food.calories}kcal)") }
                                )
                            }
                        }
                    }
                }
            }
            if (frequentFoods.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = uiState.foodSearch,
                        onValueChange = { viewModel?.onFoodSearchChange(it) },
                        label = { Text("저장된 음식 검색") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (filteredFoods.isEmpty()) {
                    item { Text("검색 결과가 없습니다. 아래에서 음식 이름과 칼로리를 직접 입력할 수 있습니다.") }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("저장된 음식", style = MaterialTheme.typography.titleSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filteredFoods, key = { it.id }) { food ->
                                    SuggestionChip(
                                        onClick = { viewModel?.selectFrequentFood(food) },
                                        label = { Text("${food.foodName} (${food.calories}kcal)") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = uiState.foodName,
                    onValueChange = { viewModel?.onFoodNameChange(it) },
                    label = { Text("음식 이름") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = uiState.nameError != null,
                    supportingText = uiState.nameError?.let { { Text(it) } }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.servingAmount,
                        onValueChange = { viewModel?.onServingAmountChange(it) },
                        label = { Text("섭취량") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = uiState.servingUnit,
                        onValueChange = { viewModel?.onServingUnitChange(it) },
                        label = { Text("섭취 단위") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (uiState.referenceCalories != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("빠른 섭취량", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(0.5, 1.0, 1.5, 2.0)) { ratio ->
                                FilterChip(
                                    selected = uiState.selectedServingRatio == ratio,
                                    onClick = { viewModel?.selectServingRatio(ratio) },
                                    label = { Text("${ratio}인분") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = uiState.selectedServingRatio == null,
                                    onClick = { viewModel?.useCustomServing() },
                                    label = { Text("직접 입력") }
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = uiState.calories,
                    onValueChange = { viewModel?.onCaloriesChange(it) },
                    label = { Text("칼로리 (kcal)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = uiState.caloriesError != null,
                    supportingText = uiState.caloriesError?.let { { Text(it) } }
                )
                Text(
                    "칼로리는 입력한 음식과 섭취량을 기준으로 계산됩니다. 최종값을 확인해 주세요.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item { MealTypeSelector(uiState.mealType) { viewModel?.onMealTypeChange(it) } }
            item {
                DateTimeSelectors(
                    date = uiState.date,
                    time = uiState.time,
                    onDateChange = { viewModel?.onDateChange(it) },
                    onTimeChange = { viewModel?.onTimeChange(it) }
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.memo,
                    onValueChange = { viewModel?.onMemoChange(it) },
                    label = { Text("메모 (선택 사항)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = uiState.saveAsFrequent,
                        onCheckedChange = { viewModel?.onSaveAsFrequentChange(it) }
                    )
                    Text("내 음식으로 저장")
                }
            }
            uiState.saveError?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Button(
                    onClick = { viewModel?.saveRecord(onBack) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text("저장하기")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoPreviewScreen(
    photoPath: String,
    onUsePhoto: () -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("촬영 사진 확인") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "사진 사용 취소")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "촬영한 음식 사진 미리보기",
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentScale = ContentScale.Fit
            )
            Text("사진을 확인해 주세요. 사진은 서버로 전송되지 않으며 기록 완료 후 삭제됩니다.")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "다시 촬영")
                    Text("다시 촬영")
                }
                Button(onClick = onUsePhoto, modifier = Modifier.weight(1f)) {
                    Text("이 사진으로 기록")
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("취소") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoAnalysisProgressScreen(
    state: PhotoAnalysisUiState.InProgress,
    onCancel: () -> Unit
) {
    val message = when (state.progress) {
        PhotoAnalysisProgress.PROCESSING_IMAGE -> "사진을 안전하게 처리하고 있습니다."
        PhotoAnalysisProgress.UPLOADING -> "사진을 분석 서버로 전송하고 있습니다."
        PhotoAnalysisProgress.ANALYZING -> "사진 속 음식과 양을 추정하는 중입니다."
    }
    Scaffold(topBar = { TopAppBar(title = { Text("음식 사진 분석") }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Text("음식을 분석하고 있습니다.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(20.dp))
            TextButton(onClick = onCancel) { Text("분석 취소") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoAnalysisResultScreen(
    state: PhotoAnalysisUiState.Result,
    entryState: AddRecordUiState,
    onNameChange: (String, String) -> Unit,
    onAmountChange: (String, String) -> Unit,
    onUnitChange: (String, String) -> Unit,
    onCaloriesChange: (String, String) -> Unit,
    onSelectedChange: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onAddMissing: () -> Unit,
    onMealTypeChange: (MealType) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onMemoChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetryAnalysis: () -> Unit,
    onRetake: () -> Unit,
    onManual: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사진 분석 결과") },
                navigationIcon = {
                    IconButton(onClick = onManual) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "직접 입력으로 돌아가기")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AsyncImage(
                    model = File(state.photoPath),
                    contentDescription = "분석한 음식 사진",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("사진 속 음식 후보와 예상량입니다.", style = MaterialTheme.typography.titleMedium)
                        Text("음식 후보는 검증된 영양 데이터와 연결될 때만 칼로리를 자동 계산합니다. 재료와 조리법에 따라 달라질 수 있어요.")
                        if (state.analysis.isPartial) {
                            Text("분석이 확실하지 않습니다. 각 항목을 확인하고 수정해 주세요.")
                        }
                        state.analysis.warnings.forEach { Text("• $it") }
                    }
                }
            }
            items(state.items, key = { it.itemId }) { item ->
                EditablePhotoFoodCard(
                    item = item,
                    onNameChange = { onNameChange(item.itemId, it) },
                    onAmountChange = { onAmountChange(item.itemId, it) },
                    onUnitChange = { onUnitChange(item.itemId, it) },
                    onCaloriesChange = { onCaloriesChange(item.itemId, it) },
                    onSelectedChange = { onSelectedChange(item.itemId, it) },
                    onRemove = { onRemove(item.itemId) }
                )
            }
            item {
                OutlinedButton(onClick = onAddMissing, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = "누락된 음식 추가")
                    Text("누락된 음식 추가")
                }
            }
            item {
                Text("선택 항목 합계 약 ${state.selectedTotalCalories} kcal", style = MaterialTheme.typography.titleLarge)
                val minimum = state.selectedMinimumCalories
                val maximum = state.selectedMaximumCalories
                if (minimum != null && maximum != null) {
                    Text("예상 범위 $minimum~$maximum kcal")
                    Text(
                        "검증된 기준 열량과 사진의 추정 섭취량을 바탕으로 ±20% 범위를 표시합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { HorizontalDivider() }
            item { MealTypeSelector(entryState.mealType, onMealTypeChange) }
            item {
                DateTimeSelectors(entryState.date, entryState.time, onDateChange, onTimeChange)
            }
            item {
                OutlinedTextField(
                    value = entryState.memo,
                    onValueChange = onMemoChange,
                    label = { Text("메모 (선택 사항)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state.saveError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(
                    onClick = onSave,
                    enabled = state.items.any { it.selected } && !state.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Text("확인한 내용으로 기록 저장")
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRetryAnalysis, modifier = Modifier.weight(1f)) { Text("다시 분석") }
                    TextButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("다시 촬영") }
                }
            }
            item { TextButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("직접 입력으로 전환") } }
        }
    }
}

@Composable
private fun EditablePhotoFoodCard(
    item: EditablePhotoFoodItem,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onSelectedChange: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.selected, onCheckedChange = onSelectedChange)
                Text("기록에 포함", modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = "인식한 음식 삭제")
                }
            }
            item.aiEstimatedCalories?.let { Text("AI 예상값: 약 $it kcal") }
            item.nutritionMatchMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.matchedFoodItemId == null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            if (item.minimumKcal != null && item.maximumKcal != null) {
                Text("예상 범위 ${item.minimumKcal}~${item.maximumKcal} kcal")
            }
            Text(
                when {
                    item.confidenceLevel == "LOW" ->
                        "분석 신뢰도: 낮음 — 음식 이름과 양을 확인해 주세요."
                    item.confidenceLevel == "MEDIUM" -> "분석 신뢰도: 보통"
                    item.confidenceLevel == "HIGH" -> "분석 신뢰도: 높음"
                    item.confidence == null -> "신뢰도 정보 없음 — 음식 이름과 양을 확인해 주세요."
                    item.confidence < PhotoAnalysisPolicy.LOW_CONFIDENCE_THRESHOLD ->
                        "분석이 확실하지 않습니다. 음식 이름과 양을 확인해 주세요."
                    else -> "분석 신뢰도 ${(item.confidence * 100).toInt()}%"
                },
                style = MaterialTheme.typography.bodySmall
            )
            item.assumptions.forEach { assumption ->
                Text("가정: $assumption", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = item.foodName,
                onValueChange = onNameChange,
                label = { Text("음식 이름") },
                isError = item.nameError != null,
                supportingText = item.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.amount,
                    onValueChange = onAmountChange,
                    label = { Text("섭취량") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.amountUnit,
                    onValueChange = onUnitChange,
                    label = { Text("단위") },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = item.calories,
                onValueChange = onCaloriesChange,
                label = { Text("최종 칼로리 (kcal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = item.caloriesError != null,
                supportingText = item.caloriesError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            item.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoAnalysisErrorScreen(
    state: PhotoAnalysisUiState.Error,
    onRetry: () -> Unit,
    onRetake: () -> Unit,
    onManual: () -> Unit,
    onCancel: () -> Unit
) {
    val (title, message) = errorText(state.type)
    val retryable = state.type in setOf(
        PhotoAnalysisError.NETWORK,
        PhotoAnalysisError.TIMEOUT,
        PhotoAnalysisError.SERVER,
        PhotoAnalysisError.MALFORMED_RESPONSE,
        PhotoAnalysisError.RATE_LIMITED
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "분석 취소")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            state.photoPath?.let {
                AsyncImage(
                    model = File(it),
                    contentDescription = "분석하지 못한 음식 사진",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(20.dp))
            if (retryable) {
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("다시 시도") }
            }
            OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) { Text("다시 촬영") }
            TextButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("직접 입력") }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("취소") }
        }
    }
}

@Composable
private fun PhotoEntryErrorDialog(
    error: PhotoAnalysisError,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val (title, message) = errorText(error)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            if (error == PhotoAnalysisError.PERMISSION_PERMANENTLY_DENIED) {
                Button(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "앱 권한 설정 열기")
                    Text("설정 열기")
                }
            } else {
                Button(onClick = onDismiss) { Text("확인") }
            }
        },
        dismissButton = {
            if (error == PhotoAnalysisError.PERMISSION_PERMANENTLY_DENIED) {
                TextButton(onClick = onDismiss) { Text("직접 입력 계속") }
            }
        }
    )
}

@Composable
private fun MealTypeSelector(selected: MealType, onChange: (MealType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("식사 유형", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MealType.entries) { type ->
                FilterChip(selected = selected == type, onClick = { onChange(type) }, label = { Text(type.displayName) })
            }
        }
    }
}

@Composable
private fun DateTimeSelectors(
    date: LocalDate,
    time: LocalTime,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
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
        ) { Text("날짜 ${date.format(dateFormatter)}") }
        OutlinedButton(
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
        ) { Text("시간 ${time.format(timeFormatter)}") }
    }
}

private fun errorText(error: PhotoAnalysisError): Pair<String, String> = when (error) {
    PhotoAnalysisError.PERMISSION_DENIED -> "카메라 권한이 없습니다" to
        "사진 촬영을 사용하지 않아도 직접 입력과 저장된 음식 선택은 계속 사용할 수 있습니다."
    PhotoAnalysisError.PERMISSION_PERMANENTLY_DENIED -> "카메라 권한을 허용해 주세요" to
        "사진으로 기록하려면 앱 설정에서 카메라 권한을 허용해 주세요."
    PhotoAnalysisError.CAMERA_UNAVAILABLE -> "카메라를 사용할 수 없습니다" to
        "이 기기에서 카메라를 열 수 없습니다. 직접 입력을 이용해 주세요."
    PhotoAnalysisError.CAMERA_CAPTURE_FAILED -> "사진을 촬영하지 못했습니다" to
        "카메라 연결을 확인한 후 다시 시도하거나 직접 입력해 주세요."
    PhotoAnalysisError.FOOD_NOT_DETECTED -> "음식을 확인하지 못했습니다" to
        "다른 각도에서 다시 촬영하거나 직접 입력해 주세요."
    PhotoAnalysisError.INVALID_IMAGE -> "사진을 처리하지 못했습니다" to
        "지원되는 음식 사진을 다시 촬영해 주세요."
    PhotoAnalysisError.IMAGE_TOO_LARGE -> "사진 크기를 줄이지 못했습니다" to
        "다른 해상도로 다시 촬영해 주세요."
    PhotoAnalysisError.UNSUPPORTED_IMAGE -> "지원하지 않는 사진 형식입니다" to
        "JPEG 또는 PNG 형식으로 다시 촬영해 주세요."
    PhotoAnalysisError.NETWORK -> "인터넷 연결이 필요합니다" to
        "사진 분석에는 인터넷 연결이 필요합니다. 직접 입력은 계속 사용할 수 있습니다."
    PhotoAnalysisError.TIMEOUT -> "분석 시간이 초과되었습니다" to
        "네트워크를 확인하고 다시 시도하거나 직접 입력해 주세요."
    PhotoAnalysisError.AUTHENTICATION -> "분석 서비스 인증이 필요합니다" to
        "서비스 설정을 확인해야 합니다. 직접 입력은 계속 사용할 수 있습니다."
    PhotoAnalysisError.RATE_LIMITED -> "잠시 후 다시 시도해 주세요" to
        "분석 요청 한도에 도달했습니다. 연속으로 재시도하지 말고 직접 입력을 이용할 수 있습니다."
    PhotoAnalysisError.MALFORMED_RESPONSE -> "분석 결과를 읽지 못했습니다" to
        "유효한 분석 결과를 받지 못했습니다. 다시 시도하거나 직접 입력해 주세요."
    PhotoAnalysisError.SERVER -> "사진 분석에 실패했습니다" to
        "분석 서버에 문제가 발생했습니다. 잠시 후 다시 시도하거나 직접 입력해 주세요."
    PhotoAnalysisError.SERVICE_NOT_CONFIGURED -> "분석 서버가 설정되지 않았습니다" to
        "안전한 음식 분석 백엔드 주소와 인증 방식이 아직 연결되지 않았습니다. 사진은 전송되지 않았으며 직접 입력은 사용할 수 있습니다."
    PhotoAnalysisError.CANCELLED -> "분석이 취소되었습니다" to "다시 촬영하거나 직접 입력해 주세요."
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun <T> StateFlowCompat<T>.collectAsStateCompat() = collectAsState()

private typealias StateFlowCompat<T> = kotlinx.coroutines.flow.StateFlow<T>

@Preview(showBackground = true)
@Composable
fun AddRecordScreenPreview() {
    HealthCareTheme { AddRecordScreen() }
}
