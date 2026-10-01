package com.example.healthcare

import com.example.healthcare.data.dao.FrequentFoodDao
import com.example.healthcare.data.dao.FoodItemDao
import com.example.healthcare.data.dao.MealRecordDao
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.photo.FoodPhotoAnalysisRepository
import com.example.healthcare.data.photo.FoodPhotoProcessor
import com.example.healthcare.data.photo.model.FoodPhotoAnalysisRequest
import com.example.healthcare.data.photo.model.PhotoAnalysisOutcome
import com.example.healthcare.data.photo.model.PhotoAnalysisProgress
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.domain.PortionFraction
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.PortionVessel
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import com.example.healthcare.ui.viewmodel.PhotoAnalysisError
import com.example.healthcare.ui.viewmodel.PhotoAnalysisUiState
import com.example.healthcare.ui.viewmodel.SmartInputMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoManualRecordViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val insertedMeals = mutableListOf<MealRecord>()
    private val registeredProducts = mutableListOf<FoodItem>()
    private val savedFrequentFoods = mutableListOf<FrequentFood>()
    private var analysisCalls = 0
    private var failMealInsert = false
    private lateinit var cacheRoot: File
    private lateinit var viewModel: AddRecordViewModel

    private val mealDao = object : MealRecordDao {
        override suspend fun insertMeal(meal: MealRecord) {
            if (failMealInsert) error("forced insert failure")
            insertedMeals += meal
        }

        override suspend fun insertPhotoMeals(meals: List<MealRecord>): List<Long> {
            if (failMealInsert) error("forced insert failure")
            insertedMeals += meals
            return meals.indices.map { it + 1L }
        }

        override suspend fun updateMeal(meal: MealRecord) = Unit
        override suspend fun deleteMeal(meal: MealRecord) = Unit
        override fun getAllMeals(): Flow<List<MealRecord>> = flowOf(emptyList())
        override fun getMealsByDate(date: String): Flow<List<MealRecord>> = flowOf(emptyList())
        override fun getTotalCaloriesByDate(date: String): Flow<Int?> = flowOf(0)
        override fun getNutritionByDate(date: String): Flow<List<com.example.healthcare.data.model.MealNutritionRow>> = flowOf(emptyList())
    }

    private val foodDao = object : FrequentFoodDao {
        override suspend fun insertFood(food: FrequentFood) {
            savedFrequentFoods += food.copy(id = savedFrequentFoods.size + 1L)
        }
        override suspend fun updateFood(food: FrequentFood) {
            savedFrequentFoods.replaceAll { if (it.id == food.id) food else it }
        }
        override suspend fun deleteFood(food: FrequentFood) {
            savedFrequentFoods.removeAll { it.id == food.id }
        }
        override fun getAllFoods(): Flow<List<FrequentFood>> = flowOf(savedFrequentFoods)
        override fun getFavoriteFoods(): Flow<List<FrequentFood>> = flowOf(savedFrequentFoods.filter { it.isFavorite })
        override fun searchFoods(query: String): Flow<List<FrequentFood>> =
            flowOf(savedFrequentFoods.filter { it.foodName.contains(query, ignoreCase = true) })
    }

    private val foodItemDao = object : FoodItemDao {
        override suspend fun upsertAll(items: List<FoodItem>) {
            registeredProducts.removeAll { existing -> items.any { it.id == existing.id } }
            registeredProducts += items
        }

        override fun observeSearch(normalizedQuery: String, limit: Int): Flow<List<FoodItem>> = flowOf(emptyList())
        override fun observeProductSearch(normalizedQuery: String, limit: Int): Flow<List<FoodItem>> = flowOf(emptyList())
        override fun observeProductBrands(limit: Int): Flow<List<FoodBrandSummary>> = flowOf(emptyList())
        override fun observeProductsByBrand(
            brand: String,
            normalizedQuery: String,
            limit: Int
        ): Flow<List<FoodItem>> = flowOf(emptyList())
        override fun observeFranchiseBrands(brands: List<String>): Flow<List<FoodBrandSummary>> = flowOf(emptyList())
        override fun observeFranchiseFoods(
            brand: String,
            normalizedQuery: String,
            limit: Int
        ): Flow<List<FoodItem>> = flowOf(emptyList())
        override suspend fun findExactMatches(normalizedName: String, limit: Int): List<FoodItem> = emptyList()
        override suspend fun findByBarcode(barcode: String): FoodItem? = registeredProducts.firstOrNull { it.barcode == barcode }
        override suspend fun findById(id: String): FoodItem? = registeredProducts.firstOrNull { it.id == id }
        override suspend fun findReplacements(
            category: String,
            excludedId: String,
            minimumKcal: Double,
            maximumKcal: Double,
            targetKcal: Double,
            limit: Int
        ): List<FoodItem> = emptyList()

        override suspend fun count(): Int = registeredProducts.size
    }

    private val analysisRepository = object : FoodPhotoAnalysisRepository {
        override suspend fun analyze(
            request: FoodPhotoAnalysisRequest,
            onProgress: (PhotoAnalysisProgress) -> Unit
        ): PhotoAnalysisOutcome {
            analysisCalls++
            return PhotoAnalysisOutcome.ServiceNotConfigured
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        cacheRoot = File("build/test-photo-manual-${System.nanoTime()}").apply { mkdirs() }
        viewModel = AddRecordViewModel(
            MealRepository(mealDao),
            FoodRepository(foodDao),
            analysisRepository,
            FoodPhotoProcessor(cacheRoot),
            nutritionRepository = NutritionRepository(foodItemDao)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        cacheRoot.deleteRecursively()
    }

    @Test
    fun `촬영 사진 사용은 분석 저장소를 호출하지 않고 수동 작성 상태로 이동한다`() {
        val photo = createCaptureFile()

        viewModel.openCamera()
        viewModel.onPhotoCaptured(photo.absolutePath)
        viewModel.usePhotoForManualRecord()

        assertTrue(viewModel.photoState.value is PhotoAnalysisUiState.ManualEntry)
        assertEquals(0, analysisCalls)
        assertTrue(photo.exists())
    }

    @Test
    fun `홈에서 고른 네 식사 구분은 저장과 성공 피드백까지 유지된다`() = runTest(dispatcher.scheduler) {
        val mealTypes = listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER)
        mealTypes.forEach { mealType ->
            viewModel.onMealTypeChange(mealType)
            viewModel.onFoodNameChange("${mealType.displayName} 테스트")
            viewModel.onCaloriesChange("100")
            var completedMealType: MealType? = null

            viewModel.saveRecord { completedMealType = it }
            advanceUntilIdle()

            assertEquals(mealType, insertedMeals.last().mealType)
            assertEquals(mealType, completedMealType)
        }
        assertEquals(4, insertedMeals.size)
    }

    @Test
    fun `검색 결과 선택은 네 식사 context를 유지한 빠른 기록으로 열리고 중복 저장되지 않는다`() =
        runTest(dispatcher.scheduler) {
            val packageFood = verifiedFood("quick-food", "참치김밥", "밥류", 180.0, "g", 420.0).copy(
                sourceType = "K-FIND-PRODUCT",
                servingDescription = "100g 기준 · 공식 총내용량 180g · 포장단위 줄"
            )

            listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER).forEach { mealType ->
                viewModel.onMealTypeChange(mealType)
                viewModel.selectVerifiedFood(packageFood)
                assertEquals(SmartInputMode.QUICK_RECORD, viewModel.smartInputState.value.mode)
                assertEquals(mealType, viewModel.uiState.value.mealType)

                viewModel.selectPortionPreset(requireNotNull(PortionGuide.defaultPreset(packageFood)))
                var successCount = 0
                viewModel.saveRecord { successCount++ }
                viewModel.saveRecord { successCount++ }
                advanceUntilIdle()

                assertEquals(mealType, insertedMeals.last().mealType)
                assertEquals(1, successCount)
            }

            assertEquals(4, insertedMeals.size)
        }

    @Test
    fun `빠른 기록의 자세히 입력은 기존 수동 상세 입력 상태로 이동한다`() {
        val food = verifiedFood("details", "참치김밥", "밥류", 100.0, "g", 220.0)
        viewModel.selectVerifiedFood(food)
        assertEquals(SmartInputMode.QUICK_RECORD, viewModel.smartInputState.value.mode)

        viewModel.showManualDetails()

        assertEquals(SmartInputMode.MANUAL, viewModel.smartInputState.value.mode)
        assertEquals("참치김밥", viewModel.uiState.value.foodName)
        assertEquals("details", viewModel.uiState.value.selectedFoodItemId)
    }

    @Test
    fun `최근 기록 shortcut은 공식 제공량으로 위장하지 않고 저장한 양을 적용한다`() {
        val food = verifiedFood("recent", "샐러드", "샐러드류", 100.0, "g", 120.0)
        val recent = MealRecord(
            id = 42,
            date = "2026-09-29",
            time = "12:00",
            mealType = MealType.LUNCH,
            foodName = "샐러드",
            calories = 216,
            servingAmount = 180.0,
            servingUnit = "g",
            foodItemId = "recent"
        )
        viewModel.selectVerifiedFood(food)

        viewModel.applyRecentAmount(recent)

        val state = viewModel.uiState.value
        assertEquals("180", state.servingAmount)
        assertEquals("216", state.calories)
        assertEquals("최근 기록 180g", state.selectedPortion?.label)
        assertEquals("사용자의 최근 기록", state.selectedPortion?.sourceReference)
        assertTrue(state.selectedPortion?.description.orEmpty().contains("공식 제공량은 아니에요"))
    }

    @Test
    fun `원본 FoodItem이 사라진 즐겨찾기도 snapshot으로 빠른 기록하고 해제할 수 있다`() =
        runTest(dispatcher.scheduler) {
            val favorite = FrequentFood(
                id = 7,
                foodName = "사라진 제품",
                defaultServing = "85g",
                calories = 210,
                isFavorite = true,
                isFrequent = false,
                carbohydrateGrams = 31.0,
                proteinGrams = 8.0,
                fatGrams = 6.0,
                foodItemId = "removed-product",
                sourceType = "K-FIND-PRODUCT",
                sourceFoodCode = "removed-product",
                brand = "테스트 브랜드"
            )
            savedFrequentFoods += favorite

            viewModel.selectFavoriteFood(favorite)
            advanceUntilIdle()

            assertEquals(SmartInputMode.QUICK_RECORD, viewModel.smartInputState.value.mode)
            assertEquals("removed-product", viewModel.uiState.value.selectedFoodItemId)
            assertEquals("85", viewModel.uiState.value.servingAmount)
            assertEquals("210", viewModel.uiState.value.calories)
            assertEquals("보통", viewModel.uiState.value.selectedPortion?.label)

            viewModel.removeFavorite(favorite)
            advanceUntilIdle()
            assertTrue(savedFrequentFoods.isEmpty())
        }

    @Test
    fun `즐겨찾기 원본 FoodItem이 있으면 최신 원본 영양정보로 빠른 기록한다`() =
        runTest(dispatcher.scheduler) {
            val currentFood = verifiedFood(
                "favorite-current", "참치김밥", "밥류", 180.0, "g", 430.0
            ).copy(
                sourceType = "K-FIND-PRODUCT",
                servingDescription = "100g 기준 · 공식 총내용량 180g · 포장단위 줄",
                brand = "현재 브랜드"
            )
            registeredProducts += currentFood
            val favorite = FrequentFood(
                id = 9,
                foodName = "참치김밥",
                defaultServing = "180g",
                calories = 400,
                isFavorite = true,
                isFrequent = false,
                foodItemId = currentFood.id,
                sourceType = "K-FIND-PRODUCT",
                sourceFoodCode = currentFood.sourceFoodCode,
                brand = "이전 브랜드"
            )

            viewModel.selectFavoriteFood(favorite)
            advanceUntilIdle()

            assertEquals(SmartInputMode.QUICK_RECORD, viewModel.smartInputState.value.mode)
            assertEquals(currentFood, viewModel.uiState.value.selectedFood)
            assertEquals("430", viewModel.uiState.value.calories)
        }

    @Test
    fun `빠른 기록 저장 실패는 성공 callback 없이 재시도 가능한 오류를 남긴다`() =
        runTest(dispatcher.scheduler) {
            val food = verifiedFood("failure", "참치김밥", "밥류", 180.0, "g", 420.0).copy(
                sourceType = "K-FIND-PRODUCT",
                servingDescription = "100g 기준 · 공식 총내용량 180g · 포장단위 줄"
            )
            viewModel.selectVerifiedFood(food)
            viewModel.selectPortionPreset(requireNotNull(PortionGuide.defaultPreset(food)))
            failMealInsert = true
            var successCount = 0

            viewModel.saveRecord { successCount++ }
            advanceUntilIdle()

            assertEquals(0, successCount)
            assertTrue(insertedMeals.isEmpty())
            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals("기록을 저장하지 못했습니다. 다시 시도해주세요.", viewModel.uiState.value.saveError)
            assertEquals(food.name, viewModel.uiState.value.foodName)
            assertEquals(food.id, viewModel.uiState.value.selectedFoodItemId)
            assertEquals("420", viewModel.uiState.value.calories)
            failMealInsert = false
            viewModel.saveRecord { successCount++ }
            viewModel.saveRecord { successCount++ }
            advanceUntilIdle()
            assertEquals(1, successCount)
            assertEquals(1, insertedMeals.size)
        }

    @Test
    fun `저장 음식의 빠른 섭취량은 기준 칼로리와 제공량에 비례한다`() {
        viewModel.selectFrequentFood(
            FrequentFood(foodName = "비빔밥", defaultServing = "100g", calories = 600)
        )

        viewModel.selectServingRatio(0.5)

        val state = viewModel.uiState.value
        assertEquals("50", state.servingAmount)
        assertEquals("g", state.servingUnit)
        assertEquals("300", state.calories)
        assertEquals(0.5, state.selectedServingRatio)

        viewModel.onCaloriesChange("275")
        assertEquals("275", viewModel.uiState.value.calories)
        assertNull(viewModel.uiState.value.selectedServingRatio)
    }

    @Test
    fun `검색 음식 150그램은 탄단지 모두 같은 비율로 MealRecord에 저장한다`() =
        runTest(dispatcher.scheduler) {
            val food = FoodItem(
                id = "macro-food", sourceType = "K-FIND", sourceFoodCode = "macro-food",
                name = "영양 음식", normalizedName = "영양음식", category = "밥류",
                referenceAmount = 100.0, unit = "g", energyKcal = 200.0,
                carbohydrateGrams = 30.0, proteinGrams = 10.0, fatGrams = 5.0,
                servingDescription = "100g 기준", dataVersion = "test", createdAt = 1, updatedAt = 1
            )
            viewModel.selectVerifiedFood(food)
            viewModel.onServingAmountChange("150")
            viewModel.saveRecord {}
            advanceUntilIdle()

            val saved = insertedMeals.single()
            assertEquals(45.0, saved.carbohydrateGrams!!, 0.0001)
            assertEquals(15.0, saved.proteinGrams!!, 0.0001)
            assertEquals(7.5, saved.fatGrams!!, 0.0001)
        }

    @Test
    fun `직접 입력은 근거 없는 탄단지를 0으로 만들지 않는다`() = runTest(dispatcher.scheduler) {
        viewModel.onFoodNameChange("직접 입력 음식")
        viewModel.onServingAmountChange("1")
        viewModel.onServingUnitChange("인분")
        viewModel.onCaloriesChange("300")
        viewModel.saveRecord {}
        advanceUntilIdle()

        val saved = insertedMeals.single()
        assertNull(saved.carbohydrateGrams)
        assertNull(saved.proteinGrams)
        assertNull(saved.fatGrams)
    }

    @Test
    fun `자주 먹는 음식은 저장 시점 탄단지를 보존하고 재기록 양에 비례한다`() =
        runTest(dispatcher.scheduler) {
            val food = FoodItem(
                id = "favorite-food", sourceType = "K-FIND", sourceFoodCode = "favorite-food",
                name = "저장 영양 음식", normalizedName = "저장영양음식", category = "밥류",
                referenceAmount = 100.0, unit = "g", energyKcal = 200.0,
                carbohydrateGrams = 30.0, proteinGrams = 10.0, fatGrams = 5.0,
                servingDescription = "100g 기준", dataVersion = "test", createdAt = 1, updatedAt = 1
            )
            viewModel.selectVerifiedFood(food)
            viewModel.onServingAmountChange("150")
            viewModel.onSaveAsFrequentChange(true)
            viewModel.saveRecord {}
            advanceUntilIdle()

            val favorite = savedFrequentFoods.single()
            assertEquals(45.0, favorite.carbohydrateGrams!!, 0.0001)
            assertEquals(15.0, favorite.proteinGrams!!, 0.0001)
            assertEquals(7.5, favorite.fatGrams!!, 0.0001)

            viewModel.selectFrequentFood(favorite)
            viewModel.selectServingRatio(0.5)
            viewModel.saveRecord {}
            advanceUntilIdle()

            val repeated = insertedMeals.last()
            assertEquals(22.5, repeated.carbohydrateGrams!!, 0.0001)
            assertEquals(7.5, repeated.proteinGrams!!, 0.0001)
            assertEquals(3.75, repeated.fatGrams!!, 0.0001)
        }

    @Test
    fun `검증 음식의 직접 섭취량 변경은 기준량으로 칼로리를 다시 계산한다`() {
        viewModel.selectVerifiedFood(
            FoodItem(
                id = "official-1",
                sourceType = "K-FIND",
                sourceFoodCode = "official-1",
                name = "검증 음식",
                normalizedName = "검증음식",
                referenceAmount = 100.0,
                unit = "g",
                energyKcal = 320.0,
                servingDescription = "100 g",
                dataVersion = "test",
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        listOf("50" to "160", "100" to "320", "250" to "800").forEach { (amount, calories) ->
            viewModel.onServingAmountChange(amount)
            assertEquals(calories, viewModel.uiState.value.calories)
        }
        viewModel.onServingAmountChange("60")

        assertEquals("192", viewModel.uiState.value.calories)
        assertEquals(0.6, viewModel.uiState.value.selectedServingRatio)

        viewModel.onServingUnitChange("ml")
        assertEquals("", viewModel.uiState.value.calories)

        viewModel.onServingUnitChange("g")
        assertEquals("192", viewModel.uiState.value.calories)
    }

    @Test
    fun `생활 단위 없는 음식은 빈 실제 섭취량에서 시작해 원본 단위로 영양소를 비례 저장한다`() =
        runTest(dispatcher.scheduler) {
            val soup = FoodItem(
                id = "seaweed-soup", sourceType = "K-FIND", sourceFoodCode = "D305-223200000-0001",
                name = "미역국_북어", normalizedName = "미역국북어", category = "국 및 탕류",
                referenceAmount = 100.0, unit = "g", energyKcal = 19.0,
                carbohydrateGrams = 1.04, proteinGrams = 2.46, fatGrams = 0.56,
                servingDescription = "100g 기준", dataVersion = "test", createdAt = 1, updatedAt = 1
            )
            viewModel.selectVerifiedFood(soup)
            assertEquals("", viewModel.uiState.value.servingAmount)
            assertEquals("", viewModel.uiState.value.calories)
            assertEquals("g", viewModel.uiState.value.servingUnit)
            assertTrue(viewModel.uiState.value.preciseAmountOpen)

            viewModel.onServingAmountChange("350")
            assertEquals("67", viewModel.uiState.value.calories)
            assertEquals(3.5, viewModel.uiState.value.selectedServingRatio ?: 0.0, 0.0)
            viewModel.saveRecord {}
            advanceUntilIdle()

            val saved = insertedMeals.single()
            assertEquals(350.0, saved.servingAmount ?: 0.0, 0.0)
            assertEquals("g", saved.servingUnit)
            assertEquals(67, saved.calories)
            assertEquals(3.64, saved.carbohydrateGrams ?: 0.0, 0.0001)
            assertEquals(8.61, saved.proteinGrams ?: 0.0, 0.0001)
            assertEquals(1.96, saved.fatGrams ?: 0.0, 0.0001)
        }

    @Test
    fun `밀리리터 기준 음식도 그램 변환 없이 소수 섭취량을 비례 계산하고 잘못된 값은 비운다`() {
        val soup = FoodItem(
            id = "seaweed-soup-ml", sourceType = "K-FIND", sourceFoodCode = "D605-223000000-0001",
            name = "미역국", normalizedName = "미역국", category = "국 및 탕류",
            referenceAmount = 100.0, unit = "ml", energyKcal = 40.0,
            servingDescription = "100ml 기준", dataVersion = "test", createdAt = 1, updatedAt = 1
        )
        viewModel.selectVerifiedFood(soup)
        viewModel.onServingAmountChange("100")
        assertEquals("40", viewModel.uiState.value.calories)
        viewModel.onServingAmountChange("350")
        assertEquals("140", viewModel.uiState.value.calories)
        assertEquals("ml", viewModel.uiState.value.servingUnit)

        listOf("0", "-1", "abc", "1e309", "999999999999999999999999").forEach { invalid ->
            viewModel.onServingAmountChange(invalid)
            assertEquals("", viewModel.uiState.value.calories)
            assertNull(viewModel.uiState.value.selectedServingRatio)
        }
        viewModel.onServingAmountChange("50,5")
        assertEquals("20", viewModel.uiState.value.calories)
    }

    @Test
    fun `고형 음식 ml 기준은 UI를 우회해도 직접 기록 선택되지 않는다`() = runTest(dispatcher.scheduler) {
        val unsafe = FoodItem(
            id = "kfind-solid-volume", sourceType = "K-FIND", sourceFoodCode = "solid-volume",
            name = "김밥_참치", normalizedName = "김밥참치", category = "밥류",
            referenceAmount = 100.0, unit = "ml", energyKcal = 128.0,
            servingDescription = "100ml 기준", dataVersion = "test", createdAt = 1, updatedAt = 1
        )
        viewModel.selectVerifiedFood(unsafe)
        assertNull(viewModel.uiState.value.selectedFood)
        assertNull(viewModel.uiState.value.selectedFoodItemId)
        assertTrue(viewModel.smartInputState.value.message.orEmpty().contains("바로 바꾸기 어려워요"))

        viewModel.saveRecord {}
        advanceUntilIdle()
        assertTrue(insertedMeals.isEmpty())
    }

    @Test
    fun `밥 생활 단위 선택은 그램 입력 없이 예상 칼로리와 표시 스냅샷을 저장한다`() = runTest(dispatcher.scheduler) {
        val rice = FoodItem(
            id = "official-rice", sourceType = "K-FIND", sourceFoodCode = "rice", name = "흰밥",
            normalizedName = "흰밥", category = "밥류", referenceAmount = 100.0, unit = "g",
            energyKcal = 140.0, servingDescription = "100g 기준", dataVersion = "test",
            createdAt = 1, updatedAt = 1
        )
        viewModel.selectVerifiedFood(rice)
        viewModel.selectPortionPreset(PortionGuide.presets(rice).first { it.label == "반 공기" })
        assertEquals("105", viewModel.uiState.value.servingAmount)
        assertEquals("147", viewModel.uiState.value.calories)
        assertFalse(viewModel.uiState.value.preciseAmountOpen)

        viewModel.saveRecord {}
        advanceUntilIdle()

        val saved = insertedMeals.single()
        assertEquals(105.0, saved.servingAmount ?: 0.0, 0.0)
        assertEquals("반 공기", saved.portionDisplayLabel)
        assertEquals("HOUSEHOLD_UNIT", saved.portionEstimationType)
        assertEquals(147, saved.calories)
    }

    @Test
    fun `검색 음식은 먹은 양을 고르기 전 기준량 그대로 저장되지 않는다`() = runTest(dispatcher.scheduler) {
        val rice = FoodItem(
            id = "official-rice", sourceType = "K-FIND", sourceFoodCode = "rice", name = "흰밥",
            normalizedName = "흰밥", category = "밥류", referenceAmount = 100.0, unit = "g",
            energyKcal = 140.0, servingDescription = "100g 기준", dataVersion = "test",
            createdAt = 1, updatedAt = 1
        )
        viewModel.selectVerifiedFood(rice)
        viewModel.saveRecord {}
        advanceUntilIdle()

        assertTrue(insertedMeals.isEmpty())
        assertEquals("먹은 양을 선택하거나 더 정확히 입력해 주세요.", viewModel.uiState.value.saveError)
    }

    @Test
    fun `잘 모르겠어요는 그릇과 비율 선택만으로 계산하며 이전 양을 다시 쓸 수 있다`() {
        val rice = FoodItem(
            id = "official-rice", sourceType = "K-FIND", sourceFoodCode = "rice", name = "흰밥",
            normalizedName = "흰밥", category = "밥류", referenceAmount = 100.0, unit = "g",
            energyKcal = 140.0, servingDescription = "100g 기준", dataVersion = "test",
            createdAt = 1, updatedAt = 1
        )
        viewModel.selectVerifiedFood(rice)
        viewModel.startUnknownPortion()
        assertTrue(viewModel.uiState.value.portionHelpOpen)
        viewModel.choosePortionVessel(PortionVessel.RICE_BOWL)
        viewModel.choosePortionFraction(PortionFraction.HALF)
        assertEquals("147", viewModel.uiState.value.calories)
        assertEquals("밥그릇 · 절반 정도", viewModel.uiState.value.selectedPortion?.label)
        assertFalse(viewModel.uiState.value.portionHelpOpen)

        viewModel.selectRecentMeal(MealRecord(
            date = "2026-09-20", time = "12:00", mealType = com.example.healthcare.data.model.MealType.LUNCH,
            foodName = "흰밥", calories = 147, servingAmount = 105.0, servingUnit = "g",
            portionPresetId = "visual-RICE_BOWL-HALF", portionDisplayLabel = "밥그릇 · 절반 정도",
            portionEstimationType = "VISUAL_ESTIMATE"
        ))
        assertEquals("밥그릇 · 절반 정도", viewModel.uiState.value.selectedPortion?.label)
        assertEquals("147", viewModel.uiState.value.calories)
    }

    @Test
    fun `미등록 바코드 상품은 사용자 확인값으로 등록한 뒤 기록한다`() = runTest(dispatcher.scheduler) {
        viewModel.lookupBarcode("8801234567890")
        advanceUntilIdle()
        viewModel.showBarcodeManualRegistration()
        viewModel.onFoodNameChange("사용자 확인 제품")
        viewModel.onServingAmountChange("60")
        viewModel.onServingUnitChange("g")
        viewModel.onCaloriesChange("192")

        viewModel.saveRecord {}
        advanceUntilIdle()

        val product = registeredProducts.single()
        assertEquals("USER_CONFIRMED", product.sourceType)
        assertEquals("8801234567890", product.barcode)
        assertEquals(60.0, product.referenceAmount, 0.0)
        assertEquals(192.0, product.energyKcal, 0.0)
        assertEquals(product.id, insertedMeals.single().foodItemId)
        assertEquals(RecordSource.BARCODE, insertedMeals.single().source)
    }

    @Test
    fun `사진 수동 기록은 MANUAL 한 건으로 저장하고 임시 사진을 삭제한다`() =
        runTest(dispatcher.scheduler) {
            val photo = createCaptureFile()
            viewModel.openCamera()
            viewModel.onPhotoCaptured(photo.absolutePath)
            viewModel.usePhotoForManualRecord()
            viewModel.onFoodNameChange("비빔밥")
            viewModel.onServingAmountChange("0.5")
            viewModel.onServingUnitChange("인분")
            viewModel.onCaloriesChange("500")
            var successCount = 0

            viewModel.saveRecord { successCount++ }
            viewModel.saveRecord { successCount++ }
            advanceUntilIdle()

            assertEquals(1, insertedMeals.size)
            val saved = insertedMeals.single()
            assertEquals("비빔밥", saved.foodName)
            assertEquals(500, saved.calories)
            assertEquals(0.5, saved.servingAmount)
            assertEquals("인분", saved.servingUnit)
            assertEquals(RecordSource.MANUAL, saved.source)
            assertNull(saved.photoAnalysisId)
            assertNull(saved.photoRequestId)
            assertEquals(1, successCount)
            assertFalse(photo.exists())
            assertEquals(PhotoAnalysisUiState.Idle, viewModel.photoState.value)
            assertEquals(0, analysisCalls)
        }

    @Test
    fun `사진 기록 취소는 임시 사진을 삭제한다`() {
        val photo = createCaptureFile()
        viewModel.openCamera()
        viewModel.onPhotoCaptured(photo.absolutePath)
        viewModel.usePhotoForManualRecord()

        viewModel.cancelPhotoFlow()

        assertFalse(photo.exists())
        assertEquals(PhotoAnalysisUiState.Idle, viewModel.photoState.value)
    }

    @Test
    fun `미리보기 취소와 재촬영은 오류 없이 이전 사진을 삭제하고 입력을 유지한다`() {
        viewModel.onFoodNameChange("입력 유지")
        val firstPhoto = createCaptureFile()
        viewModel.openCamera()
        viewModel.onPhotoCaptured(firstPhoto.absolutePath)
        viewModel.retakePhoto()

        assertFalse(firstPhoto.exists())
        assertEquals(PhotoAnalysisUiState.Camera, viewModel.photoState.value)

        val secondPhoto = createCaptureFile()
        viewModel.onPhotoCaptured(secondPhoto.absolutePath)
        viewModel.cancelPhotoFlow()

        assertFalse(secondPhoto.exists())
        assertEquals(PhotoAnalysisUiState.Idle, viewModel.photoState.value)
        assertEquals("입력 유지", viewModel.uiState.value.foodName)
    }

    @Test
    fun `촬영 취소 뒤 늦은 실패와 성공 콜백은 오류를 표시하지 않고 사진을 삭제한다`() {
        viewModel.onFoodNameChange("입력 유지")
        viewModel.openCamera()
        viewModel.cancelPhotoFlow()

        val latePhoto = createCaptureFile()
        viewModel.showCameraCaptureError()
        viewModel.showCameraFileError()
        viewModel.showCameraUnavailable()
        viewModel.onPhotoCaptured(latePhoto.absolutePath)

        assertEquals(PhotoAnalysisUiState.Idle, viewModel.photoState.value)
        assertEquals("입력 유지", viewModel.uiState.value.foodName)
        assertFalse(latePhoto.exists())
    }

    @Test
    fun `진행 중인 촬영과 파일 실패는 취소와 구분해 오류를 표시한다`() {
        viewModel.openCamera()
        viewModel.showCameraCaptureError()
        assertEquals(
            PhotoAnalysisUiState.Error(PhotoAnalysisError.CAMERA_CAPTURE_FAILED),
            viewModel.photoState.value
        )

        viewModel.openCamera()
        viewModel.showCameraFileError()
        assertEquals(
            PhotoAnalysisUiState.Error(PhotoAnalysisError.INVALID_IMAGE),
            viewModel.photoState.value
        )
    }

    @Test
    fun `저장 성공 후 다음 기록을 위해 입력 상태를 초기화한다`() =
        runTest(dispatcher.scheduler) {
            viewModel.onFoodNameChange("김밥")
            viewModel.onServingAmountChange("1")
            viewModel.onServingUnitChange("인분")
            viewModel.onCaloriesChange("420")
            viewModel.onMemoChange("가상 검수 데이터")

            viewModel.saveRecord {}
            advanceUntilIdle()

            assertEquals(1, insertedMeals.size)
            assertEquals("", viewModel.uiState.value.foodName)
            assertEquals("", viewModel.uiState.value.servingAmount)
            assertEquals("", viewModel.uiState.value.servingUnit)
            assertEquals("", viewModel.uiState.value.calories)
            assertEquals("", viewModel.uiState.value.memo)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `라면과 밥은 각 근거량으로 계산해 두 기록을 원자적으로 저장한다`() = runTest(dispatcher.scheduler) {
        val ramen = verifiedFood("ramen", "라면_치즈", "면 및 만두류", 100.0, "g", 99.0).copy(
            sourceType = "K-FIND-PRODUCT",
            servingDescription = "100g 기준 · 공식 총내용량 100g · 포장단위 봉"
        )
        val rice = verifiedFood("rice", "쌀밥", "밥류", 100.0, "g", 166.0)

        viewModel.selectVerifiedFood(ramen)
        viewModel.selectPortionPreset(requireNotNull(PortionGuide.defaultPreset(ramen)))
        viewModel.showCompanionSearch("쌀밥")
        viewModel.selectSearchFood(rice)
        val companion = viewModel.uiState.value.companionFoods.single()
        viewModel.updateCompanionPortion(
            companion.selectionId,
            PortionGuide.presets(rice).first { it.label == "반 공기" }
        )

        viewModel.saveRecord {}
        advanceUntilIdle()

        assertEquals(2, insertedMeals.size)
        assertEquals(listOf("라면 · 치즈", "쌀밥"), insertedMeals.map { it.foodName })
        assertEquals(99, insertedMeals[0].calories)
        assertEquals(174, insertedMeals[1].calories)
        assertEquals("반 공기", insertedMeals[1].portionDisplayLabel)
        assertEquals(273, insertedMeals.sumOf { it.calories })
    }

    @Test
    fun `같은 함께 먹은 음식도 실제 두 번 선택하면 별도 수량으로 유지한다`() {
        val burger = verifiedFood("burger", "햄버거_치즈", "빵 및 과자류", 100.0, "g", 284.0)
        val fries = verifiedFood("fries", "감자튀김_포테이토", "튀김류", 100.0, "g", 199.0).copy(
            sourceType = "K-FIND-PRODUCT",
            servingDescription = "100g 기준 · 공식 총내용량 100g · 포장단위 봉"
        )
        viewModel.selectVerifiedFood(burger)
        repeat(2) {
            viewModel.showCompanionSearch("감자튀김")
            viewModel.selectSearchFood(fries)
        }
        assertEquals(2, viewModel.uiState.value.companionFoods.size)
        assertEquals(398, viewModel.uiState.value.companionFoods.sumOf { it.calories })
    }

    private fun verifiedFood(
        id: String,
        name: String,
        category: String,
        amount: Double,
        unit: String,
        kcal: Double
    ) = FoodItem(
        id = id, sourceType = "K-FIND", sourceFoodCode = id, name = name,
        normalizedName = name.replace("_", ""), category = category,
        referenceAmount = amount, unit = unit, energyKcal = kcal,
        servingDescription = "${amount.toInt()}$unit 기준", dataVersion = "test",
        createdAt = 0, updatedAt = 0
    )

    private fun createCaptureFile(): File {
        val directory = File(cacheRoot, "food_photo_captures").apply { mkdirs() }
        return File(directory, "capture-${System.nanoTime()}.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }
}
