import SwiftUI
import CoreLocation

/// رجل أو امرأة: تُكتب به رسائل المساعدة لعائلتك (يحتاج / تحتاج)
struct GenderPicker: View {
    let gender: String?
    let onChange: (String) -> Void

    var body: some View {
        HStack(spacing: 10) {
            Chip(tr("رجل", "Man"), selected: gender == Gender.male) { onChange(Gender.male) }
            Chip(tr("امرأة", "Woman"), selected: gender == Gender.female) { onChange(Gender.female) }
        }
    }
}

/// التحقق من بيانات الملف الشخصي. يعيد رسالة الخطأ أو nil.
func validateProfile(name: String, phone: String, email: String) -> String? {
    if name.trimmingCharacters(in: .whitespaces).count < 2 { return tr("اكتب اسمك كما تحب أن يراه أهلك.", "Type your name as you'd like your family to see it.") }
    if phone.filter(\.isNumber).count < 9 { return tr("أدخل رقم جوال صحيحًا.", "Enter a valid mobile number.") }
    let trimmed = email.trimmingCharacters(in: .whitespaces)
    if !trimmed.isEmpty && trimmed.range(of: "^\\S+@\\S+\\.\\S+$", options: .regularExpression) == nil {
        return tr("البريد غير صحيح. صححه أو اتركه فارغًا.", "The email isn't valid. Fix it or leave it empty.")
    }
    return nil
}

/// ملفي: الاسم والجوال والبريد
struct ProfileSheet: View {
    let onDismiss: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var name = ""
    @State private var phone = ""
    @State private var email = ""
    @State private var gender: String?
    @State private var error: String?
    @State private var loaded = false

    var body: some View {
        let patient = model.patient
        let changed = patient.map { name != $0.fullName || phone != $0.phone || email != ($0.email ?? "") || gender != $0.gender } ?? false
        AfiatiSheet(title: tr("ملفي", "My profile"), onDismiss: onDismiss) {
            VStack(spacing: 6) {
                AvatarView(name: name.isEmpty ? (patient?.fullName ?? "") : name, id: patient?.id ?? "", size: 84, color: Brand.green)
                Text(name.isEmpty ? (patient?.fullName ?? "") : name).style(26, .bold)
                StatusLine(color: Brand.glow, text: tr("ملفك محفوظ على جوالك", "Your profile is saved on your phone"))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            VStack(spacing: 10) {
                FieldLabel(tr("الاسم كما يراه أهلك", "Name as your family sees it"))
                GlassField(text: Binding(get: { name }, set: { name = $0; error = nil }), placeholder: tr("الاسم", "Name"), capitalization: .words)
                FieldLabel(tr("رقم الجوال", "Mobile number"))
                GlassField(text: Binding(get: { phone }, set: { phone = $0; error = nil }), placeholder: "05xxxxxxxx", keyboard: .phonePad, ltr: true)
                FieldLabel(tr("أنا", "I am"))
                GenderPicker(gender: gender) { gender = $0; error = nil }
                FieldLabel(tr("البريد الإلكتروني (اختياري)", "Email (optional)"))
                GlassField(text: Binding(get: { email }, set: { email = $0; error = nil }), placeholder: "name@example.com", keyboard: .emailAddress, ltr: true, capitalization: .never)
                if let error = error { Text(error).style(16, .medium, Brand.danger).frame(maxWidth: .infinity, alignment: .leading) }
                PrimaryButton(tr("حفظ التعديلات", "Save changes"), icon: "square.and.arrow.down", enabled: changed) {
                    error = validateProfile(name: name, phone: phone, email: email)
                    if error == nil {
                        model.updatePatient(name: name, phone: phone, email: email.trimmingCharacters(in: .whitespaces).isEmpty ? nil : email, gender: gender)
                        onDismiss()
                    }
                }
                .padding(.top, 8)
                Text(tr("لا نطلب كلمة مرور. بياناتك على جوالك، وتُنسخ إلى خادم عافيتي عند توفر الإنترنت لتصل لمن توافق عليهم من عائلتك فقط.",
                        "No password needed. Your data is on your phone and is copied to the Afiati server when online, reaching only the family members you approve."))
                    .style(15, .regular, Brand.secondaryText)
            }
        }
        .onAppear {
            guard !loaded, let patient = patient else { return }
            loaded = true
            name = patient.fullName
            phone = patient.phone
            email = patient.email ?? ""
            gender = patient.gender
        }
    }
}

/// الإعدادات: اللغة، التذكيرات، رقم الطوارئ، المظهر، والمزامنة
struct SettingsSheet: View {
    let onDismiss: () -> Void
    @EnvironmentObject private var model: AppModel
    @ObservedObject private var location = HelpLocation.shared
    @AppStorage("alert.sound") private var alertSound = true

    var body: some View {
        AfiatiSheet(title: tr("الإعدادات", "Settings"), onDismiss: onDismiss) {
            VStack(spacing: 10) {
                FieldLabel("اللغة · Language")
                HStack(spacing: 10) {
                    Chip("العربية", selected: !Lang.english) { model.setLanguage(english: false) }
                    Chip("English", selected: Lang.english) { model.setLanguage(english: true) }
                }

                FieldLabel(tr("التذكيرات", "Reminders")).padding(.top, 10)
                let notificationsOn = model.notificationStatus == .authorized || model.notificationStatus == .provisional
                PermissionRow(icon: "bell.fill", title: tr("إشعارات الأدوية", "Medicine notifications"),
                              status: notificationsOn ? tr("مفعّلة", "On") : tr("متوقفة · لن تصلك التذكيرات", "Off · you won't get reminders"), ok: notificationsOn) {
                    if model.notificationStatus == .notDetermined {
                        Task {
                            await Reminders.requestPermission()
                            await model.refreshNotificationStatus()
                            await Reminders.reschedule()
                        }
                    } else {
                        Links.notificationSettings()
                    }
                }
                Toggle(isOn: $alertSound) {
                    HStack(spacing: 12) {
                        Image(systemName: "bell.badge.fill").foregroundColor(Brand.glow)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tr("صوت تنبيه موعد الدواء", "Medicine time alert sound")).style(18, .medium)
                            Text(tr("نغمة تتكرر والبطاقة تومض حتى تسجّل الجرعة، وتتوقف بعد 15 دقيقة", "A repeating chime while the card flashes until you mark the dose; stops after 15 minutes"))
                                .style(14, .regular, Brand.secondaryText)
                        }
                    }
                }
                .tint(Brand.mint)
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .glass(radius: 22)
                PermissionRow(icon: "location.fill", title: tr("موقعي مع طلب المساعدة", "My location with help requests"),
                              status: location.granted ? tr("مسموح · يصل موقعك لعائلتك على خرائط Google", "Allowed · your family sees your location on Google Maps") : tr("غير مسموح · اضغط للسماح", "Not allowed · tap to allow"),
                              ok: location.granted) {
                    if location.undetermined { location.requestPermission() } else { Links.appSettings() }
                }

                FieldLabel(tr("رقم للطوارئ", "Emergency number")).padding(.top, 10)
                EmergencyContactCard()

                FieldLabel(tr("المظهر", "Appearance")).padding(.top, 10)
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 6), spacing: 6) {
                    ForEach(ThemeAccent.allCases) { accent in
                        AccentSwatch(accent: accent, selected: Appearance.accent == accent) { model.applyAppearance(accent: accent, mode: Appearance.mode) }
                    }
                }
                Text(Appearance.accent.title).style(16, .regular, Brand.secondaryText).frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 4)
                ChipRow(items: ThemeMode.allCases, title: { $0.title }, selected: { Appearance.mode == $0 }, action: { model.applyAppearance(accent: Appearance.accent, mode: $0) })
                Toggle(isOn: Binding(get: { Appearance.largeText }, set: { model.setLargeText($0) })) {
                    HStack(spacing: 12) {
                        Image(systemName: "textformat.size").foregroundColor(Brand.glow)
                        Text(tr("خط أكبر", "Larger text")).style(18, .medium)
                    }
                }
                .tint(Brand.mint)
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .glass(radius: 22)

                FieldLabel(tr("المزامنة", "Sync")).padding(.top, 10)
                SyncCard()

                FieldLabel(tr("الحساب", "Account")).padding(.top, 10)
                DeleteAccountRow(onDeleted: onDismiss)
            }
        }
        .task { await model.refreshNotificationStatus() }
    }
}

/// حذف الحساب وكل البيانات من الخادم والجوال، بعد تأكيد. يشترطه متجرا Apple وGoogle.
private struct DeleteAccountRow: View {
    let onDeleted: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var working = false

    var body: some View {
        Button { model.present(DeleteAccountDialog(working: $working, onDeleted: onDeleted)) } label: {
            HStack(spacing: 12) {
                Image(systemName: "trash.fill").font(.system(size: 20)).foregroundColor(Brand.danger)
                VStack(alignment: .leading, spacing: 2) {
                    Text(working ? tr("جارٍ الحذف…", "Deleting…") : tr("حذف حسابي وبياناتي", "Delete my account and data")).style(18, .medium, Brand.danger)
                    Text(tr("من الخادم ومن هذا الجوال، ولا يمكن التراجع", "From the server and this phone. This can't be undone")).style(14, .regular, Brand.secondaryText)
                }
                Spacer()
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .glass(radius: 22)
        }
        .buttonStyle(PressStyle())
        .disabled(working)
    }
}

private struct DeleteAccountDialog: View {
    @Binding var working: Bool
    let onDeleted: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var error: String?

    var body: some View {
        if let error = error {
            AfiatiDialog(title: tr("لم يُحذف الحساب", "The account wasn't deleted"), text: error, confirm: tr("حسنًا", "OK"),
                         onConfirm: { model.dismissDialog() }, onDismiss: { model.dismissDialog() }) { EmptyView() }
        } else {
            AfiatiDialog(
                title: tr("حذف حسابك نهائيًا؟", "Delete your account for good?"),
                text: tr("تُحذف أدويتك وسجل جرعاتك وطلبات المساعدة وروابط العائلة من الخادم ومن هذا الجوال، ولن يرى أقاربك حالتك بعد الآن. لا يمكن استرجاعها.",
                         "Your medicines, dose history, help requests and family links are deleted from the server and this phone, and your relatives will no longer see your status. This can't be recovered."),
                confirm: working ? tr("جارٍ الحذف…", "Deleting…") : tr("حذف نهائيًا", "Delete for good"), destructive: true, dismiss: tr("إلغاء", "Cancel"),
                confirmEnabled: !working,
                onConfirm: {
                    working = true
                    Task {
                        let message = await model.deleteAccount()
                        working = false
                        if let message = message { error = message } else { model.dismissDialog(); onDeleted() }
                    }
                },
                onDismiss: { if !working { model.dismissDialog() } }
            ) { EmptyView() }
        }
    }
}

let developerSite = "https://alshaye.ai"

/// عن التطبيق: من زر «i» أعلى الشاشة، بين الإشعارات والإعدادات
struct AboutSheet: View {
    let onDismiss: () -> Void

    private var version: String {
        let info = Bundle.main.infoDictionary
        let name = info?["CFBundleShortVersionString"] as? String ?? ""
        let build = info?["CFBundleVersion"] as? String ?? ""
        return tr("الإصدار \(name) (\(build))", "Version \(name) (\(build))")
    }

    var body: some View {
        AfiatiSheet(title: tr("عن التطبيق", "About"), onDismiss: onDismiss) {
            VStack(spacing: 12) {
                Card(radius: 24, padding: 22, spacing: 8, alignment: .center) {
                    LogoView(size: 84)
                    Text(tr("عافيتي", "Afiati")).style(30, .bold)
                    Text(tr("مساعدك الأمين لمواعيد الأدوية، وطمأنينة لعائلتك.", "Your trusted companion for medicine times, and peace of mind for your family."))
                        .style(17, .regular, Brand.secondaryText).multilineTextAlignment(.center)
                    Text(version).style(14, .regular, Brand.secondaryText)
                }
                Card(radius: 24, padding: 22, spacing: 6, alignment: .center) {
                    Text(tr("تطوير", "Developed by")).style(15, .regular, Brand.secondaryText)
                    Text(tr("شايع الشايع", "Shaye Alshaye")).style(26, .bold)
                    Text(tr("مطور وخبير ذكاء اصطناعي", "Developer & AI Expert")).style(17, .regular, Brand.secondaryText).multilineTextAlignment(.center)
                    GlassButton("alshaye.ai", icon: "globe") { Links.open(developerSite) }.padding(.top, 6)
                }
            }
        }
    }
}

/// رقم يتصل به الكبير من شاشة المساعدة بضغطة، ويعمل بلا إنترنت. يبقى على الجوال فقط.
private struct EmergencyContactCard: View {
    @EnvironmentObject private var model: AppModel
    @State private var name = ""
    @State private var phone = ""
    @State private var loaded = false

    var body: some View {
        let changed = name != model.emergencyName || phone != model.emergencyPhone
        VStack(spacing: 10) {
            Text(tr("يظهر زر الاتصال به في شاشة المساعدة، ويعمل حتى بدون إنترنت.", "A button to call them appears on the help screen, and it works even without internet."))
                .style(15, .regular, Brand.secondaryText).frame(maxWidth: .infinity, alignment: .leading)
            GlassField(text: $name, placeholder: tr("الاسم (مثلًا: ابني خالد)", "Name (e.g. my son Khalid)"))
            GlassField(text: $phone, placeholder: "05xxxxxxxx", keyboard: .phonePad, ltr: true)
            GlassButton(changed ? tr("حفظ الرقم", "Save number") : tr("محفوظ", "Saved"), icon: "square.and.arrow.down", enabled: changed && phone.filter(\.isNumber).count >= 3) {
                model.saveEmergencyContact(name: name, phone: phone)
                model.toast = tr("حُفظ رقم الطوارئ", "Emergency number saved")
            }
        }
        .padding(16)
        .glass(radius: 24)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            name = model.emergencyName
            phone = model.emergencyPhone
        }
    }
}

private struct PermissionRow: View {
    let icon: String
    let title: String
    let status: String
    let ok: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 22)).foregroundColor(ok ? Brand.glow : Brand.warning).frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).style(18, .medium)
                    Text(status).style(15, .regular, ok ? Brand.secondaryText : Brand.warning)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !ok { Text(tr("تفعيل", "Turn on")).style(17, .bold, Brand.glow) }
            }
            .padding(16)
            .glass(radius: 22)
        }
        .buttonStyle(PressStyle())
        .disabled(ok)
    }
}

private struct AccentSwatch: View {
    let accent: ThemeAccent
    let selected: Bool
    let action: () -> Void

    var body: some View {
        let palette = Palette(accent: accent, light: Appearance.light)
        Button(action: action) {
            Circle()
                .fill(LinearGradient(colors: [palette.color(0x4FF0B0), palette.color(0x13684F)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .padding(5)
                .frame(width: 46, height: 46)
                .overlay(Circle().strokeBorder(selected ? Brand.text : Brand.text.opacity(0.2), lineWidth: selected ? 3 : 1))
        }
        .buttonStyle(PressStyle())
        .accessibilityLabel(accent.title)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct SyncCard: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        let sync = model.sync
        let look: (icon: String, color: Color, title: String) = {
            if sync.running { return ("arrow.triangle.2.circlepath", Brand.warning, tr("جاري المزامنة…", "Syncing…")) }
            if sync.status == .synced && sync.pending == 0 { return ("checkmark.icloud.fill", Brand.glow, tr("كل شيء متزامن", "Everything is synced")) }
            if sync.status == .failed { return ("exclamationmark.triangle.fill", Brand.danger, tr("تعذّرت المزامنة", "Sync failed")) }
            if sync.status == .notConfigured { return ("icloud.slash.fill", Brand.secondaryText, tr("المزامنة غير مفعّلة", "Sync is off")) }
            return ("icloud.slash.fill", Brand.warning, tr("بانتظار الإنترنت", "Waiting for internet"))
        }()
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Image(systemName: look.icon).font(.system(size: 22)).foregroundColor(look.color)
                Text(look.title).style(19, .bold, look.color)
            }
            Text(sync.pending > 0
                 ? tr("\(sync.pending) \((3...10).contains(sync.pending) ? "تغييرات" : "تغيير") محفوظة على جوالك وتُرفع تلقائيًا.", "\(sync.pending) \(sync.pending == 1 ? "change" : "changes") saved on your phone, uploading automatically.")
                 : tr("كل تغييراتك محفوظة على جوالك وفي الخادم.", "All your changes are saved on your phone and on the server."))
                .style(16, .regular, Brand.secondaryText)
            if let last = sync.lastSync { Text(tr("آخر مزامنة \(Format.relative(last))", "Last sync \(Format.relative(last))")).style(15, .regular, Brand.secondaryText) }
            if sync.status == .failed, let error = sync.error { Text(error).style(15, .regular, Brand.danger) }
            GlassButton(tr("زامن الآن", "Sync now"), icon: "arrow.triangle.2.circlepath", enabled: !sync.running) { model.syncNow() }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glass(radius: 24)
    }
}

/// الإشعارات الواردة من العائلة
struct NotificationsSheet: View {
    let onDismiss: () -> Void
    @EnvironmentObject private var model: AppModel

    private func look(for type: String) -> (String, Color) {
        switch type {
        case "invite_request": return ("person.badge.plus", Color(hex: 0x4D7FB5))
        case "invite_response": return ("heart", Color(hex: 0x1E8E6C))
        case "family_reminder": return ("bell.badge.fill", Color(hex: 0xB07A3F))
        default: return ("exclamationmark.triangle.fill", Color(hex: 0xBD616E))
        }
    }

    var body: some View {
        let items = model.notifications
        AfiatiSheet(title: tr("الإشعارات", "Notifications"), onDismiss: onDismiss) {
            VStack(spacing: 10) {
                if items.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "bell").font(.system(size: 44)).foregroundColor(Brand.secondaryText)
                        Text(tr("لا توجد إشعارات", "No notifications")).style(20, .bold)
                        Text(tr("هنا تظهر طلبات المتابعة وتذكيرات عائلتك.", "Follow requests and reminders from your family appear here.")).style(16, .regular, Brand.secondaryText).multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 30)
                }
                ForEach(items) { item in
                    let kind = look(for: item.type)
                    HStack(spacing: 12) {
                        IconBadge(icon: kind.0, color: kind.1, size: 46)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title).style(18, .bold)
                            Text(item.body).style(16, .regular, Brand.secondaryText)
                            Text(Format.relative(item.createdAt)).style(14, .regular, Brand.secondaryText)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        if !item.read { Circle().fill(Brand.glow).frame(width: 10, height: 10) }
                    }
                    .padding(14)
                    .glass(radius: 22, highlight: item.read ? 0.15 : 0.5)
                }
                if items.contains(where: { !$0.read }) {
                    GlassButton(tr("تحديد الكل كمقروء", "Mark all as read")) { model.markAllRead() }.padding(.top, 6)
                }
            }
        }
        .onAppear { model.refreshNotifications() }
    }
}
