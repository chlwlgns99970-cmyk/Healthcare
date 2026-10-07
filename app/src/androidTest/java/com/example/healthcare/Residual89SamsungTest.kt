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
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthcare.data.FoodMetadataStore
import com.example.healthcare.domain.RecipeCaloriePolicy
import com.example.healthcare.domain.RecordedAmountSnapshot
import com.example.healthcare.ui.screens.RecipeReferenceCard
import com.example.healthcare.ui.theme.HealthCareTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Every new Food mapping, using the packaged common assets; no persistent writes. */
class Residual89SamsungTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun loadAssets() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        runBlocking { FoodMetadataStore(context).ensureLoaded() }
    }
    private fun visible(text:String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }
    private fun verify(id:String,rid:String,name:String,kind:String) {
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId==rid }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind==kind && it.amountGrams>0 })
        assertEquals(name,rows.first().recipeName)
        assertNotNull(rows.first().foodReferenceKcal)
        compose.setContent { HealthCareTheme {
            val device=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(device.density,1.30f)) {
                Column(Modifier.width(360.dp).height(720.dp).verticalScroll(rememberScrollState())) {
                    RecipeReferenceCard(id)
                }
            }
        } }
        visible("$name · ${rows.first().recipeBasis}")
        visible(if (kind=="SURVEY_AVERAGE") "재료별 예상 열량 · 공공 조사 평균 참고 구성" else "재료별 예상 열량 · 공식 레시피 참고 구성")
        rows.forEach {
            visible("${it.ingredientName} ${RecordedAmountSnapshot.format(it.amountGrams)}g · 약 ${kotlin.math.round(it.estimatedKcal).toInt()} kcal")
        }
        visible("$name · ${rows.first().recipeBasis}")
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(context.cacheDir,"residual-89-$id.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        }
        bitmap.recycle()
    }
    private fun salad(id:String)=verify(id,"DAEGU-LOW-SODIUM-P40","양상추샐러드(마요네즈 소스)","REFERENCE_RECIPE")
    private fun potato(id:String)=verify(id,"KDCA-281-6394","감자볶음, 베이컨","SURVEY_AVERAGE")
    @Test fun salad01()=salad("kfind-d114-640230000-0001")
    @Test fun salad02()=salad("kfind-d414-704000000-0001")
    @Test fun salad03()=salad("kfind-d514-704000000-0001")
    @Test fun salad04()=salad("kfind-d614-704000000-0001")
    @Test fun salad05()=salad("kfind-d714-704000000-0001")
    @Test fun potato01()=potato("kfind-d510-450032500-0001")
    @Test fun potato02()=potato("kfind-d610-450032500-0001")
}
