# be-selectors

## 캠페인 썸네일 저장소

관리자 캠페인 썸네일은 `POST /api/admin/uploads/campaign-thumbnails`로 업로드한다.
애플리케이션은 EC2 IAM Role의 기본 AWS 자격증명 체인을 사용하며 액세스 키를 설정 파일에 저장하지 않는다.

`local` 프로필에서는 별도 AWS 설정 없이 `./.local-media`에 저장하고
`http://127.0.0.1:8080/media/...`로 제공한다. 저장 경로와 공개 URL은
`MEDIA_LOCAL_DIRECTORY`, `MEDIA_LOCAL_PUBLIC_BASE_URL`로 변경할 수 있다.

운영 `.env`에 다음 값을 설정한다.

```dotenv
AWS_REGION=ap-northeast-2
MEDIA_S3_BUCKET=hiselectors-media
MEDIA_PUBLIC_BASE_URL=https://media.hiselectors.shop
```

애플리케이션의 EC2 IAM Role에는 업로드 경로만 허용한다.

```json
{
  "Effect": "Allow",
  "Action": "s3:PutObject",
  "Resource": "arn:aws:s3:::hiselectors-media/campaigns/*"
}
```

S3 버킷은 비공개로 유지하고 `MEDIA_PUBLIC_BASE_URL`에는 CloudFront 등 공개 조회 도메인을 사용한다.
