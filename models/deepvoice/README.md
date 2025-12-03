## 📌 Overview
본 모듈은 딥러닝 기반 음성 특징 분석 및 보이스피싱 위험 판단 가능성을 검증하기 위한 **Prototype 실험용 모델**입니다.  
기본 음성 특징(MFCC) 실험뿐 아니라, **Wav2Vec2 기반 Pretrained ASR 모델을 활용해 LibriSpeech 오디오북 데이터로 Fine-Tuning된 음성 인식 결과를 활용**했습니다.

본 시스템은 실제 화자 인증이나 DeepVoice 정식 모델 개발 목적이 아니라,  
**보이스피싱 위험 탐지 가능성 검증(Proof-of-Concept)**을 위해 구축되었습니다.

---

## 🧪 Experimental Setup

| 항목 | 내용 |
|------|------|
| 사용 모델 | Wav2Vec2 Base (Facebook AI / HuggingFace) |
| Pretrained 데이터 | LibriSpeech ASR corpus (English Audiobook 데이터 960hr) |
| Fine-Tuning 수행 여부 | ✔️ 수행 (Limited Epoch, 범용 음성 인식 성능 검증 수준) |
| 활용 방식 | STT 결과 → 보이스피싱 탐지 모델 입력 |
| 목적 | 원시 음성을 모델 입력 대신, **STT 전처리된 텍스트 기반 탐지 실험** |

사용한 모델은 아래와 같은 특징을 가짐:
- Wav2Vec2 Base (95M parameters)
- LibriSpeech 데이터로 Fine-Tuning된 공개 모델 사용 (Custom 학습 X)
- 음성 → 텍스트 변환 후, 텍스트 기반 VoicePhishing 탐지 모델에 전달
- 실시간 모델 서빙/추론은 구현하지 않고, **실험용 오프라인 추론 방식** 적용

---
## 🎯 Pretrained Model Information

본 프로젝트에서 사용한 음성 인식 모델은 HuggingFace의 공개 ASR 모델 **`facebook/wav2vec2-base-960h`** 입니다.  
이 모델은 **LibriSpeech (960-hour, English Audiobook)** 데이터로 학습된 Self-Supervised Speech Model로, 음성을 텍스트로 변환하는 STT 단계에 활용했습니다.

| 항목 | 내용 |
|------|------|
| 모델 이름 | facebook/wav2vec2-base-960h |
| 모델 유형 | Wav2Vec 2.0 Base |
| 파라미터 수 | 약 95M |
| 학습 데이터 | LibriSpeech 960h (영어 오디오북) |
| 목적 | Automatic Speech Recognition (ASR) |
| 활용 방식 | 음성 → 텍스트 변환 후, 텍스트 기반 보이스피싱 Context 모델 입력 |
| HuggingFace 링크 | https://huggingface.co/facebook/wav2vec2-base-960h |

---

### 📌 Why we used this model?

- Google Cloud STT 결과의 일정한 품질 검증을 위해 오픈소스 기반 모델도 병행 평가
- 실제 서비스에서 음성 → 텍스트 변환 품질 비교 용도
- STT 결과를 입력으로 하는 **보이스피싱 문맥 분석 모델 (NLP)** 연결 실험에서 활용
- 커스텀 학습이 아닌, **Prototype 단계**임을 명확히 하기 위함

---

### 🛠 Recommended Future Upgrades

| 개선 방향 | 설명 |
|-----------|------|
| 한국어 음성 모델 도입 | AI-Hub, KsponSpeech, Zeroth Speech 등 한국어 STT 학습 모델 활용 |
| Custom Fine-Tuning | 실 서비스 통화 데이터 기반 Fine-Tuning 수행 |
| 화자 정보 결합 | Speaker Embedding + Textual Context → Multi-modal VoicePhishing 탐지 |
| Real-time Serving | WebRTC 기반 실시간 STT + 감지 pipeline 구축 |
---

## 📊 Results Included

| 결과 항목 | 설명 |
|-----------|------|
| confusion_matrix.png | 텍스트 기반 분류 모델의 성능 평가 지표 |
| threshold_experiment.png | Threshold 변화 시 Precision-Recall-F1 변화 분석 |
| (선택) threshold_table.csv | Threshold 값별 성능 수치 정리 |

---

## 🛑 What This Model is NOT
❌ 실제 DeepVoice 정식 모델 아님
❌ 화자 인증, VoiceClone, 음성 특징 기반 DeepFake 탐지 모델 아님
❌ 실시간 inference 서비스 구현 아님
❌ 한국어 음성 Fine-Tuning 모델 아님

---
