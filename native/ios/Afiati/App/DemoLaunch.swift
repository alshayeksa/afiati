#if DEBUG
import Foundation

/**
 For App Store screenshots only, in Debug builds (never in TestFlight or the App Store).
 Launch arguments sign the app into a demo account on a local test server:
   -demoProfile <uuid> -demoToken <token> -demoName <name> -demoPhone <phone> -demoGender male|female
   -api.url http://127.0.0.1:8798   -language ar|en
 Used by AfiatiUITests/ScreenshotTests.swift (.github/workflows/ios-screenshots.yml).
 */
enum DemoLaunch {
    static func apply() {
        let defaults = UserDefaults.standard
        guard let id = defaults.string(forKey: "demoProfile"), let token = defaults.string(forKey: "demoToken") else { return }
        // A real user is asked after setting up; the demo skips that screen, so ask here (the test taps «Allow»)
        Task { _ = await Reminders.requestPermission() }
        if PatientRepository.get()?.id == id { return }
        // Another demo person: start from a clean phone
        for table in ["medication_logs_local", "medications_local", "outbox", "heart_rate_local", "vital_readings_local", "sync_state", "patients_local"] {
            _ = try? Database.shared.run("DELETE FROM \(table)")
        }
        let now = Iso.now()
        _ = try? Database.shared.run(
            "INSERT INTO patients_local (id, auth_user_id, full_name, phone, email, phone_verified, created_at, updated_at, gender) VALUES (?, NULL, ?, ?, NULL, 0, ?, ?, ?)",
            [id, defaults.string(forKey: "demoName") ?? "Demo", defaults.string(forKey: "demoPhone") ?? "0500000000", now, now, defaults.string(forKey: "demoGender")]
        )
        SyncState.set(SyncState.deviceToken, token)
        // The profile is already on the server: nothing to upload
        SyncState.set(SyncState.profileVersion, now)
    }

    /// The local test server, accepted over plain http in Debug builds only
    static func isLocalServer(_ url: String) -> Bool { url.hasPrefix("http://127.0.0.1:") }
}
#endif
