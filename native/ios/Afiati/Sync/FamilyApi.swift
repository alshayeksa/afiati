import Foundation

enum Privacy {
    static let full = "full"
    static let statusOnly = "status_only"
    static let alertsOnly = "alerts_only"

    static func title(_ level: String) -> String {
        switch level {
        case full: return tr("كل شيء", "Everything")
        case statusOnly: return tr("أخذت أم لا فقط", "Taken or not only")
        default: return tr("تنبيهات التأخير فقط", "Missed-dose alerts only")
        }
    }

    static func body(_ level: String) -> String {
        switch level {
        case full: return tr("يرى أسماء الأدوية والجرعات ومواعيدها", "Sees medicine names, doses and times")
        case statusOnly: return tr("يرى هل أخذت جرعاتك، بدون أسماء الأدوية", "Sees whether you took your doses, without medicine names")
        default: return tr("لا يرى التفاصيل، ويصله تنبيه عند فوات جرعة", "Doesn't see details; gets an alert when a dose is missed")
        }
    }
}

struct FamilyLink: Identifiable, Equatable {
    let id: String
    let patientId: String
    let caregiverId: String
    let privacyLevel: String
    let status: String
    let name: String
}

struct FamilyOverview: Equatable {
    let followers: [FamilyLink]
    let following: [FamilyLink]
    let pending: [FamilyLink]
}

struct PatientMedication {
    let id: String
    let name: String
    let doseAmount: Double
    let doseUnit: String
}

struct PatientDay {
    let patientName: String?
    let privacyLevel: String
    let medications: [PatientMedication]
    let logs: [DoseLog]
}

struct NotificationItem: Identifiable, Equatable {
    let id: String
    let type: String
    let title: String
    let body: String
    var read: Bool
    let createdAt: String
}

struct Invitation {
    let code: String
    let expiresAt: String
}

struct JoinResult {
    let status: String
    let patientName: String
}

struct FamilyError: Error, LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// طلب مساعدة: من الكبير لعائلته
struct HelpRequest: Identifiable, Equatable {
    let id: String
    let patientId: String
    let patientName: String
    /// رقم الكبير، يصل لعائلته فقط ليتصلوا به
    let patientPhone: String?
    let patientGender: String?
    let status: String
    let message: String?
    let acknowledgedBy: String?
    let acknowledgedByName: String?
    let acknowledgedByGender: String?
    /// للعائلة فقط: موقع الكبير ورابطه على خرائط Google
    let latitude: Double?
    let longitude: Double?
    let accuracy: Double?
    let mapUrl: String?
    /// للكبير: هل وصل موقعه لعائلته
    let locationShared: Bool
    let createdAt: String

    var answered: Bool { status == "acknowledged" }
}

struct HelpSent {
    let help: HelpRequest
    let notified: Int
}

struct ActiveHelp {
    let mine: HelpRequest?
    let family: [HelpRequest]
}

/// ميزات العائلة على الخادم. تحتاج إنترنت، وكل خطأ يصل برسالة واضحة بلغة الواجهة.
enum FamilyApi {
    static var offlineMessage: String {
        tr("خاصية العائلة تحتاج اتصالًا بالإنترنت. اتصل بالشبكة لمتابعة ذويك.", "Family features need internet. Connect to check on your loved ones.")
    }

    private static var messages: [String: String] {
        [
            "not_configured": tr("خدمة العائلة غير مُعدّة بعد.", "The family service isn't set up yet."),
            "network_error": tr("تعذر الاتصال بخدمة العائلة. تحقق من الإنترنت وحاول مرة أخرى.", "Couldn't reach the family service. Check your internet and try again."),
            "invalid_code": tr("الرمز غير صحيح أو منتهي الصلاحية.", "The code is wrong or expired."),
            "self_follow": tr("هذا رمزك أنت. أرسله لأحد أفراد عائلتك ليتابعك.", "This is your own code. Send it to a family member so they can follow you."),
            "forbidden": tr("لا تملك صلاحية متابعة هذا الشخص.", "You don't have permission to follow this person."),
            "request_not_found": tr("هذا الطلب لم يعد بانتظار الموافقة.", "This request is no longer waiting for approval."),
            "profile_exists": tr("هذا الملف مسجل مسبقًا على جهاز آخر.", "This profile is already registered on another device."),
            "help_not_found": tr("طلب المساعدة لم يعد موجودًا.", "The help request no longer exists."),
        ]
    }

    @discardableResult
    private static func call(_ path: String, method: String = "GET", body: JSON? = nil, rateLimitMessage: String? = nil) async throws -> JSON {
        if !Connectivity.shared.online { throw FamilyError(message: offlineMessage) }
        do {
            try await SyncEngine.ensureIdentity()
            return try await Api.request(path, method: method, body: body)
        } catch let error as ApiError {
            if error.status == 429, let message = rateLimitMessage { throw FamilyError(message: message) }
            throw FamilyError(message: messages[error.code] ?? tr("تعذر إكمال الطلب. حاول مرة أخرى.", "Couldn't complete the request. Try again."))
        }
    }

    private static func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlQueryValueAllowed) ?? value
    }

    private static func link(_ o: JSON) -> FamilyLink? {
        guard let id = Json.string(o["id"]) else { return nil }
        return FamilyLink(
            id: id, patientId: Json.string(o["patient_id"]) ?? "", caregiverId: Json.string(o["caregiver_id"]) ?? "",
            privacyLevel: Json.string(o["privacy_level"]) ?? Privacy.full, status: Json.string(o["status"]) ?? "",
            name: Json.string((o["profiles"] as? JSON)?["full_name"]) ?? tr("فرد من العائلة", "A family member")
        )
    }

    static func overview() async throws -> FamilyOverview {
        let o = try await call("/v1/family")
        return FamilyOverview(
            followers: Json.objects(o["followers"]).compactMap(link),
            following: Json.objects(o["following"]).compactMap(link),
            pending: Json.objects(o["pending"]).compactMap(link)
        )
    }

    static func createInvitation() async throws -> Invitation {
        let o = try await call("/v1/family/invitations", method: "POST")
        return Invitation(code: Json.string(o["code"]) ?? "", expiresAt: Json.string(o["expires_at"]) ?? "")
    }

    static func join(_ code: String) async throws -> JoinResult {
        let o = try await call("/v1/family/join", method: "POST", body: ["code": code],
                               rateLimitMessage: tr("محاولات كثيرة. انتظر ساعة ثم حاول مرة أخرى.", "Too many attempts. Wait an hour and try again."))
        return JoinResult(status: Json.string(o["status"]) ?? "pending", patientName: Json.string(o["patient_name"]) ?? "")
    }

    static func respond(caregiverId: String, approve: Bool, privacyLevel: String) async throws {
        try await call("/v1/family/respond", method: "POST",
                       body: ["caregiver_id": caregiverId, "action": approve ? "approve" : "reject", "privacy_level": privacyLevel])
    }

    static func patientDay(_ patientId: String, from: Date, to: Date) async throws -> PatientDay {
        let query = "from=\(encode(Iso.format(from)))&to=\(encode(Iso.format(to)))"
        let o = try await call("/v1/family/patients/\(encode(patientId))/today?\(query)")
        let medications = Json.objects(o["medications"]).compactMap { m -> PatientMedication? in
            guard let id = Json.string(m["id"]) else { return nil }
            return PatientMedication(id: id, name: Json.string(m["name"]) ?? "", doseAmount: Json.double(m["dose_amount"]) ?? 1,
                                     doseUnit: Json.string(m["dose_unit"]) ?? "")
        }
        var byId: [String: PatientMedication] = [:]
        for medication in medications { byId[medication.id] = medication }
        let logs = Json.objects(o["logs"]).compactMap(Json.parseLog).map { log -> DoseLog in
            var copy = log
            let medication = byId[log.medicationId]
            copy.medicationName = medication?.name ?? ""
            copy.doseAmount = medication?.doseAmount ?? 1
            copy.doseUnit = medication?.doseUnit ?? ""
            return copy
        }
        return PatientDay(patientName: Json.string(o["patient_name"]), privacyLevel: Json.string(o["privacy_level"]) ?? Privacy.full,
                          medications: medications, logs: logs)
    }

    static func remind(_ patientId: String) async throws {
        try await call("/v1/family/patients/\(encode(patientId))/remind", method: "POST",
                       rateLimitMessage: tr("أرسلت تذكيرًا قبل قليل. يمكنك الإرسال مرة أخرى بعد 10 دقائق.", "You sent a reminder a moment ago. You can send another in 10 minutes."))
    }

    private static func help(_ o: JSON?) throws -> HelpRequest {
        guard let o = o, let id = Json.string(o["id"]) else { throw FamilyError(message: tr("تعذر إكمال الطلب. حاول مرة أخرى.", "Couldn't complete the request. Try again.")) }
        return HelpRequest(
            id: id, patientId: Json.string(o["patient_id"]) ?? "", patientName: Json.string(o["patient_name"]) ?? "",
            patientPhone: Json.string(o["patient_phone"]), patientGender: Gender.of(Json.string(o["patient_gender"])),
            status: Json.string(o["status"]) ?? "open", message: Json.string(o["message"]),
            acknowledgedBy: Json.string(o["acknowledged_by"]), acknowledgedByName: Json.string(o["acknowledged_by_name"]),
            acknowledgedByGender: Gender.of(Json.string(o["acknowledged_by_gender"])),
            latitude: Json.double(o["latitude"]), longitude: Json.double(o["longitude"]), accuracy: Json.double(o["location_accuracy"]),
            mapUrl: Json.string(o["map_url"]), locationShared: Json.bool(o["location_shared"]) ?? false,
            createdAt: Json.string(o["created_at"]) ?? ""
        )
    }

    private static func withPoint(_ body: JSON, _ point: HelpPoint?) -> JSON {
        guard let point = point else { return body }
        var copy = body
        copy["latitude"] = point.latitude
        copy["longitude"] = point.longitude
        if let accuracy = point.accuracy { copy["accuracy"] = accuracy }
        return copy
    }

    static func requestHelp(message: String?, point: HelpPoint?) async throws -> HelpSent {
        let o = try await call("/v1/help", method: "POST", body: withPoint(["message": message.map { $0 as Any } ?? NSNull()], point),
                               rateLimitMessage: tr("أرسلت طلبات مساعدة كثيرة. اتصل بعائلتك أو بالإسعاف مباشرة.", "You sent many help requests. Call your family or an ambulance directly."))
        let request = try help(o["help"] as? JSON)
        return HelpSent(help: request, notified: Json.int(o["notified"]) ?? 0)
    }

    static func acknowledgeHelp(_ id: String) async throws -> HelpRequest {
        let o = try await call("/v1/help/\(encode(id))/acknowledge", method: "POST")
        return try help(o["help"] as? JSON)
    }

    /// الموقع إن وصل بعد إرسال الطلب
    static func sendHelpLocation(_ id: String, _ point: HelpPoint) async throws -> HelpRequest {
        let o = try await call("/v1/help/\(encode(id))/location", method: "POST", body: withPoint([:], point))
        return try help(o["help"] as? JSON)
    }

    static func resolveHelp(_ id: String) async throws -> HelpRequest {
        let o = try await call("/v1/help/\(encode(id))/resolve", method: "POST")
        return try help(o["help"] as? JSON)
    }

    static func activeHelp() async throws -> ActiveHelp {
        let o = try await call("/v1/help/active")
        let mine = (o["mine"] as? JSON).flatMap { try? help($0) }
        return ActiveHelp(mine: mine, family: Json.objects(o["family"]).compactMap { try? help($0) })
    }

    static func notifications() async throws -> [NotificationItem] {
        let o = try await call("/v1/notifications")
        return Json.objects(o["notifications"]).compactMap { item in
            guard let id = Json.string(item["id"]) else { return nil }
            return NotificationItem(id: id, type: Json.string(item["type"]) ?? "", title: Json.string(item["title"]) ?? "",
                                    body: Json.string(item["body"]) ?? "", read: Json.bool(item["read"]) ?? false,
                                    createdAt: Json.string(item["created_at"]) ?? "")
        }
    }

    static func markAllRead() async throws {
        try await call("/v1/notifications/read-all", method: "POST")
    }
}

extension CharacterSet {
    /// قيمة داخل الاستعلام: بلا & و= و+ و/ و:
    static let urlQueryValueAllowed: CharacterSet = {
        var set = CharacterSet.alphanumerics
        set.insert(charactersIn: "-._~")
        return set
    }()
}
