package com.fuma.hiselectors.creator.repository;

import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorDiscoveryInfoRepository
        extends JpaRepository<CreatorDiscoveryInfo, Long> {
}
