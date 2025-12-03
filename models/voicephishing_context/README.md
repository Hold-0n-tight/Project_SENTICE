On‑Device Context‑Aware Voice Phishing Detection with Domain Expansion
이 폴더는 온디바이스(온디바이스 inference) 환경에서 구동 가능한
경량 LSTM 기반 보이스피싱 탐지 모델의 전체 연구 및 개발 과정을 담고 있다.
특히 기존 오픈소스 데이터로는 부족했던 “개인정보처리자/담당자(Targeted Operator)” 도메인을 신규 확장하여, 실제 업무 환경에서도 탐지 가능한 모델을 구축한 것이 핵심이다.

## 1. Project Overview
🔍 목적
GPU 서버 없이 모바일·임베디드 장치(On-device) 에서 작동할 수 있는 초경량 보이스피싱 탐지 모델 개발
개인정보처리자·담당자를 대상으로 한 공격형/정상 대화까지 탐지 가능한 도메인 확장형 모델 구축
실시간 전화 시나리오를 고려하여, 발화가 점진적으로 입력되는 환경에서의 안정적인 탐지 성능 확보
🔐 온디바이스로 설계한 이유
서버 전송 기반 AI는 통화 내용이 서버로 전송됨 → 개인정보/사생활 침해 위험
따라서 디바이스 내부에서 모든 추론이 이루어지는 모델 구조가 필수
이로 인해 경량 아키텍처(LSTM) 가 유력 후보로 선택됨

## 2. Folder Structure
voicephishing_context/
├── prompts/             # LLM 데이터 생성에 사용된 프롬프트 엔지니어링
├── notebooks/           # 실험, 역검증, 점진적 추론 실험, 모델 비교 등의 ipynb
├── models/              # 최종 선택된 LSTM 모델 및 체크포인트
└── results/             # 실험 결과 CSV, confusion matrix PNG 등
각 폴더는 아래 섹션에서 자세히 설명한다.

## 3. Data
📌 (1) 오픈소스 데이터
기본 데이터는 korccvi v2 기반의 오픈소스 금융·상담·일반 대화 데이터 약 4,800개
정상/민원/일반 문의 중심이며, 보이스피싱 또는 개인정보처리자 도메인 대화가 없음
📌 (2) LLM 기반 데이터 생성
데이터 절대량이 부족하고, 특히 개인정보처리자(Targeted Operator) 도메인이 존재하지 않기 때문에 직접 생성했다.
사용된 LLM:
Gemini 2.0 Flash Exp
GPT‑4o
Llama 모델
각 모델당:
Specific(개인정보처리자 도메인) 1,500개
Normal(일반 보이스피싱/정상) 1,500개
→ 총 9,000개 + OS 4,800개 데이터 기반
📌 (3) 프롬프트 엔지니어링 포인트
보이스피싱 실패 확률 포함 (사기 실패 시나리오 생성)
정상 대화에서도 일부 개인정보 요청/언급이 일어날 수 있게 설계 (현실성↑)
규칙 파괴 요소 삽입 → LLM이 단조로운 구조를 만들지 않도록 제어
생성 과정 전체를 notebook에서 자동화
📌 (4) 데이터 평가 & 역검증
3개 LLM의 생성 데이터 품질을 언어 다양성·표현 패턴·실제 OS 데이터 평가 성능 기준으로 역검증
그 결과를 바탕으로 open-source : Gemini : GPT = 1.3 : 1.3 : 0.6 의 loss weight로 최종 curated 세트를 구성
데이터는 절대량이 적기 때문에 모든 데이터를 사용하되 loss weight 조정만 수행

## 4. Experiments (notebooks/)
notebooks 폴더에는 아래 실험이 포함되어 있다.
✔ 1) 데이터 생성/역검증
3개 LLM 생성 데이터의 품질 비교
OS 데이터에 대한 generalization 검사
✔ 2) 모델 구조 비교
GPU 없이 구동 가능한 모델을 찾기 위해 다음을 벤치마크 비교했다.
LLM 기반 classifier
KoBERT 기반 classifier
고정된 shallow neural classifier
경량 LSTM (최종 선택)
비교 지표:
Accuracy / F1
Latency(ms)
Parameter size
메모리 footprint
✔ 3) 점진적 추론 실험 (Incremental Inference)
실제 전화처럼 입력이 문장/단어 단위로 점진적으로 들어올 때:
어느 구간에서 성능이 수렴하는지
초반 몇 %의 대화로도 탐지가 가능한지
실시간 inference latency 테스팅
→ 실제 환경 적용 가능성을 확인하는 핵심 실험

## 5. Model (models/)
최종 선택 모델: 경량 LSTM 기반 Context-Aware Classifier
이유:
온디바이스에서 수 ms 단위로 추론 가능
메모리 사용량 최소
KoBERT 대비 6–20배 빠른 추론
LLM classifier 대비 모델 크기 50배 이상 감소
모델 구조, 하이퍼파라미터, 가중치는 models/ 폴더에 저장됨

## 6. Results (results/)
OS 데이터 + LLM 생성 데이터 혼합 학습 결과를 정리한 CSV
모델별 정밀도/재현율 비교
confusion matrix PNG
점진적 추론에서의 performance curve
LLM별 데이터 품질 비교 표

## 7. Summary
voicephishing_context/는
“개인정보처리자 대상 보이스피싱 탐지”라는 새로운 도메인을 구축하고,
이를 GPU 없이 온디바이스 환경에서 실시간 탐지 가능한 모델로 구현한 전체 연구 과정의 기록이다.
핵심 특징은 다음과 같다:
기존 공개 데이터로는 불가능했던 도메인 확장을 LLM 기반 데이터 생성으로 해결
생성 데이터의 신뢰도를 **역검증(back‑validation)**으로 확보
점진적 입력 환경(전화)에서도 잘 작동하는 모델
latency·메모리 제약을 고려한 경량 LSTM 최종 선정
이 폴더는 데이터 → 프롬프트 → 실험 → 모델 → 결과까지
온디바이스 보이스피싱 탐지 솔루션을 구축하기 위한 모든 작업물을 포함한다.