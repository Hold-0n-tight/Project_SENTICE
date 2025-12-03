본 문서는 Mobile App → Model Gateway → STT → Detection Models → App 으로 이어지는 전체 음성 기반 피싱 탐지 시스템의 통합 구조를 설명한다.
Integration 레이어는 개별 모델들이 아닌 서비스 전체를 하나로 묶어 동작하도록 연결하는 최상위 단계이다.

## 1. System Architecture
아래는 시스템 전반의 통신 흐름을 도식화한 구조이며,
architecture.md 문서에 상세 서술이 포함되어 있다.
 ┌────────────────────┐
 │     Mobile App     │
 │ (gRPC Audio Stream)│
 └───────────┬────────┘
             │
             ▼
 ┌────────────────────┐
 │   Model Gateway     │
 │ • Audio Chunking    │
 │ • STT 요청/수신     │
 │ • 모델 병렬 호출    │
 │ • 결과 병합         │
 └───────────┬────────┘
             │
   ┌─────────┴─────────────────────────────┐
   ▼                                       ▼
┌──────────────┐                    ┌────────────────┐
│ Google Cloud  │                    │ DeepVoice Model│
│ Speech-to-Text│                    │ (Wav2Vec2 기반)│
└───────┬──────┘                    └───────┬────────┘
        │                                   │
        │ transcript                        │ deepfake score
        │                                   │
        ▼                                   ▼
                       ┌───────────────────────┐
                       │   Context Classifier  │
                       │   (LSTM 기반 모델)    │
                       └───────────┬───────────┘
                                   │
                                   ▼
                     ┌───────────────────────────┐
                     │   Merge Engine (Gateway)   │
                     └───────────┬───────────────┘
                                 │
                                 ▼
                     ┌───────────────────────────┐
                     │     Final Result to App    │
                     └───────────────────────────┘

## 2. Directory Structure
integration 폴더는 다음과 같이 구성된다:
integration/
├── README.md                 # (현재 문서)
├── architecture.md           # 전체 시스템 아키텍처 상세 기술
├── model_gateway/
│   └── gateway_overview.md   # Gateway 동작 원리 및 구조
├── latency_test/
│   └── result_summary.md     # End-to-end 성능 측정 결과
└── pipeline_diagram/
    └── pipeline.png          # 전체 데이터 흐름도
각 문서가 하는 역할은 아래에 설명되어 있다.

## 3. Components

### 3.1 Model Gateway
gRPC 기반 음성 스트리밍 처리
Google Cloud STT API 호출 및 partial transcript 수신
딥보이스 모델 및 LSTM 문맥 분류 모델 병렬 호출
모델 결과 merge 후 앱에 전달
→ 자세한 내용은 model_gateway/gateway_overview.md 참고

### 3.2 Google Cloud STT
50–200ms 단위 audio chunk를 실시간 처리
partial/final transcript 제공
Gateway가 이 transcript에 따라 모델 inference를 트리거

### 3.3 Models
DeepVoice Model
Wav2Vec2 기반, LibriSpeech 파인튜닝된 음성 특징 모델
Synthetic voice(딥보이스) 탐지
confusion matrix 및 threshold tuning 수행
Voice Phishing Context LSTM Classifier
LLM 기반 synthetic dataset + 실제 도메인 데이터로 학습
개인정보 도메인 / 금융 도메인 문맥 분류
KoBERT 및 LLM classifier와 비교 실험 후 최종 LSTM 채택
각 모델은 독립적 모듈로 배치되며, Gateway가 inference orchestration을 담당한다.

## 4. End-to-End Pipeline
전체 실행 순서 요약
Mobile App
StreamingRecognizeRequest 형태로 audio chunk 전송
Gateway
audio chunk → buffer → STT API 전달
Google STT
transcript 반환
Gateway
transcript를 두 모델로 병렬 전달
DeepVoice Model
음성 deepfake 여부 출력
Context Classifier(LSTM)
대화의 보이스피싱 위험도 판단
Merge Engine
전체 결과 통합 (위험 등급, score 포함)
Mobile App
Toast 알림 형태로 즉시 표시
→ 하나의 음성 chunk에 대해 전체 지연시간은 평균 350~600ms

## 5. Latency Test
latency 테스트 상세 결과는 latency_test/result_summary.md 참고.
핵심 결론:
Chunk Size	End-to-end latency
50ms	가장 빠르지만 비효율적
100ms	가장 안정적, 서비스 권장 값
200ms	latency 증가
STT가 전체 지연의 60–70% 차지.

## 6. Protocol Definition (Proto)
서비스와 Gateway는 gRPC 기반으로 통신하며, 사용된 proto 파일은 다음과 같다:
nest.proto
syntax = "proto3";

package com.example.gcs;

option java_multiple_files = true;
option java_package = "com.example.gcs";
option java_outer_classname = "Nest";

service SpeechRecognition {
  rpc DoSpeechRecognition(stream StreamingRecognizeRequest)
      returns (stream StreamingRecognizeResponse);
}

message StreamingRecognizeRequest {
  bytes audio_content = 1;
}

message StreamingRecognizeResponse {
  string transcript = 1;
}

## 7. Testing Strategy
Integration 레이어에서는 개별 모델 테스트가 아닌 연동 테스트에 중점을 둔다.
주요 테스트 항목
gRPC 스트림 끊김 없이 유지되는지
STT partial latency 측정
모델 inference와 병합 결과의 일관성 확인
최종 결과가 Toast 형태로 앱에 정상 표출되는지
지연시간 spike 감지 (network 변동 포함)

## 8. Conclusion
Integration 레이어는 다음 핵심 기능을 수행한다:
앱의 음성을 끊김 없이 수신
STT → 두 모델 → 결과 병합을 실시간으로 연결
전체 pipeline의 안정성과 latency를 관리
이 README는 프로젝트 전체 흐름을 한눈에 파악하기 위한 상위 문서이며,
각 하위 문서들은 모델 상세, 아키텍처, 성능 분석 등 심층 정보를 제공한다.