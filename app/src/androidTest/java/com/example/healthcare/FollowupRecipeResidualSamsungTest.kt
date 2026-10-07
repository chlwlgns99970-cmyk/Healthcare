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
class FollowupRecipeResidualSamsungTest {
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
        java.io.File(context.cacheDir,"followup-residual-$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        };bitmap.recycle()
    }
    private fun verified(id:String,rid:String,name:String,kind:String) {
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId==rid }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind==kind && it.amountGrams>0 })
        assertEquals(name,rows.first().recipeName)
        show(id);visible("$name · ${rows.first().recipeBasis}")
        val last=rows.last()
        visible("${last.ingredientName} ${com.example.healthcare.domain.RecordedAmountSnapshot.format(last.amountGrams)}g · 약 ${kotlin.math.round(last.estimatedKcal).toInt()} kcal")
        if(kind=="SURVEY_AVERAGE") visible("식이 조사 평균 자료로, 음식 조리를 위한 정보로 사용하기에는 적절하지 않습니다.")
        visible("$name · ${rows.first().recipeBasis}");capture(rid)
    }
    @Test fun reviewedMapping01() { verified("kfind-d111-532040000-0001","MENUZEN-D112009","메추리알장조림","REFERENCE_RECIPE") }
    @Test fun reviewedMapping02() { verified("kfind-d113-584000000-0001","MENUZEN-D132020","숙주나물","REFERENCE_RECIPE") }
    @Test fun reviewedMapping03() { verified("kfind-d405-225130000-0001","MENUZEN-D051194","배추들깨국","REFERENCE_RECIPE") }
    @Test fun reviewedMapping04() { verified("kfind-d113-595050000-0001","MENUZEN-D132034","취나물무침(된장)","REFERENCE_RECIPE") }
    @Test fun reviewedMapping05() { verified("kfind-d111-517000000-0001","MENUZEN-D114004","두부조림(돼지고기)","REFERENCE_RECIPE") }
    @Test fun reviewedMapping06() { verified("kfind-d405-226150000-0001","KDCA-281-8494","북어국, 무","SURVEY_AVERAGE") }
    @Test fun reviewedMapping07() { verified("kfind-d105-242000000-0001","MENUZEN-D051131","오징어국","REFERENCE_RECIPE") }
    @Test fun reviewedMapping08() { verified("kfind-d405-310000000-0001","MENUZEN-D051173","꼬치어묵국","REFERENCE_RECIPE") }
    @Test fun reviewedMapping09() { verified("kfind-d409-457000000-0001","MENUZEN-D095013","오믈렛","REFERENCE_RECIPE") }
    @Test fun reviewedMapping10() { verified("kfind-d113-568000000-0001","MENUZEN-D103009","고구마줄기볶음","REFERENCE_RECIPE") }
    @Test fun reviewedMapping11() { verified("kfind-d103-160000000-0001","MENUZEN-D031009","수제비","REFERENCE_RECIPE") }
    @Test fun reviewedMapping12() { verified("kfind-d303-151000000-0001","MENUZEN-D051147","만두국","REFERENCE_RECIPE") }
    @Test fun reviewedMapping13() { verified("kfind-d310-466000000-0001","KDCA-281-3306","두부김치, 돼지고기","SURVEY_AVERAGE") }
    @Test fun reviewedMapping14() { verified("kfind-d310-493000000-0001","KDCA-281-3710","주꾸미볶음, 채소","SURVEY_AVERAGE") }
    @Test fun reviewedMapping15() { verified("kfind-d514-644430000-0001","KDCA-281-7626","오이생채, 달래","SURVEY_AVERAGE") }
    @Test fun reviewedMapping16() { verified("kfind-d505-306440000-0001","MENUZEN-D052124","마른새우아욱국","REFERENCE_RECIPE") }
    @Test fun reviewedMapping17() { verified("kfind-d514-619260000-0001","MENUZEN-D132096","도라지오이무침","REFERENCE_RECIPE") }
    @Test fun reviewedMapping18() { verified("kfind-d410-469050000-0001","KDCA-281-12175","마늘쫑볶음, 건새우","SURVEY_AVERAGE") }
    @Test fun reviewedMapping19() { verified("kfind-d414-721000000-0001","MENUZEN-D132037","콩나물무침(겨자, 미나리)","REFERENCE_RECIPE") }
    @Test fun reviewedMapping20() { verified("kfind-d414-721000000-0001","MENUZEN-D132037","콩나물무침(겨자, 미나리)","REFERENCE_RECIPE") }
}
