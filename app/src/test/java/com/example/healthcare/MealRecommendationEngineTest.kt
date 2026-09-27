package com.example.healthcare

import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.domain.MealRecommendationEngine
import com.example.healthcare.domain.RecommendationSeed
import com.example.healthcare.domain.RecommendationStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealRecommendationEngineTest {
    @Test
    fun lunchBudgetUsesTenPercentOrEightyKcalTolerance() {
        val results = MealRecommendationEngine.recommend(
            candidates = listOf(560, 570, 650, 730, 740).map { kcal -> seed("meal-$kcal", kcal) },
            budgetKcal = 650,
            excludedNames = emptySet(),
            allergyNames = emptySet(),
            preferredNames = emptySet(),
            dietType = "GENERAL",
            limit = 3
        )

        assertEquals(3, results.size)
        assertTrue(results.all { it.template.totalKcal in 570..730 })
    }

    @Test
    fun allergyMatchKeepsRecommendationAndCanBeWarned() {
        val results = MealRecommendationEngine.recommend(
            candidates = listOf(
                seed("peanut", 640, setOf("땅콩 소스")).copy(allergenTags = setOf("땅콩")),
                seed("safe", 650, setOf("현미밥"))
            ),
            budgetKcal = 650,
            excludedNames = emptySet(),
            allergyNames = setOf("땅콩"),
            preferredNames = emptySet(),
            dietType = "GENERAL",
            limit = 3
        )

        assertEquals(setOf("peanut", "safe"), results.map { it.template.id }.toSet())
        assertEquals(setOf("땅콩"), MealRecommendationEngine.matchedAllergens(
            seed("peanut", 640, setOf("땅콩 소스")).copy(allergenTags = setOf("땅콩")), setOf("땅콩")
        ))
    }

    @Test
    fun allergyAliasesWarnAndIncompleteIngredientMetadataDoesNotBlockRecommendation() {
        val results = MealRecommendationEngine.recommend(
            candidates = listOf(
                seed("egg", 650, setOf("계란말이")).copy(allergenTags = setOf("계란")),
                seed("unknown", 650).copy(ingredientInfoComplete = false),
                seed("verified", 650, setOf("현미밥"))
            ),
            budgetKcal = 650,
            excludedNames = emptySet(), allergyNames = setOf("달걀"),
            preferredNames = emptySet(), dietType = "GENERAL", limit = 10
        )
        assertEquals(setOf("egg", "unknown", "verified"), results.map { it.template.id }.toSet())
        assertEquals(setOf("달걀"), MealRecommendationEngine.matchedAllergens(
            seed("egg", 650, setOf("계란말이")).copy(allergenTags = setOf("계란")), setOf("달걀")
        ))
    }

    @Test
    fun foodNameAloneIsNotTreatedAsConfirmedAllergenMetadata() {
        val unverified = seed("shrimp-rice", 650, setOf("새우볶음밥"))
        assertTrue(MealRecommendationEngine.matchedAllergens(unverified, setOf("새우")).isEmpty())
    }

    @Test
    fun multipleConfirmedAllergensMatchAndAliasesNormalize() {
        val verified = seed("sandwich", 650).copy(allergenTags = setOf("밀", "계란", "우유"))
        assertEquals(
            setOf("밀", "달걀", "우유"),
            MealRecommendationEngine.matchedAllergens(verified, linkedSetOf("밀가루", "난류", "유제품"))
        )
    }

    @Test
    fun explicitAllergenTagProducesWarningWithoutExclusion() {
        val tagged = seed("milk", 650).copy(allergenTags = setOf("우유"))
        val safe = seed("rice", 650, setOf("현미밥"))
        val results = MealRecommendationEngine.recommend(
            listOf(tagged, safe), 650, emptySet(), setOf("유제품"), emptySet(), "GENERAL", limit = 10
        )
        assertEquals(setOf("milk", "rice"), results.map { it.template.id }.toSet())
        assertEquals(setOf("우유"), MealRecommendationEngine.matchedAllergens(tagged, setOf("유제품")))
    }

    @Test
    fun dislikedKeywordUsesNormalizedOneWaySubstringMatching() {
        fun named(id: String, name: String) = seed(id, 650).let { it.copy(template = it.template.copy(name = name)) }
        val results = MealRecommendationEngine.recommend(
            candidates = listOf(
                named("plain", "미역국"), named("beef", "소고기 미역국"),
                named("pollack", "북어미역국"), named("safe", "된장찌개")
            ),
            budgetKcal = 650,
            excludedNames = setOf(" 미역국 "), allergyNames = emptySet(),
            preferredNames = emptySet(), dietType = "GENERAL", limit = 10
        )
        assertEquals(listOf("safe"), results.map { it.template.id })
    }

    @Test
    fun cookingModeAndBudgetAreAppliedBeforeScoring() {
        val results = MealRecommendationEngine.recommend(
            candidates = listOf(
                seed("low-convenience", 650, tags = "|CONVENIENCE|", costLevel = "LOW"),
                seed("medium-convenience", 650, tags = "|CONVENIENCE|", costLevel = "MEDIUM"),
                seed("low-cook", 650, tags = "|COOK|", costLevel = "LOW")
            ),
            budgetKcal = 650,
            excludedNames = emptySet(),
            allergyNames = emptySet(),
            preferredNames = emptySet(),
            dietType = "GENERAL",
            cookingMode = "CONVENIENCE",
            budgetLevel = "LOW",
            limit = 3
        )

        assertEquals(listOf("low-convenience"), results.map { it.template.id })
    }

    @Test
    fun familiarModePrefersRecentlyConsumedTemplateWhenCaloriesMatch() {
        val recent = seed("recent", 650).copy(recentUseCount = 2)
        val new = seed("new", 650)

        val results = MealRecommendationEngine.recommend(
            candidates = listOf(new, recent),
            budgetKcal = 650,
            excludedNames = emptySet(),
            allergyNames = emptySet(),
            preferredNames = emptySet(),
            dietType = "GENERAL",
            recommendationDiversity = "FAMILIAR",
            limit = 2
        )

        assertEquals("recent", results.first().template.id)
    }

    @Test
    fun fallbackStagesOnlyRelaxTheNamedSoftCondition() {
        val candidates = listOf(
            seed("wider", 795),
            seed("closest", 1050)
        )
        fun search(stage: RecommendationStage) = MealRecommendationEngine.recommend(
            candidates, 650, emptySet(), emptySet(), emptySet(), "GENERAL",
            stage = stage, limit = 10
        ).map { it.template.id }

        assertTrue(search(RecommendationStage.EXACT).isEmpty())
        assertEquals(listOf("wider"), search(RecommendationStage.WIDER_CALORIES))
        assertTrue("closest" in search(RecommendationStage.CLOSEST_VERIFIED))
    }

    @Test
    fun preparationMinutesDoNotFilterOrInfluenceRecommendationScore() {
        val results = MealRecommendationEngine.recommend(
            candidates = listOf(
                seed("slow", 650, preparationMinutes = 120),
                seed("quick", 650, preparationMinutes = 5)
            ),
            budgetKcal = 650,
            excludedNames = emptySet(),
            allergyNames = emptySet(),
            preferredNames = emptySet(),
            dietType = "GENERAL",
            limit = 10
        )

        assertEquals(setOf("slow", "quick"), results.map { it.template.id }.toSet())
        assertEquals(results.first().score, results.last().score, 0.0)
    }

    @Test
    fun hardExclusionsAndMissingIngredientsSurviveEveryFallback() {
        val candidates = listOf(
            seed("allergy", 650, setOf("땅콩")),
            seed("excluded", 650, setOf("우유")),
            seed("diet", 650, excludedDietTypes = "|VEGETARIAN|"),
            seed("incomplete", 650).copy(dataCompleteness = 0.0),
            seed("safe", 900)
        )
        RecommendationStage.entries.forEach { stage ->
            val result = MealRecommendationEngine.recommend(
                candidates, 650, setOf("우유"), setOf("땅콩"), emptySet(), "VEGETARIAN",
                stage = stage, limit = 10
            )
            assertTrue(result.all { it.template.id in setOf("allergy", "safe") })
            assertTrue(result.any { it.template.id == "allergy" })
        }
    }

    @Test
    fun cookingModeOnlyExpandsAfterExplicitOptInAndSeenMealsStayHidden() {
        val candidates = listOf(
            seed("convenience", 650, tags = "|CONVENIENCE|"),
            seed("cook", 650, tags = "|COOK|")
        )
        val default = MealRecommendationEngine.recommend(candidates, 650, emptySet(), emptySet(),
            emptySet(), "GENERAL", cookingMode = "CONVENIENCE", limit = 10)
        assertEquals(listOf("convenience"), default.map { it.template.id })
        val expanded = MealRecommendationEngine.recommend(candidates, 650, emptySet(), emptySet(),
            emptySet(), "GENERAL", cookingMode = "CONVENIENCE", limit = 10,
            stage = RecommendationStage.EXPANDED_MODE,
            allowedCookingModes = setOf("CONVENIENCE", "COOK"),
            excludedTemplateIds = setOf("convenience"))
        assertEquals(listOf("cook"), expanded.map { it.template.id })
    }

    private fun seed(
        id: String,
        kcal: Int,
        foods: Set<String> = setOf("쌀밥"),
        tags: String = "",
        costLevel: String = "MEDIUM",
        preparationMinutes: Int = 10,
        excludedDietTypes: String = ""
    ) = RecommendationSeed(
        template = MealTemplate(
            id = id,
            name = id,
            supportedMealTypes = "|LUNCH|",
            totalKcal = kcal,
            preparationMinutes = preparationMinutes,
            costLevel = costLevel,
            tags = tags,
            excludedDietTypes = excludedDietTypes,
            source = "TEST",
            createdAt = 1,
            updatedAt = 1
        ),
        ingredientNames = foods
    )
}
