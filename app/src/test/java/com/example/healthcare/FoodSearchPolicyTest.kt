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

    @Test fun exactAndCompleteFoodNameMatchesRankBeforeGenericSourceType() {
        val exactProduct = food("신라면", "면류", "g").copy(sourceType = "K-FIND-PRODUCT", normalizedName = "신라면")
        val prefixProduct = exactProduct.copy(name = "신라면 블랙", normalizedName = "신라면블랙")
        val containsProduct = exactProduct.copy(name = "매운 신라면", normalizedName = "매운신라면")
        val generic = food("신라면", "면류", "g").copy(normalizedName = "신라면")
        assertEquals(listOf(0, 20, 20, 0), listOf(exactProduct, prefixProduct, containsProduct, generic)
            .map { FoodSearchPolicy.searchRank(it, "신라면") })
        assertEquals("제품", FoodSearchPolicy.resultGroup(exactProduct))
        assertEquals("기본·종류", FoodSearchPolicy.resultGroup(generic))
        assertTrue(FoodSearchPolicy.isOfficialKfind(exactProduct))
    }

    @Test fun tunaGimbapExactAliasAndTokenMatchesRankBeforePlainGimbap() {
        val exactSourceOrder = food("김밥_참치", "밥류", "g").copy(normalizedName = "김밥참치")
        val spacedExact = food("참치 김밥", "밥류", "g").copy(normalizedName = "참치김밥")
        val tokenMatch = food("참치 마요 김밥", "밥류", "g").copy(normalizedName = "참치마요김밥")
        val strongVariant = food("매콤참치김밥", "밥류", "g").copy(normalizedName = "매콤참치김밥")
        val generic = food("김밥", "밥류", "g").copy(normalizedName = "김밥")

        assertEquals(0, FoodSearchPolicy.searchRank(exactSourceOrder, "참치김밥"))
        assertEquals(0, FoodSearchPolicy.searchRank(spacedExact, "참치 김밥"))
        assertTrue(FoodSearchPolicy.searchRank(tokenMatch, "참치김밥") <
            FoodSearchPolicy.searchRank(generic, "참치김밥"))
        assertTrue(FoodSearchPolicy.searchRank(strongVariant, "참치김밥") <
            FoodSearchPolicy.searchRank(generic, "참치김밥"))
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

    @Test fun sameNormalizedDisplayNameCreatesOneGroupWithoutLosingProducts() {
        val general = food("참치김밥", "밥류", "g").copy(
            id = "general", sourceFoodCode = "general", carbohydrateGrams = 35.0,
            proteinGrams = 9.0, fatGrams = 7.0
        )
        val cu = general.copy(
            id = "cu", sourceType = "K-FIND-PRODUCT", sourceFoodCode = "cu-1",
            name = "참치 김밥", normalizedName = "참치김밥", brand = "CU",
            referenceAmount = 180.0, energyKcal = 370.0, carbohydrateGrams = 52.0
        )
        val gs = general.copy(
            id = "gs", sourceType = "K-FIND-PRODUCT", sourceFoodCode = "gs-1",
            brand = "GS25", referenceAmount = 165.0, energyKcal = 345.0,
            proteinGrams = 11.0
        )

        val groups = FoodSearchPolicy.groupSearchResults(listOf(gs, cu, general), "참치김밥")

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().size)
        assertEquals(2, groups.single().alternatives.size)
        val preserved = listOf(groups.single().representative) + groups.single().alternatives
        assertEquals(setOf("general", "cu", "gs"), preserved.map(FoodItem::id).toSet())
        assertEquals(setOf(null, "CU", "GS25"), preserved.map(FoodItem::brand).toSet())
        assertEquals(setOf(100.0, 180.0, 165.0), preserved.map(FoodItem::referenceAmount).toSet())
        assertEquals(setOf(128.0, 370.0, 345.0), preserved.map(FoodItem::energyKcal).toSet())
    }

    @Test fun canonicalDishOrderAndBrandPrefixesCreateOneFoodKindGroup() {
        val official = food("김밥_참치", "밥류", "g").copy(id = "official", sourceFoodCode = "official")
        val spaced = food("참치 김밥", "밥류", "g").copy(id = "spaced", sourceFoodCode = "spaced")
        val branded = food("CU 참치김밥", "밥류", "g").copy(
            id = "cu", sourceFoodCode = "cu", sourceType = "K-FIND-PRODUCT", brand = "CU"
        )

        val group = FoodSearchPolicy.groupSearchResults(listOf(branded, spaced, official), "참치김밥").single()

        assertEquals("참치김밥", group.key)
        assertEquals("official", group.representative.id)
        assertEquals(setOf("official", "spaced", "cu"),
            (listOf(group.representative) + group.alternatives).map(FoodItem::id).toSet())
    }

    @Test fun basicAliasRanksAheadOfBrandProductsForTofuAndEggQueries() {
        val tofu = food("두부_단단한 것_생것", "두류", "g").copy(
            id = "tofu", sourceType = "USDA-SR-LEGACY", aliases = "|두부|일반두부|"
        )
        val tofuSnack = food("두부과자", "과자류", "g").copy(
            id = "snack", sourceType = "K-FIND-PRODUCT", normalizedName = "두부과자"
        )
        val egg = food("달걀_삶은 것", "난류", "g").copy(
            id = "egg", sourceType = "USDA-SR-LEGACY", aliases = "|달걀|계란|삶은달걀|삶은계란|"
        )

        assertTrue(FoodSearchPolicy.searchRank(tofu, "두부") < FoodSearchPolicy.searchRank(tofuSnack, "두부"))
        assertEquals(FoodSearchPolicy.searchRank(egg, "달걀"), FoodSearchPolicy.searchRank(egg, "계란"))
        assertEquals(5, FoodSearchPolicy.searchRank(egg, "삶은 계란"))
    }

    @Test fun semanticallyDifferentTunaGimbapNamesRemainSeparateGroups() {
        val tuna = food("참치김밥", "밥류", "g").copy(id = "tuna", sourceFoodCode = "tuna")
        val tunaMayo = tuna.copy(
            id = "tuna-mayo", sourceFoodCode = "tuna-mayo", name = "참치마요김밥",
            normalizedName = "참치마요김밥"
        )

        val groups = FoodSearchPolicy.groupSearchResults(listOf(tunaMayo, tuna), "참치김밥")

        assertEquals(2, groups.size)
        assertEquals("tuna", groups.first().representative.id)
        assertEquals(setOf("참치김밥", "참치마요김밥"), groups.map { it.key }.toSet())
    }

    @Test fun brandQueryChoosesMatchingBrandAsRepresentative() {
        val base = food("참치김밥", "밥류", "g").copy(
            sourceType = "K-FIND-PRODUCT", normalizedName = "참치김밥"
        )
        val gs = base.copy(id = "gs", sourceFoodCode = "gs", brand = "GS25")
        val cu = base.copy(id = "cu", sourceFoodCode = "cu", brand = "CU")

        val group = FoodSearchPolicy.groupSearchResults(listOf(gs, cu), "CU 참치김밥").single()

        assertEquals("cu", group.representative.id)
        assertEquals("CU", group.representative.brand)
    }

    @Test fun representativeSelectionIsDeterministicRegardlessOfInputOrder() {
        val incomplete = food("참치김밥", "밥류", "g").copy(
            id = "b", sourceFoodCode = "b", carbohydrateGrams = 30.0
        )
        val complete = incomplete.copy(
            id = "a", sourceFoodCode = "a", proteinGrams = 10.0, fatGrams = 8.0
        )

        val first = FoodSearchPolicy.groupSearchResults(listOf(incomplete, complete), "참치김밥").single()
        val second = FoodSearchPolicy.groupSearchResults(listOf(complete, incomplete), "참치김밥").single()

        assertEquals("a", first.representative.id)
        assertEquals(first.representative.id, second.representative.id)
        assertEquals(first.alternatives.map(FoodItem::id), second.alternatives.map(FoodItem::id))
    }

    private fun food(name: String, category: String, unit: String) = FoodItem(
        id = "test", sourceType = "K-FIND", sourceFoodCode = "test", name = name,
        normalizedName = name.replace("_", ""), category = category,
        referenceAmount = 100.0, unit = unit, energyKcal = 128.0,
        servingDescription = "100$unit 기준", dataVersion = "test", createdAt = 0, updatedAt = 0
    )
}
