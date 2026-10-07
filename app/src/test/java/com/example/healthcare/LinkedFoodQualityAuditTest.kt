package com.example.healthcare

import com.example.healthcare.data.FoodMetadataStore
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.FoodAmountPolicy
import com.example.healthcare.domain.PortionQuality
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LinkedFoodQualityAuditTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }
    private fun rows(file: File): List<Map<String, String>> {
        val lines = file.readLines()
        val headers = FoodMetadataStore.csv(lines.first())
        return lines.drop(1).filter(String::isNotBlank).map { headers.zip(FoodMetadataStore.csv(it)).toMap() }
    }
    private fun food(row: Map<String,String>) = FoodItem(id = row.getValue("id"), sourceType = row.getValue("sourceType"),
        sourceFoodCode = row.getValue("sourceFoodCode"), name = row.getValue("name"), normalizedName = row.getValue("normalizedName"),
        aliases = row["aliases"].orEmpty(), category = row["category"], referenceAmount = row.getValue("referenceAmount").toDouble(),
        unit = row.getValue("unit"), energyKcal = row.getValue("energyKcal").toDouble(),
        carbohydrateGrams = row["carbohydrateGrams"]?.toDoubleOrNull(), proteinGrams = row["proteinGrams"]?.toDoubleOrNull(),
        fatGrams = row["fatGrams"]?.toDoubleOrNull(), servingDescription = row.getValue("servingDescription"), brand = row["brand"],
        dataVersion = row.getValue("dataVersion"), createdAt = 0, updatedAt = 0)
    private fun foods(before: Boolean) = listOf("food_items.csv","product_items.csv","franchise_official_items.csv").flatMap {
        rows(File(root, if (before) "app/build/data-quality-qa/before-$it" else "app/src/main/assets/fooddata/$it"))
    }
    @Test fun allBundledIdentitiesLinkToProvenanceWithoutInventingUnknowns() {
        val foodRows = foods(false)
        val meta = rows(File(root,"app/src/main/assets/fooddata/food_metadata.csv"))
        val byId = meta.associateBy { it.getValue("foodItemId") }
        assertEquals(meta.size,byId.size)
        foodRows.forEach { f ->
            val m = requireNotNull(byId[f.getValue("id")])
            assertEquals(f.getValue("sourceFoodCode"),m.getValue("sourceFoodCode"))
            assertTrue(m.getValue("sourceReference").isNotBlank())
            assertTrue(m.getValue("checkedAt").matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            assertFalse(m.getValue("allergens").split('|').any { it == "SAFE" || it == "NONE" })
            assertTrue(m.getValue("allergenStatus") != "CONFIRMED_LABEL" || m.getValue("sourceReference").contains("https://"))
        }
    }
    @Test fun existingNutritionAndStableFoodIdentitiesRemainUnchanged() {
        val after = foods(false).associateBy { it.getValue("id") }
        foods(true).forEach { old ->
            val current = requireNotNull(after[old.getValue("id")])
            listOf("sourceType","sourceFoodCode","name","referenceAmount","unit","energyKcal","carbohydrateGrams","proteinGrams","fatGrams")
                .forEach { field -> assertEquals("${old["id"]}:$field",old[field],current[field]) }
        }
    }
    @Test fun runtimeAmountPolicyAuditKeepsVerifiedUnitsAndUnknownBasis() {
        fun counts(before: Boolean): Map<PortionQuality,Int> = foods(before).map(::food)
            .groupingBy(FoodAmountPolicy::portionQuality).eachCount()
        val before = counts(true); val after = counts(false)
        fun json(value: Map<PortionQuality,Int>) = value.entries.sortedBy { it.key.name }
            .joinToString(",", "{", "}") { "\"${it.key.name}\":${it.value}" }
        File(root,"app/build/data-quality-qa/runtime-amount-audit.json").writeText("{\"before\":${json(before)},\"after\":${json(after)}}")
        val all = foods(false).map(::food)
        // Use the actual runtime policy rather than a second parser for household-unit coverage.
        fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
        File(root,"app/build/data-quality-qa/runtime-serving-audit.csv").writeText(
            "foodItemId,servingAmount,servingUnit,basisAmountPerServing,basisUnit,portionQuality,sourceReference\n" +
                all.joinToString("\n", postfix = "\n") { food ->
                    val choice = FoodAmountPolicy.defaultChoice(food)
                    listOf(food.id, if (choice == null) "" else "1", choice?.unit.orEmpty(),
                        choice?.basisAmountPerUnit?.toString().orEmpty(), choice?.basisUnit.orEmpty(),
                        FoodAmountPolicy.portionQuality(food).name, choice?.evidence.orEmpty()).joinToString(",", transform = ::csv)
                })
        assertEquals("개",FoodAmountPolicy.defaultChoice(all.single { it.sourceFoodCode == "FDC-173424" })?.unit)
        assertTrue(all.filter { it.sourceFoodCode in setOf("D401-007560000-0001","D401-007030000-0001","D501-007030000-0001") }
            .all { FoodAmountPolicy.portionQuality(it) == PortionQuality.UNRESOLVED })
    }
}
