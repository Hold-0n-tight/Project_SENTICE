본 문서는 실시간 음성 스트리밍 기반 파이프라인(App → Gateway → STT → Model → App)의 end-to-end latency 측정 결과를 정리한다.

## 1. Test Environment
항목	사양
Mobile Device	Galaxy / iPhone (테스트 공통)
Network	LTE / 5G 환경, 평균 30–80 Mbps
Server	GCP VM (4 vCPU, 16GB RAM)
STT API	Google Cloud Speech-to-Text (StreamingRecognize)
Models	DeepVoice(wav2vec2 기반), LSTM 기반 문맥 분류기
Audio Format	Linear PCM 16-bit, 16kHz
Chunk Size	50 ms / 100 ms / 200 ms 비교

## 2. Methodology
Latency 측정 지점
App → Gateway gRPC Input Time
STT Partial Response Time
DeepVoice Model Inference Time
LSTM Context Model Inference Time
Gateway Final Merge Time
Gateway → App Response Time
총 latency는 다음 공식으로 계산:
End-to-End Latency 
= (STT latency) + (Model inference latency) + (Gateway overhead)

## 3. Results

### 3.1. Chunk Size Comparison
Chunk Size	STT 응답(ms)	모델 병렬 inference(ms)	Gateway overhead(ms)	총 End-to-end(ms)
50 ms	180–260	80–150	20–40	300–450
100 ms	200–320	80–150	20–40	340–500
200 ms	260–420	80–150	20–40	420–620

### 3.2. Observations
✔ 50ms chunk
가장 짧은 latency
하지만 네트워크 부하 증가, 모바일 전력 소모 증가
✔ 100ms chunk
latency와 안정성의 균형이 가장 좋음
실 서비스 기준 “default recommended value”
✔ 200ms chunk
chunk 크기가 커질수록 STT 응답 지연이 증가
긴 문장을 말할 때 partial latency가 크게 늘어짐

## 4. Bottleneck Analysis
STT API가 전체 latency의 60~70% 차지
모델 inference는 GPU 없이도 충분히 빠름 (80–150ms)
Gateway 자체 오버헤드는 매우 적음
chunk 크기 증가 → STT latency 선형 증가

## 5. Recommendations
100ms chunk size를 기본값으로 유지 (성능/안정성 균형 최적)
STT 응답 지연 대비 위해:
API session pre-warming
음성 구간 탐지(VAD) 적용 시 더욱 최적화 가능
DeepVoice/LSTM 모델은 batch inference 지원 고려 가능

## 6. Conclusion
실제 테스트 기준으로 end-to-end latency는 약 350–600ms 범위에서 안정적이며 모바일 실시간 피싱 탐지 서비스에 충분히 적합한 성능을 보여준다.