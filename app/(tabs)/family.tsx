import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Header } from '@/components/Header';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { Colors, globalText, shadow } from '@/lib/theme';
import { useFamilyStore } from '@/store/familyStore';

export default function Family() {
  const [tab, setTab] = useState<'followers' | 'following'>('followers');
  const { followers, following, pending, online, error, load } = useFamilyStore();
  useFocusEffect(useCallback(() => void load(), [load]));
  const list = tab === 'followers' ? followers : following;

  return (
    <Screen>
      <Header title="عائلتي" />
      {!online ? (
        <View style={styles.offline}>
          <View style={styles.textRow}><Text style={[globalText.cardTitle, styles.rtlText]}>العائلة تحتاج الإنترنت</Text></View>
          <View style={styles.textRow}><Text style={[globalText.body, styles.rtlText]}>{error}</Text></View>
        </View>
      ) : null}
      <View style={styles.tabs}>
        <Tab label="من يتابعني" active={tab === 'followers'} onPress={() => setTab('followers')} />
        <Tab label="أتابعهم" active={tab === 'following'} onPress={() => setTab('following')} />
      </View>

      {online && pending.length > 0 ? (
        <Pressable style={styles.alert} onPress={() => router.push('/privacy-approval')}>
          <View style={styles.textRow}>
            <Text style={[globalText.cardTitle, styles.rtlText]}>طلبات تحتاج موافقتك</Text>
          </View>
          <View style={styles.textRow}>
            <Text style={[globalText.body, styles.rtlText]}>{pending.length} طلب متابعة جديد</Text>
          </View>
        </Pressable>
      ) : null}

      {online && list.length === 0 ? (
        <View style={styles.card}>
          <View style={styles.textRow}>
            <Text style={[globalText.cardTitle, styles.rtlText]}>{tab === 'followers' ? 'لا يوجد متابعون بعد' : 'لا تتابع أحداً بعد'}</Text>
          </View>
          <View style={styles.emptySubtitle} accessibilityLabel="يمكنك مشاركة رمز دعوة أو إدخال رمز من أحد أفراد عائلتك.">
            <View style={styles.emptySubtitleRow}>
              <Text style={[globalText.body, styles.rtlText]}>يمكنك مشاركة رمز دعوة أو إدخال رمز من</Text>
            </View>
            <View style={styles.emptySubtitleRow}>
              <Text style={[globalText.body, styles.rtlText]}>أحد أفراد عائلتك.</Text>
            </View>
          </View>
        </View>
      ) : null}

      {online && list.map((link) => (
        <Pressable
          key={link.id}
          style={styles.card}
          onPress={() => {
            if (tab === 'following') router.push(`/patient/${link.patient_id}`);
          }}>
          <View style={styles.textRow}>
            <Text style={[globalText.cardTitle, styles.rtlText]}>{link.profiles?.full_name || 'فرد من العائلة'}</Text>
          </View>
          <View style={styles.textRow}>
            <Text style={[globalText.body, styles.rtlText]}>{tab === 'followers' ? 'يتابع أدويتك' : 'تتابع أدويته اليوم'}</Text>
          </View>
          <View style={styles.textRow}>
            <Text style={styles.badge}>{link.privacy_level === 'full' ? 'كل شيء' : link.privacy_level === 'status_only' ? 'الحالة فقط' : 'تنبيهات فقط'}</Text>
          </View>
        </Pressable>
      ))}

      <PrimaryButton
        title={tab === 'followers' ? 'دعوة شخص جديد' : 'متابعة شخص جديد'}
        icon={tab === 'followers' ? 'share-social' : 'person-add'}
        onPress={() => online && router.push(tab === 'followers' ? '/invite' : '/join')}
      />
    </Screen>
  );
}

function Tab({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.tab, active && styles.active]}>
      <Text style={[globalText.body, active && { color: Colors.primary, fontWeight: '900' }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  tabs: { flexDirection: 'row', backgroundColor: Colors.white, borderRadius: 16, padding: 6, direction: 'rtl' },
  tab: { flex: 1, minHeight: 58, alignItems: 'center', justifyContent: 'center', borderRadius: 12 },
  active: { backgroundColor: Colors.tint },
  card: { direction: 'rtl', alignItems: 'stretch', backgroundColor: Colors.white, borderRadius: 16, padding: 20, gap: 8, ...shadow },
  alert: { direction: 'rtl', alignItems: 'stretch', backgroundColor: '#FFF4DF', borderRadius: 16, padding: 20, gap: 8, borderWidth: 1, borderColor: Colors.gold },
  offline: { direction: 'rtl', alignItems: 'stretch', backgroundColor: Colors.tint, borderRadius: 16, padding: 20, gap: 8, borderWidth: 1, borderColor: Colors.primaryLight },
  textRow: { width: '100%', direction: 'rtl', flexDirection: 'row', justifyContent: 'flex-start', alignItems: 'center' },
  rtlText: { flexShrink: 1, textAlign: 'right', writingDirection: 'rtl' },
  emptySubtitle: { width: '100%', direction: 'rtl', alignItems: 'stretch' },
  emptySubtitleRow: { width: '100%', direction: 'rtl', flexDirection: 'row', justifyContent: 'flex-start', alignItems: 'center' },
  badge: { backgroundColor: Colors.tint, color: Colors.primary, padding: 8, borderRadius: 999, fontSize: 16, fontWeight: '800', textAlign: 'right', writingDirection: 'rtl' },
});
