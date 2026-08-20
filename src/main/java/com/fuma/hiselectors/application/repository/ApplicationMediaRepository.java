package com.fuma.hiselectors.application.repository;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationMediaRepository extends JpaRepository<ApplicationMedia, Long> {

    List<ApplicationMedia> findTop3ByApplicationIdOrderBySequenceNoAsc(Long applicationId);

    List<ApplicationMedia> findAllByApplicationIdOrderBySequenceNoAsc(Long applicationId);

    List<ApplicationMedia> findAllByApplicationIdInOrderByApplicationIdAscSequenceNoAsc(
            Collection<Long> applicationIds);

    void deleteByApplicationId(Long applicationId);
}
