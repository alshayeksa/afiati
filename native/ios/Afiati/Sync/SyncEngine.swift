import Foundation

enum SyncStatus { case synced, offline, notConfigured, noPatient, failed }

struct SyncResult {
    var status: SyncStatus
    var pushed = 0
    var pulled = 0
    var error: String?
}

/**
 المزامنة: تسجيل الجهاز مرة واحدة، ثم رفع الملف الشخصي، ثم رفع outbox، ثم سحب التغييرات بمؤشر الخادم.
 ترتيب وقواعد أندرويد نفسها (SyncEngine.kt). مزامنة واحدة في كل وقت، والطلبات التالية تنتظر دورها.
 */
actor SyncEngine {
    static let shared = SyncEngine()

    /// لا يتجاوز MAX_PUSH_OPERATIONS في worker/src/sync.ts
    private let pushBatch = 25
    private let pullPage = 200
    private var last: Task<SyncResult, Never>?

    func sync() async -> SyncResult {
        let previous = last
        let task = Task { () -> SyncResult in
            _ = await previous?.value
            return await self.run()
        }
        last = task
        return await task.value
    }

    private func run() async -> SyncResult {
        guard let patient = PatientRepository.get() else { return SyncResult(status: .noPatient) }
        if !Connectivity.shared.online { return SyncResult(status: .offline) }
        do {
            try await SyncEngine.ensureIdentity(patient)
            try await pushProfile()
            // رمز الإشعارات لا يوقف المزامنة إن تعذّر، ويُعاد في المرة القادمة
            try? await Push.register()
            // جرعات اليوم والغد تصل الخادم قبل موعدها، فيعرف إن فاتت جرعة حتى لو لم يُفتح التطبيق
            let today = Day.today()
            MedicationRepository.ensureLogs(patient.id, for: today)
            MedicationRepository.ensureLogs(patient.id, for: today.adding(days: 1))
            let pushed = try await pushOutbox()
            let pulled = try await pull()
            SyncState.set(SyncState.lastSuccess, Iso.now())
            return SyncResult(status: .synced, pushed: pushed, pulled: pulled)
        } catch let error as ApiError {
            return SyncResult(status: error.code == "network_error" ? .offline : .failed, error: error.message)
        } catch {
            return SyncResult(status: .failed, error: error.localizedDescription)
        }
    }

    /// يسجّل الجوال لدى الخادم مرة واحدة ويحفظ رمزه. يُستدعى أيضًا قبل ميزات العائلة.
    static func ensureIdentity(_ patient: Patient? = PatientRepository.get()) async throws {
        guard let local = patient else { throw ApiError(status: 0, code: "no_patient", message: tr("لا يوجد ملف شخصي.", "No profile.")) }
        if SyncState.get(SyncState.deviceToken) != nil { return }
        let response: JSON
        do {
            response = try await Api.request("/v1/register", method: "POST", body: [
                "profile_id": local.id,
                "full_name": local.fullName,
                "phone": local.phone,
                "email": local.email.map { $0 as Any } ?? NSNull(),
                "gender": local.gender.map { $0 as Any } ?? NSNull(),
                "platform": "ios",
            ], auth: false)
        } catch let error as ApiError where error.code == "profile_exists" {
            throw ApiError(status: 409, code: error.code, message: tr("هذا الملف مسجل مسبقًا على جهاز آخر.", "This profile is already registered on another device."))
        }
        guard let token = response["token"] as? String else { throw ApiError(status: 0, code: "bad_response", message: "register") }
        SyncState.set(SyncState.deviceToken, token)
        // الخادم استلم هذه النسخة من الملف عند التسجيل
        SyncState.set(SyncState.profileVersion, local.updatedAt)
        if let deviceId = response["device_id"] as? String { PatientRepository.setCloudIdentity(deviceId) }
    }

    private func pushProfile() async throws {
        guard let patient = PatientRepository.get(), SyncState.get(SyncState.profileVersion) != patient.updatedAt else { return }
        try await Api.request("/v1/me", method: "PUT", body: [
            "full_name": patient.fullName,
            "phone": patient.phone,
            "email": patient.email.map { $0 as Any } ?? NSNull(),
            "gender": patient.gender.map { $0 as Any } ?? NSNull(),
        ])
        SyncState.set(SyncState.profileVersion, patient.updatedAt)
    }

    private func pushOutbox() async throws -> Int {
        var pushed = 0
        // كل دورة تحذف عمليات أو تزيد محاولاتها حتى تُستبعد، فالحلقة تنتهي دائمًا
        while true {
            let operations = Outbox.list(pushBatch)
            if operations.isEmpty { return pushed }
            let body: JSON = ["operations": operations.map { op -> JSON in
                [
                    "operation_id": op.operationId,
                    "entity_type": op.entityType,
                    "entity_id": op.entityId,
                    "operation_type": op.operationType,
                    "payload": Json.decode(op.payload) ?? [:],
                ]
            }]
            // أخطاء الشبكة والخادم تخرج من هنا وتبقى العمليات للمحاولة القادمة
            let response = try await Api.request("/v1/sync/push", method: "POST", body: body)
            for result in Json.objects(response["results"]) {
                guard let id = result["operation_id"] as? String else { continue }
                if (result["status"] as? String) == "applied" {
                    Outbox.remove(id)
                    pushed += 1
                } else {
                    Outbox.fail(id, (result["error"] as? String) ?? "rejected")
                }
            }
            if operations.count < pushBatch { return pushed }
        }
    }

    private func pull() async throws -> Int {
        var medicationsAfter = Int(SyncState.get(SyncState.pullMedications) ?? "") ?? 0
        var logsAfter = Int(SyncState.get(SyncState.pullLogs) ?? "") ?? 0
        var pulled = 0
        while true {
            let page = try await Api.request("/v1/sync/pull?medications_after=\(medicationsAfter)&logs_after=\(logsAfter)&limit=\(pullPage)")
            let medications = Json.objects(page["medications"]).compactMap(Json.parseMedication)
            let logs = Json.objects(page["logs"]).compactMap(Json.parseLog)
            try MedicationRepository.applyRemote(medications, logs)
            // المؤشر يُحفظ بعد حفظ الصفحة محليًا فقط
            guard let cursor = page["cursor"] as? JSON else { return pulled }
            medicationsAfter = Json.int(cursor["medications_after"]) ?? medicationsAfter
            logsAfter = Json.int(cursor["logs_after"]) ?? logsAfter
            SyncState.set(SyncState.pullMedications, String(medicationsAfter))
            SyncState.set(SyncState.pullLogs, String(logsAfter))
            pulled += medications.count + logs.count
            if Json.bool(page["has_more"]) != true { return pulled }
        }
    }
}
