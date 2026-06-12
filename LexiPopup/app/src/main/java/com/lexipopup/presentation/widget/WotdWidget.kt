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
import androidx.glance.layout.defaultWeight
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
import java.util.Calendar

private val BgPurple = Color(0xFF4527A0)
private val BgPurpleLight = Color(0xFF512DA8)
private val White100 = ColorProvider(Color.White)
private val White70 = ColorProvider(Color(0xB3FFFFFF))
private val White40 = ColorProvider(Color(0x66FFFFFF))
private val Gold = ColorProvider(Color(0xFFFFD54F))
private val Lilac = ColorProvider(Color(0xFFCE93D8))

class WotdWidget : GlanceAppWidget() {

    companion object {
        val TRAY = DpSize(200.dp, 60.dp)
        val CARD = DpSize(200.dp, 160.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(TRAY, CARD))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val word = fetchWordOfDay(context)
        provideContent {
            val h = LocalSize.current.height
            if (h < 100.dp) WotdTray(word) else WotdCard(word)
        }
    }

    private suspend fun fetchWordOfDay(context: Context): WordEntity? = try {
        val dao = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .wordDao()
        val total = dao.getTotalCount().coerceAtLeast(1)
        val offset = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % total
        dao.getWordAtOffset(offset)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun WotdTray(word: WordEntity?) {
    val ctx = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgPurple)
            .clickable(actionStartActivity(wotdIntent(ctx, word?.word))),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "★",
                style = TextStyle(color = Gold, fontSize = 13.sp)
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = word?.word?.replaceFirstChar { it.uppercaseChar() } ?: "Word of Day",
                    style = TextStyle(
                        color = White100,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
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
            Text(
                text = "›",
                style = TextStyle(color = Lilac, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun WotdCard(word: WordEntity?) {
    val ctx = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgPurple)
            .clickable(actionStartActivity(wotdIntent(ctx, word?.word)))
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
                    text = "WORD OF THE DAY",
                    style = TextStyle(color = White40, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = "★",
                    style = TextStyle(color = Gold, fontSize = 14.sp)
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            Text(
                text = word?.word?.replaceFirstChar { it.uppercaseChar() } ?: "—",
                style = TextStyle(
                    color = White100,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )

            if (word != null) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        word.pronunciation.takeIf { it.isNotBlank() },
                        word.partOfSpeech.takeIf { it.isNotBlank() }
                    ).joinToString("  ·  "),
                    style = TextStyle(color = Lilac, fontSize = 10.sp),
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
                style = TextStyle(color = Lilac, fontSize = 9.sp)
            )
        }
    }
}

private fun wotdIntent(context: Context, word: String?): Intent =
    Intent(context, PopupActivity::class.java).apply {
        if (word != null) {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, word)
        } else {
            putExtra("mode", "manual_search")
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

class WotdWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = WotdWidget()
}
