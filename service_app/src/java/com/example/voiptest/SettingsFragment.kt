package com.example.voiptest

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.voiptest.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var listener: HomeFragmentListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is HomeFragmentListener) {
            listener = context
            Log.d("SettingsFragment", "MainActivity listener attached")
        } else {
            throw RuntimeException("$context must implement HomeFragmentListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // SharedPreferences에서 저장된 모드 불러오기
        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val modeName = prefs.getString("phishing_protection_mode", "NORMAL") ?: "NORMAL"

        // RadioButton 초기 선택
        when (modeName) {
            "NORMAL" -> {
                binding.radioNormal.isChecked = true
                binding.tvCurrentMode.text = "현재 모드: 일반 모드"
                binding.tvCurrentMode.setTextColor(resources.getColor(android.R.color.holo_green_light, null))
            }
            "HARD" -> {
                binding.radioHard.isChecked = true
                binding.tvCurrentMode.text = "현재 모드: 하드 모드"
                binding.tvCurrentMode.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
            }
        }

        Log.d("SettingsFragment", "Current mode loaded: $modeName")
    }

    private fun setupListeners() {
        // 뒤로가기 버튼
        binding.btnBack.setOnClickListener {
            Log.d("SettingsFragment", "Back button clicked")
            parentFragmentManager.popBackStack()
        }

        // RadioGroup 변경 리스너
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioNormal -> PhishingProtectionMode.NORMAL
                R.id.radioHard -> PhishingProtectionMode.HARD
                else -> PhishingProtectionMode.NORMAL
            }

            // SharedPreferences에 저장
            saveMode(mode)

            // MainActivity에 모드 변경 알림
            listener?.onModeChanged(mode)

            // UI 업데이트
            updateCurrentModeText(mode)

            // Toast 표시
            val modeName = if (mode == PhishingProtectionMode.NORMAL) "일반" else "하드"
            Toast.makeText(requireContext(), "${modeName} 모드로 변경되었습니다", Toast.LENGTH_SHORT).show()

            Log.d("SettingsFragment", "Mode changed to: $mode")
        }
    }

    private fun saveMode(mode: PhishingProtectionMode) {
        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("phishing_protection_mode", mode.name)
            .apply()
        Log.d("SettingsFragment", "Mode saved: ${mode.name}")
    }

    private fun updateCurrentModeText(mode: PhishingProtectionMode) {
        when (mode) {
            PhishingProtectionMode.NORMAL -> {
                binding.tvCurrentMode.text = "현재 모드: 일반 모드"
                binding.tvCurrentMode.setTextColor(resources.getColor(android.R.color.holo_green_light, null))
            }
            PhishingProtectionMode.HARD -> {
                binding.tvCurrentMode.text = "현재 모드: 하드 모드"
                binding.tvCurrentMode.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
        Log.d("SettingsFragment", "MainActivity listener detached")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}