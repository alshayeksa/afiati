import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { type ComponentProps, type ReactNode, useMemo, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { Header } from '@/components/Header';
import { PrimaryButton } from '@/components/PrimaryButton';
import { Screen } from '@/components/Screen';
import { TimeWheelPicker } from '@/components/TimeWheelPicker';
import { formatTime } from '@/lib/helpers';
import { Colors, globalText, shadow } from '@/lib/theme';
import { useMedicationStore } from '@/store/medicationStore';
import { DoseUnit, RepeatType } from '@/store/types';

const quickSuggestions = ['دواء السكري', 'دواء الضغط', 'مسكن', 'دواء القلب', 'دواء المعدة'];
const suggestionCategories = [
  { label: 'فيتامينات', options: ['فيتامين د', 'فيتامين ب', 'فيتامين سي', 'كالسيوم', 'حديد', 'مغنيسيوم'] },
  { label: 'الحساسية', options: ['حبوب الحساسية', 'بخاخ الأنف', 'قطرة الحساسية', 'شراب الحساسية'] },
  { label: 'الكوليسترول', options: ['دواء الكوليسترول', 'منظم الدهون'] },
  { label: 'العين', options: ['قطرة العين', 'قطرة مرطبة', 'قطرة ضغط العين', 'مرهم العين'] },
  { label: 'الربو', options: ['بخاخ الربو', 'بخاخ توسيع الشعب', 'بخاخ وقائي'] },
  { label: 'المفاصل', options: ['دواء المفاصل', 'مسكن المفاصل', 'كريم موضعي', 'مضاد التهاب'] },
];
const unitOptions: DoseUnit[] = ['حبة', 'مل', 'قطرة', 'بخة'];
const defaultTimes = ['08:00', '11:00', '14:00', '17:00', '20:00'];
const doseLabels = ['الجرعة الأولى', 'الجرعة الثانية', 'الجرعة الثالثة', 'الجرعة الرابعة', 'الجرعة الخامسة'];
const repeatTypeOptions: { value: RepeatType; label: string; icon: ComponentProps<typeof Ionicons>['name'] }[] = [
  { value: 'daily', label: 'يوميًا', icon: 'today-outline' },
  { value: 'weekly', label: 'أسبوعيًا', icon: 'calendar-outline' },
  { value: 'monthly', label: 'شهريًا', icon: 'calendar-number-outline' },
];
const frequencyTitles: Record<RepeatType, string> = {
  daily: 'كم مرة في اليوم؟',
  weekly: 'كم مرة في الأسبوع؟',
  monthly: 'كم مرة في الشهر؟',
};
const maxFrequency: Record<RepeatType, number> = {
  daily: 5,
  weekly: 4,
  monthly: 4,
};
const monthDays = Array.from({ length: 31 }, (_, index) => index + 1);
const weekdays = [
  { value: 7, label: 'السبت' },
  { value: 1, label: 'الأحد' },
  { value: 2, label: 'الاثنين' },
  { value: 3, label: 'الثلاثاء' },
  { value: 4, label: 'الأربعاء' },
  { value: 5, label: 'الخميس' },
  { value: 6, label: 'الجمعة' },
];

export default function AddMedication() {
  const [step, setStep] = useState(1);
  const [name, setName] = useState('');
  const [dose, setDose] = useState(1);
  const [unit, setUnit] = useState<DoseUnit>('حبة');
  const [repeatType, setRepeatType] = useState<RepeatType>('daily');
  const [repeatDays, setRepeatDays] = useState<number[]>([]);
  const [frequency, setFrequency] = useState(1);
  const [times, setTimes] = useState(['08:00']);
  const [stock, setStock] = useState<number | null>(30);
  const [editingTimeIndex, setEditingTimeIndex] = useState<number | null>(null);
  const [showMoreSuggestions, setShowMoreSuggestions] = useState(false);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const { addMedication, findDuplicateMedication } = useMedicationStore();
  const progress = useMemo(() => `${step} من 6`, [step]);

  const next = () => {
    if (step === 1 && !name.trim()) {
      Alert.alert('اكتب اسم الدواء أولًا');
      return;
    }
    if (step === 4 && repeatType !== 'daily' && repeatDays.length !== frequency) {
      Alert.alert(`اختر ${frequency} ${frequency === 1 ? 'يوم' : 'أيام'}`);
      return;
    }
    if (step < 6) setStep(step + 1);
  };

  const previous = () => {
    setStep(step - 1);
  };

  const selectRepeatType = (value: RepeatType) => {
    const todayWeekday = new Date().getDay() + 1;
    setRepeatType(value);
    setFrequency(1);
    setTimes(['08:00']);
    setRepeatDays(value === 'weekly' ? [todayWeekday] : value === 'monthly' ? [new Date().getDate()] : []);
  };

  const selectFrequency = (value: number) => {
    setFrequency(value);
    if (repeatType === 'daily') {
      setTimes(defaultTimes.slice(0, value));
      return;
    }
    if (repeatType === 'weekly') {
      const todayWeekday = new Date().getDay() + 1;
      const orderedDays = [todayWeekday, ...weekdays.map((day) => day.value).filter((day) => day !== todayWeekday)];
      setRepeatDays(orderedDays.slice(0, value));
      return;
    }
    const todayMonthDay = new Date().getDate();
    const orderedMonthDays = [todayMonthDay, ...monthDays.filter((day) => day !== todayMonthDay)];
    setRepeatDays(orderedMonthDays.slice(0, value));
  };

  const changeFrequency = (amount: number) => {
    selectFrequency(Math.max(1, Math.min(maxFrequency[repeatType], frequency + amount)));
  };

  const toggleRepeatDay = (day: number) => {
    if (repeatDays.includes(day)) {
      setRepeatDays(repeatDays.filter((item) => item !== day));
      return;
    }
    if (repeatDays.length >= frequency) {
      setRepeatDays([...repeatDays.slice(1), day]);
      return;
    }
    setRepeatDays([...repeatDays, day]);
  };

  const draft = () => ({
    name: name.trim(),
    dose_amount: dose,
    dose_unit: unit,
    repeat_type: repeatType,
    repeat_days: repeatDays,
    frequency,
    times: repeatType === 'daily' ? times.slice(0, frequency) : times.slice(0, 1),
    stock_count: stock,
    notes: null,
    created_at: undefined,
    updated_at: undefined,
  });

  const persist = async () => {
    try {
      await addMedication(draft());
      router.replace('/(tabs)/dashboard');
    } catch (error) {
      Alert.alert('لم يتم الحفظ', error instanceof Error ? error.message : 'حاول مرة أخرى.');
    }
  };

  const save = async () => {
    const duplicate = await findDuplicateMedication(draft());
    if (!duplicate) {
      await persist();
      return;
    }
    Alert.alert(
      'دواء مطابق موجود',
      `يوجد ${duplicate.name} بنفس الجرعة والمواعيد. هل تريد إضافته مرة أخرى؟`,
      [
        { text: 'إلغاء', style: 'cancel' },
        { text: 'إضافة على أي حال', onPress: () => void persist() },
      ],
    );
  };

  return (
    <Screen>
      <Header title="إضافة دواء" back />
      <View style={styles.progress}>
        <View style={[styles.bar, { width: `${(step / 6) * 100}%` }]} />
      </View>
      <Text style={globalText.small}>الخطوة {progress}</Text>

      {step === 1 ? (
        <View style={[styles.card, styles.nameCard]}>
          <Text style={globalText.section}>ما اسم الدواء؟</Text>
          <TextInput
            value={name}
            onChangeText={setName}
            placeholder="اكتب اسم الدواء من العلبة"
            style={[styles.input, styles.nameInput]}
            textAlign="right"
          />

          <Text style={styles.suggestionLabel}>اقتراحات سريعة</Text>
          <View style={styles.chips}>
            {quickSuggestions.map((item) => (
              <Chip key={item} label={item} selected={name === item} onPress={() => setName(item)} />
            ))}
          </View>

          <Pressable
            style={styles.moreButton}
            onPress={() => {
              setShowMoreSuggestions(!showMoreSuggestions);
              setActiveCategory(null);
            }}>
            <Text style={styles.moreButtonText}>{showMoreSuggestions ? 'إخفاء التصنيفات' : 'عرض المزيد'}</Text>
            <Ionicons name={showMoreSuggestions ? 'chevron-up' : 'chevron-down'} size={22} color={Colors.primaryDark} />
          </Pressable>

          {showMoreSuggestions && activeCategory === null ? (
            <>
              <Text style={styles.suggestionLabel}>تصنيفات إضافية</Text>
              <View style={styles.chips}>
                {suggestionCategories.map((category) => (
                  <Chip key={category.label} label={category.label} selected={false} onPress={() => setActiveCategory(category.label)} />
                ))}
              </View>
            </>
          ) : null}

          {showMoreSuggestions && activeCategory !== null ? (
            <>
              <Text style={styles.suggestionLabel}>اختر النوع — {activeCategory}</Text>
              <View style={styles.chips}>
                {suggestionCategories
                  .find((category) => category.label === activeCategory)
                  ?.options.map((item) => (
                    <Chip key={item} label={item} selected={name === item} onPress={() => setName(item)} />
                  ))}
              </View>
              <Pressable style={styles.backButton} onPress={() => setActiveCategory(null)}>
                <Ionicons name="arrow-forward" size={20} color={Colors.primaryDark} />
                <Text style={styles.moreButtonText}>رجوع إلى التصنيفات</Text>
              </Pressable>
            </>
          ) : null}
        </View>
      ) : null}

      {step === 2 ? (
        <Section title="كم تأخذ في كل مرة؟">
          <View style={styles.counter}>
            <Counter label="+" onPress={() => setDose(dose + 1)} />
            <Text style={styles.number}>{dose}</Text>
            <Counter label="-" onPress={() => setDose(Math.max(1, dose - 1))} />
          </View>
          <View style={styles.chips}>{unitOptions.map((item) => <Chip key={item} label={item} selected={unit === item} onPress={() => setUnit(item)} />)}</View>
        </Section>
      ) : null}

      {step === 3 ? (
        <Section title="كم تكرر الدواء؟">
          <View style={styles.repeatTypeOptions}>
            {repeatTypeOptions.map((option) => (
              <Pressable
                key={option.value}
                onPress={() => selectRepeatType(option.value)}
                style={[styles.repeatOption, repeatType === option.value && styles.selected]}>
                <View style={styles.repeatOptionContent}>
                  <Ionicons
                    name={option.icon}
                    size={30}
                    color={repeatType === option.value ? Colors.primaryDark : Colors.muted}
                  />
                  <Text style={styles.repeatOptionText}>{option.label}</Text>
                </View>
              </Pressable>
            ))}
          </View>

          <View style={styles.frequencyDetails}>
            <View style={styles.rtlOptionRow}>
              <Text style={styles.detailTitle}>{frequencyTitles[repeatType]}</Text>
            </View>
            <View style={styles.counter}>
              <Counter label="+" onPress={() => changeFrequency(1)} />
              <Text style={styles.number}>{frequency}</Text>
              <Counter label="-" onPress={() => changeFrequency(-1)} />
            </View>
          </View>
        </Section>
      ) : null}

      {step === 4 ? (
        repeatType === 'daily' ? (
          <Section title="متى تأخذ الدواء؟">
            {times.slice(0, frequency).map((time, index) => (
              <Pressable key={index} style={styles.timeRow} onPress={() => setEditingTimeIndex(index)}>
                <View style={styles.timeRowText}>
                  <Text style={globalText.cardTitle}>{doseLabels[index]}</Text>
                  <Text style={globalText.small}>اضغط لتغيير الوقت</Text>
                </View>
                <View style={styles.timeBadge}>
                  <Ionicons name="time-outline" size={24} color={Colors.primaryDark} />
                  <Text style={styles.timeValue}>{formatTime(time)}</Text>
                </View>
              </Pressable>
            ))}
          </Section>
        ) : repeatType === 'weekly' ? (
          <Section title="اختر أيام الدواء">
            <Text style={globalText.body}>اختر {frequency} {frequency === 1 ? 'يوم فقط' : 'أيام فقط'}.</Text>
            <View style={styles.weekdayOptions}>
              {weekdays.map((day) => (
                <Pressable
                  key={day.value}
                  onPress={() => toggleRepeatDay(day.value)}
                  style={[styles.weekdayOption, repeatDays.includes(day.value) && styles.selected]}>
                  <View style={styles.rtlOptionRow}>
                    <Text style={styles.detailOptionText}>{day.label}</Text>
                  </View>
                </Pressable>
              ))}
            </View>
            <ScheduleTimeRow time={times[0]} onPress={() => setEditingTimeIndex(0)} />
          </Section>
        ) : (
          <Section title="اختر أيام الشهر">
            <Text style={globalText.body}>اختر {frequency} {frequency === 1 ? 'يوم فقط' : 'أيام فقط'}.</Text>
            <View style={styles.monthDayOptions}>
              {monthDays.map((day) => (
                <Pressable
                  key={day}
                  accessibilityLabel={`يوم ${day}`}
                  onPress={() => toggleRepeatDay(day)}
                  style={[styles.monthDayOption, repeatDays.includes(day) && styles.selected]}>
                  <Text style={styles.monthDayOptionText}>{day}</Text>
                </Pressable>
              ))}
            </View>
            <ScheduleTimeRow time={times[0]} onPress={() => setEditingTimeIndex(0)} />
          </Section>
        )
      ) : null}

      {step === 5 ? (
        <View style={styles.card}>
          <Text style={globalText.section}>كم حبة لديك الآن؟</Text>
          <Text style={globalText.body}>سنذكرك عند اقتراب النفاد.</Text>
          <View style={styles.counter}>
            <Counter label="+" onPress={() => setStock((stock || 0) + 1)} />
            <Text style={styles.number}>{stock ?? 0}</Text>
            <Counter label="-" onPress={() => setStock(Math.max(0, (stock || 0) - 1))} />
          </View>
          <PrimaryButton title="تخط" icon="play-skip-forward" tone="outline" onPress={() => setStock(null)} />
        </View>
      ) : null}

      {step === 6 ? (
        <View style={styles.card}>
          <Text style={globalText.section}>تأكيد الدواء</Text>
          <Text style={globalText.body}>{name}</Text>
          <Text style={globalText.body}>
            {dose} {unit} • {repeatType === 'daily' ? 'يوميًا' : repeatType === 'weekly' ? 'أسبوعيًا' : 'شهريًا'} • {frequency} {frequency === 1 ? 'مرة' : 'مرات'}
          </Text>
          <Text style={globalText.body}>
            {repeatType === 'daily'
              ? times.slice(0, frequency).map(formatTime).join('، ')
              : repeatType === 'weekly'
                ? repeatDays.map((day) => weekdays.find((item) => item.value === day)?.label).join('، ')
                : repeatDays.map((day) => `اليوم ${day}`).join('، ')}
          </Text>
          {repeatType !== 'daily' ? <Text style={globalText.body}>الوقت: {formatTime(times[0])}</Text> : null}
          <Text style={globalText.body}>{stock === null ? 'بدون تتبع مخزون' : `المخزون: ${stock}`}</Text>
        </View>
      ) : null}

      <View style={styles.navigationRow}>
        <PrimaryButton
          title={step === 6 ? 'حفظ الدواء' : 'التالي'}
          icon={step === 6 ? 'save' : 'arrow-back'}
          onPress={step === 6 ? () => void save() : next}
          style={styles.navigationButton}
        />
        {step > 1 ? (
          <PrimaryButton
            title="السابق"
            icon="arrow-forward"
            tone="outline"
            onPress={previous}
            style={styles.navigationButton}
          />
        ) : null}
      </View>

      {editingTimeIndex !== null ? (
        <TimeWheelPicker
          visible
          value={times[editingTimeIndex]}
          title={repeatType === 'daily' ? `وقت ${doseLabels[editingTimeIndex]}` : 'وقت الدواء في الأيام المحددة'}
          onCancel={() => setEditingTimeIndex(null)}
          onConfirm={(value) => {
            setTimes(times.map((item, i) => (i === editingTimeIndex ? value : item)));
            setEditingTimeIndex(null);
          }}
        />
      ) : null}
    </Screen>
  );
}

function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.chip, selected && styles.selected]}>
      <Text style={globalText.body}>{label}</Text>
    </Pressable>
  );
}

function ScheduleTimeRow({ time, onPress }: { time: string; onPress: () => void }) {
  return (
    <Pressable style={styles.timeRow} onPress={onPress}>
      <View style={styles.timeRowText}>
        <Text style={globalText.cardTitle}>وقت الدواء</Text>
        <Text style={globalText.small}>اضغط لتغيير الوقت</Text>
      </View>
      <View style={styles.timeBadge}>
        <Ionicons name="time-outline" size={24} color={Colors.primaryDark} />
        <Text style={styles.timeValue}>{formatTime(time)}</Text>
      </View>
    </Pressable>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>{title}</Text>
      </View>
      {children}
    </View>
  );
}

function Counter({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={styles.counterButton}>
      <Text style={styles.counterText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  progress: { height: 12, borderRadius: 999, backgroundColor: Colors.white, overflow: 'hidden' },
  bar: { height: '100%', backgroundColor: Colors.primary },
  navigationRow: { flexDirection: 'row', gap: 12 },
  navigationButton: { flex: 1 },
  card: { backgroundColor: Colors.white, borderRadius: 16, padding: 20, gap: 16, ...shadow },
  section: {
    direction: 'rtl',
    alignItems: 'stretch',
    padding: 16,
    gap: 16,
    backgroundColor: Colors.white,
    borderRadius: 16,
    ...shadow,
  },
  sectionHeader: {
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
    marginBottom: 10,
  },
  sectionTitle: {
    fontFamily: 'Tajawal_800ExtraBold',
    fontSize: 24,
    color: Colors.text,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  nameCard: {
    direction: 'rtl',
    alignItems: 'flex-start',
    borderWidth: 1.5,
    borderColor: Colors.line,
    padding: 16,
    gap: 12,
  },
  input: { minHeight: 64, borderRadius: 16, borderWidth: 1, borderColor: Colors.line, padding: 16, fontSize: 20, color: Colors.text },
  nameInput: { width: '100%', direction: 'rtl', writingDirection: 'rtl', textAlign: 'right' },
  chips: { alignSelf: 'flex-start', flexDirection: 'row', flexWrap: 'wrap', gap: 10, direction: 'rtl' },
  chip: { minHeight: 56, paddingHorizontal: 16, borderRadius: 16, borderWidth: 1, borderColor: Colors.line, justifyContent: 'center' },
  selected: { backgroundColor: Colors.tint, borderColor: Colors.primary },
  counter: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', direction: 'rtl' },
  counterButton: { width: 76, height: 76, borderRadius: 18, backgroundColor: Colors.primary, alignItems: 'center', justifyContent: 'center' },
  counterText: { color: Colors.white, fontSize: 34, fontWeight: '900' },
  number: { fontSize: 44, fontWeight: '900', color: Colors.text },
  rtlOptionRow: {
    width: '100%',
    direction: 'rtl',
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
  },
  repeatTypeOptions: { flexDirection: 'row', direction: 'rtl', gap: 10 },
  repeatOption: {
    flex: 1,
    minHeight: 104,
    borderRadius: 16,
    borderWidth: 2,
    borderColor: Colors.line,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 12,
  },
  repeatOptionContent: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  repeatOptionText: {
    fontFamily: 'Tajawal_800ExtraBold',
    fontSize: 18,
    color: Colors.text,
    textAlign: 'center',
    writingDirection: 'rtl',
  },
  frequencyDetails: { gap: 18, marginTop: 12 },
  detailTitle: {
    fontFamily: 'Tajawal_800ExtraBold',
    fontSize: 20,
    color: Colors.text,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  detailOption: {
    minHeight: 64,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: Colors.line,
    justifyContent: 'center',
    paddingHorizontal: 18,
  },
  detailOptionText: {
    fontFamily: 'Tajawal_800ExtraBold',
    fontSize: 18,
    color: Colors.text,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  weekdayOptions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, direction: 'rtl' },
  weekdayOption: {
    minWidth: '47%',
    minHeight: 64,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: Colors.line,
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  monthDayOptions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, direction: 'rtl' },
  monthDayOption: {
    width: 64,
    minHeight: 64,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: Colors.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  monthDayOptionText: {
    fontFamily: 'Tajawal_800ExtraBold',
    fontSize: 20,
    color: Colors.text,
  },
  timeRow: {
    minHeight: 76,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Colors.line,
    paddingHorizontal: 16,
    paddingVertical: 10,
    flexDirection: 'row',
    direction: 'rtl',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  timeRowText: { gap: 2 },
  timeBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: Colors.tint,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  timeValue: { fontSize: 20, fontWeight: '800', color: Colors.primaryDark },
  moreButton: {
    alignSelf: 'stretch',
    minHeight: 56,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: Colors.primary,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  moreButtonText: { fontSize: 18, fontWeight: '800', color: Colors.primaryDark },
  suggestionLabel: { fontFamily: 'Tajawal_800ExtraBold', fontSize: 18, color: Colors.muted, textAlign: 'right' },
  backButton: {
    minHeight: 56,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
});
