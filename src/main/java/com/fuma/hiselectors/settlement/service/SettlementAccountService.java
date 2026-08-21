package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.settlement.dto.SettlementAccountResponse;
import com.fuma.hiselectors.settlement.dto.SettlementAccountUpsertRequest;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementAccountService {

    private final SelectorsRepository selectorsRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SelectorAccessService selectorAccessService;

    public SettlementAccountResponse getAccount(String loginId) {
        Selectors selectors = selectorAccessService.requireReadable(loginId);
        SettlementAccount account = settlementAccountRepository
                .findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(selectors.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return SettlementAccountResponse.of(account);
    }

    @Transactional
    public SettlementAccountResponse upsert(String loginId, SettlementAccountUpsertRequest request) {
        Selectors selectors = selectorsRepository.findByIdForUpdate(
                        selectorAccessService.requireCurrent(loginId).getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
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
}
