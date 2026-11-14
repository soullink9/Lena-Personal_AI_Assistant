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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date

/**
 * Camera mode's look, after UniFi Protect's live view: the pictures fill the screen edge to edge on black,
 * with hairline gaps, and nothing else takes room. Each picture carries only a small label in its corner
 * (its name, and Motion while it sees motion); the bar with the views and the time floats over the
 * pictures and fades away when nobody's touching anything. Blue marks where the remote is, orange marks
 * motion, red marks live, always with a word beside the color, never color alone. Shared by camera mode
 * ([CameraActivity]) and its card on the TV home ([TvHome]).
 */
object CameraLook {
    /** The screen behind the pictures, and the hairlines between them. */
    val screen = Color(0xFF000000)

    /** A picture with nothing in it yet. */
    val emptyPicture = Color(0xFF16171A)

    /** The labels' plate over a picture. */
    val plate = Color(0x99000000)

    val text = Color(0xFFFFFFFF)
    val textDim = Color(0xFFA1A6AD)

    /** Where the remote is, and the view that's on (UniFi's blue). */
    val focus = Color(0xFF006FFF)

    /** Motion, and anything waiting (connecting again). */
    val motion = Color(0xFFF5A623)

    /** Live. */
    val live = Color(0xFFF03A47)

    /** The gap between pictures: a hairline, as in Protect's live view. */
    val gap = 2.dp

    /** A label's text. */
    fun labelStyle(size: TextUnit = 12.sp, color: Color = text, bold: Boolean = false) = TextStyle(
        fontSize = size,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
        color = color,
    )

    /** The time in the bar, to the minute, in the device's 12- or 24-hour clock: "7:49 PM" or "19:49". */
    fun time(c: Context, now: Long): String = DateFormat.getTimeFormat(c).format(Date(now))

    /** The date in the bar: "Sat, Nov 14". */
    fun date(now: Long): String = DateFormat.format("EEE, MMM d", Date(now)).toString()

    /**
     * A small label on a dark plate, in a picture's corner: [label], with a dot of [dot] before it when
     * given; the dot blinks when [blink] (live).
     */
    @Composable
    fun Pill(label: String, dot: Color? = null, blink: Boolean = false, size: TextUnit = 12.sp, modifier: Modifier = Modifier) {
        Row(
            modifier.background(plate, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dot != null) {
                val shown = if (blink) {
                    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
                        initialValue = 1f, targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
                        label = "pulse",
                    )
                    pulse
                } else 1f
                Box(Modifier.size(7.dp).alpha(shown).background(dot, CircleShape))
            }
            Text(label, style = labelStyle(size), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
