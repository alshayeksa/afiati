import { Ionicons } from '@expo/vector-icons';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, Image, Pressable, StyleSheet, Text, View } from 'react-native';

import { MedicationCard } from '@/components/MedicationCard';
import { PrimaryButton } from '@/components/PrimaryButton';
import { ProgressCard } from '@/components/ProgressRing';
import { Screen } from '@/components/Screen';
import { getDoseStatus, timeGreeting, todayArabic } from '@/lib/helpers';
import { Colors, globalText, shadow } from '@/lib/theme';
import { useMedicationStore } from '@/store/medicationStore';
import { useUserStore } from '@/store/userStore';

export default function Dashboard() {
  const { medications, logs, load, markTaken } = useMedicationStore();
  const user = useUserStore((state) => state.user);
  const [markingLogId, setMarkingLogId] = useState<string | null>(null);
  useFocusEffect(useCallback(() => void load(), [load]));
  const done = logs.filter((log) => log.status === 'taken').length;
  const current = logs.find((log) => getDoseStatus(log).tone === 'gold');
  const currentMed = current ? medications.find((med) => med.id === current.medication_id) : undefined;

  const handleMarkTaken = async (logId: string, medicationId: string) => {
    if (markingLogId) return;

    try {
      setMarkingLogId(logId);
      await markTaken(logId, medicationId);
    } catch (error) {
      Alert.alert('لم يتم تسجيل الجرعة', error instanceof Error ? error.message : 'حاول مرة أخرى.');
    } finally {
      setMarkingLogId(null);
    }
  };

  return (
    <Screen>
      <View style={styles.dashboardRoot}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="فتح الملف الشخصي"
          onPress={() => router.navigate('/(tabs)/profile')}
          style={({ pressed }) => [styles.topRow, pressed && styles.topRowPressed]}>
          <Image source={require('@/assets/logo.png')} style={styles.logo} />
          <View style={styles.greetingBlock}>
            <View style={styles.greetingLine}>
              <Text style={[globalText.section, styles.greetingText, styles.greetingTitle]}>{timeGreeting()}</Text>
            </View>
            <View style={styles.greetingLine}>
              <Text style={[globalText.section, styles.greetingText, styles.userName]}>{user?.full_name || 'مرحبًا'}</Text>
            </View>
            <View style={styles.greetingLine}>
              <Text style={[globalText.small, styles.dateText]}>{todayArabic()}</Text>
            </View>
          </View>
          <Ionicons name="person-circle-outline" size={30} color={Colors.primary} />
        </Pressable>

        <ProgressCard taken={done} total={logs.length} />

        {medications.length === 0 ? (
          <View style={styles.empty}>
            <View style={styles.emptyIcon}>
              <Ionicons name="medical-outline" size={58} color={Colors.primary} />
            </View>
            <Text style={globalText.cardTitle}>لم تضف أي دواء بعد</Text>
            <View
              style={styles.emptySubtitle}
              accessibilityLabel="ابدأ بإضافة أول دواء وسنرتب مواعيده لك.">
              <View style={styles.emptySubtitleRow}>
                <Text style={[globalText.body, styles.emptySubtitleText]}>ابدأ بإضافة أول دواء وسنرتب مواعيده</Text>
              </View>
              <View style={styles.emptySubtitleRow}>
                <Text style={[globalText.body, styles.emptySubtitleText]}>لك.</Text>
              </View>
            </View>
            <PrimaryButton title="أضف دواءك الأول" icon="add-circle" onPress={() => router.push('/add-medication')} />
          </View>
        ) : (
          <>
            {current && currentMed ? (
              <PrimaryButton
                title={markingLogId === current.id ? 'جار التسجيل...' : `أخذت ${currentMed.name}`}
                onPress={() => void handleMarkTaken(current.id, currentMed.id)}
                style={{ minHeight: 72 }}
              />
            ) : null}
            <View style={styles.dosesTitleWrap}>
              <Text style={[globalText.section, styles.rtlAnchor, styles.dosesTitleAnchor, styles.dosesTitle]}>جرعات اليوم</Text>
            </View>
            {logs.map((log) => {
              const med = medications.find((item) => item.id === log.medication_id);
              return med ? <MedicationCard key={log.id} medication={med} log={log} /> : null;
            })}
          </>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  dashboardRoot: { gap: 18 },
  topRow: { flexDirection: 'row-reverse', alignItems: 'center', paddingHorizontal: 20 },
  topRowPressed: { opacity: 0.72 },
  greetingBlock: {
    flex: 1,
    direction: 'rtl',
    alignItems: 'stretch',
  },
  greetingLine: {
    width: '100%',
    minHeight: 22,
    direction: 'rtl',
    position: 'relative',
  },
  rtlAnchor: { position: 'absolute', start: 0 },
  greetingText: {
    position: 'absolute',
    start: 0,
    top: 0,
    fontSize: 17,
    lineHeight: 22,
    textAlign: 'left',
    writingDirection: 'rtl',
    width: '100%',
  },
  userName: {
    color: Colors.primary,
  },
  dateText: {
    position: 'absolute',
    start: 0,
    top: 0,
    fontSize: 13,
    lineHeight: 18,
    textAlign: 'left',
    writingDirection: 'rtl',
    width: '100%',
  },
  greetingTitle: { color: Colors.primary },
  dosesTitleWrap: { width: '100%', minHeight: 38, position: 'relative', direction: 'rtl' },
  dosesTitleAnchor: { top: 0 },
  dosesTitle: { direction: 'rtl', textAlign: 'right', writingDirection: 'rtl' },
  logo: { width: 70, height: 70, borderRadius: 18 },
  empty: { backgroundColor: Colors.white, borderRadius: 16, padding: 24, gap: 14, alignItems: 'center', ...shadow },
  emptyIcon: {
    width: 104,
    height: 104,
    borderRadius: 52,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.tint,
  },
  emptySubtitle: {
    width: '100%',
    direction: 'rtl',
    alignItems: 'stretch',
  },
  emptySubtitleRow: {
    width: '100%',
    direction: 'rtl',
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
  },
  emptySubtitleText: {
    flexShrink: 1,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
});
