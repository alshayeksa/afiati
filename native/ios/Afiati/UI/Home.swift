import SwiftUI

enum Tab: Hashable { case today, medications, history, family }

/// نافذة سفلية من الشاشة الرئيسية
enum HomeSheet: Identifiable {
    case profile, settings, notifications, about
    case medication(Medication)

    var id: String {
        switch self {
        case .profile: return "profile"
        case .settings: return "settings"
        case .notifications: return "notifications"
        case .about: return "about"
        case .medication(let medication): return "medication-" + medication.id
        }
    }
}

/// نافذة تأكيد فوق كل الشاشة (فوق شريط التبويب)
struct PresentedDialog: Identifiable {
    let id = UUID()
    let view: AnyView
}

extension AppModel {
    func present<V: View>(_ view: V) { dialog = PresentedDialog(view: AnyView(view)) }
    func dismissDialog() { dialog = nil }
}

/// جذر الواجهة: الإعداد الأول، أو الشاشة الرئيسية
struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ZStack {
            AppBackground()
            if model.loaded {
                if model.patient == nil { OnboardingView() } else { HomeView() }
            }
            // إيقاف الخدمة أو تحديث إلزامي من صفحة التحكم: شاشة تغطي كل شيء
            if let block = model.control.block {
                BlockView(block: block, updateURL: model.control.updateURL).zIndex(100)
            }
        }
        .id("\(model.languageVersion)-\(model.themeVersion)")
        .environment(\.layoutDirection, Lang.direction)
        .environment(\.locale, Lang.locale)
        .preferredColorScheme(Appearance.mode == .system ? nil : (Appearance.light ? .light : .dark))
        .tint(Brand.glow)
        .onChange(of: scheme) { value in
            if Appearance.mode == .system { model.systemStyleChanged(dark: value == .dark) }
        }
    }
}

/**
 الشاشة الرئيسية بأسلوب موجة: شريط علوي زجاجي، محتوى التبويب،
 وفي الأسفل زر الإضافة المضيء وشريط التبويب العائم.
 */
struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showAdd = false
    @State private var showVitals = false
    @State private var showPulse = false
    @State private var showHelp = false
    @State private var openPatient: FamilyLink?
    @State private var sheet: HomeSheet?

    private func closeAll() {
        showAdd = false; showVitals = false; showPulse = false; openPatient = nil; sheet = nil
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch model.tab {
                case .today: TodayScreen(header: header, onAdd: { show { showAdd = true } }, onOpenMedication: { sheet = .medication($0) },
                                         onHelp: { show { showHelp = true } }, onVitals: { show { showVitals = true } })
                case .medications: MedicationsScreen(header: header, onAdd: { show { showAdd = true } }, onOpen: { sheet = .medication($0) })
                case .history: HistoryScreen(header: header)
                case .family: FamilyScreen(header: header, onOpenPatient: { link in show { openPatient = link } }, onHelp: { show { showHelp = true } })
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            // تدرّج خلف شريط التبويب، فلا يتداخل المحتوى معه
            LinearGradient(colors: [.clear, Brand.base.opacity(0.85), Brand.base], startPoint: .top, endPoint: .bottom)
                .frame(height: 120)
                .ignoresSafeArea(edges: .bottom)
                .allowsHitTesting(false)

            BottomBar(tab: model.tab, onTab: { model.tab = $0 }, onAdd: { show { showAdd = true } })
                .padding(.bottom, 6)

            if let link = openPatient {
                PatientScreen(link: link, onClose: { hide { openPatient = nil } })
            }
            if showAdd {
                AddMedicationScreen(onClose: { hide { showAdd = false } })
            }
            if showVitals {
                VitalsScreen(onClose: { hide { showVitals = false } }, onPulse: { show { showPulse = true } })
            }
            if showPulse {
                PulseScreen(onClose: { hide { showPulse = false } })
            }
            if showHelp {
                HelpScreen(onClose: { hide { showHelp = false } })
            }
            ToastView().padding(.bottom, 92).zIndex(40)
            if let dialog = model.dialog {
                dialog.view.zIndex(60)
            }
        }
        .afiatiSheet(item: $sheet) { item in
            switch item {
            case .profile: ProfileSheet(onDismiss: { sheet = nil })
            case .settings: SettingsSheet(onDismiss: { sheet = nil })
            case .notifications: NotificationsSheet(onDismiss: { sheet = nil })
            case .about: AboutSheet(onDismiss: { sheet = nil })
            case .medication(let medication): MedicationSheet(medication: medication, onDismiss: { sheet = nil })
            }
        }
        .onAppear { handle(model.pendingOpen) }
        .onChange(of: model.pendingOpen) { handle($0) }
        // منتصف الليل والتطبيق مفتوح، ومتابعة طلبات المساعدة الجارية
        .task {
            model.refreshHelp()
            var ticks = 0
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                ticks += 1
                if model.myHelp != nil || !model.familyHelp.isEmpty { model.refreshHelp() }
                if ticks % 2 == 0 { model.checkDay() }
                // صفحة التحكم: كل دقيقتين، فتظهر أو تختفي شاشة الإيقاف دون إعادة فتح التطبيق
                if ticks % 8 == 0 { model.refreshControl() }
            }
        }
    }

    private func show(_ change: () -> Void) {
        withAnimation(.easeOut(duration: 0.28), change)
    }

    private func hide(_ change: () -> Void) {
        withAnimation(.easeIn(duration: 0.22), change)
    }

    /// ضغطة على إشعار من العائلة
    private func handle(_ target: OpenTarget?) {
        guard let target = target else { return }
        model.pendingOpen = nil
        switch target {
        case .family:
            closeAll()
            model.tab = .family
            model.familyFocus += 1
        case .help:
            closeAll()
            model.refreshHelp()
            // للكبير: حالة طلبه. لفرد العائلة: بطاقة الطلب أعلى الشاشة
            if model.myHelp != nil { show { showHelp = true } } else { model.tab = .today }
        case .notifications:
            closeAll()
            sheet = .notifications
        case .patient(let patientId):
            // تنبيه جرعة فائتة أو مخزون: يفتح يوم هذا القريب مباشرة
            closeAll()
            showHelp = false
            model.tab = .family
            model.refreshFamily()
            Task {
                for _ in 0..<20 {
                    if let link = model.family?.following.first(where: { $0.patientId == patientId && $0.status == "active" }) {
                        show { openPatient = link }
                        return
                    }
                    try? await Task.sleep(nanoseconds: 500_000_000)
                }
            }
        }
    }

    private var header: AnyView {
        AnyView(TopBar(onProfile: { sheet = .profile }, onNotifications: { sheet = .notifications },
                       onAbout: { sheet = .about }, onSettings: { sheet = .settings }))
    }
}

/// قائمة تبويب: الشريط العلوي ثم المحتوى، مع مساحة لشريط التبويب السفلي
struct TabList<Content: View>: View {
    let header: AnyView
    @ViewBuilder let content: () -> Content
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                header
                // طلبات مساعدة ممن تتابعهم: أعلى كل تبويب حتى يرد أحد
                ForEach(model.familyHelp) { help in FamilyHelpCard(help: help) }
                content()
            }
            .padding(.top, 6)
            .padding(.bottom, 150)
        }
    }
}

/// زر اللغة في الشريط العلوي: «E» يحوّل إلى الإنجليزية، و«ع» يعيد العربية
private struct LanguageButton: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Button { model.setLanguage(english: !Lang.english) } label: {
            Text(Lang.english ? "ع" : "E").style(19, .bold, Brand.glow)
                .frame(width: 34, height: 42)
                .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .accessibilityLabel(tr("English", "العربية"))
    }
}

private struct TopBar: View {
    let onProfile: () -> Void
    let onNotifications: () -> Void
    let onAbout: () -> Void
    let onSettings: () -> Void
    @EnvironmentObject private var model: AppModel

    var body: some View {
        HStack {
            // اسم المستخدم: يفتح ملفه
            Button(action: onProfile) {
                HStack(spacing: 8) {
                    AvatarView(name: model.patient?.fullName ?? "", id: model.patient?.id ?? "", size: 32, color: Brand.green)
                    Text(model.patient?.fullName ?? "").style(16, .medium).lineLimit(1).frame(maxWidth: 150, alignment: .leading)
                }
                .padding(.leading, 5)
                .padding(.trailing, 14)
                .padding(.vertical, 5)
                .glass(Capsule())
            }
            .buttonStyle(PressStyle())
            .accessibilityLabel(tr("ملفي", "My profile"))
            Spacer()
            HStack(spacing: 0) {
                Button(action: onNotifications) {
                    Image(systemName: "bell")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundColor(Brand.glow)
                        .frame(width: 42, height: 42)
                        .overlay(alignment: .topTrailing) {
                            if model.unreadCount > 0 {
                                Circle().fill(Brand.danger).frame(width: 10, height: 10)
                                    .overlay(Circle().stroke(Brand.base, lineWidth: 1.5)).padding(8)
                            }
                        }
                }
                .buttonStyle(PressStyle())
                .accessibilityLabel(tr("الإشعارات", "Notifications"))
                divider
                Button(action: onAbout) {
                    Image(systemName: "info.circle").font(.system(size: 20, weight: .medium)).foregroundColor(Brand.text).frame(width: 42, height: 42)
                }
                .buttonStyle(PressStyle())
                .accessibilityLabel(tr("عن التطبيق", "About"))
                divider
                LanguageButton()
                divider
                Button(action: onSettings) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(LinearGradient(colors: [Appearance.tone(0xC6FFE6), Brand.glow, Brand.mint], startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: 42, height: 42)
                }
                .buttonStyle(PressStyle())
                .accessibilityLabel(tr("الإعدادات", "Settings"))
            }
            .padding(.horizontal, 6)
            .glass(Capsule())
        }
        .padding(.horizontal, 14)
    }

    private var divider: some View {
        Rectangle().fill(Brand.text.opacity(0.2)).frame(width: 1, height: 18).padding(.horizontal, 3)
    }
}

private struct BottomBar: View {
    let tab: Tab
    let onTab: (Tab) -> Void
    let onAdd: () -> Void
    @EnvironmentObject private var model: AppModel

    var body: some View {
        HStack(spacing: 10) {
            HStack(spacing: 2) {
                TabButton(title: tr("اليوم", "Today"), icon: "calendar", selected: tab == .today) { onTab(.today) }
                TabButton(title: tr("أدويتي", "Medicines"), icon: "pills.fill", selected: tab == .medications) { onTab(.medications) }
                TabButton(title: tr("السجل", "History"), icon: "chart.bar.fill", selected: tab == .history) { onTab(.history) }
                TabButton(title: tr("العائلة", "Family"), icon: "person.3.fill", selected: tab == .family,
                          badge: !(model.family?.pending.isEmpty ?? true)) { onTab(.family) }
            }
            .padding(5)
            .background(Appearance.tone(0x0A1512).opacity(0.92), in: Capsule())
            .glass(Capsule())

            Button(action: onAdd) {
                Image(systemName: "plus")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(Brand.ink)
                    .frame(width: 62, height: 62)
                    .background(
                        RadialGradient(colors: [Appearance.tone(0x7BFFC9), Appearance.tone(0x1FD38E), Appearance.tone(0x0B6B4E)],
                                       center: UnitPoint(x: 0.35, y: 0.3), startRadius: 0, endRadius: 50),
                        in: Circle())
                    .overlay(Circle().stroke(Color.white.opacity(0.6), lineWidth: 1))
                    .shadow(color: Brand.glow.opacity(0.45), radius: 14)
            }
            .buttonStyle(PressStyle())
            .accessibilityLabel(tr("إضافة دواء", "Add medicine"))
        }
        .padding(.horizontal, 12)
    }
}

private struct TabButton: View {
    let title: String
    let icon: String
    let selected: Bool
    var badge = false
    let action: () -> Void

    var body: some View {
        let tint = selected ? Brand.text : Brand.text.opacity(0.6)
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: icon).font(.system(size: 19, weight: .medium))
                Text(title).style(13, .medium, tint).lineLimit(1).minimumScaleFactor(0.75)
            }
            .foregroundColor(tint)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(selected ? AnyView(LinearGradient(colors: [Brand.mint.opacity(0.75), Brand.deepGreen.opacity(0.85)], startPoint: .top, endPoint: .bottom)) : AnyView(Color.clear))
            .clipShape(Capsule())
            .overlay(alignment: .topTrailing) {
                if badge { Circle().fill(Brand.warning).frame(width: 10, height: 10).padding(.top, 5).padding(.trailing, 12) }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(PressStyle())
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}
