import { useRef, useState } from 'react';
import { FlatList, Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import { Colors, globalText, shadow } from '@/lib/theme';

const ITEM_HEIGHT = 56;
const VISIBLE_ITEMS = 5;
const WHEEL_HEIGHT = ITEM_HEIGHT * VISIBLE_ITEMS;

const HOURS = Array.from({ length: 12 }, (_, index) => index + 1);
const MINUTES = Array.from({ length: 60 }, (_, index) => index);

type Period = 'ص' | 'م';

const parseValue = (value: string) => {
  const [h, m] = value.split(':').map(Number);
  const hour24 = Number.isInteger(h) ? h : 8;
  const minute = Number.isInteger(m) && m >= 0 && m < 60 ? m : 0;
  return {
    hour12: hour24 % 12 === 0 ? 12 : hour24 % 12,
    minute,
    period: (hour24 >= 12 ? 'م' : 'ص') as Period,
  };
};

const toValue = (hour12: number, minute: number, period: Period) => {
  const hour24 = period === 'م' ? (hour12 % 12) + 12 : hour12 % 12;
  return `${String(hour24).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
};

type TimeWheelPickerProps = {
  visible: boolean;
  value: string;
  title: string;
  onCancel: () => void;
  onConfirm: (value: string) => void;
};

export function TimeWheelPicker({ visible, value, title, onCancel, onConfirm }: TimeWheelPickerProps) {
  const initial = parseValue(value);
  const [hour, setHour] = useState(initial.hour12);
  const [minute, setMinute] = useState(initial.minute);
  const [period, setPeriod] = useState<Period>(initial.period);

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <Pressable style={styles.overlay} onPress={onCancel}>
        <Pressable style={styles.sheet} onPress={() => null}>
          <Text style={globalText.cardTitle}>{title}</Text>

          <View style={styles.wheelsRow}>
            <Wheel items={HOURS} selected={hour} onChange={setHour} format={(item) => String(item)} />
            <Text style={styles.separator}>:</Text>
            <Wheel items={MINUTES} selected={minute} onChange={setMinute} format={(item) => String(item).padStart(2, '0')} />
            <View style={styles.periodColumn}>
              {(['ص', 'م'] as Period[]).map((item) => (
                <Pressable
                  key={item}
                  onPress={() => setPeriod(item)}
                  style={[styles.periodChip, period === item && styles.periodChipSelected]}>
                  <Text style={[styles.periodText, period === item && styles.periodTextSelected]}>{item}</Text>
                </Pressable>
              ))}
            </View>
          </View>

          <View style={styles.actions}>
            <Pressable style={styles.confirmButton} onPress={() => onConfirm(toValue(hour, minute, period))}>
              <Text style={globalText.button}>تأكيد</Text>
            </Pressable>
            <Pressable style={styles.cancelButton} onPress={onCancel}>
              <Text style={globalText.body}>إلغاء</Text>
            </Pressable>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

type WheelProps = {
  items: number[];
  selected: number;
  onChange: (item: number) => void;
  format: (item: number) => string;
};

function Wheel({ items, selected, onChange, format }: WheelProps) {
  const listRef = useRef<FlatList<number>>(null);
  const initialIndex = Math.max(0, items.indexOf(selected));
  const [activeIndex, setActiveIndex] = useState(initialIndex);

  const settle = (offsetY: number) => {
    const index = Math.min(items.length - 1, Math.max(0, Math.round(offsetY / ITEM_HEIGHT)));
    setActiveIndex(index);
    onChange(items[index]);
  };

  const select = (index: number) => {
    listRef.current?.scrollToIndex({ index, animated: true });
    setActiveIndex(index);
    onChange(items[index]);
  };

  return (
    <View style={styles.wheel}>
      <View pointerEvents="none" style={styles.highlightBand} />
      <FlatList
        ref={listRef}
        data={items}
        keyExtractor={(item) => String(item)}
        renderItem={({ item, index }) => (
          <Pressable onPress={() => select(index)} style={styles.item}>
            <Text style={[styles.itemText, index === activeIndex && styles.itemTextActive]}>{format(item)}</Text>
          </Pressable>
        )}
        getItemLayout={(_, index) => ({ length: ITEM_HEIGHT, offset: ITEM_HEIGHT * index, index })}
        initialScrollIndex={initialIndex}
        showsVerticalScrollIndicator={false}
        snapToInterval={ITEM_HEIGHT}
        decelerationRate="fast"
        contentContainerStyle={styles.wheelContent}
        onMomentumScrollEnd={(event) => settle(event.nativeEvent.contentOffset.y)}
        onScrollToIndexFailed={({ index }) => {
          setTimeout(() => listRef.current?.scrollToIndex({ index, animated: false }), 100);
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(14, 64, 93, 0.35)', justifyContent: 'center', padding: 24 },
  sheet: { backgroundColor: Colors.white, borderRadius: 20, padding: 20, gap: 20, ...shadow },
  wheelsRow: { flexDirection: 'row', direction: 'rtl', alignItems: 'center', justifyContent: 'center', gap: 12 },
  separator: { fontSize: 36, fontWeight: '900', color: Colors.text, marginBottom: 4 },
  wheel: { height: WHEEL_HEIGHT, width: 88, overflow: 'hidden' },
  wheelContent: { paddingVertical: ITEM_HEIGHT * 2 },
  highlightBand: {
    position: 'absolute',
    top: ITEM_HEIGHT * 2,
    height: ITEM_HEIGHT,
    left: 0,
    right: 0,
    borderRadius: 14,
    backgroundColor: Colors.tint,
    borderWidth: 1.5,
    borderColor: Colors.primary,
  },
  item: { height: ITEM_HEIGHT, alignItems: 'center', justifyContent: 'center' },
  itemText: { fontSize: 20, color: Colors.muted, fontWeight: '600' },
  itemTextActive: { fontSize: 28, fontWeight: '900', color: Colors.primaryDark },
  periodColumn: { gap: 10 },
  periodChip: {
    width: 64,
    height: 64,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Colors.line,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.white,
  },
  periodChipSelected: { backgroundColor: Colors.primary, borderColor: Colors.primary },
  periodText: { fontSize: 22, fontWeight: '800', color: Colors.text },
  periodTextSelected: { color: Colors.white },
  actions: { gap: 10 },
  confirmButton: {
    minHeight: 64,
    borderRadius: 16,
    backgroundColor: Colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelButton: { minHeight: 52, alignItems: 'center', justifyContent: 'center' },
});
