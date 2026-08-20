package com.fuma.hiselectors.application.repository;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByUserIdAndGenerationId(Long userId, Long generationId);

    List<Application> findByMediaCollectionStatusInAndMediaCollectionRetryCountLessThanOrderByIdAsc(
            Collection<MediaCollectionStatus> statuses, int maxRetryCount, Pageable pageable);
}
