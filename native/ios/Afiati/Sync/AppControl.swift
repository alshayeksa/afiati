import Foundation

/**
 التحكم من صفحة /forceupdate: إيقاف الخدمة، والتحديث الإلزامي لنسخة الآيفون.
 يُقرأ من الخادم عند كل فتح للتطبيق، ويُحفظ آخر ما وصل فيبقى ساريًا بلا إنترنت.
 */
struct AppControl: Equatable {
    enum Block { case serviceOff, update }

    var serviceOn = true
    var minBuild = 0
    var updateURL = ""

    private static let key = "control.state"

    /// رقم بناء هذه النسخة (CFBundleVersion)
    static var currentBuild: Int { Int(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "") ?? 0 }

    /// ما يمنع استخدام التطبيق الآن، أو nil
    var block: Block? {
        if !serviceOn { return .serviceOff }
        if minBuild > 0 && AppControl.currentBuild < minBuild { return .update }
        return nil
    }

    static func saved() -> AppControl {
        guard let values = UserDefaults.standard.dictionary(forKey: key) else { return AppControl() }
        return AppControl(serviceOn: values["service_on"] as? Bool ?? true, minBuild: values["min_build"] as? Int ?? 0,
                          updateURL: values["url"] as? String ?? "")
    }

    /// يقرأ الحالة من الخادم. nil عند الخطأ، فتبقى الحالة المحفوظة.
    static func refresh() async -> AppControl? {
        guard let config = try? await Api.request("/v1/app-config", auth: false) else { return nil }
        let ios = config["ios"] as? JSON
        let url = Json.string(ios?["url"]) ?? ""
        let value = AppControl(serviceOn: Json.bool(config["service_on"]) ?? true, minBuild: Json.int(ios?["min_build"]) ?? 0,
                               updateURL: url.hasPrefix("https://") ? url : "")
        UserDefaults.standard.set(["service_on": value.serviceOn, "min_build": value.minBuild, "url": value.updateURL], forKey: key)
        return value
    }
}
