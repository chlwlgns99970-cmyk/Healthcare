package com.example.healthcare

import com.example.healthcare.data.entity.*
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.*
import com.example.healthcare.ui.viewmodel.RecommendedIngredientUi
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class RecommendationServingPolicyTest {
    private fun food(id: String = "food", kcal: Double = 100.0, category: String = "밥류") = FoodItem(
        id, "K-FIND", id, "검증 음식 $id", "검증음식$id", category = category,
        referenceAmount = 100.0, unit = "g", energyKcal = kcal, proteinGrams = 10.0,
        carbohydrateGrams = 15.0, fatGrams = 2.0, servingDescription = "100g 기준", dataVersion = "fixture",
        createdAt = 0, updatedAt = 0)
    private fun egg() = food(kcal = 155.0).copy(sourceType = "USDA-SR-LEGACY", sourceFoodCode = "FDC-173424", name = "달걀_삶은 것",
        carbohydrateGrams = 1.12, proteinGrams = 12.58, fatGrams = 10.61)
    private fun roll() = food(kcal = 137.0).copy(sourceType = "K-FIND-PRODUCT", sourceFoodCode = "P123-203020200-2032",
        name = "백종원한줄김밥", carbohydrateGrams = 25.95, proteinGrams = 4.57, fatGrams = 1.64, servingDescription = "100g 기준 · 공식 총내용량 216g · 포장단위 줄")
    private fun options(food: FoodItem, base: Double, meal: MealType = MealType.LUNCH) =
        RecommendationServingPolicy.options(listOf(MealTemplateIngredient(1, "fixture", food.id, base, food.unit, true)), listOf(food), meal)
    private fun ingredient(food: FoodItem, amount: Double) = RecommendedIngredientUi(1, food, amount, amount,
        food.unit, NutritionKcal(food, amount), adjustable = true, minimumAmount = null, maximumAmount = null, adjustmentStep = null)
    private fun NutritionKcal(food: FoodItem, amount: Double) = requireNotNull(FoodAmountPolicy.calculate(food, amount, "g")).calories
    private fun seed(id: String, meal: MealType, kcal: Int, alternatives: List<Int> = listOf(kcal)) = RecommendationSeed(
        MealTemplate(id, id, "|${meal.name}|", kcal, 10.0, 20.0, 5.0, 10, "LOW", "|COOK|", source = "verified", createdAt = 0, updatedAt = 0),
        setOf(id), servingsByMeal = mapOf(meal to alternatives.map { RecommendationServing(1.0, it, Macronutrients(20.0, 10.0, 5.0)) }))
    private fun plan(seeds: List<RecommendationSeed>, target: Int = 1500) = requireNotNull(DailyMealPlanEngine.generate(
        "2026-10-04", target, DailyRecommendationTheme.CHEAT, seeds, MealCoachRepository.defaultPreference()))

    @Test fun verifiedEggOffersOneTwoThreeAndDisplaysCount() {
        val choices = options(egg(), 100.0)
        assertEquals(listOf("1개", "2개", "3개"), choices.map { it.labels.single().substringAfterLast(" · ") })
        assertEquals(listOf(0.5, 1.0, 1.5), choices.map { it.portion })
        assertEquals("2개", RecommendationServingPolicy.label(egg(), 100.0))
    }
    @Test fun verifiedRollOffersHalfOneOneAndHalfWithoutTransferringIdentity() {
        assertEquals(listOf(108.0, 216.0, 324.0), RecommendationServingPolicy.amounts(roll(), 280.0, MealType.LUNCH))
        assertEquals(listOf("0.5줄", "1줄", "1.5줄"), options(roll(), 280.0).map { it.labels.single().substringAfter(" · ") })
        assertEquals("300g", RecommendationServingPolicy.label(food().copy(name = "김밥"), 300.0))
    }
    @Test fun measuredFallbackKeepsBasisUnitAndHasFoodSpecificBounds() {
        assertEquals(listOf(250.0, 350.0, 450.0), RecommendationServingPolicy.amounts(food(category = "죽 및 스프류"), 800.0, MealType.LUNCH))
        assertEquals(listOf(100.0, 150.0, 200.0), RecommendationServingPolicy.amounts(food(category = "구이류"), 250.0, MealType.LUNCH))
        val drink = food(category = "음료 및 차류").copy(unit = "ml", name = "원자료 음료", servingDescription = "100ml 기준")
        assertTrue(RecommendationServingPolicy.label(drink, 180.0).endsWith("컵"))
        assertFalse(RecommendationServingPolicy.label(food(), 200.0).contains("줄"))
    }
    @Test fun recommendationAndConsumptionUseSameCountAndNutrition() {
        val ui = ingredient(egg(), 100.0)
        assertEquals("2", ui.displayedQuantity); assertEquals("개", ui.displayedUnit)
        assertEquals("2개", ui.consumedSnapshot()?.label)
        assertEquals(155, ui.consumedAmount?.calories)
        assertEquals(1.12, ui.consumedAmount!!.carbohydrateGrams!!, 0.0)
        assertEquals(12.58, ui.consumedAmount!!.proteinGrams!!, 0.0)
        assertEquals(10.61, ui.consumedAmount!!.fatGrams!!, 0.0)
    }
    @Test fun savedRecommendationCarriesHistoricalUnitForEditing() {
        val ui = ingredient(roll(), 216.0)
        val snapshot = requireNotNull(ui.consumedSnapshot())
        val row = MealRecord(id = 99, date = "2026-10-04", time = "12:00", mealType = MealType.LUNCH,
            foodName = ui.foodItem.name, calories = ui.calories, servingAmount = ui.amount, servingUnit = ui.unit,
            portionPresetId = snapshot.presetId, portionDisplayLabel = snapshot.label)
        val historic = requireNotNull(RecordedAmountSnapshot.from(row))
        assertEquals("1줄", historic.label); assertEquals(216.0, historic.basisPerUnit, 0.0)
        assertEquals("0.5줄", historic.copy(quantity = 0.5).label)
    }
    @Test fun excessiveInputTemplateIsNormalizedBeforeTargetSearch() {
        assertEquals(550.0, RecommendationServingPolicy.amounts(food(category = "찌개 및 전골류"), 1269.0, MealType.DINNER).maxOrNull()!!, 0.0)
        assertEquals(150.0, RecommendationServingPolicy.amounts(egg(), 500.0, MealType.LUNCH).maxOrNull()!!, 0.0)
        assertEquals(listOf(120.0), RecommendationServingPolicy.amounts(food().copy(sourceType = "K-FIND-PRODUCT",
            servingDescription = "100g 기준 · 공식 총내용량 120g · 포장단위 봉"), 360.0, MealType.LUNCH))
    }
    @Test fun realisticGapDoesNotInventPortionToMeetTarget() {
        val seeds = listOf(seed("아침", MealType.BREAKFAST, 400), seed("점심", MealType.LUNCH, 400),
            seed("저녁", MealType.DINNER, 500), seed("간식", MealType.SNACK, 162))
        val p = plan(seeds)
        assertEquals(1462, p.totalKcal); assertEquals(1500, p.targetKcal); assertTrue(p.meals.all { it.portion == 1.0 })
    }
    @Test fun alternativeMenuClosesGapWithoutExpandingFixedFood() {
        val seeds = listOf(seed("아침", MealType.BREAKFAST, 400), seed("점심", MealType.LUNCH, 400),
            seed("저녁", MealType.DINNER, 500), seed("간식", MealType.SNACK, 162), seed("다른 간식", MealType.SNACK, 200))
        val p = plan(seeds)
        assertEquals(1500, p.totalKcal); assertEquals("다른 간식", p.meals.last().name)
    }
    @Test fun snackCannotExpandToFillLargeDayGap() {
        val seeds = listOf(seed("아침", MealType.BREAKFAST, 400), seed("점심", MealType.LUNCH, 400),
            seed("저녁", MealType.DINNER, 500), seed("간식", MealType.SNACK, 200, listOf(200, 1000)))
        assertEquals(200, plan(seeds).meals.last().kcal)
        assertTrue(RecommendationServingPolicy.amounts(food(kcal = 450.0, category = "빵 및 과자류"), 270.0, MealType.SNACK)
            .all { NutritionKcal(food(kcal = 450.0), it) <= RecommendationServingPolicy.MAX_SNACK_KCAL })
    }

    @Test fun real292TemplatePortionAuditAnd1500Example() {
        val fixture = ServingAssetFixture()
        val seeds = fixture.seeds()
        assertEquals(292, seeds.size); assertEquals(292, seeds.map { it.template.id }.distinct().size)
        assertTrue(seeds.all { s -> DailyMealPlanEngine.slots.filter { MealRecommendationEngine.supportsMeal(s.template, it) }
            .any { RecommendationServingPolicy.available(s, it).isNotEmpty() } })
        val p = plan(seeds)
        assertEquals(4, p.meals.size)
        p.meals.forEach { meal -> assertNotNull(RecommendationServingPolicy.selected(seeds.single { it.template.id == meal.templateId }, meal.mealType, meal.portion)) }
        assertTrue(p.meals.last().kcal <= 225)
        fixture.writeAudit(seeds, p)
    }
}

internal class ServingAssetFixture {
    val root: File = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }
    private fun rows(file: String): List<Map<String, String>> {
        val lines = File(root, "app/src/main/assets/fooddata/$file").readLines()
        val headers = csv(lines.first())
        return lines.drop(1).filter(String::isNotBlank).map { headers.zip(csv(it)).toMap() }
    }
    private fun csv(line: String): List<String> {
        val values = mutableListOf<String>(); val value = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) { when (val c = line[i]) {
            '"' -> if (quoted && i + 1 < line.length && line[i + 1] == '"') { value.append('"'); i++ } else quoted = !quoted
            ',' -> if (quoted) value.append(c) else { values += value.toString(); value.clear() }
            else -> value.append(c)
        }; i++ }; values += value.toString(); return values
    }
    private val foods by lazy { rows("food_items.csv").associate { f ->
        f.getValue("id") to FoodItem(f.getValue("id"), f.getValue("sourceType"), f.getValue("sourceFoodCode"), f.getValue("name"),
            f.getValue("normalizedName"), aliases = f["aliases"].orEmpty(), category = f["category"],
            referenceAmount = f.getValue("referenceAmount").toDouble(), unit = f.getValue("unit"), energyKcal = f.getValue("energyKcal").toDouble(),
            proteinGrams = f["proteinGrams"]?.toDoubleOrNull(), carbohydrateGrams = f["carbohydrateGrams"]?.toDoubleOrNull(),
            fatGrams = f["fatGrams"]?.toDoubleOrNull(), servingDescription = f.getValue("servingDescription"),
            dataVersion = f.getValue("dataVersion"), createdAt = 0, updatedAt = 0)
    } }
    private val ingredients by lazy { rows("meal_template_ingredients.csv").map { i -> MealTemplateIngredient(
        mealTemplateId = i.getValue("mealTemplateId"), foodItemId = i.getValue("foodItemId"), amount = i.getValue("amount").toDouble(),
        unit = i.getValue("unit"), adjustable = i.getValue("adjustable").toBoolean(), maximumAmount = i["maximumAmount"]?.toDoubleOrNull()) }.groupBy { it.mealTemplateId } }
    fun seeds() = rows("meal_templates.csv").map { t ->
        val linked = ingredients.getValue(t.getValue("id")); val fs = linked.map { foods.getValue(it.foodItemId) }
        val choices = DailyMealPlanEngine.slots.associateWith { RecommendationServingPolicy.options(linked, fs, it) }
        val base = linked.zip(fs).map { (i, f) -> requireNotNull(FoodAmountPolicy.calculate(f, i.amount, i.unit)) }
        val n = Macronutrients.knownSum(base.map { Macronutrients(it.carbohydrateGrams, it.proteinGrams, it.fatGrams) })
        RecommendationSeed(MealTemplate(t.getValue("id"), t.getValue("name"), t.getValue("supportedMealTypes"), base.sumOf { it.calories },
            n.proteinGrams, n.carbohydrateGrams, n.fatGrams, t.getValue("preparationMinutes").toInt(), t.getValue("costLevel"),
            t.getValue("tags"), t.getValue("allergens"), t.getValue("excludedDietTypes"), t["cuisineType"], t.getValue("source"), 0, 0),
            fs.map { it.name }.toSet(),
            allergenTags = t.getValue("allergens").split('|').filter(String::isNotBlank).toSet(),
            ingredientInfoComplete = MealRecommendationEngine.hasCompleteIngredientInfo(t.getValue("tags")), ingredientCategories = fs.mapNotNull { it.category }.toSet(), servingsByMeal = choices)
    }
    fun installLinkedMetadata() {
        FoodMetadataPolicy.install(rows("food_metadata.csv").filter { it["foodItemId"] in foods }.map { r -> FoodMetadata(
            foodId=r.getValue("foodItemId"), sourceReference=r.getValue("sourceReference"), checkedAt=r.getValue("checkedAt"),
            householdUnit=r["householdUnit"].orEmpty(),basisAmountPerUnit=r["basisAmountPerUnit"]?.toDoubleOrNull(),basisUnit=r["basisUnit"].orEmpty(),
            servingSourceReference=r["servingSourceReference"].orEmpty(),servingEvidenceKind=r["servingEvidenceKind"].orEmpty(),
            servingSourceSize=r["servingSourceSize"].orEmpty(), recommendationReferenceAmount=r["recommendationReferenceAmount"]?.toDoubleOrNull(),
            recommendationReferenceUnit=r["recommendationReferenceUnit"].orEmpty(),recommendationSourceReference=r["recommendationSourceReference"].orEmpty()) })
    }
    fun writeAudit(seeds: List<RecommendationSeed>, plan: TodayMealPlan) {
        val out = File(root, "app/build/feedback-final-qa").apply { mkdirs() }
        val reduced = seeds.count { seed -> val linked = ingredients.getValue(seed.template.id)
            val max = seed.servingsByMeal.filterKeys { MealRecommendationEngine.supportsMeal(seed.template, it) }.values.flatten().maxOf { it.portion }
            linked.any { it.amount * max < (it.maximumAmount ?: it.amount * 1.5) } }
        File(out, "recommendation-serving-audit.json").writeText("{\"templates\":${seeds.size},\"templatesWithReducedMaximum\":$reduced,\"maximumSnackKcal\":350,\"targetKcal\":${plan.targetKcal},\"totalKcal\":${plan.totalKcal},\"meals\":[" +
            plan.meals.joinToString(",") { m -> "{\"meal\":\"${m.mealType}\",\"name\":\"${m.name.replace("\"", "\\\"")}\",\"kcal\":${m.kcal},\"portion\":${m.portion},\"amount\":\"${m.amountLabels.joinToString(" / ").replace("\"", "\\\"")}\"}" } + "]}")
    }
}
