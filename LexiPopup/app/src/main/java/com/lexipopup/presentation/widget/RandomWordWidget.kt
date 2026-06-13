package com.lexipopup.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lexipopup.data.local.entities.RandomWordEntity
import com.lexipopup.presentation.dashboard.MainActivity
import com.lexipopup.workers.RandomWordWorker
import dagger.hilt.android.EntryPointAccessors

// ── Color tokens ──────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF0B1D2E)
private val BgCard      = Color(0xFF132636)
private val AccentCyan  = ColorProvider(Color(0xFF4FC3F7))
private val White100    = ColorProvider(Color.White)
private val White70     = ColorProvider(Color(0xB3FFFFFF))
private val White40     = ColorProvider(Color(0x66FFFFFF))
private val GoldColor   = ColorProvider(Color(0xFFFFD54F))
private val GreenTint   = ColorProvider(Color(0xFF81C784))
private val RedTint     = ColorProvider(Color(0xFFEF9A9A))

// ── Preference keys stored per-widget ─────────────────────────────────────────
private val KEY_EXPANDED   = booleanPreferencesKey("rw_expanded")
private val KEY_WORD_ID    = longPreferencesKey("rw_word_id")

// ── ActionParameters keys ─────────────────────────────────────────────────────
val PARAM_WORD_ID = ActionParameters.Key<Long>("word_id")

class RandomWordWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        val TRAY = DpSize(200.dp, 72.dp)
        val CARD = DpSize(200.dp, 180.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(TRAY, CARD))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val word = fetchCurrentWord(context)
        provideContent {
            val prefs    = currentState<androidx.datastore.preferences.core.Preferences>()
            val expanded = prefs[KEY_EXPANDED] ?: false
            val h        = LocalSize.current.height

            if (h < 100.dp) {
                RandomWordTray(word)
            } else {
                if (expanded && word != null) {
                    RandomWordExpanded(word)
                } else {
                    RandomWordCard(word)
                }
            }
        }
    }

    private suspend fun fetchCurrentWord(context: Context): RandomWordEntity? = try {
        val dao = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .randomWordDao()
        dao.getNextUnseen()
    } catch (e: Exception) {
        null
    }
}

// ── Tray (1-row compact) ──────────────────────────────────────────────────────

@androidx.compose.runtime.Composable
private fun RandomWordTray(word: RandomWordEntity?) {
    val ctx = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgDeep)
            .clickable(actionStartActivity(mainIntent(ctx, word?.word))),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚡", style = TextStyle(color = AccentCyan, fontSize = 13.sp))
            Spacer(GlanceModifier.width(6.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = word?.word?.replaceFirstChar { it.uppercaseChar() } ?: "Random Word",
                    style = TextStyle(color = White100, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (!word?.teaser.isNullOrBlank()) {
                    Text(
                        text = word!!.teaser,
                        style = TextStyle(color = White70, fontSize = 9.sp),
                        maxLines = 1
                    )
                }
            }
            Text("›", style = TextStyle(color = AccentCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold))
        }
    }
}

// ── Card — collapsed face ─────────────────────────────────────────────────────

@androidx.compose.runtime.Composable
private fun RandomWordCard(word: RandomWordEntity?) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgDeep)
            .clickable(
                if (word != null)
                    actionRunCallback<ToggleExpandCallback>(
                        actionParametersOf(PARAM_WORD_ID to word.id)
                    )
                else
                    actionRunCallback<RefetchCallback>()
            )
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
                    text = "RANDOM",
                    style = TextStyle(color = White40, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.defaultWeight())
                Text("⚡", style = TextStyle(color = AccentCyan, fontSize = 13.sp))
            }

            Spacer(GlanceModifier.height(8.dp))

            if (word != null) {
                Text(
                    text = word.word.replaceFirstChar { it.uppercaseChar() },
                    style = TextStyle(color = White100, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(3.dp))
                if (word.pronunciation.isNotBlank()) {
                    Text(
                        text = word.pronunciation,
                        style = TextStyle(color = AccentCyan, fontSize = 10.sp),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.height(2.dp))
                }
                Text(
                    text = word.partOfSpeech.ifBlank { "—" },
                    style = TextStyle(color = White40, fontSize = 9.sp),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    text = if (word.teaser.isNotBlank()) word.teaser else word.definition.take(60),
                    style = TextStyle(color = White70, fontSize = 11.sp),
                    maxLines = 2
                )
            } else {
                Text(
                    text = "Generating…",
                    style = TextStyle(color = White70, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "Add your Groq API key in LexiPopup → Settings → AI to start receiving advanced words.",
                    style = TextStyle(color = White40, fontSize = 10.sp),
                    maxLines = 3
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            Text(
                text = if (word != null) "Tap to reveal definition ▾" else "Open app to configure →",
                style = TextStyle(color = AccentCyan, fontSize = 9.sp)
            )
        }
    }
}

// ── Card — expanded / revealed ────────────────────────────────────────────────

@androidx.compose.runtime.Composable
private fun RandomWordExpanded(word: RandomWordEntity) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgCard)
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word.word.replaceFirstChar { it.uppercaseChar() },
                    style = TextStyle(color = White100, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(4.dp))
                if (word.partOfSpeech.isNotBlank()) {
                    Text(
                        text = word.partOfSpeech,
                        style = TextStyle(color = GoldColor, fontSize = 9.sp)
                    )
                }
            }

            if (word.pronunciation.isNotBlank()) {
                Text(
                    text = word.pronunciation,
                    style = TextStyle(color = AccentCyan, fontSize = 10.sp),
                    maxLines = 1
                )
            }

            Spacer(GlanceModifier.height(6.dp))

            Text(
                text = word.definition,
                style = TextStyle(color = White70, fontSize = 11.sp),
                maxLines = 3
            )

            if (word.example.isNotBlank()) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "\"${word.example}\"",
                    style = TextStyle(color = White40, fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    maxLines = 2
                )
            }

            if (word.etymology.isNotBlank()) {
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = "📜 ${word.etymology}",
                    style = TextStyle(color = White40, fontSize = 9.sp),
                    maxLines = 2
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xFF1B5E20))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable(
                            actionRunCallback<SaveAndNextCallback>(
                                actionParametersOf(PARAM_WORD_ID to word.id)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓ Save & Next",
                        style = TextStyle(color = GreenTint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(GlanceModifier.width(6.dp))
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .background(Color(0xFF4A0F0F))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable(
                            actionRunCallback<SkipWordCallback>(
                                actionParametersOf(PARAM_WORD_ID to word.id)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✗ Skip",
                        style = TextStyle(color = RedTint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// ── Action Callbacks ──────────────────────────────────────────────────────────

/** Toggle collapsed ↔ expanded */
class ToggleExpandCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val current = prefs[KEY_EXPANDED] ?: false
            prefs.toMutablePreferences().apply { this[KEY_EXPANDED] = !current }
        }
        RandomWordWidget().update(context, glanceId)
    }
}

/** Save word to vocab history + advance to next */
class SaveAndNextCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val wordId = parameters[PARAM_WORD_ID] ?: return
        try {
            val dao = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .randomWordDao()
            dao.markSeen(wordId)
            if (dao.getUnseenCount() < 3) {
                RandomWordWorker.scheduleOneTime(context)
            }
        } catch (_: Exception) { }
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply { this[KEY_EXPANDED] = false }
        }
        RandomWordWidget().update(context, glanceId)
    }
}

/** Dismiss without saving */
class SkipWordCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val wordId = parameters[PARAM_WORD_ID] ?: return
        try {
            val dao = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .randomWordDao()
            dao.markSeen(wordId)
            if (dao.getUnseenCount() < 3) {
                RandomWordWorker.scheduleOneTime(context)
            }
        } catch (_: Exception) { }
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply { this[KEY_EXPANDED] = false }
        }
        RandomWordWidget().update(context, glanceId)
    }
}

/** Triggered when the queue is empty — kicks off a fetch */
class RefetchCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RandomWordWorker.scheduleOneTime(context)
        RandomWordWidget().update(context, glanceId)
    }
}

// ── Receiver ──────────────────────────────────────────────────────────────────

class RandomWordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RandomWordWidget()
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun mainIntent(context: Context, word: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        if (word != null) putExtra("lookup_word", word)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
