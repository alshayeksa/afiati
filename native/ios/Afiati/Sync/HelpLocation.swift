import CoreLocation
import Foundation

/// موقع الكبير لطلب المساعدة فقط: يُقرأ عند فتح شاشة المساعدة، ولا يُتابع في الخلفية.
struct HelpPoint: Equatable {
    let latitude: Double
    let longitude: Double
    let accuracy: Double?
    let time: Date
}

@MainActor
final class HelpLocation: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = HelpLocation()

    /// موقع أقدم من هذا لا يُرسل
    private let maxAge: TimeInterval = 10 * 60
    /// دقة تكفي ولا داعي لانتظار GPS بعدها
    private let goodEnough: Double = 50

    private let manager = CLLocationManager()
    @Published private(set) var status: CLAuthorizationStatus = .notDetermined
    private var best: CLLocation?
    private var waiting: CheckedContinuation<HelpPoint?, Never>?

    private override init() {
        super.init()
        status = manager.authorizationStatus
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    var granted: Bool { status == .authorizedWhenInUse || status == .authorizedAlways }
    var undetermined: Bool { status == .notDetermined }

    /// الموقع متوقف في إعدادات الجوال كلها
    var enabled: Bool { CLLocationManager.locationServicesEnabled() }

    func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    private func point(_ location: CLLocation) -> HelpPoint {
        HelpPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude,
                  accuracy: location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : nil, time: location.timestamp)
    }

    /// آخر موقع يعرفه الجوال إن كان حديثًا: فوري، يصلح لإرساله مع الطلب مباشرة
    func lastKnown() -> HelpPoint? {
        guard granted, let location = manager.location, -location.timestamp.timeIntervalSinceNow <= maxAge else { return nil }
        return point(location)
    }

    /// موقع جديد الآن، أو أفضل ما وصل خلال المهلة. nil إن تعذّر.
    func current(timeout: TimeInterval = 25) async -> HelpPoint? {
        guard granted else { return nil }
        if waiting != nil { finish() }
        best = nil
        let result = await withCheckedContinuation { (continuation: CheckedContinuation<HelpPoint?, Never>) in
            waiting = continuation
            manager.startUpdatingLocation()
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                self?.finish()
            }
        }
        return result ?? lastKnown()
    }

    private func finish() {
        manager.stopUpdatingLocation()
        guard let continuation = waiting else { return }
        waiting = nil
        continuation.resume(returning: best.map(point))
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            for location in locations where location.horizontalAccuracy >= 0 {
                if let current = self.best, current.horizontalAccuracy <= location.horizontalAccuracy { continue }
                self.best = location
            }
            if let best = self.best, best.horizontalAccuracy <= self.goodEnough { self.finish() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // خطأ مؤقت (مثل عدم توفر إشارة بعد) لا يوقف المحاولة؛ المهلة تنهيها
        if let error = error as? CLError, error.code == .denied {
            Task { @MainActor in self.finish() }
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in self.status = self.manager.authorizationStatus }
    }
}
