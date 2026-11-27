import { router, useFocusEffect } from 'expo-router';
import { useCallback } from 'react';
import { Alert, Text } from 'react-native';

import { Header } from '@/components/Header';
import { MedicationCard } from '@/components/MedicationCard';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { globalText } from '@/lib/theme';
import { useMedicationStore } from '@/store/medicationStore';

export default function Medications() {
  const { medications, load, deleteMedication } = useMedicationStore();
  useFocusEffect(useCallback(() => void load(), [load]));
  return (
    <Screen>
      <Header title="كل أدويتي" />
      {medications.length === 0 ? <Text style={globalText.body}>لا توجد أدوية محفوظة.</Text> : null}
      {medications.map((med) => (
        <MedicationCard key={med.id} medication={med} />
      ))}
      <PrimaryButton title="إضافة دواء" icon="add-circle" onPress={() => router.push('/add-medication')} />
      {medications[0] ? (
        <PrimaryButton
          title="حذف آخر دواء"
          icon="trash"
          tone="danger"
          onPress={() =>
            Alert.alert('تأكيد الحذف', 'هل تريد حذف هذا الدواء؟', [
              { text: 'إلغاء' },
              { text: 'حذف', style: 'destructive', onPress: () => void deleteMedication(medications[0].id) },
            ])
          }
        />
      ) : null}
    </Screen>
  );
}
