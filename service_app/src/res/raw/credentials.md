# Google Cloud Credentials 설정 안내

이 애플리케이션의 일부 기능은 Google Cloud 서비스와의 연동이 필요합니다. 이를 위해 서비스 계정 인증 정보가 담긴 `credentials.json` 파일이 필요합니다.

## 설정 방법

1.  Google Cloud Platform 콘솔에서 서비스 계정 키를 생성하고 JSON 형식으로 다운로드합니다.
2.  다운로드한 파일의 이름을 `credentials.json`으로 변경합니다.
3.  해당 파일을 이 디렉토리(`app/src/main/res/raw/`) 안에 위치시킵니다.

**⚠️ 중요 보안 안내**

`credentials.json` 파일에는 민감한 개인 키 정보가 포함되어 있으므로, **절대로 Git과 같은 버전 관리 시스템에 커밋해서는 안 됩니다.**

실수로라도 커밋되는 것을 방지하기 위해, 프로젝트의 최상위 `.gitignore` 파일에 다음 경로가 반드시 포함되어 있는지 확인하십시오.

```
/app/src/main/res/raw/credentials.json
```
