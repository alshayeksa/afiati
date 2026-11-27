import SwiftUI
import UIKit

// MARK: - المظهر

/// لون التطبيق. الأزرق المحيطي يطابق شعار عافيتي، والبقية تُشتق من الألوان الأصلية بتدوير درجة اللون.
enum ThemeAccent: String, CaseIterable, Identifiable {
    case ocean, emerald, gold, violet, coral, platinum

    var id: String { rawValue }

    var title: String {
        switch self {
        case .ocean: return tr("الأزرق المحيطي", "Ocean blue")
        case .emerald: return tr("الزمرّدي", "Emerald")
        case .gold: return tr("الذهبي الملكي", "Royal gold")
        case .violet: return tr("البنفسجي الليلي", "Midnight violet")
        case .coral: return tr("المرجاني", "Coral")
        case .platinum: return tr("البلاتيني", "Platinum")
        }
    }

    var hue: Double? {
        switch self {
        case .ocean: return 200
        case .emerald: return nil
        case .gold: return 43
        case .violet: return 268
        case .coral: return 352
        case .platinum: return 210
        }
    }

    var sat: Double {
        switch self {
        case .gold, .coral: return 0.95
        case .violet: return 0.9
        case .platinum: return 0.12
        default: return 1
        }
    }

    var lift: Double { self == .platinum ? 0.05 : 0 }
}

/// وضع المظهر: داكن (الأصل)، فاتح، أو يتبع إعداد الجوال
enum ThemeMode: String, CaseIterable, Identifiable {
    case dark, light, system

    var id: String { rawValue }

    var title: String {
        switch self {
        case .dark: return tr("داكن", "Dark")
        case .light: return tr("فاتح", "Light")
        case .system: return tr("تلقائي", "Auto")
        }
    }
}

/// المظهر المحفوظ في الجوال. تغييره يعيد بناء الواجهة (AppModel.themeVersion).
enum Appearance {
    static private(set) var accent: ThemeAccent = ThemeAccent(rawValue: UserDefaults.standard.string(forKey: "appearance.accent") ?? "") ?? .ocean
    static private(set) var mode: ThemeMode = ThemeMode(rawValue: UserDefaults.standard.string(forKey: "appearance.mode") ?? "") ?? .dark
    /// تكبير الخط لمن يحتاج
    static private(set) var largeText: Bool = UserDefaults.standard.bool(forKey: "appearance.largeText")
    static var systemDark: Bool = UITraitCollection.current.userInterfaceStyle != .light

    static var light: Bool { mode == .light || (mode == .system && !systemDark) }

    static func apply(accent: ThemeAccent, mode: ThemeMode) {
        self.accent = accent
        self.mode = mode
        UserDefaults.standard.set(accent.rawValue, forKey: "appearance.accent")
        UserDefaults.standard.set(mode.rawValue, forKey: "appearance.mode")
    }

    static func setLargeText(_ value: Bool) {
        largeText = value
        UserDefaults.standard.set(value, forKey: "appearance.largeText")
    }

    static func tone(_ rgb: Int) -> Color { Palette(accent: accent, light: light).color(rgb) }
}

/**
 يحوّل الألوان الأصلية (الزمرّدي الداكن) إلى السمة المختارة:
 يدوّر الأخضر إلى لون السمة، وفي الفاتح يقلب الخلفيات الداكنة إلى لؤلؤي ويغمّق الألوان المضيئة.
 */
struct Palette {
    let accent: ThemeAccent
    let light: Bool

    private static var cache: [String: Color] = [:]

    func color(_ rgb: Int) -> Color {
        let key = "\(accent.rawValue)|\(light)|\(rgb)"
        if let cached = Palette.cache[key] { return cached }
        let r = Double((rgb >> 16) & 0xFF) / 255, g = Double((rgb >> 8) & 0xFF) / 255, b = Double(rgb & 0xFF) / 255
        var (h, s, l) = Palette.hsl(r, g, b)
        if let hue = accent.hue, s >= 0.08, (110.0...185.0).contains(h) {
            h = hue + (h - 155) * 0.4
            s = min(1, s * accent.sat)
            l = min(0.97, l + (l > 0.5 ? accent.lift : 0))
        }
        if light {
            let baseHue = accent.hue ?? 155
            let platinum = accent == .platinum
            if s < 0.05 && l > 0.95 {
                h = baseHue; s = 0.35 * accent.sat; l = 0.13
            } else if l < 0.2 {
                if s < 0.01 { h = baseHue }
                s = min(s, 0.4) * accent.sat
                l = 0.965 - l * 0.7
            } else if l > 0.55 && s > (platinum ? 0.03 : 0.3) {
                l *= platinum ? 0.5 : 0.62
            }
        }
        let (red, green, blue) = Palette.rgb(h, s, l)
        let color = Color(red: red, green: green, blue: blue)
        Palette.cache[key] = color
        return color
    }

    private static func hsl(_ r: Double, _ g: Double, _ b: Double) -> (Double, Double, Double) {
        let maxV = max(r, g, b), minV = min(r, g, b)
        let l = (maxV + minV) / 2
        if maxV == minV { return (0, 0, l) }
        let d = maxV - minV
        let s = l > 0.5 ? d / (2 - maxV - minV) : d / (maxV + minV)
        var h: Double
        if maxV == r { h = (g - b) / d + (g < b ? 6 : 0) } else if maxV == g { h = (b - r) / d + 2 } else { h = (r - g) / d + 4 }
        h *= 60
        return (h, s, l)
    }

    private static func rgb(_ h: Double, _ s: Double, _ l: Double) -> (Double, Double, Double) {
        if s <= 0 { return (l, l, l) }
        let hue = (h.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / 360
        let q = l < 0.5 ? l * (1 + s) : l + s - l * s
        let p = 2 * l - q
        func f(_ t0: Double) -> Double {
            var t = t0
            if t < 0 { t += 1 }
            if t > 1 { t -= 1 }
            if t < 1.0 / 6 { return p + (q - p) * 6 * t }
            if t < 1.0 / 2 { return q }
            if t < 2.0 / 3 { return p + (q - p) * (2.0 / 3 - t) * 6 }
            return p
        }
        return (f(hue + 1.0 / 3), f(hue), f(hue - 1.0 / 3))
    }
}

/// ألوان العلامة، بأسلوب «موجة»: خلفية شبه سوداء، أسطح زجاجية، ولون مضيء.
enum Brand {
    static var green: Color { Appearance.tone(0x13684F) }
    static var deepGreen: Color { Appearance.tone(0x0B4F3D) }
    static var mint: Color { Appearance.tone(0x22B085) }
    static var glow: Color { Appearance.tone(0x4FF0B0) }
    static var ink: Color { Appearance.tone(0x04241A) }
    static var text: Color { Appearance.tone(0xFFFFFF) }
    static var secondaryText: Color { text.opacity(0.62) }
    static var base: Color { Appearance.tone(0x020504) }
    static var sheet: Color { Appearance.tone(0x07110D) }
    static var menu: Color { Appearance.tone(0x0E1A16) }
    static let success = Color(hex: 0x2FBF71)
    static let danger = Color(hex: 0xE5484D)
    static let warning = Color(hex: 0xFFA62B)

    private static let palette: [Color] = [
        Color(hex: 0x1E8E6C), Color(hex: 0xB07A3F), Color(hex: 0x4D7FB5),
        Color(hex: 0x5B4B8A), Color(hex: 0xBD616E), Color(hex: 0x3F94A0),
    ]

    /// لون ثابت لكل دواء أو شخص حسب معرّفه
    static func avatarColor(_ id: String) -> Color {
        let sum = id.unicodeScalars.reduce(0) { $0 + Int($1.value) }
        return palette[sum % palette.count]
    }
}

extension Color {
    init(hex: Int, alpha: Double = 1) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }
}

// MARK: - الخط

/// خط تجوال (ترخيص OFL). الأحجام أكبر من المعتاد لتناسب كبار السن، وتكبر أكثر مع «الخط الكبير».
enum Tajawal {
    static func name(_ weight: Font.Weight) -> String {
        switch weight {
        case .ultraLight, .thin, .light: return "Tajawal-Light"
        case .medium, .semibold: return "Tajawal-Medium"
        case .bold, .heavy, .black: return "Tajawal-Bold"
        default: return "Tajawal-Regular"
        }
    }

    /// الحجم بعد التهذيب: العناوين الكبيرة (23 إلى 40) أهدأ قليلًا، والأرقام الضخمة كما هي
    static func size(_ size: CGFloat) -> CGFloat {
        let base = (23...40).contains(size) ? size * 0.87 : size
        return Appearance.largeText ? base * 1.15 : base
    }
}

func tj(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
    .custom(Tajawal.name(weight), size: Tajawal.size(size))
}

extension View {
    /// نص بخط تجوال ولون ومسافة مريحة بين السطور
    func style(_ size: CGFloat, _ weight: Font.Weight = .regular, _ color: Color? = nil) -> some View {
        font(tj(size, weight))
            .foregroundColor(color ?? Brand.text)
            .lineSpacing(Tajawal.size(size) * 0.28)
    }
}

// MARK: - الأسطح

/// خلفية التطبيق: أسود بلون السمة (أو لؤلؤي في الفاتح) مع إضاءة خفيفة من الأعلى
struct AppBackground: View {
    var body: some View {
        ZStack {
            Brand.base
            RadialGradient(colors: [Appearance.tone(0x061610), .clear], center: .top, startRadius: 0, endRadius: 520)
        }
        .ignoresSafeArea()
    }
}

extension View {
    /// سطح زجاجي: تعبئة شفافة وحد مضيء خافت
    func glass<S: InsettableShape>(_ shape: S, highlight: Double = 0.22) -> some View {
        background(shape.fill(Brand.text.opacity(0.055)))
            .overlay(shape.strokeBorder(Brand.glow.opacity(highlight), lineWidth: 1))
            .clipShape(shape)
    }

    func glass(radius: CGFloat, highlight: Double = 0.22) -> some View {
        glass(RoundedRectangle(cornerRadius: radius, style: .continuous), highlight: highlight)
    }

    /// إطار ملون شفاف (تنبيه، مخزون)
    func tinted(_ color: Color, radius: CGFloat = 22, fill: Double = 0.12, stroke: Double = 0.45) -> some View {
        background(RoundedRectangle(cornerRadius: radius, style: .continuous).fill(color.opacity(fill)))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(color.opacity(stroke), lineWidth: 1))
    }
}

/// ضغطة خفيفة: يبهت الزر أثناء الضغط
struct PressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// الزر الرئيسي: مضيء بنص داكن، كبير وسهل اللمس
struct PrimaryButton: View {
    let title: String
    var icon: String?
    var enabled = true
    let action: () -> Void

    init(_ title: String, icon: String? = nil, enabled: Bool = true, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.enabled = enabled
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let icon = icon { Image(systemName: icon).font(.system(size: 20, weight: .semibold)) }
                Text(title).style(19, .bold, Brand.ink).multilineTextAlignment(.center)
            }
            .foregroundColor(Brand.ink)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.vertical, 17)
            .background(LinearGradient(colors: [Brand.glow, Brand.mint], startPoint: .top, endPoint: .bottom),
                        in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: Brand.glow.opacity(enabled ? 0.35 : 0), radius: 12, y: 2)
            .opacity(enabled ? 1 : 0.45)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .disabled(!enabled)
    }
}

/// زر ثانوي زجاجي بنص مضيء
struct GlassButton: View {
    let title: String
    var icon: String?
    var color: Color?
    var enabled = true
    let action: () -> Void

    init(_ title: String, icon: String? = nil, color: Color? = nil, enabled: Bool = true, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.color = color
        self.enabled = enabled
        self.action = action
    }

    var body: some View {
        let tint = color ?? Brand.glow
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon = icon { Image(systemName: icon).font(.system(size: 19, weight: .medium)) }
                Text(title).style(18, .medium, tint).multilineTextAlignment(.center)
            }
            .foregroundColor(tint)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 14)
            .padding(.vertical, 16)
            .glass(radius: 20)
            .opacity(enabled ? 1 : 0.45)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .disabled(!enabled)
    }
}

// MARK: - الموجات

/// موجة العلامة: خطوط رفيعة مضيئة تلتف حول بعضها وتتحرك بهدوء، كنبض هادئ
struct WaveRibbon: View {
    var animated = true

    var body: some View {
        let stops = WaveRibbon.stops(Palette(accent: Appearance.accent, light: Appearance.light))
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !animated)) { timeline in
            Canvas { context, size in
                let phase = animated ? timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 10_000) * 0.22 : 0
                WaveRibbon.draw(in: &context, size: size, phase: phase, stops: stops)
            }
        }
        .accessibilityHidden(true)
    }

    static func stops(_ palette: Palette) -> [Gradient.Stop] {
        [(0x1FD38E, 0.0), (0x7BFFC9, 0.42), (0xF2FFF9, 0.55), (0x5BF0B4, 0.68), (0x17B57A, 1.0)]
            .map { Gradient.Stop(color: palette.color($0.0), location: $0.1) }
    }

    static func draw(in context: inout GraphicsContext, size: CGSize, phase: Double, stops: [Gradient.Stop]) {
        let width = Double(size.width), height = Double(size.height)
        guard width > 0 else { return }
        // من اليمين إلى اليسار، مع اتجاه القراءة العربية
        let shading = GraphicsContext.Shading.linearGradient(Gradient(stops: stops), startPoint: CGPoint(x: width, y: 0), endPoint: .zero)
        let lines: [Path] = (0..<30).map { index in
            let k = Double(index) / 29 - 0.5
            var path = Path()
            var x = -10.0
            var first = true
            while x <= width + 10 {
                let t = (width - x) / width
                let center = height * 0.50 + height * 0.26 * sin(t * 2 * .pi * 0.95 + 0.4 + phase) - height * 0.06 * t
                let spread = height * 0.30 * sin(t * 2 * .pi * 0.72 + 1.15 + phase * 0.6) * (0.75 + 0.25 * cos(t * 5))
                let point = CGPoint(x: x, y: center + k * spread + k * k * 14)
                if first { path.move(to: point); first = false } else { path.addLine(to: point) }
                x += 6
            }
            return path
        }
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 7))
            layer.opacity = 0.16
            for path in lines { layer.stroke(path, with: shading, lineWidth: 1.8) }
        }
        context.drawLayer { layer in
            layer.opacity = 0.6
            for path in lines { layer.stroke(path, with: shading, lineWidth: 0.6) }
        }
    }
}

/**
 موجة النبض: خطوط رفيعة مضيئة تنساب كالموجة، ثم تجتمع في نبضتي قلب (تخطيط القلب).
 كل دورة تبدأ بتوهج في الطرف الأيمن، ورأس مضيء يرسم الخط نحو اليسار تاركًا أثرًا يخبو،
 ثم تنتهي بتوهج في الطرف الأيسر. الألوان تتبدل بهدوء بين الأزرق والسماوي والبنفسجي.
 */
struct PulseWave: View {
    var animated = true

    var body: some View {
        let light = Appearance.light
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !animated)) { timeline in
            Canvas { context, size in
                let time = timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 3600)
                context.drawLayer { layer in
                    layer.addFilter(.blur(radius: 9))
                    PulseWave.drawLines(in: &layer, size: size, time: time, light: light, lineWidth: 2.6, alpha: 0.55, lines: 7)
                }
                // القلب في الوسط: توهجه وتعبئته الشفافة خلف الخطوط، فتظهر الموجة تمر من خلاله، ثم حدّه فوقها
                PulseWave.drawHeart(in: &context, size: size, time: time, light: light, front: false)
                PulseWave.drawLines(in: &context, size: size, time: time, light: light, lineWidth: 0.8, alpha: 1, lines: 16)
                PulseWave.drawHeart(in: &context, size: size, time: time, light: light, front: true)
                PulseWave.drawGlow(in: &context, size: size, time: time, light: light)
            }
        }
        .accessibilityHidden(true)
    }

    /// مدة رسم النبضة من طرف إلى طرف
    private static let sweepSeconds = 3.6
    /// موضعا النبضتين على عرض الموجة (0 يمين، 1 يسار)
    private static let beats: [Double] = [0.30, 0.70]

    private static func gauss(_ x: Double, _ sigma: Double) -> Double { exp(-(x * x) / (2 * sigma * sigma)) }

    /// شكل نبضة واحدة: P صغيرة، Q هبوط، R قمة حادة، S هبوط، T موجة هادئة
    private static func beatShape(_ u: Double) -> Double {
        0.10 * gauss(u + 0.55, 0.09) - 0.16 * gauss(u + 0.13, 0.045) + 1 * gauss(u, 0.05) -
            0.38 * gauss(u - 0.13, 0.05) + 0.20 * gauss(u - 0.52, 0.12)
    }

    /// ارتفاع الخط الأوسط عند t: موجة هادئة + النبضتان
    private static func center(_ t: Double, _ time: Double, _ height: Double) -> Double {
        // خفقة كل ثانية: القمة ترتفع ثم تهدأ
        let beat = 0.82 + 0.18 * exp(-(time.truncatingRemainder(dividingBy: 1) * 5))
        var y = height * 0.58 + height * 0.11 * sin(t * 2 * .pi * 1.3 + time * 0.5)
        for position in beats { y -= height * 0.40 * beat * beatShape((t - position) / 0.16) }
        return y
    }

    /// لون متبدل: يتنقل بهدوء بين الأزرق والسماوي والبنفسجي مع الزمن وعلى طول الخط
    private static func shifting(_ time: Double, _ position: Double, _ light: Bool, _ alpha: Double) -> Color {
        let hue = (205 + 45 * sin(time * 0.45 + position * 2.4) + 360).truncatingRemainder(dividingBy: 360)
        return Color(hue: hue / 360, saturation: light ? 0.85 : 0.62, brightness: light ? 0.78 : 1, opacity: min(max(alpha, 0), 1))
    }

    /// تقدم الرسم في الدورة: 0 عند الطرف الأيمن، 1 عند الأيسر
    private static func sweep(_ time: Double) -> Double { (time / sweepSeconds).truncatingRemainder(dividingBy: 1) }

    /// فرشاة الخط: أمام الرأس خافت، الرأس أبيض متوهج، وخلفه أثر ملوّن يخبو. المواضع من اليسار (0) إلى اليمين (1).
    private static func brush(_ time: Double, _ light: Bool, _ width: Double, _ alpha: Double) -> GraphicsContext.Shading {
        let head = 1 - sweep(time)
        let dim = 0.16 * alpha
        func at(_ x: Double) -> Double { min(max(x, 0), 1) }
        var stops: [Gradient.Stop] = [
            .init(color: shifting(time, 0, light, dim), location: 0),
            .init(color: shifting(time, head, light, dim), location: at(head - 0.02)),
            .init(color: Color.white.opacity(alpha), location: at(head)),
            .init(color: shifting(time, head + 0.05, light, alpha), location: at(head + 0.05)),
            .init(color: shifting(time, head + 0.30, light, 0.65 * alpha), location: at(head + 0.30)),
            .init(color: shifting(time, head + 0.65, light, 0.35 * alpha), location: at(head + 0.65)),
            .init(color: shifting(time, 1, light, 0.30 * alpha), location: 1),
        ]
        // مواضع مكررة عند الطرفين تُحذف (مثل distinctBy في أندرويد)
        var seen = Set<Double>()
        stops = stops.filter { seen.insert(Double($0.location)).inserted }
        return .linearGradient(Gradient(stops: stops), startPoint: .zero, endPoint: CGPoint(x: width, y: 0))
    }

    static func drawLines(in context: inout GraphicsContext, size: CGSize, time: Double, light: Bool, lineWidth: Double, alpha: Double, lines: Int) {
        let width = Double(size.width), height = Double(size.height)
        guard width > 0 else { return }
        let shading = brush(time, light, width, alpha)
        for index in 0..<lines {
            let k = Double(index) / Double(lines - 1) - 0.5
            var path = Path()
            var x = 0.0
            var first = true
            while x <= width {
                let t = (width - x) / width
                // الخطوط تتباعد كالموجة بعيدًا عن النبضة، وتجتمع عندها فتظهر حادة
                // تجتمع أيضًا قليلًا عند القلب في الوسط، كأنها تعبر من خلاله
                let pinch = max(beats.map { gauss(t - $0, 0.06) }.max() ?? 0, 0.5 * gauss(t - 0.5, 0.07))
                let spread = height * 0.34 * (1 - pinch) * (0.7 + 0.3 * sin(t * 7 + time * 0.7))
                let point = CGPoint(x: x, y: center(t, time, height) + k * spread)
                if first { path.move(to: point); first = false } else { path.addLine(to: point) }
                x += 1.5
            }
            var layer = context
            layer.opacity = 1 - abs(k) * 0.9
            layer.stroke(path, with: shading, lineWidth: lineWidth)
        }
    }

    /// خفقة القلب كل ثانية: «لب» قوية ثم «دب» أخف، بين 0 و1
    private static func heartBeat(_ time: Double) -> Double {
        let phase = time.truncatingRemainder(dividingBy: 1)
        return max(exp(-pow(phase - 0.04, 2) / 0.0018), 0.65 * exp(-pow(phase - 0.3, 2) / 0.0018))
    }

    /// شكل القلب (المعادلة المعروفة) بمركز وحجم، والرأس للأعلى
    private static func heartPath(_ center: CGPoint, _ size: Double) -> Path {
        var path = Path()
        for i in 0...72 {
            let a = Double(i) / 72 * 2 * .pi
            let x = 16 * pow(sin(a), 3)
            let y = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)
            let point = CGPoint(x: center.x + x / 34 * size, y: center.y - (y + 2.5) / 34 * size)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }

    /// قلب ينبض في وسط الموجة، ويضيء أكثر حين يمر به رأس الموجة المضيء
    static func drawHeart(in context: inout GraphicsContext, size: CGSize, time: Double, light: Bool, front: Bool) {
        let width = Double(size.width), height = Double(size.height)
        guard width > 0 else { return }
        let beat = heartBeat(time)
        let p = sweep(time)
        let passing = exp(-pow(p - 0.5, 2) / 0.004)
        let heart = height * 0.62 * (1 + 0.13 * beat)
        let center = CGPoint(x: width / 2, y: height * 0.5)
        let path = heartPath(center, heart)
        if !front {
            let glow = heart * 1.25
            let color = shifting(time, 0.5, light, 0.30 + 0.25 * beat + 0.3 * passing)
            context.fill(Path(ellipseIn: CGRect(x: center.x - glow, y: center.y - glow, width: glow * 2, height: glow * 2)),
                         with: .radialGradient(Gradient(colors: [color, color.opacity(0)]), center: center, startRadius: 0, endRadius: glow))
            context.fill(path, with: .linearGradient(Gradient(colors: [shifting(time, 0.5, light, 0.22), shifting(time, 0.5, light, 0.08)]),
                                                     startPoint: CGPoint(x: center.x, y: center.y - heart / 2), endPoint: CGPoint(x: center.x, y: center.y + heart / 2)))
        } else {
            context.stroke(path, with: .color(shifting(time, 0.5, light, 0.35)), lineWidth: 4 * (1 + 0.5 * beat))
            context.stroke(path, with: .color(Color.white.opacity(0.55 + 0.35 * max(beat, passing))), lineWidth: 1.3)
        }
    }

    /// الرأس المضيء، وتوهج البداية في الطرف الأيمن وتوهج النهاية في الطرف الأيسر
    static func drawGlow(in context: inout GraphicsContext, size: CGSize, time: Double, light: Bool) {
        let width = Double(size.width), height = Double(size.height)
        guard width > 0 else { return }
        let p = sweep(time)
        func glow(_ point: CGPoint, _ radius: Double, _ color: Color) {
            let rect = CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2)
            context.fill(Path(ellipseIn: rect), with: .radialGradient(Gradient(colors: [color, color.opacity(0)]), center: point, startRadius: 0, endRadius: radius))
        }
        let head = CGPoint(x: width * (1 - p), y: center(p, time, height))
        glow(head, 22, shifting(time, 1 - p, light, 0.55))
        context.fill(Path(ellipseIn: CGRect(x: head.x - 3, y: head.y - 3, width: 6, height: 6)), with: .color(.white))
        // توهج الانطلاق: يشتعل في الطرف الأيمن أول الدورة ثم يخبو
        let startGlow = exp(-p * 9)
        glow(CGPoint(x: width, y: center(0, time, height)), 46, shifting(time, 1, light, 0.85 * startGlow))
        // توهج الوصول: يكبر حين يقترب الرأس من الطرف الأيسر ويبقى لحظة بعد الوصول
        let endGlow = max(exp(-(1 - p) * 9), 0.9 * exp(-p * 7))
        glow(CGPoint(x: 0, y: center(1, time, height)), 46, shifting(time, 0, light, 0.85 * endGlow))
    }
}

// MARK: - شعار وصور

/// شعار عافيتي داخل مربع أبيض مستدير بتوهج خفيف
struct LogoView: View {
    let size: CGFloat

    var body: some View {
        Image("Logo")
            .resizable()
            .scaledToFit()
            .frame(width: size * 0.92, height: size * 0.92)
            .frame(width: size, height: size)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.24, style: .continuous))
            .shadow(color: Brand.glow.opacity(0.45), radius: 14)
            .accessibilityLabel(tr("شعار عافيتي", "Afiati logo"))
    }
}

/// دائرة ملونة فيها الحرف الأول من الاسم
struct AvatarView: View {
    let name: String
    let id: String
    var size: CGFloat = 48
    var color: Color?

    var body: some View {
        Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)))
            .font(.custom(Tajawal.name(.medium), size: size * 0.42))
            .foregroundColor(.white)
            .frame(width: size, height: size)
            .background(color ?? Brand.avatarColor(id), in: Circle())
            .accessibilityHidden(true)
    }
}

/// دائرة ملونة فيها أيقونة (للأدوية والإشعارات)
struct IconBadge: View {
    let icon: String
    let color: Color
    var size: CGFloat = 48
    var tint: Color = .white

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: size * 0.44, weight: .medium))
            .foregroundColor(tint)
            .frame(width: size, height: size)
            .background(color, in: Circle())
            .accessibilityHidden(true)
    }
}
