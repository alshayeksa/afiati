package com.alshayeksa.afiati.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alshayeksa.afiati.BuildConfig
import com.alshayeksa.afiati.reminders.Reminders
import com.alshayeksa.afiati.sync.SyncStatus

/** التحقق من بيانات الملف الشخصي. يعيد رسالة الخطأ أو null. */
fun validateProfile(name: String, phone: String, email: String): String? = when {
    name.trim().length < 2 -> "اكتب اسمك كما تحب أن يراه أهلك."
    phone.count(Char::isDigit) < 9 -> "أدخل رقم جوال صحيحًا."
    email.isNotBlank() && !Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim()) -> "البريد غير صحيح. صححه أو اتركه فارغًا."
    else -> null
}

/** ملفي: الاسم والجوال والبريد */
@Composable
fun ProfileSheet(onDismiss: () -> Unit) {
    val patient = AppModel.patient ?: return
    var name by remember { mutableStateOf(patient.fullName) }
    var phone by remember { mutableStateOf(patient.phone) }
    var email by remember { mutableStateOf(patient.email.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val changed = name != patient.fullName || phone != patient.phone || email != patient.email.orEmpty()

    AfiatiSheet("ملفي", onDismiss) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AvatarView(name.ifBlank { patient.fullName }, patient.id, size = 84.dp, color = Brand.green)
            Text(name.ifBlank { patient.fullName }, style = tajawal(26, FontWeight.Bold))
            StatusLine(Brand.glow, "ملفك محفوظ على جوالك")
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("الاسم كما يراه أهلك")
            GlassField(name, { name = it; error = null }, "الاسم")
            FieldLabel("رقم الجوال")
            GlassField(phone, { phone = it; error = null }, "05xxxxxxxx", keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone), ltr = true)
            FieldLabel("البريد الإلكتروني (اختياري)")
            GlassField(email, { email = it; error = null }, "name@example.com", keyboard = KeyboardOptions(keyboardType = KeyboardType.Email), ltr = true)
            error?.let { Text(it, style = tajawal(16, FontWeight.Medium, Brand.danger)) }
            PrimaryButton("حفظ التعديلات", Modifier.padding(top = 8.dp), icon = Icons.Filled.Save, enabled = changed) {
                error = validateProfile(name, phone, email)
                if (error == null) {
                    AppModel.updatePatient(name, phone, email.ifBlank { null })
                    onDismiss()
                }
            }
            Text(
                "لا نطلب كلمة مرور. بياناتك على جوالك، وتُنسخ إلى خادم عافيتي عند توفر الإنترنت لتصل لمن توافق عليهم من عائلتك فقط.",
                style = tajawal(15, color = Brand.secondaryText),
            )
        }
    }
}

/** الإعدادات: التذكيرات، المظهر، المزامنة، وعن التطبيق */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // يتغير عند العودة من إعدادات الجوال، فتُقرأ الأذونات من جديد
    val tick = AppModel.resumeTick
    val notificationsAllowed = remember(tick) {
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val exactAllowed = remember(tick) { Reminders.canScheduleExact(context) }
    LaunchedEffect(exactAllowed) { if (exactAllowed) runCatching { Reminders.reschedule(context) } }

    AfiatiSheet("الإعدادات", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("التذكيرات")
            PermissionRow(
                Icons.Filled.Notifications, "إشعارات الأدوية",
                if (notificationsAllowed) "مفعّلة" else "متوقفة · لن تصلك التذكيرات", notificationsAllowed,
            ) {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            PermissionRow(
                Icons.Filled.AlarmOn, "التذكير في الوقت الدقيق",
                if (exactAllowed) "مفعّل" else "متوقف · قد يتأخر التذكير دقائق", exactAllowed,
            ) {
                if (Build.VERSION.SDK_INT >= 31) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            }

            FieldLabel("المظهر", Modifier.padding(top = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeAccent.entries.forEach { accent -> AccentSwatch(accent, Appearance.accent == accent) { Appearance.apply(accent, Appearance.mode) } }
            }
            Text(Appearance.accent.title, style = tajawal(16, color = Brand.secondaryText), modifier = Modifier.padding(horizontal = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode -> Chip(mode.title, Appearance.mode == mode, Modifier.weight(1f)) { Appearance.apply(Appearance.accent, mode) } }
            }
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(role = Role.Switch) { Appearance.toggleLargeText(!Appearance.largeText) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.TextFields, null, tint = Brand.glow, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("خط أكبر", style = tajawal(18, FontWeight.Medium), modifier = Modifier.weight(1f))
                Switch(
                    Appearance.largeText, { Appearance.toggleLargeText(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.mint, checkedThumbColor = Brand.text, uncheckedTrackColor = Brand.text.copy(alpha = 0.1f)),
                )
            }

            FieldLabel("المزامنة", Modifier.padding(top = 10.dp))
            SyncCard()

            FieldLabel("عن التطبيق", Modifier.padding(top = 10.dp))
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogoView(72.dp)
                Text("عافيتي", style = tajawal(26, FontWeight.Bold))
                Text("مساعدك الأمين لمواعيد الأدوية، وطمأنينة لعائلتك.", style = tajawal(16, color = Brand.secondaryText), textAlign = TextAlign.Center)
                Text("الإصدار ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = tajawal(14, color = Brand.secondaryText))
            }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, status: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(enabled = !ok, role = Role.Button, onClick = onFix).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (ok) Brand.glow else Brand.warning, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = tajawal(18, FontWeight.Medium))
            Text(status, style = tajawal(15, color = if (ok) Brand.secondaryText else Brand.warning))
        }
        if (!ok) Text("تفعيل", style = tajawal(17, FontWeight.Bold, Brand.glow))
    }
}

@Composable
private fun AccentSwatch(accent: ThemeAccent, selected: Boolean, onClick: () -> Unit) {
    val palette = Palette(accent, Appearance.light)
    Box(
        Modifier.size(52.dp).border(if (selected) 3.dp else 1.dp, if (selected) Brand.text else Brand.text.copy(alpha = 0.2f), CircleShape).padding(5.dp)
            .background(Brush.linearGradient(listOf(palette.color(0x4FF0B0), palette.color(0x13684F))), CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = accent.title, onClick = onClick),
    )
}

@Composable
private fun SyncCard() {
    val sync = AppModel.sync
    val (icon, color, title) = when {
        sync.running -> Triple(Icons.Filled.Sync, Brand.warning, "جاري المزامنة…")
        sync.status == SyncStatus.SYNCED && sync.pending == 0 -> Triple(Icons.Filled.CloudDone, Brand.glow, "كل شيء متزامن")
        sync.status == SyncStatus.FAILED -> Triple(Icons.Filled.Warning, Brand.danger, "تعذّرت المزامنة")
        sync.status == SyncStatus.NOT_CONFIGURED -> Triple(Icons.Filled.CloudOff, Brand.secondaryText, "المزامنة غير مفعّلة")
        else -> Triple(Icons.Filled.CloudOff, Brand.warning, "بانتظار الإنترنت")
    }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = tajawal(19, FontWeight.Bold, color))
        }
        Text(
            when {
                sync.pending > 0 -> "${sync.pending} ${if (sync.pending in 3..10) "تغييرات" else "تغيير"} محفوظة على جوالك وتُرفع تلقائيًا."
                else -> "كل تغييراتك محفوظة على جوالك وفي الخادم."
            },
            style = tajawal(16, color = Brand.secondaryText),
        )
        sync.lastSync?.let { Text("آخر مزامنة ${Format.relative(it)}", style = tajawal(15, color = Brand.secondaryText)) }
        sync.error?.takeIf { sync.status == SyncStatus.FAILED }?.let { Text(it, style = tajawal(15, color = Brand.danger)) }
        GlassButton("زامن الآن", icon = Icons.Filled.Sync, enabled = !sync.running) { AppModel.syncNow() }
    }
}

/** الإشعارات الواردة من العائلة */
@Composable
fun NotificationsSheet(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { AppModel.refreshNotifications() }
    val items = AppModel.notifications
    AfiatiSheet("الإشعارات", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Notifications, null, tint = Brand.secondaryText, modifier = Modifier.size(48.dp))
                    Text("لا توجد إشعارات", style = tajawal(20, FontWeight.Bold))
                    Text("هنا تظهر طلبات المتابعة وتذكيرات عائلتك.", style = tajawal(16, color = Brand.secondaryText), textAlign = TextAlign.Center)
                }
            }
            items.forEach { item ->
                val (icon, color) = when (item.type) {
                    "invite_request" -> Icons.Filled.PersonAdd to Color(0xFF4D7FB5)
                    "invite_response" -> Icons.Filled.FavoriteBorder to Color(0xFF1E8E6C)
                    "family_reminder" -> Icons.Filled.NotificationsActive to Color(0xFFB07A3F)
                    else -> Icons.Filled.Warning to Color(0xFFBD616E)
                }
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), if (item.read) 0.15f else 0.5f).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(icon, color, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = tajawal(18, FontWeight.Bold))
                        Text(item.body, style = tajawal(16, color = Brand.secondaryText))
                        Text(Format.relative(item.createdAt), style = tajawal(14, color = Brand.secondaryText))
                    }
                    if (!item.read) Box(Modifier.size(10.dp).background(Brand.glow, CircleShape))
                }
            }
            if (items.any { !it.read }) GlassButton("تحديد الكل كمقروء", Modifier.padding(top = 6.dp)) { AppModel.markAllRead() }
        }
    }
}
