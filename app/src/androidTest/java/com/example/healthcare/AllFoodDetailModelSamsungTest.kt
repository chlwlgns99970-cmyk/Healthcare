package com.example.healthcare

import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.FoodMetadataStore
import com.example.healthcare.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

/** Real packaged CSV parser, metadata loader and production presentation model for every ID. */
class AllFoodDetailModelSamsungTest {
    @Test fun everyPackagedFoodAndMenuHasAnExactCompletePresentation()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa",context.packageName)
        val app=context.applicationContext as HealthcareApplication
        app.foodMetadataStore.ensureLoaded()
        val bundle=context.assets.open("fooddata/food_data_manifest.properties").bufferedReader().use { it.readLines().first { line->line.startsWith("bundleId=") }.substringAfter('=') }
        withTimeout(180000) { while(context.getSharedPreferences("bundled_food_seed",0).getString("bundle_id",null)!=bundle)delay(100) }
        val databaseIds=mutableSetOf<String>()
        val database=com.example.healthcare.data.database.AppDatabase.getDatabase(context)
        database.openHelper.readableDatabase.query("SELECT id FROM food_items").use { cursor ->
            while(cursor.moveToNext())databaseIds+=cursor.getString(0)
        }
        val ids=HashSet<String>();var count=0;var basisReview=0;var lifestyle=0;var partial=0;var refs=0
        val sourceCounts=mutableMapOf<String,Int>()
        val missing=mutableListOf<String>()
        var publicTextReferences=0;var displayQueryMissing=0
        var defaultLifestyle=0;var gramMlFallback=0;var manualAmountReview=0
        val names=java.io.File(context.cacheDir,"all-food-runtime-names.jsonl").bufferedWriter()
        listOf("food_items.csv","product_items.csv","franchise_official_items.csv").forEach { file ->
            context.assets.open("fooddata/$file").bufferedReader().use { reader ->
                val headers=FoodMetadataStore.csv(reader.readLine())
                reader.lineSequence().filter(String::isNotBlank).forEach { line ->
                    val row=headers.zip(FoodMetadataStore.csv(line)).toMap()
                    fun text(k:String)=row.getValue(k)
                    fun number(k:String)=row[k]?.takeIf(String::isNotBlank)?.toDouble()
                    val food=FoodItem(text("id"),text("sourceType"),text("sourceFoodCode"),text("name"),text("normalizedName"),
                        text("aliases"),row["category"],text("referenceAmount").toDouble(),text("unit"),text("energyKcal").toDouble(),
                        number("carbohydrateGrams"),number("proteinGrams"),number("fatGrams"),number("sodiumMilligrams"),
                        text("servingDescription"),row["brand"]?.takeIf(String::isNotBlank),row["barcode"],text("dataVersion"),
                        text("createdAt").toLong(),text("updatedAt").toLong())
                    assertTrue(ids.add(food.id));count++
                    assertTrue(food.id,databaseIds.contains(food.id))
                    val model=FoodDetailPolicy.forFood(food)
                    names.appendLine(JSONObject().put("foodId",food.id).put("displayName",model.name)
                        .put("displayQuery",FoodSearchPolicy.normalize(model.name)).toString())
                    assertEquals(food.id,model.foodId);assertTrue(model.name.isNotBlank())
                    assertEquals(5,model.nutrition.size)
                    assertEquals(RecordedAmountSnapshot.format(food.energyKcal)+" kcal",model.nutrition[0].value)
                    listOf(food.carbohydrateGrams,food.proteinGrams,food.fatGrams,food.sodiumMilligrams).forEachIndexed { index,value ->
                        val suffix=if(index==3)"mg" else "g"
                        assertEquals(food.id,value?.let { RecordedAmountSnapshot.format(it)+suffix } ?: "미확인",model.nutrition[index+1].value)
                    }
                    assertFalse(model.nutritionBasis.contains("미확인"))
                    val metadata=FoodMetadataPolicy.lookup(food.id)
                    assertNotNull(food.id,metadata)
                    val m=metadata!!
                    val facts=model.facts.associate { it.key to it.value }
                    assertEquals(food.sourceFoodCode,facts["sourceIdentifier"])
                    assertEquals(m.checkedAt,facts["checkedAt"])
                    if(m.manufacturer.isNotBlank())assertTrue(food.id,facts.values.contains(m.manufacturer.trim()))
                    if(!food.servingDescription.isNullOrBlank())assertEquals(food.servingDescription!!.trim(),facts["servingDescription"])
                    when(FoodAmountPolicy.defaultChoice(food)?.unit) {
                        null -> manualAmountReview++
                        "g","ml" -> gramMlFallback++
                        else -> defaultLifestyle++
                    }
                    val query=FoodSearchPolicy.normalize(model.name)
                    if(!food.normalizedName.contains(query) && !food.aliases.contains(query))displayQueryMissing++
                    listOf("packageSize" to m.packageSize,"intakeReference" to m.intakeReference,
                        "classification" to m.rawClassification,"sourceReference" to m.sourceReference,
                        "sourceDate" to m.sourceDate,"productReportNumber" to m.productReportNumber,
                        "allergenText" to m.allergenText,"crossContactText" to m.crossContactText).forEach { (key,value) ->
                        if(value.isNotBlank() && facts[key]!=value.trim())missing+="${food.id}:$key"
                    }
                    val declared=m.completeIngredientText.takeIf(String::isNotBlank) ?: m.ingredientText.takeIf(String::isNotBlank)
                    if(declared!=null)assertEquals(food.id,declared,model.ingredientText)
                    if(m.referenceIngredientText.isNotBlank()) {
                        publicTextReferences++
                        assertEquals(m.referenceIngredientText,model.referenceRecipe.single { it.key=="referenceIngredientText" }.value)
                        assertEquals(m.referenceRecipeUrl,model.referenceRecipe.single { it.key=="referenceUrl" }.value)
                    }
                    if(model.basisNeedsReview)basisReview++
                    if(model.facts.any { it.key=="householdUnit" })lifestyle++
                    if(model.nutrition.drop(1).take(3).any { it.value=="미확인" })partial++
                    if(model.compositionIds.isNotEmpty())refs++
                    assertEquals(RecipeCaloriePolicy.lookupCompositions(food.id).map { it.first().recipeId },model.compositionIds)
                    sourceCounts[food.sourceType]=(sourceCounts[food.sourceType]?:0)+1
                }
            }
        }
        assertEquals(67357,count);assertEquals(emptyList<String>(),missing)
        assertEquals(ids,databaseIds);assertEquals(0,displayQueryMissing)
        names.close()
        val menus=FranchiseCatalog.brands.flatMap { FranchiseCatalog.officialMenus(it) }
        val rawMenus=OfficialFranchiseMenus.entries
        val menuIds=(menus+rawMenus).map { it.id }.toSet()
        val orphan=FoodMetadataPolicy.snapshot().filter { it.foodId !in ids && it.foodId !in menuIds }
        assertEquals("metadata must resolve to Room Food or an exact official catalog identity",emptyList<String>(),orphan.map { it.foodId })
        menus.forEach { menu ->
            val model=FoodDetailPolicy.forMenu(menu)
            assertEquals(menu.id,model.foodId);assertTrue(model.name.isNotBlank())
            assertTrue(model.facts.any { it.key=="sourceReference" && it.value.isNotBlank() })
            if(menu.energyKcal==null)assertEquals("미확인",model.nutrition[0].value)
        }
        val report=JSONObject().put("foods",count).put("modelSuccess",count).put("modelFailure",0)
            .put("dataExistsUiMissing",missing.size).put("metadataOrphan",orphan.size)
            .put("catalogMenus",menus.size).put("catalogModelSuccess",menus.size)
            .put("basisReviewFactsRetained",basisReview).put("lifestyleUnitDisplayed",lifestyle)
            .put("partialNutrition",partial).put("compositionFoods",refs).put("sources",JSONObject(sourceCounts))
            .put("publicRecipeTextFoods",publicTextReferences).put("displayQueryMissing",displayQueryMissing)
            .put("franchiseNutritionFoods",database.foodItemDao().observeAllFranchiseFoods(FranchiseCatalog.brands).first().size)
            .put("defaultLifestyleFoods",defaultLifestyle).put("gramMlFallbackFoods",gramMlFallback).put("manualAmountReviewFoods",manualAmountReview)
        java.io.File(context.cacheDir,"all-food-detail-model-audit.json").writeText(report.toString(2))
    }
}
