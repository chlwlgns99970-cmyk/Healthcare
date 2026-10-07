package com.example.healthcare.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.healthcare.domain.AllergyNoticeKind
import com.example.healthcare.domain.AllergyNoticePolicy

@Composable
fun ContextualAllergyNotice(configuredAllergies: Set<String>, confirmedAllergens: Set<String>,
    allergenInfoComplete: Boolean, decisionPoint: Boolean, modifier: Modifier = Modifier,
    mayContainAllergens: Set<String> = emptySet()) {
    val notice = AllergyNoticePolicy.resolve(configuredAllergies, confirmedAllergens,
        allergenInfoComplete, decisionPoint, mayContainAllergens)
    notice.text?.let { text ->
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = if (notice.kind == AllergyNoticeKind.CONFIRMED || notice.kind == AllergyNoticeKind.CROSS_CONTACT) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.testTag("allergy-notice-${notice.kind.name}"))
    }
}
