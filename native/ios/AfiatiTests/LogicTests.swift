import XCTest
@testable import Afiati

/// منطق لا يحتاج شاشة: يجب أن يطابق الخادم ونسخة أندرويد تمامًا
final class LogicTests: XCTestCase {
    func testDoseLogIdMatchesServerAndAndroid() {
        // نفس المثال في LogicTest.kt بأندرويد، ومحسوب مستقلًا بـ Python
        XCTAssertEqual(Ids.doseLogId("3f2b8c1e-1111-4222-8333-444455556666", "2026-11-27T05:00:00.000Z"), "f09b9f25-92e5-8872-b39f-f790423e0939")
        XCTAssertEqual(Ids.doseLogId("med-1", "2026-11-27T05:00:00.000Z"), "1996683f-4e7a-8726-b7ec-3ef878d9b97e")
        XCTAssertNotEqual(Ids.doseLogId("a", "2026-11-27T05:00:00.000Z"), Ids.doseLogId("a", "2026-11-27T17:00:00.000Z"))
    }

    func testIsoFormatHasMillisecondsAndZ() {
        let date = Date(timeIntervalSince1970: 1_795_755_600) // 2026-11-27T05:00:00Z
        XCTAssertEqual(Iso.format(date), "2026-11-27T05:00:00.000Z")
        XCTAssertEqual(Iso.parse("2026-11-27T05:00:00.000Z"), date)
        XCTAssertEqual(Iso.parse("2026-11-27T05:00:00Z"), date)
    }

    func testWeekdayNumbering() {
        XCTAssertEqual(Day(year: 2026, month: 11, day: 29).weekday, 1) // الأحد
        XCTAssertEqual(Day(year: 2026, month: 11, day: 26).weekday, 5) // الخميس
        XCTAssertEqual(Day(year: 2026, month: 11, day: 28).weekday, 7) // السبت
    }

    func testEpochDay() {
        XCTAssertEqual(Day(year: 1970, month: 1, day: 1).epochDay, 0)
        XCTAssertEqual(Day(year: 2026, month: 11, day: 27).epochDay, 20_784)
    }

    func testScheduleRules() {
        let thursday = Day(year: 2026, month: 11, day: 26)
        XCTAssertTrue(Schedule.isScheduled(RepeatType.weekly, [5], on: thursday))
        XCTAssertFalse(Schedule.isScheduled(RepeatType.weekly, [1], on: thursday))
        XCTAssertTrue(Schedule.isScheduled(RepeatType.monthly, [26], on: thursday))
        XCTAssertTrue(Schedule.isScheduled(RepeatType.daily, [], on: thursday))
        XCTAssertEqual(Schedule.timesFor(RepeatType.weekly, ["08:00", "20:00"]), ["08:00"])
        XCTAssertEqual(Schedule.timesFor(RepeatType.daily, ["08:00", "20:00"]), ["08:00", "20:00"])
        XCTAssertNil(Schedule.parseTime("25:00"))
        XCTAssertEqual(Schedule.parseTime("07:05")?.minute, 5)
    }

    func testNextOccurrenceSkipsPastTimes() {
        let now = Schedule.date(Day(year: 2026, month: 11, day: 26), "09:00")!
        let next = Schedule.nextOccurrence(RepeatType.daily, [], "08:00", after: now)!
        XCTAssertEqual(Day(next), Day(year: 2026, month: 11, day: 27))
        let weekly = Schedule.nextOccurrence(RepeatType.weekly, [1], "08:00", after: now)!
        XCTAssertEqual(Day(weekly), Day(year: 2026, month: 11, day: 29))
    }

    func testConflictRules() {
        let taken = Conflict.DoseState(status: DoseStatus.taken, takenAt: "2026-11-27T05:01:00.000Z", updatedAt: "2026-11-27T05:01:00.000Z")
        let pending = Conflict.DoseState(status: DoseStatus.pending, takenAt: nil, updatedAt: "2026-11-27T06:00:00.000Z")
        // الجرعة المأخوذة لا تعود، من أي طرف
        XCTAssertEqual(Conflict.mergeDose(taken, pending), taken)
        XCTAssertEqual(Conflict.mergeDose(pending, taken), taken)
        XCTAssertTrue(Conflict.shouldApplyRemote(nil, "2026-11-27T05:00:00.000Z"))
        XCTAssertFalse(Conflict.shouldApplyRemote("2026-11-27T06:00:00.000Z", "2026-11-27T05:00:00.000Z"))
    }

    func testUpcomingRemindersAreSortedLimitedAndSkipTaken() {
        let now = Schedule.date(Day(year: 2026, month: 11, day: 26), "09:00")!
        func medication(_ id: String, _ times: [String]) -> Medication {
            Medication(id: id, patientId: "p", name: id, doseAmount: 1, doseUnit: "حبة", repeatType: RepeatType.daily, repeatDays: [], frequency: times.count,
                       times: times, stockCount: nil, stockAlert: 7, isActive: true, notes: nil, createdAt: "2026-11-01T00:00:00.000Z",
                       updatedAt: "2026-11-01T00:00:00.000Z", deletedAt: nil)
        }
        let list = Reminders.upcoming([medication("a", ["08:00", "20:00"]), medication("b", ["12:00"])], after: now, limit: 5, horizonDays: 3) { id, at in
            id == "b" && at == Schedule.scheduledAt(Day(year: 2026, month: 11, day: 26), "12:00")
        }
        XCTAssertEqual(list.count, 5)
        XCTAssertEqual(list.map(\.at), list.map(\.at).sorted())
        // جرعة «b» ظهر اليوم مأخوذة مسبقًا، فأول تذكير هو «a» الساعة 8 مساءً
        XCTAssertEqual(list.first?.medication.id, "a")
        XCTAssertEqual(list.first?.time, "20:00")
    }

    func testVitalRanges() {
        XCTAssertEqual(VitalRanges.pressure(118, 76), .normal)
        XCTAssertEqual(VitalRanges.pressure(125, 79), .elevated)
        XCTAssertEqual(VitalRanges.pressure(150, 85), .high)
        XCTAssertEqual(VitalRanges.pressure(185, 100), .veryHigh)
        XCTAssertEqual(VitalRanges.pressure(85, 55), .low)
        XCTAssertEqual(VitalRanges.sugar(95, .fasting), .normal)
        XCTAssertEqual(VitalRanges.sugar(110, .fasting), .elevated)
        XCTAssertEqual(VitalRanges.sugar(110, .afterMeal), .normal)
        XCTAssertEqual(VitalRanges.sugar(50, .random), .veryLow)
        XCTAssertEqual(VitalRanges.temperature(37.0), .normal)
        XCTAssertEqual(VitalRanges.temperature(38.5), .high)
        XCTAssertEqual(VitalRanges.pulse(72), .normal)
        XCTAssertEqual(VitalRanges.display(VitalReading(id: "1", patientId: "p", type: .pressure, value: 120, value2: 80, context: nil, measuredAt: "")), "\u{2066}120/80\u{2069}")
        XCTAssertEqual(VitalRanges.display(VitalReading(id: "1", patientId: "p", type: .temperature, value: 36.86, value2: nil, context: nil, measuredAt: "")), "36.9")
    }

    func testGreetingsCycleWithoutRepeats() {
        let items = (1...30).map { "g\($0)" }
        // دورة كاملة: كل تحية مرة واحدة
        let cycle = (0..<30).map { Greetings.pick(items, epochDay: 20_790 + $0 - (20_790 % 30)) }
        XCTAssertEqual(Set(cycle).count, 30)
        XCTAssertEqual(Greetings.pick(items, epochDay: 20_784), Greetings.pick(items, epochDay: 20_784))
        XCTAssertEqual(Greetings.bundled.count, Greetings.bundledEnglish.count)
    }

    func testControlBlocks() {
        XCTAssertEqual(AppControl(serviceOn: false, minBuild: 0, updateURL: "").block, .serviceOff)
        XCTAssertEqual(AppControl(serviceOn: true, minBuild: 100_000, updateURL: "").block, .update)
        XCTAssertNil(AppControl(serviceOn: true, minBuild: 0, updateURL: "").block)
        XCTAssertNil(AppControl(serviceOn: true, minBuild: 1, updateURL: "").block)
    }

    func testArabicDoseWording() {
        XCTAssertEqual(Format.number(2), "2")
        XCTAssertEqual(Format.number(0.5), "0.5")
    }
}

/// حساب النبض: نفس اختبارات أندرويد بإشارة إصبع صناعية
final class PulseMathTests: XCTestCase {
    /// مولّد ثابت حتى تتكرر الاختبارات بنفس النتيجة
    private struct Random {
        var generator: SeededGenerator
        init(_ seed: UInt64) { generator = SeededGenerator(seed: seed) }
        mutating func next() -> Double { Double(generator.next() >> 11) / Double(1 << 53) }
    }

    /// نبضة حادة الصعود مع موجة ثانوية، انجراف إضاءة بطيء، ضوضاء، وإطارات غير منتظمة بين 27 و31 إطارًا في الثانية
    private func finger(_ bpm: Double, seconds: Double = 30, noise: Double = 0.25, jitter: Double = 0.1, seed: UInt64 = 1) -> ([Double], [Double]) {
        var random = Random(seed)
        var times: [Double] = []
        var values: [Double] = []
        var t = 0.0
        var beatStart = 0.0
        var period = 60 / bpm
        while t < seconds {
            if t - beatStart >= period {
                beatStart += period
                period = 60 / bpm * (1 + (random.next() - 0.5) * 2 * jitter * 0.3)
            }
            let phase = (t - beatStart) / period
            let beat = exp(-((phase - 0.15) * (phase - 0.15)) / 0.006) + 0.35 * exp(-((phase - 0.5) * (phase - 0.5)) / 0.01)
            let drift = 6 * sin(2 * Double.pi * t / 23) + 0.3 * t
            values.append(120 + drift - 1.5 * beat + noise * (random.next() - 0.5) * 2)
            times.append(t)
            t += 1 / (27 + random.next() * 4)
        }
        return (times, values)
    }

    func testReadsCommonHeartRates() {
        for bpm in [48.0, 60, 72, 88, 105, 130, 160] {
            for seed in UInt64(1)...3 {
                let (t, v) = finger(bpm, seed: seed)
                guard let estimate = PulseMath.estimate(t, v) else { return XCTFail("no reading at \(bpm) (seed \(seed))") }
                XCTAssertEqual(Double(estimate.bpm), bpm, accuracy: 2, "bpm \(bpm) seed \(seed)")
            }
        }
    }

    func testNoisySignalStillWithinTwoBeats() {
        let (t, v) = finger(75, noise: 0.9, seed: 7)
        guard let estimate = PulseMath.estimate(t, v) else { return XCTFail("no reading") }
        XCTAssertEqual(Double(estimate.bpm), 75, accuracy: 2)
    }

    func testPureNoiseGivesNoNumber() {
        for seed in UInt64(1)...20 {
            var random = Random(seed)
            let t = (0..<900).map { Double($0) / 30 }
            let v = (0..<900).map { _ in 120 + 2 * (random.next() - 0.5) }
            XCTAssertNil(PulseMath.estimate(t, v), "noise produced a number (seed \(seed))")
        }
    }

    func testFlatSignalGivesNoNumber() {
        let t = (0..<900).map { Double($0) / 30 }
        XCTAssertNil(PulseMath.estimate(t, [Double](repeating: 120, count: 900)))
    }

    func testFingerDetection() {
        XCTAssertTrue(PulseMath.fingerOn(FrameStats(timeSeconds: 0, luma: 90, cb: 100, cr: 200)))
        XCTAssertFalse(PulseMath.fingerOn(FrameStats(timeSeconds: 0, luma: 90, cb: 128, cr: 128)))
        XCTAssertFalse(PulseMath.fingerOn(FrameStats(timeSeconds: 0, luma: 250, cb: 100, cr: 200)))
    }
}
