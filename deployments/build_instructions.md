This document explains how to build and run the VoIP Service project including the Android app, the models, and service integration.

---

## 1. Prerequisites

Make sure the environment is set up as described in `environment_setup.md`.

- Java JDK 17
- Android Studio + Gradle wrapper
- Python 3.10+
- Required Python packages installed
- Google Cloud STT service account key configured
- Docker (optional, for deployment)

---

## 2. Building the Android App

1. Open a terminal in `service_app/` folder.
2. Use Gradle wrapper to clean and build:

```bash
./gradlew clean assembleDebug
The APK will be located at:
service_app/build/outputs/apk/debug/app-debug.apk
Install the APK on a device or emulator:
adb install -r service_app/build/outputs/apk/debug/app-debug.apk

## 3. Preparing the Models

### 3.1 DeepVoice Model
Navigate to models/deepvoice/.
Optional: Run the notebook to retrain or fine-tune:
jupyter notebook deepvoice_model.ipynb
Save the trained/optimized model in saved_model/ for integration.

### 3.2 Voice Phishing Context Model
Navigate to models/voicephishing_context/.
Run preprocessing scripts to generate training data from LLM-generated datasets:
python scripts/preprocess_data.py
Train the LSTM model (or use existing notebook):
jupyter notebook lstm_model_training.ipynb
Save the trained model in saved_model/.

## 4. Service-Model Integration
Ensure integration/ scripts are set up to connect app → models.
Run integration test using a sample audio file:
python integration/pipeline_test.py
Verify detection output and logs.

## 5. Optional: Docker Deployment
Navigate to deployments/docker/.
Build Docker image:
docker build -t voip-service:latest .
Run container:
docker run -p 8080:8080 voip-service:latest
Verify service endpoint:
http://localhost:8080/health

## 6. Notes
Always use the Gradle wrapper (./gradlew) instead of system Gradle.
Models should be pre-trained and saved in saved_model/ before running integration.
Google Cloud STT credentials must be accessible via environment variable GOOGLE_APPLICATION_CREDENTIALS.
Use sample audio for testing before deploying full dataset.

---