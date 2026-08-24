# 정산 정보 암호화 설정

`settlement_account.account_number`와 `settlement_account.business_number`는
AES-256-GCM 암호문만 저장한다. 기존 평문 데이터는 지원하지 않으므로 적용 전에 데이터를
초기화하고 `src/main/resources/db/010_settlement_account_encryption.sql`을 실행한다.

운영 환경에는 카카오 토큰 키와 별개의 32바이트 키를 Base64로 인코딩해 설정한다.

```dotenv
SETTLEMENT_ACCOUNT_ENCRYPTION_KEY=<32바이트 난수를 Base64로 인코딩한 값>
```

키를 분실하면 저장된 정산 정보를 복호화할 수 없다. 키는 소스 코드나 DB가 아닌 Secret
저장소에서 관리하고, SQL 적용과 환경변수 설정을 마친 뒤 애플리케이션을 배포하고 더미
데이터를 다시 적재한다.
