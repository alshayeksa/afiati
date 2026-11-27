import * as BackgroundTask from 'expo-background-task';
import * as TaskManager from 'expo-task-manager';
import { AppState } from 'react-native';

import { onNetworkChange } from '@/lib/connectivity';
import { syncNow } from '@/lib/sync/syncEngine';

const SYNC_TASK = 'afiati-background-sync';

TaskManager.defineTask(SYNC_TASK, async () => {
  const result = await syncNow();
  return result.status === 'failed'
    ? BackgroundTask.BackgroundTaskResult.Failed
    : BackgroundTask.BackgroundTaskResult.Success;
});

export async function startSyncCoordinator() {
  void syncNow();

  const networkSubscription = onNetworkChange((online) => {
    if (online) void syncNow();
  });
  const appStateSubscription = AppState.addEventListener('change', (state) => {
    if (state === 'active') void syncNow();
  });

  try {
    const registered = await TaskManager.isTaskRegisteredAsync(SYNC_TASK);
    if (!registered) {
      await BackgroundTask.registerTaskAsync(SYNC_TASK, { minimumInterval: 15 });
    }
  } catch {
    // المزامنة عند الفتح وعودة الشبكة تبقى فعالة حتى إذا منع النظام مهام الخلفية.
  }

  return () => {
    networkSubscription.remove();
    appStateSubscription.remove();
  };
}

