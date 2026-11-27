import CryptoKit
import Foundation

/// معرّفات ثابتة: نفس الجرعة لها نفس المعرّف على كل جهاز، مثل Ids.kt في أندرويد وlib/sync/ids.ts.
enum Ids {
    /// أول 128 بت من بصمة hex بصيغة UUID (الإصدار 8، RFC 9562)
    static func uuidFromHex(_ hex: String) -> String {
        let h = Array(String(hex.prefix(32)).lowercased())
        precondition(h.count == 32 && h.allSatisfy { $0.isHexDigit }, "needs at least 32 hex characters")
        let nibble = Int(String(h[16]), radix: 16) ?? 0
        let variant = String((nibble & 0x3) | 0x8, radix: 16)
        func part(_ from: Int, _ to: Int) -> String { String(h[from..<to]) }
        return "\(part(0, 8))-\(part(8, 12))-8\(part(13, 16))-\(variant)\(part(17, 20))-\(part(20, 32))"
    }

    static func doseLogKey(_ medicationId: String, _ scheduledAt: String) -> String { "\(medicationId)|\(scheduledAt)" }

    static func sha256Hex(_ text: String) -> String {
        SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    static func doseLogId(_ medicationId: String, _ scheduledAt: String) -> String {
        uuidFromHex(sha256Hex(doseLogKey(medicationId, scheduledAt)))
    }

    static func random() -> String { UUID().uuidString.lowercased() }
}
