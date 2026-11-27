import AVFoundation
import SwiftUI
import UIKit

/// مدة القياس بعد استقرار الإشارة
private let measureSeconds = 30.0
/// أول ثوانٍ بعد وضع الإصبع: الكاميرا تضبط الإضاءة، فلا تُحسب
private let settleSeconds = 3.0
/// إن رُفع الإصبع أطول من هذا يبدأ القياس من جديد
private let fingerGraceSeconds = 0.7
/// أقصى مدة لمحاولة واحدة، ثم يتوقف الفلاش
private let giveUpSeconds = 90.0
/// المعدل المعتاد أثناء الراحة للبالغين، للتنبيه اللطيف فقط
private let restingRange = 50...110

private struct Failure {
    let title: String
    let body: String
    var showTips = false
    var openSettings = false
}

/// حالة قياس واحد، تُحدَّث مع كل إطار على الخيط الرئيسي
@MainActor
private final class PulseSession: ObservableObject {
    private var times: [Double] = []
    private var values: [Double] = []
    private var segmentStart = -1.0
    private var lastFinger = -1.0
    private var firstFrame = -1.0
    private var frames = 0
    private var lastLive = 0.0

    @Published var fingerOn = false
    @Published var progress = 0.0
    @Published var live: Int?
    @Published var wave: [Double] = []

    enum Outcome {
        case done(PulseEstimate?, [Double])
        case gaveUp
    }

    private func resetSegment() {
        segmentStart = -1
        times.removeAll()
        values.removeAll()
        progress = 0
        live = nil
        wave = []
    }

    func onFrame(_ frame: FrameStats) -> Outcome? {
        if firstFrame < 0 { firstFrame = frame.timeSeconds }
        frames += 1
        let t = frame.timeSeconds
        let on = PulseMath.fingerOn(frame)
        if on {
            lastFinger = t
            if segmentStart < 0 { segmentStart = t }
        } else if segmentStart >= 0 && t - lastFinger > fingerGraceSeconds {
            resetSegment()
        }
        if fingerOn != (segmentStart >= 0) { fingerOn = segmentStart >= 0 }
        if on && segmentStart >= 0 && t - segmentStart >= settleSeconds {
            times.append(t)
            values.append(frame.luma)
        }
        let measured = times.count > 1 ? (times.last ?? 0) - (times.first ?? 0) : 0
        progress = min(max(measured / measureSeconds, 0), 1)
        if times.count > 30 && frames % 2 == 0 { wave = PulseMath.display(times, values) }
        // رقم مبدئي بعد 10 ثوانٍ، فقط إن اجتاز فحص الجودة
        if measured >= 10 && t - lastLive >= 1 {
            lastLive = t
            if let estimate = PulseMath.estimate(times, values) { live = estimate.bpm }
        }
        if measured >= measureSeconds { return .done(PulseMath.estimate(times, values), wave) }
        if t - firstFrame > giveUpSeconds { return .gaveUp }
        return nil
    }
}

private enum Stage { case intro, measuring, result, failed }

/**
 قياس النبض بالكاميرا الخلفية والفلاش (PPG). 30 ثانية بعد استقرار الإشارة.
 إن كانت الإشارة ضعيفة لا يُعرض رقم، ويُطلب إعادة القياس. الفلاش يُطفأ عند الانتهاء أو الخروج أو سخونة الجوال.
 */
struct PulseScreen: View {
    let onClose: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var stage: Stage = .intro
    @State private var failure: Failure?
    @State private var result: PulseEstimate?
    @State private var resultWave: [Double] = []
    @State private var saving = false
    @State private var attempt = 0

    private func fail(_ value: Failure) {
        failure = value
        stage = .failed
    }

    private func begin() {
        if ProcessInfo.processInfo.thermalState == .serious || ProcessInfo.processInfo.thermalState == .critical {
            fail(Failure(title: tr("الجوال ساخن الآن", "Your phone is hot right now"), body: tr("انتظر بضع دقائق حتى يبرد، ثم أعد القياس.", "Wait a few minutes for it to cool down, then measure again.")))
        } else {
            attempt += 1
            stage = .measuring
        }
    }

    private func start() {
        if PulseCamera.authorized { return begin() }
        if PulseCamera.undetermined {
            Task {
                if await PulseCamera.requestAccess() { begin() } else { denied() }
            }
            return
        }
        denied()
    }

    private func denied() {
        fail(Failure(title: tr("نحتاج إذن الكاميرا", "Camera permission needed"),
                     body: tr("القياس يستخدم الكاميرا الخلفية والفلاش فقط، ولا تُحفظ أي صورة. اسمح بالكاميرا من إعدادات الجوال.",
                              "The measurement uses only the back camera and flash, and no photo is saved. Allow the camera in your phone settings."),
                     openSettings: true))
    }

    private func save(_ estimate: PulseEstimate) {
        guard let patientId = model.patient?.id, !saving else { return }
        saving = true
        do {
            try HeartRateRepository.save(patientId, bpm: estimate.bpm, quality: estimate.quality)
            model.vitalsVersion += 1
            model.toast = tr("حُفظ القياس", "Measurement saved")
            stage = .intro
        } catch {
            model.toast = tr("تعذّر حفظ القياس", "Couldn't save the measurement")
        }
        saving = false
    }

    var body: some View {
        FullScreen {
            ScreenHeader(title: tr("قياس النبض", "Heart rate"), onClose: onClose)
            ScrollView {
                VStack(spacing: 14) {
                    switch stage {
                    case .intro:
                        Intro(onStart: start)
                    case .measuring:
                        Measuring(
                            onDone: { estimate, wave in
                                UINotificationFeedbackGenerator().notificationOccurred(.success)
                                if let estimate = estimate {
                                    result = estimate
                                    resultWave = wave
                                    stage = .result
                                } else {
                                    fail(Failure(title: tr("لم تتضح إشارة النبض", "The pulse signal wasn't clear"),
                                                 body: tr("لن نعرض رقمًا غير دقيق. جرّب مرة أخرى بهذه الطريقة:", "We won't show an inaccurate number. Try again like this:"), showTips: true))
                                }
                            },
                            onFail: fail,
                            onStop: { stage = .intro }
                        )
                        .id(attempt)
                    case .result:
                        if let estimate = result {
                            ResultView(estimate: estimate, wave: resultWave, saving: saving, onSave: { save(estimate) }, onAgain: start)
                        }
                    case .failed:
                        if let failure = failure {
                            FailureView(failure: failure, onAgain: start, onSettings: { Links.appSettings() })
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 12)
            }
        }
    }
}

private struct PulseCard<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 10, content: content)
            .frame(maxWidth: .infinity)
            .padding(20)
            .glass(radius: 26)
    }
}

private struct Intro: View {
    let onStart: () -> Void
    @EnvironmentObject private var model: AppModel

    var body: some View {
        PulseCard {
            FingerIllustration().frame(width: 170, height: 170)
            Text(tr("ضع إصبعك على الكاميرا", "Place your finger on the camera")).style(26, .bold).multilineTextAlignment(.center)
            Text(tr("برفق، وأبقِ يدك ثابتة", "Gently, and keep your hand still")).style(18, .regular, Brand.secondaryText).multilineTextAlignment(.center)
            PrimaryButton(tr("ابدأ القياس", "Start measuring"), icon: "heart.fill", action: onStart).padding(.top, 4)
        }
        Tips()
        PulseHistory()
        Text(tr("قراءة تقديرية وليست تشخيصًا طبيًا. يتم القياس داخل جوالك، ولا تُحفظ ولا تُرسل أي صورة.", "An estimate, not a medical diagnosis. It's measured on your phone; no photo is saved or sent."))
            .style(14, .regular, Brand.secondaryText).multilineTextAlignment(.center)
    }
}

private struct Tips: View {
    var body: some View {
        PulseCard {
            Text(tr("لقياس أدق", "For a more accurate reading")).style(19, .bold).frame(maxWidth: .infinity, alignment: .leading)
            ForEach([
                tr("اجلس وارتح دقيقة قبل القياس.", "Sit and rest for a minute first."),
                tr("غطِّ الكاميرا الخلفية والفلاش معًا بطرف إصبعك.", "Cover both the back camera and the flash with your fingertip."),
                tr("لا تضغط بقوة، ولا تحرك يدك أو تتكلم حتى ينتهي.", "Don't press hard, and don't move or talk until it finishes."),
            ], id: \.self) { tip in
                HStack(alignment: .top, spacing: 10) {
                    Circle().fill(Brand.glow).frame(width: 7, height: 7).padding(.top, 10)
                    Text(tip).style(17, .regular, Brand.secondaryText).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }
}

private struct PulseHistory: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        let readings = model.patient.map { HeartRateRepository.recent($0.id, limit: 5) } ?? []
        if !readings.isEmpty {
            PulseCard {
                Text(tr("قياساتك السابقة", "Your previous readings")).style(19, .bold).frame(maxWidth: .infinity, alignment: .leading)
                ForEach(readings) { reading in
                    HStack(spacing: 8) {
                        Text("\(reading.bpm)").style(26, .bold, Brand.glow)
                        Text(tr("نبضة", "bpm")).style(16, .regular, Brand.secondaryText)
                        Spacer()
                        Text(Format.readingTime(reading.measuredAt)).style(15, .regular, Brand.secondaryText)
                    }
                }
            }
            .id(model.vitalsVersion)
        }
    }
}

private struct Measuring: View {
    let onDone: (PulseEstimate?, [Double]) -> Void
    let onFail: (Failure) -> Void
    let onStop: () -> Void
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var session = PulseSession()
    @State private var camera = PulseCamera()
    @State private var finished = false

    private func finish(_ action: () -> Void) {
        guard !finished else { return }
        finished = true
        camera.stop()
        action()
    }

    var body: some View {
        let remaining = max(Int((1 - session.progress) * measureSeconds), 0)
        PulseCard {
            ProgressHeart(progress: session.progress, active: session.fingerOn)
            if !session.fingerOn {
                Text(tr("ضع إصبعك على الكاميرا", "Place your finger on the camera")).style(24, .bold).multilineTextAlignment(.center)
                Text(tr("غطِّ الكاميرا الخلفية والفلاش برفق بطرف إصبعك", "Gently cover the back camera and flash with your fingertip")).style(17, .regular, Brand.warning).multilineTextAlignment(.center)
            } else {
                Text(session.progress > 0 ? tr("باقي \(remaining) ثانية", "\(remaining) seconds left") : tr("جارٍ ضبط الإشارة…", "Adjusting the signal…")).style(24, .bold).multilineTextAlignment(.center)
                Text(tr("ممتاز، أبقِ إصبعك ثابتًا ولا تتكلم", "Great, keep your finger still and don't talk")).style(17, .regular, Brand.mint).multilineTextAlignment(.center)
            }
            SignalWave(points: session.wave).frame(height: 110)
            Text(session.live.map { tr("تقريبًا \($0) نبضة في الدقيقة", "About \($0) beats per minute") } ?? " ").style(17, .regular, Brand.secondaryText)
        }
        GlassButton(tr("إيقاف القياس", "Stop measuring"), icon: "stop.fill", color: Brand.text) { finish(onStop) }
            .onAppear {
                // الشاشة تبقى مضاءة أثناء القياس
                UIApplication.shared.isIdleTimerDisabled = true
                camera.onFrame = { frame in
                    guard !finished else { return }
                    switch session.onFrame(frame) {
                    case .done(let estimate, let wave): finish { onDone(estimate, wave) }
                    case .gaveUp:
                        finish {
                            onFail(Failure(title: tr("لم نستطع إكمال القياس", "Couldn't finish the measurement"),
                                           body: tr("لم يبقَ الإصبع على الكاميرا مدة كافية. جرّب مرة أخرى:", "Your finger didn't stay on the camera long enough. Try again:"), showTips: true))
                        }
                    case nil: break
                    }
                }
                camera.onReady = { hasFlash in
                    if !hasFlash {
                        finish {
                            onFail(Failure(title: tr("جوالك بلا فلاش خلفي", "Your phone has no rear flash"),
                                           body: tr("القياس يحتاج ضوء الفلاش ليمر خلال الإصبع، لذلك لا يعمل على هذا الجوال.", "The measurement needs the flash light to shine through your finger, so it doesn't work on this phone.")))
                        }
                    }
                }
                camera.onError = { message in finish { onFail(Failure(title: tr("تعذّر تشغيل الكاميرا", "Couldn't start the camera"), body: message)) } }
                camera.start()
            }
            // الكاميرا والفلاش: يُغلقان حين تغادر هذه الحالة، مهما كان السبب
            .onDisappear {
                camera.stop()
                UIApplication.shared.isIdleTimerDisabled = false
            }
            // الخروج من التطبيق: يتوقف القياس ويُطفأ الفلاش
            .onChange(of: scenePhase) { phase in
                if phase != .active {
                    finish {
                        onFail(Failure(title: tr("توقف القياس", "Measurement stopped"),
                                       body: tr("خرجت من التطبيق أثناء القياس، فأطفأنا الفلاش. ابدأ من جديد.", "You left the app during the measurement, so we turned off the flash. Start again.")))
                    }
                }
            }
            // سخونة الجوال: يتوقف القياس ويُطفأ الفلاش
            .onReceive(NotificationCenter.default.publisher(for: ProcessInfo.thermalStateDidChangeNotification)) { _ in
                let state = ProcessInfo.processInfo.thermalState
                if state == .serious || state == .critical {
                    finish {
                        onFail(Failure(title: tr("توقف القياس لأن الجوال ساخن", "Stopped because the phone is hot"),
                                       body: tr("أطفأنا الفلاش. انتظر بضع دقائق حتى يبرد، ثم أعد القياس.", "We turned off the flash. Wait a few minutes for it to cool down, then measure again.")))
                    }
                }
            }
    }
}

private struct ProgressHeart: View {
    let progress: Double
    let active: Bool
    @State private var beat = false

    var body: some View {
        ZStack {
            Circle().stroke(Brand.glow.opacity(0.15), lineWidth: 12)
            Circle().trim(from: 0, to: progress)
                .stroke(Brand.glow, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.2), value: progress)
            Image(systemName: "heart.fill")
                .font(.system(size: 58))
                .foregroundColor(active ? Brand.danger : Brand.secondaryText)
                .scaleEffect(active && beat ? 1.12 : 1)
        }
        .frame(width: 170, height: 170)
        .padding(.vertical, 6)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.42).repeatForever(autoreverses: true)) { beat = true }
        }
    }
}

/// موجة النبض: خط مضيء، مسطح حتى تصل الإشارة
private struct SignalWave: View {
    let points: [Double]

    var body: some View {
        Canvas { context, size in
            let middle = size.height / 2
            var path = Path()
            if points.count < 2 {
                path.move(to: CGPoint(x: 0, y: middle))
                path.addLine(to: CGPoint(x: size.width, y: middle))
                context.stroke(path, with: .color(Brand.glow.opacity(0.4)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                return
            }
            for (index, value) in points.enumerated() {
                let point = CGPoint(x: size.width * CGFloat(index) / CGFloat(points.count - 1), y: middle - CGFloat(value) * middle * 0.85)
                if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
            }
            context.stroke(path, with: .color(Brand.glow), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
        }
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }
}

private struct ResultView: View {
    let estimate: PulseEstimate
    let wave: [Double]
    let saving: Bool
    let onSave: () -> Void
    let onAgain: () -> Void

    var body: some View {
        PulseCard {
            Image(systemName: "waveform.path.ecg").font(.system(size: 40, weight: .semibold)).foregroundColor(Brand.glow)
            Text(tr("نبض القلب", "Heart rate")).style(19, .regular, Brand.secondaryText)
            Text("\(estimate.bpm)").font(.custom(Tajawal.name(.bold), size: 84)).foregroundColor(Brand.text)
            Text(tr("نبضة في الدقيقة", "beats per minute")).style(19, .regular, Brand.secondaryText)
            SignalWave(points: wave).frame(height: 110)
            if !restingRange.contains(estimate.bpm) {
                Text(tr("القراءة خارج المعتاد أثناء الراحة. اجلس خمس دقائق ثم أعد القياس، وإن تكررت أو شعرت بتعب أو دوخة فاستشر طبيبك.",
                        "This reading is outside the usual resting range. Sit for five minutes and measure again; if it repeats or you feel tired or dizzy, consult your doctor."))
                    .style(16, .regular, Brand.warning).multilineTextAlignment(.center)
            }
            PrimaryButton(saving ? tr("جارٍ الحفظ…", "Saving…") : tr("حفظ القياس", "Save measurement"), icon: "square.and.arrow.down", enabled: !saving, action: onSave)
            GlassButton(tr("قِس مرة أخرى", "Measure again"), icon: "arrow.clockwise", action: onAgain)
            Text(tr("قراءة تقديرية وليست تشخيصًا طبيًا", "An estimate, not a medical diagnosis")).style(14, .regular, Brand.secondaryText).multilineTextAlignment(.center)
        }
    }
}

private struct FailureView: View {
    let failure: Failure
    let onAgain: () -> Void
    let onSettings: () -> Void

    var body: some View {
        PulseCard {
            Image(systemName: "waveform.path.ecg").font(.system(size: 46, weight: .semibold)).foregroundColor(Brand.warning)
            Text(failure.title).style(24, .bold).multilineTextAlignment(.center)
            Text(failure.body).style(17, .regular, Brand.secondaryText).multilineTextAlignment(.center)
            if failure.openSettings {
                PrimaryButton(tr("فتح الإعدادات", "Open settings"), action: onSettings)
            } else {
                PrimaryButton(tr("أعد القياس", "Measure again"), icon: "arrow.clockwise", action: onAgain)
            }
        }
        if failure.showTips { Tips() }
    }
}

/// رسم توضيحي: طرف إصبع يغطي الكاميرا والفلاش في ظهر الجوال
private struct FingerIllustration: View {
    var body: some View {
        let outline = Brand.glow
        Canvas { context, size in
            let w = size.width, h = size.height
            let phone = CGRect(x: w * 0.2, y: h * 0.04, width: w * 0.5, height: h * 0.88)
            let shell = Path(roundedRect: phone, cornerRadius: w * 0.08)
            context.fill(shell, with: .color(outline.opacity(0.12)))
            context.stroke(shell, with: .color(outline), lineWidth: 3)
            // الكاميرا والفلاش أعلى ظهر الجوال
            let lens = CGPoint(x: phone.minX + w * 0.12, y: phone.minY + h * 0.12)
            let flash = CGPoint(x: lens.x + w * 0.12, y: lens.y)
            // الإصبع من أسفل اليمين حتى يغطي العدسة والفلاش
            let tip = CGPoint(x: lens.x + w * 0.07, y: lens.y + h * 0.015)
            let base = CGPoint(x: w * 0.98, y: h * 0.8)
            var finger = Path()
            finger.move(to: tip)
            finger.addLine(to: base)
            context.stroke(finger, with: .color(Color(hex: 0xC98F6A)), style: StrokeStyle(lineWidth: w * 0.21, lineCap: .round))
            context.stroke(finger, with: .color(Color(hex: 0xE9B994)), style: StrokeStyle(lineWidth: w * 0.18, lineCap: .round))
            // الظفر على ظهر الإصبع
            let dx = base.x - tip.x, dy = base.y - tip.y
            let length = max(sqrt(dx * dx + dy * dy), 1)
            let nail = CGPoint(x: tip.x + dx / length * w * 0.09, y: tip.y + dy / length * w * 0.09)
            let nailRadius = w * 0.045
            context.fill(Path(ellipseIn: CGRect(x: nail.x - nailRadius, y: nail.y - nailRadius, width: nailRadius * 2, height: nailRadius * 2)), with: .color(Color(hex: 0xF6D9C4)))
            // العدسة والفلاش يظهران من تحت الإصبع، والدائرة المتقطعة تحدد مكانه
            func circle(_ center: CGPoint, _ radius: CGFloat) -> Path {
                Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
            }
            context.fill(circle(lens, w * 0.05), with: .color(Color(hex: 0x0D2233, alpha: 0.55)))
            context.fill(circle(flash, w * 0.025), with: .color(Color(hex: 0xFFE08A, alpha: 0.8)))
            context.stroke(circle(CGPoint(x: (lens.x + flash.x) / 2, y: lens.y), w * 0.13), with: .color(outline),
                           style: StrokeStyle(lineWidth: 2.5, dash: [10, 8]))
        }
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }
}
