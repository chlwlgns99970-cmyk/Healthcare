package com.example.healthcare

import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Only reviewed source groups and the changed theme audit; all 292 stable identities are retained. */
class SlowAgingStylePolicyTest {
    private companion object {
        val fixture by lazy { ServingAssetFixture() }
        val seeds by lazy { fixture.seeds() }
        val preference by lazy { MealCoachRepository.defaultPreference().copy(maxPreparationMinutes = 120) }
        val stylePlan by lazy { requireNotNull(DailyMealPlanEngine.generate("2026-10-04", 1500,
            DailyRecommendationTheme.SLOW_AGING_STYLE, seeds, preference)) }
    }

    private fun rows(relativePath: String): List<Map<String, String>> {
        val lines = File(fixture.root, relativePath).readLines().filter(String::isNotBlank)
        fun csv(line: String): List<String> {
            val result = mutableListOf<String>(); val value = StringBuilder(); var quoted = false; var index = 0
            while (index < line.length) {
                when (val ch = line[index]) {
                    '"' -> if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                        value.append('"'); index++
                    } else quoted = !quoted
                    ',' -> if (quoted) value.append(ch) else { result += value.toString(); value.clear() }
                    else -> value.append(ch)
                }; index++
            }; result += value.toString(); return result
        }
        val headers = csv(lines.first())
        return lines.drop(1).map { headers.zip(csv(it)).toMap() }
    }

    @Test fun publicRuleSourcesAndReviewedIngredientEvidenceAreTraceable() {
        assertEquals(listOf("https://www.who.int/news-room/fact-sheets/detail/healthy-diet",
            "https://www.nhlbi.nih.gov/health/dash-eating-plan"), SlowAgingStylePolicy.ruleSources)
        val source = rows("data-source/recommendation/verified-food-groups.csv")
        assertEquals(source.size, source.map { it.getValue("stableTemplateId") }.distinct().size)
        source.forEach { row ->
            val evidence = requireNotNull(SlowAgingStylePolicy.evidence(row.getValue("stableTemplateId")))
            assertEquals(row.getValue("ingredients").split('|').toSet(), evidence.ingredients)
            assertEquals(row.getValue("foodGroups").split('|').toSet(), evidence.groups)
            assertEquals(row.getValue("sourceUrl"), evidence.sourceUrl)
            assertTrue(evidence.sourceUrl.startsWith("https://") || evidence.sourceUrl.startsWith("http://"))
            assertTrue(row.getValue("sourceName").isNotBlank())
            assertTrue(row.getValue("notes").isNotBlank())
            assertTrue(evidence.evidenceScope in setOf("PUBLIC_DISH_DEFINITION", "PUBLIC_REFERENCE_RECIPE",
                "BRAND_OFFICIAL_MAJOR_INGREDIENTS"))
            assertEquals(row.getValue("verifiedAt"), evidence.verifiedAt)
        }
    }

    @Test fun foodGroupsRespectReviewedIngredientsWithoutAssumingWholeGrainOrVegetablesFromName() {
        val barley = requireNotNull(SlowAgingStylePolicy.evidence("kfind-catalog-d101-016000000-0001"))
        assertEquals(setOf("MIXED_GRAIN"), barley.groups)
        assertFalse("WHOLE_GRAIN" in barley.groups) // Milling is not given by the source.
        val rice = requireNotNull(SlowAgingStylePolicy.evidence("kfind-breakfast-cook-3"))
        assertTrue("GRAIN_UNSPECIFIED" in rice.groups)
        assertFalse("REFINED_GRAIN" in rice.groups) // A recipe saying rice does not establish milling.
        val tofu = requireNotNull(SlowAgingStylePolicy.evidence("kfind-catalog-d111-517000000-0001"))
        assertTrue("LEGUME_SOY" in tofu.groups)
        assertFalse("VEGETABLE" in tofu.groups) // Small seasoning vegetables do not certify a vegetable serving.
        val salad = seeds.single { it.template.id == "kfind-catalog-d214-640000000-0002" }
        assertEquals(setOf("POULTRY"), SlowAgingStylePolicy.groups(salad))
        assertFalse(SlowAgingStylePolicy.eligible(salad))
        val redMeat = seeds.single { it.template.id == "kfind-catalog-d106-297000000-0001" }
        assertTrue("RED_MEAT" in SlowAgingStylePolicy.groups(redMeat))
        assertEquals(5.0, SlowAgingStylePolicy.score(redMeat), 0.0)
        assertTrue(SlowAgingStylePolicy.eligible(redMeat)) // Verified beans/vegetables outweigh the red-meat penalty.
        val officialSandwich = seeds.single { it.template.id == "kfind-dinner-convenience-1" }
        assertTrue(SlowAgingStylePolicy.eligible(officialSandwich))
        assertEquals(5.0, SlowAgingStylePolicy.score(officialSandwich), 0.0)
    }

    @Test fun unknownIngredientsStayUnknownEvenWithHealthySoundingNameAndDishCategory() {
        val unknown = seeds.first().copy(template = seeds.first().template.copy(id = "unknown-whole-grain",
            name = "통곡물 콩 채소 샐러드"), ingredientNames = setOf("통곡물", "두부", "채소"),
            ingredientCategories = setOf("밥류", "샐러드"), ingredientInfoComplete = false)
        assertNull(SlowAgingStylePolicy.evidence(unknown.template.id))
        assertTrue(SlowAgingStylePolicy.groups(unknown).isEmpty())
        assertFalse(SlowAgingStylePolicy.eligible(unknown))
        assertEquals(0.0, SlowAgingStylePolicy.score(unknown), 0.0)
        val tofu = seeds.single { it.template.id == "kfind-catalog-d306-276000000-0001" }
        assertFalse(tofu.ingredientInfoComplete) // A reference recipe does not certify all ingredients/allergens.
        assertTrue(SlowAgingStylePolicy.eligible(tofu))
    }

    @Test fun actualBundledStyleMakesFourDistinctMealsWithDailyFoodGroupVariety() {
        val plan = stylePlan
        assertEquals(DailyMealPlanEngine.slots, plan.meals.map { it.mealType })
        assertEquals(4, plan.meals.map { it.templateId }.distinct().size)
        plan.meals.forEach { meal ->
            val seed = seeds.single { it.template.id == meal.templateId }
            assertTrue(SlowAgingStylePolicy.eligible(seed))
            assertNotNull(RecommendationServingPolicy.selected(seed, meal.mealType, meal.portion))
            assertEquals(SlowAgingStylePolicy.groups(seed), meal.foodGroups)
        }
        val groups = plan.meals.flatMap { it.foodGroups }.toSet()
        assertTrue(groups.any { it == "MIXED_GRAIN" || it == "WHOLE_GRAIN" })
        assertTrue("LEGUME_SOY" in groups)
        assertTrue("VEGETABLE" in groups)
        assertTrue(plan.meals.last().kcal <= minOf(RecommendationServingPolicy.MAX_SNACK_KCAL, 225))
        assertTrue(kotlin.math.abs(plan.totalKcal - 1500) <= 75)
        val out = File(fixture.root, "app/build/style-qa").apply { mkdirs() }
        fun quote(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        File(out, "actual-style-plan.json").writeText("{\"targetKcal\":${plan.targetKcal},\"totalKcal\":${plan.totalKcal},\"meals\":[" +
            plan.meals.joinToString(",") { meal -> "{\"meal\":${quote(meal.mealType.name)},\"name\":${quote(meal.name)},\"kcal\":${meal.kcal},\"portion\":${meal.portion},\"amount\":${quote(meal.amountLabels.joinToString(" / "))},\"foodGroups\":${quote(meal.foodGroups.joinToString("|"))}}" } + "]}")
    }

    @Test fun dislikedVerifiedIngredientExcludesItsDishAndStillAllowsFourMeals() {
        val dislikes = setOf("두부")
        val pool = DailyMealPlanEngine.candidates(seeds, DailyRecommendationTheme.SLOW_AGING_STYLE,
            preference, dislikes, emptySet())
        assertFalse(pool.values.flatten().any { it.template.id == "kfind-catalog-d306-276000000-0001" })
        assertFalse(pool.values.flatten().any { it.template.id == "kfind-catalog-d111-517000000-0001" })
        val plan = requireNotNull(DailyMealPlanEngine.generate("2026-10-04", 1500,
            DailyRecommendationTheme.SLOW_AGING_STYLE, seeds, preference, dislikes = dislikes))
        assertEquals(4, plan.meals.size)
        assertTrue(plan.meals.none { meal -> SlowAgingStylePolicy.evidence(requireNotNull(meal.templateId))
            ?.ingredients.orEmpty().any { "두부" in it } })
    }

    @Test fun styleRetainsTheExistingConfiguredTargetWithoutAutomaticDeficitOrSurplus() {
        val target = requireNotNull(DailyCalorieTarget.resolve(CalorieGoal(targetCalories = 1500,
            startDate = "2026-10-04"), null))
        assertEquals(target, stylePlan.targetKcal)
        assertEquals(1500, stylePlan.remainingBudgetKcal)
        assertEquals(stylePlan.meals.sumOf { it.kcal }, stylePlan.totalKcal)
        assertTrue(stylePlan.reasons.contains("현재 하루 목표 1500 kcal 기준"))
    }

    @Test fun userExplanationStatesCompositionAndVariationWithoutHealthPromises() {
        assertTrue(SlowAgingStylePolicy.DESCRIPTION in stylePlan.reasons)
        assertTrue(SlowAgingStylePolicy.VARIATION_NOTE in stylePlan.reasons)
        val text = (stylePlan.reasons + DailyRecommendationTheme.SLOW_AGING_STYLE.description).joinToString(" ")
        listOf("노화를 늦", "수명이 늘", "치매를 예방", "질병을 막", "효과를 보장").forEach {
            assertFalse(it, text.contains(it))
        }
        assertTrue(text.contains("식품군"))
    }

    @Test fun styleAuditHas292UniqueStableIdsAndEverySourcedVerdictMatchesRuntime() {
        val audit = rows("data-source/recommendation/theme-eligibility-audit.csv")
        assertEquals(292, seeds.size)
        assertEquals(292, audit.size)
        assertEquals(292, audit.map { it.getValue("stableId") }.distinct().size)
        assertEquals(seeds.map { it.template.id }.toSet(), audit.map { it.getValue("stableId") }.toSet())
        audit.forEach { row ->
            val seed = seeds.single { it.template.id == row.getValue("stableId") }
            assertEquals(seed.template.name, row.getValue("menuName"))
            assertEquals(row.getValue("slowAgingStyleEligible").toBoolean(), SlowAgingStylePolicy.eligible(seed))
            assertEquals(row.getValue("verifiedFoodGroups").split('|').filter(String::isNotBlank).toSet(),
                SlowAgingStylePolicy.groups(seed))
            assertEquals(row.getValue("groupSource"), SlowAgingStylePolicy.evidence(seed.template.id)?.sourceUrl.orEmpty())
        }
        assertEquals(168, audit.count { it.getValue("verifiedFoodGroups").isNotBlank() })
        assertEquals(188, audit.count { it.getValue("majorIngredientInformationKnown").toBoolean() })
        val candidates = seeds.filter(SlowAgingStylePolicy::eligible)
        assertEquals(120, candidates.size)
        assertEquals(listOf(48, 105, 106, 8), DailyMealPlanEngine.slots.map { meal ->
            DailyMealThemePolicy.eligible(seeds, DailyRecommendationTheme.SLOW_AGING_STYLE, meal).size })
    }

    @Test fun actualStyleSupportsFiveUnseenFourMealCombinationsAtCurrent1500Target() {
        val seen = DailyMealPlanEngine.slots.associateWith { mutableSetOf<String>() }
        val signatures = mutableSetOf<String>()
        val plans = (1..5).map {
            val plan = requireNotNull(DailyMealPlanEngine.generate("2026-10-04", 1500,
                DailyRecommendationTheme.SLOW_AGING_STYLE, seeds, preference,
                seen = seen.mapValues { entry -> entry.value.toSet() }, excludedSignatures = signatures.toSet()))
            assertEquals(1500, plan.targetKcal)
            assertEquals(4, plan.meals.map { it.templateId }.distinct().size)
            assertTrue("1500 kcal ±5%: ${plan.totalKcal}", kotlin.math.abs(plan.totalKcal - 1500) <= 75)
            assertTrue(plan.meals.last().kcal <= 225)
            assertTrue(signatures.add(plan.signature))
            plan.meals.forEach { meal ->
                val id = requireNotNull(meal.templateId)
                assertTrue(seen.getValue(meal.mealType).add(id))
                val seed = seeds.single { it.template.id == id }
                assertTrue(SlowAgingStylePolicy.eligible(seed))
                val amount = requireNotNull(RecommendationServingPolicy.selected(seed, meal.mealType, meal.portion))
                assertEquals(amount.kcal, meal.kcal)
                assertEquals(amount.labels, meal.amountLabels)
            }
            plan
        }
        assertEquals(5, seen.getValue(MealType.SNACK).size)
        val out = File(fixture.root, "app/build/style-qa").apply { mkdirs() }
        File(out, "actual-style-alternates.csv").writeText("plan,targetKcal,totalKcal,mealType,templateId,kcal,portion,amount\n" +
            plans.flatMapIndexed { index, plan -> plan.meals.map { meal ->
                "${index + 1},${plan.targetKcal},${plan.totalKcal},${meal.mealType},${meal.templateId},${meal.kcal},${meal.portion},\"${meal.amountLabels.joinToString(" / ").replace("\"", "\"\"")}\""
            } }.joinToString("\n"))
    }

    @Test fun every292IdentityHasAResearchResultWithoutPromotingReferenceAllergensToSafe() {
        val research = rows("data-source/recommendation/recommendation-ingredient-research.csv")
        assertEquals(seeds.map { it.template.id }.toSet(), research.map { it.getValue("stableTemplateId") }.toSet())
        assertEquals(292, research.size)
        research.forEach { row ->
            assertTrue(row.getValue("reason").isNotBlank())
            assertTrue(row.getValue("identityMatchReason").isNotBlank())
            assertEquals("UNKNOWN_REFERENCE_ONLY", row.getValue("allergenStatus"))
            assertTrue(row.getValue("ingredientStatus") in setOf("REFERENCE_PARTIAL", "PARTIAL_DESCRIPTION", "UNKNOWN"))
            if (row.getValue("researchStatus") == "SOURCED_PARTIAL_COMPOSITION") {
                assertTrue(row.getValue("sourceUrl").startsWith("https://") || row.getValue("sourceUrl").startsWith("http://"))
                assertTrue(row.getValue("sourceReference").isNotBlank())
            }
        }
        val old = rows("data-source/recommendation/recommendation-292-audit.csv")
        assertEquals(36, old.count { it.getValue("ingredientCompleteness") == "COMPLETE" })
        assertEquals(255, old.count { it.getValue("ingredientCompleteness") == "UNKNOWN" })
    }
}
