package com.alshayeksa.afiati

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * لغة الواجهة: عربية (الافتراضية) أو إنجليزية. يختارها المستخدم من زر «ع/E» أو من الإعدادات وتُحفظ.
 * القيمة حالة Compose، فتغييرها يعيد رسم كل الشاشات فورًا ويقلب اتجاهها.
 * تُحمَّل في AfiatiApp قبل أي شاشة أو إشعار، فتعمل أيضًا في الإشعارات والتطبيق مغلق.
 */
object Lang {
    private const val PREFS = "afiati"
    private const val KEY = "language"

    var english by mutableStateOf(false)
        private set

    val code get() = if (english) "en" else "ar"
    val direction get() = if (english) LayoutDirection.Ltr else LayoutDirection.Rtl
    /** للتواريخ: الشهور وأيام الأسبوع بلغة الواجهة */
    val locale: Locale get() = if (english) Locale.ENGLISH else Locale.forLanguageTag("ar")

    fun load(context: Context) {
        english = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "ar") == "en"
    }

    fun set(context: Context, toEnglish: Boolean) {
        if (english == toEnglish) return
        english = toEnglish
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }
}

/** النص بلغة الواجهة الحالية */
fun tr(ar: String, en: String): String = if (Lang.english) en else ar
