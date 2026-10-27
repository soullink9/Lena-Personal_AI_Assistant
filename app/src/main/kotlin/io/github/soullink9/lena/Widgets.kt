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

/** Quick Settings tile: start a voice call from the pull-down shade. */
class LenaTile : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        val call = Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_CALL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, call, PendingIntent.FLAG_IMMUTABLE)
        if (isLocked) unlockAndRun { startActivityAndCollapse(pi) } else startActivityAndCollapse(pi)
    }
}

/** Home screen widget: Call, Ask and New chat, plus your first three quick prompts. */
class LenaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prompts = Prefs.quickPrompts(context).take(3)
        provideContent { GlanceTheme { Content(context, prompts) } }
    }

    @Composable
    private fun Content(c: Context, prompts: List<String>) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lena",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Action(R.drawable.ic_glyph_call, "Voice call", Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_CALL))
                Spacer(GlanceModifier.width(8.dp))
                Action(R.drawable.ic_glyph_ask, "Ask Lena", AskActivity.intent(c))
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

class LenaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LenaWidget()
}
