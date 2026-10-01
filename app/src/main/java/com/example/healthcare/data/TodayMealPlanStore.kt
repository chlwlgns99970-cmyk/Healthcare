package com.example.healthcare.data

import android.content.Context
import com.example.healthcare.data.model.MealType
import com.example.healthcare.domain.DailyRecommendationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StoredDailyMeal(val mealType: MealType, val templateId: String, val portion: Double = 1.0, val kcal: Int = 0)
data class StoredTodayPlan(val date: String, val theme: DailyRecommendationTheme, val target: Int,
    val meals: List<StoredDailyMeal>, val signatures: List<String> = emptyList())

interface TodayMealPlanPersistence {
    val state: StateFlow<StoredTodayPlan?>
    fun save(plan: StoredTodayPlan)
}

class InMemoryTodayMealPlanStore(initial: StoredTodayPlan? = null) : TodayMealPlanPersistence {
    private val value = MutableStateFlow(initial)
    override val state = value.asStateFlow()
    override fun save(plan: StoredTodayPlan) { value.value = plan }
}

/** A versioned, bounded snapshot in the existing preference infrastructure. No Room schema change. */
class TodayMealPlanStore(context: Context) : TodayMealPlanPersistence {
    private val preferences = context.getSharedPreferences("recommendation_cycle_v2", Context.MODE_PRIVATE)
    private val value = MutableStateFlow(DailyPlanSnapshotCodec.decode(preferences.getString(KEY, null)))
    override val state = value.asStateFlow()
    override fun save(plan: StoredTodayPlan) {
        check(preferences.edit().putString(KEY, DailyPlanSnapshotCodec.encode(plan)).commit())
        value.value = plan
    }
    private companion object { const val KEY = "today_four_meal_plan_v1" }
}

object DailyPlanSnapshotCodec {
    fun encode(plan: StoredTodayPlan): String = listOf("1", plan.date, plan.theme.name, plan.target.toString(),
        plan.meals.joinToString(";") { "${it.mealType.name},${it.templateId},${it.portion},${it.kcal}" },
        plan.signatures.takeLast(24).joinToString("\n")).joinToString("\t")
    fun decode(raw: String?): StoredTodayPlan? = runCatching {
        val parts = requireNotNull(raw).split('\t')
        require(parts.size == 6 && parts[0] == "1")
        java.time.LocalDate.parse(parts[1])
        val target = parts[3].toInt().also { require(it > 0) }
        val meals = parts[4].split(';').filter(String::isNotBlank).map {
            val fields = it.split(',')
            require(fields.size == 4 && fields[1].isNotBlank())
            StoredDailyMeal(MealType.valueOf(fields[0]), fields[1], fields[2].toDouble().also { portion -> require(portion == 1.0) }, fields[3].toInt().also { require(it > 0) })
        }
        require(meals.distinctBy { it.mealType }.size == meals.size && meals.distinctBy { it.templateId }.size == meals.size)
        StoredTodayPlan(parts[1], DailyRecommendationTheme.valueOf(parts[2]), target, meals,
            parts[5].split('\n').filter(String::isNotBlank).takeLast(24))
    }.getOrNull()
}
