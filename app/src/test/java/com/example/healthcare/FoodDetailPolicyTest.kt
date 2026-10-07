package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FoodDetailPolicyTest {
    private fun food(id:String="food")=FoodItem(id,"K-FIND","D703-161000000-0001","스파게티","스파게티",category="면 및 만두류",
        referenceAmount=100.0,unit="ml",energyKcal=100.0,carbohydrateGrams=14.0,proteinGrams=null,fatGrams=2.0,
        servingDescription="100ml 기준",dataVersion="test",createdAt=0,updatedAt=0)
    @After fun restore() { FoodMetadataPolicy.install(emptyList());RecipeCaloriePolicy.install(emptyList()) }
    @Test fun sourceBasisReviewRetainsKnownNutritionWithoutEnablingConversion() {
        val model=FoodDetailPolicy.forFood(food())
        assertEquals("100ml 기준",model.nutritionBasis);assertEquals("100 kcal",model.nutrition[0].value)
        assertEquals("미확인",model.nutrition.single { it.key=="protein" }.value)
        assertTrue(model.basisNeedsReview);assertTrue(FoodAmountPolicy.choices(food()).isEmpty())
    }
    @Test fun exactIdentityCannotBorrowSameNameMetadata() {
        FoodMetadataPolicy.install(listOf(FoodMetadata("other",sourceReference="https://official.example/other",checkedAt="2026-10-05",manufacturer="다른 제조사")))
        assertFalse(FoodDetailPolicy.forFood(food()).facts.any { it.value=="다른 제조사" })
    }
    @Test fun fullKnownMetadataTextAndSourceHaveNoTruncation() {
        val raw="원문 원재료 ".repeat(100)
        FoodMetadataPolicy.install(listOf(FoodMetadata("food",sourceReference="https://official.example/food",checkedAt="2026-10-05",
            packageSize="140g",intakeReference="1봉",manufacturer="제조사",sourceDate="2026-09-01",completeIngredientText=raw)))
        val model=FoodDetailPolicy.forFood(food())
        assertEquals(raw,model.ingredientText)
        assertEquals("140g",model.facts.single { it.key=="packageSize" }.value)
        assertEquals("1봉",model.facts.single { it.key=="intakeReference" }.value)
        assertEquals("2026-09-01",model.facts.single { it.key=="sourceDate" }.value)
    }
    @Test fun missingMenuNutritionIsExplicitAndNeverZero() {
        val model=FoodDetailPolicy.forMenu(FranchiseMenu("menu","브랜드","메뉴","https://official.example/menu","2026-10-05"))
        assertTrue(model.nutrition.all { it.value=="미확인" });assertEquals("영양 기준량 미확인",model.nutritionBasis)
        assertTrue(model.facts.any { it.key=="sourceReference" })
    }
    @Test fun realZeroAndPartialNutritionRemainDistinct() {
        val model=FoodDetailPolicy.forFood(food().copy(energyKcal=0.0,proteinGrams=0.0))
        assertEquals("0 kcal",model.nutrition[0].value);assertEquals("0g",model.nutrition.single { it.key=="protein" }.value)
        assertEquals("미확인",model.nutrition.single { it.key=="sodium" }.value)
    }
    @Test fun marketingDescriptionCannotBecomeCompleteIngredientDeclaration() {
        FoodMetadataPolicy.install(listOf(FoodMetadata("food",sourceReference="https://official.example/food",checkedAt="2026-10-05",
            ingredientText="든든한 보양 메뉴",ingredientStatus="PARTIAL_DESCRIPTION")))
        assertEquals("원문 제품·재료 설명",FoodDetailPolicy.forFood(food()).ingredientLabel)
    }
    @Test fun publicRecipeTextCannotReplaceSelectedFoodNutritionOrBecomeCompleteEstimate() {
        FoodMetadataPolicy.install(listOf(FoodMetadata("food",sourceReference="https://official.example/food",checkedAt="2026-10-05",
            referenceRecipeName="토마토스파게티",referenceIngredientText="토마토 50g, 스파게티 100g",
            referenceRecipeBasis="원문 1인 배식량 350g",referenceRecipeUrl="https://www.foodnuri.go.kr/portal/age/ageFood/view.do?key=48",
            referenceRecipeHash="a".repeat(64))))
        val model=FoodDetailPolicy.forFood(food())
        assertEquals("100 kcal",model.nutrition[0].value)
        assertEquals("100ml 기준",model.nutritionBasis)
        assertTrue(model.compositionIds.isEmpty())
        assertEquals("토마토 50g, 스파게티 100g",model.referenceRecipe.single { it.key=="referenceIngredientText" }.value)
    }
}
