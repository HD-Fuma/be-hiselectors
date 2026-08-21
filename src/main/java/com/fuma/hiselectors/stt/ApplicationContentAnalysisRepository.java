package com.fuma.hiselectors.stt;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ApplicationContentAnalysisRepository
        extends JpaRepository<ApplicationContentAnalysis, Long> {

    Optional<ApplicationContentAnalysis> findByContentKey(String contentKey);

    List<ApplicationContentAnalysis> findByApplicantId(Long applicantId);

    // 파생 delete 는 트랜잭션이 필요하다. 이 호출만 짧게 감싸 커넥션을 오래 쥐지 않게 한다.
    @Transactional
    void deleteByApplicantId(Long applicantId);
}
