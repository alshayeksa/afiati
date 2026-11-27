import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';

import { Header } from '@/components/Header';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { Colors, globalText, shadow } from '@/lib/theme';
import { useFamilyStore } from '@/store/familyStore';
import { FamilyLink } from '@/store/types';

const privacy: Array<{ value: FamilyLink['privacy_level']; title: string; body: string }> = [
  { value: 'full', title: 'كل شيء', body: 'الأدوية والجرعات والمواعيد' },
  { value: 'status_only', title: 'أخذت أم لا فقط', body: 'بدون أسماء الأدوية' },
  { value: 'alerts_only', title: 'تنبيهات التأخير فقط', body: 'عند فوات الجرعة' },
];

export default function PrivacyApproval() {
  const { pending, approve, reject, load } = useFamilyStore();
  const [level, setLevel] = useState<FamilyLink['privacy_level']>('full');
  useFocusEffect(useCallback(() => void load(), [load]));
  const request = pending[0];

  const answer = async (action: () => Promise<void>) => {
    try {
      await action();
    } catch (error) {
      Alert.alert('تعذر إرسال الرد', error instanceof Error ? error.message : 'حاول مرة أخرى.');
    }
  };

  return (
    <Screen>
      <Header title="موافقة الخصوصية" back />
      {!request ? <Text style={globalText.body}>لا توجد طلبات حالياً.</Text> : null}
      {request ? (
        <>
          <View style={styles.card}>
            <Text style={globalText.section}>{request.profiles?.full_name || 'أحد أفراد العائلة'} يريد متابعة أدويتك</Text>
          </View>
          {privacy.map((item) => (
            <Pressable key={item.value} onPress={() => setLevel(item.value)} style={[styles.option, level === item.value && styles.selected]}>
              <Text style={globalText.cardTitle}>{item.title}</Text>
              <Text style={globalText.body}>{item.body}</Text>
            </Pressable>
          ))}
          <PrimaryButton title="موافق" icon="checkmark-circle" onPress={() => void answer(() => approve(request.caregiver_id, level))} />
          <PrimaryButton title="رفض" icon="close-circle" tone="outline" onPress={() => void answer(() => reject(request.caregiver_id))} />
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: Colors.white, borderRadius: 16, padding: 20, ...shadow },
  option: { backgroundColor: Colors.white, borderRadius: 16, padding: 18, gap: 8, borderWidth: 1, borderColor: Colors.line },
  selected: { borderColor: Colors.primary, backgroundColor: Colors.tint },
});
