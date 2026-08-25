package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.settlement.dto.SettlementAccountResponse;
import com.fuma.hiselectors.settlement.dto.SettlementAccountUpsertRequest;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.model.SettlementType;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.settlement.security.SettlementAccountCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementAccountService {

    private final SettlementAccountRepository settlementAccountRepository;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SelectorAccessService selectorAccessService;
    private final SettlementAccountCrypto accountCrypto;

    public SettlementAccountResponse getAccount(String loginId) {
        Selectors selectors = selectorAccessService.requireSettlementReadable(loginId);
        SettlementAccount account = settlementAccountRepository
                .findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(selectors.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return response(account);
    }

    @Transactional
    public SettlementAccountResponse upsert(String loginId, SettlementAccountUpsertRequest request) {
        Selectors selectors = selectorAccessService.requireSettlementWritable(loginId);
        SettlementAccount account = settlementAccountRepository
                .findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(selectors.getId())
                .orElseGet(() -> SettlementAccount.builder().selectorsId(selectors.getId()).build());
        updateIdentity(account, request);
        account.update(request.bankName().trim(), accountCrypto.encrypt(request.accountNumber().trim()),
                request.accountHolder().trim());
        SettlementAccount saved = settlementAccountRepository.save(account);
        settlementHistoryRepository
                .findAllBySelectorsIdAndStatus(selectors.getId(), SettlementStatus.PAYMENT_HOLD_INFO)
                .forEach(SettlementHistory::reopenFromInformationHold);
        return response(saved);
    }

    private void updateIdentity(SettlementAccount account, SettlementAccountUpsertRequest request) {
        SettlementType storedType = SettlementType.fromStorage(account.getSettlementType())
                .orElse(null);
        SettlementType targetType = request.settlementType() != null
                ? request.settlementType()
                : storedType;
        String storedNumber = accountCrypto.decrypt(account.getBusinessNumberEncrypted());
        String requestedNumber = trimToNull(request.businessNumber());
        String targetNumber = request.businessNumber() != null ? requestedNumber : storedNumber;
        requireValidIdentifier(targetType, targetNumber);
        String encryptedNumber = request.businessNumber() != null
                ? accountCrypto.encrypt(targetNumber)
                : account.getBusinessNumberEncrypted();
        account.registerIdentity(targetType, encryptedNumber);
    }

    private SettlementAccountResponse response(SettlementAccount account) {
        return SettlementAccountResponse.of(
                account,
                accountCrypto.decrypt(account.getAccountNumberEncrypted()),
                accountCrypto.decrypt(account.getBusinessNumberEncrypted()));
    }

    private void requireValidIdentifier(SettlementType settlementType, String businessNumber) {
        if (settlementType == null || !settlementType.isValidIdentifier(businessNumber)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "식별번호 형식이 올바르지 않습니다.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
