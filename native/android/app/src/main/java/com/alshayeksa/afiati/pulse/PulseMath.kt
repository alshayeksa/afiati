package com.alshayeksa.afiati.pulse

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** نتيجة قياس اجتاز فحص الجودة */
data class PulseEstimate(val bpm: Int, val quality: Double)

/** متوسطات إطار واحد من الكاميرا (منتصف الصورة) */
data class FrameStats(val timeSeconds: Double, val luma: Double, val cb: Double, val cr: Double)

/**
 * حساب النبض من الضوء المار في الإصبع (PPG): كل نبضة تملأ الإصبع بالدم فيعتم قليلًا.
 * كل شيء على الجوال، ولا تُحفظ أي صورة.
 *
 * الطريقتان يجب أن تتفقا قبل عرض أي رقم: الارتباط الذاتي (تكرار الموجة) وعدّ القمم.
 * إن لم تتفقا أو كانت الإشارة ضعيفة فلا رقم، ويُطلب إعادة القياس.
 */
object PulseMath {
    const val RATE = 30.0
    /** 40 إلى 200 نبضة في الدقيقة */
    const val MIN_BPM = 40.0
    const val MAX_BPM = 200.0
    private const val LOW_CUT = 0.7
    private const val HIGH_CUT = 3.5
    const val MIN_QUALITY = 0.45

    /**
     * هل الإصبع يغطي الكاميرا والفلاش؟ الصورة تصير حمراء متجانسة.
     * Cr (الأحمر) مرتفع وCb منخفض، والإضاءة ليست سوداء ولا مشبعة.
     */
    fun fingerOn(frame: FrameStats) = frame.cr > 160 && frame.cr - frame.cb > 50 && frame.luma in 15.0..245.0

    fun resample(times: DoubleArray, values: DoubleArray, rate: Double = RATE): DoubleArray {
        if (times.size < 2) return DoubleArray(0)
        val count = ((times.last() - times.first()) * rate).toInt() + 1
        val out = DoubleArray(count)
        var j = 0
        for (i in 0 until count) {
            val t = times.first() + i / rate
            while (j < times.size - 2 && times[j + 1] < t) j++
            val t0 = times[j]
            val t1 = times[j + 1]
            val f = if (t1 > t0) ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0) else 0.0
            out[i] = values[j] + (values[j + 1] - values[j]) * f
        }
        return out
    }

    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        fun run(x: DoubleArray): DoubleArray {
            val y = DoubleArray(x.size)
            var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
            for (i in x.indices) {
                val v = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x[i]; y2 = y1; y1 = v
                y[i] = v
            }
            return y
        }

        /** للأمام ثم للخلف: بلا إزاحة زمنية */
        fun twoWay(x: DoubleArray) = run(run(x).reversedArray()).reversedArray()
    }

    /** Butterworth من الدرجة الثانية (معادلات RBJ) */
    private fun biquad(cutoff: Double, rate: Double, highPass: Boolean): Biquad {
        val w0 = 2 * PI * cutoff / rate
        val alpha = sin(w0) / (2 * 0.7071067811865476)
        val c = cos(w0)
        val a0 = 1 + alpha
        return if (highPass) {
            Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        } else {
            Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }
    }

    /** يبقي ترددات النبض فقط (0.7 إلى 3.5 هرتز) ويزيل انجراف الإضاءة */
    fun bandPass(x: DoubleArray, rate: Double = RATE): DoubleArray {
        if (x.size < 8) return DoubleArray(0)
        val mean = x.average()
        // انعكاس عند الطرفين حتى لا تشوّه بداية المرشح ونهايته الإشارة
        val pad = minOf((2 * rate).toInt(), x.size - 1)
        val padded = DoubleArray(x.size + 2 * pad)
        for (i in padded.indices) {
            val k = i - pad
            val j = when {
                k < 0 -> -k
                k >= x.size -> 2 * (x.size - 1) - k
                else -> k
            }
            padded[i] = x[j] - mean
        }
        val filtered = biquad(HIGH_CUT, rate, false).twoWay(biquad(LOW_CUT, rate, true).twoWay(padded))
        return filtered.copyOfRange(pad, pad + x.size)
    }

    /** النبض من تكرار الموجة، ومدى وضوح التكرار (0 إلى 1) */
    fun autocorrelation(x: DoubleArray, rate: Double = RATE): Pair<Double, Double>? {
        val n = x.size
        val minLag = (rate * 60 / MAX_BPM).toInt()
        val maxLag = (rate * 60 / MIN_BPM).toInt() + 1
        if (n < maxLag * 2) return null
        val energy = x.sumOf { it * it } / n
        if (energy <= 0) return null
        val r = DoubleArray(maxLag + 2)
        for (lag in 0..maxLag + 1) {
            var sum = 0.0
            for (i in 0 until n - lag) sum += x[i] * x[i + lag]
            r[lag] = sum / (n - lag) / energy
        }
        val peaks = (max(minLag, 1)..maxLag).filter { r[it] > r[it - 1] && r[it] >= r[it + 1] }
        if (peaks.isEmpty()) return null
        val best = peaks.maxOf { r[it] }
        // أقصر دورة قريبة من الأفضل: لا نخلط النبض بضعفه
        val lag = peaks.first { r[it] >= 0.85 * best }
        val a = r[lag - 1]; val b = r[lag]; val c = r[lag + 1]
        val shift = (a - c) / (2 * (a - 2 * b + c)).let { if (it == 0.0) 1.0 else it }
        val period = (lag + shift.coerceIn(-0.5, 0.5)) / rate
        return 60 / period to b
    }

    /** مواضع القمم: كل قمة هي الأعلى ضمن 45% من الدورة على جانبيها، فلا تُعدّ الموجة الثانوية بعد النبضة */
    fun peaks(x: DoubleArray, periodSeconds: Double, rate: Double = RATE): List<Int> {
        val half = max(1, (0.45 * periodSeconds * rate).toInt())
        val threshold = 0.3 * sqrt(x.sumOf { it * it } / x.size)
        val out = mutableListOf<Int>()
        for (i in x.indices) {
            if (x[i] < threshold) continue
            var top = true
            for (j in max(0, i - half)..minOf(x.size - 1, i + half)) {
                if (x[j] > x[i] || (x[j] == x[i] && j < i)) { top = false; break }
            }
            if (top) out += i
        }
        return out
    }

    private fun median(values: List<Double>) = values.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

    /** النبض إن كانت الإشارة واضحة، وإلا null */
    fun estimate(times: DoubleArray, values: DoubleArray): PulseEstimate? {
        if (times.size < 2 || times.last() - times.first() < 6) return null
        // الإصبع يعتم مع النبضة: نعكس الإشارة لتصير النبضة قمة
        val signal = bandPass(resample(times, values)).map { -it }.toDoubleArray()
        val (acBpm, clarity) = autocorrelation(signal) ?: return null
        if (clarity < MIN_QUALITY || acBpm !in MIN_BPM..MAX_BPM) return null
        val top = peaks(signal, 60 / acBpm)
        if (top.size < 5) return null
        // موضع كل قمة بدقة أقل من إطار (قطع مكافئ)، فلا يقفز النبض السريع بسبب حجم الإطار
        val exact = top.map { i ->
            if (i == 0 || i == signal.size - 1) i.toDouble() else {
                val a = signal[i - 1]; val b = signal[i]; val c = signal[i + 1]
                val d = a - 2 * b + c
                i + if (d == 0.0) 0.0 else ((a - c) / (2 * d)).coerceIn(-0.5, 0.5)
            }
        }
        val intervals = exact.zipWithNext { a, b -> (b - a) / RATE }
        val typical = median(intervals)
        val steady = intervals.filter { abs(it - typical) <= 0.25 * typical }
        val steadyShare = steady.size.toDouble() / intervals.size
        if (steadyShare < 0.7) return null
        val peakBpm = 60 / steady.average()
        // الطريقتان يجب أن تتفقا
        if (abs(peakBpm - acBpm) / acBpm > 0.1) return null
        return PulseEstimate(peakBpm.roundToInt(), clarity * steadyShare)
    }

    /** آخر ثوانٍ للعرض: بلا انجراف، والنبضة للأعلى، بين -1 و1 */
    fun display(times: DoubleArray, values: DoubleArray, seconds: Double = 5.0): List<Float> {
        if (times.size < 10) return emptyList()
        val from = times.indexOfFirst { it >= times.last() - seconds - 1.5 }.coerceAtLeast(0)
        val uniform = resample(times.copyOfRange(from, times.size), values.copyOfRange(from, values.size))
        if (uniform.size < 10) return emptyList()
        val filtered = bandPass(uniform).map { -it }
        val tail = filtered.takeLast((seconds * RATE).toInt())
        val peak = tail.maxOf { abs(it) }.takeIf { it > 1e-9 } ?: return tail.map { 0f }
        return tail.map { (it / peak).toFloat() }
    }
}
