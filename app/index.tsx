import * as NativeSplashScreen from 'expo-splash-screen';
import { router } from 'expo-router';
import { useCallback, useEffect, useRef } from 'react';
import { Image, StyleSheet } from 'react-native';

import { initializeDatabase } from '@/lib/database';
import { patientRepository } from '@/lib/repositories/patientRepository';

const SPLASH_VISIBLE_DURATION_MS = 8000;

export default function Splash() {
  const frameRef = useRef<number | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleSplashLayout = useCallback(() => {
    if (frameRef.current !== null || timerRef.current !== null) return;

    frameRef.current = requestAnimationFrame(() => {
      frameRef.current = null;
      NativeSplashScreen.hide();
      timerRef.current = setTimeout(() => {
        void initializeDatabase()
          .then(() => patientRepository.get())
          .then((patient) => router.replace(patient ? '/(tabs)/dashboard' : '/onboarding'));
      }, SPLASH_VISIBLE_DURATION_MS);
    });
  }, []);

  useEffect(() => {
    return () => {
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
      if (timerRef.current !== null) clearTimeout(timerRef.current);
    };
  }, []);

  return (
    <Image
      accessibilityLabel="عافيتي"
      onLayout={handleSplashLayout}
      resizeMode="cover"
      source={require('@/assets/splash.png')}
      style={styles.splash}
    />
  );
}

const styles = StyleSheet.create({
  splash: {
    width: '100%',
    height: '100%',
  },
});
