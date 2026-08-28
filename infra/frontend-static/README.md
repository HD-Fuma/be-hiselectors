# 정적 프론트엔드 CDN

React/Vite 산출물은 기존 private `hiselectors-deploy` 버킷의 `client/`, `admin/`
prefix를 그대로 사용한다. CloudFront OAC만 S3 읽기를 허용하며 프론트 배포 역할은 해당
prefix 쓰기와 두 distribution invalidation만 수행한다.

이 stack은 `us-east-1`에 배포한다. CloudFront viewer 인증서가 반드시 이 region에 있어야
하기 때문이다.

## 1. DNS 위임 준비

먼저 기존 Gabia zone과 똑같은 A/CNAME을 가진 Route 53 zone만 만든다. 이 단계에서는
프론트 트래픽이 계속 구 EC2로 간다.

```bash
aws cloudformation deploy \
  --region us-east-1 \
  --stack-name hiselectors-frontend \
  --template-file infra/frontend-static/template.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides EnableFrontendCdn=false RouteFrontendToCdn=false
```

stack 생성 직후 termination protection을 켠다.

```bash
aws cloudformation update-termination-protection \
  --region us-east-1 \
  --stack-name hiselectors-frontend \
  --enable-termination-protection
```

`NameServers` output의 네 nameserver로 Gabia DNS 권한을 변경한다. Route 53이 권한 DNS로
확인되기 전에는 다음 단계로 진행하지 않는다. 두 zone의 레코드가 동일하므로 위임 전파 중에도
서비스 경로는 바뀌지 않는다.

## 2. CDN 생성

Route 53 위임이 확인되면 같은 stack을 갱신한다.

```bash
aws cloudformation deploy \
  --region us-east-1 \
  --stack-name hiselectors-frontend \
  --template-file infra/frontend-static/template.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides EnableFrontendCdn=true RouteFrontendToCdn=false
```

이 update가 다음을 만들지만 A 레코드는 아직 구 EC2를 유지한다.

- `hiselectors.shop`, `www`, `app`용 client CloudFront
- `admin`용 admin CloudFront
- ACM `hiselectors.shop` + `*.hiselectors.shop` 인증서
- S3 OAC bucket policy
- `gha-fe-deploy`의 prefix 쓰기 및 invalidation 권한

stack output의 distribution ID를 각 GitHub repository variable
`CLOUDFRONT_DISTRIBUTION_ID`로 저장한다.

## 3. DNS 전환

두 distribution의 직접 접속과 프론트 배포를 검증한 뒤 DNS만 전환한다.

```bash
aws cloudformation deploy \
  --region us-east-1 \
  --stack-name hiselectors-frontend \
  --template-file infra/frontend-static/template.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides EnableFrontendCdn=true RouteFrontendToCdn=true
```

## 검증과 롤백

루트, `www`, `app`, `admin`의 `/`와 SPA deep link가 HTTPS 200인지 확인한다. 브라우저에서
API 호출도 확인한다. 롤백은 `EnableFrontendCdn=true RouteFrontendToCdn=false`로 DNS만
구 EC2 주소로 되돌린다. CDN 리소스는 그대로 유지한다. `EnableFrontendCdn=false`로 되돌리면
안 된다. 검증과 DNS TTL 경과 전에는 구 EC2를 종료하지 않는다.
