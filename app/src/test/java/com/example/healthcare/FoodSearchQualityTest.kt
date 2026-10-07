package com.example.healthcare

import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.FoodMetadata
import com.example.healthcare.domain.FoodMetadataPolicy
import com.example.healthcare.domain.FoodSearchPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FoodSearchQualityTest {
    @After fun clearEvidence() = FoodMetadataPolicy.install(emptyList())

    private fun food(id: String, name: String = "참치김밥") = FoodItem(id, "K-FIND", id, name,
        FoodSearchPolicy.normalize(name), category = "밥류", referenceAmount = 100.0, unit = "g",
        energyKcal = 150.0, servingDescription = "100g 기준", dataVersion = "isolated", createdAt = 0, updatedAt = 0)

    private fun evidence(id: String, complete: Boolean = true) = FoodMetadata(foodId = id,
        ingredients = setOf("쌀", "참치"), allergens = setOf("생선"), ingredientInfoComplete = complete,
        allergenInfoComplete = complete, sourceReference = "https://official.example/identity/$id", checkedAt = "2026-10-04",
        ingredientStatus = if (complete) "COMPLETE_DECLARATION" else "PARTIAL_SOURCE",
        allergenStatus = if (complete) "CONFIRMED_LABEL" else "PARTIAL_SOURCE")

    @Test fun officialServingPrecedesGenericSourceAndMissingPortion() {
        val generic = food("generic")
        val serving = generic.copy(id = "serving", sourceType = "K-FIND-PRODUCT", sourceFoodCode = "serving",
            servingDescription = "100g 기준 · 공식 총내용량 216g · 포장단위 줄")
        assertEquals("serving", FoodSearchPolicy.groupSearchResults(listOf(generic, serving), "참치김밥").single().representative.id)
    }

    @Test fun basicTofuVariantPrecedesExactNamePackagedProductWithoutHidingIt() {
        val basic = food("basic-tofu", "두부_생것").copy(category = "두류")
        val packaged = food("packaged-tofu", "두부").copy(sourceType = "K-FIND-PRODUCT",
            brand = "검증 제조사", servingDescription = "공식 총내용량 300g · 포장단위 팩")
        val ranked = FoodSearchPolicy.rankedSearchResults(listOf(packaged, basic), "두부")
        assertEquals(basic.id, ranked.first().id)
        assertEquals(setOf(basic.id, packaged.id), ranked.map { it.id }.toSet())
        assertEquals(packaged.id, FoodSearchPolicy.rankedSearchResults(listOf(basic, packaged), "검증 제조사").first().id)
    }

    @Test fun nutritionCompletenessPrecedesMetadataOnlyCompleteness() {
        val onlyMetadata = food("metadata")
        val fullNutrition = food("nutrition").copy(carbohydrateGrams = 20.0, proteinGrams = 8.0, fatGrams = 5.0)
        FoodMetadataPolicy.install(listOf(evidence("metadata")))
        assertEquals("nutrition", FoodSearchPolicy.groupSearchResults(listOf(onlyMetadata, fullNutrition), "참치김밥").single().representative.id)
    }

    @Test fun invalidMacroNumbersDoNotCountAsCompleteButPublishedZeroDoes() {
        val invalid = food("invalid").copy(carbohydrateGrams = Double.NaN, proteinGrams = Double.POSITIVE_INFINITY, fatGrams = -1.0)
        val known = food("known").copy(carbohydrateGrams = 0.0, proteinGrams = 8.0, fatGrams = 0.0)
        assertEquals("known", FoodSearchPolicy.groupSearchResults(listOf(invalid, known), "참치김밥").single().representative.id)
    }

    @Test fun metadataCompletenessIsConsideredBeforeSourceAndStableIds() {
        val bare = food("a")
        val verified = food("z").copy(sourceType = "K-FIND-PRODUCT")
        FoodMetadataPolicy.install(listOf(evidence("z")))
        assertEquals("z", FoodSearchPolicy.groupSearchResults(listOf(bare, verified), "참치김밥").single().representative.id)
    }

    @Test fun fullDeclarationWinsOverPartialMainIngredientEvidence() {
        FoodMetadataPolicy.install(listOf(evidence("partial", false), evidence("complete")))
        assertEquals("complete", FoodSearchPolicy.groupSearchResults(listOf(food("partial"), food("complete")), "참치김밥").single().representative.id)
    }

    @Test fun exactFoodMatchCannotBeOutrankedByRicherDifferentMenu() {
        val exact = food("exact")
        val different = food("different", "참치마요김밥").copy(servingDescription = "공식 총내용량 220g · 포장단위 줄",
            carbohydrateGrams = 25.0, proteinGrams = 10.0, fatGrams = 6.0)
        FoodMetadataPolicy.install(listOf(evidence("different")))
        assertEquals("exact", listOf(different, exact).sortedWith(FoodSearchPolicy.representativeComparator("참치김밥")).first().id)
    }

    @Test fun exactBrandMenusPrecedeUnrelatedFoodThatMentionsBrand() {
        val menu = food("menu", "전복죽").copy(brand = "본죽", sourceType = "OFFICIAL-BRAND-NUTRITION")
        val unrelated = food("unrelated", "본죽맛죽").copy(brand = "다른 제조사")
        assertTrue(FoodSearchPolicy.searchRank(menu, "본죽") < FoodSearchPolicy.searchRank(unrelated, "본죽"))
        assertEquals("menu", listOf(unrelated, menu).sortedWith(FoodSearchPolicy.representativeComparator("본죽")).first().id)
        val aliasMenu = menu.copy(brand = "메가MGC커피", name = "아메리카노", normalizedName = "아메리카노")
        assertEquals(-10, FoodSearchPolicy.searchRank(aliasMenu, "메가커피"))
    }

    @Test fun emptyBrandCardsAreFilteredWithoutRemovingSourceIdentities() {
        val input = listOf(FoodBrandSummary("미확정 브랜드", 0), FoodBrandSummary("본죽", 65))
        assertEquals(listOf("본죽"), FoodSearchPolicy.visibleBrands(input).map { it.brand })
        assertEquals(2, input.size)
    }

    @Test fun repeatedOrderingPreservesEverySourceAndStableTieBreak() {
        val sourceOne = food("b").copy(sourceFoodCode = "same")
        val sourceTwo = sourceOne.copy(id = "a")
        val forward = FoodSearchPolicy.groupSearchResults(listOf(sourceOne, sourceTwo), "참치김밥").single()
        val reverse = FoodSearchPolicy.groupSearchResults(listOf(sourceTwo, sourceOne), "참치김밥").single()
        assertEquals("a", forward.representative.id)
        assertEquals(forward, reverse)
        assertEquals(setOf("a", "b"), (forward.alternatives + forward.representative).map { it.id }.toSet())
    }
}
