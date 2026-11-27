package com.alshayeksa.afiati.pulse

import com.alshayeksa.afiati.tr
import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * الكاميرا الخلفية والفلاش للقياس فقط: تُفتح عند البدء وتُغلق مع الفلاش عند الانتهاء.
 * لكل قياس كائن جديد. لا تُحفظ الصور ولا تغادر الجوال: يُحسب من كل إطار متوسط الألوان فقط.
 * يُستدعى من الخيط الرئيسي.
 */
class PulseCamera(
    private val context: Context,
    private val onFrame: (FrameStats) -> Unit,
    private val onReady: (hasFlash: Boolean) -> Unit,
    private val onError: (String) -> Unit,
) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var stopped = false
    private var firstTimestamp = -1L

    fun start() {
        registry.currentState = Lifecycle.State.CREATED
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (stopped) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    // صورة صغيرة تكفي: نحتاج متوسط اللون فقط
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(320, 240), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .build()
                analysis.setAnalyzer(executor, ::analyze)
                cameraProvider.unbindAll()
                registry.currentState = Lifecycle.State.STARTED
                val bound = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                camera = bound
                registry.currentState = Lifecycle.State.RESUMED
                val hasFlash = bound.cameraInfo.hasFlashUnit()
                if (hasFlash) bound.cameraControl.enableTorch(true)
                onReady(hasFlash)
            } catch (error: Exception) {
                onError(tr("تعذّر تشغيل الكاميرا. أغلق التطبيقات التي تستخدمها وحاول مرة أخرى.", "Couldn't start the camera. Close apps that use it and try again."))
                stop()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        if (stopped) return
        stopped = true
        runCatching { camera?.cameraControl?.enableTorch(false) }
        runCatching { provider?.unbindAll() }
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
        camera = null
        executor.shutdown()
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (firstTimestamp < 0) firstTimestamp = image.imageInfo.timestamp
            val stats = average(image, (image.imageInfo.timestamp - firstTimestamp) / 1e9)
            ContextCompat.getMainExecutor(context).execute { if (!stopped) onFrame(stats) }
        } finally {
            image.close()
        }
    }

    /** متوسط Y وCb وCr في نصف الصورة الأوسط، بخطوة 4 بكسلات */
    private fun average(image: ImageProxy, seconds: Double): FrameStats {
        val width = image.width
        val height = image.height
        val y = image.planes[0]
        val u = image.planes[1]
        val v = image.planes[2]
        var sumY = 0L; var sumU = 0L; var sumV = 0L; var count = 0
        var row = height / 4
        while (row < height * 3 / 4) {
            var col = width / 4
            while (col < width * 3 / 4) {
                sumY += y.buffer.get(row * y.rowStride + col * y.pixelStride).toInt() and 0xFF
                val chroma = (row / 2)
                sumU += u.buffer.get(chroma * u.rowStride + (col / 2) * u.pixelStride).toInt() and 0xFF
                sumV += v.buffer.get(chroma * v.rowStride + (col / 2) * v.pixelStride).toInt() and 0xFF
                count++
                col += 4
            }
            row += 4
        }
        val n = count.coerceAtLeast(1).toDouble()
        return FrameStats(seconds, sumY / n, sumU / n, sumV / n)
    }
}
