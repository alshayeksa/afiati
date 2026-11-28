import Foundation
import SwiftUI
import UIKit
import UserNotifications

/// حالة المزامنة كما تظهر في الواجهة
struct SyncUi {
    var status: SyncStatus?
    var pending = 0
    var lastSync: String?
    var error: String?
    var running = false
}

/// ما يُفتح من إشعار
enum OpenTarget: Equatable {
    case family, notifications, help
    case patient(String)
}

/**
 حالة التطبيق كلها في مكان واحد: الملف والأدوية والجرعات والعائلة والمزامنة.
 الواجهة تتحدث تلقائيًا لأن القيم منشورة (@Published).
 */
@MainActor
final class AppModel: ObservableObject {
    static let shared = AppModel()

    /// نافذة التراجع بعد تسجيل جرعة
    static let undoSeconds: UInt64 = 5

    @Published private(set) var loaded = false
    @Published private(set) var patient: Patient?
    @Published private(set) var medications: [Medication] = []
    @Published private(set) var todayLogs: [DoseLog] = []
    /// آخر 30 يومًا، للسجل والإحصاءات
    @Published private(set) var historyLogs: [DoseLog] = []
    @Published private(set) var today = Day.today()

    /// جرعة سُجّلت للتو وما زال يمكن التراجع عنها
    @Published private(set) var pendingTake: DoseLog?
    private var takeTask: Task<Void, Never>?

    @Published private(set) var sync = SyncUi()

    @Published private(set) var family: FamilyOverview?
    @Published private(set) var familyError: String?
    @Published private(set) var familyLoading = false
    @Published var notifications: [NotificationItem] = []
    var unreadCount: Int { notifications.filter { !$0.read }.count }

    /// يتغير كلما عاد التطبيق للواجهة، لإعادة قراءة الأذونات
    @Published private(set) var resumeTick = 0
    /// إذن الإشعارات كما قرأناه آخر مرة
    @Published private(set) var notificationStatus: UNAuthorizationStatus = .authorized

    // زر المساعدة
    /// طلبي النشط إن وُجد
    @Published private(set) var myHelp: HelpRequest?
    /// طلبات مساعدة نشطة ممن أتابعهم
    @Published private(set) var familyHelp: [HelpRequest] = []

    /// رقم للطوارئ يُحفظ على الجوال فقط، للاتصال المباشر ولو بلا إنترنت
    @Published private(set) var emergencyName = UserDefaults.standard.string(forKey: "emergency.name") ?? ""
    @Published private(set) var emergencyPhone = UserDefaults.standard.string(forKey: "emergency.phone") ?? ""

    /// تحيات اليوم
    @Published private(set) var greetings = Greetings.saved()

    /// إيقاف الخدمة أو تحديث إلزامي من صفحة التحكم
    @Published private(set) var control = AppControl.saved()

    /// يتغير مع اللغة أو المظهر، فتُعاد بناء الواجهة كلها
    @Published private(set) var languageVersion = 0
    @Published private(set) var themeVersion = 0
    /// يزيد بعد كل حفظ أو حذف قراءة، فتتحدث بطاقات العلامات الحيوية
    @Published var vitalsVersion = 0

    /// فتح شاشة بعينها من إشعار
    @Published var pendingOpen: OpenTarget?
    /// يزيد عند فتح العائلة من إشعار طلب متابعة: تُغلق نوافذها ويظهر الطلب
    @Published var familyFocus = 0
    /// رسالة قصيرة تظهر أسفل الشاشة
    @Published var toast: String?
    /// التبويب الحالي (يبقى بعد تغيير اللغة أو المظهر)
    @Published var tab: Tab = .today
    /// نافذة تأكيد فوق كل الشاشة
    @Published var dialog: PresentedDialog?

    private var running = false

    private init() {
        reload()
        loaded = true
        Task {
            if let fresh = await Greetings.refresh() { greetings = fresh }
        }
        if patient != nil { afterChange(sync: true) }
    }

    // MARK: - اللغة والمظهر

    /// يغيّر لغة الواجهة: تُعاد تسمية أزرار الإشعارات ونصوص التذكيرات، ويعرف الخادم اللغة في المزامنة القادمة
    func setLanguage(english: Bool) {
        guard english != Lang.english else { return }
        Lang.set(english: english)
        languageVersion += 1
        Reminders.registerCategories()
        if patient != nil {
            Task { await Reminders.reschedule() }
            syncNow()
        }
    }

    func applyAppearance(accent: ThemeAccent, mode: ThemeMode) {
        Appearance.apply(accent: accent, mode: mode)
        themeVersion += 1
    }

    func setLargeText(_ value: Bool) {
        Appearance.setLargeText(value)
        themeVersion += 1
    }

    /// وضع الجوال تغيّر (فاتح/داكن) والمظهر «تلقائي»
    func systemStyleChanged(dark: Bool) {
        guard Appearance.systemDark != dark else { return }
        Appearance.systemDark = dark
        if Appearance.mode == .system { themeVersion += 1 }
    }

    // MARK: - دورة الحياة

    /// يقرأ كل شيء من قاعدة البيانات
    func reload() {
        let date = Day.today()
        let current = PatientRepository.get()
        if let patient = current {
            // اليوم والأيام الستة السابقة: إن لم يُفتح التطبيق فيها تظهر جرعاتها فائتة في السجل
            for offset in stride(from: 6, through: 0, by: -1) { MedicationRepository.ensureLogs(patient.id, for: date.adding(days: -offset)) }
            let (from, to) = Schedule.dayRange(date)
            let historyFrom = Iso.format(date.adding(days: -29).start)
            medications = MedicationRepository.list(patient.id)
            todayLogs = MedicationRepository.logsBetween(patient.id, from, to)
            historyLogs = MedicationRepository.logsBetween(patient.id, historyFrom, to)
        } else {
            medications = []
            todayLogs = []
            historyLogs = []
        }
        if self.patient != current { self.patient = current }
        today = date
        sync.pending = Outbox.pendingCount()
        sync.lastSync = SyncState.get(SyncState.lastSuccess)
    }

    /// بعد أي تغيير محلي: تحديث التذكيرات ورفع التغييرات
    private func afterChange(sync shouldSync: Bool, reminders: Bool = true) {
        Task {
            if reminders { await Reminders.reschedule() }
            if shouldSync { syncNow() }
        }
    }

    func onForeground() {
        running = true
        resumeTick += 1
        refreshControl()
        Task { await refreshNotificationStatus() }
        // تحيات جديدة أُضيفت للجدول تظهر دون إعادة تشغيل التطبيق
        Task { if let fresh = await Greetings.refresh() { greetings = fresh } }
        reload()
        if patient != nil {
            afterChange(sync: true)
            refreshNotifications(quiet: true)
            refreshHelp()
            // طلب متابعة أو موافقة وصل والتطبيق في الخلفية
            if family != nil { refreshFamily() }
        }
    }

    func onBackground() {
        running = false
        commitPendingTake()
        BackgroundSync.schedule()
    }

    /// منتصف الليل والتطبيق مفتوح: جرعات اليوم الجديد
    func checkDay() {
        if Day.today() != today {
            reload()
            afterChange(sync: true)
        }
    }

    /// إشعار «أخذتها» من خارج التطبيق: نحدّث الشاشة إن كانت مفتوحة
    func refreshIfRunning() {
        if running { reload() }
    }

    func refreshNotificationStatus() async {
        notificationStatus = await Reminders.authorizationStatus()
    }

    func syncNow() {
        if sync.running { return }
        sync.running = true
        Task {
            let result = await SyncEngine.shared.sync()
            sync.status = result.status
            sync.error = result.error
            sync.running = false
            // رفض الخادم (خدمة متوقفة أو نسخة قديمة): نقرأ الحالة فتظهر الشاشة المناسبة
            if result.status == .failed { refreshControl() }
            reload()
        }
    }

    /// يقرأ حالة صفحة التحكم من الخادم
    func refreshControl() {
        Task {
            if let fresh = await AppControl.refresh(), fresh != control { control = fresh }
        }
    }

    // MARK: - الملف الشخصي

    func createPatient(name: String, phone: String, email: String?, gender: String?) {
        do {
            try PatientRepository.create(fullName: name, phone: phone, email: email, gender: gender)
        } catch {
            toast = tr("تعذّر حفظ بياناتك. حاول مرة أخرى.", "Couldn't save your details. Try again.")
            return
        }
        reload()
        afterChange(sync: true)
        // إذن الإشعارات يُطلب مرة بعد إنشاء الملف، لأن التذكيرات لا تظهر بدونه
        Task {
            await Reminders.requestPermission()
            await refreshNotificationStatus()
            await Reminders.reschedule()
        }
    }

    func updatePatient(name: String, phone: String, email: String?, gender: String?) {
        do {
            try PatientRepository.update(fullName: name, phone: phone, email: email, gender: gender)
        } catch {
            toast = error.localizedDescription
            return
        }
        reload()
        afterChange(sync: true, reminders: false)
        toast = tr("حُفظت بياناتك", "Your details were saved")
    }

    // MARK: - الأدوية

    func findDuplicate(_ draft: MedicationDraft) -> Medication? {
        guard let id = patient?.id else { return nil }
        return MedicationRepository.findDuplicate(id, draft)
    }

    func addMedication(_ draft: MedicationDraft) {
        guard let id = patient?.id else { return }
        do {
            try MedicationRepository.add(id, draft)
        } catch {
            toast = tr("تعذّر حفظ الدواء. حاول مرة أخرى.", "Couldn't save the medicine. Try again.")
            return
        }
        reload()
        afterChange(sync: true)
        toast = tr("أُضيف \(draft.name) وستصلك تذكيراته", "\(draft.name) added; you'll get its reminders")
    }

    func editMedication(_ medication: Medication, _ draft: MedicationDraft) {
        guard let id = patient?.id else { return }
        MedicationRepository.edit(id, medication.id, draft)
        Reminders.checkStock(patientId: id, medicationId: medication.id)
        reload()
        afterChange(sync: true)
        toast = tr("حُفظت تعديلات \(draft.name)", "\(draft.name) changes saved")
    }

    func stopMedication(_ medication: Medication) {
        guard let id = patient?.id else { return }
        MedicationRepository.deactivate(id, medication.id)
        reload()
        afterChange(sync: true)
        toast = tr("أُوقف \(medication.name)", "\(medication.name) stopped")
    }

    func refill(_ medication: Medication, amount: Int) {
        guard let id = patient?.id, amount > 0 else { return }
        MedicationRepository.refill(id, medication.id, amount)
        Reminders.checkStock(patientId: id, medicationId: medication.id)
        reload()
        afterChange(sync: true, reminders: false)
        toast = tr("أُضيف \(amount) إلى مخزون \(medication.name)", "Added \(amount) to \(medication.name) stock")
    }

    // MARK: - الجرعات

    func isTaken(_ log: DoseLog) -> Bool { log.status == DoseStatus.taken || pendingTake?.id == log.id }

    /// يسجّل الجرعة بعد ثوانٍ قليلة، ويمكن التراجع خلالها
    func takeDose(_ log: DoseLog) {
        if log.status == DoseStatus.taken { return }
        commitPendingTake()
        pendingTake = log
        takeTask = Task {
            try? await Task.sleep(nanoseconds: AppModel.undoSeconds * 1_000_000_000)
            if Task.isCancelled { return }
            commitPendingTake()
        }
    }

    func undoTake() {
        takeTask?.cancel()
        takeTask = nil
        pendingTake = nil
    }

    private func commitPendingTake() {
        guard let log = pendingTake, let id = patient?.id else { return }
        takeTask?.cancel()
        takeTask = nil
        pendingTake = nil
        if MedicationRepository.markTaken(id, log.id) { Reminders.checkStock(patientId: id, medicationId: log.medicationId) }
        Reminders.clear(medicationId: log.medicationId, scheduledAt: log.scheduledAt)
        reload()
        afterChange(sync: true)
    }

    /// «لم آخذها»: تراجع عن جرعة سُجّلت بالخطأ، في أي وقت
    func untakeDose(_ log: DoseLog) {
        if pendingTake?.id == log.id { return undoTake() }
        guard let id = patient?.id else { return }
        let changed = MedicationRepository.untake(id, log.id)
        if changed { Reminders.checkStock(patientId: id, medicationId: log.medicationId) }
        reload()
        if changed {
            afterChange(sync: true)
            toast = tr("أُلغي تسجيل جرعة \(log.medicationName)", "\(log.medicationName) dose unmarked")
        }
    }

    /// هل الجرعة بعيدة عن موعدها (أكثر من ساعة قبله)؟ نسأل قبل تسجيلها
    func isEarly(_ log: DoseLog, now: Date = Date()) -> Bool {
        guard let at = Iso.parse(log.scheduledAt) else { return false }
        return at > now.addingTimeInterval(3600)
    }

    // MARK: - المساعدة

    func saveEmergencyContact(name: String, phone: String) {
        emergencyName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        emergencyPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        UserDefaults.standard.set(emergencyName, forKey: "emergency.name")
        UserDefaults.standard.set(emergencyPhone, forKey: "emergency.phone")
    }

    func refreshHelp() {
        guard patient != nil else { return }
        Task {
            if let active = try? await FamilyApi.activeHelp() {
                if myHelp != active.mine { myHelp = active.mine }
                if familyHelp != active.family { familyHelp = active.family }
            }
        }
    }

    func refreshHelpIfRunning() { if running { refreshHelp() } }

    func sendHelp(reason: String?, point: HelpPoint?) async throws -> HelpSent {
        let sent = try await FamilyApi.requestHelp(message: reason, point: point)
        myHelp = sent.help
        return sent
    }

    func sendHelpLocation(_ id: String, _ point: HelpPoint) async throws -> HelpRequest {
        let updated = try await FamilyApi.sendHelpLocation(id, point)
        if myHelp?.id == updated.id { myHelp = updated }
        return updated
    }

    /// «أنا بخير الآن». يعيد رسالة الخطأ إن فشل.
    func resolveMyHelp() async -> String? {
        guard let help = myHelp else { return nil }
        do {
            _ = try await FamilyApi.resolveHelp(help.id)
            myHelp = nil
            toast = tr("طمأنّا عائلتك أنك بخير", "We told your family you're okay")
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    func acknowledgeHelp(_ help: HelpRequest) {
        Task {
            do {
                let updated = try await FamilyApi.acknowledgeHelp(help.id)
                familyHelp = familyHelp.map { $0.id == updated.id ? updated : $0 }
                if updated.acknowledgedBy == patient?.id {
                    toast = tr("أبلغنا \(help.patientName) أنك قادم", "We told \(help.patientName) you're coming")
                } else {
                    let who = updated.acknowledgedByName ?? tr("أحد أفراد العائلة", "A family member")
                    toast = tr("\(who) \(Gender.pick(updated.acknowledgedByGender, "في طريقه", "في طريقها")) \(Gender.pick(help.patientGender, "إليه", "إليها"))", "\(who) is on the way")
                }
            } catch {
                toast = error.localizedDescription
            }
        }
    }

    // MARK: - العائلة

    func refreshFamily() {
        if familyLoading { return }
        familyLoading = true
        Task {
            do {
                family = try await FamilyApi.overview()
                familyError = nil
            } catch {
                familyError = error.localizedDescription
            }
            familyLoading = false
        }
    }

    func refreshFamilyIfRunning() { if running && patient != nil { refreshFamily() } }

    func refreshNotifications(quiet: Bool = false) {
        Task {
            do {
                notifications = try await FamilyApi.notifications()
            } catch {
                if !quiet { toast = error.localizedDescription }
            }
        }
    }

    func refreshNotificationsIfRunning() { if running && patient != nil { refreshNotifications(quiet: true) } }

    func markAllRead() {
        Task {
            do {
                try await FamilyApi.markAllRead()
                notifications = notifications.map { var item = $0; item.read = true; return item }
            } catch {
                toast = error.localizedDescription
            }
        }
    }

    /// يعيد رسالة الخطأ إن فشل
    func respond(caregiverId: String, approve: Bool, level: String) async -> String? {
        do {
            try await FamilyApi.respond(caregiverId: caregiverId, approve: approve, privacyLevel: level)
            refreshFamily()
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    // MARK: - من الإشعارات

    /// «أخذتها» من إشعار التذكير، والتطبيق مغلق أو في الخلفية
    func takeFromNotification(medicationId: String, scheduledAt: String) async {
        guard let patient = PatientRepository.get() else { return }
        let changed = MedicationRepository.markTaken(patient.id, medicationId: medicationId, scheduledAt: scheduledAt)
        Reminders.clear(medicationId: medicationId, scheduledAt: scheduledAt)
        refreshIfRunning()
        if changed {
            Reminders.checkStock(patientId: patient.id, medicationId: medicationId)
            // ترفع الآن إن أمكن، فلا يصل العائلة تنبيه جرعة فائتة وهي مأخوذة
            _ = await SyncEngine.shared.sync()
        }
        await Reminders.reschedule()
    }

    /// «أنا قادم» من إشعار طلب المساعدة، دون فتح التطبيق
    func acknowledgeFromNotification(helpId: String, patientName: String) async {
        let message: String
        do {
            let help = try await FamilyApi.acknowledgeHelp(helpId)
            if let by = help.acknowledgedBy, by != PatientRepository.get()?.id {
                let who = help.acknowledgedByName ?? tr("أحد أفراد العائلة", "A family member")
                message = tr(who + Gender.pick(help.acknowledgedByGender, " في طريقه إلى ", " في طريقها إلى ") + patientName, "\(who) is on the way to \(patientName)")
            } else {
                message = tr("أبلغنا \(patientName) أنك قادم", "We told \(patientName) you're coming")
            }
        } catch {
            message = tr("تعذّر الإبلاغ: ", "Couldn't send: ") + error.localizedDescription
        }
        // نستبدل الإشعار العاجل بتأكيد هادئ
        let center = UNUserNotificationCenter.current()
        let delivered = await center.deliveredNotifications()
        let ids = delivered.filter { ($0.request.content.userInfo["help_id"] as? String) == helpId }.map(\.request.identifier)
        center.removeDeliveredNotifications(withIdentifiers: ids)
        Reminders.showNow(identifier: "help-ack|" + helpId, title: message, userInfo: ["type": "help_ack", "help_id": helpId])
        refreshHelpIfRunning()
    }
}
