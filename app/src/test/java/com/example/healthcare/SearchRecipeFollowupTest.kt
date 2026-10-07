package com.example.healthcare

import com.example.healthcare.domain.*
import org.junit.Assert.*
import org.junit.Test

class SearchRecipeFollowupTest {
    @Test fun all89BrandIndustriesAreCoveredWithoutCrossSellingLeakage() {
        assertEquals(89, FranchiseCatalog.entries.size)
        FranchiseCatalog.entries.forEach { brand ->
            val filters = FranchiseCatalog.brandFilterCategories.filter { FranchiseCatalog.matchesBrandFilter(brand.name, it.label) }
            assertEquals("${brand.name}: ${brand.category}", if (brand.name in setOf("KFC", "본죽&비빔밥")) 2 else 1, filters.size)
        }
        listOf("굽네치킨", "지코바").forEach {
            assertFalse(FranchiseCatalog.matchesBrandFilter(it, "피자"))
            assertTrue(FranchiseCatalog.matchesBrandFilter(it, "치킨"))
        }
        assertTrue(FranchiseCatalog.matchesBrandFilter("피자스쿨", "피자"))
        assertFalse(FranchiseCatalog.matchesBrandFilter("피자스쿨", "치킨"))
        assertFalse(FranchiseCatalog.brandFilterCategories.any { it.label == "사이드" })
    }
    @Test fun recipeCaloriesRequireQuantitiesAndUseIndependentNutritionWithoutBalancingTotal() {
        val row = RecipeIngredientEstimate("food", "MFDS-108", "양상추샐러드", "4인분 전체",
            "오이 120g", "오이", 120.0, "usda-sr-168409", "오이 생것", 15.0,
            "https://www.foodsafetykorea.go.kr/popup/sensuousmenuView.do?rcp_menu_no=108",
            "https://fdc.nal.usda.gov/food-details/168409/nutrients", "a".repeat(64), "2026-10-04")
        try {
            RecipeCaloriePolicy.install(listOf(row))
            assertEquals(18.0, RecipeCaloriePolicy.lookup("food").single().estimatedKcal, 0.00001)
            assertTrue(RecipeCaloriePolicy.lookup("names-only").isEmpty())
            assertThrows(IllegalArgumentException::class.java) { RecipeCaloriePolicy.install(listOf(row.copy(amountGrams = Double.NaN))) }
            assertThrows(IllegalArgumentException::class.java) { RecipeCaloriePolicy.install(listOf(row.copy(nutrientFoodId = ""))) }
        } finally { RecipeCaloriePolicy.install(emptyList()) }
    }
}
