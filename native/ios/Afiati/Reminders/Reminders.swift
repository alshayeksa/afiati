import Foundation
import UIKit
import UserNotifications

/**
 تذكيرات الأدوية المحلية: إشعار لكل موعد قادم، يعمل بلا إنترنت.
 iOS يحتفظ بـ 64 إشعارًا مجدولًا كحد أقصى، فنجدول أقرب المواعيد (حتى 56 موعدًا، أي عدة أيام)
 ونعيد الجدولة عند فتح التطبيق، وعند تغيّر الأدوية، وعند كل ضغطة على إشعار، وفي المزامنة الدورية.
 الإشعار فيه زر «أخذتها» يسجّل الجرعة مباشرة، وزر «ذكّرني بعد 10 دقائق».
 */
enum Reminders {
    static let doseCategory = "DOSE"
    static let helpCategory = "HELP_REQUEST"
    static let helpMapCategory = "HELP_MAP"
    static let takeAction = "TAKE"
    static let snoozeAction = "SNOOZE"
    static let ackAction = "HELP_ACK"
    static let callAction = "HELP_CALL"
    static let mapAction = "HELP_MAP_OPEN"
    static let snoozeMinutes = 10

    private static let dosePrefix = "dose|"
    private static let snoozePrefix = "snooze|"
    private static let stockPrefix = "stock|"
    /// يبقى مكان للتأجيل وتنبيهات المخزون تحت حد النظام (64)
    private static let maxScheduled = 56
    /// أبعد موعد نجدوله
    private static let horizonDays = 14

    private static var center: UNUserNotificationCenter { UNUserNotificationCenter.current() }

    /// الأزرار تحت الإشعارات، بلغة الواجهة. تُسجَّل عند التشغيل وعند تغيير اللغة.
    static func registerCategories() {
        let take = UNNotificationAction(identifier: takeAction, title: tr("أخذتها ✓", "Taken ✓"), options: [])
        let snooze = UNNotificationAction(identifier: snoozeAction, title: tr("ذكّرني بعد 10 دقائق", "Remind me in 10 minutes"), options: [])
        let ack = UNNotificationAction(identifier: ackAction, title: tr("أنا قادم", "I'm coming"), options: [])
        let call = UNNotificationAction(identifier: callAction, title: tr("اتصال", "Call"), options: [.foreground])
        let map = UNNotificationAction(identifier: mapAction, title: tr("الخريطة", "Map"), options: [.foreground])
        center.setNotificationCategories([
            UNNotificationCategory(identifier: doseCategory, actions: [take, snooze], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: helpCategory, actions: [ack, call, map], intentIdentifiers: [], options: []),
            UNNotificationCategory(identifier: helpMapCategory, actions: [map], intentIdentifiers: [], options: []),
        ])
    }

    static func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    /// يطلب إذن الإشعارات (مرة واحدة يسأل النظام) ثم رمز الإشعارات الفورية
    @discardableResult
    static func requestPermission() async -> Bool {
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        if granted { await Push.requestDeviceToken() }
        return granted
    }

    struct Upcoming {
        let medication: Medication
        let time: String
        let at: Date
    }

    /// أقرب المواعيد القادمة لكل الأدوية النشطة، مرتبة بالوقت. منطق خالص يُختبر.
    static func upcoming(_ medications: [Medication], after now: Date, limit: Int, horizonDays: Int,
                         isTaken: (String, String) -> Bool) -> [Upcoming] {
        var result: [Upcoming] = []
        let today = Day(now)
        for medication in medications where medication.isActive {
            let created = Iso.parse(medication.createdAt)
            for offset in 0...horizonDays {
                let day = today.adding(days: offset)
                guard Schedule.isScheduled(medication.repeatType, medication.repeatDays, on: day) else { continue }
                for time in Schedule.timesFor(medication.repeatType, medication.times) {
                    guard let at = Schedule.date(day, time), at > now else { continue }
                    if let created = created, at < created { continue }
                    if isTaken(medication.id, Iso.format(at)) { continue }
                    result.append(Upcoming(medication: medication, time: time, at: at))
                }
            }
        }
        result.sort { $0.at < $1.at }
        return Array(result.prefix(limit))
    }

    /// يعيد جدولة كل التذكيرات من قاعدة البيانات. يُستدعى بعد أي تغيير في الأدوية وعند فتح التطبيق.
    static func reschedule() async {
        let pending = await center.pendingNotificationRequests()
        let old = pending.map(\.identifier).filter { $0.hasPrefix(dosePrefix) }
        center.removePendingNotificationRequests(withIdentifiers: old)
        guard let patient = PatientRepository.get() else { return }
        let medications = MedicationRepository.list(patient.id)
        let list = upcoming(medications, after: Date(), limit: maxScheduled, horizonDays: horizonDays) { medicationId, scheduledAt in
            MedicationRepository.isTaken(medicationId, scheduledAt)
        }
        for item in list {
            let scheduledAt = Iso.format(item.at)
            let content = doseContent(item.medication, time: item.time, scheduledAt: scheduledAt)
            let parts = Day.calendar.dateComponents([.year, .month, .day, .hour, .minute], from: item.at)
            let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: dosePrefix + item.medication.id + "|" + scheduledAt, content: content, trigger: trigger))
        }
    }

    private static func doseContent(_ medication: Medication, time: String, scheduledAt: String) -> UNMutableNotificationContent {
        let content = UNMutableNotificationContent()
        content.title = tr("حان موعد \(medication.name)", "Time for \(medication.name)")
        content.body = "\(Format.dose(medication.doseAmount, medication.doseUnit)) · \(Format.time(time))"
        content.sound = .default
        content.categoryIdentifier = doseCategory
        content.threadIdentifier = "doses"
        content.userInfo = ["kind": "dose", "medication_id": medication.id, "scheduled_at": scheduledAt, "time": time]
        if #available(iOS 15.0, *) { content.interruptionLevel = .timeSensitive }
        return content
    }

    /// «ذكّرني بعد 10 دقائق»
    static func snooze(medicationId: String, time: String, scheduledAt: String) async {
        guard let medication = MedicationRepository.find(medicationId), medication.isActive else { return }
        let content = doseContent(medication, time: time, scheduledAt: scheduledAt)
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(snoozeMinutes * 60), repeats: false)
        try? await center.add(UNNotificationRequest(identifier: snoozePrefix + medicationId + "|" + scheduledAt, content: content, trigger: trigger))
    }

    /// يزيل إشعار الجرعة الظاهر وتأجيلها بعد تسجيلها
    static func clear(medicationId: String, scheduledAt: String) {
        let ids = [dosePrefix + medicationId + "|" + scheduledAt, snoozePrefix + medicationId + "|" + scheduledAt]
        center.removePendingNotificationRequests(withIdentifiers: ids)
        center.removeDeliveredNotifications(withIdentifiers: ids)
    }

    /**
     بعد كل جرعة أو علبة جديدة: إن بلغ الدواء حد التنبيه يظهر إشعار مرة واحدة،
     ولا يتكرر حتى يُضاف مخزون فوق الحد ثم ينخفض من جديد. يعمل بلا إنترنت.
     */
    static func checkStock(patientId: String, medicationId: String) {
        guard let medication = MedicationRepository.list(patientId).first(where: { $0.id == medicationId }) else { return }
        let key = "stock_alerted_\(medicationId)"
        let identifier = stockPrefix + medicationId
        if !medication.lowStock {
            UserDefaults.standard.removeObject(forKey: key)
            center.removeDeliveredNotifications(withIdentifiers: [identifier])
            return
        }
        if UserDefaults.standard.bool(forKey: key) { return }
        UserDefaults.standard.set(true, forKey: key)
        let count = medication.stockCount ?? 0
        let content = UNMutableNotificationContent()
        content.title = count <= 0 ? tr("نفد \(medication.name)", "\(medication.name) has run out") : tr("\(medication.name) قارب على النفاد", "\(medication.name) is running low")
        content.body = count <= 0
            ? tr("اطلب علبة جديدة، ثم اضغط «أضفت علبة جديدة» في عافيتي.", "Order a new box, then tap “I added a new box” in Afiati.")
            : tr("باقي \(count). اطلب علبة جديدة قريبًا، ثم اضغط «أضفت علبة جديدة» في عافيتي.", "\(count) left. Order a new box soon, then tap “I added a new box” in Afiati.")
        content.sound = .default
        content.userInfo = ["kind": "stock", "medication_id": medicationId]
        center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: nil))
    }

    /// إشعار محلي فوري (تأكيد «أنا قادم» من الإشعار)
    static func showNow(identifier: String, title: String, body: String = "", userInfo: [String: String] = [:]) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.userInfo = userInfo
        center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: nil))
    }
}
