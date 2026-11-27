import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Header } from '@/components/Header';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { familyApi, NotificationItem } from '@/lib/family';
import { patientRepository } from '@/lib/repositories/patientRepository';
import { Colors, globalText, shadow } from '@/lib/theme';

export default function Notifications() {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    if (!(await patientRepository.get())) return;
    try {
      const { notifications } = await familyApi.notifications();
      setItems(notifications);
      setError(null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'تعذر تحميل الإشعارات.');
    }
  }, []);
  useFocusEffect(useCallback(() => void load(), [load]));

  const markAllRead = async () => {
    try {
      await familyApi.markAllRead();
      await load();
    } catch (markError) {
      setError(markError instanceof Error ? markError.message : 'تعذر تحديث الإشعارات.');
    }
  };

  return (
    <Screen>
      <Header title="الإشعارات" />
      {error ? (
        <View style={styles.card}>
          <View style={styles.textRow}><Text style={[globalText.body, styles.rtlText]}>{error}</Text></View>
        </View>
      ) : null}
      {items.length === 0 ? (
        <View style={styles.emptyState}>
          <View style={styles.textRow}>
            <Text style={[globalText.body, styles.rtlText]}>لا توجد إشعارات بعد.</Text>
          </View>
        </View>
      ) : null}
      {items.map((item) => (
        <View key={item.id} style={styles.card}>
          <View style={styles.textRow}>
            <Text style={[globalText.cardTitle, styles.rtlText]}>{item.title}</Text>
          </View>
          <View style={styles.textRow}>
            <Text style={[globalText.body, styles.rtlText]}>{item.body}</Text>
          </View>
        </View>
      ))}
      <PrimaryButton
        title="تحديد الكل كمقروء"
        icon="checkmark-done"
        tone="outline"
        onPress={() => void markAllRead()}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { direction: 'rtl', alignItems: 'stretch', backgroundColor: Colors.white, borderRadius: 16, padding: 20, gap: 8, ...shadow },
  emptyState: { direction: 'rtl', alignItems: 'stretch' },
  textRow: { width: '100%', flexDirection: 'row', justifyContent: 'flex-start', alignItems: 'center' },
  rtlText: { textAlign: 'right', writingDirection: 'rtl' },
});
