package com.lexipopup.presentation.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.unit.DayNightColorProvider
import com.lexipopup.data.local.entities.WordEntity
import com.lexipopup.presentation.popup.PopupActivity
import dagger.hilt.android.EntryPointAccessors

private val RecentHeaderBg = Color(0xFF004D40)
private val RecentHeaderText = ColorProvider(Color.White)
private val RecentHeaderSub = DayNightColorProvider(day = Color(0x99FFFFFF), night = Color(0x99FFFFFF))
private val RecentTealAccent = ColorProvider(Color(0xFF80CBC4))
private val RecentCardBg = DayNightColorProvider(day = Color(0xFFF5F7FA), night = Color(0xFF0D1F1E))
private val RecentWordText = DayNightColorProvider(day = Color(0xFF0D1B2A), night = Color(0xFFECEFF1))
private val RecentMeaning = DayNightColorProvider(day = Color(0xFF546E7A), night = Color(0xFF80A4A0))
private val RecentDivider = DayNightColorProvider(day = Color(0x14000000), night = Color(0x14FFFFFF))
private val RecentEmpty = DayNightColorProvider(day = Color(0xFF80A4A0), night = Color(0xFF37635F))

private val CARD_ACCENTS = listOf(
    DayNightColorProvider(day = Color(0xFF00695C), night = Color(0xFF4DB6AC)),
    DayNightColorProvider(day = Color(0xFF00796B), night = Color(0xFF80CBC4)),
    DayNightColorProvider(day = Color(0xFF00838F), night = Color(0xFF80DEEA)),
    DayNightColorProvider(day = Color(0xFF0277BD), night = Color(0xFF4FC3F7)),
    DayNightColorProvider(day = Color(0xFF6A1B9A), night = Color(0xFFCE93D8)),
    DayNightColorProvider(day = Color(0xFFAD1457), night = Color(0xFFF48FB1)),
    DayNightColorProvider(day = Color(0xFF558B2F), night = Color(0xFFAED581)),
    DayNightColorProvider(day = Color(0xFFE65100), night = Color(0xFFFFB74D)),
    DayNightColorProvider(day = Color(0xFF1565C0), night = Color(0xFF90CAF9)),
    DayNightColorProvider(day = Color(0xFF37474F), night = Color(0xFF90A4AE)),
)

private val CARD_BGS = listOf(
    DayNightColorProvider(day = Color(0xFFE0F2F1), night = Color(0xFF0D302C)),
    DayNightColorProvider(day = Color(0xFFE0F7FA), night = Color(0xFF082D30)),
    DayNightColorProvider(day = Color(0xFFE1F5FE), night = Color(0xFF082233)),
    DayNightColorProvider(day = Color(0xFFE3F2FD), night = Color(0xFF0D2045)),
    DayNightColorProvider(day = Color(0xFFF3E5F5), night = Color(0xFF2D1B33)),
    DayNightColorProvider(day = Color(0xFFFCE4EC), night = Color(0xFF330F1C)),
    DayNightColorProvider(day = Color(0xFFF9FBE7), night = Color(0xFF252D0A)),
    DayNightColorProvider(day = Color(0xFFFFF3E0), night = Color(0xFF3D2000)),
    DayNightColorProvider(day = Color(0xFFE8EAF6), night = Color(0xFF1A1E40)),
    DayNightColorProvider(day = Color(0xFFECEFF1), night = Color(0xFF1A2226)),
)

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

    Column(modifier = GlanceModifier.fillMaxSize().background(RecentCardBg)) {

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
                    style = TextStyle(color = RecentHeaderSub, fontSize = 10.sp)
                )
            }
            Text(
                text = "↻",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshRecentCallback>()),
                style = TextStyle(color = RecentHeaderText, fontSize = 18.sp)
            )
        }

        if (words.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("↺", style = TextStyle(color = RecentEmpty, fontSize = 28.sp))
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = "No words yet",
                        style = TextStyle(color = RecentEmpty, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "Look up words to see them here",
                        style = TextStyle(color = RecentEmpty, fontSize = 10.sp)
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
    val colorIdx = index % CARD_ACCENTS.size
    val accent = CARD_ACCENTS[colorIdx]
    val bg = CARD_BGS[colorIdx]

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
                    style = TextStyle(color = RecentWordText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (word.shortMeaning.isNotBlank()) {
                    Text(
                        text = word.shortMeaning,
                        style = TextStyle(color = RecentMeaning, fontSize = 10.sp),
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
        Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(RecentDivider)) {}
    }
}

private fun recentWordIntent(context: Context, word: String): Intent =
    Intent(context, PopupActivity::class.java).apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, word)
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
