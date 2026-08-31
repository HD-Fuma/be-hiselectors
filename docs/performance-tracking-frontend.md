# 성과 추적 / 셀렉터스 매칭 — 프론트 연동 가이드

브랜치: `Feat/performance-tracking`
모든 엔드포인트는 **관리자 인증** 필요(기존 admin 토큰 그대로).

요청 3파트 중 **Part 1·2a 는 백엔드 변경 없음(FE 작업만)**, 나머지는 신규 API.

---

## Part 1. 콘텐츠 유형 차트 확대 — FE만

- 화면: `/performance/contents`
- 백엔드 변경 **없음**. 기존 `GET /api/admin/content-performance` 데이터를 그대로 사용.
- FE: 콘텐츠 유형 차트를 열의 절반 폭으로 레이아웃만 조정.

## Part 2a. 성과 Top 5 — FE만

- 화면: `/performance/selectors` → "기간 성과" 탭
- 백엔드 변경 **없음**. 기존 요약 API에 이미 포함되어 있음:
  `GET /api/admin/selector-performance/summary?generationId&startDate&endDate`
- 응답의 **`top5`** 배열을 "성과 분포/추이" 자리에 렌더:
  ```jsonc
  "top5": [
    { "selectorId": 12, "nickname": "...", "profileImageUrl": "...",
      "generationName": "4기", "totalSales": 1234000, "rank": 1, "previousRank": 3 }
  ]
  ```

---

## Part 2b. 셀렉터스 개인 상세 성과 차트 — 신규

셀렉터스 상세 클릭 시, 상품별·캠페인별 확정매출로 "두각" 확인.

**`GET /api/admin/selector-performance/{selectorId}/breakdown`**

| 쿼리 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `startDate` | `YYYY-MM-DD` | N | 생략 시 전체 기간 |
| `endDate` | `YYYY-MM-DD` | N | 해당 날짜 전체 포함 |

응답 `SelectorBreakdownResponse`:
```jsonc
{
  "selectorId": 12,
  "selectorsCode": "SEL-0012",
  "nickname": "뷰티하는곰",
  "category": "BEAUTY",
  "products": [
    { "productId": 501, "productName": "...", "brandName": "...",
      "thumbnailUrl": "...", "category": "BEAUTY",
      "confirmedSales": 980000, "confirmedOrderCount": 14, "soldQuantity": 20 }
  ],
  "campaigns": [
    { "campaignId": 7, "title": "가을 뷰티 기획전",
      "confirmedSales": 1200000, "confirmedOrderCount": 18, "soldQuantity": 25 }
  ]
}
```
- 매출 내림차순 정렬. 각각 그대로 막대/도넛 차트에 사용.
- 확정(구매확정) 매출만 집계, 본인 구매 제외.
- 주의: 한 상품이 여러 캠페인에 속하면 캠페인 매출이 중복 계상될 수 있음(개인 두각 확인용).

---

## Part 3-①. 적합 셀렉터스 추천(매칭) — 신규

신규 상품/캠페인 등록 화면에서 추천 리스트를 띄우는 용도.

**`GET /api/admin/selector-matching`**

카테고리 지정 방식 **3택 1** (하나만 주면 됨):

| 쿼리 | 설명 |
|---|---|
| `category` | 카테고리 코드 직접 지정 (예: `BEAUTY`) |
| `productId` | 상품 ID → 서버가 그 상품의 카테고리로 추천 |
| `campaignId` | 캠페인 ID → 캠페인 소속 상품들의 카테고리(복수)로 추천 |
| `startDate` / `endDate` | (선택) 과거 실적 집계 기간, 생략 시 전체 |
| `limit` | (선택, 기본 20, 1~100) 최대 인원 |

> 셋 다 없으면 400. 상품 등록 화면은 보통 `productId`, 기획전(캠페인) 화면은 `campaignId` 를 넘기면 편합니다.

응답 `SelectorMatchResponse[]` (추천 순):
```jsonc
[
  {
    "selectorId": 12,
    "selectorsCode": "SEL-0012",
    "nickname": "뷰티하는곰",
    "category": "BEAUTY",
    "profileImageUrl": "...",
    "categorySales": 3400000,      // 해당 카테고리 과거 확정매출
    "categoryOrderCount": 42,
    "representativeMatch": true,    // 대표 카테고리 일치 여부
    "matchReason": "BEAUTY 카테고리 확정매출 3,400,000원 · 주문 42건"
  }
]
```
- 정렬: 카테고리 확정매출 ↓ → 대표카테고리 일치 → 주문수 ↓.
- 실적이 없어도 대표 카테고리가 일치하는 셀렉터스는 포함(신규 카테고리 커버). 이 경우 `categorySales: 0`, `matchReason`은 "대표 카테고리(...) 일치".
- 삭제·블랙리스트 셀렉터스는 제외됨.
- FE: 이 리스트에서 체크 선택 → 아래 제안 발송 API에 `selectorIds`로 전달.

---

## Part 3-②. 제안 메일 발송 — 신규

선택한 셀렉터스들에게 제안 메일을 다건 발송(Gmail). 기존 크리에이터 제안과 동일한 비동기(TaskRun) 방식.

**`POST /api/admin/selector-proposals`**

헤더: `Idempotency-Key: <UUID>` (필수, 중복 발송 방지 — 재시도 시 같은 키 사용)

바디 `SelectorProposalRequest`:
```jsonc
{
  "selectorIds": [12, 45, 78],   // 필수, 1~500명
  "subject": "이번 기획전에도 함께해요",  // 선택
  "body": "안녕하세요 ${recipientName}님 ..." // 선택 (subject와 함께 or 둘 다 생략)
}
```
- `subject`/`body`를 **둘 다 생략**하면 서버 기본 셀렉터스 템플릿 사용.
- `body`에 `${recipientName}` `${adminName}` `${adminPosition}` `${adminEmail}` `${proposalLink}` 치환 변수 사용 가능(수신자별 이름 자동 치환).
- 이메일이 없는 셀렉터스는 자동 제외. 유효 수신자가 0명이면 409(`SELECTOR_EMAIL_REQUIRED`).

**응답 202** `TaskRunResponse` — 즉시 반환, 실제 발송은 백그라운드:
```jsonc
{ "runId": "d1f...uuid", "taskType": "SELECTOR_PROPOSAL_EMAIL_SEND",
  "status": "QUEUED", "totalCount": 3, "succeededCount": 0, "failedCount": 0, ... }
```

**발송 성공/실패 확인** — `runId`로 폴링 또는 SSE(기존 태스크 패널과 동일):
- 폴링: `GET /api/admin/task-runs/{runId}` → `succeededCount` / `failedCount` / `status`(`SUCCEEDED` | `PARTIAL_FAILED` | `FAILED`)
- 스트림: `GET /api/admin/task-runs/stream` (SSE)
- 한 명 실패해도 나머지는 계속 발송되며 실패 건은 `failedCount`에 집계. 전원 실패 시 `FAILED`.

에러 코드: 400(Idempotency-Key 누락/형식), 409(멱등키 충돌 또는 유효 수신자 없음).

---

### 권장 화면 흐름 (Part 3)
1. 상품/캠페인 등록 화면에서 `selector-matching` 호출(`productId` 또는 `campaignId`) → 추천 리스트 표시
2. 관리자가 체크 + (선택)멘트 편집
3. `Idempotency-Key`(프론트에서 UUID 생성) 붙여 `POST /selector-proposals`
4. 반환된 `runId`로 태스크 패널/폴링하여 성공·실패 표시
