package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.PortionFraction
import com.example.healthcare.domain.PortionGuide
import com.example.healthcare.domain.PortionVessel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionGuideTest {
    private fun food(
        name: String = "흰밥",
        category: String = "밥류",
        amount: Double = 100.0,
        unit: String = "g",
        kcal: Double = 140.0
    ) = FoodItem(
        id = "test-food", sourceType = "K-FIND", sourceFoodCode = "test", name = name,
        normalizedName = name, category = category, referenceAmount = amount, unit = unit,
        energyKcal = kcal, servingDescription = "${amount.toInt()}$unit 기준", dataVersion = "test",
        createdAt = 0, updatedAt = 0
    )

    @Test fun riceHouseholdChoicesUseDocumented210GramBowl() {
        val rice = food()
        val presets = PortionGuide.presets(rice)
        assertEquals(listOf("1/4공기", "반 공기", "3/4공기", "한 공기", "한 공기 반", "두 공기"),
            presets.map { it.label })
        assertEquals(74, PortionGuide.estimate(rice, presets[0])?.calories)
        assertEquals(147, PortionGuide.estimate(rice, presets[1])?.calories)
        assertEquals(221, PortionGuide.estimate(rice, presets[2])?.calories)
        assertEquals(294, PortionGuide.estimate(rice, presets[3])?.calories)
        assertEquals(441, PortionGuide.estimate(rice, presets[4])?.calories)
        assertEquals(588, PortionGuide.estimate(rice, presets[5])?.calories)
        assertTrue(presets.all { it.sourceReference.contains("식품안전나라") })
    }

    @Test fun paperCupUsesDocumented180MillilitresOnlyForDrinks() {
        val drink = food("보리차", "음료 및 차류", 100.0, "ml", 40.0)
        val presets = PortionGuide.presets(drink)
        assertEquals(90.0, presets[0].amount, 0.0)
        assertEquals(36, PortionGuide.estimate(drink, presets[0])?.calories)
        assertEquals(72, PortionGuide.estimate(drink, presets[1])?.calories)
        assertFalse(PortionGuide.presets(food("국", "국 및 탕류", 100.0, "ml", 40.0))
            .any { it.label.contains("컵") })
    }

    @Test fun fishPalmUsesDocumentedGuideOnlyForMatchingFish() {
        val fish = food("고등어구이", "구이류", 100.0, "g", 200.0)
        val presets = PortionGuide.presets(fish)
        assertEquals("손바닥 크기 1장", presets[1].label)
        assertEquals(220, PortionGuide.estimate(fish, presets[1])?.calories)
        assertTrue(presets[1].sourceReference.contains("식품안전나라"))
        assertFalse(PortionGuide.presets(food("삼겹살구이", "구이류")).any { it.label.contains("손바닥") })
    }

    @Test fun documentedBreadAndAppleChoicesStayFoodSpecific() {
        val bread = food("통밀식빵", "빵류", 100.0, "g", 240.0)
        val breadChoices = PortionGuide.presets(bread)
        assertEquals(listOf("식빵 한 조각", "식빵 두 조각", "식빵 세 조각"), breadChoices.map { it.label })
        assertEquals(80, PortionGuide.estimate(bread, breadChoices.first())?.calories)

        val apple = food("사과", "과일류", 100.0, "g", 50.0)
        val appleChoices = PortionGuide.presets(apple)
        assertEquals(listOf("사과 반 개", "사과 한 개"), appleChoices.map { it.label })
        assertEquals(50, PortionGuide.estimate(apple, appleChoices.first())?.calories)
        assertEquals(100, PortionGuide.estimate(apple, appleChoices.last())?.calories)
        assertFalse(PortionGuide.presets(food("사과잼", "잼류")).any { it.label.contains("사과 반") })
    }

    @Test fun knownCountStaysAvailableAndUnmappedFoodRequiresDirectAmount() {
        val piece = food("빵", "빵 및 과자류", 1.0, "개", 75.0)
        assertEquals(listOf("반 개", "한 개", "한 개 반", "두 개"), PortionGuide.presets(piece).map { it.label })
        assertEquals(150, PortionGuide.estimate(piece, PortionGuide.presets(piece).last())?.calories)
        val unknown = food("김밥", "밥류")
        val presets = PortionGuide.presets(unknown)
        assertTrue(presets.isEmpty())
        assertTrue(PortionGuide.requiresDirectAmount(unknown))
        assertFalse(PortionGuide.requiresDirectAmount(piece))
    }

    @Test fun visualFlowUsesVesselAndFractionButLabelsUnmeasuredChoices() {
        val rice = food()
        val preset = requireNotNull(PortionGuide.visualEstimate(rice, PortionVessel.RICE_BOWL, PortionFraction.HALF))
        assertEquals(105.0, preset.amount, 0.0)
        assertEquals(147, PortionGuide.estimate(rice, preset)?.calories)
        assertTrue(preset.description.contains("매우 대략"))
    }

    @Test fun invalidAndOverflowNumbersNeverProduceCalories() {
        val rice = food()
        val normal = PortionGuide.presets(rice).first()
        assertNull(PortionGuide.estimate(rice, normal.copy(amount = Double.NaN)))
        assertNull(PortionGuide.estimate(rice, normal.copy(amount = Double.MAX_VALUE)))
        assertNull(PortionGuide.estimate(rice, normal.copy(amount = -1.0)))
        assertTrue(PortionGuide.presets(food(amount = 0.0)).isEmpty())
    }

    @Test fun recommendationUsesHouseholdWordsBeforeRawWeight() {
        assertEquals("밥 약 0.7공기", PortionGuide.recommendationLabel(food(), 140.0))
        assertEquals("추천 식단에 담긴 양",
            PortionGuide.recommendationLabel(food("김밥", "밥류"), 200.0))
        val noodles = food("쫄면", "면 및 만두류", 100.0, "g", 129.0)
        assertEquals("쫄면 약 1.2그릇", PortionGuide.recommendationLabel(noodles, 550.0))
        assertEquals(581, PortionGuide.estimate(noodles, PortionGuide.presets(noodles)[1])?.calories)
    }

    @Test fun verifiedRamenPackageUsesOfficialTotalWeightWithoutChangingNutritionBasis() {
        val ramen = food("신라면", "면류", 100.0, "g", 417.0).copy(
            sourceType = "K-FIND-PRODUCT",
            sourceFoodCode = "P108-003000400-0138",
            servingDescription = "100g 기준 · 공식 총내용량 120g · 포장단위 봉"
        )
        val presets = PortionGuide.presets(ramen)
        assertEquals(listOf("반 봉", "1봉", "1.5봉", "2봉"), presets.map { it.label })
        assertEquals(120.0, presets[1].amount, 0.0)
        assertEquals(500, PortionGuide.estimate(ramen, presets[1])?.calories)
        assertEquals(1000, PortionGuide.estimate(ramen, presets[3])?.calories)
        assertEquals("1봉 · 500 kcal", PortionGuide.resultServingSummary(ramen))
        assertEquals("1봉 120g 기준", PortionGuide.resultServingBasis(ramen))
        assertTrue(presets.all { it.sourceReference.contains("P108-003000400-0138") })
    }

    @Test fun verifiedPieceOffersWholePiecesButUnmarkedPizzaDoesNotInventSlices() {
        val slice = food("공식 피자 조각", "즉석식품류", 100.0, "g", 250.0).copy(
            sourceType = "K-FIND-PRODUCT", sourceFoodCode = "slice",
            servingDescription = "100g 기준 · 공식 총내용량 80g · 포장단위 조각"
        )
        assertEquals(listOf("1조각", "2조각", "3조각", "4조각"), PortionGuide.presets(slice).map { it.label })
        assertEquals(200, PortionGuide.estimate(slice, PortionGuide.presets(slice).first())?.calories)

        val unmarked = slice.copy(servingDescription = "100g 기준 · 공식 총내용량 320g")
        assertEquals(listOf("제품의 절반", "제품 전체", "제품 1.5개", "제품 2개"),
            PortionGuide.presets(unmarked).map { it.label })
        assertFalse(PortionGuide.presets(unmarked).any { it.label.contains("조각") })
        assertEquals("제품 전체 · 800 kcal", PortionGuide.resultServingSummary(unmarked))
        assertEquals("제품 전체 320g 기준", PortionGuide.resultServingBasis(unmarked))
    }

    @Test fun verifiedLineUnitIsOfferedOnlyWhenOfficialSourceSaysLine() {
        val verifiedRoll = food("참치김밥", "밥류", 100.0, "g", 170.0).copy(
            sourceType = "K-FIND-PRODUCT",
            sourceFoodCode = "official-roll",
            servingDescription = "100g 기준 · 공식 총내용량 240g · 포장단위 줄"
        )
        assertEquals(listOf("반 줄", "1줄", "1.5줄", "2줄"),
            PortionGuide.presets(verifiedRoll).map { it.label })
        assertEquals(408, PortionGuide.estimate(verifiedRoll, PortionGuide.presets(verifiedRoll)[1])?.calories)

        val genericRoll = verifiedRoll.copy(
            sourceType = "K-FIND",
            servingDescription = "100g 기준"
        )
        assertTrue(PortionGuide.presets(genericRoll).isEmpty())
        assertTrue(PortionGuide.requiresDirectAmount(genericRoll))
    }

    @Test fun hamburgerNeverBecomesOnePieceWithoutOfficialPieceEvidence() {
        val genericBurger = food("햄버거", "빵류", 100.0, "g", 250.0)
        assertEquals("영양정보 100g 기준 · 약 250 kcal", PortionGuide.resultServingSummary(genericBurger))
        assertTrue(PortionGuide.presets(genericBurger).isEmpty())
        assertTrue(PortionGuide.requiresDirectAmount(genericBurger))
        assertFalse(PortionGuide.presets(genericBurger).any { it.label.contains("개") })

        val packagedBurger = genericBurger.copy(
            sourceType = "K-FIND-PRODUCT",
            servingDescription = "100g 기준 · 공식 총내용량 180g"
        )
        assertEquals("제품 전체 · 450 kcal", PortionGuide.resultServingSummary(packagedBurger))
        assertEquals("제품 전체 180g 기준", PortionGuide.resultServingBasis(packagedBurger))
        assertFalse(PortionGuide.presets(packagedBurger).any { it.label == "1개" })
    }

    @Test fun beverageUsesBottleOrPackOnlyWhenOfficialUnitExists() {
        val beverage = food("과일음료", "음료 및 차류", 100.0, "ml", 44.0).copy(
            sourceType = "K-FIND-PRODUCT",
            servingDescription = "100ml 기준 · 공식 총내용량 500ml · 포장단위 병"
        )
        assertEquals("1병 · 220 kcal", PortionGuide.resultServingSummary(beverage))
        assertEquals("1병 500ml 기준", PortionGuide.resultServingBasis(beverage))
        assertEquals("1병", PortionGuide.presets(beverage)[1].label)

        val noPackageUnit = beverage.copy(servingDescription = "100ml 기준 · 공식 총내용량 200ml")
        assertEquals("제품 전체 · 88 kcal", PortionGuide.resultServingSummary(noPackageUnit))
        assertEquals("제품 전체 200ml 기준", PortionGuide.resultServingBasis(noPackageUnit))
        assertFalse(PortionGuide.presets(noPackageUnit).any { it.label.contains("병") || it.label.contains("팩") })

        val pack = beverage.copy(
            energyKcal = 65.789,
            servingDescription = "100ml 기준 · 공식 총내용량 190ml · 포장단위 팩"
        )
        assertEquals("1팩 · 125 kcal", PortionGuide.resultServingSummary(pack))
        assertEquals("1팩 190ml 기준", PortionGuide.resultServingBasis(pack))
        assertEquals("1팩", PortionGuide.presets(pack)[1].label)
    }
}
