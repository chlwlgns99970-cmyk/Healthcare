package com.example.healthcare.data.repository

import com.example.healthcare.data.dao.FoodItemDao
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.FoodBrandSummary
import com.example.healthcare.domain.FoodSearchPolicy
import com.example.healthcare.domain.FoodBrowseCategory
import com.example.healthcare.domain.FranchiseCatalog
import com.example.healthcare.domain.FoodMenuCategoryPolicy
import com.example.healthcare.data.product.DisabledProductNutritionProvider
import com.example.healthcare.data.product.ProductNutritionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

open class NutritionRepository(
    private val foodItemDao: FoodItemDao,
    private val productProvider: ProductNutritionProvider = DisabledProductNutritionProvider,
    private val metadataLoader: suspend () -> Unit = {}
) {
    open fun search(query: String): Flow<List<FoodItem>> {
        val variants = FoodSearchPolicy.queries(query)
        if (variants.isEmpty()) return flowOf(emptyList())
        return flow {
            metadataLoader()
            val primary = searchVariant(variants.first())
            emitAll(if (variants.size == 1) primary else combine(primary, searchVariant(variants[1])) { exact, alternate ->
                rankedResults(exact + alternate, variants.first())
            })
        }.flowOn(Dispatchers.Default)
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
                .filter { it.productCount > 0 }
                .filter { normalizedQuery.isBlank() || FoodSearchPolicy.normalize(it.brand).contains(normalizedQuery) }
                .take(80)
                .toList()
        }
    }

    open fun searchProductsByBrand(brand: String, query: String): Flow<List<FoodItem>> {
        if (brand.isBlank()) return flowOf(emptyList())
        return flow { metadataLoader(); emitAll(foodItemDao.observeProductsByBrand(
            brand = brand,
            normalizedQuery = FoodSearchPolicy.normalize(query), limit = -1
        ).map { it.sortedWith(FoodSearchPolicy.representativeComparator(query)) }) }
    }

    open fun searchFranchiseBrands(query: String): Flow<List<FoodBrandSummary>> =
        foodItemDao.observeFranchiseBrands(FranchiseCatalog.brands).map { brands ->
            val counts = brands.associateBy(FoodBrandSummary::brand)
            FranchiseCatalog.brands.map { brand -> FoodBrandSummary(brand,
                FranchiseCatalog.menuCount(brand, counts[brand]?.productCount ?: 0)) }
                .filter { FranchiseCatalog.matchesBrand(it.brand, query) }
                .sortedWith(compareByDescending<FoodBrandSummary> { it.productCount > 0 }.thenBy { it.brand })
        }

    open fun searchFranchiseFoods(brand: String, query: String): Flow<List<FoodItem>> {
        if (brand !in FranchiseCatalog.brands) return flowOf(emptyList())
        return flow { metadataLoader(); emitAll(foodItemDao.observeFranchiseFoods(
            brand = brand,
            normalizedQuery = FranchiseCatalog.menuQuery(brand,query), limit = -1
        ).map { it.sortedWith(FoodSearchPolicy.representativeComparator(query)) }) }
    }

    /** Brand discovery uses representative industry; menus inside a brand use dish taxonomy. */
    open fun searchFranchiseBrandsByCategory(category: String, query: String = ""): Flow<List<FoodBrandSummary>> =
        searchFranchiseBrands(query).map { brands -> brands.filter { FranchiseCatalog.matchesBrandFilter(it.brand, category) } }

    private fun searchVariant(query: String): Flow<List<FoodItem>> = combine(
        // SQLite LIMIT -1 retains matching identities until evidence ranking.
        // Prefix/name truncation otherwise drops named servings such as a roll
        // before the shared comparator ever sees them. The UI uses a lazy list.
        foodItemDao.observeProductSearch(query,limit=-1),
        foodItemDao.observeSearch(query,limit=-1)
    ) { products, generic -> rankedResults(products + generic, query) }

    private fun rankedResults(items: List<FoodItem>, query: String): List<FoodItem> =
        FoodSearchPolicy.rankedSearchResults(items,query)

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

    open suspend fun findById(id: String): FoodItem? {
        metadataLoader()
        return foodItemDao.findById(id)
    }

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
            com.example.healthcare.domain.FoodAmountPolicy.calculate(item, amount,
                com.example.healthcare.domain.FoodAmountPolicy.canonicalUnit(item.unit))?.calories ?: 0
    }
}
