import Foundation
import Network

struct ApiError: Error, LocalizedError {
    let status: Int
    let code: String
    let message: String
    var errorDescription: String? { message }
}

/// عميل خادم عافيتي على Cloudflare (مجلد worker).
enum Api {
    /// العنوان المدمج في التطبيق، يُستخدم ما لم تحدد صفحة التحكم غيره
    static let defaultURL = "https://afiati-api.alshayeksa.workers.dev"
    /// إعدادات التطبيق (ومنها عنوان الخادم) تُقرأ من هنا، فيمكن نقل الخادم بلا تحديث للتطبيق
    static let configURL = "https://afiati.alshaye.ai/v1/app-config"
    private static let urlKey = "api.url"

    /// عنوان الخادم الحالي: من صفحة التحكم (يُحفظ في الجوال)، أو المدمج
    static var baseURL: String {
        if let saved = UserDefaults.standard.string(forKey: urlKey), saved.hasPrefix("https://") { return saved }
        #if DEBUG
        if let saved = UserDefaults.standard.string(forKey: urlKey), DemoLaunch.isLocalServer(saved) { return saved }
        #endif
        return defaultURL
    }

    static func setBaseURL(_ url: String?) {
        if let url = url { UserDefaults.standard.set(url, forKey: urlKey) } else { UserDefaults.standard.removeObject(forKey: urlKey) }
    }
    private static let timeout: TimeInterval = 20

    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = timeout
        configuration.timeoutIntervalForResource = 40
        configuration.waitsForConnectivity = false
        return URLSession(configuration: configuration)
    }()

    private static var networkError: ApiError {
        ApiError(status: 0, code: "network_error", message: tr("تعذر الوصول إلى الخادم.", "Couldn't reach the server."))
    }

    @discardableResult
    /// absolute: عنوان كامل بدل مسار على الخادم الحالي
    static func request(_ path: String, method: String = "GET", body: JSON? = nil, auth: Bool = true, absolute: String? = nil) async throws -> JSON {
        guard let url = URL(string: absolute ?? (baseURL + path)) else { throw ApiError(status: 0, code: "bad_url", message: path) }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        // منطقة الجوال الزمنية: الخادم لا يرسل في الليل التنبيهات التي تحتمل التأجيل
        request.setValue(TimeZone.current.identifier, forHTTPHeaderField: "X-Timezone")
        // لغة الواجهة: رسائل العائلة من الخادم تصل بها
        request.setValue(Lang.code, forHTTPHeaderField: "X-Language")
        // رقم النسخة: الخادم يرفض النسخ القديمة إن فُعّل التحديث الإلزامي من صفحة التحكم
        request.setValue("ios", forHTTPHeaderField: "X-Platform")
        request.setValue(AppControl.currentBuild > 0 ? String(AppControl.currentBuild) : nil, forHTTPHeaderField: "X-App-Build")
        if auth {
            guard let token = SyncState.get(SyncState.deviceToken) else {
                throw ApiError(status: 401, code: "unauthorized", message: tr("الجهاز غير مسجل.", "This device isn't registered."))
            }
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body = body {
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw networkError
        }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? JSON
        guard (200...299).contains(status) else {
            let code = (json?["error"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "http_error"
            throw ApiError(status: status, code: code, message: (json?["message"] as? String) ?? "HTTP \(status)")
        }
        return json ?? [:]
    }
}

/// حالة الاتصال بالإنترنت
final class Connectivity {
    static let shared = Connectivity()
    private let monitor = NWPathMonitor()
    private(set) var online = true

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in self?.online = path.status == .satisfied }
        monitor.start(queue: DispatchQueue(label: "ai.alshaye.afiati.network"))
    }
}
