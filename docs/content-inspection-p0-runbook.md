# 콘텐츠 버전·재검수 P0 운영 확인

## 배포 게이트

운영 배포는 애플리케이션 교체 전에 다음 조건을 확인한다.

- `content_version.uq_content_version_content_no`가 `(content_id, version_no)` UNIQUE다.
- 기존 `uk_content_version_content_hash`가 있다면 정확히 `(content_id, content_hash)` UNIQUE다.
- 검증 후 `015_drop_content_version_hash_unique.sql`을 실행하고 해시 UNIQUE가 사라졌는지 다시 확인한다.

배포 워크플로의 마이그레이션은 멱등하다. 이미 해시 UNIQUE가 없으면 no-op이며, 버전 번호 UNIQUE가 없거나 기존 해시 인덱스 정의가 예상과 다르면 배포를 중단한다.

## 배포 후 재처리

1. 새 `Idempotency-Key`로 `POST /api/admin/content-batch/run`을 호출한다.
2. 응답의 실행 ID를 `GET /api/admin/task-runs/{runId}`로 조회해 `CONTENT_SYNC`와 후속 `CONTENT_REPORT_GENERATION` 완료를 확인한다.
3. 기존 실패 콘텐츠 243, 245, 246의 최신 버전을 확인한다.

```sql
SELECT c.content_id,
       c.last_version_no,
       cv.content_version_id,
       cv.version_no,
       cv.content_hash,
       cv.creation_reason,
       cv.status,
       cv.inspection_decision,
       cv.created_at
FROM content c
JOIN content_version cv
  ON cv.content_id = c.content_id
 AND cv.version_no = c.last_version_no
WHERE c.content_id IN (243, 245, 246)
ORDER BY c.content_id;
```

4. 각 콘텐츠에 이전 실패 이후 생성된 최신 버전과 최신 정책 리포트가 있는지 확인한다.
5. 기존에 재확정이 막힌 콘텐츠는 최신 `contentVersionId`로 `POST /api/admin/content-versions/{contentVersionId}/inspect`를 한 번 실행한다. 수동 실행은 동일 정책이어도 새 관리자 검토로 취급한다.
6. 재검출 위반은 `PENDING`, 미검출된 기존 열린 위반은 `RESOLVED`인지 확인한다.
7. 위반이 모두 해소된 콘텐츠는 `APPROVED`, `violations=[]`로 확정한다. 일부 위반이 남으면 최신 `PENDING` 항목 전체를 확정 또는 기각한다.
8. 열린 위반이 없을 때 자동 패널티가 해제됐는지 확인한다. 관리자 수동 패널티는 자동 해제 대상이 아니다.

운영 데이터의 위반 상태를 SQL로 직접 변경하지 않는다. 재검수와 관리자 확정 API를 통해 정상화한다.
