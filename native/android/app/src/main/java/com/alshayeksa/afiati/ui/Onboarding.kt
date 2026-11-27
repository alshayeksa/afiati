package com.alshayeksa.afiati.ui

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
                    Text("عافيتي", style = tajawal(52, FontWeight.Bold), modifier = Modifier.padding(bottom = 8.dp))
                    Text("مساعدك الأمين لمواعيد الأدوية، وطمأنينة لعائلتك.", style = tajawal(20, color = Brand.secondaryText))
                }
                WaveRibbon(Modifier.fillMaxWidth().height(150.dp))
                Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Feature(Icons.Filled.NotificationsActive, "تذكير بكل جرعة في وقتها", "وتسجّلها من الإشعار بضغطة واحدة.")
                    Feature(Icons.Filled.Diversity1, "عائلتك تطمئن عليك", "بموافقتك، وأنت تختار ما يرونه.")
                    Feature(Icons.Filled.WifiOff, "يعمل بدون إنترنت", "كل شيء محفوظ على جوالك أولًا.")
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("لنتعرّف عليك", style = tajawal(36, FontWeight.Bold))
                    Text("بياناتك على جوالك. لا نطلب كلمة مرور.", style = tajawal(18, color = Brand.secondaryText))
                }
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("اسمك كما يراه أهلك")
                    GlassField(name, { name = it; error = null }, "الاسم", style = tajawal(21), keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    FieldLabel("رقم جوالك")
                    GlassField(phone, { phone = it; error = null }, "05xxxxxxxx", style = tajawal(21), keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone), ltr = true)
                    FieldLabel("البريد الإلكتروني (اختياري)")
                    GlassField(email, { email = it; error = null }, "name@example.com", keyboard = KeyboardOptions(keyboardType = KeyboardType.Email), ltr = true)
                    error?.let { Text(it, style = tajawal(17, FontWeight.Medium, Brand.danger)) }
                    Text(
                        "يظهر اسمك لمن توافق عليه من عائلتك فقط، ولا يظهر رقمك لأحد.",
                        style = tajawal(15, color = Brand.secondaryText), modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (page == 0) {
                PrimaryButton("لنبدأ", icon = Icons.AutoMirrored.Filled.ArrowForward) { page = 1 }
            } else {
                PrimaryButton(if (saving) "جارٍ الحفظ…" else "ابدأ استخدام عافيتي", icon = Icons.Filled.Check, enabled = !saving) {
                    error = validateProfile(name, phone, email)
                    if (error == null) {
                        saving = true
                        AppModel.createPatient(name, phone, email.ifBlank { null })
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
