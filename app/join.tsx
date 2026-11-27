import { router } from 'expo-router';
import { useState } from 'react';
import { Alert, StyleSheet, TextInput, View } from 'react-native';

import { Header } from '@/components/Header';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { familyApi } from '@/lib/family';
import { Colors } from '@/lib/theme';

export default function Join() {
  const [code, setCode] = useState('');

  const submit = async () => {
    if (code.length !== 6) {
      Alert.alert('رمز غير مكتمل', 'أدخل ستة أرقام.');
      return;
    }
    try {
      const result = await familyApi.join(code);
      const name = result.patient_name || 'صاحب الدعوة';
      if (result.status === 'active') {
        Alert.alert('أنت تتابعه بالفعل', `أنت تتابع ${name} مسبقًا.`);
      } else {
        Alert.alert('تم الإرسال', `في انتظار موافقة ${name}.`);
      }
      router.back();
    } catch (error) {
      Alert.alert('تعذر الإرسال', error instanceof Error ? error.message : 'حاول مرة أخرى.');
    }
  };

  return (
    <Screen>
      <Header title="أدخل رمز الدعوة" back />
      <View style={styles.boxes}>
        {Array.from({ length: 6 }).map((_, index) => (
          <TextInput
            key={index}
            value={code[index] || ''}
            onChangeText={(text) => {
              const next = `${code.slice(0, index)}${text.slice(-1)}${code.slice(index + 1)}`.replace(/\D/g, '').slice(0, 6);
              setCode(next);
            }}
            style={styles.box}
            keyboardType="number-pad"
            maxLength={1}
            textAlign="center"
          />
        ))}
      </View>
      <PrimaryButton title="إرسال الطلب" icon="send" onPress={() => void submit()} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  boxes: { flexDirection: 'row', justifyContent: 'center', gap: 8, direction: 'rtl' },
  box: { width: 48, height: 64, borderRadius: 12, borderWidth: 1, borderColor: Colors.line, backgroundColor: Colors.white, fontSize: 26, fontWeight: '900' },
});
