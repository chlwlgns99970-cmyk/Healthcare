package com.example.healthcare.data.database

import androidx.room.TypeConverter
import com.example.healthcare.data.model.ActivityLevel
import com.example.healthcare.data.model.MealType
import com.example.healthcare.data.model.RecordSource
import com.example.healthcare.data.model.TargetMode

/**
 * Room 데이터베이스를 위한 타입 변환기
 */
class Converters {
    @TypeConverter
    fun fromMealType(value: MealType): String {
        return value.name
    }

    @TypeConverter
    fun toMealType(value: String): MealType {
        return MealType.valueOf(value)
    }

    @TypeConverter
    fun fromRecordSource(value: RecordSource): String = value.name

    @TypeConverter
    fun toRecordSource(value: String): RecordSource = RecordSource.valueOf(value)

    @TypeConverter
    fun fromActivityLevel(value: ActivityLevel): String = value.name

    @TypeConverter
    fun toActivityLevel(value: String): ActivityLevel = ActivityLevel.valueOf(value)

    @TypeConverter
    fun fromTargetMode(value: TargetMode): String = value.name

    @TypeConverter
    fun toTargetMode(value: String): TargetMode = TargetMode.valueOf(value)
}
