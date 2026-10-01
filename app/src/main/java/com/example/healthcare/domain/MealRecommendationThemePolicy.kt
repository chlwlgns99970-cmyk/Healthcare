package com.example.healthcare.domain

import com.example.healthcare.data.entity.MealTemplate
import com.example.healthcare.data.model.MealType

enum class MealRecommendationTheme(val label: String, val description: String) {
    LIGHT("가볍게 먹고 싶은 날", "같은 식사 시간의 후보 중 칼로리가 낮은 쪽 1/3에서 골라요."),
    BALANCED("균형 있게 먹기", "앱 분류 기준: 탄단지 열량비가 탄수화물 45~65%, 단백질 10~35%, 지방 20~35%인 메뉴예요."),
    FILLING("든든하게 먹기", "같은 식사 시간의 후보 중 칼로리가 높은 쪽 1/3에서 골라요.")
}

/** Classify a meal's hard-filtered pool before calorie tolerance and cycle exclusions. */
object MealRecommendationThemePolicy {
    fun select(
        candidates: List<RecommendationSeed>,
        theme: MealRecommendationTheme,
        mealType: MealType
    ): List<RecommendationSeed> {
        val pool = candidates.filter {
            it.template.totalKcal > 0 && MealRecommendationEngine.supportsMeal(it.template, mealType)
        }.distinctBy { it.template.id }
            .distinctBy { MealRecommendationEngine.normalizeFoodName(it.template.name) }
        if (theme == MealRecommendationTheme.BALANCED) {
            return pool.filter { matchesBalanced(it.template) }
        }
        val calories = pool.map { it.template.totalKcal }.sorted()
        if (calories.size < 2 || calories.first() == calories.last()) return emptyList()
        return when (theme) {
            MealRecommendationTheme.LIGHT -> {
                val boundary = calories[(calories.size - 1) / 3]
                pool.filter { it.template.totalKcal <= boundary && it.template.totalKcal < calories.last() }
            }
            MealRecommendationTheme.FILLING -> {
                val boundary = calories[(2 * (calories.size - 1) + 2) / 3]
                pool.filter { it.template.totalKcal >= boundary && it.template.totalKcal > calories.first() }
            }
            MealRecommendationTheme.BALANCED -> emptyList()
        }
    }

    /** This is the app's explicit macro classification, not an assessment of overall health. */
    fun matchesBalanced(template: MealTemplate): Boolean {
        val carbohydrate = template.carbohydrateGrams ?: return false
        val protein = template.proteinGrams ?: return false
        val fat = template.fatGrams ?: return false
        if (listOf(carbohydrate, protein, fat).any { !it.isFinite() || it <= 0.0 }) return false
        val carbohydrateEnergy = carbohydrate * 4.0
        val proteinEnergy = protein * 4.0
        val fatEnergy = fat * 9.0
        val total = carbohydrateEnergy + proteinEnergy + fatEnergy
        if (!total.isFinite() || total <= 0.0) return false
        return carbohydrateEnergy / total in 0.45..0.65 &&
            proteinEnergy / total in 0.10..0.35 && fatEnergy / total in 0.20..0.35
    }
}
