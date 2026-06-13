package com.lexipopup.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lexipopup.data.local.entities.RandomWordEntity
import com.lexipopup.presentation.popup.PopupActivity
import com.lexipopup.workers.RandomWordWorker
import dagger.hilt.android.EntryPointAccessors

// ── Color tokens ──────────────────────────────────────────────────────────────
private val BgDeep    = Color(0xFF0B1D2E)
private val BgAlt     = Color(0xFF0F1F30)
private val AccentCyan = ColorProvider(Color(0xFF4FC3F7))
private val White100  = ColorProvider(Color.White)
private val White70   = ColorProvider(Color(0xB3FFFFFF))
private val White40   = ColorProvider(Color(0x66FFFFFF))
private val GoldColor = ColorProvider(Color(0xFFFFD54F))

// ── Preference keys (one per slot) ────────────────────────────────────────────
private val KEY_SLOT_0 = longPreferencesKey("rw_slot_0")
private val KEY_SLOT_1 = longPreferencesKey("rw_slot_1")
private val KEY_SLOT_2 = longPreferencesKey("rw_slot_2")

// ── ActionParameters keys ─────────────────────────────────────────────────────
val PARAM_WORD_ID   = ActionParameters.Key<Long>("word_id")
val PARAM_SLOT      = ActionParameters.Key<Int>("slot")
val PARAM_OTHER_A   = ActionParameters.Key<Long>("other_a")
val PARAM_OTHER_B   = ActionParameters.Key<Long>("other_b")
val PARAM_WORD_TEXT = ActionParameters.Key<String>("word_text")

class RandomWordWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        val TRAY  = DpSize(200.dp, 72.dp)
        val MULTI = DpSize(200.dp, 240.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(TRAY, MULTI))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = try {
            EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .randomWordDao()
        } catch (_: Exception) { null }

        // Read current slot IDs from state
        var prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        var s0 = prefs[KEY_SLOT_0] ?: 0L
        var s1 = prefs[KEY_SLOT_1] ?: 0L
        var s2 = prefs[KEY_SLOT_2] ?: 0L

        // Auto-fill any empty slots on first run
        if (dao != null && (s0 == 0L || s1 == 0L || s2 == 0L)) {
            val occupied = setOf(s0, s1, s2).filter { it != 0L }.toSet()
            val pool = dao.getTopUnseen(10).filter { it.id !in occupied }.iterator()
            if (s0 == 0L) s0 = pool.nextOrNull()?.id ?: 0L
            if (s1 == 0L) s1 = pool.nextOrNull()?.id ?: 0L
            if (s2 == 0L) s2 = pool.nextOrNull()?.id ?: 0L
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { p ->
                p.toMutablePreferences().apply {
                    this[KEY_SLOT_0] = s0
                    this[KEY_SLOT_1] = s1
                    this[KEY_SLOT_2] = s2
                }
            }
        }

        val word0 = if (s0 != 0L) dao?.getById(s0) else null
        val word1 = if (s1 != 0L) dao?.getById(s1) else null
        val word2 = if (s2 != 0L) dao?.getById(s2) else null

        // Kick off a background fetch if queue is running low
        if (dao != null && dao.getUnseenCount() < 5) RandomWordWorker.scheduleOneTime(context)

        provideContent {
            if (LocalSize.current.height < 100.dp) {
                RandomWordTray(word0)
            } else {
                RandomWordMultiCard(word0, word1, word2, s0, s1, s2)
            }
        }
    }
}

// ── Tray (compact 1-row) ──────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun RandomWordTray(word: RandomWordEntity?) {
    val ctx = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgDeep)
            .clickable(
                if (word != null)
                    actionStartActivity(popupIntent(ctx, word.word))
                else
                    actionRunCallback<RefreshAllCallback>()
            ),
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

// ── Multi-card (3 words + refresh) ────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun RandomWordMultiCard(
    word0: RandomWordEntity?, word1: RandomWordEntity?, word2: RandomWordEntity?,
    id0: Long, id1: Long, id2: Long
) {
    Column(modifier = GlanceModifier.fillMaxSize().background(BgDeep)) {

        // ── Header row with refresh button ────────────────────────────────────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚡", style = TextStyle(color = AccentCyan, fontSize = 11.sp))
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = "RANDOM WORDS",
                style = TextStyle(color = White40, fontSize = 8.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            // Refresh-all button
            Box(
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable(
                        actionRunCallback<RefreshAllCallback>(
                            actionParametersOf(
                                PARAM_WORD_ID to id0,
                                PARAM_OTHER_A to id1,
                                PARAM_OTHER_B to id2
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("↺", style = TextStyle(color = AccentCyan, fontSize = 16.sp))
            }
        }

        // ── Three word slot rows ───────────────────────────────────────────────
        val slots = listOf(
            Triple(word0, id0, 0),
            Triple(word1, id1, 1),
            Triple(word2, id2, 2)
        )
        slots.forEach { (word, wordId, slot) ->
            val otherA = when (slot) { 0 -> id1; 1 -> id0; else -> id0 }
            val otherB = when (slot) { 0 -> id2; 1 -> id2; else -> id1 }
            WordSlotRow(word, wordId, slot, otherA, otherB)
        }
    }
}

@androidx.compose.runtime.Composable
private fun WordSlotRow(
    word: RandomWordEntity?,
    wordId: Long,
    slot: Int,
    otherA: Long,
    otherB: Long
) {
    val ctx = LocalContext.current
    val bg = if (slot % 2 == 0) BgDeep else BgAlt

    // Tap word card → opens PopupActivity with this word AND instantly replaces
    // it in the background with the next unseen word from the queue.
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(bg)
            .clickable(
                if (wordId != 0L && word != null)
                    actionRunCallback<OpenAndReplaceCallback>(
                        actionParametersOf(
                            PARAM_SLOT      to slot,
                            PARAM_WORD_ID   to wordId,
                            PARAM_OTHER_A   to otherA,
                            PARAM_OTHER_B   to otherB,
                            PARAM_WORD_TEXT to word.word
                        )
                    )
                else
                    actionRunCallback<RefreshAllCallback>()
            )
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (word != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = word.word.replaceFirstChar { it.uppercaseChar() },
                            style = TextStyle(
                                color = White100,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        if (word.partOfSpeech.isNotBlank()) {
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                text = word.partOfSpeech,
                                style = TextStyle(color = GoldColor, fontSize = 9.sp),
                                maxLines = 1
                            )
                        }
                    }
                    val hint = word.teaser.ifBlank { word.definition.take(55) }
                    if (hint.isNotBlank()) {
                        Text(
                            text = hint,
                            style = TextStyle(color = White70, fontSize = 10.sp),
                            maxLines = 1
                        )
                    }
                } else {
                    Text("—", style = TextStyle(color = White40, fontSize = 14.sp))
                }
            }
            // Visual cue that the card is tappable
            Spacer(GlanceModifier.width(8.dp))
            Text("→", style = TextStyle(color = AccentCyan, fontSize = 16.sp))
        }
    }
}

// ── Action Callbacks ──────────────────────────────────────────────────────────

/**
 * Tap a word card → simultaneously:
 *  1. Opens PopupActivity with the tapped word (foreground, instant)
 *  2. Marks the word as seen and replaces it in the widget with the next
 *     unseen word from the queue (background, no extra user interaction)
 */
class OpenAndReplaceCallback : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        val slot     = parameters[PARAM_SLOT]      ?: return
        val wordId   = parameters[PARAM_WORD_ID]   ?: return
        val wordText = parameters[PARAM_WORD_TEXT] ?: return
        val otherA   = parameters[PARAM_OTHER_A]   ?: 0L
        val otherB   = parameters[PARAM_OTHER_B]   ?: 0L

        // 1 ── Open popup immediately (startActivity is safe from a callback)
        context.startActivity(
            Intent(context, PopupActivity::class.java).apply {
                putExtra("lookup_word", wordText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )

        // 2 ── Replace this slot with a fresh word in the background
        val dao = try {
            EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .randomWordDao()
        } catch (_: Exception) { return }

        dao.markSeen(wordId)

        val excluded = setOf(wordId, otherA, otherB)
        val nextWord = dao.getTopUnseen(10).firstOrNull { it.id !in excluded }

        if (dao.getUnseenCount() < 5) RandomWordWorker.scheduleOneTime(context)

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                when (slot) {
                    0 -> this[KEY_SLOT_0] = nextWord?.id ?: 0L
                    1 -> this[KEY_SLOT_1] = nextWord?.id ?: 0L
                    2 -> this[KEY_SLOT_2] = nextWord?.id ?: 0L
                }
            }
        }
        RandomWordWidget().update(context, glanceId)
    }
}

/** ↺ button → mark all 3 current words seen and load 3 fresh ones */
class RefreshAllCallback : ActionCallback {
    override suspend fun onAction(
        context: Context, glanceId: GlanceId, parameters: ActionParameters
    ) {
        val id0 = parameters[PARAM_WORD_ID] ?: 0L
        val id1 = parameters[PARAM_OTHER_A] ?: 0L
        val id2 = parameters[PARAM_OTHER_B] ?: 0L

        val dao = try {
            EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .randomWordDao()
        } catch (_: Exception) { return }

        if (id0 != 0L) dao.markSeen(id0)
        if (id1 != 0L) dao.markSeen(id1)
        if (id2 != 0L) dao.markSeen(id2)

        val pool = dao.getTopUnseen(10).iterator()
        val n0 = pool.nextOrNull()?.id ?: 0L
        val n1 = pool.nextOrNull()?.id ?: 0L
        val n2 = pool.nextOrNull()?.id ?: 0L

        if (dao.getUnseenCount() < 5) RandomWordWorker.scheduleOneTime(context)

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_SLOT_0] = n0
                this[KEY_SLOT_1] = n1
                this[KEY_SLOT_2] = n2
            }
        }
        RandomWordWidget().update(context, glanceId)
    }
}

// ── Receiver ──────────────────────────────────────────────────────────────────

class RandomWordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RandomWordWidget()
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun popupIntent(context: Context, word: String): Intent =
    Intent(context, PopupActivity::class.java).apply {
        putExtra("lookup_word", word)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

private fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null
