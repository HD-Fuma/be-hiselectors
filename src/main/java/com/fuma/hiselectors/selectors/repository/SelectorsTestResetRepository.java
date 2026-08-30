package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 테스트 계정 리셋용 조회·삭제 쿼리.
 *
 * <p>한 셀렉터스에 매달린 테이블이 열다섯 개가 넘어 도메인별 리포지터리에 흩어 두면
 * 삭제 순서를 한눈에 볼 수 없다. 외래키 순서가 곧 정확성이라 이 파일에 모아 둔다.
 * 삭제 순서는 {@code SelectorsTestResetService} 가 정한다.
 */
@Repository
@RequiredArgsConstructor
public class SelectorsTestResetRepository {

    private final EntityManager entityManager;

    /** 지원서를 SNS 계정으로 찾는다. {@code accountIdCandidates} 는 소문자로 정규화된 후보군. */
    public List<Long> findApplicationIds(
            SnsPlatform snsCode, Collection<String> accountIdCandidates) {
        return entityManager.createQuery("""
                        select a.id from Application a
                        where a.snsCode = :snsCode
                          and lower(a.snsAccountId) in :candidates
                        """, Long.class)
                .setParameter("snsCode", snsCode)
                .setParameter("candidates", accountIdCandidates)
                .getResultList();
    }

    public List<Long> findSelectorsIdsBySnsAccount(
            SnsPlatform snsCode, Collection<String> accountIdCandidates) {
        return entityManager.createQuery("""
                        select account.selectorsId from SelectorsSnsAccount account
                        where account.snsCode = :snsCode
                          and lower(account.accountId) in :candidates
                        """, Long.class)
                .setParameter("snsCode", snsCode)
                .setParameter("candidates", accountIdCandidates)
                .getResultList();
    }

    /** 지원서에서 출발해 같은 사용자·지원서에 묶인 셀렉터스까지 찾는다. */
    public List<Long> findSelectorsIdsByApplicationIds(Collection<Long> applicationIds) {
        return entityManager.createQuery("""
                        select s.id from Selectors s
                        where s.applicationId in :applicationIds
                           or s.userId in (
                                select a.userId from Application a where a.id in :applicationIds)
                        """, Long.class)
                .setParameter("applicationIds", applicationIds)
                .getResultList();
    }

    /** 셀렉터스가 이미 있으면 과거 기수 지원서까지 함께 지워야 재지원이 막히지 않는다. */
    public List<Long> findApplicationIdsBySelectorsIds(Collection<Long> selectorsIds) {
        return entityManager.createQuery("""
                        select a.id from Application a
                        where a.userId in (
                                select s.userId from Selectors s
                                where s.id in :selectorsIds and s.userId is not null)
                           or a.id in (
                                select s.applicationId from Selectors s
                                where s.id in :selectorsIds and s.applicationId is not null)
                        """, Long.class)
                .setParameter("selectorsIds", selectorsIds)
                .getResultList();
    }

    public List<Long> findContentIds(Collection<Long> selectorsIds) {
        return entityManager.createQuery(
                        "select c.id from Content c where c.selectorsId in :selectorsIds",
                        Long.class)
                .setParameter("selectorsIds", selectorsIds)
                .getResultList();
    }

    public List<Long> findContentVersionIds(Collection<Long> contentIds) {
        return entityManager.createQuery(
                        "select v.id from ContentVersion v where v.contentId in :contentIds",
                        Long.class)
                .setParameter("contentIds", contentIds)
                .getResultList();
    }

    public List<String> findSelectorsCodes(Collection<Long> selectorsIds) {
        return entityManager.createQuery("""
                        select s.selectorsCode from Selectors s
                        where s.id in :selectorsIds and s.selectorsCode is not null
                        """, String.class)
                .setParameter("selectorsIds", selectorsIds)
                .getResultList();
    }

    public int deleteViolationEvidenceHistories(Collection<Long> contentVersionIds) {
        return deleteIn(
                "delete from violation_evidence_history where content_version_id in (:ids)",
                contentVersionIds);
    }

    public int deleteViolationItems(Collection<Long> contentIds) {
        return deleteIn("delete from violation_item where content_id in (:ids)", contentIds);
    }

    public int deleteContentReports(Collection<Long> contentVersionIds) {
        return deleteIn(
                "delete from content_report where content_version_id in (:ids)",
                contentVersionIds);
    }

    public int deleteContentMedia(Collection<Long> contentVersionIds) {
        return deleteIn(
                "delete from content_media where content_version_id in (:ids)",
                contentVersionIds);
    }

    public int deleteContentEngagements(Collection<Long> contentIds) {
        return deleteIn("delete from content_engagement where content_id in (:ids)", contentIds);
    }

    public int deleteContentVersions(Collection<Long> contentIds) {
        return deleteIn("delete from content_version where content_id in (:ids)", contentIds);
    }

    public int deleteContents(Collection<Long> contentIds) {
        return deleteIn("delete from content where content_id in (:ids)", contentIds);
    }

    public int deletePenaltyHistories(Collection<Long> selectorsIds) {
        return deleteIn("delete from penalty_history where selectors_id in (:ids)", selectorsIds);
    }

    public int deleteClickLogs(Collection<Long> selectorsIds) {
        return deleteIn("delete from click_log where selectors_id in (:ids)", selectorsIds);
    }

    public int deletePurchaseHistories(Collection<Long> selectorsIds) {
        return deleteIn("delete from purchase_history where selectors_id in (:ids)", selectorsIds);
    }

    public int deleteProductGroupItems(Collection<Long> selectorsIds) {
        return deleteIn("""
                delete from product_group_item
                where group_id in (
                        select product_group_id from product_group where selectors_id in (:ids))
                """, selectorsIds);
    }

    public int deleteProductGroups(Collection<Long> selectorsIds) {
        return deleteIn("delete from product_group where selectors_id in (:ids)", selectorsIds);
    }

    public int deleteBlacklistHistories(Collection<Long> selectorsIds) {
        return deleteIn("delete from blacklist_history where selectors_id in (:ids)", selectorsIds);
    }

    public int deleteSettlementHistories(Collection<Long> selectorsIds) {
        return deleteIn(
                "delete from settlement_history where selectors_id in (:ids)", selectorsIds);
    }

    public int deleteSettlementAccounts(Collection<Long> selectorsIds) {
        return deleteIn(
                "delete from settlement_account where selectors_id in (:ids)", selectorsIds);
    }

    public int deleteExcellenceSelections(Collection<Long> selectorsIds) {
        return deleteIn(
                "delete from selector_excellence_selection where selectors_id in (:ids)",
                selectorsIds);
    }

    public int deleteSelectorsGenerations(Collection<Long> selectorsIds) {
        return deleteIn(
                "delete from selectors_generation where selectors_id in (:ids)", selectorsIds);
    }

    public int deleteSelectorsSnsAccounts(Collection<Long> selectorsIds) {
        return deleteIn(
                "delete from selectors_sns_account where selectors_id in (:ids)", selectorsIds);
    }

    public int deleteSelectors(Collection<Long> selectorsIds) {
        return deleteIn("delete from selectors where selectors_id in (:ids)", selectorsIds);
    }

    public int deleteApplicationMedia(Collection<Long> applicationIds) {
        return deleteIn(
                "delete from application_media where application_id in (:ids)", applicationIds);
    }

    public int deleteApplicationReports(Collection<Long> applicationIds) {
        return deleteIn(
                "delete from application_report where application_id in (:ids)", applicationIds);
    }

    public int deleteApplicationContentAnalyses(Collection<Long> applicationIds) {
        return deleteIn(
                "delete from application_content_analysis where applicant_id in (:ids)",
                applicationIds);
    }

    public int deleteApplications(Collection<Long> applicationIds) {
        return deleteIn("delete from application where application_id in (:ids)", applicationIds);
    }

    private int deleteIn(String sql, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return entityManager.createNativeQuery(sql)
                .setParameter("ids", ids)
                .executeUpdate();
    }
}
