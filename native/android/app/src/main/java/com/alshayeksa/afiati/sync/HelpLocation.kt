package com.alshayeksa.afiati.sync

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** موقع الكبير لطلب المساعدة فقط: يُقرأ عند فتح شاشة المساعدة، ولا يُتابع في الخلفية. */
data class HelpPoint(val latitude: Double, val longitude: Double, val accuracy: Float?, val timeMillis: Long)

object HelpLocation {
    /** موقع أقدم من هذا لا يُرسل */
    private const val MAX_AGE_MS = 10 * 60 * 1000L
    /** دقة تكفي ولا داعي لانتظار GPS بعدها */
    private const val GOOD_ENOUGH_METERS = 50f

    fun granted(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun enabled(context: Context): Boolean =
        context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) ?: false

    private fun Location.point() = HelpPoint(latitude, longitude, if (hasAccuracy()) accuracy else null, time)

    private fun providers(manager: LocationManager): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31 && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
    }

    /** آخر موقع يعرفه الجوال إن كان حديثًا: فوري، يصلح لإرساله مع الطلب مباشرة */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): HelpPoint? {
        if (!granted(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            providers(manager).mapNotNull { manager.getLastKnownLocation(it) }
                .filter { System.currentTimeMillis() - it.time <= MAX_AGE_MS }
                .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
                ?.point()
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun fromProvider(context: Context, manager: LocationManager, provider: String): HelpPoint? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                    if (continuation.isActive) continuation.resume(location?.point())
                }
            }.onFailure { if (continuation.isActive) continuation.resume(null) }
        }

    /** موقع جديد الآن: الأسرع من الشبكة، ثم GPS إن كانت دقتها ضعيفة. null إن تعذّر خلال المهلة. */
    suspend fun current(context: Context, timeoutMs: Long = 25_000): HelpPoint? {
        if (!granted(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val names = runCatching { providers(manager) }.getOrDefault(emptyList())
        if (names.isEmpty()) return null
        // كل مصدر يحدّث الأفضل حين يصل؛ عند انتهاء المهلة نأخذ أفضل ما وصل
        var best: HelpPoint? = null
        val accuracy = { point: HelpPoint? -> point?.accuracy ?: Float.MAX_VALUE }
        withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                val done = Channel<Unit>(Channel.UNLIMITED)
                val jobs = names.map { name ->
                    launch {
                        val point = fromProvider(context, manager, name)
                        synchronized(this@HelpLocation) { if (point != null && accuracy(point) < accuracy(best)) best = point }
                        done.send(Unit)
                    }
                }
                repeat(jobs.size) {
                    done.receive()
                    if (accuracy(best) <= GOOD_ENOUGH_METERS) return@coroutineScope jobs.forEach { it.cancel() }
                }
            }
        }
        return best ?: lastKnown(context)
    }
}
