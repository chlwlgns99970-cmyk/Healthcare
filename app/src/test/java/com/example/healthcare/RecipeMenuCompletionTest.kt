package com.example.healthcare

import com.example.healthcare.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RecipeMenuCompletionTest {
    @After fun reset() { RecipeCaloriePolicy.install(emptyList()) }
    private fun row(complete: Boolean) = RecipeIngredientEstimate("food","MFDS-247","알감자구이","4인분 전체",
        "감자 520g, 소금 2g","감자",520.0,"fdc-reference-170026","Potatoes, flesh and skin, raw",77.0,
        "https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=247",
        "https://fdc.nal.usda.gov/food-details/170026/nutrients","a".repeat(64),"2026-10-04",complete)
    @Test fun completeAndPartialReferencesUseOnlyInputNutrition() {
        RecipeCaloriePolicy.install(listOf(row(true)))
        assertEquals(400.4,RecipeCaloriePolicy.lookup("food").single().estimatedKcal,0.00001)
        assertTrue(RecipeCaloriePolicy.lookup("food").single().recipeComplete)
        assertTrue(RecipeCaloriePolicy.lookup("unknown").isEmpty())
        RecipeCaloriePolicy.install(listOf(row(false)))
        assertFalse(RecipeCaloriePolicy.lookup("food").single().recipeComplete)
    }
    @Test fun incompleteOrConflictingEvidenceCannotBeInstalled() {
        listOf(row(true).copy(amountGrams=Double.NaN),row(true).copy(nutrientFoodId=""),row(true).copy(amountGrams=0.0)).forEach {
            try { RecipeCaloriePolicy.install(listOf(it));fail("Invalid reference accepted") } catch (_: IllegalArgumentException) {}
        }
        try {
            RecipeCaloriePolicy.install(listOf(row(true),row(false).copy(ingredientName="소금",nutrientFoodId="salt")))
            fail("Conflicting completeness accepted")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun publishedNolbooMenusKeepMissingNutritionNullAndStoreScopeExplicit() {
        val menus=FranchiseCatalog.officialMenus("놀부부대찌개")
        assertEquals(24,menus.size)
        assertTrue(menus.all { it.energyKcal==null && it.servingAmount==null && it.servingUnit==null })
        val store=menus.filter { it.saleState=="PUBLIC_TOURISM_STORE_MENU" }
        val brand=menus.filter { it.saleState=="OFFICIAL_ORDER_BRAND_SALES_UNVERIFIED" }
        assertEquals(7,store.size);assertEquals(17,brand.size)
        assertTrue(store.all { it.sourceDescription.contains("공공 관광 매장 메뉴") && it.sourceDescription.contains("신길로 39") })
        assertTrue(brand.all { it.sourceDescription.contains("현재 판매 여부 미확인") && it.sourceDescription.contains(it.sourceDate) })
        assertEquals("부대찌개",FoodMenuCategoryPolicy.categoryOf(brand.single { it.name=="놀부세트" }))
        assertNull(FoodMenuCategoryPolicy.categoryOf(brand.single { it.name=="돈곱새 1인" }))
        assertTrue(menus.any { it.name=="고기듬뿍김치찌개" && FoodMenuCategoryPolicy.categoryOf(it)=="한식" })
        assertTrue(store.filter { it.name.contains("세트") }.all { FoodMenuCategoryPolicy.categoryOf(it)==null })
    }
    @Test fun individualDescriptionsClassifyAmbiguousNamesButSetsRemainUnknown() {
        val sandwich=FranchiseMenu("official-quality-eggdrop-1cc56c59a082","에그드랍","아보홀릭","https://eggdrop.com/menu/view.php?seq=50","2026-10-04")
        assertEquals("샌드위치",FoodMenuCategoryPolicy.categoryOf(sandwich))
        assertNull(FoodMenuCategoryPolicy.categoryOf(sandwich.copy(id="unknown-set",name="아보홀릭 세트")))
        assertEquals("한식",FoodMenuCategoryPolicy.categoryOf(sandwich.copy(id="pancake",brand="국수나무",name="15cm 감자전")))
    }
}
