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

    /// يقرأ الإعدادات من afiati.alshaye.ai (وإن تعذر فمن الخادم الحالي). nil عند الخطأ، فتبقى الحالة المحفوظة.
    static func refresh() async -> AppControl? {
        var fetched = try? await Api.request("", auth: false, absolute: Api.configURL)
        if fetched == nil { fetched = try? await Api.request("/v1/app-config", auth: false) }
        guard let config = fetched else { return nil }
        await applyServer(Json.string(config["api_url"]))
        let ios = config["ios"] as? JSON
        let url = Json.string(ios?["url"]) ?? ""
        let value = AppControl(serviceOn: Json.bool(config["service_on"]) ?? true, minBuild: Json.int(ios?["min_build"]) ?? 0,
                               updateURL: url.hasPrefix("https://") ? url : "")
        UserDefaults.standard.set(["service_on": value.serviceOn, "min_build": value.minBuild, "url": value.updateURL], forKey: key)
        return value
    }

    /// عنوان الخادم من صفحة التحكم: لا ينتقل إليه التطبيق إلا بعد أن يجيب /health، فلا ينقطع بعنوان خاطئ.
    private static func applyServer(_ url: String?) async {
        var target = url ?? ""
        while target.hasSuffix("/") { target.removeLast() }
        guard target.hasPrefix("https://"), target != Api.defaultURL else {
            if Api.baseURL != Api.defaultURL { Api.setBaseURL(nil) }
            return
        }
        guard target != Api.baseURL else { return }
        let health = try? await Api.request("", auth: false, absolute: target + "/health")
        if Json.bool(health?["ok"]) == true { Api.setBaseURL(target) }
    }
}
