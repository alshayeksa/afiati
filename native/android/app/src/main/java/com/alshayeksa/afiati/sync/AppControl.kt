package com.alshayeksa.afiati.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alshayeksa.afiati.BuildConfig
import com.alshayeksa.afiati.ui.LocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * التحكم من صفحة /forceupdate: إيقاف الخدمة، والتحديث الإلزامي لنسخة أندرويد.
 * يُقرأ من الخادم عند كل فتح للتطبيق، ويُحفظ آخر ما وصل فيبقى ساريًا بلا إنترنت.
 */
object AppControl {
    enum class Block { SERVICE_OFF, UPDATE }

    private const val KEY_SERVICE = "control.service_on"
    private const val KEY_MIN = "control.min_build"
    private const val KEY_URL = "control.url"

    var serviceOn by mutableStateOf(true); private set
    var minBuild by mutableStateOf(0); private set
    var updateUrl by mutableStateOf(""); private set

    /** ما يمنع استخدام التطبيق الآن، أو null */
    val block: Block?
        get() = when {
            !serviceOn -> Block.SERVICE_OFF
            minBuild > 0 && BuildConfig.VERSION_CODE < minBuild -> Block.UPDATE
            else -> null
        }

    fun load() {
        val prefs = LocalStore.prefs
        serviceOn = prefs.getBoolean(KEY_SERVICE, true)
        minBuild = prefs.getInt(KEY_MIN, 0)
        updateUrl = prefs.getString(KEY_URL, "").orEmpty()
    }

    /**
     * يقرأ الإعدادات من afiati.alshaye.ai (وإن تعذر فمن الخادم الحالي). الأخطاء تُتجاهل وتبقى الحالة المحفوظة.
     * ومنها عنوان الخادم: لا ينتقل إليه التطبيق إلا بعد أن يجيب /health، فلا ينقطع بعنوان خاطئ.
     */
    suspend fun refresh() {
        val config = withContext(Dispatchers.IO) {
            runCatching { Api.request("", auth = false, absolute = BuildConfig.CONFIG_URL) }.getOrNull()
                ?: runCatching { Api.request("/v1/app-config", auth = false) }.getOrNull()
        } ?: return
        withContext(Dispatchers.IO) { applyServer(config.optString("api_url").takeIf { !config.isNull("api_url") }) }
        val android = config.optJSONObject("android")
        serviceOn = config.optBoolean("service_on", true)
        minBuild = android?.optInt("min_build", 0) ?: 0
        updateUrl = android?.optString("url").orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
        LocalStore.prefs.edit().putBoolean(KEY_SERVICE, serviceOn).putInt(KEY_MIN, minBuild).putString(KEY_URL, updateUrl).apply()
    }

    private fun applyServer(url: String?) {
        val target = url?.trimEnd('/')?.takeIf { it.startsWith("https://") }
        if (target == null || target == Api.defaultUrl) {
            if (Api.baseUrl != Api.defaultUrl) Api.setBaseUrl(null)
            return
        }
        if (target == Api.baseUrl) return
        val healthy = runCatching { Api.request("", auth = false, absolute = "$target/health").optBoolean("ok") }.getOrDefault(false)
        if (healthy) Api.setBaseUrl(target)
    }
}
