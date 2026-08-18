package de.ilazlow.velosonic.analysis

import com.google.mlkit.nl.languageid.IdentifiedLanguage
import com.google.mlkit.nl.languageid.LanguageIdentification
import de.ilazlow.velosonic.data.db.TrackEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LanguageResult(val language: String? = null, val confidence: Double = 0.0)

private const val UNDETERMINED = "und"

/**
 * Android substitute for iOS's `NLLanguageRecognizer` — same metadata-only approach (title +
 * artist + album text, never audio/lyrics) via ML Kit's on-device Language Identification.
 * Mirrors `detectLanguageFromMetadata` exactly: joins the three fields with a space, rejects the
 * "undetermined" result, and reports the single highest-confidence candidate. Persisted
 * `languageSource` is always `"metadata"` when a language is found, same as iOS, signalling this
 * heuristic's origin distinctly from any hypothetical future audio/lyrics-based source.
 */
@Singleton
class LanguageDetector @Inject constructor() {
    private val identifier by lazy { LanguageIdentification.getClient() }

    suspend fun detect(track: TrackEntity): LanguageResult {
        val text = listOf(track.title, track.artistName, track.albumName.orEmpty()).joinToString(" ")
        if (text.isBlank()) return LanguageResult()

        val languages = suspendCancellableCoroutine<List<IdentifiedLanguage>> { cont ->
            identifier.identifyPossibleLanguages(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

        val top = languages.maxByOrNull { it.confidence } ?: return LanguageResult()
        if (top.languageTag == UNDETERMINED) return LanguageResult()
        return LanguageResult(language = top.languageTag, confidence = top.confidence.toDouble())
    }
}
