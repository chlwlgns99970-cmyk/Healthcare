package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.RecognitionCandidate
import com.example.healthcare.data.entity.RecognitionSession
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.data.photo.FoodPhotoAnalysisRepository
import com.example.healthcare.data.photo.FoodPhotoProcessor
import com.example.healthcare.data.photo.UnconfiguredFoodPhotoAnalysisRepository
import com.example.healthcare.data.photo.model.DetectedFoodItem
import com.example.healthcare.data.photo.model.FoodPhotoAnalysis
import com.example.healthcare.data.photo.model.FoodPhotoAnalysisRequest
import com.example.healthcare.data.photo.model.PhotoAnalysisOutcome
import com.example.healthcare.data.photo.model.PhotoAnalysisProgress
import com.example.healthcare.data.recognition.KoreanNutritionLabelRecognizer
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.data.repository.RecognitionRepository
import com.example.healthcare.domain.NutritionBasisCandidate
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.FoodBrowseCategory
import com.example.healthcare.domain.NutritionLabelParseResult
import com.example.healthcare.domain.NutritionLabelParser
import com.example.healthcare.domain.MealRecommendationEngine
import com.example.healthcare.domain.Macronutrients
import com.example.healthcare.domain.PortionEstimationType
import com.example.healthcare.domain.PortionFraction
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.PortionPreset
import com.example.healthcare.domain.PortionVessel
import com.example.healthcare.util.CalorieUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

class AddRecordViewModel(
    private val mealRepository: MealRepository,
    private val foodRepository: FoodRepository,
    private val photoAnalysisRepository: FoodPhotoAnalysisRepository = UnconfiguredFoodPhotoAnalysisRepository(),
    private val photoProcessor: FoodPhotoProcessor? = null,
    private val nutritionRepository: NutritionRepository? = null,
    private val nutritionLabelRecognizer: KoreanNutritionLabelRecognizer? = null,
    private val recognitionRepository: RecognitionRepository? = null
) : ViewModel() {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val _uiState = MutableStateFlow(AddRecordUiState())
    val uiState: StateFlow<AddRecordUiState> = _uiState.asStateFlow()

    private val _photoState = MutableStateFlow<PhotoAnalysisUiState>(PhotoAnalysisUiState.Idle)
    val photoState: StateFlow<PhotoAnalysisUiState> = _photoState.asStateFlow()
    private var analysisJob: Job? = null
    private var searchJob: Job? = null
    private var capturePurpose: CapturePurpose = CapturePurpose.FOOD_PHOTO
    private var barcodeScanActive = false

    private val _smartInputState = MutableStateFlow(
        SmartInputUiState(photoAnalysisAvailable = photoAnalysisRepository.isConfigured)
    )
    val smartInputState: StateFlow<SmartInputUiState> = _smartInputState.asStateFlow()

    val frequentFoods: StateFlow<List<FrequentFood>> = foodRepository.allFoods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteFoods: StateFlow<List<FrequentFood>> = foodRepository.favoriteFoods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentMeals: StateFlow<List<MealRecord>> = mealRepository.allMeals
        .map { records -> records.distinctBy { it.foodName.trim().lowercase() }.take(8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedDateMeals: StateFlow<List<MealRecord>> = _uiState
        .map { it.date }
        .distinctUntilChanged()
        .flatMapLatest { date -> mealRepository.getMealsByDate(date.format(dateFormatter)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            photoProcessor?.cleanOldTemporaryPhotos()
            recognitionRepository?.cleanExpired(System.currentTimeMillis() - RECOGNITION_RETENTION_MILLIS)
        }
    }

    fun onFoodNameChange(name: String) {
        _uiState.update {
            val keepLabelEstimate = it.recordSource == RecordSource.NUTRITION_LABEL && it.selectedFoodItemId == null
            it.copy(
                foodName = name,
                nameError = null,
                referenceCalories = if (keepLabelEstimate) it.referenceCalories else null,
                referenceServingAmount = if (keepLabelEstimate) it.referenceServingAmount else null,
                referenceServingUnit = if (keepLabelEstimate) it.referenceServingUnit else null,
                selectedServingRatio = if (keepLabelEstimate) it.selectedServingRatio else null,
                selectedFoodItemId = null,
                selectedFood = null,
                estimatedCarbohydrateGrams = if (keepLabelEstimate) it.estimatedCarbohydrateGrams else null,
                estimatedProteinGrams = if (keepLabelEstimate) it.estimatedProteinGrams else null,
                estimatedFatGrams = if (keepLabelEstimate) it.estimatedFatGrams else null,
                selectedPortion = if (keepLabelEstimate) it.selectedPortion else null,
                selectedBarcode = it.pendingProductBarcode,
                recordSource = if (keepLabelEstimate) RecordSource.NUTRITION_LABEL
                    else if (it.pendingProductBarcode != null) RecordSource.BARCODE else RecordSource.MANUAL
            )
        }
    }

    fun onCaloriesChange(calories: String) {
        _uiState.update { it.copy(calories = calories, caloriesError = null, selectedServingRatio = null) }
    }

    fun onServingAmountChange(amount: String) {
        _uiState.update { state ->
            val recalculated = state.recalculateReferenceCalories(amount, state.servingUnit)
            state.copy(
                servingAmount = amount,
                calories = if (state.hasReferenceFood()) recalculated?.first.orEmpty() else state.calories,
                selectedServingRatio = recalculated?.second,
                selectedPortion = null,
                caloriesError = null,
                saveError = null
            )
        }
    }

    fun onServingUnitChange(unit: String) {
        _uiState.update { state ->
            val recalculated = state.recalculateReferenceCalories(state.servingAmount, unit)
            state.copy(
                servingUnit = unit,
                calories = if (state.hasReferenceFood()) recalculated?.first.orEmpty() else state.calories,
                selectedServingRatio = recalculated?.second,
                selectedPortion = null,
                caloriesError = null
            )
        }
    }

    fun onFoodSearchChange(query: String) {
        _uiState.update { it.copy(foodSearch = query) }
        when {
            _smartInputState.value.isCompanionSearch -> searchVerifiedFoods(query)
            _smartInputState.value.selectedBrand != null -> searchSelectedBrandProducts(query)
            _smartInputState.value.searchMode == FoodSearchMode.BRAND -> searchProductBrands(query)
            _smartInputState.value.searchMode == FoodSearchMode.FRANCHISE -> searchFranchiseBrands(query)
            else -> searchVerifiedFoods(query)
        }
    }

    fun onCompanionSearchChange(query: String) {
        searchVerifiedFoods(query)
    }

    private fun searchVerifiedFoods(query: String) {
        _smartInputState.update { it.copy(searchQuery = query, message = null, brandResults = emptyList()) }
        searchJob?.cancel()
        val category = _smartInputState.value.selectedFoodCategory
        if ((query.isBlank() && category == FoodBrowseCategory.ALL) || nutritionRepository == null) {
            _smartInputState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _smartInputState.update { it.copy(isSearching = true) }
            val foodResults = nutritionRepository.browse(category, query)
            val franchiseResults = if (query.isBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                nutritionRepository.searchFranchiseBrands(query)
            }
            combine(
                foodResults,
                franchiseResults
            ) { foods, franchises -> foods to franchises }.collectLatest { (results, franchises) ->
                _smartInputState.update {
                    it.copy(searchResults = results, brandResults = franchises, isSearching = false)
                }
            }
        }
    }

    fun selectFoodCategory(category: FoodBrowseCategory) {
        if (_smartInputState.value.isCompanionSearch || _smartInputState.value.searchMode != FoodSearchMode.FOOD) return
        _smartInputState.update { it.copy(selectedFoodCategory = category) }
        searchVerifiedFoods(_smartInputState.value.searchQuery)
    }

    fun selectFoodSearchMode(mode: FoodSearchMode) {
        if (_smartInputState.value.isCompanionSearch) return
        searchJob?.cancel()
        _smartInputState.update {
            it.copy(
                searchMode = mode,
                searchQuery = "",
                searchResults = emptyList(),
                brandResults = emptyList(),
                selectedBrand = null,
                brandProducts = emptyList(),
                brandCategories = emptyList(),
                selectedBrandCategory = null,
                selectedFoodCategory = FoodBrowseCategory.ALL,
                isSearching = false,
                message = null
            )
        }
        when (mode) {
            FoodSearchMode.BRAND -> searchProductBrands("")
            FoodSearchMode.FRANCHISE -> searchFranchiseBrands("")
            FoodSearchMode.FOOD -> Unit
        }
    }

    private fun searchProductBrands(query: String) {
        val repository = nutritionRepository
        _smartInputState.update {
            it.copy(searchQuery = query, message = null, searchResults = emptyList())
        }
        searchJob?.cancel()
        if (repository == null) {
            _smartInputState.update { it.copy(brandResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _smartInputState.update { it.copy(isSearching = true) }
            repository.searchProductBrands(query).collectLatest { results ->
                _smartInputState.update { it.copy(brandResults = results, isSearching = false) }
            }
        }
    }

    private fun searchFranchiseBrands(query: String, filterQuery: String = query) {
        val repository = nutritionRepository
        _smartInputState.update {
            it.copy(searchQuery = query, message = null, searchResults = emptyList())
        }
        searchJob?.cancel()
        if (repository == null) {
            _smartInputState.update { it.copy(brandResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _smartInputState.update { it.copy(isSearching = true) }
            repository.searchFranchiseBrands(filterQuery).collectLatest { results ->
                _smartInputState.update { it.copy(brandResults = results, isSearching = false) }
            }
        }
    }

    fun selectProductBrand(brand: FoodBrandSummary) {
        searchJob?.cancel()
        _smartInputState.update {
            it.copy(
                searchMode = if (
                    it.searchMode == FoodSearchMode.FOOD &&
                    brand.brand in com.example.healthcare.domain.FranchiseCatalog.brands
                ) FoodSearchMode.FRANCHISE else it.searchMode,
                selectedBrand = brand,
                searchQuery = "",
                brandResults = emptyList(),
                brandProducts = emptyList(),
                brandCategories = emptyList(),
                selectedBrandCategory = null,
                message = null
            )
        }
        searchSelectedBrandProducts("")
    }

    fun clearSelectedProductBrand() {
        searchJob?.cancel()
        _smartInputState.update {
            it.copy(
                selectedBrand = null,
                searchQuery = "",
                brandProducts = emptyList(),
                brandCategories = emptyList(),
                selectedBrandCategory = null,
                isSearching = false,
                message = null
            )
        }
        when (_smartInputState.value.searchMode) {
            FoodSearchMode.FRANCHISE -> searchFranchiseBrands("")
            FoodSearchMode.BRAND -> searchProductBrands("")
            FoodSearchMode.FOOD -> Unit
        }
    }

    fun selectBrandCategory(category: String?) {
        val state = _smartInputState.value
        _smartInputState.update { it.copy(selectedBrandCategory = category) }
        if (state.searchMode == FoodSearchMode.FRANCHISE && state.selectedBrand == null) {
            searchFranchiseBrands("", category.orEmpty())
        }
    }

    private fun searchSelectedBrandProducts(query: String) {
        val repository = nutritionRepository
        val selectedBrand = _smartInputState.value.selectedBrand
        _smartInputState.update { it.copy(searchQuery = query, message = null) }
        searchJob?.cancel()
        if (repository == null || selectedBrand == null) {
            _smartInputState.update { it.copy(brandProducts = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _smartInputState.update { it.copy(isSearching = true) }
            val productsFlow = if (_smartInputState.value.searchMode == FoodSearchMode.FRANCHISE) {
                repository.searchFranchiseFoods(selectedBrand.brand, query)
            } else {
                repository.searchProductsByBrand(selectedBrand.brand, query)
            }
            productsFlow.collectLatest { products ->
                _smartInputState.update { state ->
                    state.copy(
                        brandProducts = products,
                        brandCategories = if (query.isBlank()) {
                            products.mapNotNull(FoodItem::category).distinct().sorted()
                        } else state.brandCategories,
                        isSearching = false
                    )
                }
            }
        }
    }

    fun showSmartInputHub() {
        barcodeScanActive = false
        _smartInputState.update {
            it.copy(
                mode = SmartInputMode.HUB,
                message = null,
                barcodeItem = null,
                ocrResult = null,
                selectedOcrCandidateIndex = null,
                ocrConsumedAmount = "",
                ocrPortionPreset = null,
                ocrPreciseOpen = false,
                isCompanionSearch = false,
                searchMode = FoodSearchMode.FOOD,
                selectedBrand = null,
                brandResults = emptyList(),
                brandProducts = emptyList(),
                brandCategories = emptyList(),
                selectedBrandCategory = null
            )
        }
    }

    fun showManualEntry() {
        _smartInputState.update { it.copy(mode = SmartInputMode.MANUAL, message = null, isCompanionSearch = false) }
    }

    fun showBarcodeManualRegistration() {
        val barcode = _smartInputState.value.barcode.takeIf(String::isNotBlank) ?: return
        _uiState.update {
            AddRecordUiState(
                mealType = it.mealType,
                date = it.date,
                time = it.time,
                pendingProductBarcode = barcode,
                selectedBarcode = barcode,
                recordSource = RecordSource.BARCODE
            )
        }
        _smartInputState.update { it.copy(mode = SmartInputMode.MANUAL, message = null) }
    }

    fun showFoodSearch() {
        _smartInputState.update {
            it.copy(
                mode = SmartInputMode.SEARCH,
                message = null,
                isCompanionSearch = false,
                searchMode = FoodSearchMode.FOOD,
                selectedBrand = null,
                brandResults = emptyList(),
                brandProducts = emptyList(),
                brandCategories = emptyList(),
                selectedBrandCategory = null
            )
        }
    }

    fun showCompanionSearch(initialQuery: String = "") {
        if (_uiState.value.selectedFood == null) {
            _uiState.update { it.copy(saveError = "먼저 음식 검색에서 기본 음식을 선택해 주세요.") }
            return
        }
        _smartInputState.update {
            it.copy(mode = SmartInputMode.SEARCH, message = null, isCompanionSearch = true,
                searchMode = FoodSearchMode.FOOD, searchQuery = initialQuery, searchResults = emptyList(),
                selectedBrand = null, brandResults = emptyList(), brandProducts = emptyList(),
                brandCategories = emptyList(), selectedBrandCategory = null)
        }
        if (initialQuery.isNotBlank()) searchVerifiedFoods(initialQuery)
    }

    fun cancelCompanionSearch() {
        _smartInputState.update {
            it.copy(mode = SmartInputMode.MANUAL, message = null, isCompanionSearch = false,
                searchQuery = "", searchResults = emptyList(), isSearching = false)
        }
    }

    fun selectSearchFood(food: FoodItem) {
        if (_smartInputState.value.isCompanionSearch) addCompanionFood(food) else selectVerifiedFood(food)
    }

    fun selectVerifiedFood(food: FoodItem, source: RecordSource = RecordSource.FOOD_SEARCH) {
        if (FoodSearchPolicy.needsBasisReview(food)) {
            _smartInputState.update {
                it.copy(message = "이 항목은 공식 영양정보 단위를 일상적인 섭취량으로 바로 바꾸기 어려워요. 다른 항목을 선택하거나 직접 입력해 주세요.")
            }
            return
        }
        val amount = food.referenceAmount
        val requiresDirectAmount = PortionGuide.requiresDirectAmount(food)
        _uiState.update {
            it.copy(
                foodName = FoodSearchPolicy.displayName(food),
                calories = if (requiresDirectAmount) "" else food.energyKcal.roundToInt().toString(),
                servingAmount = if (requiresDirectAmount) "" else formatAmount(amount),
                servingUnit = food.unit,
                foodSearch = "",
                referenceCalories = food.energyKcal.roundToInt(),
                referenceServingAmount = amount,
                referenceServingUnit = food.unit,
                selectedServingRatio = if (requiresDirectAmount) null else 1.0,
                selectedFoodItemId = food.id,
                selectedFood = food,
                estimatedCarbohydrateGrams = null,
                estimatedProteinGrams = null,
                estimatedFatGrams = null,
                companionFoods = emptyList(),
                selectedPortion = null,
                portionHelpOpen = false,
                selectedVessel = null,
                preciseAmountOpen = requiresDirectAmount,
                selectedBarcode = food.barcode,
                pendingProductBarcode = null,
                recordSource = source,
                nameError = null,
                caloriesError = null
            )
        }
        _smartInputState.update {
            it.copy(
                mode = SmartInputMode.MANUAL,
                searchQuery = "",
                searchResults = emptyList(),
                selectedBrand = null,
                brandResults = emptyList(),
                brandProducts = emptyList(),
                brandCategories = emptyList(),
                selectedBrandCategory = null
            )
        }
        viewModelScope.launch {
            runCatching {
                persistRecognition(
                    sourceType = if (source == RecordSource.BARCODE) "BARCODE" else "FOOD_SEARCH",
                    detectedName = food.name,
                    matchedFoodItemId = food.id,
                    amount = food.referenceAmount,
                    unit = food.unit,
                    calories = food.energyKcal.roundToInt()
                )
            }
        }
    }

    fun beginBarcodeScan(): Boolean {
        if (barcodeScanActive) return false
        barcodeScanActive = true
        _smartInputState.update { it.copy(mode = SmartInputMode.BARCODE_LOADING, message = null) }
        return true
    }

    fun cancelBarcodeScan() {
        barcodeScanActive = false
        _smartInputState.update { it.copy(mode = SmartInputMode.HUB, message = null) }
    }

    fun showBarcodeScanError() {
        barcodeScanActive = false
        _smartInputState.update {
            it.copy(mode = SmartInputMode.HUB, message = "바코드 스캐너를 열지 못했습니다. 다시 시도하거나 다른 기록 방법을 사용해 주세요.")
        }
    }

    fun lookupBarcode(rawValue: String) {
        barcodeScanActive = false
        val barcode = rawValue.filter(Char::isDigit)
        if (barcode.isBlank() || nutritionRepository == null) {
            _smartInputState.update {
                it.copy(mode = SmartInputMode.BARCODE_NOT_FOUND, barcode = barcode, message = "상품 정보를 찾을 수 없습니다.")
            }
            return
        }
        viewModelScope.launch {
            _smartInputState.update { it.copy(mode = SmartInputMode.BARCODE_LOADING, barcode = barcode, message = null) }
            val item = nutritionRepository.findByBarcode(barcode)
            if (item == null) {
                _smartInputState.update {
                    it.copy(mode = SmartInputMode.BARCODE_NOT_FOUND, barcode = barcode, message = "등록된 상품 영양정보가 없습니다.")
                }
            } else {
                _smartInputState.update { it.copy(mode = SmartInputMode.BARCODE_RESULT, barcode = barcode, barcodeItem = item) }
            }
        }
    }

    fun useBarcodeItem() {
        _smartInputState.value.barcodeItem?.let { selectVerifiedFood(it, RecordSource.BARCODE) }
    }

    fun onMealTypeChange(type: MealType) {
        _uiState.update { it.copy(mealType = type) }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun onTimeChange(time: LocalTime) {
        _uiState.update { it.copy(time = time) }
    }

    fun onMemoChange(memo: String) {
        _uiState.update { it.copy(memo = memo) }
    }

    fun onSaveAsFrequentChange(save: Boolean) {
        _uiState.update { it.copy(saveAsFrequent = save) }
    }

    fun selectFrequentFood(food: FrequentFood) {
        val (servingAmount, servingUnit) = parseServing(food.defaultServing)
        _uiState.update {
            it.copy(
                foodName = food.foodName,
                calories = food.calories.toString(),
                servingAmount = formatAmount(servingAmount),
                servingUnit = servingUnit,
                foodSearch = "",
                referenceCalories = food.calories,
                referenceServingAmount = servingAmount,
                referenceServingUnit = servingUnit,
                selectedServingRatio = 1.0,
                selectedFoodItemId = null,
                selectedFood = null,
                estimatedCarbohydrateGrams = null,
                estimatedProteinGrams = null,
                estimatedFatGrams = null,
                selectedPortion = null,
                selectedBarcode = null,
                pendingProductBarcode = null,
                recordSource = RecordSource.SAVED_FOOD,
                nameError = null,
                caloriesError = null
            )
        }
        _smartInputState.update { it.copy(mode = SmartInputMode.MANUAL, message = null) }
    }

    fun deleteFrequentFood(food: FrequentFood) {
        viewModelScope.launch {
            foodRepository.deleteFood(food)
        }
    }

    fun selectRecentMeal(meal: MealRecord) {
        val servingAmount = meal.servingAmount ?: 1.0
        val servingUnit = meal.servingUnit?.takeIf { it.isNotBlank() } ?: "인분"
        _uiState.update {
            it.copy(
                foodName = meal.foodName,
                calories = meal.calories.toString(),
                servingAmount = formatAmount(servingAmount),
                servingUnit = servingUnit,
                foodSearch = "",
                referenceCalories = meal.calories,
                referenceServingAmount = servingAmount,
                referenceServingUnit = servingUnit,
                selectedServingRatio = 1.0,
                selectedFoodItemId = meal.foodItemId,
                selectedFood = null,
                estimatedCarbohydrateGrams = meal.carbohydrateGrams,
                estimatedProteinGrams = meal.proteinGrams,
                estimatedFatGrams = meal.fatGrams,
                selectedPortion = meal.portionDisplayLabel?.let { label ->
                    PortionPreset(
                        id = meal.portionPresetId ?: "previous",
                        label = label,
                        amount = servingAmount,
                        unit = servingUnit,
                        estimationType = runCatching { PortionEstimationType.valueOf(meal.portionEstimationType.orEmpty()) }
                            .getOrDefault(PortionEstimationType.VISUAL_ESTIMATE),
                        sourceReference = meal.portionSourceReference.orEmpty(),
                        description = "이전에 저장한 양이에요."
                    )
                },
                selectedBarcode = meal.barcode,
                pendingProductBarcode = null,
                recordSource = RecordSource.RECENT_REPEAT,
                nameError = null,
                caloriesError = null
            )
        }
    }

    fun repeatRecentMeal(meal: MealRecord, onSuccess: () -> Unit) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                mealRepository.insertMeal(
                    meal.copy(
                        id = 0,
                        date = LocalDate.now().format(dateFormatter),
                        time = LocalTime.now().format(timeFormatter),
                        source = RecordSource.RECENT_REPEAT,
                        photoAnalysisId = null,
                        photoRequestId = null,
                        photoItemId = null,
                        plannedMealId = null,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                )
                onSuccess()
            } catch (_: Exception) {
                _uiState.update { it.copy(saveError = "최근 음식을 다시 기록하지 못했습니다.") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun selectServingRatio(ratio: Double) {
        val state = _uiState.value
        val referenceCalories = state.referenceCalories ?: return
        val referenceAmount = state.referenceServingAmount ?: return
        val referenceUnit = state.referenceServingUnit ?: return
        _uiState.update {
            it.copy(
                servingAmount = formatAmount(referenceAmount * ratio),
                servingUnit = referenceUnit,
                calories = (referenceCalories * ratio).roundToInt().toString(),
                selectedServingRatio = ratio,
                selectedPortion = null,
                caloriesError = null
            )
        }
    }

    fun updateCompanionPortion(selectionId: String, preset: PortionPreset) {
        _uiState.update { state ->
            val updated = state.companionFoods.map { entry ->
                if (entry.selectionId != selectionId || PortionGuide.presets(entry.food).none { it.id == preset.id }) entry
                else PortionGuide.estimate(entry.food, preset)?.let { estimate ->
                    entry.copy(portion = preset, calories = estimate.calories)
                } ?: entry
            }
            state.copy(companionFoods = updated, saveError = null)
        }
    }

    fun removeCompanionFood(selectionId: String) {
        _uiState.update { state ->
            state.copy(companionFoods = state.companionFoods.filterNot { it.selectionId == selectionId }, saveError = null)
        }
    }

    private fun addCompanionFood(food: FoodItem) {
        if (FoodSearchPolicy.needsBasisReview(food)) {
            _smartInputState.update { it.copy(message = "이 항목은 공식 영양정보 단위를 일상적인 섭취량으로 바로 바꾸기 어려워요.") }
            return
        }
        val portion = PortionGuide.defaultPreset(food)
        val estimate = portion?.let { PortionGuide.estimate(food, it) }
        if (portion == null || estimate == null) {
            _smartInputState.update { it.copy(message = "이 음식은 계산에 사용할 수 있는 영양정보 양이 없어 함께 추가할 수 없습니다.") }
            return
        }
        _uiState.update { state ->
            state.copy(
                companionFoods = state.companionFoods + CompanionFoodEntry(
                    selectionId = UUID.randomUUID().toString(),
                    food = food,
                    portion = portion,
                    calories = estimate.calories
                ),
                saveError = null
            )
        }
        cancelCompanionSearch()
    }

    fun useCustomServing() {
        _uiState.update { it.copy(selectedServingRatio = null, selectedPortion = null,
            preciseAmountOpen = true, saveError = null) }
    }

    fun selectPortionPreset(preset: PortionPreset) {
        val state = _uiState.value
        val food = state.selectedFood ?: return
        if (PortionGuide.presets(food).none { it.id == preset.id }) return
        applyPortion(preset)
    }

    fun startUnknownPortion() {
        _uiState.update { it.copy(portionHelpOpen = true, selectedVessel = null) }
    }

    fun choosePortionVessel(vessel: PortionVessel) {
        _uiState.update { it.copy(portionHelpOpen = true, selectedVessel = vessel) }
    }

    fun choosePortionFraction(fraction: PortionFraction) {
        val state = _uiState.value
        val vessel = state.selectedVessel ?: return
        val food = state.selectedFood
        if (food != null) {
            val preset = PortionGuide.visualEstimate(food, vessel, fraction) ?: return
            applyPortion(preset)
        } else {
            val referenceAmount = state.referenceServingAmount ?: return
            val unit = state.referenceServingUnit ?: return
            val kcal = state.referenceCalories ?: return
            val estimatedAmount = referenceAmount * fraction.ratio
            val preset = PortionPreset(
                id = "visual-${vessel.name}-${fraction.name}",
                label = "${vessel.label} · ${fraction.label}",
                amount = estimatedAmount,
                unit = unit,
                estimationType = PortionEstimationType.VISUAL_ESTIMATE,
                sourceReference = "저장된 영양정보에 표시된 양",
                description = "실제 그릇 크기는 측정하지 않았어요. 영양정보에 표시된 양에 먹은 비율을 적용한 매우 대략적인 값이에요."
            )
            _uiState.update {
                it.copy(
                    servingAmount = formatAmount(estimatedAmount), servingUnit = unit,
                    calories = (kcal * fraction.ratio).roundToInt().coerceAtLeast(1).toString(),
                    selectedPortion = preset, selectedServingRatio = fraction.ratio,
                    caloriesError = null, saveError = null
                )
            }
        }
        _uiState.update { it.copy(portionHelpOpen = false) }
    }

    fun selectReferencePortion(ratio: Double, label: String) {
        val state = _uiState.value
        val amount = state.referenceServingAmount?.takeIf { it > 0.0 } ?: return
        val unit = state.referenceServingUnit ?: return
        val calories = state.referenceCalories ?: return
        if (ratio !in 0.1..2.0) return
        if (calories.toDouble() * ratio > Int.MAX_VALUE) return
        val preset = PortionPreset(
            id = "reference-$ratio",
            label = label,
            amount = amount * ratio,
            unit = unit,
            estimationType = PortionEstimationType.VISUAL_ESTIMATE,
            sourceReference = "저장된 영양정보에 표시된 양",
            description = "저장된 영양정보 양에 대한 대략적인 비율이에요."
        )
        val calculated = (calories.toDouble() * ratio).roundToInt().coerceAtLeast(1)
        _uiState.update {
            it.copy(
                servingAmount = formatAmount(preset.amount), servingUnit = unit,
                calories = calculated.toString(), selectedPortion = preset,
                selectedServingRatio = ratio, caloriesError = null, saveError = null
            )
        }
    }

    private fun applyPortion(preset: PortionPreset) {
        val food = _uiState.value.selectedFood ?: return
        val estimate = PortionGuide.estimate(food, preset) ?: return
        _uiState.update {
            it.copy(
                servingAmount = formatAmount(estimate.amount), servingUnit = estimate.unit,
                calories = estimate.calories.toString(), selectedPortion = preset,
                selectedServingRatio = estimate.amount / food.referenceAmount,
                caloriesError = null, saveError = null
            )
        }
    }

    fun saveRecord(onSuccess: () -> Unit) {
        val state = _uiState.value
        val manualPhotoPath = (_photoState.value as? PhotoAnalysisUiState.ManualEntry)?.photoPath
        var hasError = false
        if (state.foodName.isBlank()) {
            _uiState.update { it.copy(nameError = "음식 이름을 입력해주세요.") }
            hasError = true
        }

        if (state.selectedFood != null && state.selectedPortion == null && !state.preciseAmountOpen) {
            _uiState.update { it.copy(saveError = "먹은 양을 선택하거나 더 정확히 입력해 주세요.") }
            hasError = true
        }

        if (state.selectedFood?.let(PortionGuide::requiresDirectAmount) == true &&
            parsePositiveAmount(state.servingAmount) == null
        ) {
            _uiState.update { it.copy(saveError = "실제 먹은 양을 0보다 큰 숫자로 입력해 주세요.") }
            hasError = true
        }

        val calorieInt = CalorieUtils.parseAndRoundCalories(state.calories)
        if (calorieInt == null || calorieInt <= 0) {
            _uiState.update { it.copy(caloriesError = "올바른 칼로리를 입력해주세요.") }
            hasError = true
        }

        val productAmount = parsePositiveAmount(state.servingAmount)
        if (state.pendingProductBarcode != null && (productAmount == null || state.servingUnit.isBlank())) {
            _uiState.update { it.copy(saveError = "상품 등록에는 영양정보에 표시된 양과 단위가 필요합니다.") }
            hasError = true
        }

        if (hasError || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val registeredProduct = state.pendingProductBarcode?.let { barcode ->
                    val repository = requireNotNull(nutritionRepository) { "상품 영양 저장소를 사용할 수 없습니다." }
                    val product = FoodItem(
                        id = "user-barcode-$barcode",
                        sourceType = "USER_CONFIRMED",
                        sourceFoodCode = barcode,
                        name = state.foodName.trim(),
                        normalizedName = MealRecommendationEngine.normalizeFoodName(state.foodName),
                        referenceAmount = requireNotNull(productAmount),
                        unit = state.servingUnit.trim(),
                        energyKcal = calorieInt!!.toDouble(),
                        servingDescription = servingDescription(state.servingAmount, state.servingUnit),
                        barcode = barcode,
                        dataVersion = "user-confirmed",
                        createdAt = now,
                        updatedAt = now
                    )
                    repository.upsert(listOf(product))
                    product
                }
                val primaryFood = registeredProduct ?: state.selectedFood
                val primaryNutrition = primaryFood?.let { food ->
                    val directAmount = parsePositiveAmount(state.servingAmount)
                        ?.takeIf { state.servingUnit.trim().equals(food.unit.trim(), ignoreCase = true) }
                    if (directAmount != null) {
                        Macronutrients.forFood(food, directAmount)
                    } else if (food.energyKcal > 0.0) {
                        Macronutrients(food.carbohydrateGrams, food.proteinGrams, food.fatGrams)
                            .scaled(calorieInt!!.toDouble() / food.energyKcal)
                    } else Macronutrients.Unknown
                } ?: Macronutrients(
                    state.estimatedCarbohydrateGrams,
                    state.estimatedProteinGrams,
                    state.estimatedFatGrams
                )
                val primaryRecord = MealRecord(
                        date = state.date.format(dateFormatter),
                        time = state.time.format(timeFormatter),
                        mealType = state.mealType,
                        foodName = state.foodName.trim(),
                        calories = calorieInt!!,
                        carbohydrateGrams = primaryNutrition.carbohydrateGrams,
                        proteinGrams = primaryNutrition.proteinGrams,
                        fatGrams = primaryNutrition.fatGrams,
                        memo = state.memo.takeIf { it.isNotBlank() },
                        servingAmount = parsePositiveAmount(state.servingAmount),
                        servingUnit = state.servingUnit.trim().takeIf { it.isNotBlank() },
                        portionPresetId = state.selectedPortion?.id,
                        portionDisplayLabel = state.selectedPortion?.label,
                        portionEstimationType = state.selectedPortion?.estimationType?.name,
                        portionSourceReference = state.selectedPortion?.sourceReference,
                        source = if (registeredProduct != null) RecordSource.BARCODE else state.recordSource,
                        foodItemId = registeredProduct?.id ?: state.selectedFoodItemId,
                        barcode = registeredProduct?.barcode ?: state.selectedBarcode
                    )
                val companionRecords = state.companionFoods.map { entry ->
                    val nutrition = Macronutrients.forFood(entry.food, entry.portion.amount)
                    MealRecord(
                        date = state.date.format(dateFormatter),
                        time = state.time.format(timeFormatter),
                        mealType = state.mealType,
                        foodName = FoodSearchPolicy.displayName(entry.food),
                        calories = entry.calories,
                        carbohydrateGrams = nutrition.carbohydrateGrams,
                        proteinGrams = nutrition.proteinGrams,
                        fatGrams = nutrition.fatGrams,
                        servingAmount = entry.portion.amount,
                        servingUnit = entry.portion.unit,
                        portionPresetId = entry.portion.id,
                        portionDisplayLabel = entry.portion.label,
                        portionEstimationType = entry.portion.estimationType.name,
                        portionSourceReference = entry.portion.sourceReference,
                        source = RecordSource.FOOD_SEARCH,
                        foodItemId = entry.food.id,
                        barcode = entry.food.barcode,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                }
                if (companionRecords.isEmpty()) mealRepository.insertMeal(primaryRecord)
                else mealRepository.insertPhotoMeals(listOf(primaryRecord) + companionRecords)

                if (state.saveAsFrequent) {
                    foodRepository.insertFoodIfAbsent(
                        FrequentFood(
                            foodName = state.foodName.trim(),
                            defaultServing = servingDescription(state.servingAmount, state.servingUnit),
                            calories = calorieInt,
                            isFavorite = true
                        )
                    )
                }
                if (manualPhotoPath != null) {
                    photoProcessor?.deleteTemporaryPhoto(manualPhotoPath)
                    _photoState.value = PhotoAnalysisUiState.Idle
                }
                _uiState.value = AddRecordUiState()
                showSmartInputHub()
                onSuccess()
            } catch (_: Exception) {
                _uiState.update { it.copy(saveError = "기록을 저장하지 못했습니다. 다시 시도해주세요.") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun openCamera(purpose: CapturePurpose = CapturePurpose.FOOD_PHOTO) {
        deleteCurrentPhoto()
        capturePurpose = purpose
        _photoState.value = PhotoAnalysisUiState.Camera
    }

    fun openNutritionLabelCamera() = openCamera(CapturePurpose.NUTRITION_LABEL)

    fun showCameraPermissionDenied(permanently: Boolean) {
        _photoState.value = PhotoAnalysisUiState.Error(
            type = if (permanently) PhotoAnalysisError.PERMISSION_PERMANENTLY_DENIED
            else PhotoAnalysisError.PERMISSION_DENIED
        )
    }

    fun showCameraUnavailable() {
        if (_photoState.value == PhotoAnalysisUiState.Camera) {
            _photoState.value = PhotoAnalysisUiState.Error(PhotoAnalysisError.CAMERA_UNAVAILABLE)
        }
    }

    fun showCameraCaptureError() {
        if (_photoState.value == PhotoAnalysisUiState.Camera) {
            _photoState.value = PhotoAnalysisUiState.Error(PhotoAnalysisError.CAMERA_CAPTURE_FAILED)
        }
    }

    fun showCameraFileError() {
        if (_photoState.value == PhotoAnalysisUiState.Camera) {
            _photoState.value = PhotoAnalysisUiState.Error(PhotoAnalysisError.INVALID_IMAGE)
        }
    }

    fun onPhotoCaptured(path: String) {
        if (_photoState.value != PhotoAnalysisUiState.Camera) {
            photoProcessor?.deleteTemporaryPhoto(path)
            return
        }
        if (capturePurpose == CapturePurpose.NUTRITION_LABEL) {
            _photoState.value = PhotoAnalysisUiState.Idle
            recognizeNutritionLabel(path)
        } else {
            _photoState.value = PhotoAnalysisUiState.Preview(
                photoPath = path,
                requestId = UUID.randomUUID().toString(),
                capturedAt = Instant.now().toString()
            )
        }
    }

    private fun recognizeNutritionLabel(path: String) {
        val recognizer = nutritionLabelRecognizer
        if (recognizer == null) {
            photoProcessor?.deleteTemporaryPhoto(path)
            _smartInputState.update {
                it.copy(mode = SmartInputMode.HUB, message = "영양성분표 인식 기능을 사용할 수 없습니다.")
            }
            return
        }
        viewModelScope.launch {
            _smartInputState.update { it.copy(mode = SmartInputMode.OCR_PROCESSING, message = null) }
            runCatching { recognizer.recognize(path) }
                .onSuccess { result ->
                    _smartInputState.update {
                        it.copy(
                            mode = SmartInputMode.OCR_RESULT,
                            ocrResult = result,
                            selectedOcrCandidateIndex = result.energyCandidates.indices.firstOrNull(),
                            ocrConsumedAmount = "",
                            ocrPortionPreset = null,
                            ocrPreciseOpen = false,
                            message = if (result.energyCandidates.isEmpty()) {
                                "열량 기준을 찾지 못했습니다. 다시 촬영하거나 직접 입력해 주세요."
                            } else null
                        )
                    }
                }
                .onFailure {
                    _smartInputState.update {
                        it.copy(mode = SmartInputMode.HUB, message = "영양성분표를 읽지 못했습니다. 다시 촬영해 주세요.")
                    }
                }
            photoProcessor?.deleteTemporaryPhoto(path)
            capturePurpose = CapturePurpose.FOOD_PHOTO
        }
    }

    fun selectOcrCandidate(index: Int) {
        val result = _smartInputState.value.ocrResult ?: return
        if (index !in result.energyCandidates.indices) return
        val candidate = result.energyCandidates[index]
        _smartInputState.update {
            it.copy(
                selectedOcrCandidateIndex = index,
                ocrConsumedAmount = "",
                ocrPortionPreset = null,
                message = null
            )
        }
    }

    fun onOcrConsumedAmountChange(value: String) {
        _smartInputState.update { it.copy(ocrConsumedAmount = value.filter { char -> char.isDigit() || char == '.' },
            ocrPortionPreset = null, message = null) }
    }

    fun showPreciseOcrAmount() {
        _smartInputState.update { it.copy(ocrPreciseOpen = true) }
    }

    fun selectOcrPortion(ratio: Double, label: String) {
        val smart = _smartInputState.value
        val result = smart.ocrResult ?: return
        val candidate = smart.selectedOcrCandidateIndex?.let(result.energyCandidates::getOrNull) ?: return
        if (ratio !in 0.1..1.0) return
        val packageAmount = result.totalAmount?.takeIf { it > 0.0 && result.totalAmountUnit == candidate.unit }
        val base = packageAmount ?: candidate.referenceAmount
        val preset = PortionPreset(
            id = "ocr-fraction-$ratio",
            label = label,
            amount = base * ratio,
            unit = candidate.unit,
            estimationType = if (packageAmount != null) PortionEstimationType.PACKAGE_LABEL else PortionEstimationType.VISUAL_ESTIMATE,
            sourceReference = if (packageAmount != null) "포장지 총내용량 ${formatAmount(base)} ${candidate.unit}"
                else "영양성분표에 표시된 양 ${formatAmount(base)} ${candidate.unit}",
            description = if (packageAmount != null) "포장 전체에서 먹은 비율로 계산한 예상값이에요."
                else "총내용량을 확인하지 못해 영양표에 표시된 양의 비율로 계산한 매우 대략적인 값이에요."
        )
        _smartInputState.update { it.copy(ocrConsumedAmount = formatAmount(preset.amount),
            ocrPortionPreset = preset, message = null) }
    }

    fun confirmOcrResult() {
        val smart = _smartInputState.value
        val result = smart.ocrResult ?: return
        val candidate = smart.selectedOcrCandidateIndex?.let { result.energyCandidates.getOrNull(it) } ?: return
        val amount = smart.ocrConsumedAmount.toDoubleOrNull()?.takeIf { it > 0.0 }
        if (amount == null) {
            _smartInputState.update { it.copy(message = "먹은 양을 대략 선택하거나 직접 입력해 주세요.") }
            return
        }
        val calories = NutritionLabelParser.calculateCalories(candidate, amount)
        _uiState.update {
            it.copy(
                foodName = it.foodName.ifBlank { "영양성분표 확인 음식" },
                calories = calories.toString(),
                servingAmount = formatAmount(amount),
                servingUnit = candidate.unit,
                referenceCalories = candidate.energyKcal.roundToInt(),
                referenceServingAmount = candidate.referenceAmount,
                referenceServingUnit = candidate.unit,
                selectedServingRatio = null,
                selectedPortion = smart.ocrPortionPreset,
                recordSource = RecordSource.NUTRITION_LABEL,
                selectedFoodItemId = null,
                selectedFood = null,
                estimatedCarbohydrateGrams = result.carbohydrateGrams?.times(amount / candidate.referenceAmount),
                estimatedProteinGrams = result.proteinGrams?.times(amount / candidate.referenceAmount),
                estimatedFatGrams = result.fatGrams?.times(amount / candidate.referenceAmount),
                selectedBarcode = null,
                nameError = null,
                caloriesError = null
            )
        }
        _smartInputState.update {
            it.copy(
                mode = SmartInputMode.MANUAL,
                ocrResult = null,
                selectedOcrCandidateIndex = null,
                ocrConsumedAmount = "",
                ocrPortionPreset = null,
                ocrPreciseOpen = false,
                message = "인식한 기준과 섭취량으로 계산했습니다. 저장 전에 확인해 주세요."
            )
        }
        viewModelScope.launch {
            runCatching {
                persistRecognition(
                    sourceType = "NUTRITION_LABEL_OCR",
                    detectedName = "사용자가 확인한 영양성분표",
                    matchedFoodItemId = null,
                    amount = amount,
                    unit = candidate.unit,
                    calories = calories
                )
            }
        }
    }

    fun continueOcrWithUnknownAmount() {
        val smart = _smartInputState.value
        val result = smart.ocrResult ?: return
        val candidate = smart.selectedOcrCandidateIndex?.let(result.energyCandidates::getOrNull) ?: return
        _uiState.update {
            it.copy(
                foodName = it.foodName.ifBlank { "영양성분표 확인 음식" },
                calories = "",
                servingAmount = "",
                servingUnit = candidate.unit,
                referenceCalories = candidate.energyKcal.roundToInt(),
                referenceServingAmount = candidate.referenceAmount,
                referenceServingUnit = candidate.unit,
                selectedFood = null,
                selectedPortion = null,
                portionHelpOpen = true,
                recordSource = RecordSource.NUTRITION_LABEL,
                selectedFoodItemId = null,
                selectedBarcode = null
            )
        }
        _smartInputState.update {
            it.copy(mode = SmartInputMode.MANUAL, ocrResult = null, ocrPortionPreset = null,
                message = "그릇과 먹은 정도를 골라 주세요. 정확한 무게가 없어도 예상값으로 기록할 수 있어요.")
        }
    }

    fun retakePhoto() {
        deleteCurrentPhoto()
        _photoState.value = PhotoAnalysisUiState.Camera
    }

    fun usePhotoForManualRecord() {
        val preview = _photoState.value as? PhotoAnalysisUiState.Preview ?: return
        _photoState.value = PhotoAnalysisUiState.ManualEntry(preview.photoPath)
    }

    fun analyzePhoto(locale: String, timezone: String) {
        if (analysisJob?.isActive == true) return
        val source = when (val state = _photoState.value) {
            is PhotoAnalysisUiState.Preview -> AnalysisSource(
                state.photoPath,
                state.requestId,
                state.capturedAt
            )
            is PhotoAnalysisUiState.Error -> {
                val path = state.photoPath ?: return
                val requestId = state.requestId ?: return
                val capturedAt = state.capturedAt ?: return
                AnalysisSource(path, requestId, capturedAt)
            }
            is PhotoAnalysisUiState.Result -> AnalysisSource(
                state.photoPath,
                UUID.randomUUID().toString(),
                Instant.now().toString()
            )
            else -> return
        }

        analysisJob = viewModelScope.launch {
            _photoState.value = PhotoAnalysisUiState.InProgress(
                source.photoPath,
                source.requestId,
                source.capturedAt,
                PhotoAnalysisProgress.PROCESSING_IMAGE
            )
            val outcome = photoAnalysisRepository.analyze(
                FoodPhotoAnalysisRequest(
                    imageFile = java.io.File(source.photoPath),
                    requestId = source.requestId,
                    locale = locale,
                    timezone = timezone,
                    capturedAt = source.capturedAt
                )
            ) { progress ->
                _photoState.value = PhotoAnalysisUiState.InProgress(
                    source.photoPath,
                    source.requestId,
                    source.capturedAt,
                    progress
                )
            }

            _photoState.value = when (outcome) {
                is PhotoAnalysisOutcome.Success -> createResultState(
                    source.photoPath,
                    source.capturedAt,
                    outcome.analysis
                )
                PhotoAnalysisOutcome.FoodNotDetected -> source.toError(PhotoAnalysisError.FOOD_NOT_DETECTED)
                PhotoAnalysisOutcome.InvalidImage -> source.toError(PhotoAnalysisError.INVALID_IMAGE)
                PhotoAnalysisOutcome.ImageTooLarge -> source.toError(PhotoAnalysisError.IMAGE_TOO_LARGE)
                PhotoAnalysisOutcome.UnsupportedImage -> source.toError(PhotoAnalysisError.UNSUPPORTED_IMAGE)
                PhotoAnalysisOutcome.NetworkUnavailable -> source.toError(PhotoAnalysisError.NETWORK)
                PhotoAnalysisOutcome.TimedOut -> source.toError(PhotoAnalysisError.TIMEOUT)
                PhotoAnalysisOutcome.AuthenticationRequired -> source.toError(PhotoAnalysisError.AUTHENTICATION)
                PhotoAnalysisOutcome.RateLimited -> source.toError(PhotoAnalysisError.RATE_LIMITED)
                PhotoAnalysisOutcome.MalformedResponse -> source.toError(PhotoAnalysisError.MALFORMED_RESPONSE)
                PhotoAnalysisOutcome.ServerError -> source.toError(PhotoAnalysisError.SERVER)
                PhotoAnalysisOutcome.ServiceNotConfigured -> source.toError(PhotoAnalysisError.SERVICE_NOT_CONFIGURED)
                PhotoAnalysisOutcome.Cancelled -> source.toError(PhotoAnalysisError.CANCELLED)
            }
        }
    }

    fun updatePhotoItemName(itemId: String, value: String) = updatePhotoItems { items ->
        items.map { item ->
            if (item.itemId != itemId) item
            else if (value.trim() == item.foodName.trim()) item.copy(foodName = value, nameError = null)
            else item.copy(
                foodName = value,
                nameError = null,
                matchedFoodItemId = null,
                verifiedReferenceCalories = null,
                verifiedReferenceAmount = null,
                verifiedReferenceUnit = null,
                nutritionMatchMessage = "음식 이름을 수정해 자동 영양 연결을 해제했습니다. 칼로리를 확인해 주세요."
            )
        }
    }

    fun updatePhotoItemAmount(itemId: String, value: String) = updatePhotoItems { items ->
        items.map { item ->
            if (item.itemId == itemId) item.withVerifiedAmount(value, item.amountUnit) else item
        }
    }

    fun updatePhotoItemUnit(itemId: String, value: String) = updatePhotoItems { items ->
        items.map { item ->
            if (item.itemId == itemId) item.withVerifiedAmount(item.amount, value) else item
        }
    }

    fun updatePhotoItemCalories(itemId: String, value: String) = updatePhotoItems { items ->
        items.map { if (it.itemId == itemId) it.copy(calories = value, caloriesError = null) else it }
    }

    fun togglePhotoItem(itemId: String, selected: Boolean) = updatePhotoItems { items ->
        items.map { if (it.itemId == itemId) it.copy(selected = selected) else it }
    }

    fun removePhotoItem(itemId: String) = updatePhotoItems { items ->
        items.filterNot { it.itemId == itemId }
    }

    fun addMissingPhotoItem() = updatePhotoItems { items ->
        items + EditablePhotoFoodItem(
            itemId = "manual-${UUID.randomUUID()}",
            foodName = "",
            amount = "",
            amountUnit = "",
            calories = "",
            selected = true,
            aiFoodName = null,
            aiEstimatedCalories = null,
            minimumKcal = null,
            maximumKcal = null,
            confidence = null,
            description = null
        )
    }

    fun savePhotoAnalysisRecords(onSuccess: () -> Unit) {
        val result = _photoState.value as? PhotoAnalysisUiState.Result ?: return
        if (result.isSaving) return

        var hasError = false
        val validatedItems = result.items.map { item ->
            if (!item.selected) return@map item
            val calories = CalorieUtils.parseAndRoundCalories(item.calories)
            val nameError = if (item.foodName.isBlank()) "음식 이름을 입력해주세요." else null
            val caloriesError = if (calories == null || calories <= 0) "올바른 칼로리를 입력해주세요." else null
            if (nameError != null || caloriesError != null) hasError = true
            item.copy(nameError = nameError, caloriesError = caloriesError)
        }
        if (validatedItems.none { it.selected }) return
        if (hasError) {
            _photoState.value = result.copy(items = validatedItems)
            return
        }

        _photoState.value = result.copy(isSaving = true, saveError = null)
        val entryState = _uiState.value
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val records = validatedItems.filter { it.selected }.map { item ->
                    val confirmedCalories = CalorieUtils.parseAndRoundCalories(item.calories)!!
                    val confirmedAmount = item.amount.toDoubleOrNull()?.takeIf { it > 0.0 }
                    val confirmedUnit = item.amountUnit.trim().takeIf { it.isNotBlank() }
                    val wasEdited = item.aiFoodName != item.foodName.trim() ||
                        item.aiEstimatedCalories != confirmedCalories ||
                        item.originalAmount != confirmedAmount ||
                        item.originalAmountUnit != confirmedUnit
                    val matchedFood = item.matchedFoodItemId?.let { nutritionRepository?.findById(it) }
                    val nutrition = matchedFood?.let { food ->
                        if (food.energyKcal > 0.0) {
                            Macronutrients(food.carbohydrateGrams, food.proteinGrams, food.fatGrams)
                                .scaled(confirmedCalories.toDouble() / food.energyKcal)
                        } else Macronutrients.Unknown
                    } ?: Macronutrients.Unknown
                    MealRecord(
                        date = entryState.date.format(dateFormatter),
                        time = entryState.time.format(timeFormatter),
                        mealType = entryState.mealType,
                        foodName = item.foodName.trim(),
                        calories = confirmedCalories,
                        carbohydrateGrams = nutrition.carbohydrateGrams,
                        proteinGrams = nutrition.proteinGrams,
                        fatGrams = nutrition.fatGrams,
                        memo = entryState.memo.takeIf { it.isNotBlank() },
                        servingAmount = confirmedAmount,
                        servingUnit = confirmedUnit,
                        source = RecordSource.PHOTO_AI,
                        photoAnalysisId = result.analysis.analysisId,
                        photoRequestId = result.analysis.requestId,
                        photoItemId = item.itemId,
                        aiFoodName = item.aiFoodName,
                        aiEstimatedCalories = item.aiEstimatedCalories,
                        aiMinimumCalories = item.minimumKcal,
                        aiMaximumCalories = item.maximumKcal,
                        aiConfidence = item.confidence,
                        analysisModelVersion = result.analysis.modelVersion,
                        analysisRequestedAt = result.requestedAt,
                        foodItemId = item.matchedFoodItemId,
                        wasAiResultEdited = wasEdited,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                }
                mealRepository.insertPhotoMeals(records)
                runCatching {
                    val sessionId = recognitionRepository?.createSession(
                        RecognitionSession(
                            requestId = result.analysis.requestId,
                            sourceType = "PHOTO_AI",
                            capturedAt = runCatching { Instant.parse(result.requestedAt).toEpochMilli() }.getOrDefault(now),
                            status = "CONFIRMED",
                            createdAt = now,
                            completedAt = now
                        )
                    )
                    if (sessionId != null) {
                        recognitionRepository.saveCandidates(
                            validatedItems.map { item ->
                                RecognitionCandidate(
                                    sessionId = sessionId,
                                    detectedName = item.aiFoodName ?: item.foodName,
                                    matchedFoodItemId = item.matchedFoodItemId,
                                    estimatedAmount = item.amount.toDoubleOrNull(),
                                    unit = item.amountUnit.takeIf(String::isNotBlank),
                                    estimatedKcal = CalorieUtils.parseAndRoundCalories(item.calories),
                                    minimumKcal = item.minimumKcal,
                                    maximumKcal = item.maximumKcal,
                                    confidenceLevel = item.confidenceLevel,
                                    selected = item.selected
                                )
                            }
                        )
                    }
                }
                photoProcessor?.deleteTemporaryPhoto(result.photoPath)
                _photoState.value = PhotoAnalysisUiState.Idle
                onSuccess()
            } catch (_: Exception) {
                _photoState.value = result.copy(
                    items = validatedItems,
                    isSaving = false,
                    saveError = "기록을 저장하지 못했습니다. 다시 시도해주세요."
                )
            }
        }
    }

    fun cancelPhotoFlow() {
        analysisJob?.cancel()
        deleteCurrentPhoto()
        capturePurpose = CapturePurpose.FOOD_PHOTO
        _photoState.value = PhotoAnalysisUiState.Idle
    }

    fun returnToManualEntry() = cancelPhotoFlow()

    private suspend fun createResultState(
        photoPath: String,
        requestedAt: String,
        analysis: FoodPhotoAnalysis
    ): PhotoAnalysisUiState.Result {
        return PhotoAnalysisUiState.Result(
            photoPath = photoPath,
            requestedAt = requestedAt,
            analysis = analysis,
            items = analysis.detectedItems.map { detected ->
                val verified = nutritionRepository?.matchVerifiedFood(
                    listOf(detected.foodName) + detected.alternativeNames
                )
                detected.toEditable(verified)
            }
        )
    }

    private fun DetectedFoodItem.toEditable(verified: FoodItem? = null): EditablePhotoFoodItem {
        val verifiedCalories = if (verified != null && estimatedAmount != null && estimatedAmount > 0.0 &&
            amountUnit?.trim()?.equals(verified.unit, ignoreCase = true) == true
        ) {
            NutritionRepository.calculateCalories(verified, estimatedAmount)
        } else null
        val productUsesVerifiedLookup = nutritionRepository != null
        val finalCalories = when {
            verifiedCalories != null -> verifiedCalories
            !productUsesVerifiedLookup -> estimatedKcal
            else -> null
        }
        val range = finalCalories?.let { calories ->
            (calories * 0.8).roundToInt().coerceAtLeast(1) to (calories * 1.2).roundToInt().coerceAtLeast(1)
        }
        return EditablePhotoFoodItem(
        itemId = itemId,
        foodName = verified?.name ?: foodName,
        amount = estimatedAmount?.toString().orEmpty(),
        amountUnit = amountUnit.orEmpty(),
        calories = finalCalories?.toString().orEmpty(),
        selected = true,
        aiFoodName = foodName,
        aiEstimatedCalories = estimatedKcal,
        minimumKcal = range?.first ?: if (!productUsesVerifiedLookup) minimumKcal else null,
        maximumKcal = range?.second ?: if (!productUsesVerifiedLookup) maximumKcal else null,
        confidence = confidence,
        description = description,
        originalAmount = estimatedAmount,
        originalAmountUnit = amountUnit,
        confidenceLevel = confidenceLevel,
        assumptions = assumptions,
        matchedFoodItemId = verified?.id,
        verifiedReferenceCalories = verified?.energyKcal,
        verifiedReferenceAmount = verified?.referenceAmount,
        verifiedReferenceUnit = verified?.unit,
        nutritionMatchMessage = when {
            verifiedCalories != null -> "${requireNotNull(verified).sourceType} 영양 데이터(${verified.servingDescription})로 다시 계산했습니다."
            verified != null -> "영양 항목은 찾았지만 기준 단위가 달라 섭취량을 확인해 주세요."
            productUsesVerifiedLookup -> "영양 정보를 찾지 못했습니다. 비슷한 음식을 선택하거나 직접 입력해 주세요."
            else -> null
        }
        )
    }

    private fun updatePhotoItems(transform: (List<EditablePhotoFoodItem>) -> List<EditablePhotoFoodItem>) {
        val current = _photoState.value as? PhotoAnalysisUiState.Result ?: return
        _photoState.value = current.copy(items = transform(current.items), saveError = null)
    }

    private fun deleteCurrentPhoto() {
        photoProcessor?.deleteTemporaryPhoto(_photoState.value.photoPathOrNull())
    }

    private fun PhotoAnalysisUiState.photoPathOrNull(): String? = when (this) {
        is PhotoAnalysisUiState.Preview -> photoPath
        is PhotoAnalysisUiState.ManualEntry -> photoPath
        is PhotoAnalysisUiState.InProgress -> photoPath
        is PhotoAnalysisUiState.Result -> photoPath
        is PhotoAnalysisUiState.Error -> photoPath
        PhotoAnalysisUiState.Camera, PhotoAnalysisUiState.Idle -> null
    }

    private fun parseServing(value: String): Pair<Double, String> {
        val match = Regex("""^\s*(\d+(?:\.\d+)?)\s*(.*)$""").matchEntire(value)
        val amount = match?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
        val unit = match?.groupValues?.get(2)?.trim().orEmpty().ifBlank { "인분" }
        return amount to unit
    }

    private fun formatAmount(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun parsePositiveAmount(value: String): Double? =
        value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

    private fun AddRecordUiState.hasReferenceFood(): Boolean =
        referenceCalories != null && referenceServingAmount != null && referenceServingUnit != null

    private fun AddRecordUiState.recalculateReferenceCalories(
        amountText: String,
        unitText: String
    ): Pair<String, Double>? {
        val referenceKcal = referenceCalories ?: return null
        val referenceAmount = referenceServingAmount?.takeIf { it > 0.0 } ?: return null
        val referenceUnit = referenceServingUnit ?: return null
        if (!unitText.trim().equals(referenceUnit.trim(), ignoreCase = true)) return null
        val amount = parsePositiveAmount(amountText) ?: return null
        val ratio = amount / referenceAmount
        val calories = referenceKcal * ratio
        if (!ratio.isFinite() || !calories.isFinite() || calories > Int.MAX_VALUE.toDouble()) return null
        return calories.roundToInt().coerceAtLeast(1).toString() to ratio
    }

    private fun EditablePhotoFoodItem.withVerifiedAmount(
        newAmount: String,
        newUnit: String
    ): EditablePhotoFoodItem {
        val referenceKcal = verifiedReferenceCalories
        val referenceAmount = verifiedReferenceAmount?.takeIf { it > 0.0 }
        val referenceUnit = verifiedReferenceUnit
        val amountValue = newAmount.toDoubleOrNull()?.takeIf { it > 0.0 }
        val calculated = if (
            referenceKcal != null && referenceAmount != null && referenceUnit != null && amountValue != null &&
            newUnit.trim().equals(referenceUnit.trim(), ignoreCase = true)
        ) {
            (referenceKcal * amountValue / referenceAmount).roundToInt().coerceAtLeast(1)
        } else null
        return copy(
            amount = newAmount,
            amountUnit = newUnit,
            calories = if (verifiedReferenceCalories != null) calculated?.toString().orEmpty() else calories,
            minimumKcal = calculated?.let { (it * 0.8).roundToInt().coerceAtLeast(1) }
                ?: if (verifiedReferenceCalories != null) null else minimumKcal,
            maximumKcal = calculated?.let { (it * 1.2).roundToInt().coerceAtLeast(1) }
                ?: if (verifiedReferenceCalories != null) null else maximumKcal,
            caloriesError = null
        )
    }

    private fun servingDescription(amount: String, unit: String): String {
        val normalizedAmount = amount.trim()
        val normalizedUnit = unit.trim()
        return when {
            normalizedAmount.isNotEmpty() && normalizedUnit.isNotEmpty() -> "$normalizedAmount$normalizedUnit"
            normalizedAmount.isNotEmpty() -> normalizedAmount
            normalizedUnit.isNotEmpty() -> normalizedUnit
            else -> "1회분"
        }
    }

    private suspend fun persistRecognition(
        sourceType: String,
        detectedName: String,
        matchedFoodItemId: String?,
        amount: Double?,
        unit: String?,
        calories: Int?
    ) {
        val repository = recognitionRepository ?: return
        val now = System.currentTimeMillis()
        val sessionId = repository.createSession(
            RecognitionSession(
                requestId = UUID.randomUUID().toString(),
                sourceType = sourceType,
                capturedAt = now,
                status = "CONFIRMED",
                createdAt = now,
                completedAt = now
            )
        )
        repository.saveCandidates(
            listOf(
                RecognitionCandidate(
                    sessionId = sessionId,
                    detectedName = detectedName,
                    matchedFoodItemId = matchedFoodItemId,
                    estimatedAmount = amount,
                    unit = unit,
                    estimatedKcal = calories,
                    selected = true
                )
            )
        )
    }

    override fun onCleared() {
        analysisJob?.cancel()
        deleteCurrentPhoto()
    }

    private companion object {
        const val RECOGNITION_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }

    private data class AnalysisSource(
        val photoPath: String,
        val requestId: String,
        val capturedAt: String
    ) {
        fun toError(type: PhotoAnalysisError) = PhotoAnalysisUiState.Error(
            type = type,
            photoPath = photoPath,
            requestId = requestId,
            capturedAt = capturedAt
        )
    }
}


data class AddRecordUiState(
    val foodName: String = "",
    val calories: String = "",
    val servingAmount: String = "",
    val servingUnit: String = "",
    val foodSearch: String = "",
    val mealType: MealType = MealType.BREAKFAST,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val memo: String = "",
    val saveAsFrequent: Boolean = false,
    val isSaving: Boolean = false,
    val nameError: String? = null,
    val caloriesError: String? = null,
    val saveError: String? = null,
    val referenceCalories: Int? = null,
    val referenceServingAmount: Double? = null,
    val referenceServingUnit: String? = null,
    val selectedServingRatio: Double? = null,
    val selectedFoodItemId: String? = null,
    val selectedFood: FoodItem? = null,
    val estimatedCarbohydrateGrams: Double? = null,
    val estimatedProteinGrams: Double? = null,
    val estimatedFatGrams: Double? = null,
    val companionFoods: List<CompanionFoodEntry> = emptyList(),
    val selectedPortion: PortionPreset? = null,
    val portionHelpOpen: Boolean = false,
    val selectedVessel: PortionVessel? = null,
    val preciseAmountOpen: Boolean = false,
    val selectedBarcode: String? = null,
    val pendingProductBarcode: String? = null,
    val recordSource: RecordSource = RecordSource.MANUAL
)

enum class CapturePurpose { FOOD_PHOTO, NUTRITION_LABEL }

enum class SmartInputMode {
    HUB,
    MANUAL,
    SEARCH,
    BARCODE_LOADING,
    BARCODE_RESULT,
    BARCODE_NOT_FOUND,
    OCR_PROCESSING,
    OCR_RESULT
}

enum class FoodSearchMode { FOOD, BRAND, FRANCHISE }

data class SmartInputUiState(
    val mode: SmartInputMode = SmartInputMode.HUB,
    val photoAnalysisAvailable: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<FoodItem> = emptyList(),
    val searchMode: FoodSearchMode = FoodSearchMode.FOOD,
    val selectedFoodCategory: FoodBrowseCategory = FoodBrowseCategory.ALL,
    val brandResults: List<FoodBrandSummary> = emptyList(),
    val selectedBrand: FoodBrandSummary? = null,
    val brandProducts: List<FoodItem> = emptyList(),
    val brandCategories: List<String> = emptyList(),
    val selectedBrandCategory: String? = null,
    val isSearching: Boolean = false,
    val isCompanionSearch: Boolean = false,
    val barcode: String = "",
    val barcodeItem: FoodItem? = null,
    val ocrResult: NutritionLabelParseResult? = null,
    val selectedOcrCandidateIndex: Int? = null,
    val ocrConsumedAmount: String = "",
    val ocrPortionPreset: PortionPreset? = null,
    val ocrPreciseOpen: Boolean = false,
    val message: String? = null
)

data class CompanionFoodEntry(
    val selectionId: String,
    val food: FoodItem,
    val portion: PortionPreset,
    val calories: Int
)

sealed interface PhotoAnalysisUiState {
    data object Idle : PhotoAnalysisUiState
    data object Camera : PhotoAnalysisUiState
    data class Preview(val photoPath: String, val requestId: String, val capturedAt: String) : PhotoAnalysisUiState
    data class ManualEntry(val photoPath: String) : PhotoAnalysisUiState
    data class InProgress(
        val photoPath: String,
        val requestId: String,
        val capturedAt: String,
        val progress: PhotoAnalysisProgress
    ) : PhotoAnalysisUiState
    data class Result(
        val photoPath: String,
        val requestedAt: String,
        val analysis: FoodPhotoAnalysis,
        val items: List<EditablePhotoFoodItem>,
        val isSaving: Boolean = false,
        val saveError: String? = null
    ) : PhotoAnalysisUiState {
        val selectedTotalCalories: Int
            get() = items.filter { it.selected }.sumOf { CalorieUtils.parseAndRoundCalories(it.calories) ?: 0 }
        val selectedMinimumCalories: Int?
            get() = items.filter { it.selected }.map { it.minimumKcal }.takeIf { values -> values.isNotEmpty() && values.all { it != null } }?.sumOf { it ?: 0 }
        val selectedMaximumCalories: Int?
            get() = items.filter { it.selected }.map { it.maximumKcal }.takeIf { values -> values.isNotEmpty() && values.all { it != null } }?.sumOf { it ?: 0 }
    }
    data class Error(
        val type: PhotoAnalysisError,
        val photoPath: String? = null,
        val requestId: String? = null,
        val capturedAt: String? = null
    ) : PhotoAnalysisUiState
}

enum class PhotoAnalysisError {
    PERMISSION_DENIED,
    PERMISSION_PERMANENTLY_DENIED,
    CAMERA_UNAVAILABLE,
    CAMERA_CAPTURE_FAILED,
    FOOD_NOT_DETECTED,
    INVALID_IMAGE,
    IMAGE_TOO_LARGE,
    UNSUPPORTED_IMAGE,
    NETWORK,
    TIMEOUT,
    AUTHENTICATION,
    RATE_LIMITED,
    MALFORMED_RESPONSE,
    SERVER,
    SERVICE_NOT_CONFIGURED,
    CANCELLED
}

data class EditablePhotoFoodItem(
    val itemId: String,
    val foodName: String,
    val amount: String,
    val amountUnit: String,
    val calories: String,
    val selected: Boolean,
    val aiFoodName: String?,
    val aiEstimatedCalories: Int?,
    val minimumKcal: Int?,
    val maximumKcal: Int?,
    val confidence: Double?,
    val description: String?,
    val originalAmount: Double? = null,
    val originalAmountUnit: String? = null,
    val nameError: String? = null,
    val caloriesError: String? = null,
    val confidenceLevel: String? = null,
    val assumptions: List<String> = emptyList(),
    val matchedFoodItemId: String? = null,
    val verifiedReferenceCalories: Double? = null,
    val verifiedReferenceAmount: Double? = null,
    val verifiedReferenceUnit: String? = null,
    val nutritionMatchMessage: String? = null
)
