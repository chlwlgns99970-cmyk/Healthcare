package com.example.healthcare

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.BodyProfileStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.domain.BodyProfile
import com.example.healthcare.domain.BodySex
import com.example.healthcare.domain.FoodBrowseCategory
import com.example.healthcare.domain.FoodSearchPolicy
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FruitRecordRegressionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val database get() = AppDatabase.getDatabase(context)

    @Before fun ready() = runBlocking {
        assertEquals("com.example.healthcare.qa", context.packageName)
        if (BodyProfileStore(context).read() == null) {
            BodyProfileStore(context).save(BodyProfile(BodySex.MALE, 35, 175.0, 70.0))
            compose.activityRule.scenario.recreate()
        }
        withTimeout(180_000) { while (database.foodItemDao().count() < 31_582) delay(100) }
        waitTag("dashboard-root")
    }

    @Test fun allSixFruitQueriesAndCategoryIntentUseBasicRepresentatives() = runBlocking {
        val repository = NutritionRepository(database.foodItemDao())
        val beforeCount = database.foodItemDao().count()
        listOf("사과", "바나나", "딸기", "포도", "배", "복숭아").forEach { query ->
            listOf(FoodBrowseCategory.ALL, FoodBrowseCategory.FRUIT).forEach { category ->
                val rows = repository.browse(category, query).first()
                val groups = FoodSearchPolicy.groupSearchResults(rows, query)
                assertEquals(query, FoodSearchPolicy.displayName(groups.first().representative))
                assertEquals("USDA-SR-LEGACY", groups.first().representative.sourceType)
                assertEquals(rows.size, groups.sumOf { it.size })
                assertEquals(rows.map { it.id }.toSet(), groups.flatMap {
                    listOf(it.representative) + it.alternatives
                }.map { it.id }.toSet())
                Log.i("FruitRecordQA", "$category $query basic rank=1 rows=${rows.size}")
            }
        }
        val intent = repository.browse(FoodBrowseCategory.ALL, "과일").first()
        val basicGroups = FoodSearchPolicy.groupSearchResults(intent, "과일").take(8)
        assertEquals(setOf("사과", "바나나", "딸기", "포도", "배", "복숭아", "오렌지", "수박"),
            basicGroups.map { FoodSearchPolicy.displayName(it.representative) }.toSet())
        assertTrue(basicGroups.all { it.representative.sourceType == "USDA-SR-LEGACY" })
        assertEquals(beforeCount, database.foodItemDao().count())
    }

    @Test fun allAppleBananaAndFruitCategoryAreVisibleInRealSearch() {
        openBreakfastSearch()
        compose.onNodeWithText("전체").assertIsSelected()
        search("사과", "usda-sr-171688")
        capture("all-apple.png")
        search("바나나", "usda-sr-173944")
        capture("all-banana.png")
        scrollText("과일")
        compose.onNodeWithText("과일").performClick()
        search("사과", "usda-sr-171688")
    }

    @Test fun homeQuickSaveThenSystemBackClosesActivityWithoutReopeningRecord() = runBlocking {
        val beforeIds = database.mealRecordDao().getAllMeals().first().map { it.id }.toSet()
        try {
            openBreakfastSearch()
            search("사과", "usda-sr-171688")
            compose.onNodeWithTag("food-search-result-usda-sr-171688").performClick()
            waitText("빠른 기록")
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasSetTextAction() and hasText("먹은 양"))
            compose.onNode(hasSetTextAction() and hasText("먹은 양")).performTextReplacement("100")
            compose.onNode(hasSetTextAction() and hasText("먹은 양")).performImeAction()
            scrollText("아침에 기록")
            compose.onNodeWithText("아침에 기록").performClick()
            waitTag("dashboard-root")
            compose.onAllNodesWithText("빠른 기록").assertCountEquals(0)
            val inserted = database.mealRecordDao().getAllMeals().first().filter { it.id !in beforeIds }
            assertEquals(1, inserted.size)
            assertEquals(MealType.BREAKFAST, inserted.single().mealType)
            capture("home-after-record.png")
            Espresso.pressBackUnconditionally()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(Lifecycle.State.DESTROYED, compose.activityRule.scenario.state)
        } finally {
            database.mealRecordDao().getAllMeals().first().filter { it.id !in beforeIds }.forEach {
                database.mealRecordDao().deleteMeal(it)
            }
        }
    }

    @Test fun upperAndSystemBackCancelHomeSearchWithoutDuplicateHome() {
        openBreakfastSearch()
        search("사과", "usda-sr-171688")
        compose.onNodeWithTag("food-search-result-usda-sr-171688").performClick()
        waitText("빠른 기록")
        compose.onNodeWithContentDescription("음식 검색으로 돌아가기").performClick()
        waitText("음식 검색")
        compose.onNodeWithContentDescription("뒤로가기").performClick()
        waitTag("dashboard-root")
        openBreakfastSearch()
        search("사과", "usda-sr-171688")
        compose.onNodeWithTag("food-search-result-usda-sr-171688").performClick()
        waitText("빠른 기록")
        Espresso.pressBack()
        waitText("음식 검색")
        Espresso.pressBack()
        waitTag("dashboard-root")
        Espresso.pressBackUnconditionally()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(Lifecycle.State.DESTROYED, compose.activityRule.scenario.state)
    }

    @Test fun historyDetailBackAndEditSaveReturnToHistoryList() = runBlocking {
        val name = "QA navigation ${System.nanoTime()}"
        database.mealRecordDao().insertMeal(MealRecord(date = LocalDate.now().toString(), time = "08:30",
            mealType = MealType.BREAKFAST, foodName = name, calories = 52))
        val record = database.mealRecordDao().getAllMeals().first().single { it.foodName == name }
        try {
            compose.onNodeWithText("통계").performClick()
            waitText("식사 기록")
            compose.onNodeWithText("식사 기록").performClick()
            scrollText(name)
            compose.onNodeWithText(name).performClick()
            waitTag("history-detail-edit")
            compose.onNodeWithContentDescription("뒤로 가기").performClick()
            waitText("오늘의 기록")
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("history-item-edit-${record.id}"))
            compose.onNodeWithTag("history-item-edit-${record.id}").performClick()
            compose.onNode(hasSetTextAction() and hasText("칼로리", substring = true)).performTextReplacement("60")
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("history-edit-save"))
            compose.onNodeWithTag("history-edit-save").performClick()
            waitText("오늘의 기록")
            assertEquals(60, database.mealRecordDao().getAllMeals().first().single { it.id == record.id }.calories)
        } finally {
            database.mealRecordDao().getAllMeals().first().firstOrNull { it.id == record.id }?.let {
                database.mealRecordDao().deleteMeal(it)
            }
        }
    }

    @Test fun crowdedGenericResultsKeepBasicPearAndSeparateBrandJuiceLookup() = runBlocking {
        val memory = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val pear = FoodItem(id = "pear", sourceType = "USDA-SR-LEGACY", sourceFoodCode = "pear",
                name = "배_동양배_생것", normalizedName = "배동양배생것", aliases = "|배|과일|",
                category = "과일류", referenceAmount = 100.0, unit = "g", energyKcal = 42.0,
                servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0)
            val crowded = (1..100).map { pear.copy(id = "bread-$it", sourceType = "K-FIND",
                sourceFoodCode = "bread-$it",
                name = "배가공${it.toString().padStart(3, '0')}", normalizedName = "배가공$it", aliases = "", category = "빵류") }
            val juice = pear.copy(id = "juice", sourceType = "K-FIND-PRODUCT", name = "사과주스",
                normalizedName = "사과주스", aliases = "", category = "음료", brand = "OO")
            memory.foodItemDao().upsertAll(crowded + pear + juice)
            val repository = NutritionRepository(memory.foodItemDao())
            assertEquals("pear", repository.search("배").first().first().id)
            assertEquals("juice", repository.search("OO 사과주스").first().first().id)
            assertEquals(102, memory.foodItemDao().count())
        } finally { memory.close() }
    }

    private fun openBreakfastSearch() {
        compose.onNodeWithTag("dashboard-meal-breakfast").performClick()
        waitText("음식 검색")
    }
    private fun search(query: String, id: String) {
        compose.onNode(hasSetTextAction() and hasText("음식·제품 또는 브랜드 이름", substring = true))
            .performTextReplacement(query)
        waitTag("food-search-result-$id")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("food-search-result-$id"))
        compose.onNodeWithTag("food-search-result-$id").assertIsDisplayed()
        compose.onNodeWithTag("food-search-result-$id").assert(hasText(query))
    }
    private fun waitTag(tag: String) = compose.waitUntil(30_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun waitText(text: String) = compose.waitUntil(30_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun scrollText(text: String) = compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    private fun capture(name: String) {
        val output = File(context.getExternalFilesDir(null), "fruit-record-qa/$name")
        output.parentFile!!.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
