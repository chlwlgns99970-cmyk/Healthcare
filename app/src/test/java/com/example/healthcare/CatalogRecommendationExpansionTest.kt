package com.example.healthcare

import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After

/** Only the new source compositions and eight exact measured choices. */
class CatalogRecommendationExpansionTest {
    private val fixture by lazy { ServingAssetFixture() }
    private val seeds by lazy { fixture.seeds() }
    @Before fun loadEvidence() { fixture.installLinkedMetadata() }
    @After fun resetEvidence() { FoodMetadataPolicy.install(emptyList()) }

    @Test fun newOfficialCompositionExpandsBreakfastAndSnackWithoutChangingTheRules() {
        assertEquals(292, seeds.size)
        assertEquals(292, seeds.map { it.template.id }.distinct().size)
        assertEquals(120, seeds.count(SlowAgingStylePolicy::eligible))
        assertEquals(listOf(48, 105, 106, 8), DailyMealPlanEngine.slots.map { meal ->
            DailyMealThemePolicy.eligible(seeds, DailyRecommendationTheme.SLOW_AGING_STYLE, meal).size
        })
        val cabbage = requireNotNull(SlowAgingStylePolicy.evidence("kfind-catalog-d104-194000000-0001"))
        assertEquals(setOf("쌀", "당근", "호박", "양파"), cabbage.ingredients)
        assertEquals(setOf("GRAIN_UNSPECIFIED", "VEGETABLE"), cabbage.groups)
        val rice = requireNotNull(SlowAgingStylePolicy.evidence("kfind-catalog-d101-039000000-0001"))
        assertTrue("팥" in rice.ingredients)
        assertTrue("LEGUME_SOY" in rice.groups)
        assertFalse("WHOLE_GRAIN" in rice.groups)
        val wrap = seeds.single { it.template.id == "kfind-catalog-d202-083000000-0021" }
        val source = requireNotNull(SlowAgingStylePolicy.evidence(wrap.template.id))
        assertTrue(SlowAgingStylePolicy.eligible(wrap))
        assertEquals(setOf("식물성 텐더", "채소"), source.ingredients)
        assertEquals(setOf("VEGETABLE"), source.groups)
        assertFalse("LEGUME_SOY" in source.groups)
        assertFalse(wrap.ingredientInfoComplete)
        assertEquals("BRAND_OFFICIAL_MAJOR_INGREDIENTS", source.evidenceScope)
        assertTrue(source.sourceUrl.startsWith("https://cjnews.cj.net/"))
        // Neither the dish spelling join nor verified vegetables cancel the
        // existing red-meat moderation weight.
        assertFalse(SlowAgingStylePolicy.eligible(seeds.single {
            it.template.id == "kfind-catalog-d103-168000000-0001"
        }))
    }

    @Test fun reviewedMeasuredReferencesStopCategoryExpansionAndPreserveRiceBowlChoices() {
        val expected = mapOf(
            "d305-233000000-0001" to 150.0,
            "d303-177000000-0002" to 100.0,
            "d202-115000000-0001" to 41.0,
            "d106-289000000-0001" to 300.0,
            "d306-267000000-0001" to 300.0,
            "d306-276000000-0001" to 500.0,
            "d308-374000000-0001" to 100.0,
            "d101-049000000-0001" to 360.0
        )
        expected.forEach { (code, grams) ->
            val seed = seeds.single { it.template.id == "kfind-catalog-$code" }
            val metadata = requireNotNull(FoodMetadataPolicy.lookup("kfind-$code"))
            assertEquals(grams, requireNotNull(metadata.recommendationReferenceAmount), 0.0)
            val options = RecommendationServingPolicy.available(seed, MealType.LUNCH)
            assertEquals(1, options.size)
            assertTrue(options.single().labels.single().endsWith(" · ${RecordedAmountSnapshot.format(grams)}g"))
        }
        listOf("d301-027000000-0001", "d101-006000000-0001").forEach { code ->
            val seed = seeds.single { it.template.id == "kfind-catalog-$code" }
            assertNull(FoodMetadataPolicy.lookup("kfind-$code")?.recommendationReferenceAmount)
            assertEquals(listOf("0.5공기", "1공기", "1.5공기"),
                RecommendationServingPolicy.available(seed, MealType.LUNCH).map { it.labels.single().substringAfterLast(" · ") })
        }
        assertEquals(150.0, requireNotNull(FoodMetadataPolicy.lookup("kfind-d305-239000000-0001")?.recommendationReferenceAmount), 0.0)
        assertEquals(50.0, requireNotNull(FoodMetadataPolicy.lookup("kfind-d110-472000000-0001")?.recommendationReferenceAmount), 0.0)
    }

    @Test fun expandedSourcePoolStillMakesFiveUnseenFourMealPlansAtTheExistingTarget() {
        val preference = MealCoachRepository.defaultPreference().copy(maxPreparationMinutes = 120)
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
                val selected = requireNotNull(RecommendationServingPolicy.selected(seed, meal.mealType, meal.portion))
                assertEquals(selected.kcal, meal.kcal)
                assertEquals(selected.labels, meal.amountLabels)
            }
            plan
        }
        assertEquals(5, seen.getValue(MealType.SNACK).size)
        val out = File(fixture.root, "app/build/catalog-recommendation").apply { mkdirs() }
        File(out, "actual-style-alternates.csv").writeText("plan,targetKcal,totalKcal,mealType,templateId,kcal,portion,amount\n" +
            plans.flatMapIndexed { index, plan -> plan.meals.map { meal ->
                "${index + 1},${plan.targetKcal},${plan.totalKcal},${meal.mealType},${meal.templateId},${meal.kcal},${meal.portion},\"${meal.amountLabels.joinToString(" / ").replace("\"", "\"\"")}\""
            } }.joinToString("\n"))
    }
}
