package com.alshayeksa.afiati

import com.alshayeksa.afiati.pulse.FrameStats
import com.alshayeksa.afiati.pulse.PulseMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class PulseMathTest {
    /**
     * إشارة إصبع صناعية: نبضة حادة الصعود مع موجة ثانوية (dicrotic)، انجراف إضاءة بطيء،
     * ضوضاء، وإطارات غير منتظمة بين 27 و31 إطارًا في الثانية.
     */
    private fun finger(bpm: Double, seconds: Double = 30.0, noise: Double = 0.25, jitter: Double = 0.1, seed: Int = 1): Pair<DoubleArray, DoubleArray> {
        val random = Random(seed)
        val times = mutableListOf<Double>()
        val values = mutableListOf<Double>()
        var t = 0.0
        var beatStart = 0.0
        var period = 60 / bpm
        while (t < seconds) {
            if (t - beatStart >= period) {
                beatStart += period
                // تغير طبيعي بسيط بين النبضات
                period = 60 / bpm * (1 + (random.nextDouble() - 0.5) * 2 * jitter * 0.3)
            }
            val phase = (t - beatStart) / period
            val beat = exp(-((phase - 0.15) * (phase - 0.15)) / 0.006) + 0.35 * exp(-((phase - 0.5) * (phase - 0.5)) / 0.01)
            val drift = 6 * sin(2 * PI * t / 23) + 0.3 * t
            // الدم يعتم الإصبع: الإضاءة تنخفض مع النبضة
            values += 120 + drift - 1.5 * beat + noise * (random.nextDouble() - 0.5) * 2
            times += t
            t += 1 / (27 + random.nextDouble() * 4)
        }
        return times.toDoubleArray() to values.toDoubleArray()
    }

    @Test
    fun readsCommonHeartRates() {
        for (bpm in listOf(48.0, 60.0, 72.0, 88.0, 105.0, 130.0, 160.0)) {
            for (seed in 1..3) {
                val (t, v) = finger(bpm, seed = seed)
                val estimate = PulseMath.estimate(t, v)
                assertNotNull("no reading at $bpm (seed $seed)", estimate)
                assertEquals("bpm $bpm seed $seed", bpm, estimate!!.bpm.toDouble(), 2.0)
            }
        }
    }

    @Test
    fun noisySignalStillWithinTwoBeats() {
        val (t, v) = finger(75.0, noise = 0.9, seed = 7)
        val estimate = PulseMath.estimate(t, v)
        assertNotNull(estimate)
        assertEquals(75.0, estimate!!.bpm.toDouble(), 2.0)
    }

    @Test
    fun pureNoiseGivesNoNumber() {
        for (seed in 1..20) {
            val random = Random(seed)
            val t = DoubleArray(900) { it / 30.0 }
            val v = DoubleArray(900) { 120 + 2 * (random.nextDouble() - 0.5) }
            assertNull("noise produced a number (seed $seed)", PulseMath.estimate(t, v))
        }
    }

    @Test
    fun flatSignalGivesNoNumber() {
        val t = DoubleArray(900) { it / 30.0 }
        assertNull(PulseMath.estimate(t, DoubleArray(900) { 120.0 }))
    }

    @Test
    fun tooShortGivesNoNumber() {
        val (t, v) = finger(70.0, seconds = 4.0)
        assertNull(PulseMath.estimate(t, v))
    }

    @Test
    fun handMovementBurstsAreRejectedOrStillAccurate() {
        val (t, v) = finger(70.0, seed = 3)
        val random = Random(9)
        // حركات قوية متكررة تفوق النبض بكثير
        for (i in v.indices) if ((t[i] % 4.0) < 1.2) v[i] += 25 * (random.nextDouble() - 0.5)
        val estimate = PulseMath.estimate(t, v)
        // إما لا رقم، أو رقم صحيح؛ لا رقم خاطئ
        if (estimate != null) assertEquals(70.0, estimate.bpm.toDouble(), 3.0)
    }

    @Test
    fun displayWaveIsNormalized() {
        val (t, v) = finger(72.0, seconds = 10.0)
        val wave = PulseMath.display(t, v)
        assertEquals(150, wave.size)
        assertTrue(wave.all { it in -1f..1f })
        assertEquals(1f, wave.maxOf { kotlin.math.abs(it) }, 1e-4f)
    }

    @Test
    fun fingerDetection() {
        // إصبع على الفلاش: أحمر قوي
        assertTrue(PulseMath.fingerOn(FrameStats(0.0, luma = 95.0, cb = 85.0, cr = 235.0)))
        // غرفة عادية
        assertFalse(PulseMath.fingerOn(FrameStats(0.0, luma = 110.0, cb = 125.0, cr = 132.0)))
        // الكاميرا مغطاة بلا ضوء
        assertFalse(PulseMath.fingerOn(FrameStats(0.0, luma = 6.0, cb = 120.0, cr = 175.0)))
        // جسم أحمر بعيد غير مضاء بالفلاش يبقى مقبولًا فقط إن كان شديد الحمرة
        assertFalse(PulseMath.fingerOn(FrameStats(0.0, luma = 120.0, cb = 110.0, cr = 150.0)))
    }
}
