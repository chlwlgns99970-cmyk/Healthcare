package com.example.healthcare.domain

/** A separate official recipe reference, never the selected food's certified formulation. */
data class RecipeIngredientEstimate(
    val foodId: String, val recipeId: String, val recipeName: String, val recipeBasis: String,
    val ingredientText: String, val ingredientName: String, val amountGrams: Double,
    val nutrientFoodId: String, val nutrientName: String, val kcalPer100g: Double,
    val recipeUrl: String, val nutrientUrl: String, val recipeSha256: String, val checkedAt: String,
    val recipeComplete: Boolean = false,
    val foodReferenceKcal: Double? = null, val foodReferenceAmount: Double? = null, val foodReferenceUnit: String = "",
    val compositionKind: String = "ORIGINAL", val sourceInstitution: String = ""
) {
    val estimatedKcal: Double get() = amountGrams * kcalPer100g / 100.0
}

object RecipeCaloriePolicy {
    @Volatile private var references: Map<String, List<RecipeIngredientEstimate>> = emptyMap()
    @Volatile private var compositions: Map<String, List<List<RecipeIngredientEstimate>>> = emptyMap()
    fun lookup(foodId: String): List<RecipeIngredientEstimate> = references[foodId].orEmpty()
    fun lookupCompositions(foodId: String): List<List<RecipeIngredientEstimate>> = compositions[foodId].orEmpty()
    fun install(rows: List<RecipeIngredientEstimate>, additionalRows: List<RecipeIngredientEstimate> = emptyList()) {
        validate(rows)
        validate(additionalRows, allowMultiple = true)
        val grouped = (rows + additionalRows).groupBy { it.foodId }.mapValues { (_, group) ->
            group.groupBy { it.recipeId }.values.toList()
        }
        compositions = grouped
        references = grouped.mapValues { (_, alternatives) ->
            alternatives.firstOrNull { it.first().compositionKind == "ORIGINAL" && it.first().recipeComplete }
                ?: alternatives.firstOrNull { it.first().recipeComplete }
                ?: alternatives.first()
        }
    }
    private fun validate(rows: List<RecipeIngredientEstimate>, allowMultiple: Boolean = false) {
        require(rows.all { it.amountGrams.isFinite() && it.amountGrams > 0 &&
            it.kcalPer100g.isFinite() && it.kcalPer100g >= 0 && it.foodId.isNotBlank() &&
            it.recipeId.isNotBlank() && it.nutrientFoodId.isNotBlank() &&
            it.recipeUrl.startsWith("https://") && it.nutrientUrl.startsWith("https://") &&
            it.recipeSha256.matches(Regex("[a-fA-F0-9]{64}")) && it.checkedAt.isNotBlank() })
        require(rows.map { Triple(it.foodId, it.recipeId, it.ingredientName) }.distinct().size == rows.size)
        if (!allowMultiple) require(rows.groupBy { it.foodId }.values.all { group -> group.map { it.recipeId }.distinct().size == 1 })
        val grouped = rows.groupBy { it.foodId to it.recipeId }
        require(grouped.values.all { group -> group.map { it.recipeId }.distinct().size == 1 })
        require(grouped.values.all { group -> group.map { it.recipeComplete }.distinct().size == 1 })
        require(rows.all { (it.foodReferenceKcal == null || (it.foodReferenceKcal.isFinite() && it.foodReferenceKcal >= 0)) &&
            (it.foodReferenceAmount == null || (it.foodReferenceAmount.isFinite() && it.foodReferenceAmount > 0)) })
        require(rows.all { it.compositionKind in setOf("ORIGINAL", "REFERENCE_RECIPE", "SURVEY_AVERAGE") })
        require(grouped.values.all { group -> group.map { it.compositionKind }.distinct().size == 1 })
    }
}
