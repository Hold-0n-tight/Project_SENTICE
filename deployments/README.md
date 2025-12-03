This folder contains all resources and instructions related to deploying the VoIP Service project, including Docker setup, Kubernetes configuration, and server environment.

---

## Folder Structure

deployments/
├── docker/ # Dockerfiles and docker-compose for service components
├── k8s/ # Kubernetes manifests and configurations (optional)
├── nginx_config/ # NGINX configuration files for API routing and reverse proxy
└── README.md # This document

---

## 1. Docker

- Contains Dockerfiles for the following components:
  - **Model containers**: DeepVoice and Voice Phishing Context models
  - **API gateway container**: Routes requests from the app to model containers
  - **Optional services**: Logging, monitoring, or auxiliary tools

- Example usage:

```bash
cd deployments/docker/
docker build -t voip-service:latest .
docker run -p 8080:8080 voip-service:latest
Ensure environment variables like GOOGLE_APPLICATION_CREDENTIALS are set before running containers.

## 2. Kubernetes (k8s)
Optional manifests for deploying the service in a cluster.
Contains deployment and service YAML files for:
API gateway
Model containers
Nginx ingress (optional)
Example usage:
kubectl apply -f deployments/k8s/

## 3. NGINX Configuration
Contains reverse proxy settings for routing app requests to the correct model services.
SSL termination can be configured here if deploying in production.

## 4. Notes
Pre-trained models must exist in the saved_model/ directories within each model container.
Ensure Docker networking allows communication between API gateway, models, and VoIP server.
This folder is primarily for deployment-related assets. All source code remains in service_app/ and models/ folders.
Refer to environment_setup.md and build_instructions.md for setup and build instructions before deploying.

---