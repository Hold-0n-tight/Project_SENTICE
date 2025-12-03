This document describes how to set up the development and deployment environment for the VoIP Service project.

---

## 1. System Requirements

- **OS:** macOS (tested), Linux (optional)
- **CPU:** 4-core or higher recommended
- **RAM:** 8GB minimum, 16GB recommended
- **Disk:** 20GB free space for source code, build, and datasets

---

## 2. Software Requirements

| Tool / SDK                 | Version / Notes                                  |
|-----------------------------|-------------------------------------------------|
| Java JDK                    | 17 (or compatible with Gradle 8.x)             |
| Android Studio              | Arctic Fox or newer                             |
| Gradle                      | 8.x (wrapper provided in project)              |
| Python                      | 3.10+ (for preprocessing scripts and ML)       |
| pip                         | latest                                          |
| ffmpeg                      | latest (required for audio preprocessing)      |
| Docker                      | latest (for containerized deployment)          |
| Kubernetes (optional)       | latest (for cluster deployment)                |

---

## 3. Project Dependencies

### 3.1 Python Dependencies

Run:

```bash
pip install -r requirements.txt
Requirements include:
torch
torchaudio
transformers
librosa
matplotlib
scikit-learn
pandas
jupyter

### 3.2 Android Dependencies
All Android dependencies are managed by Gradle.
Make sure local.properties points to correct Android SDK path:
sdk.dir=/Users/<username>/Library/Android/sdk
Gradle wrapper is included (gradlew) and should be used for all builds.

### 3.3 Google Cloud Setup
Create a service account in Google Cloud with Speech-to-Text API access.
Download the JSON key and set environment variable:
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account-key.json"
Verify access:
gcloud auth activate-service-account --key-file=$GOOGLE_APPLICATION_CREDENTIALS
gcloud config set project <PROJECT_ID>

## 4. VoIP Service Deployment Environment
Asterisk Server: AWS EC2 instance (Ubuntu 20.04 recommended)
Dependencies: asterisk, asterisk-config, nginx
Ports: SIP 5060 (UDP), RTP 10000-20000 (UDP)
TLS/SSL: Optional for secure SIP communication

## 5. Local Testing
Clone the repository:
git clone <repository_url>
cd VoipService
Build Android app:
./gradlew clean assembleDebug
Start preprocessing scripts:
python scripts/preprocess_data.py
Run VoIP app for testing or debugging.

## 6. Notes
Ensure Python virtual environment is activated to avoid dependency conflicts.
Update env.example file with correct API keys and environment variables before running.
For Docker or Kubernetes deployment, refer to deployment/docker/ and deployment/k8s/.

---