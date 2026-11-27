import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import { medicationRepository } from '@/lib/repositories/medicationRepository';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export async function refreshMedicationReminders(patientId: string, requestPermission = false) {
  let permissions = await Notifications.getPermissionsAsync();
  if (requestPermission && permissions.status !== 'granted') {
    permissions = await Notifications.requestPermissionsAsync();
  }
  if (permissions.status !== 'granted') return;

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('medication-reminders', {
      name: 'مواعيد الأدوية',
      importance: Notifications.AndroidImportance.HIGH,
      vibrationPattern: [0, 250, 250, 250],
    });
  }

  await Notifications.cancelAllScheduledNotificationsAsync();
  const medications = await medicationRepository.list(patientId);
  for (const medication of medications) {
    const schedule = async (trigger: Notifications.NotificationTriggerInput) => {
      await Notifications.scheduleNotificationAsync({
        content: {
          title: 'حان موعد دوائك',
          body: `${medication.name} — ${medication.dose_amount} ${medication.dose_unit}`,
          data: { type: 'medication_due', medication_id: medication.id },
        },
        trigger,
      });
    };

    for (const [index, time] of medication.times.entries()) {
      const [hour, minute] = time.split(':').map(Number);
      if (!Number.isInteger(hour) || !Number.isInteger(minute)) continue;

      if (medication.repeat_type === 'daily') {
        await schedule({
          type: Notifications.SchedulableTriggerInputTypes.DAILY,
          hour,
          minute,
          channelId: Platform.OS === 'android' ? 'medication-reminders' : undefined,
        });
        continue;
      }

      if (index > 0) continue;
      for (const day of medication.repeat_days) {
        await schedule(
          medication.repeat_type === 'weekly'
            ? {
                type: Notifications.SchedulableTriggerInputTypes.WEEKLY,
                weekday: day,
                hour,
                minute,
                channelId: Platform.OS === 'android' ? 'medication-reminders' : undefined,
              }
            : {
                type: Notifications.SchedulableTriggerInputTypes.MONTHLY,
                day,
                hour,
                minute,
                channelId: Platform.OS === 'android' ? 'medication-reminders' : undefined,
              },
        );
      }
    }
  }
}
