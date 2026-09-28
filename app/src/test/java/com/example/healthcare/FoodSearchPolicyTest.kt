package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.FoodBrowseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchPolicyTest {
    @Test fun spacingPunctuationAndCompatibilityCharactersNormalizeWithoutChangingSourceName() {
        assertEquals("참치김밥", FoodSearchPolicy.normalize(" 참치-김밥 "))
        assertEquals("참치김밥", FoodSearchPolicy.normalize("참치 김밥"))
        assertEquals("abc123", FoodSearchPolicy.normalize("ＡＢＣ－１２３"))
        assertEquals(listOf("참치김밥", "김밥참치"), FoodSearchPolicy.queries("참치 김밥"))
        assertEquals(listOf("계란", "달걀"), FoodSearchPolicy.queries("계란"))
        assertEquals(listOf("닭가슴살"), FoodSearchPolicy.queries("닭 가슴살"))
        assertEquals(listOf("흰밥", "쌀밥"), FoodSearchPolicy.queries("흰밥"))
        assertEquals(listOf("김밥"), FoodSearchPolicy.queries("김밥"))
        assertEquals(listOf("컵라면"), FoodSearchPolicy.queries("컵라면"))
        assertEquals(listOf("라면"), FoodSearchPolicy.queries("라면"))
        assertEquals("김밥", FoodSearchPolicy.broaderSuggestion("참치김밥"))
    }

    @Test fun solidVolumeBasisRequiresReviewWithoutChangingOriginalUnitOrCalories() {
        val volumeRice = food("김밥_참치", "밥류", "ml")
        assertTrue(FoodSearchPolicy.needsBasisReview(volumeRice))
        assertEquals("ml", volumeRice.unit)
        assertEquals(128.0, volumeRice.energyKcal, 0.0)
        assertFalse(FoodSearchPolicy.needsBasisReview(volumeRice.copy(unit = "g")))
        assertFalse(FoodSearchPolicy.needsBasisReview(food("우유", "음료 및 차류", "ml")))
    }

    @Test fun officialProductsRankBeforeGenericRowsForExactPrefixAndContainsMatches() {
        val exactProduct = food("신라면", "면류", "g").copy(sourceType = "K-FIND-PRODUCT", normalizedName = "신라면")
        val prefixProduct = exactProduct.copy(name = "신라면 블랙", normalizedName = "신라면블랙")
        val containsProduct = exactProduct.copy(name = "매운 신라면", normalizedName = "매운신라면")
        val generic = food("신라면", "면류", "g").copy(normalizedName = "신라면")
        assertEquals(listOf(0, 1, 2, 3), listOf(exactProduct, prefixProduct, containsProduct, generic)
            .map { FoodSearchPolicy.searchRank(it, "신라면") })
        assertEquals("제품", FoodSearchPolicy.resultGroup(exactProduct))
        assertEquals("기본·종류", FoodSearchPolicy.resultGroup(generic))
        assertTrue(FoodSearchPolicy.isOfficialKfind(exactProduct))
    }

    @Test fun officialSoupDetailIsNaturalizedForDisplayWithoutChangingSourceName() {
        val source = food("미역국_북어", "국 및 탕류", "ml")
            .copy(sourceFoodCode = "D605-223200000-0001")
        assertEquals("북어미역국", FoodSearchPolicy.displayName(source))
        assertEquals("미역국_북어", source.name)

        val duplicateDetail = source.copy(name = "콩나물국_콩나물", normalizedName = "콩나물국콩나물")
        assertEquals("콩나물국 · 콩나물", FoodSearchPolicy.displayName(duplicateDetail))
        val qualifier = source.copy(name = "홍합탕_홍합만", normalizedName = "홍합탕홍합만")
        assertEquals("홍합탕 · 홍합만", FoodSearchPolicy.displayName(qualifier))
    }

    @Test fun officialFoodCodeOriginCreatesSourceSupportedVariantLabels() {
        val source = food("미역국", "국 및 탕류", "ml")
        assertEquals("가정식 · 분석값", FoodSearchPolicy.sourceVariantLabel(source.copy(sourceFoodCode = "D105-x")))
        assertEquals("외식 · 분석값", FoodSearchPolicy.sourceVariantLabel(source.copy(sourceFoodCode = "D305-x")))
        assertEquals("외식 · 재료량 기반 산출", FoodSearchPolicy.sourceVariantLabel(source.copy(sourceFoodCode = "D405-x")))
        assertEquals("초등학교 급식 · 재료량 기반 산출", FoodSearchPolicy.sourceVariantLabel(source.copy(sourceFoodCode = "D505-x")))
        assertEquals("중·고등학교 급식 · 재료량 기반 산출", FoodSearchPolicy.sourceVariantLabel(source.copy(sourceFoodCode = "D605-x")))
        assertEquals("산업체 급식 · 재료량 기반 산출", FoodSearchPolicy.sourceVariantLabel(source.copy(sourceFoodCode = "D705-x")))
        assertEquals(null, FoodSearchPolicy.sourceVariantLabel(source.copy(sourceType = "K-FIND-PRODUCT")))
    }

    @Test fun broadAliasesAndCategoriesFindBasicFoodsWithoutMixingCompletedSalads() {
        assertEquals(listOf("과일류", "과일"), FoodSearchPolicy.queries("과일류"))
        assertEquals(listOf("야채", "채소"), FoodSearchPolicy.queries("야채"))
        assertEquals(listOf("간식", "과자"), FoodSearchPolicy.queries("간식"))

        val apple = food("사과_껍질 포함_생것", "과일류", "g")
        val cabbage = food("양배추_생것", "채소류", "g")
        val dressedSalad = food("닭가슴살 샐러드", "샐러드류", "g")
        assertTrue(FoodSearchPolicy.matchesCategory(apple, FoodBrowseCategory.FRUIT))
        assertTrue(FoodSearchPolicy.matchesCategory(cabbage, FoodBrowseCategory.VEGETABLE))
        assertFalse(FoodSearchPolicy.matchesCategory(dressedSalad, FoodBrowseCategory.VEGETABLE))
    }

    @Test fun duplicateKeyCollapsesOnlySameBrandBasisAndNutrition() {
        val first = food("사과_생것", "과일류", "g").copy(
            id = "one", sourceFoodCode = "FDC-1", sourceType = "USDA-SR-LEGACY",
            brand = null, carbohydrateGrams = 13.0, proteinGrams = 0.3, fatGrams = 0.2
        )
        val duplicate = first.copy(id = "two", sourceFoodCode = "FDC-2")
        val anotherBrand = duplicate.copy(brand = "다른 브랜드")
        val anotherBasis = duplicate.copy(referenceAmount = 80.0)
        assertEquals(FoodSearchPolicy.deduplicationKey(first), FoodSearchPolicy.deduplicationKey(duplicate))
        assertFalse(FoodSearchPolicy.deduplicationKey(first) == FoodSearchPolicy.deduplicationKey(anotherBrand))
        assertFalse(FoodSearchPolicy.deduplicationKey(first) == FoodSearchPolicy.deduplicationKey(anotherBasis))
    }

    private fun food(name: String, category: String, unit: String) = FoodItem(
        id = "test", sourceType = "K-FIND", sourceFoodCode = "test", name = name,
        normalizedName = name.replace("_", ""), category = category,
        referenceAmount = 100.0, unit = unit, energyKcal = 128.0,
        servingDescription = "100$unit 기준", dataVersion = "test", createdAt = 0, updatedAt = 0
    )
}
