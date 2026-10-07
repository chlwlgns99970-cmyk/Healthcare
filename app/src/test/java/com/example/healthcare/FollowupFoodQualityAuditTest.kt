package com.example.healthcare

import com.example.healthcare.data.FoodMetadataStore
import com.example.healthcare.data.entity.*
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.*
import java.io.File
import org.junit.Assert.*
import org.junit.After
import org.junit.Test

/** Current-task audit. It does not rerun protected regression suites. */
class FollowupFoodQualityAuditTest {
    @After fun clearEvidence() { FoodMetadataPolicy.install(emptyList()) }
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }
    private val out = File(root, "app/build/food-quality-followup").apply { mkdirs() }
    private fun rows(directory: File, name: String): List<Map<String,String>> {
        val lines = File(directory,name).readLines()
        val header = FoodMetadataStore.csv(lines.first())
        return lines.drop(1).filter(String::isNotBlank).map { header.zip(FoodMetadataStore.csv(it)).toMap() }
    }
    private fun food(r: Map<String,String>) = FoodItem(id=r.getValue("id"),sourceType=r.getValue("sourceType"),
        sourceFoodCode=r.getValue("sourceFoodCode"),name=r.getValue("name"),normalizedName=r.getValue("normalizedName"),
        aliases=r["aliases"].orEmpty(),category=r["category"],brand=r["brand"],referenceAmount=r.getValue("referenceAmount").toDouble(),
        unit=r.getValue("unit"),energyKcal=r.getValue("energyKcal").toDouble(),carbohydrateGrams=r["carbohydrateGrams"]?.toDoubleOrNull(),
        proteinGrams=r["proteinGrams"]?.toDoubleOrNull(),fatGrams=r["fatGrams"]?.toDoubleOrNull(),
        servingDescription=r.getValue("servingDescription"),dataVersion=r.getValue("dataVersion"),createdAt=0,updatedAt=0)
    private fun foods(directory: File) = listOf("food_items.csv","product_items.csv","franchise_official_items.csv")
        .flatMap { rows(directory,it) }.map(::food)
    private fun csv(value: Any?) = "\"${value.toString().replace("\"","\"\"")}\""
    private fun writeServingAudit(directory: File, phase: String) {
        val all=foods(directory)
        File(out,"$phase-serving-audit.csv").writeText("foodItemId,sourceFoodCode,name,brand,category,nutritionBasis,servingUnit,basisAmountPerUnit,quality,sourceReference\n"+
            all.joinToString("\n",postfix="\n") { f ->
                val c=FoodAmountPolicy.defaultChoice(f)
                listOf(f.id,f.sourceFoodCode,f.name,f.brand.orEmpty(),f.category.orEmpty(),"${f.referenceAmount}${f.unit}",
                    c?.unit.orEmpty(),c?.basisAmountPerUnit ?: "",FoodAmountPolicy.portionQuality(f).name,c?.evidence.orEmpty())
                    .joinToString(",",transform=::csv)
            })
        val counts=all.groupingBy(FoodAmountPolicy::portionQuality).eachCount()
        val household=all.count { FoodAmountPolicy.defaultChoice(it)?.unit?.let { u -> u !in setOf("g","ml") } == true }
        File(out,"$phase-serving-summary.json").writeText("{\"foods\":${all.size},\"household\":$household,\"qualities\":"+
            counts.entries.sortedBy { it.key.name }.joinToString(",","{","}") { "\"${it.key.name}\":${it.value}" }+"}")
    }
    private fun writeRecommendationAudit(directory: File, phase: String) {
        val byId=foods(directory).associateBy(FoodItem::id)
        val links=rows(directory,"meal_template_ingredients.csv").map { r -> MealTemplateIngredient(
            mealTemplateId=r.getValue("mealTemplateId"),foodItemId=r.getValue("foodItemId"),amount=r.getValue("amount").toDouble(),
            unit=r.getValue("unit"),adjustable=r.getValue("adjustable").toBoolean()) }.groupBy { it.mealTemplateId }
        val audit=mutableListOf<List<Any>>()
        val seeds=rows(directory,"meal_templates.csv").map { r ->
            val linked=links.getValue(r.getValue("id")); val fs=linked.map { byId.getValue(it.foodItemId) }
            val nutrition=linked.zip(fs).map { (i,f) -> requireNotNull(FoodAmountPolicy.calculate(f,i.amount,i.unit)) }
            val macros=Macronutrients.knownSum(nutrition.map { Macronutrients(it.carbohydrateGrams,it.proteinGrams,it.fatGrams) })
            val template=MealTemplate(id=r.getValue("id"),name=r.getValue("name"),supportedMealTypes=r.getValue("supportedMealTypes"),
                totalKcal=nutrition.sumOf { it.calories },proteinGrams=macros.proteinGrams,
                carbohydrateGrams=macros.carbohydrateGrams,fatGrams=macros.fatGrams,
                preparationMinutes=r.getValue("preparationMinutes").toInt(),costLevel=r.getValue("costLevel"),tags=r.getValue("tags"),
                allergens=r.getValue("allergens"),excludedDietTypes=r.getValue("excludedDietTypes"),cuisineType=r["cuisineType"],source=r.getValue("source"),createdAt=0,updatedAt=0)
            val choices=DailyMealPlanEngine.slots.associateWith { meal -> RecommendationServingPolicy.options(linked,fs,meal) }
            choices.filterKeys { MealRecommendationEngine.supportsMeal(template,it) }.forEach { (meal,options) ->
                audit += listOf(template.id,template.name,meal.name,linked.single().amount,fs.single().category.orEmpty(),
                    options.size,options.minOfOrNull { it.kcal } ?: "",options.maxOfOrNull { it.kcal } ?: "",
                    options.joinToString(" | ") { it.labels.joinToString(" + ") },
                    options.minOfOrNull { linked.single().amount*it.portion } ?: "",
                    options.maxOfOrNull { linked.single().amount*it.portion } ?: "")
            }
            assertTrue("No verified serving for ${template.id}",choices.filterKeys { MealRecommendationEngine.supportsMeal(template,it) }.values.any { it.isNotEmpty() })
            RecommendationSeed(template,fs.map { it.name }.toSet(),allergenTags=setOf("UNKNOWN"),ingredientInfoComplete=false,
                ingredientCategories=fs.mapNotNull { it.category }.toSet(),servingsByMeal=choices)
        }
        assertEquals(292,seeds.size); assertEquals(292,seeds.map { it.template.id }.distinct().size)
        File(out,"$phase-recommendation-serving-audit.csv").writeText("templateId,name,meal,sourceAmount,category,options,minKcal,maxKcal,labels,minBasisAmount,maxBasisAmount\n"+
            audit.joinToString("\n",postfix="\n") { row -> row.joinToString(",",transform=::csv) })
        val plans=DailyRecommendationTheme.entries.mapNotNull { theme ->
            DailyMealPlanEngine.generate("2026-10-04",1500,theme,seeds,MealCoachRepository.defaultPreference())
        }
        assertTrue(plans.any { it.theme==DailyRecommendationTheme.SLOW_AGING_STYLE })
        if (phase=="final") assertEquals(DailyRecommendationTheme.entries.toSet(),plans.map { it.theme }.toSet())
        plans.forEach { p -> p.meals.forEach { m ->
            val seed=seeds.single { it.template.id==m.templateId }
            val selected=requireNotNull(RecommendationServingPolicy.selected(seed,m.mealType,m.portion))
            assertEquals(selected.kcal,m.kcal); assertEquals(selected.labels,m.amountLabels)
        } }
        File(out,"$phase-actual-plans.csv").writeText("theme,target,total,meal,templateId,name,kcal,portion,labels\n"+
            plans.flatMap { p -> p.meals.map { m -> listOf(p.theme.name,p.targetKcal,p.totalKcal,m.mealType.name,m.templateId.orEmpty(),m.name,m.kcal,m.portion,m.amountLabels.joinToString(" + ")) } }
                .joinToString("\n",postfix="\n") { row -> row.joinToString(",",transform=::csv) })
    }
    @Test fun captureCurrentTaskBaselineFromAllFoodsAnd292Recommendations() {
        assertEquals(31844,foods(out).size)
        writeServingAudit(out,"regression-baseline"); writeRecommendationAudit(out,"regression-baseline")
    }
    @Test fun finalAuditPreservesIdentitiesNutritionAndUsesOnlyVerifiedServingOptions() {
        val current=File(root,"app/src/main/assets/fooddata")
        FoodMetadataPolicy.install(rows(current,"food_metadata.csv").map { r -> FoodMetadata(
            foodId=r.getValue("foodItemId"),sourceReference=r.getValue("sourceReference"),checkedAt=r.getValue("checkedAt"),
            householdUnit=r["householdUnit"].orEmpty(),basisAmountPerUnit=r["basisAmountPerUnit"]?.toDoubleOrNull(),
            basisUnit=r["basisUnit"].orEmpty(),servingSourceReference=r["servingSourceReference"].orEmpty(),
            servingEvidenceKind=r["servingEvidenceKind"].orEmpty(),servingSourceSize=r["servingSourceSize"].orEmpty(),
            recommendationReferenceAmount=r["recommendationReferenceAmount"]?.toDoubleOrNull(),
            recommendationReferenceUnit=r["recommendationReferenceUnit"].orEmpty(),
            recommendationSourceReference=r["recommendationSourceReference"].orEmpty()) })
        val after=foods(current).associateBy(FoodItem::id)
        foods(out).forEach { old ->
            val now=requireNotNull(after[old.id])
            assertEquals(old.sourceFoodCode,now.sourceFoodCode); assertEquals(old.name,now.name)
            assertEquals(old.referenceAmount,now.referenceAmount,0.0); assertEquals(old.unit,now.unit)
            assertEquals(old.energyKcal,now.energyKcal,0.0); assertEquals(old.carbohydrateGrams,now.carbohydrateGrams)
            assertEquals(old.proteinGrams,now.proteinGrams); assertEquals(old.fatGrams,now.fatGrams)
        }
        writeServingAudit(current,"final"); writeRecommendationAudit(current,"final")
        val search=after.values.filter { it.name.contains("김밥") }.toList()
        val groups=FoodSearchPolicy.groupSearchResults(search,"김밥")
        File(out,"final-kimbap-results.csv").writeText("foodItemId,name,summary,unit\n"+groups.joinToString("\n",postfix="\n") {
            listOf(it.representative.id,it.representative.name,PortionGuide.resultServingSummary(it.representative),
                FoodAmountPolicy.defaultChoice(it.representative)?.unit.orEmpty()).joinToString(",",transform=::csv) })
        assertTrue(groups.any { FoodAmountPolicy.defaultChoice(it.representative)?.unit=="줄" })
    }
}
