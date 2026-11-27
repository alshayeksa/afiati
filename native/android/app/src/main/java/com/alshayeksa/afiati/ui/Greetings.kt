package com.alshayeksa.afiati.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alshayeksa.afiati.data.Json
import com.alshayeksa.afiati.sync.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate
import kotlin.random.Random

/**
 * تحية اليوم في الشاشة الرئيسية: من جدول greetings في الخادم (تُضاف له تحيات جديدة متى شئت).
 * القائمة محفوظة في الجوال فتعمل بلا إنترنت، وتتحدث مرة كل 12 ساعة.
 * كل يوم تحية مختلفة عشوائيًا، ولا تتكرر حتى تمر كل التحيات.
 */
object Greetings {
    private const val KEY_LIST = "greetings.list"
    private const val KEY_FETCHED = "greetings.fetched_at"
    private const val REFRESH_MS = 12 * 60 * 60 * 1000L

    /** نسخة مرفقة بالتطبيق لأول تشغيل بلا إنترنت (نفس بداية الجدول) */
    private val bundled = listOf(
        "نهارك سعيد وعافيتك تدوم.", "دمت بصحة وعافية.", "أسعد الله يومك.", "يومك صحة وطمأنينة.", "صحتك أغلى ما تملك.",
        "عافاك الله وأدام ابتسامتك.", "كل يوم بداية جميلة.", "جعل الله أيامك عافية.", "ابتسم، فالحياة جميلة.", "صحتك تستحق اهتمامك.",
        "ربي يحفظك ويسعدك.", "يوم جميل بإذن الله.", "عافيتك تسعد من يحبك.", "كن بخير دائمًا.", "أدام الله عليك الصحة.",
        "يوم مليء بالخير والبركة.", "اعتنِ بنفسك، فأنت غالٍ.", "عسى أيامك كلها عافية.", "صحتك اليوم، سعادتك غدًا.", "حفظك الله لمن يحبك.",
        "ألبسك الله ثوب العافية.", "يومك سعادة وراحة بال.", "متعك الله بالصحة والعافية.", "العافية أجمل النعم.", "حفظك الله من كل مكروه.",
        "أدام الله عليك نعمة العافية.", "أيامك جميلة بإذن الله.", "أسعد الله قلبك.", "عافيتك تهمنا.", "طاب يومك ودامت عافيتك.",
    )

    var list by mutableStateOf(bundled)
        private set

    fun load() {
        val saved = runCatching { LocalStore.prefs.getString(KEY_LIST, null) }.getOrNull() ?: return
        runCatching { JSONArray(saved) }.getOrNull()
            ?.let { array -> (0 until array.length()).map(array::getString).filter { it.isNotBlank() } }
            ?.takeIf { it.isNotEmpty() }
            ?.let { list = it }
    }

    /** يجلب الجدول من الخادم إن مر وقت كافٍ. الأخطاء تُتجاهل وتبقى القائمة المحفوظة. */
    suspend fun refresh(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val last = LocalStore.prefs.getLong(KEY_FETCHED, 0L)
            if (!force && System.currentTimeMillis() - last < REFRESH_MS) return@runCatching
            val texts = Json.objects(Api.request("/v1/greetings", auth = false).optJSONArray("greetings"))
                .map { it.optString("text").trim() }.filter { it.isNotEmpty() }
            if (texts.isNotEmpty()) {
                LocalStore.prefs.edit().putString(KEY_LIST, JSONArray(texts).toString()).putLong(KEY_FETCHED, System.currentTimeMillis()).apply()
                withContext(Dispatchers.Main) { list = texts }
            }
        }
    }

    fun forDay(date: LocalDate): String = pick(list, date.toEpochDay())

    /**
     * ترتيب عشوائي ثابت لكل دورة، والدورة بطول القائمة: كل تحية تظهر مرة واحدة قبل أن تتكرر،
     * ونفس اليوم يعطي نفس التحية طوال اليوم.
     */
    fun pick(items: List<String>, epochDay: Long): String {
        if (items.isEmpty()) return ""
        val size = items.size
        val cycle = Math.floorDiv(epochDay, size.toLong())
        val position = Math.floorMod(epochDay, size.toLong()).toInt()
        return items.shuffled(Random(cycle * 31 + size)).get(position)
    }
}
