import Foundation

/// نتيجة قياس اجتاز فحص الجودة
struct PulseEstimate: Equatable {
    let bpm: Int
    let quality: Double
}

/// متوسطات إطار واحد من الكاميرا (منتصف الصورة)
struct FrameStats {
    let timeSeconds: Double
    let luma: Double
    let cb: Double
    let cr: Double
}

/**
 حساب النبض من الضوء المار في الإصبع (PPG): كل نبضة تملأ الإصبع بالدم فيعتم قليلًا.
 كل شيء على الجوال، ولا تُحفظ أي صورة. نفس خوارزمية أندرويد (PulseMath.kt).

 الطريقتان يجب أن تتفقا قبل عرض أي رقم: الارتباط الذاتي (تكرار الموجة) وعدّ القمم.
 إن لم تتفقا أو كانت الإشارة ضعيفة فلا رقم، ويُطلب إعادة القياس.
 */
enum PulseMath {
    static let rate = 30.0
    /// 40 إلى 200 نبضة في الدقيقة
    static let minBpm = 40.0
    static let maxBpm = 200.0
    private static let lowCut = 0.7
    private static let highCut = 3.5
    static let minQuality = 0.45

    /**
     هل الإصبع يغطي الكاميرا والفلاش؟ الصورة تصير حمراء متجانسة.
     Cr (الأحمر) مرتفع وCb منخفض، والإضاءة ليست سوداء ولا مشبعة.
     */
    static func fingerOn(_ frame: FrameStats) -> Bool {
        frame.cr > 160 && frame.cr - frame.cb > 50 && (15.0...245.0).contains(frame.luma)
    }

    static func resample(_ times: [Double], _ values: [Double], rate: Double = rate) -> [Double] {
        guard times.count >= 2, let first = times.first, let last = times.last else { return [] }
        let count = Int((last - first) * rate) + 1
        var out = [Double](repeating: 0, count: count)
        var j = 0
        for i in 0..<count {
            let t = first + Double(i) / rate
            while j < times.count - 2 && times[j + 1] < t { j += 1 }
            let t0 = times[j], t1 = times[j + 1]
            let f = t1 > t0 ? min(max((t - t0) / (t1 - t0), 0), 1) : 0
            out[i] = values[j] + (values[j + 1] - values[j]) * f
        }
        return out
    }

    private struct Biquad {
        let b0, b1, b2, a1, a2: Double

        func run(_ x: [Double]) -> [Double] {
            var y = [Double](repeating: 0, count: x.count)
            var x1 = 0.0, x2 = 0.0, y1 = 0.0, y2 = 0.0
            for i in 0..<x.count {
                let v = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x[i]; y2 = y1; y1 = v
                y[i] = v
            }
            return y
        }

        /// للأمام ثم للخلف: بلا إزاحة زمنية
        func twoWay(_ x: [Double]) -> [Double] { Array(run(Array(run(x).reversed())).reversed()) }
    }

    /// Butterworth من الدرجة الثانية (معادلات RBJ)
    private static func biquad(cutoff: Double, rate: Double, highPass: Bool) -> Biquad {
        let w0 = 2 * Double.pi * cutoff / rate
        let alpha = sin(w0) / (2 * 0.7071067811865476)
        let c = cos(w0)
        let a0 = 1 + alpha
        if highPass {
            return Biquad(b0: (1 + c) / 2 / a0, b1: -(1 + c) / a0, b2: (1 + c) / 2 / a0, a1: -2 * c / a0, a2: (1 - alpha) / a0)
        }
        return Biquad(b0: (1 - c) / 2 / a0, b1: (1 - c) / a0, b2: (1 - c) / 2 / a0, a1: -2 * c / a0, a2: (1 - alpha) / a0)
    }

    /// يبقي ترددات النبض فقط (0.7 إلى 3.5 هرتز) ويزيل انجراف الإضاءة
    static func bandPass(_ x: [Double], rate: Double = rate) -> [Double] {
        guard x.count >= 8 else { return [] }
        let mean = x.reduce(0, +) / Double(x.count)
        // انعكاس عند الطرفين حتى لا تشوّه بداية المرشح ونهايته الإشارة
        let pad = min(Int(2 * rate), x.count - 1)
        var padded = [Double](repeating: 0, count: x.count + 2 * pad)
        for i in 0..<padded.count {
            let k = i - pad
            let j = k < 0 ? -k : (k >= x.count ? 2 * (x.count - 1) - k : k)
            padded[i] = x[j] - mean
        }
        let filtered = biquad(cutoff: highCut, rate: rate, highPass: false).twoWay(biquad(cutoff: lowCut, rate: rate, highPass: true).twoWay(padded))
        return Array(filtered[pad..<(pad + x.count)])
    }

    /// النبض من تكرار الموجة، ومدى وضوح التكرار (0 إلى 1)
    static func autocorrelation(_ x: [Double], rate: Double = rate) -> (bpm: Double, clarity: Double)? {
        let n = x.count
        let minLag = Int(rate * 60 / maxBpm)
        let maxLag = Int(rate * 60 / minBpm) + 1
        guard n >= maxLag * 2 else { return nil }
        let energy = x.reduce(0) { $0 + $1 * $1 } / Double(n)
        guard energy > 0 else { return nil }
        var r = [Double](repeating: 0, count: maxLag + 2)
        for lag in 0...(maxLag + 1) {
            var sum = 0.0
            for i in 0..<(n - lag) { sum += x[i] * x[i + lag] }
            r[lag] = sum / Double(n - lag) / energy
        }
        let peaks = (max(minLag, 1)...maxLag).filter { r[$0] > r[$0 - 1] && r[$0] >= r[$0 + 1] }
        guard let best = peaks.map({ r[$0] }).max() else { return nil }
        // أقصر دورة قريبة من الأفضل: لا نخلط النبض بضعفه
        guard let lag = peaks.first(where: { r[$0] >= 0.85 * best }) else { return nil }
        let a = r[lag - 1], b = r[lag], c = r[lag + 1]
        let denominator = 2 * (a - 2 * b + c)
        let shift = (a - c) / (denominator == 0 ? 1 : denominator)
        let period = (Double(lag) + min(max(shift, -0.5), 0.5)) / rate
        return (60 / period, b)
    }

    /// مواضع القمم: كل قمة هي الأعلى ضمن 45% من الدورة على جانبيها، فلا تُعدّ الموجة الثانوية بعد النبضة
    static func peaks(_ x: [Double], periodSeconds: Double, rate: Double = rate) -> [Int] {
        guard !x.isEmpty else { return [] }
        let half = max(1, Int(0.45 * periodSeconds * rate))
        let threshold = 0.3 * sqrt(x.reduce(0) { $0 + $1 * $1 } / Double(x.count))
        var out: [Int] = []
        for i in 0..<x.count {
            if x[i] < threshold { continue }
            var top = true
            for j in max(0, i - half)...min(x.count - 1, i + half) where x[j] > x[i] || (x[j] == x[i] && j < i) {
                top = false
                break
            }
            if top { out.append(i) }
        }
        return out
    }

    private static func median(_ values: [Double]) -> Double {
        let sorted = values.sorted()
        if sorted.count % 2 == 1 { return sorted[sorted.count / 2] }
        return (sorted[sorted.count / 2 - 1] + sorted[sorted.count / 2]) / 2
    }

    /// النبض إن كانت الإشارة واضحة، وإلا nil
    static func estimate(_ times: [Double], _ values: [Double]) -> PulseEstimate? {
        guard times.count >= 2, let first = times.first, let last = times.last, last - first >= 6 else { return nil }
        // الإصبع يعتم مع النبضة: نعكس الإشارة لتصير النبضة قمة
        let signal = bandPass(resample(times, values)).map { -$0 }
        guard let correlation = autocorrelation(signal), correlation.clarity >= minQuality, (minBpm...maxBpm).contains(correlation.bpm) else { return nil }
        let acBpm = correlation.bpm
        let clarity = correlation.clarity
        let top = peaks(signal, periodSeconds: 60 / acBpm)
        guard top.count >= 5 else { return nil }
        // موضع كل قمة بدقة أقل من إطار (قطع مكافئ)، فلا يقفز النبض السريع بسبب حجم الإطار
        let exact: [Double] = top.map { i in
            if i == 0 || i == signal.count - 1 { return Double(i) }
            let a = signal[i - 1], b = signal[i], c = signal[i + 1]
            let d = a - 2 * b + c
            return Double(i) + (d == 0 ? 0 : min(max((a - c) / (2 * d), -0.5), 0.5))
        }
        let intervals = zip(exact, exact.dropFirst()).map { ($1 - $0) / rate }
        let typical = median(intervals)
        let steady = intervals.filter { abs($0 - typical) <= 0.25 * typical }
        let steadyShare = Double(steady.count) / Double(intervals.count)
        guard steadyShare >= 0.7, !steady.isEmpty else { return nil }
        let peakBpm = 60 / (steady.reduce(0, +) / Double(steady.count))
        // الطريقتان يجب أن تتفقا
        guard abs(peakBpm - acBpm) / acBpm <= 0.1 else { return nil }
        return PulseEstimate(bpm: Int(peakBpm.rounded()), quality: clarity * steadyShare)
    }

    /// آخر ثوانٍ للعرض: بلا انجراف، والنبضة للأعلى، بين -1 و1
    static func display(_ times: [Double], _ values: [Double], seconds: Double = 5) -> [Double] {
        guard times.count >= 10, let last = times.last else { return [] }
        let from = max(times.firstIndex { $0 >= last - seconds - 1.5 } ?? 0, 0)
        let uniform = resample(Array(times[from...]), Array(values[from...]))
        guard uniform.count >= 10 else { return [] }
        let filtered = bandPass(uniform).map { -$0 }
        let tail = Array(filtered.suffix(Int(seconds * rate)))
        guard let peak = tail.map({ abs($0) }).max(), peak > 1e-9 else { return tail.map { _ in 0 } }
        return tail.map { $0 / peak }
    }
}
