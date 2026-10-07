package com.example.healthcare.data

import android.content.Context
import com.example.healthcare.domain.FoodMetadata
import com.example.healthcare.domain.FoodMetadataPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Generated evidence only; no web access, DB schema change, or historical recalculation. */
class FoodMetadataStore(private val context: Context) {
    private val mutex = Mutex()
    @Volatile private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            val records = withContext(Dispatchers.IO) {
                context.assets.open("fooddata/food_metadata.csv").bufferedReader().use { reader ->
                    val headers = csv(requireNotNull(reader.readLine()))
                    reader.lineSequence().filter(String::isNotBlank).map { line ->
                        val values = csv(line)
                        require(values.size == headers.size) { "Invalid metadata CSV column count" }
                        val row = headers.zip(values).toMap()
                        fun text(key: String) = row[key].orEmpty()
                        fun tokens(key: String) = text(key).split('|').filter(String::isNotBlank).toSet()
                        FoodMetadata(
                            foodId = text("foodItemId"), ingredients = tokens("ingredients"),
                            allergens = tokens("allergens"), mayContainAllergens = tokens("mayContainAllergens"),
                            foodGroups = tokens("foodGroups"),
                            ingredientInfoComplete = text("ingredientStatus") == "COMPLETE_DECLARATION",
                            allergenInfoComplete = text("allergenStatus") == "CONFIRMED_LABEL",
                            sourceReference = text("sourceReference"), checkedAt = text("checkedAt"),
                            brand = text("brand").ifBlank { null }, sourceName = text("sourceName"),
                            sourceDate = text("sourceDate"), ingredientStatus = text("ingredientStatus"),
                            allergenStatus = text("allergenStatus"), ingredientText = text("ingredientText"),
                            rawClassification = text("rawClassification"), manufacturer = text("manufacturer"),
                            productReportNumber = text("productReportNumber"), packageSize = text("packageSize"),
                            intakeReference = text("intakeReference"), parserVersion = text("parserVersion"),
                            identityEvidence = text("identityEvidence"), foodGroupEvidenceScope = text("foodGroupEvidenceScope"),
                            staleCandidate = text("staleCandidate") == "true",
                            allergenText = text("allergenText"), crossContactText = text("crossContactText"),
                            completeIngredientText = text("completeIngredientText"),
                            householdUnit = text("householdUnit"), basisAmountPerUnit = text("basisAmountPerUnit").toDoubleOrNull(),
                            basisUnit = text("basisUnit"), servingSourceReference = text("servingSourceReference"),
                            servingEvidenceKind = text("servingEvidenceKind"), servingSourceSize = text("servingSourceSize"),
                            recommendationReferenceAmount = text("recommendationReferenceAmount").toDoubleOrNull(),
                            recommendationReferenceUnit = text("recommendationReferenceUnit"),
                            recommendationSourceReference = text("recommendationSourceReference"),
                            menuCategory = text("menuCategory"), sourceStatus = text("sourceStatus"),
                            availabilityStatus = text("availabilityStatus"), sourceVersion = text("sourceVersion"),
                            sourceHash = text("sourceHash"),
                            referenceRecipeName = text("referenceRecipeName"),
                            referenceIngredientText = text("referenceIngredientText"),
                            referenceRecipeBasis = text("referenceRecipeBasis"),
                            referenceRecipeUrl = text("referenceRecipeUrl"),
                            referenceRecipeHash = text("referenceRecipeHash")
                        )
                    }.toList()
                }
            }
            FoodMetadataPolicy.install(records)
            val recipeRows = withContext(Dispatchers.IO) {
                fun readRecipes(asset: String): List<com.example.healthcare.domain.RecipeIngredientEstimate> =
                context.assets.open(asset).bufferedReader().use { reader ->
                    val headers = csv(requireNotNull(reader.readLine()))
                    reader.lineSequence().filter(String::isNotBlank).map { line ->
                        val values = csv(line)
                        require(values.size == headers.size)
                        val row = headers.zip(values).toMap()
                        fun text(key: String) = row.getValue(key)
                        com.example.healthcare.domain.RecipeIngredientEstimate(
                            text("foodId"), text("recipeId"), text("recipeName"), text("recipeBasis"),
                            text("ingredientText"), text("ingredientName"), text("amountGrams").toDouble(),
                            text("nutrientFoodId"), text("nutrientName"), text("kcalPer100g").toDouble(),
                            text("recipeUrl"), text("nutrientUrl"), text("recipeSha256"), text("checkedAt"),
                            row["recipeComplete"] == "true",row["foodReferenceKcal"]?.toDoubleOrNull(),
                            row["foodReferenceAmount"]?.toDoubleOrNull(),row["foodReferenceUnit"].orEmpty(),
                            row["compositionKind"] ?: "ORIGINAL", row["sourceInstitution"].orEmpty()
                        )
                    }.toList()
                }
                readRecipes("fooddata/recipe_ingredient_estimates.csv") to
                    readRecipes("fooddata/official_recipe_reference_estimates.csv")
            }
            com.example.healthcare.domain.RecipeCaloriePolicy.install(recipeRows.first, recipeRows.second)
            loaded = true
        }
    }

    companion object {
        /** Generator validates single-line fields, escaped CSV quotes, and deterministic UTF-8. */
        internal fun csv(line: String): List<String> {
            val result = mutableListOf<String>()
            val value = StringBuilder()
            var quoted = false
            var index = 0
            while (index < line.length) {
                val char = line[index]
                when {
                    char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                        value.append('"'); index++
                    }
                    char == '"' -> quoted = !quoted
                    char == ',' && !quoted -> { result += value.toString(); value.clear() }
                    else -> value.append(char)
                }
                index++
            }
            require(!quoted) { "Unclosed metadata CSV quote" }
            result += value.toString()
            return result
        }
    }
}
