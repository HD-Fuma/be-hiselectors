package com.fuma.hiselectors.selectors.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import java.util.List;
import java.util.Map;

/**
 * 테스트 계정 리셋 결과.
 *
 * @param snsCode           리셋 대상 플랫폼
 * @param accountId         입력받은 SNS 계정명
 * @param selectorsIds      삭제한 셀렉터스 ID
 * @param applicationIds    삭제한 지원서 ID
 * @param deletedRowCount   삭제한 전체 행 수
 * @param deletedRowCounts  테이블별 삭제 행 수. 0 건인 테이블은 담지 않는다
 */
public record SelectorsTestResetResponse(
        SnsPlatform snsCode,
        String accountId,
        List<Long> selectorsIds,
        List<Long> applicationIds,
        int deletedRowCount,
        Map<String, Integer> deletedRowCounts
) {
}
