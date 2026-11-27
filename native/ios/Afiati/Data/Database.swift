import Foundation
import SQLite3

/// SQLite يحتاج نسخ النص المربوط، لأن نص Swift قد يُحرَّر قبل تنفيذ الجملة
private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

struct DatabaseError: Error, LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// صف من نتيجة استعلام. الأعمدة الفارغة (NULL) غير موجودة في القاموس.
struct Row {
    let values: [String: Any]

    func str(_ column: String) -> String { values[column] as? String ?? "" }
    func strOrNil(_ column: String) -> String? { values[column] as? String }
    func int(_ column: String) -> Int { intOrNil(column) ?? 0 }
    func intOrNil(_ column: String) -> Int? {
        if let value = values[column] as? Int64 { return Int(value) }
        if let value = values[column] as? Double { return Int(value) }
        return nil
    }
    func double(_ column: String) -> Double { doubleOrNil(column) ?? 0 }
    func doubleOrNil(_ column: String) -> Double? {
        if let value = values[column] as? Double { return value }
        if let value = values[column] as? Int64 { return Double(value) }
        return nil
    }
}

/**
 قاعدة البيانات المحلية: المصدر الأساسي لكل البيانات الشخصية، فالتطبيق يعمل بلا إنترنت.
 الجداول نفسها في نسخة أندرويد (AfiatiDatabase)، والمزامنة تمر عبر جدول outbox.
 كل الوصول عبر طابور واحد، فيصلح من أي خيط.
 */
final class Database {
    static let shared = Database()
    static let version = 4

    private var handle: OpaquePointer?
    private let queue = DispatchQueue(label: "ai.alshaye.afiati.database")
    private let queueKey = DispatchSpecificKey<Bool>()

    private init() {
        queue.setSpecific(key: queueKey, value: true)
        let folder = (try? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true))
            ?? FileManager.default.temporaryDirectory
        let path = folder.appendingPathComponent("afiati.db").path
        if sqlite3_open_v2(path, &handle, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil) != SQLITE_OK {
            NSLog("Afiati: could not open the database")
        }
        // التذكير من شاشة القفل يقرأ القاعدة والجوال مقفل (بعد أول فتح له)
        try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: path)
        try? run("PRAGMA foreign_keys = ON")
        _ = try? query("PRAGMA journal_mode = WAL")
        migrate()
    }

    /// ينفّذ الكتلة داخل طابور القاعدة (أو مباشرة إن كنا فيه)
    func sync<T>(_ block: () throws -> T) rethrows -> T {
        if DispatchQueue.getSpecific(key: queueKey) == true { return try block() }
        return try queue.sync(execute: block)
    }

    private var lastError: String {
        if let message = sqlite3_errmsg(handle) { return String(cString: message) }
        return "unknown database error"
    }

    private func prepare(_ sql: String, _ params: [Any?]) throws -> OpaquePointer {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &statement, nil) == SQLITE_OK, let prepared = statement else {
            throw DatabaseError(message: lastError)
        }
        for (offset, param) in params.enumerated() {
            let index = Int32(offset + 1)
            guard let value = param else {
                sqlite3_bind_null(prepared, index)
                continue
            }
            if let flag = value as? Bool {
                sqlite3_bind_int64(prepared, index, flag ? 1 : 0)
            } else if let number = value as? Int {
                sqlite3_bind_int64(prepared, index, Int64(number))
            } else if let number = value as? Int64 {
                sqlite3_bind_int64(prepared, index, number)
            } else if let number = value as? Double {
                sqlite3_bind_double(prepared, index, number)
            } else if let text = value as? String {
                sqlite3_bind_text(prepared, index, text, -1, SQLITE_TRANSIENT)
            } else {
                sqlite3_bind_null(prepared, index)
            }
        }
        return prepared
    }

    /// ينفّذ جملة ويعيد عدد الصفوف المتغيرة
    @discardableResult
    func run(_ sql: String, _ params: [Any?] = []) throws -> Int {
        try sync {
            let statement = try prepare(sql, params)
            defer { sqlite3_finalize(statement) }
            let code = sqlite3_step(statement)
            guard code == SQLITE_DONE || code == SQLITE_ROW else { throw DatabaseError(message: lastError) }
            return Int(sqlite3_changes(handle))
        }
    }

    func query(_ sql: String, _ params: [Any?] = []) throws -> [Row] {
        try sync {
            let statement = try prepare(sql, params)
            defer { sqlite3_finalize(statement) }
            var rows: [Row] = []
            while true {
                let code = sqlite3_step(statement)
                if code == SQLITE_DONE { break }
                guard code == SQLITE_ROW else { throw DatabaseError(message: lastError) }
                var values: [String: Any] = [:]
                for column in 0..<sqlite3_column_count(statement) {
                    let name = String(cString: sqlite3_column_name(statement, column))
                    switch sqlite3_column_type(statement, column) {
                    case SQLITE_INTEGER: values[name] = sqlite3_column_int64(statement, column)
                    case SQLITE_FLOAT: values[name] = sqlite3_column_double(statement, column)
                    case SQLITE_TEXT:
                        if let text = sqlite3_column_text(statement, column) { values[name] = String(cString: text) }
                    default: break
                    }
                }
                rows.append(Row(values: values))
            }
            return rows
        }
    }

    /// معاملة واحدة: كل التغييرات تُحفظ معًا أو لا يُحفظ شيء
    func transaction<T>(_ block: () throws -> T) throws -> T {
        try sync {
            try run("BEGIN IMMEDIATE")
            do {
                let result = try block()
                try run("COMMIT")
                return result
            } catch {
                _ = try? run("ROLLBACK")
                throw error
            }
        }
    }

    private func migrate() {
        let current = (try? query("PRAGMA user_version").first?.int("user_version")) ?? 0
        guard current < Database.version else { return }
        do {
            try transaction {
                if current < 1 {
                    for sql in Database.schema { try run(sql) }
                }
                // الإصدارات اللاحقة تُضاف هنا خطوة خطوة دون حذف بيانات المستخدم
                try run("PRAGMA user_version = \(Database.version)")
            }
        } catch {
            NSLog("Afiati: database migration failed: \(error.localizedDescription)")
        }
    }

    /// المخطط كاملًا بالإصدار 4 (كما في أندرويد بعد كل الترقيات)
    private static let schema: [String] = [
        """
        CREATE TABLE IF NOT EXISTS patients_local (
            id TEXT PRIMARY KEY NOT NULL,
            auth_user_id TEXT UNIQUE,
            full_name TEXT NOT NULL,
            phone TEXT NOT NULL,
            email TEXT,
            phone_verified INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            gender TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS medications_local (
            id TEXT PRIMARY KEY NOT NULL,
            patient_id TEXT NOT NULL,
            name TEXT NOT NULL,
            dose_amount REAL NOT NULL,
            dose_unit TEXT NOT NULL,
            repeat_type TEXT NOT NULL DEFAULT 'daily',
            repeat_days_json TEXT NOT NULL DEFAULT '[]',
            frequency INTEGER NOT NULL,
            times_json TEXT NOT NULL,
            stock_count INTEGER,
            stock_alert INTEGER NOT NULL DEFAULT 7,
            is_active INTEGER NOT NULL DEFAULT 1,
            notes TEXT,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            deleted_at TEXT,
            FOREIGN KEY (patient_id) REFERENCES patients_local(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS medication_logs_local (
            id TEXT PRIMARY KEY NOT NULL,
            medication_id TEXT NOT NULL,
            patient_id TEXT NOT NULL,
            scheduled_at TEXT NOT NULL,
            taken_at TEXT,
            status TEXT NOT NULL DEFAULT 'pending',
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            UNIQUE (medication_id, scheduled_at),
            FOREIGN KEY (medication_id) REFERENCES medications_local(id) ON DELETE CASCADE,
            FOREIGN KEY (patient_id) REFERENCES patients_local(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS outbox (
            operation_id TEXT PRIMARY KEY NOT NULL,
            patient_id TEXT NOT NULL,
            entity_type TEXT NOT NULL,
            entity_id TEXT NOT NULL,
            operation_type TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            created_at TEXT NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            last_error TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS sync_state (
            key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_medications_local_patient ON medications_local(patient_id, is_active, updated_at)",
        "CREATE INDEX IF NOT EXISTS idx_logs_local_patient_time ON medication_logs_local(patient_id, scheduled_at)",
        "CREATE INDEX IF NOT EXISTS idx_outbox_created ON outbox(created_at)",
        """
        CREATE TABLE IF NOT EXISTS heart_rate_local (
            id TEXT PRIMARY KEY NOT NULL,
            patient_id TEXT NOT NULL,
            bpm INTEGER NOT NULL CHECK (bpm BETWEEN 30 AND 250),
            measured_at TEXT NOT NULL,
            quality REAL NOT NULL
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_heart_rate_patient ON heart_rate_local(patient_id, measured_at)",
        """
        CREATE TABLE IF NOT EXISTS vital_readings_local (
            id TEXT PRIMARY KEY NOT NULL,
            patient_id TEXT NOT NULL,
            type TEXT NOT NULL CHECK (type IN ('pressure', 'sugar', 'temperature')),
            value REAL NOT NULL,
            value2 REAL,
            context TEXT,
            measured_at TEXT NOT NULL
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_vitals_patient_type ON vital_readings_local(patient_id, type, measured_at)",
    ]
}
