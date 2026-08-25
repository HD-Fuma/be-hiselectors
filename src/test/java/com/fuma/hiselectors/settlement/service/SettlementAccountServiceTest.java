package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.settlement.dto.SettlementAccountUpsertRequest;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.model.SettlementType;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.settlement.security.SettlementAccountCrypto;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementAccountServiceTest {

    private static final SettlementAccountCrypto ACCOUNT_CRYPTO = new SettlementAccountCrypto(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void inactiveSelectorCanGetAccountWithSettlementGuard() {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        SettlementAccount account = SettlementAccount.builder()
                .selectorsId(9L)
                .bankName("국민은행")
                .accountNumberEncrypted(encrypt("123-456"))
                .accountHolder("홍길동")
                .businessNumberEncrypted(encrypt("900101-1234567"))
                .settlementType(SettlementType.INDIVIDUAL.name())
                .build();
        SettlementAccountService service = new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService, ACCOUNT_CRYPTO);

        when(selectorAccessService.requireSettlementReadable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));

        var response = service.getAccount("selector-user");

        assertThat(response.bankName()).isEqualTo("국민은행");
        assertThat(response.accountNumber()).isEqualTo("123-456");
        assertThat(response.accountHolder()).isEqualTo("홍길동");
        assertThat(response.settlementType()).isEqualTo(SettlementType.INDIVIDUAL);
        assertThat(response.businessNumber()).isEqualTo("******-*******");
        verify(selectorAccessService).requireSettlementReadable("selector-user");
    }

    @Test
    void legacyAccountDoesNotExposeUnclassifiedBusinessNumber() {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        SettlementAccount account = SettlementAccount.builder()
                .selectorsId(9L)
                .businessNumberEncrypted(encrypt("900101-1234567"))
                .build();
        SettlementAccountService service = new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService, ACCOUNT_CRYPTO);

        when(selectorAccessService.requireSettlementReadable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));

        var response = service.getAccount("selector-user");

        assertThat(response.settlementType()).isNull();
        assertThat(response.businessNumber()).isNull();
    }

    @Test
    void inactiveSelectorCanUpsertAccountAndReopenOnlyInformationHold() {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        SettlementAccount account = SettlementAccount.builder().selectorsId(9L).build();
        SettlementHistory infoHold = heldHistory(SettlementStatus.PAYMENT_HOLD_INFO);
        SettlementHistory blackHold = heldHistory(SettlementStatus.PAYMENT_HOLD_BLACK);
        SettlementAccountService service = new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService, ACCOUNT_CRYPTO);

        when(selectorAccessService.requireSettlementWritable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(historyRepository.findAllBySelectorsIdAndStatus(9L, SettlementStatus.PAYMENT_HOLD_INFO))
                .thenReturn(List.of(infoHold));

        var response = service.upsert("selector-user",
                new SettlementAccountUpsertRequest(" 국민은행 ", " 123-456 ", " 홍길동 ",
                        SettlementType.INDIVIDUAL, " 900101-1234567 "));

        assertThat(response.bankName()).isEqualTo("국민은행");
        assertThat(response.accountNumber()).isEqualTo("123-456");
        assertThat(response.accountHolder()).isEqualTo("홍길동");
        assertThat(response.settlementType()).isEqualTo(SettlementType.INDIVIDUAL);
        assertThat(response.businessNumber()).isEqualTo("******-*******");
        assertThat(account.getBusinessNumberEncrypted()).doesNotContain("900101-1234567");
        assertThat(decrypt(account.getBusinessNumberEncrypted())).isEqualTo("900101-1234567");
        assertThat(account.getAccountNumberEncrypted()).doesNotContain("123-456");
        assertThat(decrypt(account.getAccountNumberEncrypted())).isEqualTo("123-456");
        assertThat(infoHold.getStatus()).isEqualTo(SettlementStatus.PAYMENT_PENDING);
        assertThat(blackHold.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_BLACK);
        verify(selectorAccessService).requireSettlementWritable("selector-user");
        verify(accountRepository).save(account);
    }

    @Test
    void individualTypeAndNumberArePreservedWhenOmittedAndNumberCanChange() {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        SettlementAccount account = individualAccount();
        SettlementAccountService service = new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService, ACCOUNT_CRYPTO);

        when(selectorAccessService.requireSettlementWritable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(historyRepository.findAllBySelectorsIdAndStatus(9L, SettlementStatus.PAYMENT_HOLD_INFO))
                .thenReturn(List.of());

        var response = service.upsert("selector-user",
                request(null, null));

        assertThat(account.getSettlementType()).isEqualTo(SettlementType.INDIVIDUAL.name());
        assertThat(decrypt(account.getBusinessNumberEncrypted())).isEqualTo("900101-1234567");
        assertThat(response.businessNumber()).isEqualTo("******-*******");
        service.upsert("selector-user",
                request(SettlementType.INDIVIDUAL, "900101-7654321"));

        assertThat(decrypt(account.getBusinessNumberEncrypted()))
                .isEqualTo("900101-7654321");
    }

    @Test
    void registeredSettlementTypeCanChange() {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        SettlementAccount account = individualAccount();
        SettlementAccountService service = new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService, ACCOUNT_CRYPTO);

        when(selectorAccessService.requireSettlementWritable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(historyRepository.findAllBySelectorsIdAndStatus(9L, SettlementStatus.PAYMENT_HOLD_INFO))
                .thenReturn(List.of());

        service.upsert("selector-user",
                request(SettlementType.CORPORATION, "123-45-67890"));

        assertThat(account.getSettlementType()).isEqualTo(SettlementType.CORPORATION.name());
        assertThat(decrypt(account.getBusinessNumberEncrypted())).isEqualTo("123-45-67890");
    }

    @ParameterizedTest
    @EnumSource(value = SettlementType.class, names = {"SOLE_PROPRIETOR", "CORPORATION"})
    void businessNumberCanChangeForBusiness(SettlementType settlementType) {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        SettlementAccount account = SettlementAccount.builder()
                .selectorsId(9L)
                .bankName("국민은행")
                .accountNumberEncrypted(encrypt("123-456"))
                .accountHolder("주식회사 셀렉터스")
                .settlementType(settlementType.name())
                .businessNumberEncrypted(encrypt("123-45-67890"))
                .build();
        SettlementAccountService service = new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService, ACCOUNT_CRYPTO);

        when(selectorAccessService.requireSettlementWritable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(historyRepository.findAllBySelectorsIdAndStatus(9L, SettlementStatus.PAYMENT_HOLD_INFO))
                .thenReturn(List.of());

        var response = service.upsert("selector-user",
                request(settlementType, " 987-65-43210 "));

        assertThat(account.getBusinessNumberEncrypted()).doesNotContain("987-65-43210");
        assertThat(decrypt(account.getBusinessNumberEncrypted())).isEqualTo("987-65-43210");
        assertThat(response.businessNumber()).isEqualTo("987-65-43210");
    }

    @Test
    void legacyAccountRequiresTypeAndNumberToRegisterIdentity() {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        SettlementAccount account = SettlementAccount.builder()
                .selectorsId(9L)
                .businessNumberEncrypted(encrypt("unclassified-number"))
                .build();
        SettlementAccountService service = new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService, ACCOUNT_CRYPTO);

        when(selectorAccessService.requireSettlementWritable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.upsert("selector-user",
                request(SettlementType.SOLE_PROPRIETOR, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @ParameterizedTest
    @CsvSource({
            "INDIVIDUAL, 900101123456",
            "INDIVIDUAL, 900101-12-34567",
            "SOLE_PROPRIETOR, 123456789",
            "CORPORATION, 1234-5-67890"
    })
    void invalidIdentifierFormatsAreRejected(
            SettlementType settlementType, String invalidNumber) {
        SettlementAccount account = SettlementAccount.builder().selectorsId(9L).build();
        SettlementAccountService service = serviceFor(account);

        assertThatThrownBy(() -> service.upsert(
                "selector-user", request(settlementType, invalidNumber)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void legacyIndividualRegistrationStoresRequestedNumber() {
        SettlementAccount account = SettlementAccount.builder()
                .selectorsId(9L)
                .businessNumberEncrypted(encrypt("9001011234567"))
                .build();
        SettlementAccountService service = serviceFor(account);

        var response = service.upsert("selector-user",
                request(SettlementType.INDIVIDUAL, "900101-1234567"));

        assertThat(account.getSettlementType()).isEqualTo(SettlementType.INDIVIDUAL.name());
        assertThat(decrypt(account.getBusinessNumberEncrypted())).isEqualTo("900101-1234567");
        assertThat(response.businessNumber()).isEqualTo("******-*******");
    }

    @Test
    void legacyIndividualRegistrationAcceptsDifferentNumber() {
        SettlementAccount account = SettlementAccount.builder()
                .selectorsId(9L)
                .businessNumberEncrypted(encrypt("9001011234567"))
                .build();
        SettlementAccountService service = serviceFor(account);

        service.upsert("selector-user",
                request(SettlementType.INDIVIDUAL, "900101-7654321"));

        assertThat(account.getSettlementType()).isEqualTo(SettlementType.INDIVIDUAL.name());
        assertThat(decrypt(account.getBusinessNumberEncrypted())).isEqualTo("900101-7654321");
    }

    @Test
    void unknownStoredTypeIsHiddenAndCanBeRepaired() {
        SettlementAccount account = SettlementAccount.builder()
                .selectorsId(9L)
                .settlementType("UNKNOWN")
                .businessNumberEncrypted(encrypt("900101-1234567"))
                .build();
        SettlementAccountService service = serviceFor(account);

        var response = service.getAccount("selector-user");

        assertThat(response.settlementType()).isNull();
        assertThat(response.businessNumber()).isNull();
        service.upsert("selector-user",
                request(SettlementType.INDIVIDUAL, "900101-1234567"));

        assertThat(account.getSettlementType()).isEqualTo(SettlementType.INDIVIDUAL.name());
    }

    @Test
    void invalidStoredBusinessNumberMustBeRepairedForSuccessfulUpsert() {
        SettlementAccount account = SettlementAccount.builder()
                .selectorsId(9L)
                .settlementType(SettlementType.CORPORATION.name())
                .businessNumberEncrypted(encrypt("invalid-number"))
                .build();
        SettlementAccountService service = serviceFor(account);

        assertThatThrownBy(() -> service.upsert("selector-user", request(null, null)))
                .isInstanceOf(BusinessException.class);

        service.upsert("selector-user", request(null, "123-45-67890"));

        assertThat(account.getSettlementType()).isEqualTo(SettlementType.CORPORATION.name());
        assertThat(decrypt(account.getBusinessNumberEncrypted())).isEqualTo("123-45-67890");
    }

    private SettlementAccountUpsertRequest request(
            SettlementType settlementType, String businessNumber) {
        return new SettlementAccountUpsertRequest(
                "국민은행", "123-456", "홍길동", settlementType, businessNumber);
    }

    private SettlementAccount individualAccount() {
        return SettlementAccount.builder()
                .selectorsId(9L)
                .bankName("국민은행")
                .accountNumberEncrypted(encrypt("123-456"))
                .accountHolder("홍길동")
                .settlementType(SettlementType.INDIVIDUAL.name())
                .businessNumberEncrypted(encrypt("900101-1234567"))
                .build();
    }

    private SettlementAccountService serviceFor(SettlementAccount account) {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        when(selectorAccessService.requireSettlementReadable("selector-user"))
                .thenReturn(selectors);
        when(selectorAccessService.requireSettlementWritable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(historyRepository.findAllBySelectorsIdAndStatus(9L, SettlementStatus.PAYMENT_HOLD_INFO))
                .thenReturn(List.of());
        return new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService, ACCOUNT_CRYPTO);
    }

    private static String encrypt(String value) {
        return ACCOUNT_CRYPTO.encrypt(value);
    }

    private static String decrypt(String value) {
        return ACCOUNT_CRYPTO.decrypt(value);
    }

    private Selectors inactiveSelectors() {
        Selectors selectors = Selectors.builder()
                .selectorsRoleId(Selectors.INACTIVE_ROLE)
                .build();
        ReflectionTestUtils.setField(selectors, "id", 9L);
        return selectors;
    }

    private SettlementHistory heldHistory(SettlementStatus holdStatus) {
        SettlementHistory history = SettlementHistory.create(9L, LocalDateTime.of(2026, 5, 1, 0, 0));
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 6, 21, 0, 0));
        history.transitionTo(holdStatus, LocalDateTime.of(2026, 7, 20, 0, 0));
        return history;
    }
}
