package com.fuma.hiselectors.stt;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ApplicationContentAnalysisRepository
        extends JpaRepository<ApplicationContentAnalysis, Long> {

    Optional<ApplicationContentAnalysis> findByContentKey(String contentKey);

    List<ApplicationContentAnalysis> findByApplicantId(Long applicantId);

    // 파생 delete(SELECT 후 개별 DELETE) 대신 벌크 DELETE 한 방. 이 호출만 짧은 트랜잭션으로 감싼다.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ApplicationContentAnalysis a WHERE a.applicantId = :applicantId")
    void deleteByApplicantId(@Param("applicantId") Long applicantId);
}
