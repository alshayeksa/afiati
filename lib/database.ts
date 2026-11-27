import * as SQLite from 'expo-sqlite';

const DATABASE_NAME = 'afiati.db';
const DATABASE_VERSION = 3;

let databasePromise: Promise<SQLite.SQLiteDatabase> | null = null;

export const getDatabase = () => {
  if (!databasePromise) {
    databasePromise = SQLite.openDatabaseAsync(DATABASE_NAME).then(async (database) => {
      await migrateDatabase(database);
      return database;
    });
  }
  return databasePromise;
};

export const initializeDatabase = async () => {
  await getDatabase();
};

async function migrateDatabase(database: SQLite.SQLiteDatabase) {
  await database.execAsync('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON;');

  const versionRow = await database.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
  const currentVersion = versionRow?.user_version ?? 0;
  if (currentVersion >= DATABASE_VERSION) return;

  await database.withExclusiveTransactionAsync(async (transaction) => {
    if (currentVersion < 1) {
      await transaction.execAsync(`
        CREATE TABLE IF NOT EXISTS patients_local (
          id TEXT PRIMARY KEY NOT NULL,
          auth_user_id TEXT UNIQUE,
          full_name TEXT NOT NULL,
          phone TEXT NOT NULL,
          email TEXT,
          phone_verified INTEGER NOT NULL DEFAULT 0,
          created_at TEXT NOT NULL,
          updated_at TEXT NOT NULL
        );

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
        );

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
        );

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
        );

        CREATE TABLE IF NOT EXISTS sync_state (
          key TEXT PRIMARY KEY NOT NULL,
          value TEXT NOT NULL,
          updated_at TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_medications_local_patient
          ON medications_local(patient_id, is_active, updated_at);
        CREATE INDEX IF NOT EXISTS idx_logs_local_patient_time
          ON medication_logs_local(patient_id, scheduled_at);
        CREATE INDEX IF NOT EXISTS idx_outbox_created
          ON outbox(created_at);
      `);
    }

    if (currentVersion < 2) {
      await transaction.runAsync(
        `UPDATE patients_local
         SET phone = '0' || substr(phone, 5),
             updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
         WHERE phone GLOB '+9665[0-9]*' AND length(phone) = 13`,
      );
    }

    if (currentVersion >= 1 && currentVersion < 3) {
      await transaction.execAsync(`
        ALTER TABLE medications_local ADD COLUMN repeat_type TEXT NOT NULL DEFAULT 'daily';
        ALTER TABLE medications_local ADD COLUMN repeat_days_json TEXT NOT NULL DEFAULT '[]';
      `);
    }

    await transaction.execAsync(`PRAGMA user_version = ${DATABASE_VERSION}`);
  });
}
