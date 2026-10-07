package com.example.healthcare

import com.example.healthcare.data.FoodMetadataStore
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.domain.*
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Executable serving/category coverage for this request's immutable inputs. */
class CatalogQaAuditTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .first { File(it,"settings.gradle.kts").exists() }
    private val output = File(root,"app/build/catalog-qa").apply { mkdirs() }
    private fun rows(dir: File, name: String): List<Map<String,String>> {
        val lines=File(dir,name).readLines();val header=FoodMetadataStore.csv(lines.first())
        return lines.drop(1).filter(String::isNotBlank).map { header.zip(FoodMetadataStore.csv(it)).toMap() }
    }
    private fun foods(dir: File) = listOf("food_items.csv","product_items.csv","franchise_official_items.csv")
        .flatMap { rows(dir,it) }.map { r -> FoodItem(id=r.getValue("id"),sourceType=r.getValue("sourceType"),
            sourceFoodCode=r.getValue("sourceFoodCode"),name=r.getValue("name"),normalizedName=r.getValue("normalizedName"),
            aliases=r["aliases"].orEmpty(),category=r["category"],brand=r["brand"],referenceAmount=r.getValue("referenceAmount").toDouble(),
            unit=r.getValue("unit"),energyKcal=r.getValue("energyKcal").toDouble(),
            carbohydrateGrams=r["carbohydrateGrams"]?.toDoubleOrNull(),proteinGrams=r["proteinGrams"]?.toDoubleOrNull(),
            fatGrams=r["fatGrams"]?.toDoubleOrNull(),servingDescription=r.getValue("servingDescription"),
            dataVersion=r.getValue("dataVersion"),createdAt=0,updatedAt=0) }
    private fun install(dir: File) = FoodMetadataPolicy.install(rows(dir,"food_metadata.csv").map { r -> FoodMetadata(
        foodId=r.getValue("foodItemId"),sourceReference=r.getValue("sourceReference"),checkedAt=r.getValue("checkedAt"),
        ingredients=r["ingredients"].orEmpty().split('|').filter(String::isNotBlank).toSet(),
        allergens=r["allergens"].orEmpty().split('|').filter(String::isNotBlank).toSet(),
        ingredientInfoComplete=r["ingredientStatus"]=="COMPLETE_DECLARATION",
        allergenInfoComplete=r["allergenStatus"]=="CONFIRMED_LABEL",
        ingredientStatus=r["ingredientStatus"].orEmpty(),allergenStatus=r["allergenStatus"].orEmpty(),
        completeIngredientText=r["completeIngredientText"].orEmpty(),ingredientText=r["ingredientText"].orEmpty(),
        allergenText=r["allergenText"].orEmpty(),
        rawClassification=r["rawClassification"].orEmpty(),householdUnit=r["householdUnit"].orEmpty(),
        basisAmountPerUnit=r["basisAmountPerUnit"]?.toDoubleOrNull(),basisUnit=r["basisUnit"].orEmpty(),
        servingSourceReference=r["servingSourceReference"].orEmpty(),servingEvidenceKind=r["servingEvidenceKind"].orEmpty(),
        servingSourceSize=r["servingSourceSize"].orEmpty(),recommendationReferenceAmount=r["recommendationReferenceAmount"]?.toDoubleOrNull(),
        recommendationReferenceUnit=r["recommendationReferenceUnit"].orEmpty(),recommendationSourceReference=r["recommendationSourceReference"].orEmpty(),
        menuCategory=r["menuCategory"].orEmpty()) })
    private fun quote(value: Any?)="\"${value.toString().replace("\"","\"\"")}\""
    private fun audit(dir: File, phase: String) {
        install(dir);val all=foods(dir)
        assertEquals(all.size,all.map(FoodItem::id).distinct().size)
        File(output,"$phase-serving-audit.csv").writeText("foodItemId,sourceFoodCode,name,brand,category,nutritionBasis,servingUnit,basisAmountPerUnit,quality,sourceReference\n"+
            all.joinToString("\n",postfix="\n") { f -> val c=FoodAmountPolicy.defaultChoice(f)
                listOf(f.id,f.sourceFoodCode,f.name,f.brand.orEmpty(),f.category.orEmpty(),"${f.referenceAmount}${f.unit}",
                    c?.unit.orEmpty(),c?.basisAmountPerUnit ?: "",FoodAmountPolicy.portionQuality(f).name,c?.evidence.orEmpty()).joinToString(",",transform=::quote) })
        val counts=all.groupingBy(FoodAmountPolicy::portionQuality).eachCount()
        val household=all.count { FoodAmountPolicy.defaultChoice(it)?.unit?.let { u -> u !in setOf("g","ml") }==true }
        File(output,"$phase-serving-summary.json").writeText("{\"foods\":${all.size},\"household\":$household,\"qualities\":"+
            counts.entries.sortedBy { it.key.name }.joinToString(",","{","}") { "\"${it.key.name}\":${it.value}" }+"}")
        File(output,"$phase-food-category-audit.csv").writeText("category,foodItemId,sourceFoodCode,name,brand,sourceCategory\n"+
            FoodBrowseCategory.entries.flatMap { category -> all.filter { FoodSearchPolicy.matchesCategory(it,category) }.map { f ->
                listOf(category.label,f.id,f.sourceFoodCode,f.name,f.brand.orEmpty(),f.category.orEmpty()).joinToString(",",transform=::quote) } }.joinToString("\n",postfix="\n"))
        if (phase=="final") auditMenuCategories(all)
        if (phase=="baseline") { assertEquals(31849,all.size);assertEquals(14699,household) }
    }
    private fun auditMenuCategories(all: List<FoodItem>) {
        data class Menu(val id:String,val name:String,val brand:String,val industry:String,val category:String?,val sourceCategory:String)
        val menus=all.filter(FranchiseCatalog::isFranchise).map { f -> Menu(f.id,f.name,f.brand.orEmpty(),
            FranchiseCatalog.categoryOf(f.brand.orEmpty()).orEmpty(),FoodMenuCategoryPolicy.categoryOf(f),
            FoodMetadataPolicy.lookup(f.id)?.rawClassification.orEmpty()) } +
            FranchiseCatalog.brands.flatMap { brand -> FranchiseCatalog.officialMenus(brand).map { m ->
                Menu(m.id,m.name,brand,FranchiseCatalog.categoryOf(brand).orEmpty(),FoodMenuCategoryPolicy.categoryOf(m),m.category) } }
        fun suspicion(m: Menu): String {
            val n=FoodSearchPolicy.normalize(m.name)
            return when {
                m.category=="피자" && !n.removePrefix("피자").contains("피자") &&
                    listOf("토스트","샌드위치","스파게티","파스타","샐러드","콜라","사이다","감자튀김","치즈볼").any(n::contains) -> "PIZZA_OTHER_DISH_FORM"
                m.category=="치킨" && listOf("떡볶이","감자튀김","치즈볼","치킨무","콜라","사이다","샐러드","버거","피자").any(n::contains) -> "CHICKEN_OTHER_DISH_FORM"
                m.category==null -> "UNKNOWN_INDIVIDUAL_MENU_CATEGORY"
                else -> ""
            }
        }
        File(output,"final-franchise-menu-category-audit.csv").writeText("menuId,name,brand,brandIndustry,menuCategory,sourceCategory,suspicion\n"+
            menus.joinToString("\n",postfix="\n") { m -> listOf(m.id,m.name,m.brand,m.industry,m.category.orEmpty(),m.sourceCategory,suspicion(m)).joinToString(",",transform=::quote) })
        val wrong=menus.filter { suspicion(it) in setOf("PIZZA_OTHER_DISH_FORM","CHICKEN_OTHER_DISH_FORM") }
        assertTrue("Explicit false positives: ${wrong.take(10)}",wrong.isEmpty())
        File(output,"final-product-category-audit.csv").writeText("productId,name,brand,sourceCategory\n"+
            all.filter(FoodSearchPolicy::isProduct).joinToString("\n",postfix="\n") { f ->
                listOf(f.id,f.name,f.brand.orEmpty(),f.category.orEmpty()).joinToString(",",transform=::quote) })
        File(output,"final-menu-category-summary.csv").writeText("category,menuCount,brandCount,categoryMatch,falsePositiveSuspected,unknown,duplicateIdentity,duplicateName\n"+
            (FranchiseCatalog.categories + menus.mapNotNull(Menu::category) + "UNKNOWN").distinct().sorted().joinToString("\n",postfix="\n") { category ->
                val selected=menus.filter { (it.category ?: "UNKNOWN")==category }
                listOf(category,selected.size,selected.map(Menu::brand).distinct().size,selected.count { suspicion(it).isBlank() },
                    selected.count { suspicion(it).startsWith("PIZZA_") || suspicion(it).startsWith("CHICKEN_") },
                    selected.count { it.category==null },selected.size-selected.map(Menu::id).distinct().size,
                    selected.size-selected.map { it.brand+"|"+FoodSearchPolicy.normalize(it.name) }.distinct().size).joinToString(",",transform=::quote)
            })
    }
    @After fun reset() { FoodMetadataPolicy.install(emptyList()) }
    @Test fun onlyFranchiseMenuCategoryCompletionAudit() {
        val dir=File(root,"app/src/main/assets/fooddata")
        install(dir)
        auditMenuCategories(foods(dir))
    }
    @Test fun baselineRuntimeServingAndAllFoodCategoriesFromCapturedAssets() {
        audit(File(output,"baseline/app/src/main/assets/fooddata"),"regression-baseline")
    }
    @Test fun finalRuntimeServingPreservesOriginalIdentitiesAndNutrition() {
        val dir=File(root,"app/src/main/assets/fooddata");val current=foods(dir).associateBy(FoodItem::id)
        foods(File(output,"baseline/app/src/main/assets/fooddata")).forEach { old ->
            val now=requireNotNull(current[old.id]);assertEquals(old.sourceFoodCode,now.sourceFoodCode);assertEquals(old.name,now.name)
            assertEquals(old.referenceAmount,now.referenceAmount,0.0);assertEquals(old.unit,now.unit)
            assertEquals(old.energyKcal,now.energyKcal,0.0);assertEquals(old.carbohydrateGrams,now.carbohydrateGrams)
            assertEquals(old.proteinGrams,now.proteinGrams);assertEquals(old.fatGrams,now.fatGrams)
        }
        audit(dir,"final")
    }
    @Test fun shortYogurtQueryRetainsPublishedStrawberryIdentity() {
        val dir=File(root,"app/src/main/assets/fooddata");install(dir)
        val query="요플레"
        val candidates=foods(dir).filter { FoodSearchPolicy.normalize(it.normalizedName+it.aliases).contains(query) }
            .let { FoodSearchPolicy.rankedSearchResults(it,query) }
        File(output,"yogurt-search-ranking.csv").writeText("index,id,name,kind,quality,rank\n"+
            candidates.mapIndexed { index,f -> listOf(index,f.id,f.name,FoodSearchPolicy.groupSearchResults(listOf(f),query).single().key,
                FoodAmountPolicy.portionQuality(f),FoodSearchPolicy.searchRank(f,query)).joinToString(",",transform=::quote) }.joinToString("\n",postfix="\n"))
        assertTrue("See yogurt-search-ranking.csv: ${candidates.size} identities",
            candidates.any { it.id=="kfind-product-p119-202040200-0361" })
    }
}
