package com.example.healthcare

import android.app.Application
import com.example.healthcare.data.AppFontSizeStore
import com.example.healthcare.data.appupdate.AndroidApkIdentityReader
import com.example.healthcare.data.appupdate.ApkUpdateVerifier
import com.example.healthcare.data.appupdate.AppUpdateManager
import com.example.healthcare.data.appupdate.AppUpdatePreferences
import com.example.healthcare.data.appupdate.HttpAppUpdateRepository
import com.example.healthcare.data.appupdate.PrivateUpdateApkDownloader
import com.example.healthcare.data.appupdate.SystemAppInstaller
import com.example.healthcare.data.SharedPreferencesRecommendationCycleStore
import com.example.healthcare.data.TodayMealPlanStore
import com.example.healthcare.data.repository.TodayMealPlanRepository
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
    val todayMealPlanRepository by lazy {
        TodayMealPlanRepository(TodayMealPlanStore(this), mealCoachRepository, goalRepository,
            energyProfileRepository, mealRepository, foodRepository)
    }
    val foodDataUpdateCoordinator by lazy {
        FoodDataUpdateCoordinator(database, FoodDataUpdateStateStore(this))
    }
    val nutritionLabelRecognizer by lazy { KoreanNutritionLabelRecognizer(this) }
    val foodPhotoProcessor by lazy { FoodPhotoProcessor(cacheDir) }

    private val appUpdateMetadataClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val appUpdateDownloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }
    val appUpdateManager by lazy {
        val identityReader = AndroidApkIdentityReader(this)
        AppUpdateManager(
            repository = HttpAppUpdateRepository(appUpdateMetadataClient, BuildConfig.APP_UPDATE_URL),
            preferences = AppUpdatePreferences(this),
            downloader = PrivateUpdateApkDownloader(this, appUpdateDownloadClient),
            verifier = ApkUpdateVerifier(
                identityReader = identityReader,
                expectedPackageName = PRODUCT_APPLICATION_ID,
                knownReleaseSignerSha256 = RELEASE_SIGNER_SHA256
            ),
            installer = SystemAppInstaller("${BuildConfig.APPLICATION_ID}.update-file-provider"),
            scope = applicationScope,
            currentVersionCode = BuildConfig.VERSION_CODE,
            currentPackageName = BuildConfig.APPLICATION_ID,
            expectedPackageName = PRODUCT_APPLICATION_ID,
            installEnabled = BuildConfig.APP_UPDATE_INSTALL_ENABLED,
            showTechnicalFailureReason = BuildConfig.APP_UPDATE_SHOW_DEBUG_FAILURE
        )
    }

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

    private companion object {
        const val PRODUCT_APPLICATION_ID = "com.example.healthcare"
        const val RELEASE_SIGNER_SHA256 =
            "385693830FF4C9F9122A9DC5D992646A871AC82764439AEC1C1078BF55496CA8"
    }
}
