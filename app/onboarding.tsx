import { router } from 'expo-router';
import { useState } from 'react';
import { Alert, Image, StyleSheet, Text, TextInput, View } from 'react-native';

import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { patientRepository } from '@/lib/repositories/patientRepository';
import { syncNow } from '@/lib/sync/syncEngine';
import { Colors, globalText, shadow } from '@/lib/theme';
import { useUserStore } from '@/store/userStore';

export default function Onboarding() {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [saving, setSaving] = useState(false);
  const setUser = useUserStore((state) => state.setUser);

  const submit = async () => {
    if (!name.trim()) {
      Alert.alert('الاسم مطلوب', 'اكتب الاسم الذي تريد ظهوره داخل عافيتي.');
      return;
    }
    if (phone.replace(/\D/g, '').length < 9) {
      Alert.alert('رقم الجوال غير مكتمل', 'أدخل رقم جوال صحيحًا.');
      return;
    }
    if (email.trim() && !/^\S+@\S+\.\S+$/.test(email.trim())) {
      Alert.alert('البريد غير صحيح', 'صحح البريد أو اتركه فارغًا.');
      return;
    }
    try {
      setSaving(true);
      const patient = await patientRepository.create({ full_name: name, phone, email });
      setUser(patient);
      void syncNow();
      router.replace('/(tabs)/dashboard');
    } catch (error) {
      Alert.alert('تعذر حفظ الملف', error instanceof Error ? error.message : 'حاول مرة أخرى.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Screen>
      <View style={styles.header}>
        <Image source={require('@/assets/logo.png')} style={styles.logo} />
        <Text style={[globalText.section, styles.rtlText]}>أهلًا بك في عافيتي</Text>
        <Text style={[globalText.body, styles.rtlText]}>أنشئ ملفك المحلي للبدء. لا نطلب كلمة مرور أو تسجيل حساب.</Text>
      </View>
      <View style={styles.card}>
        <Text style={[globalText.cardTitle, styles.rtlText]}>الاسم</Text>
        <TextInput value={name} onChangeText={setName} placeholder="الاسم" style={styles.input} textAlign="right" />
        <Text style={[globalText.cardTitle, styles.rtlText]}>رقم الجوال</Text>
        <TextInput value={phone} onChangeText={setPhone} placeholder="05xxxxxxxx" keyboardType="phone-pad" style={styles.input} textAlign="right" />
        <Text style={[globalText.cardTitle, styles.rtlText]}>البريد الإلكتروني (اختياري)</Text>
        <TextInput value={email} onChangeText={setEmail} placeholder="name@example.com" keyboardType="email-address" autoCapitalize="none" style={styles.input} textAlign="right" />
        <Text style={[globalText.small, styles.rtlText]}>تُحفظ بياناتك على جهازك، وتُزامن عند توفر الإنترنت. يلزم التحقق من الجوال لاحقًا للاستعادة وميزات العائلة.</Text>
      </View>
      <PrimaryButton title={saving ? 'جار الحفظ...' : 'ابدأ استخدام عافيتي'} icon="checkmark-circle" onPress={() => void submit()} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { direction: 'rtl', alignItems: 'center', gap: 10, paddingTop: 20 },
  logo: { width: 120, height: 120, borderRadius: 28 },
  card: { direction: 'rtl', alignItems: 'stretch', backgroundColor: Colors.white, borderRadius: 18, padding: 20, gap: 10, ...shadow },
  input: { direction: 'rtl', writingDirection: 'rtl', minHeight: 58, borderWidth: 1, borderColor: Colors.line, borderRadius: 14, paddingHorizontal: 16, fontSize: 18, color: Colors.text },
  rtlText: { width: '100%', textAlign: 'right', writingDirection: 'rtl' },
});
