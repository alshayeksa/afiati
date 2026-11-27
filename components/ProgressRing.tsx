import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle } from 'react-native-svg';

import { Colors, shadow } from '@/lib/theme';

type ProgressCardProps = {
  taken: number;
  total: number;
};

type ProgressRingProps = {
  done: number;
  total: number;
};

export function ProgressCard({ taken, total }: ProgressCardProps) {
  const percentage = total === 0 ? 0 : Math.round((taken / total) * 100);
  const size = 110;
  const strokeWidth = 10;
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const progress = circumference - (percentage / 100) * circumference;
  const color = percentage === 100 ? Colors.primary : percentage >= 50 ? Colors.gold : Colors.danger;
  const status =
    percentage === 100 ? '🎉 ممتاز!' : percentage >= 50 ? '💪 استمر' : taken === 0 ? '⏳ لم تبدأ بعد' : '⚠️ تأخرت';

  return (
    <View style={styles.card}>
      <View style={styles.circleWrapper}>
        <Svg width={size} height={size}>
          <Circle cx={size / 2} cy={size / 2} r={radius} stroke={Colors.line} strokeWidth={strokeWidth} fill="none" />
          <Circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke={color}
            strokeWidth={strokeWidth}
            fill="none"
            strokeDasharray={circumference}
            strokeDashoffset={progress}
            strokeLinecap="round"
            rotation="-90"
            origin={`${size / 2}, ${size / 2}`}
          />
        </Svg>
        <View style={styles.circleCenter}>
          <Text style={[styles.percentage, { color }]}>{percentage}%</Text>
        </View>
      </View>

      <View style={styles.info}>
        <View style={styles.infoRow}>
          <Text style={styles.title}>التزامك اليوم</Text>
        </View>
        <View style={styles.infoRow}>
          <Text style={styles.fraction}>
            <Text style={styles.takenNum}>{taken}</Text>
            <Text style={styles.separator}> من </Text>
            <Text style={styles.totalNum}>{total}</Text>
          </Text>
        </View>
        <View style={styles.infoRow}>
          <Text style={styles.label}>جرعات مكتملة</Text>
        </View>
        <View style={styles.infoRow}>
          <View style={[styles.pill, { backgroundColor: `${color}20` }]}>
            <Text style={[styles.pillText, { color }]}>{status}</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

export function ProgressRing({ done, total }: ProgressRingProps) {
  return <ProgressCard taken={done} total={total} />;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: Colors.white,
    borderRadius: 20,
    padding: 20,
    marginHorizontal: 20,
    marginTop: 16,
    direction: 'rtl',
    flexDirection: 'row-reverse',
    alignItems: 'center',
    gap: 20,
    ...shadow,
  },
  circleWrapper: {
    width: 110,
    height: 110,
    justifyContent: 'center',
    alignItems: 'center',
  },
  circleCenter: {
    position: 'absolute',
    justifyContent: 'center',
    alignItems: 'center',
  },
  percentage: {
    fontSize: 22,
    fontFamily: 'Tajawal_800ExtraBold',
  },
  info: {
    flex: 1,
    direction: 'rtl',
    alignItems: 'stretch',
    gap: 4,
  },
  infoRow: {
    width: '100%',
    direction: 'rtl',
    flexDirection: 'row',
    justifyContent: 'flex-start',
    alignItems: 'center',
  },
  title: {
    fontSize: 13,
    fontFamily: 'Tajawal_400Regular',
    color: Colors.muted,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  fraction: {
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  takenNum: {
    fontSize: 28,
    fontFamily: 'Tajawal_800ExtraBold',
    color: Colors.text,
  },
  separator: {
    fontSize: 16,
    fontFamily: 'Tajawal_400Regular',
    color: Colors.muted,
  },
  totalNum: {
    fontSize: 20,
    fontFamily: 'Tajawal_700Bold',
    color: Colors.muted,
  },
  label: {
    fontSize: 13,
    fontFamily: 'Tajawal_400Regular',
    color: Colors.muted,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  pill: {
    paddingHorizontal: 12,
    paddingVertical: 5,
    borderRadius: 20,
    marginTop: 6,
  },
  pillText: {
    fontSize: 13,
    fontFamily: 'Tajawal_700Bold',
    textAlign: 'right',
    writingDirection: 'rtl',
  },
});
