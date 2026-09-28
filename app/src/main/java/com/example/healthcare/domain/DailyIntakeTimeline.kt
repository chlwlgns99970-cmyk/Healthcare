package com.example.healthcare.domain

import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.TargetMode
import java.time.LocalDate

/** Read-only summaries built from the existing meal, goal, and energy-profile histories. */
enum class DailyIntakeStatus {
    NO_RECORD,
    NO_TARGET,
    OVER,
    BALANCED,
    UNDER,
    BELOW_BMR
}

/** Calendar-only goal state. Detailed intake coaching remains in [DailyIntakeStatus]. */
enum class CalendarGoalStatus {
    ACHIEVED,
    EXCEEDED,
    NO_DATA,
    UNKNOWN_TARGET
}

data class DailyIntakeSummary(
    val date: LocalDate,
    val intakeCalories: Int,
    val targetCalories: Int?,
    val bmrCalories: Int?,
    val recordCount: Int,
    val status: DailyIntakeStatus
) {
    val differenceCalories: Int? get() = targetCalories?.let { intakeCalories - it }

    val calendarGoalStatus: CalendarGoalStatus
        get() = when {
            recordCount == 0 -> CalendarGoalStatus.NO_DATA
            targetCalories == null -> CalendarGoalStatus.UNKNOWN_TARGET
            intakeCalories > targetCalories -> CalendarGoalStatus.EXCEEDED
            else -> CalendarGoalStatus.ACHIEVED
        }
}

class DailyIntakeTimeline private constructor(
    private val mealTotals: Map<LocalDate, MealTotal>,
    private val goals: List<DatedGoal>,
    private val profiles: List<DatedProfile>
) {
    fun summary(date: LocalDate): DailyIntakeSummary {
        val mealTotal = mealTotals[date] ?: MealTotal(0, 0)
        val goal = goals.lastOrNull { it.date <= date }?.calories
        val profile = profiles.lastOrNull { it.date <= date }?.profile
        val bmr = profile?.basalMetabolicRateKcal?.takeIf { it > 0 }
        val target = when {
            // Existing Dashboard/History use 2,000 kcal until a manual goal is saved.
            profile == null || profile.targetMode == TargetMode.MANUAL -> goal ?: 2000
            bmr == null -> null
            else -> runCatching {
                EnergyBalanceCalculator.resolveDailyTargetKcal(
                    goal ?: 2000,
                    bmr,
                    profile.palMultiplier,
                    profile.targetMode
                )
            }.getOrNull()?.takeIf { it > 0 }
        }
        val intake = mealTotal.calories
        val status = when {
            mealTotal.count == 0 -> DailyIntakeStatus.NO_RECORD
            target == null -> DailyIntakeStatus.NO_TARGET
            bmr != null && intake < bmr -> DailyIntakeStatus.BELOW_BMR
            intake > target -> DailyIntakeStatus.OVER
            intake.toDouble() >= target * 0.95 -> DailyIntakeStatus.BALANCED
            else -> DailyIntakeStatus.UNDER
        }
        return DailyIntakeSummary(date, intake, target, bmr, mealTotal.count, status)
    }

    fun recentDays(endDate: LocalDate, count: Int): List<DailyIntakeSummary> =
        (count.coerceIn(0, 366) - 1 downTo 0).map { summary(endDate.minusDays(it.toLong())) }

    /** An unrecorded day, a status change, or a day without a target ends the run. */
    fun streakEndingOn(date: LocalDate): Int {
        val status = summary(date).status
        if (status !in STREAK_STATUSES) return 0
        val earliestRecordedDate = mealTotals.keys.minOrNull() ?: return 0
        var cursor = date
        var length = 0
        while (cursor >= earliestRecordedDate && summary(cursor).status == status) {
            length += 1
            cursor = cursor.minusDays(1)
        }
        return length
    }

    companion object {
        val Empty = DailyIntakeTimeline(emptyMap(), emptyList(), emptyList())

        fun build(
            meals: List<MealRecord>,
            goals: List<CalorieGoal>,
            profiles: List<EnergyProfileHistory>
        ): DailyIntakeTimeline {
            val totals = meals.asSequence()
                .filter { it.calories >= 0 }
                .mapNotNull { meal -> parseDate(meal.date)?.let { it to meal.calories } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, calories) ->
                    MealTotal(
                        calories = calories.sumOf { it.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        count = calories.size
                    )
                }
            val datedGoals = goals.mapNotNull { goal ->
                parseDate(goal.startDate)?.let { date ->
                    goal.targetCalories.takeIf { it > 0 }?.let { DatedGoal(date, it, goal.id) }
                }
            }.sortedWith(compareBy<DatedGoal> { it.date }.thenBy { it.id })
            val datedProfiles = profiles.mapNotNull { profile ->
                parseDate(profile.effectiveFromDate)?.let { DatedProfile(it, profile) }
            }.sortedWith(compareBy<DatedProfile> { it.date }.thenBy { it.profile.updatedAt })
            return DailyIntakeTimeline(totals, datedGoals, datedProfiles)
        }

        private fun parseDate(value: String): LocalDate? =
            runCatching { LocalDate.parse(value) }.getOrNull()

        private val STREAK_STATUSES = setOf(
            DailyIntakeStatus.OVER,
            DailyIntakeStatus.BALANCED,
            DailyIntakeStatus.UNDER
        )
    }
}

private data class MealTotal(val calories: Int, val count: Int)
private data class DatedGoal(val date: LocalDate, val calories: Int, val id: Long)
private data class DatedProfile(val date: LocalDate, val profile: EnergyProfileHistory)
