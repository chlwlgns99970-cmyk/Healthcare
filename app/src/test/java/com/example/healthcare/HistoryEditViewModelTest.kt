package com.example.healthcare

import com.example.healthcare.data.dao.CalorieGoalDao
import com.example.healthcare.data.dao.EnergyProfileDao
import com.example.healthcare.data.dao.MealRecordDao
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.GoalRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.ui.viewmodel.HistoryViewModel
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val originalDate = LocalDate.of(2026, 9, 17)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `편집은 기존 값을 불러오고 같은 기록의 모든 사용자 필드를 갱신한다`() = runTest(dispatcher.scheduler) {
        val dao = FakeMealDao(listOf(photoMeal()))
        val viewModel = historyViewModel(dao)
        val mealsCollection = backgroundScope.launch { viewModel.dailyMeals.collect {} }
        val totalCollection = backgroundScope.launch { viewModel.dailyTotalCalories.collect {} }
        advanceUntilIdle()

        viewModel.startEditing(photoMeal())
        assertEquals("원래 음식", viewModel.editState.value.foodName)
        assertEquals("320", viewModel.editState.value.calories)
        assertEquals("1", viewModel.editState.value.servingAmount)
        assertEquals("인분", viewModel.editState.value.servingUnit)

        val changedDate = originalDate.minusDays(1)
        viewModel.onEditFoodNameChange("수정한 음식")
        viewModel.onEditCaloriesChange("450")
        viewModel.onEditMealTypeChange(MealType.DINNER)
        viewModel.onEditDateChange(changedDate)
        viewModel.onEditTimeChange(LocalTime.of(19, 45))
        viewModel.onEditServingAmountChange("1.5")
        viewModel.onEditServingUnitChange("그릇")
        viewModel.onEditMemoChange("수정한 메모")

        var callbackMeal: MealRecord? = null
        viewModel.saveMealEdit { callbackMeal = it }
        viewModel.saveMealEdit { callbackMeal = it }
        advanceUntilIdle()

        assertEquals(1, dao.updateCount)
        val updated = dao.meals.value.single()
        assertEquals(7L, updated.id)
        assertEquals("2026-09-16", updated.date)
        assertEquals("19:45", updated.time)
        assertEquals(MealType.DINNER, updated.mealType)
        assertEquals("수정한 음식", updated.foodName)
        assertEquals(450, updated.calories)
        assertEquals(1.5, updated.servingAmount!!, 0.0)
        assertEquals("그릇", updated.servingUnit)
        assertEquals("수정한 메모", updated.memo)
        assertEquals(RecordSource.PHOTO_AI, updated.source)
        assertEquals("request-1", updated.photoRequestId)
        assertEquals(100L, updated.createdAtEpochMillis)
        assertEquals(999L, updated.updatedAtEpochMillis)
        assertTrue(updated.wasAiResultEdited)
        assertEquals(updated, callbackMeal)
        assertEquals(changedDate, viewModel.selectedDate.value)
        assertEquals(450, viewModel.dailyTotalCalories.value)
        assertEquals(0, dao.getTotalCaloriesByDate(originalDate.toString()).first())
        assertFalse(viewModel.editState.value.isEditing)

        mealsCollection.cancel()
        totalCollection.cancel()
    }

    @Test
    fun `잘못된 편집값은 저장하지 않고 각 입력 오류를 유지한다`() = runTest(dispatcher.scheduler) {
        val dao = FakeMealDao(listOf(photoMeal()))
        val viewModel = historyViewModel(dao)
        viewModel.startEditing(photoMeal())
        viewModel.onEditFoodNameChange(" ")
        viewModel.onEditCaloriesChange("0")
        viewModel.onEditServingAmountChange("잘못된 값")

        viewModel.saveMealEdit()
        advanceUntilIdle()

        assertEquals(0, dao.updateCount)
        assertNotNull(viewModel.editState.value.foodNameError)
        assertNotNull(viewModel.editState.value.caloriesError)
        assertNotNull(viewModel.editState.value.servingAmountError)
        assertTrue(viewModel.editState.value.isEditing)
    }

    @Test
    fun `수정 실패 시 기존 기록은 바뀌지 않고 재시도 가능한 오류를 표시한다`() = runTest(dispatcher.scheduler) {
        val original = photoMeal()
        val dao = FakeMealDao(listOf(original), failUpdates = true)
        val viewModel = historyViewModel(dao)
        viewModel.startEditing(original)
        viewModel.onEditFoodNameChange("저장되면 안 되는 값")

        viewModel.saveMealEdit()
        advanceUntilIdle()

        assertEquals(original, dao.meals.value.single())
        assertEquals("기록을 수정하지 못했습니다. 다시 시도해주세요.", viewModel.editState.value.saveError)
        assertFalse(viewModel.editState.value.isSaving)
        assertTrue(viewModel.editState.value.isEditing)
    }

    @Test
    fun `생활 단위 기록은 편집에서 복원되고 비율 변경 후 식별자와 생성 시각을 보존한다`() = runTest(dispatcher.scheduler) {
        val original = photoMeal().copy(
            calories = 294, servingAmount = 210.0, servingUnit = "g",
            portionPresetId = "rice-one", portionDisplayLabel = "한 공기",
            portionEstimationType = "HOUSEHOLD_UNIT", portionSourceReference = "식품안전나라"
        )
        val dao = FakeMealDao(listOf(original))
        val viewModel = historyViewModel(dao)
        viewModel.startEditing(original)
        assertEquals("한 공기", viewModel.editState.value.portionDisplayLabel)

        viewModel.selectEditRatio(0.5, "절반")
        assertEquals("105", viewModel.editState.value.servingAmount)
        assertEquals("147", viewModel.editState.value.calories)
        viewModel.saveMealEdit()
        advanceUntilIdle()

        val saved = dao.meals.value.single()
        assertEquals(7L, saved.id)
        assertEquals(100L, saved.createdAtEpochMillis)
        assertEquals(147, saved.calories)
        assertEquals("한 공기 · 절반", saved.portionDisplayLabel)
        assertEquals("VISUAL_ESTIMATE", saved.portionEstimationType)
    }

    private fun historyViewModel(dao: FakeMealDao) = HistoryViewModel(
        mealRepository = MealRepository(dao),
        goalRepository = GoalRepository(FakeGoalDao()),
        energyProfileRepository = EnergyProfileRepository(FakeEnergyProfileDao()),
        todayProvider = { originalDate },
        nowProvider = { 999L }
    )

    private fun photoMeal() = MealRecord(
        id = 7,
        date = originalDate.toString(),
        time = "08:30",
        mealType = MealType.BREAKFAST,
        foodName = "원래 음식",
        calories = 320,
        memo = "원래 메모",
        servingAmount = 1.0,
        servingUnit = "인분",
        source = RecordSource.PHOTO_AI,
        photoRequestId = "request-1",
        photoItemId = "item-1",
        createdAtEpochMillis = 100L,
        updatedAtEpochMillis = 100L
    )

    private class FakeMealDao(
        initialMeals: List<MealRecord>,
        private val failUpdates: Boolean = false
    ) : MealRecordDao {
        val meals = MutableStateFlow(initialMeals)
        var updateCount = 0

        override suspend fun insertMeal(meal: MealRecord) = Unit
        override suspend fun insertPhotoMeals(meals: List<MealRecord>): List<Long> = emptyList()

        override suspend fun updateMeal(meal: MealRecord) {
            if (failUpdates) error("저장 실패")
            updateCount += 1
            meals.value = meals.value.map { current -> if (current.id == meal.id) meal else current }
        }

        override suspend fun deleteMeal(meal: MealRecord) {
            meals.value = meals.value.filterNot { it.id == meal.id }
        }

        override fun getAllMeals(): Flow<List<MealRecord>> = meals

        override fun getMealsByDate(date: String): Flow<List<MealRecord>> =
            meals.map { records -> records.filter { it.date == date }.sortedBy { it.time } }

        override fun getTotalCaloriesByDate(date: String): Flow<Int?> =
            meals.map { records -> records.filter { it.date == date }.sumOf { it.calories } }

        override fun getNutritionByDate(date: String): Flow<List<com.example.healthcare.data.model.MealNutritionRow>> =
            meals.map { records ->
                records.filter { it.date == date }.map {
                    com.example.healthcare.data.model.MealNutritionRow(
                        it.id, it.carbohydrateGrams, it.proteinGrams, it.fatGrams
                    )
                }
            }
    }

    private class FakeGoalDao : CalorieGoalDao {
        override suspend fun insertGoal(goal: CalorieGoal) = Unit
        override fun getLatestGoal(): Flow<CalorieGoal?> = flowOf(null)
        override fun getGoalForDate(date: String): Flow<CalorieGoal?> = flowOf(null)
        override fun getAllGoals(): Flow<List<CalorieGoal>> = flowOf(emptyList())
    }

    private class FakeEnergyProfileDao : EnergyProfileDao {
        override fun getAllProfiles(): Flow<List<EnergyProfileHistory>> = flowOf(emptyList())
        override fun getProfileForDate(date: String): Flow<EnergyProfileHistory?> = flowOf(null)
        override suspend fun getProfileStartingOn(date: String): EnergyProfileHistory? = null
        override suspend fun upsertProfile(profile: EnergyProfileHistory) = Unit
    }
}
