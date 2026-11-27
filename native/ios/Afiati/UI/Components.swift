import SwiftUI

/// عنوان كل تبويب: واضح دون أن يطغى على المحتوى، وتحته سطر وصف واحد
struct BigTitle: View {
    let title: String
    var subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).style(26, .bold)
            if let subtitle = subtitle { Text(subtitle).style(15, .regular, Brand.secondaryText) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .padding(.top, 14)
        .padding(.bottom, 12)
    }
}

/// عنوان قسم داخل الصفحة
struct SectionTitle: View {
    let text: String
    var top: CGFloat = 18

    var body: some View {
        Text(text).style(19, .bold)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, top)
            .padding(.bottom, 8)
    }
}

struct FieldLabel: View {
    let text: String

    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text).style(15, .regular, Brand.secondaryText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 4)
    }
}

/// حقل نص زجاجي
struct GlassField: View {
    @Binding var text: String
    let placeholder: String
    var size: CGFloat = 19
    var weight: Font.Weight = .regular
    var centered = false
    var keyboard: UIKeyboardType = .default
    var ltr = false
    var capitalization: TextInputAutocapitalization = .sentences

    var body: some View {
        ZStack(alignment: centered ? .center : .leading) {
            if text.isEmpty {
                Text(placeholder).style(size, weight, Brand.text.opacity(0.38)).lineLimit(1)
            }
            TextField("", text: $text)
                .font(tj(size, weight))
                .foregroundColor(Brand.text)
                .multilineTextAlignment(centered ? .center : .leading)
                .keyboardType(keyboard)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled(keyboard != .default)
                .tint(Brand.glow)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .glass(radius: 18)
        .environment(\.layoutDirection, ltr ? .leftToRight : Lang.direction)
    }
}

/// زر اختيار زجاجي، يضيء عند اختياره
struct Chip: View {
    let text: String
    let selected: Bool
    var icon: String?
    let action: () -> Void

    init(_ text: String, selected: Bool, icon: String? = nil, action: @escaping () -> Void) {
        self.text = text
        self.selected = selected
        self.icon = icon
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon = icon { Image(systemName: icon).foregroundColor(selected ? Brand.text : Brand.glow) }
                Text(text).style(18, selected ? .bold : .medium).lineLimit(1).minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(selected ? AnyView(LinearGradient(colors: [Brand.mint.opacity(0.8), Brand.deepGreen.opacity(0.9)], startPoint: .top, endPoint: .bottom)) : AnyView(Color.clear))
            .glass(radius: 18, highlight: selected ? 0.6 : 0.22)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// صف من أزرار الاختيار بعرض متساوٍ
struct ChipRow<Item: Hashable>: View {
    let items: [Item]
    let title: (Item) -> String
    let selected: (Item) -> Bool
    let action: (Item) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(items, id: \.self) { item in
                Chip(title(item), selected: selected(item)) { action(item) }
            }
        }
    }
}

/// شبكة أزرار اختيار تلتف حسب العرض
struct ChipGrid: View {
    let items: [String]
    var columns = 2
    let selected: (String) -> Bool
    let action: (String) -> Void

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: columns), spacing: 8) {
            ForEach(items, id: \.self) { item in
                Chip(item, selected: selected(item)) { action(item) }
            }
        }
    }
}

/// عدّاد كبير: زر زائد، الرقم، زر ناقص
struct Stepper2: View {
    let value: String
    var caption: String?
    var minusEnabled = true
    var plusEnabled = true
    let onMinus: () -> Void
    let onPlus: () -> Void

    var body: some View {
        HStack {
            StepButton(icon: "plus", label: tr("زيادة", "Increase"), enabled: plusEnabled, action: onPlus)
            VStack(spacing: 0) {
                Text(value).style(46, .bold).lineLimit(1).minimumScaleFactor(0.6)
                if let caption = caption { Text(caption).style(16, .regular, Brand.secondaryText) }
            }
            .frame(maxWidth: .infinity)
            StepButton(icon: "minus", label: tr("إنقاص", "Decrease"), enabled: minusEnabled, action: onMinus)
        }
    }
}

private struct StepButton: View {
    let icon: String
    let label: String
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 30, weight: .semibold))
                .foregroundColor(enabled ? Brand.glow : Brand.text.opacity(0.25))
                .frame(width: 72, height: 72)
                .glass(Circle(), highlight: enabled ? 0.35 : 0.1)
        }
        .buttonStyle(PressStyle())
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

/// حلقة التقدم المضيئة
struct ProgressRing<Center: View>: View {
    let progress: Double
    let size: CGFloat
    var stroke: CGFloat = 12
    @ViewBuilder let center: () -> Center

    var body: some View {
        let gradient = AngularGradient(colors: [Brand.mint, Brand.glow, Appearance.tone(0xC6FFE6), Brand.mint], center: .center)
        ZStack {
            Circle().stroke(Brand.text.opacity(0.09), lineWidth: stroke)
            Circle().trim(from: 0, to: min(max(progress, 0), 1))
                .stroke(gradient, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .shadow(color: Brand.glow.opacity(0.45), radius: 6)
                .animation(.easeOut(duration: 0.7), value: progress)
            center()
        }
        .padding(stroke / 2)
        .frame(width: size, height: size)
    }
}

/// بطاقة زجاجية فارغة برسالة وزر
struct EmptyCard: View {
    let title: String
    let message: String
    var action: String?
    var onAction: (() -> Void)?

    var body: some View {
        VStack(spacing: 10) {
            Text(title).style(21, .bold).multilineTextAlignment(.center)
            Text(message).style(17, .regular, Brand.secondaryText).multilineTextAlignment(.center)
            if let action = action, let onAction = onAction {
                PrimaryButton(action, action: onAction).padding(.top, 8)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .glass(radius: 26)
        .padding(.horizontal, 14)
    }
}

/// نقطة ملونة ونص: حالة الحفظ والمزامنة
struct StatusLine: View {
    let color: Color
    let text: String

    var body: some View {
        HStack(spacing: 8) {
            Circle().fill(color).frame(width: 9, height: 9).overlay(Circle().stroke(color.opacity(0.3), lineWidth: 2))
            Text(text).style(16, .regular, color)
        }
    }
}

/// بطاقة زجاجية عامة
struct Card<Content: View>: View {
    var radius: CGFloat = 26
    var padding: CGFloat = 18
    var spacing: CGFloat = 10
    var alignment: HorizontalAlignment = .leading
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: alignment, spacing: spacing, content: content)
            .frame(maxWidth: .infinity, alignment: alignment == .center ? .center : .leading)
            .padding(padding)
            .glass(radius: radius)
    }
}

/// زر دائري زجاجي بأيقونة (إغلاق، رجوع، تحديث)
struct CircleIconButton: View {
    let icon: String
    let label: String
    var color: Color?
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(color ?? Brand.text)
                .frame(width: 48, height: 48)
                .glass(Circle())
        }
        .buttonStyle(PressStyle())
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

/// رأس شاشة كاملة: العنوان وزر «إغلاق»
struct ScreenHeader: View {
    let title: String
    let onClose: () -> Void

    var body: some View {
        HStack {
            Text(title).style(26, .bold)
            Spacer()
            Button(action: onClose) {
                Text(tr("إغلاق", "Close")).style(18, .medium, Brand.glow)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
            }
            .buttonStyle(PressStyle())
        }
        .padding(.leading, 20)
        .padding(.trailing, 10)
        .padding(.top, 10)
    }
}

/// شاشة كاملة فوق التطبيق (إضافة دواء، العلامات الحيوية، المساعدة)
struct FullScreen<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            AppBackground()
            VStack(spacing: 0, content: content)
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
        .zIndex(10)
    }
}

// MARK: - النوافذ

/// نافذة تأكيد أو تنبيه داكنة فوق الشاشة. تُعرض داخل overlay.
struct AfiatiDialog<Content: View>: View {
    let title: String
    var text: String?
    let confirm: String
    var destructive = false
    var dismiss: String?
    var confirmEnabled = true
    let onConfirm: () -> Void
    let onDismiss: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea().onTapGesture(perform: onDismiss)
            VStack(alignment: .leading, spacing: 14) {
                Text(title).style(21, .bold)
                if let text = text { Text(text).style(17, .regular, Brand.text.opacity(0.78)) }
                content()
                HStack(spacing: 6) {
                    Spacer()
                    if let dismiss = dismiss {
                        Button(action: onDismiss) {
                            Text(dismiss).style(18, .regular, Brand.text.opacity(0.75)).padding(.horizontal, 12).padding(.vertical, 8)
                        }
                        .buttonStyle(PressStyle())
                    }
                    Button(action: onConfirm) {
                        Text(confirm).style(18, .bold, destructive ? Brand.danger : Brand.glow).padding(.horizontal, 12).padding(.vertical, 8)
                    }
                    .buttonStyle(PressStyle())
                    .disabled(!confirmEnabled)
                }
            }
            .padding(22)
            .background(Brand.menu, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(Brand.glow.opacity(0.2), lineWidth: 1))
            .padding(.horizontal, 24)
            .frame(maxHeight: .infinity)
        }
        .transition(.opacity)
        .zIndex(50)
    }
}

extension AfiatiDialog where Content == EmptyView {
    init(title: String, text: String? = nil, confirm: String, destructive: Bool = false, dismiss: String? = nil,
         onConfirm: @escaping () -> Void, onDismiss: @escaping () -> Void) {
        self.init(title: title, text: text, confirm: confirm, destructive: destructive, dismiss: dismiss,
                  onConfirm: onConfirm, onDismiss: onDismiss, content: { EmptyView() })
    }
}

/// قالب النوافذ السفلية: خلفية داكنة، عنوان، وزر إغلاق
struct AfiatiSheet<Content: View>: View {
    let title: String?
    var closeTitle: String?
    let onDismiss: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack(alignment: .bottom) {
            Brand.sheet.ignoresSafeArea()
            VStack(spacing: 0) {
                if let title = title {
                    HStack {
                        Text(title).style(22, .bold)
                        Spacer()
                        Button(action: onDismiss) {
                            Text(closeTitle ?? tr("تم", "Done")).style(18, .medium, Brand.glow).padding(.horizontal, 12).padding(.vertical, 8)
                        }
                        .buttonStyle(PressStyle())
                    }
                    .padding(.leading, 20)
                    .padding(.trailing, 12)
                    .padding(.top, 20)
                    .padding(.bottom, 10)
                } else {
                    HStack {
                        Spacer()
                        Button(action: onDismiss) {
                            Text(closeTitle ?? tr("تم", "Done")).style(18, .medium, Brand.glow).padding(.horizontal, 12).padding(.vertical, 8)
                        }
                        .buttonStyle(PressStyle())
                    }
                    .padding(.horizontal, 12)
                    .padding(.top, 14)
                }
                ScrollView {
                    VStack(alignment: .leading, spacing: 0, content: content)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 28)
                }
            }
            ToastView().padding(.bottom, 20)
        }
        .environment(\.layoutDirection, Lang.direction)
        .preferredColorScheme(Appearance.light ? .light : .dark)
    }
}

/// شريط «سُجّلت الجرعة · تراجع»، والرسائل القصيرة
struct ToastView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Group {
            if let pending = model.pendingTake {
                bar {
                    Text(tr("سُجّلت جرعة \(pending.medicationName)", "\(pending.medicationName) dose logged")).style(17, .medium).lineLimit(2)
                    Spacer()
                    Button { model.undoTake() } label: {
                        Text(tr("تراجع", "Undo")).style(18, .bold, Brand.glow).padding(.horizontal, 12).padding(.vertical, 6)
                    }
                    .buttonStyle(PressStyle())
                }
            } else if let toast = model.toast {
                bar {
                    Text(toast).style(17, .medium)
                    Spacer(minLength: 0)
                }
                .task(id: toast) {
                    try? await Task.sleep(nanoseconds: 3_500_000_000)
                    if model.toast == toast { model.toast = nil }
                }
            }
        }
        .animation(.easeOut(duration: 0.25), value: model.pendingTake?.id)
        .animation(.easeOut(duration: 0.25), value: model.toast)
    }

    private func bar<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        HStack(content: content)
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .background(Brand.menu, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(Brand.glow.opacity(0.3), lineWidth: 1))
            .shadow(color: .black.opacity(0.35), radius: 16, y: 4)
            .padding(.horizontal, 16)
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

extension View {
    /// نافذة سفلية بخلفية التطبيق، بكامل الطول
    func afiatiSheet<Item: Identifiable, Content: View>(item: Binding<Item?>, @ViewBuilder content: @escaping (Item) -> Content) -> some View {
        sheet(item: item) { value in
            content(value).presentationDetents([.large]).presentationDragIndicator(.hidden).environmentObject(AppModel.shared)
        }
    }

    func afiatiSheet<Content: View>(isPresented: Binding<Bool>, @ViewBuilder content: @escaping () -> Content) -> some View {
        sheet(isPresented: isPresented) {
            content().presentationDetents([.large]).presentationDragIndicator(.hidden).environmentObject(AppModel.shared)
        }
    }
}

/// يفتح رابطًا أو رقم هاتف
@MainActor
enum Links {
    static func open(_ value: String) {
        guard let url = URL(string: value) else { return }
        UIApplication.shared.open(url)
    }

    /// يفتح شاشة الاتصال بالرقم. iOS يسأل قبل الاتصال، فلا خطر من ضغطة خاطئة.
    static func dial(_ phone: String) {
        let digits = phone.filter { $0.isNumber || $0 == "+" }
        guard !digits.isEmpty, let url = URL(string: "tel://\(digits)") else { return }
        UIApplication.shared.open(url)
    }

    static func appSettings() { open(UIApplication.openSettingsURLString) }

    static func notificationSettings() {
        open(UIApplication.openNotificationSettingsURLString)
    }
}
