package com.example.healthcare.data.appupdate

import android.content.Context
import androidx.core.content.edit

class AppUpdatePreferences(context: Context) : AppUpdatePreferenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    override val lastSuccessfulCheckAt: Long
        get() = preferences.getLong(KEY_LAST_SUCCESSFUL_CHECK, 0L)

    override val lastPromptedVersionCode: Int
        get() = preferences.getInt(KEY_LAST_PROMPTED_VERSION, 0)

    override fun recordSuccessfulCheck(atMillis: Long) {
        preferences.edit { putLong(KEY_LAST_SUCCESSFUL_CHECK, atMillis) }
    }

    override fun recordPromptedVersion(versionCode: Int) {
        preferences.edit { putInt(KEY_LAST_PROMPTED_VERSION, versionCode) }
    }

    private companion object {
        const val PREFERENCE_NAME = "app_update_preferences"
        const val KEY_LAST_SUCCESSFUL_CHECK = "lastSuccessfulCheckAt"
        const val KEY_LAST_PROMPTED_VERSION = "lastPromptedVersionCode"
    }
}
