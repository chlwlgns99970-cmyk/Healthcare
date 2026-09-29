package com.example.healthcare

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.seed.BundledFoodDataSeeder
import com.example.healthcare.data.RecommendationCycleSnapshot
import com.example.healthcare.data.SharedPreferencesRecommendationCycleStore
import com.example.healthcare.data.repository.NutritionRepository
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.FoodBrowseCategory
import com.example.healthcare.domain.FranchiseCatalog
import com.example.healthcare.ui.RecommendationImageResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BundledFoodDataTest {
    @Test
    fun officialKfindFoodsAndRecommendationTemplatesAreSeeded() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val database = AppDatabase.getDatabase(context)

        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT ||
                database.mealCoachDao().templateCount() < EXPECTED_TEMPLATE_COUNT) {
                delay(100)
            }
        }

        val officialFoodCount = scalar(
            database,
            "SELECT COUNT(*) FROM food_items WHERE sourceType = 'K-FIND' AND dataVersion = '2026-08-28'"
        )
        val officialBarcodeCount = scalar(
            database,
            "SELECT COUNT(*) FROM food_items WHERE sourceType = 'K-FIND' AND barcode IS NOT NULL"
        )
        val officialProductCount = scalar(
            database,
            "SELECT COUNT(*) FROM food_items WHERE sourceType = 'K-FIND-PRODUCT' AND dataVersion = '2026-08-28'"
        )
        val templateCount = scalar(database, "SELECT COUNT(*) FROM meal_templates WHERE source LIKE '%K-FIND%'")
        val ingredientCount = scalar(
            database,
            "SELECT COUNT(*) FROM meal_template_ingredients WHERE mealTemplateId LIKE 'kfind-%'"
        )

        assertEquals(EXPECTED_KFIND_FOOD_COUNT, officialFoodCount)
        assertEquals(EXPECTED_BASIC_FOOD_COUNT, scalar(
            database,
            "SELECT COUNT(*) FROM food_items WHERE sourceType = 'USDA-SR-LEGACY'"
        ))
        assertEquals(EXPECTED_KFIND_PRODUCT_COUNT, officialProductCount)
        assertEquals(0, officialBarcodeCount)
        assertEquals(EXPECTED_TEMPLATE_COUNT, templateCount)
        assertEquals(EXPECTED_TEMPLATE_COUNT, ingredientCount)
        val templates = database.openHelper.readableDatabase.query(
            "SELECT id, name, tags, allergens, proteinGrams, carbohydrateGrams, fatGrams FROM meal_templates"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(List(7) { index -> if (cursor.isNull(index)) "" else cursor.getString(index) })
                }
            }
        }
        assertEquals(EXPECTED_TEMPLATE_COUNT, templates.map { it[0] }.distinct().size)
        assertEquals(EXPECTED_TEMPLATE_COUNT, templates.map {
            com.example.healthcare.domain.MealRecommendationEngine.normalizeFoodName(it[1])
        }.distinct().size)
        assertTrue(templates.all { "INGREDIENTS_COMPLETE" in it[2] || "INGREDIENTS_INCOMPLETE" in it[2] })
        assertTrue(templates.filter { "INGREDIENTS_INCOMPLETE" in it[2] }
            .all { it[3].isNotBlank() })
        val grilledChickenSalad = templates.first { it[0] == "kfind-catalog-d214-640000000-0002" }
        listOf("밀", "우유", "대두", "달걀", "토마토", "닭고기").forEach { allergen ->
            assertTrue("official allergen $allergen should be retained", allergen in grilledChickenSalad[3])
        }
        val macroCompleteCount = templates.count {
            it[4].isNotBlank() && it[5].isNotBlank() && it[6].isNotBlank()
        }
        assertEquals(EXPECTED_MACRO_COMPLETE_COUNT, macroCompleteCount)
        assertEquals(
            EXPECTED_INGREDIENT_COMPLETE_COUNT,
            templates.count { "INGREDIENTS_COMPLETE" in it[2] }
        )
        assertEquals(
            EXPECTED_CONFIRMED_ALLERGEN_COUNT,
            templates.count { it[3].isNotBlank() && "UNKNOWN" !in it[3] }
        )
        val explicitImages = templates.count { RecommendationImageResolver.resolve(it[0], it[1]) != null }
        val neutralPlaceholders = templates.size - explicitImages
        assertEquals(EXPECTED_TEMPLATE_COUNT, explicitImages)
        assertEquals(0, neutralPlaceholders)
        assertEquals(EXPECTED_TEMPLATE_COUNT, RecommendationImageResolver.mappedTemplateIds().size)
        listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK").forEach { mealType ->
            val count = scalar(
                database,
                "SELECT COUNT(*) FROM meal_templates WHERE supportedMealTypes LIKE '%|$mealType|%'"
            )
            assertTrue("$mealType should have at least three recommendations", count >= 3)
        }

        val searchResults = database.foodItemDao().observeSearch("김밥", 30).first()
        assertTrue(searchResults.isNotEmpty())
        assertTrue(searchResults.all(FoodSearchPolicy::isOfficialKfind))
        assertTrue(searchResults.all { it.energyKcal >= 0.0 && it.referenceAmount > 0.0 })
    }

    @Test
    fun recommendationSeenStateSurvivesStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val scope = "TEST_${System.nanoTime()}"
        val expected = RecommendationCycleSnapshot(setOf("meal-1", "meal-2"), "meal-2")
        SharedPreferencesRecommendationCycleStore(context).write(scope, expected)

        assertEquals(expected, SharedPreferencesRecommendationCycleStore(context).read(scope))
    }

    @Test
    fun tunaGimbapSearchFindsSourceRowsAndRanksGramBasisBeforeVolume() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val results = NutritionRepository(database.foodItemDao()).search("참치김밥").first()
        assertTrue(results.isNotEmpty())
        assertEquals("참치김밥", FoodSearchPolicy.normalize(results.first().name))
        assertTrue(results.any { it.name == "김밥_참치" && it.unit == "g" })
        assertTrue(results.any { it.name == "김밥_참치" && it.unit == "ml" })
        assertEquals("g", results.first { it.name == "김밥_참치" }.unit)
        assertTrue(results.filter(FoodSearchPolicy::needsBasisReview).all { it.unit == "ml" })
        val exactBrands = results.filter { FoodSearchPolicy.normalize(it.name) == "참치김밥" }
            .mapNotNull { it.brand }
        assertTrue(exactBrands.distinct().size > 1)
        assertEquals(results.map { it.id }, NutritionRepository(database.foodItemDao())
            .search("참치 김밥").first().map { it.id })
    }

    @Test
    fun seaweedSoupKeepsOfficialMassVolumeAndOriginVariantsSeparate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val results = NutritionRepository(database.foodItemDao()).search("미역국").first()
        val plain = results.filter { it.name == "미역국" }
        assertTrue(plain.any { it.unit == "g" && it.energyKcal == 12.0 })
        assertTrue(plain.any { it.unit == "ml" && it.energyKcal == 7.0 })
        assertTrue(plain.any { it.unit == "ml" && it.energyKcal == 10.0 })
        assertTrue(plain.any { it.unit == "ml" && it.energyKcal == 11.0 })
        assertEquals(plain.map { it.id }.size, plain.map { it.id }.distinct().size)

        val driedPollack = results.filter { it.name == "미역국_북어" }
        assertTrue(driedPollack.isNotEmpty())
        assertTrue(driedPollack.all { FoodSearchPolicy.displayName(it) == "북어미역국" })
        assertTrue(driedPollack.mapNotNull(FoodSearchPolicy::sourceVariantLabel).distinct().size >= 2)
    }

    @Test
    fun commonKoreanFoodQueriesHaveOfficialCandidates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val repository = NutritionRepository(database.foodItemDao())
        listOf(
            "김밥", "참치김밥", "참치 김밥", "비빔밥", "쫄면", "라면", "된장찌개",
            "계란", "달걀", "닭가슴살", "닭 가슴살", "사과", "바나나",
            "식빵", "우유", "떡볶이", "불고기", "삼겹살"
        ).forEach { query ->
            assertTrue("No official candidate for $query", repository.search(query).first().isNotEmpty())
        }
    }

    @Test
    fun basicFruitVegetableAliasesAndCategoryBrowseUseOfficialRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val repository = NutritionRepository(database.foodItemDao())
        listOf("사과", "바나나", "딸기", "포도", "오렌지", "수박", "복숭아", "배").forEach { query ->
            assertTrue("No basic fruit for $query", repository.search(query).first().any {
                it.sourceType == "USDA-SR-LEGACY"
            })
        }
        listOf("양배추", "상추", "토마토", "오이").forEach { query ->
            assertTrue("No basic vegetable for $query", repository.search(query).first().any {
                it.sourceType == "USDA-SR-LEGACY"
            })
        }
        assertTrue(repository.search("과일").first().any { it.name.startsWith("사과_") })
        assertTrue(repository.search("야채").first().any { it.name.startsWith("양배추_") })
        val fruitBrowse = repository.browse(FoodBrowseCategory.FRUIT, "").first()
        assertTrue("Basic apple missing from fruit browse", fruitBrowse.any {
            it.id == "usda-sr-171688"
        })
        assertTrue(fruitBrowse.all {
            FoodSearchPolicy.matchesCategory(it, FoodBrowseCategory.FRUIT)
        })
        assertTrue(repository.browse(FoodBrowseCategory.VEGETABLE, "오이").first().any {
            it.name.startsWith("오이_")
        })
        assertTrue(repository.search("과자").first().isNotEmpty())
    }

    @Test
    fun broadFoodSearchExposesOnlyConcreteTypesAndBrandsPresentInKfind() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val repository = NutritionRepository(database.foodItemDao())
        val ramen = repository.search("라면").first()
        val burger = repository.search("햄버거").first()
        val pizza = repository.search("피자").first()
        val chicken = repository.search("치킨").first()
        val cola = repository.search("코카콜라").first()
        val gimbap = repository.search("김밥").first()
        val rice = repository.search("밥").first()

        assertTrue(ramen.any { it.sourceType == BundledFoodDataSeeder.SOURCE_TYPE_KFIND_PRODUCT })
        assertTrue(ramen.any { !it.brand.isNullOrBlank() })
        assertTrue(NutritionRepository(database.foodItemDao()).search("신라면").first()
            .any { it.name == "신라면" && it.brand?.contains("농심") == true })
        assertTrue(NutritionRepository(database.foodItemDao()).search("진라면").first()
            .any { it.name.contains("진라면") && it.brand?.contains("오뚜기") == true })
        assertTrue(burger.any { it.sourceType == BundledFoodDataSeeder.SOURCE_TYPE_KFIND_PRODUCT })
        assertTrue(pizza.any { it.sourceType == BundledFoodDataSeeder.SOURCE_TYPE_KFIND_PRODUCT })
        assertTrue(chicken.any { it.sourceType == BundledFoodDataSeeder.SOURCE_TYPE_KFIND_PRODUCT })
        assertTrue(cola.any { it.sourceType == BundledFoodDataSeeder.SOURCE_TYPE_KFIND_PRODUCT })
        assertTrue(gimbap.isNotEmpty())
        assertTrue(rice.isNotEmpty())
        assertTrue((ramen + burger + pizza + chicken + cola + gimbap + rice)
            .all { it.referenceAmount > 0.0 && it.unit in setOf("g", "ml") })
    }

    @Test
    fun manufacturerBrowseUsesOfficialNamesAndKeepsProductsInsideSelectedCompany() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val repository = NutritionRepository(database.foodItemDao())
        val matchingBrands = repository.searchProductBrands("농심").first()
        val brand = matchingBrands.first { it.brand == "(주)농심" }
        assertEquals(54, brand.productCount)
        assertEquals(3, matchingBrands.first { it.brand == "㈜농심" }.productCount)

        val products = repository.searchProductsByBrand(brand.brand, "").first()
        assertEquals(brand.productCount, products.size)
        assertTrue(products.all { it.brand == brand.brand })
        assertTrue(products.all { it.sourceType == BundledFoodDataSeeder.SOURCE_TYPE_KFIND_PRODUCT })

        val withinBrand = repository.searchProductsByBrand(brand.brand, "신라면").first()
        assertTrue(withinBrand.isNotEmpty())
        assertTrue(withinBrand.all { it.brand == brand.brand && it.name.contains("신라면") })

        val generalSearch = repository.search("농심").first()
        assertTrue(generalSearch.any { it.brand == brand.brand })
        assertTrue(repository.search("밥").first().any { it.sourceType == BundledFoodDataSeeder.SOURCE_TYPE_KFIND })
    }

    @Test
    fun franchiseBrowseSeparatesOfficialMenusAndGeneralSearchFindsBrandAndMenu() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val repository = NutritionRepository(database.foodItemDao())
        val brands = repository.searchFranchiseBrands("").first()

        assertEquals(FranchiseCatalog.brands.toSet(), brands.map { it.brand }.toSet())
        assertEquals(EXPECTED_FRANCHISE_FOOD_COUNT, brands.sumOf { it.productCount })
        val mcdonalds = brands.first { it.brand == "맥도날드" }
        assertEquals(37, mcdonalds.productCount)

        val menus = repository.searchFranchiseFoods(mcdonalds.brand, "").first()
        assertEquals(mcdonalds.productCount, menus.size)
        assertTrue(menus.all(FranchiseCatalog::isFranchise))
        assertTrue(menus.all { it.brand == "맥도날드" && it.dataVersion == "2026-08-28" })
        assertTrue(repository.search("맥도날드").first().any { it.brand == "맥도날드" })
        assertTrue(repository.search("빅맥").first().any { it.brand == "맥도날드" && it.name.contains("빅맥") })

        val expectedOfficialCounts = mapOf(
            "BBQ" to 4,
            "네네치킨" to 9,
            "메가MGC커피" to 9,
            "매머드커피" to 8
        )
        expectedOfficialCounts.forEach { (brandName, expectedCount) ->
            val brandMenus = repository.searchFranchiseFoods(brandName, "").first()
            assertEquals(expectedCount, brandMenus.size)
            assertTrue(brandMenus.all { it.sourceType == "OFFICIAL-BRAND-NUTRITION" })
        }
        assertTrue(repository.search("황금올리브").first().any { it.brand == "BBQ" })
        assertTrue(repository.search("메가커피").first().any { it.brand == "메가MGC커피" })
        assertTrue(repository.search("매머드익스프레스").first().any { it.brand == "매머드커피" })
        assertEquals("BBQ", FranchiseCatalog.canonicalBrand("비비큐"))
        assertEquals("네네치킨", FranchiseCatalog.canonicalBrand("네네"))
        assertEquals("메가MGC커피", FranchiseCatalog.canonicalBrand("메가MGC"))
        assertEquals("매머드커피", FranchiseCatalog.canonicalBrand("메머드커피"))
    }

    @Test
    fun sameDateHomeRecommendationIsCreatedOnceAndThenReused() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val repository = MealCoachRepository(
            database,
            database.mealCoachDao(),
            database.foodItemDao(),
            database.mealRecordDao()
        )
        val preference = repository.ensureDefaultPreference()
        val date = LocalDate.now()
        val first = repository.getOrCreateTodayRecommendation(
            date, 2_000, preference, MealType.LUNCH, 600
        )
        val second = repository.getOrCreateTodayRecommendation(
            date, 2_000, preference, MealType.LUNCH, 600
        )

        assertTrue(first != null)
        assertEquals(first?.id, second?.id)
        val (_, plannedMeals) = repository.ensureDailyPlan(date, 2_000, preference)
        assertEquals(
            first?.id,
            plannedMeals.first { it.mealType == MealType.LUNCH.name }.selectedTemplateId
        )
    }

    @Test
    fun everyBundledSolidVolumeRowIsFlaggedAndGramAlternativeSortsFirst() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<HealthcareApplication>()
        assertEquals("com.example.healthcare.qa", context.packageName)
        val database = AppDatabase.getDatabase(context)
        withTimeout(180_000) {
            while (database.foodItemDao().count() < EXPECTED_TOTAL_FOOD_COUNT) delay(100)
        }
        val volumeIds = database.openHelper.readableDatabase.query(
            "SELECT id FROM food_items WHERE sourceType = 'K-FIND' AND lower(unit) = 'ml'"
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val volumeFoods = volumeIds.mapNotNull { database.foodItemDao().findById(it) }
        assertEquals(volumeIds.size, volumeFoods.size)
        assertEquals(2_382, volumeFoods.count(FoodSearchPolicy::needsBasisReview))
        val tuna = NutritionRepository(database.foodItemDao()).search("참치김밥").first()
        assertTrue(tuna.any { it.name == "김밥_참치" && it.unit == "ml" && FoodSearchPolicy.needsBasisReview(it) })
        assertEquals("g", tuna.first { it.name == "김밥_참치" }.unit)
    }

    private fun scalar(database: AppDatabase, sql: String): Int =
        database.openHelper.readableDatabase.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val EXPECTED_KFIND_FOOD_COUNT = 19_617
        const val EXPECTED_BASIC_FOOD_COUNT = 12
        const val EXPECTED_KFIND_PRODUCT_COUNT = 11_921
        const val EXPECTED_OFFICIAL_FRANCHISE_FOOD_COUNT = 30
        const val EXPECTED_TOTAL_FOOD_COUNT = EXPECTED_KFIND_FOOD_COUNT + EXPECTED_BASIC_FOOD_COUNT +
            EXPECTED_KFIND_PRODUCT_COUNT + EXPECTED_OFFICIAL_FRANCHISE_FOOD_COUNT
        const val EXPECTED_TEMPLATE_COUNT = 292
        const val EXPECTED_MACRO_COMPLETE_COUNT = 261
        const val EXPECTED_INGREDIENT_COMPLETE_COUNT = 36
        const val EXPECTED_CONFIRMED_ALLERGEN_COUNT = 33
        const val EXPECTED_FRANCHISE_FOOD_COUNT = 2_440
    }
}
