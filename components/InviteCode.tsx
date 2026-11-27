import { StyleSheet, Text, View } from 'react-native';

import { Colors } from '@/lib/theme';

export function InviteCode({ code }: { code: string }) {
  return (
    <View style={styles.row}>
      {code.split('').map((digit, index) => (
        <View key={`${digit}-${index}`} style={styles.box}>
          <Text style={styles.digit}>{digit}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'center', gap: 8, direction: 'rtl' },
  box: {
    width: 46,
    height: 60,
    borderRadius: 12,
    backgroundColor: Colors.white,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: Colors.line,
  },
  digit: { fontSize: 28, fontWeight: '900', color: Colors.primary },
});
