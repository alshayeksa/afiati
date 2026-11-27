import 'react-native-gesture-handler';
import { Ionicons } from '@expo/vector-icons';
import { useFonts, Tajawal_400Regular, Tajawal_700Bold, Tajawal_800ExtraBold } from '@expo-google-fonts/tajawal';
import * as NavigationBar from 'expo-navigation-bar';
import { Stack } from 'expo-router';
import { StatusBar, setStatusBarHidden } from 'expo-status-bar';
import { useEffect } from 'react';
import { ColorValue, I18nManager, Platform } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { initializeDatabase } from '@/lib/database';
import { startSyncCoordinator } from '@/lib/sync/coordinator';
import { useUserStore } from '@/store/userStore';

// نتحكم بالاتجاه يدويًا عبر lib/layout.ts بدل الاعتماد على forceRTL.
I18nManager.allowRTL(false);
I18nManager.forceRTL(false);
I18nManager.swapLeftAndRightInRTL(false);

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    Tajawal_400Regular,
    Tajawal_700Bold,
    Tajawal_800ExtraBold,
  });
  const hydrateUser = useUserStore((state) => state.hydrate);

  useEffect(() => {
    setStatusBarHidden(true, 'fade');

    if (Platform.OS !== 'android') return;

    NavigationBar.NavigationBar.setHidden(true);
  }, []);

  useEffect(() => {
    let stop: (() => void) | undefined;
    void initializeDatabase()
      .then(() => hydrateUser())
      .then(() => startSyncCoordinator())
      .then((cleanup) => {
        stop = cleanup;
      });
    return () => stop?.();
  }, [hydrateUser]);

  if (!fontsLoaded) return null;

  return (
    <SafeAreaProvider>
      <StatusBar hidden />
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="index" />
        <Stack.Screen name="onboarding" />
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="add-medication" />
        <Stack.Screen name="invite" />
        <Stack.Screen name="join" />
        <Stack.Screen name="patient/[id]" />
        <Stack.Screen name="privacy-approval" />
      </Stack>
    </SafeAreaProvider>
  );
}

export function tabIcon(name: keyof typeof Ionicons.glyphMap, color: ColorValue) {
  return <Ionicons name={name} size={28} color={color} />;
}
