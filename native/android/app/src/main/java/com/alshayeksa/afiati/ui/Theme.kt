package com.alshayeksa.afiati.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alshayeksa.afiati.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ألوان العلامة، بأسلوب «موجة»: خلفية شبه سوداء، أسطح زجاجية، ولون مضيء.
 * كل لون يُحسب من سمة المستخدم، فتغيير المظهر يعيد تلوين الواجهة فورًا.
 */
object Brand {
    val green get() = tone(0x13684F)
    val deepGreen get() = tone(0x0B4F3D)
    val mint get() = tone(0x22B085)
    val glow get() = tone(0x4FF0B0)
    val ink get() = tone(0x04241A)
    val text get() = tone(0xFFFFFF)
    val secondaryText get() = text.copy(alpha = 0.62f)
    val base get() = tone(0x020504)
    val sheet get() = tone(0x07110D)
    val menu get() = tone(0x0E1A16)
    val success = Color(0xFF2FBF71)
    val danger = Color(0xFFE5484D)
    val warning = Color(0xFFFFA62B)

    fun tone(rgb: Long): Color = Appearance.tone(rgb)

    private val palette = listOf(
        Color(0xFF1E8E6C), Color(0xFFB07A3F), Color(0xFF4D7FB5),
        Color(0xFF5B4B8A), Color(0xFFBD616E), Color(0xFF3F94A0),
    )

    /** لون ثابت لكل دواء أو شخص حسب معرّفه */
    fun avatarColor(id: String): Color = palette[Math.floorMod(id.sumOf { it.code }, palette.size)]
}

private val Tajawal = FontFamily(
    Font(R.font.tajawal_light, FontWeight.Light),
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

/** خط تجوال (ترخيص OFL). الأحجام أكبر من المعتاد لتناسب كبار السن، وتكبر أكثر مع «الخط الكبير». */
fun tajawal(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Brand.text): TextStyle {
    // العناوين الكبيرة (23 إلى 40) أهدأ قليلًا، والأرقام الضخمة (عدّاد، نبض) كما هي
    val base = if (size in 23..40) size * 0.87f else size.toFloat()
    val scaled = if (Appearance.largeText) base * 1.15f else base
    return TextStyle(
        fontFamily = Tajawal, fontSize = scaled.sp, fontWeight = weight, color = color,
        // مسافة أوسع بين السطور، موزعة فوق السطر وتحته، فلا تتلاصق الأسطر المتتالية
        lineHeight = (scaled * 1.6f).sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

/** سمة التطبيق: ألوان المظهر المختار، خط تجوال، واتجاه عربي من اليمين */
@Composable
fun AfiatiTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    SideEffect { Appearance.systemDark = systemDark }
    val scheme = if (Appearance.light) {
        lightColorScheme(primary = Brand.glow, onPrimary = Brand.ink, background = Brand.base, surface = Brand.sheet,
                         onBackground = Brand.text, onSurface = Brand.text, error = Brand.danger)
    } else {
        darkColorScheme(primary = Brand.glow, onPrimary = Brand.ink, background = Brand.base, surface = Brand.sheet,
                        onBackground = Brand.text, onSurface = Brand.text, error = Brand.danger)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalTextStyle provides tajawal(18),
            content = content,
        )
    }
}

/** خلفية التطبيق: أسود بلون السمة (أو لؤلؤي في الفاتح) مع إضاءة خفيفة من الأعلى */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(Brand.base).drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Brand.tone(0x061610), Color.Transparent),
                    center = Offset(size.width / 2, 0f), radius = 520.dp.toPx(),
                )
            )
        }
    )
}

/** سطح زجاجي: تعبئة شفافة وحد مضيء خافت */
fun Modifier.glass(shape: Shape, highlight: Float = 0.22f): Modifier =
    clip(shape).background(Brand.text.copy(alpha = 0.055f)).border(1.dp, Brand.glow.copy(alpha = highlight), shape)

/** الزر الرئيسي: مضيء بنص داكن، كبير وسهل اللمس */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(if (enabled) 12.dp else 0.dp, shape, ambientColor = Brand.glow, spotColor = Brand.glow)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Brand.glow, Brand.mint)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 17.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Brand.ink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = tajawal(19, FontWeight.Bold, Brand.ink), textAlign = TextAlign.Center)
    }
}

/** زر ثانوي زجاجي بنص مضيء */
@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Brand.glow,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).glass(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = tajawal(18, FontWeight.Medium, color), textAlign = TextAlign.Center)
    }
}

/** موجة العلامة: خطوط رفيعة مضيئة تلتف حول بعضها وتتحرك بهدوء، كنبض هادئ */
@Composable
fun WaveRibbon(modifier: Modifier = Modifier, animated: Boolean = true) {
    val colors = waveColors(Palette(Appearance.accent, Appearance.light))
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        val offset = phase
        val start = withFrameNanos { it }
        while (true) withFrameNanos { phase = offset + (it - start) / 1e9f * 0.22f }
    }
    Box(modifier.clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize().blur(7.dp, BlurredEdgeTreatment.Unbounded)) {
            drawWave(phase, colors, strokeWidth = 1.8.dp.toPx(), alpha = 0.16f)
        }
        Canvas(Modifier.fillMaxSize()) {
            drawWave(phase, colors, strokeWidth = 0.6.dp.toPx(), alpha = 0.6f)
        }
    }
}

fun waveColors(palette: Palette): Array<Pair<Float, Color>> = arrayOf(
    0f to palette.color(0x1FD38E), 0.42f to palette.color(0x7BFFC9), 0.55f to palette.color(0xF2FFF9),
    0.68f to palette.color(0x5BF0B4), 1f to palette.color(0x17B57A),
)

fun DrawScope.drawWave(phase: Float, waveColors: Array<Pair<Float, Color>>, strokeWidth: Float, alpha: Float) {
    val width = size.width
    val height = size.height
    if (width <= 0f) return
    // من اليمين إلى اليسار، مع اتجاه القراءة العربية
    val brush = Brush.horizontalGradient(*waveColors, startX = width, endX = 0f)
    val count = 30
    val step = 6.dp.toPx()
    val margin = 10.dp.toPx()
    val bulge = 14.dp.toPx()
    val path = Path()
    for (index in 0 until count) {
        val k = index.toFloat() / (count - 1) - 0.5f
        path.rewind()
        var x = -margin
        var first = true
        while (x <= width + margin) {
            val t = (width - x) / width
            val center = height * 0.50f + height * 0.26f * sin(t * 2 * PI.toFloat() * 0.95f + 0.4f + phase) - height * 0.06f * t
            val spread = height * 0.30f * sin(t * 2 * PI.toFloat() * 0.72f + 1.15f + phase * 0.6f) * (0.75f + 0.25f * cos(t * 5))
            val y = center + k * spread + k * k * bulge
            if (first) path.moveTo(x, y) else path.lineTo(x, y)
            first = false
            x += step
        }
        drawPath(path, brush, alpha = alpha, style = Stroke(strokeWidth))
    }
}

/** شعار عافيتي داخل مربع أبيض مستدير بتوهج خفيف */
@Composable
fun LogoView(size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.24f)
    Box(
        modifier.size(size).shadow(18.dp, shape, ambientColor = Brand.glow, spotColor = Brand.glow).clip(shape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.afiati_logo), contentDescription = "شعار عافيتي", modifier = Modifier.size(size * 0.92f))
    }
}

/** دائرة ملونة فيها الحرف الأول من الاسم */
@Composable
fun AvatarView(name: String, id: String, size: Dp = 48.dp, color: Color? = null) {
    val trimmed = name.trim()
    val initial = if (trimmed.isEmpty()) "" else trimmed.substring(0, trimmed.offsetByCodePoints(0, 1))
    Box(
        Modifier.size(size).background(color ?: Brand.avatarColor(id), CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = tajawal((size.value * 0.42f).toInt(), FontWeight.Medium, Color.White))
    }
}

/** دائرة ملونة فيها أيقونة (للأدوية والإشعارات) */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 48.dp, tint: Color = Color.White) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** يفتح رابطًا في المتصفح أو تطبيق آخر */
@Composable
fun rememberUrlOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    }
}
