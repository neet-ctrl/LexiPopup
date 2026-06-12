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
        val DUAL = DpSize(200.dp, 300.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(TRAY, CARD, DUAL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (englishWord, bioWord) = fetchBothWords(context)
        provideContent {
            val h = LocalSize.current.height
            when {
                h < 100.dp  -> WotdTray(englishWord, bioWord)
                h < 260.dp  -> WotdCard(englishWord, bioWord, dualMode = false)
                else        -> WotdCard(englishWord, bioWord, dualMode = true)
            }
        }
    }

    private suspend fun fetchBothWords(context: Context): Pair<WordEntity?, WordEntity?> = try {
        val ep = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val dao = ep.wordDao()
        val dayOffset = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        val englishTotal = dao.getTotalCount(AppMode.ENGLISH.id).coerceAtLeast(1)
        val englishWord = dao.getWordAtOffset(dayOffset % englishTotal, AppMode.ENGLISH.id)
            ?: dao.getWordAtOffset(0, AppMode.ENGLISH.id)

        val bioTotal = dao.getTotalCount(AppMode.BIOLOGY.id).coerceAtLeast(1)
        val bioWord = if (bioTotal > 1) {
            dao.getWordAtOffset(dayOffset % bioTotal, AppMode.BIOLOGY.id)
                ?: dao.getWordAtOffset(0, AppMode.BIOLOGY.id)
        } else null

        Pair(englishWord, bioWord)
    } catch (_: Exception) {
        Pair(null, null)
    }
}

@Composable
private fun WotdTray(englishWord: WordEntity?, bioWord: WordEntity?) {
    val ctx = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgPurple)
            .clickable(actionStartActivity(wotdIntent(ctx, englishWord?.word, AppMode.ENGLISH))),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "★", style = TextStyle(color = Gold, fontSize = 13.sp))
            Spacer(GlanceModifier.width(6.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = englishWord?.word?.replaceFirstChar { it.uppercaseChar() } ?: "Word of Day",
                    style = TextStyle(color = White100, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(text = "🧬", style = TextStyle(color = BioGold, fontSize = 13.sp))
            Spacer(GlanceModifier.width(6.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = bioWord?.word?.replaceFirstChar { it.uppercaseChar() } ?: "Term of Day",
                    style = TextStyle(color = White100, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
            Text(text = "›", style = TextStyle(color = Lilac, fontSize = 22.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun WotdCard(englishWord: WordEntity?, bioWord: WordEntity?, dualMode: Boolean) {
    val ctx = LocalContext.current

    if (!dualMode) {
        // Single card showing English word of the day
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(BgPurple)
                .clickable(actionStartActivity(wotdIntent(ctx, englishWord?.word, AppMode.ENGLISH)))
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(14.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WORD OF THE DAY",
                        style = TextStyle(color = White40, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(text = "★", style = TextStyle(color = Gold, fontSize = 14.sp))
                }
                Spacer(GlanceModifier.height(10.dp))
                Text(
                    text = englishWord?.word?.replaceFirstChar { it.uppercaseChar() } ?: "—",
                    style = TextStyle(color = White100, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (englishWord != null) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = listOfNotNull(
                            englishWord.pronunciation.takeIf { it.isNotBlank() },
                            englishWord.partOfSpeech.takeIf { it.isNotBlank() }
                        ).joinToString("  ·  "),
                        style = TextStyle(color = Lilac, fontSize = 10.sp),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        text = englishWord.shortMeaning,
                        style = TextStyle(color = White70, fontSize = 12.sp),
                        maxLines = 2
                    )
                } else {
                    Spacer(GlanceModifier.height(8.dp))
                    Text(text = "Tap to look up a word", style = TextStyle(color = White70, fontSize = 12.sp))
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(text = "Tap to explore →", style = TextStyle(color = Lilac, fontSize = 9.sp))
            }
        }
    } else {
        // Dual card: English on top, Biology below
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // ── English Word of the Day ──────────────────────────────────────
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(BgPurple)
                    .clickable(actionStartActivity(wotdIntent(ctx, englishWord?.word, AppMode.ENGLISH)))
            ) {
                Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WORD OF THE DAY",
                            style = TextStyle(color = White40, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(text = "★", style = TextStyle(color = Gold, fontSize = 12.sp))
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = englishWord?.word?.replaceFirstChar { it.uppercaseChar() } ?: "—",
                        style = TextStyle(color = White100, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    if (englishWord != null && englishWord.shortMeaning.isNotBlank()) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = englishWord.shortMeaning,
                            style = TextStyle(color = White70, fontSize = 11.sp),
                            maxLines = 2
                        )
                    }
                    Spacer(GlanceModifier.defaultWeight())
                }
            }

            // ── Thin divider ─────────────────────────────────────────────────
            Box(
                modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(Color(0x44FFFFFF))
            ) {}

            // ── Biology Term of the Day ──────────────────────────────────────
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(BgBioGreen)
                    .clickable(actionStartActivity(wotdIntent(ctx, bioWord?.word, AppMode.BIOLOGY)))
            ) {
                Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TERM OF THE DAY",
                            style = TextStyle(color = White40, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(text = "🧬", style = TextStyle(color = BioGold, fontSize = 12.sp))
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = bioWord?.word?.replaceFirstChar { it.uppercaseChar() } ?: "—",
                        style = TextStyle(color = White100, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    if (bioWord != null && bioWord.shortMeaning.isNotBlank()) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = bioWord.shortMeaning,
                            style = TextStyle(color = BioAccent, fontSize = 11.sp),
                            maxLines = 2
                        )
                    } else {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = "Look up biology terms to see them here",
                            style = TextStyle(color = BioAccent, fontSize = 11.sp),
                            maxLines = 2
                        )
                    }
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
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
