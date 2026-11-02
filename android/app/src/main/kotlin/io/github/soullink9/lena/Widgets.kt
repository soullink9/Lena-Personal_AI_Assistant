package io.github.soullink9.lena

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.withTimeoutOrNull

/** Quick Settings tile: talk to Lena from the pull-down shade (the sheet, already listening). */
class LenaTile : TileService() {

    /** The tile shows as an idle button labelled Talk to Lena whenever the shade opens. */
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            updateTile()
        }
    }

    /**
     * Opens the sheet, listening, and closes the shade (Android 14+ requires a PendingIntent for that).
     * The sheet handles the lock screen itself, as it does for the assistant gesture.
     */
    override fun onClick() {
        val talk = AskActivity.intent(this, talk = true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, talk, PendingIntent.FLAG_IMMUTABLE)
        startActivityAndCollapse(pi)
    }
}

/**
 * Home screen widget: Talk, Type and New chat, plus your first three quick prompts, and
 * (if you turn it on) how Lena feels. Only her mood shows here, never the reasons, since anyone
 * can see your home screen.
 */
class LenaWidget : GlanceAppWidget() {

    /**
     * Builds the widget: your first three quick prompts and, if chosen, her mood (only the mood, since
     * anyone can see a home screen). Home Assistant gets eight seconds, so a slow server never leaves the
     * widget blank.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prompts = Prefs.quickPrompts(context).take(3)
        val feelings = if (!Prefs.widgetMood(context)) null
        else HomeAssistant.from(context)?.let { house -> withTimeoutOrNull(8_000) { runCatching { house.feelings() }.getOrNull() } }
        provideContent { GlanceTheme { Content(context, prompts, feelings) } }
    }

    /**
     * The widget itself: her name and mood, the three action buttons, then the quick prompts, in your
     * wallpaper's colours.
     */
    @Composable
    private fun Content(c: Context, prompts: List<String>, feelings: Feelings?) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.lena_avatar),
                    contentDescription = null,
                    modifier = GlanceModifier.size(28.dp).cornerRadius(14.dp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        "Lena",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    )
                    if (feelings != null) Text(
                        listOfNotNull(feelings.mood, feelings.energy.ifBlank { null }, feelings.moment).joinToString(" · "),
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp),
                    )
                }
                Action(R.drawable.ic_glyph_talk, "Talk to Lena", AskActivity.intent(c, talk = true))
                Spacer(GlanceModifier.width(8.dp))
                Action(R.drawable.ic_glyph_ask, "Type to Lena", AskActivity.intent(c))
                Spacer(GlanceModifier.width(8.dp))
                Action(R.drawable.ic_glyph_new, "New chat", Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_CHAT))
            }
            prompts.forEach { p ->
                Spacer(GlanceModifier.height(8.dp))
                Box(
                    GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.secondaryContainer).cornerRadius(16.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable(actionStartActivity(AskActivity.intent(c, p))),
                ) {
                    Text(p, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp))
                }
            }
        }
    }

    /** One round button on the widget. */
    @Composable
    private fun Action(icon: Int, label: String, intent: Intent) {
        Box(
            GlanceModifier.size(40.dp).background(GlanceTheme.colors.primaryContainer).cornerRadius(20.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = label,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            )
        }
    }
}

/** Tells Android which widget this app provides. */
class LenaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LenaWidget()
}
