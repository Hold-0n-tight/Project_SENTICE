package com.example.voiptest

import android.content.Context
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicBoolean
import android.os.SystemClock

/**
 * 딥페이크 오디오 탐지 클래스
 * PTL 모델을 사용하여 실시간 오디오 스트림에서 딥페이크를 탐지합니다.
 * 튬블링 윈도우 방식: 3초 버퍼를 채우고 분석 후 비움 (겹침 없음)
 */
class DeepfakeDetector(
    private val context: Context,
    private val onResult: (isReal: Boolean, confidence: Float) -> Unit
) {
    private var model: Module? = null
    private val audioBuffer = mutableListOf<Short>()
    private val pendingBuffer = mutableListOf<Short>()  // 분석 중에 들어오는 샘플 임시 저장
    private val isAnalyzing = AtomicBoolean(false)

    // Statistics
    private var inferenceCount = 0L
    private var totalInferenceTimeMs = 0L
    private var totalCpuTimeMs = 0L

    companion object {
        private const val TAG = "DeepfakeDetector"
        private const val MODEL_NAME = "deepfake_audio_detection_quantized.ptl"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE_SECONDS = 2
        private const val TARGET_BUFFER_SIZE = SAMPLE_RATE * BUFFER_SIZE_SECONDS // 48000 샘플 (3초)

        // VAD (Voice Activity Detection) 임계값
        // 측정값: 무음 평균 4.36, 음성 평균 108.4
        // 임계값을 음성 평균의 약 65%로 설정하여 노이즈 필터링 강화
        private const val RMS_THRESHOLD = 70.0

        // ⭐ 추가: 3초 버퍼 전체의 RMS가 이 값보다 낮으면 분석을 SKIP
        private const val FINAL_BUFFER_RMS_THRESHOLD = 15.0
    }

    init {
        loadModel()
    }

    /**
     * Assets에서 PTL 모델을 로드합니다.
     */
    private fun loadModel() {
        try {
            Log.d(TAG, "모델 로드 시작: $MODEL_NAME")

            // Assets에서 임시 파일로 복사
            val modelFile = assetFilePath(context, MODEL_NAME)

            // PyTorch Lite 모델 로드
            model = LiteModuleLoader.load(modelFile)

            Log.d(TAG, "✅ 모델 로드 성공!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 모델 로드 실패", e)
        }
    }

    /**
     * Assets 파일을 임시 파일로 복사하여 경로를 반환합니다.
     */
    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)

        // 이미 파일이 존재하면 바로 반환
        if (file.exists()) {
            return file.absolutePath
        }

        // Assets에서 파일 복사
        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }

        return file.absolutePath
    }

    /**
     * remoteSttPort에서 받은 ByteArray를 버퍼에 추가합니다.
     * 튬블링 윈도우 방식: 분석 중이면 pendingBuffer에, 아니면 audioBuffer에 저장
     * @param byteArray 16-bit PCM 오디오 데이터
     */
    fun addAudioChunk(byteArray: ByteArray) {
        // ByteArray를 Short 배열로 변환 (16-bit PCM)
        val shortArray = ByteBuffer.wrap(byteArray)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        // ✅ 1단계: RMS 에너지 레벨 측정
        val rms = calculateRMS(shortArray)

        // ✅ 2단계: 음성 활동 감지 (VAD) - 임시 비활성화
        // if (!isVoiceActive(rms)) {
        //     Log.d(TAG, "⏭️ 무음 감지 (RMS: %.2f < %.2f) → 분석 SKIP".format(rms, RMS_THRESHOLD))
        //     return
        // }

        // ByteArray를 Short 배열로 다시 변환 (position이 이미 이동했으므로)
        shortArray.rewind()

        synchronized(audioBuffer) {
            if (isAnalyzing.get()) {
                // ✅ 분석 중이면 임시 버퍼에 저장
                while (shortArray.hasRemaining()) {
                    pendingBuffer.add(shortArray.get())
                }
                Log.d("deepVoice", "🔄 분석 중... pendingBuffer에 추가 (RMS: %.2f, pending 크기: ${pendingBuffer.size})".format(rms))
            } else {
                // ✅ 분석 안 할 때는 메인 버퍼에 저장
                while (shortArray.hasRemaining()) {
                    audioBuffer.add(shortArray.get())
                }
                Log.d("deepVoice", "🎤 audioBuffer에 추가 (RMS: %.2f, buffer 크기: ${audioBuffer.size}/${TARGET_BUFFER_SIZE})".format(rms))
            }
        }

        // 버퍼가 3초치 쌓였는지 확인
        analyzeIfReady()
    }

    /**
     * RMS 에너지 레벨을 기반으로 음성 활동을 감지합니다.
     * @param rms RMS 에너지 값
     * @return true: 음성 있음, false: 무음
     */
    private fun isVoiceActive(rms: Double): Boolean {
        return rms > RMS_THRESHOLD
    }

    /**
     * RMS (Root Mean Square) 에너지 계산
     * @param shortBuffer Short 배열
     * @return RMS 값
     */
    private fun calculateRMS(shortBuffer: ShortBuffer): Double {
        shortBuffer.rewind() // 처음부터 읽기 시작

        var sum = 0.0
        var count = 0

        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get()
            sum += (sample * sample).toDouble()
            count++
        }

        return if (count > 0) {
            kotlin.math.sqrt(sum / count)
        } else {
            0.0
        }
    }

    /**
     * 버퍼가 3초치(48000 샘플) 쌓였으면 분석을 시작합니다.
     * 튬블링 윈도우 방식: 분석 시작 시 버퍼를 비움
     */
    private fun analyzeIfReady() {
        synchronized(audioBuffer) {
            // 버퍼가 충분히 쌓이지 않았으면 리턴
            if (audioBuffer.size < TARGET_BUFFER_SIZE) {
                return
            }

            // 이미 분석 중이면 SKIP
            if (isAnalyzing.get()) {
                return
            }

            // 분석 시작
            isAnalyzing.set(true)

            // 버퍼의 스냅샷을 만듦 (정확히 3초 분량)
            val audioData = audioBuffer.toShortArray()

            // ✅ 튬블링 윈도우: 분석 시작 시 버퍼를 비움!
            audioBuffer.clear()

            Log.d("deepVoice", "🎤 튬블링 윈도우 분석 시작 (샘플 수: ${audioData.size}, 버퍼 비움)")

            // 비동기로 추론 실행
            runInference(audioData)
        }
    }

    /**
     * 3초 버퍼가 대부분 무음인지 정교하게 판단합니다.
     * @param audioData 3초 분량의 오디오 데이터 (ShortArray)
     * @return 두 가지 조건을 모두 만족하면 true (대부분 무음), 아니면 false
     */
    private fun isBufferMostlySilent(audioData: ShortArray): Boolean {
        // --- 하이퍼파라미터 ---
        // 개별 청크의 RMS가 이 값보다 낮으면 '무음 청크'로 간주
        val INDIVIDUAL_RMS_THRESHOLD = 5.0
        // 3초 버퍼 내 '무음 청크'의 비율이 이 값을 초과해야 함
        val SILENT_CHUNK_RATIO_THRESHOLD = 2.0 / 3.0
        // 3초 버퍼 전체의 평균 RMS가 이 값보다 낮아야 함
        val AVERAGE_RMS_THRESHOLD = 15.0
        // RMS를 계산할 개별 청크의 크기 (20ms = 320 샘플)
        val CHUNK_SIZE_SAMPLES = 320
        // ---

        if (audioData.isEmpty()) return true

        val numChunks = audioData.size / CHUNK_SIZE_SAMPLES
        if (numChunks == 0) return true

        var silentChunkCount = 0
        var totalRmsSum = 0.0

        for (i in 0 until numChunks) {
            val chunkStart = i * CHUNK_SIZE_SAMPLES
            val chunkEnd = chunkStart + CHUNK_SIZE_SAMPLES
            val chunk = audioData.sliceArray(chunkStart until chunkEnd)
            val chunkBuffer = ShortBuffer.wrap(chunk)

            val chunkRms = calculateRMS(chunkBuffer)
            totalRmsSum += chunkRms

            if (chunkRms < INDIVIDUAL_RMS_THRESHOLD) {
                silentChunkCount++
            }
        }

        val averageRms = totalRmsSum / numChunks
        val silentRatio = silentChunkCount.toDouble() / numChunks

        Log.d(TAG, "VAD Stats: Avg RMS=%.2f, Silent Ratio=%.2f (%d/%d)".format(averageRms, silentRatio, silentChunkCount, numChunks))

        // 두 가지 조건 모두 만족 시 '대부분 무음'으로 판단
        val isMostlySilent = averageRms < AVERAGE_RMS_THRESHOLD && silentRatio > SILENT_CHUNK_RATIO_THRESHOLD
        if (isMostlySilent) {
            Log.d(TAG, "🔇 3초 버퍼가 대부분 무음으로 판단되어 분석을 건너뜁니다.")
        }

        return isMostlySilent
    }

    /**
     * PTL 모델을 사용하여 비동기로 추론을 실행합니다.
     */
    private fun runInference(audioData: ShortArray) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ✅ [수정] 정교한 무음 구간 스킵 로직
                if (isBufferMostlySilent(audioData)) {
                    // onResult 콜백을 호출하지 않으므로, UI는 변경되지 않고 이전 상태를 유지함
                    return@launch
                }
                // ✅ 무음 구간 스킵 로직 끝

                if (model == null) {
                    Log.e(TAG, "❌ 모델이 로드되지 않았습니다.")
                    isAnalyzing.set(false)
                    return@launch
                }

                // 1. Short -> Float 변환 및 정규화
                val floatArray = FloatArray(audioData.size)
                for (i in audioData.indices) {
                    floatArray[i] = audioData[i].toFloat() / Short.MAX_VALUE // -1.0 ~ 1.0 정규화
                }

                // 2. Zero-mean, Unit-variance 정규화
                // ✅ PyTorch tensor.std()와 동일하게 unbiased=True (n-1로 나눔) 적용
                val mean = floatArray.average().toFloat()

                // ✅ Bessel's correction 적용: n-1로 나눔 (unbiased variance)
                var sumSquaredDiff = 0.0
                for (i in floatArray.indices) {
                    val diff = floatArray[i] - mean
                    sumSquaredDiff += (diff * diff).toDouble()
                }

                val n = floatArray.size
                val variance = if (n > 1) {
                    (sumSquaredDiff / (n - 1)).toFloat()  // ← n-1로 나눔 (unbiased)
                } else {
                    0f
                }
                val std = kotlin.math.sqrt(variance)

                // 정규화 적용 (Python Colab과 동일)
                for (i in floatArray.indices) {
                    floatArray[i] = (floatArray[i] - mean) / (std + 1e-5f)
                }

                Log.d(TAG, "📊 정규화 통계 (unbiased): mean=$mean, std=$std, n=$n")

                // 3. Tensor 생성 [1, 48000] (3초 분량)
                val inputTensor = Tensor.fromBlob(
                    floatArray,
                    longArrayOf(1, audioData.size.toLong())
                )

                // 4. 모델 추론
                val startTime = System.currentTimeMillis()
                val startCpuTime = SystemClock.currentThreadTimeMillis()

                val outputTensor = model!!.forward(IValue.from(inputTensor)).toTensor()

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

                val scores = outputTensor.dataAsFloatArray // [1, 2] -> [real_score, fake_score]

                // 5. Softmax 적용
                val expSum = kotlin.math.exp(scores[0].toDouble()) + kotlin.math.exp(scores[1].toDouble())
                val probReal = (kotlin.math.exp(scores[0].toDouble()) / expSum).toFloat()
                val probFake = (kotlin.math.exp(scores[1].toDouble()) / expSum).toFloat()

                // 6. 결과 판정 (확률이 높은 쪽 선택)
                val isReal = probReal > probFake
                val confidence = if (isReal) probReal else probFake

                Log.d("deepFakeCheck", "📊 분석 결과: ${if (isReal) "✅ 진짜" else "⚠️ 딥페이크"} (신뢰도: ${(confidence * 100).toInt()}%)")
                Log.d("deepVoice", "   Real: ${(probReal * 100).toInt()}%, Fake: ${(probFake * 100).toInt()}%")

                // 7. 메인 스레드에서 콜백 호출
                withContext(Dispatchers.Main) {
                    onResult(isReal, confidence)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 추론 실패", e)
            } finally {
                synchronized(audioBuffer) {
                    // ✅ 분석 완료 후 pendingBuffer → audioBuffer 이동
                    if (pendingBuffer.isNotEmpty()) {
                        audioBuffer.addAll(pendingBuffer)
                        Log.d("deepVoice", "✅ 분석 완료! pendingBuffer(${pendingBuffer.size} 샘플)를 audioBuffer로 이동")
                        pendingBuffer.clear()
                    } else {
                        Log.d("deepVoice", "✅ 분석 완료! (pendingBuffer 비어있음)")
                    }

                    // 분석 완료, 플래그 해제
                    isAnalyzing.set(false)
                }
            }
        }
    }

    /**
     * 리소스 정리
     */
    fun cleanup() {
        try {
            model?.destroy()
            audioBuffer.clear()
            pendingBuffer.clear()
            Log.d(TAG, "DeepfakeDetector 리소스 정리 완료")
        } catch (e: Exception) {
            Log.e(TAG, "리소스 정리 중 오류", e)
        }
    }

    /**
     * 통계 리포트를 생성하고 카운터를 리셋합니다.
     */
    fun getAndResetStats(): String {
        synchronized(this) {
            val avgInferenceTime = if (inferenceCount > 0) totalInferenceTimeMs / inferenceCount else 0
            val report = "DeepfakeDetector Report: Ran $inferenceCount times | " +
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
