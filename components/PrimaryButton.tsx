import { Ionicons } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, ViewStyle } from 'react-native';

import { Colors, globalText } from '@/lib/theme';

export function PrimaryButton({
  title,
  icon = 'checkmark-circle',
  onPress,
  tone = 'primary',
  style,
}: {
  title: string;
  icon?: keyof typeof Ionicons.glyphMap;
  onPress: () => void;
  tone?: 'primary' | 'gold' | 'outline' | 'danger';
  style?: ViewStyle;
}) {
  const color = tone === 'gold' ? Colors.gold : tone === 'danger' ? Colors.danger : Colors.primary;
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        tone === 'outline' ? styles.outline : { backgroundColor: color },
        pressed && { opacity: 0.82 },
        style,
      ]}>
      <Ionicons name={icon} size={30} color={tone === 'outline' ? color : Colors.white} />
      <Text style={[globalText.button, tone === 'outline' && { color }]}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 64,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    direction: 'rtl',
    gap: 10,
    paddingHorizontal: 18,
  },
  outline: {
    backgroundColor: Colors.white,
    borderWidth: 2,
    borderColor: Colors.primary,
  },
});
