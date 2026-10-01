package com.example.healthcare.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.healthcare.data.BodyProfilePersistence
import com.example.healthcare.data.WeightGoalPersistence
import com.example.healthcare.data.photo.FoodPhotoAnalysisRepository
import com.example.healthcare.data.photo.FoodPhotoProcessor
import com.example.healthcare.data.recognition.KoreanNutritionLabelRecognizer
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.GoalRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.data.repository.TodayMealPlanRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.data.repository.RecognitionRepository
import com.example.healthcare.data.update.FoodDataUpdateCoordinator

/**
 * ViewModel 생성을 위한 Factory 클래스
 */
class ViewModelFactory(
    private val mealRepository: MealRepository,
    private val goalRepository: GoalRepository,
    private val energyProfileRepository: EnergyProfileRepository,
    private val foodRepository: FoodRepository,
    private val foodPhotoAnalysisRepository: FoodPhotoAnalysisRepository,
    private val foodPhotoProcessor: FoodPhotoProcessor,
    private val nutritionRepository: NutritionRepository,
    private val mealCoachRepository: MealCoachRepository,
    private val nutritionLabelRecognizer: KoreanNutritionLabelRecognizer,
    private val recognitionRepository: RecognitionRepository,
    private val bodyProfileStore: BodyProfilePersistence,
    private val weightGoalStore: WeightGoalPersistence,
    private val foodDataUpdateCoordinator: FoodDataUpdateCoordinator? = null,
    private val todayMealPlanRepository: TodayMealPlanRepository? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(mealRepository, goalRepository, energyProfileRepository, mealCoachRepository,
                    todayMealPlanRepository = todayMealPlanRepository) as T
            }
            modelClass.isAssignableFrom(AddRecordViewModel::class.java) -> {
                AddRecordViewModel(
                    mealRepository,
                    foodRepository,
                    foodPhotoAnalysisRepository,
                    foodPhotoProcessor,
                    nutritionRepository,
                    nutritionLabelRecognizer,
                    recognitionRepository
                ) as T
            }
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(mealRepository, goalRepository, energyProfileRepository,
                    nutritionRepository = nutritionRepository) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    goalRepository,
                    energyProfileRepository,
                    bodyProfileStore,
                    weightGoalStore,
                    foodDataUpdateCoordinator = foodDataUpdateCoordinator
                ) as T
            }
            modelClass.isAssignableFrom(MealPlanViewModel::class.java) -> {
                MealPlanViewModel(mealCoachRepository, nutritionRepository) as T
            }
            modelClass.isAssignableFrom(TodayMealPlanViewModel::class.java) -> {
                TodayMealPlanViewModel(requireNotNull(todayMealPlanRepository)) as T
            }
            modelClass.isAssignableFrom(MealPreferenceViewModel::class.java) -> {
                MealPreferenceViewModel(mealCoachRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
