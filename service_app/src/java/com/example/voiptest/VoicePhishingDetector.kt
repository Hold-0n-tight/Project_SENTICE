package com.example.voiptest

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite 기반 보이스피싱 탐지 클래스
 *이걸
 * Bidirectional LSTM 모델을 사용하여 대화 텍스트에서 보이스피싱 패턴을 탐지합니다.
 *
 * 모델 정보:
 * - 입력: 한국어 대화 텍스트 (최대 100 토큰)
 * - 출력: 보이스피싱 확률 (0.0 ~ 1.0)
 * - 어휘 크기: 10,000 단어
 */
class VoicePhishingDetector(private val context: Context) {

    companion object {
        private const val TAG = "VoicePhishingDetector"
        private const val MODEL_FILE = "voicephishing_model_quantized.tflite"
        private const val TOKENIZER_FILE = "tokenizer.json"
    }

    // TFLite 인터프리터
    private lateinit var interpreter: Interpreter

    // Statistics
    private var inferenceCount = 0L
    private var totalInferenceTimeMs = 0L
    private var totalCpuTimeMs = 0L

    // 토크나이저 정보
    private lateinit var wordIndex: Map<String, Int>
    private var maxLength: Int = 100
    private var numWords: Int = 10000

    init {
        try {
            loadTokenizer()
            loadModel()
            Log.d(TAG, "VoicePhishingDetector initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VoicePhishingDetector", e)
            throw e
        }
    }

    /**
     * tokenizer.json 파일을 로드하여 word_index 맵 생성
     */
    private fun loadTokenizer() {
        try {
            val jsonString = context.assets.open(TOKENIZER_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            // word_index 파싱
            val wordIndexJson = jsonObject.getJSONObject("word_index")
            wordIndex = mutableMapOf<String, Int>().apply {
                wordIndexJson.keys().forEach { key ->
                    put(key, wordIndexJson.getInt(key))
                }
            }

            // max_length, num_words 파싱
            maxLength = jsonObject.optInt("max_length", 100)
            numWords = jsonObject.optInt("num_words", 10000)

            Log.d(TAG, "Tokenizer loaded: vocab_size=${wordIndex.size}, max_length=$maxLength, num_words=$numWords")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tokenizer", e)
            throw e
        }
    }

    /**
     * TFLite 모델 파일을 메모리에 매핑하여 로드
     */
    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile(MODEL_FILE)

            // TFLite Interpreter 옵션 설정
            val options = Interpreter.Options().apply {
                setNumThreads(4)  // CPU 스레드 수
            }

            interpreter = Interpreter(modelBuffer, options)

            Log.d(TAG, "TFLite model loaded: $MODEL_FILE")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model", e)
            throw e
        }
    }

    /**
     * Assets에서 모델 파일을 MappedByteBuffer로 로드
     */
    private fun loadModelFile(filename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * 텍스트를 전처리하여 모델 입력 형식으로 변환
     *
     * @param text 입력 텍스트 (한국어 대화)
     * @return Float 배열 (1 x maxLength)
     */
    private fun preprocessText(text: String): Array<FloatArray> {
        // 1. 텍스트를 공백 기준으로 토큰화
        val tokens = text.trim().split("\\s+".toRegex())

        // 2. 토큰을 정수 시퀀스로 변환
        val sequence = mutableListOf<Int>()
        for (token in tokens) {
            val index = wordIndex[token] ?: wordIndex["<OOV>"] ?: 1  // OOV 처리
            if (index < numWords) {  // num_words 제한
                sequence.add(index)
            }
        }

        // 3. maxLength로 패딩/자르기
        val paddedSequence = FloatArray(maxLength) { 0f }
        val copyLength = minOf(sequence.size, maxLength)
        for (i in 0 until copyLength) {
            paddedSequence[i] = sequence[i].toFloat()
        }

        // 4. 2D 배열로 변환 (배치 크기 1)
        return arrayOf(paddedSequence)
    }

    /**
     * 대화 텍스트에 대한 보이스피싱 여부 예측
     *
     * @param dialogue 누적된 대화 텍스트 (예: "LOCAL: 안녕하세요 REMOTE: 네 안녕하세요 ...")
     * @return Pair<Boolean, Float> - (보이스피싱 여부, 확률)
     */
    fun predict(dialogue: String): Pair<Boolean, Float> {
        try {
            val startTime = System.currentTimeMillis()
            val startCpuTime = SystemClock.currentThreadTimeMillis()

            // 1. 전처리
            val inputArray = preprocessText(dialogue)

            // 2. 출력 버퍼 준비 (1 x 1 크기)
            val outputArray = Array(1) { FloatArray(1) }

            // 3. TFLite 추론
            interpreter.run(inputArray, outputArray)

            val endCpuTime = SystemClock.currentThreadTimeMillis()
            val endTime = System.currentTimeMillis()

            val inferenceTime = endTime - startTime
            val cpuTime = (endCpuTime - startCpuTime) / 1_000_000 // ns to ms

            // 실시간 성능 로그
            Log.i(TAG, "Real-time - Inference Time: ${inferenceTime}ms, CPU Time: ${cpuTime}ms")

            // 통계 업데이트
            synchronized(this) {
                inferenceCount++
                totalInferenceTimeMs += inferenceTime
                totalCpuTimeMs += cpuTime
            }

            // 4. Sigmoid 출력값 (보이스피싱 확률)
            val probability = outputArray[0][0]

            // 5. 임계값 0.5 기준 분류
            val isPhishing = probability > 0.5f

            Log.d(TAG, "Prediction: isPhishing=$isPhishing, probability=$probability")
            Log.d(TAG, "Input dialogue length: ${dialogue.length} chars")

            return Pair(isPhishing, probability)

        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            // 에러 시 안전하게 정상으로 간주
            return Pair(false, 0.0f)
        }
    }

    /**
     * 리소스 정리
     */
    fun cleanup() {
        try {
            interpreter.close()
            Log.d(TAG, "VoicePhishingDetector resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup resources", e)
        }
    }

    /**
     * 통계 리포트를 생성하고 카운터를 리셋합니다.
     */
    fun getAndResetStats(): String {
        synchronized(this) {
            val avgInferenceTime = if (inferenceCount > 0) totalInferenceTimeMs / inferenceCount else 0
            val report = "VoicePhishingDetector Report: Ran $inferenceCount times | " +
                         "Avg Inference Time: ${avgInferenceTime}ms | " +
                         "Total CPU Time: ${totalCpuTimeMs}ms"

            // 통계 리셋
            inferenceCount = 0L
            totalInferenceTimeMs = 0L
            totalCpuTimeMs = 0L

            return report
        }
    }
}
