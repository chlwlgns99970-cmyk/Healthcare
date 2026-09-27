package com.example.healthcare.data.seed

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.entity.MealTemplateIngredient
import kotlin.math.roundToInt

/**
 * 빌드 시 정제·검수한 로컬 CSV만 읽습니다. 런타임 웹 스크래핑이나 임의 영양값 생성은 하지 않습니다.
 */
class BundledFoodDataSeeder(
    private val context: Context,
    private val database: AppDatabase
) {
    suspend fun seedIfAvailable(): SeedResult {
        val files = context.assets.list(ASSET_DIRECTORY).orEmpty().toSet()
        if (FOODS_FILE !in files) return SeedResult.NotBundled
        return runCatching {
            val manifest = if (MANIFEST_FILE in files) readManifest("$ASSET_DIRECTORY/$MANIFEST_FILE") else emptyMap()
            val bundleId = manifest["bundleId"].orEmpty()
            val expectedFoodCount = manifest["totalFoodCount"]?.toIntOrNull()
                ?: manifest["foodCount"]?.toIntOrNull() ?: 0
            val preferences = context.getSharedPreferences(SEED_PREFERENCES, Context.MODE_PRIVATE)
            if (
                bundleId.isNotBlank() &&
                preferences.getString(KEY_BUNDLE_ID, null) == bundleId &&
                database.foodItemDao().count() >= expectedFoodCount
            ) {
                return@runCatching SeedResult.AlreadySeeded(bundleId, expectedFoodCount)
            }
            val foodFiles = listOf(FOODS_FILE, PRODUCT_FOODS_FILE, OFFICIAL_FRANCHISE_FOODS_FILE)
                .filter(files::contains)
            val foods = foodFiles.flatMap { file ->
                readCsv("$ASSET_DIRECTORY/$file").map(::foodItem)
            }
            val ingredients = if (INGREDIENTS_FILE in files) {
                readCsv("$ASSET_DIRECTORY/$INGREDIENTS_FILE").map(::ingredient)
            } else emptyList()
            val foodsById = foods.associateBy(FoodItem::id)
            val ingredientsByTemplate = ingredients.groupBy(MealTemplateIngredient::mealTemplateId)
            val templates = if (TEMPLATES_FILE in files) {
                readCsv("$ASSET_DIRECTORY/$TEMPLATES_FILE").map { row ->
                    mealTemplate(row, ingredientsByTemplate[row.required("id")].orEmpty(), foodsById)
                }
            } else emptyList()
            require(foodsById.size == foods.size) { "Duplicate FoodItem id in bundled data" }
            val templateIds = templates.map(MealTemplate::id).toSet()
            require(templateIds.size == templates.size) { "Duplicate MealTemplate id in bundled data" }
            require(ingredients.all { it.mealTemplateId in templateIds }) {
                "Bundled ingredient references an unknown meal template"
            }

            database.withTransaction {
                val foodItemDao = database.foodItemDao()
                val mealCoachDao = database.mealCoachDao()
                if (foods.isNotEmpty()) foodItemDao.upsertAll(foods)
                if (templates.isNotEmpty()) {
                    mealCoachDao.upsertTemplates(templates)
                    mealCoachDao.deleteIngredientsForTemplates(templateIds.toList())
                }
                if (ingredients.isNotEmpty()) mealCoachDao.upsertIngredients(ingredients)
            }
            if (bundleId.isNotBlank()) {
                preferences.edit { putString(KEY_BUNDLE_ID, bundleId) }
            }
            SeedResult.Seeded(foods.size, templates.size, ingredients.size)
        }.getOrElse { SeedResult.InvalidBundle(it.message.orEmpty()) }
    }

    private fun foodItem(row: Map<String, String>): FoodItem {
        val now = System.currentTimeMillis()
        return FoodItem(
            id = row.required("id"),
            sourceType = row.required("sourceType"),
            sourceFoodCode = row.required("sourceFoodCode"),
            name = row.required("name"),
            normalizedName = row.required("normalizedName"),
            aliases = row["aliases"].orEmpty(),
            category = row["category"].nullable(),
            referenceAmount = row.required("referenceAmount").toDouble(),
            unit = row.required("unit"),
            energyKcal = row.required("energyKcal").toDouble(),
            carbohydrateGrams = row["carbohydrateGrams"].doubleOrNull(),
            proteinGrams = row["proteinGrams"].doubleOrNull(),
            fatGrams = row["fatGrams"].doubleOrNull(),
            sodiumMilligrams = row["sodiumMilligrams"].doubleOrNull(),
            servingDescription = row.required("servingDescription"),
            brand = row["brand"].nullable(),
            barcode = row["barcode"].nullable(),
            dataVersion = row.required("dataVersion"),
            createdAt = row["createdAt"].longOrNull() ?: now,
            updatedAt = row["updatedAt"].longOrNull() ?: now
        )
    }

    private fun mealTemplate(
        row: Map<String, String>,
        ingredients: List<MealTemplateIngredient>,
        foodsById: Map<String, FoodItem>
    ): MealTemplate {
        val now = System.currentTimeMillis()
        require(ingredients.isNotEmpty()) { "Meal template has no ingredients: ${row.required("id")}" }
        val foods = ingredients.map { ingredient ->
            val food = requireNotNull(foodsById[ingredient.foodItemId]) {
                "Unknown food item ${ingredient.foodItemId} in ${ingredient.mealTemplateId}"
            }
            require(ingredient.unit.equals(food.unit, ignoreCase = true)) {
                "Unit mismatch for ${ingredient.foodItemId}: ${ingredient.unit} != ${food.unit}"
            }
            ingredient to food
        }
        fun nutrientTotal(selector: (FoodItem) -> Double?): Double? =
            foods.map { (ingredient, food) ->
                selector(food)?.let { nutrient -> nutrient * ingredient.amount / food.referenceAmount }
            }.takeIf { values -> values.all { it != null } }?.sumOf { requireNotNull(it) }
        return MealTemplate(
            id = row.required("id"),
            name = row.required("name"),
            supportedMealTypes = row.required("supportedMealTypes"),
            totalKcal = foods.sumOf { (ingredient, food) ->
                food.energyKcal * ingredient.amount / food.referenceAmount
            }.roundToInt(),
            proteinGrams = nutrientTotal(FoodItem::proteinGrams),
            carbohydrateGrams = nutrientTotal(FoodItem::carbohydrateGrams),
            fatGrams = nutrientTotal(FoodItem::fatGrams),
            preparationMinutes = row.required("preparationMinutes").toInt(),
            costLevel = row.required("costLevel"),
            tags = row["tags"].orEmpty(),
            allergens = row["allergens"].orEmpty(),
            excludedDietTypes = row["excludedDietTypes"].orEmpty(),
            cuisineType = row["cuisineType"].nullable(),
            source = row.required("source"),
            createdAt = row["createdAt"].longOrNull() ?: now,
            updatedAt = row["updatedAt"].longOrNull() ?: now
        )
    }

    private fun ingredient(row: Map<String, String>) = MealTemplateIngredient(
        id = row["id"].longOrNull() ?: 0,
        mealTemplateId = row.required("mealTemplateId"),
        foodItemId = row.required("foodItemId"),
        amount = row.required("amount").toDouble(),
        unit = row.required("unit"),
        adjustable = row.required("adjustable").toBooleanStrict(),
        minimumAmount = row["minimumAmount"].doubleOrNull(),
        maximumAmount = row["maximumAmount"].doubleOrNull(),
        adjustmentStep = row["adjustmentStep"].doubleOrNull()
    )

    private fun readCsv(path: String): List<Map<String, String>> = context.assets.open(path).bufferedReader().use { reader ->
        val lines = reader.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return@use emptyList()
        val headers = parseCsvLine(lines.first())
        lines.drop(1).map { line ->
            val values = parseCsvLine(line)
            headers.mapIndexed { index, header -> header to values.getOrElse(index) { "" } }.toMap()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private fun readManifest(path: String): Map<String, String> = context.assets.open(path).bufferedReader().useLines { lines ->
        lines.map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith("#") && '=' in it }
            .associate { line ->
                val separator = line.indexOf('=')
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
    }

    private fun Map<String, String>.required(key: String): String =
        get(key)?.trim()?.takeIf(String::isNotBlank) ?: error("Required column is empty: $key")

    private fun String?.nullable(): String? = this?.trim()?.takeIf(String::isNotBlank)
    private fun String?.doubleOrNull(): Double? = nullable()?.toDoubleOrNull()
    private fun String?.longOrNull(): Long? = nullable()?.toLongOrNull()

    sealed interface SeedResult {
        data object NotBundled : SeedResult
        data class AlreadySeeded(val bundleId: String, val foodCount: Int) : SeedResult
        data class Seeded(val foodCount: Int, val templateCount: Int, val ingredientCount: Int) : SeedResult
        data class InvalidBundle(val reason: String) : SeedResult
    }

    companion object {
        const val ASSET_DIRECTORY = "fooddata"
        const val FOODS_FILE = "food_items.csv"
        const val PRODUCT_FOODS_FILE = "product_items.csv"
        const val OFFICIAL_FRANCHISE_FOODS_FILE = "franchise_official_items.csv"
        const val TEMPLATES_FILE = "meal_templates.csv"
        const val INGREDIENTS_FILE = "meal_template_ingredients.csv"
        const val MANIFEST_FILE = "food_data_manifest.properties"
        private const val SEED_PREFERENCES = "bundled_food_seed"
        private const val KEY_BUNDLE_ID = "bundle_id"
        const val REQUIRED_KFIND_ATTRIBUTION = "식품영양성분 데이터베이스"
        const val REQUIRED_KFIND_ATTRIBUTION_ENGLISH = "Korean Food Composition Database system(K-FCDB)"
        const val SOURCE_TYPE_KFIND = "K-FIND"
        const val SOURCE_TYPE_KFIND_PRODUCT = "K-FIND-PRODUCT"
        const val SOURCE_TYPE_OFFICIAL_BRAND = "OFFICIAL-BRAND-NUTRITION"
    }
}
