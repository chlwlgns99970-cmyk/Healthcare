package com.example.healthcare

import com.example.healthcare.data.entity.*
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.repository.MealCoachRepository
import com.example.healthcare.domain.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class FollowupServingPriorityTest {
    @After fun reset() { FoodMetadataPolicy.install(emptyList()) }
    private fun seed(id:String,meal:MealType,kcal:Int)=RecommendationSeed(
        MealTemplate(id,id,"|${meal.name}|",kcal,10.0,20.0,5.0,10,"LOW","|COOK|",source="verified",createdAt=0,updatedAt=0),
        setOf(id),servingsByMeal=mapOf(meal to listOf(RecommendationServing(1.0,kcal,Macronutrients(20.0,10.0,5.0)))))
    @Test fun realisticPreferred1462PlanWinsOverUnpreferredExact1500WithoutExpandingFood() {
        val seeds=listOf(seed("아침",MealType.BREAKFAST,400),seed("점심",MealType.LUNCH,400),seed("저녁",MealType.DINNER,500),
            seed("좋아하는간식",MealType.SNACK,162),seed("다른간식",MealType.SNACK,200))
        val plan=requireNotNull(DailyMealPlanEngine.generate("2026-10-04",1500,DailyRecommendationTheme.CHEAT,seeds,
            MealCoachRepository.defaultPreference().copy(preferredFoods="|좋아하는간식|")))
        assertEquals(1462,plan.totalKcal); assertEquals(1500,plan.targetKcal)
        assertEquals("좋아하는간식",plan.meals.last().name); assertTrue(plan.meals.all { it.portion==1.0 })
    }
    @Test fun closestMealKeepsPreferenceAndUsesAnAvailableServingOnly() {
        val seeds=listOf(seed("좋아하는식사",MealType.LUNCH,462),seed("다른식사",MealType.LUNCH,500))
        val result=MealRecommendationEngine.recommend(seeds,500,emptySet(),emptySet(),setOf("좋아하는식사"),"NONE",
            mealType=MealType.LUNCH,stage=RecommendationStage.CLOSEST_VERIFIED)
        assertEquals("좋아하는식사",result.first().template.name)
        assertEquals(462,result.first().template.totalKcal); assertEquals(1.0,result.first().portion,0.0)
    }
    @Test fun searchAndInternalCalculationUseSameEggAndRollQuantity() {
        val egg=FoodItem("egg","USDA-SR-LEGACY","FDC-173424","달걀_삶은 것","달걀삶은것",category="난류",
            referenceAmount=100.0,unit="g",energyKcal=155.0,servingDescription="100g 기준",dataVersion="source",createdAt=0,updatedAt=0)
        assertEquals("1개 · 78 kcal",PortionGuide.resultServingSummary(egg))
        assertEquals("1개 50g 기준",PortionGuide.resultServingBasis(egg))
        assertEquals("2개",RecommendationServingPolicy.label(egg,100.0))
        val roll=egg.copy(id="roll",sourceType="K-FIND-PRODUCT",sourceFoodCode="P123-203020200-2032",name="백종원한줄김밥",
            energyKcal=137.0,servingDescription="100g 기준 · 공식 총내용량 216g · 포장단위 줄")
        assertEquals("1줄 · 296 kcal",PortionGuide.resultServingSummary(roll))
        assertEquals("1줄 216g 기준",PortionGuide.resultServingBasis(roll))
        assertEquals("1줄",RecommendationServingPolicy.label(roll,216.0))
    }
    @Test fun representativeChecksIngredientsBeforeAllergensAfterServingAndNutrition() {
        val a=FoodItem("ingredient","K-FIND","ingredient","음식","음식",referenceAmount=100.0,unit="g",energyKcal=100.0,
            servingDescription="100g 기준",dataVersion="source",createdAt=0,updatedAt=0)
        val b=a.copy(id="allergen",sourceFoodCode="allergen")
        FoodMetadataPolicy.install(listOf(
            FoodMetadata(a.id,ingredients=setOf("쌀"),ingredientStatus="PARTIAL_DESCRIPTION",sourceReference="https://official.example/a",checkedAt="2026-10-04"),
            FoodMetadata(b.id,allergens=setOf("밀"),allergenInfoComplete=true,allergenStatus="CONFIRMED_LABEL",sourceReference="https://official.example/b",checkedAt="2026-10-04")))
        assertEquals(a,FoodSearchPolicy.groupSearchResults(listOf(b,a),"음식").single().representative)
    }
    @Test fun reviewedExactFoodWeightRetainsMenusWithoutExpandingOrInventingServings() {
        val stew=FoodItem("stew","K-FIND","D305-239000000-0001","어탕","어탕",category="국 및 탕류",
            referenceAmount=100.0,unit="g",energyKcal=57.0,servingDescription="100g 기준",dataVersion="source",createdAt=0,updatedAt=0)
        val fish=stew.copy(id="fish",sourceFoodCode="D110-472000000-0001",name="멸치볶음",category="볶음류",energyKcal=276.0)
        FoodMetadataPolicy.install(listOf(stew to 150.0,fish to 50.0).map { (f,amount) ->
            FoodMetadata(f.id,sourceReference="https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do",checkedAt="2026-10-04",
                recommendationReferenceAmount=amount,recommendationReferenceUnit="g",
                recommendationSourceReference="https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do#${f.sourceFoodCode}") })
        listOf(stew to 150.0,fish to 50.0).forEach { (f,amount) ->
            assertEquals(listOf(amount),RecommendationServingPolicy.amounts(f,400.0,MealType.LUNCH))
            val link=MealTemplateIngredient(mealTemplateId=f.id,foodItemId=f.id,amount=400.0,unit="g",adjustable=false)
            val option=RecommendationServingPolicy.options(listOf(link),listOf(f),MealType.LUNCH).single()
            assertEquals(amount/400.0,option.portion,0.0)
            assertTrue(option.labels.single().endsWith("${amount.toInt()}g"))
            assertEquals("g",FoodAmountPolicy.defaultChoice(f)!!.unit)
        }
        val different=fish.copy(id="another-code",sourceFoodCode="OTHER")
        assertEquals(listOf(100.0,150.0,200.0),RecommendationServingPolicy.amounts(different,400.0,MealType.LUNCH))
        assertEquals(276,FoodAmountPolicy.calculate(fish,100.0,"g")!!.calories)
    }
    @Test fun searchKeepsExactIdentityFirstThenNamedHouseholdUnitAcrossResultGroups() {
        val exact=FoodItem("exact","K-FIND-PRODUCT","EXACT","김밥","김밥",referenceAmount=100.0,unit="g",energyKcal=137.0,
            servingDescription="100g 기준 · 공식 총내용량 200g",dataVersion="source",createdAt=0,updatedAt=0)
        val named=exact.copy(id="named",sourceFoodCode="NAMED",name="백종원한줄김밥",normalizedName="백종원한줄김밥",
            servingDescription="100g 기준 · 공식 총내용량 216g · 포장단위 줄")
        val whole=exact.copy(id="whole",sourceFoodCode="WHOLE",name="1900김밥",normalizedName="1900김밥")
        val broad=exact.copy(id="broad",sourceType="K-FIND",sourceFoodCode="BROAD",name="김밥_김치",normalizedName="김밥김치",
            aliases="|김밥|김치|",servingDescription="100g 기준")
        val groups=FoodSearchPolicy.groupSearchResults(listOf(broad,whole,named,exact),"김밥")
        assertEquals(listOf("exact","named","whole","broad"),groups.map { it.representative.id })
        assertEquals("1줄 · 296 kcal",PortionGuide.resultServingSummary(groups[1].representative))
    }
}
