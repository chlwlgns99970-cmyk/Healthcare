package com.example.healthcare.ui

import androidx.annotation.DrawableRes
import com.example.healthcare.R
import com.example.healthcare.domain.MealRecommendationEngine

/**
 * 검증 식단의 안정 ID를 대표 사진에 연결합니다.
 *
 * 등록 추천은 stable template ID로 전용 사진에 연결합니다. 카탈로그 밖의 알 수 없는 음식만
 * null을 반환해 잘못된 사진을 노출하지 않습니다.
 */
object RecommendationImageResolver {
    private val templateImages = mapOf(
        "kfind-breakfast-cook-1" to R.drawable.photo_meal_sesame_porridge,
        "kfind-breakfast-cook-2" to R.drawable.photo_curry_rice,
        "kfind-breakfast-cook-3" to R.drawable.photo_meal_gondre_rice,
        "kfind-breakfast-dining_out-1" to R.drawable.photo_meal_bibim_naengmyeon,
        "kfind-breakfast-dining_out-2" to R.drawable.photo_meal_tuna_sandwich,
        "kfind-breakfast-dining_out-3" to R.drawable.photo_meal_green_chili_kimbap,
        "kfind-breakfast-convenience-1" to R.drawable.photo_chicken_sandwich,
        "kfind-breakfast-convenience-2" to R.drawable.photo_meal_ham_cheese_french_toast,
        "kfind-breakfast-convenience-3" to R.drawable.photo_meal_chicken_tender_wrap,
        "kfind-lunch-cook-1" to R.drawable.photo_fried_rice,
        "kfind-lunch-cook-2" to R.drawable.photo_meal_omurice,
        "kfind-lunch-cook-3" to R.drawable.photo_fried_rice,
        "kfind-lunch-dining_out-1" to R.drawable.photo_meal_jjolmyeon,
        "kfind-lunch-dining_out-2" to R.drawable.photo_meal_pork_bone_soup,
        "kfind-lunch-dining_out-3" to R.drawable.photo_udon_soup,
        "kfind-lunch-convenience-1" to R.drawable.photo_meal_bulnak_hotpot,
        "kfind-lunch-convenience-2" to R.drawable.photo_meal_mille_feuille_nabe,
        "kfind-lunch-convenience-3" to R.drawable.photo_fried_rice,
        "kfind-dinner-cook-1" to R.drawable.photo_meal_mackerel_stew,
        "kfind-dinner-cook-2" to R.drawable.photo_fried_rice,
        "kfind-dinner-cook-3" to R.drawable.photo_meal_tteokguk,
        "kfind-dinner-dining_out-1" to R.drawable.photo_meal_sashimi_rice_bowl,
        "kfind-dinner-dining_out-2" to R.drawable.photo_sushi,
        "kfind-dinner-dining_out-3" to R.drawable.photo_meal_dumpling_soup,
        "kfind-dinner-convenience-1" to R.drawable.photo_chicken_sandwich,
        "kfind-dinner-convenience-2" to R.drawable.photo_meal_baked_spaghetti,
        "kfind-dinner-convenience-3" to R.drawable.photo_meal_soup_tteokbokki,
        "kfind-snack-cook-1" to R.drawable.photo_meal_pumpkin_porridge,
        "kfind-snack-cook-2" to R.drawable.photo_meal_misutgaru,
        "kfind-snack-cook-3" to R.drawable.photo_meal_garaetteok,
        "kfind-snack-dining_out-1" to R.drawable.photo_meal_jeungpyeon,
        "kfind-snack-dining_out-2" to R.drawable.photo_meal_glutinous_rice_donut,
        "kfind-snack-dining_out-3" to R.drawable.photo_meal_ice_cream_sundae,
        "kfind-snack-convenience-1" to R.drawable.photo_meal_iced_latte,
        "kfind-snack-convenience-2" to R.drawable.photo_meal_hot_grapefruit,
        "kfind-snack-convenience-3" to R.drawable.photo_meal_greek_yogurt_cupcake
    ) + GeneratedRecommendationImages.templateImages

    @DrawableRes
    fun resolveTemplate(templateId: String): Int? = templateImages[templateId]

    internal fun mappedTemplateIds(): Set<String> = templateImages.keys

    @DrawableRes
    fun resolveName(foodName: String): Int? {
        val name = MealRecommendationEngine.normalizeFoodName(foodName)
        return when {
            name.containsAll("연어", "아보카도", "샐러드") -> R.drawable.photo_salmon_avocado_salad
            name.containsAny("요거트과일볼", "요거트과일", "과일요거트볼") -> R.drawable.photo_breakfast_yogurt_bowl
            name.containsAny("모듬초밥", "초밥모듬") -> R.drawable.photo_sushi
            name.contains("카레라이스") -> R.drawable.photo_curry_rice
            name.contains("볶음밥") -> R.drawable.photo_fried_rice
            name.contains("샌드위치") && name.containsAny("닭", "치킨") -> R.drawable.photo_chicken_sandwich
            name.contains("우동") -> R.drawable.photo_udon_soup
            else -> null
        }
    }

    @DrawableRes
    fun resolve(templateId: String?, foodName: String): Int? =
        templateId?.let(::resolveTemplate) ?: resolveName(foodName)

    private fun String.containsAny(vararg tokens: String): Boolean = tokens.any(::contains)
    private fun String.containsAll(vararg tokens: String): Boolean = tokens.all(::contains)
}
