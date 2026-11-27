import { StyleSheet } from 'react-native';

export const Colors = {
  primary: '#247FAB',
  primaryDark: '#155B82',
  primaryLight: '#8CCCE8',
  background: '#EEF8FC',
  tint: '#DDF1F8',
  gold: '#F5A623',
  text: '#13233A',
  muted: '#65758A',
  line: '#CFE3EC',
  gray: '#F3F7FA',
  white: '#FFFFFF',
  danger: '#E53E3E',
};

export const globalText = StyleSheet.create({
  title: { fontFamily: 'Tajawal_800ExtraBold', fontSize: 32, color: Colors.text, textAlign: 'right', writingDirection: 'rtl' },
  section: { fontFamily: 'Tajawal_800ExtraBold', fontSize: 24, color: Colors.text, textAlign: 'right', writingDirection: 'rtl' },
  cardTitle: { fontFamily: 'Tajawal_800ExtraBold', fontSize: 20, color: Colors.text, textAlign: 'right', writingDirection: 'rtl' },
  body: { fontFamily: 'Tajawal_400Regular', fontSize: 18, color: Colors.text, textAlign: 'right', writingDirection: 'rtl', lineHeight: 30 },
  small: { fontFamily: 'Tajawal_400Regular', fontSize: 16, color: Colors.muted, textAlign: 'right', writingDirection: 'rtl' },
  button: { fontFamily: 'Tajawal_800ExtraBold', fontSize: 20, color: Colors.white, textAlign: 'center', writingDirection: 'rtl' },
});

export const shadow = {
  shadowColor: '#0E405D',
  shadowOpacity: 0.08,
  shadowRadius: 14,
  shadowOffset: { width: 0, height: 8 },
  elevation: 3,
};
