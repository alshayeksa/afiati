import CoreImage.CIFilterBuiltins
import SwiftUI

/// نافذة من صفحة العائلة
private enum FamilySheet: Identifiable {
    case invite, join
    case approval(FamilyLink)

    var id: String {
        switch self {
        case .invite: return "invite"
        case .join: return "join"
        case .approval(let link): return "approval-" + link.id
        }
    }
}

struct FamilyScreen: View {
    let header: AnyView
    let onOpenPatient: (FamilyLink) -> Void
    let onHelp: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var following = false
    @State private var sheet: FamilySheet?
    @State private var awaitingRequest = false

    var body: some View {
        let family = model.family
        let error = model.familyError
        let pending = family?.pending ?? []
        TabList(header: header) {
            BigTitle(title: tr("العائلة", "Family"), subtitle: tr("اطمئنان متبادل بموافقتك، وأنت تختار ما يرونه", "Peace of mind both ways, with your approval — you choose what they see"))
            HelpCard(onOpen: onHelp)
            HStack(spacing: 4) {
                Segment(title: tr("يتابعونني", "Following me"), selected: !following) { following = false }
                Segment(title: tr("أتابعهم", "I follow"), selected: following) { following = true }
            }
            .padding(5)
            .glass(Capsule())
            .padding(.horizontal, 14)
            .padding(.bottom, 14)
            if let error = error, family == nil { OfflineCard(message: error) }
            if !pending.isEmpty {
                Button { sheet = .approval(pending[0]) } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "person.badge.plus").font(.system(size: 26)).foregroundColor(Brand.warning)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(pending.count == 1 ? tr("\(pending[0].name) يريد متابعتك", "\(pending[0].name) wants to follow you") : tr("\(pending.count) طلبات متابعة", "\(pending.count) follow requests")).style(19, .bold)
                            Text(tr("اضغط للموافقة واختيار ما يراه", "Tap to approve and choose what they see")).style(15, .regular, Brand.secondaryText)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(18)
                    .tinted(Brand.warning, radius: 24, fill: 0.13, stroke: 0.5)
                }
                .buttonStyle(PressStyle())
                .padding(.horizontal, 14)
                .padding(.bottom, 12)
            }
            if let family = family {
                let list = following ? family.following : family.followers
                if list.isEmpty {
                    EmptyCard(
                        title: following ? tr("لا تتابع أحدًا بعد", "You don't follow anyone yet") : tr("لا أحد يتابعك بعد", "No one follows you yet"),
                        message: following ? tr("اطلب من قريبك رمز الدعوة من تطبيقه، ثم أدخله هنا لتطمئن على أدويته.", "Ask your relative for the invite code from their app, then enter it here to check on their medicines.")
                            : tr("أرسل رمز دعوة لابنك أو ابنتك، فيطمئنون عليك ويصلهم تنبيه إذا فاتتك جرعة.", "Send an invite code to your son or daughter so they can check on you and get an alert if you miss a dose.")
                    )
                } else {
                    ForEach(list) { link in
                        PersonRow(link: link, following: following) { if following { onOpenPatient(link) } }
                    }
                }
            } else if error == nil {
                ProgressView().tint(Brand.glow).scaleEffect(1.3).frame(maxWidth: .infinity).padding(30)
            }
            VStack(spacing: 10) {
                if following {
                    PrimaryButton(tr("متابعة قريب برمز", "Follow a relative with a code"), icon: "person.badge.plus") { sheet = .join }
                } else {
                    PrimaryButton(tr("دعوة فرد من العائلة", "Invite a family member"), icon: "square.and.arrow.up") { sheet = .invite }
                }
                GlassButton(tr("تحديث", "Refresh"), icon: "arrow.clockwise", enabled: !model.familyLoading) { model.refreshFamily() }
            }
            .padding(.horizontal, 14)
            .padding(.top, 16)
        }
        .onAppear { model.refreshFamily() }
        // فُتحت من إشعار طلب متابعة: نغلق النوافذ ونعرض الطلب حين يصل
        .onChange(of: model.familyFocus) { _ in
            sheet = nil
            following = false
            awaitingRequest = true
            model.refreshFamily()
        }
        .onChange(of: model.familyLoading) { loading in
            if awaitingRequest && !loading && model.family != nil {
                if let first = model.family?.pending.first { sheet = .approval(first) }
                awaitingRequest = false
            }
        }
        .afiatiSheet(item: $sheet) { item in
            switch item {
            case .invite: InviteSheet(onDismiss: { sheet = nil })
            case .join: JoinSheet(onDismiss: { sheet = nil })
            case .approval(let link): ApprovalSheet(request: link, onDismiss: { sheet = nil })
            }
        }
    }
}

private struct Segment: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).style(18, selected ? .bold : .medium, selected ? Brand.text : Brand.secondaryText)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(selected ? AnyView(LinearGradient(colors: [Brand.mint.opacity(0.75), Brand.deepGreen.opacity(0.85)], startPoint: .top, endPoint: .bottom)) : AnyView(Color.clear))
                .clipShape(Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(PressStyle())
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}

struct OfflineCard: View {
    let message: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "wifi.slash").font(.system(size: 24)).foregroundColor(Brand.warning)
            Text(message).style(17).frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(18)
        .glass(radius: 24)
        .padding(.horizontal, 14)
        .padding(.bottom, 12)
    }
}

private struct PersonRow: View {
    let link: FamilyLink
    let following: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                AvatarView(name: link.name, id: link.id, size: 52)
                VStack(alignment: .leading, spacing: 2) {
                    Text(link.name).style(20, .bold).lineLimit(1)
                    Text(following ? tr("اضغط لترى أدويته اليوم", "Tap to see today's medicines") : tr("يرى: \(Privacy.title(link.privacyLevel))", "Sees: \(Privacy.title(link.privacyLevel))"))
                        .style(15, .regular, Brand.secondaryText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if following { Image(systemName: "arrow.forward").foregroundColor(Brand.glow) }
            }
            .padding(14)
            .glass(radius: 24)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .disabled(!following)
        .padding(.horizontal, 14)
        .padding(.bottom, 10)
    }
}

/// دعوة فرد من العائلة: رمز من 6 أرقام صالح 24 ساعة، مع رمز QR ومشاركة
struct InviteSheet: View {
    let onDismiss: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var invitation: Invitation?
    @State private var error: String?
    @State private var loading = false
    @State private var copied = false

    private func generate() {
        loading = true
        Task {
            do {
                invitation = try await FamilyApi.createInvitation()
                error = nil
            } catch {
                self.error = error.localizedDescription
            }
            loading = false
        }
    }

    var body: some View {
        let code = invitation?.code
        let name = model.patient?.fullName ?? ""
        let message = tr("السلام عليكم، أدعوك لمتابعة أدويتي في تطبيق عافيتي لتطمئن علي.\nرمز الدعوة: \(code ?? "")\n(صالح لمدة 24 ساعة)",
                         "Hello! I'm inviting you to follow my medicines in the Afiati app so you can check on me.\nInvite code: \(code ?? "")\n(valid for 24 hours)")
        AfiatiSheet(title: tr("دعوة فرد من العائلة", "Invite a family member"), onDismiss: onDismiss) {
            VStack(spacing: 12) {
                Text(tr("أرسل الرمز لابنك أو ابنتك. يفتح عافيتي في جواله ويدخله في «العائلة ← أتابعهم»، ثم توافق أنت وتختار ما يراه.",
                        "Send the code to your son or daughter. They open Afiati on their phone and enter it under “Family → I follow”, then you approve and choose what they see."))
                    .style(17, .regular, Brand.secondaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                VStack(spacing: 8) {
                    if let code = code {
                        HStack(spacing: 6) {
                            ForEach(Array(code.enumerated()), id: \.offset) { _, digit in
                                Text(String(digit)).style(30, .bold, Brand.glow).frame(width: 44, height: 58).glass(radius: 14, highlight: 0.45)
                            }
                        }
                        .environment(\.layoutDirection, .leftToRight)
                        Text(tr("صالح لمدة 24 ساعة · \(name.isEmpty ? "رمزك" : name)", "Valid for 24 hours · \(name.isEmpty ? "your code" : name)")).style(15, .regular, Brand.secondaryText)
                    } else if let error = error {
                        Text(error).style(17, .regular, Brand.warning).multilineTextAlignment(.center).padding(.horizontal, 16)
                    } else {
                        ProgressView().tint(Brand.glow).scaleEffect(1.3)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 22)
                .glass(radius: 26)
                if let code = code {
                    ShareLink(item: message) {
                        HStack(spacing: 10) {
                            Image(systemName: "square.and.arrow.up").font(.system(size: 20, weight: .semibold))
                            Text(tr("مشاركة الرمز", "Share the code")).style(19, .bold, Brand.ink)
                        }
                        .foregroundColor(Brand.ink)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 17)
                        .background(LinearGradient(colors: [Brand.glow, Brand.mint], startPoint: .top, endPoint: .bottom), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                        .shadow(color: Brand.glow.opacity(0.35), radius: 12, y: 2)
                    }
                    GlassButton(copied ? tr("تم النسخ", "Copied") : tr("نسخ الرمز", "Copy code"), icon: copied ? "checkmark" : "doc.on.doc") {
                        UIPasteboard.general.string = code
                        copied = true
                        Task {
                            try? await Task.sleep(nanoseconds: 2_000_000_000)
                            copied = false
                        }
                    }
                    if let image = QRCode.image(code) {
                        FieldLabel(tr("أو امسح الرمز بكاميرا الجوال الآخر", "Or scan the code with the other phone's camera")).padding(.top, 6)
                        Image(uiImage: image)
                            .interpolation(.none)
                            .resizable()
                            .frame(width: 176, height: 176)
                            .padding(12)
                            .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                            .accessibilityLabel(tr("رمز QR للدعوة", "Invite QR code"))
                    }
                }
                GlassButton(tr("رمز جديد", "New code"), icon: "arrow.clockwise", enabled: !loading) { generate() }
            }
        }
        .onAppear { if invitation == nil && !loading { generate() } }
    }
}

enum QRCode {
    static func image(_ text: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)),
              let image = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: image)
    }
}

/// إدخال رمز دعوة من قريب
struct JoinSheet: View {
    let onDismiss: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var code = ""
    @State private var error: String?
    @State private var sending = false
    @State private var done: String?
    @FocusState private var focused: Bool

    private func submit() {
        guard code.count == 6 else { error = tr("أدخل الأرقام الستة كاملة.", "Enter all six digits."); return }
        sending = true
        Task {
            do {
                let result = try await FamilyApi.join(code)
                let name = result.patientName.isEmpty ? tr("صاحب الدعوة", "the inviter") : result.patientName
                done = result.status == "active" ? tr("أنت تتابع \(name) مسبقًا.", "You already follow \(name).") : tr("أُرسل طلبك. بانتظار موافقة \(name).", "Request sent. Waiting for \(name) to approve.")
                model.refreshFamily()
            } catch {
                self.error = error.localizedDescription
            }
            sending = false
        }
    }

    var body: some View {
        AfiatiSheet(title: tr("متابعة قريب", "Follow a relative"), closeTitle: done != nil ? tr("تم", "Done") : tr("إلغاء", "Cancel"), onDismiss: onDismiss) {
            VStack(spacing: 14) {
                if let done = done {
                    VStack(spacing: 10) {
                        Image(systemName: "checkmark").font(.system(size: 34, weight: .bold)).foregroundColor(Brand.ink)
                            .frame(width: 72, height: 72)
                            .background(LinearGradient(colors: [Brand.glow, Brand.mint], startPoint: .top, endPoint: .bottom), in: Circle())
                        Text(done).style(20, .bold).multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 20)
                    PrimaryButton(tr("حسنًا", "OK"), action: onDismiss)
                } else {
                    Text(tr("اكتب رمز الدعوة المكوّن من 6 أرقام الذي أرسله لك قريبك.", "Type the 6-digit invite code your relative sent you.")).style(17, .regular, Brand.secondaryText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    ZStack {
                        TextField("", text: Binding(get: { code }, set: { value in code = String(value.filter(\.isNumber).prefix(6)); error = nil }))
                            .keyboardType(.numberPad)
                            .textContentType(.oneTimeCode)
                            .focused($focused)
                            .foregroundColor(.clear)
                            .tint(.clear)
                            .frame(width: 1, height: 1)
                            .opacity(0.02)
                        HStack(spacing: 8) {
                            ForEach(0..<6, id: \.self) { index in
                                let characters = Array(code)
                                Text(index < characters.count ? String(characters[index]) : "").style(32, .bold, Brand.glow)
                                    .frame(width: 48, height: 64)
                                    .glass(radius: 16, highlight: index == code.count ? 0.7 : 0.25)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { focused = true }
                    }
                    .environment(\.layoutDirection, .leftToRight)
                    .frame(maxWidth: .infinity)
                    if let error = error { Text(error).style(17, .medium, Brand.danger) }
                    PrimaryButton(sending ? tr("جارٍ الإرسال…", "Sending…") : tr("إرسال طلب المتابعة", "Send follow request"), icon: "person.badge.plus",
                                  enabled: !sending && code.count == 6) { submit() }
                }
            }
        }
        // لوحة الأرقام تظهر وحدها عند فتح النافذة، فلا يحتاج الكبير للبحث عن مكان الكتابة
        .task {
            try? await Task.sleep(nanoseconds: 450_000_000)
            focused = true
        }
    }
}

/// الموافقة على طلب متابعة واختيار مستوى الخصوصية
struct ApprovalSheet: View {
    let request: FamilyLink
    let onDismiss: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var level = Privacy.full
    @State private var sending = false
    @State private var error: String?

    private func answer(_ approve: Bool) {
        sending = true
        Task {
            let message = await model.respond(caregiverId: request.caregiverId, approve: approve, level: level)
            sending = false
            if let message = message {
                error = message
            } else {
                model.toast = approve ? tr("\(request.name) يتابعك الآن", "\(request.name) now follows you") : tr("رُفض الطلب", "Request declined")
                onDismiss()
            }
        }
    }

    var body: some View {
        AfiatiSheet(title: nil, onDismiss: onDismiss) {
            VStack(spacing: 8) {
                AvatarView(name: request.name, id: request.id, size: 80)
                Text(tr("\(request.name) يريد متابعة أدويتك", "\(request.name) wants to follow your medicines")).style(24, .bold).multilineTextAlignment(.center)
                Text(tr("اختر ما يراه من أدويتك.", "Choose what they can see.")).style(17, .regular, Brand.secondaryText)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 10)
            VStack(spacing: 10) {
                ForEach([Privacy.full, Privacy.statusOnly, Privacy.alertsOnly], id: \.self) { option in
                    let selected = level == option
                    Button { level = option } label: {
                        HStack(spacing: 14) {
                            Circle()
                                .strokeBorder(selected ? Brand.glow : Brand.text.opacity(0.3), lineWidth: 2)
                                .background(Circle().fill(selected ? Brand.glow : .clear).padding(5))
                                .frame(width: 26, height: 26)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(Privacy.title(option)).style(19, .bold)
                                Text(Privacy.body(option)).style(15, .regular, Brand.secondaryText)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(18)
                        .glass(radius: 22, highlight: selected ? 0.7 : 0.2)
                    }
                    .buttonStyle(PressStyle())
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
                if let error = error { Text(error).style(17, .medium, Brand.danger) }
                PrimaryButton(tr("موافق", "Approve"), icon: "checkmark", enabled: !sending) { answer(true) }
                GlassButton(tr("رفض", "Decline"), color: Brand.danger, enabled: !sending) { answer(false) }
            }
            .padding(.top, 18)
        }
    }
}

/// شاشة متابعة قريب: أدويته اليوم حسب ما سمح به، وزر تذكير لطيف
struct PatientScreen: View {
    let link: FamilyLink
    let onClose: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var day: PatientDay?
    @State private var error: String?
    @State private var loading = false
    @State private var reminding = false
    @State private var now = Date()

    private func load() {
        loading = true
        Task {
            let today = Day.today()
            do {
                day = try await FamilyApi.patientDay(link.patientId, from: today.start, to: today.adding(days: 1).start)
                error = nil
            } catch {
                self.error = error.localizedDescription
            }
            loading = false
        }
    }

    var body: some View {
        FullScreen {
            ScrollView {
                VStack(spacing: 0) {
                    HStack {
                        CircleIconButton(icon: "arrow.backward", label: tr("رجوع", "Back"), action: onClose)
                        Spacer()
                        CircleIconButton(icon: "arrow.clockwise", label: tr("تحديث", "Refresh"), color: Brand.glow, enabled: !loading) { load() }
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 10)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(tr("أدوية اليوم", "Today's medicines")).style(18, .medium, Brand.glow)
                        Text(day?.patientName ?? link.name).style(38, .bold)
                        Text(Format.gregorianDay()).style(16, .regular, Brand.secondaryText)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
                    .padding(.bottom, 10)
                    content
                }
                .padding(.bottom, 24)
            }
            PrimaryButton(reminding ? tr("جارٍ الإرسال…", "Sending…") : tr("إرسال تذكير لطيف", "Send a gentle reminder"), icon: "bell.badge.fill", enabled: !reminding) {
                reminding = true
                Task {
                    do {
                        try await FamilyApi.remind(link.patientId)
                        model.toast = tr("وصل تذكيرك إلى \(link.name)", "Your reminder reached \(link.name)")
                    } catch {
                        model.toast = error.localizedDescription
                    }
                    reminding = false
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .onAppear { load() }
        .modifier(NowTicker(now: $now))
    }

    @ViewBuilder
    private var content: some View {
        if let current = day {
            if current.privacyLevel == Privacy.alertsOnly {
                EmptyCard(title: tr("تنبيهات التأخير فقط", "Missed-dose alerts only"),
                          message: tr("اختار \(link.name) أن تصلك تنبيهات عند فوات جرعة، دون عرض تفاصيل أدويته.", "\(link.name) chose to send you alerts when a dose is missed, without showing medicine details."))
            } else {
                let logs = current.logs
                let done = logs.filter { $0.status == DoseStatus.taken }.count
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tr("التزامه اليوم", "Their adherence today")).style(17, .regular, Brand.secondaryText)
                        Text(logs.isEmpty ? tr("لا جرعات اليوم", "No doses today") : tr("أخذ \(done) من \(logs.count)", "Took \(done) of \(logs.count)")).style(24, .bold)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    ProgressRing(progress: logs.isEmpty ? 0 : Double(done) / Double(logs.count), size: 96) {
                        Text(logs.isEmpty ? "—" : tr("\(done * 100 / logs.count)٪", "\(done * 100 / logs.count)%")).style(22, .bold)
                    }
                }
                .padding(18)
                .glass(radius: 26)
                .padding(.horizontal, 14)
                .padding(.bottom, 14)
                ForEach(logs) { log in
                    let shown: DoseLog = {
                        var copy = log
                        if current.privacyLevel == Privacy.statusOnly || log.medicationName.isEmpty { copy.medicationName = tr("جرعة دواء", "Medicine dose") }
                        return copy
                    }()
                    DoseRow(log: shown, state: doseState(log, now: now), onTake: nil) {}
                }
            }
        } else if let error = error {
            OfflineCard(message: error)
        } else {
            ProgressView().tint(Brand.glow).scaleEffect(1.3).frame(maxWidth: .infinity).padding(40)
        }
    }
}
