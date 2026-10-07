package com.example.healthcare

import com.example.healthcare.data.RecommendationCyclePolicy
import com.example.healthcare.data.RecommendationCycleSnapshot
import com.example.healthcare.data.entity.FrequentFood
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RecommendationLearningPolicyTest {
    private val now = Instant.parse("2026-10-02T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L
    private fun seed(id: String, name: String = id, meal: MealType = MealType.LUNCH, kcal: Int = 500) =
        RecommendationSeed(MealTemplate(id, name, "|${meal.name}|", kcal, 20.0, 65.0, 10.0,
            10, "LOW", "|COOK|INGREDIENTS_COMPLETE|", "", "", "KOREAN", "verified", 0, 0), setOf(name))
    private fun record(id: Long = 1, name: String = "a", time: Long = now) = MealRecord(id = id,
        date = "2026-10-02", time = "12:00", mealType = MealType.LUNCH, foodName = name, calories = 500,
        createdAtEpochMillis = time)
    private fun favorite(name: String = "a") = FrequentFood(id = 1, foodName = name,
        defaultServing = "100g", calories = 500, isFavorite = true, foodItemId = "food-$name")
    private fun scores(records: List<MealRecord> = emptyList(), favorites: List<FrequentFood> = emptyList(),
        state: RecommendationLearningSnapshot = RecommendationLearningSnapshot()) =
        RecommendationLearningPolicy.scores(listOf(seed("a"), seed("b")), records, favorites, state, now)
    private fun rank(seeds: List<RecommendationSeed>, learned: Map<String, Double>, preferred: Set<String> = emptySet(),
        excluded: Set<String> = emptySet(), stage: RecommendationStage = RecommendationStage.EXACT) =
        MealRecommendationEngine.recommend(seeds, 500, excluded, emptySet(), preferred, "GENERAL", limit = 10,
            stage = stage, mealType = MealType.LUNCH, learnedScores = learned)

    @Test fun favoriteIsPositiveAndDoesNotRewardUnrelatedFoods() {
        val result = scores(favorites = listOf(favorite()))
        assertEquals(3.0, result.getValue("a"), 0.0); assertEquals(0.0, result.getValue("b"), 0.0)
    }
    @Test fun actualRecordIsPositiveAndLinkedRecommendationAddsOnlyWeakExtra() {
        assertEquals(2.0, scores(records = listOf(record())).getValue("a"), 0.0)
        assertEquals(2.25, scores(records = listOf(record().copy(source = RecordSource.RECOMMENDATION))).getValue("a"), 0.0)
    }
    @Test fun recordScoreDecaysAndRecordsOutsideWindowHaveNoInfluence() {
        assertEquals(1.0, scores(records = listOf(record(time = now - 30 * day))).getValue("a"), 0.00001)
        assertEquals(0.0, scores(records = listOf(record(time = now - 91 * day))).getValue("a"), 0.0)
    }
    @Test fun legacyZeroTimestampUsesKnownRecordDateAndResetStillExcludesIt() {
        val legacy = record().copy(createdAtEpochMillis = 0)
        assertTrue(scores(records = listOf(legacy)).getValue("a") > 0)
        val reset = RecommendationLearningPolicy.reset(listOf(legacy), emptyList(), now - 1)
        assertEquals(0.0, scores(records = listOf(legacy), state = reset).getValue("a"), 0.0)
    }
    @Test fun repeatedRecordsAndFavoriteAreCapped() {
        val rows = (1..400).map { record(it.toLong()) }
        assertEquals(RecommendationLearningPolicy.MAX_SCORE,
            scores(records = rows, favorites = listOf(favorite())).getValue("a"), 0.0)
    }
    @Test fun replacementIsWeakNegativeWithCapAndDecay() {
        val state = RecommendationLearningSnapshot(rejections = mapOf("a" to RecommendationRejection(1000, now)))
        assertEquals(-2.0, scores(state = state).getValue("a"), 0.0)
        val aged = state.copy(rejections = mapOf("a" to RecommendationRejection(1000, now - 14 * day)))
        assertEquals(-1.0, scores(state = aged).getValue("a"), 0.00001)
    }
    @Test fun passiveExposureAndDetailWithoutRecordHaveNoSignal() {
        assertTrue(scores().values.all { it == 0.0 })
    }
    @Test fun resetZeroesRetainedFavoritesRecordsAndReplacementSignals() {
        val rows = listOf(record(8))
        val favorites = listOf(favorite())
        val reset = RecommendationLearningPolicy.reset(rows, favorites, now)
        assertTrue(scores(rows, favorites, reset).values.all { it == 0.0 })
        assertEquals(8L, rows.single().id); assertTrue(favorites.single().isFavorite)
    }
    @Test fun onlyNewRecordAfterResetContributesEvenWhenOldRecordIsEdited() {
        val reset = RecommendationLearningPolicy.reset(listOf(record(8)), emptyList(), now - 100)
        val rows = listOf(record(8, time = now - 1), record(9, time = now - 1))
        assertEquals(2.0, scores(records = rows, state = reset).getValue("a"), 0.00001)
    }
    @Test fun positiveAndNegativeChangeOnlyWeakOrdering() {
        val seeds = listOf(seed("a"), seed("b"))
        assertEquals("b", rank(seeds, mapOf("b" to 3.0)).first().template.id)
        assertEquals("b", rank(seeds, mapOf("a" to -0.75)).first().template.id)
    }
    @Test fun explicitPreferenceAlwaysOutranksBehaviorWithinEligibleRange() {
        val seeds = listOf(seed("a", kcal = 575), seed("b"))
        assertEquals("a", rank(seeds, mapOf("a" to -999.0, "b" to 999.0), setOf("a")).first().template.id)
    }
    @Test fun dislikeAndMealEligibilityCannotBeBypassedByLearning() {
        val seeds = listOf(seed("a"), seed("b"), seed("wrong", meal = MealType.DINNER))
        assertEquals(listOf("b"), rank(seeds, mapOf("a" to 999.0, "wrong" to 999.0), excluded = setOf("a")).map { it.template.id })
    }
    @Test fun hardCalorieRangeAndClosestFitOutrankBehavior() {
        val seeds = listOf(seed("a", kcal = 700), seed("b"))
        assertEquals(listOf("b"), rank(seeds, mapOf("a" to 999.0)).map { it.template.id })
        assertEquals("b", rank(seeds, mapOf("a" to 999.0), stage = RecommendationStage.CLOSEST_VERIFIED).first().template.id)
    }
    @Test fun fullCycleDoesNotRepeatEvenWithStrongLearnedCandidate() {
        val order = rank(listOf(seed("a"), seed("b")), mapOf("a" to 4.0)).map { it.template.id }
        val selection = RecommendationCyclePolicy.select(order, RecommendationCycleSnapshot(setOf("a"), "a"), 1)
        assertEquals(listOf("b"), selection.selectedTemplateIds)
    }
    @Test fun nonfiniteScoresAreIgnoredAndTieBreakRemainsStable() {
        assertEquals("a", rank(listOf(seed("b"), seed("a")), mapOf("b" to Double.NaN)).first().template.id)
    }
    private fun dailySeeds() = listOf(seed("a", meal = MealType.BREAKFAST), seed("b", meal = MealType.BREAKFAST),
        seed("lunch"), seed("dinner", meal = MealType.DINNER), seed("snack", meal = MealType.SNACK, kcal = 200))
    private fun daily(learned: Map<String, Double>, preferred: String = "", seen: Set<String> = emptySet()) =
        requireNotNull(DailyMealPlanEngine.generate("2026-10-02", 1700, DailyRecommendationTheme.CHEAT, dailySeeds(),
            MealCoachRepository.defaultPreference().copy(preferredFoods = preferred),
            seen = mapOf(MealType.BREAKFAST to seen), learnedScores = learned))
    @Test fun dailyPlanLearningBreaksEqualFitTieButExplicitTasteWins() {
        assertEquals("b", daily(mapOf("b" to 4.0)).meals.first().templateId)
        assertEquals("a", daily(mapOf("a" to -2.0, "b" to 4.0), "|a|").meals.first().templateId)
    }
    @Test fun dailyPlanKeepsUnseenCycleRegardlessOfLearnedScore() {
        assertEquals("b", daily(mapOf("a" to 4.0), seen = setOf("a")).meals.first().templateId)
    }
}
