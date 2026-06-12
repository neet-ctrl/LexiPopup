package com.lexipopup.presentation.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import com.lexipopup.presentation.popup.PopupActivity
import dagger.hilt.android.EntryPointAccessors

// ── Adaptive colour helper ────────────────────────────────────────────────────

@Composable
private fun isNight(): Boolean {
    val config = LocalContext.current.resources.configuration
    return (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

@Composable
private fun adaptiveColor(day: Color, night: Color): ColorProvider =
    ColorProvider(if (isNight()) night else day)

// ── Colour palette ────────────────────────────────────────────────────────────

private val RecentHeaderBg = Color(0xFF004D40)
private val RecentHeaderText = ColorProvider(Color.White)
private val RecentTealAccent = ColorProvider(Color(0xFF80CBC4))

private val cardAccentDay = listOf(
    Color(0xFF00695C), Color(0xFF00796B), Color(0xFF00838F), Color(0xFF0277BD),
    Color(0xFF6A1B9A), Color(0xFFAD1457), Color(0xFF558B2F), Color(0xFFE65100),
    Color(0xFF1565C0), Color(0xFF37474F)
)
private val cardAccentNight = listOf(
    Color(0xFF4DB6AC), Color(0xFF80CBC4), Color(0xFF80DEEA), Color(0xFF4FC3F7),
    Color(0xFFCE93D8), Color(0xFFF48FB1), Color(0xFFAED581), Color(0xFFFFB74D),
    Color(0xFF90CAF9), Color(0xFF90A4AE)
)
private val cardBgDay = listOf(
    Color(0xFFE0F2F1), Color(0xFFE0F7FA), Color(0xFFE1F5FE), Color(0xFFE3F2FD),
    Color(0xFFF3E5F5), Color(0xFFFCE4EC), Color(0xFFF9FBE7), Color(0xFFFFF3E0),
    Color(0xFFE8EAF6), Color(0xFFECEFF1)
)
private val cardBgNight = listOf(
    Color(0xFF0D302C), Color(0xFF082D30), Color(0xFF082233), Color(0xFF0D2045),
    Color(0xFF2D1B33), Color(0xFF330F1C), Color(0xFF252D0A), Color(0xFF3D2000),
    Color(0xFF1A1E40), Color(0xFF1A2226)
)

@Composable private fun recentHeaderSub() = adaptiveColor(Color(0x99FFFFFF), Color(0x99FFFFFF))
@Composable private fun recentCardBg() = adaptiveColor(Color(0xFFF5F7FA), Color(0xFF0D1F1E))
@Composable private fun recentWordText() = adaptiveColor(Color(0xFF0D1B2A), Color(0xFFECEFF1))
@Composable private fun recentMeaning() = adaptiveColor(Color(0xFF546E7A), Color(0xFF80A4A0))
@Composable private fun recentDivider() = adaptiveColor(Color(0x14000000), Color(0x14FFFFFF))
@Composable private fun recentEmpty() = adaptiveColor(Color(0xFF80A4A0), Color(0xFF37635F))

@Composable
private fun cardAccent(index: Int): ColorProvider {
    val i = index % cardAccentDay.size
    return adaptiveColor(cardAccentDay[i], cardAccentNight[i])
}

@Composable
private fun cardBg(index: Int): ColorProvider {
    val i = index % cardBgDay.size
    return adaptiveColor(cardBgDay[i], cardBgNight[i])
}

// ── Widget ────────────────────────────────────────────────────────────────────

class RecentWordsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val words = fetchRecentWords(context)
        provideContent { RecentContent(words) }
    }

    private suspend fun fetchRecentWords(context: Context): List<WordEntity> = try {
        EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .wordDao()
            .getRecentWordsList(10)
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
private fun RecentContent(words: List<WordEntity>) {
    val ctx = LocalContext.current
    val indexed = words.mapIndexed { i, w -> i to w }

    Column(modifier = GlanceModifier.fillMaxSize().background(recentCardBg())) {

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(RecentHeaderBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("↺", style = TextStyle(color = RecentTealAccent, fontSize = 16.sp))
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Recent Words",
                    style = TextStyle(color = RecentHeaderText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Last ${words.size} looked up",
                    style = TextStyle(color = recentHeaderSub(), fontSize = 10.sp)
                )
            }
            Text(
                text = "⌕",
                modifier = GlanceModifier
                    .clickable(actionStartActivity(recentManualSearchIntent(ctx)))
                    .padding(horizontal = 6.dp),
                style = TextStyle(color = RecentHeaderText, fontSize = 18.sp)
            )
            Text(
                text = "↻",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshRecentCallback>()),
                style = TextStyle(color = RecentHeaderText, fontSize = 18.sp)
            )
        }

        if (words.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("↺", style = TextStyle(color = recentEmpty(), fontSize = 28.sp))
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = "No words yet",
                        style = TextStyle(color = recentEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "Look up words to see them here",
                        style = TextStyle(color = recentEmpty(), fontSize = 10.sp)
                    )
                }
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(indexed, itemId = { (_, w) -> w.id }) { (index, word) ->
                    RecentWordCard(word = word, index = index, ctx = ctx)
                }
            }
        }
    }
}

@Composable
private fun RecentWordCard(word: WordEntity, index: Int, ctx: Context) {
    val accent = cardAccent(index)
    val bg = cardBg(index)

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(recentWordIntent(ctx, word.word)))
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .width(22.dp)
                    .height(22.dp)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = word.word.replaceFirstChar { it.uppercaseChar() },
                    style = TextStyle(color = recentWordText(), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (word.shortMeaning.isNotBlank()) {
                    Text(
                        text = word.shortMeaning,
                        style = TextStyle(color = recentMeaning(), fontSize = 10.sp),
                        maxLines = 1
                    )
                }
            }
            if (word.partOfSpeech.isNotBlank()) {
                Text(
                    text = word.partOfSpeech.take(4),
                    style = TextStyle(color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
        Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(recentDivider())) {}
    }
}

// Uses "lookup_word" extra instead of ACTION_SEND + MIME type so the word survives
// Glance's PendingIntent wrapping — Android can strip the MIME type from PendingIntents,
// causing processIntent to fall through to manual-search mode.
private fun recentWordIntent(context: Context, word: String): Intent =
    Intent(context, PopupActivity::class.java).apply {
        putExtra("lookup_word", word)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

private fun recentManualSearchIntent(context: Context): Intent =
    Intent(context, PopupActivity::class.java).apply {
        putExtra("mode", "manual_search")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

class RefreshRecentCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        RecentWordsWidget().update(context, glanceId)
    }
}

class RecentWordsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RecentWordsWidget()
}
