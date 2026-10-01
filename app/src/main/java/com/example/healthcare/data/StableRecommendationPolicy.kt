package com.example.healthcare.data

/** Keep valid daily choices; a new taste needs at most one new choice to be represented. */
object StableRecommendationPolicy {
    fun preserve(
        storedIds: List<String>,
        eligibleIds: Set<String>,
        preferredIds: Set<String>,
        preferenceChanged: Boolean,
        unseenPreferredIds: Set<String>,
        limit: Int = 3
    ): List<String> {
        val valid = storedIds.distinct().filter(eligibleIds::contains)
        if (valid.size < limit || !preferenceChanged || preferredIds.isEmpty() ||
            valid.any(preferredIds::contains) || unseenPreferredIds.isEmpty()) return valid
        return valid.dropLast(1)
    }
}
