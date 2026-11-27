import Foundation
import UIKit
import UserNotifications

/**
 إشعارات العائلة الفورية عبر Apple (APNs): طلب متابعة، موافقة، تذكير من قريب، طلب مساعدة.
 الخادم يرسل التنبيه إلى رمز الجهاز، ويختار فئة الإشعار التي تحدد أزراره (أنا قادم، اتصال، الخريطة).
 */
enum Push {
    private static let tokenKey = "apns.token"

    /// يحفظ الرمز الذي أعطته Apple للجهاز. المزامنة ترسله للخادم إن تغيّر.
    static func saveDeviceToken(_ data: Data) {
        let hex = data.map { String(format: "%02x", $0) }.joined()
        UserDefaults.standard.set(hex, forKey: tokenKey)
    }

    /// الرمز بالصيغة التي يفهمها الخادم: بيئة الاختبار لبناء Xcode، والإنتاج لـ TestFlight والمتجر
    static var serverToken: String? {
        guard let hex = UserDefaults.standard.string(forKey: tokenKey), !hex.isEmpty else { return nil }
        #if DEBUG
        return "apns-dev:" + hex
        #else
        return "apns:" + hex
        #endif
    }

    /// يسجّل الرمز في الخادم إن تغيّر. جزء من المزامنة، بعد تسجيل الجهاز.
    static func register() async throws {
        guard let token = serverToken, SyncState.get(SyncState.pushToken) != token else { return }
        try await Api.request("/v1/me/push-token", method: "PUT", body: ["push_token": token])
        SyncState.set(SyncState.pushToken, token)
    }

    /// يطلب من Apple رمز الجهاز. يُستدعى بعد السماح بالإشعارات.
    @MainActor
    static func requestDeviceToken() {
        UIApplication.shared.registerForRemoteNotifications()
    }
}
