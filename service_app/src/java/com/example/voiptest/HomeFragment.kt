package com.example.voiptest

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.voiptest.databinding.FragmentHomeBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var tvPhoneNumber: TextView
    private lateinit var btnCall: ImageView
    private lateinit var btnDelete: FloatingActionButton
    private lateinit var btnSettings: FloatingActionButton

    // MainActivity와 통신하기 위한 리스너
    private var listener: HomeFragmentListener? = null

    private val phoneNumber = StringBuilder()

    // 버튼 데이터 (숫자, 문자)
    private val buttonData = mapOf(
        1 to Pair("1", ""),
        2 to Pair("2", "ABC"),
        3 to Pair("3", "DEF"),
        4 to Pair("4", "GHI"),
        5 to Pair("5", "JKL"),
        6 to Pair("6", "MNO"),
        7 to Pair("7", "PQRS"),
        8 to Pair("8", "TUV"),
        9 to Pair("9", "WXYZ"),
        0 to Pair("0", "+"),
        -1 to Pair("*", ""),
        -2 to Pair("#", "")
    )

    /**
     * MainActivity와 연결
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is HomeFragmentListener) {
            listener = context
            Log.d("HomeFragment", "MainActivity listener attached")
        } else {
            throw RuntimeException("$context must implement HomeFragmentListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
        setupDialPad()
        setupActionButtons()
    }

    private fun initViews() {
        tvPhoneNumber = binding.tvPhoneNumber
        btnCall = binding.btnCall
        btnDelete = binding.btnDelete
        btnSettings = binding.btnSettings
    }

    private fun setupDialPad() {
        // 각 버튼 ID 매핑
        val buttonIds = listOf(
            R.id.btn1 to 1,
            R.id.btn2 to 2,
            R.id.btn3 to 3,
            R.id.btn4 to 4,
            R.id.btn5 to 5,
            R.id.btn6 to 6,
            R.id.btn7 to 7,
            R.id.btn8 to 8,
            R.id.btn9 to 9,
            R.id.btn0 to 0,
            R.id.btnStar to -1,
            R.id.btnHash to -2
        )

        buttonIds.forEach { (buttonId, key) ->
            val buttonView = binding.root.findViewById<View>(buttonId)
            val data = buttonData[key]!!

            // 숫자와 문자 설정
            val tvNumber = buttonView.findViewById<TextView>(R.id.tvNumber)
            val tvLetters = buttonView.findViewById<TextView>(R.id.tvLetters)

            tvNumber.text = data.first

            if (data.second.isNotEmpty()) {
                tvLetters.text = data.second
                tvLetters.visibility = View.VISIBLE
            } else {
                tvLetters.visibility = View.GONE
            }

            // 클릭 리스너
            buttonView.setOnClickListener {
                onDialPadClick(data.first)
            }
        }
    }

    private fun setupActionButtons() {
        // 통화 버튼
        btnCall.setOnClickListener {
            makeCall()
        }

        // 삭제 버튼
        btnDelete.setOnClickListener {
            deleteLastDigit()
        }

        btnDelete.setOnLongClickListener {
            clearAll()
            true
        }

        // 설정 버튼
        btnSettings.setOnClickListener {
            Log.d("HomeFragment", "Settings button clicked")
            listener?.onSettingsButtonClicked()
        }
    }

    private fun onDialPadClick(digit: String) {
        phoneNumber.append(digit)
        updatePhoneNumberDisplay()
    }

    private fun deleteLastDigit() {
        if (phoneNumber.isNotEmpty()) {
            phoneNumber.deleteCharAt(phoneNumber.length - 1)
            updatePhoneNumberDisplay()
        }
    }

    private fun clearAll() {
        phoneNumber.clear()
        updatePhoneNumberDisplay()
    }

    private fun updatePhoneNumberDisplay() {
        if (phoneNumber.isEmpty()) {
            tvPhoneNumber.text = ""
        } else {
            // 전화번호 포맷팅 (간단한 예시)
            tvPhoneNumber.text = formatPhoneNumber(phoneNumber.toString())
        }
    }

    private fun formatPhoneNumber(number: String): String {
        // 간단한 포맷팅 로직 (실제로는 더 복잡한 로직 필요)
        return when {
            number.length <= 3 -> number
            number.length <= 7 -> "${number.substring(0, 3)} ${number.substring(3)}"
            number.startsWith("02") && number.length <= 10 -> {
                "(02) ${number.substring(2, 5)} ${number.substring(5)}"
            }
            else -> number
        }
    }

    private fun makeCall() {
        // TODO: 현재는 테스트를 위해 번호가 "7002"로 고정되어 있습니다.
        // 실제 서비스에서는 아래 주석 처리된 코드와 같이 다이얼패드에 입력된 번호를 사용해야 합니다.
        val numberToCall = "7002"
        // val numberToCall = phoneNumber.toString()

        if (numberToCall.isEmpty()) {
            Log.d("HomeFragment", "Phone number is empty. Not making a call.")
            return
        }

        Log.d("HomeFragment", "Making call to: $numberToCall")

        // MainActivity에 통화 시작 요청
        listener?.onCallButtonClicked(numberToCall)
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
        Log.d("HomeFragment", "MainActivity listener detached")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}