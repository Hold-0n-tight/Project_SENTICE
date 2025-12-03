package com.example.voiptest

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voiptest.databinding.FragmentCallBinding

class CallFragment : Fragment() {
    private var _binding: FragmentCallBinding? = null
    private val binding get() = _binding!!

    // MainActivity와 통신하기 위한 리스너
    private var listener: CallFragmentListener? = null

    // 채팅 어댑터
    private lateinit var chatAdapter: ChatAdapter

    private var isPhishingDialogShowing = false // 다이얼로그 중복 표시 방지 플래그

    /**
     * MainActivity와 연결
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is CallFragmentListener) {
            listener = context
            Log.d("CallFragment", "MainActivity listener attached")
        } else {
            throw RuntimeException("$context must implement CallFragmentListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // 초기 UI 설정
        binding.tvCallerName.text = "연결 중..."
        binding.tvTime.text = "00:00"

        // RecyclerView 및 어댑터 설정
        chatAdapter = ChatAdapter()
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true // 새 항목이 추가될 때 아래쪽에서부터 쌓이도록 설정
            }
            adapter = chatAdapter
        }
    }

    private fun setupListeners() {
        // 통화 종료 버튼
        binding.btnEndCall.setOnClickListener {
            Log.d("CallFragment", "End call button clicked")
            listener?.onHangupButtonClicked()
        }

        // 자동 음소거 기능 스위치
        binding.switchAutoMute.setOnCheckedChangeListener { _, isChecked ->
            listener?.onAutoMuteToggleClicked(isChecked)
        }
    }

    // ========== MainActivity에서 호출하는 UI 업데이트 메서드 ==========

    /**
     * 통화 상태 업데이트
     */
    fun updateCallStatus(status: String) {
        binding.tvCallerName.text = status
        Log.d("CallFragment", "Call status updated: $status")
    }

    /**
     * STT 결과 업데이트
     * @param speaker "LOCAL" 또는 "REMOTE"
     * @param text 인식된 텍스트
     */
    fun updateSttResult(speaker: String, text: String) {
        // [인식중]으로 시작하는 중간 결과는 무시하고, 최종 결과만 처리
        if (!text.startsWith("[")) {
            val message = if (speaker == "LOCAL") {
                MessageModel.SenderMessage(text)
            } else {
                MessageModel.ReceiverMessage(text)
            }
            chatAdapter.addItem(message)
            binding.rvChat.scrollToPosition(chatAdapter.itemCount - 1)
            Log.d("CallFragment", "STT Final Result - $speaker: $text")
        } else {
            Log.d("CallFragment", "STT Intermediate Result - $speaker: $text")
            // TODO: 중간 결과는 별도의 TextView에 표시하는 것을 고려할 수 있음
        }
    }

    /**
     * 보이스피싱 탐지 결과 업데이트
     */
    fun updatePhishingResult(probability: Float, message: String) {
//        binding.tvPhishingResult.text = message

        val color = when {
            probability >= 0.7 -> Color.RED
            probability >= 0.5 -> Color.rgb(255, 165, 0) // 주황
            else -> Color.GREEN
        }
//        binding.tvPhishingResult.setTextColor(color)

        Log.d("CallFragment", "Phishing Result: $message")
    }

    /**
     * 딥페이크 탐지 결과 업데이트
     */
    fun updateDeepfakeResult(isReal: Boolean, confidence: Float) {
        val message = if (isReal) {
            "✅ 진짜 음성 (${(confidence * 100).toInt()}%)"
        } else {
            "⚠️ 딥페이크 의심 (${(confidence * 100).toInt()}%)"
        }

//        binding.tvDeepfakeResult.text = message

        val color = if (isReal) Color.GREEN else Color.RED
//        binding.tvDeepfakeResult.setTextColor(color)

        Log.d("CallFragment", "Deepfake Result: $message")
    }

    /** ⭐ 추가: 배경색 변경 함수 */
    fun updateBackgroundColor(isWarning: Boolean) {
        val color = if (isWarning) {
            ContextCompat.getColor(requireContext(), R.color.warning_background) // colors.xml에 정의 필요
        } else {
            Color.TRANSPARENT // 또는 기본 배경색
        }
        binding.root.setBackgroundColor(color)
    }

    /** ⭐ 추가: 마이크 뮤트 상태 표시 함수 */
    fun showMicMuteStatus(isMuted: Boolean) {
        if (isMuted) {
            // 뮤트 중일 때 UI 표시
            binding.tvCallerName.text = "🔇 마이크 뮤트 중... (10초)"
            binding.tvCallerName.setTextColor(Color.RED)
            Log.d("CallFragment", "마이크 뮤트 상태 표시")
        } else {
            // 뮤트 해제 시 원래 상태로 복귀
            binding.tvCallerName.text = "통화 연결됨"
            binding.tvCallerName.setTextColor(Color.WHITE)
            Log.d("CallFragment", "마이크 뮤트 상태 해제")
        }
    }

    /** ⭐ 추가: 보이스피싱 경고 다이얼로그 표시 함수 */
    fun showPhishingDialog() {
        if (isPhishingDialogShowing) return // 이미 표시 중이면 무시

        isPhishingDialogShowing = true
        AlertDialog.Builder(requireContext())
            .setTitle("🚨 보이스피싱 경고 🚨")
            .setMessage("현재 통화에서 보이스피싱이 강하게 의심됩니다. 금전 거래나 개인정보 요구에 절대로 응하지 마세요!")
            .setPositiveButton("확인") { dialog, _ ->
                // ⭐ 확인 버튼 클릭 시 MainActivity에 알림
                listener?.onPhishingConfirmed()
                dialog.dismiss()
            }
            .setOnDismissListener {
                isPhishingDialogShowing = false // 다이얼로그가 닫히면 플래그 해제
            }
            .setCancelable(false) // 바깥 영역 터치로 닫기 방지
            .show()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
        Log.d("CallFragment", "MainActivity listener detached")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = CallFragment()
    }
}