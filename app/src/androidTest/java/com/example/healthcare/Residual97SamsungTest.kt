package com.example.healthcare

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.FoodMetadataStore
import com.example.healthcare.data.database.AppDatabase
import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.repository.FoodRepository
import com.example.healthcare.data.repository.MealRepository
import com.example.healthcare.domain.RecipeCaloriePolicy
import com.example.healthcare.ui.screens.RecipeReferenceCard
import com.example.healthcare.ui.theme.HealthCareTheme
import com.example.healthcare.ui.viewmodel.AddRecordViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Packaged production-common assets on Samsung; record writes use only in-memory Room. */
class Residual97SamsungTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val kimbap="kfind-d101-007000000-0001"
    @Before fun loadPackagedAssets() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        runBlocking { FoodMetadataStore(context).ensureLoaded() }
    }
    private fun show(id:String) {
        compose.setContent { HealthCareTheme {
            val device=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(device.density,1.30f)) {
                Column(Modifier.width(360.dp).height(720.dp).verticalScroll(rememberScrollState())) {
                    RecipeReferenceCard(id)
                }
            }
        } }
    }
    private fun visible(text:String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }
    private fun capture(name:String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(context.cacheDir,"residual-97-$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        };bitmap.recycle()
    }
    private fun verified(id:String,rid:String,name:String) {
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId==rid }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind=="REFERENCE_RECIPE" && it.amountGrams>0 })
        assertEquals(name,rows.first().recipeName)
        show(id)
        visible("$name · ${rows.first().recipeBasis}")
        rows.forEach { row ->
            visible("${row.ingredientName} ${com.example.healthcare.domain.RecordedAmountSnapshot.format(row.amountGrams)}g · 약 ${kotlin.math.round(row.estimatedKcal).toInt()} kcal")
        }
        visible("$name · ${rows.first().recipeBasis}");capture(rid)
    }
    @Test fun newMapping01() { verified("kfind-d110-464320000-0001","MENUZEN-D102007","돼지고기볶음(고추장, 야채)") }
    @Test fun newMapping02() { verified("kfind-d110-457000000-0001","MENUZEN-D101006","낙지볶음(고추장)") }
}
