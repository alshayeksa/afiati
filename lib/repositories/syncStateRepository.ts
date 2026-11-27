import { getDatabase } from '@/lib/database';

export type SyncStateKey =
  | 'device_token'
  | 'last_successful_sync'
  | 'profile_synced_version'
  | 'pull_medications_after'
  | 'pull_logs_after';

export const syncStateRepository = {
  async get(key: SyncStateKey) {
    const database = await getDatabase();
    const row = await database.getFirstAsync<{ value: string }>('SELECT value FROM sync_state WHERE key = ?', key);
    return row?.value ?? null;
  },

  async set(key: SyncStateKey, value: string) {
    const database = await getDatabase();
    await database.runAsync(
      `INSERT INTO sync_state (key, value, updated_at) VALUES (?, ?, ?)
       ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
      key,
      value,
      new Date().toISOString(),
    );
  },
};
