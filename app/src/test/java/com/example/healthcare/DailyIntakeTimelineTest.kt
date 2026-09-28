package com.example.healthcare

import com.example.healthcare.data.entity.CalorieGoal
import com.example.healthcare.data.entity.EnergyProfileHistory
import com.example.healthcare.data.entity.MealRecord
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.TargetMode
import com.example.healthcare.domain.DailyIntakeStatus
import com.example.healthcare.domain.CalendarGoalStatus
import com.example.healthcare.domain.DailyIntakeTimeline
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyIntakeTimelineTest {
    private val day = LocalDate.of(2026, 9, 20)
    private val goal = CalorieGoal(targetCalories = 2000, startDate = "2026-09-01")

    @Test
    fun `classifies over balanced and under with over taking priority above target`() {
        fun status(calories: Int) = timeline(listOf(meal(day, calories))).summary(day).status
        assertEquals(DailyIntakeStatus.UNDER, status(1899))
        assertEquals(DailyIntakeStatus.BALANCED, status(1900))
        assertEquals(DailyIntakeStatus.BALANCED, status(2000))
        assertEquals(DailyIntakeStatus.OVER, status(2001))
    }

    @Test
    fun `calendar distinguishes achieved exceeded and no data`() {
        assertEquals(CalendarGoalStatus.NO_DATA, timeline(emptyList()).summary(day).calendarGoalStatus)
        assertEquals(CalendarGoalStatus.ACHIEVED, timeline(listOf(meal(day, 2000))).summary(day).calendarGoalStatus)
        assertEquals(CalendarGoalStatus.EXCEEDED, timeline(listOf(meal(day, 2001))).summary(day).calendarGoalStatus)
    }

    @Test
    fun `unrecorded day uses empty state and unsaved goal keeps existing default`() {
        assertEquals(DailyIntakeStatus.NO_RECORD, timeline(emptyList()).summary(day).status)
        val default = DailyIntakeTimeline.build(listOf(meal(day, 500)), emptyList(), emptyList()).summary(day)
        assertEquals(2000, default.targetCalories)
        assertEquals(DailyIntakeStatus.UNDER, default.status)
    }

    @Test
    fun `invalid energy profile shows missing target instead of crashing`() {
        val invalidProfile = profile(day, TargetMode.MAINTENANCE).copy(basalMetabolicRateKcal = 0)
        assertEquals(DailyIntakeStatus.NO_TARGET,
            DailyIntakeTimeline.build(listOf(meal(day, 500)), emptyList(),
                listOf(invalidProfile)).summary(day).status)
    }

    @Test
    fun `bmr warning takes priority and mode target follows history`() {
        val meals = listOf(meal(day, 1400))
        val profile = profile(day, TargetMode.MAINTENANCE)
        val summary = DailyIntakeTimeline.build(meals, listOf(goal), listOf(profile)).summary(day)
        assertEquals(2325, summary.targetCalories)
        assertEquals(1500, summary.bmrCalories)
        assertEquals(DailyIntakeStatus.BELOW_BMR, summary.status)
        val bmrSummary = DailyIntakeTimeline.build(meals, listOf(goal),
            listOf(profile.copy(targetMode = TargetMode.BMR))).summary(day)
        assertEquals(1500, bmrSummary.targetCalories)
    }

    @Test
    fun `historical goal changes do not rewrite earlier dates`() {
        val earlier = day.minusDays(2)
        val timeline = DailyIntakeTimeline.build(
            listOf(meal(earlier, 2000), meal(day, 2000)),
            listOf(goal, CalorieGoal(targetCalories = 2500, startDate = day.toString())),
            emptyList()
        )
        assertEquals(DailyIntakeStatus.BALANCED, timeline.summary(earlier).status)
        assertEquals(DailyIntakeStatus.UNDER, timeline.summary(day).status)
    }

    @Test
    fun `same day goal changes use the newest database id`() {
        val timeline = DailyIntakeTimeline.build(
            meals = listOf(meal(day, 1000)),
            goals = listOf(
                CalorieGoal(id = 1, targetCalories = 2000, startDate = day.toString()),
                CalorieGoal(id = 3, targetCalories = 2100, startDate = day.toString()),
                CalorieGoal(id = 2, targetCalories = 1800, startDate = day.toString())
            ),
            profiles = emptyList()
        )

        assertEquals(2100, timeline.summary(day).targetCalories)
    }

    @Test
    fun `multiple meals aggregate and edits or deletions refresh summaries`() {
        val original = listOf(meal(day, 800), meal(day, 1200))
        val first = timeline(original).summary(day)
        assertEquals(2000, first.intakeCalories)
        assertEquals(2, first.recordCount)
        assertEquals(DailyIntakeStatus.BALANCED, first.status)
        assertEquals(DailyIntakeStatus.UNDER,
            timeline(listOf(original.first().copy(calories = 700))).summary(day).status)
        assertEquals(DailyIntakeStatus.NO_RECORD, timeline(emptyList()).summary(day).status)
    }

    @Test
    fun `streak stops at gaps and excludes warning or missing goal`() {
        val timeline = timeline(listOf(
            meal(day.minusDays(3), 2100),
            meal(day.minusDays(1), 2100),
            meal(day, 2200)
        ))
        assertEquals(2, timeline.streakEndingOn(day))
        assertEquals(0, timeline.streakEndingOn(day.minusDays(2)))
        val withWarning = DailyIntakeTimeline.build(
            listOf(meal(day.minusDays(1), 1200), meal(day, 1200)),
            listOf(goal), listOf(profile(day.minusDays(1), TargetMode.MAINTENANCE))
        )
        assertEquals(0, withWarning.streakEndingOn(day))
    }

    @Test
    fun `recent days keep empty dates and malformed records do not contribute`() {
        val timeline = timeline(listOf(meal(day, 600), meal(day, 300).copy(date = "bad-date")))
        val days = timeline.recentDays(day, 7)
        assertEquals(7, days.size)
        assertEquals(DailyIntakeStatus.NO_RECORD, days.first().status)
        assertEquals(600, days.last().intakeCalories)
    }

    private fun timeline(meals: List<MealRecord>) =
        DailyIntakeTimeline.build(meals, listOf(goal), emptyList())

    private fun meal(date: LocalDate, calories: Int) = MealRecord(
        date = date.toString(), time = "12:00", mealType = MealType.LUNCH,
        foodName = "테스트 식사", calories = calories
    )

    private fun profile(date: LocalDate, mode: TargetMode) = EnergyProfileHistory(
        basalMetabolicRateKcal = 1500, activityLevelCode = ActivityLevel.LIGHT,
        palMultiplier = 1.55, targetMode = mode, effectiveFromDate = date.toString()
    )
}
