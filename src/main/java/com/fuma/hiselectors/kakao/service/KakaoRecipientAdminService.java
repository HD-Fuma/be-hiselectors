package com.fuma.hiselectors.kakao.service;

import com.fuma.hiselectors.kakao.dto.KakaoRecipientAdminResponse;
import com.fuma.hiselectors.kakao.repository.KakaoRecipientAdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoRecipientAdminService {
    private static final Set<String> STATUSES = Set.of("READY", "UNLINKED", "UNAVAILABLE");
    private final KakaoRecipientAdminRepository repository;

    public Page<KakaoRecipientAdminResponse> search(String keyword, String status, int page, int size) {
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "status 값이 올바르지 않습니다.");
        }
        String normalized = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return repository.search(normalized, status, PageRequest.of(page, size));
    }
}
