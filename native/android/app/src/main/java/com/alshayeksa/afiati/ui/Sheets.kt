package com.alshayeksa.afiati.ui

import com.alshayeksa.afiati.Lang
import com.alshayeksa.afiati.tr
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.alshayeksa.afiati.data.Gender
import com.alshayeksa.afiati.sync.HelpLocation
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
/** رجل أو امرأة: تُكتب به رسائل المساعدة لعائلتك (يحتاج / تحتاج) */
@Composable
fun GenderPicker(gender: String?, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip(tr("رجل", "Man"), gender == Gender.MALE, Modifier.weight(1f)) { onChange(Gender.MALE) }
        Chip(tr("امرأة", "Woman"), gender == Gender.FEMALE, Modifier.weight(1f)) { onChange(Gender.FEMALE) }
    }
}

fun validateProfile(name: String, phone: String, email: String): String? = when {
    name.trim().length < 2 -> tr("اكتب اسمك كما تحب أن يراه أهلك.", "Type your name as you'd like your family to see it.")
    phone.count(Char::isDigit) < 9 -> tr("أدخل رقم جوال صحيحًا.", "Enter a valid mobile number.")
    email.isNotBlank() && !Regex("^\\S+@\\S+\\.\\S+$").matches(email.trim()) -> tr("البريد غير صحيح. صححه أو اتركه فارغًا.", "The email isn't valid. Fix it or leave it empty.")
    else -> null
}

/** ملفي: الاسم والجوال والبريد */
@Composable
fun ProfileSheet(onDismiss: () -> Unit) {
    val patient = AppModel.patient ?: return
    var name by remember { mutableStateOf(patient.fullName) }
    var phone by remember { mutableStateOf(patient.phone) }
    var email by remember { mutableStateOf(patient.email.orEmpty()) }
    var gender by remember { mutableStateOf(patient.gender) }
    var error by remember { mutableStateOf<String?>(null) }
    val changed = name != patient.fullName || phone != patient.phone || email != patient.email.orEmpty() || gender != patient.gender

    AfiatiSheet(tr("ملفي", "My profile"), onDismiss) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AvatarView(name.ifBlank { patient.fullName }, patient.id, size = 84.dp, color = Brand.green)
            Text(name.ifBlank { patient.fullName }, style = tajawal(26, FontWeight.Bold))
            StatusLine(Brand.glow, tr("ملفك محفوظ على جوالك", "Your profile is saved on your phone"))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel(tr("الاسم كما يراه أهلك", "Name as your family sees it"))
            GlassField(name, { name = it; error = null }, tr("الاسم", "Name"))
            FieldLabel(tr("رقم الجوال", "Mobile number"))
            GlassField(phone, { phone = it; error = null }, "05xxxxxxxx", keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone), ltr = true)
            FieldLabel(tr("أنا", "I am"))
            GenderPicker(gender) { gender = it; error = null }
            FieldLabel(tr("البريد الإلكتروني (اختياري)", "Email (optional)"))
            GlassField(email, { email = it; error = null }, "name@example.com", keyboard = KeyboardOptions(keyboardType = KeyboardType.Email), ltr = true)
            error?.let { Text(it, style = tajawal(16, FontWeight.Medium, Brand.danger)) }
            PrimaryButton(tr("حفظ التعديلات", "Save changes"), Modifier.padding(top = 8.dp), icon = Icons.Filled.Save, enabled = changed) {
                error = validateProfile(name, phone, email)
                if (error == null) {
                    AppModel.updatePatient(name, phone, email.ifBlank { null }, gender)
                    onDismiss()
                }
            }
            Text(
                tr("لا نطلب كلمة مرور. بياناتك على جوالك، وتُنسخ إلى خادم عافيتي عند توفر الإنترنت لتصل لمن توافق عليهم من عائلتك فقط.", "No password needed. Your data is on your phone and is copied to the Afiati server when online, reaching only the family members you approve."),
                style = tajawal(15, color = Brand.secondaryText),
            )
        }
    }
}

/** الإعدادات: التذكيرات، رقم الطوارئ، المظهر، والمزامنة */
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

    AfiatiSheet(tr("الإعدادات", "Settings"), onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("اللغة · Language")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("العربية", !Lang.english, Modifier.weight(1f)) { AppModel.setLanguage(context, false) }
                Chip("English", Lang.english, Modifier.weight(1f)) { AppModel.setLanguage(context, true) }
            }

            FieldLabel(tr("التذكيرات", "Reminders"), Modifier.padding(top = 10.dp))
            PermissionRow(
                Icons.Filled.Notifications, tr("إشعارات الأدوية", "Medicine notifications"),
                if (notificationsAllowed) tr("مفعّلة", "On") else tr("متوقفة · لن تصلك التذكيرات", "Off · you won't get reminders"), notificationsAllowed,
            ) {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            PermissionRow(
                Icons.Filled.AlarmOn, tr("التذكير في الوقت الدقيق", "Exact-time reminders"),
                if (exactAllowed) tr("مفعّل", "On") else tr("متوقف · قد يتأخر التذكير دقائق", "Off · reminders may be a few minutes late"), exactAllowed,
            ) {
                if (Build.VERSION.SDK_INT >= 31) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(role = Role.Switch) { DoseAlert.setSound(!DoseAlert.soundOn) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.NotificationsActive, null, tint = Brand.glow, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("صوت تنبيه موعد الدواء", "Medicine time alert sound"), style = tajawal(18, FontWeight.Medium))
                    Text(tr("نغمة تتكرر والبطاقة تومض حتى تسجّل الجرعة، وتتوقف بعد 15 دقيقة", "A repeating chime while the card flashes until you mark the dose; stops after 15 minutes"), style = tajawal(14, color = Brand.secondaryText))
                }
                Switch(
                    DoseAlert.soundOn, { DoseAlert.setSound(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.mint, checkedThumbColor = Brand.text, uncheckedTrackColor = Brand.text.copy(alpha = 0.1f)),
                )
            }

            var locationAsked by remember { mutableIntStateOf(0) }
            val locationAllowed = remember(tick, locationAsked) { HelpLocation.granted(context) }
            val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { locationAsked++ }
            PermissionRow(
                Icons.Filled.LocationOn, tr("موقعي مع طلب المساعدة", "My location with help requests"),
                if (locationAllowed) tr("مسموح · يصل موقعك لعائلتك على خرائط Google", "Allowed · your family sees your location on Google Maps") else tr("غير مسموح · اضغط للسماح", "Not allowed · tap to allow"), locationAllowed,
            ) {
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }

            FieldLabel(tr("رقم للطوارئ", "Emergency number"), Modifier.padding(top = 10.dp))
            EmergencyContactCard()

            FieldLabel(tr("المظهر", "Appearance"), Modifier.padding(top = 10.dp))
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
                Text(tr("خط أكبر", "Larger text"), style = tajawal(18, FontWeight.Medium), modifier = Modifier.weight(1f))
                Switch(
                    Appearance.largeText, { Appearance.toggleLargeText(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.mint, checkedThumbColor = Brand.text, uncheckedTrackColor = Brand.text.copy(alpha = 0.1f)),
                )
            }

            FieldLabel(tr("المزامنة", "Sync"), Modifier.padding(top = 10.dp))
            SyncCard()
        }
    }
}

const val DEVELOPER_SITE = "https://alshaye.ai"

/** عن التطبيق: من زر «i» أعلى الشاشة، بين الإشعارات والإعدادات */
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AfiatiSheet(tr("عن التطبيق", "About"), onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogoView(84.dp)
                Text(tr("عافيتي", "Afiati"), style = tajawal(30, FontWeight.Bold))
                Text(tr("مساعدك الأمين لمواعيد الأدوية، وطمأنينة لعائلتك.", "Your trusted companion for medicine times, and peace of mind for your family."), style = tajawal(17, color = Brand.secondaryText), textAlign = TextAlign.Center)
                Text(tr("الإصدار ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"), style = tajawal(14, color = Brand.secondaryText))
            }
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(tr("تطوير", "Developed by"), style = tajawal(15, color = Brand.secondaryText))
                Text(tr("شايع الشايع", "Shaye Alshaye"), style = tajawal(26, FontWeight.Bold))
                Text(tr("مطور وخبير ذكاء اصطناعي", "Developer & AI Expert"), style = tajawal(17, color = Brand.secondaryText), textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                GlassButton("alshaye.ai", icon = Icons.Filled.Language) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_SITE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { AppModel.toast = tr("لا يوجد متصفح لفتح الموقع", "No browser to open the site") }
                }
            }
        }
    }
}

/** رقم يتصل به الكبير من شاشة المساعدة بضغطة، ويعمل بلا إنترنت. يبقى على الجوال فقط. */
@Composable
private fun EmergencyContactCard() {
    var name by remember { mutableStateOf(AppModel.emergencyName) }
    var phone by remember { mutableStateOf(AppModel.emergencyPhone) }
    val changed = name != AppModel.emergencyName || phone != AppModel.emergencyPhone
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("يظهر زر الاتصال به في شاشة المساعدة، ويعمل حتى بدون إنترنت.", "A button to call them appears on the help screen, and it works even without internet."), style = tajawal(15, color = Brand.secondaryText))
        GlassField(name, { name = it }, tr("الاسم (مثلًا: ابني خالد)", "Name (e.g. my son Khalid)"))
        GlassField(phone, { phone = it }, "05xxxxxxxx", keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone), ltr = true)
        GlassButton(if (changed) tr("حفظ الرقم", "Save number") else tr("محفوظ", "Saved"), icon = Icons.Filled.Save, enabled = changed && phone.count(Char::isDigit) >= 3) {
            AppModel.saveEmergencyContact(name, phone)
            AppModel.toast = tr("حُفظ رقم الطوارئ", "Emergency number saved")
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
        if (!ok) Text(tr("تفعيل", "Turn on"), style = tajawal(17, FontWeight.Bold, Brand.glow))
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
        sync.running -> Triple(Icons.Filled.Sync, Brand.warning, tr("جاري المزامنة…", "Syncing…"))
        sync.status == SyncStatus.SYNCED && sync.pending == 0 -> Triple(Icons.Filled.CloudDone, Brand.glow, tr("كل شيء متزامن", "Everything is synced"))
        sync.status == SyncStatus.FAILED -> Triple(Icons.Filled.Warning, Brand.danger, tr("تعذّرت المزامنة", "Sync failed"))
        sync.status == SyncStatus.NOT_CONFIGURED -> Triple(Icons.Filled.CloudOff, Brand.secondaryText, tr("المزامنة غير مفعّلة", "Sync is off"))
        else -> Triple(Icons.Filled.CloudOff, Brand.warning, tr("بانتظار الإنترنت", "Waiting for internet"))
    }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = tajawal(19, FontWeight.Bold, color))
        }
        Text(
            when {
                sync.pending > 0 -> tr("${sync.pending} ${if (sync.pending in 3..10) "تغييرات" else "تغيير"} محفوظة على جوالك وتُرفع تلقائيًا.", "${sync.pending} ${if (sync.pending == 1) "change" else "changes"} saved on your phone, uploading automatically.")
                else -> tr("كل تغييراتك محفوظة على جوالك وفي الخادم.", "All your changes are saved on your phone and on the server.")
            },
            style = tajawal(16, color = Brand.secondaryText),
        )
        sync.lastSync?.let { Text(tr("آخر مزامنة ${Format.relative(it)}", "Last sync ${Format.relative(it)}"), style = tajawal(15, color = Brand.secondaryText)) }
        sync.error?.takeIf { sync.status == SyncStatus.FAILED }?.let { Text(it, style = tajawal(15, color = Brand.danger)) }
        GlassButton(tr("زامن الآن", "Sync now"), icon = Icons.Filled.Sync, enabled = !sync.running) { AppModel.syncNow() }
    }
}

/** الإشعارات الواردة من العائلة */
@Composable
fun NotificationsSheet(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { AppModel.refreshNotifications() }
    val items = AppModel.notifications
    AfiatiSheet(tr("الإشعارات", "Notifications"), onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Notifications, null, tint = Brand.secondaryText, modifier = Modifier.size(48.dp))
                    Text(tr("لا توجد إشعارات", "No notifications"), style = tajawal(20, FontWeight.Bold))
                    Text(tr("هنا تظهر طلبات المتابعة وتذكيرات عائلتك.", "Follow requests and reminders from your family appear here."), style = tajawal(16, color = Brand.secondaryText), textAlign = TextAlign.Center)
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
            if (items.any { !it.read }) GlassButton(tr("تحديد الكل كمقروء", "Mark all as read"), Modifier.padding(top = 6.dp)) { AppModel.markAllRead() }
        }
    }
}
