import XCTest

/**
 App Store screenshots. Run by .github/workflows/ios-screenshots.yml, never by the normal build.
 The workflow starts a local test server, creates demo families with Screenshots/seed.mjs, and passes
 them here as JSON (TEST_RUNNER_SEED_JSON). Each screen is saved as PNG next to the simulator's host
 home folder (afiati-shots/<name>-<lang>.png). No real user data is involved.
 */
final class ScreenshotTests: XCTestCase {
    private struct Person: Decodable { let id: String; let token: String; let name: String; let phone: String; let gender: String }
    private struct Family: Decodable { let elder: Person; let son: Person; let firstMedicine: String }
    private struct Seed: Decodable { let base: String; let ar: Family; let en: Family }

    private var seed: Seed!
    private var outDir: URL!

    override func setUpWithError() throws {
        continueAfterFailure = false
        let json = try XCTUnwrap(ProcessInfo.processInfo.environment["SEED_JSON"], "SEED_JSON missing")
        seed = try JSONDecoder().decode(Seed.self, from: Data(json.utf8))
        let home = ProcessInfo.processInfo.environment["SIMULATOR_HOST_HOME"] ?? NSTemporaryDirectory()
        outDir = URL(fileURLWithPath: home).appendingPathComponent("afiati-shots")
        try FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)
    }

    func testArabic() throws { try capture(lang: "ar", family: seed.ar) }
    func testEnglish() throws { try capture(lang: "en", family: seed.en) }

    // MARK: - Scenes

    private func capture(lang: String, family: Family) throws {
        let ar = lang == "ar"
        func t(_ arabic: String, _ english: String) -> String { ar ? arabic : english }

        // 1) The elder's day: one dose taken, one due now
        var app = launch(as: family.elder, lang: lang)
        XCTAssertTrue(app.staticTexts[family.firstMedicine].firstMatch.waitForExistence(timeout: 40), "medicines did not sync")
        let mark = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", t("تسجيل أخذ", "Mark "))).firstMatch
        if mark.waitForExistence(timeout: 10) { mark.tap() }
        sleep(6) // the «undo» banner fades
        save("home", lang)

        tapTab(app, t("أدويتي", "Medicines"))
        sleep(2)
        save("meds", lang)

        tapTab(app, t("العائلة", "Family"))
        sleep(3)
        save("family", lang)

        // 2) The elder asked for help and the son answered «I'm coming»
        let help = try api("POST", "/v1/help", token: family.elder.token, body: ["message": t("أشعر بدوخة", "I feel dizzy"), "latitude": 24.7136, "longitude": 46.6753, "accuracy": 12])
        let helpId = try XCTUnwrap((help["help"] as? [String: Any])?["id"] as? String)
        _ = try api("POST", "/v1/help/\(helpId)/acknowledge", token: family.son.token)
        refresh(app)
        let coming = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", t("إليك", "is coming to you"))).firstMatch
        XCTAssertTrue(coming.waitForExistence(timeout: 30), "help reply not shown")
        coming.tap()
        sleep(3)
        save("help", lang)
        _ = try api("POST", "/v1/help/\(helpId)/resolve", token: family.elder.token)
        app.terminate()

        // 3) The son: his mother's help request reaches him, then her day
        let alert = try api("POST", "/v1/help", token: family.elder.token, body: ["message": t("أشعر بدوخة", "I feel dizzy"), "latitude": 24.7136, "longitude": 46.6753, "accuracy": 12])
        let alertId = try XCTUnwrap((alert["help"] as? [String: Any])?["id"] as? String)
        app = launch(as: family.son, lang: lang)
        tapTab(app, t("العائلة", "Family"))
        let needs = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", t("تحتاج مساعدة", "needs help"))).firstMatch
        // The local server can be slow right after a help request: look again after a refresh
        if !needs.waitForExistence(timeout: 45) { refresh(app); tapTab(app, t("العائلة", "Family")) }
        XCTAssertTrue(needs.waitForExistence(timeout: 45), "help alert not shown to the son")
        sleep(2)
        save("alert", lang)
        _ = try api("POST", "/v1/help/\(alertId)/resolve", token: family.elder.token)
        refresh(app)
        tapTab(app, t("العائلة", "Family"))

        let follow = app.buttons[t("أتابعهم", "I follow")].firstMatch
        XCTAssertTrue(follow.waitForExistence(timeout: 15))
        follow.tap()
        let byName = NSPredicate(format: "label CONTAINS %@", family.elder.name)
        var mother = app.buttons.matching(byName).firstMatch
        if !mother.waitForExistence(timeout: 20) { mother = app.staticTexts.matching(byName).firstMatch }
        XCTAssertTrue(mother.waitForExistence(timeout: 5), "mother not in the list")
        mother.tap()
        let adherence = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", t("التزامها", "adherence"))).firstMatch
        XCTAssertTrue(adherence.waitForExistence(timeout: 30), "mother's day did not load")
        sleep(2)
        save("mom-day", lang)
        app.terminate()
    }

    // MARK: - Helpers

    private func launch(as person: Person, lang: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-demoProfile", person.id, "-demoToken", person.token, "-demoName", person.name,
            "-demoPhone", person.phone, "-demoGender", person.gender,
            "-api.url", seed.base, "-language", lang, "-appearance.mode", "dark",
        ]
        app.launchEnvironment["TZ"] = "Asia/Riyadh"
        app.launch()
        allowNotifications()
        return app
    }

    /// The system asks once for notifications: allow, so the app looks as it does for real users
    private func allowNotifications() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let allow = springboard.buttons.matching(NSPredicate(format: "label IN %@", ["Allow", "السماح"])).firstMatch
        if allow.waitForExistence(timeout: 8) { allow.tap() }
    }

    /// Background and foreground: the app reads help requests and the family again
    private func refresh(_ app: XCUIApplication) {
        XCUIDevice.shared.press(.home)
        sleep(2)
        app.activate()
        sleep(4)
    }

    private func tapTab(_ app: XCUIApplication, _ title: String) {
        let tab = app.buttons[title].firstMatch
        XCTAssertTrue(tab.waitForExistence(timeout: 15), "tab \(title) missing")
        tab.tap()
    }

    private func save(_ name: String, _ lang: String) {
        let shot = XCUIScreen.main.screenshot()
        try? shot.pngRepresentation.write(to: outDir.appendingPathComponent("\(name)-\(lang).png"))
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = "\(name)-\(lang)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @discardableResult
    private func api(_ method: String, _ path: String, token: String, body: [String: Any]? = nil) throws -> [String: Any] {
        var request = URLRequest(url: URL(string: seed.base + path)!)
        request.httpMethod = method
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Asia/Riyadh", forHTTPHeaderField: "X-Timezone")
        if let body = body { request.httpBody = try JSONSerialization.data(withJSONObject: body) }
        var result: [String: Any] = [:]
        var failure: Error?
        let done = expectation(description: path)
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error { failure = error }
            else if let code = (response as? HTTPURLResponse)?.statusCode, !(200...299).contains(code) {
                failure = NSError(domain: "api", code: code, userInfo: [NSLocalizedDescriptionKey: "\(method) \(path) -> \(code)"])
            } else if let data = data { result = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:] }
            done.fulfill()
        }.resume()
        wait(for: [done], timeout: 20)
        if let failure = failure { throw failure }
        return result
    }
}
