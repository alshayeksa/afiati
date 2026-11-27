import SwiftUI

/// حالة الجرعة كما يراها المستخدم
enum DoseState { case taken, due, late, missed, upcoming }

func doseState(_ log: DoseLog, now: Date, takenOverride: Bool = false) -> DoseState {
    if takenOverride || log.status == DoseStatus.taken { return .taken }
    if log.status == DoseStatus.missed { return .missed }
    guard let at = Iso.parse(log.scheduledAt) else { return .upcoming }
    let minutes = now.timeIntervalSince(at) / 60
    if minutes < -30 { return .upcoming }
    if minutes <= 60 { return .due }
    return .late
}

/// فترات اليوم لتجميع الجرعات
private enum Period: Int, CaseIterable {
    case morning, noon, evening, night

    var title: String {
        switch self {
        case .morning: return tr("الصباح", "Morning")
        case .noon: return tr("الظهر", "Noon")
        case .evening: return tr("المساء", "Evening")
        case .night: return tr("الليل", "Night")
        }
    }

    var icon: String {
        switch self {
        case .morning: return "sun.max"
        case .noon: return "sun.max.fill"
        case .evening: return "sunset"
        case .night: return "moon.stars"
        }
    }

    static func of(_ log: DoseLog) -> Period {
        let hour = Iso.parse(log.scheduledAt).map { Day.calendar.component(.hour, from: $0) } ?? 8
        switch hour {
        case 4...11: return .morning
        case 12...16: return .noon
        case 17...20: return .evening
        default: return .night
        }
    }
}

/// الوقت الحالي، يتحدث كل نصف دقيقة لتتغير الحالات «موعدها الآن» و«متأخرة» وحدها
struct NowTicker: ViewModifier {
    @Binding var now: Date

    func body(content: Content) -> some View {
        content.task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                now = Date()
            }
        }
    }
}

struct TodayScreen: View {
    let header: AnyView
    let onAdd: () -> Void
    let onOpenMedication: (Medication) -> Void
    let onHelp: () -> Void
    let onVitals: () -> Void
    @EnvironmentObject private var model: AppModel
    @State private var now = Date()

    private func take(_ log: DoseLog) {
        if model.isEarly(log, now: now) {
            model.present(AfiatiDialog(
                title: tr("موعدها لم يحن بعد", "It's not time yet"),
                text: tr("جرعة \(log.medicationName) موعدها \(Format.isoTime(log.scheduledAt)). هل أخذتها فعلًا الآن؟",
                         "\(log.medicationName) is due at \(Format.isoTime(log.scheduledAt)). Did you really take it now?"),
                confirm: tr("نعم، أخذتها", "Yes, I took it"), dismiss: tr("لا", "No"),
                onConfirm: { model.dismissDialog(); model.takeDose(log) },
                onDismiss: { model.dismissDialog() }
            ))
        } else {
            model.takeDose(log)
        }
    }

    var body: some View {
        let logs = model.todayLogs
        let medications = model.medications
        TabList(header: header) {
            Greeting(onVitals: onVitals)
            // موجة النبض: هوية عافيتي، مدمجة تحت التحية
            PulseWave().frame(height: 84).padding(.top, 2)
            // زر المساعدة في صفحة «العائلة» حتى لا يُضغط بالخطأ؛ هنا يظهر فقط أثناء طلب جارٍ
            if model.myHelp != nil { HelpCard(onOpen: onHelp) }

            if medications.isEmpty && logs.isEmpty {
                EmptyCard(
                    title: tr("أضف دواءك الأول", "Add your first medicine"),
                    message: tr("اكتب اسم الدواء ومواعيده، وسنذكّرك بكل جرعة في وقتها حتى بدون إنترنت.",
                                "Type the medicine name and times, and we'll remind you of every dose on time, even offline."),
                    action: tr("إضافة دواء", "Add medicine"), onAction: onAdd
                )
            } else {
                ReminderHealthCard()
                // جرعات اليوم أولًا: هي ما يحتاج الكبير أن يسجّله
                SectionTitle(text: tr("جرعات اليوم", "Today's doses"), top: 0)
                if logs.isEmpty {
                    EmptyCard(title: tr("لا جرعات اليوم", "No doses today"),
                              message: tr("أدويتك الأسبوعية أو الشهرية ليس لها موعد اليوم.", "Your weekly or monthly medicines aren't scheduled today."))
                }
                ForEach(Period.allCases, id: \.self) { period in
                    let group = logs.filter { Period.of($0) == period }
                    if !group.isEmpty {
                        HStack(spacing: 8) {
                            Image(systemName: period.icon).foregroundColor(Brand.glow).font(.system(size: 17))
                            Text(period.title).style(17, .bold, Brand.secondaryText)
                            Spacer()
                        }
                        .padding(.horizontal, 22)
                        .padding(.top, 4)
                        .padding(.bottom, 8)
                        ForEach(group) { log in
                            DoseRow(log: log, state: doseState(log, now: now, takenOverride: model.isTaken(log)), onTake: { take(log) }) {
                                if let medication = medications.first(where: { $0.id == log.medicationId }) { onOpenMedication(medication) }
                            }
                        }
                    }
                }
                ForEach(medications.filter(\.lowStock)) { medication in
                    LowStockCard(medication: medication) { onOpenMedication(medication) }
                }
                // التزام اليوم في الآخر
                ProgressCard(logs: logs, now: now)
            }
        }
        .modifier(NowTicker(now: $now))
    }
}

private struct Greeting: View {
    let onVitals: () -> Void
    @EnvironmentObject private var model: AppModel

    var body: some View {
        let sync = model.sync
        let status: (Color, String)? = {
            if sync.running { return nil }
            if sync.status == .failed { return (Brand.danger, tr("محفوظ على جوالك · تعذّرت المزامنة", "Saved on your phone · sync failed")) }
            if sync.status == .offline && sync.pending > 0 { return (Brand.warning, tr("محفوظ على جوالك · يُرفع عند عودة الإنترنت", "Saved on your phone · uploads when you're back online")) }
            if sync.status == .offline { return (Brand.warning, tr("بدون إنترنت · كل شيء يعمل", "Offline · everything still works")) }
            return nil
        }()
        let list = Lang.english ? model.greetings.english : model.greetings.arabic
        let greeting = Greetings.pick(list, epochDay: model.today.epochDay).trimmingCharacters(in: CharacterSet(charactersIn: ".، "))
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                // الاسم في الشريط العلوي، فهنا تحية اليوم من جدول التحيات
                Text(greeting).style(25, .bold, Brand.glow)
                Text("\(Format.gregorianDay(model.today)) · \(Format.hijriDay(model.today))").style(15, .regular, Brand.secondaryText)
                // في الحالة العادية لا داعي لسطر «محفوظ»؛ يظهر فقط حين يحتاج انتباهًا
                if let status = status { StatusLine(color: status.0, text: status.1).padding(.top, 4) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // رابط صغير للعلامات الحيوية، يسار التحية
            VitalsLink(onOpen: onVitals)
        }
        .padding(.leading, 20)
        .padding(.trailing, 14)
        .padding(.top, 16)
    }
}

private struct ProgressCard: View {
    let logs: [DoseLog]
    let now: Date
    @EnvironmentObject private var model: AppModel

    var body: some View {
        let total = logs.count
        let done = logs.filter { model.isTaken($0) }.count
        let late = logs.filter { log in
            let state = doseState(log, now: now, takenOverride: model.isTaken(log))
            return state == .late || state == .missed
        }.count
        let message: String = {
            if done == total { return tr("ممتاز! أكملت جرعات اليوم", "Excellent! You've completed today's doses") }
            if late > 0 { return late == 1 ? tr("جرعة واحدة متأخرة", "One dose is late") : tr("\(late) جرعات متأخرة", "\(late) doses are late") }
            if done == 0 { return tr("يومك يبدأ الآن", "Your day starts now") }
            return tr("أحسنت، استمر", "Well done, keep going")
        }()
        if total > 0 {
            VStack(spacing: 10) {
                HStack {
                    Text(message).style(18, .bold)
                    Spacer()
                    Text(tr("أخذت \(done) من \(total)", "Took \(done) of \(total)")).style(16, .medium, late > 0 ? Brand.warning : Brand.glow)
                }
                // شريط التقدم: يمتلئ من بداية السطر مع اتجاه القراءة
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Brand.text.opacity(0.08))
                        Capsule().fill(LinearGradient(colors: [Brand.glow, Brand.mint], startPoint: .leading, endPoint: .trailing))
                            .frame(width: proxy.size.width * CGFloat(done) / CGFloat(total))
                    }
                }
                .frame(height: 10)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .glass(radius: 22)
            .padding(.horizontal, 14)
            .padding(.top, 6)
            .padding(.bottom, 14)
        }
    }
}

private func inTime(_ minutes: Int) -> String {
    if minutes < 60 { return tr("بعد \(max(minutes, 1)) دقيقة", "in \(max(minutes, 1)) min") }
    if minutes < 120 { return tr("بعد ساعة", "in an hour") }
    if minutes < 180 { return tr("بعد ساعتين", "in 2 hours") }
    if minutes < 660 { return tr("بعد \(minutes / 60) ساعات", "in \(minutes / 60) hours") }
    return tr("بعد \(minutes / 60) ساعة", "in \(minutes / 60) hours")
}

/// تنبيه إذا كانت الإشعارات متوقفة، لأن التذكيرات لن تظهر بدونها
private struct ReminderHealthCard: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        if model.notificationStatus == .denied || model.notificationStatus == .notDetermined {
            Button {
                if model.notificationStatus == .notDetermined {
                    Task {
                        await Reminders.requestPermission()
                        await model.refreshNotificationStatus()
                        await Reminders.reschedule()
                    }
                } else {
                    Links.notificationSettings()
                }
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "bell.slash.fill").font(.system(size: 24)).foregroundColor(Brand.danger)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tr("الإشعارات متوقفة", "Notifications are off")).style(18, .bold)
                        Text(tr("لن تصلك تذكيرات الأدوية. اضغط هنا لتفعيلها.", "You won't get medicine reminders. Tap here to turn them on.")).style(15, .regular, Brand.secondaryText)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(16)
                .tinted(Brand.danger)
            }
            .buttonStyle(PressStyle())
            .padding(.horizontal, 14)
            .padding(.bottom, 12)
        }
    }
}

private struct LowStockCard: View {
    let medication: Medication
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 12) {
                Image(systemName: "shippingbox.fill").font(.system(size: 24)).foregroundColor(Brand.warning)
                VStack(alignment: .leading, spacing: 2) {
                    Text(tr("\(medication.name) قارب على النفاد", "\(medication.name) is running low")).style(18, .bold).lineLimit(1)
                    Text(tr("باقي \(medication.stockCount ?? 0) · اضغط إذا اشتريت علبة جديدة", "\(medication.stockCount ?? 0) left · tap if you bought a new box")).style(15, .regular, Brand.secondaryText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(16)
            .tinted(Brand.warning)
        }
        .buttonStyle(PressStyle())
        .padding(.horizontal, 14)
        .padding(.bottom, 12)
    }
}

/// بطاقة جرعة: الدواء والوقت والحالة، وزر تسجيل كبير
struct DoseRow: View {
    let log: DoseLog
    let state: DoseState
    let onTake: (() -> Void)?
    let onClick: () -> Void

    var body: some View {
        let time = Format.isoTime(log.scheduledAt)
        let parts = time.split(separator: " ", maxSplits: 1).map(String.init)
        let taken = state == .taken
        Button(action: onClick) {
            HStack(spacing: 0) {
                VStack(spacing: 0) {
                    Text(parts.first ?? time).style(21, .bold, taken ? Brand.secondaryText : Brand.text)
                    Text(parts.count > 1 ? parts[1] : "").style(14, .regular, Brand.secondaryText)
                }
                .frame(width: 64)
                Rectangle().fill(Brand.text.opacity(0.12)).frame(width: 1, height: 40).padding(.horizontal, 10)
                VStack(alignment: .leading, spacing: 1) {
                    Text(log.medicationName).style(19, .bold, taken ? Brand.secondaryText : Brand.text).lineLimit(1)
                    if taken {
                        Text(Format.dose(log.doseAmount, log.doseUnit)).style(15, .regular, Brand.secondaryText).lineLimit(1)
                        // تهنئة صغيرة مميزة داخل مربع الدواء نفسه
                        Text(tr("أحسنت · أخذتها", "Well done · taken") + (log.takenAt.map { " " + Format.isoTime($0) } ?? ""))
                            .style(13, .bold, Brand.mint).lineLimit(1)
                    } else {
                        Text("\(Format.dose(log.doseAmount, log.doseUnit)) · \(stateText)").style(15, .regular, stateColor(state)).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                CheckButton(taken: taken, name: log.medicationName, onTake: onTake).padding(.leading, 8)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .glass(radius: 24, highlight: state == .due ? 0.5 : 0.22)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .padding(.horizontal, 14)
        .padding(.bottom, 10)
        .accessibilityHint(tr("تفاصيل \(log.medicationName)", "Details of \(log.medicationName)"))
    }

    private var stateText: String {
        switch state {
        case .taken: return log.takenAt.map { tr("أُخذت \(Format.isoTime($0))", "Taken \(Format.isoTime($0))") } ?? tr("أُخذت", "Taken")
        case .due: return tr("موعدها الآن", "Due now")
        case .late: return tr("متأخرة", "Late")
        case .missed: return tr("فاتت", "Missed")
        case .upcoming:
            if let at = Iso.parse(log.scheduledAt) {
                let minutes = Int(at.timeIntervalSinceNow / 60)
                if (0...(12 * 60)).contains(minutes) { return inTime(minutes) }
            }
            return tr("لم يحن موعدها", "Not yet due")
        }
    }
}

private struct CheckButton: View {
    let taken: Bool
    let name: String
    let onTake: (() -> Void)?

    var body: some View {
        if taken {
            Image(systemName: "checkmark")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(Brand.ink)
                .frame(width: 54, height: 54)
                .background(LinearGradient(colors: [Brand.glow, Brand.mint], startPoint: .top, endPoint: .bottom), in: Circle())
                .shadow(color: Brand.glow.opacity(0.4), radius: 8)
                .accessibilityLabel(tr("أُخذت", "Taken"))
        } else if let onTake = onTake {
            Button(action: onTake) {
                Image(systemName: "checkmark")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(Brand.glow)
                    .frame(width: 54, height: 54)
                    .glass(Circle(), highlight: 0.45)
            }
            .buttonStyle(PressStyle())
            .accessibilityLabel(tr("تسجيل أخذ \(name)", "Mark \(name) as taken"))
        } else {
            Image(systemName: "pills.fill")
                .font(.system(size: 20))
                .foregroundColor(Brand.secondaryText)
                .frame(width: 54, height: 54)
                .glass(Circle(), highlight: 0.12)
        }
    }
}

/// لون نص الحالة لاستخدامه خارج هذه الشاشة
func stateColor(_ state: DoseState) -> Color {
    switch state {
    case .taken, .due: return Brand.glow
    case .late: return Brand.warning
    case .missed: return Brand.danger
    case .upcoming: return Brand.secondaryText
    }
}
