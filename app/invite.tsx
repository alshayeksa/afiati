import QRCode from 'react-native-qrcode-svg';
import { useEffect, useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import { Header } from '@/components/Header';
import { InviteCode } from '@/components/InviteCode';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { familyApi } from '@/lib/family';
import { openWhatsAppInvite } from '@/lib/helpers';
import { Colors, globalText, shadow } from '@/lib/theme';

export default function Invite() {
  const [code, setCode] = useState('------');

  const generate = async () => {
    try {
      const invitation = await familyApi.createInvitation();
      setCode(invitation.code);
    } catch (error) {
      Alert.alert('تعذر إنشاء الرمز', error instanceof Error ? error.message : 'حاول مرة أخرى.');
    }
  };

  useEffect(() => {
    void generate();
  }, []);

  return (
    <Screen>
      <Header title="ادعُ أحد أفراد عائلتك" back />
      <View style={styles.card}>
        <InviteCode code={code} />
        <Text style={globalText.body}>صالح لمدة 24 ساعة</Text>
      </View>
      <View style={styles.qr}>{code.includes('-') ? null : <QRCode value={code} size={180} />}</View>
      <PrimaryButton title="مشاركة عبر واتساب" icon="logo-whatsapp" onPress={() => void openWhatsAppInvite(code)} />
      <PrimaryButton title="تجديد الرمز" icon="refresh" tone="outline" onPress={() => void generate()} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: Colors.white, borderRadius: 16, padding: 22, alignItems: 'center', gap: 16, ...shadow },
  qr: { minHeight: 220, borderRadius: 16, backgroundColor: Colors.white, alignItems: 'center', justifyContent: 'center', ...shadow },
});
