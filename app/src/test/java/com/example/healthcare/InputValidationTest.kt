package com.example.healthcare

import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InputValidationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AddRecordViewModel
    private var requestedMealDate: String? = null
    
    // 가짜 DAO
    private val fakeMealDao = object : com.example.healthcare.data.dao.MealRecordDao {
        override fun getAllMeals(): Flow<List<MealRecord>> = flowOf(emptyList())
        override fun getMealsByDate(date: String): Flow<List<MealRecord>> {
            requestedMealDate = date
            return flowOf(
                if (date == "2026-09-24") listOf(
                    MealRecord(
                        date = date,
                        time = "12:00",
                        mealType = MealType.LUNCH,
                        foodName = "날짜 검증 식사",
                        calories = 420
                    )
                ) else emptyList()
            )
        }
        override fun getTotalCaloriesByDate(date: String): Flow<Int?> = flowOf(0)
        override fun getNutritionByDate(date: String): Flow<List<com.example.healthcare.data.model.MealNutritionRow>> = flowOf(emptyList())
        override suspend fun insertMeal(meal: MealRecord) {}
        override suspend fun insertPhotoMeals(meals: List<MealRecord>): List<Long> = meals.map { 1L }
        override suspend fun updateMeal(meal: MealRecord) {}
        override suspend fun deleteMeal(meal: MealRecord) {}
    }
    
    private val fakeFoodDao = object : com.example.healthcare.data.dao.FrequentFoodDao {
        override fun getAllFoods(): Flow<List<FrequentFood>> = flowOf(emptyList())
        override fun getFavoriteFoods(): Flow<List<FrequentFood>> = flowOf(emptyList())
        override fun searchFoods(query: String): Flow<List<FrequentFood>> = flowOf(emptyList())
        override suspend fun insertFood(food: FrequentFood) {}
        override suspend fun updateFood(food: FrequentFood) {}
        override suspend fun deleteFood(food: FrequentFood) {}
    }

    private val fakeMealRepository = MealRepository(fakeMealDao)
    private val fakeFoodRepository = FoodRepository(fakeFoodDao)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AddRecordViewModel(fakeMealRepository, fakeFoodRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `음식명이 공백일 때 에러 메시지 검증`() = runTest {
        viewModel.onFoodNameChange("")
        viewModel.onCaloriesChange("100")
        
        viewModel.saveRecord {}
        
        assertEquals("음식 이름을 입력해주세요.", viewModel.uiState.value.nameError)
    }

    @Test
    fun `칼로리가 0 이하이거나 형식이 잘못되었을 때 에러 메시지 검증`() = runTest {
        // 0 입력
        viewModel.onFoodNameChange("사과")
        viewModel.onCaloriesChange("0")
        viewModel.saveRecord {}
        assertEquals("올바른 칼로리를 입력해주세요.", viewModel.uiState.value.caloriesError)
        
        // 음수 입력
        viewModel.onCaloriesChange("-10")
        viewModel.saveRecord {}
        assertEquals("올바른 칼로리를 입력해주세요.", viewModel.uiState.value.caloriesError)
        
        // 문자 입력
        viewModel.onCaloriesChange("abc")
        viewModel.saveRecord {}
        assertEquals("올바른 칼로리를 입력해주세요.", viewModel.uiState.value.caloriesError)
    }

    @Test
    fun `정상적인 입력 시 에러가 없어야 함`() = runTest {
        viewModel.onFoodNameChange("사과")
        viewModel.onCaloriesChange("150.5") // 반올림 테스트 포함
        
        viewModel.saveRecord {}
        
        assertEquals(null, viewModel.uiState.value.nameError)
        assertEquals(null, viewModel.uiState.value.caloriesError)
    }

    @Test
    fun `선택 날짜 변경 시 해당 날짜 기록만 다시 조회한다`() = runTest {
        val collection = backgroundScope.launch { viewModel.selectedDateMeals.collect {} }

        viewModel.onDateChange(java.time.LocalDate.of(2026, 9, 24))
        advanceUntilIdle()

        assertEquals("2026-09-24", requestedMealDate)
        assertEquals(listOf("날짜 검증 식사"), viewModel.selectedDateMeals.value.map { it.foodName })
        collection.cancel()
    }
}
