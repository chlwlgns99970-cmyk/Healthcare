package com.example.healthcare

import com.example.healthcare.data.entity.UserExcludedFood
import com.example.healthcare.data.entity.UserMealPreference
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.data.repository.MealPreferenceRepository
import com.example.healthcare.domain.FoodPreferencePolicy
import com.example.healthcare.domain.FoodPreferenceStyle
import com.example.healthcare.ui.viewmodel.MealPreferenceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MealPreferenceViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun onboardingSavesCanonicalStylesAndPreservesExistingKeywordsAndOtherSettings() = runTest {
        val existing = MealCoachRepository.defaultPreference(now = 123).copy(
            preferredFoods = "|된장찌개|사과|", cookingMode = "COOK", budgetLevel = "LOW",
            dietType = "VEGETARIAN", recommendationDiversity = "VARIED"
        )
        val repository = FakePreferenceRepository(existing)
        val onboarding = MealPreferenceViewModel(repository)
        advanceUntilIdle()

        onboarding.togglePreferredStyle("RICE")
        onboarding.togglePreferredStyle("STYLE:KOREAN")
        var completed = 0
        onboarding.saveTaste { completed++ }
        advanceUntilIdle()

        val saved = repository.preference.value
        assertEquals(1, completed)
        assertEquals(1, repository.saveCount)
        assertEquals(setOf(FoodPreferenceStyle.KOREAN, FoodPreferenceStyle.RICE),
            FoodPreferencePolicy.stylesFromStored(saved.preferredFoods))
        assertEquals(listOf("된장찌개", "사과"), FoodPreferencePolicy.keywordsFromStored(saved.preferredFoods))
        assertEquals(existing.copy(preferredFoods = saved.preferredFoods), saved)
    }

    @Test
    fun settingsUpdatesTheSameSourceReadByOnboardingAndRetainsAllergyAndDislikes() = runTest {
        val repository = FakePreferenceRepository(MealCoachRepository.defaultPreference().copy(
            preferredFoods = "|STYLE:RICE|기존음식|"
        ))
        val allergiesAndDislikes = listOf(
            UserExcludedFood(1, "대두", "ALLERGY", 123),
            UserExcludedFood(2, "오이", "DISLIKE", 123)
        )
        repository.excludedFoods.value = allergiesAndDislikes
        val settings = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        assertEquals("기존음식", settings.uiState.value.preferredInput)
        assertEquals(setOf(FoodPreferenceStyle.RICE), settings.uiState.value.preferredStyles)

        settings.togglePreferredStyle("RICE")
        settings.togglePreferredStyle("NOODLE")
        settings.onPreferredInput("사과, 사과, 바나나")
        settings.save()
        advanceUntilIdle()

        val reopenedSetup = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        assertEquals(setOf(FoodPreferenceStyle.NOODLE), reopenedSetup.uiState.value.preferredStyles)
        assertEquals("사과, 바나나", reopenedSetup.uiState.value.preferredInput)
        assertEquals(allergiesAndDislikes, reopenedSetup.uiState.value.excludedFoods)
        assertEquals(1, repository.saveCount)
        assertNull(settings.uiState.value.error)
    }

    @Test
    fun repeatedSaveDuringPendingOnboardingWritesOnceAndConsumesSuccessOnce() = runTest {
        val repository = FakePreferenceRepository()
        val viewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        var completed = 0
        viewModel.saveTaste(setOf("RICE", "STYLE:RICE")) { completed++ }
        viewModel.saveTaste(setOf("RICE")) { completed++ }
        assertTrue(viewModel.uiState.value.isSaving)
        advanceUntilIdle()

        assertEquals(1, repository.saveCount)
        assertEquals(1, completed)
        assertEquals("|STYLE:RICE|", repository.preference.value.preferredFoods)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun failedOnboardingRetainsSelectionAndCanRetryWithoutCompletingEarly() = runTest {
        val repository = FakePreferenceRepository()
        val viewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        viewModel.togglePreferredStyle("KOREAN")
        repository.failSave = true
        var completed = 0

        viewModel.saveTaste { completed++ }
        advanceUntilIdle()
        assertEquals(0, completed)
        assertEquals("", repository.preference.value.preferredFoods)
        assertEquals(setOf(FoodPreferenceStyle.KOREAN), viewModel.uiState.value.preferredStyles)
        assertNotNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)

        repository.failSave = false
        viewModel.saveTaste { completed++ }
        advanceUntilIdle()
        assertEquals(1, completed)
        assertEquals("|STYLE:KOREAN|", repository.preference.value.preferredFoods)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun noStyleChoiceSavesEmptyPreferenceAndTogglingDoesNotCreateDuplicateSelections() = runTest {
        val repository = FakePreferenceRepository()
        val viewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        viewModel.togglePreferredStyle("RICE")
        viewModel.togglePreferredStyle("STYLE:RICE")
        assertTrue(viewModel.uiState.value.preferredStyles.isEmpty())
        viewModel.togglePreferredStyle("NOODLE")
        viewModel.clearPreferredStyles()
        viewModel.saveTaste { }
        advanceUntilIdle()
        assertEquals("", repository.preference.value.preferredFoods)
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun failedSettingsSaveRetainsBothStylesAndKeywordsForRetry() = runTest {
        val repository = FakePreferenceRepository()
        val viewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        viewModel.togglePreferredStyle("NOODLE")
        viewModel.onPreferredInput("우동, 라면")
        repository.failSave = true
        viewModel.save()
        advanceUntilIdle()
        assertEquals("", repository.preference.value.preferredFoods)
        assertEquals("우동, 라면", viewModel.uiState.value.preferredInput)
        assertEquals(setOf(FoodPreferenceStyle.NOODLE), viewModel.uiState.value.preferredStyles)
        assertNotNull(viewModel.uiState.value.error)

        repository.failSave = false
        viewModel.save()
        viewModel.save()
        advanceUntilIdle()
        assertEquals(1, repository.saveCount)
        assertEquals("|STYLE:NOODLE|우동|라면|", repository.preference.value.preferredFoods)
    }

    @Test
    fun skippedOnboardingDiscardsDraftInTheSameSettingsViewModelWithoutSaving() = runTest {
        val original = MealCoachRepository.defaultPreference().copy(
            preferredFoods = "|STYLE:RICE|참치|", cookingMode = "COOK"
        )
        val repository = FakePreferenceRepository(original)
        val sharedViewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        sharedViewModel.togglePreferredStyle("RICE")
        sharedViewModel.togglePreferredStyle("KOREAN")
        assertEquals(setOf(FoodPreferenceStyle.KOREAN), sharedViewModel.uiState.value.preferredStyles)

        sharedViewModel.discardTasteDraft()

        assertEquals(original, repository.preference.value)
        assertEquals(0, repository.saveCount)
        assertEquals(setOf(FoodPreferenceStyle.RICE), sharedViewModel.uiState.value.preferredStyles)
        assertEquals("참치", sharedViewModel.uiState.value.preferredInput)
        assertEquals(original, sharedViewModel.uiState.value.preference)
        repository.preference.value = original.copy(preferredFoods = "|STYLE:NOODLE|우동|")
        advanceUntilIdle()
        assertEquals(setOf(FoodPreferenceStyle.NOODLE), sharedViewModel.uiState.value.preferredStyles)
        assertEquals("우동", sharedViewModel.uiState.value.preferredInput)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun successfulOnboardingSaveReceivesLaterCanonicalStyleAndKeywordChanges() = runTest {
        val repository = FakePreferenceRepository(MealCoachRepository.defaultPreference().copy(
            preferredFoods = "|참치|"
        ))
        val viewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        viewModel.togglePreferredStyle("RICE")
        viewModel.onPreferredInput("저장하지 않은 설정 입력")
        viewModel.saveTaste { }
        advanceUntilIdle()
        assertEquals("참치", viewModel.uiState.value.preferredInput)

        repository.preference.value = repository.preference.value.copy(preferredFoods = "|STYLE:NOODLE|우동|")
        advanceUntilIdle()

        assertEquals(setOf(FoodPreferenceStyle.NOODLE), viewModel.uiState.value.preferredStyles)
        assertEquals("우동", viewModel.uiState.value.preferredInput)
        assertEquals(repository.preference.value, viewModel.uiState.value.preference)
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun successfulSettingsSaveReceivesLaterCanonicalStyleAndKeywordChanges() = runTest {
        val repository = FakePreferenceRepository()
        val viewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        viewModel.togglePreferredStyle("RICE")
        viewModel.onPreferredInput(" 참치, 참치 ")
        viewModel.save()
        advanceUntilIdle()
        assertEquals("참치", viewModel.uiState.value.preferredInput)

        repository.preference.value = repository.preference.value.copy(preferredFoods = "|STYLE:NOODLE|우동|")
        advanceUntilIdle()

        assertEquals(setOf(FoodPreferenceStyle.NOODLE), viewModel.uiState.value.preferredStyles)
        assertEquals("우동", viewModel.uiState.value.preferredInput)
        assertEquals(repository.preference.value, viewModel.uiState.value.preference)
        assertEquals(1, repository.saveCount)
    }

    @Test
    fun learningResetRequiresConfirmationAndCancellationChangesNothing() = runTest {
        val repository = FakePreferenceRepository()
        val viewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        viewModel.confirmLearningReset()
        advanceUntilIdle()
        assertEquals(0, repository.learningResetCount)
        viewModel.requestLearningReset()
        assertTrue(viewModel.uiState.value.showLearningResetConfirmation)
        viewModel.cancelLearningReset()
        viewModel.confirmLearningReset()
        advanceUntilIdle()
        assertEquals(0, repository.learningResetCount)
    }

    @Test
    fun confirmedLearningResetIsSingleAndPreservesExplicitPreferenceExclusionsAndDraft() = runTest {
        val existing = MealCoachRepository.defaultPreference().copy(preferredFoods = "|우동|", dietType = "VEGETARIAN")
        val repository = FakePreferenceRepository(existing)
        val exclusion = UserExcludedFood(1, "땅콩", "ALLERGY", 123)
        repository.excludedFoods.value = listOf(exclusion)
        val viewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        viewModel.onPreferredInput("새로운 미저장 취향")
        viewModel.requestLearningReset()
        viewModel.confirmLearningReset()
        viewModel.confirmLearningReset()
        advanceUntilIdle()
        assertEquals(1, repository.learningResetCount)
        assertEquals(0, repository.saveCount)
        assertEquals(existing, repository.preference.value)
        assertEquals(listOf(exclusion), repository.excludedFoods.value)
        assertEquals("새로운 미저장 취향", viewModel.uiState.value.preferredInput)
        assertFalse(viewModel.uiState.value.isResettingLearning)
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun failedLearningResetKeepsSettingsAndAllowsConfirmedRetry() = runTest {
        val repository = FakePreferenceRepository()
        val viewModel = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        repository.failLearningReset = true
        viewModel.requestLearningReset()
        viewModel.confirmLearningReset()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isResettingLearning)
        assertNotNull(viewModel.uiState.value.error)
        repository.failLearningReset = false
        viewModel.requestLearningReset()
        viewModel.confirmLearningReset()
        advanceUntilIdle()
        assertEquals(1, repository.learningResetCount)
        assertNull(viewModel.uiState.value.error)
    }

    @Test fun settingsFailureKeepsInputAndRetryAcknowledgesOneWrite() = runTest {
        val repository = FakePreferenceRepository()
        val vm = MealPreferenceViewModel(repository)
        advanceUntilIdle()
        vm.onPreferredInput("새로운 입력")
        repository.failSave = true
        vm.save(); advanceUntilIdle()
        assertNull(vm.saveAcknowledgement.pending.value)
        assertEquals("새로운 입력", vm.uiState.value.preferredInput)
        assertNotNull(vm.uiState.value.error)
        repository.failSave = false
        vm.save(); vm.save(); advanceUntilIdle()
        assertEquals(1, repository.saveCount)
        assertNotNull(vm.saveAcknowledgement.pending.value)
        vm.save(); advanceUntilIdle(); assertEquals(1, repository.saveCount)
        assertNotNull(vm.saveAcknowledgement.confirm())
        assertNull(vm.saveAcknowledgement.confirm())
    }

    private class FakePreferenceRepository(
        initial: UserMealPreference = MealCoachRepository.defaultPreference()
    ) : MealPreferenceRepository {
        override val preference = MutableStateFlow(initial)
        override val excludedFoods = MutableStateFlow(emptyList<UserExcludedFood>())
        var saveCount = 0
        var failSave = false
        var learningResetCount = 0
        var failLearningReset = false
        override suspend fun resetRecommendationLearning() {
            if (failLearningReset) error("forced reset failure")
            learningResetCount++
        }
        override suspend fun ensureDefaultPreference(): UserMealPreference = preference.value
        override suspend fun savePreference(preference: UserMealPreference) {
            if (failSave) error("forced save failure")
            saveCount++
            this.preference.value = preference
        }
        override suspend fun addExcludedFood(name: String, type: String): Boolean {
            if (excludedFoods.value.any { it.normalizedFoodName == name && it.exclusionType == type }) return false
            excludedFoods.value += UserExcludedFood(excludedFoods.value.size + 1L, name, type, 123)
            return true
        }
        override suspend fun deleteExcludedFood(food: UserExcludedFood) {
            excludedFoods.value -= food
        }
    }
}
