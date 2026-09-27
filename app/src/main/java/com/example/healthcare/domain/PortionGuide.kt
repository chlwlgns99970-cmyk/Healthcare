package com.example.healthcare.domain

import com.example.healthcare.data.entity.FoodItem
import kotlin.math.roundToInt

/** Household choices are estimates, never a claim that the user's food was weighed. */
enum class PortionEstimationType { PACKAGE_LABEL, OFFICIAL_SERVING, HOUSEHOLD_UNIT, VISUAL_ESTIMATE, MANUAL_AMOUNT }

enum class PortionVessel(val label: String) {
    SMALL_DISH("작은 종지"), RICE_BOWL("밥그릇"), SOUP_BOWL("국그릇"),
    PLATE("일반 접시"), LARGE_PLATE("큰 접시"), CUP("컵"), PACKAGE("봉지 또는 포장"), UNKNOWN("잘 모르겠어요")
}

enum class PortionFraction(val label: String, val ratio: Double) {
    TASTE("조금 맛봤어요", 0.1), QUARTER("1/4 정도", 0.25), HALF("절반 정도", 0.5),
    THREE_QUARTERS("3/4 정도", 0.75), ALMOST_ALL("거의 다 먹었어요", 0.9), ALL("전부 먹었어요", 1.0)
}

data class PortionPreset(
    val id: String,
    val label: String,
    val amount: Double,
    val unit: String,
    val estimationType: PortionEstimationType,
    val sourceReference: String,
    val description: String
)

data class PortionEstimate(val amount: Double, val unit: String, val calories: Int)
data class VerifiedPackageUnit(val amount: Double, val unit: String, val packageUnit: String)

object PortionGuide {
    // 식품안전나라 식품구성탑: 밥 1공기 210g.
    const val RICE_BOWL_GRAMS = 210.0
    const val RICE_SOURCE = "식품안전나라 식품구성탑 · 밥 1공기 210g · https://www.foodsafetykorea.go.kr/portal/board/boardDetail.do?bbs_no=bbs039&menu_grp=MENU_NEW03&menu_no=4847&ntctxt_no=22282"
    // 식품안전나라 성인 영양지수 교육자료: 종이컵 180mL 기준.
    const val PAPER_CUP_ML = 180.0
    const val CUP_SOURCE = "식품안전나라 성인 영양지수 교육자료 · 종이컵 180mL · https://various.foodsafetykorea.go.kr/nq/ebook/adult/adult_balance.pdf"
    const val FISH_PALM_GRAMS = 110.0
    const val FISH_SOURCE = "식품안전나라 성인 영양지수 교육자료 · 생선 손바닥 크기 약 110g · https://various.foodsafetykorea.go.kr/nq/data/adult_guidebook.pdf"
    const val BREAD_SLICE_GRAMS = 100.0 / 3.0
    const val BREAD_SOURCE = "식품안전나라 식품구성탑 · 식빵 3쪽 100g · https://www.foodsafetykorea.go.kr/portal/board/boardDetail.do?bbs_no=bbs039&menu_grp=MENU_NEW03&menu_no=4847&ntctxt_no=22282"
    const val HALF_APPLE_GRAMS = 100.0
    const val APPLE_SOURCE = "식품안전나라 성인 영양지수 교육자료 · 사과 반 개 100g · https://various.foodsafetykorea.go.kr/nq/ebook/adult/adult_balance.pdf"
    const val JJOLMYEON_SERVING_GRAMS = 450.0
    const val JJOLMYEON_SOURCE = "식품안전나라 외식 영양성분 자료집 · 쫄면 1인분 450g · https://www.foodsafetykorea.go.kr/upload/20150824/20150824011539_1440389739434.pdf"
    private val officialTotalPattern = Regex("공식 총내용량\\s+([0-9]+(?:\\.[0-9]+)?)(g|ml)", RegexOption.IGNORE_CASE)
    private val packageUnitPattern = Regex("포장단위\\s+(봉|캔|병|팩|개|조각)")

    fun presets(food: FoodItem): List<PortionPreset> {
        if (!validFood(food)) return emptyList()
        val unit = food.unit.trim()
        verifiedPackage(food)?.let { packageInfo ->
            val source = "식품영양성분 데이터베이스 · ${food.sourceFoodCode} · ${food.servingDescription}"
            val ratios = if (packageInfo.packageUnit == "조각") {
                listOf(1.0, 2.0, 3.0, 4.0)
            } else {
                listOf(0.5, 1.0, 1.5, 2.0)
            }
            return ratios.map { ratio ->
                val label = packageLabel(ratio, packageInfo.packageUnit)
                val wholeLabel = if (packageInfo.packageUnit == "제품") "제품 전체" else "1${packageInfo.packageUnit}"
                preset(
                    id = "package-${compactRatioId(ratio)}",
                    label = label,
                    amount = packageInfo.amount * ratio,
                    unit = packageInfo.unit,
                    type = PortionEstimationType.PACKAGE_LABEL,
                    source = source,
                    description = "공식 총내용량 ${formatAmount(packageInfo.amount)}${packageInfo.unit}을 $wholeLabel 기준으로 적용한 비례 추정값이에요."
                )
            }
        }
        return when {
            isPlainRice(food) -> listOf(
                preset("rice-quarter", "1/4공기", RICE_BOWL_GRAMS * 0.25, unit, PortionEstimationType.HOUSEHOLD_UNIT, RICE_SOURCE),
                preset("rice-half", "반 공기", RICE_BOWL_GRAMS * 0.5, unit, PortionEstimationType.HOUSEHOLD_UNIT, RICE_SOURCE),
                preset("rice-three-quarters", "3/4공기", RICE_BOWL_GRAMS * 0.75, unit, PortionEstimationType.HOUSEHOLD_UNIT, RICE_SOURCE),
                preset("rice-one", "한 공기", RICE_BOWL_GRAMS, unit, PortionEstimationType.HOUSEHOLD_UNIT, RICE_SOURCE),
                preset("rice-one-half", "한 공기 반", RICE_BOWL_GRAMS * 1.5, unit, PortionEstimationType.HOUSEHOLD_UNIT, RICE_SOURCE),
                preset("rice-two", "두 공기", RICE_BOWL_GRAMS * 2.0, unit, PortionEstimationType.HOUSEHOLD_UNIT, RICE_SOURCE)
            )
            isDrink(food) -> listOf(
                preset("cup-half", "종이컵 반 컵", PAPER_CUP_ML * 0.5, unit, PortionEstimationType.HOUSEHOLD_UNIT, CUP_SOURCE),
                preset("cup-one", "종이컵 한 컵", PAPER_CUP_ML, unit, PortionEstimationType.HOUSEHOLD_UNIT, CUP_SOURCE),
                preset("cup-two", "종이컵 두 컵", PAPER_CUP_ML * 2.0, unit, PortionEstimationType.HOUSEHOLD_UNIT, CUP_SOURCE)
            )
            isFish(food) -> listOf(
                preset("fish-half-palm", "손바닥 반 장", FISH_PALM_GRAMS * 0.5, unit,
                    PortionEstimationType.HOUSEHOLD_UNIT, FISH_SOURCE),
                preset("fish-palm", "손바닥 크기 1장", FISH_PALM_GRAMS, unit,
                    PortionEstimationType.HOUSEHOLD_UNIT, FISH_SOURCE),
                preset("fish-one-half-palm", "손바닥 크기 1장 반", FISH_PALM_GRAMS * 1.5, unit,
                    PortionEstimationType.HOUSEHOLD_UNIT, FISH_SOURCE)
            )
            isSlicedBread(food) -> listOf(
                preset("bread-one", "식빵 한 조각", BREAD_SLICE_GRAMS, unit, PortionEstimationType.HOUSEHOLD_UNIT, BREAD_SOURCE),
                preset("bread-two", "식빵 두 조각", BREAD_SLICE_GRAMS * 2.0, unit, PortionEstimationType.HOUSEHOLD_UNIT, BREAD_SOURCE),
                preset("bread-three", "식빵 세 조각", 100.0, unit, PortionEstimationType.HOUSEHOLD_UNIT, BREAD_SOURCE)
            )
            isApple(food) -> listOf(
                preset("apple-half", "사과 반 개", HALF_APPLE_GRAMS, unit, PortionEstimationType.HOUSEHOLD_UNIT, APPLE_SOURCE),
                preset("apple-one", "사과 한 개", HALF_APPLE_GRAMS * 2.0, unit, PortionEstimationType.HOUSEHOLD_UNIT, APPLE_SOURCE)
            )
            isJjolmyeon(food) -> listOf(
                preset("jjol-half", "쫄면 반 그릇", JJOLMYEON_SERVING_GRAMS * 0.5, unit,
                    PortionEstimationType.OFFICIAL_SERVING, JJOLMYEON_SOURCE),
                preset("jjol-one", "쫄면 한 그릇", JJOLMYEON_SERVING_GRAMS, unit,
                    PortionEstimationType.OFFICIAL_SERVING, JJOLMYEON_SOURCE),
                preset("jjol-one-half", "쫄면 한 그릇 반", JJOLMYEON_SERVING_GRAMS * 1.5, unit,
                    PortionEstimationType.OFFICIAL_SERVING, JJOLMYEON_SOURCE)
            )
            unit in setOf("개", "조각", "봉", "봉지", "캔", "병", "인분") && food.referenceAmount == 1.0 -> listOf(
                preset("count-half", "반 $unit", 0.5, unit, PortionEstimationType.OFFICIAL_SERVING, food.servingDescription),
                preset("count-one", "한 $unit", 1.0, unit, PortionEstimationType.OFFICIAL_SERVING, food.servingDescription),
                preset("count-one-half", "한 $unit 반", 1.5, unit, PortionEstimationType.OFFICIAL_SERVING, food.servingDescription),
                preset("count-two", "두 $unit", 2.0, unit, PortionEstimationType.OFFICIAL_SERVING, food.servingDescription)
            )
            else -> emptyList()
        }
    }

    /** True when no verified package, official serving, or documented household conversion exists. */
    fun requiresDirectAmount(food: FoodItem): Boolean = validFood(food) && presets(food).isEmpty()

    fun defaultPreset(food: FoodItem): PortionPreset? {
        val choices = presets(food)
        return choices.firstOrNull { it.id in setOf("rice-one", "cup-one", "fish-palm", "count-one", "package-1", "relative-regular") }
            ?: choices.minByOrNull { kotlin.math.abs(it.amount - food.referenceAmount) }
    }

    fun visualEstimate(food: FoodItem, vessel: PortionVessel, fraction: PortionFraction): PortionPreset? {
        if (!validFood(food)) return null
        val hasDocumentedVessel =
            (isPlainRice(food) && vessel == PortionVessel.RICE_BOWL) ||
                (isDrink(food) && vessel == PortionVessel.CUP) ||
                (isFish(food) && vessel == PortionVessel.PLATE)
        if (!hasDocumentedVessel) return null
        val base = when {
            isPlainRice(food) && vessel == PortionVessel.RICE_BOWL -> RICE_BOWL_GRAMS
            isDrink(food) && vessel == PortionVessel.CUP -> PAPER_CUP_ML
            isFish(food) && vessel == PortionVessel.PLATE -> FISH_PALM_GRAMS
            else -> return null
        }
        val source = when {
            isPlainRice(food) && vessel == PortionVessel.RICE_BOWL -> RICE_SOURCE
            isDrink(food) && vessel == PortionVessel.CUP -> CUP_SOURCE
            isFish(food) && vessel == PortionVessel.PLATE -> FISH_SOURCE
            else -> return null
        }
        return preset(
            "visual-${vessel.name}-${fraction.name}",
            "${vessel.label} · ${fraction.label}",
            base * fraction.ratio,
            food.unit,
            PortionEstimationType.VISUAL_ESTIMATE,
            source,
            "실제 그릇 크기와 담긴 양은 측정하지 않았어요. 매우 대략적인 값이에요."
        )
    }

    fun estimate(food: FoodItem, preset: PortionPreset): PortionEstimate? {
        if (!validFood(food) || !preset.amount.isFinite() || preset.amount <= 0.0 ||
            !preset.unit.equals(food.unit, ignoreCase = true)
        ) return null
        // 공식 포장 단위는 화면에 먼저 표시한 1포장 kcal를 기준으로 배수 계산합니다.
        // 원본 정밀도는 그대로 보존하면서 1봉 500 kcal, 2봉 1,000 kcal처럼 사용자에게
        // 모순되어 보이는 2차 반올림(예: 1,001 kcal)을 피합니다.
        val calories = if (preset.estimationType == PortionEstimationType.PACKAGE_LABEL) {
            verifiedPackage(food)?.let { packageInfo ->
                val onePackageCalories =
                    (food.energyKcal * packageInfo.amount / food.referenceAmount).roundToInt()
                onePackageCalories * (preset.amount / packageInfo.amount)
            } ?: (food.energyKcal * preset.amount / food.referenceAmount)
        } else {
            food.energyKcal * preset.amount / food.referenceAmount
        }
        if (!calories.isFinite() || calories <= 0.0 || calories > Int.MAX_VALUE.toDouble()) return null
        return PortionEstimate(preset.amount, food.unit, calories.roundToInt().coerceAtLeast(1))
    }

    fun recommendationLabel(food: FoodItem, amount: Double): String {
        if (!validFood(food) || !amount.isFinite() || amount <= 0.0) return "양 확인 필요"
        return when {
            verifiedPackage(food)?.let { kotlin.math.abs(amount - it.amount) < 0.02 } == true ->
                verifiedPackage(food)?.let { "약 1${it.packageUnit}" }.orEmpty()
            isPlainRice(food) -> "밥 약 ${formatRatio(amount / RICE_BOWL_GRAMS)}공기"
            isDrink(food) -> "종이컵 약 ${formatRatio(amount / PAPER_CUP_ML)}컵"
            isFish(food) -> "생선 손바닥 약 ${formatRatio(amount / FISH_PALM_GRAMS)}장"
            isSlicedBread(food) -> "식빵 약 ${formatRatio(amount / BREAD_SLICE_GRAMS)}조각"
            isApple(food) -> "사과 약 ${formatRatio(amount / (HALF_APPLE_GRAMS * 2.0))}개"
            isJjolmyeon(food) -> "쫄면 약 ${formatRatio(amount / JJOLMYEON_SERVING_GRAMS)}그릇"
            else -> "추천 식단에 담긴 양"
        }
    }

    fun verifiedPackage(food: FoodItem): VerifiedPackageUnit? {
        if (!food.sourceType.equals("K-FIND-PRODUCT", ignoreCase = true)) return null
        val totalMatch = officialTotalPattern.find(food.servingDescription) ?: return null
        val packageMatch = packageUnitPattern.find(food.servingDescription)
        val amount = totalMatch.groupValues[1].toDoubleOrNull() ?: return null
        val unit = totalMatch.groupValues[2].lowercase()
        if (!amount.isFinite() || amount <= 0.0 || !unit.equals(food.unit.trim(), ignoreCase = true)) return null
        return VerifiedPackageUnit(amount, unit, packageMatch?.groupValues?.get(1) ?: "제품")
    }

    fun resultServingSummary(food: FoodItem): String {
        val packageInfo = verifiedPackage(food) ?: return "영양정보 ${formatAmount(food.referenceAmount)}${food.unit} 기준 · 약 ${food.energyKcal.roundToInt()} kcal"
        val calories = food.energyKcal * packageInfo.amount / food.referenceAmount
        val label = if (packageInfo.packageUnit == "제품") "제품 전체" else "1${packageInfo.packageUnit}"
        return "$label ${formatAmount(packageInfo.amount)}${packageInfo.unit} · ${calories.roundToInt()} kcal"
    }

    fun presetSupportingText(food: FoodItem, preset: PortionPreset): String? = estimate(food, preset)?.let { estimate ->
        if (preset.estimationType == PortionEstimationType.PACKAGE_LABEL) {
            "${formatAmount(estimate.amount)}${estimate.unit} · ${estimate.calories} kcal"
        } else {
            "선택 시 ${estimate.calories} kcal"
        }
    }

    private fun preset(id: String, label: String, amount: Double, unit: String, type: PortionEstimationType, source: String,
                       description: String = "그릇·컵 크기와 실제 양에 따라 달라질 수 있어요.") =
        PortionPreset(id, label, amount, unit, type, source, description)

    private fun isPlainRice(food: FoodItem) = food.category == "밥류" && food.unit == "g" &&
        food.name.contains("밥") && listOf("김밥", "볶음밥", "비빔밥", "덮밥", "주먹밥").none(food.name::contains)

    private fun isDrink(food: FoodItem) = food.unit == "ml" &&
        (food.category?.contains("음료") == true || food.category?.contains("차류") == true)

    private fun isFish(food: FoodItem) = food.unit == "g" &&
        food.category in setOf("구이류", "찜류", "조림류", "수·조·어·육류") &&
        listOf("고등어", "삼치", "갈치", "연어", "참치", "생선").any(food.name::contains)

    private fun isSlicedBread(food: FoodItem) = food.unit == "g" && food.name.contains("식빵") &&
        !food.name.contains("샌드위치")

    private fun isApple(food: FoodItem) = food.unit == "g" &&
        (food.name == "사과" || food.name.startsWith("사과,"))

    private fun isJjolmyeon(food: FoodItem) = food.unit == "g" && food.name == "쫄면" &&
        food.category?.contains("면") == true

    private fun validFood(food: FoodItem) = food.referenceAmount.isFinite() && food.referenceAmount > 0.0 &&
        food.energyKcal.isFinite() && food.energyKcal > 0.0 && food.unit.isNotBlank()

    private fun formatRatio(value: Double): String = when {
        kotlin.math.abs(value - 0.5) < 0.02 -> "반 "
        kotlin.math.abs(value - 0.75) < 0.02 -> "3/4"
        kotlin.math.abs(value - 1.0) < 0.02 -> "1"
        else -> String.format(java.util.Locale.KOREA, "%.1f", value)
    }

    private fun packageLabel(ratio: Double, unit: String): String = when (ratio) {
        0.5 -> if (unit == "제품") "제품의 절반" else "반 $unit"
        1.0 -> if (unit == "제품") "제품 전체" else "1$unit"
        1.5 -> if (unit == "제품") "제품 1.5개" else "1.5$unit"
        2.0 -> if (unit == "제품") "제품 2개" else "2$unit"
        3.0 -> "3$unit"
        4.0 -> "4$unit"
        else -> "${formatAmount(ratio)}$unit"
    }

    private fun compactRatioId(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString()
    else value.toString().replace('.', '-')

    private fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(java.util.Locale.KOREA, "%.1f", value).trimEnd('0').trimEnd('.')
}
