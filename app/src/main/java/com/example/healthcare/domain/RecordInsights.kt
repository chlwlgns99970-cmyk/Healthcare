package com.example.healthcare.domain

import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.MealType
import java.time.LocalDate
import kotlin.math.roundToInt

data class RecordPeriodSummary(
    val days: Int,
    val recordedDays: Int,
    val calorieDays: Int,
    val averageCalories: Int?,
    val mostRecordedMeals: List<MealType>,
    val nutrition: Macronutrients,
    val partial: Boolean
) {
    val lines: List<String> get() = if (recordedDays == 0) listOf("아직 요약할 기록이 충분하지 않아요.") else buildList {
        add("최근 ${days}일 중 ${recordedDays}일 기록했어요.")
        averageCalories?.let {
            add(if (partial) "확인 가능한 ${calorieDays}일 기록의 평균은 약 $it kcal예요."
                else "기록한 날의 평균 섭취량은 약 $it kcal예요.")
        }
        if (mostRecordedMeals.isNotEmpty()) add("${mostRecordedMeals.joinToString("·") { it.displayName }} 기록이 가장 많아요.")
        if (partial) add("확인 가능한 기록 기준으로 계산했어요.")
    }
}

data class NextMealGuidance(
    val intakeCalories: Int,
    val targetCalories: Int?,
    val remainingCalories: Int?,
    val nextMeal: MealType?,
    val range: IntRange?,
    val partial: Boolean,
    val message: String
)

object RecordInsights {
    fun period(records: List<MealRecord>, end: LocalDate, days: Int): RecordPeriodSummary {
        val count = days.coerceIn(1, 366)
        val start = end.minusDays(count.toLong() - 1)
        val selected = records.filter { record ->
            runCatching { LocalDate.parse(record.date) }.getOrNull()?.let { it in start..end } == true
        }
        val recordedDays = selected.map { it.date }.distinct().size
        val totals = selected.filter { it.calories > 0 }.groupBy { it.date }
            .mapValues { (_, rows) -> rows.sumOf { it.calories.toLong() } }
        val average = totals.values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val frequencies = selected.groupingBy { it.mealType }.eachCount()
        val top = frequencies.values.maxOrNull()
        val common = DashboardMealOrder.filter { top != null && frequencies[it] == top }
        val nutrition = Macronutrients.knownSum(selected.map { Macronutrients(it.carbohydrateGrams, it.proteinGrams, it.fatGrams) })
        val partial = selected.any { it.calories <= 0 || it.carbohydrateGrams == null || it.proteinGrams == null || it.fatGrams == null }
        return RecordPeriodSummary(count, recordedDays, totals.size, average, common, nutrition, partial)
    }

    fun nextMeal(records: List<MealRecord>, target: Int?): NextMealGuidance {
        val safeTarget = target?.takeIf { it > 0 }
        val intake = records.filter { it.calories > 0 }.sumOf { it.calories.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val partial = records.any { it.calories <= 0 }
        val remaining = safeTarget?.let { it - intake }
        val remainingMeals = DailyMealPlanEngine.slots.filter { meal -> records.none { it.mealType == meal } }
        val next = remainingMeals.firstOrNull()
        val range = remaining?.let { budget -> next?.let { DailyMealPlanEngine.nextMealRange(budget, remainingMeals.toSet(), it) } }
        val prefix = if (partial) "확인 가능한 기록 기준 · " else ""
        val message = prefix + when {
            remaining == null -> "하루 목표 칼로리를 설정하면 다음 식사 범위를 안내해요."
            remaining < 0 -> "현재 기록은 오늘 목표보다 약 ${-remaining} kcal 높아요."
            remaining == 0 -> "현재 기록은 오늘 목표와 같아요."
            next == null -> "네 식사의 기록을 모두 남겼어요. 오늘 리포트에서 확인해보세요."
            range != null -> "${next.displayName}은 약 ${range.first}~${range.last} kcal 범위로 고르면 오늘 목표에 가까워요."
            else -> "오늘 목표까지 약 $remaining kcal 남았어요."
        }
        return NextMealGuidance(intake, safeTarget, remaining?.coerceAtLeast(0), next, range, partial, message)
    }
}
