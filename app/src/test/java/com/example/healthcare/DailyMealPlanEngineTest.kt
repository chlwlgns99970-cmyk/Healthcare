package com.example.healthcare

import com.example.healthcare.data.*
import com.example.healthcare.data.entity.*
import com.example.healthcare.data.model.*
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.*
import org.junit.Assert.*
import org.junit.Test

class DailyMealPlanEngineTest {
    private val pref = MealCoachRepository.defaultPreference()
    private val theme = DailyRecommendationTheme.CHEAT
    private fun seed(id: String, meal: MealType, kcal: Int, protein: Double? = 20.0, name: String = id,
        ingredients: Set<String> = setOf(name), category: String = "밥류") = RecommendationSeed(
        MealTemplate(id, name, "|${meal.name}|", kcal, protein, 60.0, 12.0, 10, "LOW", "|COOK|INGREDIENTS_COMPLETE|",
            "|대두|", "", "KOREAN", "verified", 0, 0), ingredients, setOf("대두"), ingredientCategories = setOf(category))
    private fun fixtures() = DailyMealPlanEngine.slots.flatMap { meal ->
        (0..5).map { n -> seed("${meal.name}-$n", meal, (if (meal == MealType.SNACK) 150 else 550) + n * 20) }
    }
    private fun plan(target: Int? = 2000, seeds: List<RecommendationSeed> = fixtures(), t: DailyRecommendationTheme = theme,
        dislikes: Set<String> = emptySet(), records: List<MealRecord> = emptyList(),
        fixed: Map<MealType, DailyPlanMeal> = emptyMap(), excluded: Set<String> = emptySet(),
        preference: UserMealPreference = pref, seen: Map<MealType, Set<String>> = emptyMap()) =
        DailyMealPlanEngine.generate("2026-10-01", target, t, seeds, preference, dislikes, setOf("대두"), records, fixed, seen, excluded)
    @Test fun eightStylesExistAndNoHealthEffectsAreClaimed() {
        assertEquals(8, DailyRecommendationTheme.entries.size)
        val copy = DailyRecommendationTheme.entries.joinToString { it.label + it.description }
        listOf("살이 빠", "근육이 생", "노화를 늦", "치료", "질병 예방").forEach { assertFalse(copy.contains(it)) }
    }
    @Test fun targetMissingHasNoImplicit2000() { assertNull(plan(null)); assertNull(DailyCalorieTarget.resolve(null,null)) }
    @Test fun manualTargetUsesSaved1850() { assertEquals(1850, DailyCalorieTarget.resolve(CalorieGoal(targetCalories=1850,startDate="2026-10-01"),null)) }
    @Test fun configuredBmrTargetUsesExistingMode() {
        assertEquals(1600, DailyCalorieTarget.resolve(null, EnergyProfileHistory(basalMetabolicRateKcal=1600,activityLevelCode=ActivityLevel.MODERATE,palMultiplier=1.6,targetMode=TargetMode.BMR,effectiveFromDate="2026-10-01")))
    }
    @Test fun configuredMaintenanceUsesExistingMode() {
        assertEquals(2560, DailyCalorieTarget.resolve(null, EnergyProfileHistory(basalMetabolicRateKcal=1600,activityLevelCode=ActivityLevel.MODERATE,palMultiplier=1.6,targetMode=TargetMode.MAINTENANCE,effectiveFromDate="2026-10-01")))
    }
    @Test fun manualProfileWithoutGoalStillHasNoTarget() {
        assertNull(DailyCalorieTarget.resolve(null, EnergyProfileHistory(basalMetabolicRateKcal=1600,activityLevelCode=ActivityLevel.MODERATE,palMultiplier=1.6,targetMode=TargetMode.MANUAL,effectiveFromDate="2026-10-01")))
    }
    @Test fun negativeTargetCannotGenerate() { assertNull(plan(-1)) }
    @Test fun zeroManualGoalCannotBecomeDefaultTarget() { assertNull(DailyCalorieTarget.resolve(CalorieGoal(targetCalories=0,startDate="2026-10-01"),null)) }
    @Test fun targetChangeRegeneratesAgainstNewBudget() { assertNotEquals(requireNotNull(plan(1850)).totalKcal,requireNotNull(plan(2200)).totalKcal) }
    @Test fun invalidSourceCannotEnterAnySlot() { assertNull(plan(seeds=fixtures().map { it.copy(template=it.template.copy(source="")) })) }
    @Test fun dietRetainsMissingProteinInsteadOfEstimatingIt() { val p=requireNotNull(plan(t=DailyRecommendationTheme.DIET,seeds=fixtures().map { it.copy(template=it.template.copy(proteinGrams=null)) })); assertNull(p.nutrition.proteinGrams) }
    @Test fun bulkRetainsMissingProteinInsteadOfEstimatingIt() { val p=requireNotNull(plan(t=DailyRecommendationTheme.BULK,seeds=fixtures().map { it.copy(template=it.template.copy(proteinGrams=null)) })); assertNull(p.nutrition.proteinGrams) }
    @Test fun allFourAlreadyRecordedMealsAreKeptWithoutRecommendations() {
        val rows=DailyMealPlanEngine.slots.mapIndexed { i,m -> MealRecord(id=i.toLong(),date="2026-10-01",time="08:00",mealType=m,foodName="기록$i",calories=500) }
        val p=requireNotNull(plan(seeds=emptyList(),records=rows)); assertEquals(2000,p.totalKcal); assertTrue(p.meals.all { it.recorded })
    }
    @Test fun real2000PlanContainsFourSlotsAndExactSum() {
        val p=requireNotNull(plan()); assertEquals(4,p.meals.size); assertEquals(DailyMealPlanEngine.slots,p.meals.map { it.mealType }); assertEquals(p.meals.sumOf { it.kcal },p.totalKcal)
    }
    @Test fun actual1850IsNotReplacedWith2000() { assertEquals(1850,requireNotNull(plan(1850)).targetKcal) }
    @Test fun withinFivePercentOutranksOutsideFive() { assertTrue(kotlin.math.abs(requireNotNull(plan()).totalKcal-2000)<=100) }
    @Test fun withinTenFallbackIsTruthful() {
        val s=DailyMealPlanEngine.slots.map { seed(it.name,it,if(it==MealType.SNACK)250 else 650) }
        val p=requireNotNull(plan(seeds=s)); assertEquals(2200,p.totalKcal); assertTrue(p.differenceText.contains("±10%"))
    }
    @Test fun closestFarPlanReportsActualDifference() {
        val s=DailyMealPlanEngine.slots.map { seed(it.name,it,100) }; val p=requireNotNull(plan(seeds=s)); assertEquals(400,p.totalKcal); assertTrue(p.differenceText.contains("1600")); assertTrue(p.differenceText.contains("낮은"))
    }
    @Test fun breakfastEligibilityIsRequired() { assertNull(plan(seeds=fixtures().filterNot { it.template.supportedMealTypes.contains("BREAKFAST") })) }
    @Test fun lunchEligibilityIsRequired() { assertNull(plan(seeds=fixtures().filterNot { it.template.supportedMealTypes.contains("LUNCH") })) }
    @Test fun dinnerEligibilityIsRequired() { assertNull(plan(seeds=fixtures().filterNot { it.template.supportedMealTypes.contains("DINNER") })) }
    @Test fun snackEligibilityIsRequired() { assertNull(plan(seeds=fixtures().filterNot { it.template.supportedMealTypes.contains("SNACK") })) }
    @Test fun stableIdsAreDistinct() { assertEquals(4,requireNotNull(plan()).meals.map { it.templateId }.distinct().size) }
    @Test fun canonicalNamesAreDistinctEvenDifferentIds() {
        assertNull(plan(seeds=DailyMealPlanEngine.slots.mapIndexed { i,m -> seed("id$i",m,500,name="같은 메뉴") }))
    }
    @Test fun lightOnlySelectsLowerThirdPerSlot() {
        val p=requireNotNull(plan(t=DailyRecommendationTheme.LIGHT)); assertTrue(p.meals.all { it.templateId!!.endsWith("-0") || it.templateId.endsWith("-1") })
    }
    @Test fun heartyOnlySelectsUpperThirdPerSlot() {
        val p=requireNotNull(plan(t=DailyRecommendationTheme.HEARTY)); assertTrue(p.meals.all { it.templateId!!.endsWith("-4") || it.templateId.endsWith("-5") })
    }
    @Test fun balancedRejectsMissingMacros() { assertNull(plan(t=DailyRecommendationTheme.BALANCED,seeds=fixtures().map { it.copy(template=it.template.copy(proteinGrams=null)) })) }
    @Test fun balancedUsesAppRatios() { assertTrue(requireNotNull(plan(t=DailyRecommendationTheme.BALANCED)).meals.all { DailyMealThemePolicy.complete(it.nutrition) }) }
    @Test fun dietPreservesTargetAndKnownProtein() { val p=requireNotNull(plan(t=DailyRecommendationTheme.DIET)); assertEquals(2000,p.targetKcal); assertTrue(p.meals.all { it.nutrition.proteinGrams != null }) }
    @Test fun bulkPreservesTargetAndActualProtein() { val p=requireNotNull(plan(t=DailyRecommendationTheme.BULK)); assertEquals(2000,p.targetKcal); assertTrue(p.meals.all { it.nutrition.proteinGrams==20.0 }) }
    @Test fun healthyRejectsUnknownNutrition() { assertNull(plan(t=DailyRecommendationTheme.HEALTHY,seeds=fixtures().map { it.copy(template=it.template.copy(fatGrams=null)) })) }
    @Test fun healthyUsesExplicitNearBalancedRule() { assertNotNull(plan(t=DailyRecommendationTheme.HEALTHY)) }
    @Test fun cheatDoesNotChangeOrBypassTarget() { val p=requireNotNull(plan()); assertEquals(2000,p.targetKcal); assertTrue(kotlin.math.abs(p.totalKcal-2000)<=100) }
    @Test fun slowStyleHasExplicitUnsupportedState() { assertNull(plan(t=DailyRecommendationTheme.SLOW_AGING_STYLE)); assertTrue(DailyMealThemePolicy.SLOW_STYLE_LIMITATION.contains("조건")) }
    @Test fun dislikeAppliesToEveryThemeAndOutranksPreference() {
        val preference=pref.copy(preferredFoods="|싫은재료|")
        DailyRecommendationTheme.entries.forEach { t ->
            val s=fixtures().map { it.copy(ingredientNames=setOf("싫은재료")) }
            assertNull(plan(seeds=s,t=t,dislikes=setOf("싫은재료"),preference=preference))
        }
    }
    @Test fun preferenceIsRealAndDoesNotInventReasonsWhenAbsent() {
        assertFalse(requireNotNull(plan()).reasons.any { it.contains("취향") })
        val p=requireNotNull(plan(preference=pref.copy(preferredFoods="|STYLE:KOREAN|")))
        assertTrue(p.meals.all { it.preferenceMatched }); assertTrue(p.reasons.any { it.contains("취향") })
    }
    @Test fun preferenceDoesNotBypassMealEligibility() { assertNull(plan(seeds=fixtures().filterNot { it.template.supportedMealTypes.contains("SNACK") },preference=pref.copy(preferredFoods="|STYLE:KOREAN|"))) }
    @Test fun allergyWarnsWithoutBlocking() { assertTrue(requireNotNull(plan()).meals.all { "대두" in it.matchedAllergens }) }
    @Test fun unknownAllergensAreNotLabelledSafe() {
        val p=requireNotNull(plan(seeds=fixtures().map { it.copy(allergenTags=setOf("UNKNOWN"),ingredientInfoComplete=false) }))
        assertTrue(p.meals.all { !it.allergenInfoComplete && !it.ingredientInfoComplete })
    }
    @Test fun existingBreakfastRemainsActual450AndRemaining1550() {
        val rows=listOf(MealRecord(id=7,date="2026-10-01",time="08:00",mealType=MealType.BREAKFAST,foodName="실제 아침",calories=450))
        val p=requireNotNull(plan(records=rows)); assertTrue(p.meals.first().recorded); assertEquals("실제 아침",p.meals.first().name); assertEquals(450,p.recordedKcal); assertEquals(1550,p.remainingBudgetKcal)
    }
    @Test fun severalBreakfastRecordsAreSummedWithoutOverwrite() {
        val rows=listOf(250,200).mapIndexed { i,k -> MealRecord(id=i.toLong(),date="2026-10-01",time="08:00",mealType=MealType.BREAKFAST,foodName="기록$i",calories=k) }
        assertEquals(450,requireNotNull(plan(records=rows)).meals.first().kcal)
    }
    @Test fun exceededActualIntakeCannotPretendRemainingPlanFits() {
        assertNull(plan(records=listOf(MealRecord(id=1,date="2026-10-01",time="08:00",mealType=MealType.BREAKFAST,foodName="기록",calories=2100))))
    }
    @Test fun lunchReplacementKeepsOtherThreeAndRecalculates() {
        val old=requireNotNull(plan()); val fixed=old.meals.filterNot { it.mealType==MealType.LUNCH }.associateBy { it.mealType }
        val next=requireNotNull(DailyMealPlanEngine.generate(old.date,old.targetKcal,theme,fixtures(),pref,fixed=fixed,excludedIds=setOf(old.meals[1].templateId!!)))
        assertEquals(fixed.values.toList(),next.meals.filterNot { it.mealType==MealType.LUNCH }); assertNotEquals(old.meals[1].templateId,next.meals[1].templateId); assertEquals(next.meals.sumOf { it.kcal },next.totalKcal)
    }
    @Test fun replacementWithNoCandidateLeavesCallerPlanUntouched() {
        val old=requireNotNull(plan()); assertNull(DailyMealPlanEngine.generate(old.date,2000,theme,fixtures().filterNot { it.template.supportedMealTypes.contains("LUNCH") },pref,fixed=old.meals.filterNot { it.mealType==MealType.LUNCH }.associateBy { it.mealType }))
    }
    @Test fun alternateCannotImmediatelyRepeatSignature() {
        val p=requireNotNull(plan()); assertNotEquals(p.signature,requireNotNull(plan(excluded=setOf(p.signature))).signature)
    }
    @Test fun unseenMenusHavePriorityAcrossCycle() {
        val p=requireNotNull(plan()); val seen=p.meals.associate { it.mealType to setOf(it.templateId!!) }
        val next=requireNotNull(plan(seen=seen)); assertTrue(next.meals.all { it.templateId !in seen[it.mealType].orEmpty() })
    }
    @Test fun deterministicSameInputsHaveSameIdentityAndStandardPortion() {
        assertEquals(requireNotNull(plan()).signature,requireNotNull(plan()).signature); assertTrue(requireNotNull(plan()).meals.all { it.portion==1.0 })
    }
    @Test fun savedDateThemeIdsPortionsAndTargetRoundTrip() {
        val p=requireNotNull(plan()); val stored=StoredTodayPlan(p.date,p.theme,p.targetKcal,p.meals.map { StoredDailyMeal(it.mealType,it.templateId!!,it.portion,it.kcal) },listOf(p.signature))
        assertEquals(stored,DailyPlanSnapshotCodec.decode(DailyPlanSnapshotCodec.encode(stored)))
    }
    @Test fun corruptOrDuplicateSnapshotDoesNotLoad() {
        assertNull(DailyPlanSnapshotCodec.decode("broken")); assertNull(DailyPlanSnapshotCodec.decode("1\t2026-10-01\tCHEAT\t2000\tBREAKFAST,same,1.0,500;LUNCH,same,1.0,500\t"))
    }
    @Test fun missingMacroRemainsNullNotFabricated() {
        val p=requireNotNull(plan(seeds=fixtures().map { it.copy(template=it.template.copy(proteinGrams=null)) })); assertNull(p.nutrition.proteinGrams); assertFalse(p.macroComplete)
    }
    @Test fun partialRecordedNutritionIsNeverPresentedAsCompleteDailyMacros() {
        val rows=listOf(MealRecord(id=1,date="2026-10-01",time="08:00",mealType=MealType.BREAKFAST,foodName="known",calories=250,carbohydrateGrams=20.0,proteinGrams=10.0,fatGrams=5.0),
            MealRecord(id=2,date="2026-10-01",time="08:00",mealType=MealType.BREAKFAST,foodName="unknown",calories=200))
        val p=requireNotNull(plan(records=rows)); assertFalse(p.macroComplete); assertEquals(10.0,p.meals.first().nutrition.proteinGrams!!,0.0)
    }
    @Test fun cycleCannotReturnSeenMenuWhenUnseenPoolIsBlockedByOtherMeals() {
        val s=DailyMealPlanEngine.slots.map { seed(it.name,it,500) }
        val seen=DailyMealPlanEngine.slots.associateWith { setOf(it.name) }
        assertNull(plan(seeds=s,seen=seen))
    }
}
