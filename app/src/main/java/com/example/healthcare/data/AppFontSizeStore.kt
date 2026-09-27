package com.example.healthcare.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppFontSize(
    val label: String,
    val scale: Float
) {
    SMALL("작게", 0.90f),
    NORMAL("보통", 1.00f),
    LARGE("크게", 1.15f),
    EXTRA_LARGE("아주 크게", 1.30f);

    companion object {
        fun fromStoredValue(value: String?): AppFontSize =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

/** Room 데이터와 독립된 화면 표시 설정입니다. */
class AppFontSizeStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val _fontSize = MutableStateFlow(
        AppFontSize.fromStoredValue(preferences.getString(KEY_FONT_SIZE, null))
    )
    val fontSize: StateFlow<AppFontSize> = _fontSize.asStateFlow()

    fun set(value: AppFontSize) {
        if (_fontSize.value == value) return
        preferences.edit(commit = true) { putString(KEY_FONT_SIZE, value.name) }
        _fontSize.value = value
    }

    companion object {
        const val PREFERENCES_NAME = "app_display_preferences"
        private const val KEY_FONT_SIZE = "font_size"
    }
}
