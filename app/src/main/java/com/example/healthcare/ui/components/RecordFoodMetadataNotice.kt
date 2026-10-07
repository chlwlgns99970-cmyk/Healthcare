package com.example.healthcare.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthcare.HealthcareApplication
import com.example.healthcare.data.entity.UserExcludedFood
import com.example.healthcare.domain.FoodMetadataPolicy
import com.example.healthcare.domain.FranchiseCatalog
import kotlinx.coroutines.flow.flowOf

/** Exact source identity only. A manually entered name is never used to infer allergens. */
@Composable
fun RecordFoodMetadataNotice(foodId: String?, foodName: String, sourceDescription: String? = null,
    configuredAllergies: Set<String>? = null) {
    if (foodName.isBlank()) return
    val application = LocalContext.current.applicationContext as? HealthcareApplication
    val allergySource = remember(application, configuredAllergies) {
        if (configuredAllergies == null) application?.mealCoachRepository?.excludedFoods
            ?: flowOf(emptyList<UserExcludedFood>()) else flowOf(emptyList<UserExcludedFood>())
    }
    val exclusions by allergySource.collectAsState(initial = emptyList())
    val allergies = configuredAllergies ?: exclusions.filter { it.exclusionType == "ALLERGY" }
        .map { it.normalizedFoodName }.toSet()
    val exactIdentity = remember(foodId, foodName, sourceDescription) {
        foodId ?: sourceDescription?.let { source ->
            FranchiseCatalog.brands.asSequence().flatMap { FranchiseCatalog.officialMenus(it).asSequence() }
                .firstOrNull { it.recordName == foodName && it.sourceDescription == source }?.id
        }
    }
    val metadata = exactIdentity?.let(FoodMetadataPolicy::lookup)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (metadata?.ingredients?.isNotEmpty() == true) {
            val referenceRecipe = metadata.foodGroupEvidenceScope == "REFERENCE_RECIPE_COMPOSITION"
            val label = when {
                referenceRecipe -> "공공 조리자료의 주요 재료"
                metadata.ingredientInfoComplete -> "공식 원재료"
                else -> "공식 설명에서 확인한 주요 재료"
            }
            Text("$label · ${metadata.ingredients.sorted().take(8).joinToString("·")}",
                style = MaterialTheme.typography.bodySmall, softWrap = true)
            if (referenceRecipe) Text("실제 조리법과 재료 구성은 달라질 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ContextualAllergyNotice(allergies, metadata?.allergens.orEmpty(),
            metadata?.allergenInfoComplete == true, decisionPoint = true,
            mayContainAllergens = metadata?.mayContainAllergens.orEmpty())
    }
}
