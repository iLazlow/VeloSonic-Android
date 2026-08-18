package de.ilazlow.velosonic.analysis

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val VOCAL_CLASSIFIER_SAMPLE_RATE = 16_000

data class SoundResult(
    val encodedLabels: String? = null,
    val isInstrumental: Boolean? = null,
    val vocalConfidence: Double? = null
)

private const val MODEL_ASSET = "yamnet.tflite"
private const val VOCAL_THRESHOLD = 0.15
private const val LABEL_MIN_CONFIDENCE = 0.01
private const val LABEL_REPORT_THRESHOLD = 0.04
private const val MAX_LABELS = 25

/**
 * Android substitute for Apple's on-device SoundAnalysis (`SNClassifySoundRequest`) — runs
 * Google's YAMNet (the same 521-label AudioSet vocabulary category) via MediaPipe's high-level
 * `AudioClassifier` task, which owns YAMNet's internal mel-spectrogram framing itself (no manual
 * windowing/hop bookkeeping needed, unlike driving the raw TFLite `Interpreter` directly).
 * YAMNet's vocabulary includes "Singing"-family labels close enough to Apple's own
 * "singing"/"vocal" substring match that iOS's exact isInstrumental derivation carries over
 * unchanged — see [classify].
 *
 * Input must already be mono Float32 @[VOCAL_CLASSIFIER_SAMPLE_RATE] (YAMNet's native rate,
 * distinct from the 22050Hz buffer [AudioSampleLoader] produces for BPM/key — callers resample
 * once more via [AudioAnalysisMath.resampleLinear] for this step only).
 */
@Singleton
class VocalClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val classifier: AudioClassifier by lazy {
        val baseOptions = BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build()
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .build()
        AudioClassifier.createFromOptions(context, options)
    }

    fun classify(samples16k: FloatArray): SoundResult {
        if (samples16k.isEmpty()) return SoundResult()

        val format = AudioData.AudioDataFormat.builder()
            .setNumOfChannels(1)
            .setSampleRate(VOCAL_CLASSIFIER_SAMPLE_RATE.toFloat())
            .build()
        val audioData = AudioData.create(format, samples16k.size)
        audioData.load(samples16k)

        // classify() returns one AudioClassifierResult for the whole clip, itself holding one
        // ClassificationResult per internal window YAMNet sliced the clip into — not a list of
        // per-call results the way the naming might suggest.
        val result = classifier.classify(audioData)

        // label -> [confidenceSum, windowCount], mirrors _VeloSonicSoundAnalysisObserver's
        // running (sum, count) accumulator averaged across every classification window.
        val accumulator = HashMap<String, DoubleArray>()
        for (classificationResult in result.classificationResults()) {
            val categories = classificationResult.classifications().firstOrNull()?.categories() ?: continue
            for (category in categories) {
                val confidence = category.score().toDouble()
                if (confidence <= LABEL_MIN_CONFIDENCE) continue
                val name = category.categoryName() ?: continue
                val entry = accumulator.getOrPut(name) { doubleArrayOf(0.0, 0.0) }
                entry[0] += confidence
                entry[1] += 1.0
            }
        }

        val topLabels = accumulator.entries
            .map { (label, sumCount) -> label to (sumCount[0] / sumCount[1]) }
            .filter { it.second >= LABEL_REPORT_THRESHOLD }
            .sortedByDescending { it.second }
            .take(MAX_LABELS)

        val vocalScore = topLabels.firstOrNull { (label, _) ->
            val lower = label.lowercase()
            lower.contains("singing") || lower.contains("vocal")
        }?.second ?: 0.0

        val encoded = topLabels.joinToString(",") { (label, confidence) ->
            "$label|${"%.4f".format(confidence)}"
        }

        return SoundResult(
            encodedLabels = encoded.ifEmpty { null },
            isInstrumental = vocalScore < VOCAL_THRESHOLD,
            vocalConfidence = vocalScore
        )
    }
}
