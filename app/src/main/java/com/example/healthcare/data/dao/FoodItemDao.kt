package com.example.healthcare.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.FoodBrandSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FoodItem>)

    @Query(
        "SELECT * FROM food_items " +
            "WHERE sourceType != 'K-FIND-PRODUCT' AND (" +
            "normalizedName LIKE '%' || :normalizedQuery || '%' " +
            "OR aliases LIKE '%' || :normalizedQuery || '%' " +
            "OR replace(lower(COALESCE(brand, '')), ' ', '') LIKE '%' || :normalizedQuery || '%') " +
            "ORDER BY CASE WHEN sourceType = 'K-FIND' AND lower(unit) = 'ml' AND " +
            "category NOT IN ('음료 및 차류', '국 및 탕류', '찌개 및 전골류', '죽 및 스프류', " +
            "'장류, 양념류', '유제품류 및 빙과류') THEN 1 ELSE 0 END, " +
            "CASE WHEN sourceType = 'K-FIND-PRODUCT' AND normalizedName = :normalizedQuery THEN 0 " +
            "WHEN sourceType = 'K-FIND-PRODUCT' AND normalizedName LIKE :normalizedQuery || '%' THEN 1 " +
            "WHEN sourceType = 'K-FIND-PRODUCT' THEN 2 " +
            "WHEN normalizedName = :normalizedQuery THEN 3 " +
            "WHEN normalizedName LIKE :normalizedQuery || '%' THEN 4 ELSE 5 END, name " +
            "LIMIT :limit"
    )
    fun observeSearch(normalizedQuery: String, limit: Int = 80): Flow<List<FoodItem>>

    @Query(
        "SELECT * FROM food_items " +
            "WHERE sourceType = 'K-FIND-PRODUCT' AND (" +
            "normalizedName LIKE '%' || :normalizedQuery || '%' " +
            "OR aliases LIKE '%' || :normalizedQuery || '%') " +
            "ORDER BY CASE WHEN normalizedName = :normalizedQuery THEN 0 " +
            "WHEN normalizedName LIKE :normalizedQuery || '%' THEN 1 ELSE 2 END, name, sourceFoodCode " +
            "LIMIT :limit"
    )
    fun observeProductSearch(normalizedQuery: String, limit: Int = 60): Flow<List<FoodItem>>

    @Query(
        "SELECT brand, COUNT(*) AS productCount FROM food_items " +
            "WHERE sourceType = 'K-FIND-PRODUCT' AND brand IS NOT NULL AND TRIM(brand) != '' " +
            "GROUP BY brand ORDER BY productCount DESC, brand COLLATE NOCASE LIMIT :limit"
    )
    fun observeProductBrands(limit: Int = 4000): Flow<List<FoodBrandSummary>>

    @Query(
        "SELECT * FROM food_items WHERE sourceType = 'K-FIND-PRODUCT' AND brand = :brand " +
            "AND (:normalizedQuery = '' OR normalizedName LIKE '%' || :normalizedQuery || '%' " +
            "OR aliases LIKE '%' || :normalizedQuery || '%') " +
            "ORDER BY CASE WHEN normalizedName = :normalizedQuery THEN 0 " +
            "WHEN normalizedName LIKE :normalizedQuery || '%' THEN 1 ELSE 2 END, name, sourceFoodCode " +
            "LIMIT :limit"
    )
    fun observeProductsByBrand(
        brand: String,
        normalizedQuery: String,
        limit: Int = 200
    ): Flow<List<FoodItem>>

    @Query(
        "SELECT brand, COUNT(*) AS productCount FROM food_items " +
            "WHERE sourceType IN ('K-FIND', 'OFFICIAL-BRAND-NUTRITION') AND brand IN (:brands) " +
            "GROUP BY brand ORDER BY productCount DESC, brand COLLATE NOCASE"
    )
    fun observeFranchiseBrands(brands: List<String>): Flow<List<FoodBrandSummary>>

    @Query(
        "SELECT * FROM food_items WHERE sourceType IN ('K-FIND', 'OFFICIAL-BRAND-NUTRITION') AND brand = :brand " +
            "AND (:normalizedQuery = '' OR normalizedName LIKE '%' || :normalizedQuery || '%' " +
            "OR aliases LIKE '%' || :normalizedQuery || '%') " +
            "ORDER BY CASE WHEN normalizedName = :normalizedQuery THEN 0 " +
            "WHEN normalizedName LIKE :normalizedQuery || '%' THEN 1 ELSE 2 END, name, sourceFoodCode " +
            "LIMIT :limit"
    )
    fun observeFranchiseFoods(
        brand: String,
        normalizedQuery: String,
        limit: Int = 600
    ): Flow<List<FoodItem>>

    @Query(
        "SELECT * FROM food_items " +
            "WHERE normalizedName = :normalizedName " +
            "OR aliases LIKE '%|' || :normalizedName || '|%' " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun findExactMatches(normalizedName: String, limit: Int = 10): List<FoodItem>

    @Query("SELECT * FROM food_items WHERE barcode = :barcode ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findByBarcode(barcode: String): FoodItem?

    @Query("SELECT * FROM food_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): FoodItem?

    @Query(
        "SELECT * FROM food_items WHERE category = :category AND id != :excludedId " +
            "AND energyKcal BETWEEN :minimumKcal AND :maximumKcal ORDER BY ABS(energyKcal - :targetKcal) LIMIT :limit"
    )
    suspend fun findReplacements(
        category: String,
        excludedId: String,
        minimumKcal: Double,
        maximumKcal: Double,
        targetKcal: Double,
        limit: Int = 5
    ): List<FoodItem>

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun count(): Int
}
