package com.example.healthcare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.healthcare.domain.MacronutrientFormatter
import com.example.healthcare.domain.Macronutrients

/** kcal를 보조하는 간결한 3열 영양 요약. 누락값을 0으로 표현하지 않습니다. */
@Composable
fun MacroSummaryRow(
    nutrition: Macronutrients,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MacroMetric("탄수화물", nutrition.carbohydrateGrams, Modifier.weight(1f))
        MacroMetric("단백질", nutrition.proteinGrams, Modifier.weight(1f))
        MacroMetric("지방", nutrition.fatGrams, Modifier.weight(1f))
    }
}

@Composable
private fun MacroMetric(label: String, value: Double?, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            MacronutrientFormatter.grams(value),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
    }
}
