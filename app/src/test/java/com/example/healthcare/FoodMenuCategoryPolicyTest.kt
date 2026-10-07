package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FoodMenuCategoryPolicyTest {
    @Test fun fullReviewUsesExactIdsWithoutInventingUnknownDishForms() {
        val explicit=FranchiseMenu("official-menu-한솥-동백","한솥","동백","https://www.hsd.co.kr/menu/menu_list","2026-10-05")
        assertEquals("도시락",FoodMenuCategoryPolicy.categoryOf(explicit))
        assertNull(FoodMenuCategoryPolicy.categoryOf(explicit.copy(id="different-dongbaek",brand="다른 브랜드")))
        assertEquals("일식",FoodMenuCategoryPolicy.categoryOf(explicit.copy(id="japanese",name="타코야끼")))
        assertEquals("사이드",FoodMenuCategoryPolicy.categoryOf(explicit.copy(id="croquette",name="감자고로케")))
        assertNull(FoodMenuCategoryPolicy.categoryOf(explicit.copy(id="unexplained",name="스페셜 세트 A")))
    }
    private fun food(name: String, brand: String, industry: String) = FoodItem(
        id = "$brand-$name", sourceType = "K-FIND", sourceFoodCode = name,
        name = name, normalizedName = FoodSearchPolicy.normalize(name), aliases = "|${FoodSearchPolicy.normalize(industry)}|",
        category = industry, brand = brand, referenceAmount = 100.0, unit = "g",
        energyKcal = 100.0, servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0)
    @After fun reset() { FoodMetadataPolicy.install(emptyList()) }
    @Test fun pizzaIndustryDoesNotClassifyOtherDishFormsAsPizza() {
        listOf("갈릭스틱토스트", "치킨샌드위치", "햄버거", "크림파스타", "고구마브레드", "그린샐러드", "치즈볼", "콜라").forEach { name ->
            assertFalse(name, FoodMenuCategoryPolicy.matches(food(name,"피자스쿨","피자"),"피자"))
        }
        assertTrue(FoodMenuCategoryPolicy.matches(food("불고기피자","피자스쿨","피자"),"피자"))
        assertNull(FoodMenuCategoryPolicy.categoryOf(food("콤비네이션","피자스쿨","피자")))
    }
    @Test fun chickenIndustryDoesNotClassifySidesAndDrinksAsChicken() {
        listOf("떡볶이", "감자튀김", "치즈볼", "치킨무", "콜라", "치킨버거", "치킨피자", "치킨샐러드").forEach { name ->
            assertFalse(name, FoodMenuCategoryPolicy.matches(food(name,"노랑통닭","치킨"),"치킨"))
        }
        assertTrue(FoodMenuCategoryPolicy.matches(food("후라이드치킨","노랑통닭","치킨"),"치킨"))
    }
    @Test fun exactMenuSourceFillsAmbiguousNamesWithoutUsingBrandIndustry() {
        val pizza=food("슈퍼파파스","파파존스","피자")
        FoodMetadataPolicy.install(listOf(FoodMetadata(pizza.id,sourceReference="https://official.example/menu",checkedAt="2026-10-04",menuCategory="피자")))
        assertEquals("피자",FoodMenuCategoryPolicy.categoryOf(pizza))
        val dessert=food("콜라맛젤리","제조사","과자류")
        assertFalse(FoodSearchPolicy.matchesCategory(dessert,FoodBrowseCategory.BEVERAGE))
        assertTrue(FoodSearchPolicy.matchesCategory(dessert,FoodBrowseCategory.SNACK))
        assertFalse(FoodSearchPolicy.matchesCategory(food("탕수육","중식체인","중식"),FoodBrowseCategory.RICE_NOODLE))
        assertEquals("피자",FoodMenuCategoryPolicy.categoryOf(FranchiseMenu("pizza","파파존스","더블 치즈버거","https://official.example/menu","2026-10-04",category="피자")))
    }
    @Test fun existingSpecificChipsMatchPublishedDishFormsAndTheirBroaderGroups() {
        val riceRoll = food("참치김밥","김가네","김밥")
        assertEquals("김밥",FoodMenuCategoryPolicy.categoryOf(riceRoll))
        assertTrue(FoodMenuCategoryPolicy.matches(riceRoll,"분식"))
        val ramen = food("토리 시오 라멘","멘지","라멘")
        assertEquals("라멘",FoodMenuCategoryPolicy.categoryOf(ramen))
        assertTrue(FoodMenuCategoryPolicy.matches(ramen,"국수·우동"))
        val mala = food("토마토 마라탕","탕화쿵푸","마라탕")
        assertEquals("마라탕",FoodMenuCategoryPolicy.categoryOf(mala))
        assertTrue(FoodMenuCategoryPolicy.matches(mala,"중식"))
        assertEquals("찜닭",FoodMenuCategoryPolicy.categoryOf(food("로제 찜닭","두찜","찜닭")))
        assertTrue(FoodMenuCategoryPolicy.matches(food("로제떡볶이","노랑통닭","치킨"),"떡볶이"))
        assertFalse(FoodMenuCategoryPolicy.matches(food("김말이","노랑통닭","치킨"),"떡볶이"))
        assertTrue(FoodMenuCategoryPolicy.matches(food("돌솥비빔밥","본죽&비빔밥","죽·비빔밥"),"죽·비빔밥"))
        assertFalse(FoodMenuCategoryPolicy.matches(food("새우볶음밥","본죽&비빔밥","죽·비빔밥"),"죽·비빔밥"))
    }
    @Test fun missingDishSpellingsAndCondimentsStayIndependentOfBrandIndustry() {
        assertEquals("치킨",FoodMenuCategoryPolicy.categoryOf(food("닭튀김_지파이 하바네로(L)","롯데리아","햄버거")))
        assertEquals("치킨",FoodMenuCategoryPolicy.categoryOf(food("후라이드 마일드 순살","네네치킨","치킨")))
        assertEquals("사이드",FoodMenuCategoryPolicy.categoryOf(food("치킨 양념소스","네네치킨","치킨")))
        assertEquals("사이드",FoodMenuCategoryPolicy.categoryOf(food("통콘너겟","신전떡볶이","분식")))
        assertEquals("사이드",FoodMenuCategoryPolicy.categoryOf(food("치즈 스틱","파파존스","피자")))
        assertEquals("파스타",FoodMenuCategoryPolicy.categoryOf(food("베이컨까르보나라","청년피자","피자")))
        assertEquals("중식",FoodMenuCategoryPolicy.categoryOf(food("꿔바로우","춘리마라탕","마라탕")))
        val sprite = food("스프라이트","본죽","죽")
        assertTrue(FoodMenuCategoryPolicy.matchesBrowse(sprite,FoodBrowseCategory.BEVERAGE))
        assertFalse(FoodMenuCategoryPolicy.matchesBrowse(sprite,FoodBrowseCategory.SOUP_STEW))
    }
    @Test fun individualOfficialSourceGroupsAndRoutesOutrankIngredientNames() {
        val sandwich = food("치킨 데리야끼","써브웨이","sandwich").copy(sourceType="OFFICIAL-BRAND-NUTRITION")
        assertEquals("샌드위치",FoodMenuCategoryPolicy.categoryOf(sandwich))
        assertEquals("샌드위치",FoodMenuCategoryPolicy.categoryOf(FranchiseMenu("sandwich","써브웨이","쉬림프",
            "https://www.subway.co.kr/menuView/sandwich?menuItemIdx=1520","2026-10-04")))
        val wrap = food("에그마요 랩","샐러디","랩").copy(sourceType="OFFICIAL-BRAND-NUTRITION")
        assertEquals("샌드위치",FoodMenuCategoryPolicy.categoryOf(wrap))
        assertFalse(FoodMenuCategoryPolicy.isFoodMenuReference("가맹문의 1688-5938"))
        assertFalse(FoodMenuCategoryPolicy.isFoodMenuReference("신선합니다"))
        assertTrue(FoodMenuCategoryPolicy.isFoodMenuReference("후라이드 마일드 순살"))
        assertFalse(FranchiseCatalog.officialMenuNames("춘리마라탕").any { it.startsWith("가맹문의") })
    }
    @Test fun groupedFranchiseFiltersUseIndividualMenusRatherThanBrandIndustries() {
        val pizzaToast = food("갈릭스틱토스트", "피자스쿨", "피자")
        val pizza = food("고구마피자", "피자스쿨", "피자")
        val chickenSide = food("치즈볼", "지코바", "치킨")
        val chicken = food("양념치킨", "지코바", "치킨")
        assertFalse(FranchiseCatalog.matchesFilter(pizzaToast, "피자"))
        assertTrue(FranchiseCatalog.matchesFilter(pizza, "피자"))
        assertFalse(FranchiseCatalog.matchesFilter(chickenSide, "치킨"))
        assertTrue(FranchiseCatalog.matchesFilter(chicken, "치킨"))
        assertTrue(FranchiseCatalog.categoriesForFilter("샌드위치·토스트").contains("토스트"))
    }
}
