package com.alshayeksa.afiati.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * قاعدة البيانات المحلية: المصدر الأساسي لكل البيانات الشخصية، فالتطبيق يعمل بلا إنترنت.
 * الجداول نفسها في نسخة Expo (lib/database.ts)، والمزامنة تمر عبر جدول outbox.
 */
class AfiatiDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        listOf(
            """CREATE TABLE patients_local (
                id TEXT PRIMARY KEY NOT NULL,
                auth_user_id TEXT UNIQUE,
                full_name TEXT NOT NULL,
                phone TEXT NOT NULL,
                email TEXT,
                phone_verified INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                gender TEXT
            )""",
            """CREATE TABLE medications_local (
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
            )""",
            """CREATE TABLE medication_logs_local (
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
            )""",
            """CREATE TABLE outbox (
                operation_id TEXT PRIMARY KEY NOT NULL,
                patient_id TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                operation_type TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT
            )""",
            """CREATE TABLE sync_state (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )""",
            "CREATE INDEX idx_medications_local_patient ON medications_local(patient_id, is_active, updated_at)",
            "CREATE INDEX idx_logs_local_patient_time ON medication_logs_local(patient_id, scheduled_at)",
            "CREATE INDEX idx_outbox_created ON outbox(created_at)",
            HeartRateRepository.TABLE_SQL,
            HeartRateRepository.INDEX_SQL,
            VitalsRepository.TABLE_SQL,
            VitalsRepository.INDEX_SQL,
        ).forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // خطوة خطوة دون حذف بيانات المستخدم
        if (oldVersion < 2) {
            // 2: قياسات النبض بالكاميرا
            db.execSQL(HeartRateRepository.TABLE_SQL)
            db.execSQL(HeartRateRepository.INDEX_SQL)
        }
        if (oldVersion < 3) {
            // 3: رجل أو امرأة، لصياغة رسائل المساعدة
            db.execSQL("ALTER TABLE patients_local ADD COLUMN gender TEXT")
        }
        if (oldVersion < 4) {
            // 4: العلامات الحيوية اليدوية (الضغط، السكر، الحرارة)
            db.execSQL(VitalsRepository.TABLE_SQL)
            db.execSQL(VitalsRepository.INDEX_SQL)
        }
    }

    companion object {
        const val NAME = "afiati.db"
        const val VERSION = 4
    }
}

object Db {
    private lateinit var helper: AfiatiDatabase

    @Synchronized
    fun init(context: Context) {
        if (!::helper.isInitialized) helper = AfiatiDatabase(context.applicationContext)
    }

    val db: SQLiteDatabase get() = helper.writableDatabase

    /** معاملة واحدة: كل التغييرات تُحفظ معًا أو لا يُحفظ شيء */
    fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val database = db
        database.beginTransaction()
        try {
            val result = block(database)
            database.setTransactionSuccessful()
            return result
        } finally {
            database.endTransaction()
        }
    }
}

fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column))
fun Cursor.strOrNull(column: String): String? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
fun Cursor.intOrNull(column: String): Int? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }
fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))

/** عمود قد لا يكون في الاستعلام (مثل أعمدة الربط مع جدول الأدوية) */
fun Cursor.optStr(column: String): String? = getColumnIndex(column).let { if (it < 0 || isNull(it)) null else getString(it) }
fun Cursor.optDouble(column: String): Double? = getColumnIndex(column).let { if (it < 0 || isNull(it)) null else getDouble(it) }

inline fun <T> Cursor.list(map: (Cursor) -> T): List<T> = use { cursor ->
    buildList { while (cursor.moveToNext()) add(map(cursor)) }
}

inline fun <T> Cursor.first(map: (Cursor) -> T): T? = use { cursor -> if (cursor.moveToFirst()) map(cursor) else null }
