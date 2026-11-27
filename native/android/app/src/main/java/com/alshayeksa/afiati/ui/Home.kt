package com.alshayeksa.afiati.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
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
    var openMedication by remember { mutableStateOf<Medication?>(null) }
    var openPatient by remember { mutableStateOf<FamilyLink?>(null) }

    // منتصف الليل والتطبيق مفتوح
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            AppModel.checkDay()
        }
    }
    BackHandler(enabled = tab != Tab.TODAY && !showAdd && openPatient == null) { tab = Tab.TODAY }

    val header: @Composable () -> Unit = {
        TopBar(onProfile = { showProfile = true }, onNotifications = { showNotifications = true }, onSettings = { showSettings = true })
    }
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            Tab.TODAY -> TodayScreen(header, onAdd = { showAdd = true }, onOpenMedication = { openMedication = it })
            Tab.MEDICATIONS -> MedicationsScreen(header, onAdd = { showAdd = true }, onOpen = { openMedication = it })
            Tab.HISTORY -> HistoryScreen(header)
            Tab.FAMILY -> FamilyScreen(header, onOpenPatient = { openPatient = it })
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
        UndoBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp))
    }

    if (!showAdd && openPatient == null) {
        when {
            showProfile -> ProfileSheet(onDismiss = { showProfile = false })
            showSettings -> SettingsSheet(onDismiss = { showSettings = false })
            showNotifications -> NotificationsSheet(onDismiss = { showNotifications = false })
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
        content()
    }
}

/** عنوان كبير لكل تبويب، مثل اسم التطبيق في موجة */
@Composable
fun BigTitle(title: String, subtitle: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 14.dp)) {
        Text(title, style = tajawal(40, FontWeight.Bold))
        subtitle?.invoke()
    }
}

@Composable
private fun TopBar(onProfile: () -> Unit, onNotifications: () -> Unit, onSettings: () -> Unit) {
    val patient = AppModel.patient
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        // اسم المستخدم: يفتح ملفه
        Row(
            Modifier.glass(CircleShape).clickable(role = Role.Button, onClickLabel = "ملفي", onClick = onProfile)
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
                    Icons.Outlined.Notifications, "الإشعارات", tint = Brand.glow,
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onNotifications).padding(9.dp).size(24.dp),
                )
                if (AppModel.unreadCount > 0) {
                    Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(10.dp).background(Brand.danger, CircleShape).border(1.5.dp, Brand.base, CircleShape))
                }
            }
            Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(18.dp).background(Brand.text.copy(alpha = 0.2f)))
            Icon(
                Icons.Filled.Settings, "الإعدادات", tint = Brand.text,
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
            TabButton("اليوم", Icons.Filled.Today, tab == Tab.TODAY, Modifier.weight(1f)) { onTab(Tab.TODAY) }
            TabButton("أدويتي", Icons.Filled.Medication, tab == Tab.MEDICATIONS, Modifier.weight(1f)) { onTab(Tab.MEDICATIONS) }
            TabButton("السجل", Icons.Filled.BarChart, tab == Tab.HISTORY, Modifier.weight(1f)) { onTab(Tab.HISTORY) }
            TabButton("العائلة", Icons.Filled.Diversity1, tab == Tab.FAMILY, Modifier.weight(1f), badge = (AppModel.family?.pending?.size ?: 0) > 0) { onTab(Tab.FAMILY) }
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
                .clickable(role = Role.Button, onClickLabel = "إضافة دواء", onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, "إضافة دواء", tint = Brand.ink, modifier = Modifier.size(34.dp))
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
                Text("سُجّلت جرعة ${pending.medicationName}", style = tajawal(17, FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 2)
                Text(
                    "تراجع", style = tajawal(18, FontWeight.Bold, Brand.glow),
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) { AppModel.undoTake() }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            } else if (toast != null) {
                Text(toast, style = tajawal(17, FontWeight.Medium), modifier = Modifier.weight(1f))
            }
        }
    }
}
