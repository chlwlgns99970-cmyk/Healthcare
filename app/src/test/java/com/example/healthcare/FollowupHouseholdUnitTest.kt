package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.FoodAmountPolicy
import com.example.healthcare.domain.FoodMetadata
import com.example.healthcare.domain.FoodMetadataPolicy
import com.example.healthcare.domain.PortionQuality
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FollowupHouseholdUnitTest {
    private var originalMetadata: Collection<FoodMetadata> = emptyList()
    @Before fun preserveMetadata() { originalMetadata = FoodMetadataPolicy.snapshot() }
    @After fun restoreMetadata() { FoodMetadataPolicy.install(originalMetadata) }

    private fun food(id: String = "usda-sr-171688", unit: String = "g") = FoodItem(
        id = id, sourceType = "USDA-SR-LEGACY", sourceFoodCode = "FDC-171688",
        name = "사과", normalizedName = "사과", category = "과일류",
        referenceAmount = 100.0, unit = unit, energyKcal = 52.0,
        carbohydrateGrams = 13.81, proteinGrams = 0.26, fatGrams = 0.17,
        servingDescription = "100$unit 기준", dataVersion = "fixture", createdAt = 0, updatedAt = 0)

    private fun metadata(food: FoodItem, amount: Double = 182.0, basis: String = "g",
        unit: String = "개", kind: String = "VERIFIED_CONVERSION", source: String = "https://fdc.nal.usda.gov/download-datasets/") =
        FoodMetadata(foodId = food.id, sourceReference = source, checkedAt = "2026-10-04",
            householdUnit = unit, basisAmountPerUnit = amount, basisUnit = basis,
            servingSourceReference = source, servingEvidenceKind = kind,
            servingSourceSize = "medium (3 inch diameter), 1개 182g")

    @Test fun exactSourceServingOutranksGenericReferenceAndPreservesNutritionBasis() {
        val apple = food()
        FoodMetadataPolicy.install(listOf(metadata(apple)))
        val choice = FoodAmountPolicy.defaultChoice(apple)!!
        assertEquals("개", choice.unit)
        assertEquals(182.0, choice.basisAmountPerUnit, 0.0)
        assertEquals(PortionQuality.VERIFIED_CONVERSION, choice.quality)
        assertTrue(choice.evidence.contains("medium"))
        assertEquals(95, FoodAmountPolicy.calculate(apple, 1.0, "개")!!.calories)
        assertEquals(52, FoodAmountPolicy.calculate(apple, 100.0, "g")!!.calories)
        assertEquals(1, FoodAmountPolicy.choices(apple).count { it.unit == "개" })
    }

    @Test fun anotherExactFoodNeverBorrowsTheServingFromMatchingName() {
        val apple = food()
        FoodMetadataPolicy.install(listOf(metadata(apple)))
        val other = apple.copy(id = "different-source-code", name = "사과조각", sourceFoodCode = "FDC-OTHER")
        assertEquals(listOf("g"), FoodAmountPolicy.choices(other).map { it.unit })
    }

    @Test fun mismatchedMassVolumeAndUnresolvedSolidVolumeRemainUnknown() {
        val unrelated = food().copy(name = "일반 음식", normalizedName = "일반음식")
        FoodMetadataPolicy.install(listOf(metadata(unrelated, basis = "ml")))
        assertEquals(listOf("g"), FoodAmountPolicy.choices(unrelated).map { it.unit })
        val solid = food("kfind-d401-007030000-0001", "ml").copy(
            sourceType = "K-FIND", sourceFoodCode = "D401-007030000-0001", name = "김밥_계란", category = "밥류")
        FoodMetadataPolicy.install(listOf(metadata(solid, amount = 216.0, basis = "g", unit = "줄")))
        assertTrue(FoodAmountPolicy.choices(solid).isEmpty())
        assertNull(FoodAmountPolicy.calculate(solid, 1.0, "줄"))
    }

    @Test fun invalidOrUnreviewedMetadataCannotCreateHouseholdUnit() {
        val generic = food().copy(name = "일반 음식", normalizedName = "일반음식")
        FoodMetadataPolicy.install(emptyList())
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                FoodMetadataPolicy.install(listOf(metadata(generic, amount = invalid)))
            }
            assertEquals(listOf("g"), FoodAmountPolicy.choices(generic).map { it.unit })
        }
        listOf(metadata(generic, kind = "NAME_HEURISTIC"), metadata(generic, source = "unverified")).forEach {
            assertThrows(IllegalArgumentException::class.java) { FoodMetadataPolicy.install(listOf(it)) }
        }
        listOf(metadata(generic, amount = 10001.0),metadata(generic).copy(servingSourceSize = ""),metadata(generic, unit = "모")).forEach {
            FoodMetadataPolicy.install(listOf(it))
            assertEquals(listOf("g"), FoodAmountPolicy.choices(generic).map { choice -> choice.unit })
        }
    }

    @Test fun exactReportedMilkPackPrecedesUnnamedWholeProductAndCalculatesInMl() {
        val product = food("kfind-product-p109-500050200-0515", "ml").copy(
            sourceType = "K-FIND-PRODUCT", sourceFoodCode = "P109-500050200-0515", name = "매일두유 99.9",
            category = "음료류", energyKcal = 50.0, servingDescription = "100ml 기준 · 공식 총내용량 190ml")
        FoodMetadataPolicy.install(listOf(metadata(product, amount = 190.0, basis = "ml", unit = "팩",
            kind = "OFFICIAL_SERVING", source = "https://productguide.maeil.com/products/99-9-190ml")))
        assertEquals("팩", FoodAmountPolicy.defaultChoice(product)!!.unit)
        assertEquals(PortionQuality.OFFICIAL_SERVING, FoodAmountPolicy.portionQuality(product))
        assertEquals(95, FoodAmountPolicy.calculate(product, 1.0, "팩")!!.calories)
        assertEquals(380.0, FoodAmountPolicy.calculate(product, 2.0, "팩")!!.basisAmount, 0.0)
        assertNull(FoodAmountPolicy.calculate(product, 190.0, "g"))
    }

    @Test fun officialMultiItemMeasureDividesOnlyItsExplicitCount() {
        val grapes = food("usda-sr-174683").copy(sourceFoodCode = "FDC-174683", name = "포도_적색 또는 녹색_생것", energyKcal = 69.0)
        FoodMetadataPolicy.install(listOf(metadata(grapes, amount = 49.0/10.0)))
        assertEquals(49.0, FoodAmountPolicy.calculate(grapes, 10.0, "개")!!.basisAmount, 0.00001)
        assertEquals(34, FoodAmountPolicy.calculate(grapes, 10.0, "개")!!.calories)
    }
}
