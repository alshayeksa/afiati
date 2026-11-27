import BackgroundTasks
import SwiftUI
import UIKit
import UserNotifications

/// مزامنة دورية في الخلفية: يختار النظام وقتها (عادة مرات قليلة في اليوم)، وتجدد التذكيرات أيضًا.
enum BackgroundSync {
    static let identifier = "ai.alshaye.afiati.sync"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            schedule()
            let work = Task {
                let result = await SyncEngine.shared.sync()
                await Reminders.reschedule()
                refresh.setTaskCompleted(success: result.status == .synced)
            }
            refresh.expirationHandler = { work.cancel() }
        }
    }

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}

/**
 يُنشئ حالة التطبيق عند التشغيل مباشرة، حتى لو شغّله النظام في الخلفية بسبب ضغطة على إشعار،
 ليُسجَّل مستقبِل الإشعارات قبل وصولها.
 */
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Reminders.registerCategories()
        BackgroundSync.register()
        _ = AppModel.shared
        // رمز الإشعارات الفورية يتجدد عند كل تشغيل إن كانت الإشعارات مسموحة
        Task {
            let status = await Reminders.authorizationStatus()
            if status == .authorized || status == .provisional || status == .ephemeral { await Push.requestDeviceToken() }
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let previous = Push.serverToken
        Push.saveDeviceToken(deviceToken)
        if previous != Push.serverToken {
            Task { @MainActor in AppModel.shared.syncNow() }
        }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        NSLog("Afiati: remote notifications unavailable: \(error.localizedDescription)")
    }

    /// إشعار وصل والتطبيق مفتوح: يظهر أيضًا (مثل أندرويد)، وتتحدث الشاشة
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let type = notification.request.content.userInfo["type"] as? String ?? ""
        Task { @MainActor in
            let model = AppModel.shared
            model.refreshNotificationsIfRunning()
            if type.hasPrefix("help") { model.refreshHelpIfRunning() }
            if type.hasPrefix("invite") { model.refreshFamilyIfRunning() }
        }
        completionHandler([.banner, .list, .sound])
    }

    /// ضغطة على الإشعار أو على أحد أزراره
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        let string = { (key: String) -> String? in
            if let value = info[key] as? String, !value.isEmpty { return value }
            return nil
        }
        let action = response.actionIdentifier
        Task { @MainActor in
            let model = AppModel.shared
            switch action {
            case Reminders.takeAction:
                if let medicationId = string("medication_id"), let scheduledAt = string("scheduled_at") {
                    await model.takeFromNotification(medicationId: medicationId, scheduledAt: scheduledAt)
                }
            case Reminders.snoozeAction:
                if let medicationId = string("medication_id"), let scheduledAt = string("scheduled_at") {
                    Reminders.clear(medicationId: medicationId, scheduledAt: scheduledAt)
                    await Reminders.snooze(medicationId: medicationId, time: string("time") ?? "", scheduledAt: scheduledAt)
                }
            case Reminders.ackAction:
                if let helpId = string("help_id") {
                    await model.acknowledgeFromNotification(helpId: helpId, patientName: string("patient_name") ?? "")
                }
            case Reminders.callAction:
                model.pendingOpen = .help
                if let phone = string("phone") { Links.dial(phone) }
            case Reminders.mapAction:
                model.pendingOpen = .help
                if let url = string("map_url"), url.hasPrefix("https://www.google.com/maps/") { Links.open(url) }
            default:
                // ضغطة على الإشعار نفسه: يفتح الشاشة المناسبة
                let type = string("type") ?? ""
                if string("kind") == "dose" || string("kind") == "stock" {
                    model.tab = .today
                } else if type == "invite_request" {
                    model.pendingOpen = .family
                } else if type.hasPrefix("help") {
                    model.pendingOpen = .help
                } else if (type == "missed_dose" || type == "low_stock"), let patientId = string("patient_id") {
                    // جرعة فائتة أو دواء قارب على النفاد: يفتح يوم قريبك وزر التذكير
                    model.pendingOpen = .patient(patientId)
                } else if !type.isEmpty {
                    model.pendingOpen = .notifications
                }
            }
            completionHandler()
        }
    }
}

@main
struct AfiatiApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var model = AppModel.shared
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // عناوين شريط التنقل والقوائم من UIKit بخط تجوال
        if let font = UIFont(name: "Tajawal-Medium", size: 17) {
            UINavigationBar.appearance().titleTextAttributes = [.font: font]
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .onChange(of: scenePhase) { phase in
                    switch phase {
                    case .active:
                        model.systemStyleChanged(dark: UITraitCollection.current.userInterfaceStyle != .light)
                        model.onForeground()
                    case .background:
                        model.onBackground()
                    default:
                        break
                    }
                }
        }
    }
}
