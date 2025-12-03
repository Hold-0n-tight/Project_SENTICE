package com.example.gcs

import android.content.Context
import android.util.Log
import com.google.api.gax.rpc.ClientStream
import com.google.api.gax.rpc.ResponseObserver
import com.google.api.gax.rpc.StreamController
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.SpeechSettings
import com.google.cloud.speech.v1.StreamingRecognitionConfig
import com.google.cloud.speech.v1.StreamingRecognizeRequest
import com.google.cloud.speech.v1.StreamingRecognizeResponse
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoogleSpeechToText(
    private val context: Context,
    private val sampleRate: Int = 16000
) {

    private var speechClient: SpeechClient? = null
    private var requestObserver: ClientStream<StreamingRecognizeRequest>? = null

    fun startStreaming(
        onResult: (String, Boolean) -> Unit,  // (텍스트, 최종여부)
        onError: (Throwable) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. SpeechClient 초기화
                val credentials = GoogleCloudAuth.getCredentials(context)
                val settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider { credentials }
                    .build()

                speechClient = SpeechClient.create(settings)

                // 2. RecognitionConfig 설정
                val recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(sampleRate)
                    .setLanguageCode("ko-KR")
                    .build()

                // 3. StreamingRecognitionConfig 설정
                val streamingConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(recognitionConfig)
                    .setInterimResults(true) // 중간 결과 받기
                    .build()

                // 4. ResponseObserver 생성
                val responseObserver = object : ResponseObserver<StreamingRecognizeResponse> {
                    override fun onStart(controller: StreamController) {
                        Log.d("STT", "스트리밍 시작")
                    }

                    override fun onResponse(response: StreamingRecognizeResponse) {
                        if (response.resultsCount > 0) {
                            val result = response.getResults(0)
                            val transcript = result.getAlternatives(0).transcript
                            val isFinal = result.isFinal

                            // UI 스레드에서 콜백 호출
                            CoroutineScope(Dispatchers.Main).launch {
                                onResult(transcript, isFinal)
                            }
                        }
                    }

                    override fun onError(t: Throwable) {
                        Log.e("STT", "에러 발생", t)
                        CoroutineScope(Dispatchers.Main).launch {
                            onError(t)
                        }
                    }

                    override fun onComplete() {
                        Log.d("STT", "스트리밍 완료")
                    }
                }

                // 5. 스트리밍 시작
                requestObserver = speechClient?.streamingRecognizeCallable()
                    ?.splitCall(responseObserver)

                // 6. 첫 요청 (Config만 전송)
                val initialRequest = StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingConfig)
                    .build()

                requestObserver?.send(initialRequest)

            } catch (e: Exception) {
                Log.e("STT", "초기화 실패", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun sendAudioData(audioData: ByteArray) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioBytes = ByteString.copyFrom(audioData)
                val request = StreamingRecognizeRequest.newBuilder()
                    .setAudioContent(audioBytes)
                    .build()

                requestObserver?.send(request)
            } catch (e: Exception) {
                Log.e("STT", "오디오 전송 실패", e)
            }
        }
    }

    fun stopStreaming() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 약간의 딜레이 후 종료 (마지막 결과 받기 위해)
                delay(300)
                requestObserver?.closeSend()
                speechClient?.close()
            } catch (e: Exception) {
                Log.e("STT", "스트림 종료 실패", e)
            }
        }
    }
}