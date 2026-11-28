package com.alshayeksa.afiati.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alshayeksa.afiati.sync.AppControl
import com.alshayeksa.afiati.tr

/**
 * شاشة تغطي التطبيق: الخدمة متوقفة مؤقتًا، أو يلزم تحديث التطبيق.
 * زر الاتصال بالإسعاف ورقم الطوارئ يبقيان، فلا يُحرم الكبير من طلب المساعدة.
 */
@Composable
fun BlockScreen(block: AppControl.Block) {
    val context = LocalContext.current
    val open = rememberUrlOpener()
    Box(Modifier.fillMaxSize().clickable(enabled = true, indication = null, interactionSource = null) {}) {
        AppBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LogoView(96.dp)
                Spacer(Modifier.height(6.dp))
                if (block == AppControl.Block.UPDATE) {
                    Text2(tr("يتوفر تحديث لعافيتي", "An Afiati update is available"), 28, FontWeight.Bold)
                    Text2(tr("حدّث التطبيق ليستمر في العمل. بياناتك وأدويتك محفوظة ولن تتغير.", "Update the app to keep using it. Your data and medicines are saved and won't change."), 18, color = Brand.secondaryText)
                } else {
                    Text2(tr("الخدمة متوقفة مؤقتًا", "The service is paused"), 28, FontWeight.Bold)
                    Text2(tr("نعمل على تحسين عافيتي، وستعود قريبًا. تذكيرات أدويتك المجدولة تبقى تصلك.", "We're improving Afiati and will be back soon. Your scheduled medicine reminders keep coming."), 18, color = Brand.secondaryText)
                }
                WaveRibbon(Modifier.fillMaxWidth().height(110.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (block == AppControl.Block.UPDATE && AppControl.updateUrl.isNotEmpty()) {
                    PrimaryButton(tr("تحديث التطبيق", "Update the app"), icon = Icons.Filled.SystemUpdate) { open(AppControl.updateUrl) }
                }
                if (AppModel.emergencyPhone.isNotBlank()) {
                    GlassButton(tr("اتصال بـ ${AppModel.emergencyName.ifBlank { AppModel.emergencyPhone }}", "Call ${AppModel.emergencyName.ifBlank { AppModel.emergencyPhone }}"), icon = Icons.Filled.Call) {
                        dial(context, AppModel.emergencyPhone)
                    }
                }
                GlassButton(tr("اتصال بالإسعاف $AMBULANCE", "Call ambulance $AMBULANCE"), icon = CrescentIcon, color = Brand.danger) { dial(context, AMBULANCE) }
            }
        }
    }
}

@Composable
private fun Text2(text: String, size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Brand.text) {
    androidx.compose.material3.Text(text, style = tajawal(size, weight, color), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
