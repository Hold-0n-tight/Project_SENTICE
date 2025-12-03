# Deployment Guide - VoIP Service

## 1. 개요
본 서비스는 AWS EC2 상에서 Asterisk PBX와 VoIP 앱을 연동하여 구성됩니다.
이 문서는 배포 환경, 설치 순서 및 기본 설정 방법을 설명합니다.

## 2. 서버 환경
- OS: Ubuntu 22.04 LTS
- CPU/RAM: 최소 2vCPU / 4GB RAM
- 네트워크: 공인 IP, 방화벽/보안 그룹 설정 필요

## 3. Asterisk PBX 설치
1. EC2 인스턴스 생성 (Ubuntu)
2. 필요 패키지 설치
   ```bash
   sudo apt update
   sudo apt install -y build-essential git wget
Asterisk 설치
wget http://downloads.asterisk.org/pub/telephony/asterisk/asterisk-20-current.tar.gz
tar xvfz asterisk-20-current.tar.gz
cd asterisk-20*/
./configure
make
sudo make install
sudo make samples
sudo make config
서비스 시작
sudo systemctl start asterisk
sudo systemctl enable asterisk

## 4. VoIP 앱 연동
앱에서 SIP 계정 설정 (Asterisk 서버 IP + 포트)
오디오/RTCP 포트 허용 (UDP 5060, 10000-20000)
STT 및 탐지 모델과 연동 확인

## 5. 네트워크 및 포트 구성
서비스	포트	프로토콜
SIP	5060	UDP
RTP	10000-20000	UDP
SSH	22	TCP

## 6. 참고 사항
보안 그룹 설정 필수
Asterisk CLI (asterisk -rvv)로 상태 확인
필요 시 SSL/TLS 설정 가능
