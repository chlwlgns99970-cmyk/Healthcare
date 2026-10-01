package com.example.healthcare.domain

import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import java.time.LocalDate
import java.time.LocalTime

val DashboardMealOrder = listOf(
    MealType.BREAKFAST,
    MealType.LUNCH,
    MealType.DINNER,
    MealType.SNACK
)

data class DashboardMealSummary(
    val mealType: MealType,
    val primaryFoodName: String?,
    val additionalFoodCount: Int,
    val knownCalories: Int?,
    val hasUnknownCalories: Boolean
) {
    val displayText: String
        get() = when {
            primaryFoodName == null -> "기록 없음"
            additionalFoodCount == 0 && knownCalories != null -> "$primaryFoodName · $knownCalories kcal"
            additionalFoodCount == 0 -> "$primaryFoodName · 열량 정보 없음"
            knownCalories != null && hasUnknownCalories ->
                "$primaryFoodName 외 ${additionalFoodCount}개 · 확인 가능한 기록 $knownCalories kcal"
            knownCalories != null -> "$primaryFoodName 외 ${additionalFoodCount}개 · 총 $knownCalories kcal"
            else -> "$primaryFoodName 외 ${additionalFoodCount}개 · 열량 정보 없음"
        }
}

data class TodayFoodReportItem(
    val recordId: Long,
    val mealType: MealType,
    val name: String,
    val calories: Int?
)

data class TodayMealReport(
    val mealType: MealType,
    val calories: Int?,
    val hasUnknownCalories: Boolean
)

data class TodayFoodReport(
    val totalCalories: Int,
    val targetCalories: Int?,
    val remainingCalories: Int?,
    val nutrition: Macronutrients,
    val meals: List<TodayMealReport>,
    val foods: List<TodayFoodReportItem>,
    val hasPartialNutrition: Boolean,
    val message: String
)

object DashboardSummaryPolicy {
    fun mealSummaries(meals: List<MealRecord>): List<DashboardMealSummary> =
        DashboardMealOrder.map { type ->
            val records = meals.filter { it.mealType == type }
            val known = records.map(MealRecord::calories).filter { it > 0 }
            DashboardMealSummary(
                mealType = type,
                primaryFoodName = records.firstOrNull()?.foodName,
                additionalFoodCount = (records.size - 1).coerceAtLeast(0),
                knownCalories = known.takeIf(List<Int>::isNotEmpty)?.sum(),
                hasUnknownCalories = records.any { it.calories <= 0 }
            )
        }

    fun report(
        meals: List<MealRecord>,
        targetCalories: Int?,
        nutrition: Macronutrients
    ): TodayFoodReport {
        val knownFoods = meals.filter { it.calories > 0 }
        val total = knownFoods.sumOf(MealRecord::calories)
        val target = targetCalories?.takeIf { it > 0 }
        val remaining = target?.minus(total)
        val mealReports = DashboardMealOrder.map { type ->
            val records = meals.filter { it.mealType == type }
            val known = records.filter { it.calories > 0 }
            TodayMealReport(
                mealType = type,
                calories = known.takeIf(List<MealRecord>::isNotEmpty)?.sumOf(MealRecord::calories),
                hasUnknownCalories = records.any { it.calories <= 0 }
            )
        }
        val foods = meals.map { record ->
            TodayFoodReportItem(
                recordId = record.id,
                mealType = record.mealType,
                name = record.foodName,
                calories = record.calories.takeIf { it > 0 }
            )
        }.sortedWith(compareByDescending<TodayFoodReportItem> { it.calories ?: -1 }.thenBy { it.name })
        val partial = meals.any { it.calories <= 0 } ||
            nutrition.carbohydrateGrams == null || nutrition.proteinGrams == null || nutrition.fatGrams == null
        val message = when {
            meals.isEmpty() -> "오늘 기록된 식사가 없어요."
            remaining == null -> "오늘 기록한 음식과 영양정보를 모아봤어요."
            remaining >= 0 -> "오늘 목표까지 약 $remaining kcal 남았어요."
            else -> "오늘 기록은 목표보다 약 ${-remaining} kcal 높아요. 기록 내용을 확인해보세요."
        }
        return TodayFoodReport(total, target, remaining, nutrition, mealReports, foods, partial, message)
    }

    fun situationMessage(
        selectedDate: LocalDate,
        today: LocalDate,
        now: LocalTime,
        meals: List<MealRecord>,
        targetCalories: Int?,
        variant: Int
    ): String {
        if (selectedDate != today) return "선택한 날짜의 식사 기록을 확인하고 있어요."
        val total = meals.filter { it.calories > 0 }.sumOf(MealRecord::calories)
        val target = targetCalories?.takeIf { it > 0 }
        val messages = when {
            meals.isEmpty() && now.hour < 11 -> listOf(
                "좋은 아침이에요. 아침 식사를 기록해볼까요?",
                "오늘 첫 식사를 편하게 기록해보세요."
            )
            meals.isEmpty() -> listOf(
                "오늘 첫 식사를 기록해보세요.",
                "먹은 음식부터 하나씩 남겨볼까요?"
            )
            target != null && total > target -> listOf(
                "오늘 기록은 목표보다 약 ${total - target} kcal 높아요. 기록 내용을 확인해보세요.",
                "오늘 기록을 모두 모았어요. 목표 대비 내용을 확인해보세요."
            )
            meals.map(MealRecord::mealType).distinct().size >= 2 -> listOf(
                "오늘도 식사 기록을 이어가고 있어요.",
                "기록한 식사를 오늘 리포트에서 한눈에 볼 수 있어요."
            )
            target != null -> listOf(
                "오늘 목표까지 약 ${(target - total).coerceAtLeast(0)} kcal 남았어요.",
                "한 끼를 기록했어요. 남은 기록도 편하게 이어가세요."
            )
            else -> listOf(
                "오늘 식사 기록을 이어가고 있어요.",
                "기록한 음식은 오늘 리포트에서 비교할 수 있어요."
            )
        }
        return messages[Math.floorMod(variant, messages.size)]
    }
}
