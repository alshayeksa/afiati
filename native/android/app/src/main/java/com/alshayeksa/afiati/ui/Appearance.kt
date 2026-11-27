package com.alshayeksa.afiati.ui

import com.alshayeksa.afiati.tr
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/** لون التطبيق. الأزرق المحيطي يطابق شعار عافيتي، والبقية تُشتق من الألوان الأصلية بتدوير درجة اللون. */
enum class ThemeAccent(val key: String, private val ar: String, private val en: String, val hue: Double?, val sat: Double = 1.0, val lift: Double = 0.0) {
    OCEAN("ocean", "الأزرق المحيطي", "Ocean blue", 200.0),
    EMERALD("emerald", "الزمرّدي", "Emerald", null),
    GOLD("gold", "الذهبي الملكي", "Royal gold", 43.0, 0.95),
    VIOLET("violet", "البنفسجي الليلي", "Midnight violet", 268.0, 0.9),
    CORAL("coral", "المرجاني", "Coral", 352.0, 0.95),
    PLATINUM("platinum", "البلاتيني", "Platinum", 210.0, 0.12, 0.05);

    val title get() = tr(ar, en)
}

/** وضع المظهر: داكن (الأصل)، فاتح، أو يتبع إعداد الجوال */
enum class ThemeMode(val key: String, private val ar: String, private val en: String) {
    DARK("dark", "داكن", "Dark"), LIGHT("light", "فاتح", "Light"), SYSTEM("system", "تلقائي", "Auto");

    val title get() = tr(ar, en)
}

object LocalStore {
    lateinit var prefs: SharedPreferences
        private set

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.getSharedPreferences("afiati", Context.MODE_PRIVATE)
    }
}

/** المظهر المحفوظ في الجوال. تغييره يعيد تلوين الواجهة فورًا، لأن ألوان Brand تُقرأ من هذه الحالة. */
object Appearance {
    var accent by mutableStateOf(ThemeAccent.OCEAN)
        private set
    var mode by mutableStateOf(ThemeMode.DARK)
        private set
    var systemDark by mutableStateOf(true)
    /** تكبير الخط لمن يحتاج */
    var largeText by mutableStateOf(false)
        private set

    val light get() = mode == ThemeMode.LIGHT || (mode == ThemeMode.SYSTEM && !systemDark)

    fun init(context: Context) {
        val prefs = LocalStore.prefs
        accent = ThemeAccent.entries.firstOrNull { it.key == prefs.getString("appearance.accent", null) } ?: ThemeAccent.OCEAN
        mode = ThemeMode.entries.firstOrNull { it.key == prefs.getString("appearance.mode", null) } ?: ThemeMode.DARK
        largeText = prefs.getBoolean("appearance.largeText", false)
        systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO
    }

    fun apply(accent: ThemeAccent, mode: ThemeMode) {
        this.accent = accent
        this.mode = mode
        LocalStore.prefs.edit().putString("appearance.accent", accent.key).putString("appearance.mode", mode.key).apply()
    }

    fun toggleLargeText(value: Boolean) {
        largeText = value
        LocalStore.prefs.edit().putBoolean("appearance.largeText", value).apply()
    }

    fun tone(rgb: Long): Color = Palette(accent, light).color(rgb)
}

/**
 * يحوّل الألوان الأصلية (الزمرّدي الداكن) إلى السمة المختارة:
 * يدوّر الأخضر إلى لون السمة، وفي الفاتح يقلب الخلفيات الداكنة إلى لؤلؤي ويغمّق الألوان المضيئة.
 */
class Palette(val accent: ThemeAccent, val light: Boolean) {
    fun color(rgb: Long): Color {
        val key = Triple(accent, light, rgb)
        cache[key]?.let { return it }
        val r = ((rgb shr 16) and 0xFF) / 255.0
        val g = ((rgb shr 8) and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0
        var (h, s, l) = hsl(r, g, b)
        val hue = accent.hue
        if (hue != null && s >= 0.08 && h in 110.0..185.0) {
            h = hue + (h - 155) * 0.4
            s = min(1.0, s * accent.sat)
            l = min(0.97, l + if (l > 0.5) accent.lift else 0.0)
        }
        if (light) {
            val baseHue = hue ?: 155.0
            val platinum = accent == ThemeAccent.PLATINUM
            when {
                s < 0.05 && l > 0.95 -> { h = baseHue; s = 0.35 * accent.sat; l = 0.13 }
                l < 0.2 -> {
                    if (s < 0.01) h = baseHue
                    s = min(s, 0.4) * accent.sat
                    l = 0.965 - l * 0.7
                }
                l > 0.55 && s > (if (platinum) 0.03 else 0.3) -> l *= if (platinum) 0.5 else 0.62
            }
        }
        val (red, green, blue) = rgb(h, s, l)
        return Color(red.toFloat(), green.toFloat(), blue.toFloat()).also { cache[key] = it }
    }

    companion object {
        private val cache = HashMap<Triple<ThemeAccent, Boolean, Long>, Color>()

        private fun hsl(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
            val maxV = max(r, max(g, b))
            val minV = min(r, min(g, b))
            val l = (maxV + minV) / 2
            if (maxV == minV) return Triple(0.0, 0.0, l)
            val d = maxV - minV
            val s = if (l > 0.5) d / (2 - maxV - minV) else d / (maxV + minV)
            val h = when (maxV) {
                r -> (g - b) / d + (if (g < b) 6 else 0)
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            } * 60
            return Triple(h, s, l)
        }

        private fun rgb(h: Double, s: Double, l: Double): Triple<Double, Double, Double> {
            if (s <= 0) return Triple(l, l, l)
            val hue = ((h % 360) + 360) % 360 / 360
            val q = if (l < 0.5) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            fun f(t0: Double): Double {
                var t = t0
                if (t < 0) t += 1
                if (t > 1) t -= 1
                return when {
                    t < 1.0 / 6 -> p + (q - p) * 6 * t
                    t < 1.0 / 2 -> q
                    t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                    else -> p
                }
            }
            return Triple(f(hue + 1.0 / 3), f(hue), f(hue - 1.0 / 3))
        }
    }
}
