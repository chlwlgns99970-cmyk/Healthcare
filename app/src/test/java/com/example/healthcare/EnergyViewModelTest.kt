package com.example.healthcare

import com.example.healthcare.data.dao.CalorieGoalDao
import com.example.healthcare.data.dao.EnergyProfileDao
import com.example.healthcare.data.dao.MealRecordDao
import com.example.healthcare.data.BodyProfilePersistence
import com.example.healthcare.data.WeightGoalPersistence
import com.example.healthcare.data.WeightLossGoal
import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.GoalRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodyProfileCalculator
import com.example.healthcare.domain.BodySex
import com.example.healthcare.ui.viewmodel.DashboardViewModel
import com.example.healthcare.ui.viewmodel.SettingsViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnergyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 9, 16)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `BMR 미설정이면 기존 직접 목표를 유지한다`() = runTest(dispatcher.scheduler) {
        val dashboard = dashboardViewModel(intake = 2000, goal = 2100, profile = null)
        val collection = backgroundScope.launch { dashboard.energyUiState.collect {} }
        advanceUntilIdle()

        assertFalse(dashboard.energyUiState.value.isConfigured)
        assertEquals(2100, dashboard.energyUiState.value.effectiveTargetCalories)
        collection.cancel()
    }

    @Test
    fun `정상 프로필은 유지 목표와 초과 환산을 즉시 만든다`() = runTest(dispatcher.scheduler) {
        val profile = energyProfile(targetMode = TargetMode.MAINTENANCE)
        val dashboard = dashboardViewModel(intake = 2625, goal = 2000, profile = profile)
        val collection = backgroundScope.launch { dashboard.energyUiState.collect {} }
        advanceUntilIdle()

        val state = dashboard.energyUiState.value
        assertEquals(2325, state.maintenanceCalories)
        assertEquals(2325, state.effectiveTargetCalories)
        assertEquals(300, state.dailySurplusCalories)
        assertNotNull(state.sevenDayEquivalentKg)
        assertNotNull(state.thirtyDayEquivalentKg)
        collection.cancel()
    }

    @Test
    fun `BMR 초과지만 유지 기준 이내면 체중 환산이 없다`() = runTest(dispatcher.scheduler) {
        val dashboard = dashboardViewModel(2000, 2000, energyProfile())
        val collection = backgroundScope.launch { dashboard.energyUiState.collect {} }
        advanceUntilIdle()

        val state = dashboard.energyUiState.value
        assertEquals(0, state.dailySurplusCalories)
        assertNull(state.sevenDayEquivalentKg)
        assertNull(state.thirtyDayEquivalentKg)
        collection.cancel()
    }

    @Test
    fun `설정 입력은 유지 칼로리 미리보기를 갱신한다`() = runTest(dispatcher.scheduler) {
        val energyDao = FakeEnergyProfileDao()
        val viewModel = settingsViewModel(energyDao)

        viewModel.onBmrChange("1500")
        viewModel.onActivityLevelSelected(ActivityLevel.LIGHT)

        assertEquals(2325, viewModel.energyState.value.maintenancePreviewKcal)
    }

    @Test
    fun `CUSTOM PAL 범위 오류는 저장하지 않는다`() = runTest(dispatcher.scheduler) {
        val energyDao = FakeEnergyProfileDao()
        val viewModel = settingsViewModel(energyDao)
        viewModel.onBmrChange("1500")
        viewModel.onActivityLevelSelected(ActivityLevel.CUSTOM)
        viewModel.onCustomPalChange("2.41")

        viewModel.saveEnergyProfile()
        advanceUntilIdle()

        assertEquals("PAL은 1.40 이상 2.40 이하로 입력해 주세요.", viewModel.energyState.value.customPalError)
        assertNull(energyDao.profile.value)
    }

    @Test
    fun `설정 저장은 같은 날짜 이력을 중복 생성하지 않고 최신값으로 갱신한다`() = runTest(dispatcher.scheduler) {
        val energyDao = FakeEnergyProfileDao()
        val viewModel = settingsViewModel(energyDao)
        viewModel.onBmrChange("1500")
        viewModel.onActivityLevelSelected(ActivityLevel.LIGHT)
        viewModel.onTargetModeSelected(TargetMode.BMR)
        viewModel.saveEnergyProfile()
        advanceUntilIdle()

        val originalId = energyDao.profile.value?.profileId
        viewModel.onBmrChange("1600")
        viewModel.saveEnergyProfile()
        advanceUntilIdle()

        assertEquals(1, energyDao.saveCount)
        assertEquals(originalId, energyDao.profile.value?.profileId)
        assertEquals(1600, energyDao.profile.value?.basalMetabolicRateKcal)
        assertEquals("에너지 기준을 저장했습니다.", viewModel.energyState.value.saveMessage)
    }

    @Test
    fun `저장 실패는 성공 상태로 표시하지 않는다`() = runTest(dispatcher.scheduler) {
        val dao = FakeEnergyProfileDao()
        val failingRepository = object : EnergyProfileRepository(dao) {
            override suspend fun saveProfile(
                basalMetabolicRateKcal: Int,
                activityLevel: ActivityLevel,
                palMultiplier: Double,
                targetMode: TargetMode,
                effectiveFromDate: String,
                nowEpochMillis: Long
            ) {
                error("저장 실패")
            }
        }
        val viewModel = SettingsViewModel(
            goalRepository = GoalRepository(FakeGoalDao(2000)),
            energyProfileRepository = failingRepository,
            todayProvider = { today },
            nowProvider = { 100L }
        )
        viewModel.onBmrChange("1500")
        viewModel.onActivityLevelSelected(ActivityLevel.MODERATE)

        viewModel.saveEnergyProfile()
        advanceUntilIdle()

        assertNull(viewModel.energyState.value.saveMessage)
        assertEquals("저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", viewModel.energyState.value.saveError)
    }

    @Test
    fun `신체정보 저장은 운동 체중과 예상 BMR 입력을 같은 source of truth로 갱신한다`() = runTest(dispatcher.scheduler) {
        val bodyStore = FakeBodyProfileStore()
        val energyDao = FakeEnergyProfileDao()
        val viewModel = SettingsViewModel(
            goalRepository = GoalRepository(FakeGoalDao(2000)),
            energyProfileRepository = EnergyProfileRepository(energyDao),
            bodyProfileStore = bodyStore,
            todayProvider = { today },
            nowProvider = { 100L }
        )
        viewModel.onBmrChange("1500")
        viewModel.onBodySexSelected(BodySex.MALE)
        viewModel.onBodyAgeChange("35")
        viewModel.onBodyHeightChange("175")
        viewModel.onBodyWeightChange("70.4")
        var savedWeight: Double? = null

        viewModel.saveBodyProfile { savedWeight = it }
        advanceUntilIdle()

        assertEquals(70.4, savedWeight ?: 0.0, 0.0)
        assertEquals(70.4, bodyStore.saved?.weightKg ?: 0.0, 0.0)
        assertEquals(1628, viewModel.bodyProfileState.value.estimatedBmrKcal)
        assertEquals("1628", viewModel.energyState.value.bmrInput)
        assertNull(energyDao.profile.value)
    }

    @Test
    fun `저장된 활동 수준이 있으면 몸무게 변경이 BMR과 유지 칼로리에 즉시 반영된다`() = runTest(dispatcher.scheduler) {
        val bodyStore = FakeBodyProfileStore(BodyProfile(BodySex.MALE, 35, 175.0, 70.0))
        val energyDao = FakeEnergyProfileDao(energyProfile(TargetMode.MAINTENANCE))
        val viewModel = SettingsViewModel(
            goalRepository = GoalRepository(FakeGoalDao(2000)),
            energyProfileRepository = EnergyProfileRepository(energyDao),
            bodyProfileStore = bodyStore,
            todayProvider = { today },
            nowProvider = { 200L }
        )
        advanceUntilIdle()
        viewModel.onBodyWeightChange("90")
        viewModel.saveBodyProfile()
        advanceUntilIdle()

        val expectedBmr = BodyProfileCalculator.estimateBmr(BodyProfile(BodySex.MALE, 35, 175.0, 90.0))
        assertEquals(expectedBmr, energyDao.profile.value?.basalMetabolicRateKcal)
        assertEquals((expectedBmr * 1.55).toInt(), viewModel.energyState.value.maintenancePreviewKcal)
        assertEquals(TargetMode.MAINTENANCE, energyDao.profile.value?.targetMode)
    }

    @Test
    fun `저장된 신체정보를 다시 열면 모든 값과 계산값을 복원한다`() {
        val stored = BodyProfile(BodySex.FEMALE, 42, 163.5, 58.2)
        val viewModel = SettingsViewModel(
            goalRepository = GoalRepository(FakeGoalDao(2000)),
            energyProfileRepository = EnergyProfileRepository(FakeEnergyProfileDao()),
            bodyProfileStore = FakeBodyProfileStore(stored),
            todayProvider = { today }
        )
        val state = viewModel.bodyProfileState.value
        assertEquals(BodySex.FEMALE, state.sex)
        assertEquals("42", state.ageInput)
        assertEquals("163.5", state.heightInput)
        assertEquals("58.2", state.weightInput)
        assertEquals(BodyProfileCalculator.estimateBmr(stored), state.estimatedBmrKcal)
    }

    @Test
    fun `직접 목표와 체중 목표를 횟수 제한 없이 번갈아 적용한다`() = runTest(dispatcher.scheduler) {
        val goalDao = FakeGoalDao(2000)
        val energyDao = FakeEnergyProfileDao(energyProfile(TargetMode.MAINTENANCE))
        val goalRepository = GoalRepository(goalDao)
        val energyRepository = EnergyProfileRepository(energyDao)
        val weightStore = FakeWeightGoalStore()
        val settings = SettingsViewModel(
            goalRepository = goalRepository,
            energyProfileRepository = energyRepository,
            bodyProfileStore = FakeBodyProfileStore(BodyProfile(BodySex.MALE, 30, 170.0, 70.0)),
            weightGoalStore = weightStore,
            todayProvider = { today },
            nowProvider = { 100L }
        )
        val dashboard = DashboardViewModel(
            MealRepository(FakeMealDao(0)), goalRepository, energyRepository
        )
        val dashboardCollection = backgroundScope.launch { dashboard.targetCalories.collect {} }
        advanceUntilIdle()

        settings.updateGoal(1800) {}
        advanceUntilIdle()
        assertEquals(1800, goalDao.current.targetCalories)
        assertEquals(TargetMode.MANUAL, energyDao.profile.value?.targetMode)
        assertEquals(1800, dashboard.targetCalories.value)

        settings.updateGoal(2100) {}
        advanceUntilIdle()
        assertEquals(2100, goalDao.current.targetCalories)
        assertEquals(2100, dashboard.targetCalories.value)
        assertFalse(settings.isSaving.value)

        settings.onWeightGoalTargetChange("69")
        settings.onWeightGoalWeeksChange("4")
        settings.applyWeightGoal()
        advanceUntilIdle()
        assertEquals(2050, goalDao.current.targetCalories)
        assertEquals(2050, dashboard.targetCalories.value)

        settings.updateGoal(2000) {}
        advanceUntilIdle()
        assertEquals(2000, goalDao.current.targetCalories)
        assertEquals(2000, dashboard.targetCalories.value)
        assertEquals(2050, weightStore.saved?.appliedTargetKcal)

        settings.applyWeightGoal()
        advanceUntilIdle()
        assertEquals(2050, goalDao.current.targetCalories)
        assertEquals(2050, dashboard.targetCalories.value)

        settings.onIntakeTargetChange("1900")
        settings.applyIntakeTarget()
        advanceUntilIdle()
        assertEquals(1900, goalDao.current.targetCalories)
        assertEquals(1900, dashboard.targetCalories.value)
        assertEquals(TargetMode.MANUAL, energyDao.profile.value?.targetMode)
        dashboardCollection.cancel()
    }

    private fun dashboardViewModel(
        intake: Int,
        goal: Int,
        profile: EnergyProfileHistory?
    ): DashboardViewModel = DashboardViewModel(
        MealRepository(FakeMealDao(intake)),
        GoalRepository(FakeGoalDao(goal)),
        EnergyProfileRepository(FakeEnergyProfileDao(profile))
    )

    private fun settingsViewModel(dao: FakeEnergyProfileDao) = SettingsViewModel(
        goalRepository = GoalRepository(FakeGoalDao(2000)),
        energyProfileRepository = EnergyProfileRepository(dao),
        todayProvider = { today },
        nowProvider = { 100L }
    )

    private fun energyProfile(targetMode: TargetMode = TargetMode.MANUAL) = EnergyProfileHistory(
        profileId = 1,
        basalMetabolicRateKcal = 1500,
        activityLevelCode = ActivityLevel.LIGHT,
        palMultiplier = 1.55,
        targetMode = targetMode,
        effectiveFromDate = today.toString(),
        createdAt = 1,
        updatedAt = 1
    )

    private class FakeMealDao(private val intake: Int) : MealRecordDao {
        override suspend fun insertMeal(meal: MealRecord) = Unit
        override suspend fun insertPhotoMeals(meals: List<MealRecord>): List<Long> = emptyList()
        override suspend fun updateMeal(meal: MealRecord) = Unit
        override suspend fun deleteMeal(meal: MealRecord) = Unit
        override fun getAllMeals(): Flow<List<MealRecord>> = flowOf(emptyList())
        override fun getMealsByDate(date: String): Flow<List<MealRecord>> = flowOf(emptyList())
        override fun getTotalCaloriesByDate(date: String): Flow<Int?> = flowOf(intake)
        override fun getNutritionByDate(date: String): Flow<List<com.example.healthcare.data.model.MealNutritionRow>> = flowOf(emptyList())
    }

    private class FakeGoalDao(goal: Int) : CalorieGoalDao {
        private val goalFlow = MutableStateFlow(CalorieGoal(1, goal, "2020-01-01"))
        private var nextId = 2L
        val current: CalorieGoal get() = goalFlow.value
        override suspend fun insertGoal(goal: CalorieGoal) {
            goalFlow.value = if (goal.id == 0L) goal.copy(id = nextId++) else goal
        }
        override fun getLatestGoal(): Flow<CalorieGoal?> = goalFlow
        override fun getGoalForDate(date: String): Flow<CalorieGoal?> = goalFlow
        override fun getAllGoals(): Flow<List<CalorieGoal>> = flowOf(listOf(goalFlow.value))
    }

    private class FakeEnergyProfileDao(initial: EnergyProfileHistory? = null) : EnergyProfileDao {
        val profile = MutableStateFlow(initial)
        var saveCount = if (initial == null) 0 else 1
        override fun getAllProfiles(): Flow<List<EnergyProfileHistory>> =
            profile.map { value -> listOfNotNull(value) }
        override fun getProfileForDate(date: String): Flow<EnergyProfileHistory?> = profile
        override suspend fun getProfileStartingOn(date: String): EnergyProfileHistory? =
            profile.value?.takeIf { it.effectiveFromDate == date }

        override suspend fun upsertProfile(profile: EnergyProfileHistory) {
            val assigned = if (profile.profileId == 0L) profile.copy(profileId = 1L) else profile
            this.profile.value = assigned
            if (saveCount == 0) saveCount = 1
        }
    }

    private class FakeBodyProfileStore(initial: BodyProfile? = null) : BodyProfilePersistence {
        var saved: BodyProfile? = initial
        override fun read(): BodyProfile? = saved
        override fun readExerciseWeight(): Double? = saved?.weightKg
        override fun save(profile: BodyProfile) { saved = profile }
    }

    private class FakeWeightGoalStore : WeightGoalPersistence {
        var saved: WeightLossGoal? = null
        override fun read(): WeightLossGoal? = saved
        override fun save(goal: WeightLossGoal) { saved = goal }
    }
}
