package com.example.healthcare.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.RecordSource

/**
 * 식사 기록 엔티티
 */
@Entity(
    tableName = "meal_records",
    indices = [
        Index(value = ["photoRequestId", "photoItemId"], unique = true),
        Index(value = ["plannedMealId"], unique = true),
        Index(value = ["foodItemId"]),
        Index(value = ["barcode"])
    ]
)
data class MealRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,          // 날짜 (YYYY-MM-DD)
    val time: String,          // 시각 (HH:mm)
    val mealType: MealType,    // 식사 유형 (Enum)
    val foodName: String,      // 음식명
    val calories: Int,         // 칼로리
    /** 저장 당시 확정한 영양정보입니다. 원본에 없는 값과 복원 불가능한 과거 기록은 null입니다. */
    val carbohydrateGrams: Double? = null,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
    val memo: String? = null,  // 메모 (선택 사항)
    val servingAmount: Double? = null,
    val servingUnit: String? = null,
    /** 생활 단위는 표시용 스냅샷입니다. 계산에 사용한 양은 servingAmount/servingUnit에 남깁니다. */
    val portionPresetId: String? = null,
    val portionDisplayLabel: String? = null,
    val portionEstimationType: String? = null,
    val portionSourceReference: String? = null,
    @ColumnInfo(defaultValue = "'MANUAL'")
    val source: RecordSource = RecordSource.MANUAL,
    val photoAnalysisId: String? = null,
    val photoRequestId: String? = null,
    val photoItemId: String? = null,
    val aiFoodName: String? = null,
    val aiEstimatedCalories: Int? = null,
    val aiMinimumCalories: Int? = null,
    val aiMaximumCalories: Int? = null,
    val aiConfidence: Double? = null,
    val analysisModelVersion: String? = null,
    val analysisRequestedAt: String? = null,
    /** 추천 계획을 실제 섭취로 확정한 경우에만 채웁니다. */
    val plannedMealId: Long? = null,
    /** 검증된 영양 데이터 항목과 연결된 경우에만 채웁니다. */
    val foodItemId: String? = null,
    val barcode: String? = null,
    @ColumnInfo(defaultValue = "0")
    val wasAiResultEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)
