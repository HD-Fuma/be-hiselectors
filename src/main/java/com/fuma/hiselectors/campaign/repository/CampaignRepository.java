package com.fuma.hiselectors.campaign.repository;

import com.fuma.hiselectors.campaign.model.Campaign;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    Optional<Campaign> findByIdAndIsDeletedFalse(Long id);

    List<Campaign> findAllByIsDeletedFalseOrderByIdDesc();

    @Query(value = """
            select distinct s.selectors_id as selectorId, s.selectors_nickname as nickname,
                   a.sns_code as platform, a.account_id as accountId, a.follower_count as followerCount
              from product_group pg
              join product_group_item pgi on pgi.group_id = pg.product_group_id
              join selectors s on s.selectors_id = pg.selectors_id
         left join selectors_sns_account a on a.selectors_sns_account_id = (
                select account.selectors_sns_account_id
                  from selectors_sns_account account
                 where account.selectors_id = s.selectors_id and account.is_deleted = false
                 order by account.last_collected_at desc, account.selectors_sns_account_id desc
                 limit 1
            )
             where pg.campaign_id = :campaignId
               and pgi.created_at >= :startAt and pgi.created_at < :endExclusive
             order by s.selectors_id asc
            """, countQuery = """
            select count(distinct pg.selectors_id)
              from product_group pg
              join product_group_item pgi on pgi.group_id = pg.product_group_id
             where pg.campaign_id = :campaignId
               and pgi.created_at >= :startAt and pgi.created_at < :endExclusive
            """, nativeQuery = true)
    Page<CampaignParticipantProjection> findParticipants(@Param("campaignId") Long campaignId,
            @Param("startAt") LocalDateTime startAt, @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable);
}
