package de.ilazlow.velosonic.analysis

import de.ilazlow.velosonic.data.db.TrackAnalysisEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-track pipeline orchestrator — mirrors AudioAnalysisManager.swift's `analyzeSnapshot`: load
 * a short decode window once, then run BPM/key/sound-classification/language detection and merge
 * into one row. Runs sequentially rather than iOS's `async let` fan-out (BPM+key+sound each in
 * their own detached task) — a single track's decode+FFT+TFLite-inference pipeline is already
 * well under a second on typical hardware, so fanning out three sub-steps wouldn't meaningfully
 * shorten it; concurrency instead happens at the across-tracks level (see the analysis worker's
 * semaphore-bounded loop).
 */
@Singleton
class AudioAnalysisEngine @Inject constructor(
    private val sampleLoader: AudioSampleLoader,
    private val vocalClassifier: VocalClassifier,
    private val languageDetector: LanguageDetector
) {
    /** Throws [AnalysisTooShortException] (caller should persist [tooShortStub], not the
     *  failure skip-queue) or any other [Exception] (caller should record a skip-queue
     *  failure) — matches AudioAnalysisManager.swift's `analyzeSnapshot` error handling. */
    suspend fun analyze(track: TrackEntity): TrackAnalysisEntity {
        val samples = sampleLoader.loadSamples(track)

        val acoustic = AcousticAnalyzer.analyze(samples, ANALYSIS_TARGET_SAMPLE_RATE.toDouble())
        val key = KeyAnalyzer.detectKey(samples, ANALYSIS_TARGET_SAMPLE_RATE.toDouble())
        val samples16k = AudioAnalysisMath.resampleLinear(samples, ANALYSIS_TARGET_SAMPLE_RATE, VOCAL_CLASSIFIER_SAMPLE_RATE)
        val sound = runCatching { vocalClassifier.classify(samples16k) }.getOrDefault(SoundResult())
        val language = languageDetector.detect(track)

        return TrackAnalysisEntity(
            id = track.id,
            trackId = track.id,
            serverHost = track.serverHost,
            analyzedAt = System.currentTimeMillis(),
            audioSourceWasLocal = false,
            bpm = acoustic.bpm,
            bpmConfidence = acoustic.bpmConfidence,
            energy = acoustic.energy,
            loudnessDb = acoustic.loudnessDb,
            danceability = acoustic.danceability,
            acousticness = acoustic.acousticness,
            key = key.key,
            keyConfidence = key.confidence,
            mode = key.mode,
            soundLabels = sound.encodedLabels,
            isInstrumental = sound.isInstrumental,
            vocalConfidence = sound.vocalConfidence,
            detectedLanguage = language.language,
            languageConfidence = language.confidence.takeIf { language.language != null },
            languageSource = if (language.language != null) "metadata" else null
        )
    }

    /** Empty-but-persisted stub for a too-short track — matches iOS's special case where
     *  `AnalysisError.tooShort` writes a stub with all-nil acoustic fields rather than entering
     *  the failure skip-queue, so the track counts as "analyzed" and is never retried. */
    fun tooShortStub(track: TrackEntity): TrackAnalysisEntity = TrackAnalysisEntity(
        id = track.id,
        trackId = track.id,
        serverHost = track.serverHost,
        analyzedAt = System.currentTimeMillis()
    )
}
