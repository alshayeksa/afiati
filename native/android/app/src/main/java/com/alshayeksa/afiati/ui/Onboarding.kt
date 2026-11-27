package com.alshayeksa.afiati.ui

import com.alshayeksa.afiati.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** أول فتح: ترحيب قصير، ثم الاسم والجوال. بلا كلمة مرور ولا حساب. */
@Composable
fun OnboardingScreen() {
    var page by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = page == 1) { page = 0 }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = top + 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (page == 0) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogoView(84.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("عافيتي", "Afiati"), style = tajawal(52, FontWeight.Bold), modifier = Modifier.padding(bottom = 8.dp))
                    Text(tr("مساعدك الأمين لمواعيد الأدوية، وطمأنينة لعائلتك.", "Your trusted companion for medicine times, and peace of mind for your family."), style = tajawal(20, color = Brand.secondaryText))
                }
                WaveRibbon(Modifier.fillMaxWidth().height(150.dp))
                Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Feature(Icons.Filled.NotificationsActive, tr("تذكير بكل جرعة في وقتها", "A reminder for every dose on time"), tr("وتسجّلها من الإشعار بضغطة واحدة.", "Log it from the notification with one tap."))
                    Feature(Icons.Filled.Diversity1, tr("عائلتك تطمئن عليك", "Your family can check on you"), tr("بموافقتك، وأنت تختار ما يرونه.", "With your approval, and you choose what they see."))
                    Feature(Icons.Filled.WifiOff, tr("يعمل بدون إنترنت", "Works without internet"), tr("كل شيء محفوظ على جوالك أولًا.", "Everything is saved on your phone first."))
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("لنتعرّف عليك", "Let's get to know you"), style = tajawal(36, FontWeight.Bold))
                    Text(tr("بياناتك على جوالك. لا نطلب كلمة مرور.", "Your data stays on your phone. No password needed."), style = tajawal(18, color = Brand.secondaryText))
                }
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel(tr("اسمك كما يراه أهلك", "Your name as your family sees it"))
                    GlassField(name, { name = it; error = null }, tr("الاسم", "Name"), style = tajawal(21), keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    FieldLabel(tr("رقم جوالك", "Your mobile number"))
                    GlassField(phone, { phone = it; error = null }, "05xxxxxxxx", style = tajawal(21), keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone), ltr = true)
                    FieldLabel(tr("أنا", "I am"))
                    GenderPicker(gender) { gender = it; error = null }
                    FieldLabel(tr("البريد الإلكتروني (اختياري)", "Email (optional)"))
                    GlassField(email, { email = it; error = null }, "name@example.com", keyboard = KeyboardOptions(keyboardType = KeyboardType.Email), ltr = true)
                    error?.let { Text(it, style = tajawal(17, FontWeight.Medium, Brand.danger)) }
                    Text(
                        tr("يظهر اسمك لمن توافق عليه من عائلتك فقط، ويصلهم رقمك وموقعك فقط حين تطلب المساعدة.", "Only the family members you approve see your name; they get your number and location only when you ask for help."),
                        style = tajawal(15, color = Brand.secondaryText), modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (page == 0) {
                PrimaryButton(tr("لنبدأ", "Let's start"), icon = Icons.AutoMirrored.Filled.ArrowForward) { page = 1 }
            } else {
                PrimaryButton(if (saving) tr("جارٍ الحفظ…", "Saving…") else tr("ابدأ استخدام عافيتي", "Start using Afiati"), icon = Icons.Filled.Check, enabled = !saving) {
                    error = validateProfile(name, phone, email) ?: if (gender == null) tr("اختر: رجل أو امرأة.", "Choose: man or woman.") else null
                    if (error == null) {
                        saving = true
                        AppModel.createPatient(name, phone, email.ifBlank { null }, gender)
                    }
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, Brand.green, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = tajawal(18, FontWeight.Bold))
            Text(body, style = tajawal(16, color = Brand.secondaryText))
        }
    }
}
