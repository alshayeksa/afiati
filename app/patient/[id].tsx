import { useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import { Header } from '@/components/Header';
import { PrimaryButton } from '@/components/PrimaryButton';
import { ProgressRing } from '@/components/ProgressRing';
import { Screen } from '@/components/Screen';
import { familyApi, PatientDay } from '@/lib/family';
import { Colors, globalText, shadow } from '@/lib/theme';

export default function PatientDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [day, setDay] = useState<PatientDay | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    const end = new Date(start);
    end.setDate(end.getDate() + 1);
    try {
      setDay(await familyApi.patientDay(id, start, end));
      setError(null);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'تعذر تحميل البيانات.');
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const sendReminder = async () => {
    if (!id) return;
    try {
      await familyApi.remind(id);
      Alert.alert('تم الإرسال', 'تم إرسال تذكير لطيف.');
    } catch (sendError) {
      Alert.alert('تعذر الإرسال', sendError instanceof Error ? sendError.message : 'حاول مرة أخرى.');
    }
  };

  const logs = day?.logs ?? [];
  const medications = day?.medications ?? [];
  const done = logs.filter((log) => log.status === 'taken').length;
  const alertsOnly = day?.privacy_level === 'alerts_only';

  return (
    <Screen>
      <Header title={day?.patient_name ? `أدوية ${day.patient_name} اليوم` : 'أدوية اليوم'} back />
      {error ? <Text style={globalText.body}>{error}</Text> : null}
      {day && !alertsOnly ? <Text style={globalText.small}>آخر تحديث الآن</Text> : null}
      {day && !alertsOnly ? <ProgressRing done={done} total={logs.length} /> : null}
      {alertsOnly ? <Text style={globalText.body}>اختار أن تصلك تنبيهات التأخير فقط، لذلك لا تظهر تفاصيل الجرعات هنا.</Text> : null}
      {day && !alertsOnly && logs.length === 0 ? <Text style={globalText.body}>لا توجد جرعات مسجلة اليوم.</Text> : null}
      {logs.map((log) => {
        const medication = medications.find((item) => item.id === log.medication_id);
        return (
          <View key={log.id} style={styles.card}>
            <Text style={[globalText.cardTitle, styles.rtlText]}>{medication?.name || 'جرعة دواء'}</Text>
            <Text style={[globalText.body, styles.rtlText]}>
              {log.status === 'taken' ? 'تم أخذ الجرعة' : log.status === 'missed' ? 'فاتت الجرعة' : 'بانتظار المريض'}
            </Text>
          </View>
        );
      })}
      <PrimaryButton title="إرسال تذكير" icon="notifications" onPress={() => void sendReminder()} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { direction: 'rtl', alignItems: 'stretch', backgroundColor: Colors.white, borderRadius: 16, padding: 18, gap: 6, ...shadow },
  rtlText: { width: '100%', textAlign: 'right', writingDirection: 'rtl' },
});

