package com.example.healthcare.domain

import com.example.healthcare.data.entity.FoodItem

/** Exact-ID presentation only. These facts never enter record nutrient calculations. */
data class FoodDetailFact(val key: String, val label: String, val value: String)
data class FoodDetailModel(
    val foodId: String,
    val name: String,
    val nutritionBasis: String,
    val nutrition: List<FoodDetailFact>,
    val facts: List<FoodDetailFact>,
    val ingredientText: String?,
    val ingredientLabel: String,
    val ingredientIsReference: Boolean,
    val compositionIds: List<String>,
    val basisNeedsReview: Boolean,
    val referenceRecipe: List<FoodDetailFact> = emptyList()
)

object FoodDetailPolicy {
    fun forFood(food: FoodItem): FoodDetailModel = build(
        food.id, FoodSearchPolicy.displayName(food), food.brand,
        food.sourceType, food.sourceFoodCode, food.servingDescription,
        food.referenceAmount, food.unit, food.energyKcal,
        food.carbohydrateGrams, food.proteinGrams, food.fatGrams, food.sodiumMilligrams,
        FoodSearchPolicy.needsBasisReview(food), barcode = food.barcode.orEmpty()
    )

    fun forMenu(menu: FranchiseMenu): FoodDetailModel = build(
        menu.id, menu.recordName, menu.brand, "브랜드 공식 메뉴", menu.id,
        null, menu.servingAmount, menu.servingUnit.orEmpty(), menu.energyKcal,
        null, null, null, null, false,
        menu.sourceUrl, menu.sourceDate
    )

    private fun build(id: String, name: String, brand: String?, source: String, code: String,
        serving: String?, amount: Double?, unit: String, kcal: Double?, carbs: Double?, protein: Double?,
        fat: Double?, sodium: Double?, review: Boolean, sourceUrl: String = "", sourceDate: String = "", barcode: String = ""): FoodDetailModel {
        val metadata = FoodMetadataPolicy.lookup(id)
        val facts = buildList {
            fun fact(key: String, label: String, value: String?) {
                value?.trim()?.takeIf(String::isNotBlank)?.let { add(FoodDetailFact(key,label,it)) }
            }
            fact("brand", "제조사·브랜드", brand ?: metadata?.brand)
            metadata?.brand?.takeIf { it != brand }?.let { fact("metadataBrand","원문 브랜드",it) }
            metadata?.manufacturer?.takeIf { it != brand }?.let { fact("manufacturer","제조사",it) }
            fact("source", "자료 출처", metadata?.sourceName?.takeIf(String::isNotBlank) ?: when {
                source.startsWith("K-FIND") -> "식품의약품안전처 식품영양성분 자료"
                source == "USDA-SR-LEGACY" -> "USDA 식품영양성분 자료"
                source == "OFFICIAL-BRAND-NUTRITION" -> "브랜드 공식 영양정보"
                else -> source
            })
            fact("sourceIdentifier", "자료 식별정보", code)
            fact("barcode", "상품 바코드", barcode)
            fact("sourceReference", "확인 출처", metadata?.sourceReference?.takeIf(String::isNotBlank) ?: sourceUrl)
            fact("sourceDate", "자료 기준일", metadata?.sourceDate?.takeIf(String::isNotBlank) ?: sourceDate)
            fact("checkedAt", "자료 확인일", metadata?.checkedAt)
            fact("servingDescription", "제공량 설명", serving)
            fact("packageSize", "원문 용량 표기", metadata?.packageSize)
            fact("intakeReference", "원문 섭취 기준", metadata?.intakeReference)
            fact("classification", "원문 식품 분류", metadata?.rawClassification)
            fact("productReportNumber", "품목 식별정보", metadata?.productReportNumber)
            fact("allergenText", "원문 알레르기 정보", metadata?.allergenText)
            fact("crossContactText", "원문 교차접촉 안내", metadata?.crossContactText)
            metadata?.takeIf { it.crossContactText.isBlank() && it.mayContainAllergens.isNotEmpty() }?.let {
                fact("mayContainAllergens", "교차접촉 가능 성분", it.mayContainAllergens.sorted().joinToString(" · "))
            }
            metadata?.takeIf { it.allergenText.isBlank() && it.allergens.isNotEmpty() }?.let {
                fact("allergens", "확인된 알레르기 유발 성분", it.allergens.sorted().joinToString(" · "))
            }
            metadata?.takeIf { it.householdUnit.isNotBlank() && it.basisAmountPerUnit != null &&
                it.basisAmountPerUnit.isFinite() && it.basisAmountPerUnit > 0 &&
                it.basisUnit == unit && it.servingEvidenceKind in setOf("OFFICIAL_SERVING","VERIFIED_CONVERSION") &&
                it.servingSourceReference.startsWith("http") }?.let {
                fact("householdUnit","확인된 생활단위","1${it.householdUnit} = ${RecordedAmountSnapshot.format(it.basisAmountPerUnit!!)}${it.basisUnit}")
                fact("servingSourceReference","생활단위 출처",it.servingSourceReference)
            }
        }
        val nutrition = listOf("kcal" to kcal,"carbs" to carbs,"protein" to protein,"fat" to fat,"sodium" to sodium).map { (key,value) ->
            val label = when(key) { "kcal" -> "열량"; "carbs" -> "탄수화물"; "protein" -> "단백질"; "fat" -> "지방"; else -> "나트륨" }
            val suffix = when(key) { "kcal" -> " kcal"; "sodium" -> "mg"; else -> "g" }
            FoodDetailFact(key,label,value?.takeIf { it.isFinite() && it >= 0 }?.let { RecordedAmountSnapshot.format(it)+suffix } ?: "미확인")
        }
        val ingredient = metadata?.completeIngredientText?.takeIf(String::isNotBlank)
            ?: metadata?.ingredientText?.takeIf(String::isNotBlank)
            ?: metadata?.ingredients?.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        return FoodDetailModel(id,name,amount?.takeIf { it.isFinite() && it>0 && unit.isNotBlank() }?.let {
            "${RecordedAmountSnapshot.format(it)}$unit 기준"
        } ?: "영양 기준량 미확인",nutrition,facts,ingredient,when {
            metadata?.foodGroupEvidenceScope=="REFERENCE_RECIPE_COMPOSITION" -> "참고 구성의 주요 재료"
            metadata?.ingredientInfoComplete==true -> "공식 원재료"
            !metadata?.ingredientText.isNullOrBlank() -> "원문 제품·재료 설명"
            else -> "확인된 주요 재료"
        },
            metadata?.foodGroupEvidenceScope=="REFERENCE_RECIPE_COMPOSITION",
            RecipeCaloriePolicy.lookupCompositions(id).map { it.first().recipeId },review,
            if (!metadata?.referenceIngredientText.isNullOrBlank()) listOf(
                FoodDetailFact("referenceName","참고 레시피",metadata!!.referenceRecipeName),
                FoodDetailFact("referenceBasis","원문 제공 기준",metadata.referenceRecipeBasis),
                FoodDetailFact("referenceIngredientText","원문 재료 표",metadata.referenceIngredientText),
                FoodDetailFact("referenceUrl","참고 레시피 출처",metadata.referenceRecipeUrl)
            ) else emptyList())
    }
}
