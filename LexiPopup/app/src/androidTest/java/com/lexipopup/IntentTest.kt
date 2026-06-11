package com.lexipopup

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lexipopup.presentation.popup.PopupActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentTest {

    @Test
    fun processTextIntentExtractsWord() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PopupActivity::class.java).apply {
            action = Intent.ACTION_PROCESS_TEXT
            putExtra(Intent.EXTRA_PROCESS_TEXT, "ephemeral")
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }
        val word = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        assertNotNull(word)
        assertEquals("ephemeral", word?.trim())
    }

    @Test
    fun manualSearchModeIntent() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PopupActivity::class.java).apply {
            putExtra("mode", "manual_search")
        }
        assertEquals("manual_search", intent.getStringExtra("mode"))
    }

    @Test
    fun deepLinkWordExtraction() {
        val intent = Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse("lexipopup://lookup?word=serendipity"))
        val word = intent.data?.getQueryParameter("word")
        assertEquals("serendipity", word)
    }
}
