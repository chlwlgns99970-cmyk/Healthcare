package com.example.healthcare

import android.app.Application
import com.example.healthcare.data.AppFontSizeStore
import com.example.healthcare.data.SharedPreferencesRecommendationCycleStore
import com.example.healthcare.data.BodyProfileStore
import com.example.healthcare.data.WeightGoalStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.photo.FoodPhotoProcessor
import com.example.healthcare.data.recognition.KoreanNutritionLabelRecognizer
import com.example.healthcare.data.photo.RetrofitFoodPhotoAnalysisRepository
import com.example.healthcare.data.photo.UnconfiguredFoodPhotoAnalysisRepository
import com.example.healthcare.data.photo.api.FoodPhotoAnalysisServiceFactory
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.GoalRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.data.repository.RecognitionRepository
import com.example.healthcare.data.repository.StepCounterRepository
import com.example.healthcare.data.seed.BundledFoodDataSeeder
import com.example.healthcare.data.update.FoodDataUpdateCoordinator
import com.example.healthcare.data.update.FoodDataUpdateScheduler
import com.example.healthcare.data.update.FoodDataUpdateStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 애플리케이션 범위에서 데이터베이스와 저장소 인스턴스를 제공합니다.
 */
class HealthcareApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { AppDatabase.getDatabase(this) }

    val mealRepository by lazy { MealRepository(database.mealRecordDao()) }
    val goalRepository by lazy { GoalRepository(database.calorieGoalDao()) }
    val energyProfileRepository by lazy { EnergyProfileRepository(database.energyProfileDao()) }
    val bodyProfileStore by lazy { BodyProfileStore(this) }
    val weightGoalStore by lazy { WeightGoalStore(this) }
    val appFontSizeStore by lazy { AppFontSizeStore(this) }
    val stepCounterRepository by lazy { StepCounterRepository(this) }
    val foodRepository by lazy { FoodRepository(database.frequentFoodDao()) }
    val nutritionRepository by lazy { NutritionRepository(database.foodItemDao()) }
    val mealCoachRepository by lazy {
        MealCoachRepository(
            database = database,
            coachDao = database.mealCoachDao(),
            foodItemDao = database.foodItemDao(),
            mealRecordDao = database.mealRecordDao(),
            recommendationCycleStore = SharedPreferencesRecommendationCycleStore(this)
        )
    }
    val recognitionRepository by lazy { RecognitionRepository(database.recognitionDao()) }
    val foodDataUpdateCoordinator by lazy {
        FoodDataUpdateCoordinator(database, FoodDataUpdateStateStore(this))
    }
    val nutritionLabelRecognizer by lazy { KoreanNutritionLabelRecognizer(this) }
    val foodPhotoProcessor by lazy { FoodPhotoProcessor(cacheDir) }

    private val foodAnalysisHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build()
    }

    val foodPhotoAnalysisRepository by lazy {
        val baseUrl = BuildConfig.FOOD_ANALYSIS_BASE_URL.trim()
        if (baseUrl.isBlank()) {
            UnconfiguredFoodPhotoAnalysisRepository()
        } else {
            RetrofitFoodPhotoAnalysisRepository(
                api = FoodPhotoAnalysisServiceFactory.create(baseUrl, foodAnalysisHttpClient),
                processor = foodPhotoProcessor
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            BundledFoodDataSeeder(
                context = this@HealthcareApplication,
                database = database
            ).seedIfAvailable()
            FoodDataUpdateScheduler.schedule(
                this@HealthcareApplication,
                foodDataUpdateCoordinator.state.value.lastCheckedAt
            )
        }
    }
}
