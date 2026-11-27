package com.alshayeksa.afiati.ui

import com.alshayeksa.afiati.Lang
import com.alshayeksa.afiati.tr
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.snapshotFlow
import com.alshayeksa.afiati.MainActivity
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alshayeksa.afiati.data.Medication
import com.alshayeksa.afiati.sync.FamilyLink
import kotlinx.coroutines.delay

enum class Tab { TODAY, MEDICATIONS, HISTORY, FAMILY }

/** جذر الواجهة: الإعداد الأول، أو الشاشة الرئيسية */
@Composable
fun AfiatiRoot() {
    AfiatiTheme {
        Box(Modifier.fillMaxSize()) {
            AppBackground()
            when {
                !AppModel.loaded -> Unit
                AppModel.patient == null -> OnboardingScreen()
                else -> HomeScreen()
            }
            // إيقاف الخدمة أو تحديث إلزامي من صفحة التحكم: شاشة تغطي كل شيء
            com.alshayeksa.afiati.sync.AppControl.block?.let { BlockScreen(it) }
        }
    }
}

/**
 * الشاشة الرئيسية بأسلوب موجة: شريط علوي زجاجي، محتوى التبويب،
 * وفي الأسفل زر الإضافة المضيء وشريط التبويب العائم.
 */
@Composable
fun HomeScreen() {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showPulse by rememberSaveable { mutableStateOf(false) }
    var showVitals by rememberSaveable { mutableStateOf(false) }
    var openMedication by remember { mutableStateOf<Medication?>(null) }
    var openPatient by remember { mutableStateOf<FamilyLink?>(null) }

    // ضغطة على إشعار من العائلة
    val pendingOpen = AppModel.pendingOpen
    LaunchedEffect(pendingOpen) {
        when (pendingOpen) {
            "family" -> {
                showAdd = false; showPulse = false; showVitals = false; openPatient = null; showProfile = false; showSettings = false; showNotifications = false; showAbout = false; openMedication = null
                tab = Tab.FAMILY
                AppModel.familyFocus++
            }
            "help" -> {
                showAdd = false; showPulse = false; showVitals = false; openPatient = null; showProfile = false; showSettings = false; showNotifications = false; showAbout = false; openMedication = null
                AppModel.refreshHelp()
                // للكبير: حالة طلبه. لفرد العائلة: بطاقة الطلب أعلى الشاشة
                if (AppModel.myHelp != null) showHelp = true else tab = Tab.TODAY
            }
            "notifications" -> {
                showAdd = false; showPulse = false; showVitals = false; openPatient = null; showProfile = false; showSettings = false; showAbout = false; openMedication = null
                showNotifications = true
            }
            else -> if (pendingOpen?.startsWith(MainActivity.OPEN_PATIENT_PREFIX) == true) {
                // تنبيه جرعة فائتة أو مخزون: يفتح يوم هذا القريب مباشرة
                showAdd = false; showHelp = false; openPatient = null; showProfile = false; showSettings = false; showNotifications = false; showAbout = false; openMedication = null
                tab = Tab.FAMILY
                val patientId = pendingOpen.removePrefix(MainActivity.OPEN_PATIENT_PREFIX)
                AppModel.refreshFamily()
                val link = withTimeoutOrNull(10_000) {
                    snapshotFlow { AppModel.family?.following?.firstOrNull { it.patientId == patientId && it.status == "active" } }
                        .filterNotNull().first()
                }
                if (link != null) openPatient = link
            }
        }
        AppModel.pendingOpen = null
    }
    // منتصف الليل والتطبيق مفتوح، ومتابعة طلبات المساعدة الجارية
    LaunchedEffect(Unit) {
        AppModel.refreshHelp()
        var ticks = 0
        while (true) {
            delay(15_000)
            ticks++
            if (AppModel.myHelp != null || AppModel.familyHelp.isNotEmpty()) AppModel.refreshHelp()
            if (ticks % 2 == 0) AppModel.checkDay()
            // صفحة التحكم: كل دقيقتين، فتظهر أو تختفي شاشة الإيقاف دون إعادة فتح التطبيق
            if (ticks % 8 == 0) com.alshayeksa.afiati.sync.AppControl.refresh()
        }
    }
    BackHandler(enabled = tab != Tab.TODAY && !showAdd && !showHelp && !showPulse && !showVitals && openPatient == null) { tab = Tab.TODAY }

    val header: @Composable () -> Unit = {
        TopBar(onProfile = { showProfile = true }, onNotifications = { showNotifications = true }, onAbout = { showAbout = true }, onSettings = { showSettings = true })
    }
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            Tab.TODAY -> TodayScreen(header, onAdd = { showAdd = true }, onOpenMedication = { openMedication = it }, onHelp = { showHelp = true }, onVitals = { showVitals = true })
            Tab.MEDICATIONS -> MedicationsScreen(header, onAdd = { showAdd = true }, onOpen = { openMedication = it })
            Tab.HISTORY -> HistoryScreen(header)
            Tab.FAMILY -> FamilyScreen(header, onOpenPatient = { openPatient = it }, onHelp = { showHelp = true })
        }
        // تدرّج خلف شريط التبويب وأزرار النظام، فلا يتداخل المحتوى معها
        val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(navigation + 110.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Brand.base.copy(alpha = 0.85f), Brand.base)))
        )
        BottomBar(
            tab, onTab = { tab = it }, onAdd = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
        )
        openPatient?.let { link ->
            PatientScreen(link, onClose = { openPatient = null })
        }
        if (showAdd) {
            AddMedicationScreen(onClose = { showAdd = false })
        }
        if (showVitals) {
            VitalsScreen(onClose = { showVitals = false }, onPulse = { showPulse = true })
        }
        if (showPulse) {
            PulseScreen(onClose = { showPulse = false })
        }
        if (showHelp) {
            HelpScreen(onClose = { showHelp = false })
        }
        UndoBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp))
    }

    if (!showAdd && !showHelp && !showPulse && !showVitals && openPatient == null) {
        when {
            showProfile -> ProfileSheet(onDismiss = { showProfile = false })
            showSettings -> SettingsSheet(onDismiss = { showSettings = false })
            showNotifications -> NotificationsSheet(onDismiss = { showNotifications = false })
            showAbout -> AboutSheet(onDismiss = { showAbout = false })
            openMedication != null -> MedicationSheet(openMedication!!, onDismiss = { openMedication = null })
        }
    }
}

/** قائمة تبويب: الشريط العلوي ثم المحتوى، مع مساحة لشريط التبويب السفلي */
@Composable
fun TabList(header: @Composable () -> Unit, content: LazyListScope.() -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 6.dp, bottom = 150.dp)) {
        item(key = "top") { header() }
        // طلبات مساعدة ممن تتابعهم: أعلى كل تبويب حتى يرد أحد
        items(AppModel.familyHelp, key = { "help-" + it.id }) { FamilyHelpCard(it) }
        content()
    }
}

/** زر اللغة في الشريط العلوي: «E» يحوّل إلى الإنجليزية، و«ع» يعيد العربية */
@Composable
private fun LanguageButton() {
    val context = LocalContext.current
    Box(
        Modifier.clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = tr("English", "العربية")) { AppModel.setLanguage(context, !Lang.english) }
            .padding(horizontal = 9.dp, vertical = 6.dp).size(width = 24.dp, height = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (Lang.english) "ع" else "E", style = tajawal(19, FontWeight.Bold, Brand.glow))
    }
}

/** عنوان كل تبويب: واضح دون أن يطغى على المحتوى، وتحته سطر وصف واحد */
@Composable
fun BigTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = tajawal(26, FontWeight.Bold))
        subtitle?.let { Text(it, style = tajawal(15, color = Brand.secondaryText)) }
    }
}

@Composable
private fun TopBar(onProfile: () -> Unit, onNotifications: () -> Unit, onAbout: () -> Unit, onSettings: () -> Unit) {
    val patient = AppModel.patient
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        // اسم المستخدم: يفتح ملفه
        Row(
            Modifier.glass(CircleShape).clickable(role = Role.Button, onClickLabel = tr("ملفي", "My profile"), onClick = onProfile)
                .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarView(patient?.fullName.orEmpty(), patient?.id.orEmpty(), size = 32.dp, color = Brand.green)
            Spacer(Modifier.width(8.dp))
            Text(patient?.fullName.orEmpty(), style = tajawal(16, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis,
                 modifier = Modifier.widthIn(max = 150.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.glass(CircleShape).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Icon(
                    Icons.Outlined.Notifications, tr("الإشعارات", "Notifications"), tint = Brand.glow,
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onNotifications).padding(9.dp).size(24.dp),
                )
                if (AppModel.unreadCount > 0) {
                    Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(10.dp).background(Brand.danger, CircleShape).border(1.5.dp, Brand.base, CircleShape))
                }
            }
            Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(18.dp).background(Brand.text.copy(alpha = 0.2f)))
            Icon(
                Icons.Outlined.Info, tr("عن التطبيق", "About"), tint = Brand.text,
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onAbout).padding(9.dp).size(24.dp),
            )
            Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(18.dp).background(Brand.text.copy(alpha = 0.2f)))
            LanguageButton()
            Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(18.dp).background(Brand.text.copy(alpha = 0.2f)))
            Icon(
                Icons.Filled.Settings, tr("الإعدادات", "Settings"), tint = Brand.text,
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onSettings).padding(9.dp).size(24.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.linearGradient(listOf(Brand.tone(0xC6FFE6), Brand.glow, Brand.mint)), blendMode = BlendMode.SrcIn)
                    },
            )
        }
    }
}

@Composable
private fun BottomBar(tab: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(CircleShape).background(Brand.tone(0x0A1512).copy(alpha = 0.92f)).glass(CircleShape).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TabButton(tr("اليوم", "Today"), Icons.Filled.Today, tab == Tab.TODAY, Modifier.weight(1f)) { onTab(Tab.TODAY) }
            TabButton(tr("أدويتي", "Medicines"), Icons.Filled.Medication, tab == Tab.MEDICATIONS, Modifier.weight(1f)) { onTab(Tab.MEDICATIONS) }
            TabButton(tr("السجل", "History"), Icons.Filled.BarChart, tab == Tab.HISTORY, Modifier.weight(1f)) { onTab(Tab.HISTORY) }
            TabButton(tr("العائلة", "Family"), Icons.Filled.Diversity1, tab == Tab.FAMILY, Modifier.weight(1f), badge = (AppModel.family?.pending?.size ?: 0) > 0) { onTab(Tab.FAMILY) }
        }
        val density = LocalDensity.current
        Box(
            Modifier.size(62.dp)
                .shadow(14.dp, CircleShape, ambientColor = Brand.glow, spotColor = Brand.glow)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Brand.tone(0x7BFFC9), Brand.tone(0x1FD38E), Brand.tone(0x0B6B4E)),
                        center = with(density) { Offset(62.dp.toPx() * 0.35f, 62.dp.toPx() * 0.3f) },
                        radius = with(density) { 50.dp.toPx() },
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                .clickable(role = Role.Button, onClickLabel = tr("إضافة دواء", "Add medicine"), onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, tr("إضافة دواء", "Add medicine"), tint = Brand.ink, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun TabButton(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier, badge: Boolean = false, onClick: () -> Unit) {
    val tint = if (selected) Brand.text else Brand.text.copy(alpha = 0.6f)
    val background = if (selected) {
        Modifier.background(Brush.verticalGradient(listOf(Brand.mint.copy(alpha = 0.75f), Brand.deepGreen.copy(alpha = 0.85f))))
    } else Modifier
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(CircleShape).then(background).clickable(role = Role.Tab, onClick = onClick).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Text(title, style = tajawal(13, FontWeight.Medium, tint), maxLines = 1, textAlign = TextAlign.Center)
        }
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 12.dp).size(10.dp).background(Brand.warning, CircleShape))
    }
}

/** شريط «سُجّلت الجرعة · تراجع»، والرسائل القصيرة */
@Composable
private fun UndoBar(modifier: Modifier = Modifier) {
    val pending = AppModel.pendingTake
    val toast = AppModel.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3500)
            AppModel.toast = null
        }
    }
    val visible = pending != null || toast != null
    AnimatedVisibility(visible, modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp)).background(Brand.menu)
                .border(1.dp, Brand.glow.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pending != null) {
                Text(tr("سُجّلت جرعة ${pending.medicationName}", "${pending.medicationName} dose logged"), style = tajawal(17, FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 2)
                Text(
                    tr("تراجع", "Undo"), style = tajawal(18, FontWeight.Bold, Brand.glow),
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) { AppModel.undoTake() }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            } else if (toast != null) {
                Text(toast, style = tajawal(17, FontWeight.Medium), modifier = Modifier.weight(1f))
            }
        }
    }
}
