package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.FoodAmountPolicy
import com.example.healthcare.domain.NutritionQuality
import com.example.healthcare.domain.PortionQuality
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class FoodAmountPolicyTest {
    private fun food(unit: String = "g", kcal: Double = 180.0) = FoodItem(
        id = "fixture", sourceType = "K-FIND", sourceFoodCode = "fixture", name = "일반 음식",
        normalizedName = "일반음식", referenceAmount = 100.0, unit = unit, energyKcal = kcal,
        carbohydrateGrams = 20.0, proteinGrams = 10.0, fatGrams = 5.0,
        servingDescription = "100$unit 기준", dataVersion = "fixture", createdAt = 0, updatedAt = 0
    )
    private fun roll() = food(kcal = 137.0).copy(sourceType = "K-FIND-PRODUCT",
        sourceFoodCode = "P123-203020200-2032", name = "백종원한줄김밥",
        servingDescription = "100g 기준 · 공식 총내용량 216g · 포장단위 줄")

    @Test fun hundredGramFoodUsesWeightWithoutInventingCount() {
        val food = food()
        assertEquals(listOf("g"), FoodAmountPolicy.choices(food).map { it.unit })
        assertEquals(90, FoodAmountPolicy.calculate(food, 50.0, "g")?.calories)
        assertEquals(PortionQuality.WEIGHT_ONLY, FoodAmountPolicy.portionQuality(food))
    }

    @Test fun officialServingIsSeparateFromItsNutritionBasis() {
        val food = food().copy(sourceType = "OFFICIAL-BRAND-NUTRITION", servingDescription = "공식 1개 70g 기준")
        assertEquals(listOf("개", "g"), FoodAmountPolicy.choices(food).map { it.unit })
        assertEquals(126, FoodAmountPolicy.calculate(food, 1.0, "개")?.calories)
        assertEquals(70.0, FoodAmountPolicy.calculate(food, 1.0, "개")!!.basisAmount, 0.0)
    }

    @Test fun verifiedKimbapRollUsesOnlySourceTotalAndAllowsHalf() {
        assertEquals("줄", FoodAmountPolicy.defaultChoice(roll())?.unit)
        assertEquals(296, FoodAmountPolicy.calculate(roll(), 1.0, "줄")?.calories)
        assertEquals(148, FoodAmountPolicy.calculate(roll(), 0.5, "줄")?.calories)
        assertEquals(108.0, FoodAmountPolicy.calculate(roll(), 0.5, "줄")!!.basisAmount, 0.0)
    }

    @Test fun originalAmbiguousEggKimbapNeverInventsWeightOrRoll() {
        val original = food("ml", 97.0).copy(sourceFoodCode = "D401-007030000-0001", name = "김밥_계란", category = "밥류")
        assertTrue(FoodAmountPolicy.choices(original).isEmpty())
        assertEquals(PortionQuality.UNRESOLVED, FoodAmountPolicy.portionQuality(original))
        assertNull(FoodAmountPolicy.calculate(original, 1.0, "줄"))
        assertNull(FoodAmountPolicy.calculate(original, 100.0, "g"))
    }

    @Test fun genericKimbapHasNoRollDespiteItsName() {
        assertEquals(listOf("g"), FoodAmountPolicy.choices(food().copy(name = "김밥", category = "밥류")).map { it.unit })
    }

    @Test fun exactUsdaBoiledEggHasVerifiedCountButEggProductDoesNot() {
        val egg = food(kcal = 155.0).copy(sourceType = "USDA-SR-LEGACY", sourceFoodCode = "FDC-173424", name = "달걀_삶은 것")
        assertEquals("개", FoodAmountPolicy.defaultChoice(egg)?.unit)
        assertEquals(155, FoodAmountPolicy.calculate(egg, 2.0, "개")?.calories)
        assertEquals(50.0, FoodAmountPolicy.defaultChoice(egg)!!.basisAmountPerUnit, 0.0)
        assertFalse(FoodAmountPolicy.choices(egg.copy(sourceFoodCode = "other", name = "달걀빵")).any { it.unit == "개" })
    }

    @Test fun exactUsdaBananaUsesMediumEdibleWeight() {
        val banana = food(kcal = 89.0).copy(sourceType = "USDA-SR-LEGACY", sourceFoodCode = "FDC-173944")
        assertEquals(105, FoodAmountPolicy.calculate(banana, 1.0, "개")?.calories)
        assertEquals(118.0, FoodAmountPolicy.defaultChoice(banana)!!.basisAmountPerUnit, 0.0)
        assertTrue(FoodAmountPolicy.defaultChoice(banana)!!.evidence.contains("껍질 제외"))
    }

    @Test fun beverageUsesMlAndNeverAssumesGramDensity() {
        val drink = food("ml", 45.0).copy(category = "유제품류 및 빙과류")
        assertEquals("ml", FoodAmountPolicy.defaultChoice(drink)?.unit)
        assertEquals(90, FoodAmountPolicy.calculate(drink, 200.0, "ml")?.calories)
        assertNull(FoodAmountPolicy.calculate(drink, 200.0, "g"))
    }

    @Test fun statedProductTotalIsWholeProductEvenWithoutNamedPackage() {
        val product = food().copy(sourceType = "K-FIND-PRODUCT", servingDescription = "100g 기준 · 공식 총내용량 250g")
        assertEquals("제품 전체", FoodAmountPolicy.defaultChoice(product)?.unit)
        assertEquals(450, FoodAmountPolicy.calculate(product, 1.0, "제품 전체")?.calories)
        assertFalse(FoodAmountPolicy.choices(product).any { it.unit in setOf("봉", "개", "줄") })
    }

    @Test fun officialBagBottleCanAndPackRetainTheirUnit() {
        listOf("봉", "병", "캔", "팩").forEach { unit ->
            val product = food().copy(sourceType = "K-FIND-PRODUCT", servingDescription = "100g 기준 · 공식 총내용량 125g · 포장단위 $unit")
            assertEquals(unit, FoodAmountPolicy.defaultChoice(product)?.unit)
            assertEquals(225, FoodAmountPolicy.calculate(product, 1.0, unit)?.calories)
        }
    }

    @Test fun unitChangePreservesBasisAndRecalculatesNutrition() {
        assertEquals(0.5, FoodAmountPolicy.amountInUnit(roll(), 108.0, "줄")!!, 0.0)
        assertEquals(FoodAmountPolicy.calculate(roll(), 0.5, "줄"), FoodAmountPolicy.calculate(roll(), 108.0, "g"))
    }

    @Test fun allAvailableMacrosScaleWithSameRatio() {
        val nutrition = FoodAmountPolicy.calculate(food(), 150.0, "g")!!
        assertEquals(30.0, nutrition.carbohydrateGrams!!, 0.0)
        assertEquals(15.0, nutrition.proteinGrams!!, 0.0)
        assertEquals(7.5, nutrition.fatGrams!!, 0.0)
        assertEquals(270, nutrition.calories)
    }

    @Test fun nullAndInvalidMacrosRemainUnknown() {
        val nutrition = FoodAmountPolicy.calculate(food().copy(proteinGrams = null, fatGrams = Double.NaN), 50.0, "g")!!
        assertEquals(10.0, nutrition.carbohydrateGrams!!, 0.0)
        assertNull(nutrition.proteinGrams)
        assertNull(nutrition.fatGrams)
    }

    @Test fun historicalSnapshotScalesItsOwnNutritionWithoutCurrentFood() {
        val nutrition = FoodAmountPolicy.calculateSnapshot(216.0, "g", 296,
            com.example.healthcare.domain.Macronutrients(50.0, null, 8.0), 108.0)!!
        assertEquals(148, nutrition.calories)
        assertEquals(25.0, nutrition.carbohydrateGrams!!, 0.0)
        assertNull(nutrition.proteinGrams)
        assertEquals(4.0, nutrition.fatGrams!!, 0.0)
    }

    @Test fun snapshotUsesSameReferenceAmountAndCalorieBounds() {
        val macros = com.example.healthcare.domain.Macronutrients.Unknown
        assertNull(FoodAmountPolicy.calculateSnapshot(0.0, "g", 296, macros, 108.0))
        assertNull(FoodAmountPolicy.calculateSnapshot(216.0, "", 296, macros, 108.0))
        assertNull(FoodAmountPolicy.calculateSnapshot(216.0, "g", 296, macros, 10001.0))
        assertNull(FoodAmountPolicy.calculateSnapshot(1.0, "g", Int.MAX_VALUE, macros, 2.0))
    }

    @Test fun decimalAndCommaDecimalAreValidButAmbiguousFormatsAreRejected() {
        assertEquals(0.5, FoodAmountPolicy.parseAmount("0.5")!!, 0.0)
        assertEquals(1.5, FoodAmountPolicy.parseAmount(" 1,5 ")!!, 0.0)
        listOf("0", "-1", "1,000.2", "NaN", "Infinity", "1e5", "10001", "").forEach {
            assertNull(it, FoodAmountPolicy.parseAmount(it))
        }
    }

    @Test fun invalidZeroNegativeAndExcessiveAmountCannotCalculate() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 10001.0).forEach {
            assertNull(FoodAmountPolicy.calculate(food(), it, "g"))
        }
        assertNull(FoodAmountPolicy.calculate(roll(), 100.0, "줄"))
        assertNull(FoodAmountPolicy.calculate(food(kcal = Double.MAX_VALUE), 100.0, "g"))
    }

    @Test fun completePartialCaloriesOnlyAndUnknownAreDerived() {
        assertEquals(NutritionQuality.COMPLETE, FoodAmountPolicy.nutritionQuality(food()))
        assertEquals(NutritionQuality.PARTIAL, FoodAmountPolicy.nutritionQuality(food().copy(proteinGrams = null)))
        assertEquals(NutritionQuality.CALORIES_ONLY, FoodAmountPolicy.nutritionQuality(food().copy(carbohydrateGrams = null, proteinGrams = null, fatGrams = null)))
        assertEquals(NutritionQuality.UNKNOWN, FoodAmountPolicy.nutritionQuality(food(kcal = Double.NaN)))
    }

    @Test fun knownZeroIsNotUnknownAndUnknownCannotProduceZero() {
        val zero = food(kcal = 0.0).copy(carbohydrateGrams = 0.0, proteinGrams = 0.0, fatGrams = 0.0)
        assertEquals(NutritionQuality.COMPLETE, FoodAmountPolicy.nutritionQuality(zero))
        assertEquals(0, FoodAmountPolicy.calculate(zero, 100.0, "g")?.calories)
        assertNull(FoodAmountPolicy.calculate(zero.copy(energyKcal = Double.NaN), 100.0, "g"))
    }

    @Test fun existingFoodSpecificHouseholdReferencesRemainAvailable() {
        val cases = listOf(
            Triple(food().copy(name = "흰밥", category = "밥류"), "공기", 210.0),
            Triple(food("ml").copy(name = "보리차", category = "음료 및 차류"), "컵", 180.0),
            Triple(food().copy(name = "고등어구이", category = "구이류"), "장", 110.0),
            Triple(food().copy(name = "식빵", category = "빵류"), "조각", 100.0 / 3),
            Triple(food().copy(name = "사과", category = "과일류"), "개", 200.0),
            Triple(food().copy(name = "쫄면", category = "면류"), "인분", 450.0)
        )
        cases.forEach { (food, unit, amount) ->
            val choice = FoodAmountPolicy.choices(food).first { it.unit == unit }
            assertEquals(amount, choice.basisAmountPerUnit, 0.0)
            assertEquals(PortionQuality.VERIFIED_CONVERSION, choice.quality)
        }
    }

    @Test fun officialHotAndIceCupServingIsProtected() {
        val coffee = food("ml").copy(sourceType = "OFFICIAL-BRAND-NUTRITION", servingDescription = "공식 HOT 1잔 591ml 기준")
        assertEquals("잔", FoodAmountPolicy.defaultChoice(coffee)?.unit)
        assertEquals(591.0, FoodAmountPolicy.defaultChoice(coffee)!!.basisAmountPerUnit, 0.0)
    }

    @Test fun zeroOrMismatchedOfficialConversionFallsBackWithoutFabrication() {
        val mismatch = food().copy(servingDescription = "공식 1개 100ml 기준")
        assertEquals(listOf("g"), FoodAmountPolicy.choices(mismatch).map { it.unit })
        assertEquals(listOf("g"), FoodAmountPolicy.choices(food().copy(servingDescription = "공식 0개 70g 기준")).map { it.unit })
    }

    @Test fun originalOfficialNonWeightUnitRemainsAvailable() {
        val original = food("인분", 400.0).copy(referenceAmount = 1.0)
        assertEquals("인분", FoodAmountPolicy.defaultChoice(original)?.unit)
        assertEquals(200, FoodAmountPolicy.calculate(original, 0.5, "인분")?.calories)
    }

    @Test fun bundledQualityAuditUsesActualRuntimePolicyAndPreservesAllFoodIds() {
        val root = File("src/main/assets/fooddata").takeIf(File::isDirectory) ?: File("app/src/main/assets/fooddata")
        val foods = listOf("food_items.csv", "product_items.csv", "franchise_official_items.csv").flatMap { file ->
            val lines = File(root, file).readLines().filter(String::isNotBlank)
            val headers = csvLine(lines.first())
            lines.drop(1).map { line ->
                val values = csvLine(line)
                val row = headers.mapIndexed { index, header -> header to values[index] }.toMap()
                FoodItem(id = row.getValue("id"), sourceType = row.getValue("sourceType"),
                    sourceFoodCode = row.getValue("sourceFoodCode"), name = row.getValue("name"),
                    normalizedName = row.getValue("normalizedName"), category = row["category"],
                    referenceAmount = row.getValue("referenceAmount").toDouble(), unit = row.getValue("unit"),
                    energyKcal = row.getValue("energyKcal").toDouble(), carbohydrateGrams = row["carbohydrateGrams"]?.toDoubleOrNull(),
                    proteinGrams = row["proteinGrams"]?.toDoubleOrNull(), fatGrams = row["fatGrams"]?.toDoubleOrNull(),
                    servingDescription = row.getValue("servingDescription"), dataVersion = row.getValue("dataVersion"), createdAt = 0, updatedAt = 0)
            }
        }
        val manifest=java.util.Properties().apply { File(root,"food_data_manifest.properties").reader().use { load(it) } }
        assertEquals(manifest.getProperty("totalFoodCount").toInt(), foods.size)
        assertEquals(foods.size, foods.map { it.id }.distinct().size)
        val portions = foods.groupingBy(FoodAmountPolicy::portionQuality).eachCount()
        val nutrition = foods.groupingBy(FoodAmountPolicy::nutritionQuality).eachCount()
        assertEquals(2382, portions[PortionQuality.UNRESOLVED])
        assertEquals(0, nutrition[NutritionQuality.UNKNOWN] ?: 0)
        val rollRows = foods.filter { food -> FoodAmountPolicy.choices(food).any { it.unit == "줄" } }
        assertTrue(rollRows.size >= 9)
        assertTrue(rollRows.all { it.sourceType == "K-FIND-PRODUCT" && it.servingDescription.contains("포장단위 줄") })
        val counts = linkedMapOf(
            "totalFoods" to foods.size,
            "kcalUsable" to foods.count { FoodAmountPolicy.nutritionQuality(it) != NutritionQuality.UNKNOWN },
            "macroComplete" to (nutrition[NutritionQuality.COMPLETE] ?: 0),
            "officialServing" to (portions[PortionQuality.OFFICIAL_SERVING] ?: 0),
            "verifiedConversion" to (portions[PortionQuality.VERIFIED_CONVERSION] ?: 0),
            "verifiedLivingUnit" to foods.count { FoodAmountPolicy.choices(it).any { choice -> choice.unit !in setOf("g", "ml", "제품 전체") } },
            "gMlFallback" to ((portions[PortionQuality.WEIGHT_ONLY] ?: 0) + (portions[PortionQuality.VOLUME_ONLY] ?: 0)),
            "unresolved" to (portions[PortionQuality.UNRESOLVED] ?: 0),
            "verifiedRoll" to rollRows.size
        )
        val output = File("build/amount-qa/food-amount-runtime-audit.json")
        output.parentFile.mkdirs()
        output.writeText(counts.entries.joinToString(prefix = "{\n", postfix = "\n}", separator = ",\n") { (key, value) -> "  \"$key\": $value" })
    }

    private fun csvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> { current.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { values += current.toString(); current.clear() }
                else -> current.append(char)
            }
            index++
        }
        values += current.toString()
        return values
    }
}
