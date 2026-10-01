package com.example.healthcare

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.FoodBrowseCategory
import com.example.healthcare.domain.FoodSearchPolicy
import org.junit.Assert.*
import org.junit.Test

class FruitSearchPolicyTest {
    @Test fun apple() = assertBasicFirst("사과")
    @Test fun banana() = assertBasicFirst("바나나")
    @Test fun strawberry() = assertBasicFirst("딸기")
    @Test fun grape() = assertBasicFirst("포도")
    @Test fun pear() = assertBasicFirst("배")
    @Test fun peach() = assertBasicFirst("복숭아")

    private fun assertBasicFirst(name: String) {
        val basic = fruit(name)
        val products = listOf("주스", "맛 음료", "과자", "젤리").mapIndexed { index, suffix ->
            basic.copy(id = "product-$index", sourceType = "K-FIND-PRODUCT",
                name = name + suffix, normalizedName = FoodSearchPolicy.normalize(name + suffix),
                aliases = "|$name|", category = "가공식품", brand = "OO")
        }
        val sameNameProduct = products.first().copy(id = "same-name", name = name, normalizedName = name)
        val input = products + sameNameProduct + basic
        listOf(FoodBrowseCategory.ALL, FoodBrowseCategory.FRUIT).forEach { category ->
            val groups = FoodSearchPolicy.groupSearchResults(input.filter {
                FoodSearchPolicy.matchesCategory(it, category)
            }, name)
            assertEquals(name, groups.first().key)
            assertEquals(basic.id, groups.first().representative.id)
            assertEquals(name, FoodSearchPolicy.displayName(groups.first().representative))
            assertTrue(groups.first().alternatives.contains(sameNameProduct))
            assertEquals(input.toSet(), groups.flatMap { listOf(it.representative) + it.alternatives }.toSet())
            assertEquals(groups, FoodSearchPolicy.groupSearchResults(input.reversed(), name))
        }
        assertEquals("${name}_생것", basic.name)
    }

    @Test fun specificBrandJuiceRemainsAheadOfBasicFruitAndAnotherBrand() {
        val basic = fruit("사과")
        val juice = basic.copy(id = "juice", sourceType = "K-FIND-PRODUCT", name = "사과주스",
            normalizedName = "사과주스", brand = "OO", category = "음료")
        val another = juice.copy(id = "another", brand = "XX")
        val groups = FoodSearchPolicy.groupSearchResults(listOf(basic, another, juice), "OO 사과주스")
        assertEquals("juice", groups.first().representative.id)
        assertEquals(0, FoodSearchPolicy.searchRank(juice, "OO 사과주스"))
    }

    @Test fun categoryIntentUsesExistingExactAliasesForAllEightBasicFruits() {
        val basics = listOf("사과", "바나나", "딸기", "포도", "오렌지", "수박", "복숭아", "배").map(::fruit)
        val snack = fruit("과일젤리").copy(sourceType = "K-FIND-PRODUCT", category = "과자류")
        val groups = FoodSearchPolicy.groupSearchResults(listOf(snack) + basics, "과일")
        assertEquals(basics.map { it.id }.toSet(), groups.take(8).map { it.representative.id }.toSet())
        assertEquals("USDA-SR-LEGACY", groups.first().representative.sourceType)
    }

    private fun fruit(name: String) = FoodItem(
        id = "basic-$name", sourceType = "USDA-SR-LEGACY", sourceFoodCode = "FDC-$name",
        name = "${name}_생것", normalizedName = "${name}생것", aliases = "|$name|과일|과일류|",
        category = "과일류", referenceAmount = 100.0, unit = "g", energyKcal = 52.0,
        servingDescription = "100g 기준", dataVersion = "test", createdAt = 0, updatedAt = 0
    )
}
