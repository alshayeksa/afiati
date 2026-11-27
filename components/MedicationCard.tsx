import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';

import { formatTime, getDoseStatus } from '@/lib/helpers';
import * as L from '@/lib/layout';
import { Colors, globalText, shadow } from '@/lib/theme';
import { Medication, MedicationLog } from '@/store/types';

export function MedicationCard({ medication, log }: { medication: Medication; log?: MedicationLog }) {
  const status = getDoseStatus(log);
  const palette =
    status.tone === 'green'
      ? { bg: '#F4FAFD', border: Colors.primaryLight, badge: Colors.tint, text: Colors.primaryDark }
      : status.tone === 'red'
        ? { bg: '#FFF5F5', border: Colors.danger, badge: '#FFE5E5', text: Colors.danger }
        : status.tone === 'gold'
          ? { bg: Colors.background, border: Colors.primary, badge: '#CDEAF6', text: Colors.primaryDark }
          : { bg: Colors.white, border: Colors.line, badge: Colors.gray, text: Colors.muted };
  const timeLabel = log ? formatTime(log.scheduled_at.slice(11, 16)) : medication.times.map(formatTime).join('، ');

  return (
    <View style={[styles.card, { backgroundColor: palette.bg, borderColor: palette.border }]}>
      {/* Header */}
      <View style={[styles.header, L.row]}>
        <Text style={[styles.badge, { backgroundColor: palette.badge, color: palette.text }]}>
          {status.label}
        </Text>
        <Text style={[globalText.cardTitle, L.textRight]}>{medication.name}</Text>
      </View>

      {/* Divider */}
      <View style={[styles.divider, { backgroundColor: `${palette.border}55` }]} />

      {/* Info Row */}
      <View style={styles.infoRow}>
        <View style={styles.infoItem}>
          <Ionicons name="medical-outline" size={18} color={Colors.muted} />
          <Text style={[styles.infoText, L.textRight]}>
            الجرعة: {medication.dose_amount} {medication.dose_unit}
          </Text>
        </View>

        <View style={styles.infoItem}>
          <Ionicons name="time-outline" size={18} color={Colors.muted} />
          <Text style={[styles.infoText, L.textRight]}>{timeLabel}</Text>
        </View>

        {medication.stock_count !== null ? (
          <View style={styles.infoItem}>
            <Ionicons name="cube-outline" size={18} color={Colors.muted} />
            <Text style={[styles.infoText, L.textRight]}>المتبقي: {medication.stock_count}</Text>
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    borderWidth: 1.5,
    padding: 16,
    gap: 12,
    ...shadow,
  },
  header: {
    width: '100%',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
  },
  badge: {
    overflow: 'hidden',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 6,
    fontFamily: 'Tajawal_700Bold',
    fontSize: 14,
    textAlign: 'center',
  },
  divider: {
    height: 1,
    borderRadius: 1,
  },
  infoRow: {
    width: '100%',
    direction: 'rtl',
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'flex-start',
    gap: 16,
  },
  infoItem: {
    direction: 'rtl',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  infoText: {
    fontFamily: 'Tajawal_400Regular',
    fontSize: 15,
    color: Colors.muted,
  },
});
