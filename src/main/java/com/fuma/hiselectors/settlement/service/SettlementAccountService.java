package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.dto.SettlementAccountResponse;
import com.fuma.hiselectors.settlement.dto.SettlementAccountUpsertRequest;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementAccountService {

    private final UserRepository userRepository;
    private final SelectorsRepository selectorsRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final SettlementHistoryRepository settlementHistoryRepository;

    public SettlementAccountResponse getAccount(String loginId) {
        Selectors selectors = findSelectors(loginId);
        SettlementAccount account = settlementAccountRepository
                .findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(selectors.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return SettlementAccountResponse.of(account);
    }

    @Transactional
    public SettlementAccountResponse upsert(String loginId, SettlementAccountUpsertRequest request) {
        Selectors selectors = findSelectorsForUpdate(loginId);
        SettlementAccount account = settlementAccountRepository
                .findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(selectors.getId())
                .orElseGet(() -> SettlementAccount.builder().selectorsId(selectors.getId()).build());
        account.update(request.bankName().trim(), request.accountNumber().trim(),
                request.accountHolder().trim());
        SettlementAccount saved = settlementAccountRepository.save(account);
        settlementHistoryRepository
                .findAllBySelectorsIdAndStatus(selectors.getId(), SettlementStatus.PAYMENT_HOLD_INFO)
                .forEach(SettlementHistory::reopenFromInformationHold);
        return SettlementAccountResponse.of(saved);
    }

    private Selectors findSelectors(String loginId) {
        User user = userRepository.findByHiId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        return selectorsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
    }

    private Selectors findSelectorsForUpdate(String loginId) {
        Selectors selectors = findSelectors(loginId);
        return selectorsRepository.findByIdForUpdate(selectors.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
    }
}
