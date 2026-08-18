package com.fuma.hiselectors.campaign.repository;

import com.fuma.hiselectors.campaign.model.CampaignProduct;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignProductRepository extends JpaRepository<CampaignProduct, Long> {
    @EntityGraph(attributePaths = "product")
    List<CampaignProduct> findAllByCampaignIdOrderByIdAsc(Long campaignId);

    @EntityGraph(attributePaths = "product")
    List<CampaignProduct> findAllByCampaignIdInOrderByCampaignIdAscIdAsc(List<Long> campaignIds);
}
