package com.example.healthcare.domain

import com.example.healthcare.data.entity.FoodItem
import com.example.healthcare.data.entity.MealTemplateIngredient
import com.example.healthcare.data.model.MealType
import kotlin.math.abs
import kotlin.math.roundToInt

data class RecommendationServing(
    val portion: Double,
    val kcal: Int,
    val nutrition: Macronutrients,
    val labels: List<String> = emptyList()
)

/** Recommendation choices, not claimed serving conversions. Conversions come only from FoodAmountPolicy. */
object RecommendationServingPolicy {
    const val MAX_SNACK_KCAL = 350

    fun label(food: FoodItem, basisAmount: Double): String {
        val choice = FoodAmountPolicy.defaultChoice(food) ?: return "양 확인 필요"
        val quantity = basisAmount / choice.basisAmountPerUnit
        // Round presentation only; nutrients and the persisted snapshot retain the precise amount.
        val displayed = kotlin.math.round(quantity * 100.0) / 100.0
        return "${RecordedAmountSnapshot.format(displayed)}${choice.unit}"
    }

    fun amounts(food: FoodItem, baseAmount: Double, meal: MealType): List<Double> {
        val choice = FoodAmountPolicy.defaultChoice(food) ?: return emptyList()
        if (!baseAmount.isFinite() || baseAmount <= 0) return emptyList()
        val snack = meal == MealType.SNACK
        // An exact official one-meal reference is a measured choice, not a
        // personal health maximum or a bowl conversion. It never expands to fill calories.
        val metadata=FoodMetadataPolicy.lookup(food.id)
        val reviewedAmount=metadata?.let { evidence -> evidence.recommendationReferenceAmount?.takeIf {
            choice.unit in setOf("g","ml") && it.isFinite() && it > 0 && it <= FoodAmountPolicy.MAX_BASIS_AMOUNT &&
                evidence.recommendationReferenceUnit == choice.basisUnit &&
                evidence.recommendationSourceReference.startsWith("https://")
        } }
        if (reviewedAmount != null) {
            val nutrition=FoodAmountPolicy.calculate(food,reviewedAmount,choice.unit) ?: return emptyList()
            return listOf(reviewedAmount).filter { nutrition.calories > 0 && (!snack || nutrition.calories <= MAX_SNACK_KCAL) }
        }
        val quantities = when {
            food.sourceType == "USDA-SR-LEGACY" && food.sourceFoodCode == "FDC-173424" -> listOf(1.0, 2.0, 3.0)
            choice.unit == "줄" -> if (snack) listOf(0.5) else listOf(0.5, 1.0, 1.5)
            choice.unit in setOf("봉", "제품 전체") || PortionGuide.verifiedPackage(food) != null -> listOf(1.0)
            choice.unit in setOf("병", "캔", "팩", "컵", "잔") && choice.quality == PortionQuality.OFFICIAL_SERVING -> listOf(1.0)
            choice.unit == "공기" -> if (snack) listOf(0.5) else listOf(0.5, 1.0, 1.5)
            choice.unit == "조각" -> if (snack) listOf(1.0, 2.0) else listOf(1.0, 2.0, 3.0)
            choice.unit == "장" -> listOf(1.0, 1.5)
            choice.unit == "개" && choice.quality == PortionQuality.OFFICIAL_SERVING -> listOf(1.0)
            choice.unit == "개" -> if (snack) listOf(0.5, 1.0) else listOf(1.0, 2.0)
            choice.unit == "인분" -> if (snack) listOf(0.5) else listOf(0.5, 1.0)
            choice.unit in setOf("g", "ml") -> measuredChoices(food.category.orEmpty(), baseAmount, snack)
            else -> listOf(1.0)
        }
        return quantities.map { it * choice.basisAmountPerUnit }.distinct().filter { amount ->
            val nutrition = FoodAmountPolicy.calculate(food, amount / choice.basisAmountPerUnit, choice.unit)
            nutrition != null && nutrition.calories > 0 && (!snack || nutrition.calories <= MAX_SNACK_KCAL)
        }
    }

    private fun measuredChoices(category: String, base: Double, snack: Boolean): List<Double> {
        // Measured amounts remain g/ml. These bounded meal choices never invent a bowl/roll/package weight.
        val candidates = when {
            category.contains("음료") || category.contains("차류") -> listOf(150.0, 250.0, 350.0)
            category.contains("유제품") || category.contains("빙과") -> listOf(100.0, 150.0, 200.0)
            category.contains("빵") || category.contains("과자") -> if (snack) listOf(30.0, 60.0, 90.0) else listOf(60.0, 100.0, 150.0)
            snack -> listOf(50.0, 100.0, 150.0)
            category.contains("죽") || category.contains("스프") -> listOf(250.0, 350.0, 450.0)
            category.contains("국") || category.contains("탕") -> listOf(300.0, 450.0, 600.0)
            category.contains("찌개") || category.contains("전골") -> listOf(250.0, 400.0, 550.0)
            category.contains("밥") -> listOf(200.0, 300.0, 400.0)
            category.contains("면") || category.contains("만두") -> listOf(250.0, 350.0, 450.0)
            category.contains("구이") || category.contains("조림") || category.contains("찜") ||
                category.contains("볶음") || category.contains("부침") || category.contains("전·적") ||
                category.contains("튀김") || category.contains("무침") -> listOf(100.0, 150.0, 200.0)
            else -> listOf(base) // Unclassified foods retain their supplied measured amount, without expansion.
        }
        return (candidates + base.takeIf { it in candidates.first()..candidates.last() }).filterNotNull().distinct().sorted()
    }

    fun options(ingredients: List<MealTemplateIngredient>, foods: List<FoodItem?>, meal: MealType): List<RecommendationServing> {
        if (ingredients.isEmpty() || ingredients.size != foods.size || foods.any { it == null }) return emptyList()
        val primary = ingredients.first()
        val primaryFood = requireNotNull(foods.first())
        val ratios = amounts(primaryFood, primary.amount, meal).map { it / primary.amount }
        return ratios.mapNotNull { ratio ->
            val results = ingredients.zip(foods).map { (ingredient, nullableFood) ->
                val food = requireNotNull(nullableFood)
                val choice = FoodAmountPolicy.defaultChoice(food) ?: return@mapNotNull null
                val amount = ingredient.amount * ratio
                if (!ingredient.unit.equals(food.unit, true) || amounts(food, ingredient.amount, meal)
                        .none { abs(it - amount) < 0.00001 }) return@mapNotNull null
                FoodAmountPolicy.calculate(food, amount / choice.basisAmountPerUnit, choice.unit) ?: return@mapNotNull null
            }
            val kcal = results.sumOf { it.calories }
            if (meal == MealType.SNACK && kcal > MAX_SNACK_KCAL) return@mapNotNull null
            RecommendationServing(ratio, kcal, Macronutrients.knownSum(results.map {
                Macronutrients(it.carbohydrateGrams, it.proteinGrams, it.fatGrams)
            }), ingredients.zip(foods).map { (ingredient, food) ->
                "${FoodSearchPolicy.displayName(requireNotNull(food))} · ${label(food, ingredient.amount * ratio)}"
            })
        }.distinctBy { it.portion }.sortedBy { it.kcal }
    }

    fun available(seed: RecommendationSeed, meal: MealType): List<RecommendationServing> =
        seed.servingsByMeal[meal] ?: listOf(RecommendationServing(1.0, seed.template.totalKcal, DailyMealThemePolicy.nutrition(seed)))

    fun selected(seed: RecommendationSeed, meal: MealType, portion: Double): RecommendationServing? =
        available(seed, meal).firstOrNull { abs(it.portion - portion) < 0.000001 }
}
