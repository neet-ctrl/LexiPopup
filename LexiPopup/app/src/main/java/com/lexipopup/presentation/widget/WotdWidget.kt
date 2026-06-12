package com.lexipopup.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lexipopup.data.local.entities.WordEntity
import com.lexipopup.domain.models.AppMode
import com.lexipopup.presentation.popup.PopupActivity
import dagger.hilt.android.EntryPointAccessors
import java.util.Calendar

// ── English palette ───────────────────────────────────────────────────────────
private val BgPurple = Color(0xFF4527A0)
private val White100 = ColorProvider(Color.White)
private val White70 = ColorProvider(Color(0xB3FFFFFF))
private val White40 = ColorProvider(Color(0x66FFFFFF))
private val Gold = ColorProvider(Color(0xFFFFD54F))
private val Lilac = ColorProvider(Color(0xFFCE93D8))

// ── Biology palette ───────────────────────────────────────────────────────────
private val BgBioGreen = Color(0xFF1B5E20)
private val BioGold = ColorProvider(Color(0xFF69F0AE))
private val BioAccent = ColorProvider(Color(0xFFA5D6A7))

class WotdWidget : GlanceAppWidget() {

    companion object {
        val TRAY = DpSize(200.dp, 60.dp)
        val CARD = DpSize(200.dp, 160.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(TRAY, CARD))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (word, mode) = fetchWordOfDay(context)
        provideContent {
            val h = LocalSize.current.height
            if (h < 100.dp) WotdTray(word, mode) else WotdCard(word, mode)
        }
    }

    private suspend fun fetchWordOfDay(context: Context): Pair<WordEntity?, AppMode> = try {
        val ep = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val currentMode = ep.modeManager().currentMode.value
        val dao = ep.wordDao()
        val dayOffset = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val modeId = currentMode.id
        val word = if (currentMode == AppMode.BIOLOGY) {
            val total = dao.getTotalCount(modeId).coerceAtLeast(1)
            dao.getWordAtOffset(dayOffset % total, modeId)
                ?: dao.getWordAtOffset(0, modeId)
        } else {
            val total = dao.getTotalCount(modeId).coerceAtLeast(1)
            dao.getWordAtOffset(dayOffset % total, modeId)
        }
        Pair(word, currentMode)
    } catch (_: Exception) {
        Pair(null, AppMode.ENGLISH)
    }
}

@Composable
private fun WotdTray(word: WordEntity?, mode: AppMode) {
    val ctx = LocalContext.current
    val bgColor = if (mode == AppMode.BIOLOGY) BgBioGreen else BgPurple
    val starColor = if (mode == AppMode.BIOLOGY) BioGold else Gold
    val arrowColor = if (mode == AppMode.BIOLOGY) BioAccent else Lilac

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(actionStartActivity(wotdIntent(ctx, word?.word, mode))),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (mode == AppMode.BIOLOGY) "🧬" else "★",
                style = TextStyle(color = starColor, fontSize = 13.sp)
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = word?.word?.replaceFirstChar { it.uppercaseChar() }
                        ?: if (mode == AppMode.BIOLOGY) "Term of Day" else "Word of Day",
                    style = TextStyle(color = White100, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (!word?.pronunciation.isNullOrBlank()) {
                    Text(
                        text = word!!.pronunciation,
                        style = TextStyle(color = White70, fontSize = 10.sp),
                        maxLines = 1
                    )
                }
            }
            Text(text = "›", style = TextStyle(color = arrowColor, fontSize = 22.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun WotdCard(word: WordEntity?, mode: AppMode) {
    val ctx = LocalContext.current
    val bgColor = if (mode == AppMode.BIOLOGY) BgBioGreen else BgPurple
    val starColor = if (mode == AppMode.BIOLOGY) BioGold else Gold
    val accentColor = if (mode == AppMode.BIOLOGY) BioAccent else Lilac
    val label = if (mode == AppMode.BIOLOGY) "TERM OF THE DAY" else "WORD OF THE DAY"
    val modeIcon = if (mode == AppMode.BIOLOGY) "🧬" else "★"

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(actionStartActivity(wotdIntent(ctx, word?.word, mode)))
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = TextStyle(color = White40, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(text = modeIcon, style = TextStyle(color = starColor, fontSize = 14.sp))
            }

            Spacer(GlanceModifier.height(10.dp))

            Text(
                text = word?.word?.replaceFirstChar { it.uppercaseChar() } ?: "—",
                style = TextStyle(color = White100, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )

            if (word != null) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        word.pronunciation.takeIf { it.isNotBlank() },
                        word.partOfSpeech.takeIf { it.isNotBlank() }
                    ).joinToString("  ·  "),
                    style = TextStyle(color = accentColor, fontSize = 10.sp),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = word.shortMeaning,
                    style = TextStyle(color = White70, fontSize = 12.sp),
                    maxLines = 2
                )
            } else {
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = "Tap to look up a word",
                    style = TextStyle(color = White70, fontSize = 12.sp)
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            Text(
                text = "Tap to explore →",
                style = TextStyle(color = accentColor, fontSize = 9.sp)
            )
        }
    }
}

// Uses "lookup_word" + "lookup_mode" extras so the mode survives Glance's PendingIntent wrapping.
private fun wotdIntent(context: Context, word: String?, mode: AppMode): Intent =
    Intent(context, PopupActivity::class.java).apply {
        if (word != null) {
            putExtra("lookup_word", word)
            putExtra("lookup_mode", mode.id)
        } else {
            putExtra("mode", "manual_search")
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

class WotdWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WotdWidget()
}
