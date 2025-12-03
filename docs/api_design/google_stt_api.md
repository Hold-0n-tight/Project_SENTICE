# Google Cloud STT API 사용 문서

## 1. 개요
VoIP 서비스에서 사용자의 음성을 텍스트로 변환하기 위해 Google Cloud Speech-to-Text(STT) API를 사용했습니다.

## 2. Endpoint
https://speech.googleapis.com/v1/speech:recognize

## 3. Request 예시
```json
{
  "config": {
    "encoding": "LINEAR16",
    "languageCode": "ko-KR",
    "sampleRateHertz": 16000
  },
  "audio": {
    "content": "<base64-encoded audio>"
  }
}

## 4. Response 예시
{
  "results": [
    {
      "alternatives": [
        {
          "transcript": "안녕하세요",
          "confidence": 0.95
        }
      ]
    }
  ]
}

## 5. 인증
Google Cloud 서비스 계정 키(JSON) 사용
환경 변수 GOOGLE_APPLICATION_CREDENTIALS로 경로 설정
Git에는 인증 키 포함 금지

## 6. 서비스 호출 흐름
앱에서 음성 수집
음성을 base64로 인코딩
STT API POST 요청
결과(JSON) 수신
탐지 모델에 전달

## 7. 시각적 흐름
sequenceDiagram
    participant App
    participant GoogleSTT
    participant Model
    App->>GoogleSTT: 음성 데이터 전송
    GoogleSTT-->>App: 텍스트 결과 반환
    App->>Model: 텍스트 전달