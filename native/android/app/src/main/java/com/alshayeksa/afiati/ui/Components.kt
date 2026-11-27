package com.alshayeksa.afiati.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** نافذة تأكيد أو تنبيه داكنة */
@Composable
fun AfiatiDialog(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    dismiss: String? = null,
    destructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.menu,
        title = { Text(title, style = tajawal(21, FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (text != null) Text(text, style = tajawal(17, color = Brand.text.copy(alpha = 0.78f)))
                content?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, style = tajawal(18, FontWeight.Bold, if (destructive) Brand.danger else Brand.glow))
            }
        },
        dismissButton = dismiss?.let {
            { TextButton(onClick = onDismiss) { Text(it, style = tajawal(18, color = Brand.text.copy(alpha = 0.75f))) } }
        },
    )
}

/** حقل نص زجاجي */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = tajawal(19),
    centered: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    ltr: Boolean = false,
) {
    val textStyle = if (centered) style.copy(textAlign = TextAlign.Center) else style
    val field = @Composable {
        BasicTextField(
            value, onValueChange,
            modifier = modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(Brand.glow),
            keyboardOptions = keyboard,
            decorationBox = { inner ->
                Box(Modifier.glass(RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 16.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder, style = textStyle.copy(color = Brand.text.copy(alpha = 0.38f)),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    inner()
                }
            },
        )
    }
    if (ltr) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { field() } else field()
}

/** قالب النوافذ السفلية: خلفية داكنة، عنوان، وزر إغلاق */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfiatiSheet(title: String?, onDismiss: () -> Unit, closeTitle: String = "تم", content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Brand.sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalTextStyle provides tajawal(18)) {
            Column(Modifier.fillMaxWidth().imePadding()) {
                if (title != null) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = tajawal(22, FontWeight.Bold), modifier = Modifier.weight(1f))
                        Text(
                            closeTitle, style = tajawal(18, FontWeight.Medium, Brand.glow),
                            modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = tajawal(15, color = Brand.secondaryText), modifier = modifier.padding(horizontal = 4.dp))
}

/** عنوان قسم داخل الصفحة */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = tajawal(22, FontWeight.Bold), modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** قائمة منسدلة داكنة */
@Composable
fun AfiatiMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    DropdownMenu(expanded, onDismissRequest = onDismiss, containerColor = Brand.menu, shape = RoundedCornerShape(18.dp)) { content() }
}

@Composable
fun MenuItem(text: String, icon: ImageVector, color: Color = Brand.text, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = tajawal(18, color = color)) },
        leadingIcon = { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) },
        onClick = onClick,
    )
}

/** خيار يظهر عند سحب بطاقة أو الضغط المطوّل عليها */
class RowAction(val label: String, val menuLabel: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit)

/**
 * بطاقة تُسحب نحو بداية السطر فتظهر خياراتها في طرفها الآخر، والضغط المطوّل يعرض الخيارات نفسها في قائمة.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeRow(actions: List<RowAction>, clickLabel: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val actionWidth = 76.dp
    val maxReveal = with(LocalDensity.current) { (actionWidth * actions.size + 6.dp).toPx() }
    val reveal = remember { Animatable(0f) }
    var menu by remember { mutableStateOf(false) }
    val close: () -> Unit = { scope.launch { reveal.animateTo(0f) } }

    Box(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).fillMaxWidth().height(IntrinsicSize.Min)) {
        if (actions.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().graphicsLayer { alpha = if (maxReveal > 0) reveal.value / maxReveal else 0f },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                actions.forEach { action ->
                    Column(
                        Modifier.width(actionWidth - 6.dp).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(action.tint)
                            .clickable(enabled = reveal.value > 0f, role = Role.Button) { close(); action.onClick() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(action.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Text(action.label, style = tajawal(13, FontWeight.Medium, Color.White), maxLines = 1)
                    }
                }
            }
        }
        Box(
            Modifier.absoluteOffset { IntOffset((if (rtl) reveal.value else -reveal.value).roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = actions.isNotEmpty(),
                    state = rememberDraggableState { delta ->
                        val next = (reveal.value + if (rtl) delta else -delta).coerceIn(0f, maxReveal)
                        scope.launch { reveal.snapTo(next) }
                    },
                    onDragStopped = { reveal.animateTo(if (reveal.value > maxReveal / 2) maxReveal else 0f) },
                )
                .clip(RoundedCornerShape(24.dp))
                .combinedClickable(
                    onClickLabel = clickLabel,
                    onClick = { if (reveal.value > 0f) close() else onClick() },
                    onLongClick = if (actions.isEmpty()) null else ({ menu = true }),
                )
        ) {
            content()
            AfiatiMenu(menu, onDismiss = { menu = false }) {
                actions.forEach { action ->
                    MenuItem(action.menuLabel, action.icon, if (action.tint == Brand.danger) Brand.danger else Brand.text) {
                        menu = false
                        action.onClick()
                    }
                }
            }
        }
    }
}

/** عدّاد كبير: زر ناقص، الرقم، زر زائد */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true, plusEnabled: Boolean = true, caption: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Filled.Add, "زيادة", plusEnabled, onPlus)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = tajawal(46, FontWeight.Bold), textAlign = TextAlign.Center)
            if (caption != null) Text(caption, style = tajawal(16, color = Brand.secondaryText), textAlign = TextAlign.Center)
        }
        StepButton(Icons.Filled.Remove, "إنقاص", minusEnabled, onMinus)
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).glass(CircleShape, if (enabled) 0.35f else 0.1f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) Brand.glow else Brand.text.copy(alpha = 0.25f), modifier = Modifier.size(34.dp))
    }
}

/** زر اختيار زجاجي، يضيء عند اختياره */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val background = if (selected) Modifier.background(Brush.verticalGradient(listOf(Brand.mint.copy(alpha = 0.8f), Brand.deepGreen.copy(alpha = 0.9f))), shape) else Modifier
    Row(
        modifier.clip(shape).then(background).glass(shape, if (selected) 0.6f else 0.22f)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Brand.text else Brand.glow, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = tajawal(18, if (selected) FontWeight.Bold else FontWeight.Medium, Brand.text), maxLines = 1)
    }
}

/** حلقة التقدم المضيئة */
@Composable
fun ProgressRing(progress: Float, size: Dp, stroke: Dp = 12.dp, center: @Composable () -> Unit) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "progress")
    val track = Brand.text.copy(alpha = 0.09f)
    val brush = Brush.sweepGradient(listOf(Brand.mint, Brand.glow, Brand.tone(0xC6FFE6), Brand.mint))
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().blur(6.dp, BlurredEdgeTreatment.Unbounded)) {
            val width = stroke.toPx()
            drawArc(brush, -90f, 360f * animated, false, style = Stroke(width * 1.6f, cap = StrokeCap.Round), alpha = 0.45f,
                    topLeft = androidx.compose.ui.geometry.Offset(width, width),
                    size = androidx.compose.ui.geometry.Size(this.size.width - width * 2, this.size.height - width * 2))
        }
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val topLeft = androidx.compose.ui.geometry.Offset(width / 2, width / 2)
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - width, this.size.height - width)
            drawArc(track, 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(width))
            if (animated > 0f) drawArc(brush, -90f, 360f * animated, false, topLeft = topLeft, size = arcSize, style = Stroke(width, cap = StrokeCap.Round))
        }
        center()
    }
}

/** بطاقة زجاجية فارغة برسالة وزر */
@Composable
fun EmptyCard(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = tajawal(21, FontWeight.Bold), textAlign = TextAlign.Center)
        Text(body, style = tajawal(17, color = Brand.secondaryText), textAlign = TextAlign.Center)
        if (action != null && onAction != null) PrimaryButton(action, Modifier.padding(top = 8.dp), onClick = onAction)
    }
}

/** نقطة ملونة ونص: حالة الحفظ والمزامنة */
@Composable
fun StatusLine(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape).border(2.dp, color.copy(alpha = 0.3f), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = tajawal(16, color = color))
    }
}

/** شاشة كاملة فوق التطبيق (إضافة دواء، متابعة قريب) */
@Composable
fun FullScreen(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().clickable(enabled = true, indication = null, interactionSource = null) {}) {
        AppBackground()
        Column(Modifier.fillMaxSize(), content = content)
    }
}
