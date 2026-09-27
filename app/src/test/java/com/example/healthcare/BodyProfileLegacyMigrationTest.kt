package com.example.healthcare

import com.example.healthcare.data.BodyProfileDraft
import com.example.healthcare.data.mergeConfirmedLegacyWeight
import com.example.healthcare.domain.BodyProfileCalculator
import com.example.healthcare.domain.BodySex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyProfileLegacyMigrationTest {
    @Test fun `valid canonical weight always wins`() {
        val canonical = completeDraft(weightKg = 58.2)

        assertEquals(canonical, mergeConfirmedLegacyWeight(canonical, 91.0))
    }

    @Test fun `confirmed legacy weight fills only the missing canonical field`() {
        val canonical = completeDraft(weightKg = null)

        assertEquals(
            completeDraft(weightKg = 70.4),
            mergeConfirmedLegacyWeight(canonical, 70.4)
        )
    }

    @Test fun `legacy migration is idempotent after the value becomes canonical`() {
        val first = mergeConfirmedLegacyWeight(completeDraft(weightKg = null), 70.4)
        val second = mergeConfirmedLegacyWeight(first, 88.0)

        assertEquals(first, second)
    }

    @Test fun `partial canonical fields are preserved without invention`() {
        val partial = BodyProfileDraft(sex = BodySex.FEMALE, heightCm = 163.5)
        val merged = mergeConfirmedLegacyWeight(partial, 58.2)

        assertEquals(BodySex.FEMALE, merged.sex)
        assertNull(merged.ageYears)
        assertEquals(163.5, merged.heightCm ?: 0.0, 0.0)
        assertEquals(58.2, merged.weightKg ?: 0.0, 0.0)
    }

    @Test fun `invalid legacy values are not corrected or migrated`() {
        val belowMinimum = BodyProfileCalculator.MIN_WEIGHT_KG - 0.1
        val aboveMaximum = BodyProfileCalculator.MAX_WEIGHT_KG + 0.1

        assertNull(mergeConfirmedLegacyWeight(BodyProfileDraft(), belowMinimum).weightKg)
        assertNull(mergeConfirmedLegacyWeight(BodyProfileDraft(), aboveMaximum).weightKg)
        assertNull(mergeConfirmedLegacyWeight(BodyProfileDraft(), Double.NaN).weightKg)
    }

    @Test fun `new user without either source remains empty`() {
        assertEquals(BodyProfileDraft(), mergeConfirmedLegacyWeight(BodyProfileDraft(), null))
    }

    private fun completeDraft(weightKg: Double?) = BodyProfileDraft(
        sex = BodySex.MALE,
        ageYears = 35,
        heightCm = 175.4,
        weightKg = weightKg
    )
}
