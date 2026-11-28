import SwiftUI

/// رقم الإسعاف في السعودية
let ambulance = "997"
private let countdownSeconds = 5
private let red = Color(hex: 0xE5484D)
private let deepRed = Color(hex: 0x9E1C22)

/**
 بطاقة المساعدة: في صفحة «العائلة» دائمًا، وفي «اليوم» فقط أثناء طلب جارٍ.
 أُبعدت عن الشاشة الرئيسية حتى لا تُضغط بالخطأ أثناء تسجيل الدواء.
 */
struct HelpCard: View {
    let onOpen: () -> Void
    @EnvironmentObject private var model: AppModel

    var body: some View {
        let active = model.myHelp
        Button(action: onOpen) {
            HStack(spacing: 14) {
                Image(systemName: "sos")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 50, height: 50)
                    .background(LinearGradient(colors: [Color(hex: 0xFF6B6F), deepRed], startPoint: .top, endPoint: .bottom), in: Circle())
                    .shadow(color: red.opacity(0.5), radius: 10)
                VStack(alignment: .leading, spacing: 2) {
                    if let active = active {
                        if active.answered {
                            Text(tr("\(active.acknowledgedByName ?? "أحد أفراد عائلتك") \(Gender.pick(active.acknowledgedByGender, "قادم", "قادمة")) إليك", "\(active.acknowledgedByName ?? "A family member") is coming to you")).style(19, .bold)
                            Text(tr("اضغط إذا صرت بخير", "Tap when you're okay")).style(14, .regular, Brand.secondaryText)
                        } else {
                            Text(tr("أُرسل طلب المساعدة", "Help request sent")).style(19, .bold)
                            Text(tr("ننتظر رد عائلتك · اضغط للتفاصيل", "Waiting for your family · tap for details")).style(14, .regular, Brand.secondaryText)
                        }
                    } else {
                        Text(tr("أحتاج مساعدة", "I need help")).style(20, .bold)
                        Text(tr("تنبيه عاجل لكل من يتابعك من عائلتك", "An urgent alert to everyone in your family who follows you")).style(14, .regular, Brand.secondaryText)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(LinearGradient(colors: [red.opacity(active != nil ? 0.34 : 0.20), red.opacity(0.06)], startPoint: .leading, endPoint: .trailing),
                        in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(red.opacity(0.6), lineWidth: 1.5))
        }
        .buttonStyle(PressStyle())
        .padding(.horizontal, 14)
        .padding(.top, 6)
        .padding(.bottom, 12)
        .accessibilityHint(tr("طلب مساعدة من العائلة", "Ask your family for help"))
    }
}

private enum Phase { case countdown, sending, sent, failed }

/// أسباب جاهزة يختارها الكبير بضغطة بدل الكتابة
private var helpReasons: [String] {
    [tr("سقطت", "I fell"), tr("أشعر بدوخة", "I feel dizzy"), tr("ألم في الصدر", "Chest pain"), tr("ضيق في التنفس", "Shortness of breath")]
}

/**
 شاشة طلب المساعدة للكبير: عدّ تنازلي 5 ثوانٍ يمكن إلغاؤه (للضغط الخاطئ)، ثم يصل التنبيه للعائلة.
 أزرار الاتصال بالإسعاف وبرقم الطوارئ ظاهرة دائمًا، وتعمل بلا إنترنت.
 */
struct HelpScreen: View {
    let onClose: () -> Void
    @EnvironmentObject private var model: AppModel
    @ObservedObject private var location = HelpLocation.shared
    @State private var phase: Phase = .countdown
    @State private var seconds = countdownSeconds
    @State private var error: String?
    @State private var notified = -1
    @State private var resolving = false
    @State private var reason: String?
    @State private var started = false
    @State private var hadExisting = false
    // الموقع: آخر موقع معروف فورًا، ثم موقع جديد. لا يؤخر إرسال الطلب أبدًا
    @State private var known: HelpPoint?
    @State private var fresh: HelpPoint?
    @State private var sentWith: HelpPoint?
    @State private var freshSent = false
    @State private var locating = 0

    var body: some View {
        let help = model.myHelp
        FullScreen {
            ScrollView {
                VStack(spacing: 14) {
                    switch phase {
                    case .countdown:
                        Text(tr("سنرسل تنبيهًا عاجلًا لعائلتك", "We'll send an urgent alert to your family")).style(26, .bold).multilineTextAlignment(.center)
                        Text(tr("خلال ثوانٍ. إن ضغطت بالخطأ اضغط «إلغاء».", "In a few seconds. If you pressed by mistake, tap “Cancel”.")).style(18, .regular, Brand.secondaryText).multilineTextAlignment(.center)
                        Countdown(seconds: seconds)
                        CallButton(title: tr("أرسل الآن", "Send now"), icon: "sos", color: red) { send(nil) }
                        Text(tr("أو اختر ما حدث، ونرسله فورًا:", "Or pick what happened to send it right away:")).style(17, .regular, Brand.secondaryText)
                        ChipGrid(items: helpReasons, selected: { _ in false }, action: { send($0) })
                        GlassButton(tr("إلغاء، أنا بخير", "Cancel, I'm fine"), color: Brand.text, action: onClose)
                    case .sending:
                        PulseCircle(icon: "sos", calm: false)
                        Text(tr("جارٍ إرسال التنبيه…", "Sending the alert…")).style(26, .bold)
                    case .sent:
                        let answered = help?.answered == true
                        PulseCircle(icon: answered ? "figure.run" : "checkmark", calm: answered)
                        Text(sentTitle(help, answered: answered)).style(28, .bold).multilineTextAlignment(.center)
                        Text(sentBody(answered: answered)).style(18, .regular, notified == 0 && !answered ? Brand.warning : Brand.secondaryText).multilineTextAlignment(.center)
                        LocationStatus(shared: sentWith != nil || help?.locationShared == true, granted: location.granted, undetermined: location.undetermined,
                                       enabled: location.enabled, onAllow: { location.requestPermission() }, onSettings: { Links.appSettings(); locating += 1 })
                    case .failed:
                        PulseCircle(icon: "wifi.slash", calm: false)
                        Text(tr("تعذّر إرسال التنبيه", "Couldn't send the alert")).style(28, .bold)
                        Text(error ?? tr("تحقق من الإنترنت.", "Check your internet connection.")).style(18, .regular, Brand.warning).multilineTextAlignment(.center)
                        Text(tr("اتصل مباشرة بالأزرار أدناه، فهي تعمل بدون إنترنت.", "Call directly with the buttons below; they work without internet.")).style(17, .regular, Brand.secondaryText).multilineTextAlignment(.center)
                        GlassButton(tr("حاول مرة أخرى", "Try again")) { error = nil; send(reason) }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 24)
            }
            VStack(spacing: 10) {
                if !model.emergencyPhone.isEmpty {
                    let who = model.emergencyName.isEmpty ? model.emergencyPhone : model.emergencyName
                    CallButton(title: tr("اتصال بـ \(who)", "Call \(who)"), icon: "phone.fill", color: Brand.mint) { Links.dial(model.emergencyPhone) }
                }
                CallButton(title: tr("اتصال بالإسعاف \(ambulance)", "Call ambulance \(ambulance)"), icon: "crescent", color: red) { Links.dial(ambulance) }
                if phase == .sent && help != nil {
                    GlassButton(resolving ? tr("جارٍ الإرسال…", "Sending…") : tr("أنا بخير الآن", "I'm fine now"), icon: "checkmark", enabled: !resolving) {
                        resolving = true
                        Task {
                            let message = await model.resolveMyHelp()
                            resolving = false
                            if let message = message { model.toast = message } else { onClose() }
                        }
                    }
                } else if phase == .sent || phase == .failed {
                    GlassButton(tr("إغلاق", "Close"), color: Brand.text, action: onClose)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .onAppear(perform: start)
        // الموقع: يبدأ فورًا ويتجدد بعد السماح أو تشغيل الموقع
        .task(id: "\(locating)-\(location.granted)") {
            guard location.granted else { return }
            if known == nil { known = location.lastKnown() }
            fresh = await location.current()
        }
        // وصل موقع أدق بعد الإرسال، أو لم يُرسل موقع أصلًا: نرسله الآن
        .onChange(of: fresh) { _ in sendFreshIfUseful() }
        .onChange(of: model.myHelp?.id) { _ in sendFreshIfUseful() }
        // أُغلق الطلب من جهاز آخر أو بعد «أنا بخير»
        .onChange(of: model.myHelp == nil) { closed in
            if closed && phase == .sent && hadExisting { onClose() }
        }
        // حتى يصل إشعار «قادم إليك»، أو إن تأخر
        .task(id: phase == .sent) {
            guard phase == .sent else { return }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                model.refreshHelp()
            }
        }
    }

    private func start() {
        guard !started else { return }
        started = true
        if model.myHelp != nil {
            phase = .sent
            hadExisting = true
            return
        }
        // لم يُسأل عن الموقع بعد: نسأله الآن، فيصل الموقع مع الطلب
        if location.undetermined { location.requestPermission() }
        Task {
            while seconds > 0 && phase == .countdown {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if phase != .countdown { return }
                seconds -= 1
            }
            if phase == .countdown { send(reason) }
        }
    }

    private func send(_ chosen: String?) {
        guard phase == .countdown || phase == .failed else { return }
        reason = chosen ?? reason
        seconds = 0
        phase = .sending
        let point = fresh ?? known
        Task {
            do {
                let sent = try await model.sendHelp(reason: reason, point: point)
                notified = sent.notified
                sentWith = point
                hadExisting = true
                phase = .sent
            } catch {
                self.error = error.localizedDescription
                phase = .failed
            }
        }
    }

    private func sendFreshIfUseful() {
        guard let point = fresh, let help = model.myHelp, !freshSent, phase == .sent else { return }
        let previous = sentWith
        let worthIt = (previous == nil && !help.locationShared) ||
            (previous != nil && (previous?.accuracy ?? 9999) > 200 && (point.accuracy ?? 9999) < (previous?.accuracy ?? 9999) / 2)
        guard worthIt else { return }
        freshSent = true
        Task {
            do {
                _ = try await model.sendHelpLocation(help.id, point)
                sentWith = point
            } catch {
                freshSent = false
            }
        }
    }

    private func sentTitle(_ help: HelpRequest?, answered: Bool) -> String {
        if answered {
            return tr("\(help?.acknowledgedByName ?? "أحد أفراد عائلتك") \(Gender.pick(help?.acknowledgedByGender, "قادم", "قادمة")) إليك", "\(help?.acknowledgedByName ?? "A family member") is coming to you")
        }
        // لا أحد يتابعه: لا نقول «وصل» وهو لم يصل لأحد
        if notified == 0 { return tr("لم يصل التنبيه لأحد", "The alert reached no one") }
        return tr("وصل التنبيه إلى عائلتك", "The alert reached your family")
    }

    private func sentBody(answered: Bool) -> String {
        if answered {
            if model.patient?.gender == Gender.female { return tr("ابقي في مكانك واطمئني. إن ساء حالك فاتصلي بالإسعاف.", "Stay where you are and relax. If you feel worse, call an ambulance.") }
            return tr("ابق في مكانك واطمئن. إن ساء حالك فاتصل بالإسعاف.", "Stay where you are and relax. If you feel worse, call an ambulance.")
        }
        if notified == 0 {
            return tr(model.patient?.gender == Gender.female ? "لا يتابعك أحد من عائلتك بعد، فلم يصل التنبيه لأحد. اتصلي بالإسعاف أو برقم الطوارئ." : "لا يتابعك أحد من عائلتك بعد، فلم يصل التنبيه لأحد. اتصل بالإسعاف أو برقم الطوارئ.",
                      "No one in your family follows you yet, so the alert reached no one. Call an ambulance or your emergency number.")
        }
        return tr("ننتظر ردّهم. سنعيد تنبيههم كل دقيقتين حتى يرد أحدهم.", "Waiting for their reply. We'll alert them again every 2 minutes until someone answers.")
    }
}

/// حالة الموقع في شاشة «وصل التنبيه»
private struct LocationStatus: View {
    let shared: Bool
    let granted: Bool
    let undetermined: Bool
    let enabled: Bool
    let onAllow: () -> Void
    let onSettings: () -> Void

    var body: some View {
        if shared {
            HStack(spacing: 6) {
                Image(systemName: "location.fill").foregroundColor(Brand.mint)
                Text(tr("وصل موقعك لعائلتك على الخريطة", "Your location reached your family on the map")).style(17, .medium, Brand.mint)
            }
        } else if !granted {
            GlassButton(tr("أرسل موقعي لعائلتك", "Send my location to my family"), icon: "location.fill") {
                if undetermined { onAllow() } else { onSettings() }
            }
        } else if !enabled {
            GlassButton(tr("الموقع متوقف في جوالك · اضغط لتشغيله", "Location is off on your phone · tap to turn it on"), icon: "location.slash.fill", color: Brand.warning, action: onSettings)
        } else {
            Text(tr("جارٍ تحديد موقعك…", "Finding your location…")).style(16, .regular, Brand.secondaryText)
        }
    }
}

struct CallButton: View {
    let title: String
    let icon: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                AppIcon(name: icon, size: 20, weight: .semibold)
                Text(title).style(19, .bold, .white).lineLimit(1).minimumScaleFactor(0.7)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 17)
            .background(LinearGradient(colors: [color, color.opacity(0.75)], startPoint: .top, endPoint: .bottom), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: color.opacity(0.4), radius: 10)
        }
        .buttonStyle(PressStyle())
    }
}

private struct Countdown: View {
    let seconds: Int

    var body: some View {
        ZStack {
            Circle().stroke(red.opacity(0.15), lineWidth: 14)
            Circle().trim(from: 0, to: CGFloat(seconds) / CGFloat(countdownSeconds))
                .stroke(red, style: StrokeStyle(lineWidth: 14, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 1), value: seconds)
            Text("\(seconds)").font(.custom(Tajawal.name(.bold), size: 84)).foregroundColor(red)
        }
        .frame(width: 210, height: 210)
        .padding(.vertical, 18)
    }
}

/// دائرة نابضة بأيقونة الحالة
private struct PulseCircle: View {
    let icon: String
    let calm: Bool
    @State private var expanded = false

    var body: some View {
        let color = calm ? Brand.mint : red
        ZStack {
            Circle().fill(color.opacity(0.14)).frame(width: 190, height: 190).scaleEffect(expanded ? (calm ? 1.04 : 1.12) : 1)
            Image(systemName: icon)
                .font(.system(size: 56, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 128, height: 128)
                .background(LinearGradient(colors: [color.opacity(0.9), color.opacity(0.6)], startPoint: .top, endPoint: .bottom), in: Circle())
                .shadow(color: color.opacity(0.6), radius: 24)
        }
        .padding(.vertical, 24)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { expanded = true }
        }
    }
}

/// بطاقة عاجلة لفرد العائلة: قريبه يحتاج مساعدة، مع اتصال و«أنا قادم»
struct FamilyHelpCard: View {
    let help: HelpRequest
    @EnvironmentObject private var model: AppModel
    @State private var glow = false

    var body: some View {
        let answered = help.answered
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "sos").font(.system(size: 26, weight: .bold)).foregroundColor(red)
                VStack(alignment: .leading, spacing: 0) {
                    Text(tr("\(help.patientName) \(Gender.pick(help.patientGender, "يحتاج", "تحتاج")) مساعدة", "\(help.patientName) needs help")).style(22, .bold)
                    Text(Format.relative(help.createdAt)).style(15, .regular, Brand.secondaryText)
                }
            }
            if let message = help.message { Text(tr("«\(message)»", "“\(message)”")).style(18) }
            if answered {
                let me = help.acknowledgedBy != nil && help.acknowledgedBy == model.patient?.id
                let toThem = Gender.pick(help.patientGender, tr("إليه", "them"), tr("إليها", "them"))
                Text(me ? tr("أنت في طريقك \(toThem)", "You're on your way")
                     : tr("\(help.acknowledgedByName ?? "أحد أفراد العائلة") \(Gender.pick(help.acknowledgedByGender, "في طريقه", "في طريقها")) \(toThem)", "\(help.acknowledgedByName ?? "A family member") is on the way"))
                    .style(17, .medium, Brand.glow)
            }
            if let url = help.mapUrl {
                let accuracy = help.accuracy.map { tr(" · بدقة \(max(Int($0), 1)) م تقريبًا", " · accurate to about \(max(Int($0), 1)) m") } ?? ""
                GlassButton(Gender.pick(help.patientGender, tr("موقعه على خرائط Google", "Their location on Google Maps"), tr("موقعها على خرائط Google", "Their location on Google Maps")) + accuracy, icon: "map.fill") {
                    Links.open(url)
                }
            }
            HStack(spacing: 10) {
                if let phone = help.patientPhone, !phone.isEmpty {
                    CallButton(title: tr("اتصال", "Call"), icon: "phone.fill", color: Brand.mint) { Links.dial(phone) }
                }
                if !answered {
                    CallButton(title: tr("أنا قادم", "I'm coming"), icon: "figure.run", color: red) { model.acknowledgeHelp(help) }
                }
            }
        }
        .padding(18)
        .background(LinearGradient(colors: [red.opacity(0.28), red.opacity(0.10)], startPoint: .top, endPoint: .bottom), in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(red.opacity(answered ? 0.5 : (glow ? 1 : 0.45)), lineWidth: 2))
        .padding(.horizontal, 14)
        .padding(.top, 12)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) { glow = true }
        }
    }
}
