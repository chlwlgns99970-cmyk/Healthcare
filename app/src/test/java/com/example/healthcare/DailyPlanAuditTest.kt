package com.example.healthcare

import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.*
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

/** Loads the real assets independently, then checks every runtime theme verdict against the offline audit. */
class DailyPlanAuditTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }
    private fun rows(file: String): List<Map<String, String>> {
        val lines = File(root, file).readLines()
        val headers = csv(lines.first())
        return lines.drop(1).filter(String::isNotBlank).map { headers.zip(csv(it)).toMap() }
    }
    private fun csv(line: String): List<String> {
        val values = mutableListOf<String>(); val value = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            when (val c = line[i]) {
                '"' -> if (quoted && i + 1 < line.length && line[i + 1] == '"') { value.append('"'); i++ } else quoted = !quoted
                ',' -> if (quoted) value.append(c) else { values += value.toString(); value.clear() }
                else -> value.append(c)
            }; i++
        }; values += value.toString(); return values
    }
    private fun seeds(): List<RecommendationSeed> {
        val assets = "app/src/main/assets/fooddata/"
        val foods = rows(assets + "food_items.csv").associateBy { it.getValue("id") }
        val ingredients = rows(assets + "meal_template_ingredients.csv").groupBy { it.getValue("mealTemplateId") }
        return rows(assets + "meal_templates.csv").map { t ->
            val linked = ingredients.getValue(t.getValue("id"))
            fun nutrient(key: String): Double? = linked.map { i ->
                val f = foods.getValue(i.getValue("foodItemId"))
                f[key]?.toDoubleOrNull()?.times(i.getValue("amount").toDouble() / f.getValue("referenceAmount").toDouble())
            }.takeIf { it.all { v -> v != null } }?.sumOf { requireNotNull(it) }
            RecommendationSeed(MealTemplate(t.getValue("id"), t.getValue("name"), t.getValue("supportedMealTypes"),
                requireNotNull(nutrient("energyKcal")).roundToInt(), nutrient("proteinGrams"), nutrient("carbohydrateGrams"), nutrient("fatGrams"),
                t.getValue("preparationMinutes").toInt(), t.getValue("costLevel"), t.getValue("tags"), t.getValue("allergens"),
                t.getValue("excludedDietTypes"), t["cuisineType"], t.getValue("source"), 0, 0),
                ingredientNames = linked.map { foods.getValue(it.getValue("foodItemId")).getValue("name") }.toSet(),
                ingredientCategories = linked.map { foods.getValue(it.getValue("foodItemId")).getValue("category") }.toSet(),
                ingredientInfoComplete = "|INGREDIENTS_COMPLETE|" in t.getValue("tags"),
                allergenTags = t.getValue("allergens").split('|').filter(String::isNotBlank).toSet())
        }
    }
    @Test fun auditHas292UniqueCompleteIdsAndExactNutrition() {
        val seeds = seeds()
        val audit = rows("data-source/recommendation/theme-eligibility-audit.csv")
        assertEquals(292, seeds.size); assertEquals(292, audit.size)
        assertEquals(292, audit.map { it.getValue("stableId") }.distinct().size)
        assertEquals(seeds.map { it.template.id }.toSet(), audit.map { it.getValue("stableId") }.toSet())
        val byId = seeds.associateBy { it.template.id }
        audit.forEach { r ->
            val seed = byId.getValue(r.getValue("stableId"))
            assertEquals(seed.template.totalKcal, r.getValue("kcal").toInt())
            listOf("carbs" to seed.template.carbohydrateGrams, "protein" to seed.template.proteinGrams, "fat" to seed.template.fatGrams).forEach { (key, value) ->
                val audited = r[key]?.toDoubleOrNull()
                if (value == null) assertNull(audited) else assertEquals(value, requireNotNull(audited), 0.000000001)
            }
        }
        assertEquals(292, seeds.count { it.template.totalKcal > 0 })
        assertEquals(261, seeds.count { DailyMealThemePolicy.complete(DailyMealThemePolicy.nutrition(it)) })
    }
    @Test fun allEightThemeAuditColumnsMatchRuntimeForAll292Templates() {
        val seeds = seeds(); val audit = rows("data-source/recommendation/theme-eligibility-audit.csv")
        val columns = listOf("light", "balanced", "hearty", "diet", "bulk", "healthy", "cheat", "slowAgingStyle")
        DailyRecommendationTheme.entries.zip(columns).forEach { (theme, column) ->
            val eligible = DailyMealPlanEngine.slots.flatMap { DailyMealThemePolicy.eligible(seeds, theme, it) }.map { it.template.id }.toSet()
            audit.forEach { row -> assertEquals("${theme.name} ${row["stableId"]}", row.getValue(column + "Eligible").toBoolean(), row.getValue("stableId") in eligible) }
        }
    }
    @Test fun real292TemplatesMakeFourEligibleDistinctMealsForAllSupportedThemes() {
        val seeds = seeds(); val pref = MealCoachRepository.defaultPreference().copy(maxPreparationMinutes = 120)
        DailyRecommendationTheme.entries.filterNot { it == DailyRecommendationTheme.SLOW_AGING_STYLE }.forEach { theme ->
            val start = System.nanoTime()
            val plan = DailyMealPlanEngine.generate("2026-10-01", 1850, theme, seeds, pref)
            assertNotNull("$theme", plan)
            val actual = requireNotNull(plan)
            assertEquals(DailyMealPlanEngine.slots, actual.meals.map { it.mealType })
            assertEquals(4, actual.meals.map { it.templateId }.distinct().size)
            assertEquals(actual.meals.sumOf { it.kcal }, actual.totalKcal)
            assertTrue(actual.meals.all { meal -> MealRecommendationEngine.supportsMeal(seeds.first { it.template.id == meal.templateId }.template, meal.mealType) })
            println("DAILY_PLAN_AUDIT ${theme.name}: ${actual.totalKcal}/1850 kcal ${(System.nanoTime()-start)/1_000_000}ms ${actual.meals.map { it.name }}")
        }
    }
    @Test fun dietReal1500PrioritizesAvailableMealBudgetRangesInsteadOfTinyBreakfast() {
        val plan=requireNotNull(DailyMealPlanEngine.generate("2026-10-01",1500,DailyRecommendationTheme.DIET,seeds(),
            MealCoachRepository.defaultPreference(),dislikes=setOf("오이")))
        assertTrue("Actual ${plan.totalKcal}/1500 ${plan.meals.map { it.name to it.kcal }}", kotlin.math.abs(plan.totalKcal-1500)<=75)
        assertTrue(plan.meals.first().kcal in 300..450)
        assertTrue(plan.meals[1].kcal in 375..525)
        assertTrue(plan.meals[2].kcal in 375..525)
        assertTrue(plan.meals[3].kcal in 75..225)
    }
}
