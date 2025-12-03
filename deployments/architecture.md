This document describes the architecture of the VoIP Service deployment, including service components, model integration, and cloud infrastructure.

---

## 1. Overview

The VoIP Service consists of:

1. **Android VoIP App**  
   - Collects audio streams from users  
   - Sends audio to Google Cloud STT for transcription  
   - Receives model inference results for phishing detection

2. **DeepVoice & Voice Phishing Detection Models**  
   - Hosted on internal servers or local machines  
   - Provide inference results via gRPC API

3. **Asterisk VoIP Server (AWS EC2)**  
   - Handles SIP signaling and RTP audio streams  
   - Routes audio data to the Android app

4. **Deployment Environment**  
   - Docker containers for modular deployment  
   - Nginx reverse proxy for API routing  
   - Optional Kubernetes for scaling

---

## 2. Component Diagram

[Android App] ---> [Google Cloud STT]
|
v
[Model Gateway (gRPC)] ---> [DeepVoice Model]
[Voice Phishing Model]
|
v
[Asterisk Server (AWS)]

> Diagram can also be attached as `deployment_architecture.png` in this folder.

---

## 3. Data Flow

1. User speaks into the VoIP app.
2. App streams audio to Google Cloud STT for transcription.
3. Transcribed text is sent to phishing detection models via gRPC.
4. Model inference results are returned to the app.
5. App displays results in real-time (toast notification or alert).

---

## 4. Deployment Details

- **Asterisk Server (AWS)**  
  - Ubuntu 20.04 LTS  
  - Ports: 5060 (SIP), 10000–20000 (RTP)  
  - TLS optional

- **Docker Setup**  
  - Each model runs in a separate container  
  - API gateway container routes requests to models

- **NGINX Reverse Proxy**  
  - Exposes API endpoints for the app  
  - Handles SSL termination if required

---

## 5. Notes

- Ensure environment variables (e.g., `GOOGLE_APPLICATION_CREDENTIALS`) are set in all containers.  
- Models must be pre-trained and stored in `saved_model/` directories.  
- Docker networking should allow communication between API gateway, models, and VoIP server.  
