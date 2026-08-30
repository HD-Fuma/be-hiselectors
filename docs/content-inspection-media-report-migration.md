# 콘텐츠 미디어 추출·상세 리포트 전환 가이드

## 적용 결과

- `TEXT` 미디어의 `body.text`는 플랫폼 제목·설명·캡션 원문으로 유지한다.
- `IMAGE`와 `VIDEO`의 `body`에는 `schemaVersion=1.2`와 `stt/ocr.segments`를 저장한다.
- 검수 상세 분석은 `content_report.analysis`의 `overview/insight`에 저장한다.
- 같은 `(content_version_id, inspection_policy_id)` 재검수는 리포트를 추가하지 않고 갱신한다.
- 근거는 TEXT의 UTF-16 범위 또는 미디어 추출 `segmentId`를 저장한다. 시간과 bbox는 조회 시 `content_media.body`에서 해석한다.
- 미디어 원본이 없거나 여러 미디어 중 하나라도 추출에 실패하면 해당 콘텐츠 검수 전체를 실패 처리한다.

## 배포 순서

1. 콘텐츠 검수 배치와 수동 재검수 요청을 일시 중지한다.
2. `content_report`, `violation_evidence_history`, `content_media`를 백업한다.
3. `src/main/resources/db/017_content_inspection_report_contract.sql`을 적용한다.
4. `src/main/resources/db/018_content_report_policy_upsert.sql`을 적용한다.
   - 같은 버전·정책의 중복 리포트는 가장 최근 행으로 통합된다.
   - 위반 근거 이력의 `content_report_id`는 통합된 최신 리포트로 이동한다.
   - `(content_version_id, inspection_policy_id)` UNIQUE 제약이 생성된다.
5. Java 애플리케이션과 `stt-worker`를 같은 릴리스로 배포한다.
6. 아래 콘텐츠 전용 설정을 확인한다.

```text
CONTENT_INSPECTION_GEMINI_API_KEY
CONTENT_INSPECTION_GEMINI_API_KEYS
CONTENT_INSPECTION_ANALYSIS_MODEL
CONTENT_INSPECTION_ANALYSIS_FALLBACK_MODELS
CONTENT_INSPECTION_EXTRACTION_MODEL
CONTENT_INSPECTION_EXTRACTION_FALLBACK_MODELS
CONTENT_INSPECTION_INSTAGRAM_STT_MODEL
CONTENT_INSPECTION_INSTAGRAM_OCR_MODEL
CONTENT_INSPECTION_STT_WORKER_BASE_URL
```

전용 키를 아직 발급하지 않았다면 콘텐츠 Gemini 키 설정은 기존 `GEMINI_API_KEY(S)`로 폴백한다. 분석 모델과 추출 모델은 별도 설정이므로 동일하게 지정하더라도 정책 해시와 실행 경로는 분리된다.

7. Instagram 콘텐츠 전용 SageMaker endpoint와 모델 아티팩트를 배포하고 `/content/reel` 상태를 확인한다.
8. 애플리케이션을 시작해 `InspectionPolicyService`가 `content-inspection-v8` 정책을 활성화했는지 확인한다.
9. YouTube 1건, Instagram 단일 이미지 1건, 캐러셀 1건으로 스모크 검수를 실행한다.
10. 검수 요청을 재개한다.

## 배포 확인 SQL

```sql
SELECT content_version_id,
       inspection_policy_id,
       COUNT(*) AS report_count
FROM content_report
WHERE inspection_policy_id IS NOT NULL
GROUP BY content_version_id, inspection_policy_id
HAVING COUNT(*) > 1;
```

결과가 없어야 한다.

```sql
SELECT cr.content_report_id,
       cr.content_version_id,
       cr.inspection_policy_id,
       cr.report_schema_version,
       JSON_TYPE(cr.analysis) AS analysis_type,
       JSON_TYPE(cr.execution_metadata) AS metadata_type
FROM content_report cr
ORDER BY cr.content_report_id DESC
LIMIT 20;
```

신규 리포트의 schema version은 `1.0`, JSON 타입은 `OBJECT`여야 한다.

```sql
SELECT cm.content_media_id,
       cm.media_type,
       JSON_UNQUOTE(JSON_EXTRACT(cm.body, '$.schemaVersion')) AS schema_version,
       cm.extracted_with_policy_id,
       cm.extracted_at
FROM content_media cm
WHERE cm.media_type IN ('IMAGE', 'VIDEO')
ORDER BY cm.content_media_id DESC
LIMIT 20;
```

검수 완료 미디어는 schema version `1.2`와 추출 정책/시각이 있어야 한다.

## 실패 및 재처리

- 원본 URL 또는 YouTube video ID가 없으면 `CONTENT_MEDIA_SOURCE_UNAVAILABLE`로 실패한다.
- Instagram CDN 만료는 Business Discovery로 URL을 한 번 갱신한 뒤 재시도한다. 갱신 실패는 `CONTENT_MEDIA_SOURCE_UNAVAILABLE`, 워커/endpoint 실패는 `STT_WORKER_CALL_FAILED`로 실패한다.
- 한 캐러셀에서 일부 미디어만 성공해도 DB에는 어느 미디어도 반영하지 않는다.
- 기존 `body.text` 기반 IMAGE/VIDEO는 직접 덮어쓰지 않는다. 다음 검수에서 `EXTRACTION_CHANGE` 새 콘텐츠 버전을 만들고 구조화 추출한다.
- 실패한 새 버전은 원인을 해결한 뒤 최신 버전 ID로 검수를 다시 실행한다. SQL로 body나 검수 상태를 직접 수정하지 않는다.

## 롤백 주의

- `018` 적용 후 구버전 애플리케이션은 같은 버전·정책 리포트를 다시 INSERT해 UNIQUE 충돌을 일으킬 수 있다. 애플리케이션만 구버전으로 롤백하지 않는다.
- 코드 롤백이 필요하면 먼저 검수 요청을 중지하고, 리포트 업서트를 이해하는 호환 릴리스로 롤백한다.
- `017/018` 컬럼과 제약은 데이터 보존을 위해 즉시 삭제하지 않는다.
- 구조화 추출이 저장된 콘텐츠 버전은 과거 버전으로 유지하며 legacy `body.text`로 역변환하지 않는다.
