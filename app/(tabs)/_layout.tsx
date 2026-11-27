import { Ionicons } from '@expo/vector-icons';
import { Tabs, router } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { tabIcon } from '@/app/_layout';
import { Colors, globalText, shadow } from '@/lib/theme';

export default function MainTabs() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: Colors.primary,
        tabBarInactiveTintColor: Colors.muted,
        tabBarLabelStyle: [globalText.small, { fontWeight: '700' }],
        tabBarStyle: {
          height: 78,
          paddingBottom: 10,
          paddingTop: 8,
          borderTopColor: Colors.line,
          direction: 'rtl',
        },
      }}>
      <Tabs.Screen name="dashboard" options={{ title: 'أدويتي', tabBarIcon: ({ color }) => tabIcon('home', color) }} />
      <Tabs.Screen name="family" options={{ title: 'عائلتي', tabBarIcon: ({ color }) => tabIcon('people', color) }} />
      <Tabs.Screen
        name="add"
        options={{
          title: 'إضافة',
          tabBarLabel: '',
          tabBarButton: () => <AddMedicationTabButton />,
        }}
      />
      <Tabs.Screen name="history" options={{ title: 'السجل', tabBarIcon: ({ color }) => tabIcon('list', color) }} />
      <Tabs.Screen
        name="notifications"
        options={{ title: 'الإشعارات', tabBarIcon: ({ color }) => tabIcon('notifications', color) }}
      />
      <Tabs.Screen name="medications" options={{ href: null }} />
      <Tabs.Screen name="profile" options={{ href: null }} />
    </Tabs>
  );
}

function AddMedicationTabButton() {
  return (
    <Pressable onPress={() => router.push('/add-medication')} style={({ pressed }) => [styles.addTabWrap, pressed && { opacity: 0.86 }]}>
      <View style={styles.addTabButton}>
        <Ionicons name="add" size={34} color={Colors.white} />
      </View>
      <Text style={styles.addTabLabel}>إضافة</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  addTabWrap: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'flex-start',
    top: -24,
  },
  addTabButton: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: Colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 5,
    borderColor: Colors.white,
    ...shadow,
  },
  addTabLabel: {
    marginTop: 2,
    fontFamily: 'Tajawal_700Bold',
    fontSize: 13,
    color: Colors.primary,
    textAlign: 'center',
    writingDirection: 'rtl',
  },
});
