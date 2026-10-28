package io.github.soullink9.lena

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date
import kotlin.random.Random

/** Whether the wake word is listening, for the hint at the bottom of the display. */
enum class Listening { OFF, ON, BLOCKED }

/**
 * Lena's display: the clock, the weather, how she feels and the last thing she said, on black,
 * for a docked phone or a tablet on the wall. Used by [DisplayActivity] and the [LenaDream]
 * screen saver. [locked]: shown over the lock screen, so no reasons and nothing she said.
 */
@Composable
fun LenaDisplay(locked: Boolean, listening: Listening, onTalk: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var house by remember { mutableStateOf<HouseNow?>(null) }
    var said by remember { mutableStateOf<String?>(null) }
    var shift by remember { mutableIntStateOf(0) }

    // The clock, once a minute, on the minute. The content drifts a little each minute so a
    // screen that's on all day doesn't burn in.
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            shift = Random.nextInt(0, 1000)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    // Her feelings and the weather, every two minutes.
    LaunchedEffect(Unit) {
        val ha = HomeAssistant.from(context) ?: return@LaunchedEffect
        while (true) {
            house = runCatching { ha.now() }.getOrNull() ?: house
            delay(120_000)
        }
    }
    // The last thing she said in your ongoing conversation, every five minutes.
    LaunchedEffect(locked) {
        if (locked || !Prefs.ongoing(context)) return@LaunchedEffect
        val api = Api.from(context) ?: return@LaunchedEffect
        while (true) {
            said = Prefs.assistChat(context)?.let { id -> runCatching { api.latest(id).second }.getOrNull() }
                ?.lineSequence()?.firstOrNull { it.isNotBlank() }
            delay(300_000)
        }
    }

    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val night = hour >= 22 || hour < 6 || house?.feelings?.energy == "Drained"
    // At night: dim, warm and quiet, so it doesn't light up a bedroom.
    val ink = if (night) Color(0xFF9E5A4A) else Color.White
    val soft = ink.copy(alpha = if (night) 0.6f else 0.65f)
    val dx by animateDpAsState(((shift % 17) - 8).dp, label = "dx")
    val dy by animateDpAsState(((shift / 17 % 17) - 8).dp, label = "dy")

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Talk to Lena", onClick = onTalk),
    ) {
        Column(
            Modifier.fillMaxSize().offset(dx, dy).padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Date and weather
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(DateFormat.format("EEEE, MMMM d", now).toString(), color = soft, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                house?.weather?.let { w ->
                    Icon(weatherIcon(w.condition, night || hour >= 19 || hour < 7), null, tint = soft, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        listOfNotNull(w.temperature?.let { "%.0f%s".format(it, w.unit.ifBlank { "°" }) }, weatherWords(w.condition)).joinToString("  "),
                        color = soft, style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.weight(1f))

            // The clock
            Text(
                DateFormat.getTimeFormat(context).format(now).replace(Regex("\\s?[AaPp]\\.?[Mm]\\.?$"), ""),
                color = ink, fontSize = 112.sp, fontWeight = FontWeight.Light, lineHeight = 112.sp,
            )
            Spacer(Modifier.height(20.dp))

            // How she feels
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.clip(CircleShape)) { LenaAvatar(40.dp) }
                val f = house?.feelings
                Column {
                    Text(
                        listOfNotNull("Lena", f?.mood, f?.moment).joinToString(" · "),
                        color = ink, style = MaterialTheme.typography.titleMedium,
                    )
                    val why = f?.let { it.momentWhy.takeIf { w -> it.moment != null && w.isNotBlank() } ?: it.moodWhy }
                    if (!locked && !why.isNullOrBlank()) Text(why, color = soft, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))

            // The last thing she said
            AnimatedVisibility(visible = !night && !locked && !said.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "“${Markdown.plain(said ?: "")}”",
                    color = soft, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 560.dp).padding(bottom = 16.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (listening == Listening.ON) Icon(Icons.Rounded.Mic, null, tint = soft, modifier = Modifier.size(16.dp))
                Text(
                    when (listening) {
                        Listening.ON -> "Say \u201C${Prefs.wakePhrase(context).label}\u201D or tap to talk"
                        Listening.BLOCKED -> "Tap to talk (the phone won't let her listen here)"
                        Listening.OFF -> "Tap to talk"
                    },
                    color = soft.copy(alpha = 0.5f), style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun weatherIcon(condition: String, dark: Boolean): ImageVector = when (condition) {
    "sunny" -> Icons.Outlined.WbSunny
    "clear-night" -> Icons.Outlined.NightsStay
    "partlycloudy" -> if (dark) Icons.Outlined.NightsStay else Icons.Outlined.FilterDrama
    "rainy", "pouring", "hail" -> Icons.Outlined.WaterDrop
    "lightning", "lightning-rainy" -> Icons.Outlined.Thunderstorm
    "snowy", "snowy-rainy" -> Icons.Outlined.AcUnit
    "windy", "windy-variant" -> Icons.Outlined.Air
    else -> Icons.Outlined.Cloud
}

private fun weatherWords(condition: String): String? = when (condition) {
    "clear-night" -> "Clear"
    "partlycloudy" -> "Partly cloudy"
    "lightning-rainy" -> "Thunderstorms"
    "lightning" -> "Lightning"
    "snowy-rainy" -> "Sleet"
    "windy-variant" -> "Windy"
    "exceptional" -> null
    "" -> null
    else -> condition.replaceFirstChar { it.uppercase() }
}

/** Opens Lena listening, from the display or the screen saver. */
private fun talk(c: Context) = c.startActivity(AskActivity.intent(c, talk = true))

/**
 * Display mode: [LenaDisplay] full screen, kept on, with the wake word if you turned it on.
 * Back (or a swipe from the edge) leaves it.
 */
class DisplayActivity : ComponentActivity() {
    private val listening = mutableStateOf(Listening.OFF)
    private var wake: WakeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Prefs.wakeWord(this) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        setContent { LenaTheme { LenaDisplay(locked = false, listening = listening.value, onTalk = ::onTalk) } }
    }

    private fun onTalk() {
        wake?.stop()
        talk(this)
    }

    override fun onResume() {
        super.onResume()
        startListening()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startListening()
    }

    private fun startListening() {
        if (!Prefs.wakeWord(this)) return
        val w = wake ?: WakeListener(this, onHeard = ::onTalk, onSilenced = { listening.value = Listening.BLOCKED }).also { wake = it }
        listening.value = if (w.start()) Listening.ON else Listening.OFF
    }

    override fun onPause() {
        wake?.stop()
        if (listening.value == Listening.ON) listening.value = Listening.OFF
        super.onPause()
    }
}

/**
 * The same display as Android's screen saver (Settings → Display → Screen saver → Lena), so it
 * appears by itself while the phone charges. Over the lock screen it keeps her reasons and what
 * she said to herself. Tapping wakes the phone and opens Lena listening.
 */
class LenaDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val listening = mutableStateOf(Listening.OFF)
    private var wake: WakeListener? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val view = ComposeView(this).apply {
            setContent { LenaTheme { LenaDisplay(locked = locked, listening = listening.value, onTalk = ::onTalk) } }
        }
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        setContentView(view)
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        if (Prefs.wakeWord(this)) {
            val w = WakeListener(this, onHeard = ::onTalk, onSilenced = { listening.value = Listening.BLOCKED }).also { wake = it }
            listening.value = if (w.start()) Listening.ON else Listening.OFF
        }
    }

    private fun onTalk() {
        wake?.stop()
        talk(this)
        finish()
    }

    override fun onDreamingStopped() {
        wake?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        wake?.stop()
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
