import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';

import { Header } from '@/components/Header';
import { Screen } from '@/components/Screen';
import { Colors, globalText, shadow } from '@/lib/theme';
import { useMedicationStore } from '@/store/medicationStore';

const HISTORY_DAY_COUNT = 30;

export default function History() {
  const { logs, load } = useMedicationStore();
  const [selected, setSelected] = useState(0);
  const [, setFirstVisibleDay] = useState(0);
  const daysListRef = useRef<FlatList<Date>>(null);
  const swipeStartX = useRef<number | null>(null);
  const { width } = useWindowDimensions();
  useFocusEffect(useCallback(() => void load(), [load]));
  const days = useMemo(
    () =>
      Array.from({ length: HISTORY_DAY_COUNT }, (_, index) => {
        const date = new Date();
        date.setHours(12, 0, 0, 0);
        date.setDate(date.getDate() - index);
        return date;
      }),
    [],
  );
  const selectedDay = days[selected] ?? days[0];
  const dayGap = 8;
  const dayWidth = Math.floor((width - 40 - dayGap * 4) / 5);
  const moveDays = useCallback(
    (distance: number) => {
      if (Math.abs(distance) < 40) return;

      const steps = Math.max(1, Math.round(Math.abs(distance) / (dayWidth + dayGap)));
      setFirstVisibleDay((current) => {
        const requested = distance > 0 ? current + steps : current - steps;
        const next = Math.max(0, Math.min(HISTORY_DAY_COUNT - 5, requested));
        daysListRef.current?.scrollToOffset({
          offset: next * (dayWidth + dayGap),
          animated: true,
        });
        return next;
      });
    },
    [dayWidth],
  );
  const monthLabel = new Intl.DateTimeFormat('ar-SA-u-ca-gregory', {
    month: 'long',
    year: 'numeric',
  }).format(selectedDay);
  const taken = logs.filter((log) => log.status === 'taken').length;
  const percent = logs.length ? Math.round((taken / logs.length) * 100) : 0;

  return (
    <Screen>
      <Header title="السجل" />
      <View style={styles.calendar}>
        <View style={styles.monthRow}>
          <View style={styles.monthTitleRow}>
            <Ionicons name="calendar-outline" size={24} color={Colors.primary} />
            <Text style={styles.monthTitle}>{monthLabel}</Text>
          </View>
          <Text style={styles.swipeHint}>اسحب للتنقل بين الأيام</Text>
        </View>

        <View
          style={styles.daysViewport}
          onTouchStart={({ nativeEvent }) => {
            swipeStartX.current = nativeEvent.pageX;
          }}
          onTouchEnd={({ nativeEvent }) => {
            if (swipeStartX.current === null) return;
            moveDays(nativeEvent.pageX - swipeStartX.current);
            swipeStartX.current = null;
          }}>
          <FlatList
            ref={daysListRef}
            horizontal
            scrollEnabled={false}
            data={days}
            keyExtractor={(day) => day.toISOString()}
            showsHorizontalScrollIndicator={false}
            initialScrollIndex={0}
            style={styles.daysList}
            ItemSeparatorComponent={() => <View style={{ width: dayGap }} />}
            getItemLayout={(_, index) => ({ length: dayWidth + dayGap, offset: (dayWidth + dayGap) * index, index })}
            renderItem={({ item: day, index }) => {
            const isSelected = selected === index;
            const dayName = new Intl.DateTimeFormat('ar-SA-u-ca-gregory', { weekday: 'short' })
              .format(day)
              .replace(/^ال/, '');
            const dayNumber = new Intl.DateTimeFormat('ar-SA-u-ca-gregory', { day: 'numeric' }).format(day);
            return (
              <Pressable
                onPress={() => {
                  setSelected(index);
                  void load(day);
                }}
                style={({ pressed }) => [
                  styles.day,
                  { width: dayWidth },
                  index === 0 && styles.today,
                  isSelected && styles.selected,
                  pressed && styles.dayPressed,
                ]}>
                <Text
                  numberOfLines={1}
                  adjustsFontSizeToFit
                  minimumFontScale={0.72}
                  style={[styles.dayName, isSelected && styles.selectedText]}>
                  {dayName}
                </Text>
                <Text style={[styles.dayNumber, isSelected && styles.selectedText]}>{dayNumber}</Text>
                <View style={[styles.dayDot, isSelected && styles.selectedDot]} />
              </Pressable>
            );
            }}
          />
        </View>
      </View>
      <View style={styles.card}>
        <View style={styles.cardHeader}>
          <Text style={[globalText.section, styles.cardTitle]}>التزامك هذا الشهر: {percent}%</Text>
        </View>
      </View>
      {logs.map((log) => (
        <View key={log.id} style={[styles.row, log.status === 'taken' ? styles.green : log.status === 'missed' ? styles.red : styles.gold]}>
          <View style={styles.rowHeader}>
            <Text style={[globalText.cardTitle, styles.rowText]}>{log.medications?.name || 'جرعة'}</Text>
          </View>
          <View style={styles.rowDetails}>
            <Text style={[globalText.body, styles.rowText]}>
              {log.status === 'taken' ? 'تم أخذها' : log.status === 'missed' ? 'فاتت' : 'بانتظارك'}
            </Text>
          </View>
        </View>
      ))}
    </Screen>
  );
}

const styles = StyleSheet.create({
  calendar: {
    direction: 'rtl',
    alignItems: 'stretch',
    gap: 12,
  },
  monthRow: {
    width: '100%',
    direction: 'rtl',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  monthTitleRow: {
    direction: 'rtl',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  monthTitle: {
    fontFamily: 'Tajawal_800ExtraBold',
    fontSize: 21,
    color: Colors.text,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  swipeHint: {
    fontFamily: 'Tajawal_400Regular',
    fontSize: 13,
    color: Colors.muted,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  daysViewport: {
    width: '100%',
  },
  daysList: {
    width: '100%',
    direction: 'ltr',
  },
  day: {
    height: 82,
    borderRadius: 15,
    borderWidth: 1,
    borderColor: Colors.line,
    backgroundColor: Colors.white,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
  },
  today: {
    backgroundColor: Colors.tint,
    borderColor: Colors.primaryLight,
  },
  selected: {
    borderColor: Colors.primary,
    backgroundColor: Colors.primary,
    ...shadow,
  },
  dayPressed: { opacity: 0.8 },
  dayName: {
    width: '100%',
    paddingHorizontal: 3,
    fontFamily: 'Tajawal_700Bold',
    fontSize: 13,
    color: Colors.muted,
    textAlign: 'center',
    writingDirection: 'rtl',
  },
  dayNumber: {
    fontFamily: 'Tajawal_800ExtraBold',
    fontSize: 23,
    color: Colors.text,
    textAlign: 'center',
  },
  selectedText: { color: Colors.white },
  dayDot: {
    width: 5,
    height: 5,
    borderRadius: 3,
    backgroundColor: Colors.primaryLight,
  },
  selectedDot: { backgroundColor: Colors.white },
  card: {
    direction: 'rtl',
    alignItems: 'stretch',
    backgroundColor: Colors.white,
    borderRadius: 16,
    padding: 20,
    ...shadow,
  },
  cardHeader: {
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
  },
  cardTitle: { textAlign: 'right', writingDirection: 'rtl' },
  row: {
    width: '100%',
    direction: 'rtl',
    alignItems: 'stretch',
    borderRadius: 16,
    padding: 18,
    gap: 4,
    borderWidth: 1,
    borderColor: Colors.line,
    ...shadow,
  },
  rowHeader: {
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
  },
  rowDetails: {
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
  },
  rowText: { textAlign: 'right', writingDirection: 'rtl' },
  green: { backgroundColor: Colors.tint, borderColor: Colors.primaryLight },
  gold: { backgroundColor: '#E8F4FA', borderColor: Colors.line },
  red: { backgroundColor: '#FFECEC', borderColor: '#F5B8B8' },
});
