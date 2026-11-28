import AVFoundation
import SwiftUI

/**
 تنبيه موعد الدواء داخل التطبيق: بطاقة الجرعة تومض من موعدها حتى يسجّلها، وتتوقف بعد 15 دقيقة.
 ومعها نغمة تتكرر كل ثوانٍ (تُطفأ من الإعدادات). الإشعار المعتاد يبقى كما هو.
 */
@MainActor
enum DoseAlert {
    static let ringMinutes = 15.0
    static let soundKey = "alert.sound"
    private static let soundEvery: TimeInterval = 7
    private static var player: AVAudioPlayer?
    private static var lastPlayed = Date.distantPast

    static var soundOn: Bool { UserDefaults.standard.object(forKey: soundKey) as? Bool ?? true }

    /// الجرعة حان موعدها ولم تُسجَّل، وما زلنا في أول 15 دقيقة
    nonisolated static func isRinging(_ log: DoseLog, now: Date, taken: Bool) -> Bool {
        if taken || log.status != DoseStatus.pending { return false }
        guard let at = Iso.parse(log.scheduledAt) else { return false }
        let seconds = now.timeIntervalSince(at)
        return seconds >= 0 && seconds < 15 * 60
    }

    /// نغمة قصيرة تحترم زر الوضع الصامت
    static func play() {
        guard Date().timeIntervalSince(lastPlayed) >= soundEvery else { return }
        lastPlayed = Date()
        guard let url = Bundle.main.url(forResource: "dose-alert", withExtension: "wav") else { return }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        player = try? AVAudioPlayer(contentsOf: url)
        player?.play()
    }
}

/// وميض التنبيه حول بطاقة الجرعة: إطار وخلفية يضيئان ويخفتان
struct RingFlash: ViewModifier {
    let active: Bool
    @State private var on = false

    func body(content: Content) -> some View {
        if active {
            content
                .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Brand.warning.opacity(on ? 0.22 : 0.03)))
                .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Brand.warning.opacity(on ? 1 : 0.15), lineWidth: 2.5))
                .onAppear {
                    withAnimation(.easeInOut(duration: 0.55).repeatForever(autoreverses: true)) { on = true }
                }
        } else {
            content
        }
    }
}

/// هلال (الهلال الأحمر) لزر الاتصال بالإسعاف
struct CrescentShape: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / 24
        let origin = CGPoint(x: rect.midX - 12 * scale, y: rect.midY - 12 * scale)
        func point(_ cx: Double, _ r: Double, _ degrees: Double) -> CGPoint {
            let a = degrees * .pi / 180
            return CGPoint(x: origin.x + (cx + r * cos(a)) * scale, y: origin.y + (12 + r * sin(a)) * scale)
        }
        var path = Path()
        // القوس الخارجي من الطرف العلوي إلى السفلي مرورًا باليسار، ثم الداخلي عائدًا
        let steps = 48
        for i in 0...steps {
            let p = point(11, 9, -45.05 - 269.9 * Double(i) / Double(steps))
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        for i in 0...steps {
            path.addLine(to: point(14, 7.2, 62.2 + 235.6 * Double(i) / Double(steps)))
        }
        path.closeSubpath()
        return path
    }
}

/// أيقونة زر: رمز من النظام، أو «crescent» للهلال
struct AppIcon: View {
    let name: String
    var size: CGFloat = 20
    var weight: Font.Weight = .medium

    var body: some View {
        if name == "crescent" {
            CrescentShape().frame(width: size * 1.2, height: size * 1.2)
        } else {
            Image(systemName: name).font(.system(size: size, weight: weight))
        }
    }
}
