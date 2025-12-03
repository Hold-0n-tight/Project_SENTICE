package com.example.voiptest

/**
 * 대화 턴 데이터 클래스
 *
 * VoIP 통화 중 발생하는 각 발화(utterance)를 저장하기 위한 데이터 구조
 *
 * @property speaker 화자 구분 ("LOCAL": 사용자, "REMOTE": 상대방)
 * @property text STT로 변환된 최종 텍스트
 * @property timestamp 발화 완료 시간 (밀리초, System.currentTimeMillis())
 */
data class DialogueTurn(
    val speaker: String,      // "LOCAL" or "REMOTE"
    val text: String,         // STT 최종 결과 텍스트
    val timestamp: Long       // 발화 시간 (System.currentTimeMillis())
)
