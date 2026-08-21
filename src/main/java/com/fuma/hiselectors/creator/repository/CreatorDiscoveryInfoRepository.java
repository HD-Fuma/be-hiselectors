package com.fuma.hiselectors.creator.repository;

import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorDiscoveryInfoRepository
        extends JpaRepository<CreatorDiscoveryInfo, Long> {

    /** Instagram 핸들이 추출된 활성 계정. */
    List<CreatorDiscoveryInfo>
            findByCreatorPoolSnsCodeAndCreatorPoolDeletedFalseAndIgHandleIsNotNullOrderByIdAsc(
                    String snsCode);
}
