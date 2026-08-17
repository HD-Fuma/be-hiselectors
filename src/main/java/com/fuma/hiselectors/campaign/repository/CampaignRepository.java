package com.fuma.hiselectors.campaign.repository;

import com.fuma.hiselectors.campaign.model.Campaign;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    Optional<Campaign> findByIdAndIsDeletedFalse(Long id);

    List<Campaign> findAllByIsDeletedFalseOrderByIdDesc();
}
