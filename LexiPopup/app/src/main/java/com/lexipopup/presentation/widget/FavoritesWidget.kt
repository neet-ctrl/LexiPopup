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

private val FavHeaderBg = Color(0xFF0D47A1)
private val FavHeaderText = ColorProvider(Color.White)
private val FavHeaderSub = DayNightColorProvider(day = Color(0x99FFFFFF), night = Color(0x99FFFFFF))
private val FavCardBg = DayNightColorProvider(day = Color(0xFFF5F7FA), night = Color(0xFF1A1A2E))
private val FavDivider = DayNightColorProvider(day = Color(0x14000000), night = Color(0x14FFFFFF))
private val FavMeaning = DayNightColorProvider(day = Color(0xFF546E7A), night = Color(0xFF90A4AE))
private val FavEmpty = DayNightColorProvider(day = Color(0xFF90A4AE), night = Color(0xFF546E7A))

private fun favDiffBg(level: Int) = when (level) {
    1 -> DayNightColorProvider(day = Color(0xFFE8F5E9), night = Color(0xFF1A3020))
    2 -> DayNightColorProvider(day = Color(0xFFE3F2FD), night = Color(0xFF0D2045))
    3 -> DayNightColorProvider(day = Color(0xFFFFF8E1), night = Color(0xFF3D2000))
    4 -> DayNightColorProvider(day = Color(0xFFFFEBEE), night = Color(0xFF3D000F))
    else -> DayNightColorProvider(day = Color(0xFFF5F5F5), night = Color(0xFF252525))
}

private fun favDiffAccent(level: Int) = when (level) {
    1 -> DayNightColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF81C784))
    2 -> DayNightColorProvider(day = Color(0xFF1565C0), night = Color(0xFF64B5F6))
    3 -> DayNightColorProvider(day = Color(0xFFE65100), night = Color(0xFFFFB74D))
    4 -> DayNightColorProvider(day = Color(0xFFB71C1C), night = Color(0xFFEF9A9A))
    else -> DayNightColorProvider(day = Color(0xFF455A64), night = Color(0xFF90A4AE))
}

private fun favDiffText(level: Int) = when (level) {
    1 -> DayNightColorProvider(day = Color(0xFF1B5E20), night = Color(0xFFC8E6C9))
    2 -> DayNightColorProvider(day = Color(0xFF0D47A1), night = Color(0xFFBBDEFB))
    3 -> DayNightColorProvider(day = Color(0xFFBF360C), night = Color(0xFFFFE0B2))
    4 -> DayNightColorProvider(day = Color(0xFF7F0000), night = Color(0xFFFFCDD2))
    else -> DayNightColorProvider(day = Color(0xFF263238), night = Color(0xFFCFD8DC))
}

class FavoritesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val words = fetchFavorites(context)
        provideContent { FavoritesContent(words) }
    }

    private suspend fun fetchFavorites(context: Context): List<WordEntity> = try {
        EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .wordDao()
            .getFavoritesList()
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
private fun FavoritesContent(words: List<WordEntity>) {
    val ctx = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize().background(FavCardBg)) {

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(FavHeaderBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("★", style = TextStyle(color = ColorProvider(Color(0xFFFFD54F)), fontSize = 15.sp))
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Favourites",
                    style = TextStyle(color = FavHeaderText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${words.size} word${if (words.size != 1) "s" else ""}",
                    style = TextStyle(color = FavHeaderSub, fontSize = 10.sp)
                )
            }
            Text(
                text = "↻",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshFavoritesCallback>()),
                style = TextStyle(color = FavHeaderText, fontSize = 18.sp)
            )
        }

        if (words.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("★", style = TextStyle(color = FavEmpty, fontSize = 28.sp))
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = "No favourites yet",
                        style = TextStyle(color = FavEmpty, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "Star words to see them here",
                        style = TextStyle(color = FavEmpty, fontSize = 10.sp)
                    )
                }
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(words, itemId = { it.id }) { word ->
                    FavWordCard(word = word, ctx = ctx)
                }
            }
        }
    }
}

@Composable
private fun FavWordCard(word: WordEntity, ctx: Context) {
    val bg = favDiffBg(word.difficultyLevel)
    val accent = favDiffAccent(word.difficultyLevel)
    val text = favDiffText(word.difficultyLevel)

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(wordIntent(ctx, word.word)))
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
                    .width(3.dp)
                    .height(36.dp)
                    .background(accent)
            ) {}
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = word.word.replaceFirstChar { it.uppercaseChar() },
                    style = TextStyle(color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (word.shortMeaning.isNotBlank()) {
                    Text(
                        text = word.shortMeaning,
                        style = TextStyle(color = FavMeaning, fontSize = 10.sp),
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
        Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(FavDivider)) {}
    }
}

private fun wordIntent(context: Context, word: String): Intent =
    Intent(context, PopupActivity::class.java).apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, word)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

class RefreshFavoritesCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        FavoritesWidget().update(context, glanceId)
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = FavoritesWidget()
}
