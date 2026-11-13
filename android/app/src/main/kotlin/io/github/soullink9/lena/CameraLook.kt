package io.github.soullink9.lena

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date

/**
 * Camera mode's look: a security monitor's. Pictures edge to edge on black with thin dividers, and over
 * each one the on-screen display (OSD) a CCTV recorder draws: its channel and name in the top left, the
 * date and time in the top right, in a monospaced face with a dark outline so it reads over any picture.
 * A blinking red dot marks what's live; amber marks motion, always with a word beside it, never by color
 * alone. Faint scan lines and darker corners give the screen its monitor feel without hiding the picture.
 * Shared by camera mode ([CameraActivity]) and its card on the TV home ([TvHome]).
 */
object CameraLook {
    /** The monitor's black, and the dividers between pictures. */
    val screen = Color(0xFF050607)
    val divider = Color(0xFF1B1E21)

    /** A picture with nothing in it yet. */
    val emptyPicture = Color(0xFF0C0F11)

    /** The OSD's white, a little warm, as a recorder's text is. */
    val osd = Color(0xFFF2F2EC)
    val osdDim = Color(0xFFA9ABA6)

    /** Live and recording. */
    val live = Color(0xFFFF3B30)

    /** Motion, and anything waiting (connecting again). */
    val motion = Color(0xFFFFB300)

    /** The OSD's text: monospaced, with a dark outline (a shadow) so it reads over a bright picture. */
    fun text(size: TextUnit = 13.sp, color: Color = osd, bold: Boolean = false) = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontSize = size,
        letterSpacing = 1.sp,
        color = color,
        shadow = Shadow(Color.Black, Offset(1f, 1f), blurRadius = 3f),
    )

    /** A camera's channel, as a recorder numbers them: CH01 for the first included camera. */
    fun channel(index: Int): String = "CH%02d".format(index + 1)

    /** The OSD's date: "THU NOV 12". */
    fun date(now: Long): String = DateFormat.format("EEE MMM d", Date(now)).toString().uppercase()

    /** The OSD's time, to the second, in the device's 12- or 24-hour clock: "7:49:03 PM" or "19:49:03". */
    fun time(c: Context, now: Long): String =
        DateFormat.format(if (DateFormat.is24HourFormat(c)) "HH:mm:ss" else "h:mm:ss a", Date(now)).toString().uppercase()

    /** A red dot that blinks once a second, as a recorder's REC light does, with [label] beside it. */
    @Composable
    fun LiveDot(label: String, color: Color = live, size: TextUnit = 13.sp) {
        val blink by rememberInfiniteTransition(label = "live").animateFloat(
            initialValue = 1f, targetValue = 0.15f,
            animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
            label = "blink",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(9.dp).alpha(blink).background(color, CircleShape))
            Text(label, style = text(size, color, bold = true))
        }
    }

    /** A word on a dark plate, for what needs noticing over a picture: MOTION, SELECTED. */
    @Composable
    fun Tag(label: String, color: Color, size: TextUnit = 11.sp) {
        Text(
            label,
            style = text(size, color, bold = true),
            modifier = Modifier.background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Faint horizontal lines over what's drawn, every [spacing], as on a monitor's screen, and darker corners.
 * Drawn on top, so it covers a picture without changing it; light enough not to hide anything.
 */
fun Modifier.monitorGlass(spacing: Dp = 3.dp, lines: Float = 0.10f, corners: Float = 0.45f): Modifier = drawWithContent {
    drawContent()
    val step = spacing.toPx().coerceAtLeast(2f)
    var y = 0f
    while (y < size.height) {
        drawLine(Color.Black.copy(alpha = lines), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
    drawRect(
        Brush.radialGradient(
            0.6f to Color.Transparent,
            1f to Color.Black.copy(alpha = corners),
            center = center,
            radius = size.maxDimension * 0.75f,
        )
    )
}
