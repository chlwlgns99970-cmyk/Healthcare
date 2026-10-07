package com.example.healthcare.domain

enum class AllergyNoticeKind { NONE, CONFIRMED, CROSS_CONTACT, UNRESOLVED }

data class AllergyNotice(val kind: AllergyNoticeKind, val matchedCauses: Set<String> = emptySet(),
    val crossContactCauses: Set<String> = emptySet()) {
    val text: String? get() = when (kind) {
        AllergyNoticeKind.NONE -> null
        AllergyNoticeKind.CONFIRMED -> "알레르기 주의 · ${matchedCauses.sorted().joinToString("·")} 포함" +
            if (crossContactCauses.isEmpty()) "" else "\n알레르기 교차접촉 주의 · ${crossContactCauses.sorted().joinToString("·")}"
        AllergyNoticeKind.CROSS_CONTACT -> "알레르기 교차접촉 주의 · ${matchedCauses.sorted().joinToString("·")}"
        AllergyNoticeKind.UNRESOLVED -> "알레르기 정보 확인 필요"
    }
}

/** Presentation depends on the user's setting; missing evidence remains unknown in the data. */
object AllergyNoticePolicy {
    fun resolve(configuredAllergies: Set<String>, confirmedAllergens: Set<String>,
        allergenInfoComplete: Boolean, decisionPoint: Boolean,
        mayContainAllergens: Set<String> = emptySet()): AllergyNotice {
        val configured = configuredAllergies.filter(String::isNotBlank).map {
            FoodAllergenPolicy.canonicalize(it) ?: it.trim()
        }.toSet()
        if (configured.isEmpty()) return AllergyNotice(AllergyNoticeKind.NONE)
        fun matches(tags: Set<String>) = configured.filter { cause ->
            tags.filterNot { it.equals("UNKNOWN", true) || it.isBlank() }.any { tag ->
                if (cause in FoodAllergenPolicy.supportedCauses) FoodAllergenPolicy.matches(tag, cause)
                else MealRecommendationEngine.normalizeFoodName(tag) == MealRecommendationEngine.normalizeFoodName(cause)
            }
        }.toSet()
        val directMatches = matches(confirmedAllergens)
        val contactMatches = matches(mayContainAllergens)
        val unresolved = !allergenInfoComplete || confirmedAllergens.any { it.equals("UNKNOWN", true) } ||
            mayContainAllergens.any { it.equals("UNKNOWN", true) }
        return when {
            directMatches.isNotEmpty() -> AllergyNotice(AllergyNoticeKind.CONFIRMED, directMatches, contactMatches - directMatches)
            contactMatches.isNotEmpty() -> AllergyNotice(AllergyNoticeKind.CROSS_CONTACT, contactMatches)
            unresolved && decisionPoint -> AllergyNotice(AllergyNoticeKind.UNRESOLVED)
            else -> AllergyNotice(AllergyNoticeKind.NONE)
        }
    }
}
