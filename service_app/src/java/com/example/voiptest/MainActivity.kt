package com.example.voiptest

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.example.gcs.GoogleSpeechToText
import com.example.voiptest.databinding.ActivityMainBinding
import org.pjsip.pjsua2.*
import android.app.ActivityManager
import android.os.BatteryManager
import kotlin.jvm.java
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ========== 보이스피싱 보호 모드 정의 ==========

/**
 * 보이스피싱 보호 모드
 */
enum class PhishingProtectionMode {
    HARD,    // 하드 모드: 확인 즉시 10초 뮤트
    NORMAL   // 일반 모드: 개인정보 발화 감지 시 뮤트
}

// ========== Fragment 인터페이스 정의 ==========

/**
 * HomeFragment에서 MainActivity로 이벤트를 전달하기 위한 인터페이스
 */
interface HomeFragmentListener {
    fun onCallButtonClicked(phoneNumber: String)
    fun onModeChanged(mode: PhishingProtectionMode)  // 모드 변경 콜백
    fun onSettingsButtonClicked()  // 설정 버튼 클릭
}

/**
 * CallFragment에서 MainActivity로 이벤트를 전달하기 위한 인터페이스
 */
interface CallFragmentListener {
    fun onHangupButtonClicked()
    fun onPhishingConfirmed()  // 보이스피싱 경고 확인 시
    fun onAutoMuteToggleClicked(isEnabled: Boolean) // 자동 음소거 스위치 클릭 시
}

// ========== MainActivity ==========

class MainActivity : AppCompatActivity(),
    HomeFragmentListener,
    CallFragmentListener {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val ep = Endpoint()
    private var acc: MyAccount? = null
    private var currentCall: MyCall? = null

    // Fragment 관리
    private var currentCallFragment: CallFragment? = null

    // STT 관련
    private var localSttPort: SttAudioPort? = null
    private var remoteSttPort: SttAudioPort? = null
    private lateinit var speechToTextLocal: GoogleSpeechToText
    private lateinit var speechToTextRemote: GoogleSpeechToText

    // Deepfake Detector
    private var deepfakeDetector: DeepfakeDetector? = null

    // VoicePhishing Detector
    private var voicePhishingDetector: VoicePhishingDetector? = null

    // ⭐ 추가: 딥페이크 연속 탐지 카운트 변수
    private var deepfakeDetectionCount = 0

    // 대화 이력 관리
    private val dialogueHistory = mutableListOf<DialogueTurn>()

    // 턴 완료 플래그
    private var lastLocalTurnCompleted = false
    private var lastRemoteTurnCompleted = false

    // 모델 실행 상태
    private var isPhishingModelRunning = false

    // 추가: 중간 결과 저장용
    private var lastFinalTextLocal = ""
    private var lastFinalTextRemote = ""

    // 딥페이크 경고 관련
    private var lastDeepfakeWarningTime = 0L
    private val DEEPFAKE_WARNING_COOLDOWN_MS = 10000L // 10초

    // 마이크 뮤트 관련 (방법 2: 무음 데이터 전송 방식)
    private var isMicrophoneMuted = false
    private var unmuteTimer: Runnable? = null

    // 보이스피싱 보호 모드
    private var protectionMode: PhishingProtectionMode = PhishingProtectionMode.NORMAL // 기본값: 일반 모드
    private var isPersonalInfoMonitoring = false // 개인정보 모니터링 활성화 여부
    private var isCurrentlyPhishing = false // 최신 보이스피싱 판정 결과 (true = 위험, false = 정상)
    private var isAutoMuteEnabled = true // 자동 음소거 기능 활성화 여부

    inner class MyAccount : Account() {
        override fun onRegState(prm: OnRegStateParam) {
            handler.post {
                val status = if (prm.code == pjsip_status_code.PJSIP_SC_OK) "Registered" else "Not Registered"
                // TODO: HomeFragment에 상태 전달하도록 수정 예정
                // binding.tvStatus.text = status
                Log.d("MainActivity", "Registration status: $status")
            }
        }

        override fun onIncomingCall(prm: OnIncomingCallParam) {
            val call = MyCall(this, prm.callId)
            val p = CallOpParam(true)
            p.statusCode = pjsip_status_code.PJSIP_SC_RINGING
            try {
                // 수신 통화 자동 응답
                call.answer(p)
                currentCall = call

                // CallFragment로 전환
                handler.post {
                    showCallFragment()
                }

                Log.d("MainActivity", "Incoming call answered and CallFragment shown")
            } catch (e: Exception) {
                Log.e("MainActivity", "onIncomingCall", e)
            }
        }
    }

    inner class MyCall(acc: Account, call_id: Int) : Call(acc, call_id) {
        private var micMedia: AudioMedia? = null
        private var callMedia: AudioMedia? = null

        fun setMute(isMuted: Boolean) {
            if (micMedia == null || callMedia == null) {
                Log.w("VoipTest", "setMute called before media is initialized.")
                return
            }

            try {
                if (isMuted) {
                    Log.d("VoipTest", "Stopping mic transmission to call.")
                    micMedia!!.stopTransmit(callMedia)
                } else {
                    Log.d("VoipTest", "Starting mic transmission to call.")
                    micMedia!!.startTransmit(callMedia)
                }
            } catch (e: Exception) {
                Log.e("VoipTest", "setMute failed", e)
            }
        }

        override fun onCallState(prm: org.pjsip.pjsua2.OnCallStateParam) {
            try {
                val ci = info
                val callState = ci.state

                // 통화 상태를 CallFragment로 전달
                handler.post {
                    val statusMessage = when (callState) {
                        org.pjsip.pjsua2.pjsip_inv_state.PJSIP_INV_STATE_CALLING -> "발신 중..."
                        org.pjsip.pjsua2.pjsip_inv_state.PJSIP_INV_STATE_INCOMING -> "수신 중..."
                        org.pjsip.pjsua2.pjsip_inv_state.PJSIP_INV_STATE_EARLY -> "연결 중..."
                        org.pjsip.pjsua2.pjsip_inv_state.PJSIP_INV_STATE_CONNECTING -> "연결 중..."
                        org.pjsip.pjsua2.pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> "통화 연결됨"
                        org.pjsip.pjsua2.pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> "통화 종료"
                        else -> "알 수 없음"
                    }
                    currentCallFragment?.updateCallStatus(statusMessage)
                    Log.d("MainActivity", "Call state: $statusMessage")
                }

                // 통화 종료 시 정리 및 HomeFragment로 복귀
                if (callState == org.pjsip.pjsua2.pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                    // ================= 종합 리소스 리포트 로깅 =================
                    Log.i("ResourceReport", "========== Call Ended: Final Resource Report ==========")
                    try {
                        // 1. 각 모델별 통계 리포트
                        voicePhishingDetector?.let { Log.i("ResourceReport", it.getAndResetStats()) }
                        deepfakeDetector?.let { Log.i("ResourceReport", it.getAndResetStats()) }

                        // 2. 앱 전체 메모리 사용량
                        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
                        val totalPss = memoryInfo.getOrNull(0)?.totalPss ?: -1 // in KB
                        Log.i("ResourceReport", "App Memory Usage: ${totalPss / 1024} MB (Total PSS)")

                        // 3. 배터리 잔량
                        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        Log.i("ResourceReport", "Final Battery Level: $batteryLevel%")

                    } catch (e: Exception) {
                        Log.e("ResourceReport", "Failed to generate resource report", e)
                    }
                    Log.i("ResourceReport", "======================================================")
                    // =========================================================

                    cleanupAudioPorts()

                    handler.postDelayed({
                        showHomeFragment()
                    }, 1000) // 1초 후 HomeFragment로 복귀
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "onCallState", e)
            }
        }

        override fun onCallMediaState(prm: org.pjsip.pjsua2.OnCallMediaStateParam) {
            try {
                val ci = info
                for (i in 0 until ci.media.size) {
                    val mi = ci.media.get(i)
                    if (mi.type == org.pjsip.pjsua2.pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                        (mi.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
                                mi.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD)) {

                        Log.d("VoipTest", "Audio Media is ACTIVE")
                    // ⭐ 수정: 멤버 변수에 할당
                    this.callMedia = AudioMedia.typecastFromMedia(getMedia(i.toLong()))
                    this.micMedia = Endpoint.instance().audDevManager().captureDevMedia

                    try {
                        // 마이크로부터 수신되는 오디오 레벨을 3.5배 증폭 (송신 볼륨 증가)
                        val amplificationFactor = 7f
                        callMedia!!.adjustRxLevel(amplificationFactor)
                        Log.d("VoipTest", "Applied microphone gain: ${amplificationFactor}x")
                    } catch (e: Exception) {
                        Log.e("VoipTest", "Failed to adjust microphone gain", e)
                    }

                    // 기존 오디오 전송 로직
                    callMedia!!.startTransmit(ep.audDevManager().playbackDevMedia)
                    micMedia!!.startTransmit(callMedia)

                    // 오디오 포킹 설정
                        try {
                            setupAudioForking()
                        } catch (e: Exception) {
                            Log.e("VoipTest", "Failed to setup audio forking", e)
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "onCallMediaState", e)
            }
        }

        private fun setupAudioForking() {
            try {
                Log.d("VoipTest", "=== Starting Audio Forking Setup ===")

                // STT 스트리밍 시작
                speechToTextLocal.startStreaming(
                    onResult = { transcript, isFinal ->
                        handler.post {
                            if (isFinal) {
                                // 최종 결과: 누적 저장
                                lastFinalTextLocal += "$transcript\n"
                                Log.d("STT", "Local 최종 결과: $transcript")

                                // CallFragment로 전달
                                currentCallFragment?.updateSttResult("LOCAL", transcript)

                                // ⭐ 일반 모드: 개인정보 모니터링 + 현재 보이스피싱 상태 체크
                                if (isPersonalInfoMonitoring && isCurrentlyPhishing) {
                                    val (detected, patternName) = detectPersonalInfo(transcript)
                                    if (detected) {
                                        Log.w("PersonalInfo", "개인정보 발화 감지! 패턴: $patternName - '$transcript'")
                                        // 마이크 10초 뮤트
                                        muteMicrophoneFor10Seconds()
                                        // ⭐ 모니터링은 계속 유지 (통화 종료까지 반복 감지)
                                        // TODO: UI 알림 (선택사항)
                                        // currentCallFragment?.showPersonalInfoWarning(patternName)
                                    }
                                } else if (isPersonalInfoMonitoring && !isCurrentlyPhishing) {
                                    // 개인정보 감지되더라도 현재 정상 판정이면 로그만 출력
                                    val (detected, patternName) = detectPersonalInfo(transcript)
                                    if (detected) {
                                        Log.d("PersonalInfo", "개인정보 발화 감지되었으나 현재 정상 상황으로 판정되어 뮤트하지 않음: $patternName")
                                    }
                                }

                                // 대화 이력에 추가
                                dialogueHistory.add(DialogueTurn("LOCAL", transcript, System.currentTimeMillis()))
                                lastLocalTurnCompleted = true
                                checkAndRunPhishingDetection()
                            } else {
                                // 중간 결과: [인식중] 표시
                                Log.d("STT", "Local 중간 결과: $transcript")
                                currentCallFragment?.updateSttResult("LOCAL", "[인식중] $transcript")
                            }
                        }
                    },
                    onError = { error ->
                        Log.e("STT", "Error during streaming", error)
                        handler.post {
                            currentCallFragment?.updateSttResult("LOCAL", "[에러: ${error.message}]")
                        }
                    }
                )

                // STT 스트리밍 시작
                speechToTextRemote.startStreaming(
                    onResult = { transcript, isFinal ->
                        handler.post {
                            if (isFinal) {
                                // 최종 결과: 누적 저장
                                lastFinalTextRemote += "$transcript\n"
                                Log.d("STT", "Remote 최종 결과: $transcript")

                                // CallFragment로 전달
                                currentCallFragment?.updateSttResult("REMOTE", transcript)

                                // 대화 이력에 추가
                                dialogueHistory.add(DialogueTurn("REMOTE", transcript, System.currentTimeMillis()))
                                lastRemoteTurnCompleted = true
                                checkAndRunPhishingDetection()
                            } else {
                                // 중간 결과: [인식중] 표시
                                Log.d("STT", "Remote 중간 결과: $transcript")
                                currentCallFragment?.updateSttResult("REMOTE", "[인식중] $transcript")
                            }
                        }
                    },
                    onError = { error ->
                        Log.e("STT", "Error during streaming", error)
                        handler.post {
                            currentCallFragment?.updateSttResult("REMOTE", "[에러: ${error.message}]")
                        }
                    }
                )



                val micMedia = Endpoint.instance().audDevManager().captureDevMedia
                val callMedia = getAudioMedia(-1)

                val clockRate = 16000L
                val channelCount = 1L
                val bitsPerSample = 16L
                val frameTimeUsec = 20000L

                val format = MediaFormatAudio().apply {
                    type = org.pjsip.pjsua2.pjmedia_type.PJMEDIA_TYPE_AUDIO
                    this.clockRate = clockRate
                    this.channelCount = channelCount
                    this.bitsPerSample = bitsPerSample
                    this.frameTimeUsec = frameTimeUsec
                    avgBps = 256000
                    maxBps = 256000
                }

                // STT 포트 생성
                localSttPort = SttAudioPort { frame ->
                    val size = frame.size
                    val byteArray = if (isMicrophoneMuted) {
                        // ⭐ 뮤트 중: 무음 데이터 전송 (모든 값을 0으로)
                        ByteArray(size) { 0 }
                    } else {
                        // 정상: 실제 오디오 데이터 전송
                        ByteArray(size) { i -> frame.get(i).toByte() }
                    }
                    Log.d("AudioDebugLo", "L (Muted: $isMicrophoneMuted): ${byteArray.take(10)}...")
                    speechToTextLocal.sendAudioData(byteArray)
                }

                remoteSttPort = SttAudioPort { frame ->
                    //TODO remote의 음성중에서 무음을 인식해서 이걸 안보내야함 -> HOW?
                    val size = frame.size
                    val byteArray = ByteArray(size)
                    Log.d("AudioDebugRe", "R : ${byteArray} \n")
                    for (i in 0 until size) {
                        byteArray[i] = frame.get(i).toByte()
                    }

                    // STT로 전송
                    speechToTextRemote.sendAudioData(byteArray)
//                    Log.d("AudioDebugRe", "R : ${byteArray} \n")
                    // Deepfake Detector로 전송
                    deepfakeDetector?.addAudioChunk(byteArray)

//                    Log.d("AudioDebug", "Remote Frame - Size: ${byteArray.size} bytes")
                }

                // 포트 생성
                localSttPort?.createPort("local_stt_port", format)
                remoteSttPort?.createPort("remote_stt_port", format)

                // 컨퍼런스 브리지 연결
                micMedia.startTransmit(localSttPort)
                callMedia.startTransmit(remoteSttPort)

                Log.d("VoipTest", "=== Audio Forking Setup Completed ===")

            } catch (e: Exception) {
                Log.e("VoipTest", "Error in setupAudioForking: ${e.message}", e)
                e.printStackTrace()
            }
        }

        private fun cleanupAudioPorts() {
            try {
                Log.d("VoipTest", "Cleaning up audio ports and stopping STT...")

                speechToTextLocal.stopStreaming()
                speechToTextRemote.stopStreaming()

                localSttPort?.let {
                    it.delete()
                    Log.d("VoipTest", "Local STT port deleted")
                }
                localSttPort = null

                remoteSttPort?.let {
                    it.delete()
                    Log.d("VoipTest", "Remote STT port deleted")
                }
                remoteSttPort = null

                // ⭐ 언뮤트 타이머 취소 및 플래그 초기화
                unmuteTimer?.let {
                    handler.removeCallbacks(it)
                    Log.d("VoipTest", "Unmute timer cancelled")
                }
                unmuteTimer = null
                isMicrophoneMuted = false

                // DeepfakeDetector는 정리하지 않음 (다음 통화에서 재사용)
                // Activity 종료 시(onDestroy)에만 정리

                Log.d("VoipTest", "Audio ports cleanup completed")

            } catch (e: Exception) {
                Log.e("VoipTest", "Error cleaning up audio ports: ${e.message}", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ========== SharedPreferences에서 저장된 모드 로드 ==========
        loadSavedMode()

        // ========== Fragment 초기화 ==========
        // 앱 시작 시 HomeFragment 표시
        if (savedInstanceState == null) {
            showHomeFragment()
        }



        // ========== 기존 UI 바인딩 (임시 주석 처리 - 나중에 Fragment로 이동) ==========
        // binding.btnCall.setOnClickListener { makeCall() }
        // binding.btnHangup.setOnClickListener { endCall() }

        // STT 객체 초기화
        speechToTextLocal = GoogleSpeechToText(this, sampleRate = 16000)
        speechToTextRemote = GoogleSpeechToText(this, sampleRate = 16000)

        // DeepfakeDetector 초기화
        deepfakeDetector = DeepfakeDetector(
            context = this,
            onResult = { isReal, confidence ->
                // ==================================================
                // ⭐ 수정된 로직: UI 업데이트도 카운팅에 기대도록 변경
                // ==================================================
                if (isReal) {
                    // 1. 진짜 음성으로 판별되면 UI 업데이트 및 카운트 초기화
                    currentCallFragment?.updateDeepfakeResult(true, confidence) // "진짜 음성" 표시

                    if (deepfakeDetectionCount > 0) {
                        Log.d("DeepfakeCounter", "진짜 음성 탐지, 카운트를 0으로 초기화합니다.")
                        deepfakeDetectionCount = 0
                    }
                } else {
                    // 2. 딥페이크로 의심되면 카운트만 증가
                    deepfakeDetectionCount++
                    Log.d("DeepfakeCounter", "딥페이크 의심, 카운트 증가: $deepfakeDetectionCount")

                    // 3. 카운트가 2 이상이고 신뢰도가 70% 이상일 때만 UI 업데이트 및 경고 표시
                    if (deepfakeDetectionCount >= 2 && confidence > 0.7f) {
                        Log.d("DeepfakeCounter", "연속 2회 이상 탐지되어 경고를 표시합니다.")

                        // UI에 "딥페이크 의심" 표시
                        currentCallFragment?.updateDeepfakeResult(false, confidence)

                        // Snackbar 경고 표시
                        showDeepfakeWarningIfNeeded(confidence)

                        // 4. 경고 표시 후 카운트 즉시 초기화 (연속 경고 방지)
                        deepfakeDetectionCount = 0
                    }
                    // 1회 의심 시에는 아무 동작도 하지 않음 (UI 변경 없음)
                }
            }
        )

        // VoicePhishingDetector 초기화
        voicePhishingDetector = VoicePhishingDetector(this)

        initPjsip()


    }

    private fun initPjsip() {
        try {
            ep.libCreate()
            val epConfig = EpConfig()
            ep.libInit(epConfig)
            ep.transportCreate(
                org.pjsip.pjsua2.pjsip_transport_type_e.PJSIP_TRANSPORT_UDP,
                org.pjsip.pjsua2.TransportConfig()
            )
            ep.libStart()

            // TODO: SIP 계정 정보를 하드코딩하지 말고, 사용자 설정이나 보안 저장소에서 불러오도록 수정해야 합니다.
            // 아래는 설정 예시이며, 실제 값으로 동적으로 채워져야 합니다.
            val acfg = AccountConfig()
            acfg.idUri = "sip:USERNAME@YOUR_DOMAIN" // 예: "sip:1001@sip.example.com"
            acfg.regConfig.registrarUri = "sip:YOUR_DOMAIN" // 예: "sip:sip.example.com"
            val creds = acfg.sipConfig.authCreds
            creds.add(AuthCredInfo("Digest", "*", "USERNAME", 0, "PASSWORD")) // "USERNAME", "PASSWORD"
            acc = MyAccount()
            acc?.create(acfg)

        } catch (e: Exception) {
            Log.e("MainActivity", "initPjsip", e)
        }
    }

    private fun makeCall(uri: String) {
        if (uri.isEmpty()) return

        // 통화 시작 시 데이터 초기화
        lastFinalTextLocal = ""
        lastFinalTextRemote = ""
        dialogueHistory.clear()
        lastLocalTurnCompleted = false
        lastRemoteTurnCompleted = false

        // ⭐ 추가: 딥페이크 카운트 초기화
        deepfakeDetectionCount = 0

        // ⭐ 개인정보 모니터링 플래그 초기화
        isPersonalInfoMonitoring = false

        // ⭐ 보이스피싱 판정 결과 초기화
        isCurrentlyPhishing = false

        // ⭐ 마이크 뮤트 플래그 초기화
        isMicrophoneMuted = false

        // CallFragment는 이미 생성될 때 setupUI()에서 초기화됨

        val call = MyCall(acc!!, -1)
        val prm = CallOpParam(true)

        try {
            call.makeCall(uri, prm)
            currentCall = call
            Log.d("MainActivity", "Call initiated to: $uri")
        } catch (e: Exception) {
            Log.e("MainActivity", "makeCall", e)
        }
    }

    private fun endCall() {
        currentCall?.let {
            val prm = CallOpParam(true)
            prm.statusCode = pjsip_status_code.PJSIP_SC_OK
            try {
                it.hangup(prm)
            } catch (e: Exception) {
                Log.e("MainActivity", "endCall", e)
            }
        }
    }

    /**
     * 딥페이크 탐지 시 경고 메시지를 표시합니다 (10초 쿨다운 적용)
     */
    private fun showDeepfakeWarningIfNeeded(confidence: Float) {
        val currentTime = System.currentTimeMillis()

        // 10초 이내에 경고를 표시한 적이 있으면 SKIP
        if (currentTime - lastDeepfakeWarningTime < DEEPFAKE_WARNING_COOLDOWN_MS) {
            Log.d("DeepfakeWarning", "쿨다운 중... 경고 표시 생략")
            return
        }

        // 마지막 경고 시간 업데이트
        lastDeepfakeWarningTime = currentTime

        // Snackbar로 경고 표시 (화면 상단)
        val snackbar = Snackbar.make(
            binding.root,
            "⚠️ 딥페이크 음성 감지! (신뢰도: ${(confidence * 100).toInt()}%)",
            Snackbar.LENGTH_LONG // 3.5초 표시
        )

        // Snackbar를 화면 상단에 표시
        val view = snackbar.view
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.topMargin = 100 // 상단 여백 100dp
        view.layoutParams = params

        // 배경색을 빨간색으로 변경 (경고 효과)
        view.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))

        // 텍스트 색상을 흰색으로 설정 (View 계층에서 TextView 찾기)
        try {
            val textView = view.findViewById<TextView>(
                resources.getIdentifier("snackbar_text", "id", packageName)
            )
            textView?.apply {
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            }
        } catch (e: Exception) {
            Log.w("DeepfakeWarning", "TextView 스타일 적용 실패 (무시 가능)", e)
        }

        snackbar.show()

        Log.d("DeepfakeWarning", "딥페이크 경고 표시됨 (신뢰도: ${(confidence * 100).toInt()}%)")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechToTextLocal.stopStreaming()
            speechToTextRemote.stopStreaming()

            // DeepfakeDetector 정리
            deepfakeDetector?.cleanup()
            deepfakeDetector = null

            // VoicePhishingDetector 정리
            voicePhishingDetector?.cleanup()
            voicePhishingDetector = null

            ep.libDestroy()
        } catch (e: Exception) {
            Log.e("MainActivity", "onDestroy", e)
        }
    }

    // ========== 보이스피싱 탐지 관련 함수 ==========

    /**
     * 한 턴(Local + Remote) 완료 체크 및 모델 실행
     */
    private fun checkAndRunPhishingDetection() {
        // ⭐ 추가: 마이크가 뮤트된 동안에는 보이스피싱 판단을 일시정지
        if (isMicrophoneMuted) {
            Log.d("PhishingDetection", "마이크 뮤트 중... 보이스피싱 판단을 건너뜁니다.")
            return
        }

        if (lastLocalTurnCompleted && lastRemoteTurnCompleted && !isPhishingModelRunning) {
            lastLocalTurnCompleted = false
            lastRemoteTurnCompleted = false
            val fullDialogue = buildDialogueContext()
            runPhishingDetectionAsync(fullDialogue)
        }
    }

    /**
     * 대화 이력을 시간순으로 정렬하여 단일 문자열로 변환
     */
    private fun buildDialogueContext(): String {
        val sortedHistory = dialogueHistory.sortedBy { it.timestamp }
        return sortedHistory.joinToString(separator = " ") { turn ->
            "${turn.speaker}: ${turn.text}"
        }
    }

    /**
     * 비동기로 보이스피싱 탐지 모델 실행
     */
    private fun runPhishingDetectionAsync(dialogue: String) {
        isPhishingModelRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (isPhishing, probability) = voicePhishingDetector?.predict(dialogue)
                    ?: (false to 0.0f)
                withContext(Dispatchers.Main) {
                    updatePhishingUI(isPhishing, probability)
                }
            } catch (e: Exception) {
                Log.e("PhishingDetection", "Model error", e)
            } finally {
                isPhishingModelRunning = false
            }
        }
    }

    /**
     * ⭐ 추가: 진동 실행 함수
     */
    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500) // API 26 미만 버전용
        }
    }

    /**
     * ⭐ 수정: 보이스피싱 탐지 결과 UI 업데이트 함수
     */
    private fun updatePhishingUI(isPhishing: Boolean, probability: Float) {
        val percentText = "${(probability * 100).toInt()}%"

        // 1. 위험도 레벨 정의
        val phishingLevel = when {
            isPhishing && probability > 0.7 -> "CRITICAL"
            else -> "NORMAL" // 70% 미만은 모두 NORMAL로 간주
        }

        // 2. ⭐ 최신 보이스피싱 판정 결과 업데이트
        isCurrentlyPhishing = (phishingLevel == "CRITICAL")
        Log.d("PhishingStatus", "최신 판정: ${if (isCurrentlyPhishing) "위험" else "정상"} ($percentText)")

        // 3. 기존 텍스트 업데이트는 유지
        val message = if (phishingLevel == "CRITICAL") {
            "🚨 보이스피싱 의심 ($percentText)"
        } else {
            "✅ 정상 대화 ($percentText)"
        }
        currentCallFragment?.updatePhishingResult(probability, message)
        Log.d("PhishingResult", message)

        // 4. CRITICAL 레벨일 때만 새로운 동작 수행
        if (phishingLevel == "CRITICAL") {
            currentCallFragment?.updateBackgroundColor(true) // 배경색 변경
            vibrate() // 진동
            currentCallFragment?.showPhishingDialog() // 다이얼로그 표시
        } else {
            // NORMAL 레벨일 경우 기본 배경으로 복귀
            currentCallFragment?.updateBackgroundColor(false)
        }
    }

    // ========== Fragment 관리 메서드 ==========

    /**
     * SharedPreferences에서 저장된 보호 모드를 로드합니다
     */
    private fun loadSavedMode() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val modeName = prefs.getString("phishing_protection_mode", "NORMAL") ?: "NORMAL"
        protectionMode = try {
            PhishingProtectionMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            PhishingProtectionMode.NORMAL
        }
        Log.d("MainActivity", "보호 모드 로드: $protectionMode")
    }

    /**
     * HomeFragment를 표시합니다
     */
    private fun showHomeFragment() {
        currentCallFragment = null

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HomeFragment())
            .commit()

        Log.d("MainActivity", "Showing HomeFragment")
    }

    /**
     * SettingsFragment를 표시합니다
     */
    private fun showSettingsFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, SettingsFragment())
            .addToBackStack(null)
            .commit()

        Log.d("MainActivity", "Showing SettingsFragment")
    }

    /**
     * CallFragment를 표시합니다
     */
    private fun showCallFragment() {
        val fragment = CallFragment()
        currentCallFragment = fragment

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()

        Log.d("MainActivity", "Showing CallFragment")
    }

    /**
     * Fragment가 다시 연결될 때 참조 업데이트
     */
    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)
        if (fragment is CallFragment) {
            currentCallFragment = fragment
        }
    }

    // ========== 개인정보 패턴 감지 ==========

    /**
     * 개인정보 정규표현식 패턴 정의
     */
    companion object {
        // 주민번호: 6자리-7자리 또는 13자리 연속
        private val PATTERN_JUMIN = Regex("\\d{6}[-\\s]?\\d{7}")

        // 계좌번호: 10~14자리 숫자 (하이픈 포함 가능)
        private val PATTERN_ACCOUNT = Regex("\\d{2,4}[-\\s]?\\d{2,4}[-\\s]?\\d{2,6}[-\\s]?\\d{2,4}")

        // 카드번호: 4자리씩 4번 (하이픈/공백 포함 가능)
        private val PATTERN_CARD = Regex("\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}")

        // 비밀번호/PIN: "비밀번호", "패스워드", "pin", "핀" 다음에 숫자
        private val PATTERN_PASSWORD = Regex("(비밀번호|패스워드|pin|핀|암호)\\s*[는은:;]?\\s*\\d+", RegexOption.IGNORE_CASE)

        // OTP/인증번호: "otp", "인증번호" 다음에 6자리 숫자
        private val PATTERN_OTP = Regex("(otp|인증번호|인증코드)\\s*[는은:;]?\\s*\\d{4,6}", RegexOption.IGNORE_CASE)

        // CVC/CVV: "cvc", "cvv", "보안코드" 다음에 3~4자리
        private val PATTERN_CVC = Regex("(cvc|cvv|보안코드|보안번호)\\s*[는은:;]?\\s*\\d{3,4}", RegexOption.IGNORE_CASE)

        // 전화번호: 010-XXXX-XXXX 형태
        private val PATTERN_PHONE = Regex("010[-\\s]?\\d{4}[-\\s]?\\d{4}")
    }

    /**
     * 텍스트에서 개인정보 패턴을 감지합니다
     * @return Pair<Boolean, String> - (감지됨 여부, 감지된 패턴 이름)
     */
    private fun detectPersonalInfo(text: String): Pair<Boolean, String> {
        val patterns = listOf(
            PATTERN_JUMIN to "주민번호",
            PATTERN_ACCOUNT to "계좌번호",
            PATTERN_CARD to "카드번호",
            PATTERN_PASSWORD to "비밀번호",
            PATTERN_OTP to "인증번호",
            PATTERN_CVC to "보안코드",
            PATTERN_PHONE to "전화번호"
        )

        for ((pattern, name) in patterns) {
            if (pattern.containsMatchIn(text)) {
                Log.w("PersonalInfo", "개인정보 감지: $name - '$text'")
                return true to name
            }
        }

        return false to ""
    }

    // ========== 마이크 뮤트 관련 함수 ==========

    /**
     * 마이크를 10초간 뮤트합니다 (방법 2: 무음 데이터 전송 방식)
     */
    private fun muteMicrophoneFor10Seconds() {
        // 1. 뮤트 플래그 활성화 (localSttPort에서 무음 데이터 전송)
        isMicrophoneMuted = true
        currentCall?.setMute(true) // ⭐ 추가: PJSIP 통화로의 전송 중단
        Log.d("MicMute", "마이크 뮤트됨 (10초간) - 무음 데이터 전송 시작")

        // 2. UI 업데이트: 뮤트 상태 표시
        currentCallFragment?.showMicMuteStatus(true)

        // 3. 기존 타이머 취소 (중복 실행 방지)
        unmuteTimer?.let { handler.removeCallbacks(it) }

        // 4. 10초 후 자동 언뮤트
        unmuteTimer = Runnable {
            isMicrophoneMuted = false
            currentCall?.setMute(false) // ⭐ 추가: PJSIP 통화로의 전송 재개
            Log.d("MicMute", "마이크 자동 언뮤트됨 - 정상 오디오 전송 재개")

            // UI 업데이트: 뮤트 해제 상태 표시
            currentCallFragment?.showMicMuteStatus(false)
        }
        handler.postDelayed(unmuteTimer!!, 10000) // 10초
    }

    // ========== Fragment 인터페이스 구현 ==========

    /**
     * HomeFragment에서 통화 버튼 클릭 시 호출됨
     */
    override fun onCallButtonClicked(phoneNumber: String) {
        Log.d("MainActivity", "onCallButtonClicked: $phoneNumber")

        // CallFragment로 전환
        showCallFragment()

        // 통화 시작 (SIP URI 형식으로 변환)
        val sipUri = "sip:$phoneNumber@54.164.201.205"
        makeCall(sipUri)
    }

    /**
     * CallFragment에서 통화 종료 버튼 클릭 시 호출됨
     */
    override fun onHangupButtonClicked() {
        Log.d("MainActivity", "onHangupButtonClicked")

        // 통화 종료
        endCall()

        // HomeFragment로 복귀
        showHomeFragment()
    }

    /**
     * CallFragment에서 보이스피싱 경고 확인 버튼 클릭 시 호출됨
     */
    override fun onPhishingConfirmed() {
        if (isAutoMuteEnabled) {
            Log.d("MainActivity", "자동 음소거 실행.")
            // 기존 로직과 동일하게 보호 모드에 따라 동작 (현재는 모두 뮤트)
            when (protectionMode) {
                PhishingProtectionMode.HARD -> {
                    Log.d("MainActivity", "onPhishingConfirmed [하드 모드] - 즉시 마이크 10초간 뮤트")
                    muteMicrophoneFor10Seconds()
                }
                PhishingProtectionMode.NORMAL -> {
                    Log.d("MainActivity", "onPhishingConfirmed [일반 모드->하드 모드] - 즉시 마이크 10초간 뮤트")
                    muteMicrophoneFor10Seconds()
                }
            }
        } else {
            Log.d("MainActivity", "자동 음소거 비활성화 상태. 뮤트를 실행하지 않음.")
        }
    }

    /**
     * CallFragment에서 모드 변경 시 호출됨
     */
    override fun onModeChanged(mode: PhishingProtectionMode) {
        protectionMode = mode
        Log.d("MainActivity", "보호 모드 변경: $mode")
    }

    /**
     * CallFragment에서 자동 음소거 스위치 클릭 시 호출됨
     */
    override fun onAutoMuteToggleClicked(isEnabled: Boolean) {
        isAutoMuteEnabled = isEnabled
        Log.d("MainActivity", "자동 음소거 기능이 ${if (isEnabled) "활성화" else "비활성화"}되었습니다.")
    }

    /**
     * HomeFragment에서 설정 버튼 클릭 시 호출됨
     */
    override fun onSettingsButtonClicked() {
        Log.d("MainActivity", "onSettingsButtonClicked")
        showSettingsFragment()
    }

}