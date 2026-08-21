package com.fuma.hiselectors.kakao.repository;

import com.fuma.hiselectors.kakao.dto.KakaoRecipientAdminResponse;
import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoRecipientAdminRepository extends JpaRepository<Selectors, Long> {

    @Query(value = """
            select new com.fuma.hiselectors.kakao.dto.KakaoRecipientAdminResponse(
                s.id, u.id, coalesce(s.selectorsNickname, coalesce(u.name, coalesce(s.selectorsCode, '-'))),
                s.selectorsCode, u.email, u.hiId, r.status)
            from Selectors s
            left join User u on u.id = s.userId
            left join UserKakaoRecipient r on r.userId = u.id
            where s.deleted = false
              and (:keyword is null or lower(coalesce(s.selectorsNickname, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.selectorsCode, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.hiId, '')) like lower(concat('%', :keyword, '%')))
              and (:status is null
                   or (:status = 'READY' and r.status = com.fuma.hiselectors.kakao.model.KakaoRecipientStatus.READY)
                   or (:status = 'UNLINKED' and r.id is null)
                   or (:status = 'UNAVAILABLE' and r.status in (com.fuma.hiselectors.kakao.model.KakaoRecipientStatus.REAUTH_REQUIRED, com.fuma.hiselectors.kakao.model.KakaoRecipientStatus.INACTIVE)))
            order by s.selectorsNickname asc, s.id asc
            """,
            countQuery = """
            select count(s) from Selectors s
            left join User u on u.id = s.userId
            left join UserKakaoRecipient r on r.userId = u.id
            where s.deleted = false
              and (:keyword is null or lower(coalesce(s.selectorsNickname, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(s.selectorsCode, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.hiId, '')) like lower(concat('%', :keyword, '%')))
              and (:status is null or (:status = 'READY' and r.status = com.fuma.hiselectors.kakao.model.KakaoRecipientStatus.READY)
                   or (:status = 'UNLINKED' and r.id is null)
                   or (:status = 'UNAVAILABLE' and r.status in (com.fuma.hiselectors.kakao.model.KakaoRecipientStatus.REAUTH_REQUIRED, com.fuma.hiselectors.kakao.model.KakaoRecipientStatus.INACTIVE)))
            """)
    Page<KakaoRecipientAdminResponse> search(@Param("keyword") String keyword,
                                             @Param("status") String status,
                                             Pageable pageable);
}
