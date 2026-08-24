package com.fuma.hiselectors.application.repository;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationMediaRepository extends JpaRepository<ApplicationMedia, Long> {

    List<ApplicationMedia> findTop3ByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(
            Long applicationId);

    List<ApplicationMedia> findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(
            Long applicationId);

    List<ApplicationMedia>
            findAllByApplicationIdInOrderByApplicationIdAscSequenceNoAscMediaSequenceNoAsc(
            Collection<Long> applicationIds);

    void deleteByApplicationId(Long applicationId);
}
