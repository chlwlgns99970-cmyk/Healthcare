package com.example.healthcare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Explicit QA setup utility. Copies only the requested public food name; never writes app data. */
class ManualAmountQaClipboardTest {
    @Test fun copyKimbapQueryForManualQa() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa", context.packageName)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("QA 음식 검색", "김밥"))
    }
    @Test fun copyVerifiedKimbapQueryForManualQa() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.healthcare.qa", context.packageName)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("QA 음식 검색", "백종원한줄김밥"))
    }
}
