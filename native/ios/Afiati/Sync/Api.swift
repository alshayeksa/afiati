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
    static let baseURL = "https://afiati-api.alshayeksa.workers.dev"
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
    static func request(_ path: String, method: String = "GET", body: JSON? = nil, auth: Bool = true) async throws -> JSON {
        guard let url = URL(string: baseURL + path) else { throw ApiError(status: 0, code: "bad_url", message: path) }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        // منطقة الجوال الزمنية: الخادم لا يرسل في الليل التنبيهات التي تحتمل التأجيل
        request.setValue(TimeZone.current.identifier, forHTTPHeaderField: "X-Timezone")
        // لغة الواجهة: رسائل العائلة من الخادم تصل بها
        request.setValue(Lang.code, forHTTPHeaderField: "X-Language")
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
