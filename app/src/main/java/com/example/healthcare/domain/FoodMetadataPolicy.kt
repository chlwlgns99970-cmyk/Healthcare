package com.example.healthcare.domain

/** Evidence for an exact food/menu ID, kept outside Room so historic records stay unchanged. */
data class FoodMetadata(
    val foodId: String,
    val ingredients: Set<String> = emptySet(),
    val allergens: Set<String> = emptySet(),
    val mayContainAllergens: Set<String> = emptySet(),
    val foodGroups: Set<String> = emptySet(),
    val ingredientInfoComplete: Boolean = false,
    val allergenInfoComplete: Boolean = false,
    val sourceReference: String = "",
    val checkedAt: String = "",
    val brand: String? = null,
    val sourceName: String = "",
    val sourceDate: String = "",
    val ingredientStatus: String = "UNKNOWN",
    val allergenStatus: String = "UNKNOWN",
    val ingredientText: String = "",
    val rawClassification: String = "",
    val manufacturer: String = "",
    val productReportNumber: String = "",
    val packageSize: String = "",
    val intakeReference: String = "",
    val parserVersion: String = "",
    val identityEvidence: String = "",
    val foodGroupEvidenceScope: String = "",
    val staleCandidate: Boolean = false,
    val allergenText: String = "",
    val crossContactText: String = "",
    val completeIngredientText: String = "",
    val householdUnit: String = "",
    val basisAmountPerUnit: Double? = null,
    val basisUnit: String = "",
    val servingSourceReference: String = "",
    val servingEvidenceKind: String = "",
    val servingSourceSize: String = "",
    val recommendationReferenceAmount: Double? = null,
    val recommendationReferenceUnit: String = "",
    val recommendationSourceReference: String = "",
    val menuCategory: String = "",
    val sourceStatus: String = "",
    val availabilityStatus: String = "",
    val sourceVersion: String = "",
    val sourceHash: String = "",
    val referenceRecipeName: String = "",
    val referenceIngredientText: String = "",
    val referenceRecipeBasis: String = "",
    val referenceRecipeUrl: String = "",
    val referenceRecipeHash: String = ""
)

object FoodMetadataPolicy {
    @Volatile private var evidence: Map<String, FoodMetadata> = emptyMap()
    fun lookup(foodId: String): FoodMetadata? = evidence[foodId]
    fun snapshot(): Collection<FoodMetadata> = evidence.values.toList()

    fun allergenTags(@Suppress("UNUSED_PARAMETER") declared: Set<String>, foodIds: List<String?>): Set<String> {
        val records = foodIds.map { it?.let(::lookup) }
        val complete = records.isNotEmpty() && records.all { it?.allergenInfoComplete == true }
        if (complete) return records.filterNotNull().flatMap { it.allergens }.toSet()
        val positive = records.filterNotNull().flatMap { it.allergens }.toSet()
        // Historic template tags have no ingredient/label provenance in the K-FIND
        // originals. Preserve them in the source, but only exact evidence can confirm
        // containment. Missing or partial labels still require a decision-point caution.
        return positive + "UNKNOWN"
    }

    /** Publish a validated snapshot atomically; missing declarations never become safe. */
    fun install(records: Collection<FoodMetadata>) {
        require(records.map { it.foodId }.distinct().size == records.size) { "Duplicate metadata identity" }
        require(records.all { it.foodId.isNotBlank() && it.sourceReference.isNotBlank() && it.checkedAt.isNotBlank() })
        require(records.all { !it.allergenInfoComplete || it.allergenStatus == "CONFIRMED_LABEL" })
        require(records.all { !it.ingredientInfoComplete || it.ingredientStatus == "COMPLETE_DECLARATION" })
        require(records.all { it.referenceIngredientText.isBlank() ||
            (it.referenceRecipeName.isNotBlank() && it.referenceRecipeUrl.startsWith("https://") &&
                it.referenceRecipeHash.matches(Regex("[a-fA-F0-9]{64}"))) })
        require(records.all { it.householdUnit.isBlank() ||
            (it.basisAmountPerUnit?.let { amount -> amount.isFinite() && amount > 0 } == true &&
                it.basisUnit in setOf("g", "ml") &&
                (it.servingSourceReference.startsWith("https://") || it.servingSourceReference.startsWith("http://")) &&
                it.servingEvidenceKind in setOf("OFFICIAL_SERVING", "VERIFIED_CONVERSION")) })
        require(records.all { it.recommendationReferenceAmount == null ||
            (it.recommendationReferenceAmount.isFinite() && it.recommendationReferenceAmount > 0 &&
                it.recommendationReferenceUnit in setOf("g","ml") &&
                it.recommendationSourceReference.startsWith("https://")) })
        evidence = records.associateBy(FoodMetadata::foodId)
    }
}
