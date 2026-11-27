import { TextStyle, ViewStyle } from 'react-native';

/**
 * نظام تخطيط يدوي للاتجاه (RTL) دون الاعتماد على I18nManager.forceRTL.
 * forceRTL متوقف، والنظام يعمل LTR بشكل طبيعي، ونحن نتحكم بالاتجاه يدويًا هنا.
 *
 * الاستخدام:
 *   import * as L from '@/lib/layout';
 *   <View style={[styles.card, L.row]}>...</View>
 *   <Text style={L.textRight}>اسم الدواء</Text>
 */

// صف عناصر يبدأ من اليمين إلى اليسار (الافتراضي في الواجهة العربية).
export const row = { flexDirection: 'row-reverse' } as const satisfies ViewStyle;

// صف عناصر يبدأ من اليسار إلى اليمين (لاستثناءات نادرة مثل الأرقام/الأوقات).
export const rowReverse = { flexDirection: 'row' } as const satisfies ViewStyle;

// محاذاة النص.
export const textRight = { textAlign: 'right' } as const satisfies TextStyle;
export const textLeft = { textAlign: 'left' } as const satisfies TextStyle;

// هوامش/حشوات منطقية (start = يمين، end = يسار في الواجهة العربية).
export const ms = (n: number): ViewStyle => ({ marginRight: n });
export const me = (n: number): ViewStyle => ({ marginLeft: n });
export const ps = (n: number): ViewStyle => ({ paddingRight: n });
export const pe = (n: number): ViewStyle => ({ paddingLeft: n });

// محاذاة العناصر على المحور المتقاطع (start = يمين، end = يسار).
export const alignStart = { alignItems: 'flex-end' } as const satisfies ViewStyle;
export const alignEnd = { alignItems: 'flex-start' } as const satisfies ViewStyle;