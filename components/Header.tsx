import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Colors, globalText } from '@/lib/theme';

export function Header({ title, back = false }: { title: string; back?: boolean }) {
  return (
    <View style={styles.header}>
      <Text style={globalText.section}>{title}</Text>
      {back ? (
        <Pressable onPress={() => router.back()} style={styles.back}>
          <Text style={globalText.small}>رجوع</Text>
          <Ionicons name="arrow-forward" size={26} color={Colors.primary} />
        </Pressable>
      ) : (
        <View style={styles.spacer} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', direction: 'rtl' },
  back: { minHeight: 56, flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: 6 },
  spacer: { width: 72, minHeight: 56 },
});
