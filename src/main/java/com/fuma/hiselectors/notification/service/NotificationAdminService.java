package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.notification.dto.NotificationHistoryResponse;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationAdminService {

    private final NotificationRepository notificationRepository;

    public Page<NotificationHistoryResponse> getHistory(
            String purpose,
            NotificationStatus status,
            LocalDate from,
            LocalDate to,
            String recipientKeyword,
            int page,
            int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "시작일은 종료일보다 늦을 수 없습니다.");
        }

        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
        LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        String normalizedPurpose = normalize(purpose);
        String normalizedKeyword = normalize(recipientKeyword);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("requestAt"), Sort.Order.desc("id")));

        return notificationRepository.searchHistory(
                normalizedPurpose, status, fromAt, toExclusive, normalizedKeyword, pageable);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
