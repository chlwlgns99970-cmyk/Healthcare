package com.example.healthcare

import com.example.healthcare.ui.RecommendationImageResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecommendationImageResolverTest {
    private val allRecommendationTemplateIds = listOf(
        "kfind-breakfast-cook-1", "kfind-breakfast-cook-2", "kfind-breakfast-cook-3",
        "kfind-breakfast-dining_out-1", "kfind-breakfast-dining_out-2", "kfind-breakfast-dining_out-3",
        "kfind-breakfast-convenience-1", "kfind-breakfast-convenience-2", "kfind-breakfast-convenience-3",
        "kfind-lunch-cook-1", "kfind-lunch-cook-2", "kfind-lunch-cook-3",
        "kfind-lunch-dining_out-1", "kfind-lunch-dining_out-2", "kfind-lunch-dining_out-3",
        "kfind-lunch-convenience-1", "kfind-lunch-convenience-2", "kfind-lunch-convenience-3",
        "kfind-dinner-cook-1", "kfind-dinner-cook-2", "kfind-dinner-cook-3",
        "kfind-dinner-dining_out-1", "kfind-dinner-dining_out-2", "kfind-dinner-dining_out-3",
        "kfind-dinner-convenience-1", "kfind-dinner-convenience-2", "kfind-dinner-convenience-3",
        "kfind-snack-cook-1", "kfind-snack-cook-2", "kfind-snack-cook-3",
        "kfind-snack-dining_out-1", "kfind-snack-dining_out-2", "kfind-snack-dining_out-3",
        "kfind-snack-convenience-1", "kfind-snack-convenience-2", "kfind-snack-convenience-3"
    )

    @Test fun everyRecommendationTemplateHasAnExplicitImage() {
        allRecommendationTemplateIds.forEach { templateId ->
            assertNotNull(templateId, RecommendationImageResolver.resolveTemplate(templateId))
        }
        assertEquals(292, RecommendationImageResolver.mappedTemplateIds().size)
    }

    @Test fun stableTemplateIdsMapToMatchingMealFamilies() {
        assertEquals(
            R.drawable.rec_kfind_breakfast_cook_2,
            RecommendationImageResolver.resolveTemplate("kfind-breakfast-cook-2")
        )
        assertEquals(
            R.drawable.rec_kfind_lunch_cook_1,
            RecommendationImageResolver.resolveTemplate("kfind-lunch-cook-1")
        )
        assertEquals(
            R.drawable.rec_kfind_dinner_dining_out_2,
            RecommendationImageResolver.resolveTemplate("kfind-dinner-dining_out-2")
        )
        assertEquals(
            R.drawable.rec_kfind_dinner_convenience_1,
            RecommendationImageResolver.resolveTemplate("kfind-dinner-convenience-1")
        )
    }

    @Test fun exactNamesMapButUnknownOrMisleadingNamesUseNeutralFallback() {
        assertEquals(R.drawable.photo_salmon_avocado_salad, RecommendationImageResolver.resolveName("연어 아보카도 샐러드"))
        assertEquals(R.drawable.photo_breakfast_yogurt_bowl, RecommendationImageResolver.resolveName("요거트 과일볼"))
        assertNull(RecommendationImageResolver.resolveName("참치김밥"))
        assertNull(RecommendationImageResolver.resolveName("알 수 없는 한 끼"))
        assertNull(RecommendationImageResolver.resolveTemplate("missing-template"))
    }

    @Test fun changingTemplateChangesTheImageResourceAndResolutionIsStable() {
        val rice = RecommendationImageResolver.resolveTemplate("kfind-catalog-d301-005000000-0001")
        val soup = RecommendationImageResolver.resolveTemplate("kfind-catalog-d301-004000000-0001")
        assertNotNull(rice)
        assertNotNull(soup)
        assertNotEquals(rice, soup)
        assertEquals(rice, RecommendationImageResolver.resolveTemplate("kfind-catalog-d301-005000000-0001"))
    }
}
