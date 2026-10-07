package com.example.healthcare

import com.example.healthcare.domain.FranchiseCatalog
import com.example.healthcare.domain.FoodSearchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FranchiseCatalogExpansionTest {
    @Test fun bonBrandsKeepOfficialIdentityAndActualMenus() {
        assertEquals("본죽", FranchiseCatalog.canonicalBrand("본죽"))
        assertEquals("본죽&비빔밥", FranchiseCatalog.canonicalBrand("본죽앤비빔밥"))
        val bon = FranchiseCatalog.officialMenus("본죽")
        val bibimbap = FranchiseCatalog.officialMenus("본죽&비빔밥")
        assertEquals(65, bon.size)
        assertEquals(90, bibimbap.size)
        assertTrue(bon.any { it.name == "전복죽" })
        assertTrue(bon.all { "brdCd=BF101" in it.sourceUrl })
        assertTrue(bibimbap.all { "brdCd=BF102" in it.sourceUrl })
        assertTrue(FranchiseCatalog.entries.single { it.name == "본도시락" }.officialUrl.contains("BF104"))
    }

    @Test fun menuOnlyNeverMeansZeroCaloriesOrAnInventedServing() {
        val menus = FranchiseCatalog.brands.flatMap { FranchiseCatalog.officialMenus(it) }
        assertEquals(2685, menus.size)
        menus.forEach {
            assertNull(it.energyKcal)
            assertNull(it.servingAmount)
            assertNull(it.servingUnit)
            assertTrue(it.sourceUrl.startsWith("https://") || it.sourceUrl.startsWith("http://"))
        }
    }

    @Test fun manualPrefillHasBrandMenuAndReviewedSource() {
        val menu = FranchiseCatalog.officialMenus("본죽").single { it.name == "전복죽" }
        assertEquals("본죽 전복죽", menu.recordName)
        assertTrue(menu.sourceDescription.contains(menu.brand))
        assertTrue(menu.sourceDescription.contains(menu.sourceUrl))
        assertTrue(menu.sourceDescription.contains("2026-10-02"))
    }

    @Test fun menuIdentityIsUniqueWithinBrandAndAcrossSources() {
        val menus = FranchiseCatalog.brands.flatMap { FranchiseCatalog.officialMenus(it) }
        assertEquals(menus.size, menus.map { it.id }.distinct().size)
        assertEquals(menus.size, menus.map { Triple(it.brand, FoodSearchPolicy.normalize(it.name),
            it.saleState.takeIf { state -> state == "PUBLIC_TOURISM_STORE_MENU" || state == "OFFICIAL_ORDER_BRAND_SALES_UNVERIFIED" }.orEmpty()) }.distinct().size)
        assertEquals(89, FranchiseCatalog.brands.distinct().size)
    }

    @Test fun previouslyEmptyBrandsHaveCurrentMenuNamesAndExcludeCommentedRetiredMenus() {
        assertEquals(61, FranchiseCatalog.officialMenus("김가네").size)
        assertEquals(64, FranchiseCatalog.officialMenus("고봉민김밥인").size)
        assertEquals(48, FranchiseCatalog.officialMenus("신전떡볶이").size)
        assertEquals(19, FranchiseCatalog.officialMenus("두찜").size)
        assertTrue(FranchiseCatalog.officialMenuNames("김가네").contains("에그스팸김밥"))
        assertFalse(FranchiseCatalog.officialMenuNames("고봉민김밥인").contains("박고지김밥"))
        assertFalse(FranchiseCatalog.officialMenuNames("신전떡볶이").contains("제주컵밥"))
    }

    @Test fun countAndSearchUseMenuReferencesWithoutPretendingNutritionIsKnown() {
        assertEquals(65, FranchiseCatalog.menuCount("본죽", 0))
        assertTrue(FranchiseCatalog.matchesBrand("본죽", "삼계죽"))
        assertEquals(listOf("삼계죽"), FranchiseCatalog.officialMenuNames("본죽", "삼계죽"))
        assertTrue(FranchiseCatalog.officialMenus("없는 브랜드").isEmpty())
    }

    @Test fun genericFoodTermsDoNotResolveToOneArbitraryBrand() {
        listOf("포케", "우동", "샌드위치", "토스트", "떡볶이", "짬뽕", "마라탕", "부대찌개", "능이삼계탕", "에그샌드위치").forEach {
            assertNull("generic alias: $it", FranchiseCatalog.canonicalBrand(it))
        }
        assertEquals("써브웨이", FranchiseCatalog.canonicalBrand("서브웨이"))
        assertEquals("홍콩반점0410", FranchiseCatalog.canonicalBrand("홍콩반점"))
        assertTrue(FranchiseCatalog.matchesBrand("슬로우캘리", "포케"))
        assertTrue(FranchiseCatalog.matchesBrand("국수나무", "우동"))
        assertTrue(FranchiseCatalog.matchesBrand("역전우동0410", "우동"))
    }

    @Test fun newlyReviewedOfficialMenusAreSearchableWithoutFabricatingNutrition() {
        listOf("봉추찜닭", "청년다방", "역전우동0410", "채선당", "슬로우캘리", "써브웨이", "에그드랍").forEach { brand ->
            assertTrue(brand, FranchiseCatalog.officialMenus(brand).isNotEmpty())
        }
        assertTrue(FranchiseCatalog.officialMenus("놀부부대찌개").isNotEmpty())
        assertEquals(12, FranchiseCatalog.officialMenus("슬로우캘리").size)
    }
}
