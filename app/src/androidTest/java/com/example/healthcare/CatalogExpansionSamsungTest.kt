package com.example.healthcare

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.FoodMetadataStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.EnergyProfileRepository
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.GoalRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.data.seed.BundledFoodDataSeeder
import com.example.healthcare.domain.FoodAmountPolicy
import com.example.healthcare.domain.FoodMetadata
import com.example.healthcare.domain.FoodMetadataPolicy
import com.example.healthcare.domain.FoodMenuCategoryPolicy
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.FranchiseCatalog
import com.example.healthcare.domain.FranchiseMenu
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.PortionQuality
import com.example.healthcare.domain.RecordedAmountSnapshot
import com.example.healthcare.ui.screens.HistoryDetailPane
import com.example.healthcare.ui.screens.HistoryEditPane
import com.example.healthcare.ui.screens.QuickFoodRecordScreen
import com.example.healthcare.ui.screens.SmartFoodInputScreen
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.FoodSearchMode
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import com.example.healthcare.ui.viewmodel.HistoryViewModel
import com.example.healthcare.ui.viewmodel.SmartInputMode
import com.example.healthcare.ui.viewmodel.SmartInputUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/** Real packaged assets and application policies, with one isolated in-memory seed per class. */
class CatalogExpansionSamsungTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun all229ReviewedMenusMatchPackagedRuntimeCategories() = runBlocking {
        val decisions=testRows("fixtures/full-menu-adjudication.csv")
        assertEquals(229,decisions.size)
        decisions.forEach { row ->
            val id=requireNotNull(row["menuId"])
            val food=repository.findById(id)
            val category=if (food!=null) FoodMenuCategoryPolicy.categoryOf(food) else {
                val menu=requireNotNull(FranchiseCatalog.officialMenus(requireNotNull(row["brand"])).firstOrNull { it.id==id }) { "Missing reviewed menu $id" }
                FoodMenuCategoryPolicy.categoryOf(menu)
            }
            assertEquals("Final menu decision $id",row["category"].orEmpty().ifBlank { null },category)
        }
    }

    @Test fun recipeCompletionShowsAllInputsAndPartialNoticeWithoutChangingCalories() {
        val records=Room.inMemoryDatabaseBuilder(fixture,AppDatabase::class.java).build()
        val models=ViewModelStore()
        lateinit var add: AddRecordViewModel
        val potato=runBlocking { requireFood("kfind-d408-356000000-0001") }
        val kimbap=runBlocking { requireFood("kfind-d101-007000000-0001") }
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()),nutritionRepository=repository)
            models.put("recipe-completion",add);add.showFoodSearch();add.selectSearchFood(potato)
        }
        try {
            compact { com.example.healthcare.ui.screens.AddRecordScreen(viewModel=add) }
            scrollTo(hasText("100ml 기준 · 123 kcal"))
            compose.onNodeWithText("100ml 기준 · 123 kcal").assertIsDisplayed()
            scrollTo(hasText("감자 520g · 약 400 kcal"))
            compose.onNodeWithText("감자 520g · 약 400 kcal").assertIsDisplayed()
            scrollTo(hasText("소금 2g · 약 0 kcal"))
            compose.onNodeWithText("소금 2g · 약 0 kcal").assertIsDisplayed()
            scrollTo(hasText("공식 참고 레시피의 모든 주요 재료가 연결되었습니다."))
            compose.onAllNodesWithText("공식 참고 레시피의 모든 주요 재료가 연결되었습니다.").onFirst().assertIsDisplayed()
            saveFullAuditScreen("complete-recipe")
            scrollTo(hasText("공식 구성의 재료량과 개별 식품 영양정보를 기준으로 계산한 참고 예상값입니다."))
            compose.onNodeWithText("공식 구성의 재료량과 개별 식품 영양정보를 기준으로 계산한 참고 예상값입니다.").assertIsDisplayed()
            assertEquals(123.0,potato.energyKcal,0.0)
            compose.runOnIdle { add.selectSearchFood(kimbap) }
            scrollTo(hasText("100g 기준 · 140 kcal"))
            compose.onNodeWithText("100g 기준 · 140 kcal").assertIsDisplayed()
            scrollTo(hasTestTag("food-amount-unit"))
            compose.onNodeWithTag("food-amount-unit").assertTextContains("g",substring=true)
            compose.runOnIdle { add.onFoodQuantityChange("100") }
            compose.onNodeWithTag("food-amount-quantity").assertTextContains("100",substring=true)
            assertEquals("140",add.uiState.value.calories)
            assertEquals(listOf("g"),add.uiState.value.amountChoices.map { it.unit })
            scrollTo(hasText("재료별 예상 열량 · 공식 레시피 참고 구성"))
            compose.onAllNodesWithText("재료별 예상 열량 · 공식 레시피 참고 구성").onFirst().assertIsDisplayed()
            scrollTo(hasText("공식 김밥 레시피 참고 구성 · 선택한 음식의 실제 배합은 아닙니다."))
            compose.onAllNodesWithText("공식 김밥 레시피 참고 구성 · 선택한 음식의 실제 배합은 아닙니다.").onFirst().assertIsDisplayed()
            scrollTo(hasText("확인 가능한 재료 기준 · 일부 재료만 표시합니다. 전체 재료의 합계가 아닙니다."))
            compose.onAllNodesWithText("확인 가능한 재료 기준 · 일부 재료만 표시합니다. 전체 재료의 합계가 아닙니다.").onFirst().assertIsDisplayed()
            scrollTo(hasText("오이 13g · 약 2 kcal"))
            compose.onNodeWithText("오이 13g · 약 2 kcal").assertIsDisplayed()
            scrollTo(hasText("김 1.5g · 약 3 kcal"))
            compose.onNodeWithText("김 1.5g · 약 3 kcal").assertIsDisplayed()
            scrollTo(hasText("계란 30g · 약 42 kcal"))
            compose.onNodeWithText("계란 30g · 약 42 kcal").assertIsDisplayed()
            saveFullAuditScreen("kimbap-ingredients")
            assertEquals(140.0,kimbap.energyKcal,0.0)
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }

    @Test fun newlyCompleteBrownRiceAndGondreReferencesAppearWithOriginalCalories() {
        val records=Room.inMemoryDatabaseBuilder(fixture,AppDatabase::class.java).build()
        val models=ViewModelStore()
        val foods=runBlocking { listOf(requireFood("kfind-d701-050000000-0001"),requireFood("kfind-d301-002000000-0001")) }
        lateinit var add: AddRecordViewModel
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()),nutritionRepository=repository)
            models.put("maximized-references",add);add.showFoodSearch();add.selectSearchFood(foods[0])
        }
        try {
            compact { com.example.healthcare.ui.screens.AddRecordScreen(viewModel=add) }
            listOf("현미 360g · 약 1285 kcal","현미 30g · 약 107 kcal").forEachIndexed { index,expected ->
                if(index>0) compose.runOnIdle { add.selectSearchFood(foods[index]) }
                if(index==1) {
                    val notice="공공 조리자료의 참고 구성입니다. 선택한 음식의 실제 배합은 달라질 수 있어요."
                    scrollTo(hasText(notice));compose.onNodeWithText(notice).assertIsDisplayed()
                }
                scrollTo(hasText(expected));compose.onNodeWithText(expected).assertIsDisplayed()
                scrollTo(hasText("공식 참고 레시피의 모든 주요 재료가 연결되었습니다."))
                compose.onAllNodesWithText("공식 참고 레시피의 모든 주요 재료가 연결되었습니다.").onFirst().assertIsDisplayed()
                saveFullAuditScreen(if(index==0) "complete-brown-rice" else "complete-gondre")
                assertTrue(com.example.healthcare.domain.RecipeCaloriePolicy.lookup(foods[index].id).all { it.recipeComplete })
            }
            assertEquals(121.0,foods[0].energyKcal,0.0);assertEquals(149.0,foods[1].energyKcal,0.0)
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }

    @Test fun newlyCompletePumpkinReferenceUsesOneOriginalRecipeAndKeepsOfficialCalories() {
        val records=Room.inMemoryDatabaseBuilder(fixture,AppDatabase::class.java).build()
        val models=ViewModelStore()
        val food=runBlocking { requireFood("kfind-d404-197000000-0001") }
        lateinit var add: AddRecordViewModel
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()),nutritionRepository=repository)
            models.put("residual-complete",add);add.showFoodSearch();add.selectSearchFood(food)
        }
        try {
            compact { com.example.healthcare.ui.screens.AddRecordScreen(viewModel=add) }
            val references=com.example.healthcare.domain.RecipeCaloriePolicy.lookup(food.id)
            assertEquals(setOf("RDA-89514"),references.map { it.recipeId }.toSet())
            assertTrue(references.all { it.recipeComplete })
            references.forEach { assertEquals(food.energyKcal,it.foodReferenceKcal!!,0.0) }
            scrollTo(hasText("재료별 예상 열량 · 공식 레시피 참고 구성"))
            compose.onAllNodesWithText("재료별 예상 열량 · 공식 레시피 참고 구성").onFirst().assertIsDisplayed()
            scrollTo(hasText("팥 210g · 약 691 kcal"))
            compose.onNodeWithText("팥 210g · 약 691 kcal").assertIsDisplayed()
            scrollTo(hasText("공식 참고 레시피의 모든 주요 재료가 연결되었습니다."))
            compose.onAllNodesWithText("공식 참고 레시피의 모든 주요 재료가 연결되었습니다.").onFirst().assertIsDisplayed()
            saveFullAuditScreen("residual-complete-pumpkin")
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }

    @Test fun bulkStrategyThreeNewCompleteReferencesKeepOfficialCalories() {
        val records=Room.inMemoryDatabaseBuilder(fixture,AppDatabase::class.java).build()
        val models=ViewModelStore()
        val ids=listOf("kfind-d505-273000000-0001","kfind-d505-306000000-0001","kfind-d605-210000000-0001")
        val expectedRecipes=listOf("MFDS-152","MFDS-182","RDA-90749")
        val foods=runBlocking { ids.map { requireFood(it) } }
        lateinit var add: AddRecordViewModel
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()),nutritionRepository=repository)
            models.put("bulk-strategy-complete",add);add.showFoodSearch();add.selectSearchFood(foods[0])
        }
        try {
            compact { com.example.healthcare.ui.screens.AddRecordScreen(viewModel=add) }
            foods.forEachIndexed { index,food ->
                if(index>0) compose.runOnIdle { add.selectSearchFood(food) }
                val references=com.example.healthcare.domain.RecipeCaloriePolicy.lookup(food.id)
                assertEquals(setOf(expectedRecipes[index]),references.map { it.recipeId }.toSet())
                assertTrue(references.isNotEmpty() && references.all { it.recipeComplete })
                references.forEach { assertEquals(food.energyKcal,it.foodReferenceKcal!!,0.0) }
                scrollTo(hasText("공식 참고 레시피의 모든 주요 재료가 연결되었습니다."))
                compose.onAllNodesWithText("공식 참고 레시피의 모든 주요 재료가 연결되었습니다.").onFirst().assertIsDisplayed()
                scrollTo(hasText("이 참고 레시피는 선택한 식품의 확정 배합이 아니며, 기록할 열량에는 반영하지 않습니다."))
                compose.onAllNodesWithText("이 참고 레시피는 선택한 식품의 확정 배합이 아니며, 기록할 열량에는 반영하지 않습니다.").onFirst().assertIsDisplayed()
                saveFullAuditScreen("strategy-complete-${index+1}")
            }
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }

    @Test fun residualSesameAndAdzukiReferencesAppearAsPartialWithoutChangingOfficialCalories() {
        val records=Room.inMemoryDatabaseBuilder(fixture,AppDatabase::class.java).build()
        val models=ViewModelStore()
        val examples=listOf(
            "kfind-d613-597000000-0001" to "통깨 2g · 약 11 kcal",
            "kfind-d407-347000000-0001" to "통깨 2g · 약 11 kcal",
            "kfind-d701-083000000-0001" to "팥 40g · 약 132 kcal"
        )
        val foods=runBlocking { examples.map { requireFood(it.first) } }
        lateinit var add: AddRecordViewModel
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()),nutritionRepository=repository)
            models.put("residual-references",add);add.showFoodSearch();add.selectSearchFood(foods[0])
        }
        try {
            compact { com.example.healthcare.ui.screens.AddRecordScreen(viewModel=add) }
            examples.forEachIndexed { index,(_,expected) ->
                if(index>0) compose.runOnIdle { add.selectSearchFood(foods[index]) }
                val references=com.example.healthcare.domain.RecipeCaloriePolicy.lookupCompositions(foods[index].id).flatten().filter { it.compositionKind == "ORIGINAL" }
                assertTrue(references.isNotEmpty());assertTrue(references.none { it.recipeComplete })
                references.forEach { assertEquals(foods[index].energyKcal,it.foodReferenceKcal!!,0.0) }
                scrollTo(hasText(expected));compose.onNodeWithText(expected).assertIsDisplayed()
                scrollTo(hasText("확인 가능한 재료 기준 · 일부 재료만 표시합니다. 전체 재료의 합계가 아닙니다."))
                compose.onAllNodesWithText("확인 가능한 재료 기준 · 일부 재료만 표시합니다. 전체 재료의 합계가 아닙니다.").onFirst().assertIsDisplayed()
                saveFullAuditScreen("residual-${index+1}")
            }
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }

    @Test fun explicitRdaSpoonReferencesRemainPartialAndKeepOfficialCalories() {
        val records=Room.inMemoryDatabaseBuilder(fixture,AppDatabase::class.java).build()
        val models=ViewModelStore()
        val examples=listOf("kfind-d111-523000000-0001" to "다진 생강 8g · 약 6 kcal",
            "kfind-d307-311000000-0001" to "다진 생강 4g · 약 3 kcal")
        val foods=runBlocking { examples.map { requireFood(it.first) } }
        lateinit var add: AddRecordViewModel
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()),nutritionRepository=repository)
            models.put("explicit-rda-spoons",add);add.showFoodSearch();add.selectSearchFood(foods[0])
        }
        try {
            compact { com.example.healthcare.ui.screens.AddRecordScreen(viewModel=add) }
            examples.forEachIndexed { index,(_,expected) ->
                if(index>0) compose.runOnIdle { add.selectSearchFood(foods[index]) }
                val references=com.example.healthcare.domain.RecipeCaloriePolicy.lookupCompositions(foods[index].id).flatten().filter { it.compositionKind == "ORIGINAL" }
                assertTrue(references.isNotEmpty() && references.none { it.recipeComplete })
                references.forEach { assertEquals(foods[index].energyKcal,it.foodReferenceKcal!!,0.0) }
                scrollTo(hasText(expected));compose.onNodeWithText(expected).assertIsDisplayed()
                saveFullAuditScreen("strategy-spoon-${index+1}")
            }
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }

    @Test fun nolbooPublicMenusAndResolvedCategoriesAppearWithoutZeroNutrition() = runBlocking {
        val menus=FranchiseCatalog.officialMenus("놀부부대찌개")
        assertEquals(24,menus.size)
        val brands=repository.searchFranchiseBrands("놀부부대찌개").first()
        assertEquals(24,brands.single().productCount)
        var state by mutableStateOf(brandState("놀부부대찌개",emptyList(),listOf("한식"),"한식"))
        compact { search(state) }
        val menu=menus.single { it.name=="고기듬뿍김치찌개" }
        scrollTo(hasTestTag("franchise-menu-${menu.id}"))
        compose.onNodeWithTag("franchise-menu-${menu.id}").assertIsDisplayed()
        compose.onNodeWithText("0 kcal").assertDoesNotExist()
        scrollTo(hasText("출처: 공공 관광 매장 메뉴 · 서울 신길로 39 · 확인 2026-10-04"))
        compose.onAllNodesWithText("출처: 공공 관광 매장 메뉴 · 서울 신길로 39 · 확인 2026-10-04").onFirst().assertIsDisplayed()
        val order=menus.single { it.name=="놀부세트" }
        assertEquals("OFFICIAL_ORDER_BRAND_SALES_UNVERIFIED",order.saleState)
        compose.runOnIdle { state=brandState("놀부부대찌개",emptyList(),listOf("부대찌개"),"부대찌개") }
        scrollTo(hasTestTag("franchise-menu-${order.id}"))
        compose.onNodeWithTag("franchise-menu-${order.id}").assertIsDisplayed()
        scrollTo(hasText("출처: 공식 주문 브랜드 메뉴 · 현재 판매 여부 미확인 · 원문 갱신 ${order.sourceDate} · 확인 2026-10-05"))
        compose.onAllNodesWithText("출처: 공식 주문 브랜드 메뉴 · 현재 판매 여부 미확인 · 원문 갱신 ${order.sourceDate} · 확인 2026-10-05").onFirst().assertIsDisplayed()
        saveFullAuditScreen("nolboo-brand-scope")
        compose.onNodeWithText("0 kcal").assertDoesNotExist()
        val lunch=FranchiseCatalog.officialMenus("한솥").single { it.name=="동백" }
        compose.runOnIdle { state=brandState("한솥",emptyList(),listOf("도시락"),"도시락") }
        scrollTo(hasTestTag("franchise-menu-${lunch.id}"))
        compose.onNodeWithTag("franchise-menu-${lunch.id}").assertIsDisplayed()
        val drink=FranchiseCatalog.officialMenus("죠스떡볶이").single { it.name=="죠스쿨" }
        compose.runOnIdle { state=brandState("죠스떡볶이",emptyList(),listOf("음료"),"음료") }
        scrollTo(hasTestTag("franchise-menu-${drink.id}"))
        compose.onNodeWithTag("franchise-menu-${drink.id}").assertIsDisplayed()
        val pancake=FranchiseCatalog.officialMenus("국수나무").single { it.name=="15cm 감자전" }
        compose.runOnIdle { state=brandState("국수나무",emptyList(),listOf("한식"),"한식") }
        scrollTo(hasTestTag("franchise-menu-${pancake.id}"))
        compose.onNodeWithTag("franchise-menu-${pancake.id}").assertIsDisplayed()
        val foods=repository.search("아보홀릭").first().filter { it.brand=="에그드랍" }
        assertTrue(foods.any { FoodMenuCategoryPolicy.categoryOf(it)=="샌드위치" })
        compose.runOnIdle { state=brandState("에그드랍",foods,listOf("샌드위치"),"샌드위치") }
        val sandwich=foods.first { FoodMenuCategoryPolicy.categoryOf(it)=="샌드위치" }
        scrollTo(hasTestTag("food-search-result-${sandwich.id}"))
        compose.onNodeWithTag("food-search-result-${sandwich.id}").assertIsDisplayed()
        Unit
    }

    @Test fun completeRecipeReferenceDoesNotChangeStoredMealCalories() {
        val records=Room.inMemoryDatabaseBuilder(fixture,AppDatabase::class.java).build()
        val models=ViewModelStore()
        val food=runBlocking { requireFood("kfind-d408-356000000-0001") }
        lateinit var add: AddRecordViewModel
        var saved=false
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()),nutritionRepository=repository)
            models.put("recipe-record",add);add.selectSearchFood(food)
            add.onServingUnitChange("ml");add.onServingAmountChange("100");add.onCaloriesChange("123")
        }
        try {
            val expected=add.uiState.value.calories.toInt()
            assertTrue(com.example.healthcare.domain.RecipeCaloriePolicy.lookup(food.id).all { it.recipeComplete })
            compose.runOnIdle { add.saveRecord { saved=true } }
            compose.waitUntil(5000) { saved }
            val meal=runBlocking { records.mealRecordDao().getAllMeals().first().single() }
            assertEquals(expected,meal.calories)
            assertEquals(food.id,meal.foodItemId)
            assertEquals(123.0,food.energyKcal,0.0)
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }

    @Test fun unlinkableRecipeShowsNamesWithoutInventingIngredientCalories() {
        val records=Room.inMemoryDatabaseBuilder(fixture,AppDatabase::class.java).build()
        val models=ViewModelStore()
        val food=runBlocking { requireFood("kfind-d304-183000000-0001") }
        assertTrue(com.example.healthcare.domain.RecipeCaloriePolicy.lookup(food.id).isEmpty())
        lateinit var add: AddRecordViewModel
        compose.activityRule.scenario.onActivity {
            add=AddRecordViewModel(MealRepository(records.mealRecordDao()),FoodRepository(records.frequentFoodDao()),nutritionRepository=repository)
            models.put("unlinked-reference",add);add.showFoodSearch();add.selectSearchFood(food)
        }
        try {
            compact { com.example.healthcare.ui.screens.AddRecordScreen(viewModel=add) }
            val basis="${RecordedAmountSnapshot.format(food.referenceAmount)}${food.unit} 기준 · ${food.energyKcal.toInt()} kcal"
            scrollTo(hasText(basis));compose.onNodeWithText(basis).assertIsDisplayed()
            val detail=com.example.healthcare.domain.FoodDetailPolicy.forFood(food)
            assertFalse(detail.ingredientText.isNullOrBlank())
            val notice=if(detail.ingredientIsReference) "공공 조리자료의 참고 구성입니다. 선택한 음식의 실제 배합은 달라질 수 있어요."
                else "원문에서 확인한 재료 정보입니다. 재료별 양과 영양 근거가 있는 구성만 아래에 표시해요."
            scrollTo(hasText(notice));compose.onNodeWithText(notice).assertIsDisplayed()
            compose.onNodeWithText("재료별 예상 열량 · 공식 레시피 참고 구성").assertDoesNotExist()
            saveFullAuditScreen("unlinkable-recipe")
            assertTrue(com.example.healthcare.domain.RecipeCaloriePolicy.lookup(food.id).isEmpty())
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };records.close() }
    }

    @Test fun searchDetailBackKeepsQueryFilterAndScrollAndShowsRecipeReference() {
        val records = Room.inMemoryDatabaseBuilder(fixture, AppDatabase::class.java).build()
        val models = ViewModelStore()
        lateinit var add: AddRecordViewModel
        compose.activityRule.scenario.onActivity {
            add = AddRecordViewModel(MealRepository(records.mealRecordDao()), FoodRepository(records.frequentFoodDao()), nutritionRepository = repository)
            models.put("search-followup", add)
            add.showFoodSearch(); add.onFoodSearchChange("김밥")
        }
        try {
            compact { com.example.healthcare.ui.screens.AddRecordScreen(viewModel = add) }
            compose.waitUntil(15_000) { add.smartInputState.value.searchResults.isNotEmpty() }
            val food = runBlocking { requireFood("kfind-product-p123-203020200-2032") }
            scrollTo(hasTestTag("food-search-result-${food.id}"))
            val before = add.smartInputState.value
            val scrollBefore = compose.onAllNodes(hasScrollAction()).onFirst().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            compose.onNodeWithTag("food-search-result-${food.id}").performClick()
            compose.onNodeWithText("음식 상세").assertIsDisplayed()
            scrollTo(hasText("칼로리 근거")); compose.onNodeWithText("칼로리 근거").assertIsDisplayed()
            compose.onNodeWithContentDescription("음식 검색으로 돌아가기").performClick()
            compose.waitUntil { add.smartInputState.value.mode == SmartInputMode.SEARCH }
            assertEquals(before.searchQuery, add.smartInputState.value.searchQuery)
            assertEquals(before.selectedFoodCategory, add.smartInputState.value.selectedFoodCategory)
            assertEquals(before.searchResults, add.smartInputState.value.searchResults)
            val scrollAfter = compose.onAllNodes(hasScrollAction()).onFirst().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals(scrollBefore, scrollAfter, 0.01f)
            val salad = runBlocking { requireFood("kfind-d101-007000000-0001") }
            compose.runOnIdle { add.selectSearchFood(salad) }
            scrollTo(hasText("재료별 예상 열량 · 공식 레시피 참고 구성"))
            compose.onAllNodesWithText("재료별 예상 열량 · 공식 레시피 참고 구성").onFirst().assertIsDisplayed()
            scrollTo(hasText("오이 13g · 약 2 kcal"))
            compose.onNodeWithText("오이 13g · 약 2 kcal").assertIsDisplayed()
            assertEquals(140.0, salad.energyKcal, 0.0)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }; records.close()
        }
    }

    @Test fun brandSearchBackKeepsPizzaSchoolQueryAndPrimaryFilter() {
        val records = Room.inMemoryDatabaseBuilder(fixture, AppDatabase::class.java).build()
        val models = ViewModelStore()
        lateinit var add: AddRecordViewModel
        compose.activityRule.scenario.onActivity {
            add = AddRecordViewModel(MealRepository(records.mealRecordDao()), FoodRepository(records.frequentFoodDao()), nutritionRepository = repository)
            models.put("brand-followup", add)
            add.showFoodSearch(); add.selectFoodSearchMode(FoodSearchMode.FRANCHISE)
            add.onFoodSearchChange("피자스쿨"); add.selectBrandCategory("피자")
        }
        try {
            compact { com.example.healthcare.ui.screens.AddRecordScreen(viewModel = add) }
            compose.waitUntil(15_000) { add.smartInputState.value.brandResults.any { it.brand == "피자스쿨" } }
            scrollTo(hasTestTag("brand-result-피자스쿨"))
            compose.onNodeWithTag("brand-result-피자스쿨").performClick()
            compose.waitUntil { add.smartInputState.value.selectedBrand != null }
            compose.onNodeWithContentDescription("뒤로가기").performClick()
            compose.waitUntil { add.smartInputState.value.selectedBrand == null }
            assertEquals("피자스쿨", add.smartInputState.value.searchQuery)
            assertEquals("피자", add.smartInputState.value.selectedBrandCategory)
            scrollTo(hasTestTag("brand-result-피자스쿨"))
            compose.onNodeWithTag("brand-result-피자스쿨").assertIsDisplayed()
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }; records.close() }
    }

    @Test fun all89RepresentativeBrandFiltersAndQueryCombinationsMatchActualRepository() = runBlocking {
        val all = repository.searchFranchiseBrands("").first()
        assertEquals(89, all.size)
        FranchiseCatalog.brandFilterCategories.forEach { filter ->
            val actual = repository.searchFranchiseBrandsByCategory(filter.label).first().map { it.brand }.toSet()
            val expected = all.filter { FranchiseCatalog.matchesBrandFilter(it.brand, filter.label) }.map { it.brand }.toSet()
            assertEquals(filter.label, expected, actual)
        }
        listOf("굽네치킨" to "치킨", "지코바" to "치킨", "피자스쿨" to "피자").forEach { (brand, category) ->
            assertTrue(repository.searchFranchiseBrandsByCategory(category, brand).first().any { it.brand == brand })
            val other = if (category == "치킨") "피자" else "치킨"
            assertTrue(repository.searchFranchiseBrandsByCategory(other, brand).first().isEmpty())
        }
        val pizzas = repository.searchFranchiseBrandsByCategory("피자").first()
        var state by mutableStateOf(SmartInputUiState(mode = SmartInputMode.SEARCH, searchMode = FoodSearchMode.FRANCHISE,
            selectedBrandCategory = "피자", brandResults = pizzas))
        compact { search(state) }
        compose.onNodeWithText("굽네치킨").assertDoesNotExist()
        val chickens = repository.searchFranchiseBrandsByCategory("치킨").first()
        compose.runOnIdle { state = state.copy(selectedBrandCategory = "치킨", brandResults = chickens) }
        scrollTo(hasText("굽네치킨") and hasClickAction())
        compose.onNode(hasText("굽네치킨") and hasClickAction()).assertIsDisplayed()
        assertEquals(89, FranchiseCatalog.entries.size)
    }

    @Test fun sourceSmokeQueriesReachTheirExactIdentityThroughDaoAndRepository() = runBlocking {
        val rows = testRows("catalog-retail/search-smoke.csv")
        assertEquals(192, rows.size)
        assertEquals(16, rows.map { it.getValue("family") }.toSet().size)
        val failures = mutableListOf<String>()
        val report = mutableListOf("family,query,expectedFoodItemId,daoHit,repositoryHit")
        rows.forEach { row ->
            val query = row.getValue("query")
            val expectedId = row.getValue("expectedFoodItemId")
            val expected = database.foodItemDao().findById(expectedId)
            assertNotNull("Missing seeded identity $expectedId", expected)
            assertEquals(row.getValue("expectedName"), expected!!.name)
            val normalized = FoodSearchPolicy.normalize(query)
            assertEquals(row.getValue("normalizedQuery"), normalized)
            val daoHits = database.foodItemDao().observeProductSearch(normalized, limit = -1).first()
            val results = repository.search(query).first()
            val daoHit = daoHits.any { it.id == expectedId }
            val repositoryHit = results.any { it.id == expectedId }
            if (!daoHit || !repositoryHit) failures += "${row.getValue("family")}: $query -> $expectedId (DAO=$daoHit repository=$repositoryHit)"
            report += listOf(row.getValue("family"), query, expectedId, daoHit.toString(), repositoryHit.toString())
                .joinToString(",", transform = ::csvField)
        }
        requireNotNull(fixture.getExternalFilesDir(null)).apply { mkdirs() }.resolve("catalog-expansion-search-results.csv")
            .writeText(report.joinToString("\n", postfix = "\n"))
        Log.i("CatalogExpansionQA", "192 actual queries; missing exact identities: ${failures.size}")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun officialChapagettiBagRetainsLabelNutrientsAndSeparateCrossContact() = runBlocking {
        val food = requireFood(CHAPAGETTI_ID)
        listOf("짜파게티", "농심 짜파게티", "농심_짜파게티", "올리브 짜파게티").forEach { query ->
            assertTrue(query, repository.search(query).first().any { it.id == food.id })
        }
        assertEquals("OFFICIAL-RETAIL-PRODUCT", food.sourceType)
        assertEquals(140.0, food.referenceAmount, 0.0)
        assertEquals("g", food.unit)
        assertEquals("봉", FoodAmountPolicy.defaultChoice(food)?.unit)
        val one = requireNotNull(FoodAmountPolicy.calculate(food, 1.0, "봉"))
        assertEquals(610, one.calories)
        assertEquals(96.0, one.carbohydrateGrams!!, 0.0)
        assertEquals(9.0, one.proteinGrams!!, 0.0)
        assertEquals(20.0, one.fatGrams!!, 0.0)
        val half = requireNotNull(FoodAmountPolicy.calculate(food, 0.5, "봉"))
        assertEquals(305, half.calories)
        assertEquals(48.0, half.carbohydrateGrams!!, 0.0)
        assertEquals(one, FoodAmountPolicy.calculate(food, 140.0, "g"))
        assertNull(FoodAmountPolicy.calculate(food, 1.0, "ml"))
        val metadata = requireNotNull(FoodMetadataPolicy.lookup(food.id))
        assertTrue(metadata.ingredientInfoComplete)
        assertTrue(metadata.allergenInfoComplete)
        assertTrue(metadata.completeIngredientText.length > 350)
        assertTrue(metadata.allergens.containsAll(setOf("밀", "달걀", "우유", "대두", "돼지고기", "새우", "쇠고기")))
        assertFalse(metadata.allergens.contains("땅콩"))
        assertTrue(metadata.mayContainAllergens.contains("땅콩"))
        assertTrue(metadata.crossContactText.contains("같은 시설"))
        assertTrue(metadata.sourceHash.isNotBlank())
        assertTrue(metadata.servingSourceReference.startsWith("https://nongshimmall.com/"))
    }

    @Test fun fourIcePackagesUsePublishedWholeVolumeWithoutChangingNutritionBasis() = runBlocking {
        ICE_PACKAGES.forEach { sample ->
            val food = requireFood(sample.id)
            assertEquals(sample.basis, food.referenceAmount, 0.0)
            assertEquals("ml", food.unit)
            val choice = requireNotNull(FoodAmountPolicy.defaultChoice(food))
            assertEquals(sample.unit, choice.unit)
            assertEquals(PortionQuality.OFFICIAL_SERVING, choice.quality)
            assertEquals(sample.packageAmount, choice.basisAmountPerUnit, 0.0)
            val whole = requireNotNull(FoodAmountPolicy.calculate(food, 1.0, sample.unit))
            assertEquals(sample.calories, whole.calories)
            assertEquals(sample.packageAmount, whole.basisAmount, 0.0)
            assertEquals(whole, FoodAmountPolicy.calculate(food, sample.packageAmount, "ml"))
            assertNull(FoodAmountPolicy.calculate(food, sample.packageAmount, "g"))
        }
        val together = requireFood("official-retail-binggrae-pack-1")
        assertEquals(100.0, together.energyKcal, 0.0)
        assertEquals(450, FoodAmountPolicy.calculate(together, 0.5, "통")?.calories)
    }

    @Test fun sixRequiredBrandsAndAliasesReachActualOfficialMenusAndDishCategories() = runBlocking {
        val required = linkedMapOf(
            "피자스쿨" to listOf("피자스쿨", "pizza school"),
            "파파존스" to listOf("파파존스", "파파존스피자", "papa johns"),
            "청년피자" to listOf("청년피자", "youngman pizza"),
            "노랑통닭" to listOf("노랑통닭", "norang tongdak"),
            "지코바" to listOf("지코바", "지코바치킨", "gcova"),
            "처갓집양념치킨" to listOf("처갓집", "처갓집치킨", "cheogajip")
        )
        required.forEach { (brand, aliases) ->
            val menus = FranchiseCatalog.officialMenus(brand)
            assertTrue("No actual official menu for $brand", menus.isNotEmpty())
            assertTrue(menus.all { it.name.isNotBlank() && it.sourceUrl.startsWith("http") && it.verifiedAt.isNotBlank() })
            aliases.forEach { alias ->
                assertEquals(alias, brand, FranchiseCatalog.canonicalBrand(alias))
                assertTrue(alias, repository.searchFranchiseBrands(alias).first().any { it.brand == brand && it.productCount > 0 })
                assertEquals(alias, menus.map { it.id }, FranchiseCatalog.officialMenus(brand, alias).map { it.id })
            }
        }
        val pizzas = repository.searchFranchiseBrandsByCategory("피자").first().map { it.brand }.toSet()
        val chickens = repository.searchFranchiseBrandsByCategory("치킨").first().map { it.brand }.toSet()
        assertTrue(pizzas.containsAll(setOf("피자스쿨", "파파존스", "청년피자")))
        assertTrue(chickens.containsAll(setOf("노랑통닭", "지코바", "처갓집양념치킨")))
        // Every returned brand needs an actual dish in the requested category.
        listOf("피자" to pizzas, "치킨" to chickens).forEach { (category, brands) ->
            brands.forEach { brand ->
                val foods = repository.searchFranchiseFoods(brand, "").first()
                assertTrue("$brand has no actual $category dish", foods.any { FoodMenuCategoryPolicy.matches(it, category) } ||
                    FranchiseCatalog.officialMenus(brand).any { FoodMenuCategoryPolicy.matches(it, category) })
            }
        }
    }

    @Test fun generatedRetailCardsShowNamedAmountsAt360DpAndAppScale130() = runBlocking {
        val foods = listOf(requireFood(CHAPAGETTI_ID)) + ICE_PACKAGES.map { requireFood(it.id) }
        var state by mutableStateOf(searchState(foods.first()))
        compact { search(state) }
        foods.forEach { food ->
            compose.runOnIdle { state = searchState(food) }
            scrollTo(hasTestTag("food-search-result-${food.id}"))
            compose.onNodeWithTag("food-search-result-${food.id}").assertIsDisplayed()
                .assert(hasText(PortionGuide.resultServingSummary(food), substring = true))
            compose.onNodeWithText("0 kcal").assertDoesNotExist()
            compose.onNodeWithTag("allergy-notice-UNRESOLVED").assertDoesNotExist()
        }
    }

    @Test fun actualPizzaAndChickenMenusRespondToVisibleCategoryFilters() = runBlocking {
        val pizzaBrand = "피자스쿨"
        val pizzaFoods = repository.searchFranchiseFoods(pizzaBrand, "").first()
        val pizza = pizzaFoods.first { FoodMenuCategoryPolicy.matches(it, "피자") }
        val pizzaMenus = FranchiseCatalog.officialMenus(pizzaBrand)
        val pizzaOther = pizzaMenus.first { !FoodMenuCategoryPolicy.matches(it, "피자") && FoodMenuCategoryPolicy.categoryOf(it) != null }
        val otherCategory = requireNotNull(FoodMenuCategoryPolicy.categoryOf(pizzaOther))
        var state by mutableStateOf(brandState(pizzaBrand, pizzaFoods, listOf("피자", otherCategory), "피자"))
        var selectedMenu: FranchiseMenu? = null
        compact { search(state, onCategory = { state = state.copy(selectedBrandCategory = it) },
            onMenu = { selectedMenu = it }) }
        scrollTo(hasTestTag("food-search-result-${pizza.id}"))
        compose.onNodeWithTag("food-search-result-${pizza.id}").assertIsDisplayed()
        compose.onNodeWithTag("franchise-menu-${pizzaOther.id}").assertDoesNotExist()
        scrollTo(hasText(otherCategory) and hasClickAction())
        compose.onNode(hasText(otherCategory) and hasClickAction()).performClick()
        scrollTo(hasTestTag("franchise-menu-${pizzaOther.id}"))
        compose.onNodeWithTag("franchise-menu-${pizzaOther.id}").assertIsDisplayed()
        compose.onNodeWithTag("food-search-result-${pizza.id}").assertDoesNotExist()

        val chickenBrand = "노랑통닭"
        val chickenFoods = repository.searchFranchiseFoods(chickenBrand, "").first()
        val chickenMenus = FranchiseCatalog.officialMenus(chickenBrand)
        val chicken = chickenMenus.first { FoodMenuCategoryPolicy.matches(it, "치킨") }
        val chickenOther = chickenMenus.first { !FoodMenuCategoryPolicy.matches(it, "치킨") && FoodMenuCategoryPolicy.categoryOf(it) != null }
        val sideCategory = requireNotNull(FoodMenuCategoryPolicy.categoryOf(chickenOther))
        compose.runOnIdle { state = brandState(chickenBrand, chickenFoods, listOf("치킨", sideCategory), "치킨") }
        scrollTo(hasTestTag("franchise-menu-${chicken.id}"))
        compose.onNodeWithTag("franchise-menu-${chicken.id}").assertIsDisplayed()
        compose.onNodeWithTag("franchise-menu-${chickenOther.id}").assertDoesNotExist()
        compose.onNodeWithTag("franchise-menu-${chicken.id}").performClick()
        assertEquals(chicken.id, selectedMenu?.id)
        assertNull(selectedMenu?.energyKcal)
        scrollTo(hasText(sideCategory) and hasClickAction())
        compose.onNode(hasText(sideCategory) and hasClickAction()).performClick()
        scrollTo(hasTestTag("franchise-menu-${chickenOther.id}"))
        compose.onNodeWithTag("franchise-menu-${chickenOther.id}").assertIsDisplayed()
        compose.onNodeWithTag("franchise-menu-${chicken.id}").assertDoesNotExist()
        compose.onNodeWithText("0 kcal").assertDoesNotExist()
    }

    @Test fun actualChapagettiBagRecordDetailAndEditKeep610KcalAndLabelMacros() {
        // Read the source row from the once-seeded real bundle; recording stays
        // in a separate empty in-memory table so no other fixture records change.
        val food = runBlocking { requireFood(CHAPAGETTI_ID) }
        val recordDatabase = Room.inMemoryDatabaseBuilder(fixture, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val models = ViewModelStore()
        val meals = MealRepository(recordDatabase.mealRecordDao())
        val nutrition = NutritionRepository(recordDatabase.foodItemDao())
        val add = AddRecordViewModel(meals, FoodRepository(recordDatabase.frequentFoodDao()), nutritionRepository = nutrition)
        val history = HistoryViewModel(meals, GoalRepository(recordDatabase.calorieGoalDao()),
            EnergyProfileRepository(recordDatabase.energyProfileDao()), nutritionRepository = nutrition)
        fun onlyRecord() = runBlocking { recordDatabase.mealRecordDao().getAllMeals().first().single() }
        fun hideKeyboard() {
            compose.runOnIdle {
                val activity = compose.activity
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            }
            compose.waitForIdle()
        }
        fun assertLabelSnapshot(record: MealRecord) {
            assertEquals(food.id, record.foodItemId)
            assertEquals(610, record.calories)
            assertEquals("1봉", record.portionDisplayLabel)
            assertEquals(140.0, record.servingAmount!!, 0.0)
            assertEquals("g", record.servingUnit)
            assertEquals(96.0, record.carbohydrateGrams!!, 0.0)
            assertEquals(9.0, record.proteinGrams!!, 0.0)
            assertEquals(20.0, record.fatGrams!!, 0.0)
            val snapshot = requireNotNull(RecordedAmountSnapshot.from(record))
            assertEquals(1.0, snapshot.quantity, 0.0)
            assertEquals("봉", snapshot.unit)
            assertEquals(140.0, snapshot.basisPerUnit, 0.0)
        }
        try {
            runBlocking { recordDatabase.foodItemDao().upsertAll(listOf(food)) }
            compose.activityRule.scenario.onActivity {
                models.put("catalog-bag-add", add)
                models.put("catalog-bag-history", history)
                add.onMealTypeChange(MealType.LUNCH)
                add.selectSearchFood(food)
            }
            var stage by mutableStateOf(0)
            var displayed by mutableStateOf<MealRecord?>(null)
            var saved = false
            var edited = false
            compact {
                when (stage) {
                    1 -> HistoryDetailPane(requireNotNull(displayed), onEdit = {
                        history.startEditing(requireNotNull(displayed)); stage = 2
                    }, onDelete = {}, onBack = {})
                    2 -> {
                        val state by history.editState.collectAsState()
                        HistoryEditPane(state = state, onFoodNameChange = history::onEditFoodNameChange,
                            onCaloriesChange = history::onEditCaloriesChange, onMealTypeChange = history::onEditMealTypeChange,
                            onDateChange = history::onEditDateChange, onTimeChange = history::onEditTimeChange,
                            onServingAmountChange = history::onEditServingAmountChange, onServingUnitChange = history::onEditServingUnitChange,
                            onQuantityChange = history::onEditQuantityChange, onQuantityUnitChange = history::onEditQuantityUnitChange,
                            onPortionRatio = history::selectEditRatio, onMemoChange = history::onEditMemoChange,
                            onSave = { history.saveMealEdit { edited = true } }, onBack = {})
                    }
                    else -> {
                        val state by add.uiState.collectAsState()
                        QuickFoodRecordScreen(state, emptyList(), emptyList(), true, {}, {}, { saved = true }, add)
                    }
                }
            }
            scrollTo(hasTestTag("food-amount-quantity"))
            compose.onNodeWithTag("food-amount-quantity").assertTextContains("1")
            compose.onNodeWithTag("food-amount-unit").assertTextContains("봉", substring = true)
            scrollTo(hasText("610 kcal"))
            compose.onNodeWithText("610 kcal").assertIsDisplayed()
            scrollTo(hasText("점심에 기록"))
            compose.onNodeWithText("점심에 기록").performClick()
            compose.waitUntil(5_000) { saved && runBlocking { recordDatabase.mealRecordDao().getAllMeals().first().size == 1 } }
            val original = onlyRecord()
            assertLabelSnapshot(original)
            compose.runOnIdle { displayed = original; stage = 1 }
            scrollTo(hasText("1봉"))
            compose.onAllNodesWithText("1봉").onFirst().assertIsDisplayed()
            listOf("610", "96g", "9g", "20g").forEach { value ->
                scrollTo(hasText(value)); compose.onNodeWithText(value).assertIsDisplayed()
            }
            compose.onNodeWithTag("history-detail-edit").performClick()
            scrollTo(hasTestTag("food-amount-quantity"))
            compose.onNodeWithTag("food-amount-quantity").assertTextContains("1")
            compose.onNodeWithTag("food-amount-unit").assertTextContains("봉", substring = true)
            compose.onNodeWithTag("food-amount-quantity").performTextReplacement("0.5")
            hideKeyboard()
            scrollTo(hasTestTag("history-edit-calorie-preview"))
            compose.onNodeWithTag("history-edit-calorie-preview").assertTextContains("305 kcal", substring = true)
            listOf("48g", "4.5g", "10g").forEach { value ->
                scrollTo(hasText(value)); compose.onNodeWithText(value).assertIsDisplayed()
            }
            scrollTo(hasTestTag("food-amount-quantity"))
            compose.onNodeWithTag("food-amount-quantity").performTextReplacement("1")
            hideKeyboard()
            scrollTo(hasTestTag("history-edit-calorie-preview"))
            compose.onNodeWithTag("history-edit-calorie-preview").assertTextContains("610 kcal", substring = true)
            listOf("96g", "9g", "20g").forEach { value ->
                scrollTo(hasText(value)); compose.onNodeWithText(value).assertIsDisplayed()
            }
            scrollTo(hasTestTag("history-edit-save"))
            compose.onNodeWithTag("history-edit-save").performClick()
            compose.waitUntil(5_000) { edited }
            val updated = onlyRecord()
            assertEquals(original.id, updated.id)
            assertLabelSnapshot(updated)
            compose.runOnIdle { displayed = updated; stage = 1 }
            scrollTo(hasText("1봉"))
            compose.onAllNodesWithText("1봉").onFirst().assertIsDisplayed()
        } finally {
            compose.activityRule.scenario.onActivity { models.clear() }
            recordDatabase.close()
        }
    }

    private fun saveFullAuditScreen(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val output=java.io.File(instrumentation.targetContext.cacheDir,"full-audit-$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        bitmap.recycle()
    }

    private fun compact(content: @Composable () -> Unit) {
        compose.setContent {
            HealthCareTheme {
                val device = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(device.density, 1.30f)) {
                    Box(Modifier.width(360.dp).height(720.dp)) { content() }
                }
            }
        }
    }

    private fun scrollTo(matcher: SemanticsMatcher) = compose.onAllNodes(hasScrollAction()).onFirst()
        .performScrollToNode(matcher)

    @Composable private fun search(state: SmartInputUiState, onCategory: (String?) -> Unit = {},
        onMenu: (FranchiseMenu) -> Unit = {}) {
        SmartFoodInputScreen(state = state, recentMeals = emptyList(), onBack = {}, onPhoto = {},
            onBarcode = {}, onNutritionLabel = {}, onSearch = {}, onSearchQueryChange = {}, onFoodSelected = {},
            onUseBarcodeItem = {}, onOcrCandidateSelected = {}, onOcrAmountChange = {}, onConfirmOcr = {},
            onManual = {}, onRegisterBarcode = {}, onRepeatRecent = {}, onBrandCategorySelected = onCategory,
            onOfficialMenuSelected = onMenu, configuredAllergies = emptySet())
    }

    private fun searchState(food: FoodItem) = SmartInputUiState(mode = SmartInputMode.SEARCH,
        searchQuery = food.name, searchResults = listOf(food))

    private fun brandState(brand: String, foods: List<FoodItem>, categories: List<String>, selected: String) =
        SmartInputUiState(mode = SmartInputMode.SEARCH, searchMode = FoodSearchMode.FRANCHISE,
            selectedBrand = FoodBrandSummary(brand, foods.size + FranchiseCatalog.officialMenus(brand).size),
            brandProducts = foods, brandCategories = categories, selectedBrandCategory = selected)

    companion object {
        private const val CHAPAGETTI_ID = "official-retail-nongshim-p0000dyw-single-140g"
        private data class IcePackage(val id: String, val unit: String, val basis: Double,
            val packageAmount: Double, val calories: Int)
        private val ICE_PACKAGES = listOf(
            IcePackage("official-retail-binggrae-pack-21", "개", 75.0, 75.0, 120),
            IcePackage("kfind-product-p102-101010100-0029", "콘", 100.0, 160.0, 306),
            IcePackage("official-retail-binggrae-pack-87", "컵", 110.0, 110.0, 225),
            IcePackage("official-retail-binggrae-pack-1", "통", 100.0, 900.0, 900)
        )
        private lateinit var database: AppDatabase
        private lateinit var repository: NutritionRepository
        private lateinit var instrumentationContext: Context
        private lateinit var fixture: SeedContext
        private var originalMetadata: Collection<FoodMetadata> = emptyList()

        @BeforeClass @JvmStatic fun seedBundledAssetsOnce() = runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentationContext = instrumentation.context
            fixture = SeedContext(instrumentation.targetContext, instrumentationContext)
            originalMetadata = FoodMetadataPolicy.snapshot()
            val store = FoodMetadataStore(fixture)
            store.ensureLoaded()
            database = Room.inMemoryDatabaseBuilder(fixture, AppDatabase::class.java).build()
            repository = NutritionRepository(database.foodItemDao(), metadataLoader = store::ensureLoaded)
            val result = BundledFoodDataSeeder(fixture, database).seedIfAvailable()
            assertTrue("Real bundle could not seed: $result", result is BundledFoodDataSeeder.SeedResult.Seeded)
            val count = (result as BundledFoodDataSeeder.SeedResult.Seeded).foodCount
            assertEquals(count, repository.count())
            assertTrue("Expanded products not packaged in APK", count > 65000)
        }

        @AfterClass @JvmStatic fun closeIsolatedFixture() {
            if (::database.isInitialized) database.close()
            if (::fixture.isInitialized) fixture.clearPreferences()
            FoodMetadataPolicy.install(originalMetadata)
        }

        private suspend fun requireFood(id: String): FoodItem = requireNotNull(repository.findById(id)) { "No seeded $id" }

        private fun testRows(path: String): List<Map<String, String>> =
            instrumentationContext.assets.open(path).bufferedReader().use { reader ->
                val headers = FoodMetadataStore.csv(requireNotNull(reader.readLine()))
                reader.lineSequence().filter(String::isNotBlank).map { line ->
                    val values = FoodMetadataStore.csv(line)
                    require(values.size == headers.size)
                    headers.zip(values).toMap()
                }.toList()
            }

        private fun csvField(value: String) = "\"${value.replace("\"", "\"\"")}\""
    }

    /** Assets come from the app; seed preferences belong only to the test APK. */
    private class SeedContext(base: Context, private val testContext: Context) : ContextWrapper(base) {
        private val prefix = "catalog_expansion_${System.nanoTime()}_"
        private val names = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val fixtureName = prefix + name
            names += fixtureName
            return testContext.getSharedPreferences(fixtureName, mode)
        }
        fun clearPreferences() {
            names.forEach { name ->
                testContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                testContext.deleteSharedPreferences(name)
            }
        }
    }
}
