# ========================================
# 환경 변수 예제 파일 (env.example)
# ========================================

# Google Cloud 서비스 계정 키 경로
GOOGLE_APPLICATION_CREDENTIALS=/path/to/your/google-service-account.json

# VoIP 서버 관련 설정
ASTERISK_HOST=your_asterisk_server_ip
ASTERISK_PORT=5060
ASTERISK_USER=your_sip_username
ASTERISK_PASSWORD=your_sip_password

# 모델 서버 관련 설정
DEEPVOICE_MODEL_PATH=/path/to/deepvoice/saved_model
VOICEPHISH_MODEL_PATH=/path/to/voicephishing_context/saved_model
MODEL_API_HOST=localhost
MODEL_API_PORT=8080

# Docker 관련 환경 (선택)
DOCKER_NETWORK=voip_network

# 기타 설정
LOG_LEVEL=INFO           # 로그 레벨 (DEBUG, INFO, WARN, ERROR)
TEMP_AUDIO_DIR=/tmp/audio  # 임시 음성 파일 저장 경로

# 주의: 실제 배포 환경에서는 이 파일을 복사하여 .env로 이름을 변경 후 사용
