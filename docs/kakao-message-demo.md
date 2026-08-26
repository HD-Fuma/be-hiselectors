# 카카오톡 메시지 시연 설정

이 기능은 Kakao Developers의 사용자 간 메시지 API를 사용한 교육·시연용 기능이다.
서비스가 사용자에게 발송하는 알림톡과 동일한 기능이 아니며, 운영 서비스에서는 알림톡이나
브랜드 메시지 같은 비즈니스 메시징 제품으로 `NotificationSender` 구현체를 교체해야 한다.

## 환경변수

```text
KAKAO_REST_API_KEY=<앱 REST API 키>
KAKAO_CLIENT_SECRET=<REST API Client Secret>
KAKAO_TOKEN_ENCRYPTION_KEY=<32바이트 난수를 Base64로 인코딩한 값>
KAKAO_MESSAGE_WEB_URL=http://localhost:3000
KAKAO_MESSAGE_MOBILE_WEB_URL=http://localhost:3000
KAKAO_MESSAGE_IMAGE_URL=<HTTPS 이미지 URL>
```

암호화 키 예시는 PowerShell에서 다음과 같이 만들 수 있다. 생성한 값은 소스 코드가 아닌
팀의 Secret 저장소에 보관한다.

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

## Kakao Developers 설정

1. 카카오 로그인을 활성화한다.
2. Redirect URI에 `KAKAO_REDIRECT_URI`와 같은 주소를 등록한다.
3. 동의항목에 `friends`, `talk_message`, `profile_nickname`을 설정한다.
4. 제품 링크 관리에 메시지의 웹 URL 도메인을 등록한다.
5. 공용 발신 계정과 수신 테스트 계정을 앱 멤버로 등록한다.
6. 두 계정을 실제 카카오톡 친구로 등록한다.

추가 기능 사용 권한이 없는 앱에서는 앱 멤버만 친구 목록에 나타나고 메시지를 받을 수 있다.
수신 계정도 같은 앱에 카카오 로그인하고 `friends`, `talk_message`에 동의해야 한다.

## 시연 순서

1. `local` 프로필로 서버를 실행하고 `/kakao-test`에 접속한다.
2. 서비스 관리자 JWT와 사용자 JWT를 입력해 저장한다.
3. 공용 발신 계정을 먼저 연결한다.
4. 수신 사용자 계정을 연결한다.
5. 친구 목록을 조회하고 UUID 동기화를 실행한다.
6. 나에게 보내기와 친구에게 보내기를 차례로 검증한다.

친구 목록은 최대 10분간 캐시될 수 있다. 수신자가 즉시 보이지 않으면 앱 연결 및 동의항목을
확인하고 캐시 만료 후 다시 시도한다. 실제 카카오 메시지 발송은 자동 테스트에 포함하지 않는다.
