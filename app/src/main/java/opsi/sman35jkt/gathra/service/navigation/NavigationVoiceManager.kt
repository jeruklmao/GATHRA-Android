package opsi.sman35jkt.gathra.service.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Owns one TextToSpeech instance for one service lifetime.
 */
class NavigationVoiceManager(
    context: Context,
    private val onIndonesianUnavailable: () -> Unit,
) {
    private val applicationContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var ready = false
    private var muted = false
    private var unavailableReported = false
    private var pendingUtterance: PendingUtterance? = null

    init {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            val engine = textToSpeech
            if (status != TextToSpeech.SUCCESS || engine == null) {
                pendingUtterance = null
                reportUnavailable()
                return@TextToSpeech
            }
            val languageResult = engine.setLanguage(
                Locale.forLanguageTag(INDONESIAN_LANGUAGE_TAG),
            )
            ready = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) {
                pendingUtterance = null
                reportUnavailable()
            } else if (!muted) {
                pendingUtterance?.let { pending ->
                    speakNow(engine, pending.text, pending.utteranceId)
                }
                pendingUtterance = null
            }
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        if (muted) {
            pendingUtterance = null
            textToSpeech?.stop()
        }
    }

    fun speak(text: String, utteranceId: String) {
        if (muted || text.isBlank()) return
        val engine = textToSpeech
        if (!ready || engine == null) {
            pendingUtterance = PendingUtterance(text, utteranceId)
            return
        }
        speakNow(engine, text, utteranceId)
    }

    private fun speakNow(
        engine: TextToSpeech,
        text: String,
        utteranceId: String,
    ) {
        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId,
        )
    }

    fun shutdown() {
        ready = false
        pendingUtterance = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun reportUnavailable() {
        if (unavailableReported) return
        unavailableReported = true
        onIndonesianUnavailable()
    }

    private companion object {
        const val INDONESIAN_LANGUAGE_TAG = "id-ID"
    }

    private data class PendingUtterance(
        val text: String,
        val utteranceId: String,
    )
}
