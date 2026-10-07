package com.example.healthcare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.InMemoryRecommendationCycleStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.entity.MealTemplateIngredient
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.data.repository.MealRecommendationSearchResult
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.domain.FoodPreferencePolicy
import com.example.healthcare.domain.FoodPreferenceStyle
import com.example.healthcare.domain.MealRecommendationTheme
import com.example.healthcare.ui.screens.DashboardContent
import com.example.healthcare.ui.screens.MealPlanContent
import com.example.healthcare.ui.screens.MealPlanScreen
import com.example.healthcare.ui.screens.MealPreferenceContent
import com.example.healthcare.ui.screens.MealTasteSetupScreen
import com.example.healthcare.ui.screens.TasteSetupScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.MealPlanUiState
import com.example.healthcare.ui.viewmodel.MealPlanViewModel
import com.example.healthcare.ui.viewmodel.DashboardEnergyUiState
import com.example.healthcare.ui.viewmodel.MealPreferenceViewModel
import com.example.healthcare.ui.viewmodel.RecommendationPreviewUi
import com.example.healthcare.ui.viewmodel.TodayCoachUiState
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses an isolated Room database: no existing QA or production records/settings are changed. */
@RunWith(AndroidJUnit4::class)
class RecommendationPersonalizationUiTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: MealCoachRepository
    private val viewModels = ViewModelStore()
    private lateinit var focusManager: FocusManager
    private var nativeDensity = 1f
    private var originalMetadata: Collection<com.example.healthcare.domain.FoodMetadata> = emptyList()

    @Before fun isolatedQaFixture() {
        originalMetadata = com.example.healthcare.domain.FoodMetadataPolicy.snapshot()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa", context.packageName)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = MealCoachRepository(
            database, database.mealCoachDao(), database.foodItemDao(), database.mealRecordDao(),
            InMemoryRecommendationCycleStore()
        )
    }

    @After fun closeFixture() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModels.clear() }
        database.close()
        com.example.healthcare.domain.FoodMetadataPolicy.install(originalMetadata)
    }

    @Test fun firstTasteSetupAllowsSkipAndAccessibleChipsAt360DpAndLargeText() {
        var selected by mutableStateOf(emptySet<String>())
        var skips = 0
        var saves = 0
        setCompactContent {
            TasteSetupScreen(
                selectedStyles = selected,
                isSaving = false,
                onToggle = { key -> selected = if (key in selected) selected - key else selected + key },
                onSkip = { skips++ },
                onSave = { saves++ }
            )
        }

        val chipBounds = (FoodPreferenceStyle.entries.map { it.name } + "NONE").map { key ->
            val node = composeRule.onNodeWithTag("preference-style-$key")
                .assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$key 터치 영역 높이가 48dp보다 작습니다.", node.boundsInRoot.height >= 48f * nativeDensity - 0.5f)
            assertInsideViewport(node.boundsInRoot)
            node.boundsInRoot
        }
        assertNonOverlapping(chipBounds)
        composeRule.onNodeWithTag("preference-style-KOREAN").performClick().assertIsSelected()
        scrollTo(hasTestTag("preference-skip"))
        composeRule.onNodeWithTag("preference-skip").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(setOf("KOREAN"), selected)
            assertEquals(1, skips)
            assertEquals(0, saves)
        }
        assertEquals(null, runBlocking { database.mealCoachDao().getPreference() })
    }

    @Test fun onboardingSelectionIsSavedToTheSamePreferenceSourceUsedBySettings() {
        val initial = MealCoachRepository.defaultPreference().copy(
            cookingMode = "COOK", preferredFoods = "|참치|"
        )
        runBlocking { repository.savePreference(initial) }
        val viewModel = preferenceViewModel("onboarding")
        var completed = 0
        setCompactContent { MealTasteSetupScreen(viewModel, onComplete = { completed++ }) }

        composeRule.onNodeWithTag("preference-style-KOREAN").performClick().assertIsSelected()
        composeRule.onNodeWithTag("preference-style-RICE").performClick().assertIsSelected()
        scrollTo(hasTestTag("preference-save"))
        composeRule.onNodeWithTag("preference-save").assertIsDisplayed().performClick()
        composeRule.waitUntil(10_000) { completed == 1 }

        val stored = runBlocking { database.mealCoachDao().getPreference()!! }
        assertEquals(
            setOf(FoodPreferenceStyle.KOREAN, FoodPreferenceStyle.RICE),
            FoodPreferencePolicy.stylesFromStored(stored.preferredFoods)
        )
        assertEquals(2, stored.preferredFoods.split('|').count { it.startsWith("STYLE:") })
        assertEquals(listOf("참치"), FoodPreferencePolicy.keywordsFromStored(stored.preferredFoods))
        assertEquals("COOK", stored.cookingMode)
        assertEquals(initial.breakfastRatio, stored.breakfastRatio)
        assertEquals(initial.lunchRatio, stored.lunchRatio)
        val settings = preferenceViewModel("settings-reader")
        composeRule.waitUntil(10_000) { settings.uiState.value.preference.preferredFoods == stored.preferredFoods }
        assertEquals(viewModel.uiState.value.preferredStyles, settings.uiState.value.preferredStyles)
    }

    @Test fun firstTasteSkipLeavesExistingCanonicalPreferencesUnchanged() {
        val original = MealCoachRepository.defaultPreference().copy(
            preferredFoods = FoodPreferencePolicy.serialize(setOf(FoodPreferenceStyle.RICE), listOf("참치"))
        )
        runBlocking { repository.savePreference(original) }
        val before = runBlocking { database.mealCoachDao().getPreference() }
        val viewModel = preferenceViewModel("onboarding-skip")
        var completed = 0
        setCompactContent { MealTasteSetupScreen(viewModel, onComplete = { completed++ }) }
        composeRule.waitUntil(10_000) { FoodPreferenceStyle.RICE in viewModel.uiState.value.preferredStyles }
        composeRule.onNodeWithTag("preference-style-KOREAN").performClick()
        scrollTo(hasTestTag("preference-skip"))
        composeRule.onNodeWithTag("preference-skip").performClick()
        composeRule.runOnIdle {
            assertEquals(1, completed)
            assertEquals(FoodPreferencePolicy.stylesFromStored(before!!.preferredFoods),
                viewModel.uiState.value.preferredStyles)
            assertEquals("참치", viewModel.uiState.value.preferredInput)
        }
        assertEquals(before, runBlocking { database.mealCoachDao().getPreference() })
    }

    @Test fun failedTasteSaveKeepsSelectionAndAllowsRetry() {
        runBlocking { repository.ensureDefaultPreference() }
        var failNextSave = true
        repository = object : MealCoachRepository(
            database, database.mealCoachDao(), database.foodItemDao(), database.mealRecordDao(),
            InMemoryRecommendationCycleStore()
        ) {
            override suspend fun savePreference(preference: com.example.healthcare.data.entity.UserMealPreference) {
                if (failNextSave) throw IllegalStateException("QA fixture save failure")
                super.savePreference(preference)
            }
        }
        val viewModel = preferenceViewModel("onboarding-save-failure")
        var completed = 0
        setCompactContent { MealTasteSetupScreen(viewModel, onComplete = { completed++ }) }
        composeRule.onNodeWithTag("preference-style-NOODLE").performClick()
        scrollTo(hasTestTag("preference-save"))
        composeRule.onNodeWithTag("preference-save").performClick()
        composeRule.waitUntil(10_000) { viewModel.uiState.value.error != null && !viewModel.uiState.value.isSaving }
        scrollTo(hasTestTag("preference-style-NOODLE"))
        composeRule.onNodeWithTag("preference-style-NOODLE").assertIsSelected().assertIsDisplayed()
        scrollTo(hasText("취향을 저장하지 못했어요. 다시 시도해 주세요."))
        composeRule.onNodeWithText("취향을 저장하지 못했어요. 다시 시도해 주세요.").assertIsDisplayed()
        assertEquals(0, completed)
        assertEquals("", runBlocking { database.mealCoachDao().getPreference()!!.preferredFoods })
        composeRule.runOnIdle { failNextSave = false }
        scrollTo(hasTestTag("preference-save"))
        composeRule.onNodeWithTag("preference-save").performClick()
        composeRule.waitUntil(10_000) { completed == 1 }
        assertEquals(setOf(FoodPreferenceStyle.NOODLE), FoodPreferencePolicy.stylesFromStored(
            runBlocking { database.mealCoachDao().getPreference()!!.preferredFoods }
        ))
    }

    @Test fun settingsChangesStylesAndDislikesWithoutDuplicatePreferences() {
        runBlocking {
            repository.savePreference(MealCoachRepository.defaultPreference().copy(
                preferredFoods = FoodPreferencePolicy.serialize(setOf(FoodPreferenceStyle.KOREAN), listOf("닭"))
            ))
        }
        val viewModel = preferenceViewModel("settings-editor")
        setCompactContent {
            val state by viewModel.uiState.collectAsState()
            MealPreferenceContent(
                state = state,
                onBack = {},
                onMealEnabled = viewModel::setMealEnabled,
                onRatioChange = viewModel::setRatio,
                onDietType = viewModel::setDietType,
                onCookingMode = viewModel::setCookingMode,
                onBudget = viewModel::setBudgetLevel,
                onDiversity = viewModel::setDiversity,
                onAllergyInput = viewModel::onAllergyInput,
                onAddAllergy = viewModel::addAllergy,
                onDislikeInput = viewModel::onDislikeInput,
                onAddDislike = viewModel::addDislike,
                onPreferredInput = viewModel::onPreferredInput,
                onRemoveExcluded = viewModel::removeExcluded,
                onSave = viewModel::save,
                onTogglePreferredStyle = viewModel::togglePreferredStyle
            )
        }
        composeRule.waitUntil(10_000) { FoodPreferenceStyle.KOREAN in viewModel.uiState.value.preferredStyles }
        scrollTo(hasTestTag("preference-style-KOREAN"))
        composeRule.onNodeWithTag("preference-style-KOREAN").assertIsSelected().performClick()
        composeRule.onNodeWithTag("preference-style-RICE").performClick().assertIsSelected()

        val preferredInput = hasSetTextAction() and hasText("선호 음식", substring = true)
        scrollTo(preferredInput)
        composeRule.onNode(preferredInput).performTextReplacement("참치, 참치,  참치 ")
        dismissKeyboard()
        scrollTo(hasTestTag("preference-settings-save"))
        composeRule.onNodeWithTag("preference-settings-save").performClick()
        composeRule.waitUntil(10_000) { viewModel.uiState.value.message != null && !viewModel.uiState.value.isSaving }
        val stored = runBlocking { database.mealCoachDao().getPreference()!! }
        assertEquals(setOf(FoodPreferenceStyle.RICE), FoodPreferencePolicy.stylesFromStored(stored.preferredFoods))
        assertEquals(setOf("참치"), FoodPreferencePolicy.keywordsFromStored(stored.preferredFoods).toSet())
        assertEquals(1, stored.preferredFoods.split('|').count { it == "참치" })

        val dislikeInput = hasSetTextAction() and hasText("음식명 또는 재료", substring = true)
        scrollTo(dislikeInput)
        composeRule.onNode(dislikeInput).performTextReplacement("오이")
        composeRule.onNode(dislikeInput).performImeAction()
        dismissKeyboard()
        composeRule.waitUntil(10_000) { viewModel.uiState.value.excludedFoods.any { it.normalizedFoodName == "오이" } }
        val dislike = runBlocking { database.mealCoachDao().getExcludedFoods() }.single()
        assertEquals("오이", dislike.normalizedFoodName)
        assertEquals("DISLIKE", dislike.exclusionType)
        scrollTo(hasText("오이"))
        composeRule.onNodeWithText("오이").assertIsDisplayed()
    }

    @Test fun homeStartsWithThemeInvitationWithoutFoodAt360DpAndLargeText() {
        var opened = false
        setCompactContent {
            DashboardContent(selectedDate = LocalDate.now(), meals = emptyList(), totalCalories = 0,
                targetCalories = 1850, statusText = "목표까지 1,850 kcal", energyState = DashboardEnergyUiState(),
                coachState = TodayCoachUiState(), onPreviousDay = {}, onNextDay = {}, onAddRecord = {},
                onOpenEnergySettings = {}, onOpenRecommendations = { opened = true })
        }
        assertFalse(composeRule.onNodeWithTag("dashboard-root").fetchSemanticsNode().config.contains(SemanticsActions.ScrollBy))
        composeRule.onNodeWithText("오늘 식사 스타일을 골라보세요").assertIsDisplayed()
        composeRule.onNodeWithText("추천 고르기").assertIsDisplayed()
        assertInsideViewport(composeRule.onNodeWithTag("dashboard-recommendation-card").fetchSemanticsNode().boundsInRoot)
        composeRule.onNodeWithTag("dashboard-open-daily-plan").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test fun homeDaySummaryFitsAt360DpWithSystemAndAppFontScale130() {
        var opened = false
        setCompactContent(systemFontScale = 1.30f) {
            assertEquals(1.69f, LocalDensity.current.fontScale, 0.001f)
            DashboardContent(selectedDate = LocalDate.now(), meals = emptyList(), totalCalories = 0,
                targetCalories = 1850, statusText = "목표까지 1,850 kcal", energyState = DashboardEnergyUiState(),
                coachState = TodayCoachUiState(dailyThemeLabel = "다이어트", dailyPlanKcal = 1830, dailyPlanTarget = 1850),
                onPreviousDay = {}, onNextDay = {}, onAddRecord = {}, onOpenEnergySettings = {},
                onOpenRecommendations = { opened = true })
        }
        assertFalse(composeRule.onNodeWithTag("dashboard-root").fetchSemanticsNode().config.contains(SemanticsActions.ScrollBy))
        listOf("다이어트 식단", "1,830 / 1,850 kcal", "오늘 식단 보기").forEach { text ->
            composeRule.onNodeWithText(text).assertIsDisplayed()
            assertTextLineHeightFits(text)
        }
        assertInsideViewport(composeRule.onNodeWithTag("dashboard-recommendation-card").fetchSemanticsNode().boundsInRoot)
        composeRule.onNodeWithTag("dashboard-open-daily-plan").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test fun tasteSetupSupportsSelectionAndSkipWithSystemAndAppFontScale130() {
        var selected by mutableStateOf(emptySet<String>())
        var skipped = 0
        var saved = 0
        setCompactContent(systemFontScale = 1.30f) {
            assertEquals(1.69f, LocalDensity.current.fontScale, 0.001f)
            TasteSetupScreen(
                selectedStyles = selected, isSaving = false,
                onToggle = { key -> selected = if (key in selected) selected - key else selected + key },
                onSkip = { skipped++ }, onSave = { saved++ }
            )
        }
        scrollTo(hasTestTag("preference-style-KOREAN"))
        composeRule.onNodeWithTag("preference-style-KOREAN").assertIsDisplayed().performClick().assertIsSelected()
        val chipBounds = (FoodPreferenceStyle.entries.map { it.name } + "NONE").map { key ->
            val bounds = composeRule.onNodeWithTag("preference-style-$key")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$key 터치 영역 높이가 48dp보다 작습니다.", bounds.height >= 48f * nativeDensity - 0.5f)
            assertInsideViewport(bounds)
            bounds
        }
        assertNonOverlapping(chipBounds)
        FoodPreferenceStyle.entries.forEach { assertTextDoesNotClip(it.label) }
        assertTextDoesNotClip("특별히 없음")
        scrollTo(hasTestTag("preference-skip"))
        composeRule.onNodeWithTag("preference-skip").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(setOf("KOREAN"), selected)
            assertEquals(1, skipped)
            assertEquals(0, saved)
        }
        assertEquals(null, runBlocking { database.mealCoachDao().getPreference() })
    }

    @Test fun themeControlsAndEmptyStateRemainUsableAt360DpAndLargeText() {
        var selected: MealRecommendationTheme? = null
        var settingsOpened = 0
        val state = MealPlanUiState(
            mealType = MealType.LUNCH, budgetKcal = 550, hasLoaded = true,
            templateCount = 292, verifiedCandidateCount = 0,
            selectedTheme = MealRecommendationTheme.LIGHT,
            themeCounts = MealRecommendationTheme.entries.associateWith { if (it == MealRecommendationTheme.LIGHT) 0 else 3 }
        )
        setCompactContent {
            MealPlanContent(
                state, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {},
                onOpenPreferences = { settingsOpened++ },
                onSelectTheme = { selected = it },
                onShowTodayRecommendations = {},
                showThemes = true
            )
        }
        MealRecommendationTheme.entries.forEach { theme ->
            scrollTo(hasTestTag("recommendation-theme-${theme.name}"))
            composeRule.onNodeWithTag("recommendation-theme-${theme.name}").assertIsDisplayed()
                .performClick()
            composeRule.runOnIdle { assertEquals(theme, selected) }
        }
        scrollTo(hasText("현재 조건에 맞는 메뉴가 없어요."))
        composeRule.onNodeWithText("현재 조건에 맞는 메뉴가 없어요.").assertIsDisplayed()
        scrollTo(hasTestTag("recommendation-empty-preferences"))
        composeRule.onNodeWithTag("recommendation-empty-preferences").performClick()
        composeRule.runOnIdle { assertEquals(1, settingsOpened) }
    }

    @Test fun themeExplorationUsesFilteredMenusAndOpensPortionDetail() {
        runBlocking {
            seedMenus()
            repository.addExcludedFood("오이", "DISLIKE")
        }
        lateinit var viewModel: MealPlanViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = MealPlanViewModel(repository, NutritionRepository(database.foodItemDao()))
            viewModels.put("theme-exploration", viewModel)
        }
        setCompactContent {
            MealPlanScreen(
                MealType.LUNCH, 550, 2_000, viewModel, onBack = {}, onSaved = {}, showThemes = true
            )
        }
        composeRule.waitUntil(10_000) { viewModel.uiState.value.hasLoaded && !viewModel.uiState.value.isLoading }
        scrollTo(hasTestTag("recommendation-theme-LIGHT"))
        composeRule.onNodeWithTag("recommendation-theme-LIGHT").performClick()
        composeRule.waitUntil(10_000) {
            viewModel.uiState.value.selectedTheme == MealRecommendationTheme.LIGHT && !viewModel.uiState.value.isLoading
        }
        val page = viewModel.uiState.value.recommendations
        assertTrue(page.isNotEmpty())
        assertTrue(page.size <= 3)
        assertFalse(page.any { "오이" in it.ingredientNames.joinToString() })
        val first = page.first().recommendation.template
        scrollTo(hasText(first.name))
        composeRule.onNodeWithText(first.name).assertIsDisplayed()
        scrollTo(androidx.compose.ui.test.hasContentDescription("${first.name} 추천 이미지"))
        composeRule.onNodeWithContentDescription("${first.name} 추천 이미지").performClick()
        composeRule.waitUntil(10_000) { viewModel.uiState.value.selectedMeal?.templateId == first.id }
        scrollTo(hasText("먹은 내용 확인"))
        composeRule.onNodeWithText("먹은 내용 확인").assertIsDisplayed()
        scrollTo(hasText("오늘의 추천으로 돌아가기"))
        composeRule.onNodeWithText("오늘의 추천으로 돌아가기").performClick()
        composeRule.waitUntil(10_000) { viewModel.uiState.value.selectedTheme == null && !viewModel.uiState.value.isLoading }
        assertEquals(null, viewModel.uiState.value.selectedMeal)
    }

    @Test fun themeRecommendationsShareDislikeFilterAndRetainStableDistinctIds() = runBlocking {
        seedMenus()
        val today = LocalDate.of(2099, 10, 1)
        val before = MealRecommendationTheme.entries.associateWith { theme ->
            repository.getOrCreateThemeRecommendations(today, MealType.LUNCH, 550, theme, limit = 3)
                .recommendations.map { it.recommendation.template.id }
        }
        assertTrue(before.values.all { it.isNotEmpty() })
        assertTrue(repository.addExcludedFood("오이", "DISLIKE"))
        assertTrue(repository.addExcludedFood("대두", "ALLERGY"))
        for (theme in MealRecommendationTheme.entries) {
            val page = repository.getOrCreateThemeRecommendations(today, MealType.LUNCH, 550, theme, limit = 3)
            val ids = page.recommendations.map { it.recommendation.template.id }
            assertTrue(ids.size <= 3)
            assertEquals(ids.distinct(), ids)
            assertTrue(page.recommendations.all { "오이" !in it.ingredientNames.joinToString() })
            assertEquals(ids, repository.getOrCreateThemeRecommendations(today, MealType.LUNCH, 550, theme, 3)
                .recommendations.map { it.recommendation.template.id })
        }
        assertEquals(6, database.mealCoachDao().templateCount())
        assertEquals(6, database.foodItemDao().count())
        val remaining = repository.getOrCreateTodayRecommendations(today, MealType.LUNCH, 550, 3)
        assertTrue(remaining.recommendations.all { "오이" !in it.ingredientNames.joinToString() })
        assertTrue(remaining.recommendations.any { "대두" in it.matchedAllergens })
    }

    @Test fun homeRecommendationOpenedDuringThemeLoadUsesTodayMenuAndPortionDetail() {
        val homePage = runBlocking {
            seedMenus()
            repository.getOrCreateTodayRecommendations(LocalDate.now(), MealType.LUNCH, 550, 3)
        }
        val homeChoice = homePage.recommendations.first { it.recommendation.template.totalKcal > 550 }
        val homeTemplate = homeChoice.recommendation.template
        val themeLoadStarted = CompletableDeferred<Unit>()
        val releaseThemeLoad = CompletableDeferred<Unit>()
        var loadedTheme: MealRecommendationSearchResult? = null
        val delayedRepository = object : MealCoachRepository(
            database, database.mealCoachDao(), database.foodItemDao(), database.mealRecordDao(),
            InMemoryRecommendationCycleStore()
        ) {
            override suspend fun getOrCreateTodayRecommendations(
                localDate: LocalDate, mealType: MealType, budgetKcal: Int, limit: Int
            ): MealRecommendationSearchResult = repository.getOrCreateTodayRecommendations(
                localDate, mealType, budgetKcal, limit
            )

            override suspend fun getOrCreateThemeRecommendations(
                localDate: LocalDate, mealType: MealType, budgetKcal: Int,
                theme: MealRecommendationTheme, limit: Int
            ): MealRecommendationSearchResult {
                themeLoadStarted.complete(Unit)
                releaseThemeLoad.await()
                return repository.getOrCreateThemeRecommendations(localDate, mealType, budgetKcal, theme, limit)
                    .also { loadedTheme = it }
            }
        }
        lateinit var viewModel: MealPlanViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = MealPlanViewModel(delayedRepository, NutritionRepository(database.foodItemDao()))
            viewModels.put("theme-to-home", viewModel)
        }
        var homeTemplateId by mutableStateOf<String?>(null)
        setCompactContent {
            MealPlanScreen(
                MealType.LUNCH, 550, 2_000, viewModel, onBack = {}, onSaved = {},
                initialTemplateId = homeTemplateId, showThemes = homeTemplateId == null
            )
        }
        composeRule.waitUntil(10_000) { viewModel.uiState.value.hasLoaded && !viewModel.uiState.value.isLoading }
        scrollTo(hasTestTag("recommendation-theme-LIGHT"))
        composeRule.onNodeWithTag("recommendation-theme-LIGHT").performClick()
        composeRule.waitUntil(10_000) { themeLoadStarted.isCompleted && viewModel.uiState.value.isLoading }

        // Exercise both the latest Home request and a filter change during the suspended theme load.
        runBlocking { assertTrue(repository.addExcludedFood("오이", "DISLIKE")) }
        composeRule.runOnIdle { homeTemplateId = homeTemplate.id }
        composeRule.waitForIdle()
        releaseThemeLoad.complete(Unit)
        composeRule.waitUntil(10_000) {
            !viewModel.uiState.value.isLoading && viewModel.uiState.value.selectedTheme == null &&
                viewModel.uiState.value.selectedMeal?.templateId == homeTemplate.id
        }
        val state = viewModel.uiState.value
        val selected = requireNotNull(state.selectedMeal)
        assertFalse(requireNotNull(loadedTheme).recommendations.any { it.recommendation.template.id == homeTemplate.id })
        assertFalse(state.recommendations.any { "오이" in it.ingredientNames.joinToString() })
        assertEquals(homeTemplate.name, selected.templateName)
        assertEquals(state.recommendations.single { it.recommendation.template.id == homeTemplate.id }
            .recommendation.reason, selected.reason)
        assertFalse(selected.reason.contains("낮은 쪽"))
        assertEquals(1, selected.ingredients.size)
        assertEquals(homeChoice.ingredients.single().foodItemId, selected.ingredients.single().foodItem.id)
        assertEquals(100.0, selected.ingredients.single().amount, 0.0)
        assertEquals("g", selected.ingredients.single().unit)
        assertEquals(homeTemplate.totalKcal, selected.totalCalories)

        scrollTo(hasText("먹은 내용 확인"))
        composeRule.onNodeWithText("먹은 내용 확인").assertIsDisplayed()
        scrollTo(hasTestTag("recommendation-selected-reason"))
        composeRule.onNodeWithTag("recommendation-selected-reason").assertIsDisplayed().assertTextEquals(selected.reason)
        scrollTo(hasText("추천한 양 · 약 ${selected.totalCalories} kcal"))
        composeRule.onNodeWithText("추천한 양 · 약 ${selected.totalCalories} kcal").assertIsDisplayed()
    }

    private fun preferenceViewModel(key: String): MealPreferenceViewModel {
        lateinit var result: MealPreferenceViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = MealPreferenceViewModel(repository)
            viewModels.put(key, result)
        }
        return result
    }

    private fun setCompactContent(systemFontScale: Float = 1f, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current.density
            nativeDensity = density
            focusManager = LocalFocusManager.current
            CompositionLocalProvider(LocalDensity provides Density(density, systemFontScale)) {
                HealthCareTheme(darkTheme = false, appFontScale = 1.30f) {
                    Box(Modifier.width(360.dp).height(800.dp).testTag("recommendation-test-viewport")) { content() }
                }
            }
        }
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
    }

    private fun assertInsideViewport(bounds: Rect) {
        val viewport = composeRule.onNodeWithTag("recommendation-test-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("화면 밖의 터치 영역: $bounds, 화면: $viewport", bounds.left >= viewport.left &&
            bounds.top >= viewport.top && bounds.right <= viewport.right && bounds.bottom <= viewport.bottom)
    }

    private fun assertNonOverlapping(bounds: List<Rect>) {
        bounds.forEachIndexed { index, first ->
            bounds.drop(index + 1).forEach { second ->
                assertFalse("취향 chip이 서로 겹칩니다: $first, $second", first.overlaps(second))
            }
        }
    }

    private fun assertRecommendationCardTextFits(choice: RecommendationPreviewUi) {
        val card = composeRule.onNodeWithTag("dashboard-recommendation-card").fetchSemanticsNode().boundsInRoot
        val name = composeRule.onNodeWithText(choice.name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val calories = composeRule.onNodeWithText("약 ${choice.kcal} kcal", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val detail = composeRule.onNodeWithText(choice.reason, substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("메뉴명, kcal, 추천 이유가 겹칩니다.", name.bottom <= calories.top && calories.bottom <= detail.top)
        listOf(name, calories, detail).forEach { text ->
            assertTrue("추천 텍스트가 card 밖입니다: $text / $card", text.left >= card.left &&
                text.top >= card.top && text.right <= card.right && text.bottom <= card.bottom)
        }
    }

    private fun assertTextDoesNotClip(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("텍스트 layout 정보가 없습니다: $text", layouts.isNotEmpty())
        layouts.forEach { result ->
            assertFalse("추천 순서 높이가 잘렸습니다: $text", result.didOverflowHeight)
            (0 until result.lineCount).forEach { assertFalse("추천 순서가 ellipsis로 잘렸습니다: $text", result.isLineEllipsized(it)) }
            text.forEachIndexed { index, character ->
                if (!character.isWhitespace()) {
                    val glyph = result.getBoundingBox(index)
                    assertTrue("추천 순서 글자가 영역 밖입니다: $character / $text", glyph.right <= result.size.width + 1f &&
                        glyph.bottom <= result.size.height + 1f)
                }
            }
        }
    }

    private fun dismissKeyboard() {
        composeRule.runOnIdle { focusManager.clearFocus(force = true) }
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
    }

    /** Long names/reasons may use ellipsis, but the visible line must keep its full height. */
    private fun assertTextLineHeightFits(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("텍스트 layout 정보가 없습니다: $text", layouts.isNotEmpty())
        layouts.forEach { layout ->
            (0 until layout.lineCount).forEach { line ->
                assertTrue("텍스트 줄 높이가 잘렸습니다: $text", layout.getLineTop(line) >= -1f &&
                    layout.getLineBottom(line) <= layout.size.height + 1f)
            }
        }
    }

    private suspend fun seedMenus() {
        val calories = listOf(480, 500, 520, 580, 600, 620)
        val foods = calories.mapIndexed { index, kcal ->
            FoodItem(
                id = "taste-food-$index", sourceType = "K-FIND", sourceFoodCode = "taste-food-$index",
                name = if (index == 0) "오이 현미밥" else "두부 현미밥 $index",
                normalizedName = if (index == 0) "오이현미밥" else "두부현미밥$index",
                    // Exercise preferences at the supplied measured amount, not invented rice servings.
                    category = "QA 검증 메뉴", referenceAmount = 100.0, unit = "g", energyKcal = kcal.toDouble(),
                carbohydrateGrams = kcal * 0.55 / 4, proteinGrams = kcal * 0.20 / 4,
                fatGrams = kcal * 0.25 / 9, servingDescription = "100g 기준", dataVersion = "test",
                createdAt = 0, updatedAt = 0
            )
        }
        database.foodItemDao().upsertAll(foods)
        com.example.healthcare.domain.FoodMetadataPolicy.install(originalMetadata + foods.map {
            com.example.healthcare.domain.FoodMetadata(foodId=it.id, allergens=setOf("대두"),
                allergenInfoComplete=true, allergenStatus="CONFIRMED_LABEL",
                sourceReference="https://qa.invalid/isolated-label/${it.id}", checkedAt=LocalDate.now().toString())
        })
        database.mealCoachDao().upsertTemplates(calories.mapIndexed { index, kcal ->
            MealTemplate(
                id = "taste-menu-$index", name = foods[index].name,
                supportedMealTypes = "|LUNCH|", totalKcal = kcal,
                carbohydrateGrams = kcal * 0.55 / 4, proteinGrams = kcal * 0.20 / 4,
                fatGrams = kcal * 0.25 / 9, preparationMinutes = 10, costLevel = "LOW",
                tags = "|COOK|INGREDIENTS_COMPLETE|", allergens = "|대두|", cuisineType = "KOREAN",
                source = "QA isolated fixture", createdAt = 0, updatedAt = 0
            )
        })
        database.mealCoachDao().upsertIngredients(foods.mapIndexed { index, food ->
            MealTemplateIngredient(
                id = index + 1L, mealTemplateId = "taste-menu-$index", foodItemId = food.id,
                amount = 100.0, unit = "g", adjustable = true
            )
        })
    }
}
