package com.example.healthcare

import com.example.healthcare.data.dao.FrequentFoodDao
import com.example.healthcare.data.dao.MealRecordDao
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.data.photo.FoodPhotoAnalysisRepository
import com.example.healthcare.data.photo.model.DetectedFoodItem
import com.example.healthcare.data.photo.model.FoodPhotoAnalysis
import com.example.healthcare.data.photo.model.FoodPhotoAnalysisRequest
import com.example.healthcare.data.photo.model.PhotoAnalysisOutcome
import com.example.healthcare.data.photo.model.PhotoAnalysisProgress
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import com.example.healthcare.ui.viewmodel.PhotoAnalysisUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoAnalysisViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val insertedPhotoMeals = mutableListOf<MealRecord>()
    private var analysisCalls = 0

    private val mealDao = object : MealRecordDao {
        override suspend fun insertMeal(meal: MealRecord) = Unit
        override suspend fun insertPhotoMeals(meals: List<MealRecord>): List<Long> {
            insertedPhotoMeals += meals
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
        override suspend fun insertFood(food: FrequentFood) = Unit
        override suspend fun updateFood(food: FrequentFood) = Unit
        override suspend fun deleteFood(food: FrequentFood) = Unit
        override fun getAllFoods(): Flow<List<FrequentFood>> = flowOf(emptyList())
        override fun getFavoriteFoods(): Flow<List<FrequentFood>> = flowOf(emptyList())
        override fun searchFoods(query: String): Flow<List<FrequentFood>> = flowOf(emptyList())
    }

    private val analysisRepository = object : FoodPhotoAnalysisRepository {
        override suspend fun analyze(
            request: FoodPhotoAnalysisRequest,
            onProgress: (PhotoAnalysisProgress) -> Unit
        ): PhotoAnalysisOutcome {
            analysisCalls++
            onProgress(PhotoAnalysisProgress.UPLOADING)
            delay(100)
            return PhotoAnalysisOutcome.Success(
                FoodPhotoAnalysis(
                    analysisId = "analysis-1",
                    requestId = request.requestId,
                    analyzedAt = "2026-09-12T12:00:00Z",
                    detectedItems = listOf(
                        DetectedFoodItem(
                            itemId = "item-1",
                            foodName = "비빔밥",
                            alternativeNames = emptyList(),
                            confidence = 0.78,
                            estimatedAmount = 1.0,
                            amountUnit = "인분",
                            estimatedKcal = 610,
                            minimumKcal = 500,
                            maximumKcal = 750,
                            description = null
                        ),
                        DetectedFoodItem(
                            itemId = "item-2",
                            foodName = "김치",
                            alternativeNames = emptyList(),
                            confidence = null,
                            estimatedAmount = null,
                            amountUnit = null,
                            estimatedKcal = 30,
                            minimumKcal = null,
                            maximumKcal = null,
                            description = null
                        )
                    ),
                    totalEstimatedKcal = 640,
                    totalMinimumKcal = null,
                    totalMaximumKcal = null,
                    warnings = emptyList(),
                    modelVersion = "test-model",
                    isPartial = false
                )
            )
        }
    }

    private lateinit var viewModel: AddRecordViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        viewModel = AddRecordViewModel(
            MealRepository(mealDao),
            FoodRepository(foodDao),
            analysisRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `같은 사진의 분석 버튼을 연속으로 눌러도 요청은 한 번만 실행된다`() = runTest(dispatcher.scheduler) {
        viewModel.openCamera()
        viewModel.onPhotoCaptured("ignored.jpg")
        viewModel.analyzePhoto("ko-KR", "Asia/Seoul")
        viewModel.analyzePhoto("ko-KR", "Asia/Seoul")

        advanceUntilIdle()

        assertEquals(1, analysisCalls)
        assertTrue(viewModel.photoState.value is PhotoAnalysisUiState.Result)
    }

    @Test
    fun `사용자가 수정하고 선택한 항목만 한 번 저장된다`() = runTest(dispatcher.scheduler) {
        viewModel.openCamera()
        viewModel.onPhotoCaptured("ignored.jpg")
        viewModel.analyzePhoto("ko-KR", "Asia/Seoul")
        advanceUntilIdle()

        viewModel.updatePhotoItemCalories("item-1", "550")
        viewModel.togglePhotoItem("item-2", false)
        var successCount = 0
        viewModel.savePhotoAnalysisRecords { successCount++ }
        viewModel.savePhotoAnalysisRecords { successCount++ }
        advanceUntilIdle()

        assertEquals(1, insertedPhotoMeals.size)
        assertEquals(550, insertedPhotoMeals.single().calories)
        assertEquals(RecordSource.PHOTO_AI, insertedPhotoMeals.single().source)
        assertTrue(insertedPhotoMeals.single().wasAiResultEdited)
        assertEquals(1, successCount)
    }
}
