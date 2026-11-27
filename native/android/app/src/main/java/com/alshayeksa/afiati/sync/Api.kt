package com.alshayeksa.afiati.sync

import com.alshayeksa.afiati.tr
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.alshayeksa.afiati.BuildConfig
import com.alshayeksa.afiati.data.SyncState
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, val code: String, message: String) : Exception(message)

/** عميل خادم عافيتي على Cloudflare (مجلد worker). */
object Api {
    private val baseUrl = BuildConfig.API_URL.trimEnd('/')
    val configured: Boolean get() = baseUrl.isNotEmpty()
    private const val TIMEOUT_MS = 20_000

    /** طلب متزامن، يُستدعى من خيط خلفي فقط */
    fun request(path: String, method: String = "GET", body: JSONObject? = null, auth: Boolean = true): JSONObject {
        if (!configured) throw ApiException(0, "not_configured", tr("خادم المزامنة غير مُعد.", "The sync server isn't set up."))
        val token = if (auth) SyncState.get(SyncState.DEVICE_TOKEN) ?: throw ApiException(401, "unauthorized", tr("الجهاز غير مسجل.", "This device isn't registered.")) else null
        val connection = try {
            (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                // منطقة الجوال الزمنية: الخادم لا يرسل في الليل التنبيهات التي تحتمل التأجيل
                setRequestProperty("X-Timezone", java.time.ZoneId.systemDefault().id)
                // لغة الواجهة: رسائل العائلة من الخادم تصل بها
                setRequestProperty("X-Language", com.alshayeksa.afiati.Lang.code)
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
            }
        } catch (error: IOException) {
            throw ApiException(0, "network_error", tr("تعذر الوصول إلى الخادم.", "Couldn't reach the server."))
        }
        try {
            val status = try { connection.responseCode } catch (error: IOException) {
                throw ApiException(0, "network_error", tr("تعذر الوصول إلى الخادم.", "Couldn't reach the server."))
            }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (status !in 200..299) {
                throw ApiException(status, json?.optString("error")?.ifEmpty { null } ?: "http_error", json?.optString("message") ?: "HTTP $status")
            }
            return json ?: JSONObject()
        } catch (error: IOException) {
            throw ApiException(0, "network_error", tr("تعذر الوصول إلى الخادم.", "Couldn't reach the server."))
        } finally {
            connection.disconnect()
        }
    }
}

object Network {
    fun online(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
