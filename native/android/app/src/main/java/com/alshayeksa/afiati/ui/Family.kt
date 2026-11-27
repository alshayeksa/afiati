package com.alshayeksa.afiati.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.alshayeksa.afiati.sync.FamilyApi
import com.alshayeksa.afiati.sync.FamilyLink
import com.alshayeksa.afiati.sync.Invitation
import com.alshayeksa.afiati.sync.PatientDay
import com.alshayeksa.afiati.sync.Privacy
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun FamilyScreen(header: @Composable () -> Unit, onOpenPatient: (FamilyLink) -> Unit) {
    var following by rememberSaveable { mutableStateOf(false) }
    var showInvite by rememberSaveable { mutableStateOf(false) }
    var showJoin by rememberSaveable { mutableStateOf(false) }
    var showApproval by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { AppModel.refreshFamily() }
    // فُتحت من إشعار طلب متابعة: نغلق النوافذ ونعرض الطلب حين يصل
    val focus = AppModel.familyFocus
    var awaitingRequest by remember { mutableStateOf(false) }
    LaunchedEffect(focus) {
        if (focus == 0) return@LaunchedEffect
        showInvite = false; showJoin = false; following = false
        awaitingRequest = true
        AppModel.refreshFamily()
    }
    val pendingCount = AppModel.family?.pending?.size ?: 0
    LaunchedEffect(pendingCount, awaitingRequest, AppModel.familyLoading) {
        if (awaitingRequest && !AppModel.familyLoading && AppModel.family != null) {
            if (pendingCount > 0) showApproval = true
            awaitingRequest = false
        }
    }
    val family = AppModel.family
    val error = AppModel.familyError

    TabList(header) {
        item(key = "title") {
            BigTitle("العائلة") {
                Text("اطمئنان متبادل بموافقتك، وأنت تختار ما يرونه.", style = tajawal(17, color = Brand.secondaryText))
            }
        }
        item(key = "switch") {
            Row(
                Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp).fillMaxWidth().glass(CircleShape).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Segment("يتابعونني", !following, Modifier.weight(1f)) { following = false }
                Segment("أتابعهم", following, Modifier.weight(1f)) { following = true }
            }
        }
        if (error != null && family == null) {
            item(key = "error") { OfflineCard(error) }
        }
        val pending = family?.pending.orEmpty()
        if (pending.isNotEmpty()) {
            item(key = "pending") {
                Row(
                    Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)).background(Brand.warning.copy(alpha = 0.13f))
                        .border(1.dp, Brand.warning.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button) { showApproval = true }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PersonAdd, null, tint = Brand.warning, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (pending.size == 1) "${pending[0].name} يريد متابعتك" else "${pending.size} طلبات متابعة", style = tajawal(19, FontWeight.Bold))
                        Text("اضغط للموافقة واختيار ما يراه", style = tajawal(15, color = Brand.secondaryText))
                    }
                }
            }
        }
        if (family != null) {
            val list = if (following) family.following else family.followers
            if (list.isEmpty()) {
                item(key = "empty-$following") {
                    EmptyCard(
                        if (following) "لا تتابع أحدًا بعد" else "لا أحد يتابعك بعد",
                        if (following) "اطلب من قريبك رمز الدعوة من تطبيقه، ثم أدخله هنا لتطمئن على أدويته."
                        else "أرسل رمز دعوة لابنك أو ابنتك، فيطمئنون عليك ويصلهم تنبيه إذا فاتتك جرعة.",
                    )
                }
            } else {
                items(list, key = { (if (following) "f-" else "r-") + it.id }) { link ->
                    PersonRow(link, following) { if (following) onOpenPatient(link) }
                }
            }
        } else if (error == null) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand.glow) }
            }
        }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 14.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (following) PrimaryButton("متابعة قريب برمز", icon = Icons.Filled.PersonAdd) { showJoin = true }
                else PrimaryButton("دعوة فرد من العائلة", icon = Icons.Filled.Share) { showInvite = true }
                GlassButton("تحديث", icon = Icons.Filled.Refresh, enabled = !AppModel.familyLoading) { AppModel.refreshFamily() }
            }
        }
    }

    when {
        showInvite -> InviteSheet(onDismiss = { showInvite = false })
        showJoin -> JoinSheet(onDismiss = { showJoin = false })
        showApproval && pendingFirst() != null -> ApprovalSheet(pendingFirst()!!, onDismiss = { showApproval = false })
    }
}

private fun pendingFirst(): FamilyLink? = AppModel.family?.pending?.firstOrNull()

@Composable
private fun Segment(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val background = if (selected) Modifier.background(Brush.verticalGradient(listOf(Brand.mint.copy(alpha = 0.75f), Brand.deepGreen.copy(alpha = 0.85f)))) else Modifier
    Box(
        modifier.clip(CircleShape).then(background).clickable(role = Role.Tab, onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = tajawal(18, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) Brand.text else Brand.secondaryText))
    }
}

@Composable
private fun OfflineCard(message: String) {
    Row(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WifiOff, null, tint = Brand.warning, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, style = tajawal(17), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PersonRow(link: FamilyLink, following: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp))
            .clickable(enabled = following, role = Role.Button, onClickLabel = "متابعة ${link.name}", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarView(link.name, link.id, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(link.name, style = tajawal(20, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (following) "اضغط لترى أدويته اليوم" else "يرى: ${Privacy.title(link.privacyLevel)}",
                style = tajawal(15, color = Brand.secondaryText),
            )
        }
        if (following) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Brand.glow, modifier = Modifier.size(24.dp))
    }
}

/** دعوة فرد من العائلة: رمز من 6 أرقام صالح 24 ساعة، مع رمز QR ومشاركة */
@Composable
fun InviteSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var invitation by remember { mutableStateOf<Invitation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    fun generate() {
        loading = true
        scope.launch {
            AppModel.familyCall { FamilyApi.createInvitation(it) }
                .onSuccess { invitation = it; error = null }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { generate() }
    val code = invitation?.code
    val name = AppModel.patient?.fullName.orEmpty()
    val message = "السلام عليكم، أدعوك لمتابعة أدويتي في تطبيق عافيتي لتطمئن علي.\nرمز الدعوة: $code\n(صالح لمدة 24 ساعة)"

    AfiatiSheet("دعوة فرد من العائلة", onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "أرسل الرمز لابنك أو ابنتك. يفتح عافيتي في جواله ويدخله في «العائلة ← أتابعهم»، ثم توافق أنت وتختار ما يراه.",
                style = tajawal(17, color = Brand.secondaryText),
            )
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    code != null -> {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                code.forEach { digit ->
                                    Box(Modifier.size(width = 44.dp, height = 58.dp).glass(RoundedCornerShape(14.dp), 0.45f), contentAlignment = Alignment.Center) {
                                        Text("$digit", style = tajawal(30, FontWeight.Bold, Brand.glow))
                                    }
                                }
                            }
                        }
                        Text("صالح لمدة 24 ساعة · ${name.ifEmpty { "رمزك" }}", style = tajawal(15, color = Brand.secondaryText))
                    }
                    error != null -> Text(error!!, style = tajawal(17, color = Brand.warning), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                    else -> CircularProgressIndicator(color = Brand.glow)
                }
            }
            if (code != null) {
                PrimaryButton("مشاركة الرمز", icon = Icons.Filled.Share) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                    runCatching { context.startActivity(Intent.createChooser(send, "مشاركة رمز الدعوة")) }
                }
                GlassButton(if (copied) "تم النسخ" else "نسخ الرمز", icon = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy) {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("عافيتي", code))
                    copied = true
                }
                if (copied) LaunchedEffect(Unit) { delay(2000); copied = false }
                val qr = remember(code) { qrImage(code) }
                if (qr != null) {
                    FieldLabel("أو امسح الرمز بكاميرا الجوال الآخر", Modifier.padding(top = 6.dp))
                    Image(
                        qr, "رمز QR للدعوة", filterQuality = FilterQuality.None,
                        modifier = Modifier.align(Alignment.CenterHorizontally).size(200.dp).background(Color.White, RoundedCornerShape(18.dp)).padding(12.dp),
                    )
                }
            }
            GlassButton("رمز جديد", icon = Icons.Filled.Refresh, enabled = !loading) { generate() }
        }
    }
}

private fun qrImage(text: String): ImageBitmap? = runCatching {
    val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix.get(index % matrix.width, index / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()

/** إدخال رمز دعوة من قريب */
@Composable
fun JoinSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    // لوحة الأرقام تظهر وحدها عند فتح النافذة، فلا يحتاج الكبير للبحث عن مكان الكتابة
    val codeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(350)
        runCatching { codeFocus.requestFocus() }
    }

    fun submit() {
        if (code.length != 6) { error = "أدخل الأرقام الستة كاملة."; return }
        sending = true
        scope.launch {
            AppModel.familyCall { FamilyApi.join(it, code) }
                .onSuccess { result ->
                    val name = result.patientName.ifEmpty { "صاحب الدعوة" }
                    done = if (result.status == "active") "أنت تتابع $name مسبقًا." else "أُرسل طلبك. بانتظار موافقة $name."
                    AppModel.refreshFamily()
                }
                .onFailure { error = it.message }
            sending = false
        }
    }

    AfiatiSheet("متابعة قريب", onDismiss, closeTitle = if (done != null) "تم" else "إلغاء") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (done != null) {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Brand.glow, Brand.mint))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, null, tint = Brand.ink, modifier = Modifier.size(40.dp))
                    }
                    Text(done!!, style = tajawal(20, FontWeight.Bold), textAlign = TextAlign.Center)
                }
                PrimaryButton("حسنًا", onClick = onDismiss)
                return@Column
            }
            Text("اكتب رمز الدعوة المكوّن من 6 أرقام الذي أرسله لك قريبك.", style = tajawal(17, color = Brand.secondaryText))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BasicTextField(
                    code, { value -> code = value.filter(Char::isDigit).take(6); error = null },
                    modifier = Modifier.fillMaxWidth().focusRequester(codeFocus),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = tajawal(1, color = Color.Transparent),
                    decorationBox = { inner ->
                        Box {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                                repeat(6) { index ->
                                    val char = code.getOrNull(index)
                                    val active = index == code.length
                                    Box(
                                        Modifier.size(width = 48.dp, height = 64.dp).glass(RoundedCornerShape(16.dp), if (active) 0.7f else 0.25f),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(char?.toString() ?: "", style = tajawal(32, FontWeight.Bold, Brand.glow)) }
                                }
                            }
                            Box(Modifier.size(1.dp)) { inner() }
                        }
                    },
                )
            }
            error?.let { Text(it, style = tajawal(17, FontWeight.Medium, Brand.danger)) }
            PrimaryButton(if (sending) "جارٍ الإرسال…" else "إرسال طلب المتابعة", enabled = !sending && code.length == 6, icon = Icons.Filled.PersonAdd) { submit() }
        }
    }
}

/** الموافقة على طلب متابعة واختيار مستوى الخصوصية */
@Composable
fun ApprovalSheet(request: FamilyLink, onDismiss: () -> Unit) {
    var level by remember(request.id) { mutableStateOf(Privacy.FULL) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun answer(approve: Boolean) {
        sending = true
        AppModel.respond(request.caregiverId, approve, level) { message ->
            sending = false
            if (message == null) {
                AppModel.toast = if (approve) "${request.name} يتابعك الآن" else "رُفض الطلب"
                onDismiss()
            } else error = message
        }
    }

    AfiatiSheet(null, onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarView(request.name, request.id, size = 80.dp)
            Text("${request.name} يريد متابعة أدويتك", style = tajawal(24, FontWeight.Bold), textAlign = TextAlign.Center)
            Text("اختر ما يراه من أدويتك.", style = tajawal(17, color = Brand.secondaryText))
        }
        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Privacy.FULL, Privacy.STATUS_ONLY, Privacy.ALERTS_ONLY).forEach { option ->
                val selected = level == option
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), if (selected) 0.7f else 0.2f)
                        .clickable(role = Role.RadioButton) { level = option }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(26.dp).border(2.dp, if (selected) Brand.glow else Brand.text.copy(alpha = 0.3f), CircleShape).padding(5.dp)
                            .background(if (selected) Brand.glow else Color.Transparent, CircleShape)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Privacy.title(option), style = tajawal(19, FontWeight.Bold))
                        Text(Privacy.body(option), style = tajawal(15, color = Brand.secondaryText))
                    }
                }
            }
            error?.let { Text(it, style = tajawal(17, FontWeight.Medium, Brand.danger)) }
            PrimaryButton("موافق", icon = Icons.Filled.Check, enabled = !sending) { answer(true) }
            GlassButton("رفض", color = Brand.danger, enabled = !sending) { answer(false) }
        }
    }
}

/** شاشة متابعة قريب: أدويته اليوم حسب ما سمح به، وزر تذكير لطيف */
@Composable
fun PatientScreen(link: FamilyLink, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf<PatientDay?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reminding by remember { mutableStateOf(false) }
    val now = rememberNow()
    fun load() {
        loading = true
        scope.launch {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            AppModel.familyCall { FamilyApi.patientDay(it, link.patientId, today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant()) }
                .onSuccess { day = it; error = null }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(link.patientId) { load() }
    BackHandler(onBack = onClose)

    FullScreen {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = top + 10.dp, bottom = 24.dp)) {
            item(key = "bar") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).glass(CircleShape).clickable(role = Role.Button, onClickLabel = "رجوع", onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Brand.text, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(48.dp).glass(CircleShape).clickable(enabled = !loading, role = Role.Button, onClickLabel = "تحديث") { load() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Refresh, "تحديث", tint = Brand.glow, modifier = Modifier.size(24.dp)) }
                }
            }
            item(key = "title") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 10.dp)) {
                    Text("أدوية اليوم", style = tajawal(18, FontWeight.Medium, Brand.glow))
                    Text(day?.patientName ?: link.name, style = tajawal(38, FontWeight.Bold))
                    Text(Format.gregorianDay(), style = tajawal(16, color = Brand.secondaryText))
                }
            }
            val current = day
            when {
                current == null && error != null -> item(key = "error") { OfflineCard(error!!) }
                current == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand.glow) }
                }
                current.privacyLevel == Privacy.ALERTS_ONLY -> item(key = "alerts") {
                    EmptyCard("تنبيهات التأخير فقط", "اختار ${link.name} أن تصلك تنبيهات عند فوات جرعة، دون عرض تفاصيل أدويته.")
                }
                else -> {
                    val logs = current.logs
                    val done = logs.count { it.status == "taken" }
                    item(key = "progress") {
                        Row(
                            Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp).fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("التزامه اليوم", style = tajawal(17, color = Brand.secondaryText))
                                Text(if (logs.isEmpty()) "لا جرعات اليوم" else "أخذ $done من ${logs.size}", style = tajawal(24, FontWeight.Bold))
                            }
                            ProgressRing(if (logs.isEmpty()) 0f else done.toFloat() / logs.size, 96.dp) {
                                Text(if (logs.isEmpty()) "—" else "${done * 100 / logs.size}٪", style = tajawal(22, FontWeight.Bold))
                            }
                        }
                    }
                    items(logs, key = { "p-" + it.id }) { log ->
                        val shown = if (current.privacyLevel == Privacy.STATUS_ONLY || log.medicationName.isEmpty()) log.copy(medicationName = "جرعة دواء") else log
                        DoseRow(shown, doseState(log, now), onTake = null) {}
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            PrimaryButton(if (reminding) "جارٍ الإرسال…" else "إرسال تذكير لطيف", icon = Icons.Filled.NotificationsActive, enabled = !reminding) {
                reminding = true
                scope.launch {
                    AppModel.familyCall { FamilyApi.remind(it, link.patientId) }
                        .onSuccess { AppModel.toast = "وصل تذكيرك إلى ${link.name}" }
                        .onFailure { AppModel.toast = it.message }
                    reminding = false
                }
            }
        }
    }
}
