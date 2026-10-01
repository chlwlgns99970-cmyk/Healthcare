package com.example.healthcare

import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.FoodPreferencePolicy
import com.example.healthcare.domain.FoodPreferenceStyle
import com.example.healthcare.domain.MealRecommendationEngine
import com.example.healthcare.domain.MealRecommendationTheme
import com.example.healthcare.domain.MealRecommendationThemePolicy
import com.example.healthcare.domain.RecommendationSeed
import com.example.healthcare.domain.RecommendationStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationPersonalizationTest {
    @Test fun styleStorageReusesKeywordTokensAndNormalizesDuplicateSelections() {
        val stored = FoodPreferencePolicy.serialize(
            setOf(FoodPreferenceStyle.KOREAN, FoodPreferenceStyle.RICE),
            listOf("참치 김밥", "참치김밥", "", " 두부 ")
        )
        assertEquals("|STYLE:KOREAN|STYLE:RICE|참치김밥|두부|", stored)
        assertEquals(setOf(FoodPreferenceStyle.KOREAN, FoodPreferenceStyle.RICE),
            FoodPreferencePolicy.stylesFromStored(stored))
        assertEquals(listOf("참치김밥", "두부"), FoodPreferencePolicy.keywordsFromStored(stored))
        assertEquals("", FoodPreferencePolicy.serialize(emptySet(), emptyList()))
        assertEquals(setOf(FoodPreferenceStyle.NOODLE),
            FoodPreferencePolicy.stylesFromStored("|style:noodle|STYLE:NOODLE|"))
    }

    @Test fun stylesMatchCuisineAndLinkedCategoriesRatherThanDishNameGuesses() {
        val korean = seed("korean").withCuisine("KOREAN")
        val rice = seed("rice").copy(ingredientCategories = setOf("밥류"))
        val noodle = seed("noodle").copy(ingredientCategories = setOf("면 및 만두류"))
        val misleadingName = seed("한식밥국수")
        assertTrue(FoodPreferencePolicy.matches(korean, setOf(FoodPreferenceStyle.KOREAN.token)))
        assertTrue(FoodPreferencePolicy.matches(rice, setOf(FoodPreferenceStyle.RICE.token)))
        assertTrue(FoodPreferencePolicy.matches(noodle, setOf(FoodPreferenceStyle.NOODLE.token)))
        assertFalse(FoodPreferencePolicy.matches(misleadingName, FoodPreferenceStyle.entries.map { it.token }.toSet()))
        assertTrue(FoodPreferencePolicy.matches(seed("두부찌개"), setOf("두부")))
    }

    @Test fun noPreferenceKeepsCalorieRankingAndDoesNotClaimTastePersonalization() {
        val results = recommend(listOf(seed("near", 650), seed("far", 710)))
        assertEquals(listOf("near", "far"), results.map { it.template.id })
        assertTrue(results.none { "취향" in it.reason || it.appliedReasons.any { reason -> "취향" in reason } })
    }

    @Test fun actualPreferenceRaisesEligibleCandidateAndOnlyMatchedMenuClaimsTaste() {
        val matched = seed("preferred", 670).copy(ingredientCategories = setOf("면 및 만두류"))
        val unmatched = seed("default", 650)
        val results = recommend(listOf(unmatched, matched), setOf(FoodPreferenceStyle.NOODLE.token))
        assertEquals("preferred", results.first().template.id)
        assertTrue("취향" in results.first().reason)
        assertTrue("취향" !in results.last().reason)
    }

    @Test fun preferencesNeverOverrideMealEligibilityCaloriesDislikesDietCookingOrBudget() {
        val preferred = seed("preferred", 650).withCuisine("KOREAN")
        val wrongMeal = preferred.copy(template = preferred.template.copy(id = "breakfast", name = "breakfast", supportedMealTypes = "|BREAKFAST|"))
        val wrongCalories = preferred.copy(template = preferred.template.copy(id = "calories", name = "calories", totalKcal = 900))
        val disliked = preferred.copy(template = preferred.template.copy(id = "disliked", name = "오이국"))
        val excludedDiet = preferred.copy(template = preferred.template.copy(id = "diet", name = "diet", excludedDietTypes = "|VEGETARIAN|"))
        val wrongCooking = preferred.copy(template = preferred.template.copy(id = "dining", name = "dining", tags = "|DINING_OUT|"))
        val wrongBudget = preferred.copy(template = preferred.template.copy(id = "expensive", name = "expensive", costLevel = "HIGH"))
        val results = MealRecommendationEngine.recommend(
            listOf(wrongMeal, wrongCalories, disliked, excludedDiet, wrongCooking, wrongBudget, preferred),
            650, setOf("오이"), emptySet(), setOf(FoodPreferenceStyle.KOREAN.token), "VEGETARIAN",
            cookingMode = "COOK", budgetLevel = "LOW", mealType = MealType.LUNCH, limit = 10
        )
        assertEquals(listOf("preferred"), results.map { it.template.id })
    }

    @Test fun duplicateIdsAndEquivalentNamesCannotFillThreeSlots() {
        val first = seed("one").copy(template = seed("one").template.copy(name = "참치 김밥"))
        val sameName = seed("two").copy(template = seed("two").template.copy(name = "참치-김밥"))
        val other = seed("three")
        val results = recommend(listOf(first, first, sameName, other))
        assertEquals(2, results.size)
        assertEquals(2, results.map { it.template.id }.distinct().size)
    }

    @Test fun lightAndFillingUseSameMealPoolThirdsAndLeaveTemplateMetadataIntact() {
        val lunch = listOf(300, 400, 500, 600, 700, 800).map { seed("lunch-$it", it) }
        val breakfast = seed("breakfast", 50).let { it.copy(template = it.template.copy(supportedMealTypes = "|BREAKFAST|")) }
        val pool = lunch + breakfast
        val light = MealRecommendationThemePolicy.select(pool, MealRecommendationTheme.LIGHT, MealType.LUNCH)
        val filling = MealRecommendationThemePolicy.select(pool, MealRecommendationTheme.FILLING, MealType.LUNCH)
        assertEquals(setOf(300, 400), light.map { it.template.totalKcal }.toSet())
        assertEquals(setOf(700, 800), filling.map { it.template.totalKcal }.toSet())
        assertEquals(lunch.first().template, light.first().template)
        assertTrue(light.none { it.template.id == "breakfast" })
    }

    @Test fun calorieThemeDoesNotInventRelativeRankingWhenCandidatesAreEqual() {
        val pool = listOf(seed("one", 500), seed("two", 500))
        assertTrue(MealRecommendationThemePolicy.select(pool, MealRecommendationTheme.LIGHT, MealType.LUNCH).isEmpty())
        assertTrue(MealRecommendationThemePolicy.select(pool, MealRecommendationTheme.FILLING, MealType.LUNCH).isEmpty())
    }

    @Test fun balancedRequiresCompleteFiniteMacroValuesAndExplicitEnergyRatios() {
        fun macros(carbohydrate: Double?, protein: Double?, fat: Double?) = seed("test").template.copy(
            carbohydrateGrams = carbohydrate, proteinGrams = protein, fatGrams = fat
        )
        assertTrue(MealRecommendationThemePolicy.matchesBalanced(macros(55.0, 20.0, 100.0 / 9))) // 55/20/25%
        assertTrue(MealRecommendationThemePolicy.matchesBalanced(macros(45.0, 20.0, 140.0 / 9))) // 45/20/35%
        assertFalse(MealRecommendationThemePolicy.matchesBalanced(macros(70.0, 10.0, 80.0 / 9)))
        assertFalse(MealRecommendationThemePolicy.matchesBalanced(macros(null, 20.0, 10.0)))
        assertFalse(MealRecommendationThemePolicy.matchesBalanced(macros(50.0, Double.NaN, 10.0)))
        assertFalse(MealRecommendationThemePolicy.matchesBalanced(macros(50.0, 20.0, Double.POSITIVE_INFINITY)))
        assertFalse(MealRecommendationThemePolicy.matchesBalanced(macros(50.0, 0.0, 10.0)))
    }

    @Test fun everyThemeKeepsDislikesAndCycleIdsExcludedWhileAllergyStillWarns() {
        val safe = seed("safe", 650).withBalancedMacros().copy(allergenTags = setOf("우유"))
        val disliked = seed("오이무침", 650).withBalancedMacros()
        val seen = seed("seen", 670).withBalancedMacros()
        val pool = listOf(seed("low", 620).withBalancedMacros(), safe, disliked, seen, seed("high", 680).withBalancedMacros())
        MealRecommendationTheme.entries.forEach { theme ->
            val results = MealRecommendationEngine.recommend(
                pool, 650, setOf("오이"), setOf("우유"), emptySet(), "GENERAL",
                mealType = MealType.LUNCH, theme = theme, excludedTemplateIds = setOf("seen"), limit = 10
            )
            assertTrue(results.none { it.template.id in setOf("오이무침", "seen") })
            assertEquals(results.size, results.map { it.template.id }.distinct().size)
        }
        assertEquals(setOf("우유"), MealRecommendationEngine.matchedAllergens(safe, setOf("우유")))
        assertTrue(recommend(listOf(safe)).isNotEmpty())
    }

    @Test fun themesKeepCalorieToleranceAndReturnEmptyRatherThanRelaxingConditions() {
        val pool = listOf(seed("low", 200), seed("high", 1000))
        assertTrue(MealRecommendationEngine.recommend(
            pool, 650, emptySet(), emptySet(), emptySet(), "GENERAL",
            mealType = MealType.LUNCH, theme = MealRecommendationTheme.LIGHT
        ).isEmpty())
        assertTrue(MealRecommendationEngine.recommend(
            listOf(seed("missing")), 650, emptySet(), emptySet(), emptySet(), "GENERAL",
            mealType = MealType.LUNCH, theme = MealRecommendationTheme.BALANCED
        ).isEmpty())
    }

    @Test fun cycleExclusionsDoNotRedefineThemeBoundariesAndClosestDoesNotClaimBudgetMatch() {
        val pool = listOf(300, 400, 500, 600, 700, 800).map { seed("meal-$it", it) }
        val results = MealRecommendationEngine.recommend(
            pool, 650, emptySet(), emptySet(), emptySet(), "GENERAL",
            stage = RecommendationStage.CLOSEST_VERIFIED, mealType = MealType.LUNCH,
            theme = MealRecommendationTheme.LIGHT, excludedTemplateIds = setOf("meal-300", "meal-400")
        )
        assertTrue(results.isEmpty())
        val closest = MealRecommendationEngine.recommend(
            listOf(seed("far", 1400).copy(recentUseCount = 9)), 650,
            emptySet(), emptySet(), emptySet(), "GENERAL", stage = RecommendationStage.CLOSEST_VERIFIED
        ).single()
        assertFalse(closest.appliedReasons.any { "범위에 맞는" in it || "취향" in it || "피하고 싶은" in it })
    }

    @Test fun absentSeasonMetadataDoesNotOfferSeasonThemeAndInvalidCompletenessIsRejected() {
        assertEquals(setOf("LIGHT", "BALANCED", "FILLING"), MealRecommendationTheme.entries.map { it.name }.toSet())
        assertTrue(recommend(listOf(seed("invalid").copy(dataCompleteness = Double.NaN))).isEmpty())
    }

    private fun recommend(candidates: List<RecommendationSeed>, preferred: Set<String> = emptySet()) =
        MealRecommendationEngine.recommend(candidates, 650, emptySet(), emptySet(), preferred, "GENERAL", mealType = MealType.LUNCH)

    private fun RecommendationSeed.withCuisine(value: String) = copy(template = template.copy(cuisineType = value))
    private fun RecommendationSeed.withBalancedMacros() = copy(template = template.copy(
        carbohydrateGrams = 55.0, proteinGrams = 20.0, fatGrams = 100.0 / 9.0
    ))

    private fun seed(id: String, kcal: Int = 650) = RecommendationSeed(
        template = MealTemplate(
            id = id, name = id, supportedMealTypes = "|LUNCH|", totalKcal = kcal,
            preparationMinutes = 20, costLevel = "LOW", tags = "|COOK|", source = "TEST", createdAt = 1, updatedAt = 1
        ),
        ingredientNames = setOf("쌀밥")
    )
}
