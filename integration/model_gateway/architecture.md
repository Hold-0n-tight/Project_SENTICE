# VoIP 서비스 통합 아키텍처 설명서
본 문서는 VoIP 기반 보이스피싱 탐지 서비스의 전체 파이프라인을 “앱 → 음성 인식 → 탐지 모델 → 사용자 알림” 순서로 정리한 통합 아키텍처 문서이다.
실제 프로젝트 구조에서 integration 폴더는 각 구성 요소가 어떻게 연동되어 작동하는지를 기술하며, 이 문서는 그 최상위 개요이다.

## 1. 전체 구성 요소
1) Frontend / Mobile App (Android)
실시간 음성 수집 (AudioRecord)
음성을 100~200ms 크기의 chunk로 분리
gRPC streaming 호출로 서버에 전송
서버로부터 transcript를 수신 후 → 탐지 모델에 즉시 전달
탐지 결과에 따라 Toast 알림 표시
2) gRPC Gateway
nest.proto 기반으로 양방향 스트리밍 처리
앱으로부터 음성 chunk 수신
Google Cloud Speech-to-Text API로 전달
STT 결과(transcript)를 앱으로 스트리밍 반환
동시에 내부 모델 Gateway로도 transcript 전달 가능
3) Google Cloud STT API
음성을 텍스트로 변환
실시간 스트리밍 대응
한국어(ko-KR) 최적화 설정 사용
4) 보이스피싱 탐지 모델(Model Server)
DeepVoice 탐지 모델(Wav2Vec2 기반)
→ 목소리가 합성/변조된 보이스피싱 여부 탐지
Context 모델(LSTM 기반)
→ 문장의 맥락/대화 내용 기반 보이스피싱 의도 탐지
두 모델의 결과는 앱 UI에서 각각 활용 가능
5) Infrastructure / Deployment
Asterisk 기반 VoIP 서버(AWS EC2)
모델 서버, gRPC 서버, STT Gateway가 Docker로 개별 컨테이너 실행
API Gateway/Nginx 연동 구성 가능

## 2. 전체 데이터 파이프라인 개요
[사용자의 실제 음성]
        │
        ▼
(Android App)  
 - 마이크 입력  
 - Byte array chunk 변환  
 - gRPC 송신
        │
        ▼
(gRPC Gateway Server)
 - 음성 chunk 수신
 - Google STT API 요청
        │
        ▼
(Google STT)
 - transcript 반환
        │
        ▼
(gRPC Gateway → App)
 - 실시간 텍스트 스트리밍 전달
        │
        ▼
(Android App)
 - LSTM 기반 context model에 transcript 전달
 - DeepVoice 모델에 음색 기반 점수 전달(선택적)
        │
        ▼
(Context/Voice Model Server)
 - 보이스피싱 위험도 score 산출
        │
        ▼
(App UI)
 - Threshold 초과 시 Toast 알림

## 3. 아키텍처 개념도
아래는 문서에서 PNG로 포함할 수 있는 구조를 간단한 텍스트 다이어그램으로 표현한 것.
(ReadmeAssets에 아키텍처 PNG가 이미 있다고 했으니, 이 설명은 보조 역할)
+------------------+        +--------------------+       +----------------+
|   Mobile App     |        |    gRPC Gateway    |       | Google STT API |
|------------------|        |--------------------|       |----------------|
| Mic Capture      | <----> | Bidirectional RPC  | <----> | STT service   |
| Audio Chunking   |        | Audio Stream Proxy |       | Transcript     |
+------------------+        +--------------------+       +----------------+
         |
         | Transcript
         v
+---------------------+       +--------------------------+
| Context Model(LSTM) |  -->  |  Phishing Risk Score     |
+---------------------+       +--------------------------+
         |
         v
+-------------------+
|   Toast Alert     |
+-------------------+

## 4. 컴포넌트 간 연동 요약
Component	역할	Input	Output
Android App	음성 수집/전송, UI 알림	음성	transcript / phishing score
gRPC Gateway	스트리밍 중계	audio bytes	transcript
Google STT	음성 → 텍스트 변환	audio bytes	transcript
DeepVoice Model	음색 기반 보이스피싱 탐지	audio	score
Context Model	문맥 기반 탐지(LSTM)	transcript	phishing risk score
Asterisk (VoIP)	통화 기반 인프라	음성	RTP/audio

## 5. Threshold & Alert 전략
Context 모델의 phishing score ≥ 임계값(threshold)
→ 보이스피싱 가능성 판단
앱에서는 즉시 Toast Notification 발동
경고 문구 예시:
"⚠️ 보이스피싱 위험 문장이 탐지되었습니다."