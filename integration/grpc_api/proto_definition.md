## 1. 파일 개요
본 문서는 VoIP 서비스에서 사용된 gRPC 프로토콜 정의 파일인 nest.proto 의 구조와 동작 방식을 설명한다.
앱에서는 실시간 음성 인식(Streaming Speech Recognition)을 위해 이 API를 호출하며, 변환된 텍스트는 이후 보이스피싱 탐지 모델에 전달된다.

## 2. 파일 정보
프로토파일 이름: nest.proto
패키지명: com.example.gcs
생성 타겟: Android(Java/Kotlin) gRPC Stub
사용 목적:
실시간 음성 스트리밍 전달
Google Cloud STT API 호환 구조
transcript 결과 수신 후, 보이스피싱 탐지 모델로 전달

## 3. 프로토 구조

### 3.1 service 정의
service SpeechRecognition {
  rpc DoSpeechRecognition(stream StreamingRecognizeRequest) 
      returns (stream StreamingRecognizeResponse);
}
✔ 특징
양방향 스트리밍(Bidirectional Streaming)
앱은 연속된 음성 조각(audio chunks)을 서버로 전송하며, 서버는 인식된 텍스트를 스트리밍 형태로 실시간 반환한다.
Google Cloud STT와 동일한 구조를 모사하고 있으며, 서비스 내부에서 gateway 또는 proxy 서버를 거쳐 실제 STT API를 호출할 수 있다.

## 4. 메시지 구조

### 4.1 StreamingRecognizeRequest
message StreamingRecognizeRequest {
  bytes audio_content = 1;
}
audio_content
실시간으로 잘린 음성 chunk (raw PCM 또는 LINEAR16)
Android 측에서 마이크 입력을 byte array로 변환 후 전송
보통 100ms–200ms 단위로 전송됨

### 4.2 StreamingRecognizeResponse
message StreamingRecognizeResponse {
  string transcript = 1;
}
transcript
서버(STT)가 반환한 실시간 텍스트 조각
완전 문장이 아니라 “부분 인식 결과(partial result)”인 경우도 포함됨

## 5. 앱 호출 흐름 (정확한 동작 방식)
단계 요약
마이크 ON
음성을 0.1~0.2초 단위로 byte 배열로 추출
DoSpeechRecognition() 스트리밍 RPC로 전송
서버가 스트리밍으로 transcript 반환
보이스피싱 탐지 모델에 실시간 전달
모델 결과(위험도)가 임계치 초과 시 → 앱에서 Toast 알림 표시
흐름도 (간단 시퀀스)
App --> gRPC Server: StreamingRecognizeRequest(audio bytes)
gRPC Server --> Google STT (optional): 음성 인식 요청
gRPC Server --> App: StreamingRecognizeResponse(transcript)
App --> Context Model: transcript 전달
Context Model --> App: phishing_score
App: Toast 알림 노출

## 6. 앱 UI/UX 상 연동 타이밍
transcript 가 스트리밍으로 받아질 때마다
→ LSTM 기반 ‘맥락 기반 보이스피싱 탐지 모델’로 즉시 전달
탐지 모델의 score ≥ threshold 시
→ 즉시 Toast Notification 활성화
(“보이스피싱 가능성이 탐지되었습니다.”)
따라서 nest.proto 는 음성 인식의 핵심 역할을 하는 파일이자
앱과 모델을 연결하는 실시간 파이프라인의 출발점이다.