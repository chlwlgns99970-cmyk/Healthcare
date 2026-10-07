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
class ReferenceFinalizationSamsungTest {
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
        java.io.File(context.cacheDir,"reference-finalization-$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
        };bitmap.recycle()
    }
    private fun verified(id:String,rid:String,name:String,kind:String) {
        val rows=RecipeCaloriePolicy.lookupCompositions(id).first { it.first().recipeId==rid }
        assertTrue(rows.all { it.recipeComplete && it.compositionKind==kind && it.amountGrams>0 })
        assertEquals(name,rows.first().recipeName)
        show(id)
        if(kind=="ORIGINAL") visible("재료별 예상 열량")
        if(kind=="SURVEY_AVERAGE") visible("재료별 예상 열량 · 공공 조사 평균 참고 구성")
        visible("$name · ${rows.first().recipeBasis}")
        rows.forEach { row ->
            visible("${row.ingredientName} ${com.example.healthcare.domain.RecordedAmountSnapshot.format(row.amountGrams)}g · 약 ${kotlin.math.round(row.estimatedKcal).toInt()} kcal")
        }
        if(kind=="SURVEY_AVERAGE") visible("식이 조사 평균 자료로, 음식 조리를 위한 정보로 사용하기에는 적절하지 않습니다.")
        visible("$name · ${rows.first().recipeBasis}");capture(rid)
    }
    @Test fun publishedMapping01() { verified("kfind-d108-386000000-0001","MENUZEN-D102015","불고기(소고기)","REFERENCE_RECIPE") }
    @Test fun publishedMapping02() { verified("kfind-d110-481320000-0001","MENUZEN-D102019","소고기볶음(가지)","REFERENCE_RECIPE") }
    @Test fun publishedMapping03() { verified("kfind-d420-756000000-0001","MFDS-146","딸기바나나연두부쉐이크","ORIGINAL") }
    @Test fun publishedMapping04() { verified("kfind-d105-216390000-0001","MENUZEN-D052158","모시조개시금치된장국","REFERENCE_RECIPE") }
    @Test fun publishedMapping05() { verified("kfind-d405-318000000-0001","MENUZEN-D051287","모시조개미역국","REFERENCE_RECIPE") }
    @Test fun publishedMapping06() { verified("kfind-d312-540000000-0001","MENUZEN-D123002","고추튀김","REFERENCE_RECIPE") }
    @Test fun publishedMapping07() { verified("kfind-d105-216070000-0001","MENUZEN-D052010","냉이된장국(고추장, 바지락)","REFERENCE_RECIPE") }
    @Test fun publishedMapping08() { verified("kfind-d111-515000000-0001","MENUZEN-D112031","닭가슴살 채소조림","REFERENCE_RECIPE") }
    @Test fun publishedMapping09() { verified("kfind-d109-437050000-0001","MENUZEN-D092006","완자전(돼지고기)","REFERENCE_RECIPE") }
    @Test fun publishedMapping10() { verified("kfind-d109-437060000-0001","MENUZEN-D092007","완자전(소고기)","REFERENCE_RECIPE") }
    @Test fun publishedMapping11() { verified("kfind-d414-644000000-0001","MFDS-217","배오이무침","ORIGINAL") }
    @Test fun publishedMapping12() { verified("kfind-d112-537000000-0001","MFDS-219","감자채튀김","ORIGINAL") }
    @Test fun publishedMapping13() { verified("kfind-d105-245000000-0001","MENUZEN-D053103","버섯육개장","REFERENCE_RECIPE") }
    @Test fun publishedMapping14() { verified("kfind-d103-174000000-0001","MENUZEN-D031095","들깨버섯칼국수","REFERENCE_RECIPE") }
    @Test fun publishedMapping15() { verified("kfind-d110-457000000-0001","MENUZEN-D101006","낙지볶음(고추장)","REFERENCE_RECIPE") }
    @Test fun publishedMapping16() { verified("kfind-d505-290000000-0001","MENUZEN-D051192","바지락무국","REFERENCE_RECIPE") }
    @Test fun publishedMapping17() { verified("kfind-d505-205120000-0001","KDCA-281-11299","김치국, 두부","SURVEY_AVERAGE") }
    @Test fun publishedMapping18() { verified("kfind-d113-573000000-0001","MENUZEN-D132109","냉이나물","REFERENCE_RECIPE") }
    @Test fun publishedMapping19() { verified("kfind-d105-253000000-0001","MENUZEN-D051309","모시조개콩나물국","REFERENCE_RECIPE") }
    @Test fun publishedMapping20() { verified("kfind-d105-255000000-0001","MENUZEN-D053062","토란탕","REFERENCE_RECIPE") }
    @Test fun publishedMapping21() { verified("kfind-d410-493320000-0001","KDCA-281-3710","주꾸미볶음, 채소","SURVEY_AVERAGE") }
    @Test fun publishedMapping22() { verified("kfind-d105-223000000-0001","MENUZEN-D051043","미역국(감자)","REFERENCE_RECIPE") }
    @Test fun publishedMapping23() { verified("kfind-d105-245000000-0001","MENUZEN-D053027","육개장","REFERENCE_RECIPE") }
    @Test fun publishedMapping24() { verified("kfind-d109-439000000-0001","MENUZEN-D093014","야채전","REFERENCE_RECIPE") }
    @Test fun publishedMapping25() { verified("kfind-d508-375010000-0001","MENUZEN-D102012","불고기(돼지고기)","REFERENCE_RECIPE") }
    @Test fun publishedMapping26() { verified("kfind-d101-042410000-0001","MENUZEN-D016010","초밥(유부)","REFERENCE_RECIPE") }
    @Test fun publishedMapping27() { verified("kfind-d114-605130000-0001","MFDS-312","봄동겉절이","ORIGINAL") }
    @Test fun publishedMapping28() { verified("kfind-d105-223220000-0001","MENUZEN-D051256","새우살미역국","REFERENCE_RECIPE") }
    @Test fun publishedMapping29() { verified("kfind-d401-032620000-0001","MENUZEN-D012101","서리태흑미밥","REFERENCE_RECIPE") }
    @Test fun publishedMapping30() { verified("kfind-d513-595013200-0001","MENUZEN-D103051","취나물볶음","REFERENCE_RECIPE") }
    @Test fun publishedMapping31() { verified("kfind-d405-219000000-0001","MENUZEN-D051175","다시마무국","REFERENCE_RECIPE") }
    @Test fun publishedMapping32() { verified("kfind-d113-593000000-0001","MENUZEN-D132116","참나물깨즙무침","REFERENCE_RECIPE") }
    @Test fun publishedMapping33() { verified("kfind-d113-593000000-0001","MENUZEN-D132116","참나물깨즙무침","REFERENCE_RECIPE") }
    @Test fun publishedMapping34() { verified("kfind-d108-380000000-0001","MFDS-345","송이버섯구이","ORIGINAL") }
    @Test fun publishedMapping35() { verified("kfind-d101-026000000-0001","MENUZEN-D014009","영양돌솥밥","REFERENCE_RECIPE") }
    @Test fun publishedMapping36() { verified("kfind-d105-205370000-0001","MENUZEN-D051019","김칫국(콩나물)","REFERENCE_RECIPE") }
    @Test fun publishedMapping37() { verified("kfind-d108-386000000-0001","MENUZEN-D102016","불고기(소고기, 배)","REFERENCE_RECIPE") }
    @Test fun publishedMapping38() { verified("kfind-d305-235000000-0001","MENUZEN-D061040","아구매운탕(고추장)","REFERENCE_RECIPE") }
}
