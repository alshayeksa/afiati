import { Ionicons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import { Alert, Image, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { Header } from '@/components/Header';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { patientRepository } from '@/lib/repositories/patientRepository';
import { syncNow } from '@/lib/sync/syncEngine';
import { Colors, globalText, shadow } from '@/lib/theme';
import { useUserStore } from '@/store/userStore';

export default function Profile() {
  const user = useUserStore((state) => state.user);
  const setUser = useUserStore((state) => state.setUser);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveSuccessVisible, setSaveSuccessVisible] = useState(false);
  const [name, setName] = useState(user?.full_name ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [email, setEmail] = useState(user?.email ?? '');

  useEffect(() => {
    if (!user || editing) return;
    setName(user.full_name);
    setPhone(user.phone);
    setEmail(user.email ?? '');
  }, [editing, user]);

  const cancelEditing = () => {
    setName(user?.full_name ?? '');
    setPhone(user?.phone ?? '');
    setEmail(user?.email ?? '');
    setEditing(false);
  };

  const save = async () => {
    if (!name.trim()) {
      Alert.alert('الاسم مطلوب', 'اكتب الاسم الذي تريد ظهوره داخل عافيتي.');
      return;
    }
    if (phone.replace(/\D/g, '').length < 9) {
      Alert.alert('رقم الجوال غير مكتمل', 'أدخل رقم جوال صحيحًا.');
      return;
    }
    if (email.trim() && !/^\S+@\S+\.\S+$/.test(email.trim())) {
      Alert.alert('البريد غير صحيح', 'صحح البريد الإلكتروني أو اتركه فارغًا.');
      return;
    }

    try {
      setSaving(true);
      const updated = await patientRepository.update({ full_name: name, phone, email });
      setUser(updated);
      setEditing(false);
      void syncNow();
      setSaveSuccessVisible(true);
    } catch (error) {
      Alert.alert('تعذر حفظ البيانات', error instanceof Error ? error.message : 'حاول مرة أخرى.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Screen scroll={false}>
      <View style={styles.root}>
        <Header title="ملفي" back />

        <View style={styles.hero}>
          <Image source={require('@/assets/logo.png')} style={styles.logo} />
          <View style={styles.heroInfo}>
            <Text style={[globalText.cardTitle, styles.heroName]}>{user?.full_name || 'مستخدم عافيتي'}</Text>
            <View style={styles.statusBadge}>
              <Ionicons name="shield-checkmark" size={18} color={Colors.primaryDark} />
              <Text style={styles.statusText}>ملفك محفوظ بأمان</Text>
            </View>
          </View>
        </View>

        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={[globalText.cardTitle, styles.rtlText]}>البيانات الشخصية</Text>
          </View>

          {editing ? (
            <>
              <EditableField label="الاسم:" value={name} onChangeText={setName} icon="person-outline" />
              <EditableField
                label="رقم الجوال:"
                value={phone}
                onChangeText={setPhone}
                icon="call-outline"
                keyboardType="phone-pad"
              />
              <EditableField
                label="البريد الإلكتروني:"
                value={email}
                onChangeText={setEmail}
                icon="mail-outline"
                keyboardType="email-address"
                placeholder="اختياري"
                stacked
              />
            </>
          ) : (
            <>
              <ProfileField label="الاسم:" value={user?.full_name || 'غير مضاف'} icon="person-outline" />
              <ProfileField
                label="رقم الجوال:"
                value={user?.phone || 'غير مضاف'}
                icon="call-outline"
                valueDirection={user?.phone ? 'ltr' : 'rtl'}
              />
              <ProfileField
                label="البريد الإلكتروني:"
                value={user?.email || 'غير مضاف'}
                icon="mail-outline"
                valueDirection={user?.email ? 'ltr' : 'rtl'}
                stacked
              />
            </>
          )}
        </View>

        {editing ? (
          <View style={styles.actions}>
            <PrimaryButton
              title={saving ? 'جار الحفظ...' : 'حفظ التعديلات'}
              icon="checkmark-circle-outline"
              onPress={() => void save()}
            />
            <PrimaryButton title="إلغاء" icon="close-circle-outline" tone="outline" onPress={cancelEditing} />
          </View>
        ) : (
          <PrimaryButton title="تعديل البيانات" icon="create-outline" onPress={() => setEditing(true)} />
        )}

        <Modal
          visible={saveSuccessVisible}
          transparent
          animationType="fade"
          statusBarTranslucent
          onRequestClose={() => setSaveSuccessVisible(false)}>
          <View style={styles.successOverlay}>
            <View
              style={styles.successCard}
              accessibilityRole="alert"
              accessibilityLabel="تم الحفظ. تم تحديث بياناتك بنجاح وستتم مزامنتها تلقائيًا عند توفر الإنترنت.">
              <View style={styles.successIconWrap}>
                <Ionicons name="checkmark" size={48} color={Colors.white} />
              </View>
              <Text style={styles.successTitle}>تم الحفظ</Text>
              <Text style={styles.successMessage}>
                تم تحديث بياناتك بنجاح، وستتم مزامنتها تلقائيًا عند توفر الإنترنت.
              </Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="إغلاق رسالة نجاح الحفظ"
                onPress={() => setSaveSuccessVisible(false)}
                style={({ pressed }) => [styles.successButton, pressed && styles.successButtonPressed]}>
                <Text style={styles.successButtonText}>حسنًا</Text>
              </Pressable>
            </View>
          </View>
        </Modal>
      </View>
    </Screen>
  );
}

type FieldProps = {
  label: string;
  value: string;
  icon: keyof typeof Ionicons.glyphMap;
  valueDirection?: 'rtl' | 'ltr';
  stacked?: boolean;
};

function ProfileField({ label, value, icon, valueDirection = 'rtl', stacked = false }: FieldProps) {
  return (
    <View style={[styles.field, stacked && styles.fieldStacked]}>
      <View style={styles.fieldLabelRow}>
        <Ionicons name={icon} size={22} color={Colors.primary} />
        <Text style={styles.fieldLabel}>{label}</Text>
      </View>
      <View style={[styles.fieldValueRow, stacked && styles.fieldValueRowStacked]}>
        <Text style={[styles.fieldValue, valueDirection === 'ltr' && styles.ltrValue]}>{value}</Text>
      </View>
    </View>
  );
}

function EditableField({
  label,
  value,
  icon,
  onChangeText,
  keyboardType = 'default',
  placeholder,
  stacked = false,
}: FieldProps & {
  onChangeText: (value: string) => void;
  keyboardType?: 'default' | 'phone-pad' | 'email-address';
  placeholder?: string;
}) {
  return (
    <View style={[styles.field, stacked && styles.fieldStacked]}>
      <View style={styles.fieldLabelRow}>
        <Ionicons name={icon} size={22} color={Colors.primary} />
        <Text style={styles.fieldLabel}>{label}</Text>
      </View>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        keyboardType={keyboardType}
        autoCapitalize={keyboardType === 'email-address' ? 'none' : 'sentences'}
        placeholder={placeholder}
        placeholderTextColor={Colors.muted}
        style={[styles.input, stacked && styles.inputStacked, keyboardType !== 'default' && styles.ltrInput]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    direction: 'rtl',
    alignItems: 'stretch',
    gap: 12,
  },
  hero: {
    direction: 'rtl',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 14,
    paddingVertical: 2,
  },
  logo: {
    width: 78,
    height: 78,
    borderRadius: 20,
  },
  heroInfo: {
    direction: 'rtl',
    alignItems: 'flex-start',
    gap: 7,
  },
  heroName: {
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  statusBadge: {
    flexDirection: 'row',
    direction: 'rtl',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 18,
    backgroundColor: Colors.tint,
  },
  statusText: {
    fontFamily: 'Tajawal_700Bold',
    fontSize: 14,
    color: Colors.primaryDark,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  card: {
    direction: 'rtl',
    alignItems: 'stretch',
    gap: 0,
    padding: 16,
    borderRadius: 20,
    backgroundColor: Colors.white,
    ...shadow,
  },
  cardHeader: {
    width: '100%',
    flexDirection: 'row',
    direction: 'rtl',
    justifyContent: 'flex-start',
    alignItems: 'center',
    paddingBottom: 8,
  },
  field: {
    width: '100%',
    direction: 'rtl',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
    minHeight: 60,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: Colors.line,
  },
  fieldLabelRow: {
    direction: 'rtl',
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
    gap: 8,
    flexShrink: 0,
  },
  fieldLabel: {
    fontFamily: 'Tajawal_700Bold',
    fontSize: 14,
    color: Colors.muted,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  fieldValueRow: {
    flex: 1,
    direction: 'rtl',
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
  },
  fieldStacked: {
    flexDirection: 'column',
    alignItems: 'stretch',
    gap: 6,
    minHeight: 88,
  },
  fieldValueRowStacked: {
    width: '100%',
    minHeight: 28,
    paddingHorizontal: 30,
  },
  fieldValue: {
    flexShrink: 1,
    fontFamily: 'Tajawal_700Bold',
    fontSize: 17,
    lineHeight: 24,
    color: Colors.text,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  ltrValue: {
    flex: 1,
    textAlign: 'right',
    writingDirection: 'ltr',
  },
  input: {
    flex: 1,
    minWidth: 0,
    minHeight: 46,
    direction: 'rtl',
    textAlign: 'right',
    writingDirection: 'rtl',
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: Colors.line,
    borderRadius: 14,
    backgroundColor: Colors.gray,
    fontFamily: 'Tajawal_400Regular',
    fontSize: 16,
    color: Colors.text,
  },
  ltrInput: {
    textAlign: 'right',
    writingDirection: 'ltr',
  },
  inputStacked: {
    width: '100%',
    flex: 0,
  },
  actions: {
    direction: 'rtl',
    alignItems: 'stretch',
    gap: 10,
  },
  rtlText: {
    flexShrink: 1,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  successOverlay: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    backgroundColor: 'rgba(14, 64, 93, 0.52)',
  },
  successCard: {
    width: '100%',
    maxWidth: 380,
    direction: 'rtl',
    alignItems: 'center',
    gap: 14,
    paddingHorizontal: 24,
    paddingTop: 30,
    paddingBottom: 22,
    borderRadius: 28,
    borderWidth: 1,
    borderColor: Colors.primaryLight,
    backgroundColor: Colors.white,
    ...shadow,
  },
  successIconWrap: {
    width: 82,
    height: 82,
    borderRadius: 41,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 8,
    borderColor: Colors.tint,
    backgroundColor: Colors.primary,
  },
  successTitle: {
    fontFamily: 'Tajawal_800ExtraBold',
    fontSize: 26,
    color: Colors.primaryDark,
    textAlign: 'center',
    writingDirection: 'rtl',
  },
  successMessage: {
    fontFamily: 'Tajawal_400Regular',
    fontSize: 17,
    lineHeight: 29,
    color: Colors.muted,
    textAlign: 'center',
    writingDirection: 'rtl',
  },
  successButton: {
    width: '100%',
    minHeight: 56,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 4,
    borderRadius: 16,
    backgroundColor: Colors.primary,
  },
  successButtonPressed: {
    opacity: 0.82,
  },
  successButtonText: {
    fontFamily: 'Tajawal_800ExtraBold',
    fontSize: 19,
    color: Colors.white,
    textAlign: 'center',
    writingDirection: 'rtl',
  },
});
