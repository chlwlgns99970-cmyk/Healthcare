package com.example.healthcare.data.repository

import com.example.healthcare.data.dao.FoodItemDao
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.FoodBrowseCategory
import com.example.healthcare.domain.FranchiseCatalog
import com.example.healthcare.data.product.DisabledProductNutritionProvider
import com.example.healthcare.data.product.ProductNutritionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

open class NutritionRepository(
    private val foodItemDao: FoodItemDao,
    private val productProvider: ProductNutritionProvider = DisabledProductNutritionProvider
) {
    open fun search(query: String): Flow<List<FoodItem>> {
        val variants = FoodSearchPolicy.queries(query)
        if (variants.isEmpty()) return flowOf(emptyList())
        val primary = searchVariant(variants.first())
        if (variants.size == 1) return primary
        return combine(primary, searchVariant(variants[1])) { exact, alternate ->
            rankedResults(exact + alternate, variants.first())
        }
    }

    open fun browse(category: FoodBrowseCategory, query: String): Flow<List<FoodItem>> {
        val effectiveQuery = query.trim().ifBlank { category.seedQuery }
        if (effectiveQuery.isBlank()) return flowOf(emptyList())
        return search(effectiveQuery).map { results ->
            val filtered = results.filter { FoodSearchPolicy.matchesCategory(it, category) }
            if (category == FoodBrowseCategory.ALL) filtered else filtered.sortedWith(
                compareBy<FoodItem> {
                    when {
                        it.sourceType == "USDA-SR-LEGACY" -> 0
                        !FoodSearchPolicy.isProduct(it) -> 1
                        else -> 2
                    }
                }.thenBy { FoodSearchPolicy.searchRank(it, effectiveQuery) }
                    .thenBy(FoodSearchPolicy::displayName)
            )
        }
    }

    open fun searchProductBrands(query: String): Flow<List<FoodBrandSummary>> {
        val normalizedQuery = FoodSearchPolicy.normalize(query)
        return foodItemDao.observeProductBrands().map { brands ->
            brands.asSequence()
                .filter { normalizedQuery.isBlank() || FoodSearchPolicy.normalize(it.brand).contains(normalizedQuery) }
                .take(80)
                .toList()
        }
    }

    open fun searchProductsByBrand(brand: String, query: String): Flow<List<FoodItem>> {
        if (brand.isBlank()) return flowOf(emptyList())
        return foodItemDao.observeProductsByBrand(
            brand = brand,
            normalizedQuery = FoodSearchPolicy.normalize(query)
        )
    }

    open fun searchFranchiseBrands(query: String): Flow<List<FoodBrandSummary>> =
        foodItemDao.observeFranchiseBrands(FranchiseCatalog.brands).map { brands ->
            val counts = brands.associateBy(FoodBrandSummary::brand)
            FranchiseCatalog.brands.map { brand -> counts[brand] ?: FoodBrandSummary(brand, 0) }
                .filter { FranchiseCatalog.matchesBrand(it.brand, query) }
                .sortedWith(compareByDescending<FoodBrandSummary> { it.productCount > 0 }.thenBy { it.brand })
        }

    open fun searchFranchiseFoods(brand: String, query: String): Flow<List<FoodItem>> {
        if (brand !in FranchiseCatalog.brands) return flowOf(emptyList())
        return foodItemDao.observeFranchiseFoods(
            brand = brand,
            normalizedQuery = FoodSearchPolicy.normalize(query)
        )
    }

    private fun searchVariant(query: String): Flow<List<FoodItem>> = combine(
        foodItemDao.observeProductSearch(query),
        foodItemDao.observeSearch(query)
    ) { products, generic -> rankedResults(products + generic, query) }

    private fun rankedResults(items: List<FoodItem>, query: String): List<FoodItem> {
        val sorted = items.distinctBy(FoodItem::id)
            .sortedWith(compareBy<FoodItem> { FoodSearchPolicy.searchRank(it, query) }
                .thenBy { FoodSearchPolicy.needsBasisReview(it) }
                .thenBy(FoodSearchPolicy::sourceTieBreakRank)
                .thenBy(FoodItem::name)
                .thenBy(FoodItem::sourceFoodCode))
            .distinctBy(FoodSearchPolicy::deduplicationKey)
        val visible = sorted.take(60)
        val basicFoods = sorted.filter { it.sourceType == BASIC_FOOD_SOURCE }.take(12)
        return (visible + basicFoods).distinctBy(FoodItem::id)
    }

    open suspend fun matchVerifiedFood(names: List<String>): FoodItem? {
        names.forEach { name ->
            FoodSearchPolicy.queries(name).forEach { normalized ->
                foodItemDao.findExactMatches(normalized)
                    .firstOrNull { !FoodSearchPolicy.needsBasisReview(it) }
                    ?.let { return it }
            }
        }
        return null
    }

    open suspend fun findByBarcode(barcode: String): FoodItem? {
        val normalized = barcode.filter(Char::isDigit)
        val local = foodItemDao.findByBarcode(normalized)
        if (local != null) return local
        val remote = productProvider.findByBarcode(normalized) ?: return null
        foodItemDao.upsertAll(listOf(remote))
        return remote
    }

    open suspend fun findById(id: String): FoodItem? = foodItemDao.findById(id)

    open suspend fun findReplacements(item: FoodItem): List<FoodItem> {
        val category = item.category ?: return emptyList()
        return foodItemDao.findReplacements(
            category = category,
            excludedId = item.id,
            minimumKcal = item.energyKcal * 0.75,
            maximumKcal = item.energyKcal * 1.25,
            targetKcal = item.energyKcal,
            limit = 100
        ).filterNot(FoodSearchPolicy::needsBasisReview).take(5)
    }

    open suspend fun upsert(items: List<FoodItem>) = foodItemDao.upsertAll(items)

    open suspend fun count(): Int = foodItemDao.count()

    companion object {
        private const val BASIC_FOOD_SOURCE = "USDA-SR-LEGACY"

        fun calculateCalories(item: FoodItem, amount: Double): Int =
            kotlin.math.round(item.energyKcal * amount / item.referenceAmount).toInt().coerceAtLeast(0)
    }
}
