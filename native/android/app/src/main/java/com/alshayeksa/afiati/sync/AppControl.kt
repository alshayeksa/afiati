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

    /** يقرأ الحالة من الخادم. الأخطاء تُتجاهل وتبقى الحالة المحفوظة. */
    suspend fun refresh() {
        val config = withContext(Dispatchers.IO) { runCatching { Api.request("/v1/app-config", auth = false) }.getOrNull() } ?: return
        val android = config.optJSONObject("android")
        serviceOn = config.optBoolean("service_on", true)
        minBuild = android?.optInt("min_build", 0) ?: 0
        updateUrl = android?.optString("url").orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
        LocalStore.prefs.edit().putBoolean(KEY_SERVICE, serviceOn).putInt(KEY_MIN, minBuild).putString(KEY_URL, updateUrl).apply()
    }
}
