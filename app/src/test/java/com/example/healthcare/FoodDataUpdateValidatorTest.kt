package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.update.FoodDataUpdateCandidate
import com.example.healthcare.data.update.FoodDataUpdateValidator
import com.example.healthcare.domain.FranchiseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodDataUpdateValidatorTest {
    @Test
    fun validNewerOfficialRowsPassValidation() {
        val rows = (1..10).map(::food)
        val result = FoodDataUpdateValidator.validate(
            FoodDataUpdateCandidate("TEST", "2026-09-27", rows),
            currentVersion = "2026-08-28",
            currentRowCount = 10
        )
        assertTrue(result.errors.joinToString(), result.valid)
    }

    @Test
    fun corruptedDuplicateAndLargeDropAreRejectedWithoutInventingValues() {
        val duplicate = food(1)
        val result = FoodDataUpdateValidator.validate(
            FoodDataUpdateCandidate("TEST", "2026-09-27", listOf(duplicate, duplicate)),
            currentVersion = "2026-08-28",
            currentRowCount = 100
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("급감") })
        assertTrue(result.errors.any { it.contains("duplicate") })
        assertNull(duplicate.carbohydrateGrams)
    }

    @Test
    fun franchiseCategoriesAndAliasesCoverExpandedDomesticSearch() {
        listOf("찜닭", "국밥", "분식", "중식", "마라탕", "죽", "국수·우동", "돈가스", "토스트")
            .forEach { assertTrue(it, it in FranchiseCatalog.categories) }
        assertEquals("이삭토스트", FranchiseCatalog.canonicalBrand("이삭"))
        assertTrue("햄치즈 토스트" in FranchiseCatalog.officialMenuNames("이삭토스트", "햄치즈"))
        assertTrue(FranchiseCatalog.matchesBrand("이삭토스트", "그릴드 불갈비"))
        assertTrue(FranchiseCatalog.matchesBrand("탕화쿵푸", "마라탕"))
        assertTrue(FranchiseCatalog.matchesBrand("백소정", "돈까스"))
        assertTrue(FranchiseCatalog.matchesBrand("두찜", "찜닭"))
    }

    private fun food(index: Int) = FoodItem(
        id = "official-$index",
        sourceType = "K-FIND",
        sourceFoodCode = "CODE-$index",
        name = "음식 $index",
        normalizedName = "음식$index",
        referenceAmount = 100.0,
        unit = "g",
        energyKcal = 100.0 + index,
        servingDescription = "100g",
        dataVersion = "2026-09-27",
        createdAt = 1L,
        updatedAt = 1L
    )
}
